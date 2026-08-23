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
  (`fog-of-war-nouns.md`), and AI (GOAP + commander).
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
  this layer. The first green-company ladder now ships two Independent jobs:
  reinforce a fixed local militia line, then return for a joint counterattack,
  both against militia-only bandits with no heavy support. See
  [`campaign/`](campaign/) and
  [`campaign/early-operations/`](campaign/early-operations/overview.md).
- **Map generation** — room-purpose refactor complete (Slices A–D), with that
  substrate now paying off in both station layouts and ground maps. Tactical
  commercial interiors ship purpose-labeled sales floors/stockrooms plus real
  shelf footprints and two-cell combat aisles sized for infantry; coherent
  commercial compounds and role-specific military bases now orient their
  buildings toward shared circulation. Military sites add bunk/rack/service/C2
  fixture plans plus an outdoor radar silhouette. Large civic lots now add
  frontage-aware headquarters with a two-cell lobby/service spine, two offices,
  conference and server rooms, and distinct low-versus-opaque office cover.
  Qualifying standalone residential lots now become street-facing apartment
  blocks, and apartment facades add non-traversable firing windows that preserve
  sight, shots, and directional cover. See
  [`mapgen/`](mapgen/).
- **Moddable tilesets** *(Phases 1 + 2 shipped; one proposed cleanup)* — the
  built-in visual catalog and its generation mapping are dual-JSON and
  id-addressed. `TileRegistry` owns what assets exist; `GenMappingRegistry`
  owns how code-driven generation and rendering use them. Phase 3 discovery,
  merge, override, and strict-validation semantics remain direction until a
  real second content provider exists. `moddable-tilesets-nouns.md` carries the
  standing model; its adjacent `stories.md` board tracks the remaining narrow
  nature-pool authority cleanup.
- **Moddable weapons** *(W1 shipped; W2 ready)* — marine-primary definitions
  are id-addressed data behind fail-loud `WeaponRegistry`; the remaining
  secondary, mech, and emplacement catalogs are planned migrations. The model
  separates platform, mount, and weapon authority, with layered authored FX as
  the next slice. Shared submod discovery/merge remains deferred until a real
  provider exists. `moddable-weapons-nouns.md` carries the standing model; its
  adjacent `stories.md` board tracks the five live stories.
- **Surface relief** *(S1–S2 shipped; S3 acceptance ready)* — deterministic
  build-time height/normal derivation feeds a fail-soft screen-space ground
  composite with semantic structure height, micro relief, and land-safe water
  motion. Bounded event lighting is implemented and awaits in-game acceptance;
  unit lighting remains deferred. `surface-relief-nouns.md` carries the standing
  model; its adjacent `stories.md` board tracks the two live stories.
- **Battle radio** *(expanded event slice shipped; acceptance active)* — 214 standard-pilot clips
  now cover positional contact, fallback, friendly-fire, enemy-mech sighting,
  enemy-down, and sparse sustained-combat calls. Friendly warnings follow
  landed rounds, mech calls require current squad LOS, and every pool shares
  one global voice budget. The standalone battle and vanilla-combat bridge
  share one presentation-only cue policy, so audio never perturbs sim
  determinism. An in-game mix/content feel pass remains queued. See
  `battle-radio-nouns.md`; its adjacent `stories.md` board tracks the queued
  mix pass and the proposed credits cleanup.
- **Mechs** *(specialist family implemented; S1 acceptance ready)* — persistent
  variants own chassis and loadout while roles independently own doctrine.
  Bulwark anchors all bands, Hound assaults only with a real screen, and
  Sirocco works behind one; their geometry, silhouettes, installed mounts, and
  encounter costs agree. Production admits deterministic mixed groups through
  the shared force budget, while DEBUG family delivery remains non-progression
  playtest scaffolding. `mechs-nouns.md` carries the standing model; its
  adjacent `stories.md` board tracks the remaining acceptance pass.
  Objective-advancing infantry now consumes the
  ASSAULT point unit for a faction-neutral mech-screened advance (`0daf058e`):
  it follows behind the live chassis, fans to fire on contact, and receives
  real protection from physical direct-fire interception. Generic infantry
  pursuit now also rejects clustered runners (`43c619ff`, `8888e6f8`), settles
  exposed holders laterally/backward into real cover, and hard-clips ordinary
  Engage/Approach paths to squad cohesion without constraining mission moves.
  That hold now reads the pursuing squad's own direct-LOS contact memory
  (`db69ed73`): unknown formation members do not leak into density, contacts
  decay with alert state, and selected-squad debug ghosts show remembered cells.
  The same belief layer now hears one-time shot and detonation events through
  walls (`63ffdb6a`) with deterministic inexact localization, lower confidence,
  and anonymous indirect-fire bearings for both attacker and defender squads.
  Independent Marine and Defender commander pictures now roll those identified
  beliefs into honest hostile fields beside known friendly presence
  (`4e7089d0`), propagate through fine-connectivity-aware 8×8 tactical blocks,
  and expose four read-only debug heatmaps. No commander assignments consume
  the fields yet.
