const http = require('http');
const https = require('https');

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
      },
    };
    https.get(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => resolve(data));
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
        'User-Agent': 'Mozilla/5.0',
        'Accept': 'application/json',
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

  // 2. Try allorigins
  try {
    const raw = await fetchViaProxy(
      'https://api.allorigins.win/raw?url=' + encodeURIComponent(targetUrl)
    );
    const clean = raw.replace(/^\uFEFF/, '').trim();
    if (clean.startsWith(expectedStart)) {
      console.log('allorigins OK');
      return clean;
    }
    console.log('allorigins returned:', clean.substring(0, 80));
  } catch (e) {
    console.log('allorigins error:', e.message);
  }

  // 3. Try corsproxy.io
  try {
    const raw = await fetchViaProxy(
      'https://corsproxy.io/?' + encodeURIComponent(targetUrl)
    );
    const clean = raw.replace(/^\uFEFF/, '').trim();
    if (clean.startsWith(expectedStart)) {
      console.log('corsproxy OK');
      return clean;
    }
    console.log('corsproxy returned:', clean.substring(0, 80));
  } catch (e) {
    console.log('corsproxy error:', e.message);
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
