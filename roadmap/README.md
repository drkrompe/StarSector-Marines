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

Mission command has one shared architecture and separate enduring strategy
designs for Conquest, Sabotage, Assault, Raid, and Extraction. Start with
`mission-command-nouns.md`, then read the relevant mission design and its story
group. Mission objectives and outcomes remain with their owning domains.

Conquest is the primary territorial battle and the first production paired
mission-command implementation: marines capture reversible supply compounds
while defenders mobilize patrols, preserve strongpoints, and retain
reinforcement ownership. It is the reference migration for a broader autonomous
command-duel foundation in which both sides can progress the mission without
player micromanagement; foundation migration and paired live acceptance remain.
Marine Sabotage is the second production attacker migration: exactly three named
sites organize stable planter, kit-recovery, security, and reinforcement groups
with deterministic headless evidence. Its paired defender site-security
commander is also shipped. Assault now supplies the next paired production
duel: Marines search persistent two-dimensional sectors while defenders preserve
authored strongpoints, spread routine area security, and mobilize a belief-driven
bounded reserve. Its live acceptance and canonical-duration evidence review are
still pending. Raid now replaces elimination with one stable high-value target,
uncontested seizure, and survivor egress; its paired commanders, fixture,
selected-squad diagnostics, and argument-selected headless evidence are in
production, with canonical-duration and live review remaining.
Generic Extraction now replaces elimination with an escorted recovery-package
contract and exposes the same neutral payload projection used by Civilian
Rescue and Silent Colony without flattening their variant laws. Its autonomous
Marine corridor and bounded conventional defender interdiction commanders now
run as a paired production duel. Civilian Rescue now supplies the first
asymmetric Extraction adapter: Marines coordinate the shelter, cohort, screens,
and lift while a separately disclosed swarm director owns pressure waves and
perimeter approaches. Generic and Rescue live acceptance and the authored
Silent Colony command picture remain.
Each mission keeps its own strategy geometry. See `conquest-command.md`,
`sabotage-command.md`, `assault-command.md`, `raid-command.md`,
`extraction-command.md`, `ai-nouns.md`, `reinforcement-nouns.md`, and
`convoy-nouns.md`.

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

Faction identity has one enduring lore-reference catalog for use before authoring
weapons, armor, units, facilities, contracts, or command behavior. It separates
current 0.98a canon from conservative ground-war inference and explicit mod choices,
then routes each core faction to a dedicated guide. Start with
`faction-lore-nouns.md`, then use `equipment-lore-catalog.md` for shipped weapon,
armor, special-equipment, and loadout provenance; the owning feature nouns remain
the authority for mechanics.

Shipboard space is a new model rather than shipped work. The flagship rooms the
player sees today are hand-authored constant layouts; `ship-interiors-nouns.md`
proposes one longitudinal deck family that generates them from parameters, makes
facility capacity spatial and upgradeable, and yields hostile decks for boarding
from the same pipeline. Nothing in it has been implemented.

The retained UI foundation is proven in-engine and is being adopted one
production surface at a time. Company HQ reads as the flagship bridge's
command station, Fleet Armory owns deliberate equipment inspection and issue,
and Barracks is the ordinary read-only place to browse squads aboard ship between
operations. Its practice range now fires issued primaries through a disposable bounded
battle simulation while campaign personnel and inventory remain untouched. The UI
toolkit remains presentation infrastructure rather than company or inventory authority.
Barracks and Mech Lab headless evidence collect the same bounded battle-simulation
commands as the live views and substitute only the final Java2D drain; HQ and Armory
evidence also render as deterministic PNGs without launching the game.
See `ui-nouns.md` and `company-view-nouns.md`.

## Immediate recommendation

Use the explicit Conquest secure-travel evidence to investigate tactical
survival under contact before tuning capture allocation. On the current sealed
6,000-tick reinforced row, 19 trips produced 2 target entries, 16 squad losses,
and one deliberate replacement assignment; there was no compound-target or
capture-marker churn. Every lost squad's final living command pulse had local
contact. Median loss distance was about 44.8 cells after 39.6% of the original
approach, while four squads got within 13.5 cells, separating a broad contact-
survival problem from a smaller near-objective breach problem. Compare the
full-strength row when the local Gradle environment permits, then correlate
loss episodes with cover, suppression, and engagement behavior rather than
changing commander targets from this one seed. Exact whole-zone occupancy
remains neutral outcome evidence and must never become commander input. Then
close the remaining assignment-writer and live-acceptance edges in
`autonomous-mission-command-foundation.md`. Assault's paired production
commanders now share stable area geometry while retaining separate beliefs,
ownership, directives, panel/dump/overlay views, and forced-serial trace evidence.
Review a canonical-duration `commanderEvidence -Pmission=assault` run and the
deferred live play pass before retiring its two implementation stories.
Opening Operations remains the smallest later command-duel proof. Raid's first
contract is ready for canonical-duration and live review. Generic Extraction is
ready for live review, while Civilian Rescue now has deterministic canonical
evidence for its Marine corridor command and separate swarm-pressure director;
its live pressure pass remains deliberately deferred. Each mission
adapts the same knowledge, ownership, cadence, and diagnostic contracts through
its own geometry. The grouped work lives in the Mission Command `stories.md`.

## How to use this directory

- Read this file for product orientation and the immediate recommendation.
- Read a feature's `*-nouns.md` document for its vocabulary, ownership, flow,
  standing laws, and extension boundaries.
- Read that feature's `design/stories.md` for open work and the relevant story
  in `stories/` before implementation.
- Use `backlog.md` only for future ideas that have not become feature stories.
- Let `design/shipped.md` and Git retain completion history; do not put shipped
  recaps or session journals back into this README.
