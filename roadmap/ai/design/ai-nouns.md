# AI nouns

Status: ACTIVE — AI owns autonomous mission command, squad planning, belief-derived contact pictures, local doctrine, faction-local influence, and command observability; broader mission strategies, player interventions, strategic analysis, and live acceptance are bounded extensions.

Written: 2026-08-23

Updated: 2026-08-25 — added forced-serial Conquest fixture evidence and canonical command metrics over the perspective/referee trace split.

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

An executable-assignment change is a tactical-plan interrupt: the squad replans against
the new mission context immediately instead of finishing work authored for the
former assignment. Movement paths belong to the plan that authored them. A
planless unit drops that path, while a path may survive the instant a completed
step hands off to its sibling step so coordinated approach and engagement do
not erase one another's execution state.

Goal priority is categorical: MISSION authority outranks survival, survival
outranks engagement, and engagement outranks idle behavior. Relevance chooses
only among goals in the highest active category. A must-hold mission context
therefore cannot be displaced merely because an ordinary combat goal scores
more highly.

Squad replanning remains serial unless a measured, explicit parallel contract
is introduced. Its state, goals, actions, and read-only view boundary may
support that extension, but parallel behavior is not implied by their shape.

## Autonomous command duel

The normal battle baseline is **autonomous resolution**: every side with
strategic agency must be capable of pursuing its mission without the player
continually rescuing idle squads. The resulting **command duel** has one
perspective-specific mission strategy per side. The shared infrastructure is
symmetrical; objectives and behavior need not be. An attacker may search,
seize, plant, or escort while a defender guards, delays, intercepts, or
countercommits under the same knowledge and authority laws.

A **command frame** is the frozen input to one command pulse. It contains the
side's own force state, faction-local influence, legally disclosed objective
state, public topology, current directives, and frozen doctrine. It does not
offer unrestricted access to the opposing live world. Production mission
strategies receive only this frame and narrow topology queries, never a
`BattleView` or retained simulation reference. The frame-only
`AutonomousMissionCommand` contract is separate from the legacy live-view
`MissionCommand` contract. A perspective-specific, stateless mission disclosure
adapter is registered beside the strategy and is the sole authority that may
project objective facts into the frame. Both sides plan from their frames
before either side's new orders are committed, so commander dispatch order
cannot become knowledge or behavior leakage.

A **command pool** is the set of squads a strategy may allocate. Born
garrisons, payload guards, scripted actors, and reinforcement forces awaiting
handoff remain explicitly owned outside that pool. Ownership may exist without
a tactical assignment: reinforcement delivery claims its squad when the squad
is minted, even while the squad has no place to act. A **directive** combines
that ownership with an optional `ObjectiveAssignment`, issuing authority,
reason, issue tick, target meaning, and stability or lease state. A shared arbiter validates and
commits proposed directives after planning; mission strategies do not compete
through untracked writes to the squad assignment field. Every other assignment
writer—garrison, payload, reinforcement, scripted, and intervention
systems—must likewise register its ownership with the arbiter or perform an
explicit incumbent-checked handoff. A weaker or equal external claim cannot
displace another issuer. No direct-write escape hatch may bypass provenance.

Directive **stability** and authority **leases** are distinct clocks. Stability
is an inclusive minimum hold on a mission assignment, long enough for a squad
to execute a useful plan before ordinary command scoring may retarget it; an
objective completing, its target becoming unreachable, its command context
expiring, squad loss, explicit handoff, or higher authority may end that hold
early. A lease instead bounds temporary external authority such as a future
player intervention. Repeating the same assignment renews neither clock.

Form-up is an execution suspension, not an assignment writer or ownership
transfer. The authoritative directive remains inspectable and may be updated
while a tagged campaign squad assembles; tactical goals consume a derived
executable assignment that is null until the manifest arrives or the timeout
expires. Selected-squad and dump diagnostics show both the authoritative order
and the READY/SUSPENDED execution state.

A **commander snapshot** is the immutable explanation published after commit.
It names the perspective, strategy, phase, command pool, reserves, objective or
group summaries, and each squad's directive and reason. Mission-specific
pictures extend this envelope with tracks, sectors, sites, corridors, or
branches. Selected-squad presentation, dumps, and headless traces consume the
published snapshot rather than reverse-engineering command intent. A squad
that remains unassigned or a proposal that validation rejects still receives
an explicit reason.

A **command trace** is an opt-in battle-long diagnostic record of those published
snapshots. Its perspective stream contains only one side's post-commit command
facts and is deduplicated at command-pulse cadence. Authoritative compound
transitions, casualties, duration, and outcome belong to a separately labelled
neutral referee stream. Referee evidence may evaluate the command duel but is
never fed back into either commander. Canonical field and event ordering makes
the same trace usable by a live dump, deterministic fixture comparison, and
later aggregate analysis.

Trace capture records when a published snapshot was observed as well as the
snapshot's own command tick. Pausing and resuming capture creates explicit
observation windows; analysis never integrates reserve, assignment, or pressure
state across an invisible gap. A bounded headless run ends with a labelled
timeout rather than an invented outcome. Only a forced-serial run is canonical
for byte-stability evidence while unit behaviors share seeded random streams.

The first Conquest evidence metrics are descriptive rather than balance gates.
They report accepted assignment retargets and reissues, command-unassigned and
explicitly unreachable squad intervals, reserve squad-time, published defender
contact-to-reserve-mobilization latency, published track concentration,
compound captures and losses, combatant casualties, duration, and terminal or
timeout outcome. They name only what the trace proves: command-unassigned is
not synonymous with physical inactivity. A bounded run with compounds but no
observed ownership gain is labelled territorial progress stalled; a long
capture gap remains evidence to inspect rather than an automatic tuning order.

