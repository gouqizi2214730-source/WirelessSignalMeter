'use strict';
/**
 * 无线无忧 更新检测后端（零第三方依赖，纯 Node.js 内置模块）
 * 功能：
 *   GET /api/version       -> 返回 version.json（App 用于比对 versionCode）
 *   GET /api/nodes          -> 返回 nodes.json（App 测速预设节点，服务端下发）
 *   GET /api/download       -> 302 跳转到 version.json 中的 apkUrl
 *   POST /api/log           -> 接收 App 上传的运行日志，写入 logs/
 *   GET /downloads/<file>   -> 静态托管 public/downloads 下的 APK 等文件
 *   GET /                   -> 简单状态首页
 * 运行：node server.js   （端口可用环境变量 PORT 覆盖，默认 8080）
 */
const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = parseInt(process.env.PORT || '8080', 10);
const HOST = process.env.HOST || '0.0.0.0';
const PUBLIC_DIR = path.join(__dirname, 'public');
const VERSION_FILE = path.join(__dirname, 'version.json');
const NODES_FILE = path.join(__dirname, 'nodes.json');
// NSRebuild（包名 com.wireless.signalmeter.new）独立更新通道，与 WirelessSignalMeter 版本隔离
const NS_DIR = path.join(__dirname, 'nsrebuild');

const MIME = {
  '.apk': 'application/vnd.android.package-archive',
  '.json': 'application/json; charset=utf-8',
  '.html': 'text/html; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.png': 'image/png',
  '.css': 'text/css; charset=utf-8',
  '.md': 'text/markdown; charset=utf-8'
};

function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Cache-Control': 'no-store'
  });
  res.end(body);
}

// 防目录穿越：规范化后必须仍在 PUBLIC_DIR 内
function safeJoin(base, target) {
  const p = path.normalize(path.join(base, target));
  if (p !== base && !p.startsWith(base + path.sep)) return null;
  return p;
}

function readVersion(cb) {
  fs.readFile(VERSION_FILE, 'utf8', (err, data) => {
    if (err) return cb(null);
    try { cb(JSON.parse(data)); } catch (e) { cb(null); }
  });
}

// 静态文件服务（支持 Range 断点续传），供主站 public 与 nsrebuild 复用
function serveStatic(req, res, baseDir, rel) {
  const filePath = safeJoin(baseDir, rel);
  if (!filePath) { res.writeHead(403); res.end('Forbidden'); return; }
  fs.stat(filePath, (err, stat) => {
    if (err || !stat.isFile()) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
      res.end('Not Found');
      return;
    }
    const ext = path.extname(filePath).toLowerCase();
    const total = stat.size;
    const headers = {
      'Content-Type': MIME[ext] || 'application/octet-stream',
      'Access-Control-Allow-Origin': '*',
      'Cache-Control': 'no-store',
      'Accept-Ranges': 'bytes'
    };
    const range = req.headers.range;
    if (range) {
      const m = /bytes=(\d*)-(\d*)/.exec(range);
      const start = m && m[1] ? parseInt(m[1], 10) : 0;
      const end = m && m[2] ? parseInt(m[2], 10) : total - 1;
      if (isNaN(start) || isNaN(end) || start > end || end >= total) {
        res.writeHead(416, { 'Content-Range': 'bytes */' + total });
        res.end();
        return;
      }
      headers['Content-Range'] = `bytes ${start}-${end}/${total}`;
      headers['Content-Length'] = end - start + 1;
      res.writeHead(206, headers);
      fs.createReadStream(filePath, { start, end }).pipe(res);
      return;
    }
    headers['Content-Length'] = total;
    res.writeHead(200, headers);
    fs.createReadStream(filePath).pipe(res);
  });
}

