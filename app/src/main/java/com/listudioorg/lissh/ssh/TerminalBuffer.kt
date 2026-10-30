package com.listudioorg.lissh.ssh

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

/** 终端默认前景色（绿字） */
val TerminalDefaultFg = Color(0xFF00E871)
/** 终端默认背景（纯黑） */
val TerminalBg = Color(0xFF000000)

/** 本地提示行（灰色） */
private val LocalInfoColor = Color(0xFF9AA4AE)
/** 本地错误行（红色） */
private val LocalErrorColor = Color(0xFFFF5370)

/**
 * 极简终端缓冲区：
 * - 接收远程输出块，解析 ANSI 转义序列（SGR 颜色 / 擦除 / \r \b \t 等）；
 * - 不追踪光标与网格（"模拟终端输出"），只做流式富文本；
 * - 线程安全：远程读线程写入，UI 线程 build() 渲染。
 */
class TerminalBuffer {

    /** 每次 Append 自增，驱动 Compose 重组 */
    var version by mutableIntStateOf(0)
        private set

    private val text = StringBuilder()
    private val spans = ArrayList<Span>()

    private enum class Mode { TEXT, ESC, CSI, OSC, OSC_ESC }
    private var mode = Mode.TEXT
    private val csiBuf = StringBuilder()

    // 当前写入样式
    private var curFg: Color? = null
    private var curBg: Color? = null
    private var curBold = false

    // 正在构建的同样式区间 [segStart, segEnd)
    private var segStart = -1
    private var segEnd = -1

    private data class Span(val start: Int, val end: Int, val fg: Color?, val bg: Color?, val bold: Boolean)

    private val basePalette = listOf(
        Color(0xFF1A1C21), // 0 black
        Color(0xFFE85C5C), // 1 red
        Color(0xFF33C868), // 2 green
        Color(0xFFE7CE7C), // 3 yellow
        Color(0xFF5DA7F0), // 4 blue
        Color(0xFFC77BE0), // 5 magenta
        Color(0xFF4ED6D6), // 6 cyan
        Color(0xFFD0D5DC), // 7 white
    )
    private val brightPalette = listOf(
        Color(0xFF707880),
        Color(0xFFFF7B7B),
        Color(0xFF66F08E),
        Color(0xFFFFE9A3),
        Color(0xFF7FBDF8),
        Color(0xFFE2A7EF),
        Color(0xFF6AE1E1),
        Color(0xFFFFFFFF),
    )

    // ---------------- 公共 API ----------------

    /** 追加一段远程输出（在 IO 线程调用） */
    @Synchronized
    fun appendChunk(chunk: String) {
        var i = 0
        val n = chunk.length
        while (i < n) {
            val c = chunk[i]
            when (mode) {
                Mode.TEXT -> {
                    when (c) {
                        '\u001B' -> mode = Mode.ESC
                        '\r' -> carriageReturn()
                        '\b' -> backspace()
                        '\t' -> appendPlain("    ")
                        '\n' -> appendPlain("\n")
                        else -> if (c.code >= 32) appendStyled(c.toString()) // 忽略其余 C0 控制符
                    }
                }
                Mode.ESC -> {
                    when (c) {
                        '[' -> { mode = Mode.CSI; csiBuf.setLength(0) }
                        ']' -> mode = Mode.OSC
                        '(' -> { /* 字符集选择：吞掉下一个字节 */ if (i + 1 < n) i++ }
                        ')' -> { if (i + 1 < n) i++ }
                        else -> mode = Mode.TEXT // 其它 ESC 序列忽略
                    }
                }
                Mode.CSI -> {
                    if (c.code in 0x40..0x7E) { // final byte
                        dispatchCsi(c)
                        mode = Mode.TEXT
                    } else {
                        csiBuf.append(c)
                    }
                }
                Mode.OSC -> {
                    when (c) {
                        '\u0007' -> mode = Mode.TEXT
                        '\u001B' -> mode = Mode.OSC_ESC
                        else -> {} // 标题等 OSC 内容忽略
                    }
                }
                Mode.OSC_ESC -> mode = Mode.TEXT // ESC\ 结束 OSC（简化处理）
            }
            i++
        }
        trimIfNeeded()
        version++
    }

