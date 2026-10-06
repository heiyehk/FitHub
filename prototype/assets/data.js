/* ══════════════════════════════════════════════════════════════════
   FitHub · 原型数据层（演示用示例，非真实抓取结果）
   适配状态由 DEVICE 与 dist 声明交叉比对得出，与产品描述的 Step 3 一致
   ══════════════════════════════════════════════════════════════════ */

const TODAY = new Date('2026-10-04T09:00:00');

/* 本机 —— 一切适配结论的基准 */
const DEVICE = {
  name: 'Pixel 9 Pro',
  abi: 'arm64-v8a',
  sdk: 35,
  sdkLabel: 'Android 15',
  abis: ['arm64-v8a','armeabi-v7a','x86_64','x86'],
  pkg: 'com.google.android.apps.fitness',
  installed: [
    { repoId:'libretube', version:'1.5.0', code:150, signing:'same' },
    { repoId:'newpipe',  version:'3.1.2', code:3120, signing:'same' },
    { repoId:'aegis',    version:'1.6.1', code:1601, signing:'diff' },
    { repoId:'syncthing',version:'1.27.4', code:1274, signing:'same' }
  ]
};

const tile = (bg, fg) => ({ bg, fg });

const REPOS = [
  {
    id:'libretube', name:'LibreTube', owner:'ibreahermes', monogram:'LT',
    desc:'无广告、无追踪的 YouTube 前端，Material You 主题，支持 Piped 与 Invidious 后端。',
    lang:'Dart', langColor:'#0175C2', langs:[62,20,11,7],
    stars:28400, forks:3120, watchers:412, issues:318,
    version:'1.8.2', code:182, date:'2026-09-28',
    topics:['android','flutter','youtube','material-you'],
    tile: tile('#E7EFFA','#12447F'),
    history:[
      { tag:'1.8.2', date:'2026-09-28', pre:false, notes:['字幕加载速度提升约 40%','修复 Android 15 下的 edge-to-edge 裁切','新增 Piped 实例健康度提示'] },
      { tag:'1.8.1', date:'2026-08-19', pre:false, notes:['修复播放列表排序错乱','更新 Material You 动态取色'] },
      { tag:'1.7.0', date:'2026-06-02', pre:false, notes:['新增手势返回拦截','底层播放器升级到 media3 1.6'] }
    ],
    readme:{
      desc:'LibriTube 是一个 Material You 风格的 YouTube 客户端，强调「不追踪、无广告、可自建后端」。APK 直接从 Release 下载即可，不需要 Google Play。',
      install:'直接从 Release 页面下载 arm64-v8a 版本，最低 Android 5.0。',
      points:['不采集任何观看历史，默认关闭个性化','支持 Piped / Invidious 两种后端，可自建','全部界面文案已本地化，含简体中文']
    },
    dist:{ ext:'apk', stem:'LibreTube', abis:['arm64-v8a','armeabi-v7a','x86_64','universal'], minSdk:21, sdkLabel:'Android 5.0+', signed:'release', mb:28.4 }
  },
  {
    id:'newpipe', name:'NewPipe', owner:'TeamNewPipe', monogram:'NP',
    desc:'轻量的 YouTube 前端，不下载视频、不记录观看，GPLv3 开源十年。',
    lang:'Java', langColor:'#B07219', langs:[71,18,7,4],
    stars:32100, forks:4950, watchers:520, issues:640,
    version:'3.1.4', code:3140, date:'2026-10-01',
    topics:['android','youtube','privacy','f-droid'],
    tile: tile('#F6EDE0','#7A5312'),
    history:[
      { tag:'3.1.4', date:'2026-10-01', pre:false, notes:['修复 3.1.3 的导入崩溃','字幕样式新增紧凑模式'] },
      { tag:'3.1.3', date:'2026-08-06', pre:false, notes:['提取器更新','修复深色模式对比度'] }
    ],
    readme:{ desc:'NewPipe 是长期维护的开源 YouTube 客户端，只读取公开信息，不下载视频内容。', install:'Release 里提供 universal 与分 ABI 两种包。', points:['无追踪、无广告','离线下载仅音频','多实例与队列管理'] },
    dist:{ ext:'apk', stem:'NewPipe', abis:['universal'], minSdk:21, sdkLabel:'Android 5.0+', signed:'release', mb:9.8 }
  },
  {
    id:'aegis', name:'Aegis Authenticator', owner:'beemdevelopment', monogram:'AG',
    desc:'支持生物识别与硬件密钥的两步验证器，导出加密备份。',
    lang:'Kotlin', langColor:'#A97BFF', langs:[88,7,5],
    stars:21800, forks:1500, watchers:290, issues:88,
    version:'1.6.4', code:1640, date:'2026-09-30',
    topics:['android','2fa','security','kotlin'],
    tile: tile('#EFE9FA','#5B3E9E'),
    history:[
      { tag:'1.6.4', date:'2026-09-30', pre:false, notes:['新增通行密钥 Passkey 支持','导入流程改为分批写入，大文件不再 ANR'] },
      { tag:'1.6.3', date:'2026-08-11', pre:false, notes:['修复 Android 14 上生物识别弹窗延迟'] }
    ],
    readme:{ desc:'Aegis 是一个开源的认证器应用，支持 TOTP 与 HOTP，备份以口令加密后保存在本地。', install:'注意：本项目在 CI 中使用自签名证书，与 Google Play 版本签名不同。', points:['生物识别与设备凭据双重解锁','导入导出全程本地加密','支持从 Google Authenticator 平滑迁移'] },
    dist:{ ext:'apk', stem:'Aegis', abis:['arm64-v8a','armeabi-v7a','universal'], minSdk:26, sdkLabel:'Android 8.0+', signed:'ci-selfsigned', mb:6.2 }
  },
  {
    id:'termux', name:'Termux', owner:'termux', monogram:'TX',
    desc:'Android 上的终端与 Linux 环境，无需 root。',
    lang:'Java', langColor:'#B07219', langs:[79,14,4,3],
    stars:48600, forks:2350, watchers:610, issues:220,
    version:'0.119.0', code:1190, date:'2026-10-02',
    topics:['android','terminal','linux','shell'],
    tile: tile('#EAEEEE','#2B4B4B'),
    history:[
      { tag:'0.119.0', date:'2026-10-02', pre:false, notes:['终端底层迁移到 Termux core 0.13','修复 Android 15 的软键盘遮挡'] },
      { tag:'0.118.2', date:'2026-09-14', pre:false, notes:['修复 pkg 安装时的权限弹窗重复'] }
    ],
    readme:{ desc:'Termux 把常见的 Linux 工具链搬进 Android，不依赖 root，源码仓库与预编译包都在这里。', install:'装好后再从 F-Droid 安装 Termux:API 等插件，注意签名必须一致。', points:['apt / pkg 包管理','支持多会话与 tmux','可挂载外部存储'] },
    dist:{ ext:'apk', stem:'termux-app', abis:['arm64-v8a','armeabi-v7a','x86_64'], minSdk:24, sdkLabel:'Android 7.0+', signed:'release', mb:94.7 },
    unparsable:true
  },
  {
    id:'syncthing', name:'Syncthing', owner:'syncthing', monogram:'SY',
    desc:'去中心化的文件同步，没有中心服务器，点对点传输。',
    lang:'Java', langColor:'#B07219', langs:[83,10,4,3],
    stars:39400, forks:3400, watchers:480, issues:150,
    version:'1.27.6', code:1276, date:'2026-09-24',
    topics:['android','sync','p2p','file'],
    tile: tile('#E6F0EE','#155A4C'),
    history:[
      { tag:'1.27.6', date:'2026-09-24', pre:false, notes:['改进超大目录的扫描速度','新增 Web UI 的深色模式开关'] },
      { tag:'1.27.5', date:'2026-08-30', pre:false, notes:['修复部分设备上的电池优化误判'] }
    ],
    readme:{ desc:'Syncthing 让多台设备之间直接同步文件，没有第三方服务器，也不上传元数据。', install:'Release 提供分 ABI 包，arm64 设备请选 arm64-v8a。', points:['端到端 TLS 加密','版本化文件历史','可作为系统服务常驻'] },
    dist:{ ext:'apk', stem:'syncthing-app', abis:['arm64-v8a','armeabi-v7a','x86_64'], minSdk:24, sdkLabel:'Android 7.0+', signed:'release', mb:22.1 }
  },
  {
    id:'organicmaps', name:'Organic Maps', owner:'organicmaps', monogram:'OM',
    desc:'开源离线地图与导航，矢量地图，无追踪无广告。',
    lang:'C++', langColor:'#00599C', langs:[64,22,9,5],
    stars:11200, forks:1900, watchers:210, issues:190,
    version:'2026.10.02', code:261002, date:'2026-10-02',
    topics:['maps','navigation','offline','gis'],
    tile: tile('#E8F1F6','#0F4C74'),
    history:[
      { tag:'2026.10.02', date:'2026-10-02', pre:false, notes:['新增骑行导航语音包','地图瓦片缓存体积下降 18%'] },
      { tag:'2026.09.17', date:'2026-09-17', pre:false, notes:['修复 GPS 冷启动漂移'] }
    ],
    readme:{ desc:'Organic Maps 使用 OpenStreetMap 数据，完全离线可用，不收集任何位置信息。', install:'Release 按 ABI 分包，universal 包约 120MB。', points:['完全离线矢量地图','骑行与步行导航','支持 GPX 导入导出'] },
    dist:{ ext:'apk', stem:'OrganicMaps', abis:['arm64-v8a','armeabi-v7a','x86_64','universal'], minSdk:24, sdkLabel:'Android 7.0+', signed:'release', mb:64.5 }
  },
  {
    id:'element', name:'Element', owner:'element-hq', monogram:'EL',
    desc:'基于 Matrix 的安全即时通讯，端到端加密。',
    lang:'Kotlin', langColor:'#A97BFF', langs:[91,6,3],
    stars:9800, forks:1900, watchers:230, issues:410,
    version:'1.9.2', code:1920, date:'2026-09-27',
    topics:['matrix','chat','e2ee','compose'],
    tile: tile('#E9EDF7','#2F3E77'),
    history:[
      { tag:'1.9.2', date:'2026-09-27', pre:false, notes:['房间列表性能优化','修复 Android 15 下深色模式对比度'] },
      { tag:'1.9.1', date:'2026-09-05', pre:false, notes:['修复通知渠道重复创建'] }
    ],
    readme:{ desc:'Element 是 Matrix 协议的官方客户端，支持端到端加密与自建 homeserver。', install:'提供 APK 与 App Bundle 两种格式。', points:['E2EE 加密会话','支持自建 homeserver','空间与设备管理'] },
    dist:{ ext:'apk', stem:'Element', abis:['universal'], minSdk:26, sdkLabel:'Android 8.0+', signed:'release', mb:78.2 }
  },
  {
    id:'obtainium', name:'Obtainium', owner:'ImranR98', monogram:'OB',
    desc:'从 GitHub 仓库直接跟踪并安装应用更新，自建应用商店。',
    lang:'Kotlin', langColor:'#A97BFF', langs:[90,7,3],
    stars:3400, forks:290, watchers:95, issues:44,
    version:'0.4.2', code:42, date:'2026-10-03',
    topics:['android','foss','updates','self-hosted'],
    tile: tile('#E6F3EE','#14624F'),
    history:[
      { tag:'0.4.2', date:'2026-10-03', pre:false, notes:['新增按语言筛选安装包','修复 Android 13 以下的手势冲突'] },
      { tag:'0.4.1', date:'2026-09-20', pre:false, notes:['更新检测支持 ETag'] }
    ],
    readme:{ desc:'Obtainium 让你把 GitHub 仓库当成一个应用源，应用有新 release 就能直接推送安装。', install:'Release 提供 universal APK。', points:['按架构与语言自动选择安装包','支持 F-Droid 与 GitLab 源','完全本地，无账号'] },
    dist:{ ext:'apk', stem:'Obtainium', abis:['universal'], minSdk:24, sdkLabel:'Android 7.0+', signed:'release', mb:5.4 }
  },
  {
    id:'fdroid', name:'F-Droid Client', owner:'f-droid', monogram:'FD',
    desc:'官方 F-Droid 仓库客户端，离线浏览数百个自由软件。',
    lang:'Java', langColor:'#B07219', langs:[74,16,6,4],
    stars:4100, forks:720, watchers:120, issues:60,
    version:'1.1.7', code:1170, date:'2026-09-12',
    topics:['f-droid','foss','store'],
    tile: tile('#F1EDE4','#6B5A2E'),
    history:[ { tag:'1.1.7', date:'2026-09-12', pre:false, notes:['支持 Android 15 的分区存储','索引下载支持断点续传'] } ],
    readme:{ desc:'F-Droid 客户端用于浏览与安装自由开源软件，仓库索引签名校验默认开启。', install:'提供 universal 包。', points:['仓库索引签名校验','内置 Tor 支持','可添加第三方仓库'] },
    dist:{ ext:'apk', stem:'F-Droid', abis:['universal'], minSdk:23, sdkLabel:'Android 6.0+', signed:'release', mb:8.9 }
  },
  {
    id:'duckduckgo', name:'DuckDuckGo Browser', owner:'duckduckgo', monogram:'DD',
    desc:'默认拦截跨站追踪的隐私浏览器。',
    lang:'Kotlin', langColor:'#A97BFF', langs:[88,8,4],
    stars:8900, forks:1300, watchers:150, issues:280,
    version:'5.203.0', code:52030, date:'2026-09-08',
    topics:['browser','privacy','search'],
    tile: tile('#FBEEE5','#8A4B1E'),
    history:[ { tag:'5.203.0', date:'2026-09-08', pre:false, notes:['同步功能协议升级','修复深色模式下的跟踪拦截提示'] } ],
    readme:{ desc:'DuckDuckGo 浏览器内置追踪拦截与私有搜索，默认把「不要跟踪我」当作产品底线。', points:['跨站追踪拦截','私有搜索与本地纠错','同步端到端加密'] },
    dist:{ ext:'aab', stem:'DuckDuckGo-Browser', abis:['universal'], minSdk:24, sdkLabel:'Android 7.0+', signed:'release', mb:32.6 }
  },
  {
    id:'kdeconnect', name:'KDE Connect', owner:'KDE', monogram:'KC',
    desc:'手机与桌面之间的剪贴板、文件与通知桥接。',
    lang:'Kotlin', langColor:'#A97BFF', langs:[76,14,6,4],
    stars:4200, forks:530, watchers:110, issues:70,
    version:'1.34.5', code:1345, date:'2026-08-30',
    topics:['android','desktop','kde'],
    tile: tile('#EAF0F8','#2A4B7C'),
    history:[ { tag:'1.34.5', date:'2026-08-30', pre:false, notes:['新增电池电量显示','修复 Android 15 的通知权限'] } ],
    readme:{ desc:'KDE Connect 让桌面与手机之间互传文件、剪贴板与通知，开源且免费。', points:['局域网内零配置发现','共享剪贴板与文件','远程输入与控制'] },
    dist:{ ext:'apk', stem:'kdeconnect-android', abis:['arm64-v8a','armeabi-v7a','universal'], minSdk:24, sdkLabel:'Android 7.0+', signed:'release', mb:14.3 }
  },
  {
    id:'zed', name:'Zed', owner:'zed-industries', monogram:'ZD',
    desc:'高性能协作代码编辑器，GPU 渲染，原生多平台。',
    lang:'Rust', langColor:'#DEA584', langs:[86,9,5],
    stars:56400, forks:4100, watchers:720, issues:980,
    version:'0.168.2', code:1682, date:'2026-10-04',
    topics:['editor','rust','ai','collab'],
    tile: tile('#EBEDF2','#2B3040'),
    history:[
      { tag:'0.168.2', date:'2026-10-04', pre:false, notes:['Agent 面板支持多会话并行','大文件滚动帧率提升'] },
      { tag:'0.168.0', date:'2026-09-25', pre:false, notes:['Linux 下 Wayland 渲染路径重写'] }
    ],
    readme:{ desc:'Zed 是一款用 Rust 编写的代码编辑器，主打速度与内建的 AI Agent 协作能力。', points:['GPU 渲染与预测输入','内建 Agent 面板','多人实时协作光标'] },
    dist:{ ext:'tar.gz', stem:'zed-linux-x86_64', abis:['x86_64'], minSdk:0, sdkLabel:'Linux x86_64', signed:'unknown', mb:132.0, desktop:true }
  }
];

