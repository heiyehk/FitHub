/* ══════════════════════════════════════════════════════════════════
   FitHub · 原型交互
   详情动效三件套：scrim 淡入 / shell 缩小后退 / drawer 右侧滑入 + FLIP 图标
   ══════════════════════════════════════════════════════════════════ */

const $  = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => [...r.querySelectorAll(s)];

const body     = document.body;
const shell    = $('#shell');
const feed     = $('#feed');
const chipsEl  = $('#chips');
const drawer   = $('#drawer');
const dwScroll = $('#dwScroll');
const dwHero   = $('#dwHero');
const dwBody   = $('#dwBody');
const dwFoot   = $('#dwFoot');
const flyer   = $('#flyer');
const palette = $('#palette');
const palList  = $('#palList');
const toastEl  = $('#toast');

const esc = (s) => String(s).replace(/[&<>"]/g, c => ({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;' }[c]));
const tileHtml = (r, cls = 'tile') =>
  `<span class="${cls}" style="--tile-bg:${r.tile.bg};--tile-fg:${r.tile.fg}">${esc(r.monogram)}</span>`;

const VERDICT = {
  ok:      { cls:'is-ok',   title:'完全匹配你的设备', icon:'i-check' },
  warn:    { cls:'is-warn', title:'能装，但不是最优解', icon:'i-alert' },
  bad:     { cls:'is-bad',  title:'没有适配这台设备的安装包', icon:'i-alert' },
  unknown: { cls:'',        title:'无法解析安装包', icon:'i-alert' }
};

const STATE_LABEL = { match:'完全匹配', degrade:'可降级', mismatch:'不匹配', unknown:'无法解析', meta:'校验文件' };

function fitBadge(repo, small = false) {
  const map = {
    ok:      ['badge-ok',  '已适配'],
    warn:    ['badge-warn','可装 · 非最优'],
    bad:     ['badge-bad', '无适配包'],
    unknown: ['badge',     '无法解析']
  };
  const [cls, text] = map[repo.verdict];
  return `<span class="badge ${cls}">${text}</span>`;
}

/* ── 智能发现筛选 ─────────────────────────────────────────────── */
const MODES = [
  { id:'all',    label:'全部',        test:() => true },
  { id:'fit',    label:'适配优先',    test:r => r.verdict === 'ok' || r.verdict === 'warn' },
  { id:'recent', label:'最近更新',    test:() => true },
  { id:'star',   label:'star 最多',   test:() => true },
  { id:'up',     label:'可升级',      test:r => r.device && r.device.title.startsWith('可升级'), count:3 },
  { id:'device', label:'本机已装',    test:r => !!r.device }
];

let mode = 'fit';

function pass(r) { return MODES.find(m => m.id === mode).test(r); }

function ordered(list) {
  const l = [...list];
  if (mode === 'recent') l.sort((a, b) => b.date.localeCompare(a.date));
  else if (mode === 'star') l.sort((a, b) => b.stars - a.stars);
  else l.sort((a, b) => (b.verdict === 'ok') - (a.verdict === 'ok') || b.stars - a.stars);
  return l;
}

function renderChips() {
  chipsEl.innerHTML = MODES.map(m => {
    const n = m.count ?? VIEW.filter(r => m.test(r) && r.id !== 'zed').length;
    return `<button class="chip ${m.id === mode ? 'is-on' : ''}" role="tab" aria-selected="${m.id === mode}" data-mode="${m.id}">
      ${esc(m.label)}<span class="chip-n">${n}</span></button>`;
  }).join('');
}

chipsEl.addEventListener('click', e => {
  const btn = e.target.closest('.chip');
  if (!btn) return;
  mode = btn.dataset.mode;
  renderChips();
  renderFeed();
});

/* ── 首页各段 ─────────────────────────────────────────────────── */
function sectHead(idx, title, note, more = '查看全部') {
  return `<div class="sect-head">
    <span class="sect-idx">${idx}</span>
    <h2 class="sect-title">${title}</h2>
    <p class="sect-note">${note}</p>
    <a class="sect-more" href="#">${more}<svg class="ic"><use href="#i-chevron"/></svg></a>
  </div>`;
}

function cardTrending(r, i) {
  return `<button class="card" data-repo="${r.id}" data-from="trend">
    <div class="card-top">${tileHtml(r, 'tile tile-lg')}<span class="sect-idx" style="font-size:15px">${String(i + 1).padStart(2, '0')}</span></div>
    <div>
      <div class="row-name">${esc(r.name)} ${fitBadge(r)}</div>
      <div class="card-owner">${esc(r.owner)} / ${esc(r.name)}</div>
    </div>
    <p class="card-desc">${esc(r.desc)}</p>
    <div class="card-foot">
      <span class="meta"><span class="lang"><i class="lang-dot" style="--lang:${r.langColor}"></i>${esc(r.lang)}</span></span>
      <span class="meta"><span class="stars"><svg class="ic"><use href="#i-star"/></svg><span class="meta-num">${fmtCount(r.stars)}</span></span>
      <i class="meta-sep"></i><span class="card-ver">${esc(r.version)}</span></span>
    </div>
  </button>`;
}

function rowRecent(r, i) {
  const up = r.device && r.device.title.startsWith('可升级');
  return `<button class="row ${up ? 'is-up' : ''}" style="--i:${i}" data-repo="${r.id}" data-from="recent">
    ${tileHtml(r)}
    <span class="row-main">
      <span class="row-name">${esc(r.name)}<span class="mono">${esc(r.owner)}</span>${up ? '<span class="badge badge-ok">可升级</span>' : ''}</span>
      <span class="row-desc">${esc(r.desc)}</span>
    </span>
    <span class="row-side">
      <dl class="row-stat"><dt>最新版本</dt><dd>${esc(r.version)}</dd></dl>
      <dl class="row-stat"><dt>star</dt><dd>${fmtCount(r.stars)}</dd></dl>
      ${fitBadge(r)}
    </span>
    <svg class="ic row-go"><use href="#i-chevron"/></svg>
  </button>`;
}

function featBig(r) {
  return `<button class="feat feat-a" data-repo="${r.id}" data-from="feat">
    <span class="feat-kicker">本周精选 · 编辑挑选</span>
    <div class="feat-head">${tileHtml(r, 'tile tile-lg')}<div>
      <div class="feat-title">${esc(r.name)}</div>
      <div class="card-owner">${esc(r.owner)} / ${esc(r.name)}</div>
    </div></div>
    <p class="feat-desc">${esc(r.desc)}${r.readme.install ? ' ' + esc(r.readme.install) : ''}</p>
    <div class="langbar">${r.langs.map((p, i) => `<span style="width:${p}%;background:${[r.langColor, '#B9C2BC', '#D8D6D0', '#EDEBE6'][i]}"></span>`).join('')}</div>
    <div class="topics">${r.topics.slice(0, 4).map(t => `<span class="topic">${esc(t)}</span>`).join('')}</div>
    <div class="feat-foot">${fitBadge(r)}<i class="meta-sep"></i><span class="meta"><span class="meta-num">${fmtCount(r.stars)}</span> stars</span>
      <i class="meta-sep"></i><span class="mono" style="font-size:12px">${esc(r.version)}</span>
      <span class="sect-more" style="margin-left:auto">查看适配<svg class="ic"><use href="#i-chevron"/></svg></span></div>
  </button>`;
}

function featSmall(r, kicker, note, badgeHtml) {
  return `<button class="feat" data-repo="${r.id}" data-from="feat">
    <span class="feat-kicker">${esc(kicker)}</span>
    <div class="feat-head">${tileHtml(r, 'tile tile-lg')}<div class="feat-title">${esc(r.name)}</div></div>
    <p class="feat-desc">${esc(note || r.desc)}</p>
    <div class="topics">${r.topics.slice(0, 3).map(t => `<span class="topic">${esc(t)}</span>`).join('')}</div>
    <div class="feat-foot">${badgeHtml || fitBadge(r)}<i class="meta-sep"></i><span class="meta-num" style="font-size:12px">${fmtCount(r.stars)} stars</span></div>
  </button>`;
}

function rowAgent(a, i) {
  return `<button class="row row-agent" style="--i:${i}" data-agent="${a.id}" data-from="agent">
    ${tileHtml(a, 'tile tile-sm tile-tf')}
    <span class="row-main">
      <span class="row-name">${esc(a.name)}<span class="mono">${esc(a.owner)}</span></span>
      <span class="row-desc">${esc(a.desc)}</span>
      <span class="cmd" style="margin-top:9px"><svg class="ic"><use href="#i-package"/></svg>${esc(a.install)}</span>
    </span>
    <span class="row-side">
      <dl class="row-stat"><dt>最新版本</dt><dd>${esc(a.version)}</dd></dl>
      <dl class="row-stat"><dt>star</dt><dd>${fmtCount(a.stars)}</dd></dl>
      <span class="badge badge-ghost">${ago(a.date)}</span>
    </span>
    <svg class="ic row-go"><use href="#i-chevron"/></svg>
  </button>`;
}

function renderFeed() {
  const pool = ordered(VIEW.filter(r => r.id !== 'zed' && pass(r)));
  const trend = pool.slice(0, 8);
  const recent = pool.slice(0, 7);
  const featA = pool.find(r => r.id === 'organicmaps') || pool[0];
  const featB = pool.find(r => r.id === 'aegis') || pool.find(r => r.device) || pool[1];
  const featC = byId('zed');
  const agents = [...AGENTS].sort((a, b) => b.date.localeCompare(a.date)).slice(0, 6);

  feed.innerHTML = `
    <section class="sect" id="sec-trending">
      ${sectHead('01', '热门 <em>Trending</em>', '按 star 排序，近 7 天增长最多的一批')}
      <div class="rail">${trend.length ? trend.map(cardTrending).join('') : emptyBlock()}</div>
    </section>

    <section class="sect" id="sec-recent">
      ${sectHead('02', '最近更新 <em>Updated</em>', '按最新 release 时间排序，只含有可下载产物的仓库')}
      <div class="rows">${recent.length ? recent.map(rowRecent).join('') : emptyBlock()}</div>
    </section>

    <section class="sect" id="sec-featured">
      ${sectHead('03', '精选 <em>Featured</em>', '人工挑选：适配说明完整、签名可追溯')}
      <div class="bento">
        ${featA ? featBig(featA) : ''}
        ${featB ? featSmall(featB, '本机相关',
            featB.device ? `${featB.device.title}。${featB.device.desc}` : featB.desc) : ''}
        ${featC ? featSmall(featC, '无法适配 · 桌面端',
            `Release 只提供 ${featC.dist.sdkLabel} 桌面包，本机是 ${DEVICE.name}（${DEVICE.sdkLabel}），装不上 —— 但依然展示出来，并说明原因。`,
            '<span class="badge badge-bad"><svg class="ic"><use href="#i-alert"/></svg>不匹配</span>') : ''}
      </div>
    </section>

    <section class="sect" id="sec-agents">
      ${sectHead('04', '最新 <em>Agent</em>', '开源 AI Agent 与 CLI 工具，按最近发布排序')}
      <div class="rows">${agents.map(rowAgent).join('')}</div>
    </section>`;
}

function emptyBlock() {
  return `<div class="pal-empty" style="width:100%">当前筛选下没有结果 —— <b>试试「全部」</b>，或者清空语言与安装包条件。</div>`;
}

/* ── 抽屉 ─────────────────────────────────────────────────────── */
let current = null, sourceEl = null, flight = null;

function verdictCard(r) {
  const v = VERDICT[r.verdict];
  const best = r.best;
  const facts = best ? [
    ['架构', best.abi || '—'],
    ['系统要求', r.dist.sdkLabel],
    ['签名', best.extra || (r.dist.signed === 'unknown' ? '未校验' : 'release')],
    ['体积', fmtSize(best.mb)],
    ['sha256', best.sha]
  ] : [];
  const why = {
    ok: `本机是 <b>${DEVICE.abi}</b> / ${DEVICE.sdkLabel}，这个包同时满足架构与 minSdk <b>${r.dist.minSdk}</b>（${r.dist.sdkLabel}），签名为 ${r.dist.signed === 'ci-selfsigned' ? 'CI 自签名' : '官方 release 签名'}，可直接覆盖安装。`,
    warn: best ? `没有 ${DEVICE.abi} 的单独分包，只有 universal 包。能装，但会多占约 ${(r.dist.mb * 0.42).toFixed(0)} MB 体积，且冷启动略慢。` : '',
    bad: `这个 Release 里没有任何适配 ${DEVICE.abi} 的产物，而且本机是 Android，桌面平台包无法安装。`,
    unknown: 'APK 解析失败（可能用了加固或非标准打包）。按产品原则：解析不了就明说，不猜。下方提供原始文件链接。'
  };
  return `<div class="fitcard ${v.cls}" style="--i:0">
    <div class="fitcard-top">
      <span class="badge ${r.verdict === 'ok' ? 'badge-ok' : r.verdict === 'warn' ? 'badge-warn' : r.verdict === 'bad' ? 'badge-bad' : ''}">
        <svg class="ic"><use href="#${v.icon}"/></svg>${v.title}</span>
    </div>
    <p class="fitcard-why">${why[r.verdict] || ''}</p>
    <div class="fitcard-facts">${facts.map(([k, val]) => `<span class="fact">${k} ${esc(val)}</span>`).join('')}</div>
  </div>`;
}

function deviceCard(r) {
  if (!r.device) return '';
  const cls = r.device.tone === 'bad' ? 'is-bad' : r.device.tone === 'ok' ? 'is-ok' : '';
  return `<div class="devstate ${cls}">
    <svg class="ic" style="color:${r.device.tone === 'bad' ? 'var(--bad)' : 'var(--acc)'};margin-top:1px"><use href="#i-device"/></svg>
    <div><div class="devstate-t">${esc(r.device.title)}</div><div class="devstate-d">${esc(r.device.desc)}</div></div>
  </div>`;
}

function assetsPane(r) {
  const installable = r.assets.filter(a => a.state !== 'meta').length;
  const parsed = r.assets.filter(a => a.state !== 'unknown').length;
  return `<div class="dw-pane" data-pane="assets">
    ${verdictCard(r)}
    ${deviceCard(r)}
    <p class="pane-lead">共 <b>${r.assets.length}</b> 个产物，其中 ${installable} 个可安装。${parsed < r.assets.length ? `有 ${r.assets.length - parsed} 个无法解析，已保留原始链接。` : ''}</p>
    <div class="assets">
      ${r.assets.map((a, i) => {
        const off = a.state === 'mismatch' || a.state === 'unknown' ? 'is-off' : '';
        const best = r.best && a.name === r.best.name;
        const label = a.state === 'meta' ? '校验文件' : a.label;
        const dl = a.state === 'meta'
          ? `<span class="cmd" style="height:34px"><svg class="ic"><use href="#i-shield"/></svg>SHA256</span>`
          : `<button class="dlbtn ${best ? 'is-primary' : ''}" data-dl="${esc(a.name)}" aria-label="下载 ${esc(a.name)}">
               <svg class="ic"><use href="#i-download"/></svg>
               <svg class="ring" viewBox="0 0 44 44"><circle class="bg" cx="22" cy="22" r="19"/><circle class="fg" cx="22" cy="22" r="19" stroke-dasharray="119.4" stroke-dashoffset="119.4"/></svg>
               <svg class="ic tick"><use href="#i-check"/></svg>
             </button>`;
        return `<div class="asset is-${a.state} ${best ? 'is-best' : ''} ${off}" style="--i:${i}">
          <span class="asset-dot"></span>
          <div>
            <div class="asset-name">${esc(a.name)}</div>
            <div class="asset-state">
              <span>${esc(label)}</span>
              ${a.abi && a.state !== 'meta' ? `<i class="meta-sep"></i><span class="mono">${esc(a.sdkLabel || '')}</span>` : ''}
              ${a.sha && a.sha !== '—' ? `<i class="meta-sep"></i><span class="asset-sha">sha ${esc(a.sha)}</span>` : ''}
            </div>
            ${a.why ? `<div class="asset-why">${esc(a.why)}</div>` : ''}
          </div>
          <div class="asset-r">
            ${a.state !== 'meta' ? `<span class="asset-size">${fmtSize(a.mb)}</span>` : ''}
            ${dl}
          </div>
        </div>`;
      }).join('')}
    </div>
  </div>`;
}

function changelogPane(r) {
  return `<div class="dw-pane" data-pane="changelog">
    <div class="release">
      ${r.releases.map((rel, i) => `<div class="release-item" style="--i:${i}">
        <div class="release-top">
          <span class="release-tag">${esc(rel.tag)}</span>
          ${rel.pre ? '<span class="badge badge-pre">预发布</span>' : ''}
          ${i === 0 ? '<span class="badge badge-ok">最新</span>' : ''}
          <span class="release-date">${esc(rel.date)} · ${ago(rel.date)}</span>
        </div>
        <div class="release-notes"><ul>${rel.notes.map(n => `<li>${esc(n)}</li>`).join('')}</ul></div>
      </div>`).join('')}
    </div>
  </div>`;
}

function readmePane(r) {
  const md = r.readme;
  if (r.dist.desktop) {
    return `<div class="dw-pane" data-pane="readme">
      <div class="md">
        <p>${esc(md.desc)}</p>
        <h4>本机适配</h4>
        <p>这个 Release 只提供 <code>${esc(r.dist.sdkLabel)}</code> 平台产物。本机是 <code>${DEVICE.name}</code> / <code>${DEVICE.sdkLabel}</code>，无法安装。若你想在桌面上获取，请从 Release 页手动下载。</p>
        <h4>亮点</h4>
        <ul>${md.points.map(p => `<li>${esc(p)}</li>`).join('')}</ul>
      </div>
    </div>`;
  }
  return `<div class="dw-pane" data-pane="readme">
    <div class="md">
      <p>${esc(md.desc)}</p>
      <h4>安装说明</h4>
      <p>${esc(md.install)}</p>
      <h4>亮点</h4>
      <ul>${md.points.map(p => `<li>${esc(p)}</li>`).join('')}</ul>
      <h4>安装包信息</h4>
      <pre>minSdk   ${r.dist.minSdk}   (${esc(r.dist.sdkLabel)})
abis     ${r.dist.abis.join(', ')}
signed   ${r.dist.signed === 'ci-selfsigned' ? 'CI self-signed' : r.dist.signed === 'unknown' ? 'unverified' : 'release'}</pre>
      <hr>
      <p>数据来自 GitHub Releases 公开接口，FitHub 不做二次打包与破解。</p>
    </div>
  </div>`;
}

function buildDrawer(r) {
  const dev = installedOf(r.id);
  dwHero.innerHTML = `
    <div class="dw-hero-top">
      ${tileHtml(r, 'tile tile-lg')}
      <div style="min-width:0">
        <h2 class="dw-title" id="dwTitle">${esc(r.name)}</h2>
        <div class="dw-owner"><a href="#">${esc(r.owner)}</a> / ${esc(r.name)} · <a href="#">GitHub</a></div>
      </div>
    </div>
    <div class="dw-tags">
      ${fitBadge(r)}
      <span class="badge badge-ghost"><i class="lang-dot" style="--lang:${r.langColor}"></i>${esc(r.lang)}</span>
      ${dev ? `<span class="badge ${dev.signing === 'diff' ? 'badge-bad' : 'badge-ghost'}"><svg class="ic"><use href="#i-device"/></svg>本机 ${esc(dev.version)}</span>` : ''}
      ${r.assets.some(a => a.abi === DEVICE.abi) ? '<span class="badge badge-ghost">免登录可下载</span>' : ''}
    </div>
    <div class="dw-substats">
      <dl class="dw-stat"><dt>star</dt><dd>${fmtCount(r.stars)}</dd></dl>
      <dl class="dw-stat"><dt>fork</dt><dd>${fmtCount(r.forks)}</dd></dl>
      <dl class="dw-stat"><dt>watcher</dt><dd>${fmtCount(r.watchers)}</dd></dl>
      <dl class="dw-stat"><dt>open issues</dt><dd>${fmtCount(r.issues)}</dd></dl>
      <dl class="dw-stat"><dt>最近发布</dt><dd>${ago(r.date)}</dd></dl>
    </div>`;

  dwBody.innerHTML = assetsPane(r) + changelogPane(r) + readmePane(r);
  $$('.dw-pane', dwBody).forEach((p, i) => p.hidden = i > 0);
  $$('.dw-tab').forEach(t => t.classList.toggle('is-on', t.dataset.tab === 'assets'));
  dwScroll.scrollTop = 0;

  const desktop = r.dist.desktop;
  const ctaLabel = desktop ? '在 GitHub 打开桌面端包'
    : r.best ? `下载适配版本 · ${fmtSize(r.best.mb)}`
    : r.verdict === 'unknown' ? '打开原始安装包链接'
    : `查看全部产物 · ${r.assets.length} 个`;
  dwFoot.innerHTML = `<div class="dw-cta">
    <button class="cta" id="ctaMain" ${desktop ? '' : ''}>${esc(ctaLabel)}</button>
    <div class="cta-note">${r.best && r.best.sha !== '—'
      ? `<svg class="ic"><use href="#i-shield"/></svg>sha256 ${esc(r.best.sha)}… 下载后强校验`
      : '按 GitHub 官方 Release 直取，不经二次打包'}</div>
    <div class="dw-secondary">
      <button class="secondary" id="dwShare2"><svg class="ic"><use href="#i-share"/></svg>分享卡片</button>
      <button class="secondary" id="dwFav"><svg class="ic"><use href="#i-bookmark"/></svg>关注更新</button>
      <button class="secondary" id="dwOpen"><svg class="ic"><use href="#i-external"/></svg>Release</button>
    </div>
  </div>`;

  $('#ctaMain').addEventListener('click', () => onCta(r));
  $('#dwShare2').addEventListener('click', () => toast('生成分享卡片', `${r.name} ${r.version} · ${r.best ? r.best.abi : r.dist.sdkLabel}`));
  $('#dwFav').addEventListener('click', e => {
    const b = e.currentTarget; b.classList.toggle('is-on');
    toast(b.classList.contains('is-on') ? `已关注 ${r.name}` : `已取消关注 ${r.name}`, '有新 release 会推送通知');
  });
  $('#dwOpen').addEventListener('click', () => toast('打开 GitHub Release 页', `${r.owner}/${r.name} · ${r.version}`));
}

function onCta(r) {
  if (r.dist.desktop) return toast('本机无法安装该产物', 'Release 只提供桌面平台安装包');
  if (r.verdict === 'unknown') return toast('无法解析，已给出原始链接', r.assets[0].name);
  const row = dwBody.querySelector('.asset.is-best') || dwBody.querySelector('.asset');
  const btn = row && row.querySelector('.dlbtn');
  if (!btn) return toast('没有可下载的产物');
  simulateDownload(btn, r);
  row.scrollIntoView({ behavior:'smooth', block:'center' });
}

/* ── FLIP：图标从卡片飞到抽屉 ─────────────────────────────────── */
function fly(a, b, style, label) {
  const size = a.width;
  flyer.style.width = size + 'px';
  flyer.style.height = size + 'px';
  flyer.style.borderRadius = Math.round(size * 0.3) + 'px';
  flyer.innerHTML = `<span class="tile" style="--tile-bg:${style.bg};--tile-fg:${style.fg};width:100%;height:100%">${esc(label)}</span>`;
  const scale = b.width / size;
  return flyer.animate([
    { transform:`translate(${a.left}px,${a.top}px) scale(1)`, opacity:1 },
    { transform:`translate(${b.left}px,${b.top}px) scale(${scale})`, opacity:1 }
  ], { duration:460, easing:'cubic-bezier(.16,1,.3,1)', fill:'forwards' });
}

function openDrawer(id, fromEl) {
  const r = byId(id);
  if (!r) return;
  current = r; sourceEl = fromEl;

  const srcTile = fromEl && fromEl.querySelector('.tile');
  buildDrawer(r);

  // 目标位置：临时去掉 transform 量一次（同一帧内完成，不会闪）
  const prev = drawer.style.transform;
  drawer.style.transform = 'none';
  const dstTile = dwHero.querySelector('.tile');
  const dstRect = dstTile.getBoundingClientRect();
  drawer.style.transform = prev;

  body.classList.add('dw-open');
  drawer.setAttribute('aria-hidden', 'false');

  if (srcTile) {
    const srcRect = srcTile.getBoundingClientRect();
    const style = { bg: srcTile.style.getPropertyValue('--tile-bg'), fg: srcTile.style.getPropertyValue('--tile-fg') };
    srcTile.style.transition = 'opacity 140ms linear';
    srcTile.style.opacity = '0';
    dstTile.style.opacity = '0';
    flight = fly(srcRect, dstRect, style, srcTile.textContent);
    flight.onfinish = () => {
      dstTile.style.transition = 'opacity 160ms linear';
      dstTile.style.opacity = '1';
      setTimeout(() => { dstTile.style.transition = ''; }, 200);
      flyer.innerHTML = '';
    };
  }
}

function closeDrawer() {
  if (!body.classList.contains('dw-open')) return;
  const dstTile = dwHero.querySelector('.tile');
  const srcTile = sourceEl && document.body.contains(sourceEl) ? sourceEl.querySelector('.tile') : null;

  const finish = () => {
    body.classList.remove('dw-open');
    drawer.setAttribute('aria-hidden', 'true');
    if (srcTile) { srcTile.style.opacity = ''; srcTile.style.transition = ''; }
    if (dstTile) dstTile.style.opacity = '';
    flyer.innerHTML = '';
    current = null; sourceEl = null; flight = null;
  };

  if (flight) { try { flight.cancel(); } catch (e) {} }
  if (srcTile && dstTile) {
    const style = { bg: dstTile.style.getPropertyValue('--tile-bg'), fg: dstTile.style.getPropertyValue('--tile-fg') };
    const srcRect = srcTile.getBoundingClientRect();
    const dstRect = dstTile.getBoundingClientRect();
    dstTile.style.transition = 'opacity 120ms linear';
    dstTile.style.opacity = '0';
    flight = fly(dstRect, srcRect, style, dstTile.textContent);
    flight.onfinish = finish;
  } else finish();
}

/* ── 抽屉内 tab ───────────────────────────────────────────────── */
$('#dwTabs').addEventListener('click', e => {
  const tab = e.target.closest('.dw-tab');
  if (!tab) return;
  $$('.dw-tab').forEach(t => t.classList.toggle('is-on', t === tab));
  $$('.dw-pane').forEach(p => p.hidden = p.dataset.pane !== tab.dataset.tab);
  dwScroll.scrollTo({ top: tab.dataset.tab === 'assets' ? 0 : Math.min(dwScroll.scrollHeight, 240), behavior:'smooth' });
});

/* ── 下载模拟 ─────────────────────────────────────────────────── */
function simulateDownload(btn, r) {
  if (btn.disabled || btn.classList.contains('is-done')) return;
  btn.disabled = true;
  btn.classList.remove('is-done');
  btn.querySelector('.ic:not(.tick)').classList.add('gone');
  const fg = btn.querySelector('.ring .fg');
  const len = 119.4;
  let p = 0;
  const t = setInterval(() => {
    p += 4 + Math.random() * 9;
    if (p >= 100) {
      p = 100;
      clearInterval(t);
      fg.style.strokeDashoffset = 0;
      btn.querySelector('.ring').style.opacity = '0';
      btn.classList.add('is-done');
      toast('已加入下载队列', `${btn.dataset.dl} · ${r ? r.name : ''}`);
      setTimeout(() => { btn.querySelector('.ring').style.opacity = ''; }, 400);
    } else fg.style.strokeDashoffset = len * (1 - p / 100);
  }, 130);
}

dwBody.addEventListener('click', e => {
  const btn = e.target.closest('.dlbtn');
  if (btn) simulateDownload(btn, current);
});

/* ── 关闭入口 ─────────────────────────────────────────────────── */
$('#scrim').addEventListener('click', closeDrawer);
$('#dwClose').addEventListener('click', closeDrawer);
$('#dwShare').addEventListener('click', () => current && toast('生成分享卡片', `${current.name} ${current.version}`));
$('#dwStar').addEventListener('click', e => {
  e.currentTarget.classList.toggle('is-on');
  toast(e.currentTarget.classList.contains('is-on') ? '已加入关注' : '已取消关注', '有新 release 会推送通知');
});

/* ── 卡片点击 ─────────────────────────────────────────────────── */
feed.addEventListener('click', e => {
  const card = e.target.closest('[data-repo]');
  if (card) return openDrawer(card.dataset.repo, card);
  const agent = e.target.closest('[data-agent]');
  if (agent) {
    const a = AGENTS.find(x => x.id === agent.dataset.agent);
    toast('这是桌面 / CLI 工具', `${a.install}`);
    navigator.clipboard && navigator.clipboard.writeText(a.install).catch(() => {});
  }
});

/* ── 搜索 ─────────────────────────────────────────────────────── */
let palType = 'repo', palTimer = null;

function openPalette() {
  body.classList.add('pal-open');
  palette.setAttribute('aria-hidden', 'false');
  setTimeout(() => $('#q').focus(), 40);
  renderPalette($('#q').value);
}
function closePalette() {
  body.classList.remove('pal-open');
  palette.setAttribute('aria-hidden', 'true');
}

$('#searchBtn').addEventListener('click', openPalette);
palette.addEventListener('click', e => { if (e.target === palette) closePalette(); });
$('#palTabs').addEventListener('click', e => {
  const t = e.target.closest('.pal-tab');
  if (!t) return;
  palType = t.dataset.type;
  $$('.pal-tab').forEach(x => x.classList.toggle('is-on', x === t));
  renderPalette($('#q').value);
});
$('#q').addEventListener('input', e => {
  clearTimeout(palTimer);
  const v = e.target.value;
  palTimer = setTimeout(() => renderPalette(v), 300);   // 产品要求：300ms 防抖
});

function renderPalette(qRaw) {
  const q = qRaw.trim().toLowerCase();
  const tokens = q.split(/\s+/).filter(t => /^(has|language|topic):/.test(t));
  const plain = q.replace(/\b(has|language|topic):\S+/g, '').trim();

  if (palType !== 'repo') {
    const list = PEOPLE.filter(p => p.type === palType && (!plain || (p.name + p.handle + (p.bio || '')).toLowerCase().includes(plain)));
    palList.innerHTML = list.length ? list.map(p => `<button class="pal-row" data-person="${esc(p.handle)}">
      <span class="tile tile-sm" style="--tile-bg:${p.tile.bg};--tile-fg:${p.tile.fg}">${esc(p.name.slice(0, 2))}</span>
      <span><span class="pal-name">${esc(p.name)}${p.hireable ? ' <span class="badge badge-ok">可合作</span>' : ''}</span>
      <span class="pal-sub">@${esc(p.handle)} · ${esc(p.loc || '')}</span></span>
      <span class="pal-r">${fmtCount(p.followers)} followers</span></button>`).join('')
      : `<div class="pal-empty">没有找到匹配的${palType === 'user' ? '用户' : '组织'} —— <b>换个关键词试试</b></div>`;
    return;
  }

  let list = VIEW;
  tokens.forEach(t => {
    const [k, val] = t.split(':');
    if (k === 'language') list = list.filter(r => r.lang.toLowerCase() === val);
    if (k === 'topic') list = list.filter(r => r.topics.includes(val));
    if (k === 'has') list = list.filter(r => r.assets.length > 0);
  });
  if (plain) list = list.filter(r => (r.name + r.owner + r.desc).toLowerCase().includes(plain));

  if (!qRaw) {
    palList.innerHTML = `<div class="pal-empty" style="text-align:left;padding:18px 16px">
      <b>试试这些：</b><br>
      <code style="font-family:var(--mono);font-size:12px">stars:&gt;5000</code> 高 star 仓库 ·
      <code style="font-family:var(--mono);font-size:12px">language:kotlin</code> ·
      <code style="font-family:var(--mono);font-size:12px">topic:compose</code><br>
      注意：GitHub 没有 <code style="font-family:var(--mono);font-size:12px">has:release</code> 这个限定符，
      输入它不会报错也不会过滤（实测与不写结果数相同）。</div>`;
    return;
  }

  palList.innerHTML = list.length ? list.slice(0, 8).map(r => `<button class="pal-row" data-repo="${r.id}">
    ${tileHtml(r, 'tile tile-sm')}
    <span><span class="pal-name">${esc(r.name)} ${fitBadge(r)}</span><span class="pal-sub">${esc(r.owner)} / ${esc(r.name)} · ${esc(r.version)}</span></span>
    <span class="pal-r">${fmtCount(r.stars)} ★</span></button>`).join('')
    : `<div class="pal-empty">没有匹配 —— <b>去掉筛选语法</b>或换个关键词</div>`;
}

palList.addEventListener('click', e => {
  const r = e.target.closest('[data-repo]');
  if (r) { closePalette(); openDrawer(r.dataset.repo, null); return; }
  const p = e.target.closest('[data-person]');
  if (p) { closePalette(); toast('打开用户 / 组织页', '@' + p.dataset.person + ' · 该主体下 6 个可安装项目'); }
});

/* ── 其他按钮 ─────────────────────────────────────────────────── */
let toastTimer;
function toast(title, note) {
  toastEl.innerHTML = `<svg class="ic"><use href="#i-check"/></svg><span>${esc(title)}</span>${note ? `<span class="mono">${esc(note)}</span>` : ''}`;
  toastEl.classList.add('is-on');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.remove('is-on'), 2600);
}

$('#scanBtn').addEventListener('click', () => {
  const radar = $('#radar');
  radar.style.animation = 'none';
  radar.getAnimations().forEach(a => a.cancel());
  $$('.radar-ring').forEach((el, i) => {
    el.animate([{ transform:'scale(.86)', opacity:1 }, { transform:'scale(1.06)', opacity:.5 }],
      { duration:1200, iterations:3, delay:i * 120, easing:'ease-out' });
  });
  setTimeout(() => toast('本机扫描完成', '4 个可关联应用 · 3 个有更新 · 1 个签名冲突'), 900);
});

$('#updatesBtn').addEventListener('click', () => {
  $('#sec-recent').scrollIntoView({ behavior:'smooth', block:'start' });
  const first = feed.querySelector('.row.is-up');
  if (first) {
    first.classList.remove('is-flash');
    void first.offsetWidth;
    first.classList.add('is-flash');
  }
});

$('#filterBtn').addEventListener('click', () => toast('筛选面板', '原型里只做了智能发现 chips：语言 / topic / 有安装包'));

$$('.nav-item').forEach(item => item.addEventListener('click', e => {
  e.preventDefault();
  $$('.nav-item').forEach(i => i.classList.toggle('is-on', i === item));
  if (item.textContent.includes('更新')) $('#updatesBtn').click();
}));

document.addEventListener('keydown', e => {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); body.classList.contains('pal-open') ? closePalette() : openPalette(); return; }
  if (e.key !== 'Escape') return;
  if (body.classList.contains('pal-open')) return closePalette();
  if (body.classList.contains('dw-open')) closeDrawer();
});

/* ── init ─────────────────────────────────────────────────────── */
renderChips();
renderFeed();
