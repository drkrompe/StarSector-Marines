# Swarm strains: Brute and Spitter

Status: READY

Written: 2026-10-21

Model: `colony-breach-nouns.md` (Swarm strain).

## Scope

1. **Unit types.** Append `SWARM_BRUTE` and `SWARM_SPITTER` to `UnitType`
   (append-only) with stats sized against the Runner's 20 HP and the
   breakpoints in `rescue-pressure-acceptance.md`. Brute: slow, armoured
   (plate that small arms struggle with, rockets and DMRs answer), heavy melee.
   Spitter: fragile, ranged acid lob (arcing, splash, short DoT if the damage
   model supports it cheaply), able to target low-flying aircraft.
2. **Bodies.** Two new `LayeredArmorFamily` entries (append-only) with
   top-down layered art in the XENO style: brute visibly larger and plated,
   spitter with a sac/launcher silhouette. Generate art through the
   `codex-imagegen` skill following `art-source/geometry-first-imagegen.md`
   (retain guides and prompts under `art-source/`, ship only packed output under
   `mod/graphics/`). Corpse art for both. Loader calls in `BattleSprites`.
3. **Behaviour.**
   - Brute: goes for garrisoned buildings and emplacements; breaks doors,
     windows and light walls on contact using the existing structural damage
     path, so a held building is not a permanent safe room.
   - Spitter: holds at range behind runners, lobs at the densest infantry it
     senses or at low aircraft; retreats when charged.
   Use the existing swarm sensing laws: senses, does not know. Strategic
   movement must go through the same swarm movement call the Runner uses, so
   `swarm-flow-fields.md` speeds all strains at once; do not fork pathing.
4. **Hookup.** If `bore-spawners.md` has landed its tier strain table, fill the
   brute and spitter slots; otherwise expose a director-independent way to spawn
   them (debug mission / scene) and leave the hookup to whichever story lands
   second.
5. **Evidence.** Behaviour scenes: brute opens a garrisoned building; spitter
   keeps range and lobs; spitter engages a low flier. A
   `createSnapshots` suite or review frames showing both bodies at
   battle zoom (1920x1080, 100% scale) for owner art review, posted under
   Drive `review/swarm-strains/` per the review-set rules.

## Out of scope

Matriarch (`matriarch.md`), bores, flow fields, audio.

## Acceptance

- Both strains spawn, animate, die and leave corpses in a live-shaped battle.
- Scene verdicts pass; new unit tests are under a second.
- Owner has an art review set for both bodies.
- Noun doc updated where the strains' behaviour settled differently.