/* 最近 Agent —— 开源 AI Agent 与 CLI 工具 */
const AGENTS = [
  { id:'opencode', name:'opencode', owner:'sst', monogram:'oc', desc:'终端里的开源编码 Agent，可换模型、可自建部署。', lang:'TypeScript', langColor:'#3178C6', stars:24800, version:'0.9.14', date:'2026-10-04', install:'npm i -g opencode-ai', topics:['ai','agent','cli'], tile: tile('#E9F1F8','#1F5A8F') },
  { id:'gemini-cli', name:'gemini-cli', owner:'google-gemini', monogram:'gc', desc:'Google 的开源终端 Agent，直接跑在 Node 里。', lang:'TypeScript', langColor:'#3178C6', stars:18900, version:'0.6.1', date:'2026-10-03', install:'npm i -g @google/gemini-cli', topics:['ai','agent','cli'], tile: tile('#E8F0FB','#2050A0') },
  { id:'claude-code', name:'claude-code', owner:'anthropics', monogram:'cc', desc:'Anthropic 的编码 Agent，终端优先，支持脚本化调用。', lang:'TypeScript', langColor:'#3178C6', stars:22400, version:'1.0.44', date:'2026-10-02', install:'npm i -g @anthropic-ai/claude-code', topics:['ai','agent','cli'], tile: tile('#F5EDE7','#8A4B22') },
  { id:'crush', name:'crush', owner:'charmbracelet', monogram:'cr', desc:'Charm 出的 Agentic 编码工具，多模型路由。', lang:'Go', langColor:'#00ADD8', stars:14200, version:'0.9.3', date:'2026-09-30', install:'brew install charmbracelet/tap/crush', topics:['ai','agent','tui'], tile: tile('#E4F4F7','#0A6C86') },
  { id:'aider', name:'aider', owner:'Aider-AI', monogram:'ai', desc:'命令行结对编程工具，直接在本地仓库里改代码。', lang:'Python', langColor:'#3572A5', stars:26100, version:'0.86.1', date:'2026-09-28', install:'python -m pip install aider-install && aider-install', topics:['ai','agent','python'], tile: tile('#E8EFF6','#2A4F76') },
  { id:'goose', name:'goose', owner:'block', monogram:'gs', desc:'Block 开源的本地 Agent，能读写文件与跑命令。', lang:'Rust', langColor:'#DEA584', stars:16700, version:'1.9.0', date:'2026-09-26', install:'curl -fsSL https://github.com/block/goose/install.sh | bash', topics:['ai','agent','rust'], tile: tile('#F2ECE8','#6E4A32') },
  { id:'openhands', name:'OpenHands', owner:'All-Hands-AI', monogram:'oh', desc:'开源软件开发 Agent，带沙箱浏览器环境。', lang:'Python', langColor:'#3572A5', stars:62800, version:'0.42.0', date:'2026-09-22', install:'pip install openhands-ai', topics:['ai','agent','sandbox'], tile: tile('#EDEAF7','#4B3C8F') },
  { id:'codex', name:'codex', owner:'openai', monogram:'cx', desc:'OpenAI 的编码 Agent CLI，支持沙箱与审批模式。', lang:'Rust', langColor:'#DEA584', stars:41200, version:'0.31.0', date:'2026-10-01', install:'npm i -g @openai/codex', topics:['ai','agent','cli'], tile: tile('#EAECEF','#1F2429') }
];

