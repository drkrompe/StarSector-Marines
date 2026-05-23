# Central keep + compound-as-supply

> Conquest's climactic phase is a push through tiered defender supply
> structures toward a central keep. Compounds (`COMMAND_POST` /
> `BARRACKS` / `ARMORY`) are capture-and-hold objectives that gate
> reinforcement supply — neutralise them in order and the defender's
> ability to replenish drains. The long-term shape is a territory
> tug-of-war (re-captures, auto-garrisons via shuttle drop); the v1
> cut is the one-way "destroy them all" subset of that, scoped down
> to ship the gameplay loop without the AI-commander dependencies.

## Why

`EliminateFactionObjective` checks for zero alive defenders. With the
reinforcement system feeding garrison-depleted / objective-lost spawns
across multiple compounds, that condition only holds for a single tick
between waves — often never, because the last wave's stragglers stay
alive long enough for the next trigger to fire. Playtest read: marines
"take the city" visually long before the game ends.

The reframe gates supply on the actual supply structures. You're not
just killing defenders, you're dismantling their ability to produce
them. Brings Conquest in line with how the player perceives "winning"
a city push (you hit the command center) and gives meta-progression
to the map: the player sees N compounds to take, watches the keep
grow in prominence as flanking compounds fall.

## North star — territory tug-of-war

The destination is a back-and-forth landwar:

- Marines storm a compound; once captured, the compound is **theirs**
  (not destroyed) — defender supply from that compound stops, marine
  supply lines can extend through it.
- Defender AI commander reacts: an allied **auto-garrison drop**
  shuttles in fresh defenders to retake the compound. If marines have
  moved on, the compound flips back — defender supply resumes,
  reinforcement waves squeeze marines from behind their front.
- Marines must commit: leave a squad to hold (and reinforce that
  squad), or accept that the compound will fall back.
- Win condition over time = hold N compounds (or all compounds) for
  a sustained period, including the keep.

This reads as landwar with territory capture — superior gameplay
texture to "kill the last unit." The implication for v1: design the
state machine and rendering with **capture-and-hold** as the model
from the start, even if the only reverse transition we ship initially
is the trivial one.

## V1 cut — one-way capture flow

Ships the smallest slice that closes the gameplay loop, using the same
state machine the tug-of-war will use:

- Compound transitions: `defender-held` → `contested` → `marine-held`.
- The reverse transition (`marine-held` → `contested` → `defender-held`)
  exists in code but isn't *triggered* by any production path in v1 —
  marines hold what they take. The "auto-garrison shuttle drop" path
  that drives the reverse transition is the v2 piece pending the AI
  commander work.
- `marine-held` is the absorbing state today; in v2 it's just another
  transition.

This is the key concession: V1 implements the full state machine and
rendering, **then** the production paths use only the forward
transitions. V2 unlocks the reverse without re-architecting.

## Compound state machine

States per compound:

| State           | Meaning                                                |
|-----------------|--------------------------------------------------------|
| `DEFENDER_HELD` | Defenders occupy the zone; reinforcement supply active.|
| `CONTESTED`     | Mixed presence or transition in progress; supply still active. |
| `MARINE_HELD`   | Marines hold the zone with no defenders for ≥ hold-time; supply dead. |

Transitions (v1 = forward only; v2 adds reverses):

- `DEFENDER_HELD → CONTESTED` — first marine enters the compound zone.
- `CONTESTED → MARINE_HELD` — zone has marines, zero defenders, for
  `MARINE_HOLD_TIME` sim-seconds.
- `MARINE_HELD → CONTESTED` *(v2)* — first defender re-enters.
- `CONTESTED → DEFENDER_HELD` *(v2)* — zone has defenders, zero
  marines, for `DEFENDER_HOLD_TIME` sim-seconds.

Hold-times are asymmetric on purpose: marines have to *commit* to
capture (slow flip), but defenders auto-reclaim faster (fast flip)
because the defender side has the home-territory advantage.

## Map state visibility (compound markers)

Each compound needs an on-map marker so the player can read the state
without inspecting the unit list. Same lineage as the sabotage charge-
site markers — a sprite anchored at the compound centroid showing:

- **Faction colour ring** — red (defender-held), yellow (contested),
  blue/player (marine-held), grey (destroyed; v2 only).
- **Capture progress arc** — partial circle filling as the hold-timer
  accumulates. Mirrors charge-site progress bars; the player learns to
  read "almost flipped" at a glance.
- **Kind glyph** — small icon distinguishing COMMAND_POST / BARRACKS
  / ARMORY so the player knows which supply they're disabling.

