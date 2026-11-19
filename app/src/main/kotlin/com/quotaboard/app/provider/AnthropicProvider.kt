package com.quotaboard.app.provider

import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.PayAsYouGo
import com.quotaboard.app.data.ProviderSupport
import com.quotaboard.app.data.QuotaSnapshot
import com.quotaboard.app.data.QuotaWindow
import com.quotaboard.app.data.WindowKind
import kotlinx.serialization.json.jsonObject

class AnthropicProvider : QuotaProvider {
    override val id = "claude"
    override val displayName = "Claude"
    override val authMethods = listOf(AuthMethod.OAUTH, AuthMethod.API_KEY)
    override val oauthConfig = OAuthConfig(
        authorizeUrl = "https://console.anthropic.com/oauth/authorize",
        tokenUrl = "https://console.anthropic.com/v1/oauth/token",
        clientId = "9d1c250a-e61b-44d9-88ed-5944d196456e",
        redirectUri = "http://localhost:9871/callback",
        scope = "profile user:inference",
    )

    override val supportDescription = listOf(
        ProviderSupport("订阅额度（Pro/Max 会话窗口与周额度）", supported = true, note = "OAuth 登录后查询官方用量接口"),
        ProviderSupport("按量余额", supported = false, note = "Anthropic 未提供普通 API Key 的余额查询接口，暂不支持查询"),
    )

    override suspend fun fetch(account: Account, secrets: AccountSecrets): QuotaSnapshot {
        val unsupported = mutableListOf<String>()
        val token = secrets.accessToken
        if (token.isNullOrBlank()) {
            if (!secrets.apiKey.isNullOrBlank()) {
                val ok = try {
                    Http.get(
                        "https://api.anthropic.com/v1/models",
                        mapOf("x-api-key" to secrets.apiKey!!, "anthropic-version" to "2023-06-01"),
                    )
                    true
                } catch (e: ProviderException) {
                    throw ProviderException("API Key 校验失败：${e.message}")
                }
                if (ok) {
                    unsupported.add("该 Key 可用；Anthropic 暂不支持普通 API Key 查询额度/余额")
                    return QuotaSnapshot(unsupportedNotes = unsupported, fetchedAtMs = System.currentTimeMillis())
                }
            }
            throw ProviderException("缺少 OAuth 凭证")
        }

        val body = Http.get(
            "https://api.anthropic.com/api/oauth/usage?cedar_ember=1",
            mapOf(
                "Authorization" to "Bearer $token",
                "anthropic-beta" to "oauth-2025-04-20",
            ),
        )
        val root = Http.parseObject(body)
        val windows = mutableListOf<QuotaWindow>()
        root.obj("five_hour")?.let { w ->
            parseWindow(w, WindowKind.FIVE_HOUR)?.let { windows.add(it) }
        }
        val weeklySource = root.obj("seven_day") ?: root.obj("seven_day_oauth_apps")
        weeklySource?.let { w ->
            parseWindow(w, WindowKind.WEEKLY)?.let { windows.add(it) }
        }
        root.arr("limits")?.forEach { el ->
            val limitObj = el.jsonObject
            val scoped = limitObj.obj("weekly_scoped") ?: return@forEach
            val label = limitObj.stringValue("scope") ?: ""
            val w = parseWindow(scoped, WindowKind.WEEKLY)
            if (w != null) windows.add(w.copy(status = label.ifBlank { null }))
        }
        return QuotaSnapshot(windows = windows, fetchedAtMs = System.currentTimeMillis())
    }
}
