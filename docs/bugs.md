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

---

## BUG-04 · 一个只做了一半的回调，让 265 行变成不可达代码

**现象**：登录后「我的 → 我的项目」点下去**毫无反应**，不闪退、不转圈、不报错。

**根因**：回调只拉了数据，从来没打开那一页。

```kotlin
onOpenMyProjects = {
    loadMyRepos()        // 拉完就没了
    // 缺的就是下面这一行
},
```

`myProjectsOpen` 全仓库只有声明、`= false`（×4）和 `if (myProjectsOpen) { … }`，
**没有任何一处 `= true`**。于是 `MyProjectsScreen.kt` 那 265 行 —— 连同它的
`onRepoTap` / `onRefresh` / `onBack` 三个回调 —— 从来没有被组合过一次。
用户点下去唯一发生的事是发了个请求，然后什么也没发生。

### 为什么这类 bug 能活下来

1. **编译零警告。** 少调一个状态赋值不是类型错误，Kotlin 没有任何机制会提示
   「你声明了这个状态但从不把它设成 true」。
2. **看起来是对称的。** 相邻的 `onLogout` 里就写着 `myProjectsOpen = false`，
   读代码的人会自然以为开关在别处也有一个 true —— 实际上没有。
3. **测试覆盖不到。** 这是纯 UI 导航状态，单测碰不到。

### 判据

**「声明了一个布尔状态，就去搜它所有的写入点」** —— 特别是断言它至少有一处
被设成 `true`。只有 `= false` 的开关不是「默认关着」，是**打不开**。

反向也成立：`onClick` 回调里如果只有副作用（发请求 / 改数据）而**没有任何状态写入**，
那它多半就是漏了一行。和已有代码对称着看，能比读注释快得多。

---

## BUG-05 · 用字符串相等比版本号，`v` 前缀让它永远报「有新版」

**现象**：「我的 → 检查更新」点一下，**已经是最新的版本也提示「发现新版本」**。

**根因**：

```kotlin
if (latest == BuildConfig.VERSION_NAME) "已是最新" else "发现新版本"
```

`latest` 是 GitHub 上的 **tag**，`versionName` 是 Gradle 里的**版本名**。
GitHub 的 tag 惯例带 `v`：`v0.0.2`。于是 `"v0.0.2" == "0.0.2"` 恒为 `false` ——
**版本号一模一样也判成不等**，每次都报有新版。

同一行还有两个问题：

2. **纯字典序**。`"0.0.10" < "0.0.9"`（`1` < `9`），一旦发到 0.0.10，
   相同的版本会被判成「远端更旧」。
3. **只有两档，没有「本机更新」**。装了开发版时，远端那个**更旧**的 release
   也会被说成新版本，用户照着去「更新」反而降级。

**修法**：新增 `VersionTag.compare()` —— 剥 `v`/`V` 前缀 → 数字段按数值逐段比
（缺的补 0，所以 `1.0` ≡ `1.0.0`）→ 数字段全等时没有后缀的更新。
三档齐全：已是最新 / 远端更新 / 本机更新。

### 回归测试

`VersionTagTest`（7 条），做过**变异验证**：把 `normalize` 的 `v` 前缀剥离去掉，
恰好 3 条转红（前缀、三档比较、预发布排序），其余 4 条不受影响 ——
证明这批断言确实在盯着这个 bug。

### 判据

**版本号是结构化的，不是字符串。** 任何 `==` 或 `<` 直接作用在版本号上的地方，
都要先问「两边是不是同一种格式的同一个东西」——
tag 带前缀、`-beta` 后缀、`1.0` 与 `1.0.0`，这些都是真实存在的。

以及：**只有「新」「不新」两档的判断，通常是漏了一档。** 「本机更新」在开发版
和预装渠道的场景下是常态，不是边角情况。

---

## BUG-06 · 面板的按钮状态从零开始，下载中枢却活在进程里

**现象**：点了「下载」**什么都没发生** —— 不转圈、不报错、不弹任何提示。
换个说法：主 CTA 变成了一个死按钮。

**根因**：同一件事（「这个产物现在什么状态」）有**两个数据源**，而且其中一个
比另一个活得久。

```kotlin
// 详情面板：remember 出来的，面板一关就没了
val install = remember(repo.id) { mutableStateOf<InstallStep>(InstallStep.Idle) }

// 下载中枢：object，进程活多久它活多久，比面板久得多
object DownloadCenter { val state: StateFlow<Progress> … }
```

