#!/usr/bin/env python3
"""Has a language model write held-out evaluation queries for memes it is only told the NAMES of.

    python scripts/eval-writer.py --since 2026-10-19T15:00:00+00 --out eval/library-queries-new.json

The writer sees nothing but meme names: no descriptions, no earlier queries, no search results. For each batch of names it
says which memes it really knows and writes search queries a person might type for those, in the kinds the other query
files use (situation, look, nickname, entity, quote, english). The queries therefore come from the model's own knowledge of
the memes, not from the library's descriptions, and the person who chose the search weight did not write them.

Only memes added since --since are used, and memes that a query of another file already expects are left out, so the set
measures the part of the library the other sets do not cover.

Needs the database container, GEMINI_API_KEY in .env (the key is only sent to Google, never printed) and network access.
Environment: PG_CONTAINER (wtm-postgres), DB_USERNAME (wtm), WRITER_MODEL (gemini-3.5-flash-lite).
"""
import argparse, json, os, re, subprocess, sys, time, urllib.request, urllib.error

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PG = os.environ.get("PG_CONTAINER", "wtm-postgres")
DB_USER = os.environ.get("DB_USERNAME", "wtm")
MODEL = os.environ.get("WRITER_MODEL", "gemini-3.5-flash-lite")
OTHER_FILES = ("library-queries.json", "library-queries-blind.json", "library-queries-heldout.json")
KINDS = "situation, look, nickname, entity, quote, english"


def env(name):
    for line in open(os.path.join(ROOT, ".env"), encoding="utf-8"):
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    raise SystemExit(f"{name} is not in .env")


def names_since(since):
    sql = f"SELECT name FROM meme_template WHERE status = 'APPROVED' AND collected_at >= '{since}' ORDER BY name"
    out = subprocess.run(["docker", "exec", "-i", PG, "psql", "-U", DB_USER, "-d", "wtm", "-t", "-A", "-c", sql],
                         capture_output=True, text=True, encoding="utf-8").stdout
    return [l.strip() for l in out.splitlines() if l.strip()]


def covered():
    seen = set()
    for f in OTHER_FILES:
        for q in json.load(open(os.path.join(ROOT, "eval", f), encoding="utf-8"))["queries"]:
            seen.update(e.lower() for e in q["expect"])
    return seen


def ask(key, names):
    prompt = (
        "Below is a list of meme names from a library of reaction images and templates (Chinese and English names). "
        "You are only given the names. For every meme that you REALLY know (you know what the picture shows and where the "
        "meme comes from), write 1 or 2 search queries a person might type to find it. Do not write queries for memes you "
        "do not know, and do not guess. Queries must not just repeat the name. Mix these kinds: " + KINDS + ". "
        "'situation' = a life situation where the meme fits; 'look' = what the picture shows; 'nickname' = another name people use; "
        "'entity' = the work or character it comes from; 'quote' = a remembered line; 'english' = an English description. "
        "Write queries in the language a Taiwanese user would use (Traditional Chinese, English for 'english' and 'quote' when the original is English). "
        'Answer with JSON only: [{"name": "<exact name from the list>", "q": "<query>", "kind": "<kind>"}].\n\nNames:\n'
        + "\n".join("- " + n for n in names))
    body = json.dumps({"contents": [{"parts": [{"text": prompt}]}],
                       "generationConfig": {"temperature": 0.4, "responseMimeType": "application/json"}}).encode()
    req = urllib.request.Request(f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent?key={key}",
                                 body, {"Content-Type": "application/json"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                data = json.loads(r.read())
            text = data["candidates"][0]["content"]["parts"][0]["text"]
            return json.loads(re.sub(r"^```(?:json)?|```$", "", text.strip()))
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(20 * (attempt + 1)); continue
            raise
        except (KeyError, ValueError):
            time.sleep(3)
    return []


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--since", required=True, help="only memes collected at or after this time, e.g. 2026-10-19T15:00:00+00")
    ap.add_argument("--out", required=True)
    ap.add_argument("--batch", type=int, default=60)
    args = ap.parse_args()
    key = env("GEMINI_API_KEY")
    skip = covered()
    names = [n for n in dict.fromkeys(names_since(args.since)) if n.lower() not in skip]
    print(f"{len(names)} memes not covered by another query file", file=sys.stderr)
    library = set(names)
    queries, knew = [], set()
    for i in range(0, len(names), args.batch):
        for item in ask(key, names[i:i + args.batch]):
            n, q, k = item.get("name"), (item.get("q") or "").strip(), item.get("kind")
            if n in library and q and k in KINDS.split(", ") and q.lower() != n.lower():
                queries.append({"q": q, "kind": k, "set": "v4", "expect": [n]}); knew.add(n)
        print(f"  ... {min(i + args.batch, len(names))}/{len(names)} names, {len(queries)} queries", file=sys.stderr)
    about = (f"Held-out queries written on {time.strftime('%Y-%m-%d')} by {MODEL}, which saw only the NAMES of {len(names)} memes added to the "
             f"library after the 1,305-meme stage (no descriptions, no earlier queries, no search results). It wrote queries for the "
             f"{len(knew)} it said it knew. They were never used to choose any setting. Any listed name counts as a hit. "
             "Not checked by a person: a query can fit more than one meme (see docs/EVALUATION.zh-TW.md).")
    json.dump({"about": about, "queries": queries}, open(args.out, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print(f"{len(queries)} queries for {len(knew)} memes written to {args.out}")


if __name__ == "__main__":
    main()
