package com.timelordtty.mydca.notification

/** 进程内单飞守门，避免同一候选被并发点击生成多个草稿。 */
object DraftCreationGate {
    private val active = mutableSetOf<String>()

    @Synchronized fun tryAcquire(candidateId: String): Boolean = active.add(candidateId)
    @Synchronized fun release(candidateId: String) { active.remove(candidateId) }
}
