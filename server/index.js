const http = require('http');
const https = require('https');

const PORT = process.env.PORT || 3000;

// Cache to avoid hammering the API
let currentCache = { data: '{}', time: 0 };
let historyCache = { data: '[]', time: 0 };

function fetchUrl(hostname, path, headers) {
  return new Promise((resolve, reject) => {
    const options = { hostname, path, headers };
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
    // Try tzevaadom API (not blocked by Akamai)
    const raw = await fetchUrl('www.tzevaadom.co.il', '/alerts', {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
      'Accept': 'application/json',
      'Origin': 'https://www.tzevaadom.co.il',
    });
    const clean = raw.trim();
    // tzevaadom returns array of city names when alert active, empty array when safe
    if (clean && clean.startsWith('[')) {
      const areas = JSON.parse(clean);
      if (areas.length > 0) {
        const result = JSON.stringify({
          id: now.toString(),
          title: 'צבע אדום',
          data: areas.join(', ')
        });
        currentCache = { data: result, time: now };
        return result;
      }
    }
    currentCache = { data: '{}', time: now };
    return '{}';
  } catch (err) {
    console.error('getCurrentAlert error:', err.message);
    return currentCache.data;
  }
}

async function getHistory() {
  const now = Date.now();
  if (now - historyCache.time < 30000) return historyCache.data;

  try {
    const raw = await fetchUrl('www.oref.org.il',
      '/WarningMessages/alert/History/AlertsHistory.json', {
      'Referer': 'https://www.oref.org.il/',
      'X-Requested-With': 'XMLHttpRequest',
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
      'Accept': 'application/json',
    });
    const clean = raw.replace(/^\uFEFF/, '').trim();
    const result = (clean && clean.startsWith('[')) ? clean : '[]';
    historyCache = { data: result, time: now };
    return result;
  } catch (err) {
    console.error('getHistory error:', err.message);
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
