package com.timelordtty.mydca.outbox

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.timelordtty.mydca.data.dto.AccountingIntentDto
import okio.Buffer

/**
 * Outbox 负载编解码。
 *
 * 这里显式列出允许持久化的字段白名单，保证 Token、密码、Cookie、图片/URI、
 * 通知完整原文等内容连字段都没有，不可能被写入队列。
 * 编码结果仍然只在加密后落盘；明文不会进入 SharedPreferences 或日志。
 *
 * 读到的版本号不匹配或负载损坏时返回空队列（宁可丢弃本地队列，也不读入不可信内容）。
 */
object DraftOutboxCodec {
    private const val VERSION = 1L
    private const val FIELD_VERSION = "version"
    private const val FIELD_ENTRIES = "entries"

    fun encode(entries: List<DraftOutboxEntry>): String {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { writer ->
            writer.beginObject()
            writer.name(FIELD_VERSION).value(VERSION)
            writer.name(FIELD_ENTRIES).beginArray()
            entries.forEach { writeEntry(writer, it) }
            writer.endArray()
            writer.endObject()
        }
        return buffer.readUtf8()
    }

    fun decode(json: String?): List<DraftOutboxEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            JsonReader.of(Buffer().apply { writeUtf8(json) }).use { reader ->
                reader.beginObject()
                var version = 0L
                val entries = mutableListOf<DraftOutboxEntry>()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        FIELD_VERSION -> version = reader.nextLongOrNull() ?: 0L
                        FIELD_ENTRIES -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                readEntry(reader)?.let(entries::add)
                            }
                            reader.endArray()
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                if (version == VERSION) entries else emptyList()
            }
        }.getOrDefault(emptyList())
    }

    private fun writeEntry(writer: JsonWriter, entry: DraftOutboxEntry) {
        writer.beginObject()
        writer.name("id").value(entry.id)
        writer.name("sourceType").value(entry.sourceType)
        writer.name("sourceRef").value(entry.sourceRef)
        writer.name("origin").value(entry.origin.name)
        writer.name("summary").value(entry.summary)
        writer.name("status").value(entry.status.name)
        writer.name("createdAt").value(entry.createdAt)
        writer.name("retryCount").value(entry.retryCount.toLong())
        writer.name("nextRetryAt").value(entry.nextRetryAt)
        writer.name("lastAttemptAt").writeNullableLong(entry.lastAttemptAt)
        writer.name("lastErrorCategory").writeNullableString(entry.lastErrorCategory?.name)
        writer.name("lastErrorMessage").writeNullableString(entry.lastErrorMessage)
        writer.name("intent")
        writeIntent(writer, entry.intent)
        writer.endObject()
    }

    private fun writeIntent(writer: JsonWriter, intent: AccountingIntentDto) {
        writer.beginObject()
        writer.name("sourceType").writeNullableString(intent.sourceType)
        writer.name("sourceRef").writeNullableString(intent.sourceRef)
        writer.name("rawInput").writeNullableString(intent.rawInput)
        writer.name("txnType").writeNullableString(intent.txnType)
        writer.name("amount").writeNullableDouble(intent.amount)
        writer.name("note").writeNullableString(intent.note)
        writer.name("accountId").writeNullableLong(intent.accountId)
        writer.name("accountNameHint").writeNullableString(intent.accountNameHint)
        writer.name("confidence").writeNullableDouble(intent.confidence)
        writer.name("parsedPayloadJson").writeNullableString(intent.parsedPayloadJson)
        writer.name("missingFields").beginArray()
        intent.missingFields.forEach { field -> writer.value(field) }
        writer.endArray()
        writer.endObject()
    }

    private fun readEntry(reader: JsonReader): DraftOutboxEntry? {
        var id: String? = null
        var sourceType: String? = null
        var sourceRef: String? = null
        var origin: DraftOutboxOrigin? = null
        var summary = ""
        var status = DraftOutboxStatus.PENDING
        var createdAt = 0L
        var retryCount = 0
        var nextRetryAt = 0L
        var lastAttemptAt: Long? = null
        var lastErrorCategory: DraftOutboxErrorCategory? = null
        var lastErrorMessage: String? = null
        var intent: AccountingIntentDto? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.nextStringOrNull()
                "sourceType" -> sourceType = reader.nextStringOrNull()
                "sourceRef" -> sourceRef = reader.nextStringOrNull()
                "origin" -> origin = reader.nextStringOrNull()?.let { name ->
                    DraftOutboxOrigin.entries.firstOrNull { it.name == name }
                }
                "summary" -> summary = reader.nextStringOrNull().orEmpty()
                "status" -> status = reader.nextStringOrNull()?.let { name ->
                    DraftOutboxStatus.entries.firstOrNull { it.name == name }
                } ?: DraftOutboxStatus.PENDING
                "createdAt" -> createdAt = reader.nextLongOrNull() ?: 0L
                "retryCount" -> retryCount = (reader.nextLongOrNull() ?: 0L).toInt().coerceAtLeast(0)
                "nextRetryAt" -> nextRetryAt = reader.nextLongOrNull() ?: 0L
                "lastAttemptAt" -> lastAttemptAt = reader.nextLongOrNull()
                "lastErrorCategory" -> lastErrorCategory = reader.nextStringOrNull()?.let { name ->
                    DraftOutboxErrorCategory.entries.firstOrNull { it.name == name }
                }
                "lastErrorMessage" -> lastErrorMessage = reader.nextStringOrNull()
                "intent" -> intent = readIntent(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return DraftOutboxEntry(
            id = id?.takeIf { it.isNotBlank() } ?: return null,
            sourceType = sourceType?.takeIf { it.isNotBlank() } ?: return null,
            sourceRef = sourceRef?.takeIf { it.isNotBlank() } ?: return null,
            origin = origin ?: return null,
            summary = summary,
            intent = intent ?: return null,
            createdAt = createdAt,
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
            lastErrorCategory = lastErrorCategory,
            lastErrorMessage = lastErrorMessage,
            lastAttemptAt = lastAttemptAt,
            status = status,
        )
    }

    private fun readIntent(reader: JsonReader): AccountingIntentDto? {
        if (reader.peek() == JsonReader.Token.NULL) {
            reader.nextNull<Any>()
            return null
        }
        var sourceType: String? = null
        var sourceRef: String? = null
        var rawInput: String? = null
        var txnType: String? = null
        var amount: Double? = null
        var note: String? = null
        var accountId: Long? = null
        var accountNameHint: String? = null
        var confidence: Double? = null
        var parsedPayloadJson: String? = null
        val missingFields = mutableListOf<String>()

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "sourceType" -> sourceType = reader.nextStringOrNull()
                "sourceRef" -> sourceRef = reader.nextStringOrNull()
                "rawInput" -> rawInput = reader.nextStringOrNull()
                "txnType" -> txnType = reader.nextStringOrNull()
                "amount" -> amount = reader.nextDoubleOrNull()
                "note" -> note = reader.nextStringOrNull()
                "accountId" -> accountId = reader.nextLongOrNull()
                "accountNameHint" -> accountNameHint = reader.nextStringOrNull()
                "confidence" -> confidence = reader.nextDoubleOrNull()
                "parsedPayloadJson" -> parsedPayloadJson = reader.nextStringOrNull()
                "missingFields" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        reader.nextStringOrNull()?.let(missingFields::add)
                    }
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return AccountingIntentDto(
            sourceType = sourceType,
            sourceRef = sourceRef,
            rawInput = rawInput,
            txnType = txnType,
            amount = amount,
            note = note,
            accountId = accountId,
            accountNameHint = accountNameHint,
            confidence = confidence,
            missingFields = missingFields,
            parsedPayloadJson = parsedPayloadJson,
        )
    }

    private fun JsonWriter.writeNullableString(text: String?) {
        if (text == null) nullValue() else value(text)
    }

    private fun JsonWriter.writeNullableLong(number: Long?) {
        if (number == null) nullValue() else value(number)
    }

    private fun JsonWriter.writeNullableDouble(number: Double?) {
        if (number == null) nullValue() else value(number)
    }

    private fun JsonReader.nextStringOrNull(): String? =
        if (peek() == JsonReader.Token.NULL) {
            nextNull<Any>()
            null
        } else {
            nextString()
        }

    private fun JsonReader.nextLongOrNull(): Long? = when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any>()
            null
        }
        JsonReader.Token.NUMBER -> nextLong()
        JsonReader.Token.STRING -> nextString().toLongOrNull()
        else -> {
            skipValue()
            null
        }
    }

    private fun JsonReader.nextDoubleOrNull(): Double? = when (peek()) {
        JsonReader.Token.NULL -> {
            nextNull<Any>()
            null
        }
        JsonReader.Token.NUMBER -> nextDouble()
        JsonReader.Token.STRING -> nextString().toDoubleOrNull()
        else -> {
            skipValue()
            null
        }
    }
}
