# Ship interior nouns

Status: ACTIVE — the model is authored; no generator, facility, or adoption slice has shipped.

Written: 2026-08-26

Updated: 2026-08-28 - every furnished compartment now publishes work and carries
a trade of its own, and a task point nobody can reach costs the point rather than
the room's whole fill. Earlier: berthing is split between the ship's own hands and the
ground force she carries, so a berth is an assignment rather than an amenity;
rooms now state where they hook up and are laid down in a
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
- A **link** is a passage cut between two parts of circulation that already
  exist, rather than to reach a room. It is the only circulation on a deck that
  serves no compartment of its own: what it buys is a second way round. Links
  are found after everything is placed, because whether one is worth cutting is
  a fact about the finished network and not about any room in it.
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
  are aboard: heads, a mess, a sick bay. They scale with the whole complement
  rather than with the crew, because a passenger eats too, and together they are
  most of the rooms on a ship. A hull programmed only with its working spaces
  comes out hollow.
- **Berthing** is the exception, and is two rooms rather than one. **Crew
  quarters** hold the hands who work the ship and are sized from minimum crew;
  the **barracks** holds the ground force and is sized from lift. They are the
  same compartment in form and differ only in who sleeps there — which is the
  entire point, because a berth is an assignment and the other crew spaces are
  amenities.
- A **rack** is one bunk, and a **rank** is a row of them laid athwart the
  compartment's passage, head to the hull and feet to the deck people walk on.
  Laid the other way — along the bulkhead — a rack takes two cells of hull to
  berth one hand instead of one, and the compartment holds half as many people
  in the same floor. That is why real berthing looks like this, and it is the
  only arrangement in which the recipe's few square metres a hand is reachable
  at all.
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

**A ship carries rooms for not working in.** A crew lounge and a gymnasium are
programmed from the whole complement, alongside the heads, the mess and the sick
bay, and for the same reason: a passenger has more need of somewhere to be than a
rating with a watch to keep. They are the answer to a question the berthing story
asked and could not solve inside a berth — eight by six holds two ranks of racks
and the passage between them and nothing else, so a lounge is a room of its own,
which is what a ship actually does.

They are not decoration. The whole ambient model exists so that nobody stands
about for want of something to do, and the only idleness worth having is the kind
somebody chose. Without a room to choose, there is nowhere for that choice to
happen, and the ship's third state — neither asleep nor at work — has no shape
but standing in a passage.

Every furnished compartment is somebody's, or it is honestly nobody's. A ship
publishes work at her heads, her sick berth, her armoury counter, her holds and
boat bays, her plant and her drive, and her bridge, and carries a trade for each:
the same rule that makes a bay the technician's makes a hold the storekeeper's.
The briefing room is the deliberate exception — a briefing is an event rather
than a watch, and giving its chairs a job would station officers in them
permanently, which is the make-every-prop-a-work-point mistake arrived at from
the other end.

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

It sits on the member of a group that actually affords it, not on the group. A
mess is a table with chairs round it and the meal is at a chair: published at the
table it would be one place to sit at the fixture four people sit at. The same
rule keeps a range's arms racks as scenery even though marines do spend part of
a watch on the range: what they come to do is shoot, and the firing point is
where that happens. Drawing weapons is the armory's work.

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

An affordance is not yet a **job**. The same affordance means different work in
different rooms: stowage in a vehicle bay is the parts run and belongs to whoever
works the bay, while stowage in a berth is somebody's own locker and belongs to
whoever sleeps there. So a role names its jobs twice — the ones it works **on
watch**, in any compartment that is somebody's workplace, and the ones it has
**off watch**, only in its own berthing. Collapsing the two into one list is not
a simplification but a leak: the marines were duly offered a shift running the
mech bay's stores, which is nobody's idea of shore leave.

Berthing is therefore fenced in both directions. A role has only its off-watch
jobs there, and only in its own quarters — so a marine cannot turn in in the
ratings' bunkroom, and a technician has no business in the marines' berthing at
all, however much of it stows things.

