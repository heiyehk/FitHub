import { Resvg } from '@resvg/resvg-js';
import { readFileSync, writeFileSync, readdirSync, unlinkSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = dirname(fileURLToPath(import.meta.url));
const fonts = {
  fontFiles: ['C:/Windows/Fonts/arialbd.ttf', 'C:/Windows/Fonts/arial.ttf'],
  loadSystemFonts: false,
  defaultFontFamily: 'Arial',
};
const raster = (svgText, size) =>
  new Resvg(svgText, { fitTo: { mode: 'width', value: size }, font: fonts }).render().asPng();

const C = readFileSync(join(dir, 'wordmark-c.svg'), 'utf8');

const circle = C.replace(
  '<rect width="108" height="108" fill="#14624F"/>',
  `<defs><clipPath id="c"><circle cx="54" cy="54" r="50"/></clipPath></defs><g clip-path="url(#c)"><rect width="108" height="108" fill="#14624F"/>` +
  `<text x="54" y="54" font-family="Arial, Helvetica, sans-serif" font-size="26" font-weight="bold" ` +
  `text-anchor="middle" dominant-baseline="central" letter-spacing="-0.8">` +
  `<tspan fill="#7FE3C4">Fit</tspan><tspan fill="#FFFFFF">Hub</tspan></text></g>`,
);
const foreground = C.replace('<rect width="108" height="108" fill="#14624F"/>', '');

const jobs = [
  ['wordmark-512.png', 512, C],
  ['wordmark-1024.png', 1024, C],
  ['wordmark-round-192.png', 192, circle],
  ['wordmark-round-512.png', 512, circle],
  ['wordmark-foreground-432.png', 432, foreground],
];

for (const [name, size, svgText] of jobs) {
  const png = raster(svgText, size);
  writeFileSync(join(dir, name), png);
  console.log(`${name.padEnd(32)} ${String(size).padStart(5)}px  ${png.length.toLocaleString()} bytes`);
}

// 清理上一轮按组渲染的 word-*.png 和各方案 256 预览
for (const f of readdirSync(dir)) {
  if (/^word-[A-E]-\d+\.png$/.test(f)) unlinkSync(join(dir, f));
}
console.log('已清理 word-?-N.png 预览');
