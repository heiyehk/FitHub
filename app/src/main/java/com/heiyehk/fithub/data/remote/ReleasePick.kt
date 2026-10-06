package com.heiyehk.fithub.data.remote

/**
 * 从 GitHub Releases 列表里挑「最新版」。
 *
 * 抽成纯函数是为了能单测 —— 这个 bug 藏了很久：
 * 原来的实现是 `releases.filter { !it.draft }.firstOrNull()`，**只滤了草稿、
 * 没滤预发布**。于是像 Flutter 那样把 `3.19.0-0.1.pre` 当成了最新版，
 * 详情页的版本号、分享出去的链接、「可升级」判断全都跟着错。
 *
 * GitHub 的 `/releases` 按发布时间倒序返回，所以「第一个」就是最新的那个。
 */
object ReleasePick {

    /**
     * 参与展示与适配判定的 release 列表。
     *
     * 草稿永远排除：那是作者还没打算发布的东西。
     * 预发布受开关控制：混进「最新版」会让适配结论指向一个别人还没装的包。
     */
    fun visible(releases: List<ReleaseDto>, includePrerelease: Boolean): List<ReleaseDto> =
        releases.filter { !it.draft && (includePrerelease || !it.prerelease) }

    /** 最新的一条；没有可用 release 时返回 null（调用方要如实写「没有 release」） */
    fun latest(releases: List<ReleaseDto>, includePrerelease: Boolean): ReleaseDto? =
        visible(releases, includePrerelease).firstOrNull()
}