A **shift** is one crew member's loop through their role's jobs, and it is
derived, never listed. It is not a per-compartment thing: a marine's four jobs
live in three compartments, so a shift reaches the mess and the range as well as
the berthing it is posted to. `ai-nouns.md` is canonical for the shift, the role
and the job site, because none of that model is shipboard — a compartment is
simply this map family's job site, and a building interior on a surface map
answers the same three questions. What is shipboard is which purposes a deck
programs and what its fittings publish.

**The ship runs; a screen is a camera.** A deck is manned everywhere it has work
and advanced as a whole, and the screens that look at parts of it choose a
framing and nothing else. Framing must not decide what exists: staff only the
room somebody is watching and the ship's population becomes a fact about where
the player is looking — the technicians they walk away from stop working, the
marines they arrive at were conjured on the way, and the passage between two
compartments is empty, because traffic in a corridor is what the rooms at both
ends of it produce. This is also the cheaper half. The alternative to one deck
is one private grid per screen kept in step with the others by hand, while a
fully manned capital transport — a hundred-odd compartments and a few hundred
hands — costs well under a millisecond a frame.

One ship also means **one scene on one clock, held across page flips**, and the
clock belongs to the ship rather than to the page. The question this answers and
a per-screen scene cannot is where somebody *was*: leave berthing for the lab and
come back, and the marine who was walking to the mess should be eating. Give each
screen its own simulation and the two pages are different ships; run only the
page that is showing and the answer is "exactly where you left them, however long
you were gone", which reads as a diorama. So whoever ticks the shell ticks the
ship, and she is crewed to her complement rather than to a watch — a cap on how
many people exist is a cap on how much ship exists, and the compartments already
bound themselves by the work they hold.

It follows that a deck-hosted screen **advances** rather than seeks. Seeking is
a presentation teleport that interpolates between a route's stops in a straight
line, bypassing collision on purpose; on a hand-authored room those lines are
clear by construction, and on a generated deck they cross bulkheads. A deck that
ticks has no use for the licence. The clock is monotonic and idempotent so a
host can take a backdrop pass and an actor pass off one settled frame, and
catch-up after a long absence is bounded rather than replayed — ambient work is
a rotation with no history to lose.

**The company is quartered, and quartering is a choice.** A company that has
not been given a ship has no home, and the operations screens say so by being
unavailable until the player gives it one - every room view is a camera on the
company ship, so before there is one there is no screen to open. Quartering them
somewhere on their behalf would turn the first real decision about what the
company is for into a default they never saw.

Never chosen and no longer there are different states. A company whose ship is
gone is **displaced**: they had a home, they do not now, and they are put back to
choosing out of whatever is left rather than re-homed on their behalf. Moving
them to the next-best hull automatically would turn the loss into a shrug - the
player would learn their transport had burned by noticing the room looked
different.

**Losing her and letting her go are different, and the company knows which.**
To a fleet roster both are the same fact: she is not in it any more. To a
company one is a decision the player made and the other is something that
happened to them. The engagement a ship fails to come home from is evidence of
the second, held until she is confirmed missing - a ship can be disabled in a
battle and recovered off the field afterwards, and a company told their ship was
lost while it is being towed home has been told a falsehood.

**A company keeps what it is and loses what it had.** When the ship goes down,
the squads, the fire-team templates, the weapon and armour doctrines, the
arrangements, the named officers and the machines in the bay all survive her -
they are the company itself, and an outfit that forgets how it fights has been
deleted rather than hurt. What sinks is what was aboard and counted: the marines
who were home, the spares on the bay's shelf and the stores in her holds. That
leaves an outfit that still knows how it fights and has to buy back the means to
do it, which is a setback the player can work against rather than a save they
have to abandon.

There is no armoury inventory to sink, and looking for one is a mistake worth
naming. A company's equipment is a set of designs it owns permanently rather
than a rack it draws down: owning a template card is what lets a squad be issued
a weapon, and what an issue actually consumes is fleet cargo. Anything that
wants to cost the player materiel has to reach the counted things - the stores
and the bay's spares - because that is where the quantities live.