Renders in the world layer (under units, over floor) with a HUD-overlay
copy in the top-of-screen objective strip showing aggregate progress
("3 of 5 compounds taken"). The world-anchored marker is the primary
read; the HUD strip is the at-a-glance progress check.

## Compound-as-supply model

Each compound kind gates one reinforcement means:

| Compound       | Supplies                  | Trigger gate                    |
|----------------|---------------------------|---------------------------------|
| `BARRACKS`     | walk-in (`WalkInMeans`)   | At least one BARRACKS held by defender |
| `ARMORY`       | convoy (`ConvoyMeans`)    | At least one ARMORY held by defender   |
| `COMMAND_POST` | shuttle (`ShuttleMeans`)  | At least one COMMAND_POST held by defender |

"Held by defender" here includes `CONTESTED` — the supply structure is
still standing, it's just being fought over. Supply only dies when every
compound of a kind has fully flipped to `MARINE_HELD`. A mid-firefight
contest mustn't strand the defender's resupply mid-wave; the player
has to actually finish the capture before the chain retires.

When every compound of a kind reaches `MARINE_HELD`, that means returns
`canFulfill = false`, and the dispatcher falls through to the next
priority. Flip every compound of every kind and the chain exhausts →
request drops via the existing bugged-map diagnostic. Symmetric in v2:
marine-side reinforcement gates on **marine-held** compounds (an
ARMORY captured by marines could later supply marine convoys).

This also fixes a v3 quirk: the priority chain currently dispatches
convoy in preference to walk-in even when the player has cleared the
ARMORY. Under the new gate, a captured ARMORY removes convoy from the
chain; walk-in (gated on the surviving BARRACKS) takes over. Same
rally, different supply flavour — the player sees the shift naturally.

## Win condition

Marine side gets a new `ConquestObjective`:

```
- All defender compounds (COMMAND_POST / BARRACKS / ARMORY) in
  MARINE_HELD state
- At least one marine alive
```

Stragglers don't matter; supply infrastructure has flipped, the
battle is over. Displayed objective text updates as compounds flip:
"3 / 5 supply hubs captured" — gives the player a progress read
without surfacing internal counters.

Defender win condition stays `EliminateFactionObjective(MARINE)` for
v1 — defenders win if every marine dies regardless of compound state.
In v2 (territory tug-of-war), defender win becomes "all compounds
DEFENDER_HELD for a sustained period," giving defenders a
counter-objective.

## Map shape

The Conquest generator already lays out a fortress district at the end
of the traversal axis with tactical nodes for the `MILITARY_BASE`
compound. The central-keep extension makes that end-of-axis fortress
the visual + systemic climax:

- **Central keep**: one COMMAND_POST tactical node at the heart of a
  fortress complex deep in the fortress district. Largest building on
  the map. The "throne room" for the storming sequence.
