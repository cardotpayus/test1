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

        fun isRedirect(url: String): Boolean {
            val u = Uri.parse(url)
            if (u.scheme != redirectUri.scheme) return false
            if (redirectUri.host == "localhost") {
                if (u.host != "localhost" && u.host != "127.0.0.1") {
                    if (u.host != redirectUri.host) return false
                }
            } else if (u.host != redirectUri.host) return false
            return true
        }

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (!isRedirect(url)) return false
                    val code = OAuthService.extractCode(url, flow.state)
                    if (code != null) {
                        lifecycleScope.launch {
                            val result = try {
                                val secrets = OAuthService.exchangeCode(config, code, flow.verifier)
                                (application as QuotaApp).credentialStore.saveOAuthDraft(providerId, secrets)
                                "ok"
                            } catch (e: Exception) {
                                "登录失败：${e.message}"
                            }
                            status.value = status.value + mapOf(providerId to result)
                            finish()
                        }
                    } else {
                        lifecycleScope.launch {
                            status.value = status.value + mapOf(providerId to "回调缺少授权码或 state 不匹配")
                            finish()
                        }
                    }
                    return true
                }
            }
        }
        setContentView(webView)
        webView.loadUrl(flow.authorizeUrl)
    }
}
