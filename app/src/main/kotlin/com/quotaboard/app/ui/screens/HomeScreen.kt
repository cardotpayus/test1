package com.quotaboard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quotaboard.app.data.AccountState
import com.quotaboard.app.data.QuotaWindow
import com.quotaboard.app.data.WindowKind
import com.quotaboard.app.ui.AppViewModel
import com.quotaboard.app.ui.formatDuration
import com.quotaboard.app.ui.formatMoney
import com.quotaboard.app.ui.levelColor
import com.quotaboard.app.ui.levelFor

private data class DemoSnapshotProvider(
    val label: String,
    val provider: String,
    val fiveHour: Double?,
    val weekly: Double?,
    val balance: Double?,
    val currency: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onAddAccount: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val accounts by vm.accounts.collectAsState()
    val states by vm.states.collectAsState()
    val demo by vm.demoMode.collectAsState()
    val refreshing by vm.globalRefreshing.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (demo) "额度看板（示例数据）" else "额度看板")
                },
                actions = {
                    IconButton(onClick = { vm.refreshAll() }, enabled = !demo) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (demo) "关闭示例数据" else "开启示例数据") },
                                onClick = { vm.toggleDemo(); menuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("添加账号") },
                                onClick = { menuOpen = false; onAddAccount() },
                            )
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onAddAccount,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加账号") },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { vm.refreshAll() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                demo -> DemoList()
                accounts.isEmpty() -> EmptyState(onAddAccount)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(accounts, key = { it.id }) { account ->
                        val state = states[account.id]
                        AccountCard(
                            state = state,
                            demo = false,
                            onClick = { onOpenDetail(account.id) },
                            onDelete = { deleteTarget = account.id },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除账号？") },
            text = { Text("将同时删除本地保存的凭证与记账记录。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeAccount(id)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun DemoList() {
    val demos = listOf(
        DemoSnapshotProvider("Claude 示例", "Claude", 63.0, 42.0, null, "USD"),
        DemoSnapshotProvider("Codex 示例", "Codex (ChatGPT)", 97.0, 71.0, null, "USD"),
        DemoSnapshotProvider("DeepSeek 示例", "DeepSeek API", null, null, 83.20, "CNY"),
        DemoSnapshotProvider("Mistral 示例", "Mistral API", null, null, 12.50, "EUR"),
        DemoSnapshotProvider("opencode go 示例", "opencode go", 21.0, null, null, "USD"),
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(demos.size) { i ->
            val d = demos[i]
            val windows = buildList {
                d.fiveHour?.let { add(QuotaWindow(WindowKind.FIVE_HOUR, it)) }
                d.weekly?.let { add(QuotaWindow(WindowKind.WEEKLY, it)) }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "示例 · ${d.label}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    windows.forEach { w -> MiniProgressRow(label = w.kind.display, percent = w.usedPercent) }
                    d.balance?.let {
                        Text(
                            "余额：${formatMoney(it, d.currency)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(text = "示例")
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color = MaterialTheme.colorScheme.tertiary) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun AccountCard(
    state: AccountState?,
    demo: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val acct = state?.account
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        acct?.label ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        acct?.let { "${it.providerId} · ${if (it.authMethod == com.quotaboard.app.data.AuthMethod.OAUTH) "OAuth" else "API Key"}" } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state?.refreshing == true) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                onDelete?.let {
                    IconButton(onClick = it) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            val windows = state?.snapshot?.windows.orEmpty()
            if (windows.isEmpty() && state?.snapshot?.payg == null) {
                val text = when {
                    state?.refreshing == true -> "查询中…"
                    state?.lastError != null -> "查询失败：${state.lastError}"
                    state?.snapshot?.unsupportedNotes?.isNotEmpty() == true -> "暂不支持查询"
                    state?.lastError == null && state?.snapshot == null -> "尚未刷新"
                    else -> "暂无数据"
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state?.lastError != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            windows.forEach { w ->
                MiniProgressRow(label = w.kind.display, percent = w.usedPercent, resetsAt = w.resetsAtMs)
            }
            state?.snapshot?.payg?.let { p ->
                val level = when {
                    p.balanceAmount <= 0 -> com.quotaboard.app.ui.Level.DANGER
                    else -> com.quotaboard.app.ui.Level.NORMAL
                }
                Text(
                    "余额：${formatMoney(p.balanceAmount, p.currency)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (level == com.quotaboard.app.ui.Level.DANGER) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (state?.lastError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "查询失败：${state.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state?.snapshot?.unsupportedNotes?.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MiniProgressRow(
    label: String,
    percent: Double?,
    resetsAt: Long? = null,
) {
    val level = levelFor(percent)
    val color = levelColor(level)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(8.dp))
            val pct = ((percent ?: 0.0).coerceIn(0.0, 100.0))
            Text(
                if (percent == null) "—" else "${(pct + 0.5).toInt().coerceAtMost(100)}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            resetsAt?.let { reset ->
                Spacer(Modifier.weight(1f))
                Text(
                    "重置 ${formatDuration(reset - System.currentTimeMillis())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        if (percent == null) {
            LinearProgressIndicator(
                progress = { 0f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color,
                trackColor = color.copy(alpha = 0.15f),
            )
        } else {
            androidx.compose.material3.LinearWavyProgressIndicator(
                progress = { (percent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(12.dp),
                color = color,
                trackColor = color.copy(alpha = 0.15f),
            )
        }
    }
}
