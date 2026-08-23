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
- **Moddable weapons** *(W1 shipped)* — the same move for the weapon
  catalog. Nineteen weapons across four enums (`MarineWeapon`,
  `MarineSecondary`, `MechWeapon`, `TurretKind`) with near-identical field
  sets, none reachable by a submod and all requiring a recompile to tune.
  **W1 shipped**: a new `battle.weapon` package holds `WeaponDef` /
  `MountClass` / `WeaponRegistry`, marine primaries now live in
  `data/marines/marine-weapons.weapon.json`, and `MarineWeapon` is reduced
  to an id handle whose accessors delegate to the registry — pinned
  field-for-field by `WeaponRegistryParityTest` with no test expectation
  changed. Next is **W2**, which replaces the four-arm `ImpactProfile` enum
  with **layered** effect definitions so a weapon composes its own tracer,
  muzzle and impact particles instead of picking one of four fixed recipes.
  W5 (submod merge) is deferred and should share one mechanism with
  moddable-tilesets Phase 3. A design pass also pulled `TurretKind` out of
  the weapon catalog entirely into **W6**: it is a platform, a mount and a
  gun fused into one enum, already carried by three platforms (static
  emplacement, shuttle hardpoint, convoy vehicle) that disagree about which
  of its fields mean anything — so emplacements and structures adopt the
  three-layer model mechs already use. See
  [`moddable-weapons/`](moddable-weapons/overview.md).
- **Surface relief** *(active)* — S1 derivation and the manually accepted S2
  material-aware parallax/water pass are shipped. S3 dynamic ground bump
  lighting is code-complete (`c92d5b9a`) and awaits an in-game smoke/tuning
  pass; its fixed eight-light budget consumes muzzle, impact, heavy-blast, and
  burning-wreck events. See [`surface-relief/`](surface-relief/overview.md).
- **Battle radio** *(expanded event slice shipped)* — 214 standard-pilot clips
  now cover positional contact, fallback, friendly-fire, enemy-mech sighting,
  enemy-down, and sparse sustained-combat calls. Friendly warnings follow
  landed rounds, mech calls require current squad LOS, and every pool shares
  one global voice budget. The standalone battle and vanilla-combat bridge
  share one presentation-only cue policy, so audio never perturbs sim
  determinism. An in-game mix/content feel pass remains queued. See
  [`battle-radio/`](battle-radio/overview.md).
- **Mech roster** *(active)* — the modular arm/shoulder component substrate and
  Bulwark/Hound/Sirocco debug comparison ship in `2d3f044b`, including SRM/LRM
  -5 and -15 rack classes and profile-aware physical geometry. Hound and
  Sirocco now have role-specific swapped/flipped hull silhouettes: Hound has a
  nose chaingun and single SRM, while Sirocco has paired LRMs and a new heavy
  anti-armor cannon. That cannon and the Heavy Mortar turret now share a
  gun-launched HE pass (`39aefccb`): visible ballistic shells, timed splash,
  cannon muzzle/impact lighting and particles, vanilla explosion/ring art,
  structural damage, decals, and positional audio. Bulwark's racks are exposed
  above the hull and its chainguns are narrower. Production defender rosters
  propose deterministic mech groups by risk (`1ef74f23`): LOW has none, MEDIUM
  proposes one Bulwark, and HIGH proposes budget-preserving mixed
  Bulwark/Hound/Sirocco groups. A battle-start force score now removes chassis
  and static turret candidates the combined player/allied attack cannot support,
  keeping small Raids playable without making heavy defenses categorically
  late-game. Hound's ASSAULT
  doctrine works for either side;
  it now requires infantry or a different chassis rather than letting Hounds
  screen one another. Coherently moving mech squads now take role-aware
  six-cell open-ground slots, compress toward a 2.5-cell floor with terrain,
  and reform after clearing it; infantry squads use the same generic engine at
  fireteam scale (`4c0864d9`). A scout follows only when recon behavior
  can make it meaningful. See
  [`mechs/`](mechs/overview.md). Objective-advancing infantry now consumes the
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
- **Company view** *(active — C7 shipped)* — the player's force as one legible
  hierarchy, company → squad → marine, in the fleet and in the field.
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
  Remaining: a derived formation model, the card stack, whereabouts, a
  battle-HUD company rollup, after-action by squad, C8's rejoin state for
  late arrivals, and fire teams as the AI's maneuver element. The settled
  decisions: a card is one officer's command, so the rank ladder changes
  (officers command, NCOs lead squads); **a squad becomes twelve marines in
  three four-marine fire teams**, because progression S1's shipped 9x
  lethality scale makes a six-marine squad combat-ineffective within
  seconds of contact; lifts are denominated in fire teams, so only a
  Valkyrie lands a squad intact and an assembling squad forms up at its LZ
  before advancing. Fire teams are modelled but behind the scenes — the
  AI's maneuver element (bounding, fix-and-flank), never a level of the
  player's hierarchy. Read-only throughout — in-battle orders are
  explicitly out of scope. A tenth story (C10) settles where the company is
  readable *between* contracts: a campaign-map ability-bar button opening the
  planet-free dialog host G32 shipped, rather than another route inside the
  planet-scoped Marine Ops screen — most roster work turned out to be gated
  by where its button sits, not by any fiction. **C10 slices 1-2 are shipped**
  (`2b959e44`, `b204c237`): the ability, the planet-free host, and a standing
  pane led by runway in months of payroll, reusing `OfficerMoodReader` so the
  figure the player reads is the one the officer's mood reacts to. Confirmed in
  game that the ability opens the screen and the host dismisses cleanly. Adjacent to progression S8,
  which owns what a single marine row says. See
  [`company-view/`](company-view/overview.md).

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
   [`mechs/next-session.md`](mechs/next-session.md).
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
