# JOSH (Just Our Skills Hub)

A shared catalog that lets developers publish reusable AI-assistant "skills" and
lets other developers discover and retrieve them through their own AI assistant
— no file handoffs, no copy-pasting. This is a proof-of-concept implementation
of the design in [`phase1_design_specifications.md`](phase1_design_specifications.md), which
covers the full rationale for every choice below.

## How it fits together

```
Developer → AI Assistant → MCP Adapter → Catalog Service (HTTP API) → Catalog Store (SQLite + files)
                                                    ↑
                                          Snapshot Job (weekly, via cron)
```

![High-level component diagram](assets/high-level-component-diagram.webp)

(Editable source: [`diagrams/high-level-design.excalidraw`](diagrams/high-level-design.excalidraw))

## Modules

- **`catalog-service`** — the HTTP API: publish, discover, retrieve, version history (FR-01–04)
- **`mcp-adapter`** — MCP server exposing `search_skills` / `fetch_skill` / `skill_history` / `publish_skill` to an AI assistant
- **`snapshot-job`** — standalone weekly durability backup, invoked by OS cron

## Requirements

- Java 17+

No other install is required — this repo uses the Maven Wrapper (`./mvnw`), so you don't need Maven installed separately.

## Build

```bash
./mvnw clean install
```

This builds all three modules and runs their tests.

## 1. Run the Catalog Service

```bash
./mvnw -pl catalog-service spring-boot:run
```

By default it listens on `:8080` and stores data under `catalog-service/data/`
(a SQLite file plus a directory of skill archives). Confirm it's up:

```bash
curl http://localhost:8080/ping
```

Expected: `{"status":"ok","service":"catalog-service"}`

### Configuration

| Env var | Default | Purpose |
|---|---|---|
| `catalog.storage.root` (Spring property) | `./data` | Where the SQLite DB and archives live |
| `server.port` (Spring property) | `8080` | HTTP port |

## Walkthrough: testing FR-01 – FR-04

Each functional requirement below is self-contained — copy/paste the commands
in order within a section to see it work, including the failure/edge cases
called out in the PRD. All of these assume `catalog-service` is running
(above) and are run from the repo root, so `sample-skills/bug-report-template.zip`
resolves correctly.

### FR-01 — Publish

![FR-01 Publish flow](assets/fr-01-publish-flow.webp)

*A developer can publish a skill to the catalog; a malformed one is rejected
with an explanation and nothing partial is stored.*

**Publish a new skill:**
```bash
curl -X POST http://localhost:8080/v1/skills \
  -F "archive=@sample-skills/bug-report-template.zip" -F "author=Your Name"
```
Expected: `{"name":"bug-report-template","version":1,"checksum":"..."}` — note
the `checksum`, you'll use it below to prove retrieval integrity.

Optional:
```
// add a api docs generator skill
curl -X POST http://localhost:8080/v1/skills -F "archive=@sample-skills/api-docs-generator.zip" -F "author=YOUR_NAME"

// add a bugs report template skill
curl -X POST http://localhost:8080/v1/skills -F "archive=@sample-skills/bug-report-template.zip" -F "author=YOUR_NAME"

```
Sample skills are in `./sample-skills` folder to simulate users creating their local skills and packaging them in `.zip` format.

**Reject a malformed skill (missing `SKILL.md`) — nothing should be stored:**
```bash
mkdir -p /tmp/bad-skill && echo "not a skill" > /tmp/bad-skill/README.md
(cd /tmp/bad-skill && zip -q /tmp/bad-skill.zip README.md)
curl -X POST http://localhost:8080/v1/skills -F "archive=@/tmp/bad-skill.zip" -F "author=Your Name"
```
Expected: `{"error":"Archive is missing SKILL.md at its root"}`

### FR-02 — Discover

![FR-02 Discover flow](assets/fr-02-discover-flow.webp)

*A developer can find published skills via a natural-language query; a query
that matches nothing gets a clear "no results," not an error.*

