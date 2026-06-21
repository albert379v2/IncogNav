package com.example.util

import android.webkit.CookieManager
import com.example.data.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CookieSyncHelper {

    suspend fun saveActiveCookies(profileId: Long, repository: ProfileRepository) = withContext(Dispatchers.IO) {
        val visitedDomains = repository.getVisitedDomainsForProfile(profileId)
        val cookieManager = CookieManager.getInstance()
        
        for (visitedDomain in visitedDomains) {
            val url = visitedDomain.domainUrl
            val cookiesString = cookieManager.getCookie(url)
            if (!cookiesString.isNullOrEmpty()) {
                repository.saveCookie(profileId, url, cookiesString)
            }
        }
    }

    suspend fun restoreProfileCookies(profileId: Long, repository: ProfileRepository) = withContext(Dispatchers.Main) {
        val cookieManager = CookieManager.getInstance()
        
        // Block and clear first
        withContext(Dispatchers.IO) {
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        }
        
        val savedCookies = withContext(Dispatchers.IO) {
            repository.getCookiesForProfile(profileId)
        }
        
        for (savedCookie in savedCookies) {
            cookieManager.setCookie(savedCookie.domain, savedCookie.cookieValue)
        }
        
        withContext(Dispatchers.IO) {
            cookieManager.flush()
        }
    }
}
