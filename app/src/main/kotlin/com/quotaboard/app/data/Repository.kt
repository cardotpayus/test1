package com.quotaboard.app.data

import com.quotaboard.app.provider.ProviderRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class Repository(
    private val accounts: AccountStore,
    private val creds: CredentialStore,
    private val snapshots: SnapshotStore,
    private val ledger: LedgerStore,
) {
    private val _states = MutableStateFlow<Map<String, AccountState>>(emptyMap())
    val states: StateFlow<Map<String, AccountState>> = _states
    private val mutex = Mutex()
    private var loaded = false

    val accountsFlow: StateFlow<List<Account>> = accounts.accounts

    suspend fun loadOnce() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loaded) return@withLock
            val map = mutableMapOf<String, AccountState>()
            accounts.accounts.value.forEach { acct ->
                map[acct.id] = AccountState(
                    account = acct,
                    snapshot = snapshots.snapshot(acct.id),
                    lastError = snapshots.error(acct.id),
                )
            }
            _states.value = map
            loaded = true
        }
    }

    fun providerOf(account: Account) = ProviderRegistry.byId(account.providerId)

    suspend fun refresh(accountId: String) = withContext(Dispatchers.IO) {
        update(accountId) { st -> st.copy(refreshing = true) }
        val state = _states.value[accountId] ?: return@withContext
        val acct = state.account
        val provider = providerOf(acct) ?: run {
            update(accountId) { it.copy(refreshing = false, lastError = "未知的 Provider") }
            return@withContext
        }
        val secrets = creds.getSecrets(accountId)
        try {
            val snap = provider.fetch(acct, secrets ?: AccountSecrets())
            snapshots.save(accountId, snap, null)
            snap.payg?.let { p ->
                ledger.record(accountId, LedgerEntry(System.currentTimeMillis(), p.balanceAmount, p.currency))
            }
            update(accountId) {
                it.copy(snapshot = snap, lastError = null, updatedMs = System.currentTimeMillis(), refreshing = false)
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            snapshots.saveError(accountId, msg)
            update(accountId) { it.copy(lastError = msg, updatedMs = null, refreshing = false) }
        }
    }

    fun emptySnapshot(): QuotaSnapshot = QuotaSnapshot(fetchedAtMs = System.currentTimeMillis())

    suspend fun refreshAll() {
        loadOnce()
        accounts.accounts.value.forEach { refresh(it.id) }
    }

    fun addAccount(
        providerId: String,
        label: String,
        authMethod: AuthMethod,
        secrets: AccountSecrets,
        extra: Map<String, String> = emptyMap(),
    ): Account {
        val account = Account(
            id = UUID.randomUUID().toString(),
            providerId = providerId,
            label = label.ifBlank { ProviderRegistry.byId(providerId)?.displayName ?: providerId },
            authMethod = authMethod,
            extra = extra,
            createdAtMs = System.currentTimeMillis(),
        )
        accounts.add(account)
        creds.saveSecrets(account.id, secrets)
        _states.value = _states.value + mapOf(
            account.id to AccountState(account = account, refreshing = false)
        )
        return account
    }

    fun removeAccount(accountId: String) {
        accounts.remove(accountId)
        creds.deleteSecrets(accountId)
        snapshots.remove(accountId)
        ledger.remove(accountId)
        _states.value = _states.value - accountId
    }

    fun ledgerOf(accountId: String): List<LedgerEntry> = ledger.entries(accountId)

    private fun update(accountId: String, transform: (AccountState) -> AccountState) {
        _states.value = _states.value.toMutableMap().apply {
            get(accountId)?.let { put(accountId, transform(it)) }
        }
    }
}
