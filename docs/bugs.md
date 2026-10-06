# BUG 台账

只记**改完之后仍然值得写下来**的坑：光看代码看不出来、而且一定会有人再犯一遍的那种。
每个条目都要能回答「为什么当初会写错」——只记现象不记成因，下一个人照样会踩。

---

## BUG-01 · 一次 DNS 抖动把整个 GitHub 登录判死

**现象**：用户在浏览器里完成授权，切回 App 看到
`Could not sign in / Exchanging the device code for a token failed`。
重新点「Start sign-in」能拿新码，但只要流程中网络再抖一次就会再次失败。

**根因**：`DeviceFlowClient.pollForToken` 原来写成
`runCatching { … }.getOrElse { … }`，并且把**所有**异常都当成终局。

实测（AVD + release 包，插桩打出的真实栈）：

```
15:18:38.577 poll tick -> Pending
15:18:43.588 pollForToken threw java.nio.channels.UnresolvedAddressException msg=null
15:18:43.589 poll tick -> Failed
```

进程自始至终没被杀掉。设备码在 GitHub 那边还有 14 分钟有效，
我们却因为一次 DNS 解析失败就再也不问了。

### 三个叠在一起的陷阱

1. **`runCatching` 吞掉 `CancellationException`。**
   它捕获的是所有 `Throwable`。协程被取消（页面离开、作用域关闭）时，
   会被当成一次「换取 token 失败」报给用户 —— 而那次失败根本没发生过。

2. **`UnresolvedAddressException.getMessage()` 返回 `null`。**
   旧代码 `if (raw == null) login_err_exchange`，于是 DNS 失败显示成
   「换取 token 失败」—— 归因完全错误。
   更讽刺的是 UI 层 `explain()` 早就写好了 `login_err_dns` 分支，
   但因为压根没有原文传上来，那条文案**永远不可达**。

3. **`UnresolvedAddressException` 继承的是 `IllegalArgumentException`，不是 `IOException`。**
   按 `IOException` 抓传输层异常时会精确地漏掉「DNS 解析不了」这个最常见的那个。
   这是照着「网络异常都是 IOException」的直觉写代码时最容易犯的错。

**修法**：
- 新增 `PollResult.Unreachable`：传输层失败（DNS / IO / 5xx）是**可重试**的，
  轮询循环退避重试、连续 5 次才认输（避免永远转圈），设备码全程留在界面上。
- **申请设备码那一步同样要重试**（`DeviceResult.Unreachable` + 界面退避 3 次）。
  这一点是后来补上的：原本只有轮询会重试，第一步仍把「连不上 GitHub」和
  「GitHub 拒绝了这个 client_id」压在同一条终局路径上，一次 DNS 抖动就要用户
  手动点「Start sign-in」。**同一个原理，两处都要守。**
- `catch (ce: CancellationException) throw ce` 放在 `catch (t: Throwable)` **之前**。
- `isTransientTransport()` 里显式列出 `UnresolvedAddressException`。
- `describe()` 在 `message` 为空时回落到异常类名 —— 宁可给一句生硬的原文，
  也不要给「未知错误」，因为原文会附在报错下方，出问题时没人能查。

**顺带修的第二个 bug**：Device Flow 的关键动作发生在别的应用里，
进程很容易在那段时间被杀，回来时内存里的状态全没了，用户只能重头再来。
现在设备码落盘在 `data/PendingLogin.kt`，进登录页时自动接上轮询。

### 回归测试

`DeviceFlowClientTest`（18 条）。这批测试做过**变异验证**：把
`isTransientTransport()` 改回恒 `false`、并让取消走普通失败分支，
恰好 6 条转红（3 条传输层可重试 + 1 条类名回落 + 2 条取消传播），
其余 155 条不受影响 —— 证明这些断言确实在盯着这个 bug，不是摆设。

