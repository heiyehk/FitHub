const { createServer } = require('http');
const { readFile } = require('fs');
const { join, extname } = require('path');

const T = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8' };
const ROOT = __dirname;

createServer((q, s) => {
  const rel = decodeURIComponent(q.url.split('?')[0]);
  const p = join(ROOT, rel === '/' ? 'index.html' : rel);
  if (!p.startsWith(ROOT)) { s.writeHead(403); return s.end(); }
  readFile(p, (e, d) => {
    if (e) { s.writeHead(404); return s.end('not found'); }
    s.writeHead(200, { 'Content-Type': T[extname(p)] || 'text/plain; charset=utf-8' });
    s.end(d);
  });
}).listen(4173, () => console.log('FitHub prototype on http://localhost:4173'));
