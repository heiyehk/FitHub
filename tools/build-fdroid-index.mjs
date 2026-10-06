#!/usr/bin/env node
/**
 * 生成随包分发的「包名 → GitHub 仓库」映射表。
 *
 * 数据源是 F-Droid 官方索引 https://f-droid.org/repo/index-v2.json（约 57 MB）。
 * 它是业界公认的「packageName → 源码仓库」权威映射，FitHub 按包名反查仓库时
 * 先查这份 —— 命中就是精确答案，**不消耗任何 GitHub 配额**。
 *
 * 为什么值得为它单独下 57 MB：
 * 反查的另一条路是「拿包名去 GitHub 搜，再去候选仓库的 build.gradle 里验
 * applicationId」（Obtainium 的做法）。那条路准，但 8 个候选 × 2 个路径就是
 * 16 次请求，未登录只有 60 次/小时 —— 一次反查吃掉 27% 的配额。
 * 先查这份表，绝大多数 F-Droid 上的应用直接命中，验证只跑在剩下的少数候选上。
 *
 * 为什么只留 GitHub：
 * FitHub 只跟 GitHub 打交道。索引里 GitLab / Codeberg 上的条目留着也没用，
 * 留着还会在结果里给出「定位到了，但它不是 GitHub 仓库」这种半截答案。
 *
 * 用法：
 *   node tools/build-fdroid-index.mjs                     # 自己下
 *   node tools/build-fdroid-index.mjs <本地 index-v2.json>  # 用已经下好的文件
 * 产出：app/src/main/assets/fdroid-repo-index.json
 *
 * f-droid.org 是个慢主机，Node 内置 fetch 的 10 秒连接超时经常不够（实测
 * ConnectTimeoutError），所以默认下载带重试；实在不行就自己先下好再传路径进来。
 *
 * 这份表会过期 —— 收录 / 搬家不会通知我们。重新跑一次本脚本即可更新，
 * 所以它在仓库里而不是构建期下载：构建期下载会让每次编译都依赖 f-droid.org 可达。
 */

import { writeFileSync, mkdirSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const SOURCE = 'https://f-droid.org/repo/index-v2.json'
const here = dirname(fileURLToPath(import.meta.url))
const OUT = resolve(here, '../app/src/main/assets/fdroid-repo-index.json')

/**
 * 下载，带重试。
 *
 * 重试的是**连接**超时：f-droid.org 首次 TLS 握手经常超过 Node 默认的 10 秒，
 * 而一旦连上，57 MB 的传输本身很快。3 次之后放弃并提示改用本地文件。
 */
async function download(url, attempts = 3) {
  for (let i = 1; i <= attempts; i++) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(180_000) })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      return await res.text()
    } catch (e) {
      if (i === attempts) throw e
      const wait = i * 5
      console.log(`  第 ${i} 次失败（${e.message}），${wait}s 后重试`)
      await new Promise((r) => setTimeout(r, wait * 1000))
    }
  }
}

/**
 * 从 sourceCode 里取 `owner/repo`。
 *
 * 只认 github.com，且必须是两条路径段 —— `github.com/owner/repo.git`、带 `/tree/`
 * 的深层链接、`github.com/owner`（没有仓库）都要能处理掉。
 * 返回 null 表示这条用不上，调用方跳过。
 */
function toGithubRepo(url) {
  if (typeof url !== 'string' || !url) return null
  let m
  try {
    m = new URL(url)
  } catch {
    return null
  }
  if (m.hostname.toLowerCase() !== 'github.com') return null
  const parts = m.pathname.split('/').filter(Boolean)
  if (parts.length < 2) return null
  const owner = parts[0]
  const repo = parts[1].replace(/\.git$/i, '')
  if (!owner || !repo) return null
  return `${owner}/${repo}`
}

const localPath = process.argv[2]
let raw
if (localPath) {
  console.log(`读取本地文件 ${localPath} …`)
  raw = readFileSync(localPath, 'utf8')
} else {
  console.log(`下载 ${SOURCE} …`)
  raw = await download(SOURCE)
}
console.log(`原始大小：${(raw.length / 1024 / 1024).toFixed(1)} MB`)

const index = JSON.parse(raw)

/**
 * 取出「每包一条」的记录。
 *
 * F-Droid 的 index-v2 有两种形状，而且换过一次：
 * - 旧：`packages` 是**数组**，每项自带 `packageName` 与 `sourceCode`
 * - 新（实测 2026-10）：`packages` 是**以包名为键的对象**，值是
 *   `{ metadata: { sourceCode, … }, versions: [...] }`，`sourceCode` 埋在 metadata 里
 *
 * 两种都认。不是为了兼容「新旧版本客户端」，而是为了兼容**F-Droid 自己改格式** ——
 * 照着其中一种写死，对方一换格式这个脚本就会静默产出 0 条，看起来像「索引空了」。
 */
function records(index) {
  const p = index.packages
  if (Array.isArray(p)) return p
  if (p && typeof p === 'object') {
    return Object.entries(p).map(([packageName, v]) => ({
      packageName,
      sourceCode: v?.metadata?.sourceCode,
    }))
  }
  throw new Error('index-v2.json 的 packages 既不是数组也不是对象，格式可能又变了')
}

const packages = records(index)
console.log(`包总数：${packages.length}`)

const map = {}
for (const p of packages) {
  const name = p.packageName
  if (typeof name !== 'string' || !name) continue
  const repo = toGithubRepo(p.sourceCode)
  if (!repo) continue
  // 同名包只留第一条：F-Droid 的索引里 packageName 本身唯一，重复只可能是脏数据。
  // 真出现了就保留先到的，不覆盖 —— 后写入的那条没有任何理由更可信。
  if (!(name in map)) map[name] = repo
}

const entries = Object.entries(map).sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
const out = Object.fromEntries(entries)

mkdirSync(dirname(OUT), { recursive: true })
const json = JSON.stringify(out)
writeFileSync(OUT, json, 'utf8')

console.log(`可用的 GitHub 映射：${entries.length} 条`)
console.log(`产出：${OUT}`)
console.log(`大小：${(json.length / 1024).toFixed(0)} KB`)
console.log(`抽样：`)
for (const [k, v] of entries.slice(0, 5)) console.log(`  ${k} -> ${v}`)