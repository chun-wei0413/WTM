#!/usr/bin/env python3
"""Compares ways of ranking the library for the same queries, so a difference can only come from the way of ranking.

    python scripts/eval-compare.py eval/library-queries.json
    python scripts/eval-compare.py eval/library-queries.json eval/library-queries-blind.json --json eval/results/compare.json

It reproduces the application's own search (the query embedded with bge-m3 through Ollama, the same SQL, 20 candidates per
ranking) and then ranks the SAME candidates in different ways:

  dense          semantic search alone (cosine similarity of the embeddings in pgvector)
  bm25           keyword search with BM25 over ASCII words and Chinese two-character pieces
  rrf+bm25       dense + bm25, reciprocal rank fusion
  cc+bm25        dense + bm25, convex combination of min-max normalised scores (alpha * dense + (1 - alpha) * bm25);
                 alpha = 0.7 is what the application does now

Per query (binary relevance: a result is right when its name is one of the names listed for the query):
  S@1, S@3, S@10  the share of queries with a right answer in the first 1, 3 or 10 places
  MRR@10          mean reciprocal rank of the first right answer (0 when it is not in the first 10)
  nDCG@10         the ideal ranking has min(number of right answers, 10) of them first

Uncertainty: 95% percentile bootstrap over queries. Differences between two systems on the same queries: paired bootstrap
interval and a paired randomisation (sign-flip) test. A setting chosen on one half of the queries is scored on the other half
(200 random splits), so it is not chosen and scored on the same queries.

Needs the two containers and Ollama running; the application does not have to run. Standard library only.
Environment: PG_CONTAINER (wtm-postgres), DB_USERNAME (wtm), OLLAMA_URL (http://localhost:11434), EMBED_MODEL (bge-m3).
"""
import argparse, collections, json, math, os, random, re, subprocess, sys, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PG = os.environ.get("PG_CONTAINER", "wtm-postgres")
DB_USER = os.environ.get("DB_USERNAME", "wtm")
OLLAMA = os.environ.get("OLLAMA_URL", "http://localhost:11434")
MODEL = os.environ.get("EMBED_MODEL", "bge-m3")
CANDIDATES = 20          # per ranking, as SearchTemplatesHandler asks for with a limit of 10
LIMIT = 10
CACHE = os.path.join(ROOT, "eval", "results", "embedding-cache.json")
rng = random.Random(20261014)


# ------------------------------------------------------------------ data access
def psql(sql):
    out = subprocess.run(["docker", "exec", "-i", PG, "psql", "-U", DB_USER, "-d", "wtm", "-t", "-A", "-F", "\t"],
                         input=sql, capture_output=True, text=True, encoding="utf-8")
    if out.returncode != 0:
        sys.exit("The database could not be read: " + out.stderr.strip())
    return [line.split("\t") for line in out.stdout.splitlines() if line.strip()]


_cache = None
def embed(text):
    global _cache
    if _cache is None:
        _cache = json.load(open(CACHE, encoding="utf-8")) if os.path.exists(CACHE) else {}
    if text not in _cache:
        req = urllib.request.Request(OLLAMA + "/api/embed", json.dumps({"model": MODEL, "input": text}).encode(),
                                     {"Content-Type": "application/json"})
        _cache[text] = json.load(urllib.request.urlopen(req, timeout=300))["embeddings"][0]
    return _cache[text]


def save_cache():
    os.makedirs(os.path.dirname(CACHE), exist_ok=True)
    json.dump(_cache, open(CACHE, "w", encoding="utf-8"))


def candidates(query):
    vec = "[" + ",".join(f"{x:.7g}" for x in embed(query)) + "]"
    dense = psql(f"""SELECT t.id, t.name, 1 - (s.embedding <=> '{vec}'::vector) FROM template_search s
                     JOIN meme_template t ON t.id = s.template_id WHERE t.status = 'APPROVED'
                     ORDER BY s.embedding <=> '{vec}'::vector LIMIT {CANDIDATES}""")
    return [(i, n, float(s)) for i, n, s in dense]


# ------------------------------------------------------------------ BM25 over words and two-character pieces
def tokens(text):
    text = text.lower()
    out = re.findall(r"[a-z0-9']+", text)
    for run in re.findall(r"[一-鿿]+", text):
        out += [run[k:k + 2] for k in range(len(run) - 1)] or [run]
    return out


