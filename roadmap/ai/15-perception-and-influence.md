# 15 — Perception & Influence (Intel Sim + Commander Heatmap)

**Parked design doc.** Captures the architectural shape of the
perception layer (per-squad belief about enemy positions) and the
influence-map layer (commander-tier smoothing of that belief). Not
scheduled as a single block — the *cheap wins* surfaced during the
BreakLOS investigation lay groundwork inside the existing nav grid
and TacticalScoring without committing to the full system. Document
so the shape is committed before any tactical-layer fix accidentally
bakes a "squads are omniscient" assumption deeper into the codebase.

## Why this exists

The BreakLOS investigation surfaced two distinct problems hiding
behind the same symptom (squads that *say* they want to break LoS
but appear to do nothing):

1. **`findFallbackPosition` rejects every candidate** when
   `isHiddenFromAllEnemies` finds any enemy with LoS to the cell —
   in open terrain, every cell within `FALLBACK_SCAN_RANGE = 8`
   fails that filter and the picker returns the unit's own cell.
   See `TacticalScoring.java:647`.
2. **Squads react to enemies they have no business knowing about.**
   `WorldStateBuilder.HAS_LOS_TO_TARGET` (WorldStateBuilder.java:111)
   iterates `sim.getUnits()` with no gate for "does this squad
   actually know about that unit." A garrison squad gets pulled
   across the map by a skirmish it hasn't observed.

Both are perception problems wearing tactical-AI clothes. The
near-term tactical fix (threat-direction cover scoring, ranged LoS
in the fallback picker, threat-set gate on `HAS_LOS_TO_TARGET`) is
a down-payment on the system documented here — it solves the
immediate symptoms while laying the data-flow groundwork for the
proper perception layer.

The commander tier ([`12-squad-of-squads.md`](12-squad-of-squads.md))
also needs this. A commander that reads global ground-truth enemy
positions cheats — and erases the entire point of the perception
system below it. The heatmap shape below is the commander's
consumable read of *its own faction's belief*, not omniscient state.

## Two layers, one belief

```
Tier C — Commander heatmap                    (1–4 Hz, per-faction)
  Owns: downsampled scalar influence field.
  Reads: this commander's threat set + own units.
  Produces: frontline contour, bulge/breakthrough detection,
            objective scoring, reserve commitment decisions.

Tier B — Squad belief                          (~event-driven + decay tick)
  Owns: per-squad map of believed enemy positions.
  Populated by: direct LoS observation + audio detection +
                commander briefings (push from Tier C).
  Consumed by: WorldStateBuilder predicates, TacticalScoring
               candidate filters, BreakLOS fallback picker.

Tier A — Unit execution                        (every tick — unchanged)
  Owns: per-unit action tick. No perception state of its own;
        reads through its squad's belief.
```

The intent: **every tier plans against its own belief**, briefings
push facts from a commander's belief down to subordinates' beliefs,
and nothing in the AI ever reads global ground truth.

## Squad belief shape

Per-`Squad` field — a small map keyed on unit id:

```java
record BelievedContact(
    int unitId,
    int lastSeenCellX,
    int lastSeenCellY,
    int lastSeenTick,             // for decay
    float confidence              // [0..1], decays per tick
) {}

Map<Integer, BelievedContact> believedEnemies;
```

**Population sources:**

1. **Direct observation** — any squad member with `hasLineOfSight`
   to a hostile unit stamps a `BelievedContact` with `confidence =
   1.0` and the current tick.
2. **Audio detection** — every shot / detonation / loud event
   emits a `NoiseEvent { x, y, magnitude, sourceUnitId }` onto a
   per-tick noise bus. Each squad rolls detection per event at
   N Hz; detection probability is a function of `magnitude /
   distance²` (with line-of-sight bonus removed — sound goes
   around walls). Successful detection stamps a `BelievedContact`
   with attenuated confidence (e.g. `0.4–0.7`) and the noise
   event's position (not the source unit's true position — sound
   localization is imperfect).
3. **Commander briefing** — Tier C can `push(squadId,
   BelievedContact)` to inject contacts the squad couldn't observe
   itself. This is how the heatmap-driven "redirect reserves"
   decisions reach the squads.

**Decay:** per replan (or per N ticks), confidence decreases by a
per-unit-type rate. Below a threshold the contact drops from the
map. Decay is the only way contacts disappear without a fresh
observation overwriting them — a squad that watched an enemy duck
behind a building still believes the enemy is there until either
(a) they see them somewhere else, (b) decay clears the contact, or
(c) the commander updates them.

