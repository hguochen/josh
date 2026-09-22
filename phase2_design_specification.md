# Phase 2: Improvement Roadmap

_Improvements to JOSH identified after Phase 1 (MVP) shipped to `main`, organized by area. This is a backlog, not a build order — each item still needs its own design discussion before implementation._

---

## 1. Immediate Fixes

Small, high-value corrections found during Phase 1 implementation and testing.

- **Concurrent publish of a new skill name fails ungracefully.** The `UNIQUE(name, version)` constraint protects data integrity, but a simultaneous double-publish under the same new name currently surfaces as a raw SQL exception (500), not a clean "already published, please retry" response.
  - **Fixed:** the losing publish now gets a clean 409 with a retry message instead of a raw 500, verified with two genuinely concurrent live requests. [60ae3bf](https://github.com/hguochen/josh/commit/60ae3bf)
- **No upload size limit is enforced.** Assumption 18 (`phase1_design_specifications.md`) states skills are small, text-based artifacts, but nothing in the stack actually enforces that — a large upload would be silently accepted.
  - **Fixed:** capped uploads at 5MB, rejected with a clean 400 instead of a silent accept or a raw 500. [dea5c67](https://github.com/hguochen/josh/commit/dea5c67)

---

## 2. Scalability

- **High availability was explicitly deferred for Phase 1.** Revisit now that the MVP has shipped — decide whether it's still out of scope or becomes a Phase 2 target.
  - Active-passive: 1 active node serving requests, 1 passive on standby, automatic failover.
  - Passive needs the same data — SQLite is a single file, so replicate it (e.g. WAL streaming) or use shared storage, not a local disk per node.
  - Load balancer / virtual IP in front, routing only to the current active node.
  - Health checks to detect the active node is down and trigger failover.
  - A lock/lease so only one node ever writes at a time — avoids split-brain, matches SQLite's single-writer model.
- **Single-file SQLite store.** Adequate at current scale (~200 developers), but the growth ceiling and the trigger point for migrating to a different store should be documented rather than left implicit.
- **No pagination on `discover` / version-history endpoints.** Fine today; will break down as individual skills accumulate many versions or the catalog grows well past current assumptions.

---

## 3. Security

- **Authentication was explicitly out of scope for Phase 1** (trusted small group, PRD 8/D3). Needs a deliberate decision for Phase 2 — stay open, or add access control — rather than remaining unaddressed by default.
- **Archive extraction/packaging has not been audited for path-traversal risk** (e.g. zip-slip during unpacking). Worth a dedicated review given the code currently trusts archive contents.
- **No rate limiting or abuse protection** beyond the informal usage assumptions in Section 1 of the Phase 1 design (10 discovers/day, 5 retrieves/day, 1 publish/3 days per developer) — those are capacity assumptions, not enforced limits.

---

## 4. Observability

- **No structured logging of publish/discover/retrieve events.** Makes debugging and usage analysis difficult beyond default Spring Boot request logs.
- **Snapshot job can fail silently.** It runs via OS cron with no alerting — a failed run currently has no signal beyond a non-zero exit code nobody is watching.
- **No health/metrics endpoint** on the catalog service for operational visibility.

---

## 5. Features

- **Standalone publish CLI.** The original design mentions a CLI as an alternative to the MCP tool, but it was never built — publishing today requires either raw `curl` or an AI assistant session, leaving CI/scripting use cases unserved.
- **Snapshot restore tooling/runbook.** Backup (via `VACUUM INTO`) is implemented and tested; restore has never been exercised or documented. A backup that has never been restored from is not proven disaster recovery.
- **De-duplication** was explicitly deferred by the PRD (8, D3). Only take this up if there's a deliberate decision to bring it into scope — not a default inclusion.