面板打开时 `install` **一律从 `Idle` 起步**，从不看 `DownloadCenter.state`。
只要下载是在面板关着的时候跑的（前台服务继续下，用户切出去再回来 / 转屏 /
面板重建），两边就对不上：

| 全局 `DownloadCenter` | 面板 `installStep` | 用户看到的 | 点下去 |
|---|---|---|---|
| `Running`（还在下） | `Idle` | 按钮写「下载」 | `isBusyFor` 为真 → **静默 return，什么也没有** |
| `Ready`（已下完） | `Idle` | 按钮写「下载」 | 重新下一遍几十 MB |

第一行就是那个「点了没反应」。它是这整条链路上**唯一一条静默 return** ——
`startInstall` 里另外两个提前返回都会调 `onNoDownloadUrl()` 打开浏览器，
只有 `if (DownloadCenter.isBusyFor(asset.name)) return` 不给任何反馈。

### 修法

1. 面板打开时用 `restoreStep(DownloadCenter.state.value, repo.best?.name)` 还原一次
   （资产名对不上的一律当 `Idle` —— 全局那一份可能是别的仓库的，拿来冒充会显示假进度）。
2. **把那条静默 return 也改成有反馈**：不 return，而是把已经在跑的状态接到界面上。
   面板状态机不该有一个「点了不响」的分支。

### 判据

**「同一事实的两个副本，生命周期不一样」是 bug 的温床。** 凡是进程级单例
（`object` / `companion object`）持有状态，而界面另有一份副本，
就必须问一句：**冷启动 / 转屏 / 页面重建之后，这两份对得上吗？**

具体到写法：**状态是全局的，初始化就不能写死。** `remember { mutableStateOf(X) }`
里的 `X` 应该是「从全局源读出来的当前值」，而不是一个常量。

第二判据：**一个 `onClick` 分支如果什么都不做，它就是 bug。** 早返回要么给反馈、
要么把真实状态接上，「静默 return」在 UI 回调里永远说不出理由。

---

## BUG-07 · 写着「重试」的按钮接的是「返回」

**现象**：在用户 / 组织页上点「重试」，回到首页了。

**根因**：

```kotlin
is Async.Err -> Column {
    Text(prof.message, …)
    GhostButton(stringResource(R.string.person_retry), onBack)  // ← 重试接的是返回
}
```

`onBack` 是 `personLogin = null` —— 整页关掉，于是回到 Discover 那一个 tab，
也就是首页。文案写着「重试」，行为是「退出」，用户照着提示做却得到相反的结果。

配套的第二个 bug 让它更糟：[`openPerson`] 开头有
`if (personLogin == login) return`，而「重试」按的就是 `openPerson` ——
**就算把回调改对了，这个静默 return 也会让它依然不刷新**，因为页面已经开着、
login 也没变。少这一行的时候，重试按钮在任何情况下都是死的。

### 修法

- `PersonScreen` 单独给一个 `onRetry` 参数（默认 `onBack`，保持既有调用方不受影响）。
- `openPerson` 的早返回加上条件：`personLogin == login && personProfile !is Async.Err`。
  **「已经开着」和「上次失败了」是两件事**，前者该早退，后者正是重试要处理的情况。

### 判据

**按钮的文案承诺和它接的回调必须是同一件事。** 一个叫「重试」的东西接上「返回」，
编译器不会报错、单测也碰不到 —— 它只在用户真的失败一次时才暴露。

以及：**加一个早返回之前，先列一遍所有**需要**重新触发它的情况。**
「已经开着就别重复加载」这条守卫，写的时候没想到失败态也要能重来。

---

## BUG-08 · 打开仓库详情时把来路顺手关掉了，返回只能落回首页

**现象**（三条同一个根因）：

- 搜索结果 → 点仓库 → 返回 → **回到首页**，不是回到搜索结果
- 用户页 → 点仓库 → 返回 → **回到首页**，不是回到用户页
- 我的 → 我的项目 → 点仓库 → 返回 → 回到**我的**，不是回到**我的项目**

**根因**：每一页一个布尔量，而进详情时把来源页**顺手置成了 false**：

```kotlin
// 三条路径，同一个错
onRepoTap = { searching = false;       open(it.id, placeholder = it) }  // 搜索
onRepoTap = { personLogin = null;      open(it.id, placeholder = it) }  // 用户页
onRepoTap = { myProjectsOpen = false;  open(it.id, placeholder = it) }  // 我的项目
```