- **Progression** *(in progress)* — a cross-tier track covering the
  meta-progression axes: weapon lethality and equipment tiering, earned
  per-soldier experience, the fabrication parts economy, and the legibility
  of all of it. `progression-nouns.md` carries the standing model; the
  baseline showed that the plumbing is shipped but the content is thin — four
  authored armor patterns are unreachable, the unlock ladder ends at mission
  five, parts have exactly one source (winning), XP is flat per survivor, and six
  of eleven traits are inert enums with no UI. **S1 is shipped** (`3a307354`,
  `fdc49c36`, plus the grade spread): a TTK harness that drives the real
  firing pipeline, then the two numeric passes it made arguable. A marine
  now dies in 3.4 s rather than 30, and the equipment ladder spans 4.3x in
  measured time-to-kill rather than 1.75x. **S3 is now shipped
  too**: a lifecycle-stable `TELEMETRY` component records what every
  combatant did, the attacker is threaded through the damage pipeline so
  kills and post-mitigation damage attribute correctly through ballistic,
  AoE, turret and melee paths alike, every mission logs a table covering
  defenders and the fallen, and each deployed marine now carries a persisted
  `SoldierCareer` of lifetime missions, rounds, damage, kills and wounds.
  That table is the balance artifact S1's tuning was argued without, and the
  career record is what lets S4 make experience earned rather than issued.
  S2 and S4-S10 remain, S1's own last acceptance item is an in-game feel pass,
  and the synthesis found one proposed XP-authority cleanup. See
  `progression-nouns.md`; its adjacent board is the open-work list.
- **Retained UI foundation** *(active — U1 implementation ready for live acceptance)* —
  Marine Ops is growing a retained document layer inside the existing full-canvas
  Starsector host. U1 adds the first Java tree, top-left document coordinates,
  row/column/stack layout, shared paint/hit boxes, and a dev workbench reachable
  from Company HQ. Clipping/focus/canvas input, themes/transitions, and MoonLight-style
  `.mlx` components remain ordered follow-ons under `roadmap/ui/`; Fleet Armory is
  the first planned production conversion.