    /** 本地提示行（连接中等），绿色以外的灰字 */
    fun info(msg: String) = appendLocal("· $msg\n", LocalInfoColor, bold = false)

    /** 本地错误行（红字） */
    fun err(msg: String) = appendLocal("✕ $msg\n", LocalErrorColor, bold = true)

    @Synchronized
    fun appendLocal(msg: String, color: Color, bold: Boolean) {
        flushSeg()
        val of = curFg; val ob = curBg; val obold = curBold
        curFg = color; curBg = null; curBold = bold
        for (c in msg) when (c) {
            '\n' -> appendPlain("\n")
            '\b' -> backspace()
            '\r' -> carriageReturn()
            else -> if (c.code >= 32) appendStyled(c.toString())
        }
        flushSeg()
        curFg = of; curBg = ob; curBold = obold
        version++
    }

    /** 渲染为可直接交给 Text 的 AnnotatedString（UI 线程调用） */
    @Synchronized
    fun build(): AnnotatedString {
        flushSeg()
        val out = ArrayList<Pair<IntRange, SpanStyle>>(spans.size)
        for (s in spans) {
            val start = s.start.coerceIn(0, text.length)
            val end = s.end.coerceIn(start, text.length)
            if (end <= start) continue
            out.add(
                IntRange(start, end - 1) to SpanStyle(
                    color = s.fg ?: TerminalDefaultFg,
                    background = s.bg ?: Color.Unspecified,
                    fontWeight = if (s.bold) FontWeight.Bold else null,
                )
            )
        }
        val builder = AnnotatedString.Builder()
        builder.append(text.toString())
        for ((range, style) in out) {
            builder.addStyle(style, range.first, range.last + 1)
        }
        return builder.toAnnotatedString()
    }

    // ---------------- 解析内部 ----------------

    private fun dispatchCsi(final: Char) {
        val raw = csiBuf.toString()
        val params: List<Int?> = raw.split(';').map { p ->
            p.filter { it.isDigit() }.ifEmpty { "" }.toIntOrNull()
        }
        when (final) {
            'm' -> applySgr(params)
            'K' -> {
                // 0: 清除到行尾；1: 清除到行首；2: 清除整行 —— 无光标模型，统一清除当前行
                clearCurrentLine()
            }
            'J' -> {
                val p = params.getOrNull(0) ?: 0
                // 0 清除到屏尾；2 整屏清除 —— 简化为：0 清当前行，2 清全部
                if (p == 2) clearAll() else clearCurrentLine()
            }
            else -> {} // 光标移动 CSI[A-D]、CSI H/L/M/P 等无网格模型，忽略
        }
    }

    private fun applySgr(params: List<Int?>) {
        flushSeg()
        var i = 0
        while (i < params.size) {
            val p = params[i] ?: 0
            when {
                p == 0 -> { curFg = null; curBg = null; curBold = false }
                p == 1 -> curBold = true
                p == 4 -> {} // 下划线：忽略（SpanStyle 可扩展）
                p == 22 -> curBold = false
                p in 30..37 -> curFg = basePalette[p - 30]
                p == 39 -> curFg = null
                p in 40..47 -> curBg = basePalette[p - 40]
                p == 49 -> curBg = null
                p in 90..97 -> curFg = brightPalette[p - 90]
                p in 100..107 -> curBg = brightPalette[p - 100]
                p == 38 || p == 48 -> {
                    val c: Color? = when {
                        i + 1 < params.size && params[i + 1] == 5 -> {
                            val idx = (params.getOrNull(i + 2) ?: 0).coerceIn(0, 255)
                            i += 2
                            color256(idx)
                        }
                        i + 1 < params.size && params[i + 1] == 2 -> {
                            val r = (params.getOrNull(i + 2) ?: 0).coerceIn(0, 255)
                            val g = (params.getOrNull(i + 3) ?: 0).coerceIn(0, 255)
                            val b = (params.getOrNull(i + 4) ?: 0).coerceIn(0, 255)
                            i += 4
                            Color(r, g, b)
                        }
                        else -> null
                    }
                    if (c != null) { if (p == 38) curFg = c else curBg = c }
                }
            }
            i++
        }
    }

