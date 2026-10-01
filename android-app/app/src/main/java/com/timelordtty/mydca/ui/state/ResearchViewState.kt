package com.timelordtty.mydca.ui.state

import com.timelordtty.mydca.core.network.NetworkResult
import com.timelordtty.mydca.data.dto.ResearchPlanDto
import com.timelordtty.mydca.data.dto.ResearchRunDto

/** Used directly by Compose; snapshots stay in memory and failures clear sensitive data. */
data class ResearchReadState<T>(
    val data: T? = null,
    val loading: Boolean = true,
    val error: String? = null,
) {
    fun loaded(result: NetworkResult<T>): ResearchReadState<T> = when (result) {
        is NetworkResult.Success -> ResearchReadState(data = result.data, loading = false)
        is NetworkResult.Failure -> ResearchReadState(loading = false, error = result.message)
    }
}

fun visibleResearchPlans(plans: List<ResearchPlanDto>) = plans.filter { it.status in setOf("DRAFT", "ACTIVE") }
fun planRuns(plan: ResearchPlanDto, runs: List<ResearchRunDto>) =
    runs.filter { it.researchPlanId == plan.id }.sortedByDescending { it.startedAt }

fun researchStatus(status: String?) = when (status) {
    "DRAFT" -> "草稿 DRAFT"
    "ACTIVE" -> "研究中 ACTIVE"
    "ARCHIVED" -> "已归档 ARCHIVED"
    "SUCCESS" -> "成功 SUCCESS"
    "FAILED", "FAILURE" -> "失败 $status"
    "RUNNING" -> "运行中 RUNNING"
    null -> "已读取历史中无关联运行"
    else -> "未知状态：$status"
}
