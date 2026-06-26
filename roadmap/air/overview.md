# Air

> Umbrella doc for airborne craft in the battle tier. Read this first, then the
> shared core ([`hull-extraction.md`](hull-extraction.md)) and the per-class
> docs ([`fighters/`](fighters/overview.md), [`ships/`](ships/overview.md)). **Design stage**
> except where noted (shuttles have shipped).

## What this is

The battle tier's **airborne craft** — shuttles, fighters/drones, and (overhead)
ships. The through-line: we are **recapturing vanilla and modded Starsector
hulls as entities in our own ground-scale simulation**, scaled and re-flavored
for combat *in atmosphere*, and deciding how they interact once they're there
(who can target them, what they collide with, how they read at a ground camera).

The category mirrors the code package `battle/air/` 1:1 — `AirBody`,
`AirHandling`, shuttles, and the planned fighters already live there. The
roadmap dir and the package line up.

## Same core, different classes

Every airborne craft is the **same** thing under the hood and **different** on
top:

- **Shared core** — one vanilla hull id → a sim entity: kinematics from
  `ShipHullSpecAPI.getEngineSpec()`, geometry (collision polygon + radius) from
  the hull's `.ship` file, both fed into an `AirBody`. This is the heart of the
  category and lives in [`hull-extraction.md`](hull-extraction.md). Because it
  reads the game's own loaded specs, **modded craft are supported for free**.
- **Per-class behavior** — role, scale, count, collision fidelity, and how the
  thing fits the battlefield differ sharply:

| | shuttles | fighters | ships |
| --- | --- | --- | --- |
| status | **shipped** | design | design |
| scale | squad transport | small, ratio-preserved | huge — scale/altitude problem bites hard |
| count | few | swarm in wings (`wing_data`) | single units, possible modules |
| collision | n/a (lands) | radius/OBB plenty | **concave polygon** (real hull hits → SFX/FX) |
| role | deliver squads | strafing runs (replaces flyby) | overhead presence, ground-targetable |

Shuttles are the **shipped proof** that the `AirBody` substrate works — see
`roadmap/battle-render/complete/story-h-shuttles-system.md`. Fighters and ships
are the design-stage members that extend it.

## Members

- **Shuttles** — shipped. The reference implementation of the shared substrate.
- **Fighters / drones** — [`fighters/`](fighters/overview.md). The existing
  `battle/flyby/` fighters are a cosmetic overlay (dead weight, ripe for
  replacement); this designs them as real `AirBody` entities. Unblocks the
  `roadmap/backlog.md` "flyby fighters as real air entities" item.
- **Ships** — [`ships/`](ships/overview.md). Overhead craft modeled with full concave
  hull collision so ground defenses can target them and hits land on the actual
  hull shape.

## Cross-references

- [`air-entities-into-world.md`](air-entities-into-world.md) — the storage/
  entity-world dimension: **one `EntityWorld`, not a separate air id space.** Air
  craft become real entities in the same archetype world as battle units
  (kinematics as a component, air FX re-keyed off their `ComponentStore`s).
  Decision captured 2026-06-25; gated on `ecs-migration` step 4.
- [`hull-extraction.md`](hull-extraction.md) — the shared vanilla-hull →
  sim-entity pipeline (kinematics + geometry).
- [`vanilla-kinematics-reference.md`](vanilla-kinematics-reference.md) — scraped
  fighter/drone data (offline reference).
- `roadmap/backlog.md` § "Flyby fighters as real air entities" — unblocked by
  the fighter track here.
- `roadmap/convoy/overview.md` — the ground-vehicle sibling (`GroundBody`);
  same "data-driven body + steering" shape, different medium.
- `roadmap/vanilla-combat-bridge/overview.md` — adjacent: its proxy-target
  thread is the other side of "vanilla ships and our sim interact." Ground
  defenses targeting ships (in [`ships/`](ships/overview.md)) lives next door.
- `roadmap/command-powers/` — fighter cover is surfaced to the player as a
  command power; this category is the sim substrate those wings fly on.
- Memory: [[air_vehicle_kinematics]], [[air_unit_render_sync]],
  [[vanilla_ship_spec_scraping]], [[starsector_script_sandbox]],
  [[ground_vehicle_kinematics]].
