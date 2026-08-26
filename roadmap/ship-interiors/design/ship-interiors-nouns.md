# Ship interior nouns

Status: ACTIVE — the model is authored; no generator, facility, or adoption slice has shipped.

Written: 2026-08-26

Updated: 2026-08-26 — defined exclusive fixture task points and physically
navigated ambient work as the execution contract for inhabited rooms.

Ship interiors is the model for navigable shipboard space: the decks a mercenary
company lives and works on, the facilities it operates and grows, and the hostile
decks it will eventually board. It owns the *ship as a place*. It does not own
generation machinery, campaign economy, or the screens that depict it.

Generation machinery is `mapgen-nouns.md`'s authority. A ship deck is a map
family inside that pipeline — a recipe of stages over a generation context,
producing an ordinary validated map result. Everything below describes what makes
that family a ship rather than a station or a city.

## Why ships are not stations

Station recipes already carve reachable interiors out of solid hull, and their
layout-neutral topology — rooms, corridors, degree, depth-from-entry,
articulation rooms, bridge corridors, on-spine versus on-loop — is exactly the
signal a shipboard fight needs. That tier is shared.

What is not shared is the spatial premise. A station is organized around a
**core**: concentric rings, cardinal ports converging inward, radial depth,
mirrored geometry. A ship is organized around an **axis**. It is elongated and
deliberately asymmetric from end to end, its width varies along its length, and
it has no center to besiege.

| | Station | Ship deck |
|---|---|---|
| Organizing premise | a core | a longitudinal axis |
| Symmetry | radial or mirrored | elongated, asymmetric fore to aft |
| Assault gradient | radial depth from the perimeter | distance along the axis from the breach |
| Chokepoints | articulation rooms and gates | transverse bulkheads, ordered by frame |
| Entry | cardinal ports or the perimeter | a breach point on either flank, at a chosen frame |
| Objective | the core | end-dependent, and there are two ends |

That last row is the consequential one. A station fight converges. A ship fight
runs *along* something, and a ship has two natural prizes at opposite ends — the
command spaces forward and the engineering spaces aft. Choosing which end a
mission wants produces a different fight out of the same deck, with no extra
authored content.

## Vocabulary

- A **deck** is one navigable map: the unit of generation, of battle, and of
  camera framing. A ship is an ordered set of decks. One battle occupies one
  deck.
- A **frame** is a discrete position along the deck's longitudinal axis, numbered
  from the bow. It is the deck's primary coordinate for reasoning about progress,
  ordering, and objectives. "Hold the frame-forty bulkhead" is a legible tactical
  instruction in a way that "hold articulation room seven" is not.
- The **hull profile** is the deck's beam as a function of frame: narrow at the
  bow, broadest amidships, tapering aft, and not required to be equal to port and
  starboard. The profile is the family's visual and tactical identity.
- The **spine** is the primary fore-aft circulation corridor. Compartments hang
  off it. It is the deck's authored main line, not whichever corridor turns out
  longest.
- A **transverse bulkhead** divides the deck across its beam at one frame and
  admits passage only through its authored hatches. It is the ship's natural
  chokepoint, and unlike a station's scattered articulation rooms, bulkheads come
  in a defensible order.
- A **longitudinal zone** is one of fore, midships, or aft. A zone is a
  functional prior, not decoration: command and sensor spaces belong forward,
  volume — hangar, cargo, habitation — belongs amidships, and power, drive, and
  life support belong aft. Zone affinity is how a compartment finds its place
  without a coordinate table.
- A **compartment** is one purposed room on a deck, labeled at carve time with an
  ordinary room purpose. Consumers ask its purpose.
- A **facility** is a compartment the company operates: a barracks, a mech bay,
  an armory, a medical bay. A facility owns fixture counts, extent, and the
  capacity those imply. It is the thing an upgrade acts on.
- A **fixture** is a placed object inside a compartment that declares both its
  tactical effect — cover, blocking, sightline — and its **ambient affordance**,
  the activity an idle crew member performs at it. A berth affords rest; a gantry
  affords work; a firing lane affords practice.
- A **fixture group** is the placement unit: an anchor fixture, its satellites,
  and a shared orientation. A workspace is a bench with its stool, its parts bin,
  and its clutter, all facing the same way. Fixtures are placed as groups, never
  as independent points on a grid.
