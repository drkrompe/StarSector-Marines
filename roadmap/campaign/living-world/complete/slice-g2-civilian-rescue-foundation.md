# Slice G2 — civilian-rescue event foundation

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `cf8b717b`, `5fd8969d`, `1b2afcb4`

## What shipped

- Civilian rescue is a dedicated black-swan campaign event, not a contract. A
  primitive SoA table persists stable id, type, trigger key, lifecycle state,
  market, choice window, decision/outcome days, exact supplies/fuel cost, and
  civilian stakes/outcome.
- `(eventType, triggerKey)` preparation is idempotent and keeps the first frozen
  snapshot across retries. Save restoration grows/backfills the new columns,
  rebuilds the event index, and recovers the id sequence.
- Pending events support an explicit voluntary refusal or an atomic cargo
  commitment through the shared policy API. Insufficient resources, duplicate
  calls, invalid timing, and wrong-state calls do not partially mutate facts or
  consume cargo.
- Untouched choices expire only after their deadline and remain distinct from
  refusal. Only committed events resolve, with rescued civilians clamped to the
  persisted at-risk population.
- The moral consumer runs after event expiry. Explicit refusal records -5 mercy
  / -10 stewardship; positive rescue records locked ratio-tier deltas up to
  +15/+20 for a complete rescue. Passive expiry and zero rescue are neutral.
- Refusal and rescue have distinct ledger namespaces keyed by event id. Replay
  cannot double-count either outcome, and no compass number/reward preview is
  exposed.
- The slice has no random producer, intel/interaction surface, swarm faction,
  held-art dependency, mission factory, battle AI, or material payout.

## Verification

- Focused tests cover event-table growth/backfill/index recovery, trigger
  idempotency, invalid preparation, refusal, atomic commitment, choice windows,
  terminal replay, rescued-count clamping, passive expiry, moral ordering,
  source identity, all ratio boundaries, neutral outcomes, and overflow-safe
  ratio math.
- Full `.\\gradlew.bat build --no-daemon --max-workers=1` passes.
- Manual playtesting remains intentionally skipped for this session.

## Next

- Add a deterministic sparse world-condition producer with stable trigger keys.
- Present pending events through a diegetic local choice surface showing exact
  costs/stakes/deadline but neither material nor moral reward.
- Leave committed events awaiting the later swarm-defense mission payload; do
  not fabricate resolution at acceptance.
