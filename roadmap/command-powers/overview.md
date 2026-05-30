# Command Powers

> The player's agency layer *inside* the battle sim, and the meta-progression
> spine *between* battles. Brainstorming doc — concept + decomposition, not a
> sealed design. Open questions are live.

## What this is

Today the player watches the battle sim resolve: squads, vehicles, shuttles,
and the GOAP/commander AI fight it out. The player has marker-level visibility
but little to *do* mid-battle. **Command Powers** is the system that turns the
battle into something the player acts on — a repertoire of battlefield
abilities (orbital strikes, marine drops, recon sweeps, resupply, force
buffs) the player invokes during the fight.

Two axes make it more than a hotbar:

1. **Availability is diegetic — your fleet is your spellbook.** The ships and
   hull mods you brought to the planet determine *which* powers you can call.
   Bring a Valkyrie and you can drop heavy infantry; bring an Onslaught and you
   can call a ballistic barrage from orbit; bring an Apogee and you can sweep
   the fog of war. This is the [[feedback_world_reactive_over_expressive]]
   principle applied to player agency: the loadout emerges from real fleet
   composition, not a cosmetic ability-picker.

2. **Capacity is meta-progression — you level up your command.** Between
   battles, command level widens the pre-battle loadout budget, the in-battle
   command-point pool and its regen, and shortens cooldowns. Crucially, command
   level governs *how much* you can field — **not** *which* powers exist to you.
   Roster entry is a separate axis (see "How powers enter the roster"). This is
   the progression the player feels grow across a campaign — the
   merc-company-commander fantasy from [`../README.md`](../README.md) made
   mechanical.

The result: each battle's power loadout is *this fleet on this mission*, and
the player's growing command rating is what makes those powers hit harder.

## Why ships and hull mods first (the low-hanging fruit)

Starsector's own ship and hull-mod flavor text is dense with ground-combat
hooks — troop transports, orbital siege platforms, close-support packages,
recon scanners. Vanilla even ships a **literal** mechanic for this: the
`ground_support` / `advanced_ground_support` hull mods buff planetary-raid
strength based on marines in the fleet. Mining that corpus gives us a power
catalog that is *already lore-consistent* — players recognize a Valkyrie drop
or an Onslaught barrage as "of course that ship does that." The full survey
lives in [`ship-hullmod-survey.md`](ship-hullmod-survey.md).

## Power categories (synthesized from the survey)

The survey clusters into these candidate families. Each is a story-sized slice.

| Category | Fantasy | Representative fleet sources |
| --- | --- | --- |
| **Orbital Fire Support** | Call a barrage/strike onto a map zone | Onslaught (ballistic salvo), Invictus (hammer), Gryphon (missile fire-mission), "Kardakes" orbital siege platform, `ground_support` hull mod |
| **Close Air Support** | Strafing / bombing runs by strike craft | Astral (saturation), Legion (nanoforge-sustained), Heron (quick-reaction), Condor (budget), Drover (loiter CAP) |
| **Marine Insertion** | Drop a fresh squad onto the map | Valkyrie (canonical dropship), Phantom (stealth insertion), Additional Berthing, Starliner |
| **Recon / Intel / EW** | Reveal map, jam the enemy | Apogee (scanner), Shade (scout/EMP), Hi-Res Sensors, ECM/ECCM Package, Surveying Equipment |
| **Logistics / Resupply / CASEVAC** | Restore ammo, heal, extract wounded, repair vehicles | Tarsus/Atlas (resupply), Crig (field repair), Recovery Shuttles (medevac), Automated Repair Unit, Salvage Gantry |
| **Command & Control** | Buff/recharge — force multipliers | Operations Center (CP recovery → cooldowns), Nav Relay (movement), Targeting Unit (range), Distributed Fire Control (leader-death resilience) |
| **Marine Kit / Durability** | Between-battle outfitting that changes squad stats | Heavy Armor, Reinforced Bulkheads, Hardened Shields, Blast Doors, Safety Overrides |
| **Forward Operating Base** | Establish a structure on the map | Mora (buried-hull power station), "Ilmari" mobile fabricator, Mudskipper |

The first six are *active* powers (invoked mid-battle, command-point gated).
The last two lean *passive/meta* — they shape the squads you deploy before the
fight rather than firing during it. The split is itself an open question.

## The two-phase economy (decided)

Powers are governed by **two distinct budgets**, which keeps both the pre-battle
loadout choice and the mid-battle activation interesting:

1. **Pre-battle loadout budget (the hard choice).** Before a battle the player
   *slots* a subset of their available powers, constrained by command level.
   **Early game the budget is tight — you can only lean on a few powers, so
   which ones is a real decision; progression widens the budget so you bring
   more to the fight.** This is the deckbuilding moment, and it's where the
   merc-company-commander growth is felt most directly. Slottable powers are
   only those your *committed* fleet assets enable (see selection lens below).
