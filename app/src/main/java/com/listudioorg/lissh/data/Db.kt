package com.listudioorg.lissh.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 纯明文 SQLite 存储（绝不加密）。
 *
 * 表1 hosts: id, alias, hostname, port, username, auth_type, password, key_id
 * 表2 keys:  id, name, private_key
 */
class Db(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "lissh.db"
        const val DB_VERSION = 1

        private val HOST_PROJ = arrayOf(
            "id", "alias", "hostname", "port", "username", "auth_type", "password", "key_id"
        )
        private val KEY_PROJ = arrayOf("id", "name", "private_key")
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE hosts (
                id        INTEGER PRIMARY KEY AUTOINCREMENT,
                alias     TEXT    NOT NULL DEFAULT '',
                hostname  TEXT    NOT NULL DEFAULT '',
                port      INTEGER NOT NULL DEFAULT 22,
                username  TEXT    NOT NULL DEFAULT '',
                auth_type TEXT    NOT NULL DEFAULT '密码',
                password  TEXT    NOT NULL DEFAULT '',
                key_id    INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE keys (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                name        TEXT NOT NULL DEFAULT '',
                private_key TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 需求即明文单版本库：不做迁移
    }

    // ---------------- hosts ----------------

    fun listHosts(): List<Host> {
        val out = ArrayList<Host>()
        readableDatabase.query("hosts", HOST_PROJ, null, null, null, null, "alias COLLATE NOCASE ASC")
            .use { c -> while (c.moveToNext()) out.add(hostFrom(c)) }
        return out
    }

    fun getHost(id: Long): Host? {
        readableDatabase.query("hosts", HOST_PROJ, "id=?", arrayOf(id.toString()), null, null, null, "1")
            .use { c -> if (c.moveToFirst()) return hostFrom(c) }
        return null
    }

    fun upsertHost(h: Host): Long {
        val v = ContentValues().apply {
            put("alias", h.alias)
            put("hostname", h.hostname)
            put("port", h.port)
            put("username", h.username)
            put("auth_type", h.authType.label)
            put("password", h.password)
            put("key_id", h.keyId)
        }
        val db = writableDatabase
        if (h.id > 0) {
            v.put("id", h.id)
            db.insertWithOnConflict("hosts", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            return h.id
        }
        return db.insertWithOnConflict("hosts", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteHost(id: Long) {
        writableDatabase.delete("hosts", "id=?", arrayOf(id.toString()))
    }

    fun countHosts(): Int = count("hosts")

    // ---------------- keys ----------------

    fun listKeys(): List<KeyEntry> {
        val out = ArrayList<KeyEntry>()
        readableDatabase.query("keys", KEY_PROJ, null, null, null, null, "name COLLATE NOCASE ASC")
            .use { c -> while (c.moveToNext()) out.add(keyFrom(c)) }
        return out
    }

    fun getKey(id: Long): KeyEntry? {
        if (id <= 0) return null
        readableDatabase.query("keys", KEY_PROJ, "id=?", arrayOf(id.toString()), null, null, null, "1")
            .use { c -> if (c.moveToFirst()) return keyFrom(c) }
        return null
    }

    fun upsertKey(k: KeyEntry): Long {
        val v = ContentValues().apply {
            put("name", k.name)
            put("private_key", k.privateKey)
        }
        val db = writableDatabase
        if (k.id > 0) {
            v.put("id", k.id)
            db.insertWithOnConflict("keys", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            return k.id
        }
        return db.insertWithOnConflict("keys", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteKey(id: Long) {
        writableDatabase.delete("keys", "id=?", arrayOf(id.toString()))
    }

    fun countKeys(): Int = count("keys")

    /** 危险操作：清空全部数据。 */
    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM hosts")
        writableDatabase.execSQL("DELETE FROM keys")
        writableDatabase.execSQL("DELETE FROM sqlite_sequence WHERE name IN ('hosts','keys')")
    }

    // ---------------- 内部 ----------------

    private fun count(table: String): Int {
        var n = 0
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
            if (c.moveToFirst()) n = c.getInt(0)
        }
        return n
    }

    private fun hostFrom(c: Cursor): Host = Host(
        id = c.getLong(0),
        alias = c.getString(1) ?: "",
        hostname = c.getString(2) ?: "",
        port = c.getInt(3),
        username = c.getString(4) ?: "",
        authType = AuthType.from(c.getString(5) ?: "密码"),
        password = c.getString(6) ?: "",
        keyId = c.getLong(7),
    )

    private fun keyFrom(c: Cursor): KeyEntry = KeyEntry(
        id = c.getLong(0),
        name = c.getString(1) ?: "",
        privateKey = c.getString(2) ?: "",
    )
}