    private fun color256(idx: Int): Color = when {
        idx < 8 -> basePalette[idx]
        idx < 16 -> brightPalette[idx - 8]
        idx < 232 -> {
            val n = idx - 16
            val r = (n / 36) * 51
            val g = ((n % 36) / 6) * 51
            val b = (n % 6) * 51
            Color(r, g, b)
        }
        else -> {
            val v = (idx - 232) * 10 + 8
            Color(v, v, v)
        }
    }

    // ---------------- 写入内部 ----------------

    private fun appendStyled(s: String) {
        val start = text.length
        text.append(s)
        if (segStart < 0) { segStart = start }
        segEnd = text.length
    }

    private fun appendPlain(s: String) = appendStyled(s)

    /** '\r'：截断当前行（从最近一个 \n 之后全部删除） */
    private fun carriageReturn() {
        flushSeg()
        val lineStart = text.lastIndexOf('\n') + 1
        val end = text.length
        if (end > lineStart) deleteRange(lineStart, end - lineStart)
    }

    /** '\b'：删除前一个字符（不动 \n） */
    private fun backspace() {
        flushSeg()
        if (text.isEmpty()) return
        val last = text[text.length - 1]
        if (last == '\n') return
        deleteRange(text.length - 1, 1)
    }

    private fun clearCurrentLine() {
        flushSeg()
        val lineStart = text.lastIndexOf('\n') + 1
        val end = text.length
        if (end > lineStart) deleteRange(lineStart, end - lineStart)
    }

    private fun clearAll() {
        flushSeg()
        text.setLength(0)
        spans.clear()
        segStart = -1; segEnd = -1
    }

    /** 删除 [pos, pos+len)，并平移覆盖其上的样式区间 */
    private fun deleteRange(pos: Int, len: Int) {
        if (len <= 0) return
        text.delete(pos, pos + len)
        val cutEnd = pos + len
        val it = spans.listIterator()
        while (it.hasNext()) {
            val s = it.next()
            when {
                s.end <= pos -> {} // 完全在删除区之前
                s.start >= cutEnd -> it.set(s.copy(start = s.start - len, end = s.end - len))
                else -> {
                    val ns = s.start.coerceAtMost(pos)
                    val ne = pos
                    if (ne <= ns) it.remove() else it.set(s.copy(start = ns, end = ne))
                }
            }
        }
        if (segStart >= 0) {
            segStart = segStart.coerceIn(0, pos)
            segEnd = segEnd.coerceIn(segStart, pos)
            if (segEnd <= segStart) { segStart = -1; segEnd = -1 }
        }
    }

    private fun flushSeg() {
        if (segStart >= 0 && segEnd > segStart && text.isNotEmpty()) {
            spans.add(Span(segStart.coerceIn(0, text.length), segEnd.coerceIn(0, text.length), curFg, curBg, curBold))
        }
        segStart = -1
        segEnd = -1
    }

    /** 回滚缓冲上限，防止超长 cat 大文件撑爆内存 */
    private fun trimIfNeeded() {
        val limit = 200_000
        if (text.length <= limit) return
        val drop = text.length - 120_000
        deleteRange(0, drop)
        // 丢弃旧的颜色区间（可读性优先于🌈保色）
        spans.clear()
        segStart = -1; segEnd = -1
    }
}