**Where this plugs in:**

- `WorldStateBuilder.HAS_LOS_TO_TARGET` iterates
  `squad.believedEnemies` instead of `sim.getUnits()`. Same with
  every predicate that asks "is there an enemy who…".
- `TacticalScoring.isHiddenFromAllEnemies` becomes
  `isHiddenFromKnownEnemies` — LoS against believed positions, not
  ground truth.
- Tactical scorers (target picking, firing position, fallback
  picker) all read believed contacts.

## Commander influence map

Coarse downsample of the nav grid — a 200×200 battle becomes a
~25×25 tactical grid (8× downsample). One scalar per tactical
cell per channel. Updated at 1–4 Hz.

**Channels (day-one minimum):**

- `friendly_influence` — sum of allied-unit emissions with BFS
  propagation across walkable cells. Walls block. Topology-
  respecting, not Euclidean falloff.
- `hostile_believed` — same emission shape, sourced from the
  commander's own threat set (a roll-up of its squads' believed
  contacts, plus its own pushed contacts). **Critical:** this is
  *not* ground truth — bad intel produces a wrong heatmap which
  produces wrong commander decisions, and that's the desired
  gameplay feedback loop.

**Channels to add later (when concrete consumers appear):**

- `objective_pull` — gradient toward mission objectives.
- `recent_casualty` — repulsive field around recent friendly
  deaths.
- `supply` / `cover_density` — terrain-derived.

**Propagation:** BFS from each source with per-step attenuation
(e.g. `value *= 0.85` per cell). Closed-form distance is wrong in
urban terrain; influence shouldn't leak through buildings.
Computed lazily — re-propagate only when sources move significantly
or when staleness exceeds a threshold.

## What the higher-tier behaviors compute on the field

Once the two-channel field exists, the named tactical concepts fall
out of analyses on it rather than from bespoke logic:

- **Frontline** = zero-crossing of `friendly - hostile`. Trace the
  contour, you get a polyline. Commander uses it to identify the
  active engagement axis.
- **Our bulge** = convexity in that contour pushed into hostile
  territory. Triggers an opportunity/risk eval (perimeter-to-area
  ratio = vulnerability to envelopment).
- **Their bulge / saggy line** = concavity. Commander's signal for
  "send reserves here."
- **Breakthrough** = `hostile_believed > threshold` inside a region
  surrounded on 3+ sides by `friendly_influence > threshold`. Fires
  a "plug" goal at the nearest uncommitted squad.
- **Flat sector** = local gradient magnitude near zero on the
  contour — equal pressure, decisive squad-commit zone.

The commander tier doesn't author tactical actions. It picks goals
based on its heatmap reads, then converts those into
`ObjectiveAssignment`s (per `12-squad-of-squads.md`) which become
briefings — and briefings populate the assigned squads' belief
with the relevant contacts.

## Per-faction belief is the honesty mechanism

Each commander has its own threat set, computed by aggregating
*its faction's* squads' believed contacts (plus deduplication and
confidence merging when two squads report the same hostile). The
heatmap is computed from that aggregated belief.

Two commanders on opposite sides have entirely different heatmaps.
Neither sees ground truth. This:

- Preserves fog-of-war at the strategic layer.
- Makes flanking actually work — patient enemies can stay out of
  perception range and arrive unseen.
- Gives a clean tuning knob for difficulty: militia commanders
  have higher decay rates and worse audio detection; marine
  commanders have sharp ears and slow decay.

## Debug visualization (non-negotiable)

Both layers are undebuggable without rendering. Required from the
first commit of each layer:

- **Squad-belief overlay** — ghost markers at each squad's
  `believedEnemies` cells, color/alpha by confidence and
  staleness. Toggleable per-squad.
- **Heatmap overlay** — multi-channel false-color render of the
  tactical grid, toggleable per channel. Show the zero-crossing
  contour and flag detected bulges / breakthroughs explicitly.

Without these, the first time a commander makes a "wrong" decision
based on stale intel, you'll have no way to tell whether the bug
is in the belief, the smoothing, or the analysis. Render-first.

## Composable behaviors interaction

Perception ties into the composable-behaviors / training-tier model
from the BreakLOS discussion:

- **Per-unit-type audio detection rate.** Militia have crap ears;
  marines hear well; recon hears across the map.
- **Per-unit-type decay rate.** Trained units retain contacts
  longer ("disciplined memory"); militia forget fast.
