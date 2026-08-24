# Conquest nouns

Status: ACTIVE — Conquest owns reversible compound territory, deliberate capture and hold, defender supply pressure, canonical-keep progression, and territorial victory; defender positive victory, marine-side supply use, and inbound-garrison presentation are extensions.

Written: 2026-08-23

Updated: 2026-08-24 — defined preferred tracks, soft neighboring support, command observability, keep convergence, and fixed authored siege pressure.

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
a measured, capped detachment to an uncontested compound and preserves a
capture already underway. A compound still defended is not fed a fresh assault
squad; only a squad already at its threshold may commit. Other squads keep
pushing the broader front. This prevents both accidental capture avoidance and
the entire assault force abandoning the fight for one building.

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
presentation and dumps consume that published command state; they do not infer
a second plan or reveal hidden defenders. The assignment decision remains the
commander's authority, while local squad doctrine decides how to prosecute the
contact.

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
station. If an earlier compound is recaptured, the front immediately reopens
and ordinary compound and track priorities resume.

`mapgen-nouns.md` owns the generator recipe, compound footprint, fortress
geometry, and validation of that canonical keep. Conquest depends on the
resulting tactical places and must not restate their carve or layout details.

## Victory and extension paths

Marines win only when every compound is MARINE_HELD and the marine side still
has a participant in play. A missing compound layer or missing command post is
a malformed Conquest setup and fails the marine objective closed. Defender
victory is elimination-based; a positive territory-hold victory is an
extension.

The model has three extension paths: a defender positive territory victory,
marine-side supply from captured compounds, and a readable inbound-garrison
indication. Each must establish the objective, supply/delivery, or presentation
authority it changes rather than inheriting authority from compound ownership.
