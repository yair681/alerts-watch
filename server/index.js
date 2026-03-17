const http = require('http');
const https = require('https');
const zlib = require('zlib');

const PORT = process.env.PORT || 3000;

let currentCache = { data: '{}', time: 0 };
let historyCache = { data: '[]', time: 0 };

function fetchDirect(path) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'www.oref.org.il',
      path: path,
      headers: {
        'Referer': 'https://www.oref.org.il/',
        'X-Requested-With': 'XMLHttpRequest',
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'application/json, text/plain, */*',
        'Accept-Language': 'he-IL,he;q=0.9',
        'Accept-Encoding': 'gzip, deflate, br',
        'Origin': 'https://www.oref.org.il',
        'Connection': 'keep-alive',
      },
    };
    https.get(options, (res) => {
      const encoding = res.headers['content-encoding'];
      let stream = res;
      if (encoding === 'gzip') stream = res.pipe(zlib.createGunzip());
      else if (encoding === 'deflate') stream = res.pipe(zlib.createInflate());
      else if (encoding === 'br') stream = res.pipe(zlib.createBrotliDecompress());
      let data = '';
      stream.on('data', (chunk) => { data += chunk; });
      stream.on('end', () => resolve(data));
      stream.on('error', reject);
    }).on('error', reject);
  });
}

function fetchViaProxy(proxyUrl) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(proxyUrl);
    const options = {
      hostname: parsed.hostname,
      path: parsed.pathname + parsed.search,
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36',
        'Accept': 'application/json, text/plain, */*',
      },
    };
    https.get(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => resolve(data));
    }).on('error', reject);
  });
}

async function tryFetch(orefPath, expectedStart) {
  const targetUrl = 'https://www.oref.org.il' + orefPath;

  // 1. Try direct
  try {
    const raw = await fetchDirect(orefPath);
    const clean = raw.replace(/^\uFEFF/, '').trim();
    if (clean.startsWith(expectedStart)) {
      console.log('direct OK for ' + orefPath);
      return clean;
    }
    console.log('direct blocked, trying proxies...');
  } catch (e) {
    console.log('direct error:', e.message);
  }

  // 2. Try allorigins /get (returns JSON wrapper with http_code)
  try {
    const raw = await fetchViaProxy(
      'https://api.allorigins.win/get?url=' + encodeURIComponent(targetUrl)
    );
    const parsed = JSON.parse(raw);
    if (parsed.status && parsed.status.http_code === 200 && parsed.contents) {
      const clean = parsed.contents.replace(/^\uFEFF/, '').trim();
      if (clean.startsWith(expectedStart) && !clean.includes('"error"')) {
        console.log('allorigins OK');
        return clean;
      }
    }
    console.log('allorigins returned http_code:', parsed.status && parsed.status.http_code);
  } catch (e) {
    console.log('allorigins error:', e.message);
  }

  // 3. Try codetabs proxy
  try {
    const raw = await fetchViaProxy(
      'https://api.codetabs.com/v1/proxy?quest=' + encodeURIComponent(targetUrl)
    );
    const clean = raw.replace(/^\uFEFF/, '').trim();
    if (clean.startsWith(expectedStart) && !clean.includes('"error"')) {
      console.log('codetabs OK');
      return clean;
    }
    console.log('codetabs returned:', clean.substring(0, 80));
  } catch (e) {
    console.log('codetabs error:', e.message);
  }

  // 4. Try htmldriven proxy
  try {
    const raw = await fetchViaProxy(
      'https://jsonp.afeld.me/?url=' + encodeURIComponent(targetUrl)
    );
    const clean = raw.replace(/^\uFEFF/, '').trim();
    if (clean.startsWith(expectedStart) && !clean.includes('"error"')) {
      console.log('afeld OK');
      return clean;
    }
    console.log('afeld returned:', clean.substring(0, 80));
  } catch (e) {
    console.log('afeld error:', e.message);
  }

  return null;
}

async function getCurrentAlert() {
  const now = Date.now();
  if (now - currentCache.time < 2000) return currentCache.data;
  const result = await tryFetch('/WarningMessages/alert/alerts.json', '{');
  const data = result || '{}';
  currentCache = { data, time: now };
  return data;
}

async function getHistory() {
  const now = Date.now();
  if (now - historyCache.time < 30000) return historyCache.data;
  const result = await tryFetch('/WarningMessages/alert/History/AlertsHistory.json', '[');
  const data = result || '[]';
  historyCache = { data, time: now };
  return data;
}

const server = http.createServer(async (req, res) => {
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Access-Control-Allow-Origin', '*');

  if (req.url === '/current') {
    res.end(await getCurrentAlert());
  } else if (req.url === '/history') {
    res.end(await getHistory());
  } else {
    res.end('{"status":"ok"}');
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('Server running on port ' + PORT);
});
