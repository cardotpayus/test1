package com.quotaboard.app

import android.app.Application
import com.quotaboard.app.data.AccountStore
import com.quotaboard.app.data.CredentialStore
import com.quotaboard.app.data.LedgerStore
import com.quotaboard.app.data.SnapshotStore

class QuotaApp : Application() {
    lateinit var credentialStore: CredentialStore
        private set
    lateinit var accountStore: AccountStore
        private set
    lateinit var snapshotStore: SnapshotStore
        private set
    lateinit var ledgerStore: LedgerStore
        private set

    override fun onCreate() {
        super.onCreate()
        credentialStore = CredentialStore(this)
        accountStore = AccountStore(this)
        snapshotStore = SnapshotStore(this)
        ledgerStore = LedgerStore(this)
    }
}
