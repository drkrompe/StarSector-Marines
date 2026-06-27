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

**Multiple tracks progressing in parallel:**

- **Battle tier** — the compound-capture gameplay loop (central keep +
  compound-as-supply) is **complete for v1**: state machine, world/HUD
  markers, reinforcement gating, ConquestObjective, BSP compound
  generation, and multi-chamber keep all shipped. See
  [`conquest/central-keep.md`](conquest/central-keep.md) for the full
  shipped-with-details record. The battle tier's ongoing parallel tracks
  are convoy kinematics ([`convoy/`](convoy/overview.md)), the Services/Systems + SoA refactor
  ([`ecs-migration/`](ecs-migration/overview.md)), fog-of-war
  ([`fog-of-war/`](fog-of-war/overview.md)), and AI (GOAP + commander).
  The **feature-vertical package reorg** of `battle/` is **complete** (all
  10 slices shipped; the `entity/` rename alone is deferred to
  ecs-migration). On the render side, the **`BattleScreen` god-class
  decomposition into a layered draw-list pipeline is complete** (stories A–J +
  Final shipped & verified — `renderWorld` is now collect-all → drain-all over a
  `RenderSystem` registry); only the deferred `QuadBatch.flush` perf spike
  remains. See [`battle-render/`](battle-render/overview.md). A new **design-stage**
  track — [`command-powers/`](command-powers/overview.md) — brainstorms the
  player-agency layer (orbital strikes, marine drops, recon) and its
  between-battle meta-progression spine; powers are sourced diegetically from
  the player's fleet (ship + hull-mod flavor). A second, now **active** track —
  [`vanilla-combat-bridge/`](vanilla-combat-bridge/overview.md) — hooks the headless
  sim and vanilla `CombatEngineAPI` together (the reverse of how the mod is built).
  Past its probes: sim-authoritative *proxy targets* are proven, and a live Conquest
  ground battle now runs below a real vanilla fleet fight. The product it builds toward
  is the **drop-ship invasion** (S3d) — a transport establishes orbit over a painted
  drop zone and sim-native dropships land marines; vision + D1–D5 ladder spec'd, D1 in build.
  A third **design-stage** track — [`air/`](air/overview.md) — recaptures
  vanilla/modded airborne craft (fighters and overhead ships) as sim entities
  via a shared `ShipHullSpecAPI`-sourced hull-extraction pipeline (kinematics +
  concave-poly geometry, so modded craft work for free), scaled and re-flavored
  for ground-scale combat in atmosphere; shuttles are its already-shipped
  exemplar, and it's the data-model foundation the "flyby fighters as real air
  entities" backlog item is blocked on.
- **Campaign tier** — SoA `CampaignState`, contracts loop, patron houses,
  mission-resolver bridge. The Marine Ops mission-select screen consumes
  this layer. See [`campaign/`](campaign/).
- **Map generation** — room-purpose refactor complete (Slices A–D);
  partition strategies + per-cell labels now generalize past the keep
  toward station / ship interiors. See [`mapgen/`](mapgen/).
- **Moddable tilesets** *(design stage)* — move tile definitions and their
  gen→tile mappings out of hardcoded Java (`NatureTile`, `TileManifest`,
  per-`BlockKind` filler presets) into a dual-JSON, id-addressed
  `TileRegistry` so a submod can extend the tile catalog without
  recompiling. Phase 1 (id-registry, behavior-preserving) is the headline
  win even pre-submod: it kills the "enum order = PNG order" + hardcoded
  `(col,row)` fragility. Nests under `mapgen`'s shipped `GenRecipe`. See
  [`moddable-tilesets/`](moddable-tilesets/overview.md).

## Immediate next-up

1. **Loot picker UI** — the three-layer salvage model is already
   plumbed end-to-end (`salvageEntitlement` on `MissionOutcome`;
   briefing has the negotiation knob). What's missing is the item pool
   generator (vanilla weapons / supplies / fuel / marines / AI cores),
   the roll weighted by entitlement × enemy faction × planet industries,
   and the post-battle picker grid with cargo-capacity check + 75%
   fence-on-spot for overflow. MechWarrior Mercenaries vibe. Designed in
   [`campaign/loot/overview.md`](campaign/loot/overview.md).
2. **Contract generation for non-STRIKE types** — `ContractType` has
   six values; only STRIKE is generated. GARRISON + CADRE introduce
   retainer payment over time (closer to the contract design's
   "two-mode dichotomy" from `campaign/contracts/overview.md`).
3. **Compound-capture v2 (territory tug-of-war)** — reverse transitions
   (MARINE_HELD → CONTESTED → DEFENDER_HELD), AutoGarrisonTrigger,
   marine-side compound supply, defender positive win condition. Blocked
   on AI commander richness. See
   [`conquest/central-keep.md`](conquest/central-keep.md) § V2.

*(Shipped since this list was written: **offer expiry + patron archetypes** —
offers now lapse per archetype-driven windows (`ContractGenerator` +
`ContractLifecycleSystem`), and `HouseSeeder` populates `houseArchetype[]`
which drives the briefing voice via `BriefingComposer`. Commits `e3cbe306`,
`1e6afe6d`, `7136bc09`.)*

## How to use this directory

- **README.md** (this file) — vision, current focus, immediate next-up. Edit
  rarely; this is the stable view.
- **`backlog.md`** — known future work, grouped by area. Edit additively as
  ideas land.
- **Feature directories** (`ecs-migration/`, `campaign/`, `conquest/`, etc.)
  — each follows the `overview.md` + `stories/` + `complete/` layout
  described in [`CLAUDE.md`](../CLAUDE.md). `next-session.md` in each dir
  is the handoff artifact for picking up cold. Existing dirs are migrated
  to this layout incrementally as they're touched.

## Related project context

- [`CLAUDE.md`](../CLAUDE.md) — build toolchain, Starsector API conventions,
  repo conventions. Read at session start.
- `~/.claude/projects/.../memory/` — Claude's project memory. Holds *patterns
  and gotchas* (UI font minimum, Starsector rulecmd package gotcha, GL state
  pollution, persistence pattern). Different purpose than this roadmap —
  patterns/preferences vs. features/decisions.
