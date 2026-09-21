# System Design: JOSH(Just Our Skills Hub)

_A central hub of skills catalog_

---

## 1. Assumptions

- Developers reach the catalog exclusively through an AI assistant acting on natural-language intent — no requirement for a direct human-operated UI (PRD 9).
- The skill format (manifest + optional supporting files) is sufficient for what developers want to share — no need to support other artifact types (PRD 9).
- A trusted set of developers; no authentication/access control needed (PRD 8, D3).
- No numeric SLOs — "fast enough to feel interactive" is qualitative, not a hard target (PRD 7).
- Self-contained: must run on a reviewer's machine from a short README, no external infra dependency (PRD 9).
- System is intended for a developer team size of ~200 developers.
- "Reviewer's machine" = single-machine PoC — the catalog is simulated as a local service reachable by one or more assistant sessions on the same machine, not deployed across real separate developer machines/network.
- Skill names are the identity/versioning key — publishing under an existing name creates a new version (PRD UC-04); name is effectively unique per skill.
- No concurrent-write conflict handling — small trusted group, no need for optimistic locking or merge conflict resolution on simultaneous publishes to the same name.
- Skills are small, text-based artifacts (manifest + a handful of small supporting files), not large binaries.
- This design targets Phase 1 (MVP) only: FR-01 Publish, FR-02 Discover, FR-03 Retrieve, FR-04 Version. Phase 2 and out-of-scope items (auth, de-duplication) are excluded (PRD 11, 8).

---

## 2. Outline Approach

