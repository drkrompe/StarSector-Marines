# AI nouns

Status: ACTIVE — AI owns mission command, squad planning, belief-derived contact pictures, local doctrine, and faction-local read-only influence; strategic analysis, mech-behavior retirement, and live acceptance are bounded extensions.

Written: 2026-08-23

Updated: 2026-08-24 — replaced work sequencing with durable analysis/action boundaries and added the target-faction doctrine extension.

AI turns mission context and what a side has learned into coordinated
movement, posture, and fire intent. It is a decision system, not the authority
for combat resolution, map topology, campaign objectives, or player control.

## Decision layers

A **mission command** is the slow, faction-scoped strategic layer. It assigns
an `ObjectiveAssignment` to a squad: a bounded place or mission context to
serve. Command selects where a squad is useful; it does not author the squad's
local action sequence or make an individual unit fire.

A **squad plan** is the tactical answer to that assignment and the squad's
current knowledge. It chooses one goal and a short action sequence, then gives
members roles within each step. A squad without an assignment remains useful:
it falls through to ambient engagement rather than inventing a mission.

A **unit execution** is the per-tick realization of the assigned role. It can
move, hold, acquire a target, or author a legal fire intent, but it does not
silently replace the squad's plan. Combat systems remain responsible for
whether an authored shot lands and what it damages.

Goal priority is categorical: MISSION authority outranks survival, survival
outranks engagement, and engagement outranks idle behavior. Relevance chooses
only among goals in the highest active category. A must-hold mission context
therefore cannot be displaced merely because an ordinary combat goal scores
more highly.

Squad replanning remains serial unless a measured, explicit parallel contract
is introduced. Its state, goals, actions, and read-only view boundary may
support that extension, but parallel behavior is not implied by their shape.

## Knowledge and contact

A **belief** is a squad's own evidence about a hostile identity and location.
Direct line of sight records a full-confidence identity-backed contact. Heard
shots and detonations may create lower-confidence, inexact evidence; indirect
fire does not disclose a launcher identity merely by being heard. Beliefs decay
and disappear. An unknown hostile's live position is never promoted into a
squad tactical fact.

A **contact picture** is the immutable, once-per-tick local interpretation of
that squad's beliefs. It gives one answer for the tactical axis, threat sector,
fresh direct motion when evidence supports it, confidence-weighted hostile
presence, known friendly presence, local balance, and selected doctrine.
Presentation and diagnostics consume that published picture; they do not
reconstruct it from hidden world state.

Direct contact, alert and morale transitions, casualties, and hostile incoming
fire are tactical interrupts. The periodic replan remains the convergence path.
Reacquiring contact after a no-contact tick is an interrupt; merely seeing an
additional hostile during the same contact episode is not. A remembered
contact can guide awareness and acquisition, but advancing squads may hold on
lost direct contact only for a short reaction window rather than for the full
belief lifetime.

Each faction's commander has a separate **influence snapshot**. It aggregates
only its own squads' beliefs and honest friendly combatant presence into
topology-aware tactical fields. The current snapshots and heatmaps are
read-only diagnostics: they do not yet reassign squads, create reserves, or
brief subordinates.

## Doctrine and maneuver

The contact picture selects a sticky local doctrine: **ADVANCE**, **HOLD**, or
**DISENGAGE**. Hysteresis prevents a small score fluctuation from changing a
squad's posture every tick. Advancing squads may press, establish a contact
line, or withdraw from unfavorable local pressure; defending squads protect
their assigned ground unless they are overmatched and not under a must-hold
authority. An explicitly authored must-hold position can make its last infantry
survivor hold rather than take an ordinary structural fallback. Morale survival
behavior remains an independent higher-priority safety boundary elsewhere.

An advancing HOLD also publishes a contact initiative: **RECEIVE** or
**PROSECUTE**. A defending or overmatched squad, an approaching enemy, or a
useful firing line tells the squad to receive the contact from its current
ground. A non-approaching direct contact that only part of the squad can engage
tells it to prosecute: members with legal fire hold and shoot while the others
use the shared track to establish bounded firing positions. Prosecution never
authorizes an unbounded chase or abandonment of the mission route; if no legal
position exists inside the maneuver leash, the member continues its assigned
advance.

Fireteams are the infantry maneuver unit. They can receive distinct roles in a
shared squad step: a recoverable ambush can displace the exposed team while a
sibling covers, and a committed advance can bound rather than send every
member forward together. Normal movement may preserve a readable team
footprint, but doorways, constrained navigation, authored posts, and a live
contact-bound maneuver override decorative formation pressure. Acquisition
may retain a legal target through near-equal alternatives so reflex delay and
visual facing do not chatter.

Assigned defense stays bounded to its authored place. A compound garrison
re-clears and patrols eligible rooms inside the compound footprint; a live
turret post patrols its authored local box. A map-wide exterior flood is not a
room-clear objective, and removing the live post context returns its squad to
an ordinary local hold. Whether a wiped garrison is replaced or a defender
force is strategically recommitted belongs to mission command, not the local
guard plan.

The shared planner does not make all actors tactically identical. Infantry,
mech, and drone groups use distinct goal/action libraries for their different
movement and combat constraints. A mech role is doctrine supplied by the Mech
domain, not a replacement for chassis or loadout identity; a battle lance may
form while moving without merging unrelated squads. `company-view-nouns.md`
owns fireteam membership and company organization, while AI consumes that
organization for maneuver.

## Mission, space, and feature boundaries

Mission commands issue assignment context for the mission they serve. Zones,
portals, tactical nodes, and compound footprints are tactical places supplied
by map generation and battle setup; AI may reason over them but does not author
their geometry. `mapgen-nouns.md` owns that spatial substrate.

`conquest-nouns.md` owns territorial capture, compound state, garrison
entitlement, and supply consequences. AI may assign a squad to approach, clear,
or hold a Conquest context, but it does not decide ownership transfer or
reinforcement delivery. `reinforcement-nouns.md` owns reinforcement requests
and delivery. Other mission domains own their objective and outcome laws;
their commanders adapt those laws into assignment context.

`mechs-nouns.md` owns chassis, loadout, role identity, and production
composition. `combat-durability-nouns.md`, `ballistics-nouns.md`, and
`moddable-weapons-nouns.md` own damage, physical shot resolution, and weapon
authority. AI chooses intended behavior within those contracts and cannot
override their physical outcomes. `battle-render-nouns.md` owns rendering;
debug presentation observes AI facts without becoming a sensor or a second
decision authority.

## Strategic-extension boundaries

A commander influence snapshot is faction-local, belief-honest, immutable, and
read-only. A frontline, bulge, or breakthrough analysis may derive only from
that snapshot and must never expose hidden world state.

Any consumer that reallocates squads, creates reserves, or briefs subordinates
requires its own mission-specific authority contract. Diagnostic analysis does
not itself authorize action. Mechanical suppression, cross-squad briefing,
defender strategic response, recon doctrine, and dynamic mech reassignment are
separate extensions and must preserve the same knowledge and ownership laws.

Target-faction doctrine is likewise a bounded mission-command extension, not a
new planner or a source of hidden knowledge. `target-faction-command-doctrine.md`
may bias legal assignment, reserve, recapture, and local posture choices from a
frozen battle-facing profile; it may not change objectives, force composition,
combat resolution, or the belief facts available to a squad.
