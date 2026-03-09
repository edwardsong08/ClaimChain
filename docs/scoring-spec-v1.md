# ClaimChain Scoring Spec v1 (Rules-Based, Explainable)

## Purpose

ClaimChain’s v1 scoring is a deterministic, rules-based underwriting score designed to mimic how collection agencies / debt buyers evaluate claims for purchase. The score is:

- **Deterministic**: same inputs + same active ruleset → same output
- **Explainable**: every point change is attributable to a rule with a human-readable reason
- **Versioned**: each scoring run records the **ruleset id + version**
- **Auditable**: scoring runs and overrides are recorded in the audit trail
- **Safe under change**: scoring is frozen once a claim is packaged/listed/sold unless explicitly overridden

This v1 model is intentionally “rules-first.” ML is added later as a governed augmentation with strict fallback to rules.

Scoring and packaging are intentionally separate: an APPROVED claim should receive a score even if it is not yet buyer-ready for packaging.
---

## Outputs (persisted per scoring run)

Each scoring run produces:

- `eligible` (boolean)
- `scoreTotal` (int 0–100)
- `grade` (A/B/C/D/F)
- `subscores` (ints, by group):
  - `enforceability`
  - `documentation`
  - `collectability`
  - `operationalRisk`
- `rulesetId` + `rulesetVersion`
- `explainability_json` (bounded JSON)
- `feature_snapshot_json` (bounded JSON)

Scoring history is **append-only** in `claim_scores`. “Current score” is the most recent `scored_at`.

---

## Eligibility gates (minimal v1 gating)

ClaimChain v1 scoring is intentionally broad for approved claims.

A claim is eligible for authoritative scoring if:

1) Claim is in required status (v1: `APPROVED`)
2) If the active ruleset blocks active disputes, `disputeStatus != ACTIVE`

Missing documents, weak extraction, incomplete metadata, or sparse supporting information do **not** prevent score creation. Instead, those conditions must be reflected through lower subscores, lower total score, and explainability output.

If a claim is ineligible under the minimal gates above:
- persist a score run with `eligible=false`, `scoreTotal=0`, `grade="F"`
- include `eligibleReasons[]` in explainability

---

## Scoring dimensions and weights

Score is composed of four groups. Weights are ruleset-configurable and must sum to ~1.0.

Recommended v1 defaults:
- `enforceability`: 0.35  
- `documentation`: 0.30  
- `collectability`: 0.25  
- `operationalRisk`: 0.10  

Rules add/subtract points within a group. Each group is capped by its configured maximum (ruleset caps).

---

## Rule evaluation model

Rules are defined in the ACTIVE SCORING ruleset. For each rule that applies:

- apply its `points` delta to the associated group subscore
- append a contribution record for explainability

Rules are evaluated deterministically. If multiple rules apply, all contributions apply (unless explicitly prevented in a future schema version).

### Contribution record format (explainability)
Each contribution entry includes:

- `ruleId`
- `group`
- `delta`
- `reason` (human-readable)
- `fieldsUsed` (key/value snapshot of inputs referenced by the rule)

---

## Inputs used in v1 (available in current data model)

This v1 spec only relies on fields that exist in the platform now:

**Claim underwriting fields**
- `jurisdictionState`
- `debtorType`
- `claimType`
- `disputeStatus`
- `currentAmount` (fallback to legacy `amountOwed` if needed)
- `originalAmount`
- `dateOfDefault`
- `lastPaymentDate` (optional for v1)

**Documents**
- document count (`docCount`)
- required doc types present (`requiredDocTypesPresent`) as scoring inputs, not hard scoring gates
- extraction status per doc (`extractionStatus`)
- extracted char counts (`extractedCharCount`)
- computed extraction success rate (`extractionSuccessRate`)

**Workflow**
- `claim.status` (APPROVED gating)
- freeze state once `PACKAGED/LISTED/SOLD`

---

## Grade bands (default)

Grade is derived from `scoreTotal` using ruleset-configured thresholds:

- A: 85–100
- B: 70–84
- C: 55–69
- D: 40–54
- F: 0–39 (and all ineligible)

---

## Freeze and override rules

Once a claim transitions into any of:
- `PACKAGED`, `LISTED`, `SOLD`

Scoring becomes frozen:
- no automatic rescoring
- no rescoring unless admin override is explicitly invoked with reason and audit record

All override attempts and outcomes must be auditable.

---

## Audit events (scoring-related)

The system records audit events including requestId correlation:

- `CLAIM_SCORED`
- `CLAIM_RESCORED`
- `CLAIM_SCORE_OVERRIDE` (or equivalent override event)

---

## Canonical v1 ruleset example

See: `docs/scoring-ruleset-v1.example.json`

This example is the recommended starting point and should pass activation-time ruleset validation.

---

## Future enhancements (post-v1)

Planned improvements (not part of v1 implementation):
- jurisdiction-specific statute-of-limitations tables and enforceability rules
- stronger document consistency checks (invoice/contract matching)
- structured dispute outcome feedback loops
- ML augmentation as a separate governed service (Python training + versioned inference)
- drift monitoring, rollback, and model registry