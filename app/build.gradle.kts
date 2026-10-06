import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release 签名配置。
 *
 * 根目录放一个 keystore.properties（照 keystore.properties.example 抄）后
 * assembleRelease 会自动签名。没有该文件仍能出包，但产物未签名，无法安装。
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
// storeFile 相对**仓库根**解析，不是相对 app/。用 rootProject.file 而不是裸 file()，
// 因为裸 file() 在这个文件里是 app project 的方法，基准是 app/，照 example 写的
// `app/fithub-release.jks` 会解析成 app/app/... 然后静默退回未签名。
val hasReleaseKeystore = keystorePropertiesFile.exists() &&
    keystoreProperties.getProperty("storeFile")?.let { rootProject.file(it).exists() } == true

// [fithub] 前缀是 ASCII，方便在本机和 CI 日志里 grep。配了 properties 却解析不出
// keystore 时必须说出来 —— 否则构建照样成功，只是产出一个装不上的未签名包。
if (keystorePropertiesFile.exists() && !hasReleaseKeystore) {
    val raw = keystoreProperties.getProperty("storeFile")
    logger.warn(
        "[fithub] 存在 keystore.properties，但 storeFile=${raw ?: "<未设置>"} " +
            "解析到 ${raw?.let { rootProject.file(it).absolutePath } ?: "<无>"}，该文件不存在。" +
            "assembleRelease 会产出未签名 APK，装不上。路径相对仓库根填写。"
    )
}
logger.lifecycle(
    "[fithub] signing: propsExists=${keystorePropertiesFile.exists()} " +
        "rawStoreFile=${keystoreProperties.getProperty("storeFile") ?: "<none>"} " +
        "resolved=${keystoreProperties.getProperty("storeFile")?.let { rootProject.file(it).absolutePath } ?: "<none>"} " +
        "hasReleaseKeystore=$hasReleaseKeystore"
)

/**
 * GitHub OAuth App 的 client_id，Device Flow 唯一的前置条件。
 * 在 https://github.com/settings/developers 注册 OAuth App 后拿到的值：
 * **不带前缀的 20 位字母数字串**。`Iv1.` 那种带前缀的是 GitHub App 的 client_id，
 * OAuth App 没有前缀 —— 两者容易搞混，别照着 GitHub App 的样子去核对。
 *
 * 三级回退，缺哪一级都不影响构建 —— 新 clone 的人什么都不配也能出包：
 *   1. 环境变量 `FITHUB_CLIENT_ID` —— CI 和本地 shell 走这条
 *   2. gradle 属性 `fithub.githubClientId` —— 可写在 `~/.gradle/gradle.properties` 里
 *   3. 空串 —— 登录入口据此显示为不可用
 *
 * 三个刻意选择：
 *
 * - 用 `providers` 而不是 `System.getenv`：`org.gradle.configuration-cache` 开着，
 *   provider 会被记为构建输入，环境变量变了配置缓存才会失效；直接读 System.getenv 不会。
 * - 用 `firstOrNull { isNotBlank() }` 而不是 `orElse`：GitHub Actions 在密钥未配置时给出的是
 *   「已设置但为空」的环境变量，`orElse` 不会因此回退，会把后面的取值机会吃掉。
 * - `trim()`：从 GitHub Secrets 面板复制很容易带上换行或空格，不去掉会静默拼出坏值。
 *
 * 不入库不是因为它是秘密（它不是，Device Flow 连 client_secret 都不需要），而是
 * 少一份可以现成拿去钓鱼的素材，同时让每个 clone 的人填自己的。
 */
val githubClientId = sequenceOf(
    providers.environmentVariable("FITHUB_CLIENT_ID").orNull,
    providers.gradleProperty("fithub.githubClientId").orNull,
).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

// 只警告不失败：GitHub 换 client_id 格式、或有人 fork 后填了别的东西时，
// 不该让整个构建挂掉。正则故意宽松，只抓「明显不是 client_id」的值。
// 消息前缀 [fithub] 是 ASCII，方便在 CI 日志里 grep（管道会吃掉中文匹配）。
if (githubClientId.isNotEmpty() && !githubClientId.matches(Regex("[A-Za-z0-9_.-]{8,}"))) {
    logger.warn(
        "[fithub] FITHUB_CLIENT_ID / fithub.githubClientId 的值含有意外字符，构建不会因此失败，" +
            "但登录多半用不了。OAuth App 的 client_id 是不带前缀的 20 位字母数字串。",
    )
}

android {
    namespace = "com.heiyehk.fithub"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.heiyehk.fithub"
        minSdk = 26          // SigningInfo / PackageInstaller 能力下限
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"

        // 取值逻辑见文件顶部的 githubClientId（环境变量 / gradle 属性 / 空串）。
        buildConfigField(
            "String",
            "GITHUB_CLIENT_ID",
            "\"$githubClientId\"",
        )
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // R8 + 资源裁剪。依赖集很小，压缩后 APK 在 1.5 MB 量级
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 全局关掉了 BuildConfig，这里单独开：设置页要读真实 versionName，不能硬编码
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/previous-compilation-data.bin")
        }
    }

    lint {
        abortOnError = false
        // AGP 9.1.0 的 lintVitalAnalyzeRelease 在分析 Compose 源码时自身崩溃。
        // 已知工具缺陷，关闭 release 构建的 lint 门禁；./gradlew lint 仍可手动运行。
        checkReleaseBuilds = false
    }

    testOptions {
        // 单元测试跑在 JVM 上，android.* 桩方法需返回默认值而非抛异常
        unitTests.isReturnDefaultValues = true
    }
}