**Holding the field decides who is picked up.** Losing the ship out of a battle
the player still won leaves boats in the water and the time to use them; losing
her out of a rout does not, and most of the company goes with her. That is the
one lever the player has over the toll once she is already burning, and it is
what makes the same loss a bad day or a disaster. Squads away on a stationing
contract are somewhere else in the sector and were never aboard, which is the
standing hedge against losing everybody at once.

The roll is fixed by the company and the ship's name rather than freshly random,
because a loss the player can reload away is not a loss.

**Some hulls are boats, and a boat is not a base.** Having an interior is not
the same as being somewhere a company lives. A frigate is one deck: whatever
else she is doing happens in the space the marines would be living in, and a
company quartered there has a berth and nothing else - no armoury that locks, no
bay, nowhere to muster. That is the hull a player runs a squad around in, and
offering it as a home would let them wreck their own company by accident.

**Lift does not decide it.** A Kite carries twenty-eight hands beyond her crew
and a Wolf fifteen, and neither is a base; a shuttle has lift because people can
be packed into her for a short hop, not because they can live there. The second
deck is the line, because it is what separates somewhere the company works from
somewhere it is merely being carried.

An unsupported hull is **listed and refused**, never hidden. Absent, she reads as
a ship the game forgot; listed with the reason on her, she teaches the player
where the line is by showing it. That is the same treatment a bad home gets, and
for the same reason - the comparison is the screen's whole job - with the single
difference that the refusal is final rather than a cost.

**Moving costs money; being given a home does not.** A transfer is a refit
rather than a decision on paper - bunks, lockers, an armoury that locks and a
bay a walker can be worked on in are not aboard a freighter until somebody
builds them - and the yard is paid out of the same purse the rest of the
campaign spends from. The price is what makes choosing a home a choice: a free
transfer would have the player shop the fleet every time a hull arrived a
hundred berths larger, and the company would live wherever the spreadsheet last
pointed. It is priced from the company's own strength and from the hull being
fitted out, so moving a full company with its machines costs more than moving a
handful of marines.

A company with nowhere to live - newly founded, or displaced - moves for
nothing. There is nothing to move out of, and a price on the one action the
player has no alternative to is a tax rather than friction. A move nobody can
pay for is refused with the shortfall named, never run up as a debt: the
company is left standing where it was, which is what the screen was already
showing.

**Founding and transfer are the same question asked twice.** What will this hull
not do for us. With a home to measure against, the answer is what moving would
give up; without one, it is simply what she lacks. One screen answers both, and
a candidate is read the same way either time - as a generated deck, from the
generator and the seed her real interior would use.

**A room screen says where it is by asking.** The heading over a room view is
the ship, the compartment's longitudinal zone and side of the spine, and its
purpose - read off the deck rather than written on the page. A literal
breadcrumb is a claim nobody checks, and these pages carried three that had all
gone wrong at once: a flagship, for a company whose ship need not be the one
they fly; a mech bay, on a hull that may have none; and one berthing, on a ship
carrying eighteen. The middle segment is what makes the vessel feel like a
place rather than a menu - two squads berthed port and starboard of the same
spine are living in different parts of a ship, and the heading is where the
player finds that out.

**There is one answer to what a room looks like.** A screen has no substitute
scene to draw when the ship is unavailable, and a canvas that cannot reach her
draws nothing rather than something else. A fallback room is a second model of
the same place, and it goes stale the moment the generator changes — the two
rooms the screens once carried were, in the end, seen only in the headless
evidence, which is to say the evidence was of a room no player would ever stand
in. Deck-hosted evidence is rendered from the same deck the game builds.

**The company is the ship's marine complement.** The marines aboard the company
ship are the roster, and nobody else is: they muster into her berthings by name,
carrying the weapon and armour the armoury issued them, and every rack the roster
cannot fill stays empty. Crewing her afterwards must find nothing left to hire
there — a berthing topped up with generated hands would put strangers asleep in
the player's own ship, and the panel beside the room would list twelve names
against a compartment holding thirty. A squad away on a stationing contract is
somewhere else in the sector and is not aboard at all.

