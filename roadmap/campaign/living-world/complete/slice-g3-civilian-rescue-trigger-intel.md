# Slice G3 — civilian-rescue trigger and choice intel

**Status:** CODE COMPLETE (2026-08-12)

**Implemented:** `0da1b89e`, `34cf0654`, `5572c538`, `24ba5bdc`

## What shipped

- `CivilianRescueSpawnSystem` starts deterministic 45-day epochs on day 30.
  Each epoch selects one registered, visible size-3+ market with a primary
  entity by stable `(epoch, market id)` score, independent of economy/registry
  iteration order.
- The epoch number is the automatic trigger key. Repeated ticks, an early
  terminal choice, and late-load entry cannot create duplicate or historical
  pressure. Pending/committed rescues block overlap; lifecycle expiry runs first.
- New rows freeze the locked size formula: for `tier = marketSize - 2`, costs
  are `25 * tier` supplies and `15 * tier` fuel, stakes are
  `100 * tier * tier` civilians, and the choice deadline is creation day + 3.
- The always-registered Distress Net intel page follows the newest active row,
  maps it to the endangered market, displays exact cost/stakes/time, and routes
  commit/refuse buttons through `CivilianRescueEvent`.
- Insufficient cargo reports that nothing moved. Commitment displays a truthful
  mission-pending state. No material reward, hidden dimension, moral delta, or
  fabricated rescue outcome appears anywhere on the page.
- Debug intel can prepare a local production-shaped call through a reserved
  trigger-key namespace. It rejects active overlap and has no alternate
  choice/outcome path.

## Verification

- Focused tests cover first/late epochs, frozen formulas, selection-order
  independence, same-epoch replay after refusal, open-event overlap, eligibility,
  system order, newest-active intel selection, terminal filtering, deadline
  display clamping, debug production parity, reserved keys, repeat after terminal,
  invalid inputs, and extreme-size overflow rejection.
- Full `.\\gradlew.bat build --no-daemon --max-workers=1` passes.
- Manual playtesting remains intentionally skipped for this session.

## Next

- Lock stable event lineage through mission generation and battle outcome.
- Define partial civilian evacuation facts before calling event resolution.
- Only then build the swarm faction/unit/AI/art battle payload.
