# Starsector Marines — Roadmap

> If you only read one file, read this one.

## What this is

Starsector Marines is a Starsector 0.98a-RC8 mod that adds a Marine Operations
sub-game on top of the campaign. Setup, build, and repository guidance live in
[`CLAUDE.md`](../CLAUDE.md).

## Vision

The long-term north star is a MechWarrior 3 / MechCommander Mercenaries-style
sub-game inside Starsector. Instead of marines being anonymous cargo, the
player runs a mercenary company: named captains lead persistent troops, ships
carry them between planets, and contracts arise from local factions, brokers,
pirates, and deniable covert patrons.

Marine Operations uses a full-canvas application inside the planet interaction
dialog. The campaign owns the surrounding world and host lifecycle; the mod
owns its screens, input, battle simulation, and the durable consequences that
flow back into the campaign.

## Current focus

### Company and progression

The campaign spine already supports persistent personnel, contracts, patrons,
deployment, recovery, and battle writeback. The active product work is making
that company legible and consequential: retained Fleet Armory workflows,
company/squad/fire-team presentation, performance-derived experience, and a
broader equipment and fabrication economy. Start with `campaign-nouns.md`,
`company-view-nouns.md`, and `progression-nouns.md`.

The opening Independent contract ladder is code-complete and awaiting a live
play pass before another early-operation variant is contracted. Its standing
mission model lives in `early-operation-nouns.md`.

### Battle tier

Conquest is the primary territorial battle: marines capture reversible supply
compounds, defender delivery degrades with lost territory, and the canonical
keep closes the assault. Its remaining work is bounded acceptance and explicit
extensions, not another capture model. See `conquest-nouns.md`,
`reinforcement-nouns.md`, and `convoy-nouns.md`.

Fleet-sourced support is committed before launch and activated by the
simulation through `command-powers-nouns.md`; distinct chassis, loadouts, and
doctrine remain Mechs authority in `mechs-nouns.md`.

AI, fog of war, ballistics, durability, rendering, and ECS composition now have
canonical feature models. Their open boards own focused acceptance, cleanup,
and extension stories. Start with `ai-nouns.md`, `fog-of-war-nouns.md`,
`ballistics-nouns.md`, `combat-durability-nouns.md`,
`battle-render-nouns.md`, and `ecs-nouns.md`.

Air uses one hull-derived atmospheric motion model. Shuttles are composed world
entities; fighters still need their active flyby-to-world ownership migration,
and overhead ships remain an extension. See `air-nouns.md`. The inverse
vanilla-combat integration and its production-launch boundary live in
`vanilla-combat-bridge-nouns.md`.

### Content and presentation

Map generation composes tactical places from recipes and staged context, while
tilesets and weapons separate data catalogs from the code that consumes them.
See `mapgen-nouns.md`, `moddable-tilesets-nouns.md`, and
`moddable-weapons-nouns.md`.

The retained UI foundation is proven in-engine and is being adopted one
production surface at a time. Fleet Armory is the active conversion; the UI
toolkit remains presentation infrastructure rather than company or inventory
authority. See `ui-nouns.md` and `company-view-nouns.md`.

## Immediate recommendation

Play a mission, then implement performance-derived experience in
`s4-performance-derived-experience.md`.

The live mission is valuable first because the lethality rewrite still needs a
feel pass, and the same run now produces combat telemetry and persistent career
records. The experience story can then turn that evidence into earned
progression instead of inventing another disconnected reward counter.

## How to use this directory

- Read this file for product orientation and the immediate recommendation.
- Read a feature's `*-nouns.md` document for its vocabulary, ownership, flow,
  standing laws, and extension boundaries.
- Read that feature's `design/stories.md` for open work and the relevant story
  in `stories/` before implementation.
- Use `backlog.md` only for future ideas that have not become feature stories.
- Let `design/shipped.md` and Git retain completion history; do not put shipped
  recaps or session journals back into this README.
