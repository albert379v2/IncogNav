package com.example.data

import kotlinx.coroutines.flow.Flow

class ProfileRepository(private val profileDao: ProfileDao) {
    val allProfiles: Flow<List<BrowserProfile>> = profileDao.getAllProfiles()

    suspend fun getProfileById(id: Long): BrowserProfile? {
        return profileDao.getProfileById(id)
    }

    suspend fun insertProfile(profile: BrowserProfile): Long {
        return profileDao.insertProfile(profile)
    }

    suspend fun updateProfile(profile: BrowserProfile) {
        profileDao.updateProfile(profile)
    }

    suspend fun deleteProfile(profile: BrowserProfile) {
        profileDao.deleteProfile(profile)
    }

    suspend fun getCookiesForProfile(profileId: Long): List<ProfileCookie> {
        return profileDao.getCookiesForProfile(profileId)
    }

    suspend fun saveCookie(profileId: Long, domain: String, value: String) {
        profileDao.deleteCookieForDomain(profileId, domain)
        profileDao.insertCookie(ProfileCookie(profileId = profileId, domain = domain, cookieValue = value))
    }

    suspend fun clearCookiesForProfile(profileId: Long) {
        profileDao.deleteCookiesForProfile(profileId)
    }

    fun getHistoryForProfile(profileId: Long): Flow<List<ProfileHistory>> {
        return profileDao.getHistoryForProfile(profileId)
    }

    suspend fun insertHistory(history: ProfileHistory) {
        profileDao.insertHistory(history)
    }

    suspend fun deleteHistory(profileId: Long) {
        profileDao.deleteHistoryForProfile(profileId)
    }

    fun getBookmarksForProfile(profileId: Long): Flow<List<ProfileBookmark>> {
        return profileDao.getBookmarksForProfile(profileId)
    }

    suspend fun insertBookmark(bookmark: ProfileBookmark) {
        profileDao.insertBookmark(bookmark)
    }

    suspend fun deleteBookmark(bookmarkId: Long) {
        profileDao.deleteBookmark(bookmarkId)
    }

    suspend fun getVisitedDomainsForProfile(profileId: Long): List<ProfileVisitedDomain> {
        return profileDao.getVisitedDomainsForProfile(profileId)
    }

    suspend fun addVisitedDomain(profileId: Long, domainUrl: String) {
        val existing = profileDao.getVisitedDomainsForProfile(profileId)
        if (existing.none { it.domainUrl.equals(domainUrl, ignoreCase = true) }) {
            profileDao.insertVisitedDomain(ProfileVisitedDomain(profileId = profileId, domainUrl = domainUrl))
        }
    }

    suspend fun deleteVisitedDomainsForProfile(profileId: Long) {
        profileDao.deleteVisitedDomainsForProfile(profileId)
    }

    suspend fun getLocalStorage(profileId: Long, domain: String): String? {
        return profileDao.getLocalStorage(profileId, domain)
    }

    suspend fun saveLocalStorage(profileId: Long, domain: String, value: String) {
        if (value.length > 1500000) {
            android.util.Log.e("IncogNav", "Local storage data for $domain exceeds safe size limit (1.5MB). Skipping save to avoid DB crash.")
            return
        }
        profileDao.insertLocalStorage(ProfileLocalStorage(profileId = profileId, domain = domain, localStorageJson = value))
    }

    suspend fun deleteLocalStorageForProfile(profileId: Long) {
        profileDao.deleteLocalStorageForProfile(profileId)
    }
}
