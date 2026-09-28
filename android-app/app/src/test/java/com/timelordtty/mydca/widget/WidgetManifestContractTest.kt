package com.timelordtty.mydca.widget

import android.appwidget.AppWidgetProvider
import com.timelordtty.mydca.data.api.WealthHubApi
import com.timelordtty.mydca.data.repository.AiAccountingRepository
import com.timelordtty.mydca.data.repository.DraftRepository
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * 桌面小组件的静态契约：Manifest / appwidget-provider / 布局只能包含必要声明，
 * 且小组件层在类型层面没有任何仓库、网络或越过草稿边界的能力。
 *
 * RemoteViews 本身难以在 JVM 里渲染，因此这里把“必须成立的结构”固定成可重复执行的证据。
 */
class WidgetManifestContractTest {

    @Test
    fun widgetProviderIsDeclaredOnlyForTheSystemAppWidgetProtocol() {
        val receivers = manifest().getElementsByTagName("receiver")
        assertEquals("只允许新增一个小组件 receiver", 1, receivers.length)

        val receiver = receivers.item(0) as Element
        assertEquals(".widget.QuickCaptureWidgetProvider", receiver.android("name"))
        assertEquals("必须显式声明 exported（Android 12+ 强制）", "true", receiver.android("exported"))
        assertNotNull(receiver.android("label"))
        assertEquals("小组件 receiver 不得额外挂权限", "", receiver.android("permission"))

        val filters = receiver.childElements("intent-filter")
        assertEquals("只允许一个系统 AppWidget intent-filter", 1, filters.size)
        val filter = filters.single()
        assertEquals(
            listOf("android.appwidget.action.APPWIDGET_UPDATE"),
            filter.childElements("action").map { it.android("name") },
        )
        assertEquals("不得声明 category", 0, filter.childElements("category").size)
        assertEquals("不得声明 data（不接受自定义 scheme）", 0, filter.childElements("data").size)

        val metaData = receiver.childElements("meta-data").single()
        assertEquals("android.appwidget.provider", metaData.android("name"))
        assertEquals("@xml/widget_quick_capture_info", metaData.android("resource"))
    }

    @Test
    fun manifestComponentInventoryStaysMinimal() {
        val manifest = manifest()

        assertEquals(1, manifest.getElementsByTagName("activity").length)
        assertEquals(1, manifest.getElementsByTagName("service").length)
        assertEquals(1, manifest.getElementsByTagName("receiver").length)

        // 小组件用显式 Intent 进入 App，因此 MainActivity 不得为此再加任何外部 action。
        val activity = manifest.getElementsByTagName("activity").item(0) as Element
        assertEquals(".MainActivity", activity.android("name"))
        assertEquals("true", activity.android("exported"))
        val activityActions = activity.childElements("intent-filter")
            .flatMap { it.childElements("action") }
            .map { it.android("name") }
            .toSortedSet()
        assertEquals(
            sortedSetOf("android.intent.action.MAIN", "android.intent.action.SEND"),
            activityActions,
        )

        val permissions = (0 until manifest.getElementsByTagName("uses-permission").length)
            .map { (manifest.getElementsByTagName("uses-permission").item(it) as Element).android("name") }
            .toSortedSet()
        assertEquals(
            "小组件不得新增任何系统权限",
            sortedSetOf("android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS"),
            permissions,
        )
    }

    @Test
    fun widgetInfoDeclaresNoPeriodicRefreshAndNoConfigEntry() {
        val root = parse(infoFile()).documentElement
        assertEquals("appwidget-provider", root.tagName)
        assertEquals("@layout/widget_quick_capture", root.android("initialLayout"))
        assertEquals("updatePeriodMillis 必须为 0：不做任何周期性后台刷新", "0", root.android("updatePeriodMillis"))
        assertEquals("home_screen", root.android("widgetCategory"))
        assertTrue(root.android("resizeMode").isNotBlank())
        assertTrue(root.android("minWidth").isNotBlank())
        assertTrue(root.android("minHeight").isNotBlank())
        assertEquals("不得声明配置 Activity，小组件不提供任何配置入口", "", root.android("configure"))
    }

