package com.heiyehk.fithub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 扫描结果落盘。
 *
 * 只测 [ScanStore] 能纯文件判定的部分：写进去能原样读回来、年龄对得上、
 * 坏文件不会把整个 App 带崩。真正跑 [ScanEngine.scan] 需要 PackageManager，
 * 放到 AVD 上验。
 */
class ScanStoreTest {

    private fun result(n: Int) = ScanResult(
        total = n,
        userInstalled = n - 1,
        system = 1,
        apps = (0 until n).map {
            ScannedApp(
                packageName = "com.example.p$it",
                label = "应用 $it",
                versionName = "1.$it.0",
                versionCode = it.toLong(),
                systemApp = false,
                signerSha256 = "deadbeef$it",
            )
        },
        elapsedMs = 123L,
        complete = true,
    )

    private fun store(root: File) = object {
        fun write(r: ScanResult) = LocalStore(root, kotlinx.serialization.json.Json).write(
            "scan-result",
            ScanSnapshot(r, System.currentTimeMillis()),
        )

        fun clear() = LocalStore(root, kotlinx.serialization.json.Json).remove("scan-result")
    }

    @Test
    fun `快照能原样往返`() {
        val root = createTempDir(prefix = "scan")
        try {
            val r = result(3)
            val json = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            LocalStore(root, json).write("scan-result", ScanSnapshot(r, System.currentTimeMillis()))

            val back = json.decodeFromString<ScanSnapshot>(
                File(root, "scan-result.json").readText(),
            )
            assertEquals(r, back.result)
            assertEquals("complete 标志必须往返", true, back.result.complete)
            assertEquals("签名指纹不能丢", "deadbeef0", back.result.apps.first().signerSha256)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `缺文件时是 null 而不是空结果`() {
        val root = createTempDir(prefix = "scan")
        try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            // 与 ScanStore.read 的判据一致：读不出、或者 at <= 0 都当「没有」
            val snap = runCatching {
                json.decodeFromString<ScanSnapshot>(File(root, "scan-result.json").readText())
            }.getOrNull()
            assertNull(snap)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `坏文件不抛`() {
        val root = createTempDir(prefix = "scan")
        try {
            File(root, "scan-result.json").writeText("{ 这不是 JSON")
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val snap = runCatching {
                json.decodeFromString<ScanSnapshot>(File(root, "scan-result.json").readText())
            }.getOrNull()
            assertNull("内容损坏必须当成没有，而不是把用户数据读崩", snap)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `老结构缺字段仍能读出`() {
        // 以后给 ScanResult / ScanSnapshot 加字段时，老用户盘上的文件会少几个键。
        // ignoreUnknownKeys 管的是「多出来的键」，少键要靠字段默认值兜住 ——
        // 读不出来的话，用户一升级 App 本机页就空了。
        val root = createTempDir(prefix = "scan")
        try {
            File(root, "scan-result.json").writeText(
                """{"result":{"total":2,"userInstalled":1,"system":1,"apps":[],"elapsedMs":5}}""",
            )
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val snap = json.decodeFromString<ScanSnapshot>(File(root, "scan-result.json").readText())
            assertTrue("缺 complete 字段时不该崩", snap.result.apps.isEmpty())
            assertEquals(2, snap.result.total)
            // 缺的时间戳必须读成「无效」，让 ScanStore.read 返回 null 走全量扫描，
            // 而不是拿一份不知道多旧的结果当现状
            assertEquals(0L, snap.at)
            // 缺 complete 时必须假设「不完整」：那个数字是下限，宁可少说也不能说满
            assertEquals(false, snap.result.complete)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createTempDir(prefix: String): File =
        java.nio.file.Files.createTempDirectory(prefix).toFile()
}
