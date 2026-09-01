# Precinct mission wiring

Status: IN PROGRESS

Written: 2026-09-01

Read `precincts.md` in full first; it owns the model. `mission-tier-nouns.md`
owns tier and risk. This story is the first time a campaign battle reaches the
precinct recipe, and it is deliberately narrow: Assault and Raid only, because
their balance is not yet pinned, while Conquest stays on the stock crossroad by
the standing rule in `precincts.md`.

## Goal

An Assault or Raid battle against a real market generates its map as a set of
places — a settlement of the world's character, a garrison as hard as the
world's rating and the mission's demand allow, outlying places as the market
grows — instead of one grown settlement themed by a map-wide scatter.

## What is wrong today, found by reading rather than measuring

1. **The precinct entry point is instance state on a shared generator.**
   `BspCityGenerator.usePrecincts` stores the plan on the generator and
   `recipeFor` reads it back; `BattleSetup.MAP_GEN` is one static instance used
   by every factory. A plan set for one battle would be read by the next. The
   plan has to be an argument of the generate call.
2. **The derivation throws on a production-sized map.** `MapScale` is 112x64,
   144x80 and 280x168; `PrecinctPlan.derive` seeds places with a fixed 30-cell
   edge margin and a fixed 60-cell separation. On 112x64 the seedable span is
   52x4 cells, so the second seed is never found, `placeSeed` returns null, and
   the garrison branch dereferences it.
3. **A garrison program would swallow a medium map.** The garrison program's
   envelope is about 5300 cells with one airfield; a 144x80 map is 11520.
   `precincts.md` open item 1 records exactly this on 200x140. Nothing trims
   the program to the map.
4. **The Raid target is chosen from the high-X half.** `RaidTargetLayout` keeps
   the legacy low-X attacker / high-X defender split. On a precinct map the
   defender stands inside the objective's claim and the attacker arrives at the
   corner furthest from it, so the half is arbitrary. (`ExtractionPayloadLayout`
   and `SabotageSiteLayout` carry the same rule; they are not migrated here and
   keep it.)

## Design

### A. The plan is a per-call argument

- `MapGenerator` gains
  `generate(int width, int height, long seed, TraversalAxis axis, TargetProfile profile, PrecinctPlan precincts)`.
  The interface default delegates to the five-argument form when `precincts`
  is null and throws `UnsupportedOperationException` otherwise, since a
  generator that does not know places cannot build them.
- `BspCityGenerator` overrides it; `recipeFor` takes the plan. A non-null plan
  with a non-null axis is an `IllegalArgumentException`: Conquest is pinned to
  the stock recipe and that rule must not be reachable by accident.
- The `precinctOverride` field and `usePrecincts` are deleted. The three tests
  that used it (`MapLayoutTest`, `PrecinctInteriorTest`) pass the plan through
  the new overload. `useGrownRoads` is untouched.

### B. The plan fits the map it is given

All in `PrecinctPlan.derive`; nothing changes for the 560x336 measurements.

- **Margin and separation scale with the map.**
  `margin = min(EDGE_MARGIN, shortSide / 4)` and
  `separation = min(MIN_SEED_SEPARATION, longSide / 4)`. At 560x336 both are
  their old values; at 144x80 they are 20 and 36; at 112x64 they are 16 and 28.
  `MIN_SEED_SEPARATION` stays public with its value; add the derived form beside
  it and document why (a map seats what it can seat).
- **A garrison always gets a seed.** If rejection sampling fails, the garrison
  is seeded at the in-margin cell furthest from the seeds already placed —
  deterministic, no draw. A defended world never loses its objective to a
  small map. Outlying places keep today's behaviour: none when no seed fits.
- **The program is trimmed to the map.** Add
  `FortressProgram.fittedTo(int groundBudget)`: returns this program when
  `envelopeArea() <= groundBudget`, otherwise applies the ladder below one step
  at a time until it fits or the ladder is exhausted, and returns the result
  (over budget is allowed at the end of the ladder; the packer already records
  what it cannot place). Ladder, in order:
  1. airfields to 0 (a lot is the single largest item);
  2. `BARRACKS` 3 → 2;
  3. `CONTROL_ROOM` 4 → 2;
  4. `STOCKROOM` 2 → 1 and `PARTS_CAGE` 2 → 1;
  5. `ARMORY` 2 → 1;
  6. `BARRACKS` 2 → 1.
  The keep, gatehouse, vehicle shed and mess hall are never removed; a
  garrison with none of them is not a garrison. Use the existing `with` and
  `withAirfields`.
