package com.audioflow.player.ui.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.audioflow.player.data.remote.YouTubeCookieManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private const val TAG = "YouTubeLoginScreen"
private const val YOUTUBE_LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://www.youtube.com"
private const val YOUTUBE_HOME_URL = "https://www.youtube.com"

@HiltViewModel
class YouTubeLoginViewModel @Inject constructor(
    private val cookieManager: YouTubeCookieManager
) : ViewModel() {
    
    fun saveCookies(cookies: String) {
        cookieManager.saveCookies(cookies)
    }
    
    fun isLoggedIn(): Boolean {
        return cookieManager.isLoggedIn()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: YouTubeLoginViewModel = hiltViewModel()
) {
    var isLoading by remember { mutableStateOf(true) }
    var loginComplete by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect YouTube Account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Loading indicator
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // WebView for YouTube login
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        // Enable JavaScript (required for Google login)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        
                        // Use default User-Agent for login - safest for security checks
                        // settings.userAgentString = YouTubeCookieManager.USER_AGENT
                        
                        // Clear any existing cookies first for fresh login
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                Log.d(TAG, "Loading: $url")
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                Log.d(TAG, "Finished: $url")
                                
                                if (loginComplete) return

                                // proactively check for cookies on any Google/YouTube page
                                val cookieString = CookieManager.getInstance().getCookie(url)
                                if (cookieString != null && (url?.contains("youtube.com") == true || url?.contains("google.com") == true)) {
                                    // Check if we have authentication cookies
                                    // SAPISID or __Secure-1PSID are good indicators of being logged in
                                    val hasAuthCookies = cookieString.contains("SAPISID") || 
                                                         cookieString.contains("__Secure-1PSID") ||
                                                         cookieString.contains("SSID")
                                    
                                    if (hasAuthCookies) {
                                        Log.d(TAG, "Login successful! Found auth cookies. Saving...")
                                        viewModel.saveCookies(cookieString)
                                        loginComplete = true
                                        onLoginSuccess()
                                    }
                                }
                            }
                            
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                Log.d(TAG, "Navigation: $url")
                                
                                // Allow Google/YouTube URLs
                                if (url.contains("google.com") || 
                                    url.contains("youtube.com") ||
                                    url.contains("gstatic.com") ||
                                    url.contains("googleapis.com")) {
                                    return false
                                }
                                
                                // Block other URLs
                                return true
                            }
                        }
                        
                        // Load the login page
                        loadUrl(YOUTUBE_LOGIN_URL)
                    }
                },
                update = { webView ->
                    // No updates needed
                }
            )
        }
    }
    
    // Cleanup WebView when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            // WebView cleanup will happen automatically
        }
    }
}
