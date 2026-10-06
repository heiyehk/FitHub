package com.heiyehk.fithub.data

import com.heiyehk.fithub.data.remote.VersionTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「检查更新」的版本比较。
 *
 * 这批断言盯的是三个各自会出错的点：tag 的 `v` 前缀、数字段的字典序陷阱、
 * 以及「本机比线上新」这一档原来根本没有。
 */
class VersionTagTest {

    /** `v` 前缀：GitHub tag 惯例带 v，versionName 不带。字符串相等在这里恒为 false。 */
    @Test
    fun `v prefix does not change the version`() {
        assertEquals(0, VersionTag.compare("v0.0.2", "0.0.2"))
        assertEquals(0, VersionTag.compare("V1.4.0", "1.4.0"))
        assertEquals(0, VersionTag.compare("  v2.1  ", "2.1"))
    }

    /** 字典序陷阱：10 < 9，按字符串比会把新版本判成旧的。 */
    @Test
    fun `numeric segments compare numerically not lexicographically`() {
        assertTrue(VersionTag.compare("0.0.10", "0.0.9") > 0)
        assertTrue(VersionTag.compare("v0.0.9", "0.0.10") < 0)
        assertTrue(VersionTag.compare("1.10.0", "1.9.0") > 0)
    }

    /** 缺的段当 0，所以 `1.0` 和 `1.0.0` 是同一个版本，不是「1.0 更旧」。 */
    @Test
    fun `missing segments count as zero`() {
        assertEquals(0, VersionTag.compare("1.0", "1.0.0"))
        assertEquals(0, VersionTag.compare("1", "1.0.0.0"))
    }

    /** 带预发布后缀的更旧 —— 这是 `visible()` 在筛选 release 时用的同一条语义。 */
    @Test
    fun `prerelease suffix sorts below the plain release`() {
        assertTrue(VersionTag.compare("1.0.0", "1.0.0-beta") > 0)
        assertTrue(VersionTag.compare("v0.0.2-beta.1", "v0.0.2") < 0)
        assertEquals(0, VersionTag.compare("1.0.0-RC1", "1.0.0-rc1"))
    }

    /** 三档都要分得开：相等 / 远端更新 / 本机更新。 */
    @Test
    fun `three way comparison separates up to date newer and local ahead`() {
        // 已是最新
        assertEquals(0, VersionTag.compare("v0.0.2", "0.0.2"))
        // 远端更新
        assertTrue(VersionTag.compare("v0.0.3", "0.0.2") > 0)
        // 本机比线上新：开发版装在机器上
        assertTrue(VersionTag.compare("v0.0.1", "0.0.2") < 0)
    }

    /** 分隔符不止 `.`：`_` 和 `+` 也常见（`1.0.0+build.7`）。 */
    @Test
    fun `underscore and plus are treated as separators`() {
        assertEquals(0, VersionTag.compare("1.2_3", "1.2.3"))
        assertTrue(VersionTag.compare("1.2.3", "1.2.3+build7") > 0)
    }

    /**
     * 解析不出数字时不能抛。
     *
     * 这条不是「顺便测一下」：tag 是远端给的内容，任何字符串都可能出现，
     * 而「检查更新」是一条用户随时能点的路，抛异常等于整个页面崩掉。
     */
    @Test
    fun `unparseable input does not throw`() {
        assertEquals(0, VersionTag.compare("", ""))
        assertEquals(0, VersionTag.compare("nightly", "nightly"))
        assertTrue(VersionTag.compare("1.0.0", "nightly") > 0)
    }
}