- **Company view** *(active — C9 and C14 Slices 1–4 shipped)* — the player's force as one legible
  hierarchy, company → squad → fire team → marine, in the fleet and in the field.
  The mod already models captains, persistent six-marine squads, and
  named soldiers, but every surface renders them as a flat list and the
  organization is destroyed at deployment: `CampaignMarineDeployment.freeze`
  flattens squads into a seat list carrying no squad id, and
  `InfantryPayload` mints one battle squad per *shuttle mission*, so a
  battle squad is "whoever rode this dropship". **C7 shipped 2026-08-22**
  (`2e187f54`, `976bb87a`, `2786a3ed`): a squad is now twelve marines in
  three four-marine fire teams derived from roster order, officer rank caps
  command in squads (Lieutenant 3 through Colonel 24, the company starting
  under a Lieutenant), squads have NCOs with deterministic
  promotion-on-loss, and the UI says "squad" for the twelve and "fire team"
  for the four. **C1 and most of C8 shipped the same day** (`00ace1b0`,
  `6e3908b0`): a deploying seat carries its campaign squad's id, frozen
  label and NCO across the seam, marines landing from several lifts join
  one battle squad keyed on (campaign squad, landing zone) instead of one
  squad per sortie, lift capacity is declared in whole four-marine fire
  teams so only a Valkyrie lands a squad intact, and a squad still arriving
  holds at its LZ rather than feeding itself forward a team at a time.
  Remaining: a derived formation model, the company presentation stack, whereabouts, a
  battle-HUD company rollup, after-action by squad, C8's rejoin state for
  late arrivals. **C9 now ships fire teams as the AI's maneuver element:**
  campaign billets survive the deployment seam, generated squads receive the
  same stable four-person partition, infantry advances in team-local shapes
  across a shallow squad arc, one team bounds while its siblings cover, and
  fix-and-flank keeps a team on axis while another maneuvers. Team spacing and
  squad-arc anchors now release before doorways and narrow runs, allowing a
  natural pathing queue and automatic reformation beyond the constraint;
  unreachable or structurally indirect flank positions fall back to ordinary
  engagement. The settled
  decisions: one officer command is the company-view grouping, so the rank
  ladder changes
  (officers command, NCOs lead squads); **a squad becomes twelve marines in
  three four-marine fire teams**, because progression S1's shipped 9x
  lethality scale makes a six-marine squad combat-ineffective within
  seconds of contact; lifts are denominated in fire teams, so only a
  Valkyrie lands a squad intact and an assembling squad forms up at its LZ
  before advancing. Fire teams are the AI's maneuver element (bounding,
  fix-and-flank) and, in C14, the Fleet Armory's player-facing equipment tier:
  reusable four-billet fire-team templates are assigned only when finite fleet
  stock can supply a complete kit. Squads remain the deployment and command
  target; fire-team battle orders are explicitly out of scope. **C14 Slice 1
  ships the first end-to-end version:** four reusable starter templates, mixed
  four-billet issues including support weapons, atomic stock checks net of
  returned gear, persistent per-team assignments, and Alpha/Bravo/Charlie on
  the Fleet Armory LOADOUTS surface. The Template Designer now creates, clones and
  renames persistent custom templates, edits all four billets without stock gates,
  and saves equipment changes as new revisions so assigned teams never refit
  silently. Both the designer library and LOADOUTS picker page beyond the four
  starters. Templates now report fielded and ready-to-issue counts, selecting one
  previews the selected team's exact free + returned / required transaction,
  direct Alpha/Bravo/Charlie issue actions support rapid reuse, and two assigned
  teams can exchange scarce kits as one atomic transaction. Saved squad
  arrangements now compose Alpha, Bravo and Charlie from three templates,
  preview all twelve billets net of all returns, and issue the whole squad as
  one atomic action. Templates are equipment plans, not a collectible-card
  mechanic. Degraded-team conformance is next. A tenth story
  (C10) settles where the company is
  readable *between* contracts: a campaign-map ability-bar button opening the
  planet-free dialog host G32 shipped, rather than another route inside the
  planet-scoped Marine Ops screen — most roster work turned out to be gated
  by where its button sits, not by any fiction. The company home is shipped:
  ability, planet-free host, standing, obligation clocks, and roster readiness.
  It derives those readouts from the same campaign authorities that drive the
  rest of the game. Adjacent to progression S8, which owns what a single marine
  row says. See `company-view-nouns.md` and the company-view `stories.md` board.

## Immediate next-up

> **Recommended pickup: play a mission, then progression S4 —
> performance-derived experience** (`s4-performance-derived-experience.md`).
> S1 rewrote every combat number in the mod and its last acceptance item is
> an in-game feel pass, so a mission or two is worth more than any further
> tuning — and a played mission now dumps a full combat-telemetry table to
> the log and writes each marine's career record, so the feel pass produces
> data as well as impressions. S4 then converts those records into
> experience, which is what S1 slice 2 was counting on when it made
> equipment 2.4x the soldier ladder. The numbered list below is the
> pre-existing queue.

1. **Early-operations playtest** — the two-job Independent opening ladder is
   code-complete: one-player-sortie relief followed by a two-sortie joint
   counterattack, with employer militia, finite advancing bandits, and no
   fighters/mechs/turrets/reinforcement budget. Tune the 8+4 vs 12 relief and
   4+player vs 12 counterattack ratios in live play before contracting a third
   pinned-assault variant. See
   [`campaign/early-operations/next-session.md`](campaign/early-operations/next-session.md).
2. **Campaign personnel spine** — persistent six-person fireteams, cargo-backed
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
   witnessed (`b9e8ffd6`, `ca0a6994`, `e0b12a9c`). Captain discovery is complete
   too: eligible derelict salvage now produces deterministic, persistent
   cryo-pod survivors with replay-safe commission/referral/defer resolution and
   deferred Personnel review (`055abb97`, `d780e345`, `7a32e771`). No personnel
   story remains active; contract a new one before expanding this work. See
   [`campaign/personnel/next-session.md`](campaign/personnel/next-session.md).
