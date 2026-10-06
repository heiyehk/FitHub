package com.heiyehk.fithub.ui

import com.heiyehk.fithub.data.HistoryEntry

/**
 * 全屏页面 —— 主 Tab 之上的那一叠。
 *
 * ## 为什么是栈，不是十二个布尔量
 *
 * 原来每页一个 `var xxxOpen by remember { mutableStateOf(false) }`。问题不在于啰嗦，
 * 而在于**打开详情时来源页被顺手关掉了**：
 *
 * ```kotlin
 * onRepoTap = { searching = false; open(it.id, placeholder = it) }   // 搜索结果
 * onRepoTap = { personLogin = null; open(it.id, placeholder = it) }  // 用户页
 * ```
 *
 * 于是详情关掉之后底下什么都不剩 —— 按返回只能落到首页。「从搜索结果点进仓库，
 * 返回却回首页」「从我的项目进去，返回回我的」都是这一行造成的。
 *
 * 布尔量表达不了「来路」，因为它们只记录「现在开着哪几页」，不记录「是怎么走到这儿的」。
 * 要修就得让页面**按顺序叠起来**：进详情不弹来源，返回时一层一层退。
 *
 * ## 详情面板不在这个栈里
 *
 * 仓库详情有自己的动画生命周期（遮罩 + 右侧滑入 + 图标飞入，共用一个 Animatable），
 * 和这些纯覆盖式的页面不是一回事，硬塞进来只会把动画搅黄。它由
 * `active` / `mounted` 单独管，但在**渲染顺序上排在栈顶之上**，
 * 并且**不进栈** —— 所以关掉它就回到栈顶那一页，正是想要的来路。
 *
 * ## 隐私 / 协议 / 检查更新为什么各是一个对象
 *
 * 它们原本挤在一个 `enum InfoPage` 里，靠一个可空的 `infoPage` 变量挑。
 * 三个都是「从我的页进来、返回回我的页」，彼此从不嵌套 —— 拆成三个平级对象之后
 * 就不需要那个额外的可空变量了，少一份状态、少一处能不同步的地方。
 */
sealed interface Page {

    /** 全屏搜索 */
    data object Search : Page

    /**
     * 用户 / 组织页。
     *
     * 带上 [login] 是因为返回时要还原到**同一个人**：栈里存的是这一页本身，
     * 而不是「有个主体页开着」这种没法还原内容的说法。
     */
    data class Person(val login: String) : Page

    /** 「我的项目」 */
    data object MyProjects : Page

    /** 历史足迹 */
    data object History : Page

    /**
     * 下载与安装记录。
     *
     * [focus] 决定哪一节排最前。两个入口（「下载与安装记录」和「从 FitHub 安装的应用」）
     * 指向同一份数据但起点不同，所以焦点必须**跟着这一页走** —— 拆成两个独立布尔量
     * 就会出现「数据是同一份、起点对不上」的状态。
     */
    data class Records(val focus: HistoryEntry.Kind?) : Page

    /** WebDAV 配置 */
    data object WebDav : Page

    /** 外观 */
    data object Theme : Page

    /** 语言 */
    data object Language : Page

    /** 下载源 */
    data object Mirror : Page

    /** GitHub 登录 */
    data object Login : Page

    /** 隐私说明 */
    data object Privacy : Page

    /** 开源协议 */
    data object License : Page

    /** 检查更新 */
    data object Update : Page

    /** 首页板块管理 */
    data object SectionManager : Page
}