2. **In-battle activation economy (command points).** Starsector fleet battles
   already use **command points** spent on orders; mirroring that gives a
   recognizable in-battle currency. A battle opens with a CP pool, powers cost
   points to fire, points regenerate over the battle (rate boosted by an
   `operations_center` ship in orbit), and per-power cooldowns/charges stop
   single-power spam. Munitions-style powers (a fixed barrage) lean on charges;
   support powers (recon, jamming) lean on cooldowns.

Meta-progression pushes on *both*: command level grows the loadout budget and
the unlock roster; in-battle it grows the CP pool and regen.

## Selection lens: does it project onto the battlefield? (decided)

Not every ship system is a power. The filter that replaces the old "strong vs
loose" split is: **does this capability plausibly extend onto the ground op, or
is it purely about the ship surviving space combat?**

- **Reject — ship-only.** Heavy Armor, Blast Doors, Reinforced Bulkheads,
  Hardened Shields. These harden the *hull* in fleet combat; there's no
  believable line to the marines on the ground. They stay in the campaign
  fleet-fitting layer, not here.
- **Accept — capability that scales a ground power.** Hangar/deck capacity
  (`expanded_deck_crew`, converted-hangar) → **+1 fighter on the strafing
  wing**, i.e. a knob on CAS strength. Targeting mods → power placement range.
- **Accept — capability that *becomes* a power.** Survey Equipment is the model
  here: a planetary-investigation tool that reads as **pre-battle intel**
  (enemy kit/composition revealed in the briefing) *and/or* an **active field
  scan** that lets the player target powers *beyond their units' direct line of
  sight* — projecting command reach into the fog. One ship system, two distinct
  power expressions. Lots of mods have this kind of double life; the survey
  should be re-read through this lens, not just keyword hits.

## Counterplay couples back to the campaign fleet (decided)

Powers that draw a real ship onto the field (drop shuttle, orbital striker,
CAS wing) **can be contested by the enemy AI commander**, and the consequence
**reaches back into the Starsector campaign layer** rather than just expiring
in the battle. The leading model is *attrition, not deletion*:

- A downed drop shuttle / damaged orbital asset takes **hull damage, is forced
  to "retire" from the rest of the battle, and has its combat readiness (CR)
  lowered for later space combat** — possibly picking up a d-mod on a bad hit.
- Outright loss (the ship removed from the fleet) is reserved for catastrophic
  outcomes, not the default cost of getting shot at.

This makes invoking a power a *weighted commitment*: leaning on your only
carrier for CAS risks bringing it home half-ready for the next jump. It ties
the ground sub-game's risk into the fleet the player has to live with —
exactly the kind of world-reactive consequence the project favors
([[feedback_hard_failure_preference]], [[feedback_world_reactive_over_expressive]]).

## How this ties into existing systems

- **Reinforcement / convoy / air** — Marine Insertion powers are the *player
  side* of the dispatch the [`../reinforcement/`](../reinforcement/) system
  already owns for the defender. Convoy ([`../convoy/`](../convoy/overview.md))
  and shuttles (`battle/air/`) are the delivery vectors a drop power would
  reuse — the open "does the player ever call a friendly drop?" question in the
  convoy overview is answered *yes, via a Command Power*.
- **AI commander** — [`../ai/`](../ai/overview.md) is the enemy-side strategic
  tier. Command Powers are the asymmetric player-side answer to it; the two
  should be balanced against each other.
- **Conquest loop** — [`../conquest/`](../conquest/) compound-capture is the
  arena these powers act in (e.g. an orbital strike on a contested compound).
- **Campaign / meta** — command level, XP, and unlocks live in the campaign
  tier ([`../campaign/`](../campaign/)) and persist between battles.
- **Mission flavors** — [[mission_type_flavors]]: Conquest vs Assault may gate
  or reweight which power families are appropriate (stealth insertion suits a
  covert strike; saturation CAS suits an assault).

## What powers cost — resource layers (decided)

Command points pace activation *within* a battle, but powers that pull real
fleet assets onto the field also spend **campaign resources** — coupling the
sub-game to the Starsector fleet economy the same way counterplay does. The
stack, abstract → concrete:

1. **Command points** — in-battle pacing; regenerates; no campaign footprint.
2. **Consumables on use** — supplies / munitions burned when a power *fires*.
   Orbital barrages and resupply drops are the obvious ones (you're literally
   expending ordnance / handing over supplies). Pay-as-you-go: leaning hard on a
   power drains stores you need between battles.
