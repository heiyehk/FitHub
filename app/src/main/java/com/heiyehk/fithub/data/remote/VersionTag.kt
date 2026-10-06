package com.heiyehk.fithub.data.remote

/**
 * 版本号字符串的比较。
 *
 * 存在的原因是「检查更新」原来直接写 `latest == BuildConfig.VERSION_NAME`，
 * 那是**字符串相等**，有三个各自会出错的点：
 *
 * 1. GitHub 上的 tag 惯例带 `v`（`v0.0.2`），而 `versionName` 是 `0.0.1` ——
 *    就算版本号一模一样，`"v0.0.2" == "0.0.2"` 也是 false，于是永远报「发现新版本」。
 * 2. 字符串比较里 `"0.0.10" < "0.0.9"`（`1` < `9`）—— 一旦发到 0.0.10，
 *    相等的版本会被判成「远端更旧」。
 * 3. 相等之外只有「远端更新」和「远端更新」两档，**没有「本机更新」**：
 *    开发版装在机器上时，远端那个更旧的 release 也会被说成新版本。
 *
 * 规则：剥掉 `v`/`V` 前缀 → 数字段按数值逐段比（缺的补 0，所以 `1.0` ≡ `1.0.0`）
 * → 数字段全等时没有后缀的更新（`1.0.0` > `1.0.0-beta`）。
 *
 * **已知不处理**：后缀内部的版本排序。`beta.10` 按文本比会小于 `beta.2`。
 * 本项目的 tag 是 `v0.0.x` 这种三段数字，用不到；真要用得先把后缀也拆成 token 比。
 */
object VersionTag {

    /** 去掉 `v`/`V` 前缀与首尾空白，并统一小写 */
    fun normalize(raw: String): String =
        raw.trim().let {
            if (it.startsWith("v", ignoreCase = true)) it.substring(1) else it
        }.lowercase()

    /**
     * 比较两个版本号：a 比 b 新返回正数，一样返回 0，比 b 旧返回负数。
     *
     * 两边都解析不出数字时（比如一个空串）按文本比，不会抛。
     */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        for (i in 0 until maxOf(pa.numbers.size, pb.numbers.size)) {
            val x = pa.numbers.getOrElse(i) { 0L }
            val y = pb.numbers.getOrElse(i) { 0L }
            if (x != y) return x.compareTo(y)
        }
        return when {
            pa.suffix == pb.suffix -> 0
            // 没有后缀的更「新」：1.0.0 > 1.0.0-beta
            pa.suffix.isEmpty() -> 1
            pb.suffix.isEmpty() -> -1
            else -> pa.suffix.compareTo(pb.suffix)
        }
    }

    private data class Parsed(val numbers: List<Long>, val suffix: String)

    private fun parse(raw: String): Parsed {
        val s = normalize(raw)
        val numbers = mutableListOf<Long>()
        var i = 0
        while (i < s.length) {
            if (!s[i].isDigit()) break
            var j = i
            while (j < s.length && s[j].isDigit()) j++
            numbers += s.substring(i, j).toLongOrNull() ?: 0L
            i = j
            // 版本号里的分隔符：`.` `-` `_` `+`。跳过后如果还是数字就继续下一段，
            // 不是数字就说明进入后缀了。
            while (i < s.length && (s[i] == '.' || s[i] == '-' || s[i] == '_' || s[i] == '+')) i++
        }
        return Parsed(numbers, s.substring(i).trim('.', '-', '_', '+'))
    }
}