# Conquest nouns

Status: ACTIVE — Conquest owns reversible compound territory, deliberate capture and hold, defender supply pressure, canonical-keep progression, and territorial victory; defender positive victory, marine-side supply use, and inbound-garrison presentation are extensions.

Written: 2026-08-23

Updated: 2026-09-01 — Full Strength now authors eighty-four squads / 1,008
marines into one three-lane battle through the existing six-shuttle ferry.

Earlier 2026-08-29 — a mission now states the map features it requires and a
map that falls short is re-rolled rather than played.

Earlier 2026-08-29 — compact numbered world beacons now reserve progress and
animation for actively contested compounds.

Earlier 2026-08-28 — made the footprint-resolved capture room authoritative
across capture, command, execution, presentation, and evidence, and scoped
occupancy to the footprint as well so an open compound is capturable.

Conquest is a territorial assault: marines establish a beachhead, take the
defender's supply hubs through the city, and finish at the keep. It is not a
race to erase every defender from the map. Reinforcement makes intact
territory costly to leave behind; compound control gives the battle a durable
end condition.

## The map a mission requires

A mission is a promise about the fight, and part of that promise is structural:
conquest promises a fortified defender with a keep to take, an air arm with a
field to fly from, and a shore to land on. **A map that lacks one of those is
not a conquest map**, however correct every pass that built it was.

Generation cannot notice this on its own, and the failure is silent by
construction. Every pass declines politely — a ward with no room builds no
airfield, a claim that came up short takes a smaller lot, a stamper with
nowhere to stand emits nothing — and each of those is the right local decision
made by something that does not know what the mission was promised. A quarter
of conquest battles shipped with no garrison airfield that way. The only
symptom was an enemy whose reinforcements all arrived from off map, which reads
as a balance choice rather than as a missing building.

So the requirement is stated **next to the mission** rather than inside the
pass that happens to satisfy it, and checked against the **finished map** — a
question about the output, which does not care which pass produced it and keeps
working when one is replaced.

**A map that falls short is re-rolled, and only then an error.** The seed is
the generator's only input, so a different seed is the whole of the fix, and
the first attempt is always the caller's own seed — an ordinary battle is
bit-for-bit what it was. Running out of seeds is a real fault and says which
features were missing, because handing back an invalid map is exactly how the
missing airfield survived as long as it did.

**A mission with nothing stated requires nothing.** Requirements are a claim
about a particular mission, and inventing one for a mission nobody has thought
about turns a working battle into a crash. Conquest is currently the only
mission that makes structural demands.

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
Conquest scale. Live play establishes the current Full Strength assault envelope
at eighty-four squads / 1,008 marines against the nominal 630-defender siege,
rather than deriving it arithmetically from that initial population. Reinforced
Conquest is forty-two squads / 504 marines. The briefing and lift gate state
those authored commitments honestly.

## Arrival doctrine

Conquest owns a mission-configurable paired arrival policy. Committed campaign
transports supply the operation's lift, while dedicated six-seat Aeroshuttles
make the final descent. A mission authors how many BEACH arrival areas are
active, how many reusable Aeroshuttle pairs serve each area, and the maximum
per-craft timing variance. Normal Conquest uses its three tactical lanes as
three drop zones with one dedicated pair per zone. Each pair approaches the
area's two distinct berths, leaves after every drop, re-arms off-map, and
reuses those berths for later squads. Initial launch and re-arm variance are
seeded construction facts, so craft do not fly in lockstep while fixture
replays remain deterministic. Full Strength authors 168 six-seat half-squad
sorties, balanced as twenty-eight cycles on each of the six Aeroshuttles, so all
1,008 named marines enter the one battle. The authored drop demand is a minimum
commitment, not a ceiling: every selected campaign or debug squad is added to
the cycle plan instead of remaining in an implicit orbital reserve.
Their passengers join one twelve-marine ground squad, form up before executing
the commander's advance, and later cycles create new squads rather than
silently enlarging the first. Employer and player craft never share an arrival
group or reusable pair across the ownership boundary.

Arrival areas are generated after terrain, structures, and the spawn anchor are
final. Each publishes two clear 5×5 berths, a shared SOUTH or WEST approach, and
a stable identity. Player pairs are balanced across the mission's configured
areas and additional committed hulls or selected squads increase cycles rather
than permanent on-map craft. More than one configured pair per area reuses its
two berths on staggered arrival groups. Employer lift remains ownership-separated
but reuses the selected physical areas instead of inventing another beachhead.
An unusually small manifest spawns only pairs with at least one real sortie; the
configuration never creates unbacked personnel merely to fill an empty pair.
These troop transports retain their weapons during approach and egress but
depart as soon as unloading completes; armed loiter remains available to other
mission policies.

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

A compound is captured in its **capture room** — the room holding the nearest
standable cell to its anchor within its own footprint. The room is resolved
from the footprint rather than read off the anchor, because a compound's anchor
is a tactical-node anchor and carries no promise of standing on open floor (see
`mapgen-nouns.md`). Resolving it any other way makes a compound whose anchor
happens to sit on a wall or a furnished cell permanently uncapturable, which on
Conquest — where victory requires every compound to flip — is an unwinnable
mission rather than a cosmetic defect. Bounding the search to the compound's
own footprint is what keeps the room its own: a compound never captures in a
neighbour's room or out on the parade ground.