class Bm25:
    def __init__(self, k1=1.2, b=0.75):
        rows = psql("""SELECT t.id, t.name, regexp_replace(s.search_text, E'[\\n\\t\\r]+', ' ', 'g') FROM template_search s
                       JOIN meme_template t ON t.id = s.template_id WHERE t.status = 'APPROVED'""")
        self.docs = [(i, n) for i, n, _ in rows]
        self.tf = [collections.Counter(tokens(t)) for _, _, t in rows]
        self.len = [sum(c.values()) for c in self.tf]
        self.avg = sum(self.len) / len(rows)
        self.df = collections.Counter(t for c in self.tf for t in c)
        self.k1, self.b = k1, b

    def rank(self, query):
        q, n, scores = set(tokens(query)), len(self.docs), []
        for j, c in enumerate(self.tf):
            s = 0.0
            for t in q:
                if t in c:
                    idf = math.log(1 + (n - self.df[t] + 0.5) / (self.df[t] + 0.5))
                    s += idf * c[t] * (self.k1 + 1) / (c[t] + self.k1 * (1 - self.b + self.b * self.len[j] / self.avg))
            scores.append(s)
        top = sorted(range(n), key=lambda j: -scores[j])[:CANDIDATES]
        return [(self.docs[j][0], self.docs[j][1], scores[j]) for j in top if scores[j] > 0]


# ------------------------------------------------------------------ fusion
def rrf(lists, k=60):
    score, name = collections.defaultdict(float), {}
    for lst in lists:
        for rank, (i, n, _) in enumerate(lst, 1):
            score[i] += 1.0 / (k + rank); name[i] = n
    return [(i, name[i], score[i]) for i in sorted(score, key=lambda i: -score[i])]


def minmax(lst):
    if not lst:
        return {}
    lo, hi = min(s for _, _, s in lst), max(s for _, _, s in lst)
    return {i: (s - lo) / (hi - lo) if hi > lo else 1.0 for i, _, s in lst}


def convex(a, b, alpha):
    na, nb = minmax(a), minmax(b)
    name = {i: n for i, n, _ in a + b}
    score = {i: alpha * na.get(i, 0.0) + (1 - alpha) * nb.get(i, 0.0) for i in name}
    return [(i, name[i], score[i]) for i in sorted(score, key=lambda i: -score[i])]


ALPHAS = tuple(round(k / 10, 1) for k in range(0, 11))   # 0.0 is the keyword ranking alone, 1.0 the semantic one alone
SYSTEMS = {
    "dense":           lambda d, b: d,
    "bm25":            lambda d, b: b,
    "rrf+bm25":        lambda d, b: rrf([d, b]),
}
for _a in ALPHAS:
    SYSTEMS[f"cc+bm25 a={_a}"] = (lambda a: lambda d, b: convex(d, b, a))(_a)


# ------------------------------------------------------------------ metrics and statistics
def judge(ranking, expect):
    wanted = {e.lower() for e in expect}
    hits = [1 if n.lower() in wanted else 0 for _, n, _ in ranking[:LIMIT]]
    first = next((r for r, h in enumerate(hits, 1) if h), None)
    dcg = sum(h / math.log2(r + 1) for r, h in enumerate(hits, 1))
    ideal = sum(1 / math.log2(r + 1) for r in range(1, min(len(wanted), LIMIT) + 1))
    return {"s1": float(first == 1), "s3": float(first is not None and first <= 3), "s10": float(first is not None),
            "rr": 1 / first if first else 0.0, "ndcg": dcg / ideal if ideal else 0.0}


METRICS = [("s1", "S@1"), ("s3", "S@3"), ("s10", "S@10"), ("rr", "MRR@10"), ("ndcg", "nDCG@10")]
mean = lambda xs: sum(xs) / len(xs) if xs else float("nan")


def boot_ci(values, B):
    n = len(values)
    means = sorted(mean([values[rng.randrange(n)] for _ in range(n)]) for _ in range(B))
    return means[int(0.025 * B)], means[int(0.975 * B)]


def paired(a, b, B):
    d = [y - x for x, y in zip(a, b)]; n = len(d); obs = mean(d)
    boots = sorted(mean([d[rng.randrange(n)] for _ in range(n)]) for _ in range(B))
    flips = sum(1 for _ in range(B) if abs(mean([x if rng.random() < 0.5 else -x for x in d])) >= abs(obs) - 1e-12)
    return obs, boots[int(0.025 * B)], boots[int(0.975 * B)], (flips + 1) / (B + 1)


