@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.listudioorg.lissh.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.listudioorg.lissh.data.Db
import com.listudioorg.lissh.data.Host
import com.listudioorg.lissh.data.KeyEntry

/** 三个底部栏条目 */
private data class TabItem(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    TabItem("主机", Icons.Rounded.Dns),
    TabItem("密钥", Icons.Rounded.VpnKey),
    TabItem("设置", Icons.Rounded.Settings),
)

/**
 * 响应式主页框架：
 * - 宽 <= 600dp：底部导航栏（主机 / 密钥 / 设置）
 * - 更宽（平板/横屏）：左侧 NavigationRail
 */
@Composable
fun MainChrome(
    db: Db,
    onConnectHost: (Host) -> Unit,
    onAddHost: () -> Unit,
    onEditHost: (Host) -> Unit,
    onAddKey: () -> Unit,
    onEditKey: (KeyEntry) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 600.dp
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface) {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
                    Tabs.forEachIndexed { index, item ->
                        NavigationRailItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    TabContent(
                        tab, db,
                        onConnectHost, onAddHost, onEditHost, onAddKey, onEditKey,
                    )
                }
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        Tabs.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    TabContent(
                        tab, db,
                        onConnectHost, onAddHost, onEditHost, onAddKey, onEditKey,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    tab: Int,
    db: Db,
    onConnectHost: (Host) -> Unit,
    onAddHost: () -> Unit,
    onEditHost: (Host) -> Unit,
    onAddKey: () -> Unit,
    onEditKey: (KeyEntry) -> Unit,
) {
    when (tab) {
        0 -> HostsScreen(db, onAdd = onAddHost, onEdit = onEditHost, onConnect = onConnectHost)
        1 -> KeysScreen(db, onAdd = onAddKey, onEdit = onEditKey)
        else -> SettingsScreen(db)
    }
}

/** 供各页面统一风格的顶栏 */
@Composable
fun LisshTopBar(title: String) {
    TopAppBar(
        title = { Text(title) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        ),
    )
}