const server = http.createServer((req, res) => {
  let pathname;
  try {
    pathname = decodeURIComponent(new URL(req.url, 'http://' + (req.headers.host || 'localhost')).pathname);
  } catch (e) {
    res.writeHead(400); res.end('Bad Request'); return;
  }

  // CORS 预检（浏览器跨域时才需要；App 用 HttpURLConnection 不走 CORS，但保留以兼容 Web 调用）
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type'
    });
    res.end();
    return;
  }

  // ============ NSRebuild 独立更新通道（/nsrebuild/ 前缀） ============
  // 包名 com.wireless.signalmeter.new（v1.x），与主通道（com.wireless.signalmeter）版本体系隔离
  if (pathname === '/nsrebuild/api/version') {
    fs.readFile(path.join(NS_DIR, 'version.json'), 'utf8', (err, data) => {
      if (err) return sendJson(res, 500, { error: 'nsrebuild version.json missing' });
      try { sendJson(res, 200, JSON.parse(data)); }
      catch (e) { sendJson(res, 500, { error: 'nsrebuild version.json invalid' }); }
    });
    return;
  }

  if (pathname === '/nsrebuild/api/log') {
    if (req.method !== 'POST') {
      res.writeHead(405, { 'Access-Control-Allow-Origin': '*' });
      res.end('Method Not Allowed');
      return;
    }
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
      if (raw.length > 4 * 1024 * 1024) req.destroy();
    });
    req.on('end', () => {
      const dir = path.join(NS_DIR, 'logs');
      try { fs.mkdirSync(dir, { recursive: true }); } catch (e) { /* ignore */ }
      const stamp = new Date().toISOString().replace(/[:.]/g, '-');
      fs.writeFile(path.join(dir, 'log_' + stamp + '.json'), raw, (err) => {
        if (err) return sendJson(res, 500, { ok: false, error: 'write failed' });
        sendJson(res, 200, { ok: true });
      });
    });
    return;
  }

  if (pathname.startsWith('/nsrebuild/')) {
    const rel = pathname.slice('/nsrebuild'.length);
    serveStatic(req, res, NS_DIR, rel === '' ? '/index.html' : rel);
    return;
  }
  // ============ NSRebuild 通道结束 ============

  // 版本清单接口
  if (pathname === '/api/version') {
    readVersion((json) => {
      if (!json) return sendJson(res, 500, { error: 'version.json missing or invalid' });
      sendJson(res, 200, json);
    });
    return;
  }

  // 测速预设节点清单（App 启动/打开预设弹窗时拉取，失败则用 App 内置兜底表）
  if (pathname === '/api/nodes') {
    fs.readFile(NODES_FILE, 'utf8', (err, data) => {
      if (err) return sendJson(res, 404, { error: 'nodes.json missing' });
      let json = null;
      try { json = JSON.parse(data); } catch (e) { json = null; }
      if (!json) return sendJson(res, 500, { error: 'nodes.json invalid' });
      sendJson(res, 200, json);
    });
    return;
  }

  // 最新 APK 重定向
  if (pathname === '/api/download') {
    readVersion((json) => {
      if (!json || !json.apkUrl) { res.writeHead(404); res.end('Not Found'); return; }
      res.writeHead(302, { 'Location': json.apkUrl });
      res.end();
    });
    return;
  }

  // 接收 App 上传的运行日志（POST JSON），按时间戳写入 logs/ 目录
  if (pathname === '/api/log') {
    if (req.method !== 'POST') {
      res.writeHead(405, { 'Access-Control-Allow-Origin': '*' });
      res.end('Method Not Allowed');
      return;
    }
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
      if (raw.length > 4 * 1024 * 1024) req.destroy(); // 单条日志上限 4MB，防滥用
    });
    req.on('end', () => {
      const dir = path.join(__dirname, 'logs');
      try { fs.mkdirSync(dir, { recursive: true }); } catch (e) { /* ignore */ }
      const stamp = new Date().toISOString().replace(/[:.]/g, '-');
      const file = path.join(dir, 'log_' + stamp + '.json');
      fs.writeFile(file, raw, (err) => {
        if (err) return sendJson(res, 500, { ok: false, error: 'write failed' });
        sendJson(res, 200, { ok: true, file: path.basename(file) });
      });
    });
    return;
  }

  // 静态文件（支持 Range，便于大文件断点续传）
  const rel = pathname === '/' ? '/index.html' : pathname;
  serveStatic(req, res, PUBLIC_DIR, rel);
});

server.listen(PORT, HOST, () => {
  console.log('==================================================');
  console.log(' 无线无忧 更新服务已启动');
  console.log(' 监听:   http://' + HOST + ':' + PORT);
  console.log(' 版本接口: http://' + HOST + ':' + PORT + '/api/version');
  console.log(' 节点接口: http://' + HOST + ':' + PORT + '/api/nodes');
  console.log(' APK 目录: ' + PUBLIC_DIR + '/downloads');
  console.log('==================================================');
});
