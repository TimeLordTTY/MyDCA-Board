package com.timelordtty.mydca.widget

/**
 * 小组件 Intent 的唯一解析规则（纯 Kotlin，无 Android 依赖，可直接单元测试）。
 *
 * `onCreate` 与 `onNewIntent` 都把 `Intent.action` 交给这里，因此冷启动与复用已有实例
 * 使用完全相同的解析结果；本层只回答“是四个受控目标里的哪一个”，不做其它任何事。
 */
object WidgetNavigationResolver {
    fun resolve(action: String?): WidgetNavigationTarget? = WidgetNavigationTarget.fromAction(action)

    /** 是否是本 App 小组件的受控 action；用于把小组件意图与普通启动 / 分享意图区分开。 */
    fun isWidgetAction(action: String?): Boolean = resolve(action) != null
}
