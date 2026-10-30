@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.listudioorg.lissh.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.listudioorg.lissh.data.Db
import com.listudioorg.lissh.data.KeyEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** 密钥列表页 */
@Composable
fun KeysScreen(
    db: Db,
    onAdd: () -> Unit,
    onEdit: (KeyEntry) -> Unit,
) {
    var keys by remember { mutableStateOf(db.listKeys()) }
    var pendingDelete by remember { mutableStateOf<KeyEntry?>(null) }

    RefreshOnResume { keys = db.listKeys() }

    Scaffold(
        topBar = { LisshTopBar("密钥") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("新建密钥") },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = onAdd,
            )
        },
    ) { padding ->
        if (keys.isEmpty()) {
            EmptyState(
                Icons.Rounded.VpnKey,
                "还没有密钥",
                "点击「新建密钥」，可粘贴 PEM 或一键生成 RSA 密钥",
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                keys.forEach { key ->
                    KeyCard(
                        key = key,
                        onEdit = { onEdit(key) },
                        onDelete = { pendingDelete = key },
                    )
                }
            }
        }
    }

    ConfirmDialog(
        show = pendingDelete != null,
        title = "删除密钥",
        text = "确定要删除「${pendingDelete?.name}」吗？",
        onConfirm = {
            pendingDelete?.let { db.deleteKey(it.id) }
            keys = db.listKeys()
        },
        onDismiss = { pendingDelete = null },
    )
}

@Composable
private fun KeyCard(key: KeyEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .then(Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(Color(0xFF6BCB8B).copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.VpnKey,
                    contentDescription = null,
                    tint = Color(0xFF6BCB8B),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    key.name,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "PEM · ${key.privateKey.length} 字符",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 新建 / 编辑密钥 */
@Composable
fun KeyEditScreen(db: Db, keyId: Long?, onDone: () -> Unit) {
    val isNew = keyId == null
    val initial = remember { keyId?.let { db.getKey(it) } }

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var content by remember { mutableStateOf(initial?.privateKey ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建密钥" else "编辑密钥") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            when {
                                name.isBlank() -> error = "请填写密钥名称"
                                content.isBlank() -> error = "请填写或生成私钥内容"
                                !content.contains("-----BEGIN") ->
                                    error = "内容不是 PEM 私钥（应包含 -----BEGIN …-----）"
                                else -> {
                                    db.upsertKey(KeyEntry(keyId ?: 0L, name.trim(), content.trim()))
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密钥名称") },
                singleLine = true,
                placeholder = { Text("如：跳板机专用") },
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                label = { Text("PEM 私钥内容（明文存储）") },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                minLines = 8,
            )

            OutlinedButton(
                onClick = {
                    if (generating) return@OutlinedButton
                    generating = true
                    scope.launch {
                        val pem = runCatching {
                            withContext(Dispatchers.IO) {
                                val jsch = JSch()
                                val pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048)
                                val out = ByteArrayOutputStream()
                                pair.writePrivateKey(out)
                                String(out.toByteArray(), Charsets.UTF_8)
                            }
                        }.getOrNull()
                        if (pem == null) {
                            error = "生成失败（部分 ROM 限制 RSA）。可手动粘贴 OpenSSH/PEM 私钥。"
                        } else {
                            content = pem
                            if (name.isBlank()) name = "RSA-2048"
                        }
                        generating = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !generating,
                shape = RoundedCornerShape(12.dp),
            ) {
                if (generating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (generating) "生成中…" else "一键生成 RSA-2048 密钥")
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Text(
                "提示：私钥以明文存入 SQLite（按需求）。卸载应用即全部清除。",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
