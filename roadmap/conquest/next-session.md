# Conquest — next-session handoff

_Last updated: 2026-08-19, after shipping the bulge counterattack's
player-facing telegraph surface (`f0c766a3`)._

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
- **biome-counterattack** — code-complete, playtest pending. Core phase /
  economy / dispatch / resolve shipped in `db87ed0d`; player-facing slice 3
  shipped in `f0c766a3`: a reusable newest-dispatch battle comms feed,
  warning → inbound → success/failure/abort officer reads, an explicit
  resolution contract retained through cooldown, and a pulsing world marker
  at the threatened target centroid with the telegraph countdown.

## Immediate next-up

1. **Playtest + tune** both reinforcement systems together:
   - Progressive reinforcement (slice 5 remainder): fire cadence,
     `RALLY_REAR_SHIFT` (8 cells), round-robin spread readability.
     Watch: conquest maps no longer run `GarrisonDepletedTrigger` —
     compounds only get help once their garrison is *fully wiped*.
   - Bulge counterattack (slice 5): `BURST_TICKETS` (4),
     `SURPLUS_FLOOR_TICKETS` (2), `TELEGRAPH_SEC` (20),
     `SUCCESS_HOLD_TICKS` (10), `COOLDOWN_SEC` (240) /
     `ABORT_COOLDOWN_SEC` (30), `MAX_BULGES_PER_BATTLE` (2). The
     loss-then-reclaim rhythm must threaten without the map refusing
     to stay taken.
   - Telegraph surface (`f0c766a3`): check the comms plate against the
     compound-progress strip at common resolutions; confirm the marker is
     readable at fit-map and close zoom, the 20-second sim-time countdown
     feels honest at 1×/2×/4×, and warning/inbound/outcome copy lands without
     overstaying its real-time notice windows.

## Bulge counterattack — code-complete (`db87ed0d`, `f0c766a3`)

Slices 1, 2, 4 of `stories/biome-counterattack.md`:
`CounterattackSystem` phase machine (IDLE → TELEGRAPH → ASSAULT →
RESOLVE → COOLDOWN; abort path refunds + short cooldown, doesn't count
against the cap), all-or-nothing lump earmark with surplus floor,
prepaid `ReinforcementRequest`s through the normal means pipeline
(supply gates apply; posted-but-undeliverable requests burn their
share, unposted tickets refund), emergent resolve via sustained
`isContested` hold (10 ticks — a wave's own transit presence can't
insta-latch success). Workflow-orchestrated build: 3 adversarial
verify lenses found 8 issues, 7 fixed pre-commit (transit-presence
success latch, assault-tick re-contest race ×2, abort churn,
frontline double-dispatch, undeliverable-wave muster, stale snapshot),
1 skipped as a recorded design decision (overflow gate counts
in-flight dispatches as plugged — revisit only if playtest shows it).

Slice 3 (`f0c766a3`) adds the real battle notification surface instead of a
counterattack-only pop-up. `CounterattackCommsPresenter` translates sim state
into player-POV dispatches; `BattleCommsPanel` owns the transient plate and
world-anchored threatened-district signpost. `CounterattackSystem` now exposes
the phase timer, target centroid, and explicit SUCCESS / FAILURE / ABORTED
resolution retained throughout cooldown, so presentation never guesses from a
phase transition or misses a one-tick result.

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
