@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.listudioorg.lissh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardTab
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.listudioorg.lissh.TerminalPrefs
import com.listudioorg.lissh.data.Db
import com.listudioorg.lissh.data.Host
import com.listudioorg.lissh.ssh.ConnState
import com.listudioorg.lissh.ssh.TerminalDefaultFg
import com.listudioorg.lissh.ssh.TerminalSession
import com.listudioorg.lissh.ssh.TerminalTabs
import com.listudioorg.lissh.ssh.connStateColor

/* ============================================================
 * ANSI 控制字节
 * ============================================================ */

/** ESC = 0x1B */
private const val ESC = "\u001B"

/** 按键 → 发送给远端的字节串（Terminal 语义） */
private object Ansi {
    const val TAB = "\t"                       // 0x09
    const val SHIFT_TAB = "$ESC[Z"             // ⇧ Tab（CSI Z）
    const val ESCAPE = ESC                     // 0x1B
    const val SLASH_CTRL = "\u001F"         // Ctrl+/ = 0x1F

    const val UP = "$ESC[A"
    const val DOWN = "$ESC[B"
    const val RIGHT = "$ESC[C"
    const val LEFT = "$ESC[D"
    const val HOME = "$ESC[H"
    const val END = "$ESC[F"
    const val PAGE_UP = "$ESC[5~"
    const val PAGE_DOWN = "$ESC[6~"

    /** Ctrl+方向键：CSI 1;5X */
    fun arrow(dir: Char, ctrl: Boolean): String =
        if (ctrl) "${ESC}[1;5$dir" else "${ESC}[$dir"

    /** Ctrl+字母：'C' -> 0x03, 'L' -> 0x0C … */
    fun ctrlLetter(c: Char): String =
        (c.lowercaseChar().code - 96).toChar().toString()
}

/* ============================================================
 * 终端页入口
 * ============================================================ */

@Composable
fun TerminalScreen(db: Db, hostId: Long?, onBack: () -> Unit, onOpenSftp: () -> Unit = {}) {
    var showPicker by remember { mutableStateOf(false) }
    val handled = rememberSaveable { mutableStateOf(false) }

    // 主机卡片点击 → 路由参数直连
    LaunchedEffect(hostId) {
        if (hostId != null && !handled.value) {
            handled.value = true
            db.getHost(hostId)?.let { TerminalTabs.open(it, db) }
        }
    }

    val session = TerminalTabs.selected

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(TerminalPrefs.bgColor))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .imePadding(),
    ) {
        TabStrip(onBack = onBack, onAdd = { showPicker = true }, onOpenSftp = onOpenSftp)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (session == null) {
                TerminalEmpty(onOpen = { showPicker = true })
            } else {
                TerminalView(session)
                ConnectionStatusOverlay(session)
            }
        }

        if (session != null) {
            CmdInputRow(session)
            ExtraKeysBar(session)
        }
    }

    if (showPicker) {
        HostPickerDialog(
            db = db,
            onOpen = { host ->
                TerminalTabs.open(host, db)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/* ============================================================
 * 顶部 Tab 条：可横滚会话标签 + 状态点 + 关闭 X + 右侧 +
 * ============================================================ */

private val TabStripBg = Color(0xFF14161B)
private val TabSelectedBg = Color(0xFF272D38)
private val TabTextSelected = Color(0xFFE6EAF0)
private val TabTextNormal = Color(0xFF8A929E)
private val TabStripAdd = Color(0xFF9AA3AF)

@Composable
private fun TabStrip(onBack: () -> Unit, onAdd: () -> Unit, onOpenSftp: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(TabStripBg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Rounded.ArrowBackIosNew,
                contentDescription = "返回",
                tint = TabTextNormal,
                modifier = Modifier.size(18.dp),
            )
        }

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(start = 2.dp, end = 2.dp),
        ) {
            itemsIndexed(
                items = TerminalTabs.sessions,
                key = { _, session -> session.host.id },
            ) { index, session ->
                TerminalTabItem(
                    title = session.host.alias,
                    state = session.state,
                    selected = index == TerminalTabs.selectedIndex,
                    onSelect = { TerminalTabs.select(index) },
                    onClose = { TerminalTabs.close(index) },
                )
            }
        }

        // SFTP 入口：终端 Tab 旁的文件夹图标
        IconButton(onClick = onOpenSftp, enabled = TerminalTabs.selected != null) {
            Icon(
                Icons.Rounded.FolderOpen,
                contentDescription = "SFTP 文件管理",
                tint = if (TerminalTabs.selected != null) TabStripAdd else Color(0xFF3A3F47),
            )
        }

        IconButton(onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = "打开新会话", tint = TabStripAdd)
        }
    }
}