The player is an **intervention authority**, not a replacement for a competent
baseline commander. Existing force selection and command powers are the first
intervention families. A later direct command may bias priority or lease a
legal rally, reserve commitment, focus, or fallback for a bounded duration. It
cannot manufacture hostile knowledge, bypass objective law, seize an
externally owned squad, or write an assignment with no provenance. Zero-input
outcomes, objective progress, idle combat power, response time, order churn,
casualties, and duration are therefore first-class balance evidence. Activation
pacing, concurrent leases, and their cost authority are bounded as well, so
reissuing a request cannot turn a temporary intervention into permanent manual
control.

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

Contact locality follows the squad's live member footprint rather than only
its centroid. A dispersed fireteam therefore retains a contact local to that
element even when sibling teams pull the centroid away. Immediate friendly
strength is measured around the primary contact, so a remote sibling element
does not make an isolated fireteam's local balance appear favorable. A
remembered identity may remain useful evidence, but it is actionable planner
contact only while it still resolves to a live hostile combatant; a dead or
released identity cannot satisfy target, line-of-sight, range, or identified
contact-reinforcement facts. An anonymous current audible bearing remains a
valid investigation cue without inventing a hostile identity.

Direct contact, alert and morale transitions, casualties, and hostile incoming
fire are tactical interrupts. The periodic replan remains the convergence path.
Reacquiring contact after a no-contact tick is an interrupt; merely seeing an
additional hostile during the same contact episode is not. A remembered
contact can guide awareness and acquisition, but advancing squads may hold on
lost direct contact only for a short reaction window rather than for the full
belief lifetime.

Each faction's commander has a separate **influence snapshot**. It aggregates
only its own squads' beliefs and honest friendly combatant presence into
topology-aware tactical fields. The snapshot itself is read-only. A consumer
may act on it only when a mission-specific command contract authorizes that
action; diagnostics and heatmaps do not acquire assignment authority merely by
reading the field.

A mission command may publish a mission-specific command picture that combines
its authored assignments with honest influence-derived metrics. Conquest's
front snapshot is the first such picture: its metrics explain track progress
and pressure, while the command's explicit compound and neighboring-track laws
remain the authority for reassignment. The presence of a generic influence
field does not make Conquest tracks a universal commander abstraction.
Conquest defender command is the first opposing-faction consumer: defender
reports raise only a coarse threatened track/band and can mobilize a bounded
starting patrol reserve. The briefing grants a rally context, not the source
squad's hostile identity or exact reported position.

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

In a coordinated flank, the fixing element does not remain passively parked
once direct contact establishes the enemy line. It moves to reachable firing
or vantage positions and holds there while the maneuver element advances. An
unreachable maneuver waypoint completes the maneuver attempt and hands control
back to ordinary contact doctrine; it must not trap the squad in an endless
approach/replan loop.

Assigned defense stays bounded to its authored place. A compound garrison
re-clears and patrols eligible rooms inside the compound footprint; a live
turret post patrols its authored local box. A map-wide exterior flood is not a
room-clear objective, and removing the live post context returns its squad to
an ordinary local hold. Whether a wiped garrison is replaced or a defender
force is strategically recommitted belongs to mission command, not the local
guard plan.

When a tactical place supplies authored stand positions, initial allocation
and structural fallback assign those cells as member homes before deriving
nearby cover cells. The ordinary hold returns an idle member to that home, so a
bunker window is a durable defensive post rather than spawn-time decoration.
Invalid or unavailable authored cells fall back to the bounded nearby picker;
they do not make the entire garrison undeployable.

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

Command geometry follows mission meaning. Conquest uses a directional front;
Assault searches a two-dimensional area; Sabotage organizes around named sites;
Rescue protects a moving corridor and cohort; Silent Colony divides an
expedition between independent objectives. A useful geometry may be reused as
an implementation primitive, but one mission's ownership and convergence laws
do not silently become another mission's doctrine.

Not every opposing force needs a conventional squad commander. A swarm,
security network, or scripted hazard may use an inspectable mission director
with its own legal information and force ownership. Shared command
observability does not require fabricating beliefs, reserves, or human-style
intent for an actor whose mission fiction does not support them. RAID and
generic Extraction likewise require authoritative objective and outcome laws
before AI can invent meaningful command geometry for them.

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
decision authority. A mission-specific debug picture selects one perspective
at a time and projects only its published geometry, beliefs, and authored
targets; viewing the other side is an explicit debug perspective change, not a
merged tactical truth.

## Strategic-extension boundaries

A commander influence snapshot is faction-local, belief-honest, immutable, and
read-only as data. A frontline, bulge, or breakthrough analysis may derive only
from that snapshot and must never expose hidden world state. Conquest defender
mobilization has explicit mission authority to translate its own snapshot into
a coarse rally assignment; this does not grant that authority to other
missions or diagnostic consumers.

Any consumer that reallocates squads, creates reserves, or briefs subordinates
requires its own mission-specific authority contract. Diagnostic analysis does
not itself authorize action. Mechanical suppression, richer cross-squad contact
sharing, recon doctrine, and dynamic mech reassignment are separate extensions
and must preserve the same knowledge and ownership laws.

Target-faction doctrine is likewise a bounded mission-command extension, not a
new planner or a source of hidden knowledge. `target-faction-command-doctrine.md`
may bias legal assignment, reserve, recapture, and local posture choices from a
frozen battle-facing profile; it may not change objectives, force composition,
combat resolution, or the belief facts available to a squad.
