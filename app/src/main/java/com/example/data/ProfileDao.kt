package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name ASC")
    fun getAllProfiles(): Flow<List<BrowserProfile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): BrowserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: BrowserProfile): Long

    @Update
    suspend fun updateProfile(profile: BrowserProfile)

    @Delete
    suspend fun deleteProfile(profile: BrowserProfile)

    @Query("SELECT * FROM profile_cookies WHERE profileId = :profileId")
    suspend fun getCookiesForProfile(profileId: Long): List<ProfileCookie>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCookie(cookie: ProfileCookie)

    @Query("DELETE FROM profile_cookies WHERE profileId = :profileId")
    suspend fun deleteCookiesForProfile(profileId: Long)

    @Query("DELETE FROM profile_cookies WHERE profileId = :profileId AND domain = :domain")
    suspend fun deleteCookieForDomain(profileId: Long, domain: String)

    @Query("SELECT * FROM profile_history WHERE profileId = :profileId ORDER BY timestamp DESC")
    fun getHistoryForProfile(profileId: Long): Flow<List<ProfileHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ProfileHistory)

    @Query("DELETE FROM profile_history WHERE profileId = :profileId")
    suspend fun deleteHistoryForProfile(profileId: Long)

    @Query("SELECT * FROM profile_bookmarks WHERE profileId = :profileId")
    fun getBookmarksForProfile(profileId: Long): Flow<List<ProfileBookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: ProfileBookmark)

    @Query("DELETE FROM profile_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: Long)

    @Query("SELECT * FROM profile_visited_domains WHERE profileId = :profileId")
    suspend fun getVisitedDomainsForProfile(profileId: Long): List<ProfileVisitedDomain>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVisitedDomain(domain: ProfileVisitedDomain)

    @Query("DELETE FROM profile_visited_domains WHERE profileId = :profileId")
    suspend fun deleteVisitedDomainsForProfile(profileId: Long)
}
