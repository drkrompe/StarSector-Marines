# Ship interior nouns

Status: ACTIVE — the model is authored; no generator, facility, or adoption slice has shipped.

Written: 2026-08-26

Updated: 2026-08-27 - rooms now state where they hook up and are laid down in a
recorded pose, so a door is a constraint on placement rather than an outcome of
it, and one authored arrangement serves a deck from either side. Earlier: added the hull-size and growth model (complement plus hold, more decks past the playable envelope), hull silhouettes, the law that a deck is only ever looked at through the battle renderer, gantries as authored berths that make the home deck's vehicle bay the Mech Lab, room views as the way operations screens address parts of the ship, the company ship as a hull the player chooses out of their own fleet, and the split between a deck's form and its damage state, where a wrecked fixture is out of service and the facility is that much smaller until it is repaired.

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
- A **gantry** is one authored machine berth inside a vehicle bay: the clear
  footprint a mech or a vehicle stands in, and the direction it faces to leave.
  Generation authors the berth; a host decides what occupies it. This is the
  same division a landing pad has with a shuttle, and it is what lets one
  generated bay be the player's own lab on the home deck, a half-empty bay on a
  prize hull, and a contested objective in a boarding action.
- The **company ship** is the hull out of the player's own fleet that the
  company lives aboard. It is chosen at founding and can be transferred to
  another ship later, which makes it a decision rather than a fact about the
  save. Its deck is generated from the ship as fitted, so acquiring a better
  hull and refitting the one you have are two independent ways to change the
  interior; what the ship has since suffered is drawn on that deck rather than
  built into it. See `company-ship.md`.
- A **room view** is an operations screen's camera framed on one compartment of
  a deck. The Mech Lab is a room view of the vehicle bay; a berthing screen
  would be a room view of the barracks. A room view names a purpose and the deck
  answers with a compartment, so two screens onto the same ship see the same
  fixtures, the same machines, and the same damage — there is one ship, and the
  screens are places to stand in it.
- A **berthed machine** is a unit, never scenery. It arrives from a roster with
  its real variant and loadout, so what stands in a bay is the same entity that
  would walk out of it. Berth cells therefore stay clear in the map — a berth
  with a fixture in it is a bay nothing can be put into.
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
- **Hull contact** is the outside of the ship a room has to meet, as opposed to
  the zone it merely belongs in. Most rooms only need to fit. A boat bay must
  reach a flank or it opens onto the compartment next door; an engine room must
  sit against the transom or it is not driving anything. For those rooms a
  placement that fits is still wrong, and it is the same test a breach point
  will want.
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
- A **job** is one thing a fixture affords, sited at the cell it is done from.
  It is the authored half: generation knows which berth a job belongs to and
  whether its fixture is in service, and both are spent when the deck is staffed
  rather than carried into the battle. What survives is the task point.
- A **role** is what a crew member is aboard to do, as the jobs they will work.
  It is the join between a person and a room: a compartment publishes what its
  fixtures afford, and a role says which of those are this person's and in what
  order they come round to them. A mech technician in a vehicle bay welds,
  fetches, and reads terminals; the same technician in a barracks has nothing to
  do, which is the right answer rather than a gap. Roles are never derived from
  what a room contains — a room full of bunks affords rest to everybody and is
  somebody's *job* only if they are off watch, and folding opportunity into work
  is how every actor on a deck ends up doing whatever is nearest.
- A **fixture group** is the placement unit: an anchor fixture, its satellites,
  and a shared orientation. A workspace is a bench with its stool, its parts bin,
  and its clutter, all facing the same way. Fixtures are placed as groups, never
  as independent points on a grid.
- A **hookup** is where a room meets the deck's circulation: the bulkhead cells
  its doors may be cut through, authored by the fitting in its canonical frame.
  A fitting may offer several as alternatives — a bay entered from both ends and
  a bay entered amidships are different rooms, not a room and a defect — and the
  placer takes whichever one the surrounding deck can serve.
  A hookup is worth authoring only where an arbitrary door would cost the room
  *capacity*, not merely tidiness. Constraining a door constrains placement, and
  a room that has to be somewhere its end bulkhead meets a passage packs into
  the deck less well — measured on a frigate deck, giving berth compartments an
  end-bulkhead door cost six compartments to gain ten fixtures, because the
  program rooms took more awkward positions and left worse pockets behind. The
  bay earns it: a hatch partway down its side used to cost two of its eight
  berths, a quarter of the facility. A berth compartment does not: a stub across
  one rank costs it a single bunk of twelve.
