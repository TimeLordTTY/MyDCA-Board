package com.timelordtty.mydca.widget

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一次小组件点击请求：受控目标 + 单调 token。
 *
 * token 单调递增，保证连续两次点击同一个入口仍是两个不同事件，不会被状态流吞掉。
 */
data class WidgetNavigationRequest(
    val token: Long,
    val target: WidgetNavigationTarget,
)

/**
 * 进程内一次性的“待消费小组件目标”。
 *
 * - 只存在当前进程内存：不写偏好设置 / 文件，不上传，也不携带账户、Token、草稿或通知内容；
 * - 同一个目标只能被消费一次（[consume] 取出即清空，重复调用返回 null）；
 * - 未登录期间保留，登录成功后仍只消费一次；
 * - 进程被杀导致目标丢失时安全回到普通首页，不为跨进程恢复做任何持久化。
 */
class WidgetNavigationPendingStore {
    private val mutablePending = MutableStateFlow<WidgetNavigationRequest?>(null)
    val pending: StateFlow<WidgetNavigationRequest?> = mutablePending.asStateFlow()

    private val tokens = AtomicLong(0L)

    /** 登记一次小组件点击；传入 null（非小组件 Intent）时不覆盖仍待消费的目标。 */
    fun publish(target: WidgetNavigationTarget?): WidgetNavigationRequest? {
        val resolved = target ?: return null
        val request = WidgetNavigationRequest(token = tokens.incrementAndGet(), target = resolved)
        mutablePending.value = request
        return request
    }

    /** 取出并清空 pending；第二次调用返回 null，因此同一次点击不会被消费两次。 */
    fun consume(): WidgetNavigationRequest? {
        val request = mutablePending.value ?: return null
        mutablePending.value = null
        return request
    }

    /** 主动丢弃 pending，作为主动切页 / 返回后的兜底。 */
    fun clear() {
        mutablePending.value = null
    }

    companion object {
        /** App 进程内共享的单例：MainActivity 登记，登录后的页面消费。 */
        val instance = WidgetNavigationPendingStore()
    }
}
