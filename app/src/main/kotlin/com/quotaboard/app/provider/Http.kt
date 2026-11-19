package com.quotaboard.app.provider

import com.quotaboard.app.data.QuotaWindow
import com.quotaboard.app.data.WindowKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object Http {
    val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String, headers: Map<String, String>): String = execute(
        Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
    )

    suspend fun postJson(url: String, headers: Map<String, String>, body: String): String = execute(
        Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
            .post(body.toRequestBody("application/json".toMediaType())).build()
    )

    suspend fun postForm(url: String, form: Map<String, String>): String {
        val formBody = form.entries.joinToString("&") {
            java.net.URLEncoder.encode(it.key, "UTF-8") + "=" +
                java.net.URLEncoder.encode(it.value, "UTF-8")
        }.toRequestBody("application/x-www-form-urlencoded".toMediaType())
        return execute(Request.Builder().url(url).post(formBody).build())
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        Http.client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ProviderException("HTTP ${resp.code}: ${text.take(240)}")
            text
        }
    }

    fun parseObject(text: String): JsonObject = json.parseToJsonElement(text).jsonObject
}

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
fun JsonPrimitive.doubleValue(): Double? = doubleOrNull ?: content.toDoubleOrNull()
fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: (this[key] as? JsonPrimitive)?.contentOrNull
fun JsonObject.doubleValue(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleValue()
fun JsonObject.longValue(key: String): Long? = doubleValue(key)?.toLong()

fun parseWindow(
    payload: JsonObject,
    kind: WindowKind,
    keysPercent: List<String> = listOf("used_percent", "percent", "utilization_percent", "usedPercent", "pct"),
    keysReset: List<String> = listOf("resets_at", "resetsAt", "reset_at", "resets_in_seconds", "resetsAtSeconds"),
    keysUsed: List<String> = listOf("used", "used_amount"),
    keysLimit: List<String> = listOf("limit", "limit_amount"),
): QuotaWindow? {
    var percent: Double? = null
    var used: Double? = null
    var limit: Double? = null
    var reset: Long? = null
    var resetIsEpochMs = true
    for ((k, v) in payload) {
        val p = v as? JsonPrimitive ?: continue
        when {
            keysPercent.any { it.equals(k, ignoreCase = true) } -> percent = p.doubleValue()
            keysUsed.any { it.equals(k, ignoreCase = true) } -> used = p.doubleValue()
            keysLimit.any { it.equals(k, ignoreCase = true) } -> limit = p.doubleValue()
            keysReset.any { it.equals(k, ignoreCase = true) } -> {
                if (k.contains("second", ignoreCase = true)) {
                    resetIsEpochMs = false
                    reset = p.doubleValue()?.toLong()
                } else {
                    reset = p.doubleValue()?.toLong()
                }
            }
        }
    }
    if (percent == null && used != null && limit != null && limit > 0) percent = used / limit * 100.0
    if (percent == null && used == null) return null
    return QuotaWindow(
        kind = kind,
        usedPercent = percent,
        usedAmount = used,
        limitAmount = limit,
        resetsAtMs = if (resetIsEpochMs) reset?.takeIf { it > 0 } else reset?.times(1000),
    )
}
