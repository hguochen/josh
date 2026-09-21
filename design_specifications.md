# System Design: Skills Catalog

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
- This design targets Phase 1 (MVP) only: FR-01 Publish, FR-02 Discover, FR-03 Retrieve, FR-04 Version. Phase 2 and out-of-scope items (auth, de-duplication) are excluded (PRD §11, §8).

---

## 2. Outline Approach

- Define Scale: 
- Derive math: 
- Design system: 

---

## 3. Scope

**In scope:**
- 

**Out of scope:**
- 

**NFRs:**
- Latency: 
- Availability: 
- Consistency: 

**System type:** 

---

## 4. Scale

- DAU:
- MAU:
- Concurrent users:
- Global/regional:

---

## 5. Behavior

| Action | Per User/Day | Total/Day |
|---|---|---|
|  |  |  |
|  |  |  |
|  |  |  |

- Read:Write ratio ≈
- Peak multiplier:

---

## 6. Data

**[Object] fields:**
| Field | Size |
|---|---|
|  |  |
|  |  |
| **Total** |  |

**Dominant field:** 

---

## 7. Retention

- 

---

## 8. High Level Design

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

## 9. Component Deep Dive

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

## 10. Tradeoffs

**Consistency vs Availability:**
- 

**Latency vs Cost:**
- 

---

## 11. Summary

| Decision | Choice |
|---|---|
|  |  |
|  |  |
|  |  |
