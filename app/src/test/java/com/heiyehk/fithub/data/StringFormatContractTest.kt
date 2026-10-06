package com.heiyehk.fithub.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 格式化占位符与实参类型必须对得上。
 *
 * 这不是假想的问题：`myprojects_repo_counts` 写成 `fork %1$d · watch %2$d`，而调用点
 * 传的是 `(Int, Env.formatStars(...))` —— 第二个实参是 **String**。运行到列表第��行
 * 就 `IllegalFormatConversionException: d != java.lang.String` 直接崩掉整个页面。
 *
 * 为什么构建期没拦住：既有的翻译校验只比 en / zh 两边的占位符**是否一致**，
 * 两边都写 `%2$d` 恰恰是「一致」的。这条测试盯的是另一头 —— 占位符**与实参类型**。
 */
class StringFormatContractTest {

    private fun strings(lang: String): Map<String, String> {
        val f = File("src/main/res/values$lang/strings.xml")
        assertTrue("找不到 ${f.absolutePath}（测试要从项目根目录跑）", f.exists())
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(f)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val e = nodes.item(i)
            e.attributes.getNamedItem("name").nodeValue to (e.textContent ?: "")
        }
    }

    private fun Map<String, String>.need(key: String): String =
        get(key) ?: throw AssertionError("strings.xml 里没有 $key")

    /** 按出现顺序取出 `%1$d` / `%2$s` / `%.1f` 这类说明符里的**类型字母** */
    private fun specifiers(format: String): List<String> =
        Regex("""%(\d+\$)?[-#+ 0,]*(\d+)?(\.\d+)?([a-zA-Z%])""")
            .findAll(format)
            .map { it.groupValues[4] }
            .filter { it != "%" }
            .toList()

    @Test
    fun `仓库计数行的第二个占位符是 s 不是 d`() {
        val en = strings("").need("myprojects_repo_counts")
        assertTrue(
            "实参是 Env.formatStars(...) 的返回值（String），占位符必须写 %s；" +
                "写成 %d 会在渲染第一行时抛 IllegalFormatConversionException。当前：$en",
            en.contains("%2\$s"),
        )
    }

    /**
     * 真的拿实参跑一遍 `String.format`：类型对不上会在这里抛，而不是在用户手机上。
     *
     * 用 `values/strings.xml` 原文而不是 R.string —— 单测跑在 JVM 上拿不到 R，
     * 而这个缺陷本来就在资源文件里，读原文才是对的地方。
     */
    @Test
    fun `仓库计数行用真实实参类型能格式化`() {
        val en = strings("").need("myprojects_repo_counts")
        val zh = strings("-zh").need("myprojects_repo_counts")

        // 和 MyProjectsScreen.kt 的调用点一一对应：forks 是 Int，watchers 已被格式化
        val out = listOf(en, zh).map { f -> String.format(f, 3, "1") }
        assertTrue("fork 数应出现在结果里：${out[0]}", out[0].contains("3"))
        assertTrue("watch 数应出现在结果里：${out[0]}", out[0].contains("1"))
    }

    /** en / zh 的占位符种类必须完全一致，否则切换语言会崩或显示错位 */
    @Test
    fun `中英文的占位符种类一致`() {
        val en = strings("")
        val zh = strings("-zh")
        val mismatched = en.keys.filter { k ->
            zh.containsKey(k) && specifiers(en.getValue(k)) != specifiers(zh.getValue(k))
        }
        assertTrue("占位符种类不一致的 key：$mismatched", mismatched.isEmpty())
    }
}
