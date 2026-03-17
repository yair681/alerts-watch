export default {
  async fetch(request) {
    const url = new URL(request.url);
    const headers = {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
    };

    const orefHeaders = {
      'Referer': 'https://www.oref.org.il/',
      'X-Requested-With': 'XMLHttpRequest',
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36',
      'Accept': 'application/json, text/plain, */*',
      'Accept-Language': 'he-IL,he;q=0.9,en;q=0.8',
    };

    if (url.pathname === '/current') {
      try {
        const resp = await fetch(
          'https://www.oref.org.il/WarningMessages/alert/alerts.json',
          { headers: orefHeaders }
        );
        const text = await resp.text();
        const clean = text.replace(/^\uFEFF/, '').trim();
        return new Response(clean || '{}', { headers });
      } catch (e) {
        return new Response('{}', { headers });
      }
    }

    if (url.pathname === '/history') {
      try {
        const resp = await fetch(
          'https://www.oref.org.il/WarningMessages/alert/History/AlertsHistory.json',
          { headers: orefHeaders }
        );
        const text = await resp.text();
        const clean = text.replace(/^\uFEFF/, '').trim();
        return new Response(clean || '[]', { headers });
      } catch (e) {
        return new Response('[]', { headers });
      }
    }

    return new Response('{"status":"ok"}', { headers });
  }
};
