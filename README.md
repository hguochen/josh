# JOSH (Just Our Skills Hub)

Proof-of-concept implementation of the Skills Catalog design in [`design_specifications.md`](design_specifications.md).

## Modules

- `catalog-service` — the HTTP API: publish, discover, retrieve, version (FR-01–04)
- `mcp-adapter` — MCP server exposing `search_skills` / `fetch_skill` / `skill_history` / `publish_skill` to an AI assistant
- `snapshot-job` — standalone weekly durability snapshot, invoked by OS cron

## Requirements

- Java 17+

No other install is required — this repo uses the Maven Wrapper (`./mvnw`), so you don't need Maven installed separately.

## Build

```bash
./mvnw clean install
```

## Run the Catalog Service

```bash
./mvnw -pl catalog-service spring-boot:run
```

Then check:

```bash
curl http://localhost:8080/ping
```

Expected: `{"status":"ok","service":"catalog-service"}`

## Status

This README will be filled in further as each implementation step lands (see the roadmap discussed with the design). Currently scaffolded: project structure and a health-check endpoint only.
