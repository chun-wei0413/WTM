#!/usr/bin/env python3
"""Shows why the weight of the semantic ranking is what it is: the whole curve, on queries that did not pick it.

    python scripts/eval-compare.py eval/library-queries.json         --json eval/results/compare-author.json
    python scripts/eval-compare.py eval/library-queries-blind.json   --json eval/results/compare-blind.json
    python scripts/eval-compare.py eval/library-queries-heldout.json --json eval/results/compare-heldout.json
    python scripts/eval-alpha.py

It reads the per-query results those runs saved (no database, no model) and prints, for every weight alpha from 0 (keyword
ranking alone) to 1 (semantic ranking alone):

  1. MRR@10, S@1 and S@10 per set of queries with a 95% bootstrap interval;
  2. the alpha that is best on the author's queries (the set it was chosen on) and how that choice scores on the other two sets;
  3. which alphas cannot be told apart from alpha = 0.7: a paired comparison on all queries together, and per set.

A, the author's queries; B, written by a local vision model from the pictures alone; C, written on 2026-10-17 by a separate
model that saw only the names of memes no query in A or B expects, and never used to choose anything.
"""
import json
import os
import random
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "eval", "results")
SETS = [("A (author)", "compare-author.json"), ("B (blind)", "compare-blind.json"), ("C (held out)", "compare-heldout.json")]
BOOT = 4000
rng = random.Random(20261017)
mean = lambda xs: sum(xs) / len(xs)


def load():
    data = {}
    for label, f in SETS:
        d = json.load(open(os.path.join(RES, f), encoding="utf-8"))
        data[label] = d["per_query"]
    return data


def system(alpha):
    return f"cc+bm25 a={alpha}"


def ci(values):
    n = len(values)
    means = sorted(mean([values[rng.randrange(n)] for _ in range(n)]) for _ in range(BOOT))
    return means[int(0.025 * BOOT)], means[int(0.975 * BOOT)]


def paired(a, b):
    d = [y - x for x, y in zip(a, b)]
    n, obs = len(d), mean(d)
    boots = sorted(mean([d[rng.randrange(n)] for _ in range(n)]) for _ in range(BOOT))
    flips = sum(1 for _ in range(BOOT) if abs(mean([x if rng.random() < 0.5 else -x for x in d])) >= abs(obs) - 1e-12)
    return obs, boots[int(0.025 * BOOT)], boots[int(0.975 * BOOT)], (flips + 1) / (BOOT + 1)


def col(rows, name, m):
    return [r["results"][name][m] for r in rows]


def main():
    data = load()
    alphas = [round(k / 10, 1) for k in range(11)]
    print("1. The curve (MRR@10 with a 95% interval; S@1; S@10)\n")
    for label, rows in data.items():
        print(f"{label}: {len(rows)} queries")
        print(f"  {'alpha':>5} {'MRR@10':>22} {'S@1':>7} {'S@10':>7}")
        for a in alphas:
            rr = col(rows, system(a), "rr")
            lo, hi = ci(rr)
            print(f"  {a:>5} {mean(rr):.3f} [{lo:.3f}, {hi:.3f}]".ljust(32) +
                  f"{mean(col(rows, system(a), 's1')):>7.3f} {mean(col(rows, system(a), 's10')):>7.3f}")
        print()

    a_rows = data["A (author)"]
    best = max(alphas, key=lambda a: mean(col(a_rows, system(a), "rr")))
    print(f"2. Chosen on A: the best alpha by MRR@10 on the author's queries is {best}")
    for label, rows in data.items():
        if label.startswith("A"):
            continue
        scored = {a: mean(col(rows, system(a), "rr")) for a in alphas}
        top = max(scored, key=scored.get)
        print(f"   scored on {label}: MRR@10 at {best} = {scored[best]:.3f}; the best on {label} is alpha {top} with {scored[top]:.3f}; "
              f"alpha 1.0 (semantic alone) {scored[1.0]:.3f}; alpha 0.0 (keyword alone) {scored[0.0]:.3f}")
    print()

    print("3. Compared with alpha = 0.7 on the same queries (paired MRR@10 difference, 95% interval, randomisation p)")
    pooled = [r for rows in data.values() for r in rows]
    for label, rows in [("all 3 sets", pooled)] + list(data.items()):
        print(f"  {label}: {len(rows)} queries")
        base = col(rows, system(0.7), "rr")
        for a in alphas:
            if a == 0.7:
                continue
            d, lo, hi, p = paired(base, col(rows, system(a), "rr"))
            verdict = "" if lo <= 0 <= hi else ("  <- worse" if d < 0 else "  <- better")
            print(f"    alpha {a}: {d:+.3f}  [{lo:+.3f}, {hi:+.3f}]  p={p:.3f}{verdict}")
    print()
    print("4. Alpha = 0.7 against the semantic ranking alone (alpha = 1.0)")
    for label, rows in [("all 3 sets", pooled)] + list(data.items()):
        for m, name in (("rr", "MRR@10"), ("s10", "S@10"), ("s1", "S@1")):
            d, lo, hi, p = paired(col(rows, system(1.0), m), col(rows, system(0.7), m))
            print(f"  {label:13} {name:7} {d:+.3f}  [{lo:+.3f}, {hi:+.3f}]  p={p:.3f}")


if __name__ == "__main__":
    sys.exit(main())
