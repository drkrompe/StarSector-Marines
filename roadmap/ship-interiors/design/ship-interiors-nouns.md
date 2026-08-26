# Ship interior nouns

Status: ACTIVE — the model is authored; no generator, facility, or adoption slice has shipped.

Written: 2026-08-26

Updated: 2026-08-26 — added the hull-size and growth model (complement plus hold, more decks past the playable envelope) and hull silhouettes, retaining the task-point, fill-quality, in-plane articulation, and flat-plane material.

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
- The **spine** is the primary fore-aft circulation corridor. It is the deck's
  authored main line, not whichever corridor turns out longest. Compartments do
  not all hang off it: a deck where every room opens on the spine is a comb, and
  blocks of rooms reached by their own passages are what a ship actually looks
  like.
- A **passage** is connective walkable space that is not the spine. Passages are
  not ruled out in advance; they are cut where rooms need to be reached, which is
  what gives a deck hallways of differing length and route rather than a grid.
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
- A **room shape** is a compartment's footprint expressed as a mask of cells,
  carried with no orientation. A rectangle is the easy case, not the model: a
  bridge is a diamond, a range is an L, and a compartment may wrap a hull flare.
  A packer built around a width and a height could place none of those.
- A **room recipe** is the authored pairing of a purpose with its shape, the zone
  it belongs in, and the capacity one of it supplies. It is why a room is the
  size its function calls for rather than the size the leftover space happened to
  be.
- A **room program** is the list of recipes one deck owes, derived from the
  ship's role, complement, hold, and class. The program sizes the deck; the
  deck does not size the program.
- A **hull role** is what a ship is for, as opposed to how big it is. Class and
  role are close to independent: a troop transport and a gun destroyer are the
  same tonnage and share almost no interior. Role decides which rooms a hull
  owes at all, and it is read from the game's own per-hull designation rather
  than inferred from size.
- **Lift** is everyone aboard who is not needed to work the ship — the gap
  between a hull's minimum and maximum crew. It is the number that separates a
  ship carrying people from a ship employing them, and it is what sizes the
  boat bays. Reading maximum crew alone makes those two ships the same ship.
- **Crew spaces** are the rooms everyone aboard needs regardless of why they
  are aboard: berths, heads, a mess, a sick bay. They scale with the whole
  complement rather than with the crew, because a passenger eats too, and
  together they are most of the rooms on a ship. A hull programmed only with
  its working spaces comes out hollow.
- A **room program is gated by size as well as role.** Room footprints do not
  shrink, so a hull below destroyer size cannot hold a range, a briefing room,
  or a proper sick bay without those rooms becoming the ship. A frigate keeps a
  gig and a locker.
- A **shuttle bay** is the deck's own way in and out: where troops embark for
  the surface, where they return, and the natural place for boarders to arrive.
  Alone among rooms it must reach the side of the ship, which is the first
  placement constraint that is about the hull rather than about fit.
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
- A **hull silhouette** is a ship's outline, normalized so it can be stretched
  onto any deck size, recording port and starboard extent separately at each
  sample. Keeping the sides apart is what preserves a real hull's asymmetry; a
  synthetic curve can only produce something mirrored, which is the station
  geometry this family exists to avoid.
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

## Hull size and how a ship grows

A ship's interior size comes from **complement and hold together**, never from
crew alone. A vanilla Atlas carries the same 50–100 crew as a Hammerhead and is
an enormously larger ship; the difference is two thousand units of cargo against
one hundred. Complement wants habitation, command, and engineering space; hold
wants volume. Both are walkable during a boarding action, so both count toward
the interior a ship owes.

Growth is not uniform in the two dimensions. A deck lengthens faster than it
widens, because a short wide deck stops reading as a ship and starts reading as
a station — it loses the axis the whole family is built on. So beam grows, but
sub-linearly, and the length-to-beam ratio is held roughly constant.

Past a playable envelope a deck stops growing at all and the ship gains **more
decks** instead. This is what keeps an enormous hull from becoming an
unnavigable map, and it follows directly from law 1: one battle occupies one
deck, so decks are the unit that scales. A personnel transport is one full deck;
a capital is several.

A deck's outline should come from a **hull silhouette** wherever one is
available, so a ship inherits real proportions and real asymmetry instead of a
curve invented to look plausible. A synthetic taper — long pointed bow, short
blunt stern — remains the fallback for hulls with no outline on hand, and the
family must stay correct under both.