- **Tiered compounds spread along the axis**: BARRACKS and ARMORY
  nodes scatter through PORT / KILL_ZONE / fortress edges so the
  player encounters them as they push. Outer compounds fall first
  (cheaper supply types tied to them — walk-in / convoy); inner
  compounds gate shuttle drops; the COMMAND_POST is last (and
  generally also the keep's anchor).

### BSP compound generation

A "compound" is more than one tactical-node anchor — it's a region
containing the compound building, a defensive perimeter, and the
turret + garrison placements that defend it. The BSP generator gets
a new pass (`CompoundFiller` sibling to `MilitaryBaseFiller`) that:

1. Selects a target BSP leaf in the appropriate biome (FORTRESS for
   COMMAND_POST, anywhere defender-side for BARRACKS / ARMORY).
2. Carves a footprint — interior cells walkable, perimeter walls with
   one or two gate openings facing the attacker side.
3. Stamps the compound building (multi-cell building entry in the
   `Buildings` registry, with the right `BuildingKind` so vision /
   roof rendering handles it like other built structures).
4. Emits the tactical node at the building centroid with the right
   `Kind` + `defaultGuard = DEFENDER`.
5. Calls the defence-generation pass (next section) to populate
   garrison + turret placements around the perimeter.

Per-kind compound size:

- `BARRACKS`: medium (≈ 6x6 interior). Garrison squad size: 4-6.
- `ARMORY`: small-medium (≈ 5x5). Lighter garrison (3-5), more
  turrets pointed at the road approach (it's where the convoy comes
  from).
- `COMMAND_POST`: small standalone (≈ 4x4) when not inside the keep;
  the keep variant (see keep generation) supersedes this footprint.

### Compound defences

Each compound gets a defence pass that places garrison squads + turret
emplacements. The intelligence here is:

- **Garrison placement** — squad members spawn inside the compound
  interior on high-cover cells, biased toward windows / doorways
  facing the attacker side. Reuses the existing
  `BattleSetup.pickCellsNear` cover-sort.
- **Turret emplacements** — 1-2 turrets per compound, anchored on the
  perimeter wall, facing the attacker side of the traversal axis. New
  `GUARDPOST` tactical nodes already model this shape; the compound
  pass emits 1-2 GUARDPOSTs adjacent to the compound's exterior.
- **Doctrine** — `BARRACKS` favours SMG / pulse-rifle militia (close-
  range, defending interior); `ARMORY` favours DMR + a mortar turret
  (lobs at incoming convoys-worth-of-distance); `COMMAND_POST` mixes
  both plus a `MARINE_RED` elite slot.

Defence intensity scales with `RiskLevel`: LOW = 1 squad + 1 turret;
MEDIUM = 1 squad + 1-2 turrets; HIGH = 2 squads + 2 turrets. Pulled
from the existing `DefenderRoster` ratios so the totals stay coherent
with the rest of the defender count.

### Keep generation

> **Status (2026-05-22):** the multi-chamber work for the keep moved
> into its own refactor track at
> [`../mapgen/`](../mapgen/) — the abstraction needs to
> generalize past the keep so station / ship-interior fills can reuse
> it. Slice 6 below ships the binary-partition antechamber pattern;
> the three-chamber design (entry / inner / throne) lands when
> [`../mapgen/room-purpose-refactor.md`](../mapgen/room-purpose-refactor.md)
> reaches Slice C/D.

The keep is one big compound but with **multi-room internal structure**
so the storming sequence reads as a sequence of rooms, not a single
push. Generator layout:

- Outer perimeter wall with one gate facing the attacker side.
- A courtyard / anteroom between the gate and the inner structure,
  with its own garrison + 1-2 turret emplacements (the "outer
  guards").
- An inner building containing 2-3 chambers connected by interior
  doorways:
  - **Entry chamber** — first room past the inner door. Mixed
    garrison; can fall back to the next chamber.
  - **Inner chamber** — second room. Elite-heavy garrison
    (MARINE_RED majority).
  - **Throne room** — innermost chamber containing the COMMAND_POST
    tactical node anchor. Mech lance or heavy-elite garrison.
    Capturing this is the final beat.

Multiple garrison parties per room means falling back through the
keep isn't an all-or-nothing collapse — each room has its own
hold-fire-until-engaged dynamic, and the player feels progress as
each chamber is taken. The keep's COMMAND_POST capture marker
displays at the throne-room anchor; the keep's outer perimeter shows
a separate "outer keep" indicator so the player can read partial
progress.

Keep emission is gated to Conquest maps and sized to risk: a LOW
Conquest gets the courtyard + one inner chamber; HIGH gets the
full three-chamber layout.

## Decomposition — Services and Systems

Per [[battle_services_systems]] — *Services own state, Systems are
stateless tick consumers*. The compound layer splits cleanly:

### `CompoundService` (new)

Stateful registry. Owns:

- Per-compound state record (`TacticalNode` reference, current
  `CompoundState`, hold-time accumulator, capture progress in [0, 1]).
- Per-faction roll-up cache (`hasAliveCompound(kind, faction)`) so
  triggers + means can ask O(1) "is any BARRACKS still defender-held?"

Reads:

- `GarrisonDepletedTrigger.check` — skip compounds whose state is
  `MARINE_HELD`.
- `ConvoyMeans.canFulfill` — false when no ARMORY is defender-held.
- `WalkInMeans.canFulfill` — false when no BARRACKS is defender-held.
- `ShuttleMeans.canFulfill` — false when no COMMAND_POST is
  defender-held.
- `ConquestObjective` — true when all defender compounds are
  marine-held.

Writes: only `CompoundCaptureSystem`.

**Gating convention** — the canonical gate lives in
`*Means.canFulfill`, not the trigger. Triggers post intent ("a rally
near zone X needs help"); means decide whether they can deliver. So
`ObjectiveLostTrigger` and any future trigger don't query
`hasAliveCompound` themselves — they post unconditionally, and the
means rejects when supply is dead. `GarrisonDepletedTrigger`'s
MARINE_HELD skip is a *log-clean* optimisation (a captured compound
can't be the source of a depletion call because no defender squad
remains assigned to it), not a load-bearing gate.

### `CompoundCaptureSystem` (new, stateless)

Slow-tick consumer (matching `ReinforcementService.tick`'s 1 Hz
cadence). Each tick:

1. For each compound, sample its zone's occupancy via `ZoneGraph`
   + `ZoneQueries.zoneClear`.
2. Apply the state machine: increment hold-timer toward `MARINE_HELD`
   when conditions hold, decrement on contest, flip on threshold.
3. Update `CompoundService` state + progress.

No state of its own. Same pattern as the rest of the
SquadAlertSystem / SquadFallbackSystem extractions that landed in
sibling work.

### `CompoundMarkerRenderer` (new, in `ui` package)

Reads `CompoundService` each frame, renders the world-anchored
markers + the HUD progress strip. Stateless — pure derivation from
service state.

### Why the split

Three reasons:

1. **Test surface**: the state-machine logic in `CompoundCaptureSystem`
   has no UI / no rendering, so a unit test feeds it a fake service +
   fake zone occupancy and walks the transitions.
2. **Long-term tug-of-war**: when v2 lands the reverse transitions,
   only `CompoundCaptureSystem`'s state machine changes — service
   shape, trigger/means read paths, renderer all stay put.
3. **Sibling-system parity**: matches `EffectsService`,
   `DamageService`, `NavigationService`, `UnitRosterService` — the
   refactor direction the codebase is already moving.

## V1 implementation slices

Each slice compiles + tests + plays standalone:

1. **`CompoundService` + `CompoundCaptureSystem` (no consumers)**
   — service holds per-compound state, system ticks the forward
   transitions. No trigger / means / objective reads yet — the
   service state is a write-only-by-system observation. Test: walk a
   compound through DEFENDER_HELD → CONTESTED → MARINE_HELD via the
   system + assert state transitions.
2. **Compound markers (world + HUD)** — `CompoundMarkerRenderer`
   reads service state, draws the faction-coloured ring + progress
   arc at each compound anchor, plus the HUD progress strip. Lets
   the user *see* slice 1's state machine working in playtest before
   anything else gates on it. Sabotage charge-site markers are the
   reference visual.
3. **Trigger + means gating** — `GarrisonDepletedTrigger` skips
   marine-held compounds; `ConvoyMeans` / `WalkInMeans` /
   `ShuttleMeans` query `CompoundService.hasAliveCompound(...)` in
   `canFulfill`. Reinforcement now naturally tapers as compounds
   flip.
4. **`ConquestObjective`** — replaces the marine-side
   `EliminateFactionObjective` only on the Conquest mission factory.
   Win on all compounds marine-held.
5. **BSP compound generation pass** — `CompoundFiller` pass +
   per-kind compound shapes + defence pass. Replaces the existing
   `MilitaryBaseFiller`'s less-structured emission with a richer
   compound topology.
6. **Keep generation** — multi-room interior for the
   COMMAND_POST-anchored keep. The largest map-shape change; lands
   last because the rest of the loop already works on existing maps.

Slices 1-4 close the gameplay loop on existing maps (no map-shape
changes); 5-6 are polish + climactic-feel upgrades. The user-facing
win/loss should already feel right after slice 4.

## V2 — territory tug-of-war (the north star)

Once the AI commander is rich enough to drive proactive recapture
behaviour:

- Add `MARINE_HELD → CONTESTED → DEFENDER_HELD` transitions to
  `CompoundCaptureSystem` (the asymmetric hold-times already
  designed above).
- New `AutoGarrisonTrigger` posts a defender shuttle-drop request
  targeting a recently-captured compound. Reuses `ShuttleMeans` —
  no new delivery layer.
- Marine-side reinforcement gates on **marine-held** compounds so
  captured ARMORY/BARRACKS can supply *marine* convoys / walk-ins.
  Symmetric to the defender model.
- Defender win condition flips from "kill every marine" to "hold
  all compounds for N seconds" — gives defenders a positive
  objective instead of an attrition one.
- Compound markers gain "incoming garrison drop" state so the
  player knows when an auto-garrison is en route (gives them a
  decision: hold or move on).

## Cross-refs

- [`../reinforcement/architecture.md`](../reinforcement/architecture.md)
  — the orchestration layer this design extends. Triggers/means
  gain compound-state gates; service shape itself doesn't change.
- [`../convoy/`](../convoy/) — convoy is the ARMORY-tier means.
- [[battle_services_systems]] — the *Service (stateful, constructor-
  injected) vs *System (stateless tick consumer) convention the
  decomposition follows.
- [[tactical_linker_compound_fallback]] — existing FALLBACK_TO chain
  defenders use after their compound falls. Doesn't need changes for
  v1; the falling-back squads are still the same squads.
- [[mission_type_flavors]] — Conquest is the per-zone objective-
  taking flavor; this doc formalizes the win condition for it.
