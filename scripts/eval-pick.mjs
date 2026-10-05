// Measures whether the model's pick beats the closest search result, on the queries of eval/library-queries.json.
//
//   node scripts/eval-pick.mjs [--only situation] [--out eval/results/pick-baseline.json]
//
// Needs the application running with the real models (WTM_EMBEDDING_PROVIDER=ollama and WTM_PICKER_PROVIDER=ollama,
// otherwise the "model" is a mock that always takes the first candidate), and the administrator from .env
// (WTM_ADMIN_USERNAME / WTM_ADMIN_PASSWORD), like scripts/eval-search.mjs. Its searches are sent with record=false.
//
// For every query it asks search for the eight closest memes (the list the model is given), then asks the model to pick,
// and compares: is the closest one right, is the right one among the eight at all, and is the model's pick right.
// A pick is "explained" when the model answered; otherwise the application offered the closest result without a reason,
// and that counts as the closest result, not as a pick.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const option = (name, fallback) => (args.includes(name) ? args[args.indexOf(name) + 1] : fallback);
const out = option('--out', null);
const only = option('--only', null);
const base = process.env.WTM_BASE ?? 'http://localhost:8080';
/** How many memes the application gives the model to choose from (PickMemeHandler.CANDIDATES). */
const CANDIDATES = 8;
/** Ten picks a minute are allowed per person, so wait at least this long between two. */
const MIN_GAP_MS = 6500;

function envFile() {
  const values = {};
  for (const line of readFileSync(resolve(root, '.env'), 'utf8').split(/\r?\n/)) {
    const match = /^([A-Z0-9_]+)=(.*)$/.exec(line);
    if (match) values[match[1]] = match[2];
  }
  return values;
}

const sleep = (ms) => new Promise((done) => setTimeout(done, ms));
const env = { ...envFile(), ...process.env };
const login = await fetch(`${base}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: env.WTM_ADMIN_USERNAME ?? 'admin', password: env.WTM_ADMIN_PASSWORD }),
});
if (!login.ok) throw new Error(`Could not sign in: ${login.status}`);
const { token } = await login.json();
const auth = { Authorization: `Bearer ${token}` };

const { queries: all } = JSON.parse(readFileSync(resolve(root, 'eval/library-queries.json'), 'utf8'));
const queries = only ? all.filter((q) => q.kind === only) : all;
const lower = (text) => text.toLowerCase();

async function pickFor(situation) {
  for (let attempt = 0; attempt < 4; attempt++) {
    const started = performance.now();
    const response = await fetch(`${base}/api/templates/pick`, {
      method: 'POST',
      headers: { ...auth, 'Content-Type': 'application/json' },
      body: JSON.stringify({ situation }),
    });
    const milliseconds = performance.now() - started;
    if (response.status === 429) {
      await sleep((Number(response.headers.get('Retry-After')) || 60) * 1000);
      continue;
    }
    if (!response.ok) throw new Error(`Pick failed for "${situation}": ${response.status}`);
    return { body: await response.json(), milliseconds };
  }
  throw new Error(`Pick kept being refused for "${situation}"`);
}

const results = [];
let lastPick = 0;
for (const [i, item] of queries.entries()) {
  const wanted = item.expect.map(lower);
  const searched = await fetch(
    `${base}/api/templates/search?q=${encodeURIComponent(item.q)}&limit=${CANDIDATES}&record=false`,
    { headers: auth },
  );
  if (!searched.ok) throw new Error(`Search failed for "${item.q}": ${searched.status}`);
  const found = (await searched.json()).map((r) => r.name);
  const index = found.findIndex((name) => wanted.includes(lower(name)));

  await sleep(Math.max(0, MIN_GAP_MS - (performance.now() - lastPick)));
  lastPick = performance.now();
  const { body, milliseconds } = await pickFor(item.q);
  const chosen = body.chosen?.name ?? null;
  const explained = body.reason !== null && body.reason !== undefined;
  results.push({
    ...item,
    rank: index < 0 ? null : index + 1,
    closest: found[0] ?? null,
    chosen,
    explained,
    reason: body.reason ?? null,
    pickRight: chosen !== null && wanted.includes(lower(chosen)),
    milliseconds,
  });
  process.stderr.write(`\r${i + 1}/${queries.length}`);
}
process.stderr.write('\n');

const share = (list, test) => (list.length === 0 ? 0 : list.filter(test).length / list.length);
const percent = (x) => `${Math.round(x * 100)}%`.padStart(4);
const summary = (list) => ({
  n: list.length,
  closestRight: share(list, (r) => r.rank === 1),
  rightIsAmongEight: share(list, (r) => r.rank !== null),
  pickRight: share(list, (r) => r.pickRight),
  explained: share(list, (r) => r.explained),
  /** The closest was wrong and the pick is right. */
  fixed: list.filter((r) => r.rank !== 1 && r.pickRight).length,
  /** The closest was right and the pick is wrong. */
  broke: list.filter((r) => r.rank === 1 && !r.pickRight).length,
});

const kinds = [...new Set(results.map((r) => r.kind))];
console.log(`Pick evaluation: ${results.length} queries against ${base}, ${CANDIDATES} candidates each\n`);
console.log('kind        n  closest  in-8   pick  answered  fixed  broke');
for (const kind of [...kinds, 'ALL']) {
  const s = summary(kind === 'ALL' ? results : results.filter((r) => r.kind === kind));
  console.log(
    `${kind.padEnd(10)} ${String(s.n).padStart(3)}    ${percent(s.closestRight)}   ${percent(s.rightIsAmongEight)}   ${percent(s.pickRight)}      ${percent(s.explained)}   ${String(s.fixed).padStart(3)}    ${String(s.broke).padStart(3)}`,
  );
}
const times = results.map((r) => r.milliseconds).sort((a, b) => a - b);
console.log(
  `\npick latency: median ${Math.round(times[Math.floor(times.length / 2)])} ms, slowest ${Math.round(times[times.length - 1])} ms`,
);

const changed = results.filter((r) => (r.rank !== 1 && r.pickRight) || (r.rank === 1 && !r.pickRight));
console.log(`\nWhere the pick differs from the closest result in being right (${changed.length}):`);
for (const r of changed) {
  console.log(
    `- ${r.rank === 1 ? 'BROKE' : 'FIXED'} [${r.kind}] ${r.q}\n    wanted: ${r.expect.join(' | ')}   closest: ${r.closest}   picked: ${r.chosen}\n    reason: ${r.reason ?? '(none)'}`,
  );
}

if (out) {
  mkdirSync(dirname(resolve(root, out)), { recursive: true });
  writeFileSync(
    resolve(root, out),
    JSON.stringify({ at: new Date().toISOString(), candidates: CANDIDATES, summary: summary(results), results }, null, 2),
  );
  console.log(`\nSaved to ${out}`);
}
