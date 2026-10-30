package com.listudioorg.lissh.data

/** 认证方式（数据库以中文 label 明文存储）。 */
enum class AuthType(val label: String) {
    PASSWORD("密码"),
    KEY("密钥");

    companion object {
        fun from(value: String): AuthType =
            entries.firstOrNull { it.label == value || it.name == value } ?: PASSWORD
    }
}

/** 主机。密码字段按需求为明文存储。 */
data class Host(
    val id: Long,
    val alias: String,
    val hostname: String,
    val port: Int,
    val username: String,
    val authType: AuthType,
    val password: String,
    val keyId: Long,
)

/** 私钥。private_key 字段按需求为明文存储（完整 PEM 文本）。 */
data class KeyEntry(
    val id: Long,
    val name: String,
    val privateKey: String,
)