# ------------------------------------------------------------------ main
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("queries", nargs="+", help="query files, as eval/library-queries.json")
    ap.add_argument("--boot", type=int, default=4000, help="bootstrap resamples (default 4000)")
    ap.add_argument("--no-tune", action="store_true", help="skip the held-out tuning of the convex combination")
    ap.add_argument("--json", help="write the per-query results and the summary here")
    args = ap.parse_args()

    Q = []
    for f in args.queries:
        Q += [q for q in json.load(open(f, encoding="utf-8"))["queries"] if q["kind"] != "negative" and q["expect"]]
    size = psql("SELECT count(*) FROM template_search s JOIN meme_template t ON t.id = s.template_id WHERE t.status = 'APPROVED'")[0][0]
    print(f"{len(Q)} queries with a right answer, {size} memes in the library, embeddings by {MODEL}\n")
    bm25 = Bm25()
    cand = {}
    for k, q in enumerate(Q, 1):
        cand[q["q"]] = (candidates(q["q"]), bm25.rank(q["q"]))
        if k % 50 == 0:
            save_cache(); print(f"  ... {k}/{len(Q)} queries searched", file=sys.stderr)
    save_cache()
    per = {name: [judge(fn(*cand[q["q"]]), q["expect"]) for q in Q] for name, fn in SYSTEMS.items()}

    print("Overall (95% bootstrap interval)")
    print(f"{'system':16}" + "".join(f"{label:>22}" for _, label in METRICS))
    summary = {}
    for name in per:
        row = {}
        for m, _ in METRICS:
            v = [r[m] for r in per[name]]; lo, hi = boot_ci(v, args.boot); row[m] = (mean(v), lo, hi)
        summary[name] = row
        print(f"{name:16}" + "".join(f"{row[m][0]:.3f} [{row[m][1]:.2f},{row[m][2]:.2f}]".rjust(22) for m, _ in METRICS))

    print("\nDifference from dense on the same queries (paired; 95% interval; randomisation test p)")
    diffs = {}
    for name in ("rrf+bm25", "cc+bm25 a=0.7", "bm25"):
        for m, label in METRICS[:4]:
            d, lo, hi, p = paired([r[m] for r in per["dense"]], [r[m] for r in per[name]], args.boot)
            diffs[(name, m)] = (d, lo, hi, p)
            if m in ("s1", "s10", "rr"):
                print(f"  {name:14} {label:7} {d:+.3f}  [{lo:+.3f}, {hi:+.3f}]  p={p:.3f}")

    print("\nBy kind of query: S@1 / S@10 / MRR@10 (dense | bm25 | rrf+bm25)")
    by = collections.defaultdict(list)
    for i, q in enumerate(Q): by[q["kind"]].append(i)
    for kind, idx in by.items():
        cells = []
        for name in ("dense", "bm25", "rrf+bm25"):
            cells.append("/".join(f"{mean([per[name][i][m] for i in idx]):.2f}" for m in ("s1", "s10", "rr")))
        print(f"  {kind:10}{len(idx):>4}   " + " | ".join(f"{c:>14}" for c in cells))

    ranks = collections.Counter()
    for q in Q:
        w = {e.lower() for e in q["expect"]}
        r = next((k for k, (_, n, _) in enumerate(cand[q["q"]][0], 1) if n.lower() in w), None)
        ranks["1" if r == 1 else "2-3" if r and r <= 3 else "4-10" if r and r <= 10 else "11-20" if r else ">20"] += 1
    print("\nWhere the right answer is in the dense ranking: " +
          ", ".join(f"{k} {ranks[k]} ({ranks[k] / len(Q):.0%})" for k in ("1", "2-3", "4-10", "11-20", ">20")))
    print(f"In the dense or the BM25 candidates (20 each), the share a re-ranker could reach: "
          f"{sum(1 for q in Q if any(n.lower() in {e.lower() for e in q['expect']} for _, n, _ in cand[q['q']][0] + cand[q['q']][1])) / len(Q):.0%}")

    tuned = None
    if not args.no_tune:
        names = [n for n in per if n.startswith("cc+bm25")]
        idx, chosen, gain = list(range(len(Q))), collections.Counter(), collections.defaultdict(list)
        for _ in range(200):
            rng.shuffle(idx); dev, test = idx[:len(idx) // 2], idx[len(idx) // 2:]
            best = max(names, key=lambda n: mean([per[n][i]["rr"] for i in dev]))
            chosen[best] += 1
            for m, _ in METRICS[:5]:
                gain[m].append(mean([per[best][i][m] for i in test]) - mean([per["dense"][i][m] for i in test]))
        print("\nConvex combination dense + bm25, alpha chosen on one half of the queries, scored on the other (200 random splits)")
        print("  chosen: " + ", ".join(f"{n} x{c}" for n, c in chosen.most_common(3)))
        tuned = {}
        for m, label in METRICS:
            g = sorted(gain[m]); tuned[m] = (mean(g), g[5], g[194])
            print(f"  {label:8} change from dense: mean {mean(g):+.3f}   (2.5%-97.5% of the splits: {g[5]:+.3f} to {g[194]:+.3f})")

    if args.json:
        os.makedirs(os.path.dirname(os.path.abspath(args.json)), exist_ok=True)
        json.dump({"queries": len(Q), "library": int(size), "summary": summary, "tuned": tuned,
                   "per_query": [{"q": q["q"], "kind": q["kind"], "set": q.get("set"), "expect": q["expect"],
                                  "results": {n: per[n][i] for n in per}} for i, q in enumerate(Q)]},
                  open(args.json, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
        print(f"\nSaved to {args.json}")


if __name__ == "__main__":
    main()
