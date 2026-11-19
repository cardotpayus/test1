package com.quotaboard.app.oauth

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.quotaboard.app.QuotaApp
import com.quotaboard.app.provider.ProviderRegistry
import kotlinx.coroutines.launch

class OAuthActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PROVIDER_ID = "provider_id"
        val status = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providerId = intent.getStringExtra(EXTRA_PROVIDER_ID) ?: run { finish(); return }
        val provider = ProviderRegistry.byId(providerId)
        val config = provider?.oauthConfig ?: run { finish(); return }

        val flow = OAuthService.buildAuthorizeUrl(config)
        val redirectUri = Uri.parse(config.redirectUri)
        val authorizeHost = Uri.parse(config.authorizeUrl).host

        fun isRedirect(uri: Uri): Boolean {
            if (!uri.scheme.isNullOrBlank() && uri.scheme != redirectUri.scheme) return false
            if (redirectUri.host == "localhost") {
                return uri.host == "localhost" || uri.host == "127.0.0.1"
            }
            return uri.host == redirectUri.host
        }

        fun isAllowed(uri: Uri): Boolean {
            val host = uri.host ?: return false
            return host == authorizeHost ||
                host.endsWith(".$authorizeHost") ||
                host.endsWith(".anthropic.com") ||
                host.endsWith(".openai.com") ||
                host.endsWith(".chatgpt.com") ||
                host.endsWith(".claude.ai")
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.allowFileAccessFromFileURLs = false
            settings.displayZoomControls = false
            settings.setSupportMultipleWindows(false)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val uri = request?.url ?: return false
                    val url = uri.toString()
                    if (isRedirect(uri)) {
                        val code = OAuthService.extractCode(url, flow.state)
                        lifecycleScope.launch {
                            val result = try {
                                if (code == null) {
                                    "回调缺少授权码或 state 不匹配"
                                } else {
                                    val secrets = OAuthService.exchangeCode(config, code, flow.verifier)
                                    (application as QuotaApp).credentialStore.saveOAuthDraft(providerId, secrets)
                                    "ok"
                                }
                            } catch (e: Exception) {
                                "登录失败：${e.message}"
                            }
                            status.value = status.value + mapOf(providerId to result)
                            finish()
                        }
                        return true
                    }
                    if (!isAllowed(uri)) return true
                    return false
                }
            }
        }
        setContentView(webView)
        webView.loadUrl(flow.authorizeUrl)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
