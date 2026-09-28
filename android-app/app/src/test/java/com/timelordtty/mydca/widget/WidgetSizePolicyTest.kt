package com.timelordtty.mydca.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 尺寸自适应：完整尺寸给四个入口，空间不足时折叠成两个主入口。
 */
class WidgetSizePolicyTest {

    @Test
    fun fullSizeKeepsAllFourEntries() {
        assertFalse(WidgetSizePolicy.isCompact(250, 120))
        assertEquals(
            listOf(
                WidgetNavigationTarget.QuickCapture,
                WidgetNavigationTarget.ManualText,
                WidgetNavigationTarget.ImageOcr,
                WidgetNavigationTarget.Drafts,
            ),
            WidgetSizePolicy.entriesFor(250, 120),
        )
    }

    @Test
    fun exactThresholdStillCountsAsFull() {
        assertFalse(
            WidgetSizePolicy.isCompact(
                WidgetSizePolicy.FULL_MIN_WIDTH_DP,
                WidgetSizePolicy.FULL_MIN_HEIGHT_DP,
            ),
        )
    }

    @Test
    fun smallerSizesFallBackToTheTwoPrimaryEntries() {
        listOf(
            110 to 110,
            180 to 40,
            110 to 40,
            WidgetSizePolicy.FULL_MIN_WIDTH_DP - 1 to WidgetSizePolicy.FULL_MIN_HEIGHT_DP - 1,
        ).forEach { (width, height) ->
            assertTrue("$width x $height 应折叠", WidgetSizePolicy.isCompact(width, height))
            assertEquals(
                "$width x $height 只保留两个主入口",
                listOf(WidgetNavigationTarget.QuickCapture, WidgetNavigationTarget.Drafts),
                WidgetSizePolicy.entriesFor(width, height),
            )
        }
    }

    @Test
    fun unknownSizeDefaultsToFullLayoutInsteadOfFolding() {
        // 刚添加到桌面时系统可能还没给出 options；此时按完整尺寸渲染，避免大控件一开始就退化。
        assertFalse(WidgetSizePolicy.isCompact(0, 0))
        assertFalse(WidgetSizePolicy.isCompact(-1, 120))
        assertFalse(WidgetSizePolicy.isCompact(250, 0))
        assertEquals(WidgetEntryPoints.FULL, WidgetSizePolicy.entriesFor(0, 0))
    }

    @Test
    fun compactEntriesAreASubsetOfTheFullEntries() {
        assertTrue(WidgetEntryPoints.FULL.containsAll(WidgetEntryPoints.COMPACT))
        assertEquals(4, WidgetEntryPoints.FULL.size)
        assertEquals(2, WidgetEntryPoints.COMPACT.size)
        assertEquals(WidgetEntryPoints.COMPACT, WidgetEntryPoints.entriesFor(compact = true))
        assertEquals(WidgetEntryPoints.FULL, WidgetEntryPoints.entriesFor(compact = false))
    }
}