A company spreads across however many berthings the deck laid down, so a
berthing screen frames **the selected squad's own bunkroom** rather than the
ship's largest one. A fixed framing would show a squad list beside a room that
squad does not sleep in, and selecting a different formation would change nothing
on screen. Which room is theirs is read off where they were billeted, not off
where they are standing: half a watch is at the mess or on the range at any
moment, and a camera following current positions would swing away from the room
the moment somebody went to dinner.

Threat policy is a parameter of the route and a home deck's crew yield only to
**hostiles**. Yielding to any combatant sounds safer and is wrong here: the
machines a technician services are armed, so the crew of a bay would flee the
mechs they are welding and stand around the edges of the room permanently. The
same rule decides who a range target belongs to. A target frame is a combatant
as far as the roster is concerned, so a hostile one would have the firing detail
flee the paper it came to shoot at — it is the deck's own equipment, and it takes
the deck's own side.

**A drill may not cost the ship a hand.** Practice fires a real round through the
ordinary pipeline, which is the point of resolving it rather than posing it — but
ordinary fire has friendly contacts, damped by a damage multiplier and a
discipline roll and real nonetheless. On a firing line whose lanes are two cells
apart, fired every few seconds for as long as the ship is under way, damped and
real converges on certain: a seeded capital ran four minutes and buried two
marines. So a drill round is suppressed at the impact rather than at the
trajectory — it still flies down the lane it was aimed along and still strikes
the butts — and nothing but the target frame can be hurt by it.

The cost of getting this wrong was not obvious, which is why it is worth naming.
A crew member killed on the range is not merely one hand short: they are also an
actor with no ambient pose, and every instrument that asks "is anybody standing
about?" counts a corpse as somebody standing about. The ship's own range was
quietly manufacturing the exact defect the ambient model exists to prevent, and
reading as one.

A practice stop is the one job that **resolves** rather than only posing. The
fitting already bound each firing point to the butts it faces, so the stop's
focus is the target: the host spawns one frame per set of butts and the shooter
fires down its own lane through the ordinary shot pipeline. Nothing is authored
and nothing is drawn from a campaign inventory — the shooters are the deck's own
roster and the target is a frame the scene owns.

A deck is not a mission. A simulation with no registered objectives installs the
backstop pair, and a deck carrying nobody but its own crew therefore wins the
moment it is built and stops ticking — so a deck view disables mission
completion, and a boarding action hosted on the same scene registers its own
objectives instead.

**A point nobody can reach costs the point, not the room.** Furniture always
strands the odd sliver behind itself — the gap between two beds in a rank, the
corner past the end of a shelf — and a standing cell chosen there is a job with
no way in. Held as circulation so later furniture could not take it, one such
cell made its room fail its own connectivity check and the whole fill was thrown
away: the armoury, the sick bay, the holds, the boat bays and the bridge each
generated their fixtures, published their work, and shipped as bare deck.
Withdrawing the unreachable point is what separates the two questions — whether a
room can be walked through, and whether one particular job in it can be got at.

A fill that genuinely would seal its compartment is still refused, and a refused
fill publishes **nothing** — not its fixtures, not its berths, not its work. Rolling back only
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

A firing range is the standing example of the argued case, and the only room so
far whose empty deck *is* the room. The stretch between the firing line and the
butts is marked, reserved before anything is placed, and kept clear of fixtures
in both directions — nothing may stand in it, and nothing may open onto it.

