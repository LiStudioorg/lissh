package com.listudioorg.lissh.ssh

import android.content.ContentResolver
import android.net.Uri
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** 远程文件条目 */
data class RemoteEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val mtime: String,
    val path: String, // 完整路径，用于进入/下载
)

/**
 * SFTP 客户端封装：
 * - 复用终端页已连接的 JSch Session，按需打开 `sftp` 通道（ChannelSftp）；
 * - 所有网络操作均在 Dispatchers.IO 中执行；
 * - 通道懒加载 + 复用，屏幕销毁时 close() 断开。
 */
class SftpClient(private val sessionProvider: () -> TerminalSession?) {

    private var channel: ChannelSftp? = null

    /** 确保通道可用并执行任务 */
    private suspend fun <T> withChannel(block: (ChannelSftp) -> T): T =
        withContext(Dispatchers.IO) {
            var ch = channel
            if (ch == null || !ch.isConnected) {
                val ses = sessionProvider()
                ch = ses?.openSftpChannel()
                    ?: throw IOException("没有可用的 SSH 会话，请先在终端页连接主机")
                channel = ch
            }
            block(ch)
        }

    /** 列出目录（远端 ls） */
    suspend fun ls(path: String): List<RemoteEntry> = withChannel { ch ->
        val vec = ch.ls(path)
        val base = if (path.endsWith("/")) path else "$path/"
        val out = ArrayList<RemoteEntry>(vec.size)
        for (e in vec) {
            val name = e.filename
            if (name == "." || name == "..") continue
            val a = e.attrs
            val isDir = a.isDir
            out.add(
                RemoteEntry(
                    name = name,
                    isDir = isDir,
                    size = if (isDir) 0L else a.size,
                    mtime = try { a.getMtimeString() } catch (_: Throwable) { "" },
                    path = base + name,
                )
            )
        }
        // 目录优先，名称不区分大小写排序
        out.sortedWith(
            compareByDescending<RemoteEntry> { it.isDir }.thenBy { it.name.lowercase() }
        )
    }

    /** 登录用户主目录（首次进入用） */
    suspend fun home(): String = withChannel { ch -> ch.getHome() }

    /** 上传：contentResolver.openInputStream(uri) → sftp.put */
    suspend fun upload(
        remotePath: String,
        resolver: ContentResolver,
        uri: Uri,
    ): Unit = withChannel { ch ->
        val ins: InputStream = resolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件（Uri 被占用或已失效）")
        ins.use { stream ->
            ch.put(stream, remotePath, ChannelSftp.OVERWRITE)
        }
    }

    /** 下载：sftp.get → resolver.openOutputStream(uri) */
    suspend fun download(
        remotePath: String,
        resolver: ContentResolver,
        uri: Uri,
    ): Unit = withChannel { ch ->
        val os: OutputStream = resolver.openOutputStream(uri)
            ?: throw IOException("无法写入所选位置（Uri 无效）")
        os.use { stream ->
            ch.get(remotePath, stream)
        }
    }

    /** 屏幕销毁时断开 SFTP 通道（不影响终端 SSH 主连接） */
    fun close() {
        runCatching { channel?.disconnect() }
        channel = null
    }

    companion object {
        /** 把 SftpException 翻译为友好文案 */
        fun humanError(t: Throwable): String = when (t) {
            is SftpException ->
                when (t.id) {
                    ChannelSftp.SSH_FX_NO_SUCH_FILE -> "路径不存在"
                    ChannelSftp.SSH_FX_PERMISSION_DENIED -> "权限不足"
                    else -> "SFTP 错误 ${t.id}: ${t.message}"
                }
            is IOException -> t.message ?: t.javaClass.simpleName
            else -> "${t.javaClass.simpleName}: ${t.message ?: t.toString()}"
        }
    }
}