/* 用户 / 组织，用于搜索结果 */
const PEOPLE = [
  { type:'org',  name:'JetBrains',      handle:'JetBrains',       bio:'做 IDE 的那群人，Kotlin 老家。', loc:'JetBrains HQ', followers:84000, following:12, tile: tile('#FFF1E6','#B0651C') },
  { type:'org',  name:'Termux',         handle:'termux',           bio:'Android 上的终端环境。', loc:'Worldwide', followers:215000, following:0, tile: tile('#EAEEEE','#2B4B4B') },
  { type:'user', name:'Mia Chen',       handle:'miaow',           bio:'前端 / 动效，喜欢做小工具。', loc:'杭州', followers:1240, following:318, hireable:true, tile: tile('#F6EAF6','#7C3E8C') },
  { type:'user', name:'Ivan Petrov',    handle:'ivanpetrov',      bio:'Android 逆向与抓包，偶尔写点工具。', loc:'Belgrade', followers:8700, following:96, tile: tile('#EAF0FA','#2F4E86') },
  { type:'user', name:'Nora Ito',       handle:'nora',            bio:'独立开发者，做过三个开源 App。', loc:'Tokyo', followers:5600, following:210, hireable:true, tile: tile('#E9F4EE','#1D6B52') },
  { type:'org',  name:'KDE',            handle:'KDE',             bio:'自由桌面社区。', loc:'Germany', followers:32000, following:24, tile: tile('#EAF0F8','#2A4B7C') }
];

