// Measures how well the search finds the memes of the REAL library for the queries in eval/library-queries.json.
//
//   node scripts/eval-search.mjs [--limit 10] [--out eval/results/latest.json]
//
// Needs the application running (WTM_BASE, default http://localhost:8080) and the administrator from .env
// (WTM_ADMIN_USERNAME / WTM_ADMIN_PASSWORD). Searches are counted in the hot-search list like any other, so
// run it against a library you are happy to have that in.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const limit = Number(option('--limit', '10'));
const out = option('--out', null);
const base = process.env.WTM_BASE ?? 'http://localhost:8080';

function envFile() {
  const values = {};
  for (const line of readFileSync(resolve(root, '.env'), 'utf8').split(/\r?\n/)) {
    const match = /^([A-Z0-9_]+)=(.*)$/.exec(line);
    if (match) values[match[1]] = match[2];
  }
  return values;
}

const env = { ...envFile(), ...process.env };
const login = await fetch(`${base}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: env.WTM_ADMIN_USERNAME ?? 'admin', password: env.WTM_ADMIN_PASSWORD }),
});
if (!login.ok) throw new Error(`Could not sign in: ${login.status}`);
const { token } = await login.json();

const { queries } = JSON.parse(readFileSync(resolve(root, 'eval/library-queries.json'), 'utf8'));
const lower = (text) => text.toLowerCase();
const results = [];

for (const item of queries) {
  const started = performance.now();
  const response = await fetch(`${base}/api/templates/search?q=${encodeURIComponent(item.q)}&limit=${limit}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const milliseconds = performance.now() - started;
  if (!response.ok) throw new Error(`Search failed for "${item.q}": ${response.status}`);
  const found = (await response.json()).map((r) => r.name);
  const wanted = item.expect.map(lower);
  const index = found.findIndex((name) => wanted.includes(lower(name)));
  results.push({ ...item, rank: index < 0 ? null : index + 1, top: found.slice(0, 3), milliseconds });
}

const share = (list, test) => (list.length === 0 ? 0 : list.filter(test).length / list.length);
const percent = (x) => `${Math.round(x * 100)}%`.padStart(4);
const summary = (list) => ({
  n: list.length,
  top1: share(list, (r) => r.rank === 1),
  top3: share(list, (r) => r.rank !== null && r.rank <= 3),
  top10: share(list, (r) => r.rank !== null && r.rank <= limit),
  mrr: list.length === 0 ? 0 : list.reduce((sum, r) => sum + (r.rank ? 1 / r.rank : 0), 0) / list.length,
});

const kinds = [...new Set(results.map((r) => r.kind))];
console.log(`Library search evaluation: ${results.length} queries against ${base}\n`);
console.log('kind        n   first  top-3  top-' + limit + '   MRR');
for (const kind of [...kinds, 'ALL']) {
  const s = summary(kind === 'ALL' ? results : results.filter((r) => r.kind === kind));
  console.log(`${kind.padEnd(10)} ${String(s.n).padStart(3)}   ${percent(s.top1)}   ${percent(s.top3)}   ${percent(s.top10)}   ${s.mrr.toFixed(2)}`);
}
const times = results.map((r) => r.milliseconds).sort((a, b) => a - b);
console.log(`\nlatency: median ${Math.round(times[Math.floor(times.length / 2)])} ms, slowest ${Math.round(times[times.length - 1])} ms`);

const misses = results.filter((r) => r.rank === null || r.rank > 3);
console.log(`\nNot in the top 3 (${misses.length}):`);
for (const r of misses) {
  console.log(`- [${r.kind}] ${r.q}\n    wanted: ${r.expect.join(' | ')}   found at: ${r.rank ?? `not in top ${limit}`}\n    got: ${r.top.join(' | ')}`);
}

if (out) {
  mkdirSync(dirname(resolve(root, out)), { recursive: true });
  writeFileSync(resolve(root, out), JSON.stringify({ at: new Date().toISOString(), limit, summary: summary(results), results }, null, 2));
  console.log(`\nSaved to ${out}`);
}
