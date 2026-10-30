@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.listudioorg.lissh.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listudioorg.lissh.data.AuthType
import com.listudioorg.lissh.data.Db
import com.listudioorg.lissh.data.Host
import androidx.compose.foundation.text.KeyboardOptions

/**
 * 新建 / 编辑主机。
 * 密码框默认圆点显示，右侧“小眼睛”点击切换明文（Termius 同款交互）。
 */
@Composable
fun HostEditScreen(db: Db, hostId: Long?, onDone: () -> Unit) {
    val isNew = hostId == null
    val initial = remember { hostId?.let { db.getHost(it) } }

    var alias by remember { mutableStateOf(initial?.alias ?: "") }
    var hostname by remember { mutableStateOf(initial?.hostname ?: "") }
    var port by remember { mutableStateOf((initial?.port ?: 22).toString()) }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var authType by remember { mutableStateOf(initial?.authType ?: AuthType.PASSWORD) }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var keyId by remember { mutableStateOf(initial?.keyId ?: 0L) }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var showKeyPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val keys = remember { db.listKeys() }
    val selectedKeyName = keys.firstOrNull { it.id == keyId }?.name

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建主机" else "编辑主机") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            // —— 保存校验 ——
                            val p = port.toIntOrNull()
                            when {
                                alias.isBlank() -> error = "请填写别名"
                                hostname.isBlank() -> error = "请填写主机地址"
                                username.isBlank() -> error = "请填写用户名"
                                p == null || p !in 1..65535 -> error = "端口须为 1-65535"
                                authType == AuthType.KEY && keyId <= 0 -> error = "请选择认证密钥"
                                else -> {
                                    db.upsertHost(
                                        Host(
                                            id = hostId ?: 0L,
                                            alias = alias.trim(),
                                            hostname = hostname.trim(),
                                            port = p!!,
                                            username = username.trim(),
                                            authType = authType,
                                            password = password, // 明文入库（按需求）
                                            keyId = keyId,
                                        )
                                    )
                                    onDone()
                                }
                            }
                        },
                    ) { Text("保存") }
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
            OutlinedTextField(alias, { alias = it }, Modifier.fillMaxWidth(),
                label = { Text("别名") }, singleLine = true,
                placeholder = { Text("如：生产机") })

            OutlinedTextField(hostname, { hostname = it }, Modifier.fillMaxWidth(),
                label = { Text("主机地址 (IP / 域名)") }, singleLine = true,
                placeholder = { Text("如：192.168.1.10") })

            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("端口") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(),
                label = { Text("用户名") }, singleLine = true,
                placeholder = { Text("如：root / ubuntu") })

            Text("认证方式", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                AuthType.entries.forEachIndexed { index, t ->
                    SegmentedButton(
                        selected = authType == t,
                        onClick = { authType = t },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index, count = AuthType.entries.size
                        ),
                        label = { Text(t.label) },
                    )
                }
            }

            if (authType == AuthType.PASSWORD) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码（明文存储）") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        // 小眼睛：切换显示 / 隐藏
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff
                                else Icons.Rounded.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    placeholder = { Text("SSH 登录密码") },
                )
            } else {
                KeyPickerCard(
                    keyName = selectedKeyName,
                    onSelect = { showKeyPicker = true },
                    onClear = { keyId = 0L },
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.size(4.dp))

            Button(
                onClick = {
                    val p = port.toIntOrNull()
                    when {
                        alias.isBlank() -> error = "请填写别名"
                        hostname.isBlank() -> error = "请填写主机地址"
                        username.isBlank() -> error = "请填写用户名"
                        p == null || p !in 1..65535 -> error = "端口须为 1-65535"
                        authType == AuthType.KEY && keyId <= 0 -> error = "请选择认证密钥"
                        else -> {
                            db.upsertHost(
                                Host(
                                    id = hostId ?: 0L,
                                    alias = alias.trim(),
                                    hostname = hostname.trim(),
                                    port = p!!,
                                    username = username.trim(),
                                    authType = authType,
                                    password = password,
                                    keyId = keyId,
                                )
                            )
                            onDone()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (isNew) "创建主机" else "保存修改") }

            if (!isNew) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("删除主机", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // —— 密钥选择对话框 ——
    if (showKeyPicker) {
        AlertDialog(
            onDismissRequest = { showKeyPicker = false },
            title = { Text("选择认证密钥") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (keys.isEmpty()) {
                        Text("还没有密钥，请先到「密钥」页新建。", fontSize = 13.sp)
                    } else {
                        keys.forEach { k ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { keyId = k.id; showKeyPicker = false },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = keyId == k.id,
                                    onClick = { keyId = k.id; showKeyPicker = false },
                                )
                                Text(k.name, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKeyPicker = false }) { Text("关闭") }
            },
        )
    }

    ConfirmDialog(
        show = confirmDelete,
        title = "删除主机",
        text = "确定要删除「$alias」吗？",
        onConfirm = {
            hostId?.let { db.deleteHost(it) }
            onDone()
        },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun KeyPickerCard(keyName: String?, onSelect: () -> Unit, onClear: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable { onSelect() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("认证密钥", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    keyName ?: "未选择",
                    fontWeight = FontWeight.Medium,
                    color = if (keyName == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            TextButton(onClick = onSelect) { Text("选择") }
            if (keyName != null) {
                TextButton(onClick = onClear) {
                    Text("清除", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
