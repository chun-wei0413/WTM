# Running WTM on a Mac, and moving your library there

This is for a MacBook (Apple silicon or Intel) that has the code but not the library: the memes, their descriptions, the
accounts and the search index live on the computer that has been running the site. The steps below move all of it.
The same two scripts are also the backup and restore of the site.

## What moves, and what does not

| Moves (in the export folder) | Does not move |
|---|---|
| The database: memes, descriptions and tags, the search index with its vectors, accounts, favourites, reports, search log | `.env`, which holds secrets and is made again on each computer |
| Every picture, as a plain file | The Ollama models (downloaded again, see step 2) |
| A `MANIFEST.txt` with counts and a checksum | `node_modules`, build output, `target/` |

**Why not just copy the data folder.** The database files of one kind of processor cannot be read by another: a folder written by
Intel or AMD PostgreSQL does not open on Apple silicon. And the picture store keeps its files in its own layout. A database
dump and plain picture files work on every computer, so that is what is exported.

## 1. On the old computer: export

The two containers must be running (`docker compose up -d`). The application can stay on.

```bash
bash scripts/export-data.sh
```

It writes `<data folder>/backups/wtm-export-<date>/` (about 100 MB for 500 memes) and checks that every meme has its picture.
If it prints a warning, do not use that export. **Run it again just before you move**, so that the last few favourites and
memes come along.

Copy the whole `wtm-export-<date>` folder to the Mac: AirDrop, an external drive or a cloud drive. Copy it as one folder.

## 2. On the Mac: install the tools

You need Docker, Java 21, Maven, Node (20 or newer) and Git. With [Homebrew](https://brew.sh):

```bash
brew install --cask docker-desktop temurin@21      # Docker Desktop, Java 21
brew install maven node git ollama                 # Node 20 or newer
```

Maven brings its own, newer Java along, so tell it to use 21 (add the line to `~/.zshrc` to keep it):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

Open Docker Desktop once and wait until it says the engine is running. Then the models (about 7 GB in all; the vision model
wants 16 GB of memory to be comfortable):

```bash
ollama serve &                              # or open the Ollama app
ollama pull bge-m3                          # search by meaning
ollama pull qwen2.5vl:7b                    # looks at pictures
```

The container images (`pgvector/pgvector:pg16`, `rustfs/rustfs:1.0.0`, `amazon/aws-cli`) all exist for Apple silicon.

## 3. On the Mac: get the code and make a fresh setup

```bash
git clone https://github.com/chun-wei0413/WTM.git && cd WTM
bash scripts/init-env.sh                    # .env with new random secrets; data under ~/wtm-data
docker compose up -d
```

`init-env.sh` never overwrites an existing `.env` and never prints the secrets. To keep the data on another disk,
give the folder: `bash scripts/init-env.sh /Volumes/Big/wtm-data`.

## 4. On the Mac: import the library

Before the application has ever been started on this Mac:

```bash
bash scripts/import-data.sh ~/Downloads/wtm-export-<date>
```

It checks the dump against `MANIFEST.txt`, loads the database and the pictures, and compares the counts with the export. A
database that already holds memes is not touched unless you add `--force`.

## 5. Choose the models and start

Add one line to `.env` (the three values are `mock | ollama | gemini`, see `.env.example`):

```
WTM_VISION_PROVIDER=ollama
WTM_EMBEDDING_PROVIDER=ollama
WTM_EXPLAINER_PROVIDER=ollama
```

With `gemini` you also need `GEMINI_API_KEY=...` in the Mac's `.env`; keys are never part of an export. Then:

```bash
mvn spring-boot:run                         # the application, http://localhost:8080
cd web && npm install && npm run dev        # the web page, http://localhost:5173
```

**Signing in.** The accounts came with the database, so sign in with the administrator password you used on the old computer.
The `WTM_ADMIN_PASSWORD` in the new `.env` is only used when there is no administrator yet.

## If something goes wrong

| What you see | What it means |
|---|---|
| `bad interpreter: /bin/bash^M` | The script was saved with Windows line endings. `.gitattributes` prevents it for a clone; if you copied the files by hand, run `sed -i '' 's/\r$//' scripts/*.sh` |
| `The database '...' did not become ready` | Docker Desktop is still starting. Wait a minute and run the script again |
| `This database already holds N memes` | The Mac already has a library. Use `--force` only if you mean to replace it |
| `db.dump does not match MANIFEST.txt` | The copy is damaged. Copy the folder again |
| The numbers at the end do not match | Do not use the import. Export again on the old computer and compare `MANIFEST.txt` |
| Searching finds nothing but the memes are there | The search index needs the embedding model: check `ollama list` shows `bge-m3` |

## As a backup

The same scripts are the backup and the restore: run `scripts/export-data.sh` now and then, and keep the folder somewhere else.
To restore, start from a fresh install as in steps 3 and 4.