/* ── 派生：release 与产物 ──────────────────────────────────────── */

const ago = (dateStr) => {
  const d = new Date(dateStr + 'T00:00:00');
  const days = Math.round((TODAY - d) / 86400000);
  if (days <= 0) return '今天';
  if (days === 1) return '昨天';
  if (days < 7) return days + ' 天前';
  if (days < 30) return Math.round(days / 7) + ' 周前';
  return Math.round(days / 30) + ' 个月前';
};

const fmtSize = (mb) => mb >= 100 ? mb.toFixed(0) + ' MB' : mb.toFixed(1) + ' MB';

const fmtCount = (n) => n >= 10000 ? (n / 10000).toFixed(1) + '万' : n >= 1000 ? (n / 1000).toFixed(1) + 'k' : String(n);

/* 单个产物与本机的比对结果 —— 对应产品描述 Step 3 */
function fitAsset(asset, repo) {
  if (asset.kind === 'checksum') return { state:'meta', label:'校验文件' };
  if (repo.dist.desktop || asset.kind === 'archive') {
    return { state:'mismatch', label:'平台不匹配',
      why:`这是 ${repo.dist.sdkLabel} 的安装包，本机是 ${DEVICE.name}（${DEVICE.sdkLabel}），装不上。` };
  }
  if (asset.abi === DEVICE.abi) {
    const tag = repo.dist.signed === 'ci-selfsigned' ? 'CI 自签名' : 'release 签名';
    return { state:'match', label:`完全匹配 · ${asset.abi}`, why:'', extra:tag };
  }
  if (asset.abi === 'universal') {
    return { state:'degrade', label:'可降级 · universal',
      why:`能装，但没有为你这个架构做拆分：多占约 ${(repo.dist.mb * 0.42).toFixed(0)} MB 体积。` };
  }
  return { state:'mismatch', label:'架构不匹配',
    why:`你的设备是 ${DEVICE.abi}，这个包只提供 ${asset.abi}。` };
}

