package com.quotaboard.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CredentialStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "quota_secrets",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun saveSecrets(accountId: String, secrets: AccountSecrets) {
        prefs.edit().putString("acct_$accountId", json.encodeToString(secrets)).apply()
    }

    fun getSecrets(accountId: String): AccountSecrets? =
        prefs.getString("acct_$accountId", null)?.let {
            runCatching { json.decodeFromString<AccountSecrets>(it) }.getOrNull()
        }

    fun deleteSecrets(accountId: String) {
        prefs.edit().remove("acct_$accountId").apply()
    }

    fun saveOAuthDraft(providerId: String, secrets: AccountSecrets) {
        prefs.edit().putString("oauth_draft_$providerId", json.encodeToString(secrets)).apply()
    }

    fun takeOAuthDraft(providerId: String): AccountSecrets? {
        val raw = prefs.getString("oauth_draft_$providerId", null) ?: return null
        prefs.edit().remove("oauth_draft_$providerId").apply()
        return runCatching { json.decodeFromString<AccountSecrets>(raw) }.getOrNull()
    }
}

class AccountStore(context: Context, private val json: Json = Json { ignoreUnknownKeys = true }) {
    private val prefs = context.getSharedPreferences("quota_accounts", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(Account.serializer())

    private val _accounts = MutableStateFlow(read())
    val accounts: StateFlow<List<Account>> = _accounts

    private fun read(): List<Account> =
        prefs.getString("accounts", null)?.let {
            runCatching { json.decodeFromString(serializer, it) }.getOrNull()
        } ?: emptyList()

    private fun persist(list: List<Account>) {
        prefs.edit().putString("accounts", json.encodeToString(serializer, list)).apply()
        _accounts.value = list
    }

    fun add(account: Account) = persist(read() + account)

    fun remove(id: String) = persist(read().filterNot { it.id == id })
}

class SnapshotStore(context: Context) {
    private val prefs = context.getSharedPreferences("quota_snapshots", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun save(accountId: String, snapshot: QuotaSnapshot, error: String?) {
        prefs.edit()
            .putString("snap_$accountId", json.encodeToString(snapshot))
            .apply()
        saveError(accountId, error)
    }

    fun saveError(accountId: String, error: String?) {
        prefs.edit()
            .putString("err_$accountId", error)
            .apply()
    }

    fun snapshot(accountId: String): QuotaSnapshot? =
        prefs.getString("snap_$accountId", null)?.let {
            runCatching { json.decodeFromString<QuotaSnapshot>(it) }.getOrNull()
        }

    fun error(accountId: String): String? = prefs.getString("err_$accountId", null)

    fun remove(accountId: String) {
        prefs.edit().remove("snap_$accountId").remove("err_$accountId").apply()
    }
}

class LedgerStore(context: Context) {
    private val prefs = context.getSharedPreferences("quota_ledger", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(LedgerEntry.serializer())

    fun record(accountId: String, entry: LedgerEntry) {
        val list = entries(accountId).toMutableList()
        val last = list.lastOrNull()
        if (last != null && last.tsMs == entry.tsMs) return
        list.add(entry)
        while (list.size > 600) list.removeAt(0)
        prefs.edit().putString("ledger_$accountId", json.encodeToString(serializer, list)).apply()
    }

    fun entries(accountId: String): List<LedgerEntry> =
        prefs.getString("ledger_$accountId", null)?.let {
            runCatching { json.decodeFromString(serializer, it) }.getOrNull()
        } ?: emptyList()

    fun remove(accountId: String) {
        prefs.edit().remove("ledger_$accountId").apply()
    }

    companion object {
        fun spendSince(entries: List<LedgerEntry>, sinceMs: Long): Double? {
            if (entries.size < 2) return null
            var spend = 0.0
            var seen = false
            for (i in 1 until entries.size) {
                val prev = entries[i - 1]
                val cur = entries[i]
                if (cur.tsMs < sinceMs) continue
                val delta = cur.balance - prev.balance
                if (delta < 0) spend += -delta
                seen = true
            }
            return if (seen) spend else null
        }
    }
}
