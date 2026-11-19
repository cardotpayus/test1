package com.quotaboard.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quotaboard.app.QuotaApp
import com.quotaboard.app.data.Account
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AccountState
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.data.LedgerEntry
import com.quotaboard.app.data.Repository
import com.quotaboard.app.oauth.OAuthActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(val repo: Repository, private val app: QuotaApp) : ViewModel() {

    val accounts: StateFlow<List<Account>> = repo.accountsFlow
    val states: StateFlow<Map<String, AccountState>> = repo.states

    private val _demoMode = MutableStateFlow(false)
    val demoMode: StateFlow<Boolean> = _demoMode

    private val _globalRefreshing = MutableStateFlow(false)
    val globalRefreshing: StateFlow<Boolean> = _globalRefreshing

    val oauthStatus: StateFlow<Map<String, String>> = OAuthActivity.status

    init {
        viewModelScope.launch {
            repo.loadOnce()
        }
    }

    fun refreshAll() {
        if (_demoMode.value) return
        if (_globalRefreshing.value) return
        _globalRefreshing.value = true
        viewModelScope.launch {
            try {
                repo.refreshAll()
            } finally {
                _globalRefreshing.value = false
            }
        }
    }

    fun refreshOne(accountId: String) {
        if (_demoMode.value) return
        viewModelScope.launch { repo.refresh(accountId) }
    }

    fun toggleDemo() {
        _demoMode.value = !_demoMode.value
    }

    fun addAccount(
        providerId: String,
        label: String,
        authMethod: AuthMethod,
        secrets: AccountSecrets,
        extra: Map<String, String>,
        thenRefresh: Boolean = true,
    ): Account {
        val account = repo.addAccount(providerId, label, authMethod, secrets, extra)
        _demoMode.value = false
        if (thenRefresh) refreshOne(account.id)
        return account
    }

    fun takeOAuthDraft(providerId: String): AccountSecrets? =
        app.credentialStore.takeOAuthDraft(providerId)

    fun removeAccount(accountId: String) = repo.removeAccount(accountId)

    fun ledgerOf(accountId: String): List<LedgerEntry> = repo.ledgerOf(accountId)

    class Factory(private val app: QuotaApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(Repository(app.accountStore, app.credentialStore, app.snapshotStore, app.ledgerStore), app) as T
    }
}
