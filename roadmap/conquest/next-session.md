# Conquest — next-session handoff

_Last updated: 2026-08-13, after shipping the front-line reinforcement
trigger + wiring (`28e512ab`)._

## State of play

- **central-keep V1** — shipped (all 6 slices; see `central-keep.md`).
- **deliberate-compound-capture** — shipped (`complete/`).
- **compound-spread** — shipped `56a0407f` (`complete/`).
- **tug-of-war-v2** — shipped `3bbbcb12` + `08ef31e6` born-holding
  refinement (`complete/`).
- **progressive-reinforcement** — code-complete, playtest pending.
  Commit chain: `1e0d388` (request two-coordinate split) → `e4a49b9` /
  `ae5a1bd` / `e4a2ed6` (RecaptureTargetService + System) → `0492f36` /
  `f2f8ac0` (LandingZoneScorer) → `5bcf716` / `77b53b9` / `085f72a`
  (means deboard via scorer) → `28e512ab` (FrontLineReinforcementTrigger,
  ObjectiveNodes resolve, ShuttleMission/VehicleMission `assignNode`
  deboard stamping, BiomeMap plumbed through MapResult, wiring in
  `installReinforcementLayer` + BattleSimulation tick) → `c99cdecf`
  (critique-pass hardening: `manned` latch — never-garrisoned nodes
  aren't recapture targets — and a 90-tick dispatch-age timeout that
  re-opens targets whose dispatch died in the delivery pipeline:
  convoy routing aborts after the request is consumed, no-means drops,
  `SquadFallbackSystem` stealing a mauled squad's `assignedNode`).

## Immediate next-up

1. **Playtest + tune progressive reinforcement** (slice 5 remainder):
   fire cadence (currently 1 Hz shared with `REINFORCEMENT_TICK_PERIOD`,
   one dispatch per tick), `RALLY_REAR_SHIFT` (8 cells), and whether the
   round-robin spread reads on the map. Watch: conquest maps no longer
   run `GarrisonDepletedTrigger` — compounds only get help once their
   garrison is *fully wiped* (by design; verify it feels right).
2. **biome-counterattack** (`stories/biome-counterattack.md`) — now
   unblocked; builds on the frontline filter, RecaptureTargetService,
   the two-coordinate split, and the Commander tier.

## Known deferred / follow-ups

- Overflow → patrol (biome-constrained) — trigger posts nothing when no
  eligible targets; WalkInMeans free-agent fallback covers ambient.
- `ReinforcementMeans.dispatch` is void — a means that aborts internally
  after `canFulfill` passed (convoy routing failures) consumes the
  request without spawning, and the chain never falls through to the
  next means. The dispatch-age timeout (`c99cdecf`) heals the stranded
  target, but a boolean-returning dispatch with means fall-through would
  retry immediately instead of ~90 s later.
- Tug-of-war "not in this cut" list still open: defender positive win
  condition, marine-side compound supply, incoming-garrison marker.
- `BattleSimulation.getReinforcementService()` uses an inline FQN return
  type (pre-existing style violation; fix opportunistically).
