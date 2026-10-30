@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.listudioorg.lissh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listudioorg.lissh.TerminalPrefs
import com.listudioorg.lissh.data.Db
import com.listudioorg.lissh.ui.theme.LiSSHTheme

@Composable
fun SettingsScreen(db: Db) {
    var fontSize by remember { mutableStateOf(TerminalPrefs.fontSize) }
    var hostCount by remember { mutableStateOf(db.countHosts()) }
    var keyCount by remember { mutableIntStateOf(db.countKeys()) }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(topBar = { LisshTopBar("设置") }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // —— 终端 ——
            SectionCard(title = "终端") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TextFields, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("终端字号", fontWeight = FontWeight.Medium)
                        Text(
                            "Termius 风格等宽字体 · ${fontSize.toInt()}sp",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StepButton("−") {
                        fontSize = (fontSize - 1f).coerceAtLeast(9f)
                        TerminalPrefs.fontSize = fontSize
                    }
                    Spacer(Modifier.size(8.dp))
                    StepButton("+") {
                        fontSize = (fontSize + 1f).coerceAtMost(24f)
                        TerminalPrefs.fontSize = fontSize
                    }
                }
            }

            // —— 连接 ——
            SectionCard(title = "连接") {
                SettingRow(
                    icon = Icons.Rounded.MonitorHeart,
                    title = "主机指纹校验",
                    summary = "已按需求关闭（StrictHostKeyChecking = no）",
                    tint = Color(0xFFE7B75F),
                )
                SettingRow(
                    icon = Icons.Rounded.Storage,
                    title = "存储",
                    summary = "明文 SQLite（lissh.db）· 主机 $hostCount · 密钥 $keyCount",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // —— 关于 ——
            SectionCard(title = "关于") {
                Text(
                    "LiSSH 1.0",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "纯本地 SSH 客户端 · 无需登录 · 数据全部存于本机 SQLite。\n" +
                        "Jetpack Compose + Material3 + JSch 实现。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("清空全部数据")
            }
        }
    }

    ConfirmDialog(
        show = confirmClear,
        title = "清空全部数据",
        text = "将删除全部主机与密钥（明文 SQLite），且不可恢复。确定？",
        onConfirm = {
            db.clearAll()
            hostCount = db.countHosts()
            keyCount = db.countKeys()
        },
        onDismiss = { confirmClear = false },
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(summary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
    ) { Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}