3. **Crew at risk (manned powers)** — manned strike craft and drop shuttles put
   **crew** (a real fleet resource) on the line. A shot-down *manned* fighter
   costs crew, not just CR. This makes **manned vs automated** a live trade:
   drone / war-drone powers (the survey's `bastillon` / `rampart` hooks) risk
   *no* crew but cost an AI-core slot / carry their own ceiling — manned is
   cheaper and more capable but bleeds people when contested.
4. **Commitment / opportunity cost** — an explicit detachment ties those ships
   up (unavailable for a concurrent space engagement) and may burn a standing
   supply trickle while on-station.

**Opt-in vs use — charge at *use*, not opt-in.** The pre-battle loadout budget
(command level) already enforces the hard "which powers" choice, and an explicit
detachment already carries opportunity cost — taxing opt-in again would
double-charge the same decision. Keep *slotting* cheap; make *leaning on* a
power the thing that drains crew / supplies / fuel. The player commits a broad
toolbox but self-rations in the fight — the more interesting tension, and one
that bites harder for a debt-start merc ([[feedback_hard_failure_preference]],
[[feedback_paycheck_runway_window]]).

## How powers enter the roster (decided)

*Which* powers exist to the player is a different axis from *how much* they can
field (capacity, above). Two tiers:

1. **Baseline tier — vanilla acquisition *is* the unlock.** For powers sourced
   from vanilla ships and hull mods, the game already has the answer: the player
   finds, buys, or salvages the ship/mod through normal Starsector play, and
   *owning it* grants the power. No bespoke XP ladder, no separate unlock
   economy — the purest "your fleet is your spellbook." Acquire a Valkyrie and
   the marine-drop power is simply *there*; lose it and it's gone. This costs us
   almost nothing to build and scales naturally with the player's fleet growth.
2. **Spoils tier — bespoke "super" mods as end-game rewards.** On top of the
   vanilla baseline we seed our *own* custom hull mods / ships that grant
   command powers — rare end-game spoils, deliberately strong, **designed to
   scale toward overpowered as later content is added**. This is where the
   real authored progression lives: covert-ops spoils, patron rewards
   ([[feedback_patron_narrative_discoverable]]), deep-salvage finds, loot-table
   entries ([`../campaign/loot/`](../campaign/loot/overview.md)). It's both an
   in-game power curve *and* a development runway — increasingly potent spoils
   ship as the mod grows.

The balance guardrail for the spoils tier: OP is *fine* when gated behind
genuinely hard/rare acquisition and when the enemy AI commander scales to meet
it — the end-game merc-commander is *supposed* to feel like they're fielding
bespoke command tech nobody else has.

## Landing zones & drop geography (active thread)

Today landing zones are fixed. Playtest feedback wants them to be a **player
choice — limited and incentivized, not free placement** (free placement would
trivialize insertion). This makes drop *geography* the spatial substrate for the
Marine Insertion and Forward-Operating-Base power families, and gives them real
positioning decisions instead of "a squad appears."

**The reinforcement-reach loop** (a player-side mirror of the compound-capture
supply loop in [`../conquest/`](../conquest/)):

> push forward → clear enemy **air defence** → a closer LZ lights up →
> reinforce deeper / faster → push further.

Early game you drop at the map edge or a single rear LZ and march to the front;
as you advance and clear AA / capture nodes, forward LZs unlock, shrinking the
march and tightening the reinforcement cycle. That's the progression layer for
drops the feedback asked for — earned reach, not a free teleport.

**Air defence is the counterplay geography** (the spatial form of
attrition-not-deletion, above). AA coverage on the map is what *contests* an
insertion, and it discriminates by craft class:

- **Heavy shuttle (Valkyrie-class)** — large squad per drop, but **warded off
  by AA**; needs a *safe / cleared* LZ. High reward, demands you do the work
  first.
- **Fighters / light craft** — nimbler, can **punch a hot LZ** AA would deny a
  shuttle, but deliver less and (if manned) risk crew on the way in.

So **clearing AA is the objective that unlocks heavy reinforcement**, and the
craft-class choice (heavy-safe vs light-hot) is a per-drop risk/reward dial that
reuses the manned-vs-automated and CR/crew cost model already decided. The
enemy AI commander placing/holding AA *is* its counter to your insertion powers.

**FOB = an *established* forward LZ.** The Forward-Operating-Base family
(Mora "buried-hull power station" / "Ilmari" fabricator spoils from the survey)
is how the player *creates* a forward drop anchor rather than finding one —
deploy it into cleared territory, defend it, and it becomes a premium LZ with
the deepest reach. That makes FOB the capstone of this loop and a natural
spoils-tier power.

Open within this thread: do unlocked LZs persist for the rest of the battle once
earned, or can the enemy re-contest them (re-establishing AA, retaking a node)?
Re-contest pairs with the [`../conquest/`](../conquest/) tug-of-war v2 direction.

## The commitment layer — explicit detachment (decided)

Fork #1 ("what 'committed' means") is **resolved: explicit detachment.** The
player nominates a detachment of their fleet to the ground op, and that
committed detachment is the **single source** of all three battle-support
kinds, replacing the old "auto-scan the whole fleet" behavior:

1. **Active command powers** — from committed ships / their hull mods.
2. **Passive fighter cover** — from committed carriers' fitted bays.
3. **Shuttle transport** — from committed transports.

The **employer/contract is a co-source** alongside the player's own ships: a
`Mission` already carries `clientFighterSupport` + `employerShuttles`, and now
also `employerPowerIds` — so a patron can offer command powers / passives as
part of the contract ([[feedback_patron_narrative_discoverable]]).

This unifies what were three independent fleet-scans (fighter wings, shuttles,
and — newly — powers) under one opt-in commitment, and it's what makes the
attrition-not-deletion counterplay legible: the committed ships are exactly the
ones at CR/crew risk. It's resolved by `ops.detachment.DetachmentResolver` into a
`Detachment` (pre-resolved capability lists), the single path both pre-battle
entry points route through (`ops.MissionLaunch`). See the
[S2 story](complete/s2-fleet-available-powers-resolver.md) for the 3-slice build.

## Still open (for the brainstorm)

Forks resolved above: loadout granularity, the active/passive line,
counterplay, the cost model, roster entry, and **what "committed" means**
(explicit detachment, above). What's still live:

1. **Intel vs active-scan split for "becomes a power" mods.** Survey Equipment
   could be pre-battle-only (briefing intel), in-battle-only (scan-ahead
   targeting), or both as separate slottable powers. Sets the pattern for every
   double-life mod.
2. **UI surface.** Where the power bar lives in the full-canvas battle takeover,
   and how zone-targeting (pick-a-cell, telegraph, confirm) works. Ties into
   [[gl_state_gotchas]] and the existing battle HUD.
3. **CP regen vs cooldowns balance** — how much of the brake is the shared CP
   pool vs per-power cooldowns/charges. Tuning, but it shapes whether the feel
   is "spend a budget" or "manage timers."

## Candidate first stories

Not yet broken into `stories/` files — these are the brainstorm seeds:

- ~~**S1 — Power framework skeleton.**~~ ✅ **Shipped** — see
  [`complete/s1-power-framework-skeleton.md`](complete/s1-power-framework-skeleton.md).
  `CommandPower` + `ReconPing` + `CommandPowerService`/`CommandPowerSystem` +
  `CommandPowerPanel`, with the invoke → target → resolve → cooldown loop wired
  end-to-end. ([[feedback_ship_then_optimize]]: one working power before the
  catalog.)
- **S2 — Fleet → available-powers resolver.** Read the mission's fleet
  composition, map ship/hull-mod presence to an available-power set. This is
  the diegetic-loadout core.
- **S3 — Orbital Fire Support (first real combat power).** Zone-targeted
  barrage with a telegraph + delay, gated on a warship in orbit. The most
  legible "I did something" power.
- **S4 — Marine Insertion power.** Player-invoked squad drop reusing the
  shuttle/convoy delivery vector at a *fixed safe LZ* first; closes the convoy
  open question. Deliberately ships before the geography layer (S6).
- **S5 — Command-level meta-progression.** XP, the command-point budget
  curve, and the capacity scaling persisted in the campaign tier (roster entry
  rides vanilla acquisition + spoils, so this story is capacity-only).
- **S6 — Drop geography: dynamic LZs + air defence.** Turn the LZ into the
  limited/incentivized player choice — AA coverage, craft-class risk/reward
  (heavy-safe vs light-hot), and the clear-AA → unlock-forward-LZ reach loop.
  Builds on S4; likely depends on the flyby→`AirBody` real-air-entity promotion
  so shuttles/fighters can be contested.
- **S7 — Forward Operating Base (spoils-tier capstone).** Player *establishes*
  a forward LZ in cleared territory (Mora / fabricator spoils). Capstone of the
  reach loop; lands after S6 and the spoils-tier roster work.

## How this directory is laid out

- **`overview.md`** (this file) — concept, categories, open questions. Edit as
  the brainstorm converges.
- **`ship-hullmod-survey.md`** — the mined vanilla flavor catalog that seeds
  the power families. Reference data; append as new hooks surface.
- **`stories/`** — active/queued story docs once slices are committed to.
- **`complete/`** — sealed shipped work.
- **`next-session.md`** — handoff state once implementation starts.
