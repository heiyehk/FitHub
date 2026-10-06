#!/usr/bin/env node
// 解析 FitHub 的打包产物（APK）并交到本地，必要时再用 HTTP 转发出去。
//
// 为什么值得写成脚本而不是每次敲 curl：产物来源有三级，优先级和"选哪个文件"的规则
// 是固定的，但 ref / variant / 文件名每次都变。
//   1. GitHub Release 资产   —— App 真正消费的东西（需先打 v* tag）
//   2. CI workflow artifact —— main 分支跑绿就有，但只有 tag 推送才会变成 Release
//   3. 本机 gradle 输出      —— 没有远端产物时的兜底
// 手工 curl 最容易出的错是拿到 -unsigned 或 -debug 还在装，以及把三天前的
// 本机 APK 当成刚打好的发出去 —— 下面每一步都会把来源和时间戳打出来。
//
// 用法：
//   node scripts/fetch-release.mjs                    # 解析并落盘到 dist/
//   node scripts/fetch-release.mjs --tag v0.0.1       # 指定 release
//   node scripts/fetch-release.mjs --serve 8080       # 落盘后起本地 HTTP 转发
//   node scripts/fetch-release.mjs --local-only       # 只用本机 gradle 产物
//   node scripts/fetch-release.mjs --json             # 机器可读输出
//
// 退出码：0 成功 / 1 没找到可用产物 / 2 参数或 IO 出错

