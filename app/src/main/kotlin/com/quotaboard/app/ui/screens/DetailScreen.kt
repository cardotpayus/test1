package com.quotaboard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quotaboard.app.data.LedgerStore
import com.quotaboard.app.ui.AppViewModel
import com.quotaboard.app.ui.formatMoney
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: AppViewModel,
    accountId: String,
    onBack: () -> Unit,
) {
    val accounts by vm.accounts.collectAsState()
    val states by vm.states.collectAsState()
    val account = accounts.firstOrNull { it.id == accountId }
    val state = states[accountId]
    val ledger = vm.ledgerOf(accountId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account?.label ?: "账号详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshOne(accountId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "额度窗口",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val windows = state?.snapshot?.windows.orEmpty()
            if (windows.isEmpty()) {
                item {
                    Text(
                        state?.lastError?.let { "查询失败：$it" } ?: "暂无窗口数据",
                        color = if (state?.lastError != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                windows.forEach { w ->
                    item {
                        Column(Modifier.fillMaxWidth()) {
                            MiniProgressRow(
                                label = w.kind.display + (w.status?.let { " · $it" } ?: ""),
                                percent = w.usedPercent,
                                resetsAt = w.resetsAtMs,
                            )
                            if (w.usedAmount != null && w.limitAmount != null) {
                                Text(
                                    "已用 ${w.usedAmount} / ${w.limitAmount}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            state?.snapshot?.payg?.let { p ->
                item {
                    Text(
                        "按量余额：${formatMoney(p.balanceAmount, p.currency)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            state?.snapshot?.unsupportedNotes?.takeIf { it.isNotEmpty() }?.let { notes ->
                item {
                    Column {
                        notes.forEach {
                            Text(
                                "· $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            val dayAgo = System.currentTimeMillis() - 24L * 3600 * 1000
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            val spend24 = LedgerStore.spendSince(ledger, dayAgo)
            val spend7d = LedgerStore.spendSince(ledger, weekAgo)
            if (spend24 != null || spend7d != null) {
                item {
                    Text(
                        "近期花销（按余额差值本地推算）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                spend24?.let {
                    item { Text("近 24 小时：约 -" + formatMoney(it, ledger.lastOrNull()?.currency ?: "USD")) }
                }
                spend7d?.let {
                    item { Text("近 7 天：约 -" + formatMoney(it, ledger.lastOrNull()?.currency ?: "USD")) }
                }
            }

            if (ledger.isNotEmpty()) {
                item {
                    Text(
                        "余额记录（${ledger.size} 条）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                ledger.takeLast(30).asReversed().forEach { e ->
                    item {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(e.tsMs)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatMoney(e.balance, e.currency),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            state?.updatedMs?.let {
                item {
                    Text(
                        "最近刷新：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
