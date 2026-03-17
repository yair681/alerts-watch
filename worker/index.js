const OREF_BASE = 'https://www.oref.org.il';

const OREF_HEADERS = {
  'Referer': 'https://www.oref.org.il/',
  'X-Requested-With': 'XMLHttpRequest',
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36',
  'Accept': 'application/json, text/plain, */*',
  'Accept-Language': 'he-IL,he;q=0.9',
  'Origin': 'https://www.oref.org.il',
};

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Content-Type': 'application/json; charset=utf-8',
};

async function fetchOref(path) {
  const res = await fetch(OREF_BASE + path, { headers: OREF_HEADERS });
  const text = await res.text();
  return text.replace(/^\uFEFF/, '').trim();
}

export default {
  async fetch(request) {
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      return new Response(null, { headers: CORS_HEADERS });
    }

    try {
      if (url.pathname === '/current') {
        const data = await fetchOref('/WarningMessages/alert/alerts.json');
        return new Response(data.startsWith('{') ? data : '{}', { headers: CORS_HEADERS });
      }
      if (url.pathname === '/history') {
        const data = await fetchOref('/WarningMessages/alert/History/AlertsHistory.json');
        return new Response(data.startsWith('[') ? data : '[]', { headers: CORS_HEADERS });
      }
      return new Response('{"status":"ok"}', { headers: CORS_HEADERS });
    } catch (e) {
      return new Response(JSON.stringify({ error: e.message }), { status: 500, headers: CORS_HEADERS });
    }
  },
};
