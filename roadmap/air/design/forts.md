# Forts — defences you have to take apart

Status: ACTIVE

Written: 2026-10-14

Updated: 2026-10-13 — bolder wire, taken plant keeps running; fort buildings drawn by function with intact, damaged, wrecked and taken states; shield fort built (slice 3: octagonal works, inner keep, power plant and control room, dome held at the control room), folding in the local shield relay; redoubt/citadel and richer command awareness remain.

Read `ground-defences.md` first (the battery, the dome, nests, fire control, the placement
law), then the "Authored relationships within a compound" section of `compound-programs.md`,
which is the layout machinery a fort is built from. The local shield relay, which was once its
own story, is the shield fort below.

## What the owner asked for

Templated forts for the ground-defence emplacements: standing works that need ground forces
or overwhelming force to take out, and that can be shut down aspect by aspect.

## The problem with a bare structure

What ships today is a structure standing in a field: a battery with a fire-control radius
around it, a generator with a capture radius around it, nests scattered across open ground.
Each is one thing with one switch, so a battle against it is one decision — walk a squad
onto the switch or shoot the box. There is no approach, no inside, no choice of which part
to break, and nothing a ship and a squad do for each other.

## What a fort is

A **fort** is an authored compound layout built around one defensive purpose, from the same
rotatable-group machinery the maintenance outpost uses. It owns its local geometry — a wall
with gates, a protected interior, buildings with doors and windows — and a mission declares
which forts a battle carries the way it declares structures now. Map generation places what
was declared and connects the gates to the road network; it never decides how many.

A fort is made of **aspects**. An aspect is one function the fort performs, housed in one
place that can be reached, and each aspect can be shut down on its own:

| Aspect | Housed in | Shut down by | What stops |
|---|---|---|---|
| Guns | the battery platform | destroying it | the battery, permanently |
| Fire control | a bunker inside the wall | taking it (capture as today) or wrecking it | aim — the guns fall silent while it is not the holder's |
| Magazine | a hardened store | wrecking it (it goes up) | sustained fire: the guns fire out ready rounds, then stop |
| Power | a plant or capacitor bank | taking or wrecking it | everything that draws on it — the dome, the guns' traverse, fire control's sensors |
| Emitter | the shield generator | destroying it | the dome, permanently |
| Sensors | a mast or radar | wrecking it | the envelope shrinks to what the fort can see by eye |

Capture is the existing rule: an aspect belongs to whoever holds its place unopposed, either
side may hold it, and a captured aspect works for its new holder. Nothing is special about
attacker or defender.

## Two ways in

**Ground forces.** Breach a gate or a wall, fight through the interior, and take or wreck
the aspects that matter. A squad that takes fire control silences the guns without
destroying them, and keeps them for its own side. This is the marine's job and the reason a
fort has an inside.

**Overwhelming force.** Ship-to-ground is a super weapon, and a fort is what makes that
cost something. Sustained ship fire and barrage overload the dome; while it is down, the
fort's walls and aspects are open to ship-grade fire, and enough of it wrecks them from
above. It costs time under the battery's envelope, flux, and a ship's hull — so it is a
real alternative, never a free one.

The two help each other. A squad inside cuts the power and the dome drops for the fleet; a
barrage that overloads the dome lets a squad's boats land inside the wall. A fort that can
be cracked only one way is a puzzle with one answer.

## The family

- **Battery fort** — the platform, fire control, a magazine, and power. Nests on the wall.
- **Shield fort** — the generator, power, and a control room: one node of the world's shield,
  covering its own ground. It is the local shield relay this direction absorbed. It is a node,
  not the planet's shield: a fire team does not switch the world-wide envelope off, it opens
  the local tactical footprint to fire support while the rest of the planet stays protected.
  Orbital Barrage is not denied by it; the dome absorbs barrage as ship-grade ordnance. Any
  faction or visual treatment of its buildings is a presentation layer and may not change what
  the fort does.
- **Redoubt** — a walled nest cluster with a small command post; anti-air and anti-infantry,
  no ship-grade guns.
- **Citadel** — a battery and a shield fort sharing one wall and one power plant, so cutting
  the power drops the dome over the guns at the same moment it slows them.

