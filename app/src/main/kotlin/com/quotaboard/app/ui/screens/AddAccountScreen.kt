package com.quotaboard.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.quotaboard.app.data.AccountSecrets
import com.quotaboard.app.data.AuthMethod
import com.quotaboard.app.oauth.OAuthActivity
import com.quotaboard.app.provider.ProviderRegistry
import com.quotaboard.app.provider.QuotaProvider
import com.quotaboard.app.provider.supports
import com.quotaboard.app.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    vm: AppViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    var provider by remember { mutableStateOf<QuotaProvider?>(null) }
    var method by remember { mutableStateOf<AuthMethod?>(null) }
    var apiKey by remember { mutableStateOf("") }
    var teamId by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val oauthStatus by vm.oauthStatus.collectAsState()

    LaunchedEffect(oauthStatus) {
        val p = provider ?: return@LaunchedEffect
        val result = oauthStatus[p.id]
        if (result == "ok") {
            val draft = vm.takeOAuthDraft(p.id)
            if (draft != null) {
                vm.addAccount(p.id, label, AuthMethod.OAUTH, draft, emptyMap())
                onDone()
            }
        } else if (result != null && result != "ok") {
            error = result
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加账号") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("1 · 选择服务", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ProviderRegistry.all.forEach { p ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        provider = p
                        method = null
                        error = null
                    },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(p.displayName, fontWeight = FontWeight.SemiBold)
                        p.supportDescription.forEach { s ->
                            Text(
                                (if (s.supported) "✓ " else "✗ ") + s.capability +
                                    (s.note?.let { "（$it）" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (s.supported) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            provider?.let { p ->
                Spacer(Modifier.height(4.dp))
                Text("2 · 连接方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (p.supports(AuthMethod.OAUTH)) {
                        FilterChip(
                            selected = method == AuthMethod.OAUTH,
                            onClick = { method = AuthMethod.OAUTH },
                            label = { Text("浏览器登录（OAuth）") },
                        )
                    }
                    FilterChip(
                        selected = method == AuthMethod.API_KEY,
                        onClick = { method = AuthMethod.API_KEY },
                        label = { Text("API Key") },
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("备注名（可选，如“工作号”）") },
                    modifier = Modifier.fillMaxWidth(),
                )

                when (method) {
                    AuthMethod.OAUTH -> {
                        Text(
                            "点击下方按钮打开内置浏览器登录。凭证仅保存在本机加密存储。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = {
                            error = null
                            context.startActivity(
                                Intent(context, OAuthActivity::class.java)
                                    .putExtra(OAuthActivity.EXTRA_PROVIDER_ID, p.id)
                            )
                        }) { Text("登录 ${p.displayName}") }
                    }
                    AuthMethod.API_KEY -> {
                        val hint = when (p.id) {
                            "grok" -> "xAI Management Key（Console → Settings → Management Keys）"
                            "opencodego" -> "opencode API Key（opencode.ai 授权页）"
                            else -> "API Key"
                        }
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(hint) },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        if (p.id == "grok") {
                            OutlinedTextField(
                                value = teamId,
                                onValueChange = { teamId = it },
                                label = { Text("团队 ID（billing URL 中 teams/ 后的 UUID）") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Button(
                            onClick = {
                                if (apiKey.isBlank()) {
                                    error = "请填写 API Key"
                                    return@Button
                                }
                                if (p.id == "grok" && teamId.isBlank()) {
                                    error = "请填写 xAI 团队 ID"
                                    return@Button
                                }
                                vm.addAccount(
                                    providerId = p.id,
                                    label = label,
                                    authMethod = AuthMethod.API_KEY,
                                    secrets = AccountSecrets(apiKey = apiKey.trim()),
                                    extra = if (p.id == "grok") mapOf("teamId" to teamId.trim()) else emptyMap(),
                                )
                                onDone()
                            },
                        ) { Text("保存") }
                    }
                    null -> {}
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Default)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
