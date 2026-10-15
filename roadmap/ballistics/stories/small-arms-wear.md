# Small arms wear

Status: IN REVIEW

Written: 2026-10-15
Updated: 2026-10-15 — implemented; evidence recorded below, awaiting review

Read `ballistics-nouns.md`, `body-part-cover.md` and `combat-durability-nouns.md`
before changing this story.

## Why this lives under Ballistics

The open item it closes ("Small arms wearing cover down") is a Ballistics item:
the work is mostly about *which feature stopped a round* — a fact only the
contact walk knows — and carrying it to arrival. What happens to the feature
once struck stays in the map-structure boundary `combat-durability-nouns.md`
already owns (fences, sandbags, crates, barrels, trees, rocks, walls and shared
edges spending structure through `MapEditor`). This story adds one input to
that channel; it does not add a destruction pipeline.

## Scope

- A resolved round names the environment feature that stopped it: the crossed
  prop's cell for a `DOODAD_BLOCK`, the cell or shared edge whose edge cover
  caught it for a `COVER_CLIP`, the blocking cell for a `WALL`. Cover a man
  takes from the ground he stands in (a trench) is ground and is named by
  nothing.
- A **material** with a **rating** for each damageable environment family, in
  one table keyed by the existing prop families, barrier kinds and structural
  walls.
- At arrival, a round without an area payload spends its direct payload damage
  on that feature through `MapEditor`, scaled by the round's penetration against
  the material's rating. Area rounds keep doing their structural damage through
  their detonation's authored wall damage; nothing is counted twice.

## Constraints

- One law, data-driven: no small-arms branch. A rifle does nothing to a wall
  because its penetration is too low for the wall's rating.
- The resolver stays pure and parallel-safe; it only records the feature.
  Writes happen serially when the round's flight clock runs out.
- No extra random draws. Attribution of a cover catch to edge cover versus a
  trench reuses the catch's own roll. A fight in the open replays
  byte-identically.
- A cheap serial queue, one small record per round that actually stopped in
  the environment; misses into open ground allocate nothing new.

## Material law

Materials share the armour law's penetration-to-rating ratio and its ceiling
(meeting the rating removes structure at full payload damage). They do not take
its 10% chip floor: a structural wall that every round chips at 10% falls to a
rifle squad in seconds. Instead a material ignores rounds whose penetration is
below half its rating and rises linearly to full at the rating.

## Acceptance

- [x] Unit: material table and efficiency; resolver attribution for each stop
  kind; a rifle round into a crate wears and eventually destroys it (cover
  gone); the same round into a wall does nothing; an anti-materiel round does
  damage the wall; a trench takes nothing.
- [x] Scene `small-arms-wear`: crates shot away, sandbags worn, a wall that
  stands against rifles and yields to an anti-materiel rifle. 1920x1080, 100%.
- [x] `sceneEvidence` for the new scene plus `fighting-position`,
  `assault-muster`, `late-arrival`, and `commanderEvidence` for
  `conquest-trial` and `relief`, compared against unmodified `main`.
- [x] Full `:test` green.

## Evidence

Baseline is the branch base `a3435d196` (unmodified main when the story began).

- Scene `small-arms-wear`, all verdicts PASS. Crates: 5 of 9 shot away, the
  first at 31.1s, 45 rounds caught, 14 structure per rifle round. Sandbags:
  6.0 per rifle round, 2 of 4 gone, the first at 95.0s. Wall under rifles: 726
  rounds stopped in masonry, 0 HP lost. Wall under an anti-materiel rifle: 2
  cells breached, the first at 6.9s, 281 HP lost. The anti-materiel loop fires
  on a script, because the rifle's AI policy holds it for hardened targets.
- `fighting-position`, `assault-muster`, `late-arrival`: every loop PASS in
  both trees, with every reading identical.
- `commanderEvidence relief`: report byte-identical to baseline.
- `commanderEvidence conquest-trial`: MARINE wins in the same seven fixtures,
  and `on-path-41-rifles` times out unchanged. `offset-42` and `offset-43` are
  identical. Elsewhere, timings and losses move as cover now erodes. Marine
  losses are lower or equal in five of the six changed fixtures; `on-path-42`
  resolves 1,650 ticks sooner.

## Plan

1. `DurabilityModel.materialEfficiency`; `EnvironmentMaterial` table.
2. `BallisticResolver.Resolution.struck()` for the three stop kinds.
3. `ShotService` strike queue; callers queue a strike for direct payloads.
4. `EnvironmentWear` applies a strike through `MapEditor`.
5. Tests, scene, evidence, docs.
