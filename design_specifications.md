# System Design: Skills Catalog

---

## 1. Assumptions

- 

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
