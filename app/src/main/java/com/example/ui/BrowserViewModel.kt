package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.data.*
import com.example.util.CookieSyncHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = ProfileRepository(database.profileDao())
    private val visitedDomainsCache = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    val allProfiles = repository.allProfiles

    private val _activeProfile = MutableStateFlow<BrowserProfile?>(null)
    val activeProfile: StateFlow<BrowserProfile?> = _activeProfile

    private val _activeHistoryList = MutableStateFlow<List<ProfileHistory>>(emptyList())
    val activeHistoryList: StateFlow<List<ProfileHistory>> = _activeHistoryList

    private val _activeBookmarksList = MutableStateFlow<List<ProfileBookmark>>(emptyList())
    val activeBookmarksList: StateFlow<List<ProfileBookmark>> = _activeBookmarksList

    // State flow containing the target URL path to signal WebView to navigate
    private val _navigationTrigger = MutableSharedFlow<String>()
    val navigationTrigger: SharedFlow<String> = _navigationTrigger.asSharedFlow()

    // Active URL text status
    val currentUrlText = MutableStateFlow("")
    val loadingProgress = MutableStateFlow(0)
    val isPageLoading = MutableStateFlow(false)

    init {
        // Empty init to avoid auto-creating or auto-selecting a profile on startup.
    }

    fun selectProfile(
        profileId: Long,
        currentUrl: String = "",
        currentLocalStorageJson: String? = null
    ) {
        viewModelScope.launch {
            val oldProfile = _activeProfile.value
            if (oldProfile != null) {
                if (oldProfile.id == profileId) {
                    // It is the same profile! Just return and do absolutely nothing.
                    return@launch
                }
                var updatedOld = oldProfile
                if (currentUrl.isNotEmpty()) {
                    updatedOld = updatedOld.copy(lastVisitedUrl = currentUrl)
                }
                if (currentLocalStorageJson != null) {
                    updatedOld = updatedOld.copy(localStorageJson = currentLocalStorageJson)
                }
                // Save updated profile with latest localStorage and URL to database sequentially
                repository.updateProfile(updatedOld)
                
                // Save active cookies before leaving
                CookieSyncHelper.saveActiveCookies(oldProfile.id, repository)
            }

            val target = repository.getProfileById(profileId) ?: return@launch
            _activeProfile.value = target
            currentUrlText.value = target.lastVisitedUrl

            // Initialize visited domains cache for the target profile
            visitedDomainsCache.clear()
            val domains = repository.getVisitedDomainsForProfile(target.id)
            visitedDomainsCache.addAll(domains.map { it.domainUrl.lowercase() })

            // Synchronize room items flow
            viewModelScope.launch {
                repository.getHistoryForProfile(target.id).collect {
                    _activeHistoryList.value = it
                }
            }
            viewModelScope.launch {
                repository.getBookmarksForProfile(target.id).collect {
                    _activeBookmarksList.value = it
                }
            }

            // Restore cookies
            CookieSyncHelper.restoreProfileCookies(target.id, repository)

            // Setup proxy configuration
            applyProxyOverride(target)

            // Send signal to webview
            _navigationTrigger.emit(target.lastVisitedUrl)
        }
    }

    fun closeActiveProfile(
        currentUrl: String = "",
        currentLocalStorageJson: String? = null
    ) {
        viewModelScope.launch {
            val oldProfile = _activeProfile.value
            if (oldProfile != null) {
                var updatedOld = oldProfile
                if (currentUrl.isNotEmpty()) {
                    updatedOld = updatedOld.copy(lastVisitedUrl = currentUrl)
                }
                if (currentLocalStorageJson != null) {
                    updatedOld = updatedOld.copy(localStorageJson = currentLocalStorageJson)
                }
                repository.updateProfile(updatedOld)
                CookieSyncHelper.saveActiveCookies(oldProfile.id, repository)
            }
            _activeProfile.value = null
            currentUrlText.value = ""
            try {
                // Clear proxy override when closing the active profile
                androidx.webkit.ProxyController.getInstance().clearProxyOverride(
                    { command -> command.run() },
                    { /* Proxy cleared */ }
                )
            } catch (e: Exception) {
                android.util.Log.e("IncogNav", "Error reset proxy override on close: ${e.message}")
            }
        }
    }

    fun createProfile(
        name: String,
        initialUrl: String,
        userAgentType: String,
        customUa: String,
        proxyType: String,
        proxyHost: String,
        proxyPort: String,
        proxyUser: String,
        proxyPass: String,
        isIncognito: Boolean,
        canvasNoise: Boolean,
        webglSpoof: Boolean,
        platform: String,
        languages: String
    ) {
        viewModelScope.launch {
            val parsedPort = proxyPort.toIntOrNull() ?: 0
            val pUrl = if (initialUrl.isNotEmpty()) initialUrl else "https://www.google.com"
            val newProfile = BrowserProfile(
                name = name,
                initialUrl = pUrl,
                userAgentType = userAgentType,
                customUserAgent = customUa,
                proxyType = proxyType,
                proxyHost = proxyHost,
                proxyPort = parsedPort,
                proxyUser = proxyUser,
                proxyPass = proxyPass,
                isIncognito = isIncognito,
                lastVisitedUrl = pUrl,
                canvasNoiseEnabled = canvasNoise,
                webGlSpoofEnabled = webglSpoof,
                spoofedPlatform = platform,
                spoofedLanguages = languages
            )
            val newId = repository.insertProfile(newProfile)
            
            // Auto-seed typical starting domain
            val startDomain = getDomainUrlOnly(pUrl)
            repository.addVisitedDomain(newId, startDomain)

            selectProfile(newId)
        }
    }

    fun updateActiveProfile(updated: BrowserProfile) {
        viewModelScope.launch {
            repository.updateProfile(updated)
            _activeProfile.value = updated
            applyProxyOverride(updated)
        }
    }

    fun updateActiveProfileLocalStorage(json: String) {
        val active = _activeProfile.value ?: return
        if (active.localStorageJson == json) return
        viewModelScope.launch {
            val updated = active.copy(localStorageJson = json)
            repository.updateProfile(updated)
            _activeProfile.value = updated
        }
    }

    fun deleteProfile(profile: BrowserProfile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            repository.clearCookiesForProfile(profile.id)
            repository.deleteHistory(profile.id)
            repository.deleteVisitedDomainsForProfile(profile.id)
            
            // Automatically clear temporary caches to keep storage slim on profile deletion
            try {
                val context = getApplication<Application>()
                val webView = android.webkit.WebView(context)
                webView.clearCache(true)
                deleteDirRecursive(context.cacheDir)
            } catch (e: Exception) {
                android.util.Log.e("IncogNav", "Silent cache cleanup error: ${e.message}")
            }
            
            if (_activeProfile.value?.id == profile.id) {
                val remaining = repository.allProfiles.first()
                if (remaining.isNotEmpty()) {
                    selectProfile(remaining.first().id)
                } else {
                    _activeProfile.value = null
                    currentUrlText.value = ""
                }
            }
        }
    }

    fun navigateTo(url: String) {
        viewModelScope.launch {
            val normalized = normalizeUrl(url)
            currentUrlText.value = normalized
            
            recordVisitedDomain(normalized)
            
            _navigationTrigger.emit(normalized)
        }
    }

    fun recordVisitedDomain(url: String) {
        val activeId = _activeProfile.value?.id ?: return
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val domainUrl = getDomainUrlOnly(url).lowercase()
        if (domainUrl.isEmpty()) return

        saveDomainIfNew(activeId, domainUrl)

        // Also register base domain if it's a subdomain to capture session cookies set on the root domain
        try {
            val uri = java.net.URI(domainUrl)
            val host = uri.host ?: return
            val scheme = uri.scheme ?: "https"
            val parts = host.split(".")
            if (parts.size > 2) {
                val baseHost = parts.takeLast(2).joinToString(".")
                val baseDomain = "$scheme://$baseHost"
                saveDomainIfNew(activeId, baseDomain)
            }
        } catch (_: Exception) {}
    }

    private fun saveDomainIfNew(profileId: Long, domainUrl: String) {
        if (!visitedDomainsCache.contains(domainUrl)) {
            visitedDomainsCache.add(domainUrl)
            viewModelScope.launch {
                repository.addVisitedDomain(profileId, domainUrl)
            }
        }
    }

    fun saveCurrentUrl(url: String) {
        val active = _activeProfile.value ?: return
        if (active.lastVisitedUrl == url) return
        viewModelScope.launch {
            val updated = active.copy(lastVisitedUrl = url)
            repository.updateProfile(updated)
            _activeProfile.value = updated
        }
    }

    fun saveActiveCookiesExternal() {
        val active = _activeProfile.value ?: return
        viewModelScope.launch {
            CookieSyncHelper.saveActiveCookies(active.id, repository)
        }
    }

    fun saveProfileStateOffline(profileId: Long, currentUrl: String) {
        viewModelScope.launch {
            if (currentUrl.isNotEmpty()) {
                repository.getProfileById(profileId)?.let { oldProfile ->
                    repository.updateProfile(oldProfile.copy(lastVisitedUrl = currentUrl))
                }
            }
            CookieSyncHelper.saveActiveCookies(profileId, repository)
        }
    }

    fun recordHistory(title: String, url: String) {
        val active = _activeProfile.value ?: return
        if (active.isIncognito) return // Do not record history for incognito sessions
        viewModelScope.launch {
            repository.insertHistory(ProfileHistory(profileId = active.id, title = title, url = url))
        }
    }

    fun toggleBookmark(title: String, url: String) {
        val active = _activeProfile.value ?: return
        viewModelScope.launch {
            val current = _activeBookmarksList.value
            val existing = current.find { it.url == url }
            if (existing != null) {
                repository.deleteBookmark(existing.id)
            } else {
                repository.insertBookmark(ProfileBookmark(profileId = active.id, title = title, url = url))
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }

    fun clearActiveHistory() {
        val active = _activeProfile.value ?: return
        viewModelScope.launch {
            repository.deleteHistory(active.id)
        }
    }

    fun resetSession() {
        viewModelScope.launch {
            val active = _activeProfile.value ?: return@launch
            
            // If MULTI_PROFILE is natively supported, clear native profile cookies and webStorage data
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                try {
                    val profileStore = ProfileStore.getInstance()
                    val webProfile = profileStore.getProfile("profile_${active.id}")
                    if (webProfile != null) {
                        webProfile.cookieManager.removeAllCookies(null)
                        webProfile.webStorage.deleteAllData()
                    }
                } catch (e: java.lang.Exception) {
                    android.util.Log.e("BrowserViewModel", "Error al resetear perfil nativo: ${e.message}", e)
                }
            }
            
            repository.clearCookiesForProfile(active.id)
            CookieSyncHelper.restoreProfileCookies(active.id, repository)
            val updated = active.copy(localStorageJson = "{}")
            repository.updateProfile(updated)
            _activeProfile.value = updated
            navigateTo(active.initialUrl)
        }
    }

    private fun applyProxyOverride(profile: BrowserProfile) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            return
        }

        if (profile.proxyType == "DIRECT" || profile.proxyHost.isEmpty()) {
            ProxyController.getInstance().clearProxyOverride(
                { command -> command.run() },
                { /* Proxy cleared */ }
            )
            return
        }

        val prefix = when (profile.proxyType) {
            "SOCKS4" -> "socks4://"
            "SOCKS5" -> "socks5://"
            "HTTPS" -> "https://"
            "HTTP" -> "http://"
            else -> "http://"
        }
        val proxyStr = "$prefix${profile.proxyHost}:${profile.proxyPort}"
        val proxyConfig = ProxyConfig.Builder()
            .addProxyRule(proxyStr)
            .build()

        ProxyController.getInstance().setProxyOverride(
            proxyConfig,
            { executor -> executor.run() },
            { /* Proxy applied */ }
        )
    }

    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        
        // Handle queries
        if (!trimmed.contains(".") || trimmed.contains(" ")) {
            val query = trimmed.replace(" ", "+")
            return "https://www.google.com/search?q=$query"
        }
        
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return "https://$trimmed"
    }

    fun getDomainUrlOnly(urlStr: String): String {
        return try {
            val uri = java.net.URI(urlStr)
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return urlStr
            "$scheme://$host"
        } catch (e: Exception) {
            urlStr
        }
    }

    fun clearGlobalCache(context: android.content.Context) {
        viewModelScope.launch {
            try {
                // Clear Webview global instance cache
                val webView = android.webkit.WebView(context)
                webView.clearCache(true)

                // Recursively delete standard files inside cache directories
                deleteDirRecursive(context.cacheDir)
                deleteDirRecursive(context.codeCacheDir)

                android.util.Log.d("IncogNav", "Global cache files and V8 temporary execution buffers cleared successfully")
            } catch (e: Exception) {
                android.util.Log.e("IncogNav", "Error executing global cache purge: ${e.message}")
            }
        }
    }

    private fun deleteDirRecursive(file: java.io.File?): Boolean {
        if (file == null) return false
        if (file.isDirectory) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteDirRecursive(child)
                }
            }
        }
        return file.delete()
    }
}