A mission declares forts by kind; a bare structure stays available to scenes, debug, and
missions that want one.

## Defaults (the owner may overturn these)

- **Power is local to a fort.** A citadel shares power because it is one fort; separate forts
  do not feed each other. A world-wide grid is a later choice, not a starting assumption.
- **Walls are breachable** by demolition and by ship-grade fire while the dome is down,
  using the existing wall-health machinery.
- **Aspects do not respawn.** A wrecked aspect stays wrecked for the battle; a taken one can
  be retaken.

## Defence in depth (standing direction)

A fort is never a wall round a pad. It is layered outside in, and a layer that is only a rectangle is a failed layer:

1. **Outer belt.** An irregular ring of trench, sandbag line, wire and nests with firing steps, standing off the main
   works across an open killing ground (the glacis). It is not a box: it kinks and re-enters so no stretch is
   enfiladed end to end, it opens in front of each gate where the approach lane crosses it, and it follows the ground
   it is given: a cell the city or a neighbour holds is simply not drawn. The belt is cover and obstacle by the existing
   semantics (sandbag and trench cells are walkable heavy cover; wire is a breachable fixture), never decoration.
2. **Main works.** A thick chamfered trace with an angled bastion at each corner (nests inside them), and a gatehouse
   with a baffle at each gate so the way in turns under fire. Walls use the ordinary wall-health machinery.
3. **Interior.** Buildings read by function: the fire-control bunker low and hardened inside its own berm, the
   magazine apart from the guns inside a double berm, the power plant with its plant standing in a fenced yard, and the
   battery in a revetted pit entered round a turn. Paving exists only where vehicles drive; the rest is earth and cover.
4. **Siting.** The works and glacis need clear ground, loose stone is levelled, and forts never stand flush: a placed
   fort's whole footprint, belt included, is closed to the next.

New fort kinds follow the same order. Capture, aspects and mission authority are unchanged by it.

## Built: battery fort and aspects (slices 1 and 2)

- **Layout.** `BatteryFort` is the authored frame (works, gates, buildings, lanes) and `FortBelt` the outer belt; both
  rotate as one at four cardinal turns. `FortStage` stamps it on ground the city has left free (city is prior;
  roads and precinct claims are respected), turns the main gate to face the map centre where it can, and paves a lane
  from each gate toward the nearest road (ground paint only, not in the road graph). Fort walls carry wall-health, so
  they can be breached. The belt uses the earthworks sheet `fort-earthworks` (trench sections by connection mask, thick
  sandbag, concertina wire), produced through the geometry-first loop.
- **Declaration.** `GroundDefences` carries declared forts by `FortKind` (BATTERY_FORT today). `MissionDefencePolicy`
  stays the only decider; Conquest's untuned first pass converts its declared batteries into forts
  (`CONQUEST_BATTERIES_AS_FORTS`, one line to turn off). Bare structures remain for scenes and debug, the DEBUG dial
  has a fort option, and fixtures round-trip forts (a legacy fixture reads as none).
- **Aspects.** Each aspect is a 3x3 core structure inside its building, captured by presence like fire control was.
  Fire control lives in the bunker: the guns are silent while the bunker is not the holder's, and a wrecked bunker
  silences them for good. A wrecked magazine leaves a ready-round pool the guns fire out and then stop, and the
  magazine explodes through the existing blast machinery. Power taken, contested or wrecked slows traverse and fire
  to a quarter. Power is local to a fort. Either side may hold any aspect and a captured aspect serves its new
  holder; wrecked stays wrecked, taken can be retaken. State is drawn on the map beside the battery markers.
- **Commanders.** `DefenceStructureTactics` still works: a fort's fact points the squad at the bunker, disclosed when
  the platform is sighted.
- **Tuning is untuned.** Numbers (12 ready rounds, 0.25 unpowered rate, core hit points) are first passes.

## Built: shield fort (slice 3)

