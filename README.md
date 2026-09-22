# JOSH (Just Our Skills Hub)

A shared catalog that lets developers publish reusable AI-assistant "skills" and
lets other developers discover and retrieve them through their own AI assistant
— no file handoffs, no copy-pasting. This is a proof-of-concept implementation
of the design in [`design_specifications.md`](design_specifications.md), which
covers the full rationale for every choice below.

## How it fits together

```
Developer → AI Assistant → MCP Adapter → Catalog Service (HTTP API) → Catalog Store (SQLite + files)
                                                    ↑
                                          Snapshot Job (weekly, via cron)
```

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

### Try the API directly

Two sample skills are included under [`sample-skills/`](sample-skills), pre-zipped and ready to publish:

```bash
# Publish a skill
curl -X POST http://localhost:8080/v1/skills \
  -F "archive=@sample-skills/bug-report-template.zip" -F "author=Your Name"

# Discover skills by natural-language query
curl "http://localhost:8080/v1/skills?q=writing%20bug%20reports"

# Retrieve the latest version's archive
curl -o skill.zip "http://localhost:8080/v1/skills/bug-report-template"

# Retrieve a specific version
curl -o skill.zip "http://localhost:8080/v1/skills/bug-report-template?version=1"

# See the full version history
curl "http://localhost:8080/v1/skills/bug-report-template/versions"
```

Publishing under an existing name creates a new version rather than
overwriting — nothing is ever deleted (see Section 6 of the design doc).

### Configuration

| Env var | Default | Purpose |
|---|---|---|
| `catalog.storage.root` (Spring property) | `./data` | Where the SQLite DB and archives live |
| `server.port` (Spring property) | `8080` | HTTP port |

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
`sample-skills/` first (see above).

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
  [`design_specifications.md`](design_specifications.md).
- Diagrams' editable sources are in [`diagrams/`](diagrams) (Excalidraw
  format); rendered images are in [`assets/`](assets).
- What's explicitly out of scope for this PoC: authentication, skill
  de-duplication, and high availability (deferred — see the design doc's
  Assumptions and Summary sections).
