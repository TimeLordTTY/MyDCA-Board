package com.timelordtty.mydca.core.security

import android.content.Context
import android.content.SharedPreferences

/**
 * 最小键值持久化边界。
 * 只用于存放密文与随机 IV：实现不得写入 Token、密码、Cookie、图片/URI 或通知原文等明文。
 */
interface SecureKeyValueStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String)

    fun remove(vararg keys: String)
}

/** SharedPreferences 适配器，Android 生产实现。 */
class SharedPreferencesKeyValueStore(
    private val preferences: SharedPreferences,
) : SecureKeyValueStore {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(vararg keys: String) {
        val editor = preferences.edit()
        keys.forEach { key -> editor.remove(key) }
        editor.apply()
    }

    companion object {
        fun create(context: Context, name: String): SharedPreferencesKeyValueStore =
            SharedPreferencesKeyValueStore(
                context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE),
            )
    }
}