于是详情关掉之后，底下什么都不剩 —— 按返回只能落到 Discover 那个 tab。

### 为什么布尔量表达不了这件事

布尔量记录的是「**现在开着哪几页**」，而返回需要的是「**是怎么走到这儿的**」。
这两个问题不一样，布尔量只能回答前一个。要回答后一个，页面就得**按顺序叠起来**：
进详情不弹来源，返回时一层一层退。

顺带一提，把布尔量换成 `Int` 序号或者记一个 `returnTo` 字段能糊住单条路径，
但那只是把「有几条来路」这个数字从一个变成另一个 —— 加新页面时照样要想「这条要不要记来路」。
栈不需要回答这个问题。

### 修法

1. 新增 [com.heiyehk.fithub.ui.Page] 与 `pages: SnapshotStateList<Page>`，取代十二个布尔量。
2. `open()` 三个调用点**不再关来源页**。顺带白捡一个好处：页面没被销毁重建，
   所以搜索词、结果、滚动位置、排序选择全都原样保留。
3. 主体页的资料与仓库列表从两个全局变量改成 `Map<login, …>` ——
   页面在栈上等着期间不能丢数据，而且返回时仍然是**同一个人**那份。
4. 渲染时**只画栈顶那一页**（`isTop(page)`）。原来「栈里每页都画、靠代码里的先后顺序
   决定谁盖谁」的话，就要求「压栈顺序」和「声明顺序」永远一致，对不上时会出现
   「栈底的页面盖住了栈顶的」，而且没有任何提示。让叠放顺序从栈里读出来就不存在这个问题。
5. **把详情面板那三块挪到所有页面之下（视觉最上层）。** 这步不做的话前面全白做 ——
   Box 里后面的兄弟节点画在上面，详情留在原位会被搜索页整块盖住，
   用户点了仓库之后看到的还是上一页。

### 判据

**「关掉来源」是个需要论证的默认行为，不是顺手该做的事。**
写 `xxx = false` 的时候问一句：这一页还有可能回来吗？有就别关，让它压进栈里。

以及：**叠放顺序必须从数据里推出来，不能从代码排列顺序推出来。**
「谁画在谁上面」如果取决于两个列表碰巧一致，那它早晚会不一致。

**多层叠加时，先确认 z-order 再改逻辑** —— 逻辑改对了但被盖住，表现和没改一样。

---

## BUG-10 · 面包屑的仓库名那一级被当成「它自己」，于是回不到根

**现象**：在代码浏览里进了任意一个第一层子目录，**面包屑上的仓库名点不动**，
从那里永远回不到仓库根。

**根因**：可点条件写成了「跳过第 0 项」：

```kotlin
val clickable = index in 1 until segments.lastIndex
```

而 segments 是 `[仓库名, 子目录…]`，索引 0 是仓库名。
只有一层子目录时 `1 until 1` 是**空区间**，一个都点不了 ——
而「仓库根 + 一级子目录」恰恰是最常见的情况。

**那句注释写的是错的**：

> 仓库名和当前所在目录都不能点 —— 点自己没有意义

**仓库名不是「它自己」，它就是仓库根目录**，是每一层往上退的终点。
只有**当前所在的那一级**不可点。

### 顺带发现的第二个问题

就算把它改成可点，`onPick(seg)` 传的 `seg` 是那一项的**字符串**，
仓库名那一项存的是 `"flutter"` 而不是 `""`。点下去会把 `dir` 设成 `"flutter`，
于是去请求 `contents/flutter` —— 那个路径不存在。

只是从「点不了」变成「点了报错」，不是「点了能用」。
修法是让列表每项存**路径**：第 0 项存 `""`，其余存累计路径，显示名只在渲染时取。
**显示名和路径混在同一份列表里，是这类 bug 的来源。**

### 我为什么没自己发现

交付前我只验了「根目录」和「三层」两种情况，**两层反而跳过了** ——
而两层最常见。上一条我说「flutter 的 `bin` 里没有子目录所以没测到」也是错的：
`bin/internal` 就在列表里，只是我没滚到。不是环境问题，是我没测。

### 修法

`val clickable = index < segments.lastIndex`，加上 segments 改存路径。

### 验证

不用眼睛看颜色，直接读 UI dump 的 `clickable` 属性：

```
'flutter' clickable=false   ← 仓库标题
'flutter' clickable=true    ← 面包屑仓库名（修复前 false）
'bin'     clickable=false   ← 当前所在
```