- A **circulation lane** is authored walkable space connecting a compartment's
  entries to its fixture groups. It is a placement obligation: a fill may not
  encroach on it, and it is why a room reads as somewhere people move through
  rather than an obstacle field.
- A **level** is a discrete elevation within one deck — the floor, and any
  raised catwalk, mezzanine, or gallery above it. Levels do not overlap: a cell
  belongs to exactly one level, and a raised gallery surrounds an open well
  rather than roofing it.
- A **breach point** is where boarders enter a deck: an airlock, a docking
  collar, or a cut hull section. It sets the origin of the longitudinal assault
  gradient and is a generation fact, not a spawn coordinate discovered later.
- A **deck graph** is the published topology: the shared layout-neutral tier plus
  this family's annotation — each compartment's frame span, zone, and side of the
  spine.

## Home decks and prize decks

The company's flagship and a boarding target are the **same product of the same
generator**, differing in parameters rather than pipeline:

| | Home deck | Prize deck |
|---|---|---|
| Faction and threat | friendly; ambient threat policy admits no combatants | hostile garrison |
| Who chooses the rooms | the company's owned facilities and their upgrade level | the campaign-resolved target's class and role |
| How it is entered | an operations screen, continuously | a mission, with a breach point |
| What changes it | an upgrade transaction | battle damage, for the duration |

This is less of a leap than it looks. The flagship spaces already run a real
bounded battle simulation with real fixtures, real navigation, and a threat
policy set to admit nobody; the marine practice range already fires live rounds
through it. A home deck is already a battle map that happens to have no enemies
on it.

Keeping one pipeline is a standing requirement, not a convenience. If home and
prize decks ever need to differ, the difference is expressed as a parameter of
the family. A second generator for the company's own ship would immediately drift
from the one that has to stay tactically honest.

## Facilities, capacity, and growth

A facility's **capacity is its fixtures**. The number of berths in the barracks
is the number of billets; the number of gantries in the mech bay is the number of
heavy assets it can service. These are one fact with one owner, not a room
drawing and a separate number that can disagree.

Ship interiors publishes capacity. It does not own what consumes it: personnel
authority still owns the roster, and progression still owns quality and kit.

An **upgrade** changes a facility's parameters and therefore changes the room:
more berths, another gantry, additional firing lanes, a wider extent. This is the
model's central promise and its sharpest constraint — an improvement that does
not change the space does not belong here. Non-spatial gains are ordinary
progression.

Growth is bounded by the hull. A deck has finite area and a fixed profile, so
expanding one facility eventually costs another: bay space comes out of berthing,
berthing comes out of stores. The trade is the point. An upgrade chain that only
ever adds is a menu; one that forces a company to decide what its ship is *for*
is a decision.

## Ambient life

Crew routes are **derived from fixtures**, never hand-listed per deck. A route
author reads a compartment's placed fixtures and their affordances and emits the
stops: berths become rest, gantries become work with a weld focus, lanes become
practice, consoles become inspection.

Each usable affordance publishes one exclusive **task point**. A route asks for
an activity group rather than assuming that one coordinate belongs to it; the
battle claim service assigns one free point, retains the old claim until a
replacement succeeds, and releases it when the actor moves to unrestricted
space, abandons the work, or dies. Fixture capacity therefore bounds concurrent
activity honestly: three firing lanes admit three practicing actors, never four
actors stacked onto a painted marker.

This is the difference between a generated room and a dead one, and it is the
standing reason fixtures must declare affordance rather than only appearance. The
ambient service executes route intent through ordinary battle pathfinding,
movement, occupancy, and separation; it never interpolates an actor through a
fixture or wall. Route authorship from generated fixture data remains the missing
half.

## Fill quality

The flagship rooms that exist today are the reference for *structure* and the
counter-example for *fill*. Four gantries with service access, berths along the
hull, a practice range behind a blast wall — those arrangements are sound. What
they contain is not, and a generated compartment that reproduced them would
inherit the problem:

- **Uniform low density.** Props are sprinkled at roughly even spacing instead of
  clustering where work actually happens.
- **Unarticulated floor.** Large expanses of open deck with no sub-structure, no
  reason to be there, and no reason to cross them one way rather than another.