- A **pose** is how a room was laid down: a quarter-turn count and whether it was
  flipped. It is recorded rather than recovered, because a flipped rectangle has
  the same footprint as an unflipped one while its contents run the other way.
  One authored arrangement therefore yields eight rooms, and a fitting authors
  facing one way and reads the pose to find out where that ended up.
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

The company ship and a boarding target are the **same product of the same
generator**, differing in parameters rather than pipeline:

| | Home deck | Prize deck |
|---|---|---|
| Faction and threat | friendly; ambient threat policy admits no combatants | hostile garrison |
| Who chooses the rooms | the company ship as presently fitted | the campaign-resolved target's class and role |
| How it is entered | a room view, continuously | a mission, with a breach point |
| What changes it | an upgrade transaction | battle damage, for the duration |

This is less of a leap than it looks. The shipboard rooms already run a real
bounded battle simulation with real fixtures, real navigation, and a threat
policy set to admit nobody; the marine practice range already fires live rounds
through it. A home deck is already a battle map that happens to have no enemies
on it.

Keeping one pipeline is a standing requirement, not a convenience. If home and
prize decks ever need to differ, the difference is expressed as a parameter of
the family. A second generator for the company's own ship would immediately drift
from the one that has to stay tactically honest.

## Facilities, capacity, and growth

A facility's **capacity is its working fixtures**. The number of berths in the
barracks is the number of billets; the number of gantries in the mech bay is the
number of heavy assets it can service. These are one fact with one owner, not a
room drawing and a separate number that can disagree.

A fixture is therefore in service or out of it, and battle damage is what puts
it out. This is what makes damage cost something rather than merely show: a
mech bay with two of its four gantries wrecked services two machines until the
ship is repaired, and the room is visibly the reason. It also means every count
that reads a facility has to ask for working fixtures rather than fixtures,
which is the price of the rule and is worth paying.

Capacity follows from the room, never from where the deck happened to put it.
Two hulls of the same class berth the same number of machines, and a player who
reads a facility's size off the room is reading something true. This is why a
room states where it hooks up rather than coping with a door wherever one
arrived: a bay that lost berths to a hatch landing partway down its side made
capacity a fact about the passage outside it.

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

Affordance is a property of the **placement**, not of the art. The same crate is
stores in a hold and spoil in the gap between two bays, and only one of those is
somewhere anybody has business. A fitting decides which; the tile registry only
knows what a crate looks like. Making every prop a work point is its own failure
— a technician solemnly tending a scrap pile reads as purpose, which is worse
than reading as scenery.

Each usable affordance publishes one exclusive **task point**: a cell *beside*
the fixture, since nobody stands inside a workbench, reserved as circulation so
later furniture cannot take it back. Exclusive means one point per cell, not one
per fixture — a lane cell shared by everything adjacent to it is three benches
claiming one place to stand, and a capacity of three where one person fits. A
route asks for an activity group rather than assuming that one coordinate
belongs to it; the battle claim service assigns one free point, retains the old
claim until a replacement succeeds, and releases it when the actor moves to
unrestricted space, abandons the work, or dies. Fixture capacity therefore bounds
concurrent activity honestly: three firing lanes admit three practicing actors,
never four actors stacked onto a painted marker.

Work bound to a berth is bound to the **berth**, not to the cell, because it only
exists while something is parked there. Generation authors the link and cannot
know what the host parks; occupancy is a runtime fact about a deck, so an empty
bay is somewhere to walk through rather than somewhere to weld.

This is the difference between a generated room and a dead one, and it is the
standing reason fixtures must declare affordance rather than only appearance. The
ambient service executes route intent through ordinary battle pathfinding,
movement, occupancy, and separation; it never interpolates an actor through a
fixture or wall.

