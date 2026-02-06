package com.audioflow.player.data.remote

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YouTubeCookieManager"
private const val PREFS_NAME = "youtube_cookies"
private const val KEY_COOKIES = "cookies"
private const val KEY_LOGGED_IN = "is_logged_in"

/**
 * Manages YouTube authentication cookies for API requests.
 * Stores cookies securely using EncryptedSharedPreferences.
 */
@Singleton
class YouTubeCookieManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        // Standard Chrome User-Agent matching NewPipe Extractor's downloader
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    /**
     * Check if user is logged into YouTube
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_LOGGED_IN, false) && getCookies().isNotEmpty()
    }
    
    /**
     * Get stored cookies as a string for HTTP headers
     */
    fun getCookies(): String {
        return prefs.getString(KEY_COOKIES, "") ?: ""
    }
    
    /**
     * Save cookies from WebView after login
     * @param cookies Cookie string in format "name1=value1; name2=value2"
     */
    fun saveCookies(cookies: String) {
        Log.d(TAG, "Saving cookies (length: ${cookies.length})")
        prefs.edit()
            .putString(KEY_COOKIES, cookies)
            .putBoolean(KEY_LOGGED_IN, cookies.isNotEmpty())
            .apply()
    }
    
    /**
     * Clear all cookies (logout)
     */
    fun clearCookies() {
        Log.d(TAG, "Clearing cookies")
        prefs.edit()
            .remove(KEY_COOKIES)
            .putBoolean(KEY_LOGGED_IN, false)
            .apply()
    }
    
    /**
     * Check if cookies contain essential YouTube auth cookies
     */
    fun hasValidCookies(): Boolean {
        val cookies = getCookies()
        // YouTube uses these cookies for authentication
        return cookies.contains("SAPISID") || 
               cookies.contains("__Secure-1PSID") ||
               cookies.contains("LOGIN_INFO")
    }
}