点面包屑的 `flutter` → 回到根目录，列表恢复。崩溃 0 条。

### 判据

**「每一条都能点」和「每一条都该能点」是两件事**，写边界条件时先问「哪一种是真的」。

以及：**验收要覆盖最常见的情况，不是最边缘的情况。**
两层面包屑是最常见的，我偏偏只测了零层和三层。

---

## BUG-09 · 刷新作废了一个从来就不存在的缓存 key

**现象**：仓库详情点顶栏第二个按钮（刷新）**看起来没反应**。
不报错、不转圈，stars / 描述 / 最后推送会变，但 **release 列表纹丝不动** ——
而 release 列表恰恰是详情页的主体。顶栏那行「N 分钟前」还会显示成「刚刚」，
界面一边说这是最新的，一边摊着旧数据。

**根因**：缓存 key 里带了 `perPage`，作废的地方却写死了另一个值。

```kotlin
// GitHubApi.releases()：key 里带 perPage
get("$base/repos/$fullName/releases", "releases-$fullName-$perPage", ...)

// GitHubApi.invalidateRepo()：作废的是 -3
invalidate("repo-$fullName", "releases-$fullName-3")
```

而 `FitRepository.RELEASES_PER_PAGE` 早就从 3 改成了 20。
**`releases-<仓库>-3` 这个 key 从来没有被写出来过**，所以每次刷新都是删空气。

旁边那段注释写的正是这个毛病：

> 两个 key 都要作废，否则刷新后 releases 仍读旧的，**看起来像没刷新**。

改那个数字（BUG-02 修 v2rayNG 被判成空仓库）的时候只动了 `RELEASES_PER_PAGE`，
没回头看注释在说什么。

### 为什么不能简单地把 `perPage` 从 key 里去掉

先想过「反正都是同一个仓库的 release，去掉后缀一个 key 就够」。
**那是错的**：`get()` 每次都会写回缓存，而缓存里存的是**那次请求实际返回的条数**。
`hasInstallable`（perPage=1）先跑一次就会把**一条结果**写进去，
详情页（perPage=20）随后读到它 —— 页面上只剩一个版本。
这正是 BUG-02 那一类事故换个方向重现。

所以 `perPage` 必须留在 key 里。真正能根治的是**按前缀作废**：
新增一个 perPage 不会再漏，而且不必维护取值清单。

### 一个更值得记的教训：第一版测试是假绿的

修完之后写了 6 条 `RepoCachePrefixTest`，只测前缀删除本身。然后做变异验证 ——
把 `invalidateRepo` 改回写死 `-3`，**206 条测试一条都没红**。

因为没有一条断言穿过「`invalidateRepo` → releases 的真实 key」这条路径。
**测一个「用对了就永远对」的纯函数，挡不住接线接错。**

补的 `GitHubApiInvalidateTest` 直接用 `GitHubApi` 自己的 key 构造函数造缓存，
再断言 `invalidateRepo` 把它清掉。同样的变异让 3 条全部转红，其余 203 条不受影响。

### 判据

**同一个 key 家族的两端必须由同一个函数拼出来。**
`discoverCacheKey` / `searchCacheKey` 早就抽出来了，releases 这条漏了 ——
写死一个数字和从常量读一个数字，在代码里长得一模一样。

**「作废清单」这类东西会过期，而过期时没有报错。**
写死取值清单能挡住「有人又改了这个数字」，但挡不住「新增了一个取值」。
按前缀删才是不用维护清单的做法。

以及：**断言要穿过真正出问题的那条接线。**
从症状倒推，第一个测试通常会落在最底层那个「看起来最像出问题」的函数上 ——
而那里恰好是最不容易接错、也最不需要测的一层。

### 附带的两个修正

1. 测试写完立刻发现自己**注释里的推理是错的**：原以为前缀 `releases-owner_repo-`
   会误伤 `owner/repo2`，断言当场转红 —— 那个 key 是 `releases-owner_repo2-20`，
   `-` 的位置对不上，并没有被误伤。真正会被连坐的是 `owner/repo-2`。
   注释和测试一起改了。**测试除了抓 bug，也会抓自己对 bug 的错误理解。**
2. 第一版有两条测试把「保留同仓库元信息缓存」当成了正确行为 —— 而
   `invalidateRepo` 本来就该清元信息，那正是「刷新」的定义。改回断言两份都清。