A **shift** is one crew member's loop through their role's jobs in one
compartment, and it is derived, never listed. The order is the role's, not a
priority: a route that always ran to the nearest free job would bunch every
technician at one end of a bay. Members of a watch start on different jobs and
different phases so a shift coming on spreads across the room instead of
queueing, and a compartment takes on only as many of a role as its *scarcest*
job can sustain — a bay with eight berths and one terminal cannot occupy eight
technicians on a rotation that includes the terminal.

Threat policy is a parameter of the route and a home deck's crew yield only to
**hostiles**. Yielding to any combatant sounds safer and is wrong here: the
machines a technician services are armed, so the crew of a bay would flee the
mechs they are welding and stand around the edges of the room permanently.

A fill that would seal its compartment is refused, and a refused fill publishes
**nothing** — not its fixtures, not its berths, not its work. Rolling back only
what can be seen is how a bay came to advertise berths standing on bare painted
deck: the room looked deliberate from every angle except the one that counted,
and nothing about the result said it had been thrown away.

## Fill quality

The shipboard rooms that exist today are the reference for *structure* and the
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
4. **Capacity is spatial, and it counts only fixtures in service.** A facility's
    capacity is a count of its working fixtures. A wrecked gantry is not a
    gantry, so a bay that comes home damaged services fewer machines without
    changing shape, and it does so until the ship is repaired.
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
14. **A room's doors are authored, not discovered.** Where a room hooks up is a
    constraint on where it may go, not a result of how the passage search
    happened to reach it. A fill handed an arbitrary door has to make room for
    it out of its own arrangement — a bay whose hatch landed halfway down its
    side cleared a band straight through both ranks of gantries, giving up two
    berths to a door that could have been at the end. A room that cannot be
    served where it asks is placed with an ordinary door rather than left off
    the deck: a worse bay beats no bay.
15. **Circulation is two abreast on both axes.** Width is judged as a square,
    not as a pair: a hall widened only across its direction of travel pinches
    back to one cell at every corner, which puts a movement trap where the deck
    can least afford one. Two is a floor, not a ceiling.
16. **The home deck's vehicle bay is the Mech Lab.** Not a room that resembles
    it, and not a second layout maintained beside it. The machines it holds are
    the ones the company owns, so a bay with one mech in it and the rest of its
    berths empty is the correct picture of a company just starting out. Filling
    berths to make the room look busy would show the player equipment they do
    not have, and would make the one screen where they inspect their own
    machines disagree with the fleet it is drawn from.
17. **A deck is seen through the battle renderer, never through a second
    painter.** A generated deck is already a map, so authoring evidence, a
    hosted deck view, and a boarding action are one renderer over one
    simulation, differing only in camera, layer set, and whether the frame
    drains to the screen or to an image. A tool that redraws the deck its own
    way is measuring its own drawing: the copy drifts, and every drift reads as
    a fill defect until someone goes looking. A diagram that shows what the
    renderer has no concept of — room purpose, zone cuts, which opening is a
    door — is a legitimate second view, but it annotates the render or
    abandons the pretence of being one.
18. **A deck's form is what the ship can do when whole; damage is state laid
    over that form.** Refits change the form in both directions — a hull fitted
    with more berthing has more berths, and one whose bays were converted to
    holds has lost them — because the owner really did change the ship. Battle
    damage does not. A ship that comes home with its storage compromised still
    has the hold it was built with, wrecked and part of it unusable, and
    generating a smaller hold instead would rebuild the ship around its
    injuries and leave the player looking at a tidy little room where their bad
    afternoon should be. So capability is read with the damage taken back out,
    and the damage is read again separately to say which rooms are in what
    state. A deck sized from the hull specification is a deck of a ship the
    player does not own; a deck sized from the damage is a deck of a ship they
    no longer have.
19. **A facility the ship cannot hold is absent, not empty.** The program
    already gates rooms the hull has no room for, and the deck already reports
    what it could not place. A screen for a facility the ship cannot host says
    so; it does not open onto a bare compartment. An empty room and a missing
    one are different facts, and only one of them is a reason to go and find a
    better ship — which is the whole argument for the next hull.

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
