/**
 * 把 Arial Bold 的字形轮廓转成 SVG path，供 Android VectorDrawable 使用。
 *
 * 为什么不用 <text>：Android 的 VectorDrawable 只支持 <path>，没有 <text>，
 * 也没有内置字体（塞字体文件会让 APK 大几十 KB）。所以字形必须转成死 path。
 * 代价：改文案/换字体/调字距都要重跑本脚本。
 *
 * 关键：用 opentype 自己的 Path.toPathData() 输出坐标，不要自己手写缩放/翻转。
 * 手写过一版，把 y 轴翻转让整行字被压扁 —— resvg 会宽容地渲染出「看起来有点扁但能看」
 * 的结果，Android 直接拒绝解析，于是**静默回退到系统默认图标**，
 * 从 AVD 上看只是「图标没变」，根本不像代码错误。这个坑没有编译器会告诉你。
 */
import opentype from 'opentype.js';
import { readFileSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = dirname(fileURLToPath(import.meta.url));
const FONT = 'C:/Windows/Fonts/arialbd.ttf';
const VB = 108;            // viewport 边长
const FONT_SIZE = 20;      // advance 宽 63.3 <= adaptive icon 的 66 安全区
const TRACKING = -0.2;
const SAFE_ZONE = 66;

const font = opentype.parse(readFileSync(FONT).buffer);
const UPM = font.unitsPerEm;

const SEGMENTS = [
  { text: 'Fit', color: '#FF7FE3C4' },
  { text: 'Hub', color: '#FFFFFFFF' },
];

/** 用 opentype 自己把字形摆到指定位置并输出 path data（单位=视口单位） */
function pathDataAt(text, xPx, yPx) {
  const p = font.getPath(text, xPx, yPx, FONT_SIZE);
  return p.toPathData(2);
}

function inkBounds() {
  let yMax = -Infinity, yMin = Infinity, xMax = -Infinity, xMin = Infinity;
  for (const ch of 'FitHub') {
    const bb = font.getPath(ch, 0, 0, FONT_SIZE).getBoundingBox();
    yMax = Math.max(yMax, bb.y1, bb.y2);
    yMin = Math.min(yMin, bb.y1, bb.y2);
    xMax = Math.max(xMax, bb.x1, bb.x2);
    xMin = Math.min(xMin, bb.x1, bb.x2);
  }
  return { yMax, yMin, xMax, xMin };
}

// --- 布局：整串先按 advance 宽度水平居中，再按墨迹包围盒垂直居中 -------------
let advance = 0;
for (const ch of 'FitHub') advance += font.getAdvanceWidth(ch, FONT_SIZE) + TRACKING;
advance -= TRACKING;

const startX = (VB - advance) / 2;
const { yMax, yMin, xMax, xMin } = inkBounds();

// opentype 的 y 向下（它按屏幕坐标输出），所以 y1 上、y2 下。
// 视觉中心 = (y1+y2)/2，移到 54 即可。
const inkCY = (yMax + yMin) / 2;
const dy = VB / 2 - inkCY;

const inkW = xMax - xMin;
const inkH = yMax - yMin;
if (!Number.isFinite(inkW) || inkW <= 0) throw new Error('墨迹宽度异常');

// 按字符逐个摆放，这样两段可以分别着色
let cursor = startX;
const parts = [];
for (const seg of SEGMENTS) {
  for (const ch of seg.text) {
    parts.push({ color: seg.color, d: pathDataAt(ch, cursor, dy) });
    cursor += font.getAdvanceWidth(ch, FONT_SIZE) + TRACKING;
  }
}

for (const p of parts) {
  if (/NaN|Infinity|undefined/.test(p.d)) {
    throw new Error(`pathData 非法：${p.d.slice(0, 100)}`);
  }
}

const xml = `<?xml version="1.0" encoding="utf-8"?>
<!--
  自动生成，**不要手改**。要改字请改 scripts/icon/wordmark-c.svg 后重跑本脚本。

  ${SEGMENTS.map((s) => s.text).join('')} 字标：Arial Bold ${FONT_SIZE}px，字距 ${TRACKING}，
  视口 108x108，水平按 advance 居中、垂直按实际墨迹居中。

  为什么是死 path：Android 的 VectorDrawable 只支持 <path>，没有 <text> 也没有内置字体。
  改文案必须重新转换 —— 路径由 opentype.js 的 toPathData() 生成，不要手写坐标缩放。

  前景层在 adaptive-icon 里被裁到中心 ${SAFE_ZONE}dp 安全区。
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
${parts.map((p) => `
    <path
        android:pathData="${p.d}"
        android:fillColor="${p.color}"
        android:fillType="nonZero" />`).join('\n')}
</vector>
`;

writeFileSync(join(dir, 'ic_launcher_foreground.xml'), xml, 'utf8');
writeFileSync(
  join(dir, 'path-preview.svg'),
  `<svg xmlns="http://www.w3.org/2000/svg" width="432" height="432" viewBox="0 0 108 108">` +
  `<rect width="108" height="108" fill="#14624F"/>` +
  parts.map((p) => `<path d="${p.d}" fill="${p.color === '#FF7FE3C4' ? '#7FE3C4' : '#FFFFFF'}"/>`).join('') +
  `</svg>`,
  'utf8',
);

console.log(`advance 宽 ${advance.toFixed(2)} / 108`);
console.log(`墨迹包围盒  w=${inkW.toFixed(2)} h=${inkH.toFixed(2)}  x:${xMin.toFixed(1)}..${xMax.toFixed(1)} y:${yMin.toFixed(1)}..${yMax.toFixed(1)}`);
console.log(`安全区 ${SAFE_ZONE} 之内: advance ${advance <= SAFE_ZONE ? 'YES' : 'NO'} / 墨迹宽 ${inkW <= SAFE_ZONE ? 'YES' : 'NO'}`);
console.log(`path 段数 ${parts.length}，输出 ${xml.length} bytes`);