import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import { createReadStream, createWriteStream } from 'node:fs';
import { copyFile, mkdir, mkdtemp, readFile, readdir, rm, stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import { networkInterfaces } from 'node:os';
import { basename, extname, join, resolve, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const API = 'https://api.github.com';

// ---------------------------------------------------------------- 参数

const USAGE = `用法: node scripts/fetch-release.mjs [选项]

  --tag <tag>        指定 release tag（默认取最新的非 draft）
  --repo <o/n>       仓库，默认 heiyehk/FitHub
  --variant <name>   release | debug，默认 release
  --out <dir>        落盘目录，默认 dist/
  --token <t>        GitHub token，默认读 $GITHUB_TOKEN / $GH_TOKEN
  --serve [port]     落盘后起本地 HTTP 转发（默认 8080），支持 Range
  --local-only       跳过远端，只用本机 gradle 产物
  --json             结果以 JSON 打到 stdout
  -h, --help         显示本帮助`;

function parseArgs(argv) {
  const o = {
    tag: null, repo: 'heiyehk/FitHub', variant: 'release',
    out: join(REPO_ROOT, 'dist'), token: null, serve: false,
    port: 8080, localOnly: false, json: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const need = (name) => {
      const v = argv[++i];
      if (v === undefined) fail(`${name} 缺参数`);
      return v;
    };
    switch (a) {
      case '--tag': o.tag = need('--tag'); break;
      case '--repo': o.repo = need('--repo'); break;
      case '--variant': o.variant = need('--variant'); break;
      case '--out': o.out = resolve(need('--out')); break;
      case '--token': o.token = need('--token'); break;
      case '--local-only': o.localOnly = true; break;
      case '--json': o.json = true; break;
      case '--serve':
        o.serve = true;
        // --serve 后面如果跟的是端口就吃掉它，否则是 flag（`--serve` 后跟别的选项）
        if (argv[i + 1] !== undefined && /^\d+$/.test(argv[i + 1])) o.port = Number(argv[++i]);
        break;
      case '-h': case '--help': console.log(USAGE); process.exit(0); break;
      default: fail(`未知参数: ${a}\n\n${USAGE}`);
    }
  }
  if (o.variant !== 'release' && o.variant !== 'debug') {
    fail(`--variant 只能是 release 或 debug，收到: ${o.variant}`);
  }
  o.token ??= process.env.GITHUB_TOKEN || process.env.GH_TOKEN || null;
  return o;
}

function fail(msg) {
  console.error(`错误: ${msg}`);
  process.exit(2);
}

// ---------------------------------------------------------------- 远端

async function api(path, { token, accept = 'application/vnd.github+json' } = {}) {
  const headers = { 'User-Agent': 'fithub-fetch-release', accept };
  if (token) headers.authorization = `Bearer ${token}`;
  const res = await fetch(`${API}${path}`, { headers, redirect: 'manual' });
  if (!res.ok) {
    const e = new Error(`HTTP ${res.status} ${res.statusText} — ${path}`);
    e.status = res.status;
    throw e;
  }
  return res;
}

async function fetchFromRelease(o) {
  const releases = o.tag
    ? [await (await api(`/repos/${o.repo}/releases/tags/${encodeURIComponent(o.tag)}`, { token: o.token })).json()]
    : await (await api(`/repos/${o.repo}/releases?per_page=30`, { token: o.token })).json();

  // draft 是编辑到一半的发布，App 不该看到；预发布按时间倒序参与候选。
  const usable = releases.filter((r) => !r.draft);
  if (!usable.length) {
    return { ok: false, reason: o.tag ? `没有 tag ${o.tag} 对应的 release` : '仓库里一个 release 都没有' };
  }

  const pick = (rel) => pickAsset(rel.assets || [], o.variant);
  // 选的时候先看有没有"签名 release"，没有才退而求其次给 unsigned / debug。
  // 直接取 assets[0] 是错的：CI 同时传了 debug / release / unsigned 三种。
  const release = usable.find((r) => pick(r)?.preferred);
  const chosen = release ? { rel: release, asset: pick(release) } : (() => {
    const rel = usable.find((r) => pick(r));
    return rel ? { rel, asset: pick(rel) } : null;
  })();
  if (!chosen) return { ok: false, reason: 'release 里没有 APK 资产' };

  const { rel, asset } = chosen;
  const signed = !asset.name.includes('unsigned');
  if (!signed && o.variant === 'release') {
    console.warn(`! ${rel.tag_name} 上只有 -unsigned APK（CI 没配 KEYSTORE_BASE64），无法覆盖安装`);
  }

  const tmp = join(await mkdtemp(join(tmpdir(), 'fithub-rel-')), 'asset.apk');
  // 资产下载会 302 到 objects.githubusercontent.com。Authorization 头不能跟着跳过去 ——
  // 那是带签名的 S3 临时 URL，转发过去只会拿到 403，所以这里手动跟并且跨源时剥掉凭据。
  const url = o.token ? asset.url : asset.browser_download_url;
  await download(url, tmp, o.token);

  return {
    ok: true, file: tmp, variant: o.variant,
    origin: `github-release:${rel.tag_name}`,
    label: `${asset.name} (${rel.prerelease ? 'prerelease' : 'latest'})`,
    tag: rel.tag_name, outName: asset.name,
  };
}

// 在一个 release 的资产里挑最合适的那个。
function pickAsset(assets, variant) {
  const apks = assets.filter((a) => a.name.toLowerCase().endsWith('.apk'));
  if (!apks.length) return null;
  const rank = (a) => {
    const n = a.name;
    const isVar = n.includes(variant);
    const unsigned = n.includes('unsigned');
    // 分数越小越优先。签名版 release > unsigned release > debug
    return (isVar ? 0 : 10) + (unsigned ? 1 : 0);
  };
  const best = [...apks].sort((x, y) => rank(x) - rank(y))[0];
  return { ...best, preferred: rank(best) === 0 };
}

async function fetchFromArtifact(o) {
  if (!o.token) {
    // 没 token 连 artifact 列表都读不到，提前说清楚，别让调用方以为是网络问题。
    return { ok: false, reason: '读 CI artifact 需要 GitHub token（$GITHUB_TOKEN / --token）' };
  }
  // --tag 时按 head_branch 自己在客户端筛：tag 推送的 run 也归在 main 的
  // ?branch=main 结果里，用 branch 参数过滤会拿到别的 ref 编译出来的包。
  const runsUrl = `/repos/${o.repo}/actions/workflows/android.yml/runs?per_page=30`;
  const runs = await (await api(runsUrl, { token: o.token })).json();
  const scoped = (runs.workflow_runs || [])
    .filter((r) => r.status === 'completed')
    .filter((r) => !o.tag || r.head_branch === o.tag);
  const good = scoped.find((r) => r.conclusion === 'success');
  if (!good) {
    const last = scoped[0];
    return {
      ok: false,
      reason: last
        ? `${o.tag || 'main'} 上最近一次 CI（${last.head_sha.slice(0, 7)}）结果是 ${last.conclusion}，没有 artifact`
        : `${o.tag || 'main'} 上还没有跑完的 CI`,
    };
  }

  const { artifacts } = await (await api(`/repos/${o.repo}/actions/runs/${good.id}/artifacts`, { token: o.token })).json();
  const art = (artifacts || []).find((a) => a.name === 'apks' && !a.expired);
  if (!art) return { ok: false, reason: `run ${good.id} 里没有可用的 apks artifact（可能已过期）` };

  const work = await mkdtemp(join(tmpdir(), 'fithub-art-'));
  const zip = join(work, 'apks.zip');
  await download(`${API}/repos/${o.repo}/actions/artifacts/${art.id}/zip`, zip, o.token);
  // artifact 是个 zip，Node 没有内置解压。CI 产物只在 Windows 上跑得动（仓库脚本本身
  // 就是 PowerShell），借系统自带 Expand-Archive，不引第三方依赖。
  const out = join(work, 'unzipped');
  await expandZip(zip, out);
  const apks = (await readdir(out, { recursive: true, withFileTypes: true }))
    .filter((d) => d.isFile() && d.name.toLowerCase().endsWith('.apk'))
    .map((d) => join(d.parentPath ?? d.path, d.name));
  if (!apks.length) return { ok: false, reason: 'artifact 解压后没有 APK' };

  const name = pickAsset(apks.map((p) => ({ name: basename(p) })), o.variant)?.name;
  const file = apks.find((p) => basename(p) === name) ?? apks[0];
  return {
    ok: true, file, variant: o.variant,
    origin: `ci-artifact:run${good.id}`,
    label: `run ${good.id} (${good.head_sha.slice(0, 7)}) / ${basename(file)}`,
    tag: null, outName: basename(file),
  };
}

function expandZip(zip, dest) {
  return new Promise((resolveP, rejectP) => {
    const exe = process.platform === 'win32' ? 'powershell.exe' : 'unzip';
    const args = process.platform === 'win32'
      // $args 走的是 PowerShell 的参数数组，不用拼字符串 —— 临时目录路径里有空格。
      ? ['-NoProfile', '-NonInteractive', '-Command',
         'Expand-Archive -LiteralPath $args[0] -DestinationPath $args[1] -Force', zip, dest]
      : ['-o', zip, '-d', dest];
    const p = spawn(exe, args, { stdio: ['ignore', 'ignore', 'pipe'] });
    let err = '';
    p.stderr.on('data', (d) => { err += d; });
    p.on('error', rejectP);
    p.on('close', (code) => (code === 0 ? resolveP() : rejectP(new Error(`解压失败 (${code}): ${err.trim()}`))));
  });
}

// ---------------------------------------------------------------- 本机

async function pickLocalApk(o) {
  const dir = join(REPO_ROOT, 'app', 'build', 'outputs', 'apk', o.variant);
  let entries;
  try {
    entries = await readdir(dir);
  } catch {
    return { ok: false, reason: `没有 ${dir}（先跑 ./gradlew assemble${o.variant === 'release' ? 'Release' : 'Debug'}）` };
  }
  const apks = entries.filter((f) => f.toLowerCase().endsWith('.apk'));
  if (!apks.length) return { ok: false, reason: `${dir} 里没有 APK` };

  // 多个 APK 时取最新的：上次 assemble 换了名字但没清目录时，
  // 按名字字母序挑会拿到旧的。
  const withTime = await Promise.all(apks.map(async (f) => ({ f, t: (await stat(join(dir, f))).mtimeMs })));
  withTime.sort((a, b) => b.t - a.t);
  const chosen = withTime[0].f;

  let version = null;
  try {
    const meta = JSON.parse(await readFile(join(dir, 'output-metadata.json'), 'utf8'));
    version = meta?.elements?.[0]?.versionName ?? null;
  } catch { /* 元数据缺失不影响拿 APK，只是文件名里少个版本号 */ }

  return {
    ok: true, file: join(dir, chosen), variant: o.variant,
    origin: 'local-gradle',
    label: `${dir}\\${chosen}`,
    tag: null,
    outName: `FitHub-${version || 'local'}-${chosen.replace(/^app-/, '')}`,
    builtAt: new Date(withTime[0].t).toISOString(),
  };
}

// ---------------------------------------------------------------- 下载

async function download(url, dest, token) {
  const tmp = `${dest}.part`;
  let urlNow = url;
  // 手动跟跳转：跨源时 Authorization 必须剥掉（见 fetchFromRelease 的注释）。
  for (let hop = 0; hop < 6; hop++) {
    const headers = { 'User-Agent': 'fithub-fetch-release' };
    if (token && urlNow.startsWith(API)) headers.authorization = `Bearer ${token}`;
    const res = await fetch(urlNow, { headers, redirect: 'manual' });
    if ([301, 302, 303, 307, 308].includes(res.status)) {
      const next = res.headers.get('location');
      if (!next) throw new Error(`${res.status} 但没有 Location`);
      urlNow = new URL(next, urlNow).toString();
      continue;
    }
    if (!res.ok) throw new Error(`下载失败 HTTP ${res.status} ${res.statusText} — ${urlNow}`);

    // 先写 .part 再改名：中断时不会留下一个"看起来存在"的半个 APK。
    // 这正是 App 侧断点续传依赖的语义，脚本侧不能自己破坏它。
    await new Promise((resolveP, rejectP) => {
      const out = createWriteStream(tmp);
      const body = res.body.pipe(out);
      body.on('error', rejectP);
      out.on('error', rejectP);
      out.on('finish', () => resolveP());
    });
    const { rename } = await import('node:fs/promises');
    await rename(tmp, dest);
    return;
  }
  throw new Error('重定向次数过多');
}

async function sha256(file) {
  const h = createHash('sha256');
  for await (const chunk of createReadStream(file)) h.update(chunk);
  return h.digest('hex');
}

// ---------------------------------------------------------------- 转发

// Range 不是可选的：App 的下载是 .part + Range 续传，不支持 Range 的话
// 第一次连接断开就会整个重下，而且服务端不返回 206 时客户端行为未定义。
function startServer(file, port) {
  const server = createServer((req, res) => {
    if (req.method !== 'GET' && req.method !== 'HEAD') {
      res.writeHead(405, { allow: 'GET, HEAD' }).end();
      return;
    }
    stat(file).then((st) => {
      const total = st.size;
      res.setHeader('Accept-Ranges', 'bytes');
      res.setHeader('Content-Type', 'application/vnd.android.package-archive');
      res.setHeader('Content-Disposition', `attachment; filename="${basename(file)}"`);

      const m = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range || '');
      if (m) {
        let start = m[1] === '' ? null : Number(m[1]);
        let end = m[2] === '' ? null : Number(m[2]);
        if (start === null) { start = Math.max(0, total - (end ?? 0)); end = total - 1; } // bytes=-N 后缀请求
        if (end === null || end >= total) end = total - 1;
        if (!Number.isFinite(start) || start > end || start >= total) {
          res.writeHead(416, { 'Content-Range': `bytes */${total}` }).end();
          return;
        }
        res.writeHead(206, {
          'Content-Range': `bytes ${start}-${end}/${total}`,
          'Content-Length': end - start + 1,
        });
        if (req.method === 'HEAD') { res.end(); return; }
        createReadStream(file, { start, end }).pipe(res);
        return;
      }
      res.writeHead(200, { 'Content-Length': total });
      if (req.method === 'HEAD') { res.end(); return; }
      createReadStream(file).pipe(res);
    }).catch((e) => { res.writeHead(500).end(String(e.message)); });
  });

  return new Promise((resolveP, rejectP) => {
    server.on('error', rejectP);
    server.listen(port, '0.0.0.0', () => {
      const lan = Object.values(networkInterfaces()).flat()
        .filter((i) => i && i.family === 'IPv4' && !i.internal).map((i) => i.address);
      resolveP({ port, addrs: lan });
    });
  });
}

// ---------------------------------------------------------------- main

const o = parseArgs(process.argv.slice(2));
const log = (...a) => { if (!o.json) console.log(...a); };

let found = null;
const tried = [];
// 显式 --tag 时不回退到本机产物：调用方要的是那个 tag 编译出来的东西，
// 悄悄递一个本机 build 过去，正是最难受的假成功 —— 文件能装，但内容不是他要的版本。
const chain = o.localOnly
  ? [pickLocalApk]
  : o.tag
    ? [fetchFromRelease, fetchFromArtifact]
    : [fetchFromRelease, fetchFromArtifact, pickLocalApk];

for (const source of chain) {
  try {
    const r = await source(o);
    if (r.ok) { found = r; break; }
    tried.push(r.reason);
    if (!o.json) console.warn(`- ${source.name}: ${r.reason}`);
  } catch (e) {
    tried.push(`${source.name} 异常: ${e.message}`);
    if (!o.json) console.warn(`- ${source.name} 异常: ${e.message}`);
  }
}

if (!found) {
  const msg = '没有可用的打包产物。已尝试：\n' + tried.map((t) => `  - ${t}`).join('\n');
  if (o.json) console.log(JSON.stringify({ ok: false, tried }, null, 2));
  else console.error(`\n${msg}`);
  process.exit(1);
}

await mkdir(o.out, { recursive: true });
const dest = join(o.out, found.outName);
if (resolve(found.file) !== resolve(dest)) await copyFile(found.file, dest);

const st = await stat(dest);
const hash = await sha256(dest);
const result = {
  ok: true, file: dest, origin: found.origin, label: found.label,
  bytes: st.size, sha256: hash, builtAt: found.builtAt ?? st.mtime.toISOString(),
};

if (o.json) {
  console.log(JSON.stringify(result, null, 2));
} else {
  log(`来源  ${found.origin}`);
  log(`文件  ${found.label}`);
  log(`落盘  ${dest}`);
  log(`大小  ${st.size.toLocaleString('en-US')} bytes`);
  log(`时间  ${result.builtAt}`);
  log(`SHA   ${hash}`);
}

if (o.serve) {
  const { addrs } = await startServer(dest, o.port);
  const shown = o.json ? dest : (addrs.length ? addrs : ['127.0.0.1']);
  if (!o.json) {
    console.log('\n本地转发已启动（Ctrl+C 停止）:');
    for (const a of shown) console.log(`  http://${a}:${o.port}/${basename(dest)}`);
  }
}
