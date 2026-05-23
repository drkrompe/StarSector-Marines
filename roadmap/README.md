# Starsector Marines — Roadmap

> If you only read one file, read this one.

## What this is

A Starsector mod (game version 0.98a-RC8) that adds a Marine Operations
sub-game on top of vanilla. Setup and build details live in
[`CLAUDE.md`](../CLAUDE.md) at the repo root.

## Vision

The long-term north star is **a MechWarrior 3 / MechCommander Mercenaries-style
sub-game** inside Starsector. Instead of marines being anonymous cargo, the
player runs a merc company: named captains lead the troops, ships ferry the
team between planets, and contracts come from the planet's main faction, an
independent broker, pirates, and (at high enough rep with their enemies) a
deniable covert-ops track.

Each marine ops session is a full-canvas takeover of the planet interaction
dialog — own UI pipeline, own input routing, own rendering, no vanilla chrome
in the play area. This is intentional: the screens grow into their own
universe over time, not retrofitted into intel slots.

## Current focus

The **campaign tier** is the active surface — the SoA `CampaignState`,
contracts loop, patron houses surfacing as clients, and the
mission-resolver bridge writing back to the campaign graph on battle
outcomes. The Marine Ops mission-select screen is now the consumer of
that layer. See [`campaign/`](campaign/) — `README.md` indexes the
design docs and the `complete/` tracking history.

The battle/ground side continues to evolve in parallel (convoy
kinematics, mapgen, AI) — see `roadmap/convoy/` for the ground-vehicle
track, `roadmap/reinforcement/` for the orchestration layer above it,
`roadmap/conquest/` for the Conquest mode design (central keep +
compound-as-supply), `roadmap/ecs-migration/` for the long-running
battle-tier Services/Systems + SoA refactor arc, and the recent
session logs for other sibling-track activity.

## Immediate next-up

1. **Loot picker UI** (`loot.md` design + implementation) — the
   three-layer salvage model is already plumbed end-to-end
   (`salvageEntitlement` on `MissionOutcome`; briefing has the
   negotiation knob). What's missing is the item pool generator (vanilla
   weapons / supplies / fuel / marines / AI cores), the roll weighted by
   entitlement × enemy faction × planet industries, and the post-battle
   picker grid with cargo-capacity check + 75% fence-on-spot for
   overflow. MechWarrior Mercenaries vibe. Probably its own design doc
   before implementation.
2. **Offer expiry + patron archetypes** — small polish round. Offers
   currently never lapse; add expiry in `ContractLifecycleSystem`.
   Archetype byte (CORPORATE_RUSHED / FALLEN_NOBLE / etc) is designed
   in `mechanics.md` but `HouseSeeder` doesn't populate it; briefing
   flavor doesn't read it.
3. **Contract generation for non-STRIKE types** — `ContractType` has
   six values; only STRIKE is generated. GARRISON + CADRE introduce
   retainer payment over time (closer to the contract design's
   "two-mode dichotomy" from `contracts.md`).

## How to use this directory

- **README.md** (this file) — vision, current focus, immediate next-up. Edit
  rarely; this is the stable view.
- **`backlog.md`** — known future work, grouped by area. Edit additively as
  ideas land.
- **`sessions/YYYY-MM-DD.md`** — per-session handoff. Write one at the end
  of every working session: what shipped, what's open, what to do next time.
  These are the load-bearing artifact for picking up cold.

## Related project context

- [`CLAUDE.md`](../CLAUDE.md) — build toolchain, Starsector API conventions,
  repo conventions. Read at session start.
- `~/.claude/projects/.../memory/` — Claude's project memory. Holds *patterns
  and gotchas* (UI font minimum, Starsector rulecmd package gotcha, GL state
  pollution, persistence pattern). Different purpose than this roadmap —
  patterns/preferences vs. features/decisions.
