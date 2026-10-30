@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.listudioorg.lissh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listudioorg.lissh.data.Db
import com.listudioorg.lissh.data.Host

/** 卡片图标的配色板（Termius 式分类色） */
private val TileColors = listOf(
    0xFF5A9CF8, 0xFF6BCB8B, 0xFFE7B75F, 0xFFC77BE0,
    0xFF4ED6D6, 0xFFF0644E, 0xFF93B2D6, 0xFF66F08E,
).map { Color(it) }

@Composable
fun HostsScreen(
    db: Db,
    onAdd: () -> Unit,
    onEdit: (Host) -> Unit,
    onConnect: (Host) -> Unit,
) {
    var hosts by remember { mutableStateOf(db.listHosts()) }
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Host?>(null) }
    var menuFor by remember { mutableStateOf<Host?>(null) }

    RefreshOnResume { hosts = db.listHosts() }

    Scaffold(
        topBar = { LisshTopBar("主机") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("新建主机") },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                onClick = onAdd,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索主机 / IP / 用户名") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            if (hosts.isEmpty()) {
                EmptyState(Icons.Rounded.Dns, "还没有主机", "点击右下角「新建主机」添加第一台服务器")
            } else {
                val filtered = hosts.filter {
                    query.isBlank() ||
                        it.alias.contains(query, true) ||
                        it.hostname.contains(query, true) ||
                        it.username.contains(query, true)
                }
                if (filtered.isEmpty()) {
                    EmptyState(Icons.Rounded.Dns, "没有匹配结果", "换个关键词试试")
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 170.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filtered, key = { it.id }) { host ->
                            HostCard(
                                host = host,
                                onConnect = { onConnect(host) },
                                onEdit = { onEdit(host) },
                                onDelete = { pendingDelete = host },
                                menuOpen = menuFor?.id == host.id,
                                onMenuToggle = { open -> menuFor = if (open) host else null },
                            )
                        }
                    }
                }
            }
        }
    }

    ConfirmDialog(
        show = pendingDelete != null,
        title = "删除主机",
        text = "确定要删除「${pendingDelete?.alias}」吗？该操作不可撤销。",
        onConfirm = {
            pendingDelete?.let { db.deleteHost(it.id) }
            hosts = db.listHosts()
        },
        onDismiss = { pendingDelete = null },
    )
}

@Composable
private fun HostCard(
    host: Host,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    menuOpen: Boolean,
    onMenuToggle: (Boolean) -> Unit,
) {
    Card(
        onClick = onConnect,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(
                            TileColors[(host.id % TileColors.size).toInt()],
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = host.alias.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
                Box(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { onMenuToggle(!menuOpen) }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuToggle(false) }) {
                        DropdownMenuItem(
                            text = { Text("连接") },
                            leadingIcon = { Icon(Icons.Rounded.IosShare, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { onMenuToggle(false); onConnect() },
                        )
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = { onMenuToggle(false); onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                            onClick = { onMenuToggle(false); onDelete() },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = host.alias,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${host.username}@${host.hostname}:${host.port}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TagChip("ssh")
                TagChip(host.username)
                TagChip(host.authType.label)
            }
        }
    }
}
