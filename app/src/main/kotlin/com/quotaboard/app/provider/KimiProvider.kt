package com.quotaboard.app.provider

import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.ProviderSupport
import com.quotaboard.app.data.QuotaSnapshot
import com.quotaboard.app.data.QuotaWindow
import com.quotaboard.app.data.WindowKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class KimiProvider : QuotaProvider {
    override val id = "kimi"
    override val displayName = "Kimi Code"
    override val authMethods = listOf(AuthMethod.API_KEY)
    override val supportDescription = listOf(
        ProviderSupport("订阅额度（Kimi for Coding 5 小时/周/月窗口）", supported = true, note = "官方 GET /coding/v1/usages 接口（国内 api.kimi.com；国际站 api.kimi.ai 需真机验证）"),
        ProviderSupport("按量 API 余额", supported = false, note = "Moonshot 平台余额无公开 Key 接口，暂不支持查询"),
    )

    override suspend fun fetch(account: Account, secrets: AccountSecrets): QuotaSnapshot {
        val key = secrets.apiKey?.takeIf { it.isNotBlank() } ?: throw ProviderException("请填写 Kimi Code API Key（kimi.com/code/console）")
        val unsupported = mutableListOf(
            "Moonshot 按量平台余额暂不支持查询"
        )
        val body = Http.get(
            "https://api.kimi.com/coding/v1/usages",
            mapOf("Authorization" to "Bearer $key"),
        )
        val root = Http.parseObject(body)
        val windows = mutableListOf<QuotaWindow>()

        fun walk(node: JsonObject, kindHint: String?) {
            val parsed = parseWindow(
                node, WindowKind.SESSION,
                keysPercent = listOf("percent", "used_percent", "usedPercent", "usage_percent", "pct"),
                keysReset = listOf("reset_at", "resetAt", "resets_at", "resetsAt", "reset_time"),
            )
            if (parsed != null) {
                val kind = kindHint?.let { hint ->
                    when {
                        hint.contains("5h") || hint.contains("five") || hint.contains("5_hour") -> WindowKind.FIVE_HOUR
                        hint.contains("week") -> WindowKind.WEEKLY
                        hint.contains("month") -> WindowKind.MONTHLY
                        else -> null
                    }
                } ?: WindowKind.SESSION
                windows.add(parsed.copy(kind = kind))
            }
            for ((k, v) in node) {
                when (v) {
                    is JsonObject -> walk(v, k)
                    is kotlinx.serialization.json.JsonArray -> v.forEach { el ->
                        if (el is JsonObject) walk(el, k)
                    }
                    else -> {}
                }
            }
        }

        walk(root, null)
        val dedup = windows.distinctBy { it.kind }
        if (dedup.isEmpty()) {
            throw ProviderException("无法从响应解析用量窗口：${body.take(200)}")
        }
        return QuotaSnapshot(
            windows = dedup,
            unsupportedNotes = unsupported,
            fetchedAtMs = System.currentTimeMillis(),
        )
    }
}
