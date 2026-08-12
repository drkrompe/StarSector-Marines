# Slice G1 — silent moral-compass foundation

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `facfa007`, `6765eac6`, `2a1924e7`

## What shipped

- Four hidden signed axes are locked and persisted: mercy, integrity,
  stewardship, and institutionalism. Each is bounded to `-100..100`; none has a
  numeric UI, progress bar, threshold notification, or debug-intel display.
- The axes are independent and neutrally oriented. Civil-war side selection
  changes institutionalism only; it does not infer mercy, integrity, or
  stewardship from claimant/incumbent allegiance.
- An append-only primitive ledger snapshots stable identity, source namespace,
  exact applied deltas, happened day, and recorded day for every moral choice.
  `(sourceType, sourceId)` provides replay-safe uniqueness without scattering
  applied flags across future source tables.
- `MoralChoiceRecorder` is the only aggregate mutation seam. It validates the
  source, checks uniqueness before mutation, clamps aggregates, and records the
  actual post-clamp delta—even zero when a bounded axis was already saturated.
- `MoralCompassSystem` runs after civil-war player consequences. Applied claimant
  handoffs record -5/-10/-20 institutionalism; decisive incumbent victories
  record +5/+10/+20, using the existing contribution tiers and source chain id.
- Autonomous, stale, malformed, merely accepted, failed, and abandoned civil-
  war work produces no moral row.

## Verification

- Focused tests cover persistence identity, growth sentinels, empty/legacy
  backfill, sequence recovery, all-axis recording, validation, namespace
  uniqueness, clamped actual deltas, replay, contribution bands, both civil-war
  directions, neutral outcomes, and the end-to-end incumbent pipeline.
- Full `.\\gradlew.bat build --no-daemon --max-workers=1` passes.
- Manual playtesting remains intentionally skipped for this session.

## Next

- Lock and build a second input family that establishes different axes from real
  persisted facts. The civilian-rescue black-swan proof of concept is the
  strongest candidate for mercy and stewardship.
- Continue withholding captain drift, patron gates, and capstone testimony until
  multiple input families make their reactions earned rather than synthetic.
