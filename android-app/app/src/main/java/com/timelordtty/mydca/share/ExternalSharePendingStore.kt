package com.timelordtty.mydca.share

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一次外部分享事件的进程内快照：payload + 该事件专属的稳定 sourceRef + 单调 token。
 *
 * token 单调递增，保证连续两次内容相同的分享仍是两个不同事件，不会被状态流吞掉。
 */
data class ExternalShareCapture(
    val token: Long,
    val payload: ExternalSharePayload,
    val sourceRef: String,
)

/**
 * 进程内一次性的“待消费外部分享”。
 *
 * - 只存在于当前进程内存：不写 SharedPreferences / DataStore / 文件，不上传，不打印原文；
 * - 同一个 payload 只能被消费一次（[consume] 取出即清空，重复调用返回 null）；
 * - 未登录期间保留，登录后仍只消费一次；不为跨进程恢复持久化任何原文；
 * - 解析被安全拒绝时只保留一条中文提示，不产生任何 payload。
 */
class ExternalSharePendingStore(
    private val sourceRefFactory: (ExternalSharePayload) -> String = { payload ->
        ExternalShareSourceRef.forPayload(payload)
    },
) {
    private val mutablePending = MutableStateFlow<ExternalShareCapture?>(null)
    val pending: StateFlow<ExternalShareCapture?> = mutablePending.asStateFlow()

    private val mutableNotice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = mutableNotice.asStateFlow()

    private val tokens = AtomicLong(0L)

    /** 登记一次分享事件；成功返回本次 capture，被拒绝时返回 null 且只留中文提示。 */
    fun publish(resolution: ExternalShareResolution): ExternalShareCapture? = when (resolution) {
        is ExternalShareResolution.Accepted -> {
            val capture = captureOf(resolution.payload)
            mutablePending.value = capture
            mutableNotice.value = if (resolution.truncated) TRUNCATED_NOTICE else null
            capture
        }
        is ExternalShareResolution.Rejected -> {
            mutableNotice.value = resolution.rejection.message
            null
        }
    }

    /** 构造 capture，但不登记为待消费；供测试与调用方复用同一套 sourceRef 规则。 */
    fun captureOf(payload: ExternalSharePayload): ExternalShareCapture = ExternalShareCapture(
        token = tokens.incrementAndGet(),
        payload = payload,
        sourceRef = sourceRefFactory(payload),
    )

    /** 取出并清空 pending；第二次调用返回 null，因此同一 payload 不会被消费两次。 */
    fun consume(): ExternalShareCapture? {
        val capture = mutablePending.value ?: return null
        mutablePending.value = null
        return capture
    }

    /** 主动丢弃 pending，作为返回 / 取消 / 切换导航 / DRAFT 已创建后的兜底。 */
    fun clear() {
        mutablePending.value = null
    }

    /** 取出并清空提示；与 payload 一样只消费一次。 */
    fun consumeNotice(): String? {
        val message = mutableNotice.value
        mutableNotice.value = null
        return message
    }

    companion object {
        val TRUNCATED_NOTICE = "分享文字过长（上限 ${ExternalShareResolver.MAX_TEXT_LENGTH} 个字符），已安全截断，请复核后再解析。"

        /** App 进程内共享的单例：MainActivity 登记，登录后的采集页消费。 */
        val instance = ExternalSharePendingStore()
    }
}
