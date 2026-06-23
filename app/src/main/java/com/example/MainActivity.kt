package com.example

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.data.BrowserProfile
import com.example.data.ProfileBookmark
import com.example.data.ProfileHistory
import com.example.ui.BrowserViewModel
import com.example.ui.theme.*
import com.example.util.FingerprintSpoofer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()
    private var currentWebView: java.lang.ref.WeakReference<WebView>? = null

    fun registerWebView(webView: WebView) {
        currentWebView = java.lang.ref.WeakReference(webView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainBrowserScreen(viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val active = viewModel.activeProfile.value
        if (active != null) {
            val url = viewModel.currentUrlText.value
            viewModel.saveProfileStateOffline(active.id, url)
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            val active = viewModel.activeProfile.value
            if (active != null) {
                val webView = currentWebView?.get()
                if (webView != null) {
                    if (webView.canGoBack()) {
                        webView.goBack()
                        return true
                    } else {
                        webView.evaluateJavascript("(function(){try{return JSON.stringify(localStorage);}catch(e){return '{}';}})()") { result ->
                            val cleanJson = sanitizeJsStringResult(result)
                            viewModel.closeActiveProfile(currentUrl = viewModel.currentUrlText.value, currentLocalStorageJson = cleanJson)
                        }
                        return true
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainBrowserScreen(viewModel: BrowserViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val profiles by viewModel.allProfiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val history by viewModel.activeHistoryList.collectAsStateWithLifecycle()
    val bookmarks by viewModel.activeBookmarksList.collectAsStateWithLifecycle()
    
    val urlText by viewModel.currentUrlText.collectAsStateWithLifecycle()
    val isPageLoading by viewModel.isPageLoading.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.loadingProgress.collectAsStateWithLifecycle()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var showCreateProfileDialog by remember { mutableStateOf(false) }
    var showProtectionShieldDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }

    // Navigation trigger observer
    LaunchedEffect(key1 = true) {
        viewModel.navigationTrigger.collectLatest { targetUrl ->
            webViewInstance?.loadUrl(targetUrl)
        }
    }

    // Set user agent, language and start-of-document scripts whenever active Profile changes
    LaunchedEffect(activeProfile) {
        activeProfile?.let { p ->
            webViewInstance?.let { webView ->
                // User agent dynamic override
                val targetUa = FingerprintSpoofer.getProfileUserAgent(p)
                if (targetUa == "Default" || targetUa.isEmpty()) {
                    webView.settings.userAgentString = null // Resets to system default 100% genuine User Agent!
                } else {
                    webView.settings.userAgentString = targetUa
                }

                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    val script = FingerprintSpoofer.getSpoofingScript(p)
                    if (script.isNotEmpty()) {
                        // Allowed rules set for all origins to enable global antidetect spoofing
                        WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
                    }
                }
            }
        }
    }

    // Periodic Auto-Save (Every 8 seconds) for active profile cookies & localStorage
    LaunchedEffect(activeProfile) {
        if (activeProfile != null) {
            while (true) {
                kotlinx.coroutines.delay(8000)
                val currentUrl = viewModel.currentUrlText.value
                
                if (currentUrl.isNotEmpty()) {
                    viewModel.saveCurrentUrl(currentUrl)
                    viewModel.saveActiveCookiesExternal()
                    
                    webViewInstance?.let { webView ->
                        webView.evaluateJavascript("(function(){try{return JSON.stringify(localStorage);}catch(e){return '{}';}})()") { result ->
                            val cleanJson = sanitizeJsStringResult(result)
                            viewModel.saveActiveProfileLocalStorageForDomain(currentUrl, cleanJson)
                        }
                    }
                }
            }
        }
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = PremiumVoid,
                modifier = Modifier.width(320.dp)
            ) {
                DrawerProfilePanel(
                    profiles = profiles,
                    activeProfile = activeProfile,
                    onSelectProfile = { id ->
                        if (activeProfile?.id == id) {
                            scope.launch { drawerState.close() }
                        } else {
                            webViewInstance?.let { webView ->
                                webView.evaluateJavascript("(function(){try{return JSON.stringify(localStorage);}catch(e){return '{}';}})()") { result ->
                                    val cleanJson = sanitizeJsStringResult(result)
                                    viewModel.selectProfile(id, currentUrl = urlText, currentLocalStorageJson = cleanJson)
                                }
                            } ?: run {
                                viewModel.selectProfile(id)
                            }
                            scope.launch { drawerState.close() }
                        }
                    },
                    onCreateProfileClick = {
                        showCreateProfileDialog = true
                    },
                    onDeleteProfileClick = { profile ->
                        viewModel.deleteProfile(profile)
                    },
                    onCloseClick = {
                        scope.launch { drawerState.close() }
                    },
                    onClearCacheClick = {
                        viewModel.clearGlobalCache(context)
                    }
                )
            }
        }
    ) {
        val currentProfile = activeProfile
        if (currentProfile == null) {
            DashboardScreen(
                profiles = profiles,
                onSelectProfile = { id ->
                    viewModel.selectProfile(id)
                },
                onCreateProfileClick = {
                    showCreateProfileDialog = true
                },
                onOpenDrawerClick = {
                    scope.launch { drawerState.open() }
                },
                onDeleteProfile = { profile ->
                    viewModel.deleteProfile(profile)
                }
            )
        } else {
            BackHandler(enabled = true) {
                if (webViewInstance?.canGoBack() == true) {
                    webViewInstance?.goBack()
                } else {
                    webViewInstance?.let { webView ->
                        webView.evaluateJavascript("(function(){try{return JSON.stringify(localStorage);}catch(e){return '{}';}})()") { result ->
                            val cleanJson = sanitizeJsStringResult(result)
                            viewModel.closeActiveProfile(currentUrl = urlText, currentLocalStorageJson = cleanJson)
                        }
                    } ?: run {
                        viewModel.closeActiveProfile()
                    }
                }
            }
            Scaffold(
                topBar = {
                    Column(
                    modifier = Modifier
                        .background(PremiumVoid)
                        .statusBarsPadding()
                ) {
                    // Custom Glass-themed Address bar row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profiles list toggle button
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Perfiles de Navegación",
                                tint = AccentTeal
                            )
                        }

                        // Custom Address Search bar with nested profile badge and actions
                        var tempAddressText by remember { mutableStateOf("") }

                        LaunchedEffect(urlText) {
                            tempAddressText = urlText
                        }

                        OutlinedTextField(
                            value = tempAddressText,
                            onValueChange = { tempAddressText = it },
                            textStyle = LocalTextStyle.current.copy(
                                color = TextOffWhite,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            placeholder = { Text("Buscar o ingresar URL", color = TextMuted, fontSize = 12.sp) },
                            singleLine = true,
                            leadingIcon = {
                                activeProfile?.let { profile ->
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 8.dp, end = 2.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AccentTeal.copy(alpha = 0.15f))
                                            .border(1.dp, AccentTeal, RoundedCornerShape(6.dp))
                                            .clickable { showProtectionShieldDialog = true }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (profile.isIncognito) Icons.Default.Lock else Icons.Default.Language,
                                                contentDescription = null,
                                                tint = if (profile.isIncognito) GlowGreen else BrightCyan,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = profile.name,
                                                color = BrightCyan,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.widthIn(max = 65.dp)
                                            )
                                        }
                                    }
                                } ?: Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "URL status icon",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    if (tempAddressText.isNotEmpty()) {
                                        IconButton(
                                            onClick = { tempAddressText = "" },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear address bar",
                                                tint = TextMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.navigateTo(tempAddressText) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Navigate to Address",
                                            tint = BrightCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrightCyan,
                                unfocusedBorderColor = CardBackground,
                                focusedContainerColor = LightAccents,
                                unfocusedContainerColor = CardBackground,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            // Handle send key press or typing finish
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = {
                                    viewModel.navigateTo(tempAddressText)
                                }
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                            )
                        )
                    }

                    // Navigation Controller row (Back, Forward, Ref, Option tabs)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp, start = 8.dp, end = 8.dp),
                        horizontalArrangement = BoxArrangement(BoxAlignmentGrid)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { webViewInstance?.goBack() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Go Back", tint = TextOffWhite, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { webViewInstance?.goForward() }) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "Go Forward", tint = TextOffWhite, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { webViewInstance?.reload() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh Page", tint = TextOffWhite, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { webViewInstance?.loadUrl(activeProfile?.initialUrl ?: "https://www.google.com") }) {
                                Icon(Icons.Default.Home, contentDescription = "Homepage", tint = TextOffWhite, modifier = Modifier.size(16.dp))
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Bookmark toggle button
                            IconButton(
                                onClick = {
                                    val currentTitle = webViewInstance?.title ?: "Nueva Página"
                                    viewModel.toggleBookmark(currentTitle, urlText)
                                }
                            ) {
                                val isBookmarked = bookmarks.any { it.url == urlText }
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Toggle Bookmark",
                                    tint = if (isBookmarked) BrightCyan else TextOffWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Bookmarks List dialog trigger
                            IconButton(onClick = { showBookmarksDialog = true }) {
                                Icon(Icons.Default.Star, contentDescription = "Bookmarks List", tint = TextOffWhite, modifier = Modifier.size(18.dp))
                            }

                            // History dialog trigger
                            IconButton(onClick = { showHistoryDialog = true }) {
                                Icon(Icons.Default.History, contentDescription = "History Log", tint = TextOffWhite, modifier = Modifier.size(18.dp))
                            }

                            // Cookie extraction and copy button
                            IconButton(onClick = {
                                val activeUrl = (webViewInstance?.url ?: urlText).trim()
                                if (activeUrl.isNotEmpty()) {
                                    val cookieManager = android.webkit.CookieManager.getInstance()
                                    // Make sure we have latest cookies flushed
                                    cookieManager.flush()
                                    
                                    // Try different variants of the URL to get all cookies
                                    var cookies = cookieManager.getCookie(activeUrl)
                                    
                                    if (cookies.isNullOrEmpty()) {
                                        // Try with urlText if it's different
                                        if (urlText.isNotEmpty() && urlText != activeUrl) {
                                            cookies = cookieManager.getCookie(urlText)
                                        }
                                    }
                                    
                                    if (cookies.isNullOrEmpty()) {
                                        // Try with base domains
                                        try {
                                            val uri = android.net.Uri.parse(activeUrl)
                                            val host = uri.host
                                            if (host != null) {
                                                val scheme = uri.scheme ?: "https"
                                                cookies = cookieManager.getCookie("$scheme://$host")
                                                if (cookies.isNullOrEmpty() && host.startsWith("www.")) {
                                                    val nonWwwHost = host.removePrefix("www.")
                                                    cookies = cookieManager.getCookie("$scheme://$nonWwwHost")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("IncogNav", "Error parsing URI for cookies", e)
                                        }
                                    }
                                    
                                    if (!cookies.isNullOrEmpty()) {
                                        clipboardManager.setText(AnnotatedString(cookies))
                                        Toast.makeText(context, "Cookies copiadas al portapapeles 🍪", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No hay cookies para esta página", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Ninguna página cargada", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text(
                                    text = "🍪",
                                    fontSize = 18.sp
                                )
                            }
                        }
                    }

                    // Loading Progress Indicator
                    if (isPageLoading) {
                        LinearProgressIndicator(
                            progress = { loadingProgress.toFloat() / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = BrightCyan,
                            trackColor = Color.Transparent,
                        )
                    } else {
                        Divider(
                            color = CardBackground,
                            thickness = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            bottomBar = {},
            containerColor = PremiumVoid
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(PremiumVoid)
            ) {
                key(activeProfile?.id) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                
                                // Initialize WebView with Android Chrome native isolated profile if supported (MULTI_PROFILE)
                                if (activeProfile != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                                    try {
                                        val profileStore = ProfileStore.getInstance()
                                        val webProfile = profileStore.getOrCreateProfile("profile_${activeProfile!!.id}")
                                        WebViewCompat.setProfile(this, webProfile.name)
                                    } catch (e: Exception) {
                                        android.util.Log.e("IncogNav", "Error aplicando perfil nativo: ${e.message}", e)
                                    }
                                }
                                
                                setupWebViewConfigurations(this, viewModel, scope)
                                
                                // Set User-Agent and Start-of-Document scripting synchronously BEFORE loading URL
                                activeProfile?.let { p ->
                                    val targetUa = FingerprintSpoofer.getProfileUserAgent(p)
                                    if (targetUa == "Default" || targetUa.isEmpty()) {
                                        this.settings.userAgentString = null
                                    } else {
                                        this.settings.userAgentString = targetUa
                                    }
                                    
                                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                                        val script = FingerprintSpoofer.getSpoofingScript(p)
                                        if (script.isNotEmpty()) {
                                             WebViewCompat.addDocumentStartJavaScript(this, script, setOf("*"))
                                         }
                                    }
                                }

                                webViewInstance = this
                                (context as? MainActivity)?.registerWebView(this)
                                
                                // Load initial starting Url
                                activeProfile?.let {
                                    loadUrl(it.lastVisitedUrl)
                                } ?: loadUrl("https://www.google.com")
                            }
                        },
                        update = {
                            // Handled dynamically via triggers & launched effects to prevent reload on recreation
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
    }

    // Dialog components
    if (showCreateProfileDialog) {
        CreateProfileDialog(
            onDismiss = { showCreateProfileDialog = false },
            onCreate = { name, initialUrl, userAgentType, customUa, proxyType, proxyHost, proxyPort, proxyUser, proxyPass, isIncognito, canvasNoise, webglSpoof, platform, languages ->
                viewModel.createProfile(
                    name, initialUrl, userAgentType, customUa, proxyType, proxyHost, proxyPort, proxyUser, proxyPass, isIncognito, canvasNoise, webglSpoof, platform, languages
                )
                showCreateProfileDialog = false
            }
        )
    }

    if (showProtectionShieldDialog) {
        ProtectionShieldDialog(
            profile = activeProfile,
            onDismiss = { showProtectionShieldDialog = false },
            onUpdateSettings = { updated ->
                viewModel.updateActiveProfile(updated)
            },
            onResetSession = {
                viewModel.resetSession()
                showProtectionShieldDialog = false
            },
            onClearWebCache = {
                viewModel.clearGlobalCache(context)
                showProtectionShieldDialog = false
            }
        )
    }

    if (showHistoryDialog) {
        HistoryDialog(
            historyList = history,
            onDismiss = { showHistoryDialog = false },
            onNavigateTo = { url ->
                viewModel.navigateTo(url)
                showHistoryDialog = false
            },
            onClearHistory = {
                viewModel.clearActiveHistory()
            }
        )
    }

    if (showBookmarksDialog) {
        BookmarksDialog(
            bookmarksList = bookmarks,
            onDismiss = { showBookmarksDialog = false },
            onNavigateTo = { url ->
                viewModel.navigateTo(url)
                showBookmarksDialog = false
            },
            onDeleteBookmark = { id ->
                viewModel.deleteBookmark(id)
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun setupWebViewConfigurations(webView: WebView, viewModel: BrowserViewModel, scope: kotlinx.coroutines.CoroutineScope) {
    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

    // Zooming settings
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false

    // Cache settings
    settings.cacheMode = WebSettings.LOAD_DEFAULT

    // Explicitly configure cookie manager for this WebView instance
    val cookieManager = android.webkit.CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    webView.webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            viewModel.loadingProgress.value = newProgress
        }
    }

    webView.webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            viewModel.isPageLoading.value = true
            if (url != null) {
                viewModel.currentUrlText.value = url
                viewModel.recordVisitedDomain(url)
            }

            // Document start injection fallback
            viewModel.activeProfile.value?.let { p ->
                if (url != null) {
                    val domainUrl = viewModel.getDomainUrlOnly(url)
                    scope.launch {
                        val localDataJson = viewModel.getLocalStorage(p.id, domainUrl)
                        if (!localDataJson.isNullOrEmpty() && localDataJson != "{}") {
                            val storageScript = """
                                (function() {
                                    try {
                                        const localData = $localDataJson;
                                        for (const key in localData) {
                                            localStorage.setItem(key, localData[key]);
                                        }
                                    } catch (e) {
                                        console.error("localStorage preload injection failed", e);
                                    }
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(storageScript, null)
                        }
                    }
                }

                val script = FingerprintSpoofer.getSpoofingScript(p)
                if (script.isNotEmpty()) {
                    view?.evaluateJavascript(script, null)
                }
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            viewModel.isPageLoading.value = false
            if (url != null) {
                viewModel.currentUrlText.value = url
                viewModel.recordVisitedDomain(url)
                val title = view?.title ?: "Página Web"
                viewModel.recordHistory(title, url)

                // Inject finished-loading local storage check
                viewModel.activeProfile.value?.let { p ->
                    val domainUrl = viewModel.getDomainUrlOnly(url)
                    scope.launch {
                        val localDataJson = viewModel.getLocalStorage(p.id, domainUrl)
                        if (!localDataJson.isNullOrEmpty() && localDataJson != "{}") {
                            val storageScript = """
                                (function() {
                                    try {
                                        const localData = $localDataJson;
                                        for (const key in localData) {
                                            localStorage.setItem(key, localData[key]);
                                        }
                                    } catch (e) {
                                        console.error("localStorage postload injection failed", e);
                                    }
                                })();
                            """.trimIndent()
                                view?.evaluateJavascript(storageScript, null)
                        }
                    }
                }

                // Retrieve the updated localStorage and persist it
                view?.evaluateJavascript("(function(){try{return JSON.stringify(localStorage);}catch(e){return '{}';}})()") { result ->
                    val cleanJson = sanitizeJsStringResult(result)
                    viewModel.saveActiveProfileLocalStorageForDomain(url, cleanJson)
                }
            }
        }

        override fun onLoadResource(view: WebView?, url: String?) {
            super.onLoadResource(view, url)
            if (url != null) {
                viewModel.recordVisitedDomain(url)
            }
        }

        override fun onReceivedHttpAuthRequest(
            view: WebView?,
            handler: HttpAuthHandler?,
            host: String?,
            realm: String?
        ) {
            val active = viewModel.activeProfile.value
            if (active != null && active.proxyUser.isNotEmpty() && active.proxyPass.isNotEmpty()) {
                handler?.proceed(active.proxyUser, active.proxyPass)
            } else {
                super.onReceivedHttpAuthRequest(view, handler, host, realm)
            }
        }
    }
}

@Composable
fun DrawerProfilePanel(
    profiles: List<BrowserProfile>,
    activeProfile: BrowserProfile?,
    onSelectProfile: (Long) -> Unit,
    onCreateProfileClick: () -> Unit,
    onDeleteProfileClick: (BrowserProfile) -> Unit,
    onCloseClick: () -> Unit,
    onClearCacheClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PremiumVoid)
            .padding(16.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IncogNavLogo(modifier = Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "IncogNav",
                    color = ElegantLilac,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "ISOLATED BROWSER",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif
                )
            }

            IconButton(
                onClick = onCloseClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LightAccents)
            ) {
                Icon(
                    imageVector = Icons.Default.Close, 
                    contentDescription = "Cerrar", 
                    tint = TextOffWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onCreateProfileClick,
            colors = ButtonDefaults.buttonColors(containerColor = BrightCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Nuevo Perfil",
                tint = PremiumVoid,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Crear Nuevo Perfil",
                color = PremiumVoid,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Global Clear Cache & Purge button moved here for better space utilization
        Button(
            onClick = onClearCacheClick,
            colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BrightCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Limpiar Caché Global",
                tint = BrightCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Limpiar Caché Global",
                color = BrightCyan,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Perfiles de Navegación",
            color = TextOffWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(profiles) { profile ->
                val isActive = profile.id == activeProfile?.id
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isActive) LightAccents else CardBackground)
                        .border(
                            width = 1.dp,
                            color = if (isActive) BrightCyan else Color(0xFF49454F),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable { onSelectProfile(profile.id) }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF4A4458)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (profile.isIncognito) Icons.Default.Lock else Icons.Default.Language,
                                contentDescription = null,
                                tint = if (profile.isIncognito) Color(0xFFEFB8C8) else Color(0xFFD0BCFF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.name,
                                    color = TextOffWhite,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isActive) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(GlowGreen)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${profile.spoofedPlatform} • ${if (profile.proxyType == "DIRECT") "Direto" else "${profile.proxyType} Proxy"}",
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(if (isActive) Color(0xFF381E72) else Color(0xFF49454F))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isActive) "ACTIVE" else "STANDBY",
                                    color = if (isActive) Color(0xFFD0BCFF) else Color(0xFFCAC4D0),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            IconButton(
                                onClick = { onDeleteProfileClick(profile) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Borrar perfil",
                                    tint = Color(0xFFCAC4D0),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEADDFF).copy(alpha = 0.05f))
                    .border(1.dp, Color(0xFFEADDFF).copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "ISOLATION ENGINE",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "v2.4 Secure Hub",
                        color = BrightCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEADDFF).copy(alpha = 0.05f))
                    .border(1.dp, Color(0xFFEADDFF).copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "ACTIVE TUNNELS",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val activeProxiesCount = profiles.count { it.proxyType != "DIRECT" }
                    Text(
                        text = "$activeProxiesCount Global Tunnel" + (if (activeProxiesCount != 1) "s" else ""),
                        color = BrightCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))



        // Active Profile Connection / Antidetect details card inside drawer
        activeProfile?.let { active ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEADDFF).copy(alpha = 0.05f))
                    .border(1.dp, BrightCyan.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "ESTADO DE PERFIL ACTIVO",
                        color = BrightCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (active.proxyType != "DIRECT") GlowGreen else TextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (active.proxyType != "DIRECT") {
                                "Conectado vía ${active.proxyType}: ${active.proxyHost}"
                            } else "Conexión Directa (Sin Proxy)",
                            color = TextOffWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (active.canvasNoiseEnabled) GlowGreen else TextMuted)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Protección Antidetect: " + (if (active.canvasNoiseEnabled) "Completa" else "Básica"),
                            color = TextOffWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Ambient Dark/Light Theme Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardBackground)
                .border(1.dp, CardBackground, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (com.example.ui.theme.ThemeState.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = "Tema",
                    tint = BrightCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Tema Oscuro / Claro",
                    color = TextOffWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Switch(
                checked = com.example.ui.theme.ThemeState.isDarkMode,
                onCheckedChange = { com.example.ui.theme.ThemeState.isDarkMode = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BrightCyan,
                    checkedTrackColor = BrightCyan.copy(alpha = 0.4f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = LightAccents
                )
            )
        }
    }
}

// Complete profile builder form dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (
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
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var initialUrl by remember { mutableStateOf("https://www.google.com") }
    var userAgentType by remember { mutableStateOf("Default") }
    var customUa by remember { mutableStateOf("") }
    var proxyType by remember { mutableStateOf("DIRECT") }
    var proxyHost by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("") }
    var proxyUser by remember { mutableStateOf("") }
    var proxyPass by remember { mutableStateOf("") }
    var isIncognito by remember { mutableStateOf(false) }
    var canvasNoise by remember { mutableStateOf(false) }
    var webglSpoof by remember { mutableStateOf(false) }
    var platform by remember { mutableStateOf("Default") }
    var languages by remember { mutableStateOf("Default") }

    val userAgentOptions = listOf("Default", "Chrome Mobile", "Safari iPhone", "Firefox Mobile", "Edge Mobile", "Chrome Desktop", "Safari Mac", "Custom")
    val proxyOptions = listOf("DIRECT", "HTTP", "HTTPS", "SOCKS4", "SOCKS5")
    val platformOptions = listOf("Default", "Win32", "MacIntel", "iPhone", "Linux armv8l")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Perfil de Navegación", color = BrightCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Column {
                        Text("Nombre del perfil *", color = TextOffWhite, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text("Ej. Juan Spotify") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrightCyan, unfocusedBorderColor = TextMuted),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Column {
                        Text("URL Inicial", color = TextOffWhite, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = initialUrl,
                            onValueChange = { initialUrl = it },
                            placeholder = { Text("https://www.google.com") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrightCyan, unfocusedBorderColor = TextMuted),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Column {
                        Text("User Agent Presets", color = TextOffWhite, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = LightAccents),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$userAgentType ▾", color = BrightCyan)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(PremiumVoid)
                            ) {
                                userAgentOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = TextOffWhite) },
                                        onClick = {
                                            userAgentType = option
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (userAgentType == "Custom") {
                    item {
                        Column {
                            Text("User Agent String Personalizado", color = TextOffWhite, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                            OutlinedTextField(
                                value = customUa,
                                onValueChange = { customUa = it },
                                placeholder = { Text("Mozilla/5.0 ...") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrightCyan, unfocusedBorderColor = TextMuted),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Modo Incógnito Integrado", color = TextOffWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("No graba historial y limpia al salir", color = TextMuted, fontSize = 10.sp)
                        }
                        Switch(
                            checked = isIncognito,
                            onCheckedChange = { isIncognito = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = GlowGreen)
                        )
                    }
                }

                item {
                    Text("Configuraciones de Proxy", color = BrightCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                item {
                    Column {
                        Text("Tipo de Proxy", color = TextOffWhite, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = LightAccents),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$proxyType ▾", color = BrightCyan)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(PremiumVoid)
                            ) {
                                proxyOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = TextOffWhite) },
                                        onClick = {
                                            proxyType = option
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (proxyType != "DIRECT") {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(2f)) {
                                Text("Host / IP", color = TextOffWhite, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                                OutlinedTextField(
                                    value = proxyHost,
                                    onValueChange = { proxyHost = it },
                                    placeholder = { Text("12.34.56.78") },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrightCyan, unfocusedBorderColor = TextMuted),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Puerto", color = TextOffWhite, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                                OutlinedTextField(
                                    value = proxyPort,
                                    onValueChange = { proxyPort = it },
                                    placeholder = { Text("8080") },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrightCyan, unfocusedBorderColor = TextMuted),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Usuario Proxy (Opcional)", color = TextOffWhite, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                                OutlinedTextField(
                                    value = proxyUser,
                                    onValueChange = { proxyUser = it },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrightCyan, unfocusedBorderColor = TextMuted),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Clave Proxy (Opcional)", color = TextOffWhite, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                                OutlinedTextField(
                                    value = proxyPass,
                                    onValueChange = { proxyPass = it },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrightCyan, unfocusedBorderColor = TextMuted),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Protecciones Avanzadas (Antidetect)", color = BrightCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Canvas Ruido Invisible", color = TextOffWhite, fontSize = 12.sp)
                            Text("Evita rastreos por huella WebGL/Canvas", color = TextMuted, fontSize = 9.sp)
                        }
                        Switch(
                            checked = canvasNoise,
                            onCheckedChange = { canvasNoise = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = GlowGreen)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("WebGL Spoofing Inteligente", color = TextOffWhite, fontSize = 12.sp)
                            Text("Disfraza la tarjeta gráfica real", color = TextMuted, fontSize = 9.sp)
                        }
                        Switch(
                            checked = webglSpoof,
                            onCheckedChange = { webglSpoof = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = GlowGreen)
                        )
                    }
                }

                item {
                    Column {
                        Text("Spoofing de Plataforma", color = TextOffWhite, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = LightAccents),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$platform ▾", color = BrightCyan)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(PremiumVoid)
                            ) {
                                platformOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = TextOffWhite) },
                                        onClick = {
                                            platform = option
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text("Idiomas del Navegador (Spoof)", color = TextOffWhite, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = languages,
                            onValueChange = { languages = it },
                            placeholder = { Text("Default o ej: es-MX,es;q=0.9") },
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrightCyan, unfocusedBorderColor = TextMuted),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isNotEmpty()) {
                        onCreate(
                            name.trim(), initialUrl.trim(), userAgentType, customUa.trim(), proxyType, proxyHost.trim(), proxyPort.trim(), proxyUser.trim(), proxyPass, isIncognito, canvasNoise, webglSpoof, platform, languages.trim()
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                enabled = name.trim().isNotEmpty()
            ) {
                Text("Crear", color = PremiumVoid, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextMuted)
            }
        },
        containerColor = PremiumVoid
    )
}

// Metricas de proteccion y configuracion dialog
@Composable
fun ProtectionShieldDialog(
    profile: BrowserProfile?,
    onDismiss: () -> Unit,
    onUpdateSettings: (BrowserProfile) -> Unit,
    onResetSession: () -> Unit,
    onClearWebCache: () -> Unit
) {
    if (profile == null) return

    var canvasNoise by remember { mutableStateOf(profile.canvasNoiseEnabled) }
    var webglSpoof by remember { mutableStateOf(profile.webGlSpoofEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = GlowGreen, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Escudo de Protección", color = BrightCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                     text = "Ajustes de privacidad y protecciones activas para el perfil '${profile.name}'",
                     color = TextOffWhite,
                     fontSize = 12.sp
                )

                // Canvas toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ruido de firma Canvas", color = TextOffWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Enmascara el Canvas Fingerprint inyectando píxeles invisibles aleatorios.", color = TextMuted, fontSize = 10.sp)
                    }
                    Switch(
                        checked = canvasNoise,
                        onCheckedChange = {
                            canvasNoise = it
                            onUpdateSettings(profile.copy(canvasNoiseEnabled = it))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = GlowGreen)
                    )
                }

                // WebGL toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Spoofing WebGL GPU", color = TextOffWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Devuelve un hardware de tarjeta gráfica falso de PC/Apple para aislar la firma.", color = TextMuted, fontSize = 10.sp)
                    }
                    Switch(
                        checked = webglSpoof,
                        onCheckedChange = {
                            webglSpoof = it
                            onUpdateSettings(profile.copy(webGlSpoofEnabled = it))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = GlowGreen)
                    )
                }

                Divider(color = CardBackground)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardBackground)
                        .padding(10.dp)
                ) {
                    Column {
                        Text("FINGERPRINT SIMULATION", color = BrightCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("UserAgent: ${FingerprintSpoofer.getProfileUserAgent(profile)}", color = TextOffWhite, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Plataforma: ${FingerprintSpoofer.getProfilePlatform(profile)}", color = TextOffWhite, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Idiomas: ${profile.spoofedLanguages}", color = TextOffWhite, fontSize = 10.sp)
                    }
                }
                
                Button(
                    onClick = onResetSession,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reiniciar cookies y sesión", color = Color.White)
                }

                Button(
                    onClick = onClearWebCache,
                    colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DangerRed.copy(0.4f), RoundedCornerShape(100.dp))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = DangerRed)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Limpiar caché de navegación", color = TextOffWhite)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)) {
                Text("Listo", color = PremiumVoid, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = PremiumVoid
    )
}

@Composable
fun HistoryDialog(
    historyList: List<ProfileHistory>,
    onDismiss: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historial de Navegación", color = BrightCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (historyList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        Text("No hay historial guardado en este perfil", color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(historyList) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardBackground)
                                    .clickable { onNavigateTo(item.url) }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = item.title, color = TextOffWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(text = item.url, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = BrightCyan, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    
                    TextButton(
                        onClick = onClearHistory,
                        colors = ButtonDefaults.textButtonColors(contentColor = DangerRed)
                    ) {
                        Text("Limpiar todo el historial")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)) {
                Text("Cerrar", color = PremiumVoid, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = PremiumVoid
    )
}

@Composable
fun BookmarksDialog(
    bookmarksList: List<ProfileBookmark>,
    onDismiss: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marcadores Guardados", color = BrightCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            if (bookmarksList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text("No hay marcadores guardados", color = TextMuted)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookmarksList) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardBackground)
                                .clickable { onNavigateTo(item.url) }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.title, color = TextOffWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = item.url, color = TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            
                            IconButton(onClick = { onDeleteBookmark(item.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = DangerRed.copy(0.7f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)) {
                Text("Cerrar", color = PremiumVoid, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = PremiumVoid
    )
}

// Custom Grid align helper for bottom navigation icons layout
@Composable
private fun BoxArrangement(gridAlign: Arrangement.Horizontal): Arrangement.Horizontal = gridAlign

private val BoxAlignmentGrid = Arrangement.SpaceBetween

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Composable
fun IncogNavLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        // Draw detective hat brim
        val hatPath = Path().apply {
            moveTo(w * 0.15f, h * 0.53f)
            quadraticTo(w * 0.5f, h * 0.48f, w * 0.85f, h * 0.53f)
            quadraticTo(w * 0.85f, h * 0.58f, w * 0.5f, h * 0.54f)
            quadraticTo(w * 0.15f, h * 0.58f, w * 0.15f, h * 0.53f)
            close()
        }
        drawPath(hatPath, color = Color.White)
        
        // Draw hat crown
        val crownPath = Path().apply {
            moveTo(w * 0.32f, h * 0.5f)
            lineTo(w * 0.28f, h * 0.25f)
            quadraticTo(w * 0.5f, h * 0.20f, w * 0.72f, h * 0.25f)
            lineTo(w * 0.68f, h * 0.5f)
            close()
        }
        drawPath(crownPath, color = Color.White)
        
        // Draw hat ribbon (glowing lavender color)
        val ribbonPath = Path().apply {
            moveTo(w * 0.305f, h * 0.45f)
            lineTo(w * 0.29f, h * 0.38f)
            quadraticTo(w * 0.5f, h * 0.35f, w * 0.71f, h * 0.38f)
            lineTo(w * 0.695f, h * 0.45f)
            close()
        }
        drawPath(ribbonPath, color = Color(0xFFD0BCFF))
        
        // Sunglasses (Lenses & Bridge)
        val lensY = h * 0.68f
        val lensRadius = w * 0.12f
        val leftLensCenterX = w * 0.36f
        val rightLensCenterX = w * 0.64f
        
        // Left Lens
        drawCircle(
            color = Color.White,
            radius = lensRadius,
            center = androidx.compose.ui.geometry.Offset(leftLensCenterX, lensY)
        )
        // Right Lens
        drawCircle(
            color = Color.White,
            radius = lensRadius,
            center = androidx.compose.ui.geometry.Offset(rightLensCenterX, lensY)
        )
        
        // Sunglasses Bridge
        val bridgePath = Path().apply {
            moveTo(leftLensCenterX + lensRadius * 0.6f, lensY - lensRadius * 0.2f)
            quadraticTo(w * 0.5f, lensY - lensRadius * 0.5f, rightLensCenterX - lensRadius * 0.6f, lensY - lensRadius * 0.2f)
            lineTo(rightLensCenterX - lensRadius * 0.6f, lensY + lensRadius * 0.1f)
            quadraticTo(w * 0.5f, lensY - lensRadius * 0.2f, leftLensCenterX + lensRadius * 0.6f, lensY + lensRadius * 0.1f)
            close()
        }
        drawPath(bridgePath, color = Color.White)
    }
}

fun sanitizeJsStringResult(jsResult: String?): String {
    if (jsResult == null || jsResult == "null") return "{}"
    var s = jsResult.trim()
    if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
        s = s.substring(1, s.length - 1)
        // unescape backslashes and quotes
        s = s.replace("\\\"", "\"").replace("\\\\", "\\")
    }
    return if (s.startsWith("{") && s.endsWith("}")) s else "{}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    profiles: List<com.example.data.BrowserProfile>,
    onSelectProfile: (Long) -> Unit,
    onCreateProfileClick: () -> Unit,
    onOpenDrawerClick: () -> Unit,
    onDeleteProfile: (com.example.data.BrowserProfile) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IncogNavLogo(modifier = Modifier.size(36.dp))
                        Column {
                            Text(
                                text = "IncogNav",
                                color = ElegantLilac,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif
                            )
                            Text(
                                text = "CENTRO DE CONTROL",
                                color = TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontFamily = FontFamily.SansSerif
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawerClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Abrir Menú",
                            tint = AccentTeal
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onCreateProfileClick,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentTeal.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Crear Perfil",
                            tint = BrightCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PremiumVoid,
                    titleContentColor = TextOffWhite
                )
            )
        },
        containerColor = PremiumVoid
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Hero Welcome Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF381E72), CardBackground)
                            )
                        )
                        .border(1.dp, Color(0xFFEADDFF).copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(BrightCyan.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(GlowGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ENTORNO SEGURO Y DESACOPLADO",
                                    color = BrightCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Navegación Antidetect Multicuenta",
                            color = TextOffWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "Aísla de manera absoluta cookies, localStorage, huella WebGL y User-Agent por cada perfil. Las conexiones e inicios no se autoactivan para ahorrar datos móviles y acelerar el inicio del aplicativo.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Stats row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Profile count card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBackground)
                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PERFILES",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${profiles.size}",
                                color = TextOffWhite,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Active proxies card
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBackground)
                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TÚNELES PROXY",
                                    color = TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = BrightCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            val activeProxies = profiles.count { it.proxyType != "DIRECT" }
                            Text(
                                text = "$activeProxies Activo" + (if (activeProxies != 1) "s" else ""),
                                color = TextOffWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Connection guidelines title
            item {
                Text(
                    text = "Tus Perfiles Disponibles",
                    color = ElegantLilac,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            // Profiles list logic
            if (profiles.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBackground)
                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(20.dp))
                            .clickable { onCreateProfileClick() }
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Sin perfiles todavía",
                                color = TextOffWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Toca aquí para crear tu primer perfil y comenzar a navegar de forma aislada.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 16.sp
                            )
                            Button(
                                onClick = onCreateProfileClick,
                                colors = ButtonDefaults.buttonColors(containerColor = BrightCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentElectric)
                                    Text("Crear Perfil", color = AccentElectric, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                items(profiles) { profile ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(CardBackground)
                            .border(1.dp, Color(0xFF49454F), RoundedCornerShape(20.dp))
                            .clickable { onSelectProfile(profile.id) }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF4A4458)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (profile.isIncognito) Icons.Default.Lock else Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (profile.isIncognito) Color(0xFFEFB8C8) else Color(0xFFD0BCFF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    text = profile.name,
                                    color = TextOffWhite,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${profile.spoofedPlatform} • ${if (profile.proxyType == "DIRECT") "Conexión Directa" else "${profile.proxyType} Proxied"}",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (profile.lastVisitedUrl.isNotEmpty() && profile.lastVisitedUrl != profile.initialUrl) {
                                    Text(
                                        text = "Última: ${profile.lastVisitedUrl}",
                                        color = BrightCyan.copy(alpha = 0.8f),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            
                            IconButton(
                                onClick = { onDeleteProfile(profile) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Borrar perfil",
                                    tint = DangerRed.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}