/**
 * 译文完整性检查。
 *
 * 比对 `values/strings.xml`（英文，默认）与 `values-zh/strings.xml`（中文）的：
 *
 * 1. **key 集合** —— 任一侧缺 key 就是错的。少一侧的界面会直接显示 key 原文。
 * 2. **格式化占位符** —— `%1$s` / `%d` 的个数与序号必须一致。中文和英文语序不同，
 *    翻译时漏掉或改错占位符**编译期发现不了**，运行时才显示成 `%1$s` 或直接崩。
 *
 * 比 lint 的 `MissingTranslation` 快且不依赖 lint 门禁（本项目 `checkReleaseBuilds=false`），
 * 挂在 preBuild 上，每次出包都会跑。
 */
val verifyTranslations by tasks.registering {
    group = "verification"
    description = "检查中英文 string key 集合与格式化占位符是否一致"

    val resDir = layout.projectDirectory.dir("src/main/res")
    val reportFile = layout.buildDirectory.file("reports/translation-gap.txt")
    inputs.dir(resDir).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(reportFile)

    doLast {
        fun parse(file: File): Map<String, String> {
            if (!file.exists()) return emptyMap()
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            val out = LinkedHashMap<String, String>(nodes.length)
            for (i in 0 until nodes.length) {
                val el = nodes.item(i)
                val name = el.attributes.getNamedItem("name")?.nodeValue ?: continue
                out[name] = el.textContent.orEmpty()
            }
            return out
        }

        // 抓 %1$s、%2$d、%s、%% 之外的形式化占位符
        val placeholder = Regex("%(?:\\d+\\\$)?[sdf]|%%")

        val en = parse(resDir.file("values/strings.xml").asFile)
        val zh = parse(resDir.file("values-zh/strings.xml").asFile)

        val missingZh = en.keys - zh.keys
        val missingEn = zh.keys - en.keys
        val badPlaceholder = (en.keys intersect zh.keys).filter { key ->
            placeholder.findAll(en.getValue(key)).map { it.value }.toList() !=
                placeholder.findAll(zh.getValue(key)).map { it.value }.toList()
        }

        val lines = buildList {
            if (missingZh.isNotEmpty()) {
                add("## 英文有、中文缺 (${missingZh.size})")
                missingZh.sorted().forEach { add("  $it  =  \"${en.getValue(it)}\"") }
            }
            if (missingEn.isNotEmpty()) {
                add("## 中文有、英文缺 (${missingEn.size})")
                missingEn.sorted().forEach { add("  $it  =  \"${zh.getValue(it)}\"") }
            }
            if (badPlaceholder.isNotEmpty()) {
                add("## 占位符不一致 (${badPlaceholder.size})")
                badPlaceholder.sorted().forEach { key ->
                    val e = placeholder.findAll(en.getValue(key)).map { it.value }.toList()
                    val z = placeholder.findAll(zh.getValue(key)).map { it.value }.toList()
                    add("  $key  en=$e  zh=$z")
                }
            }
        }
        val body = if (lines.isEmpty()) {
            "OK: ${en.size} 个 key，中英文集合与占位符全部一致\n"
        } else {
            lines.joinToString("\n")
        }
        reportFile.get().asFile.apply { parentFile.mkdirs() }.writeText(body)

        if (lines.isNotEmpty()) {
            throw GradleException(
                "中英文资源不一致，详见 ${reportFile.get().asFile}\n" +
                    lines.take(30).joinToString("\n") +
                    (if (lines.size > 30) "\n  ...（共 ${lines.size} 行）" else "")
            )
        }
        logger.lifecycle("[fithub] translations: ${en.size} keys, zh ${zh.size} keys, placeholders OK")
    }
}

tasks.named("preBuild") { dependsOn(verifyTranslations) }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Ktor CIO（纯 Kotlin 实现）承担网络层，kotlinx.serialization 负责 DTO
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.serialization.json)

    // 详情页「说明」tab。刻意只取 -m3 变体，不引图片加载（Coil），
    // 保持 release 包体积；README 里的图以占位处理。
    implementation(libs.markdown)

    // 判定逻辑在 JVM 上做单元测试：模拟器为 x86_64，arm64 分支在设备上覆盖不到。
    // 见 FitEngineTest。
    testImplementation(libs.junit)
    // 登录的错误映射要能对着真实抛出的异常做断言（UnresolvedAddressException 的
    // getMessage() 是 null、ServerResponseException 只在 expectStop 开启时抛），
    // 这些在模拟器上手抖出来一次当不了回归测试。版本与主源码严格同一个 ktor。
    testImplementation(libs.ktor.client.mock)
}
