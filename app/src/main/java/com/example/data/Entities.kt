package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class BrowserProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val initialUrl: String = "https://www.google.com",
    val userAgentType: String = "Default", // Default, Chrome Desktop, Safari Mac, Chrome Mobile, Safari iPhone, Custom
    val customUserAgent: String = "",
    val proxyType: String = "DIRECT", // DIRECT, HTTP, HTTPS, SOCKS4, SOCKS5
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val proxyUser: String = "",
    val proxyPass: String = "",
    val isIncognito: Boolean = false,
    val lastVisitedUrl: String = "https://www.google.com",
    val canvasNoiseEnabled: Boolean = false,
    val webGlSpoofEnabled: Boolean = false,
    val spoofedPlatform: String = "Default", // Default, Win32, MacIntel, iPhone, Linux armv8l
    val spoofedLanguages: String = "Default", // Default, e.g., "es-MX,es;q=0.9,en;q=0.8"
    val spoofedTimezone: String = "Default",
    val spoofedGeolocation: String = "Default",
    val localStorageJson: String = "{}"
)

@Entity(tableName = "profile_cookies")
data class ProfileCookie(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val domain: String,
    val cookieValue: String
)

@Entity(tableName = "profile_history")
data class ProfileHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "profile_bookmarks")
data class ProfileBookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val url: String
)

@Entity(tableName = "profile_visited_domains")
data class ProfileVisitedDomain(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val domainUrl: String // Store domain URL to retrieve cookies (e.g. "https://domain.com")
)

@Entity(tableName = "profile_local_storage", primaryKeys = ["profileId", "domain"])
data class ProfileLocalStorage(
    val profileId: Long,
    val domain: String,
    val localStorageJson: String
)

@Entity(tableName = "proxy_bank")
data class ProxyBankItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // HTTP, HTTPS, SOCKS4, SOCKS5
    val host: String,
    val port: Int,
    val user: String = "",
    val pass: String = "",
    val label: String = "",
    val isWorking: Boolean? = null, // null = untested, true = working, false = failed
    val latencyMs: Long = 0
)