That deck is **shut** rather than merely reserved. A reservation is a rule about
furniture: it stops the fill standing anything in the lane and leaves everybody
else free to walk down it, which for a beaten zone is the whole failure. Shut
deck is closed to movement and open to sight and shot — see-through, carrying no
edge cover, and tagged a fixture rather than a wall so nothing seeds it with
destructible hit points. That is the treatment water already gets, and for the
same reason: what stops the deck here is not a wall. Shutting is recorded during
the fill and applied only once the fill is known to be kept, because deck closed
off by a discarded fill would stay closed — a strip through a room that nothing
can cross and nothing explains.

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

    Authoring a hookup is not free and does not always pay. Measured against an
    arbitrary door, a berth's authored pair of hatches *costs* it racks: an
    arbitrary hatch often lands on a short end bulkhead, where it takes one rack
    slot, while the authored pair sits on the long side by design and takes two
    slots each. The berth keeps them anyway, because what is bought is the
    arrangement — both hatches on one bulkhead, so the far rank stands against
    unbroken hull and nobody asleep is walked past all watch — and because a
    budgeted cost in a known place is what lets the fill plan around it. Author a
    hookup for an arrangement the room needs, not for tidiness, and measure
    rather than assume which one you have.
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
    painter, and its art is loaded by the deck rather than by the screen.**
    A generated deck is already a map, so authoring evidence, a
    hosted deck view, and a boarding action are one renderer over one
    simulation, differing only in camera, layer set, and whether the frame
    drains to the screen or to an image. A tool that redraws the deck its own
    way is measuring its own drawing: the copy drifts, and every drift reads as
    a fill defect until someone goes looking. A diagram that shows what the
    renderer has no concept of — room purpose, zone cuts, which opening is a
    door — is a legitimate second view, but it annotates the render or
    abandons the pretence of being one.

    The ship is one scene and the screens are only cameras onto it, so **which
    sheets to load is the deck's business, not each screen's**. Screens that
    each listed what they expected to have in shot were right until the ship was
    framed differently: the room views loaded the ground and the crew, the Mech
    Lab loaded the machines too, and the whole-ship view — which sees all of it
    at once — was written loading none of them and shipped a deck with no floor
    and no people in it. The set now hangs off the one door every screen already
    comes through.

    The same rule reaches the hull herself. **A hull's art rides with her
    facts**, beside the outline her deck was laid out inside, because they are
    the same fact read twice off the same `.ship` file — the shape and the
    picture of the vessel that shape came from. Handed to the canvas as a
    separate argument instead, drawing the ship became something a caller had to
    remember, and the caller that mattered did not: the screen built the canvas
    from the deck alone and the game drew a plan floating in empty space.

    **Headless evidence cannot see either of these.** The snapshot suite builds
    its deck with no sprite cache at all, and the headless renderer loads sheets
    off disk its own way, so the ship-view snapshot came out fully painted —
    hull, decking, fixtures and machines — for the whole time the game was
    drawing an empty wireframe, and it drew the backdrop because the suite was
    the one caller passing it. That evidence proves layout and composition and
    says nothing about whether the running mod reached its art. Only a first
    frame in the game does. Where evidence cannot check a wiring, close the trap
    in the shape of the code instead: one door, one constructor, nothing for a
    caller to remember.
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

20. **A berth is an assignment, not an amenity.** Everyone aboard eats in the
    same mess and washes in the same heads; nobody sleeps in somebody else's
    bunk. So berthing is programmed per population while the other crew spaces
    are programmed per complement, and a role turns in only in its own quarters.
    Two things fall out of that and neither is decorative. The barracks a player
    reads a billet count off holds their own people and nobody else's, so the
    facility stops overstating itself by the size of the ship's crew. And a hull
    mod that raises minimum crew finally costs the company real space — the ship
    takes more berths for herself and the ground force gets fewer — where before
    it moved every figure derived from lift except the largest one.

21. **A doorway is only ever the cells the room named.** A door is widened to
    two cells wherever the deck allows, because a compartment berthing a watch
    behind a one-cell threshold bottlenecks everything that happens at it. Where
    the room authored its doorway, the widening stays inside the slot: a berth
    budgets one rack for each hatch, and a hatch free to spread into the
    neighbouring slot took a second rack the fitting had already laid a bunk in
    — in a different place on every deck, which is the failure authored doors
    exist to end.

22. **A pose set is a cost, and the mask is what decides whether it is worth
    paying.** Turns are deduplicated by the footprint they produce, so a
    rectangle has two distinct poses and an L has four. A room whose
    arrangement needs to face a direction its mask cannot reach must be
    **handed** and take all eight — a berth is entered from one side, and
    without the flips could only ever be entered from two of its four. A room
    whose mask already reaches every direction gains only chirality, and
    chirality is not free: making the firing range handed let the
    second-largest room on the deck take a mirrored pocket, and cost one seed
    in three twenty-one of its programmed rooms, eighteen of them berths, with
    parts cages backfilled into the space. Author the flips for the
    arrangement, never for the room merely having a front and a back.

