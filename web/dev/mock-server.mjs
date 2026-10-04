// A stand-in for the Spring Boot API, for looking at the web pages without a database or a model.
// Run it with `npm run mock` next to `npm run dev`; sign in with any username and password (everyone is an administrator here, so the menu shows).
// It only answers what the search page needs; every other /api path is a 404.
import http from 'node:http';

const palette = ['#e8c547', '#6aa0c9', '#d96c5a', '#7bb08a', '#b58bd0', '#f0a35e'];
const dims = [[600, 600], [500, 700], [640, 420], [560, 560], [480, 640], [700, 460], [600, 800], [520, 520], [640, 640], [560, 400], [500, 600], [600, 500]];
const meanings = [
  '用來表達聽不懂對方在說什麼,滿頭問號的反應,常見於討論到專業術語、或對方講了一大串卻沒有重點的場合。',
  '表面客氣、內心崩潰的微笑,適合回應不合理的要求。',
  '終於準時下班的解脫感。',
  '被老闆臨時加工作時的絕望。',
  '黑人問號,表示一臉困惑。',
  '一臉嫌棄但不說破。',
  '開心到飛起來。',
  '這個週末到底去哪了。',
  '假裝很忙。',
  '看破不說破,默默喝茶。',
  '收到訊息但已讀不回。',
  '深夜 emo 的時候。',
];
const tags = ['困惑', '問號', '職場', '尷尬', '下班', '加班', '崩潰', '微笑'];

const items = dims.map(([w, h], i) => ({
  templateId: `00000000-0000-0000-0000-${String(i).padStart(12, '0')}`,
  name: `meme-${i}`,
  imageUrl: `/api/mock-img/${i}`,
  imageWidth: w,
  imageHeight: h,
  meaning: meanings[i],
  tags: tags.slice(i % 3, (i % 3) + 4 + (i % 3)),
  imageText: null,
  sourceType: i % 2 ? 'PTT' : 'IMGFLIP',
  sourceUrl: null,
  attribution: null,
}));

const svg = (i) => {
  const [w, h] = dims[i];
  const c = palette[i % palette.length];
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}"><rect width="100%" height="100%" fill="${c}"/><circle cx="${w / 2}" cy="${h / 2}" r="${Math.min(w, h) / 4}" fill="#fff" opacity=".8"/><text x="50%" y="${h - 30}" font-size="36" text-anchor="middle" font-family="sans-serif" fill="#222">MEME ${i + 1}</text></svg>`;
};

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
const fakeToken = (username) =>
  `${b64({ alg: 'none' })}.${b64({ sub: 'mock-user', username, roles: ['ADMIN'], exp: Math.floor(Date.now() / 1000) + 86400 })}.mock`;

const readBody = (req) =>
  new Promise((resolve) => {
    let data = '';
    req.on('data', (chunk) => (data += chunk));
    req.on('end', () => resolve(data));
  });

const json = (res, body) => {
  res.writeHead(200, { 'content-type': 'application/json' });
  res.end(JSON.stringify(body));
};

http
  .createServer(async (req, res) => {
    const url = new URL(req.url, 'http://x');
    const p = url.pathname;
    if (p.startsWith('/api/mock-img/')) {
      res.writeHead(200, { 'content-type': 'image/svg+xml' });
      return res.end(svg(Number(p.split('/').pop())));
    }
    if (p === '/api/auth/login' && req.method === 'POST') {
      let username = 'demo';
      try {
        username = JSON.parse(await readBody(req)).username || username;
      } catch {
        // keep the default name
      }
      return json(res, { token: fakeToken(username), expiresAt: new Date(Date.now() + 86400000).toISOString() });
    }
    if (p === '/api/library/random') return json(res, items);
    if (p === '/api/library/hot-searches')
      return json(res, ['聽不懂', '黑人問號', '尷尬微笑', '準時下班', '被加班'].map((term) => ({ term, searches: 9 })));
    if (p === '/api/templates/search') return json(res, items.slice(0, 7).map((it, i) => ({ ...it, score: 0.9 - i * 0.05, slots: [] })));
    if (p === '/api/favorites') return json(res, []);
    res.writeHead(404);
    res.end();
  })
  .listen(8080, () => console.log('Mock API on http://localhost:8080 - now run `npm run dev` and sign in with anything'));
