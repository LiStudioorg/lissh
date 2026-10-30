package com.listudioorg.lissh.ssh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.listudioorg.lissh.data.AuthType
import com.listudioorg.lissh.data.Db
import com.listudioorg.lissh.data.Host
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import com.jcraft.jsch.Session

/** 会话连接状态 */
enum class ConnState { CONNECTING, CONNECTED, FAILED, CLOSED }

/** 标签页状态点颜色 */
fun connStateColor(s: ConnState): Int = when (s) {
    ConnState.CONNECTED -> 0xFF3DDC7A.toInt()
    ConnState.CONNECTING -> 0xFFE7CE7C.toInt()
    ConnState.FAILED -> 0xFFFF5370.toInt()
    ConnState.CLOSED -> 0xFF767A80.toInt()
}

/**
 * 一个终端标签对应的 SSH 会话：
 * - JSch 建链（StrictHostKeyChecking=no，忽略主机指纹校验）；
 * - IO 协程循环读 inputStream，解码后追加进 TerminalBuffer；
 * - send() 把 UI 按键（ANSI 控制字节）写入 channel 的 OutputStream。
 */
class TerminalSession(val host: Host) {

    val buffer = TerminalBuffer()

    private val stateState = mutableStateOf(ConnState.CONNECTING)
    private val errorState = mutableStateOf("")

    var state: ConnState
        get() = stateState.value
        private set(value) { stateState.value = value }

    var lastError: String
        get() = errorState.value
        private set(value) { errorState.value = value }

    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var scope: CoroutineScope? = null
    private var closedByUser = false
    private var dbRef: Db? = null

    /** UTF-8 长字节跨界续接：保留未解码完整的尾部字节 */
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)

    fun start(db: Db) {
        dbRef = db
        closedByUser = false
        stateState.value = ConnState.CONNECTING
        errorState.value = ""
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch {
            try {
                connectAndRead()
            } catch (t: Throwable) {
                if (!closedByUser) {
                    fail("${t.javaClass.simpleName}: ${t.message ?: "连接失败"}")
                }
            }
        }
    }

    private suspend fun connectAndRead() {
        val db = dbRef ?: return
        buffer.info("正在连接 ${host.username}@${host.hostname}:${host.port}（${host.authType.label} 认证）…")

        val jsch = JSch()
        if (host.authType == AuthType.KEY) {
            val key = db.getKey(host.keyId)
                ?: throw IOException("未找到密钥（id=${host.keyId}），请先在「密钥」页添加")
            // 私钥原文即 PEM 文本，直接以字节数组交给 JSch（明文存储，按需求）
            jsch.addIdentity("lissh-key", key.privateKey.toByteArray(Charsets.UTF_8), null, null)
        }

        val ses = jsch.getSession(host.username, host.hostname, host.port)
        if (host.authType == AuthType.PASSWORD) ses.setPassword(host.password)
        // 忽略主机指纹校验
        ses.setConfig("StrictHostKeyChecking", "no")
        ses.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive")
        ses.setServerAliveInterval(15_000)
        ses.connect(15_000)

        val ch = ses.openChannel("shell") as ChannelShell
        ch.setPtyType("xterm-256color", 100, 40, 0, 0)
        ch.connect(15_000)

        session = ses
        channel = ch
        stateState.value = ConnState.CONNECTED
        buffer.info("已连接 ✓")
        readLoop(ch.inputStream ?: throw IOException("无法获取输入流"))
    }

    private fun readLoop(input: InputStream) {
        val buf = ByteArray(8192)
        while (true) {
            val n = if (closedByUser) -1 else try {
                input.read(buf)
            } catch (_: IOException) {
                -1
            }
            if (n <= 0) break
            val decoded = decodeChunk(buf, n)
            if (decoded.isNotEmpty()) buffer.appendChunk(decoded)
        }
        if (!closedByUser) fail("远程主机已断开连接")
    }

    /** UTF-8 解码：处理跨 chunk 的多字节序列 */
    private fun decodeChunk(bytes: ByteArray, len: Int): String {
        val total = pending.size + len
        if (total == 0) return ""
        val combined = ByteArray(total)
        if (pending.isNotEmpty()) System.arraycopy(pending, 0, combined, 0, pending.size)
        System.arraycopy(bytes, 0, combined, pending.size, len)

        val input = ByteBuffer.wrap(combined)
        val sb = StringBuilder()
        while (true) {
            val out = CharBuffer.allocate((input.remaining() + 4).coerceAtLeast(64))
            val r = decoder.decode(input, out, false)
            out.flip()
            sb.append(out)
            if (r.isUnderflow) break
            if (r.isError) {
                runCatching { r.throwException() }
                break
            }
            // OVERFLOW：继续循环解码剩余
        }
        val consumed = input.position()
        pending = if (consumed < total) combined.copyOfRange(consumed, total) else ByteArray(0)
        return sb.toString()
    }

    /** 将按键字符串写入远程（UI 线程调用，写入量均为几字节） */
    fun send(keys: String) {
        if (stateState.value != ConnState.CONNECTED) {
            buffer.err("未连接，按键未发送")
            return
        }
        val os = channel?.outputStream ?: return
        try {
            os.write(keys.toByteArray(Charsets.UTF_8))
            os.flush()
        } catch (t: IOException) {
            buffer.err("发送失败: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun fail(msg: String) {
        stateState.value = ConnState.FAILED
        errorState.value = msg
        buffer.err(msg)
    }

    /** 断开并清理（用户关闭标签页） */
    fun close() {
        closedByUser = true
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        scope?.cancel()
        stateState.value = ConnState.CLOSED
    }

    /** 失败后重连 */
    fun reconnect() {
        if (stateState.value == ConnState.FAILED) {
            closedByUser = true
            runCatching { channel?.disconnect() }
            runCatching { session?.disconnect() }
            runCatching { scope?.cancel() }
            val db = dbRef
            if (db != null) start(db) else fail("无法重连：缺少数据库上下文")
        }
    }
}

/** 终端标签页仓库：跨页面存活的会话集合 */
object TerminalTabs {

    val sessions = mutableStateListOf<TerminalSession>()
    private val selectedIndexState = mutableIntStateOf(-1)

    val selectedIndex: Int
        get() = selectedIndexState.intValue

    val selected: TerminalSession?
        get() = sessions.getOrNull(selectedIndexState.intValue)

    /** 打开（或选中已有）主机的会话标签 */
    fun open(host: Host, db: Db): TerminalSession {
        val existing = sessions.indexOfFirst { it.host.id == host.id }
        if (existing >= 0) {
            selectedIndexState.intValue = existing
            return sessions[existing]
        }
        val s = TerminalSession(host)
        sessions.add(s)
        selectedIndexState.intValue = sessions.size - 1
        s.start(db)
        return s
    }

    fun select(index: Int) {
        if (index in sessions.indices) selectedIndexState.intValue = index
    }

    fun close(index: Int) {
        if (index !in sessions.indices) return
        sessions[index].close()
        sessions.removeAt(index)
        selectedIndexState.intValue = when {
            sessions.isEmpty() -> -1
            else -> index.coerceAtMost(sessions.size - 1).coerceAtLeast(0)
        }
    }

    fun selectNext() {
        val n = sessions.size
        if (n == 0) return
        selectedIndexState.intValue = (selectedIndexState.intValue + 1) % n
    }

    fun selectPrev() {
        val n = sessions.size
        if (n == 0) return
        selectedIndexState.intValue = (selectedIndexState.intValue - 1 + n) % n
    }
}
