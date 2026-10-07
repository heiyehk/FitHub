package com.heiyehk.fithub.data.install

import android.content.Context
import com.heiyehk.fithub.data.LocalStore
import com.heiyehk.fithub.data.Repo
import com.heiyehk.fithub.data.parse.ApkInfo
import com.heiyehk.fithub.data.parse.ApkParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 清单里的一条：一个已经落到公共下载目录的安装包。
 *
 * [displayName] 必须是**盘上实际的名字**（`ApkStore.publish` 回读 MediaStore 之后
 * 拿到的那个），不是我们请求的名字 —— 分区存储会按 MIME_TYPE 改扩展名，两者可能不同。
 */
@Serializable
data class DownloadedApk(
    val repoName: String,
    val assetName: String,
    val displayName: String,
    val sizeBytes: Long,
    /** 我们自己算的完整 SHA-256。[verified] 表示它和发布方给的值比对过 */
    val sha256: String = "",
    val verified: Boolean = false,
    /**
     * 清单里读出来的真实信息。
     *
     * 这一组取代了原来那个独立的「解析」动作：以前用户要单独点一下「解析」，
     * 才会下载并读清单；现在下载完成时顺手就读了，界面直接用这里的事实。
     * [packageName] 空 = 不是个能装的 APK（或者读清单失败），此时其余几项也为空。
     */
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val signerSha256: String = "",
    val abis: List<String> = emptyList(),
    /** 清单里 application 的 debuggable 标志。是判断「这是给用户装还是给自己调试」的唯一依据 */
    val debuggable: Boolean = false,
    /**
     * 这一组清单字段**确实是从 APK 里读出来的**。
     *
     * 必须显式存，不能靠「字段非零」去推断含义：早期版本的清单里没有 minSdk /
     * ABI / 签名这几项，反序列化出来全是 0 和空串，而 `minSdk = 0`、
     * `abis = []` 作为**真实值**是完全合法的 —— 于是「不知道」和「清单说是这样」
     * 从此同形，界面会把一个明明适配本机的包判成不匹配，还写上一句
     * 「清单里只列了（空），本机是 x86_64」。
     *
     * 读不到清单（不是 APK / 包是坏的）时为 false，界面只当它是「下好的文件」，
     * 不给适配结论。
     */
    val manifestRead: Boolean = false,
    /** 已经通过 FitHub 装成功过一次 */
    val installed: Boolean = false,
    val at: Long,
) {
    val key: String get() = "$repoName/$assetName"

    /** 读不出包名 = 装不了，只能是个已经下好的普通文件 */
    val installable: Boolean get() = packageName.isNotBlank()

    fun file(): File = File(ApkStore.publicDir(), displayName)
}

/**
 * 「已下载」清单 —— **落盘的**，不是内存里的。
 *
 * 之前「这个包下过了没有」这件事只活在 [DownloadCenter] 的进程级 StateFlow 上，
 * 进程一被回收就归零：用户下完没装，隔天回来界面显示的又是「没下过」。
 * 而文件本身却好好地躺在盘上没人认领。现在以这份清单为事实源。
 *
 * 读的时候逐条验一次文件还在不在：公共目录是**用户**的地盘，他可以随手删掉一个
 * APK，或者用清理工具扫一遍。留着一条指向空文件的记录，只会让「打开」点下去报一个
 * 用户完全看不懂的错。
 */
object ApkLibrary {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun store(context: Context) = LocalStore(context.filesDir, json)

    private const val KEY = "apk-library"

    /**
     * 活着的条目，按下载时间倒序。
     *
     * 死条目（文件被删了）直接从返回里滤掉，**并且顺手清掉记录** —— 留着它们只会
     * 让下一次 [record] 的去重逻辑以为「已经记过了」，用户再下一遍却拿不到新文件。
     */
    fun list(context: Context): List<DownloadedApk> {
        val all = store(context).read(KEY, emptyList<DownloadedApk>())
        val (alive, dead) = all.partition { it.file().exists() && it.file().length() > 0L }
        if (dead.isNotEmpty()) {
            store(context).write(KEY, alive)
        }
        return alive.sortedByDescending { it.at }
    }

    fun get(context: Context, repoName: String, assetName: String): DownloadedApk? =
        list(context).firstOrNull { it.repoName == repoName && it.assetName == assetName }

    /**
     * 记一条。同 [DownloadedApk.key] 已存在时**并入旧条目**并更新时间。
     *
     * 必须保留旧条目上我们自己没带过来的字段（尤其是 [DownloadedApk.installed]）：
     * 下载同一个包两次，第二次下载成功不代表它又被装过一次。
     */
    fun record(context: Context, apk: DownloadedApk) {
        val current = list(context)
        val idx = current.indexOfFirst { it.key == apk.key }
        val merged = if (idx >= 0) {
            val old = current[idx]
            apk.copy(installed = apk.installed || old.installed)
        } else {
            apk
        }
        val next = current.toMutableList().also {
            if (idx >= 0) it[idx] = merged else it.add(merged)
        }
        store(context).write(KEY, next.sortedByDescending { it.at }.take(MAX))
    }

    /** 只改一个标记，不重写整条 —— 避免把没读到的字段覆盖成空。 */
    fun setInstalled(context: Context, key: String, installed: Boolean) {
        val next = list(context).map { if (it.key == key) it.copy(installed = installed) else it }
        store(context).write(KEY, next)
    }

