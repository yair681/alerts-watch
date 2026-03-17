const http = require('http');
const https = require('https');

const PORT = 3000;

const OREF_HEADERS = {
  'Referer': 'https://www.oref.org.il/',
  'X-Requested-With': 'XMLHttpRequest',
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36',
  'Accept': 'application/json, text/plain, */*',
  'Accept-Language': 'he-IL,he;q=0.9',
};

function fetchOref(path) {
  return new Promise((resolve, reject) => {
    const options = {
      hostname: 'www.oref.org.il',
      path: path,
      headers: OREF_HEADERS,
    };
    https.get(options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => resolve(data));
    }).on('error', reject);
  });
}

const server = http.createServer(async (req, res) => {
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Access-Control-Allow-Origin', '*');

  try {
    if (req.url === '/current') {
      const raw = await fetchOref('/WarningMessages/alert/alerts.json');
      const clean = raw.replace(/^\uFEFF/, '').trim();
      res.end(clean || '{}');

    } else if (req.url === '/history') {
      const raw = await fetchOref('/WarningMessages/alert/History/AlertsHistory.json');
      const clean = raw.replace(/^\uFEFF/, '').trim();
      res.end(clean || '[]');

    } else {
      res.end('{"status":"ok","service":"pikud-haoref-proxy"}');
    }
  } catch (err) {
    console.error('Error:', err.message);
    res.end(req.url === '/history' ? '[]' : '{}');
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('Pikud HaOref proxy server running on port ' + PORT);
  console.log('Endpoints:');
  console.log('  GET http://YOUR_IP:' + PORT + '/current  -> current alert');
  console.log('  GET http://YOUR_IP:' + PORT + '/history  -> alerts history');
});
