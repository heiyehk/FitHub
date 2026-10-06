package com.heiyehk.fithub.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub REST API 的响应结构。
 *
 * GitHub 返回 snake_case，Kotlin 惯例是 camelCase，所以每个字段都要标 @SerialName。
 * 漏标不会报错：kotlinx.serialization 静默取默认值，表现为 id 为空、star 为 0。
 */

@Serializable
data class OwnerDto(
    val login: String = "",
    val type: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
)

@Serializable
data class LicenseDto(
    @SerialName("spdx_id") val spdxId: String? = null,
    val name: String? = null,
)

@Serializable
data class RepoDto(
    @SerialName("full_name") val fullName: String = "",
    val name: String = "",
    val owner: OwnerDto = OwnerDto(),
    val description: String? = null,
    val language: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("forks_count") val forksCount: Int = 0,
    @SerialName("subscribers_count") val subscribersCount: Int? = null,
    @SerialName("open_issues_count") val openIssuesCount: Int = 0,
    val topics: List<String> = emptyList(),
    @SerialName("pushed_at") val pushedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val license: LicenseDto? = null,
    val archived: Boolean = false,
    val fork: Boolean = false,
    /**
     * 是不是私有仓库。
     *
     * 目前这个 App 只申请 `read:user`，所以这里**永远是 false**。
     * 仍然映射它，是为了以后真拿到 `repo` scope 时不用再改 DTO 就能对上号 ——
     * 而不是等到那时才发现字段没读，于是整列仓库都显示成公开仓库。
     */
    val isPrivate: Boolean = false,
) {
    val slug: String get() = fullName.substringAfterLast('/')
    val lastPush: String get() = (pushedAt ?: updatedAt ?: "").take(10)
}

@Serializable
data class SearchResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("incomplete_results") val incompleteResults: Boolean = false,
    val items: List<RepoDto> = emptyList(),
)

@Serializable
data class AssetDto(
    val name: String = "",
    val size: Long = 0,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("download_count") val downloadCount: Int = 0,
    val state: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
) {
    /**
     * 校验文件 / 签名文件判定。
     *
     * 名单来自真实 release 的文件名：
     * - `*_sha256sums`（termux，无扩展名）
     * - `SHA256SUMS.txt`（scrcpy）
     * - `*.asc`（syncthing、scrcpy）
     *
     * 漏判的后果是这些文件被当成可安装产物，报出与校验文件无关的适配结论。
     */
    val isChecksum: Boolean
        get() {
            val lower = name.lowercase()
            return lower.endsWith(".sha256sums") ||
                lower.endsWith("sha256sums") ||          // termux: *_sha256sums
                lower.endsWith("sha256sums.txt") ||      // scrcpy: SHA256SUMS.txt
                lower.endsWith("sha256sum") ||           // syncthing: sha256sum.txt.asc
                lower.endsWith("sha1sum.txt.asc") ||
                lower.endsWith(".sha256") ||
                lower.endsWith("checksums.txt") ||
                lower.endsWith(".sig") ||
                lower.endsWith(".asc") ||
                lower.endsWith(".sha512") ||
                lower.endsWith(".md5")
        }

    val kind: String
        get() = when {
            isChecksum -> "SHA256"
            name.endsWith(".apk", true) -> "APK"
            name.endsWith(".aab", true) -> "AAB"
            name.endsWith(".msi", true) -> "MSI"
            name.endsWith(".dmg", true) -> "DMG"
            name.endsWith(".deb", true) -> "DEB"
            name.endsWith(".rpm", true) -> "RPM"
            name.endsWith(".tar.gz", true) || name.endsWith(".tgz", true) -> "TAR.GZ"
            name.endsWith(".zip", true) -> "ZIP"
            name.endsWith(".exe", true) -> "EXE"
            else -> "其它"
        }
}

@Serializable
data class ReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val body: String? = null,
    val assets: List<AssetDto> = emptyList(),
) {
    val date: String get() = (publishedAt ?: "").take(10)
}

@Serializable
data class UserDto(
    val login: String = "",
    val name: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val followers: Int = 0,
    val following: Int = 0,
    @SerialName("public_repos") val publicRepos: Int = 0,
    val hireable: Boolean? = null,
    val blog: String? = null,
    val type: String = "User",
    @SerialName("created_at") val createdAt: String? = null,
    /** Gravatar / GitHub 头像地址。空串 = 没给，UI 落回文字色块 */
    @SerialName("avatar_url") val avatarUrl: String = "",
    /**
     * 自己名下**私有**仓库的数量。
     *
     * 可空而不是默认 0：`/user` 只在 token 带 `read:user` 时才返回这个字段，
     * 不带的话整个键就不在响应里。写成 0 的话，「没有私有仓库」和
     * 「接口没告诉我们」会变成同一个值，界面上就会把后者写成前者 ——
     * 也就是当众宣称「你的数据全在这儿」，而实际上有一批仓库压根没被统计。
     *
     * null = 不知道，0 = 确实没有。UI 必须区分这两种。
     */
    @SerialName("owned_private_repos") val ownedPrivateRepos: Int? = null,
)

@Serializable
data class UserSearchResponse(
    @SerialName("total_count") val totalCount: Int = 0,
    val items: List<UserDto> = emptyList(),
)

@Serializable
data class RateDto(
    val limit: Int = 60,
    val remaining: Int = 60,
    val reset: Long = 0,
)

/**
 * `GET /repos/{owner}/{repo}/readme` 的响应。
 *
 * `content` 是 base64 编码的 README 原文（每行以 \n 分隔），`encoding` 固定为 base64。
 */
@Serializable
data class ReadmeDto(
    val name: String = "",
    val path: String = "",
    val content: String = "",
    val encoding: String = "base64",
    val size: Int = 0,
)

@Serializable
data class RateResources(
    val core: RateDto = RateDto(),
)

@Serializable
data class RateLimitDto(
    val resources: RateResources = RateResources(),
)