    /**
     * 补写清单事实。用在「条目在、文件也在，但当初没读过清单」的情况。
     *
     * 早期版本记的条目没有 [DownloadedApk.manifestRead]，那几项反序列化出来是 0 和
     * 空串，而它们作为**真实值**也完全合法 —— 分不清「不知道」和「清单说是这样」。
     * 趁文件还在盘上补读一次（本地文件，几十毫秒）比让用户重新下一次几十 MB 划算得多。
     */
    fun setManifest(context: Context, key: String, info: ApkInfo) {
        val next = list(context).map {
            if (it.key != key) {
                it
            } else {
                it.copy(
                    packageName = info.packageName,
                    versionName = info.versionName,
                    versionCode = info.versionCode,
                    minSdk = info.minSdk,
                    targetSdk = info.targetSdk,
                    signerSha256 = info.signerSha256.orEmpty(),
                    abis = info.abis,
                    debuggable = info.isDebuggable,
                    manifestRead = true,
                )
            }
        }
        store(context).write(KEY, next)
    }

    const val MAX = 100
}

/**
 * 把清单里已经存下的清单事实合回产物。
 *
 * 这是原来「解析」按钮干的事，但**不再读 APK 文件** —— 事实下载时就读过一次、
 * 存在 [DownloadedApk] 里了，这里只是重新组装。差别很实在：每开一次详情页重读
 * 十几个几十 MB 的 APK 清单要几百毫秒，而这是纯内存操作。
 *
 * 只处理 [DownloadedApk.manifestRead] 为真的条目：
 * - 读不出包名 = 不是个能装的 APK（`.zip`、`.sha256` 之类），没有清单可言
 * - 没读过清单 = 早期版本记的条目，那些字段是「不知道」而不是真实值 0 / 空，
 *   合上去会把适配结论算错
 */
fun Repo.withDownloadedFacts(library: List<DownloadedApk>): Repo {
    val usable = library.filter { it.repoName == id && it.installable && it.manifestRead }
    if (usable.isEmpty()) return this
    val byName = usable.associateBy { it.assetName }
    val enriched = assets.map { a ->
        val e = byName[a.name]
        if (e == null) {
            a
        } else {
            ApkParser.enrich(
                a,
                ApkInfo(
                    packageName = e.packageName,
                    versionName = e.versionName.ifBlank { "—" },
                    versionCode = e.versionCode,
                    minSdk = e.minSdk,
                    targetSdk = e.targetSdk,
                    signerSha256 = e.signerSha256.ifBlank { null },
                    abis = e.abis,
                    isDebuggable = e.debuggable,
                    // enrich 用不到这两项，写成显然的值而不是空字符串占位，
                    // 万一以后有人在 enrich 里读它，也不会拿到一个假值
                    splitNames = emptyList(),
                    sizeBytes = e.sizeBytes,
                ),
            )
        }
    }
    return if (enriched == assets) this else withAssets(enriched)
}

/**
 * 一个产物这一行现在该显示什么、按下去该做什么。
 *
 * 四态缺一不可，而且**顺序不能换**：
 * - 盘上没有 → 下载
 * - 下好了，但不是安装包（或清单读不出来）→ 没有下一步
 * - 是安装包、本机没装 → 安装
 * - 本机已经装上了 → 打开
 *
 * 只判断「文件在不在」是不够的：文件躺在下载目录里 ≠ 用户装过。之前只有
 * 「下载 / 下载完成」两态，于是下完的包永远显示一个对勾，用户想装还得自己
 * 切到别的入口去。
 *
 * 而少了 NONE 那一支会引入一个更糟的失败：GitHub Release 上除了 APK 还有
 * `SHA256SUMS.txt`、`.zip`、`.tar.gz`，它们照样走同一条下载链路、照样落进
 * 下载目录。把它们也标成「安装」，用户点下去就是拿一个文本文件去喂系统
 * 安装器，然后收到一句莫名其妙的失败。
 */
enum class ApkAction { DOWNLOAD, NONE, INSTALL, OPEN }

/**
 * 清单查得到 + 是不是安装包 + 本机装没装 = 这一行现在该做什么。
 *
 * 判「装没装」要查真实的启动 Activity（[ApkInstaller.isInstalled]），不能只看
 * `getPackageInfo` 能不能拿到：有些包根本没有 LAUNCHER 入口，那种情况它永远
 * 装不上也不该显示「打开」。
 */
fun actionFor(context: Context, entry: DownloadedApk?): ApkAction = when {
    entry == null -> ApkAction.DOWNLOAD
    // 读不出包名 = 它不是个能装的 APK（或者是坏包）。不是「还没装」，是「装不了」
    entry.packageName.isBlank() -> ApkAction.NONE
    // 已装应用的更新必须给「安装」，不能给「打开」：旧版正装着，isInstalled
    // 必然为 true，于是界面上唯一能点的就是「打开」，点开还是旧版，新下的那个
    // 永远装不上。本项目自己的自更新就是第一个撞上这个的。
    ApkInstaller.isUpdateOfInstalled(context, entry) -> ApkAction.INSTALL
    ApkInstaller.isInstalled(context, entry.packageName) -> ApkAction.OPEN
    else -> ApkAction.INSTALL
}