**Search for something that exists:**
```bash
curl -G "http://localhost:8080/v1/skills" --data-urlencode "q=is there a skill for writing bug reports?"
```
Expected: a JSON array containing `bug-report-template` with its description
and latest version. (This is deliberately phrased as a full question, not
just a keyword — natural-language phrasing is exactly what discover needs to
handle.)
```
[{"name":"bug-report-template","description":"Draft a structured bug report from a description of unexpected behavior","latest_version":1}]
```

**Search for something that doesn't exist:**
```bash
curl -G "http://localhost:8080/v1/skills" --data-urlencode "q=quantum flux capacitor"
```
Expected: `[]` — an empty array, not an error.

### FR-03 — Retrieve

![FR-03 Retrieve flow](assets/fr-03-retrieve-flow.webp)

*A developer can retrieve a named skill, complete and unchanged from what was
published; retrieving one that doesn't exist gets a clear "not found."*

**Retrieve the latest version:**
```bash
curl -D - -o /tmp/skill.zip "http://localhost:8080/v1/skills/bug-report-template"
```
Expected: `200`, an `ETag` header carrying the checksum, and
`Content-Disposition: attachment; filename="bug-report-template-v1.zip"`.

```
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
  0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0HTTP/1.1 200
Content-Disposition: attachment; filename="bug-report-template-v1.zip"
ETag: "9d93068d9760f3d75ecd54511b8ab6b8d375a2a9bc94cbfd2f41192da612ba2b"
Content-Type: application/zip
Content-Length: 894

100   894  100   894    0     0   106k      0 --:--:-- --:--:-- --:--:--  109k
```

**Verify it's actually byte-identical to what was published** — the real
integrity check, not just a 200 status:
```bash
shasum -a 256 /tmp/skill.zip
```
Compare this against the `checksum` from the FR-01 publish response above —
it should match exactly.

**Retrieve a skill that doesn't exist:**
```bash
curl -i "http://localhost:8080/v1/skills/no-such-skill"
```
Expected: `404` with `{"error":"No skill named 'no-such-skill'"}`.

### FR-04 — Version

![FR-04 Version flow](assets/fr-04-version-flow.webp)

*Publishing under an existing name creates a new version rather than
overwriting; prior versions stay retrievable and the full history is visible.*
**AI Assistant listing all skills in natural language query**
```
$ list me all the skills

⏺ The Josh MCP catalog has exactly 2 skills:

  ┌─────────────────────┬─────────┬────────────────────────────────────────────────────────────────────────────┐
  │        Name         │ Version │                                Description                                 │
  ├─────────────────────┼─────────┼────────────────────────────────────────────────────────────────────────────┤
  │ bug-report-template │ v2      │ Draft a structured bug report from a description of unexpected behavior    │
  ├─────────────────────┼─────────┼────────────────────────────────────────────────────────────────────────────┤
  │ api-docs-generator  │ v2      │ Generate API reference documentation from function signatures and comments │
  └─────────────────────┴─────────┴────────────────────────────────────────────────────────────────────────────┘

  That's the full catalog. Want to fetch or use either of these?
```

**Publish a second version of the same skill:**
```bash
curl -X POST http://localhost:8080/v1/skills \
  -F "archive=@sample-skills/bug-report-template.zip" -F "author=Someone Else"
```
Expected: `{"name":"bug-report-template","version":2,...}` — version
incremented, not overwritten.
```
{"name":"bug-report-template","version":2,"checksum":"9d93068d9760f3d75ecd54511b8ab6b8d375a2a9bc94cbfd2f41192da612ba2b"}
```

**See the version history:**
```bash
curl "http://localhost:8080/v1/skills/bug-report-template/versions"
```
Expected: both v1 and v2 listed, oldest first, each with its own author and timestamp.
```
[{"version":1,"created_at":"2026-09-22T01:47:57.332021Z","author":"Gary"},{"version":2,"created_at":"2026-09-22T01:55:25.547828Z","author":"Someone Else"}]
```