/* 生成一个 release 的完整产物列表 */
function buildAssets(repo, version) {
  const d = repo.dist;
  const out = [];
  if (d.desktop) {
    out.push({ name:`${d.stem}-${version}.${d.ext}`, kind:'archive', abi:d.abis[0], mb:d.mb, sha:'—' });
    out.push({ name:`${d.stem}-${version}.sha256sums`, kind:'checksum', abi:null, mb:0.001, sha:'—' });
    return out.map(a => Object.assign(a, fitAsset(a, repo)));
  }
  d.abis.forEach((abi, i) => {
    const mb = abi === 'universal' ? d.mb : d.mb * (abi === 'arm64-v8a' ? 1 : 0.72) + i * 0.4;
    const raw = {
      name:`${d.stem}-${version}${abi === 'universal' ? '' : '-' + abi}.${d.ext}`,
      kind:d.ext.toUpperCase(), abi, mb:Number(mb.toFixed(1)),
      minSdk:d.minSdk, sdkLabel:d.sdkLabel,
      sha:(d.stem + version + abi).split('').reduce((a, c) => (a * 31 + c.charCodeAt(0)) >>> 0, 7).toString(16).padStart(8,'0').slice(0,6) +
          (d.stem.length * 977 % 65536).toString(16).padStart(4,'0')
    };
    out.push(Object.assign(raw, fitAsset(raw, repo)));
  });
  out.push({ name:'sha256sums.txt', kind:'checksum', abi:null, mb:0.004, sha:'—', label:'校验文件' });
  return out;
}

