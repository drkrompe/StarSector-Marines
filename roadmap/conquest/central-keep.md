# Central keep + compound-as-supply

> Conquest's climactic phase is a push through tiered defender supply
> structures toward a central keep / command center. Each tactical
> compound (`COMMAND_POST` / `BARRACKS` / `ARMORY`) is a destructible
> reinforcement source — destroy them in order and supply dries up.
> Win = all defender compounds destroyed; remaining defenders are
> mop-up. Replaces the "kill every alive defender" condition that
> can never resolve once reinforcement loops in.

## Why

`EliminateFactionObjective` checks for zero alive defenders. With the
reinforcement system feeding garrison-depleted / objective-lost spawns
across multiple compounds, that condition only holds for a single tick
between waves — often never, because the last wave's stragglers stay
alive long enough for the next trigger to fire. Playtest read: marines
"take the city" visually long before the game ends.

The reframe is to gate supply on the actual supply structures. The
narrative is also stronger — you're not just killing defenders, you're
dismantling their ability to keep producing them. Brings Conquest in
line with how the player perceives "winning" a city push (you hit the
command center) and gives meta-progression to the map: the player
sees they have N compounds to take, watches the keep grow in
prominence as flanking compounds fall.

## Map shape

The Conquest generator already lays out a fortress district at the end
of the traversal axis with tactical nodes for the
`MILITARY_BASE` compound. The central keep extension makes that
end-of-axis fortress the visual + systemic climax:

- **Central keep**: one COMMAND_POST tactical node at the heart of a
  fortress complex deep in the fortress district. Largest building on
  the map. The "throne room" for the storming sequence.
- **Tiered compounds spread along the axis**: BARRACKS and ARMORY
  nodes scatter through PORT / KILL_ZONE / fortress edges so the
  player encounters them as they push. Outer compounds fall first
  (cheaper supply types tied to them — convoy / walk-in); inner
  compounds gate the higher-stakes shuttle drops; the COMMAND_POST
  is last and unlocks nothing extra (it's the win condition trigger).

Generator changes are scoped — most of the tactical-node layer already
exists. What's new is the keep's enlarged footprint and the explicit
"deepest" anchor that the COMMAND_POST sits in.

## Compound-as-supply model

Each compound kind becomes the supply source for one reinforcement
means. Triggers gate on the compound being alive + un-destroyed:

| Compound      | Supplies              | Trigger gate                            |
|---------------|-----------------------|-----------------------------------------|
| `BARRACKS`    | walk-in (`WalkInMeans`) | At least one BARRACKS alive            |
| `ARMORY`      | convoy (`ConvoyMeans`)  | At least one ARMORY alive              |
| `COMMAND_POST` | shuttle (`ShuttleMeans`) | At least one COMMAND_POST alive       |

When all compounds of a kind are destroyed, that means stops being
feasible — `canFulfill` returns false, the dispatcher falls through to
the next priority. Destroy every compound and the priority chain
exhausts → request is dropped (the existing "bugged-map" diagnostic
path handles this gracefully).

This also fixes a v3 quirk: the priority chain currently dispatches
convoy in preference to walk-in even when the player has cleared the
ARMORY — under the new gate, a cleared ARMORY means convoy reports
`canFulfill = false` and walk-in (gated on the surviving BARRACKS)
takes over. Same rally, different supply flavor — the player sees the
shift naturally.

## Destructibility

Compound tactical nodes need a destroyed state. Two reasonable
implementations:

1. **HP on the structure** — extend the `Building` registry to know
   which buildings correspond to which compound tactical nodes;
   destruction = building HP hits zero. Marines could blow walls /
   apply explosive damage to bring it down. Most narratively rich;
   most work.
2. **Zone-control proxy** — the compound is "destroyed" when its
   tactical zone is held by marines with zero alive defenders for N
   seconds. Reuses existing zone-ownership logic. Less narrative
   (no explosion, no rubble) but ship-faster.

V1 lands (2) — simpler, no new HP system, validates the rest of the
compound-as-supply flow. V2 promotes to (1) once the building HP /
breach system exists. Both paths converge on the same compound-state
representation: a per-node `destroyed: boolean` flag that triggers /
means / objective all read.

## Win condition

New `ConquestObjective` replaces `EliminateFactionObjective` for the
marine side on Conquest maps:

```
- All defender COMMAND_POST / BARRACKS / ARMORY nodes destroyed
- (Optional) at least one marine alive
```

That's it. Stragglers don't matter; the city's command infrastructure
is gone, the battle is over. The displayed objective text updates as
compounds fall: "X / N command structures remaining" — gives the
player a visible progress bar without surfacing internal counters.

Defender win condition stays `EliminateFactionObjective(MARINE)` for
now — defenders win if every marine dies regardless of compound
state.

## Defender behavior after compound loss

A squad assigned to a destroyed compound needs somewhere to go. The
[[tactical_linker_compound_fallback]] memory captures the existing
chain: `FALLBACK_TO` links route a squad to the next compound. With
v1 (zone-control proxy) the squad isn't necessarily dead when the
compound "falls" — it may have already dispersed via the existing
fallback mechanism. With v2 (building HP), the squad inside the
collapsing structure takes blast damage, and survivors fall back
along the chain.

No new code today — the existing `FALLBACK_TO` linker handles this
shape. The compound-as-supply layer just observes that compounds are
destroyed; it doesn't move defenders.

## Capture-and-hold (deferred)

The user-proposed "capture-and-hold" variant — marines need to hold
compound zones for N seconds, defenders re-enter to reclaim, oscillates
until commitment — is parked. Interesting design (an ally-faction
garrison-arrival mechanic would resolve the "but the player has to
move on" awkwardness), but it requires more from the AI commander
than we currently have. Filed under "future gamemode."

## Implementation slices

V1 ships in three commits, each independently testable:

1. **`destroyed` flag on TacticalNode + zone-control proxy detector**
   — sim ticks a `CompoundDestructionDetector` that watches each
   compound zone and flips `node.destroyed = true` after N seconds of
   marines-present + zero-defenders. Mirrors the
   `ObjectiveLostTrigger` shape but writes the flag instead of
   posting a request. No gameplay change yet (nothing reads the
   flag).
2. **Trigger + means gating on compound state** — extend
   `GarrisonDepletedTrigger` to skip destroyed compounds; teach
   `ConvoyMeans` / `WalkInMeans` / `ShuttleMeans` to return
   `canFulfill = false` when no alive compound of their supply kind
   exists. Reinforcement now naturally tapers as compounds fall.
3. **`ConquestObjective` win condition** — replaces the marine-side
   `EliminateFactionObjective` only on the Conquest mission factory
   in `BattleSetup.createConquest`. Other mission types keep the
   eliminate-faction objective.

Each slice compiles + tests + plays standalone. After slice 3 the
gameplay loop closes: compounds fall → supply dies → battle ends
cleanly. The map-shape work (enlarged central keep, deeper layout
hooks) can land separately as a v2 — the gameplay loop works on
existing Conquest maps without it.

## Cross-refs

- [`../reinforcement/architecture.md`](../reinforcement/architecture.md)
  — the orchestration layer this design extends. Triggers/means
  gain the compound-life gate; service shape itself doesn't change.
- [`../convoy/`](../convoy/) — convoy is the ARMORY-tier means.
- [[tactical_linker_compound_fallback]] — existing FALLBACK_TO chain
  defenders use after their compound falls.
- [[mission_type_flavors]] — Conquest is the per-zone objective-taking
  flavor; this doc formalizes the win condition for it.
