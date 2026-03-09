# ClaimChain Packaging Spec v1 (Rules-Based, Deterministic, Traceable)

## Purpose

ClaimChain’s v1 packaging creates **buyer-ready portfolios** of claims that are:

- **Eligible** (approved, already scored, and buyer-ready)
- **Diversified** (avoid concentration risk)
- **Deterministically constructed** (same inputs + same ruleset → same package composition)
- **Traceable** (why each claim was included/excluded is explainable)
- **Versioned & auditable** (ruleset id/version recorded; packaging events are auditable)

This spec defines the v1 packaging logic. Packaging is separate from anonymization and marketplace listing; those come in later steps.

---

## Lifecycle (v1 assumption)

Packages move through a status lifecycle:

- `DRAFT` → `READY` → `LISTED` → `SOLD` → `ARCHIVED`

Claims may transition into a packaging state when included in a package (final decision implemented in Step 8.1/8.2).

---

## Eligibility gates (hard requirements)

A claim is eligible for packaging only if:

1) `claim.status == APPROVED`
2) Claim has a **current score** (latest `claim_scores.scored_at`)
3) Score meets thresholds:
   - `minScore` and/or `minGrade` from the ACTIVE PACKAGING ruleset
4) Required doc types are present:
   - ALL `requiredDocTypes` exist among claim documents
5) Extraction readiness is met:
   - `extractionSuccessRate >= minExtractionSuccessRate`
6) Dispute exclusions:
   - claim’s `disputeStatus` must NOT be in `excludeDisputeStatuses`
7) Claim is not already packaged (no duplicates), unless later rules explicitly allow it

If any hard gate fails, the claim is excluded from the candidate pool.

Packaging eligibility is intentionally stricter than scoring eligibility. In v1, an APPROVED claim should generally receive a score, but only a subset of scored claims should qualify for packaging. Packaging therefore acts as the buyer-readiness filter.

---

## Portfolio construction rules

### Package sizing constraints
From the ruleset:

- `minClaims`, `maxClaims`
- optional:
  - `minTotalFaceValue`, `maxTotalFaceValue`

Face value uses `currentAmount` (fallback to legacy `amountOwed` if needed).

Packages must satisfy all sizing constraints to be considered valid.

---

## Diversification constraints (v1)

Packaging must enforce concentration caps:

- `maxPctPerJurisdiction`
- `maxPctPerDebtorType`

**Interpretation (v1):**
- At any point while filling the package, adding a claim must not cause:
  - a single jurisdiction bucket to exceed `maxPctPerJurisdiction`
  - a single debtorType bucket to exceed `maxPctPerDebtorType`

These constraints reduce buyer risk and increase marketability.

---

## Deterministic selection strategy

The ruleset defines how to select claims from the eligible pool.

### Strategy modes
- `BEST_FIRST` (v1 recommended):
  - sort eligible claims by:
    1) highest scoreTotal
    2) highest currentAmount
    3) oldest submittedAt (FIFO fairness)
  - then fill the package while enforcing diversification + sizing constraints

- `BALANCED` (future v1.1+ option):
  - bucket claims by jurisdiction and/or debtorType
  - round-robin selection to satisfy diversification early

v1 uses **BEST_FIRST** for simplicity and predictability.

---

## Traceability requirements (must persist)

Packaging must be explainable:

### Per claim inclusion
For every claim included in a package, store an `included_reason_json` payload with at least:
- `scoreTotal`, `grade`
- eligibility thresholds used (`minScore`, `minGrade`)
- doc readiness summary (required docs present, extraction success rate)
- constraint checks (jurisdiction/debtorType buckets at time of inclusion)
- `packagingRulesetId`, `packagingRulesetVersion`

### Per claim exclusion (v1 optional, recommended later)
We may later store exclusion reasons for candidates not selected. v1 may log exclusions in audit metadata only.

### Per package creation
Record an audit event with:
- ruleset id/version used
- number of claims
- total face value
- constraint configuration summary
- selection strategy

---

## Security and privacy note (important)

Packaging occurs before buyer-facing exposure. Buyer safety is implemented by:
- generating **anonymized claim views** (Step 8.3)
- enforcing buyer entitlements (later)
- never exposing raw provider claim fields or raw docs to buyers

Packaging itself must not introduce any buyer-accessible endpoints.

---

## PACKAGING ruleset JSON contract (v1)

See: `docs/packaging-ruleset-v1.example.json`

### Expected top-level keys (v1)
- `schemaVersion` (int)
- `eligibility` (object):
  - `minScore` (int 0..100) and/or `minGrade` (string)
  - `requiredDocTypes` (array)
  - `minExtractionSuccessRate` (float 0..1)
  - `excludeDisputeStatuses` (array of strings)
- `packageSizing` (object):
  - `minClaims`, `maxClaims`
  - optional: `minTotalFaceValue`, `maxTotalFaceValue`
- `diversification` (object):
  - `maxPctPerJurisdiction`, `maxPctPerDebtorType` (floats 0..1)
- `selectionStrategy` (object):
  - `mode`: `BEST_FIRST` or `BALANCED`

Activation-time validation currently enforces basic eligibility and sizing sanity checks; additional strict validation may be added later.

---

## Future enhancements (post-v1)

Planned improvements:
- Exclusion reason persistence for all candidates
- More diversification dimensions:
  - debt age bands
  - claim type mix
  - amount bands
- Multiple package creation modes:
  - fixed-size
  - target face-value
  - buyer-targeted baskets
- Pricing strategy integration (ruleset-driven pricing later)