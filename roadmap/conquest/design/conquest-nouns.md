# Conquest nouns

Status: ACTIVE — Conquest owns reversible compound territory, deliberate capture and hold, defender supply pressure, canonical-keep progression, and territorial victory; defender positive victory, marine-side supply use, and inbound-garrison presentation are extensions.

Written: 2026-08-23

Updated: 2026-08-26 — paired, area-authored six-plus-six shuttle arrivals with immediate post-drop departure.

Conquest is a territorial assault: marines establish a beachhead, take the
defender's supply hubs through the city, and finish at the keep. It is not a
race to erase every defender from the map. Reinforcement makes intact
territory costly to leave behind; compound control gives the battle a durable
end condition.

## Authored siege pressure

Conquest is a bombastic late-game set piece, not an encounter that scales down
to the detachment brought into it. Mission tier and risk author a fixed initial
defender population; at Full Strength the nominal force exceeds the capacity
of forty full twelve-marine squads. The same authored-battle rule preserves all
eligible defender mechs, enemy fighter support, and map-authored static weapons
regardless of the attacking manifest. A player who commits less force accepts
the resulting disadvantage, while campaign economy remains free to let a
wealthy player commit more.

Defender intensity and mission lift demand are separate expressions of
Conquest scale. Raising the population of the siege does not claim that the
briefing, carrier, or meta layer must provide proportionally more drops.

## Arrival doctrine

Conquest owns a mission-configurable paired arrival policy. A Valkyrie's
twelve-seat hull is loaded with six marines per sortie; two transports approach
distinct berths in one map-authored BEACH arrival area on the same wave delay.
Their passengers join one twelve-marine ground squad, form up before executing
the commander's advance, and later cycles create new squads rather than
silently enlarging the first. Employer and player craft never share an arrival
group across the ownership boundary.

Arrival areas are generated after terrain, structures, and the spawn anchor are
final. Each publishes two clear 5×5 berths, a shared SOUTH or WEST approach, and
a stable identity. Conquest setup selects the required areas evenly across the
frontage and fails closed when the map cannot provide enough. These troop
transports retain their weapons during approach and egress but depart as soon as
unloading completes; armed loiter remains available to other mission policies.

## Territory and compounds

A **compound** is a defender supply hub represented by a tactical
`COMMAND_POST`, `BARRACKS`, or `ARMORY`. It has one ownership state:

- **DEFENDER_HELD** is defender territory, including a temporarily empty
  compound that defenders still control.
- **CONTESTED** is an active or paused turnover. It remains defender supply
  until the capture completes.
- **MARINE_HELD** is territory captured by marines. That compound no longer
  supplies the defender.

Capture is deliberately asymmetric. Marines must sustain uncontested control
long enough to take a compound; defenders recover a contested compound more
quickly once they regain it. Defender entry can reopen a marine-held compound,
so capture is reversible rather than a one-way destruction event. A mixed or
empty transition pauses its progress instead of assigning ownership by a
momentary absence.

## Assault and control loop

The marine commander treats compound capture as a primary objective. It sends
a measured, capped detachment toward a compound with no believed defender and
preserves a capture already underway. Unknown occupancy is authorization for a
probe, not positive knowledge that a distant compound is clear. When actionable
front resistance exists, squads without useful front work take distant capture
duty first and fresh allocations must leave at least one executable actionable
squad on the broader front. This budget is global across all compounds and
therefore cannot reset once per objective or once per command pulse. A compound
still believed defended is not fed a fresh assault squad; a squad already at
any objective's threshold may commit without consuming the distant budget.
Explicit keep convergence remains the culminating exception. This prevents
both accidental capture avoidance and the assault force abandoning the fight
for distant buildings.

The broader front is organized into lateral **tracks**. A squad keeps a sticky
preferred track so the assault remains readable, but the track is a coordination
frame rather than an ownership fence. When its preferred track has no actionable
resistance, a mobile squad may support either neighboring track without being
permanently re-homed. Useful resistance in the preferred track wins, preventing
routine lateral churn.

Conquest command publishes an immutable **front snapshot** after each command
tick. It explains the current phase, every mobile squad's preferred and
effective track, the reason and target behind its order, and each track's
friendly progress and belief-derived hostile pressure/frontier. Selected-squad
diagnostics additionally mark when a reachable distant capture was deferred to
retain that squad for actionable front resistance; this policy explanation is
separate from the reason for any actual lane order. Presentation and dumps
consume that published command state; they do not infer
a second plan or reveal hidden defenders. The assignment decision remains the
commander's authority, while local squad doctrine decides how to prosecute the
contact.

The front snapshot also carries frozen own-squad physical observations from the
same command frame: live strength, centroid, current zone, local contact, and
execution suspension. These facts explain whether an executable order is
closing on its published marker or has reached its target zone without exposing
opposing live positions. Exact occupancy of a compound's anchor capture zone is
objective/referee evidence instead. An adjacent assault commitment, entry into
the broader authored footprint, and presence in the actual capture zone are not
interchangeable claims. The current trace reports the first and third;
footprint entry remains a separate future measure rather than something
inferred from either one.