Occupancy is scoped the same way. A side holds a compound by standing inside
its footprint **and** in its capture room; the room alone is not enough. For an
enclosed compound the two agree, because its room lies wholly within its
footprint, and the footprint clause decides nothing. For an **open compound**
it decides everything. An airfield has no walls, so the room its apron belongs
to is the outdoors: on a generated ward a 216-cell apron resolves to a zone of
1341 cells, nearly half the map's walkable area. Read from the room alone, such
a compound is contested by the first marine to stand anywhere outside a
building, and then frozen there forever, because every defender outdoors is
equally present and a two-sided room pauses the timer. It shows permanently
mid-capture and can neither fall nor be held. The same misreading takes any
building the moment its wall is breached into the street.

The resolved capture cell and its live zone identity are the compound's
authoritative tactical target. Command assignment, reachability, keep
convergence, action markers, capture progress, and trace evidence all consume
that same resolution. The raw tactical-node anchor remains map-authored
identity and may be blocked or furnished; it is never a substitute capture
zone.

World presentation gives each compound a compact kind-and-ordinal identity
matching the battle objective rail. Defender- and marine-held beacons stay
subdued; only a contested compound gains the amber progress arc, percentage,
and restrained pulse. The marker remains centered on the authored compound for
legibility and does not replace the resolved capture cell as tactical authority.

A player contextual capture request follows the same authority. A click inside
an uncaptured compound names its stable authored node, resolves that node's live
capture room, and temporarily asks the selected Marine infantry squad to execute
the ordinary secure-compound action. The squad approaches, clears, and holds;
arrival alone does not release it. Release occurs when this compound reports
`MARINE_HELD`, at which point the squad resumes the current mission directive.
A compound already held by Marines is ordinary ground for right-click movement.

Having a capture room is not sufficient; marines have to be able to walk into
it. Every compound is therefore reachable from the marine spawn, and generation
owes that guarantee rather than the battle layer coping with its absence — no
tactic recovers from a supply hub with no way in. It follows that a compound
outranks the structures that would otherwise overwrite it: where the fortress
wall would cut one off from the map, the wall yields a breach instead (see
`mapgen-nouns.md`). One unintended opening is a far smaller cost than a
mission that cannot be won.

## Assault and control loop

`conquest-command.md` is canonical for attacker/defender allocation, track
mobilization, command pictures, diagnostics, and command evidence. This section
states the territorial constraints Conquest exposes to that adapter.

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

Travel through intermediate rooms remains cautious and may yield to contact.
The final hop into the assigned capture room is a committed assault: route
contact does not leave a squad indefinitely waiting outside the objective.
Once inside, hold posts are chosen only from that capture room, even when the
compound footprint contains several garrison rooms. Local engagement doctrine
still governs threats; commitment changes entry, not target knowledge.

The broader front is organized into lateral **tracks**. A squad keeps a sticky
preferred track so the assault remains readable, but the track is a coordination
frame rather than an ownership fence. When its preferred track has no actionable
resistance, a mobile squad may support either neighboring track without being
permanently re-homed. Useful resistance in the preferred track wins, preventing
routine lateral churn.

A known track front does not require a defender to occupy a discrete room.
When a mobile squad has no actionable zone or local contact but faction-local
belief places resistance farther up its preferred track, command gives it an
**advance-track staging order**. The marker is an own-force destination behind
the nearest believed hostile, cannot leap far beyond the friendly lead or pull
the squad backward, and is snapped to a reachable walkable cell in that track.
The marker is quantized and directive-stable so small belief changes do not
produce visible command jitter. Local contact immediately yields to squad
engagement doctrine. This closes open-ground front gaps without turning the
exterior flood into a fictitious clear-zone objective.

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
same command frame: live strength, centroid, leader zone, each member's zone,
local contact, and execution suspension. These facts explain whether an
executable order is closing on its published marker or whether any squad member
has reached its assigned target zone without exposing opposing live positions.
Exact whole-zone occupancy and capture progress remain objective/referee
evidence. An adjacent assault commitment, entry into the broader authored
footprint, own-member presence in the actual capture zone, and neutral capture
presence are not interchangeable claims. Footprint entry remains a separate
future measure rather than something inferred from any of them.

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
owned by defender command. The setup pool is captured from the live squad
roster before the first alert aggregation, so roster membership and `PATROL`
role--not a not-yet-populated cached alive count--define the starting force.
Authored setup garrisons claim garrison ownership at setup.

Conquest convoy delivery is the explicit later-force handoff. At dispatch time
the defender commander converts its latest faction-honest hostile-front belief
and the request's track into a quantized safe deployment band behind contact.
The vehicle enters from the strict defender rear edge, and its passengers are
minted directly under `MISSION_COMMAND / conquest-defender` with the request's
node hold or lost-zone clear objective. Defender command preserves and reports
that relief objective instead of absorbing the squad into a soft track reserve. Shuttle and walk-in
reinforcements retain their existing reinforcement ownership until their own
mission policies explicitly opt into a handoff.

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
