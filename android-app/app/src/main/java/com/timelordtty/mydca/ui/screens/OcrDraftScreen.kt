package com.timelordtty.mydca.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.timelordtty.mydca.data.repository.AiAccountingRepository
import com.timelordtty.mydca.ocr.MlKitImageTextRecognizer
import com.timelordtty.mydca.ui.state.OcrDraftCoordinator
import com.timelordtty.mydca.ui.state.OcrDraftStage
import java.util.UUID
import kotlinx.coroutines.launch

/** 系统 Photo Picker、本地 OCR、候选复核与 DRAFT 创建的人工闭环。 */
@Composable
fun OcrDraftScreen(
    repository: AiAccountingRepository,
    onClose: () -> Unit,
    onOpenDraft: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember { OcrDraftCoordinator() }
    val recognizer = remember { MlKitImageTextRecognizer() }
    val state by coordinator.state.collectAsState()
    var hasSelectedImage by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val requestId = UUID.randomUUID().toString()
        hasSelectedImage = true
        coordinator.selectImage(requestId)
        scope.launch {
            if (!coordinator.beginRecognition(requestId)) return@launch
            try {
                coordinator.recognitionSucceeded(requestId, recognizer.recognize(context, uri))
            } catch (_: Throwable) {
                coordinator.recognitionFailed(requestId)
            }
        }
    }

    val busy = state.stage in setOf(
        OcrDraftStage.Recognizing,
        OcrDraftStage.ParsingIntent,
        OcrDraftStage.CreatingDraft,
    )

    PageScaffold {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("图片识别记账")
            OutlinedButton(onClick = onClose, enabled = !busy) { Text("返回草稿箱") }
        }
        SafetyBanner("图片只在本机交给随 App 分发的 ML Kit 模型识别，不会上传。只有你复核并点击后，当前编辑文本才会发送到自己的 MyDCA 后端生成 DRAFT。")
        SectionCard(
            title = "1 选择图片并本地识别",
            description = "使用系统 Photo Picker 主动选择单张支付截图；App 不扫描相册，也不申请广泛存储权限。",
        ) {
            Button(
                enabled = !busy,
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) {
                Text(if (hasSelectedImage) "更换图片" else "选择支付截图")
            }
            StatusPill(stageLabel(state.stage))
            if (state.stage == OcrDraftStage.Recognizing) CircularProgressIndicator()
            state.message?.let { Text(it) }
        }

        if (state.stage in setOf(
                OcrDraftStage.Recognized,
                OcrDraftStage.ParsingIntent,
                OcrDraftStage.IntentReady,
                OcrDraftStage.CreatingDraft,
                OcrDraftStage.DraftCreated,
            )
        ) {
            SectionCard(
                title = "2 编辑识别文字",
                description = "请删除订单号、卡号、姓名等不需要的信息。识别文字不会写入本地文件或偏好设置。",
            ) {
                OutlinedTextField(
                    value = state.recognizedText,
                    onValueChange = coordinator::editText,
                    enabled = state.stage in setOf(OcrDraftStage.Recognized, OcrDraftStage.IntentReady),
                    minLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("待复核文字") },
                )
                Button(
                    enabled = state.stage == OcrDraftStage.Recognized && state.recognizedText.isNotBlank(),
                    onClick = { scope.launch { coordinator.parseIntent(repository) } },
                ) {
                    Text(if (state.stage == OcrDraftStage.ParsingIntent) "解析中" else "解析记账候选")
                }
            }
        }

        state.intent?.let { intent ->
            SectionCard(
                title = "3 复核候选意图",
                description = "候选意图不是正式流水。字段不完整时请先创建草稿，再到草稿箱补齐账户并生成影响预览。",
            ) {
                KeyValueRow("类型", intent.txnType ?: "待补充")
                KeyValueRow("金额", intent.amount?.toString() ?: "待补充")
                KeyValueRow("账户提示", intent.accountNameHint ?: "待补充")
                KeyValueRow("置信度", intent.confidence?.toString() ?: "未提供")
                if (intent.missingFields.isNotEmpty()) {
                    Text("缺失字段：${intent.missingFields.joinToString()}")
                }
                Button(
                    enabled = state.stage == OcrDraftStage.IntentReady,
                    onClick = { scope.launch { coordinator.createDraft(repository) } },
                ) {
                    Text(if (state.stage == OcrDraftStage.CreatingDraft) "创建中" else "确认生成 DRAFT")
                }
            }
        }

        state.draftId?.let { draftId ->
            SectionCard(
                title = "4 草稿已创建",
                description = "草稿 #$draftId 尚未 preview 或正式入账。请进入草稿箱补齐账户、查看影响预览并手动二次确认。",
            ) {
                StatusPill("仅 DRAFT，未入账")
                Button(onClick = { onOpenDraft(draftId) }) { Text("进入草稿箱") }
            }
        }
    }
}

private fun stageLabel(stage: OcrDraftStage): String = when (stage) {
    OcrDraftStage.Idle -> "等待选择图片"
    OcrDraftStage.ImageSelected -> "图片已选择"
    OcrDraftStage.Recognizing -> "正在本机识别"
    OcrDraftStage.Recognized -> "识别完成，等待编辑"
    OcrDraftStage.RecognitionFailed -> "识别失败"
    OcrDraftStage.ParsingIntent -> "正在解析候选"
    OcrDraftStage.IntentReady -> "候选已生成，等待复核"
    OcrDraftStage.CreatingDraft -> "正在创建 DRAFT"
    OcrDraftStage.DraftCreated -> "DRAFT 已创建"
}