- **Isolated props.** A workstation with nothing around it is a sprite, not a
  workspace. Fixtures do not read as furniture until they read as groups.
- **No scale anchor.** Nothing in a mech bay communicates that the machine in it
  is twelve metres tall.

The standard is therefore not "as good as the current rooms" but *used space*:
every part of a compartment is either a fixture group, a circulation lane, or
deliberately clear for a stated tactical reason such as a firing lane or a
weapons-free approach to a hatch. Emptiness is allowed when it is argued for and
is a defect when it is merely left over.

Density varies on purpose. Work areas are dense and cluttered; transit is clear
and legible; the boundary between them is visible from the fixtures alone,
without a floor decal explaining it.

## Elevation

A compartment may have more than one **level**. This is the model's answer to
unarticulated floor as much as it is a visual one: a mech bay with a catwalk
gallery around an open gantry well uses its footprint twice, gives the space a
scale anchor at mech-torso height, and creates firing positions that overlook the
floor. Flat is the wrong default for the largest compartments a ship has.

Levels are constrained to keep this affordable. They do not overlap, so one cell
still belongs to exactly one level and the per-cell topology, fog of war, and
occupancy models are untouched in shape. A raised gallery rings an open well; it
never roofs the floor beneath it. Movement between levels happens only at
authored transitions — stairs, ladders, lifts — which are ordinary chokepoints in
the deck graph. Line of sight between levels is a stated rule of the level pair,
not an emergent consequence of geometry.

This is distinct from stacking a ship's decks, which law 1 keeps out. Within-deck
elevation changes what one map contains; deck stacking would change what a map
*is*, and it is the latter that drags in a second grid, cross-level shadowcasting,
and vertical pathfinding.

Note that existing relief is presentation only — a shading signal that explicitly
does not alter navigation, collision, or targeting. A level is a tactical fact
and is not that. The two must not be conflated: relief makes a flat floor look
raised, and a level makes a raised floor be raised.

## Standing laws

1. **A deck is the map unit.** One battle occupies one deck. Movement between
   *decks* is mission structure, not map topology, and does not enter the tile
   grid, line of sight, or fog of war. Elevation *within* a deck is a different
   question and is permitted under law 9.
2. **The axis is authored.** Spine, frame numbering, zones, and bulkhead order
   are generation facts consumers query. No consumer re-derives them from cell
   coordinates.
3. **Beam varies with frame.** A deck is never a rectangle and never mirrored end
   to end. A recipe that emits a symmetric box has failed the family, whatever
   else validates.
4. **Capacity is spatial.** A facility's capacity is a count of its fixtures.
5. **An upgrade changes the room**, or it is not an upgrade in this model.
6. **The hull is finite.** Facility growth trades against other facilities.
7. **Ambient routes are derived**, never authored per deck.
8. **One pipeline.** Home and prize decks differ by parameter, never by a second
   generator.
9. **Levels do not overlap.** A cell belongs to exactly one level; a gallery
   rings an open well and never roofs it. Level changes happen only at authored
   transitions, and cross-level sight is a stated rule rather than an emergent
   one.
10. **Floor area is used or argued for.** Every part of a compartment is a
    fixture group, a circulation lane, or deliberately clear for a stated
    tactical reason. Leftover emptiness is a defect.

## Boundaries

`mapgen-nouns.md` owns the generation request, recipe, context, stage, room
purpose vocabulary, and final validation; a ship deck is a family within it and
inherits its determinism and connectivity obligations. The layout-neutral
topology tier published today by `StationGraph` is shared infrastructure; the
ring, core, and port annotations are station-specific and are not a ship's.

`progression-nouns.md` owns kit, quality, and the campaign economy that would
price an upgrade. `campaign-nouns.md` owns money and the fleet. Ship interiors
supplies the space and its capacity, and is not an economic authority.

`ui-nouns.md` and `company-view-nouns.md` own the operations screens. Ship
interiors owns the place those screens depict, not their layout or interaction.

Battle simulation, rendering, and camera own execution. Retargeting a larger deck
is viewport work at the established camera seam, not a renderer concern.

A boarding **mission** — its objectives, win conditions, extraction, and reward —
is not owned here. Ship interiors supplies the deck, the breach point, and the
longitudinal gradient a mission model would build on.

Current active work is indexed in `stories.md`.