- **Layout.** `ShieldFort` is a different fortress from the battery fort, not that one with the guns swapped. An
  octagonal trace with a round tower on each diagonal face (instead of pointed corner bastions), three gates (a main
  gate and two posterns, each with a gatehouse and a baffle), and an **inner keep**: a two-cell ring wall round the
  generator open only at two opposite diagonal gaps, so an outer gate leads into a yard and never at the emitter.
  The buildings keep to the half of the works away from the main gate. It shares the belt machinery (`FortBelt` now
  takes a spec per kind) and the stamping stage (`FortStage`, driven by a `FortPlan` per kind; fort ids are unique
  across kinds on a map).
- **Aspects.** The generator is the **emitter** (no presence capture; destroying it ends the dome for good). The
  **power plant** feeds the dome: wrecked, contested or held by a side that is not the dome's holder, the dome is down
  exactly as an overloaded one is, and rises again after a reboot when power returns. The **control room** decides who
  the dome serves: whoever holds it holds the dome, and the generator changes sides with it (the dome reboots first). A
  governed dome ignores the old ring of ground round the generator. Power is local to the fort, and the rules are
  written for sides: a side that has taken only the control room has a dome with no power, because the plant is still
  the other side's to withhold; taking both gives it the dome. A wrecked control room leaves the dome with whoever held it
  last; a wrecked plant or emitter stays wrecked.
- **Authority.** `GroundDefences` declares shield forts by kind. Conquest's untuned first pass converts its
  declared shield generator into one shield fort (`CONQUEST_SHIELD_AS_FORT`, one line to turn off); the DEBUG dial has a
  shield-fort option; fixtures round-trip the declaration and a legacy fixture reads as none. A bare generator beside the
  keep remains for scenes and the old dial option.
- **Commanders.** A shield fort discloses two facts, both on sighting the generator: the control room (where the dome
  is taken, held or retaken, the existing take/retake/defend roles) and the power plant (a `FORT_POWER` fact, work for the
  side that does not hold it). Fair knowledge only.
- **Map.** Aspect rings and labels draw as for the battery fort, with a "DOME UNPOWERED" line over the generator.
- **Untuned.** A control room that is merely contested does not drop the dome (only the plant does); the tower and
  keep sizes, wall hit points and the whole conversion are first passes.

## Built: building art by function

A fort building is drawn from art authored per aspect kind on the aspect's core structure (`buildingArt` in the turret
catalog; the platform names the gatehouse). Roof art covers exactly the building's interior floor, in the fort's local
frame turned with it (`FortFrame`), and replaces the ordinary brick roof there: it fades with the same per-building
reveal, so capture inside a building, fog and the roof cave-in are untouched. The picture follows the aspect: intact,
damaged (core durability under 60%), and wrecked (roof opened by the cave-in machinery, a low wreck over the floor and
under the units). Machinery on the roof runs only while the building stands and its power is its holder's: the bunker's
radar, the plant's turbine fans and glow, the magazine's vent fans and warning lamps. A taken building flies the holder's
banner and takes a wash of the holder's colour. Gatehouse towers carry embrasured caps while their walls stand, the gate
carries folded leaves, and neither draws over a breach. A new fort kind adds data (a `buildingArt` block per aspect) and a
layout, not renderer code. Retained sources: `art-source/fort-buildings/`. Art is authored with the door on one edge; a plan whose door is on the other
edge turns it half a revolution (the shield fort's plant). Interior berms are two courses thick and belt wire is two rows.
The belt wire is a bold coil on a dark contact shadow. A taken plant keeps running for its new holder: only a contested point or a destroyed battery stops it. The shield fort carries the same gatehouse pieces, turned to face each of its three gates.

## Slices

1. **Fort layout family** — one battery fort as an authored compound layout (wall, gates,
   platform, fire-control bunker, magazine, power), placed when a mission declares it.
   Fire control moves from a radius around the platform to its bunker.
2. **Aspects as dependencies** — power feeds guns, sensors and dome; the magazine feeds
   sustained fire; each aspect's state is readable on the map.
3. **Shield fort** (built) — see above; it folded the local shield relay story in.
4. **Redoubt and citadel.**
5. **Command awareness** — `DefenceStructureTactics` reasons about aspects (which one to take
   or wreck first for the effect wanted) using only what a side knows.
6. **Art** — earthworks and the fort's buildings with their aspect states and animated parts are done; the shield fort's own buildings follow the same data.
