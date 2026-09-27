package com.timelordtty.mydca.outbox

/** Outbox 持久化边界；实现必须保证队列负载不明文落盘。 */
interface DraftOutboxStorage {
    fun read(): List<DraftOutboxEntry>

    fun write(entries: List<DraftOutboxEntry>)
}

/** JVM 单元测试与短生命周期预览使用的内存实现，不写任何磁盘数据。 */
class InMemoryDraftOutboxStorage(
    initial: List<DraftOutboxEntry> = emptyList(),
) : DraftOutboxStorage {
    var stored: List<DraftOutboxEntry> = initial.toList()
        private set

    override fun read(): List<DraftOutboxEntry> = stored

    override fun write(entries: List<DraftOutboxEntry>) {
        stored = entries.toList()
    }
}