3. **Living-world follow-through** — the first black-swan event now runs from
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
   complete: a one-shot dead/ruined site can fund a blind expedition, emit a
   dedicated exact-cohort mission against hidden-seed autonomous defenses, and
   close only from its lineage-bound survivor/archive report. Dead Letter and
   Chronicle preserve measured terminal facts exactly once across save/replay,
   with production-shaped debug reachability and no inferred moral or cargo
   reward (`4d50805d`, `33b073bd`, `9a87c85b`, `43bd3693`). Contract the next
   living-world story before implementation.
   The first swarm-feel correction now quarters alien HP and prefers a
   marine-style layered top-down actor over the fallback sheet (`bccbbe16`),
   with generated fore-claws that alternate a foreground contact swipe
   (`20d3bcb0`). Runners now choose opportunistically between nearby marines
   and exposed evacuees, allowing soldiers to peel pressure from the objective
   (`4fedb34a`). Depleted rescue swarms now receive bounded perimeter waves,
   while civilians independently leash to and shelter behind their nearest
   marine (`ad11debf`). The marine screen now rallies ahead of the cohort under
   fire, and civilian facing turns smoothly instead of snapping on path changes
   (`bc29cb26`). Rescue pickup is now physical: a landed civilian Valkyrie
   boards the cohort while eight fixed local militia receive capped Aeroshuttle
   replacements, and engaged escorts advance in two-cell timed bounds instead
   of freezing (`2395397f`). The entire pickup footprint now waits through a
   twelve-second opening grace period. The follow-up moves that pickup inward,
   expands the line to five randomized four-person militia squads in a star,
   reinforces its weakest point and center mech, and gives the mech a quiet-state
   perimeter patrol. Alien health is halved again to one eighth of its original
   pools, while missed direct-fire rays that cross another hostile now transfer
   into that body at a 100% base catch chance (`5f2c083d`). The star now spans
   25x25 cells around a fifteen-cell-inset LZ, and allied infantry apply a
   density-aware avoidance steer to hostile aliens inside five cells
   (`c6589910`). Generic alien health remains 1.875 HP, while rescue runners
   now carry 2.5 HP—three service pulse hits, four SMG hits, or one DMR hit.
   Canonical rescue openings rise to 20/40/64 runners, and DEBUG now exposes a
   production-shaped HIGH/four-drop launch beside the retained force-scaled
   stress fights. Marine
   direct fire now ignores civilian bodies, alien avoidance cannot raise a
   marine above its movement-speed stat, and the five militia squads retain
   their authored perimeter posts instead of collapsing onto the LZ center
   (`bf6fdd64`). Mobile rescue squads now receive distinct line-and-depth rally
   slots before relief and throughout the escort, settle within two cells of
   those slots, and only enter their slower timed advance while an engaged
   squad has a live alien within twelve cells (`f2159d82`). Squadmates now bind
   to distinct cells around each squad rally instead of racing for the same
   deferred occupancy claim. The pickup mech also retains its center/five-point
   LZ patrol during contact, fires from that route, and cannot wander into a
   generic field-overwatch or infantry-backstop assignment (`bdfd5bf8`). The
   escort's timed advance is now tracked per squad, so one locally pressured
   unit no longer stutter-steps the entire marine force. Lift placement now
   requires a fully walkable, outdoor, non-doorway 5x5 objective footprint
   (`5a0da48d`). LZ guards and moving escorts now spread their members through
   stable per-squad 5x5 tactical pockets, preferring nearby wall and doodad
   cover while retaining their authored squad anchor (`ba42612b`). The selected
   residential shelter now contains a four-person randomized militia garrison.
   It remains inert and excluded from swarm targeting while the colonists are
   sealed, then holds the compound as a local rear guard after marine relief;
   a wiped responding force also releases the last stand so the battle cannot
   deadlock (`822f5a2a`). Targetless runners now choose deterministic local
   routes during the shuttle fly-in, while rejecting paths through the protected
   shelter and pickup footprints; contact immediately restores opportunistic
   pursuit (`100110c2`). Manual cadence and post-rebalance feel validation
   remain queued.
   See
   [`campaign/living-world/next-session.md`](campaign/living-world/next-session.md).
