// Measures how well the search finds the memes of the REAL library for the queries in eval/library-queries.json.
//
//   node scripts/eval-search.mjs [--limit 10] [--queries eval/library-queries.json] [--out eval/results/latest.json]
//
// Needs the application running (WTM_BASE, default http://localhost:8080) and the administrator from .env
// (WTM_ADMIN_USERNAME / WTM_ADMIN_PASSWORD). Its searches are sent with record=false, so they do not become
// "popular searches".
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const limit = Number(option('--limit', '10'));
const out = option('--out', null);
const queriesPath = option('--queries', 'eval/library-queries.json');
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

const { queries } = JSON.parse(readFileSync(resolve(root, queriesPath), 'utf8'));
const lower = (text) => text.toLowerCase();
const results = [];

for (const item of queries) {
  const started = performance.now();
  const response = await fetch(`${base}/api/templates/search?q=${encodeURIComponent(item.q)}&limit=${limit}&record=false`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const milliseconds = performance.now() - started;
  if (!response.ok) throw new Error(`Search failed for "${item.q}": ${response.status}`);
  const answer = await response.json();
  const found = answer.map((r) => r.name);
  const wanted = item.expect.map(lower);
  const index = found.findIndex((name) => wanted.includes(lower(name)));
  results.push({
    ...item,
    rank: index < 0 ? null : index + 1,
    top: found.slice(0, 3),
    hits: answer.map((r) => ({ name: r.name, source: r.sourceType, score: r.score })),
    milliseconds,
  });
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

// Queries of kind "negative" have no right answer: they are kept out of the hit rates and looked at on their own.
const negatives = results.filter((r) => r.kind === 'negative');
const asked = results.filter((r) => r.kind !== 'negative');
const kinds = [...new Set(asked.map((r) => r.kind))];
const sets = [...new Set(asked.map((r) => r.set).filter(Boolean))];
const row = (label, list) => {
  const s = summary(list);
  console.log(`${label.padEnd(10)} ${String(s.n).padStart(3)}   ${percent(s.top1)}   ${percent(s.top3)}   ${percent(s.top10)}   ${s.mrr.toFixed(2)}`);
};
console.log(`Library search evaluation: ${results.length} queries against ${base} (${negatives.length} of them have no right answer)\n`);
console.log('kind        n   first  top-3  top-' + limit + '   MRR');
for (const kind of [...kinds, 'ALL']) row(kind, kind === 'ALL' ? asked : asked.filter((r) => r.kind === kind));
if (sets.length > 1) {
  console.log('\nby query set (v1 = the first 46, measured before the library grew)');
  for (const set of sets) row(set, asked.filter((r) => r.set === set));
}

// Pictures that are not meme templates (Wikimedia, PTT) must not push the answer out of the first places.
const noisy = (hit) => hit.source === 'WIKIMEDIA' || hit.source === 'PTT';
const slots = asked.flatMap((r) => r.hits.slice(0, 3));
const withNoise = asked.filter((r) => r.hits.slice(0, 3).some(noisy));
console.log(`\nWikimedia/PTT pictures in the first 3 places: ${slots.filter(noisy).length} of ${slots.length} places, in ${withNoise.length} of ${asked.length} answers`);

if (negatives.length > 0) {
  const median = (values) => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)];
  const rightFirst = asked.filter((r) => r.rank === 1).map((r) => r.hits[0].score);
  const wrongFirst = asked.filter((r) => r.rank !== 1 && r.hits.length > 0).map((r) => r.hits[0].score);
  const none = negatives.map((r) => (r.hits[0] ? r.hits[0].score : 0));
  console.log('\nscore of the first result (0 to 1; the search is a weighted sum of the meaning and the keyword ranking, each scaled to 0..1,');
  console.log('so the first result scores at least the weight of the meaning ranking, 0.7 by default, when only that ranking found it)');
  console.log(`  right answer first : median ${median(rightFirst).toFixed(3)} (${rightFirst.length})`);
  console.log(`  wrong answer first : median ${median(wrongFirst).toFixed(3)} (${wrongFirst.length})`);
  console.log(`  nothing fits       : median ${median(none).toFixed(3)} (${negatives.length})`);
  for (const t of [0.75, 0.8, 0.9]) {
    const dropped = none.filter((x) => x < t).length / none.length;
    const lost = rightFirst.filter((x) => x < t).length / rightFirst.length;
    console.log(`  a cut-off at ${t}: would drop ${percent(dropped)} of the "nothing fits" answers and lose ${percent(lost)} of the right first places`);
  }
  console.log('\nwhat the search returns when nothing fits:');
  for (const r of negatives) console.log(`- ${r.q}  ->  ${r.top.join(' | ')}`);
}

const times = results.map((r) => r.milliseconds).sort((a, b) => a - b);
console.log(`\nlatency: median ${Math.round(times[Math.floor(times.length / 2)])} ms, slowest ${Math.round(times[times.length - 1])} ms`);

const misses = asked.filter((r) => r.rank === null || r.rank > 3);
console.log(`\nNot in the top 3 (${misses.length}):`);
for (const r of misses) {
  console.log(`- [${r.kind}] ${r.q}\n    wanted: ${r.expect.join(' | ')}   found at: ${r.rank ?? `not in top ${limit}`}\n    got: ${r.top.join(' | ')}`);
}

if (out) {
  mkdirSync(dirname(resolve(root, out)), { recursive: true });
  writeFileSync(resolve(root, out), JSON.stringify({ at: new Date().toISOString(), limit, summary: summary(asked), results }, null, 2));
  console.log(`\nSaved to ${out}`);
}
