package com.quotaboard.app.provider

import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.ProviderSupport
import com.quotaboard.app.data.QuotaSnapshot
import com.quotaboard.app.data.QuotaWindow
import com.quotaboard.app.data.WindowKind

class CodexProvider : QuotaProvider {
    override val id = "codex"
    override val displayName = "Codex (ChatGPT)"
    override val authMethods = listOf(AuthMethod.OAUTH, AuthMethod.API_KEY)
    override val oauthConfig = OAuthConfig(
        authorizeUrl = "https://auth.openai.com/oauth/authorize",
        tokenUrl = "https://auth.openai.com/oauth/token",
        clientId = "app_EMoamEEZ73f0CkXaXp7hrann",
        redirectUri = "http://localhost:1455/auth/callback",
        scope = "openid profile email offline_access",
    )

    override val supportDescription = listOf(
        ProviderSupport("订阅额度（ChatGPT Plus/Pro 主窗口与次级窗口）", supported = true, note = "OAuth 登录后查询官方用量接口"),
        ProviderSupport("按量余额", supported = false, note = "OpenAI Platform 余额需要专门的账单密钥，暂不支持查询"),
    )

    override suspend fun fetch(account: Account, secrets: AccountSecrets): QuotaSnapshot {
        val unsupported = mutableListOf<String>()
        val token = secrets.accessToken
        if (token.isNullOrBlank()) {
            if (!secrets.apiKey.isNullOrBlank()) {
                val ok = try {
                    Http.get(
                        "https://api.openai.com/v1/models",
                        mapOf("Authorization" to "Bearer ${secrets.apiKey}"),
                    )
                    true
                } catch (e: ProviderException) {
                    throw ProviderException("API Key 校验失败：${e.message}")
                }
                if (ok) {
                    unsupported.add("该 Key 可用；OpenAI 暂不支持普通 sk- Key 查询订阅额度/余额")
                    return QuotaSnapshot(unsupportedNotes = unsupported, fetchedAtMs = System.currentTimeMillis())
                }
            }
            throw ProviderException("缺少 OAuth 凭证")
        }

        val headers = buildMap {
            put("Authorization", "Bearer $token")
            put("User-Agent", "QuotaBoard/0.1 (Android)")
            secrets.accountId?.let { put("chatgpt-account-id", it) }
        }
        val body = Http.get("https://chatgpt.com/backend-api/wham/usage", headers)
        val root = Http.parseObject(body)
        val windows = mutableListOf<QuotaWindow>()
        root.obj("rate_limit")?.obj("primary_window")?.let { w ->
            val parsed = parseWindow(
                w, WindowKind.SESSION,
                keysReset = listOf("resets_at", "resetsAt", "reset_at"),
            )
            if (parsed?.usedPercent != null) windows.add(parsed)
        }
        root.obj("rate_limit")?.obj("secondary_window")?.let { w ->
            val parsed = parseWindow(w, WindowKind.WEEKLY)
            if (parsed?.usedPercent != null) windows.add(parsed)
        }
        if (windows.isEmpty()) {
            throw ProviderException("无法从响应解析用量窗口：${body.take(200)}")
        }
        return QuotaSnapshot(windows = windows, fetchedAtMs = System.currentTimeMillis())
    }
}
