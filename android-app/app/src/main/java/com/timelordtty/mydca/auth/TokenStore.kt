package com.timelordtty.mydca.auth

/** Token 持久化边界；生产实现必须由 Android Keystore 保护。 */
interface TokenStore {
    fun read(): String?
    fun write(token: String)
    fun clear()
}

/** JVM 测试和短生命周期预览使用的内存实现。 */
class InMemoryTokenStore(
    initialToken: String? = null,
) : TokenStore {
    var storedToken: String? = initialToken
        private set

    override fun read(): String? = storedToken

    override fun write(token: String) {
        storedToken = token
    }

    override fun clear() {
        storedToken = null
    }
}
