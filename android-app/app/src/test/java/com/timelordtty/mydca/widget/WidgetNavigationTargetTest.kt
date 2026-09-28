package com.timelordtty.mydca.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.9.0 桌面小组件：把“四个受控目标”的协议与文案固定成可重复执行的证据。
 *
 * 全部为纯 Kotlin 断言，不依赖 Instrumentation，因此可以在 JVM 单元测试里稳定回归。
 */
class WidgetNavigationTargetTest {

    @Test
    fun exactlyFourTargetsCoverTheFourDesktopEntries() {
        assertEquals(
            listOf(
                WidgetNavigationTarget.QuickCapture,
                WidgetNavigationTarget.ManualText,
                WidgetNavigationTarget.ImageOcr,
                WidgetNavigationTarget.Drafts,
            ),
            WidgetNavigationTarget.entries,
        )
    }

    @Test
    fun everyTargetKeepsAFixedActionUnderTheAppPrivatePrefix() {
        WidgetNavigationTarget.entries.forEach { target ->
            assertTrue(
                "$target 的 action 必须使用 App 私有固定前缀：${target.action}",
                target.action.startsWith(WidgetNavigationTarget.ACTION_PREFIX),
            )
        }
    }

    @Test
    fun actionAndRequestCodeIdentifyEachPendingIntentUniquely() {
        val actions = WidgetNavigationTarget.entries.map { it.action }
        val requestCodes = WidgetNavigationTarget.entries.map { it.requestCode }

        assertEquals("四个 action 必须彼此不同", 4, actions.toSet().size)
        assertEquals("四个 requestCode 必须彼此不同", 4, requestCodes.toSet().size)
        assertEquals("每个 action 必须唯一", 4, actions.distinct().size)
        assertTrue("requestCode 必须为正数", requestCodes.all { it > 0 })
    }

    @Test
    fun actionRoundTripsBackToItsTarget() {
        WidgetNavigationTarget.entries.forEach { target ->
            assertEquals(target, WidgetNavigationTarget.fromAction(target.action))
        }
    }

    @Test
    fun unknownBlankAndForeignActionsAreIgnored() {
        assertNull(WidgetNavigationTarget.fromAction(null))
        assertNull(WidgetNavigationTarget.fromAction(""))
        assertNull(WidgetNavigationTarget.fromAction("   "))
        assertNull(WidgetNavigationTarget.fromAction("android.intent.action.MAIN"))
        assertNull(WidgetNavigationTarget.fromAction("android.intent.action.SEND"))
        assertNull(WidgetNavigationTarget.fromAction(WidgetNavigationTarget.ACTION_PREFIX + "UNKNOWN"))
        assertNull(
            "不接受任意外部 route 字符串",
            WidgetNavigationTarget.fromAction(WidgetNavigationTarget.ACTION_PREFIX + "drafts/42"),
        )
        assertNull(
            "不接受大小写变体",
            WidgetNavigationTarget.fromAction(WidgetNavigationTarget.Drafts.action.lowercase()),
        )
    }

    @Test
    fun labelsAreShortPureChineseCopy() {
        WidgetNavigationTarget.entries.forEach { target ->
            assertTrue("$target 文案不能为空", target.label.isNotBlank())
            assertFalse(
                "$target 文案必须是纯中文：${target.label}",
                target.label.contains(Regex("[A-Za-z0-9]")),
            )
            assertTrue("$target 文案必须保持精简：${target.label}", target.label.length in 2..5)
        }
    }

    @Test
    fun copyExposesNoInternalNameAndNoPrivateData() {
        val forbiddenWords = listOf(
            "outbox",
            "draft",
            "ocr",
            "preview",
            "confirm",
            "quickentry",
            "sourceref",
            "repository",
            "token",
        )
        val forbiddenChinese = listOf("余额", "持仓", "收益", "金额", "通知")

        WidgetNavigationTarget.entries.forEach { target ->
            listOf(target.label, target.description).forEach { text ->
                assertTrue("文案不能为空", text.isNotBlank())
                forbiddenWords.forEach { word ->
                    assertFalse("文案不应暴露内部名称 $word：$text", text.lowercase().contains(word))
                }
                forbiddenChinese.forEach { word ->
                    assertFalse("小组件不应展示隐私数据 $word：$text", text.contains(word))
                }
                assertFalse("文案不显示任何数字（不做动态计数）：$text", text.contains(Regex("[0-9]")))
            }
        }
    }

    @Test
    fun descriptionsStateThatNothingHappensAutomatically() {
        assertTrue(WidgetNavigationTarget.ManualText.description.contains("不会自动"))
        assertTrue(WidgetNavigationTarget.ImageOcr.description.contains("亲手"))
        assertTrue(WidgetNavigationTarget.Drafts.description.contains("手动"))
        assertTrue(WidgetNavigationTarget.QuickCapture.description.contains("你自己"))
    }
}
