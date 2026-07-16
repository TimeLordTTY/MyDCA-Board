package com.timelordtty.mydca.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** 单张图片本地 OCR 边界；实现不得上传图片、URI 或图片字节。 */
fun interface ImageTextRecognizer {
    suspend fun recognize(context: Context, uri: Uri): String
}

/** 使用随 APK 分发的 ML Kit 中文模型读取系统 Photo Picker 返回的 URI。 */
class MlKitImageTextRecognizer : ImageTextRecognizer {
    override suspend fun recognize(context: Context, uri: Uri): String = suspendCancellableCoroutine { continuation ->
        val image = try {
            InputImage.fromFilePath(context.applicationContext, uri)
        } catch (error: Throwable) {
            continuation.resumeWithException(error)
            return@suspendCancellableCoroutine
        }
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result.text)
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            .addOnCompleteListener { recognizer.close() }
    }
}