- `derive` passes `garrisonFor(profile).fittedTo(round(FIT * width * height))`
  with `FIT = 0.35f`, a named constant with a comment saying it is a first
  guess to be measured, not a tuned value.
- A programmed precinct's claim on a 144x80 defended map must be measured and
  reported: generate one map at 144x80 for a rating-5, size-6 market and
  report the garrison's claim as a share of the map and how many buildings
  went unplaced (`BspKeys.UNPLACED_PROGRAM`), and the same at 112x64. Put the
  numbers in the report, not in a test.

### C. Assault and Raid reach the recipe

In the nine-argument `BattleSetup.createPlaceholder`:

```java
PrecinctPlan precincts = precinctPlanFor(type, tier, risk, profile, scale, seed);
MapResult map = MAP_GEN.generate(scale.width, scale.height, seed, null, profile, precincts);
```

`precinctPlanFor` is package-private static so it can be unit-tested:

- returns `null` unless `type` is `ASSAULT` or `RAID`;
- returns `null` when `profile` is null or `profile.marketSize() <= 0` — the
  same gate `BspCityGenerator.recipeFor` uses for the grown recipe: a battle
  with no market behind it has nothing to derive from and takes the stock map;
- otherwise `PrecinctPlan.derive(profile, Sprawl.BALANCED,
  MissionFortification.demand(tier, risk), scale.width, scale.height,
  new Random(seed ^ PRECINCT_SEED_SALT))` with a fixed long salt, so the plan's
  draws are not the generator's own stream read twice.

`BALANCED` for both, on purpose: a Raid wants a target, and targets are points
of interest, which only settlement fills emit — a `REMOTE` raid map has none
and `RaidTargetLayout` would throw. Making tactical nodes eligible targets is
the follow-on that would unlock `REMOTE` for Raid; note it under known gaps.

Sabotage, Extraction, Civilian Rescue, Silent Colony and the opening operations
stay on the recipe they have.

### D. The Raid target is on the defender's side

`RaidTargetLayout.select` replaces `interiorAnchorX >= width / 2` with: the
point of interest is nearer the defender spawn than the marine spawn, by
squared distance, using `map.defenderSpawnX/Y` and `map.marineSpawnX/Y`. On the
stock map that is very nearly the old half; on a precinct map it is the side
the objective is on. Everything else about the selection is unchanged.

Unit-test it on a hand-built `MapResult` with spawns at two corners and points
of interest on either side of the bisector, including a non-residential point
on the marine side that must be refused and a residential one on the defender
side that must be refused.

## Acceptance

- `precinctPlanFor` is null for a neutral profile and for every mission type
  but Assault and Raid, and a Balanced plan with a garrison whose fortification
  is the tier-capped, risk-nudged resolution of the world's rating otherwise.
- `PrecinctPlan.derive` never throws at 112x64, 144x80, 280x168 or 560x336 for
  any market size 0–10 and rating 0–7, and a defended world always has exactly
  one programmed precinct at every size.
- `FortressProgram.fittedTo` is the identity when the program fits, walks the
  ladder in the stated order, and never removes the keep, gatehouse, shed or
  mess.
- The conquest and grown-legacy recipes are byte-identical: no recipe other
  than the precinct one reads the plan, and the plan is null for every other
  factory.
- The existing precinct, setup, fixture and command tests pass.
- `commanderEvidence -Pmission=assault` and `-Pmission=raid` run to completion
  before and after the change, and the report states the summary deltas
  (captures, losses, duration, exits) rather than asserting them, because the
  balance of these missions is not pinned and a delta is information, not a
  failure.

## Not in scope

- Conquest on a precinct map. `precincts.md` keeps it on the stock crossroad
  until the grown maps are judged.
- The claim lottery beyond the program fit above (`precincts.md` open items 1
  and 2).
- Tactical nodes as Raid targets, and `REMOTE` for Raid.
- The half-map rule in `ExtractionPayloadLayout` and `SabotageSiteLayout`.
