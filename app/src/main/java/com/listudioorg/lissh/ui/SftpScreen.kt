@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.listudioorg.lissh.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listudioorg.lissh.ssh.RemoteEntry
import com.listudioorg.lissh.ssh.SftpClient
import com.listudioorg.lissh.ssh.TerminalTabs
import kotlinx.coroutines.launch

/**
 * SFTP 文件传输页：
 * - 借用终端页已连接主机的 JSch Session 打开 ChannelSftp；
 * - LazyColumn 展示远程目录（图标 / 名称 / 大小 / 修改时间）；
 * - 点击目录进入下一级，路径条上一级/根目录；顶部显示绝对路径；
 * - 上传走 SAF OpenDocument，下载走 SAF CreateDocument；
 * - 所有网络操作（ls/put/get）都在 Dispatchers.IO，UI 带 CircularProgressIndicator。
 */
@Composable
fun SftpScreen(hostId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    // 该主机对应的终端会话（提供认证好的 JSch Session）
    val terminal = remember(hostId) {
        TerminalTabs.sessions.firstOrNull { it.host.id == hostId }
    }
    val sftp = remember(hostId) { SftpClient { terminal } }
    val scope = rememberCoroutineScope()

    // —— 浏览状态 ——
    var path by rememberSaveable { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    /** 下载中的远端完整路径（CreateDocument 结果需要它） */
    var pendingDownload by remember { mutableStateOf<String?>(null) }
    /** 是否完成过首次定位 */
    var initialized by remember { mutableStateOf(false) }

    // —— 目录导航 ——
    fun openDir(target: String) {
        scope.launch {
            if (terminal == null) {
                toast = "没有连接中的会话，请先在终端页连接主机"
                return@launch
            }
            loading = true
            try {
                entries = sftp.ls(target) // 网络操作在 withContext(Dispatchers.IO)
                path = target
            } catch (t: Throwable) {
                toast = SftpClient.humanError(t)
            } finally {
                loading = false
            }
        }
    }

    // 首次进入定位到登录主目录；失败回退 /
    LaunchedEffect(Unit) {
        if (initialized) return@LaunchedEffect
        initialized = true
        if (terminal == null) {
            toast = "没有连接中的会话，请先在终端页连接主机"
        } else {
            loading = true
            try {
                val home = sftp.home()
                entries = sftp.ls(home)
                path = home
            } catch (t: Throwable) {
                try {
                    entries = sftp.ls("/")
                    path = "/"
                } catch (t2: Throwable) {
                    toast = SftpClient.humanError(t2)
                }
            } finally {
                loading = false
            }
        }
    }

    // —— SAF 上传：OpenDocument 拿本地 Uri ——
    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                if (terminal == null) {
                    toast = "没有连接中的会话"
                    return@launch
                }
                loading = true
                try {
                    val name = queryDisplayName(context.contentResolver, uri) ?: "upload.bin"
                    val base = path
                    val target = if (base.endsWith("/")) base + name else "$base/$name"
                    sftp.upload(target, context.contentResolver, uri)
                    toast = "已上传：$name"
                    entries = sftp.ls(path) // 传完刷新当前目录
                } catch (t: Throwable) {
                    toast = "上传失败：${SftpClient.humanError(t)}"
                } finally {
                    loading = false
                }
            }
        }
    }

    // —— SAF 下载：CreateDocument 选保存位置 ——
    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val remote = pendingDownload
        pendingDownload = null
        if (uri != null && remote != null) {
            scope.launch {
                if (terminal == null) {
                    toast = "没有连接中的会话"
                    return@launch
                }
                loading = true
                try {
                    sftp.download(remote, context.contentResolver, uri)
                    toast = "已下载：${remote.substringAfterLast('/')}"
                } catch (t: Throwable) {
                    toast = "下载失败：${SftpClient.humanError(t)}"
                } finally {
                    loading = false
                }
            }
        }
    }

    // 离开页面：断开 SFTP 通道（终端 SSH 主连接不受影响）
    DisposableEffect(Unit) {
        onDispose { sftp.close() }
    }

    // Toast
    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SFTP · ${terminal?.host?.alias ?: "未连接"}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 上传（SAF OpenDocument）
                    IconButton(
                        onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                        enabled = terminal != null && !loading,
                    ) { Icon(Icons.Rounded.Upload, contentDescription = "上传文件") }
                    // 刷新当前目录
                    IconButton(
                        onClick = { if (path.isNotEmpty()) openDir(path) },
                        enabled = terminal != null && !loading,
                    ) { Icon(Icons.Rounded.Refresh, contentDescription = "刷新") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {

                // —— 路径条：上一级 / 根目录 / 绝对路径（横向滚动） ——
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { openDir(parentOf(path)) },
                        enabled = terminal != null && !loading && path.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "上一级", modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { openDir("/") },
                        enabled = terminal != null && !loading,
                    ) {
                        Icon(Icons.Rounded.Home, contentDescription = "根目录", modifier = Modifier.size(20.dp))
                    }
                    Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        Text(
                            text = formatPath(path),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                // —— 文件列表 ——
                if (entries.isEmpty() && !loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "该目录为空",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(entries, key = { "${if (it.isDir) 'd' else 'f'}:${it.path}" }) { entry ->
                            FileRow(
                                entry = entry,
                                enabled = !loading,
                                onOpen = {
                                    if (entry.isDir) {
                                        openDir(entry.path)
                                    } else {
                                        // 下载：先记住远端路径，让用户选本地保存位置
                                        pendingDownload = entry.path
                                        downloadLauncher.launch(entry.name)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // —— 加载遮罩（ls / put / get 进行中） ——
            if (loading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** 单行文件：目录/文件图标 + 名称 + 大小 + 修改时间 */
@Composable
private fun FileRow(entry: RemoteEntry, onOpen: () -> Unit, enabled: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDir) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
            contentDescription = if (entry.isDir) "目录" else "文件",
            tint = if (entry.isDir) Color(0xFF5A9CF8) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                entry.name,
                fontWeight = if (entry.isDir) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (entry.isDir) "目录" else formatSize(entry.size),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            entry.mtime,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

// ---------- 工具 ----------

/** 求上一级路径；根 "/" 的上一级是 "/" */
private fun parentOf(p: String): String {
    val trimmed = p.trimEnd('/')
    val up = trimmed.substringBeforeLast('/', "")
    return if (up.isEmpty()) "/" else up
}

/** 顶部显示的绝对路径（保证以 / 开头） */
private fun formatPath(p: String): String =
    if (p.startsWith("/")) p else "/$p"

/** 人类可读的文件大小 */
private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

/** 从 SAF Uri 里查 DISPLAY_NAME（上传时用于远端文件名） */
private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String? {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) return c.getString(i)
    }
    return uri.lastPathSegment
}
