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
  is the **drop-ship invasion** (S3d) — **the full D1–D5 ladder shipped 2026-06-27**: click a drop
  zone → carrier establishes orbit → timed dropship waves fall through AA into a threat-scaled scatter
  → marines fight; the fleet you bring is the invasion depth, and losing the transport is the stake.
  Remaining: extraction/dustoff + the skybattle feature (the carrier-death source that arms the stake).
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
- **Map generation** — room-purpose refactor complete (Slices A–D), with that
  substrate now paying off in both station layouts and ground maps. Tactical
  commercial interiors ship purpose-labeled sales floors/stockrooms plus real
  shelf footprints and two-cell combat aisles sized for infantry; coherent
  commercial compounds and role-specific military bases now orient their
  buildings toward shared circulation. Military sites add bunk/rack/service/C2
  fixture plans plus an outdoor radar silhouette. See
  [`mapgen/`](mapgen/).
- **Moddable tilesets** *(Phases 1 + 2 shipped; Phase 3 deferred)* — moved
  tile definitions and their gen→tile mappings out of hardcoded Java
  (`NatureTile`, `TileManifest`, per-`BlockKind` filler presets) into a
  dual-JSON, id-addressed `TileRegistry` so a submod can extend the tile
  catalog without recompiling. Phase 1 (id-registry, behavior-preserving)
  already paid off pre-submod: it killed the "enum order = PNG order" +
  hardcoded `(col,row)` fragility, and Phase 2 made the gen→tile mapping
  (doodad pools, ground-render dispatch, filler params) data too. Phase 3
  (mod-merge: load order, id-override, validation) is deferred until a real
  submod exists. Nests under `mapgen`'s shipped `GenRecipe`. See
  [`moddable-tilesets/`](moddable-tilesets/overview.md).
- **Surface relief** *(active)* — S1 derivation and the manually accepted S2
  material-aware parallax/water pass are shipped. S3 dynamic ground bump
  lighting is code-complete (`c92d5b9a`) and awaits an in-game smoke/tuning
  pass; its fixed eight-light budget consumes muzzle, impact, heavy-blast, and
  burning-wreck events. See [`surface-relief/`](surface-relief/overview.md).

## Immediate next-up

1. **Campaign personnel spine** — persistent six-person fireteams, cargo-backed
   enlistment, reserve management, armory allocation/presets, explicit
   deployment, deterministic RTD/WIA/MIA/KIA outcomes, recovery, and debrief are
   shipped (`aee9b9cf`, `b7bb10db`, `b3da11ad`, `4737404d`, `c35e88d4`,
   `822572b7`). Captain home-command persistence and armory management are also
   shipped (`9c4c4ee8`, `aa26d3ec`), as are debug personnel fixtures and the
   production shortfall/recruitment route (`605cda22`, `59c4864b`, `75413bfc`).
   Captain-scoped briefing defaults, rank-scaled whole-fireteam limits, and
   frozen Results command context are now shipped (`f6247ace`, `48471e93`),
   completing the captain-command story. Named stationing is also complete:
   whole-team binding, legacy-safe return, incident casualties, default
   extraction, repair, compaction, and debrief all ship. The hidden moral
   compass's first diegetic reaction now ships too: long-serving captains gain
   one deterministic, persistent `IDEALIST`/`CYNICAL` outlook from choices they
   witnessed (`b9e8ffd6`, `ca0a6994`, `e0b12a9c`). No personnel story remains
   active; contract a new one before expanding this characterization. See
   [`campaign/personnel/next-session.md`](campaign/personnel/next-session.md).
2. **Living-world follow-through** — the first black-swan event now runs from
   deterministic trigger through player choice, swarm-rescue battle, explicit
   outcome writeback, debrief, hidden moral consequence, and durable Distress
   Net dispatch. The second archetype is also complete: a discovered political
   chain can produce a costly defector-asylum promise followed by a delayed
   protect-or-betray choice, with source-frozen plot/reputation reaction and
   hidden moral meaning consumed exactly once. The kingmaker capstone is now
   complete too: decisive claimant victories
   seal a deterministic Last Testament, deliver it through persistent intel and
   Chronicle history, and have production-shaped debug/replay coverage
   (`946262b2`, `15c017ed`, `379d8989`, `e0d7c117`). G9 **Silent Colony** is now
   active: its first two slices persist the blind expedition and select one-shot
   dead/ruined sites behind the shared event gate, with a reconstructible Dead
   Letter choice surface (`4d50805d`, `33b073bd`). Next is the dedicated
   expedition mission lineage, survivor/archive objectives, and automated-threat
   reveal.
   Keep swarm tuning deferred until manual playtesting resumes. See
   [`campaign/living-world/next-session.md`](campaign/living-world/next-session.md).
3. **Command Powers S8 B-2** — add member-level commitment for power-source
   ships so the canonical briefing narrows `PowerCatalog` to the actual
   detachment. Then S8 C can add the command-deck slot budget. See
   [`command-powers/next-session.md`](command-powers/next-session.md).
4. **Manual verification queue (deferred this session)** — the loot loop's
   visual/cargo/core shipping check, squad/debrief UI feel, and swarm roster/stat
   tuning remain pending. See
   [`campaign/loot/next-session.md`](campaign/loot/next-session.md).
5. **Compound-capture v2 (territory tug-of-war)** — reverse transitions
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