4. **Campaign narrative follow-through** — S1 patron engagement memory through
   S5 remembered target locations are shipped (`1b950e48`, `cbfebaef`,
   `53cda364`, `03d8a24e`, `7b5367d4`). The comms officer prioritizes direct
   history, then a confirmed recent Chronicle fact naming that patron, then a
   measured same-market engagement; selected history variants can now name the
   prior operation's frozen target location. Contract the next narrative story
   before adding patron evolution, captain observations, or longer-form
   continuity. See
   [`campaign/narrative/next-session.md`](campaign/narrative/next-session.md).
5. **Command Powers S8 B-2** — add member-level commitment for power-source
   ships so the canonical briefing narrows `PowerCatalog` to the actual
   detachment. Then S8 C can add the command-deck slot budget. See
   [`command-powers/next-session.md`](command-powers/next-session.md).
6. **Specialist mech playtest** — use **Spawn mech family** in the battle debug
   panel to compare Hound/Sirocco against the unchanged Bulwark, then playtest
   the reciprocal Hound/infantry screen: the Hound should lead by no more than
   six cells and hold rather than solo-charge when its infantry or
   different-chassis support is lost; a second Hound must not release it.
   Confirm moving lances adopt role-aware six-cell open-ground spacing,
   compress cleanly at streets and doors, and expand afterward without idle
   posts drifting. Check that ordinary marine fireteams also spread without
   fighting cover or divergent orders. Confirm
   Sirocco angles behind a non-Sirocco ally in its
   new 24–36-cell firing band and uses both the heavy-cannon opportunity band
   and longer LRM band. Tune chassis identities plus those formation rules.
   Budgeted production defender composition is already approved. See
   `s1-specialist-striders.md`.
7. **Manual verification queue (deferred this session)** — the loot loop's
   visual/cargo/core shipping check, squad/debrief UI feel, and swarm wave cadence
   plus post-rebalance combat feel remain pending. See
   [`campaign/loot/next-session.md`](campaign/loot/next-session.md).
8. **Compound-capture v2 (territory tug-of-war)** — reverse transitions
   (MARINE_HELD → CONTESTED → DEFENDER_HELD), AutoGarrisonTrigger,
   marine-side compound supply, defender positive win condition. Blocked
   on AI commander richness. See
   [`conquest/central-keep.md`](conquest/central-keep.md) § V2.
9. ~~**Stationing events the player can actually see and lose**~~ — **shipped**
   (G31 `e25fa582`, G32 `89ad8bac`). A pending Garrison defense or Cadre incident
   now carries a persisted response deadline, lapses into a failed assignment
   through the shipped resolution policies at -20 employer / -10 MRB, and can no
   longer be laundered into a completed term. It also pushes itself at the player
   as a modal card with our own chrome — a `PlayerEventInbox` projection over the
   persisted payloads, a persisted exactly-once acknowledgement, and a
   self-triggered `showCustomVisualDialog`. Deploy Now answers it from anywhere in
   the sector through the same seam the local Manage → Respond button uses.
   **G32's manual smoke pass is outstanding** — the dialog half is not verifiable
   headlessly. This thread also surfaced and fixed a tier-wide clock bug: the
   campaign layer measured every duration from `getClock().getDay()`, a calendar
   component that wraps monthly, so retainers, default checkpoints, incident
   cadence, offer expiry, and injury recovery all failed silently across month
   boundaries. `CampaignClock` replaces it with a monotonic counter anchored so
   existing saves keep their numbering; **an in-game confirmation pass is queued**.
   See
   [`campaign/framework/complete/monotonic-campaign-clock.md`](campaign/framework/complete/monotonic-campaign-clock.md)
   and [`campaign/contracts/next-session.md`](campaign/contracts/next-session.md).

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
  — converge on the noun doc + `design/stories.md` + temporary `stories/`
  layout described in `CLAUDE.md`. Existing legacy directories are migrated
  incrementally as they are touched; do not copy their `complete/` or
  `next-session.md` lifecycle into migrated features.

## Related project context

- `CLAUDE.md` — build toolchain, Starsector API conventions,
  repo conventions. Read at session start.
- `~/.claude/projects/.../memory/` — Claude's project memory. Holds *patterns
  and gotchas* (UI font minimum, Starsector rulecmd package gotcha, GL state
  pollution, persistence pattern). Different purpose than this roadmap —
  patterns/preferences vs. features/decisions.