Conquest debug presentation projects one front snapshot at a time. It may draw
the side's three track extents, friendly body and lead fronts, known-hostile
front, and commander-authored exact targets or labelled representative zone
markers. Those marks are explanations
of the selected perspective, not objective world truth. The selected squad's
panel names its strategic order, target, reason, and track pressure beside the
squad's actual tactical goal and current plan, making disagreement between
command intent and local execution visible without inventing another planner.

The same three physical tracks organize defender response, but not through the
marine commander's state. The defender commander reads only defender influence.
An initial patrol squad that establishes contact raises a coarse threatened
track and forward band; a bounded number of other initial patrol squads may
rally into that track, preferring their home track and then one neighbor. The
rally is an own-force destination, not a copied hostile identity or reported
cell, and the receiving squad's local contact picture supersedes it on contact.

Born garrisons never enter this mobile pool. Explicit hold or recapture work
outranks a soft track response, at least one otherwise-free patrol remains in
reserve when possible, and an expired faction report releases only assignments
owned by defender command. Squads delivered later remain under reinforcement
and counterattack authority rather than being silently absorbed into the
starting reserve. The setup pool is captured from the live squad roster before
the first alert aggregation, so roster membership and `PATROL` role—not a
not-yet-populated cached alive count—define the starting force. Authored setup
garrisons claim garrison ownership at setup. A later squad becomes eligible
only through an explicit handoff to `conquest-defender`; the handoff never
happens implicitly at delivery.

Conquest is the first production **command duel**: attacker and defender
strategies can progress the territorial battle without requiring direct player
orders. They share authored track geometry and objective truth only where the
mission law permits it; they do not share influence, reports, assignments,
reserves, or command snapshots. Player force selection and command powers may
tip the battle, but baseline command remains responsible for coherent advance,
delay, reserve use, and handoff to local squad doctrine.

On a marine capture, a free marine garrison shuttle answers once and its squad
is born to hold that compound. The original assault may therefore continue its
push. If defenders reclaim the compound, the garrison response re-arms for a
later recapture. This is the local territorial cycle: take, hold, counter,
and retake.

## Supply pressure

Compounds are also defender capabilities. An intact or contested BARRACKS
permits walk-in reinforcement, an ARMORY permits convoy reinforcement, and a
COMMAND_POST permits shuttle reinforcement. Capturing every compound of a
kind removes that defender delivery capability. The usual PORT, CITY, then
FORTRESS push removes convoy delivery first, walk-ins second, and shuttle
delivery last; a different capture order changes that degradation order.

`reinforcement-nouns.md` owns requests, tickets, triggers, delivery means,
recapture targets, and counterattack waves. Conquest owns the compound state
that those means read as supply; it does not choose how a request is delivered.
`convoy-nouns.md` owns convoy movement and deboarding, and `air-nouns.md` owns
shuttle bodies and sorties. A marine-held compound is queryable as marine
territory, and resource production transfers to its marine owner. Marine-held
resource production does not by itself authorize delivery: the installed means
fulfill defender requests only, and a marine reinforcement loop requires its
own request, supply, and means policy.

## Progression and the keep

The Conquest traversal is PORT, CITY, then FORTRESS. The outer supply hubs
give the player a readable progression before the fortress command post closes
the assault. There is exactly one canonical COMMAND_POST keep: it is both the
climactic territorial objective and a required part of a valid Conquest map.

When that keep is the only compound not held by marines, command enters
**keep convergence**. Every mobile assault squad receives the same culminating
secure-compound context regardless of track boundary, allowing local approach
and room-clear behavior to converge the force. Born-holding garrisons remain on
station. Reachability remains a legality boundary: a squad cannot preserve or
receive a compound order whose anchor zone is disconnected from its current
zone. An unreachable sticky capture is released, and an unreachable sole keep
is reported explicitly instead of pinning the squad indefinitely.

If the keep is held but one earlier compound is the sole contested territorial
objective, command enters **final-compound convergence**. The normal capture
quota still owns `SECURE_COMPOUND`; all other mobile squads receive bounded
room-clear support across any track so an end-of-map recapture cannot strand
the assault behind a track boundary. With more than one uncaptured compound,
the front reopens and ordinary compound and track priorities resume. A sole
uncontested compound likewise keeps normal capture allocation rather than
pulling the whole force into a needless convergence.

`mapgen-nouns.md` owns the generator recipe, compound footprint, fortress
geometry, and validation of that canonical keep. Conquest depends on the
resulting tactical places and must not restate their carve or layout details.

## Victory and extension paths

Marines win only when every compound is MARINE_HELD and the marine side still
has a participant in play. A missing compound layer or missing command post is
a malformed Conquest setup and fails the marine objective closed. Defender
victory is elimination-based; a positive territory-hold victory is an
extension.

A committed future shuttle sortie counts as an attacker participant even while
its transport is departing to rearm. Defender elimination cannot resolve in
the empty-ground interval between authored cycles; only the final empty sortie
and absence of live marines ends the attacker side.

The model has three extension paths: a defender positive territory victory,
marine-side supply from captured compounds, and a readable inbound-garrison
indication. Each must establish the objective, supply/delivery, or presentation
authority it changes rather than inheriting authority from compound ownership.