Tracing an outline out of ship artwork is authoring-time work. Mod runtime code
cannot read arbitrary files, so outlines reach a running game as baked catalog
data rather than by inspecting art in place. That is a delivery boundary, not a
change to the model: the generator consumes a silhouette and does not care who
produced it.

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

Because the deck is flat, articulation and scale are entirely a fixture and art
problem. The techniques that carry that load in-plane:

- **Subdivide with real structure.** Support columns, gantry frames, equipment
  rows, and low railings break a large rectangle into functional bays and carry
  genuine cover and blocking values rather than only appearance.
- **Recess rather than raise.** A maintenance trench under a gantry reads as
  depth, gives a mech's torso a reason to sit at eye level, and is an ordinary
  non-walkable or cover cell — the flat-plane-safe half of verticality.
- **Anchor scale with human-sized objects.** A twelve-metre machine reads as
  twelve metres when tool carts, service stairs, ladders, and working crew sit
  beside it. Scale is communicated by neighbours, not by elevation.
- **Let relief do the looking-tall.** Rendering height on walls, frames, and
  overhead structure is exactly the presentation-only signal for this, and it
  cannot leak into play.

## The deck is flat

**The simulation takes place on a flat plane.** Walls and obstacles have
rendering height, not actual height. The engine does not support walkable
separate height surfaces, and a ship deck does not get to invent them.

This is recorded here because a mech bay is exactly the room that tempts someone
to try. Catwalk galleries, mezzanine control rooms, and gantry platforms are the
genre's defining image of the space, and a non-overlapping scheme of levels — one
cell per position, a gallery ringing an open well — looks affordable enough to
sneak in.

It is not, because faking height leaks into everything that assumes the plane:

- Line of sight is a cell-pair question asked across the decision layer by
  targeting, zone control, and tactical scoring, with no place to say "but not
  from up there." Every one of those callers would silently be wrong.
- Adjacency is planar, so walkability, occupancy, contact, separation, and
  radius queries would all treat a raised cell as neighbouring the floor beside
  it.
- Projectiles already carry a Z, but it tests the round against **doodad
  silhouettes** — a rendering-height collision proxy. It is not a world
  elevation, and extending it to carry units would be the fake becoming load
  bearing.

So the vertical impression of a bay is a **presentation** problem, solved with
relief shading and art, which is what relief is for and which cannot leak because
it is presentation-only by charter. The tactical articulation of a bay is a
**fixture** problem, solved in-plane. Neither is an elevation.

## Standing laws

1. **A deck is the map unit.** One battle occupies one deck. Movement between
   decks is mission structure, not map topology, and does not enter the tile
   grid, line of sight, or fog of war.
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
9. **The deck is flat.** No walkable elevation, no levels, no vertical
   traversal. Height is a rendering property of walls and obstacles and never a
   tactical fact.
10. **Floor area is used or argued for.** Every part of a compartment is a
    fixture group, a circulation lane, or deliberately clear for a stated
    tactical reason. Leftover emptiness is a defect.
11. **Rooms are packed, not partitioned.** Shapes are laid into the hull and
    circulation is cut from what the packing leaves. Ruling corridors first and
    subdividing the bays between them can only ever produce bay-sized slabs of
    uniform depth: enlarging such a deck enlarges the slabs instead of fitting
    more rooms, and no room is ever the size its purpose called for.
12. **A door opens onto circulation.** Never merely onto walkable space. A deck
    whose rooms chain doorways into one another is an enfilade — the way
    outboard runs through somebody's berth and out the far side, there are no
    hallways, and a single held compartment severs the deck.
13. **Only a door opens a compartment, and a door is a door-sized hole.** A
    passage may cross structure but never cut a cell a room stands behind, and
    never runs along a bulkhead. A room that loses part of its wall loses its
    cover, its chokepoint, and any reason for a squad to clear it rather than
    walk past — and the loss is invisible in aggregate, so it is measured as the
    widest unbroken stretch open to a passage rather than as a share of cells.
14. **Circulation is two abreast on both axes.** Width is judged as a square,
    not as a pair: a hall widened only across its direction of travel pinches
    back to one cell at every corner, which puts a movement trap where the deck
    can least afford one. Two is a floor, not a ceiling.

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
