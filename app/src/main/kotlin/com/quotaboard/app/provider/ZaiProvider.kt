package com.quotaboard.app.provider

import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.PayAsYouGo
import com.quotaboard.app.data.ProviderSupport
import com.quotaboard.app.data.QuotaSnapshot
import com.quotaboard.app.data.QuotaWindow
import com.quotaboard.app.data.WindowKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class ZaiProvider : QuotaProvider {
    override val id = "zai"
    override val displayName = "智谱 GLM Coding Plan"
    override val authMethods = listOf(AuthMethod.API_KEY)
    override val supportDescription = listOf(
        ProviderSupport("订阅额度（GLM Coding Plan 窗口）", supported = true, note = "官方 quota/limit 接口（国内 open.bigmodel.cn / 国际 api.z.ai）"),
        ProviderSupport("按量余额", supported = false, note = "非 Coding Plan 的余额接口未公开，暂不支持查询"),
    )

    override suspend fun fetch(account: Account, secrets: AccountSecrets): QuotaSnapshot {
        val key = secrets.apiKey?.takeIf { it.isNotBlank() } ?: throw ProviderException("请填写 GLM / Z.ai API Key")
        val host = account.extra["host"]?.takeIf { it.isNotBlank() } ?: "https://api.z.ai"
        val base = if (host.startsWith("http")) host else "https://$host"

        val body = Http.get(
            "$base/api/monitor/usage/quota/limit",
            mapOf("Authorization" to "Bearer $key", "accept" to "application/json"),
        )
        val root = Http.parseObject(body)
        if (root.longValue("code") != null && root.longValue("code") != 200L) {
            throw ProviderException("接口返回错误：${root.stringValue("msg") ?: body.take(160)}")
        }
        val data = root.obj("data")
            ?: throw ProviderException("响应格式无法解析：${body.take(200)}")
        val windows = mutableListOf<QuotaWindow>()
        var planName: String? = null
        for (cand in listOf("planName", "plan", "plan_type", "packageName", "level")) {
            val v = data.stringValue(cand)
            if (v != null) {
                planName = v
                break
            }
        }
        data.arr("limits")?.forEach { el ->
            val limit = el as? JsonObject ?: return@forEach
            val type = limit.stringValue("type") ?: return@forEach
            if (type != "TOKENS_LIMIT" && type != "CREDIT_LIMIT" && type != "TIME_LIMIT") return@forEach
            var percent = limit.doubleValue("percentage")
            val usageLimit = limit.doubleValue("usage")
            val current = limit.doubleValue("currentValue")
            val remaining = limit.doubleValue("remaining")
            if (percent == null && usageLimit != null && usageLimit > 0) {
                val used = current ?: (usageLimit - (remaining ?: 0.0))
                percent = (used / usageLimit * 100.0).coerceIn(0.0, 100.0)
            }
            if (percent == null) return@forEach
            val kind = windowKind(limit)
            windows.add(
                QuotaWindow(
                    kind = kind,
                    usedPercent = percent,
                    usedAmount = current,
                    limitAmount = usageLimit,
                    resetsAtMs = limit.doubleValue("nextResetTime")?.toLong()?.takeIf { it > 0 },
                    status = type,
                )
            )
        }
        if (windows.isEmpty()) {
            throw ProviderException(
                "无法从响应解析 Coding Plan 额度（可能该 Key 无 Coding Plan 或格式变化）：${body.take(160)}"
            )
        }
        return QuotaSnapshot(
            windows = windows,
            fetchedAtMs = System.currentTimeMillis(),
        )
    }

    private fun windowKind(limit: JsonObject): WindowKind {
        val duration = limit.obj("duration") ?: return WindowKind.MONTHLY
        val unit = duration.stringValue("unit")?.lowercase() ?: return WindowKind.MONTHLY
        val number = duration.doubleValue("number")?.toInt() ?: return WindowKind.MONTHLY
        return when {
            unit.startsWith("hour") && number == 5 -> WindowKind.FIVE_HOUR
            unit.startsWith("hour") && number < 24 -> WindowKind.SESSION
            unit.startsWith("day") && number == 7 -> WindowKind.WEEKLY
            else -> WindowKind.MONTHLY
        }
    }
}
