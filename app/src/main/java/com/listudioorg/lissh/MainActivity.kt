package com.listudioorg.lissh

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.listudioorg.lissh.data.Db
import com.listudioorg.lissh.ssh.TerminalTabs
import com.listudioorg.lissh.ui.HostEditScreen
import com.listudioorg.lissh.ui.KeyEditScreen
import com.listudioorg.lissh.ui.MainChrome
import com.listudioorg.lissh.ui.SftpScreen
import com.listudioorg.lissh.ui.SettingsScreen
import com.listudioorg.lissh.ui.TerminalScreen
import com.listudioorg.lissh.ui.theme.LiSSHTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val db = (application as LiSSHApp).db
        setContent {
            LiSSHTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavGraph(db)
                }
            }
        }
    }
}

@Composable
private fun AppNavGraph(db: Db) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "main") {

        // 主页框架：底部导航 / NavigationRail，内部含 主机 / 密钥 / 设置 三个标签
        composable("main") {
            MainChrome(
                db = db,
                onConnectHost = { host -> nav.navigate("terminal?hostId=${host.id}") },
                onAddHost = { nav.navigate("host_edit") },
                onEditHost = { host -> nav.navigate("host_edit?id=${host.id}") },
                onAddKey = { nav.navigate("key_edit") },
                onEditKey = { key -> nav.navigate("key_edit?id=${key.id}") },
            )
        }

        // 新建 / 编辑主机
        composable(
            route = "host_edit?id={id}",
            arguments = listOf(navArgument("id") {
                type = NavType.LongType
                defaultValue = -1L
            }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            HostEditScreen(db = db, hostId = id.takeIf { it > 0 }, onDone = { nav.popBackStack() })
        }

        // 新建 / 编辑密钥
        composable(
            route = "key_edit?id={id}",
            arguments = listOf(navArgument("id") {
                type = NavType.LongType
                defaultValue = -1L
            }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            KeyEditScreen(db = db, keyId = id.takeIf { it > 0 }, onDone = { nav.popBackStack() })
        }

        // 终端（可带 hostId 直连）
        composable(
            route = "terminal?hostId={hostId}",
            arguments = listOf(navArgument("hostId") {
                type = NavType.LongType
                defaultValue = -1L
            }),
        ) { entry ->
            val hostId = entry.arguments?.getLong("hostId") ?: -1L
            TerminalScreen(
                db = db,
                hostId = hostId.takeIf { it > 0 },
                onBack = { nav.popBackStack() },
                onOpenSftp = {
                    // SFTP 入口：终端 Tab 旁的文件夹图标 → 借用当前已连接会话
                    TerminalTabs.selected?.host?.id?.let { id -> nav.navigate("sftp?hostId=$id") }
                },
            )
        }

        // SFTP 文件传输页
        composable(
            route = "sftp?hostId={hostId}",
            arguments = listOf(navArgument("hostId") {
                type = NavType.LongType
                defaultValue = -1L
            }),
        ) { entry ->
            val hostId = entry.arguments?.getLong("hostId") ?: -1L
            SftpScreen(hostId = hostId, onBack = { nav.popBackStack() })
        }
    }
}