23. **Circulation must not be left a tree.** Cutting each passage to the
    nearest thing already connected is right every single time and wrong in
    aggregate: nothing ever joins two branches, so every stub is a dead end and
    two compartments a few cells apart are walked between by going back to the
    spine and out again. Measured across five vanilla hulls, of the pairs of
    rooms whose doors lie within twenty-five cells, the worst tenth were walked
    at three to five times their straight-line distance, and the worst single
    pair on a troop transport was eighteen cells apart and two hundred and
    fourteen cells of walking. So a **link** pass runs after placement, from the
    dead ends outward, and cuts where the existing walk is at least double what
    the link would be.

    **What it may cut is not relaxed for it.** A link obeys law 13 exactly: it
    never takes a cell a compartment stands behind and never runs along a
    bulkhead. That bounds it hard, and the bound is the finding rather than a
    disappointment — on those same hulls only about one badly-detoured pair in
    ten can be joined by any legal cut at all, because in a packed warren every
    scrap of leftover deck is within a cell of somebody's room. The pass takes
    very nearly all of what exists (nothing on a frigate, which needs nothing;
    three to six links on a capital) and the residue is not a defect in it.
    **Do not answer a detour by letting a passage open a compartment**, and do
    not answer it by keeping deck clear beside every hall either. That second
    one looks like the obvious fix and was measured: forbidding rooms from the
    one or two cells beside circulation makes decks *worse*, because a room that
    may no longer touch a hall has to tunnel its own stub to reach one, and
    every stub reserves more deck and pushes the next room further out. It
    multiplies dead ends rather than joining them. On an Eagle the badly
    detoured share of near pairs went from a tenth to a quarter, nine
    compartments went unplaced or unbuilt, and corridor grew by a third while
    the deck stopped reading as a ship at all. A Valkyrie improved slightly on
    the number and lost fourteen rooms and its long berthing rows doing it.

    So the tight pack is right and the detours it leaves are the price of it.
    What is still unexplained is why a third of the hull sits void aft while
    the bow is packed wall-to-wall; that is a question about where the program
    puts its rooms, not about how tightly it puts them.

24. **A line of fixtures across a room is a wall unless it is told not to be.**
    The arrangements that read best are lines and flats — a stove line, a rank
    of benches, a counter — and every one of them severs the room it crosses.
    The counter usually gets a gap because the gap is visibly part of the
    design; the line *behind* the counter does not, and then the way through
    opens onto an unbroken row of benches and the whole working end is walled
    off from the ship. It never looks wrong: the plan reads as a galley, and the
    only symptom is that every job past the line is silently dropped as
    unreachable. So a fitting that lays a line lays the way through it in the
    same breath, and the check for it is that nothing was dropped rather than
    that the room looks right.

25. **A fitting names only ids the registry has, and says what it does without
    them.** A missing id makes placement fail silently, so a theme whose kit
    half-exists comes out bare with nothing to say why. Naming only what exists
    is the rule; it is not sufficient on its own, because the art for a new room
    and the room's arrangement do not have to land in the same commit. A fitting
    therefore carries a **stand-in** for each piece — something that has been in
    the set since the beginning — so a galley whose art has not been packed yet
    is a worse-looking galley rather than an empty hall. Read the fallback's own
    footprint, not the authored one: a stand-in of a different size laid out to
    the size of the piece it replaces is how a room fills itself wrong.

    **A threshold derived from art alone moves with the art.** Sized only from
    what has to fit, a galley became possible in a nine-cell cabin the moment its
    stand-ins were small — and every cooking point is a posting, so the ship
    acquires a watch of cooks in a room that cannot hold a range. Where the line
    falls between "the ship's galley" and "somewhere else to eat" is a judgement
    about the room, so it is stated rather than measured.

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