- **Action set gating.** Trained squads' action sets include
  `Leapfrog`, `CoveredRetreat`, etc. Militia get `Flee` — and
  "flee" decisions are driven by their own (probably wrong)
  belief, which makes them break in places trained squads wouldn't.

## Near-term cheap wins (BreakLOS investigation outcomes)

These ship as a separate tactical task and do *not* require the
full perception system. They lay the right data flow so the full
system slots in cleanly later:

1. **Threat-direction cover scoring** in `findFallbackPosition` —
   read `NavigationGrid.coverByFacing[facingFor(threatDx, threatDy)]`
   instead of `getCoverAt`. Already-baked data; one-line math
   change at TacticalScoring.java:670.
2. **Scan range scaling with move speed** — bump
   `FALLBACK_SCAN_RANGE` and let it scale per unit (fast units
   scan farther). TacticalScoring.java:101.
3. **Ranged LoS variant** — new `hasLineOfSightWithin(x0, y0, x1,
   y1, maxCells)` on `NavigationGrid`. Used **only** in
   `isHiddenFromAllEnemies` initially, so distant enemies stop
   rejecting fallback candidates. Other LoS sites keep the
   unbounded primitive until the full perception layer ships.
4. **Threat-set gate on `HAS_LOS_TO_TARGET`** — interim: filter
   `WorldStateBuilder`'s enemy iteration by "within N cells of
   any squad member's `lastSeenEnemy` cluster." This is the
   minimum-viable stand-in for the full belief map and removes
   the worst pull-across-map behavior. The full belief map
   replaces this when it ships.

The BreakLOS-specific `Hunker` action discussed earlier is
explicitly **cut** — sitting in cover under fire reads visually as
"the AI gave up" and is functionally indistinguishable from poor
morale. Movement away from threat is what the player needs to see.

## Out of scope here

- **Cross-faction perception leakage.** No "this faction sees what
  that faction sees" — each side's belief is independent.
- **Player override of perception.** Player-controlled squads run
  on player inputs; perception modeling for the player faction is
  cosmetic (debug overlays only).
- **Persistence.** Beliefs are battle-transient like the rest of
  `BattleSimulation`. No save/load involvement.
- **Resource economy for noise/intel.** No "intel points" or
  "scout drones" — perception is emergent from unit positions,
  weapons fire, and decay, not from a spend-from-pool action.
- **Detailed tuning weights.** Decay rates, detection
  probabilities, BFS attenuation factors — all need playtest
  iteration. This doc is shape-only.

## Status

**Parked.** Near-term cheap wins (see above) ship as a tactical
task and provide the seam for the full system. The full
perception + heatmap layers queue after:

- The cheap wins are in and squad behavior is grounded enough to
  validate against.
- The squad-of-squads commander (`12-squad-of-squads.md`) lands
  with the assignment shape, because the heatmap is most useful
  to a commander that's already doing assignment work.

When ready to implement (rough order):

1. `NoiseEvent` bus + producer-side wiring at every shot /
   detonation / loud event in `BattleSimulation`.
2. `BelievedContact` + `Map<Integer, BelievedContact>
   believedEnemies` on `Squad`. Population from direct LoS
   replaces the current `lastSeenEnemy` field.
3. Audio detection roll per squad per noise event.
4. Decay tick — per replan or per N ticks.
5. Swap `WorldStateBuilder` predicates + `TacticalScoring`
   filters to read believed contacts.
6. Debug overlays for squad belief.
7. Tactical-grid downsampler + BFS propagation.
8. Two-channel commander field (`friendly_influence`,
   `hostile_believed`) computed from one commander's aggregated
   beliefs.
9. Frontline / bulge / breakthrough analyzers.
10. Heatmap debug overlay.
11. Wire commander decisions (assignment changes) to use the
    heatmap.

## Cross-references

- [Squad-of-squads commander](12-squad-of-squads.md) — primary
  consumer of the heatmap; assignment shape feeds belief
  briefings.
- [Stage 2 tactical stories](10-tactical-stories.md) — Story D
  (patrol intercept via alert spread) and Story E (flank-angle
  emergence) become trivially expressible once squads have
  proper belief.
- `NavigationGrid` — owns the per-facing cover model the
  threat-direction scoring reads.
- `TacticalScoring.findFallbackPosition` — primary near-term
  consumer of the threat-set + ranged-LoS changes.
- Memory: `[[mission_type_flavors]]` (Assault is search-and-
  destroy and most needs perception), `[[long_term_vision_sub_game]]`.