@Composable
private fun TerminalTabItem(
    title: String,
    state: ConnState,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(if (selected) TabSelectedBg else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 状态点
        Box(
            Modifier
                .size(7.dp)
                .background(Color(connStateColor(state).toLong()), CircleShape),
        )
        Text(
            title,
            fontSize = 12.sp,
            color = if (selected) TabTextSelected else TabTextNormal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .widthIn(max = 120.dp),
        )
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "关闭",
                tint = TabTextNormal,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

/* ============================================================
 * 终端渲染区：纯黑底 · 绿字 · 等宽字体
 * ============================================================ */

@Composable
private fun TerminalView(session: TerminalSession) {
    val buffer = session.buffer
    val version = buffer.version
    val annotated = remember(version) { buffer.build() }
    val scroll = rememberScrollState()

    // 有新输出时自动滚到底部
    LaunchedEffect(version) {
        withFrameNanos { }
        scroll.scrollTo(scroll.maxValue)
    }

    val fs = TerminalPrefs.fontSize
    Text(
        annotated,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(10.dp),
        color = TerminalDefaultFg,
        fontFamily = FontFamily.Monospace,
        fontSize = fs.sp,
        lineHeight = (fs * 1.35f).sp,
    )
}

/** 连接中 / 失败 / 关闭状态的悬浮提示 */
@Composable
private fun ConnectionStatusOverlay(session: TerminalSession) {
    when (session.state) {
        ConnState.CONNECTED -> {}
        ConnState.CONNECTING -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = TerminalDefaultFg, modifier = Modifier.size(28.dp))
            Text("正在连接 ${session.host.hostname}…", fontSize = 12.sp, color = TabTextNormal)
        }
        ConnState.FAILED -> Column(
            Modifier
                .fillMaxSize()
                .clickable(enabled = false) { },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "连接失败",
                color = Color(0xFFFF5370),
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            )
            Text(
                session.lastError,
                fontSize = 11.sp,
                color = TabTextNormal,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            OutlinedButton(
                onClick = { session.reconnect() },
                modifier = Modifier.padding(top = 10.dp),
            ) { Text("重连") }
        }
        ConnState.CLOSED -> Text(
            "会话已关闭",
            fontSize = 12.sp,
            color = TabTextNormal,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun TerminalEmpty(onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("没有会话", color = TabTextNormal, fontSize = 14.sp)
        OutlinedButton(onClick = onOpen, modifier = Modifier.padding(top = 12.dp)) {
            Text("打开主机")
        }
    }
}

/* ============================================================
 * 命令输入行
 * ============================================================ */

@Composable
private fun CmdInputRow(session: TerminalSession) {
    var text by remember { mutableStateOf("") }

    fun submit() {
        session.send(text + "\n")
        text = ""
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF171A20))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("❯", color = TerminalDefaultFg, fontFamily = FontFamily.Monospace)
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
            placeholder = { Text("输入命令…", fontSize = 12.sp, color = Color(0xFF5A6069)) },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TerminalDefaultFg,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = TerminalDefaultFg,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        IconButton(onClick = { submit() }, enabled = session.state == ConnState.CONNECTED) {
            Icon(
                Icons.Rounded.Send,
                contentDescription = "发送",
                tint = if (session.state == ConnState.CONNECTED) TerminalDefaultFg else Color(0xFF45494F),
            )
        }
    }
}

/* ============================================================
 * Termius 风格扩展按键栏（浅灰底）
 * ============================================================ */

private val BarBg = Color(0xFF23262D)
private val BarBgLight = Color(0xFF2C3037)
private val BarLabel = Color(0xFFAEB6BF)
private val BarLabelDim = Color(0xFF7A818B)
private val CtrlHintBg = Color(0xFF1B1E24)

