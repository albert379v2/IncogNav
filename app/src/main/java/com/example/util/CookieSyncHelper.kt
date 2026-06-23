package com.example.util

import android.webkit.CookieManager
import android.webkit.WebStorage
import com.example.data.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

object CookieSyncHelper {

    fun getCookieManager(profileId: Long?): CookieManager {
        if (profileId != null && androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
            try {
                val profileStore = androidx.webkit.ProfileStore.getInstance()
                val webProfile = profileStore.getProfile("profile_$profileId")
                if (webProfile != null) {
                    return webProfile.cookieManager
                }
            } catch (e: Exception) {
                android.util.Log.e("CookieSyncHelper", "Error getting multi-profile cookie manager: ${e.message}", e)
            }
        }
        return CookieManager.getInstance()
    }

    suspend fun saveActiveCookies(profileId: Long, repository: ProfileRepository) = withContext(Dispatchers.IO) {
        val visitedDomains = repository.getVisitedDomainsForProfile(profileId)
        val cookieManager = getCookieManager(profileId)
        
        for (visitedDomain in visitedDomains) {
            val url = visitedDomain.domainUrl
            val cookiesString = cookieManager.getCookie(url)
            if (!cookiesString.isNullOrEmpty()) {
                repository.saveCookie(profileId, url, cookiesString)
            }
        }
    }

    suspend fun restoreProfileCookies(profileId: Long, repository: ProfileRepository) = withContext(Dispatchers.Main) {
        // If MULTI_PROFILE is supported, the native WebView profile already isolates and maintains
        // its own cookies, localStorage, sessionStore, and indexedDB natively on disk.
        // We must NOT wipe or override them on profile loading, otherwise we destroy session persistence.
        if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.MULTI_PROFILE)) {
            return@withContext
        }

        // Below is the fallback routine for older Android devices without MULTI_PROFILE support
        // Clear all local web storage per profile to completely seal data session isolation
        WebStorage.getInstance().deleteAllData()

        val cookieManager = getCookieManager(profileId)
        
        // Remove existing cookies with a suspending clean routine
        suspendCoroutine<Boolean> { continuation ->
            cookieManager.removeAllCookies {
                continuation.resume(true)
            }
        }
        
        val savedCookies = withContext(Dispatchers.IO) {
            repository.getCookiesForProfile(profileId)
        }
        
        for (savedCookie in savedCookies) {
            suspendCoroutine<Boolean> { continuation ->
                cookieManager.setCookie(savedCookie.domain, savedCookie.cookieValue) {
                    continuation.resume(true)
                }
            }
        }
        
        // Flush changes to disk
        cookieManager.flush()
    }
}