> 写这类测试时注意：JUnit4 要求测试方法返回 `void`。
> 用 `fun x() = runBlocking { … }` 表达式体时，`runBlocking` 的返回值会把方法
> 返回类型变成 `PollResult`，整个测试类直接 `initializationError` —— 一条都跑不了。
> 必须写 `runBlocking<Unit>`。

---

## BUG-02 · 拉 3 条 release + 过滤预发布 = 把有产物的仓库说成空仓库

**现象**：v2rayNG 详情页显示红色「无法解析安装包 / 这个仓库只发过预发布版本」，
下面写「这个仓库没有发布任何产物，下面也没有可展开的」。
而它 GitHub 上明明挂着一堆 APK，用户截图里 2.3.9 / 2.3.8 … 全是预发行，
再往下 2.2.6 是**最新发行版**。

**根因**：两个无害的设计撞在一起。

1. `FitRepository.detail` 只请求 `api.releases(fullName, 3)` —— **3 条**。
2. `ReleasePick.visible` 把预发布整条滤掉（这是对的）。

v2rayNG 最新的 3 条恰好全是预发布，滤完 `stable` 为空 → `assets` 为空 →
`withAssets` 判成 `Verdict.Unknown` → 页面断言「没有产物」。
**真正的正式版 2.2.6 排在第 10 位，我们压根没去请求。**

最讽刺的是，代码里本来就写着这个隐患：

```kotlin
// 开了预发布就得多拉几条，否则被滤掉之后可能只剩空列表
includePrerelease = Prefs.state.value.includePrerelease,
...
api.releases(fullName, 3)   // ← 注释说要多拉，实际传的就是 3
```

**修法**：
- `RELEASES_PER_PAGE = 20`（GitHub 允许到 100，但产物要全部展开，20 是首屏速度的权衡）。
- 正式版一条都没有时**回退到预发布**并把产物照样列出来，同时用
  `Repo.prereleaseOnly` 标出来，由 `FitEngine.prereleaseNotice` 说明「都是预发布」。
  **宁可标着预发布摆出来，也不要显示成空仓库** —— 后者会让用户以为得自己发包。
- 顺带给产物加了版本切换 chip（按 `Asset.tag` 分组），预发布与正式版可以来回切。

**回归测试**：`GitHubMapperPrereleaseTest` 9 条，其中一条直接复刻 v2rayNG 场景。

**判据**：「先取一个小的窗口，再对它做筛选，最后用筛选结果断言事实」——
这三步串起来时，窗口边界就是事实的边界。**凡是「筛选后为空 ⇒ 断言不存在」的地方，
都要问一句：空是因为真的没有，还是因为窗口太小。**

---

## BUG-03 · 用 `areNotificationsEnabled()` 当「用户已关掉通知」的判据

**现象**：加了通知权限申请代码，**编译通过、逻辑看着没问题，弹窗就是一次都不弹**。
`dumpsys notification` 显示 `importance=NONE`，前台服务的进度通知用户永远看不到。

**根因**：

```kotlin
if (nm?.areNotificationsEnabled() == false) return   // ← 想拦「用户在设置里关掉了」
requestPermissions(arrayOf(perm), REQ_NOTIFICATIONS)
```

Android 13+ 上，**「从未授权」和「用户在系统设置里明确关掉」返回的都是 `false`**。
这个守卫本意是省掉一次白费劲的弹窗，实际把「还没申请过」也一并挡掉了，
于是永远走不到 `requestPermissions`。**代码是对的，判据是错的。**

**修法**：只用 `shouldShowRequestPermissionRationale` 当闸门（用户拒绝过一次后才为 true），
拿不到可靠的「已关闭」信号就不去猜。

**判据**：平台 API 的布尔返回常常把「没设置过」和「显式设成某个值」压成同一个值。
拿它当守卫之前，先确认它区分得开这两种情况 —— **区分不开就别拿它做判断**，
宁可少一个优化，也别让整条路径静默失效。