/* 仓库 + 本机状态 → 抽屉所需的完整模型 */
function hydrate(repo) {
  const assets = buildAssets(repo, repo.version);
  const match = assets.find(a => a.state === 'match');
  const degrade = assets.find(a => a.state === 'degrade');
  const inst = DEVICE.installed.find(i => i.repoId === repo.id);
  let device = null;
  if (inst) {
    if (inst.signing === 'diff') device = { tone:'bad', title:'签名不一致，必须先卸载', desc:`本机装的是 ${inst.version}，但它由 Google Play 签名；这个 Release 的安装包用 CI 自签名证书。Android 会拒绝覆盖安装，强装会丢数据。` };
    else if (inst.code < repo.code) device = { tone:'', title:`可升级 ${inst.version} → ${repo.version}`, desc:'本机版本低于最新 Release，签名一致，可直接覆盖安装。' };
    else device = { tone:'ok', title:`已是最新 ${inst.version}`, desc:'本机版本与最新 Release 一致，不需要操作。' };
  }
  return {
    ...repo, assets, device,
    best: match || degrade || null,
    verdict: match ? 'ok' : degrade ? 'warn' : assets.some(a => a.state === 'meta') ? 'unknown' : 'bad',
    releases: repo.history
  };
}

const VIEW = REPOS.map(hydrate);
const byId = (id) => VIEW.find(r => r.id === id);
const installedOf = (id) => DEVICE.installed.find(i => i.repoId === id);