**Retrieve the older version specifically (prove v1 was never touched):**
```bash
curl -D - -o /tmp/skill-v1.zip "http://localhost:8080/v1/skills/bug-report-template?version=1"
```
Expected: `Content-Disposition` says `v1`, and its `ETag` matches the exact
checksum from the very first FR-01 publish above — proving the v2 publish
never altered v1.
```
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
  0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0HTTP/1.1 200
Content-Disposition: attachment; filename="bug-report-template-v1.zip"
ETag: "9d93068d9760f3d75ecd54511b8ab6b8d375a2a9bc94cbfd2f41192da612ba2b"
Content-Type: application/zip
Content-Length: 894

100   894  100   894    0     0   207k      0 --:--:-- --:--:-- --:--:--  218k
```

## 2. Run the MCP Adapter

Build the runnable jar first:

```bash
./mvnw -pl mcp-adapter -am package -DskipTests
```

Then point an MCP-compatible assistant at it. For Claude Code:

```bash
claude mcp add josh -e CATALOG_SERVICE_URL=http://localhost:8080 \
  -- java -jar /absolute/path/to/mcp-adapter/target/mcp-adapter-0.1.0-SNAPSHOT.jar
```

Start a **new** Claude Code session afterward (MCP servers load at session
start) and ask something like *"is there a skill for writing bug reports?"*
— if you're testing this and the catalog is empty, publish one of the
`sample-skills/` first (see the FR-01 walkthrough above).

> **Note:** if your assistant has its own built-in "Skills" concept (Claude
> Code does), a generic question can sometimes get answered from that instead
> of calling these tools. Being explicit — *"use the search_skills tool from
> the josh mcp server..."* — reliably routes to the catalog.

### Configuration

| Env var | Default | Purpose |
|---|---|---|
| `CATALOG_SERVICE_URL` | `http://localhost:8080` | Where the Catalog Service is running |
| `JOSH_AUTHOR` | OS username | Author recorded on skills this adapter publishes |
| `JOSH_CACHE_DIR` | `~/.josh/cache` | Where `fetch_skill` caches downloaded, checksum-verified archives |

## 3. Run the Snapshot Job

For durability, we hookup a regular CRON job to perform weekly snapshots on all skills and persist them into a long term durable storage such as S3. 
NOTE: This proof of concept implementation persist the snapshot file in a local storage instead. We can hookup AWS S3 to replace local data persistence through reconfiguring environment variable configurations below.

Build the runnable jar:

```bash
./mvnw -pl snapshot-job -am package -DskipTests
```

Run it manually to test:

```bash
CATALOG_STORAGE_ROOT=catalog-service/data SNAPSHOT_DIR=snapshots \
  java -jar snapshot-job/target/snapshot-job-0.1.0-SNAPSHOT.jar
```

It's safe to run this while `catalog-service` is up — it uses SQLite's
`VACUUM INTO` rather than a raw file copy, so it always produces a consistent
backup without needing the service stopped.

To run it weekly via cron:

```
0 3 * * 0 CATALOG_STORAGE_ROOT=/path/to/catalog-service/data SNAPSHOT_DIR=/path/to/snapshots java -jar /path/to/snapshot-job/target/snapshot-job-0.1.0-SNAPSHOT.jar >> /path/to/snapshot.log 2>&1
```

(In production this would write to S3 instead of a local directory — see
Section 6 of the design doc for that swap point.)

### Configuration

| Env var | Default | Purpose |
|---|---|---|
| `CATALOG_STORAGE_ROOT` | `./data` | Source: the Catalog Service's storage directory |
| `SNAPSHOT_DIR` | `./snapshots` | Destination root; each run creates a timestamped subdirectory |

## Design notes

- Full rationale for every decision (why SQLite, why append-only, why no
  auth, the FR-by-FR flow diagrams, etc.) is in
  [`phase1_design_specifications.md`](phase1_design_specifications.md).
- Diagrams' editable sources are in [`diagrams/`](diagrams) (Excalidraw
  format); rendered images are in [`assets/`](assets).
- What's explicitly out of scope for this PoC: authentication, skill
  de-duplication, and high availability (deferred — see the design doc's
  Assumptions and Summary sections).
