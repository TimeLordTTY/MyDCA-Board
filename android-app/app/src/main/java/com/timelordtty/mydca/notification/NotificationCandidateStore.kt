package com.timelordtty.mydca.notification

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

object NotificationCandidatePolicy {
    const val MAX_CANDIDATES = 50
    const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun normalize(items: List<NotificationCandidate>, now: Long): List<NotificationCandidate> = items
        .filter { now - it.postedAt <= RETENTION_MILLIS }
        .distinctBy { it.fingerprint }
        .sortedByDescending { it.postedAt }
        .take(MAX_CANDIDATES)
}

/** SharedPreferences 只保存脱敏结构化候选，不保存完整通知原文。 */
object NotificationCandidateStore {
    private const val PREFS_NAME = "notification_candidates_v03"
    private const val KEY_ITEMS = "items"
    private val mutableCandidates = MutableStateFlow<List<NotificationCandidate>>(emptyList())
    private var appContext: Context? = null

    val candidates: StateFlow<List<NotificationCandidate>> = mutableCandidates

    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext
        mutableCandidates.value = NotificationCandidatePolicy.normalize(read(), System.currentTimeMillis())
        persist()
    }

    @Synchronized
    fun add(candidate: NotificationCandidate): Boolean {
        if (mutableCandidates.value.any { it.fingerprint == candidate.fingerprint }) return false
        mutableCandidates.value = NotificationCandidatePolicy.normalize(
            listOf(candidate) + mutableCandidates.value,
            System.currentTimeMillis(),
        )
        persist()
        return true
    }

    @Synchronized
    fun markDraftCreated(id: String, draftId: Long) = update(id) {
        it.copy(status = NotificationCandidateStatus.DRAFT_CREATED, createdDraftId = draftId)
    }

    @Synchronized
    fun dismiss(id: String) = update(id) { it.copy(status = NotificationCandidateStatus.DISMISSED) }

    @Synchronized
    fun markReminderSent(id: String) = update(id) { it.copy(reminderSent = true) }

    private fun update(id: String, transform: (NotificationCandidate) -> NotificationCandidate) {
        mutableCandidates.value = mutableCandidates.value.map { if (it.id == id) transform(it) else it }
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        val array = JSONArray()
        mutableCandidates.value.forEach { candidate ->
            array.put(JSONObject().apply {
                put("id", candidate.id); put("fingerprint", candidate.fingerprint)
                put("packageName", candidate.packageName); put("appLabel", candidate.appLabel)
                put("postedAt", candidate.postedAt); put("titleSnippet", candidate.titleSnippet)
                put("textSnippet", candidate.textSnippet); put("amount", candidate.amount)
                put("sourceHint", candidate.sourceHint); put("status", candidate.status.name)
                put("createdDraftId", candidate.createdDraftId); put("reminderSent", candidate.reminderSent)
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun read(): List<NotificationCandidate> {
        val context = appContext ?: return emptyList()
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                NotificationCandidate(
                    id = item.getString("id"), fingerprint = item.optString("fingerprint", item.getString("id")),
                    packageName = item.getString("packageName"), appLabel = item.optNullableString("appLabel"),
                    postedAt = item.getLong("postedAt"), titleSnippet = item.optNullableString("titleSnippet"),
                    textSnippet = item.optNullableString("textSnippet"), amount = item.optNullableString("amount"),
                    sourceHint = item.optNullableString("sourceHint"),
                    status = runCatching { NotificationCandidateStatus.valueOf(item.optString("status", "NEW")) }.getOrDefault(NotificationCandidateStatus.NEW),
                    createdDraftId = if (item.isNull("createdDraftId")) null else item.optLong("createdDraftId"),
                    reminderSent = item.optBoolean("reminderSent", false),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
}