- **Define Scale:** Bound by team size (~200 devs), not DAU/MAU traffic modeling.
- **Derive math:** Skip capacity-planning math — not needed at this scale.
- **Design system:** Concentrate design effort on the real complexity for this exercise — the data model (skill/manifest/version), the versioning behavior (FR-04), the API surface (FR-01–03), and how an AI assistant reaches the catalog (the PRD's core open dependency, PRD §10) — since that's where the actual decisions live, not in scale math.

---

## 3. Scope

**In scope:**
- Publish a skill (manifest + supporting files) to the catalog (FR-01)
- Discover skills via natural-language query through an AI assistant (FR-02)
- Retrieve a named skill, complete and unchanged (FR-03)
- Version skills — re-publish under an existing name creates a new version; latest is default, specific versions retrievable, history visible (FR-04)
- Reject malformed publishes (missing name/description/body) cleanly, no partial writes

**Out of scope:**
- Authentication / access control (PRD §8, D3)
- De-duplication of similar/near-identical skills (PRD §8, D3)
- Phase 2 additions (undefined, deferred to builder's judgment — PRD §11)
- A direct human-operated UI as the access path (assistant-mediated only, per Assumptions)
- Multi-machine/networked deployment across real separate developer machines (single-machine PoC, per Assumptions)
- Concurrent-write conflict resolution / locking (per Assumptions)

**NFRs:**
- Latency: fast enough to feel interactive within an assistant conversation — no numeric target (PRD §7)
- Availability: not a stated concern — single-machine PoC, no HA requirement
- Consistency: a retrieved skill must be complete and unchanged from what was published — no silent loss or alteration (PRD §7)

**System type:** Versioned artifact/metadata store with natural-language search, exposed to AI assistants as callable tools (not a distributed system)

---

## 4. Behavior

| Action | Per User/Day | Total/Day |
|---|---|---|
| Publish (new skill or new version) | ~0.33 (≈1 every 3 days) | ~67 |
| Discover (search) | 10 | ~2,000 |
| Retrieve (fetch by name) | 5 | ~1,000 |

- Read:Write ratio ≈ 45:1 (discover + retrieve vs. publish) — heavily read-dominated
- Peak multiplier: ~2–3x during working hours (internal dev tool, not 24/7 global traffic); absolute peak is still modest (~125–190 requests/hour) — no caching/sharding needed for throughput
- Durability: low write volume ≠ low durability need — a lost publish is a lost skill for the whole team, so data must still be replicated/backed up even though the *rate* of writes never demands it

---

## 5. Data

**Skill Version fields:**
| Field | Description | Size |
|---|---|---|
| `name` | Unique identifier for the skill; used as the versioning key (same name = new version, not a new skill) | ~50 B |
| `description` | Short human-readable summary used for natural-language discovery/search matching | ~200 B |
| `author` | Name of the developer who published this version | ~50 B |
| `instructions` | The manifest's instruction body — the actual reusable AI-assistant instructions | ~5 KB |
| `files` | Supporting files bundled with the skill (filename + content pairs), 0–N | ~10 KB (avg, variable) |
| `version` | Monotonically increasing version number for this skill name | ~4 B |
| `created_at` | Timestamp this version was published | ~8 B |
| **Total** | | **~15 KB** |

**Dominant field:** `files` (when present) / `instructions` — the metadata fields (`name`, `description`, `author`, `version`, `created_at`) are negligible by comparison; storage sizing should be driven by content size, not row count.

---

## 6. Retention

- All versions of every skill are retained indefinitely — no pruning/expiration. Deleting a version would violate the "no silent loss" consistency requirement (PRD §7) and defeats the purpose of version history (FR-04).
- No hot/warm/cold tiering — data volume is trivial at this scale (~67 publishes/day × ~15 KB ≈ ~1 MB/day, ~365 MB/year), so there's no cost or performance reason to move older versions to cheaper storage.
- Storage: unbounded (grows with skill/version count), but growth rate is negligible at 200-developer scale — even a decade of history stays well under a few GB.
- Durability over recency: publishes are rare but each is valuable (a lost skill affects the whole team), so the retention priority is "never lose a version," not "keep only recent data hot/accessible."
- **Durability design: weekly full snapshots.**
  - **Frequency:** Weekly full snapshot of the entire catalog store (all skills, all versions, metadata + files).
  - **Destination:** AWS S3 in production — durable, cheap, standard choice for infrequent-access backups at this data volume. For the Phase 2 PoC, snapshots write to a local directory instead (swappable for S3 later), so the reviewer's machine stays fully offline-runnable per the self-contained Assumption.
  - **Purpose:** Durability only. Snapshots are not a serving path — no querying, indexing, or reads from the snapshot during normal discover/retrieve. Their only job is disaster recovery.
  - **Content:** Full copy, not incremental — at ~1 MB/day growth (~7 MB/week), a full weekly snapshot is trivially cheap; incremental/diff snapshotting would add complexity with no real benefit at this scale.
  - **Snapshot retention:** Keep all weekly snapshots (or a simple lifecycle rule to move old snapshots to cold storage after N months) — a separate concern from catalog version retention (the catalog itself never deletes a version, regardless of snapshot policy).
  - **Recovery model:** Manual/operator-triggered restore from the latest snapshot if primary storage is lost — not automated failover, consistent with the "no HA requirement" NFR (Section 3).

---

## 7. High Level Design

**Request flow:**
```
Client → ... → 
```

**Major components:**
- **[Service]** —
- **[Service]** —
- **[DB]** —
- **[Cache]** —

---

## 8. Component Deep Dive

### Hardest component: [NAME]

**Problem:** 

**Options:**

| | Option A | Option B |
|---|---|---|
| Latency |  |  |
| Throughput |  |  |
| Tradeoff |  |  |

**Decision:** 

---

### API Design

```
GET    /v1/...    →
POST   /v1/...    →
PUT    /v1/...    →
DELETE /v1/...    →
```

---

### SQL vs NoSQL Decision

| Store | Choice | Reason |
|---|---|---|
|  |  |  |
|  |  |  |
|  |  |  |

---

## 9. Tradeoffs

**Consistency vs Availability:**
- 

**Latency vs Cost:**
- 

---

## 10. Summary

| Decision | Choice |
|---|---|
|  |  |
|  |  |
|  |  |
