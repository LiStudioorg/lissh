package com.listudioorg.lissh

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import com.listudioorg.lissh.data.Db

/**
 * 应用入口：纯本地运行，无任何账号/网络服务（除 SSH 连接本身）。
 * SQLite 数据库全程明文存储（应用需求如此）。
 */
class LiSSHApp : Application() {

    val db: Db by lazy { Db(this) }

    override fun onCreate() {
        super.onCreate()
        TerminalPrefs.init(this)
    }
}

/** 终端偏好（字号 / 背景色），全部持久化到 SharedPreferences，运行时可观察。 */
object TerminalPrefs {

    private val fontSizeState = mutableFloatStateOf(13.0f)
    private val bgColorState = mutableIntStateOf(0xFF000000L.toInt())
    private var sp: SharedPreferences? = null

    var fontSize: Float
        get() = fontSizeState.floatValue
        set(value) {
            fontSizeState.floatValue = value.coerceIn(9.0f, 24.0f)
            sp?.edit()?.putFloat("terminal_font_size", fontSizeState.floatValue)?.apply()
        }

    /** ARGB 背景色（默认纯黑 0xFF000000） */
    var bgColor: Int
        get() = bgColorState.intValue
        set(value) {
            bgColorState.intValue = value
            sp?.edit()?.putInt("terminal_bg_color", value)?.apply()
        }

    fun init(context: Context) {
        if (sp != null) return
        sp = context.getSharedPreferences("lissh_prefs", Context.MODE_PRIVATE)
        fontSizeState.floatValue =
            sp!!.getFloat("terminal_font_size", 13.0f).coerceIn(9.0f, 24.0f)
        bgColorState.intValue =
            sp!!.getInt("terminal_bg_color", 0xFF000000L.toInt())
    }
}
