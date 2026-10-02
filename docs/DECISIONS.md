# Design decisions

This is a log of the choices that shaped wtm, why they were made, and what they cost.
Each entry says what was **measured** and what was only **reasoned**, because the two deserve
different amounts of trust.

Contents

1. [Clean Architecture with DDD only where there are rules](#1-clean-architecture-with-ddd-only-where-there-are-rules)
2. [Generation jobs are an application concern, not an aggregate](#2-generation-jobs-are-an-application-concern-not-an-aggregate)
3. [No domain events: the search index is reconciled by state](#3-no-domain-events-the-search-index-is-reconciled-by-state)
4. [PostgreSQL does the vector search, the keyword search and the job queue](#4-postgresql-does-the-vector-search-the-keyword-search-and-the-job-queue)
5. [Hybrid search, and what the numbers say about it](#5-hybrid-search-and-what-the-numbers-say-about-it)
6. [A meme snapshots its template's slots](#6-a-meme-snapshots-its-templates-slots)
7. [A template may have no slots](#7-a-template-may-have-no-slots)
8. [No query rewriting and no re-ranking (yet)](#8-no-query-rewriting-and-no-re-ranking-yet)
9. [Stateless JWT, and throttling that is honest about its limits](#9-stateless-jwt-and-throttling-that-is-honest-about-its-limits)
10. [Secrets live in `.env`, and the app refuses to start without them](#10-secrets-live-in-env-and-the-app-refuses-to-start-without-them)
11. [Object storage is addressed through the S3 API](#11-object-storage-is-addressed-through-the-s3-api)
12. [Jobs run on virtual threads from a database queue](#12-jobs-run-on-virtual-threads-from-a-database-queue)
13. [Testing strategy](#13-testing-strategy)
14. [Rendering captions with Java2D](#14-rendering-captions-with-java2d)
15. [The web client](#15-the-web-client)
16. [Favorites, hot searches and a meme maker that stays in the browser](#16-favorites-hot-searches-and-a-meme-maker-that-stays-in-the-browser)
17. [Reports: a complaint plus a second look by the vision model](#17-reports-a-complaint-plus-a-second-look-by-the-vision-model)
18. [Keeping reports from keeping the model busy, and closing the clear-cut ones without an administrator](#18-keeping-reports-from-keeping-the-model-busy-and-closing-the-clear-cut-ones-without-an-administrator)

---

## 1. Clean Architecture with DDD only where there are rules

**Context.** The system's difficulty is technical (retrieval quality, calling models, concurrency),
not in its business rules. Applying full DDD to everything would produce a lot of ceremony for
very little protection.

**Decision.**

- Clean Architecture for the whole code base: `domain` ← `application` ← `adapter`, with
  `config` wiring them together. Every external system (language and embedding models, object
  storage, the database, password hashing, token signing) sits behind a port in
  `application.port.out`.
- Tactical DDD only for the two places that really have invariants:
  - `MemeTemplate`: slots must lie inside the image, only a complete profile can be approved,
    a retired template is frozen, and the version moves when an approved template's layout changes.
  - `Meme`: every required slot has a caption, no caption exceeds its slot, captions only go into
    slots that existed, the image is attached once, and only a meme with an image can be kept.
- Everything else (search, queueing, rendering) is plain application services behind ports.
- A single bounded context. The vocabulary (template, slot, caption) is shared, so splitting it
  would add translation cost and no clarity.
- CQRS in its light form: commands go through handlers and aggregates; queries (`TemplateReadPort`,
  `TemplateSearchPort`, `GenerationReadPort`) go straight to SQL and never touch the domain model.
  No event sourcing.

**Enforced, not just intended.** `ArchitectureTest` (ArchUnit) fails the build if `domain` depends
on `application`, `adapter` or Spring, or if `application` depends on `adapter` or Spring.

**Consequence / exception.** The application layer may use one Spring annotation,
`@Transactional`, so a handler can load and save an aggregate in one transaction. This is the only
framework dependency allowed there and the rule says so explicitly.

**Cost.** More files than a plain controller → service → repository would need. That is accepted
because the ports are what make the model swap (Ollama ↔ mock) and the load test possible.

## 2. Generation jobs are an application concern, not an aggregate

**Context.** An early event-storming pass produced a `GenerationJob` aggregate with a state machine
(`REWRITTEN → RETRIEVED → RANKED → …`). Two tests showed it was not domain:

- A meme enthusiast never says "query rewritten" or "candidates ranked".
- Remove the language model and make the memes by hand: the job disappears. It exists only because
  work is asynchronous.

**Decision.** The job is a row in a queue table plus plain handlers (`SubmitGenerationHandler`,
`RunGenerationHandler`, `GetGenerationHandler`). The domain only knows `Meme` and `MemeTemplate`.

**Consequence.** The domain stays small and honest. The price is that "a job can only move forward"
is enforced by SQL (`WHERE status = 'PENDING'` and friends), not by an aggregate.

## 3. No domain events: the search index is reconciled by state

**Context.** When a template is approved, edited or retired, its search entry (text + embedding)
must follow. The reflex solution is a domain event plus a transactional outbox. Domain events were
first added, then removed once it was clear they existed only to serve this one need, and DDD was
no longer the goal.

**Decision.** A sync job compares *state*, not history:

| Situation | Detected by | Action |
|---|---|---|
| Approved, no index entry | left join finds nothing | index it |
| Approved, entry outdated | `template_search.source_updated_at <> meme_template.updated_at` | re-embed |
| Not approved, entry exists | status check | remove entry |

`source_updated_at` stores the template version the embedding was *built from*, so a template edited
while its embedding was being computed is still seen as stale.

**Consequences.**

- Self-healing: if the embedding service is down, the entry simply stays stale and is retried next
  round. No retry machinery, no outbox table, no consumer.
- Idempotent: running it twice, or from two instances, only wastes a call.
- Latency: the index follows within one sync interval (15 s by default); an administrator can
  trigger it immediately with `POST /api/admin/index/sync`.
- Not suitable when many independent consumers need the change history. That is not this system.

## 4. PostgreSQL does the vector search, the keyword search and the job queue

**Decision.** One database for everything:

- `pgvector` (HNSW, cosine) for semantic search.
- `pg_trgm` for keyword search. PostgreSQL's built-in text search configurations do not segment
  Chinese; trigrams still match substrings, and this was checked against Chinese text in a test.
- `FOR UPDATE SKIP LOCKED` for the job queue, and `pg_advisory_xact_lock` to serialize one user's
  quota check and insert.

**Why.** Fewer moving parts, one transaction boundary, and filters such as `status = 'APPROVED'`
combine with vector search in a single query.

**Cost / exit.** This is right for hundreds to tens of thousands of templates and a modest request
rate. Beyond that, the ports (`TemplateSearchPort`, `GenerationJobStore`) are where a dedicated
vector database or a message broker would plug in. Query plans were not studied: at this size a
sequential scan is fine, and whether the HNSW index is used for the vector query (which also joins
the templates table to filter by status) has not been checked.

## 5. Hybrid search, and what the numbers say about it

**Decision.** Search runs a semantic ranking and a keyword ranking and merges them with
reciprocal rank fusion (RRF), which needs no comparison between distance and similarity scales.
If the embedding service fails, keyword search alone still answers.

**What was measured** (real `bge-m3` through Ollama; 12 hand-written templates, 20 situation
queries; run with `mvn test -Dtest=SearchQualityEvalTest -Dwtm.eval=true`):

| | first result correct | in top 3 | MRR |
|---|---|---|---|
| vector | 75% | 90% | 0.85 |
| keyword | 5% | 5% | 0.05 |
| hybrid (RRF) | 75% | 90% | 0.85 |

Searching by *name* (10 queries) found the right template first for vector 100%, keyword 90%.

**What this means, honestly.**

- The keyword ranking contributed nothing on situation queries (people do not repeat the stored
  text). It is kept as a **fallback** for when embeddings are unavailable, not as an accuracy gain.
- Distances for relevant and irrelevant queries overlap (about 0.29–0.59 vs 0.50–0.63), so **no
  similarity threshold** is applied. Search always returns the nearest templates.
- The data set is small and written by the same person who wrote the templates, so the absolute
  numbers are optimistic. Use them to compare changes, not as an accuracy claim.

**An untaken option.** Scoring each usage example on its own (instead of one vector per template)
was prototyped in memory on the same queries: 75% → 85% first-result accuracy using the best single
example, and 95% using the average of that and the whole-template score. It was *not* adopted: the
variant was picked on the very queries it was scored on. The next step would be a query set written
by someone else.

## 6. A meme snapshots its template's slots

**Context.** Templates change. If a meme only stored a template id, revising a template from three
slots to two would leave old memes with captions for a slot that no longer exists.

**Decision.** `Meme` stores `TemplateRef(templateId, version, slots)`. The version increments only
when the **layout** of an approved template changes (editing the profile does not), so the version
tracks exactly the thing a snapshot protects.

**Consequence.** A meme stays valid and re-renderable after its template is revised or retired.
Retiring a template only stops *new* memes from being made with it.

## 7. A template may have no slots

**Context.** An early rule said "a template needs at least one text slot to be approved". It was
wrong: many memes carry their own text, or are pure reactions.

**Decision.** Zero slots is valid. Approval requires only a complete profile (meaning and usage
examples), because that is what makes a template findable. A meme made from a slotless template has
no captions and is the original image.

**Also:** each slot has a `required` flag, so a four-panel template can be filled partially.

## 8. No query rewriting and no re-ranking (yet)

**Context.** The first design had an LLM rewrite the query and another LLM re-rank the candidates.

**Decision.** Skip both. Retrieval was already usable (decision 5) and every extra model call adds
seconds. The pipeline is: retrieve the top three → write captions for each → render → store.
Returning three candidates lets the *user* do the final selection.

**Revisit when** there is data showing the right template is often in the top ten but not the top
three. Both steps would slot into `RunGenerationHandler` without changing any port.

## 9. Stateless JWT, and throttling that is honest about its limits

**Decision.**

- Stateless bearer tokens (HS256, 2 h) through Spring Security's resource-server support, so any
  instance can serve any request. BCrypt for passwords.
- Passwords are limited to 72 **bytes**, because BCrypt silently ignores the rest.
- A login for an unknown user still performs a hash comparison, so response time does not reveal
  whether an account exists; both cases return the same 401.
- Failed logins are counted per *(address, account)* and per address. Keying the strict limit by
  address as well means a stranger cannot lock the real owner out of an account from elsewhere.
- Generation requests are limited per user (in-progress and per day) in PostgreSQL, under an
  advisory lock, so a burst of simultaneous requests cannot slip past the check.

**Known limits.**

- Login and registration throttling is **in memory, per instance**. Several instances each allow
  their own share. A shared store such as Redis is the fix; the `RateLimiterPort` says so.
- The client address comes from `getRemoteAddr()`. Behind a reverse proxy every user would share
  the proxy's address, so forwarded headers must be enabled *and trusted only from that proxy*
  before deploying that way. It is off by default because trusting those headers without a proxy
  lets anyone forge their address.
- No password reset, e-mail verification or logout (a token is valid until it expires).

## 10. Secrets live in `.env`, and the app refuses to start without them

**Context.** Defaults such as a development admin password and JWT key were first written into
`application.yml` for convenience. They would end up in version control and could silently reach
production.

**Decision.** No secret has a default. They come from `.env` (git-ignored) or real environment
variables; `scripts/init-env.ps1` generates random ones and never overwrites an existing file.

**A trap worth recording.** Removing the defaults was not enough. When a bound property contains an
unresolved `${NAME}`, Spring Boot keeps it as **literal text** instead of failing, so a forgotten
admin password would have become the string `${WTM_ADMIN_PASSWORD}`: a known password and no
warning. `RequiredSettingsCheck` therefore runs before any bean is created and stops the
application, naming each missing setting. It also rejects the `change-me` value from `.env.example`.

## 11. Object storage is addressed through the S3 API

**Decision.** A single `ObjectStoragePort` implemented with the AWS SDK for Java v2 against any
S3-compatible store; configuration is just an endpoint and keys.

**Why not MinIO, the obvious choice.** The `minio/minio` image could no longer be pulled when this
was set up (neither from Docker Hub nor quay.io), which was discovered by an integration test
failing. Docker Compose and the tests now use RustFS. Because the code only speaks S3, changing to
SeaweedFS, Garage, Cloudflare R2 or AWS S3 is a configuration change.

**Detail.** Newer SDK versions send checksum trailers some S3-compatible stores reject, so
checksums are requested only when required. Browsers fetch images through time-limited presigned URLs
(15 minutes), so image bytes never pass through the application.

## 12. Jobs run on virtual threads from a database queue

**Decision.** Request handling only enqueues a row and returns `202`. A worker polls, claims jobs
with `SKIP LOCKED`, and runs each on a virtual thread, never more than a configured number at once.
A job that stays `RUNNING` too long (a crashed worker) is requeued, or failed after its last attempt.

**Why.** A job spends nearly all its time waiting for a model, which is what virtual threads are
cheap at, and a database queue survives restarts without another piece of infrastructure.

**Evidence.** A test has 12 threads compete for 60 jobs and asserts each job is claimed exactly
once. During development a second application context, left over from another test and sharing the
same database, was also claiming jobs; the guarantee held across both.

**Honest limit.** Throughput is bounded by the model. On one consumer GPU the model serializes
requests, so load tests must use the mock model; otherwise they measure the GPU, not the system.

## 13. Testing strategy

- **Domain:** plain unit tests, no Spring, no database.
- **Application:** handlers tested against mocked ports.
- **Integration:** the real application against real PostgreSQL (pgvector) and a real S3-compatible
  store, started by Testcontainers. They are skipped automatically when Docker is not running.
- **Deterministic AI:** the default provider is a mock with configurable latency and failure rate,
  so tests and load tests are repeatable and free.
- **Real models, on demand:** two evaluations (`SearchQualityEvalTest`, `GenerationQualityEvalTest`)
  run the real models and write reports and images to `target/`. They are excluded from the normal
  build so it needs neither a GPU nor Ollama.
- **Architecture:** ArchUnit rules (decision 1).

Bugs found by the tests rather than by reading code are the best argument for this mix: the missing
MinIO image (decision 11), the unresolved-placeholder trap (decision 10), and two test-isolation
problems caused by several application contexts sharing one database.

## 14. Rendering captions with Java2D

**Decision.** Draw captions in code (white text, black outline) instead of asking an image model to
paint text, because image models spell Chinese badly. Text is shrunk and wrapped until it fits its
slot; wrapping works per character, so it handles Chinese (no spaces) and breaks Latin text at
spaces.

**Known limits.**

- It needs a CJK-capable font. Windows and macOS have one; a Linux container needs, for example,
  Noto Sans CJK installed, otherwise glyphs render as boxes.
- Wrapping can leave a single orphan character on the last line.
- Over-long captions are cut to the slot's limit; the cut is by character count and can land
  mid-word. Asking the model to retry with a shorter caption would be better.

## 15. The web client

**Decision.** React, TypeScript and Vite, with no UI library and no state library: the app is small,
and plain CSS with variables gives light and dark themes (`prefers-color-scheme`) for free. The
interface is in Traditional Chinese because that is who it is for; the code is not.

**Same origin in development.** The Vite server forwards `/api` to the backend, so the browser sees
one origin and the server needs no CORS configuration. In production, `npm run build` yields static
files that any host can serve provided `/api` is forwarded; the Spring Boot application does not
serve them. That keeps the backend a pure API.

**Where the token lives.** In `sessionStorage`: it survives reloading the tab and disappears when the
tab closes. It is still readable by any script running on the page, so a cross-site-scripting hole
would expose it. An `HttpOnly` cookie would resist that but needs CSRF protection and server changes.
No Content-Security-Policy is configured yet. This is a known soft spot, not a solved problem.

**Two ways to get an image.** Showing an image uses the time-limited storage URL, so the bytes never
pass through the application. *Downloading* goes through `GET /api/memes/{id}/image`: a browser
cannot fetch a cross-origin storage URL as a file without CORS on the storage, and that URL carries
no check that the caller owns the meme.

**The slot editor.** An SVG laid over the image whose coordinates are image pixels, so what is drawn
is exactly what is saved and no screen-to-image conversion leaks into the data. The geometry (move,
resize with eight handles, stay inside the image, minimum size) is pure functions with tests, and
applies the same inside-the-image rule the server enforces.

**Polling, not push.** The page asks for the job again after 0.8 s, backing off to 3 s. That matches
a stateless API and a database queue; server-sent events or WebSockets would feel faster but need
connection state that survives more than one instance. The cost is up to about a second of extra
latency.

**Admin screens are convenience, not security.** Hiding the admin menu from ordinary users only keeps
the screens tidy; every admin endpoint is protected on the server.

**A bug only a real browser found.** Exercising the UI in a production build showed that reloading a
page which fetches data on load sent its first request *without* the sign-in token and got a 401.
React runs a child's effects before its parent's, and the token was installed in the parent's
effect. In development it never showed, because React's StrictMode runs every effect twice and the
second run happened to succeed. The fix is to install the token in a layout effect, which runs
before any ordinary effect in the tree, and a regression test now renders the provider without
StrictMode and asserts the first request carries the token (it failed before the fix). The lesson:
development-mode double effects can hide ordering bugs, so a production build has to be tried too.

**What is not covered.** There are no automated browser (end-to-end) tests; the interface was
exercised by hand. The template editor has only been tried at desktop width.

## 16. Favorites, hot searches and a meme maker that stays in the browser

**Context.** The product narrowed to three things: find a meme, keep the ones you like, and add text to a
kept one to make your own. The earlier flow (describe a situation, let a language model pick a template and
write captions) was dropped from the web client, because the person usually already knows what they want
to say and the problem is finding the picture.

**Decisions.**

- **Favorites are a join table** of user and library entry, listed through the same card shape as search
  results, so one component shows a meme wherever it appears. Only a published entry can be favorited, and a
  favorite disappears from the list when its entry is retired.
- **Hot searches are counted at the API, not in the browser**, and only for searches that found something and
  are 2 to 30 characters long. A whole sentence describing someone's day is not a shortcut, and showing it to
  other users would leak more than they expected. What remains is still visible to every user, without a name.
  That is a deliberate trade for a shared shortcut list, and it is stated in the known limitations.
- **The picture is served through the application** (`/api/library/{id}/image`) instead of the storage's
  temporary address, because a canvas that has drawn a cross-origin picture cannot be read back, and the
  storage sends no CORS headers. The same endpoint serves the plain download.
- **The meme maker draws in the browser.** The text boxes reuse the slot editor (move, resize, draw a new one),
  wrap and shrink text with one pure function shared by the on-page preview and the exported canvas, and the
  result is a PNG saved by the person who made it. Nothing is uploaded, so there is nothing to moderate,
  store or share. That also satisfies "what you make is only for you" by construction, not by a rule.

**Cost.** Server-side rendering (decision 14) is unused by the web client now; it stays for the generation
endpoints. The browser's text metrics differ slightly from Java2D, which is fine because both the preview and
the export use the browser's. An animated GIF loses its animation once text is added.

## 17. Reports: a complaint plus a second look by the vision model

**Context.** The vision model writes every meme's description and tags, and it is sometimes wrong. The people who
notice are the users, and the person who can fix it is the administrator. A bare "this is wrong" costs the
administrator the same effort as finding the mistake alone.

**Decisions.**

- **A report is a reason plus an optional comment, one open report per person per meme.** Reporting again replaces
  the first one, so one person cannot pile up reports, and each person may have 30 open at most.
- **Every report puts the meme in line for a second look.** The vision model is shown the picture, the current
  description and the complaints, and answers with a new description and a sentence on what it changed and why.
  The administrator sees the complaints and the proposal side by side and chooses: adopt, dismiss, ask again, or
  withdraw the meme. The line is a table claimed with `FOR UPDATE SKIP LOCKED`, like tagging, with one row per
  meme: several reports about the same meme share one analysis, and a later report starts it over.
- **The model proposes, a person decides.** Nothing about a meme changes until the administrator adopts the proposal,
  because the complaint may be wrong, the model may agree with a wrong complaint, and either may be an attempt to
  steer the library. The complaint is placed in the prompt as quoted opinion to weigh against the picture, with its
  line breaks removed, and an instruction in it is not followed. This makes it harder, not impossible, to push the
  model; the human step is what actually protects the library.
- **Adopting keeps the meme's other names** (aliases) and only replaces the meaning, usage examples, emotions, tags
  and picture text. The search index follows by itself (decision 3).

**Cost.** Each report costs the vision model about a minute, on the same graphics card that does tagging, so a flood
of reports could delay tagging. The first version only limited each person to 30 open reports, which left holes:
editing a report made the model run again, dismissing and reporting again did too, and many accounts multiplied
everything. Decision 18 closes them.

## 18. Keeping reports from keeping the model busy, and closing the clear-cut ones without an administrator

**Context.** Reports are cheap to send and expensive to answer, and anyone with an account can send them. The
limits have to hold against a person who means harm, and the administrator should only see what needs a person.

**Decisions.**

- **The model's time is the scarce thing, so the ceiling is on the model, not on people.** Every look is written
  down. Non-administrator looks are only started while fewer than 50 have happened in 24 hours, in the same SQL
  statement that claims the work, so two workers cannot overshoot. However many accounts exist, the day's cost is bounded.
- **A look has to be earned.** Reporters carry a weight: 1 for a newcomer, 2 once their reports were adopted
  more often than set aside, 0 once they were set aside five times and never adopted, 2 for an administrator. The model is
  only asked when the weights add up to 2, so one stranger alone cannot spend it; the report is still kept for the
  administrator, who can ask by hand. Each person counts once per meme.
- **A meme is looked at once per day at most**, remembered in a table that outlives the review row, so
  dismissing, adopting or editing a report does not reset it. A new person joining after the look can still
  tip the balance: the earlier proposal is judged again (no new look) and adopted if the weights now reach 3.
- **A person may report ten different memes a day.** Changing what they already said about one meme is never refused.
- **The rules act only when the evidence is clear.** Model says nothing is wrong and fewer than 3 weight insist: reports
  closed. Model proposes a usable change and 3 or more weight agree: adopted. Everything about whether a picture
  belongs, "not a meme", many people against the model, and any look an administrator asked for is left to a person
  (otherwise "undo" on a closed report would just close it again).
- **Every automatic decision can be taken back for 7 days.** Adopting records the earlier description and refuses to
  put it back over a later edit; closing records who and why, and undoing it opens the reports again.

**Honest limits.**

- A group of accounts that each earn trust, then report together, can still push a wrong description through; the
  undo list and the "adopted" counts are how that would be noticed, not a way to prevent it.
- The model reads the complaints, so a complaint can try to steer it. The prompt quotes them as opinion, and nothing
  changes without agreement from several people, but this is not a guarantee.
- A report that arrives while the model is still looking is judged when the look ends, so it counts; one that arrives in
  the instant between the model finishing and the judgement being read is judged at the next report.

**A bug this work exposed.** The administrator's description form never sent the tags or the picture text, so
saving any description wiped them on the server. The form now carries both, and they can be edited there.
