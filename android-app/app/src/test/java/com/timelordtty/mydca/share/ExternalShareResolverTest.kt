package com.timelordtty.mydca.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.8.0 系统分享入口的解析规则：把“能收什么、怎么安全拒绝”固定成可重复执行的证据。
 *
 * 全部为纯 Kotlin 断言，不依赖 Intent、Compose 或任何 instrumentation。
 */
class ExternalShareResolverTest {

    @Test
    fun plainTextShareBecomesTextPayload() {
        val resolution = ExternalShareResolver.resolve(
            ExternalShareRequest(
                action = ExternalShareResolver.ACTION_SEND,
                mimeType = "text/plain",
                sharedText = "  早餐 18 元 微信支付  ",
            ),
        )

        val accepted = resolution as ExternalShareResolution.Accepted
        assertEquals(ExternalSharePayload.Text("早餐 18 元 微信支付"), accepted.payload)
        assertFalse("普通长度文本不应被截断", accepted.truncated)
    }

    @Test
    fun otherReasonablyTextMimeTypesAreAcceptedAsText() {
        listOf("text/plain", "TEXT/PLAIN", "text/html", "text/plain; charset=utf-8").forEach { mime ->
            val resolution = ExternalShareResolver.resolve(
                ExternalShareRequest(
                    action = ExternalShareResolver.ACTION_SEND,
                    mimeType = mime,
                    sharedText = "打车 32.5 元 支付宝",
                ),
            )

            assertEquals(
                "$mime 应按文本接收",
                ExternalSharePayload.Text("打车 32.5 元 支付宝"),
                (resolution as ExternalShareResolution.Accepted).payload,
            )
        }
    }

    @Test
    fun singleImageShareWithContentUriBecomesImagePayload() {
        val uri = "content://com.example.provider/picked/payment-1"
        val resolution = ExternalShareResolver.resolve(
            ExternalShareRequest(
                action = ExternalShareResolver.ACTION_SEND,
                mimeType = "image/png",
                sharedUri = uri,
            ),
        )

        assertEquals(
            ExternalSharePayload.Image(uri),
            (resolution as ExternalShareResolution.Accepted).payload,
        )
    }

    @Test
    fun sendMultipleIsRejectedAsUnsupported() {
        val resolution = ExternalShareResolver.resolve(
            ExternalShareRequest(
                action = ExternalShareResolver.ACTION_SEND_MULTIPLE,
                mimeType = "image/*",
                sharedUri = "content://com.example.provider/picked/1",
            ),
        )

        assertEquals(
            ExternalShareRejection.UnsupportedAction,
            (resolution as ExternalShareResolution.Rejected).rejection,
        )
    }

    @Test
    fun fileSchemeImageIsRejected() {
        val resolution = ExternalShareResolver.resolve(
            ExternalShareRequest(
                action = ExternalShareResolver.ACTION_SEND,
                mimeType = "image/jpeg",
                sharedUri = "file:///sdcard/Download/payment.jpg",
            ),
        )

        assertEquals(
            ExternalShareRejection.UntrustedImageSource,
            (resolution as ExternalShareResolution.Rejected).rejection,
        )
    }

    @Test
    fun unknownSchemesAndBarePathsAreRejected() {
        listOf(
            "https://example.com/payment.png",
            "http://example.com/payment.png",
            "/sdcard/Download/payment.png",
            "android.resource://com.example.app/drawable/x",
        ).forEach { uri ->
            val resolution = ExternalShareResolver.resolve(
                ExternalShareRequest(
                    action = ExternalShareResolver.ACTION_SEND,
                    mimeType = "image/*",
                    sharedUri = uri,
                ),
            )

            assertEquals(
                "$uri 不应被当作可信图片",
                ExternalShareRejection.UntrustedImageSource,
                (resolution as ExternalShareResolution.Rejected).rejection,
            )
        }
    }

    @Test
    fun blankTextAndBlankUriAreRejected() {
        val blankText = ExternalShareResolver.resolve(
            ExternalShareRequest(
                action = ExternalShareResolver.ACTION_SEND,
                mimeType = "text/plain",
                sharedText = "   ",
            ),
        )
        val missingText = ExternalShareResolver.resolve(
            ExternalShareRequest(
                action = ExternalShareResolver.ACTION_SEND,
                mimeType = "text/plain",
                sharedText = null,
            ),
        )
        val blankUri = ExternalShareResolver.resolve(
            ExternalShareRequest(
                action = ExternalShareResolver.ACTION_SEND,
                mimeType = "image/png",
                sharedUri = "  ",
            ),
        )

        assertEquals(ExternalShareRejection.MissingText, (blankText as ExternalShareResolution.Rejected).rejection)
        assertEquals(ExternalShareRejection.MissingText, (missingText as ExternalShareResolution.Rejected).rejection)
        assertEquals(ExternalShareRejection.MissingImage, (blankUri as ExternalShareResolution.Rejected).rejection)
    }

    @Test
    fun unsupportedMimeTypeIsRejected() {
        listOf("application/pdf", "video/mp4", "application/octet-stream").forEach { mime ->
            val resolution = ExternalShareResolver.resolve(
                ExternalShareRequest(
                    action = ExternalShareResolver.ACTION_SEND,
                    mimeType = mime,
                    sharedUri = "content://com.example.provider/picked/1",
                ),
            )

            assertEquals(
                "$mime 不应被接收",
                ExternalShareRejection.UnsupportedMimeType,
                (resolution as ExternalShareResolution.Rejected).rejection,
            )
        }
    }

    @Test
    fun overLongTextIsSafelyTruncatedAtTheLimit() {
        val raw = "支付 18 元 ".repeat(400)

        val resolution = ExternalShareResolver.resolve(
            ExternalShareRequest(
                action = ExternalShareResolver.ACTION_SEND,
                mimeType = "text/plain",
                sharedText = raw,
            ),
        )

        val accepted = resolution as ExternalShareResolution.Accepted
        val text = accepted.payload as ExternalSharePayload.Text
        assertTrue("超长文本必须被截断", accepted.truncated)
        assertEquals(ExternalShareResolver.MAX_TEXT_LENGTH, text.text.length)
        assertTrue(raw.startsWith(text.text))
    }

    @Test
    fun textAtTheExactLimitIsKeptAsIs() {
        val raw = "a".repeat(ExternalShareResolver.MAX_TEXT_LENGTH)

        val resolution = ExternalShareResolver.resolve(
            ExternalShareRequest(
                action = ExternalShareResolver.ACTION_SEND,
                mimeType = "text/plain",
                sharedText = raw,
            ),
        )

        val accepted = resolution as ExternalShareResolution.Accepted
        assertFalse(accepted.truncated)
        assertEquals(raw, (accepted.payload as ExternalSharePayload.Text).text)
    }

    @Test
    fun everyRejectionCarriesAChineseUserFacingMessage() {
        ExternalShareRejection.entries.forEach { rejection ->
            assertTrue("$rejection 必须有非空提示", rejection.message.isNotBlank())
            assertTrue(
                "$rejection 必须给出中文提示：${rejection.message}",
                rejection.message.any { it.code in 0x4E00..0x9FFF },
            )
        }
    }
}