    @Test
    fun layoutsOnlyCarryTheControlledEntryIdsAndNoHardcodedCopy() {
        val full = fullLayout().readText()
        val compact = compactLayout().readText()

        WidgetEntryPoints.FULL.forEach { target ->
            assertTrue(
                "完整尺寸布局必须包含 $target 的入口：${WidgetEntryViews.nameOf(target)}",
                full.contains("@+id/${WidgetEntryViews.nameOf(target)}"),
            )
        }
        WidgetEntryPoints.COMPACT.forEach { target ->
            assertTrue(
                "紧凑尺寸布局必须包含 ${target}",
                compact.contains("@+id/${WidgetEntryViews.nameOf(target)}"),
            )
        }
        assertFalse(
            "紧凑布局不得出现手工记账入口",
            compact.contains("@+id/${WidgetEntryViews.nameOf(WidgetNavigationTarget.ManualText)}"),
        )
        assertFalse(
            "紧凑布局不得出现图片识别入口",
            compact.contains("@+id/${WidgetEntryViews.nameOf(WidgetNavigationTarget.ImageOcr)}"),
        )

        listOf("widget_quick_capture.xml" to full, "widget_quick_capture_compact.xml" to compact)
            .forEach { (name, xml) ->
                assertFalse(
                    "$name 不得写死文案：显示文案只能来自 WidgetNavigationTarget 的受控枚举",
                    xml.contains("android:text="),
                )
            }
    }

    @Test
    fun entryViewIdsAreUniqueAndNameable() {
        val ids = WidgetNavigationTarget.entries.map { WidgetEntryViews.idOf(it) }
        val names = WidgetNavigationTarget.entries.map { WidgetEntryViews.nameOf(it) }

        assertEquals(4, ids.toSet().size)
        assertEquals(4, names.toSet().size)
        assertTrue(ids.all { it > 0 })
        assertTrue(names.all { it.startsWith("widget_entry_") })
    }

    @Test
    fun providerIsAnAppWidgetProviderWithNoExtraCapability() {
        val providerClass = QuickCaptureWidgetProvider::class.java
        assertTrue(AppWidgetProvider::class.java.isAssignableFrom(providerClass))

        val allowedNames = setOf("onUpdate", "onAppWidgetOptionsChanged", "remoteViewsFor")
        val declaredNames = providerClass.declaredMethods
            .filterNot { it.isSynthetic }
            .map { it.name }

        assertTrue(
            "provider 只允许声明系统更新钩子与静态视图构造：$declaredNames",
            allowedNames.containsAll(declaredNames),
        )
        assertFalse(
            "provider 不得覆写 onReceive：不接受任何自定义外部广播",
            declaredNames.contains("onReceive"),
        )

        val forbiddenTypes = setOf(
            AiAccountingRepository::class.java,
            DraftRepository::class.java,
            WealthHubApi::class.java,
        )
        declaredNames.forEach { name ->
            val method = providerClass.declaredMethods.first { it.name == name }
            assertFalse("provider 不得依赖网络写入门面：$name", method.returnType in forbiddenTypes)
            method.parameterTypes.forEach { parameter ->
                assertFalse("provider 不得依赖网络写入门面：$name", parameter in forbiddenTypes)
            }
        }
    }

    private fun parse(file: File): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun manifest() = parse(manifestFile())

    private fun locate(vararg candidates: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            candidates.forEach { relative ->
                val file = File(dir, relative)
                if (file.isFile) return file
            }
            dir = dir.parentFile
        }
        throw AssertionError("找不到文件：${candidates.toList()}（起点 ${System.getProperty("user.dir")}）")
    }

    private fun manifestFile() = locate(
        "android-app/app/src/main/AndroidManifest.xml",
        "app/src/main/AndroidManifest.xml",
        "src/main/AndroidManifest.xml",
    )

    private fun infoFile() = locate(
        "android-app/app/src/main/res/xml/widget_quick_capture_info.xml",
        "app/src/main/res/xml/widget_quick_capture_info.xml",
        "src/main/res/xml/widget_quick_capture_info.xml",
    )

    private fun fullLayout() = locate(
        "android-app/app/src/main/res/layout/widget_quick_capture.xml",
        "app/src/main/res/layout/widget_quick_capture.xml",
        "src/main/res/layout/widget_quick_capture.xml",
    )

    private fun compactLayout() = locate(
        "android-app/app/src/main/res/layout/widget_quick_capture_compact.xml",
        "app/src/main/res/layout/widget_quick_capture_compact.xml",
        "src/main/res/layout/widget_quick_capture_compact.xml",
    )

    private fun Element.android(localName: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", localName).orEmpty()

    private fun Element.childElements(tagName: String): List<Element> =
        (0 until childNodes.length)
            .map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { it.tagName == tagName }
}
