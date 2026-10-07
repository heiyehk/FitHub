package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.AssetDto
import com.heiyehk.fithub.data.remote.GitHubApi
import com.heiyehk.fithub.data.remote.ReleaseDto
import com.heiyehk.fithub.data.remote.ReleasePick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * 「检查更新」挑哪个安装包。
 *
 * 这个测试钉的是一个真实事故：界面上提示「发现新版本 v0.0.3」，用户点下载，
 * 装上去的却是 v0.0.2，而且**全程没有任何报错**。
 *
 * 原因不是挑错了包，是**问了两次**。检查那次穿缓存拿到了新的 release，
 * 下载那次却走 `detail()` 重新查、而且落在另一条缓存键上（perPage 不同 → 键不同），
 * 读回的是上一次打开自己仓库详情页时留下的旧列表。
 *
 * 现在 `installableOf` 是纯函数、且只接受「检查结果那一条 release」，
 * 所以下面这些断言其实是在钉一个更强的性质：**它没有任何一条能读缓存的路**。
 * 类型上它不是 suspend、也拿不到 `GitHubApi`。
 */
class UpdateInstallableTest {

    /**
     * 真实构造一个 [FitRepository]，而不是 mock。
     *
     * [com.heiyehk.fithub.data.remote.GitHubApi] 的构造要一个 cacheDir，
     * 纯函数测试根本不会碰它 —— 真被调用到就会因为这个临时目录失败，
     * 而不是安静地去读一条缓存。也就是说不给它任何「能成功读到缓存」的余地。
     */
    private val repo = FitRepository(
        GitHubApi(File(System.getProperty("java.io.tmpdir"), "update-installable-test")),
    )

    private fun asset(name: String, size: Long = 1024L) =
        AssetDto(name = name, size = size)

    private fun rel(tag: String, vararg names: String) = ReleaseDto(
        tagName = tag,
        assets = names.map { asset(it) },
    )

    @Test
    fun `下载的是检查结果那条 release 的包`() {
        val release = rel("v0.0.3", "app-release.apk")
        val picked = repo.installableOf(release)
        assertNotNull("最新版里有产物就必须挑得出一个", picked)
        assertEquals("app-release.apk", picked!!.name)
        // 关键断言：挑出来的包**属于哪条 release**，由传进来的对象决定。
        // 之前这里是「再去查一次」，而那次查的是另一条缓存键。
        assertEquals("v0.0.3", picked.tag)
    }

    @Test
    fun `旧 release 的同名包不会被选中`() {
        // 两条 release 都有 app-release.apk（现实中极常见）。
        // 只把新版那一条交给 installableOf，选中的 tag 必须是它 ——
        // 旧版那一条根本没有进入这个函数，它也就没有机会被选中。
        val old = rel("v0.0.2", "app-release.apk")
        val new = rel("v0.0.3", "app-release.apk")

        val pickedFromOld = repo.installableOf(old)
        val pickedFromNew = repo.installableOf(new)

        assertEquals("v0.0.2", pickedFromOld?.tag)
        assertEquals("v0.0.3", pickedFromNew?.tag)
    }

    @Test
    fun `和 ReleasePick 合起来仍取第一条`() {
        // 检查用的是 ReleasePick.latest，下载用的是 installableOf。
        // 这条断言把两半钉在一起：给 ReleasePick 的那份列表，同一份交给 installableOf，
        // 两边必须落在同一条 release 上。
        val releases = listOf(
            rel("v0.0.3", "app-release.apk"),
            rel("v0.0.2", "app-release.apk"),
        )
        val latest = ReleasePick.latest(releases, includePrerelease = false)!!
        assertEquals(latest.tagName, repo.installableOf(latest)?.tag)
    }

    @Test
    fun `没有可安装的产物时返回 null 而不是随便挑一个`() {
        // release 里只有校验和文件：它们照样会走同一条下载链路落进下载目录，
        // 挑中就意味着拿一个文本文件去喂系统安装器。
        val release = rel("v0.0.3", "SHA256SUMS.txt", "checksums.txt")
        assertNull(repo.installableOf(release))
    }

    @Test
    fun `release 本身没有产物时也是 null`() {
        assertNull(repo.installableOf(ReleaseDto(tagName = "v0.0.3")))
    }

}
