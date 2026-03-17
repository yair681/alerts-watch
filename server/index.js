const http = require('http');
const https = require('https');

const PORT = process.env.PORT || 3000;

let currentCache = { data: '{}', time: 0 };
let historyCache = { data: '[]', time: 0 };

function fetchViaProxy(targetUrl) {
  return new Promise((resolve, reject) => {
    const proxyUrl = 'https://api.allorigins.win/raw?url=' + encodeURIComponent(targetUrl);
    const parsed = new URL(proxyUrl);
    const options = {
      hostname: parsed.hostname,
      path: parsed.pathname + parsed.search,
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
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

async function getCurrentAlert() {
  const now = Date.now();
  if (now - currentCache.time < 2000) return currentCache.data;
  try {
    const raw = await fetchViaProxy('https://www.oref.org.il/WarningMessages/alert/alerts.json');
    const clean = raw.replace(/^\uFEFF/, '').trim();
    const result = (clean && clean.startsWith('{')) ? clean : '{}';
    currentCache = { data: result, time: now };
    return result;
  } catch (err) {
    console.error('current error:', err.message);
    return currentCache.data;
  }
}

async function getHistory() {
  const now = Date.now();
  if (now - historyCache.time < 30000) return historyCache.data;
  try {
    const raw = await fetchViaProxy('https://www.oref.org.il/WarningMessages/alert/History/AlertsHistory.json');
    const clean = raw.replace(/^\uFEFF/, '').trim();
    const result = (clean && clean.startsWith('[')) ? clean : '[]';
    historyCache = { data: result, time: now };
    return result;
  } catch (err) {
    console.error('history error:', err.message);
    return historyCache.data;
  }
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
