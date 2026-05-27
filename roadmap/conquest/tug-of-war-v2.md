# Territory tug-of-war — V2 compound capture

> When marines capture a compound, a friendly garrison shuttle drops
> troops to hold it while the assault force pushes on. Existing defender
> reinforcement naturally counter-attacks. If the garrison falls, the
> compound flips back to defender control. Marines must balance advancing
> vs. holding — the tug-of-war dynamic.

## Why

V1's compound capture is one-way: MARINE_HELD is absorbing. Once marines
take a compound, it stays taken — no tension, no strategic cost to the
capture. The player never has to decide "do I leave someone behind?"
because there's no counter-pressure.

V2 activates the reverse transitions (MARINE_HELD → CONTESTED →
DEFENDER_HELD) that are already wired in `CompoundCaptureSystem` by
giving the player a garrison drop and letting the existing defender
reinforcement pipeline counter-attack naturally.

## Core mechanic

1. Marines capture a compound (state flips to MARINE_HELD).
2. `CompoundGarrisonSystem` detects the transition and spawns a
   marine-faction shuttle at the compound.
3. Shuttle lands, deboards garrison troops (infantry tier, free —
   no ticket cost). Garrison forms a squad at the compound.
4. `ConquestCommand` assigns the garrison squad to `HOLD_NODE` at
   the compound's tactical node. Garrison defends; assault squads
   push on.
5. Defender reinforcement (`GarrisonDepletedTrigger`,
   `ObjectiveLostTrigger`) dispatches counter-attack forces via
   convoy / shuttle / walk-in toward the compound's zone.
6. If defenders overwhelm the garrison: compound flips back through
   CONTESTED → DEFENDER_HELD. Defender supply from that compound
   resumes.
7. Garrison system re-arms when compound returns to DEFENDER_HELD.
   If marines recapture, another garrison drop fires. Cycle repeats.

## V2 implementation slices

### Slice 1: CompoundGarrisonSystem

New class in `battle.compound`. Watches compound state, spawns marine
garrison shuttles on capture.

- 1 Hz slow-tick (same cadence as CompoundCaptureSystem)
- Per-compound dispatch tracking: NONE → DISPATCHED → RE_ARMED → ...
- Shuttle: AEROSHUTTLE, marine faction, infantry deboard, 1 cycle
- LZ via BFS from compound anchor; entry from marine map edge
- Free (no BattleResources ticket)
- Wired into BattleSimulation tick loop, Conquest-only via BattleSetup

### Slice 2: ConquestCommand HOLD_COMPOUND pass

New priority-0 pass in ConquestCommand (before SECURE_COMPOUND and
CLEAR_ZONE). For each MARINE_HELD compound, assign at most 1 squad
to HOLD_NODE — only squads whose current zone matches the compound's
zone. Garrison squads hold; assault squads push.

## What's NOT in this cut

- **Defender positive win condition** (`HoldCompoundsObjective`) —
  defender currently wins via elimination (`EliminateFactionObjective`).
  Adding "hold all compounds for N seconds" is a follow-up that enriches
  the defender's strategic arc but isn't required for the tug-of-war
  dynamic itself.
- **Marine-side compound supply** — captured compounds gating marine
  reinforcement (symmetric to defender supply model). Deferred; the
  garrison drop is the immediate mechanic.
- **Incoming garrison marker** — visual indicator on compound markers
  when a garrison shuttle is en route. Polish pass after the core
  mechanic proves out.

## Cross-refs

- [`central-keep.md`](central-keep.md) — V1 compound capture design
  (all 6 slices shipped). V2 section there outlines the full north-star
  territory model.
- [`../reinforcement/architecture.md`](../reinforcement/architecture.md)
  — the trigger/means pipeline that drives defender counter-attacks.
- [[battle_services_systems]] — Services vs Systems convention the
  garrison system follows.