@Composable
fun ExtraKeysBar(session: TerminalSession) {
    var ctrl by remember { mutableStateOf(false) }

    fun sendRaw(s: String) = session.send(s)

    /** 发送带 Ctrl 修饰的按键后关闭 Ctrl 灯 */
    fun sendCtrl(s: String) {
        session.send(s)
        ctrl = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(BarBg),
    ) {
        // Ctrl 激活时出现字母行：Ctrl+C / D / Z / L …直接映射 0x03/0x04/0x1A/0x0C
        if (ctrl) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(CtrlHintBg)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Ctrl 已激活", fontSize = 10.sp, color = TerminalDefaultFg)
                Text("  →  发送", fontSize = 10.sp, color = BarLabelDim)
                listOf('C', 'D', 'Z', 'L', 'U', 'W').forEach { c ->
                    KeyCap(label = c.toString(), fg = TerminalDefaultFg) {
                        sendCtrl(Ansi.ctrlLetter(c))
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // —— 左：图标区 ——
            KeyCapIcon(Icons.Rounded.KeyboardTab, "Tab") { sendRaw(Ansi.TAB) }
            KeyCapIcon(Icons.Rounded.FirstPage, "Home") { sendRaw(Ansi.HOME) }
            KeyCapIcon(Icons.Rounded.LastPage, "End") { sendRaw(Ansi.END) }

            Spacer( Modifier.weight(1f) )

            // —— 中：控制区 ——
            KeyCap(label = "⇧Tab", fg = BarLabel) { sendRaw(Ansi.SHIFT_TAB) }
            KeyCap(
                label = "Ctrl",
                fg = if (ctrl) Color(0xFF000000) else BarLabel,
                bg = if (ctrl) TerminalDefaultFg else BarBgLight,
            ) { ctrl = !ctrl }
            KeyCap(label = "Esc", fg = BarLabel) { sendCtrl(Ansi.ESCAPE) }
            KeyCap(label = "/", fg = BarLabel) {
                if (ctrl) sendCtrl(Ansi.ctrlLetter('/')) else sendRaw("/")
            }
            // Ctrl+X 常用剪切（0x18）
            KeyCap(label = "⌃X", fg = BarLabelDim) {
                sendCtrl(Ansi.ctrlLetter('X'))
            }

            Spacer( Modifier.weight(1f) )

            // —— 右：方向键区 ——
            ArrowCluster(ctrl = ctrl) { dir ->
                session.send(Ansi.arrow(dir, ctrl))
                ctrl = false
            }
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    fg: Color,
    bg: Color = BarBgLight,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.sp, color = fg, maxLines = 1)
    }
}

@Composable
private fun KeyCapIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(BarBgLight)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = BarLabel, modifier = Modifier.size(16.dp))
    }
}

/** 右侧方向键：上排 ↑，下排 ← ↓ → */
@Composable
private fun ArrowCluster(ctrl: Boolean, onArrow: (Char) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniArrow(Icons.Rounded.KeyboardArrowUp, "↑") { onArrow('A') }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniArrow(Icons.Rounded.KeyboardArrowLeft, "←") { onArrow('D') }
            MiniArrow(Icons.Rounded.KeyboardArrowDown, "↓") { onArrow('B') }
            MiniArrow(Icons.Rounded.KeyboardArrowRight, "→") { onArrow('C') }
        }
    }
}

@Composable
private fun MiniArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 30.dp, height = 22.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(BarBgLight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = BarLabel, modifier = Modifier.size(15.dp))
    }
}

/* ============================================================
 * 主机选择对话框（+ 号 → 打开新会话）
 * ============================================================ */

@Composable
private fun HostPickerDialog(db: Db, onOpen: (Host) -> Unit, onDismiss: () -> Unit) {
    val hosts = remember { db.listHosts() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("打开主机") },
        text = {
            Column {
                if (hosts.isEmpty()) {
                    Text("还没有主机，请到「主机」页新建。", fontSize = 13.sp)
                } else {
                    hosts.forEach { host ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(host) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                host.alias,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${host.username}@${host.hostname}",
                                fontSize = 11.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
