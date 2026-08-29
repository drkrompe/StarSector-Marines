# Facility room themes

Status: IN PROGRESS

Written: 2026-08-26

Updated: 2026-08-28 — no shipboard purpose is left on the generic aisle treatment. The mess became a galley, a servery and a dining floor, and cooking became an affordance and a trade of its own because a ship cannot have a mess hall and no cooks. Stores split into three rooms that differ from each other, the armoury into an apron and a secure floor either side of its counter, the sick bay into a ward and a clinic, and the heads into ranked basins and stalls. The bridge took a purpose of its own, since `CONTROL_ROOM` is also a factory booth and a fortress guard post. Two defects came out of it, both now laws: a fixture line across a room seals it unless told not to, and a size threshold derived from art alone moves when the art does. What remains is the empty-region instrument. Earlier: every shipboard purpose is themed and published, not just the two this story scoped: the bridge is a plot with the watch ringed round it, the machinery spaces are flats of plant with a board and a defect list, both bays are worked hard, and a crew lounge and gymnasium were added because off-watch had nowhere to happen. The blocking defect turned out to be in the floor rather than in any theme — a standing cell was held as required circulation, so one walled-in work point cost its room the entire fill and five purposes shipped as bare deck. What remains is the empty-region instrument. Earlier: the mech bay, berthing, the mess and the firing range are
furnished and publish affordances and task points. Berthing came out with no room
for a lounge at its authored size, so that part of the scope is now a question
about the recipe rather than about the fill.

**The sweep that measured this story's fill criteria is gone.**
`CompartmentFillSweepTest` measured the empty-region and density criteria across
five hulls and six seeds, and its last reading said the fill is not there yet:
**344 of 2054 fitted compartments held a void larger than four cells square** —
a hangar came out twelve square, an engine room ten — and **480 were furnished
below a tenth of their floor**. It also confirmed the refit ladder: the same
floor measurably holds more at each of `MAKESHIFT`, `STANDARD`, `OPTIMISED`.
The test was deleted on 2026-08-28 because it cost 158s of a 560s `:test` run,
and the owner judged the invariants not worth that. Those figures are now a
historical reading rather than a live ratchet; work that claims to improve the
fill has to re-establish its own measurement.

The sweep also overcounted, which is part of why it was cheap to lose.
`AisleFitting` deliberately reserves a working aisle scaled to the room, and a
machinery space is mostly open deck on purpose, but the instrument measured the
finished deck and could not see a reservation. Telling an argued aisle from an
abandoned middle is what any replacement instrument owes — and the open design
question underneath it is whether a large declared working floor is a legitimate
room or the defect this story exists to fix.

Read `ship-interiors-nouns.md` before implementing this story, especially the
Fill quality section and law 9. Depends on `ship-deck-family.md`.

Fill mech bay and barracks compartments from parameters.

The rooms on screen today are a **partial** reference. Their structural
arrangements are sound — four gantries with service access, berths along the
hull, a practice range behind a blast wall — and those survive. Their fills are
not: props sit at uniform low density, large expanses of deck are unarticulated,
and individual fixtures float without the satellites that would make them read as
workspaces. Reproducing `MechLabSceneLayout` and `BarracksSceneLayout` is
explicitly **not** the goal; keep the arrangements and replace the fill.

Start with the mech bay. It is the higher-information case — identical gantry
bays make the parameter obvious, and it is the compartment where the current fill
fails hardest, since nothing in it establishes the scale of the machine it exists
to service.

The bay is also where law 9 bites. The deck is flat: no catwalks, no galleries,
no walkable elevation. Articulation and scale come from in-plane structure —
columns, gantry frames, recessed maintenance trenches, and human-sized objects
beside the machine — plus rendering height on walls and overhead structure,
which is presentation only.

## Scope

- A themed fill per facility purpose, selected from the compartment's semantic
  purpose, that places fixtures from parameters: gantry count and spacing for a
  mech bay; berth count, lounge extent, and firing lane count for a barracks.
  Berthing is done and holds no lounge: at eight by six, two ranks of racks and
  the passage between them use the compartment up. A lounge is a bigger room or
  a separate one, which is a `DeckSizing` decision rather than a fill defect.
  Firing lanes are done and come from the range's own outline rather than a
  count — the footprint narrows where the firing line goes, so a reshaped recipe
  moves the lanes with it. The lane itself is shut to movement rather than
  reserved, so the beaten zone is a fact about the deck and not only about the
  fill.
- Fixtures are placed as **fixture groups** — an anchor, its satellites, and a
  shared orientation — not as independent points. A berth is a bunk with its
  footlocker and personal clutter; a fabrication station is a bench with its
  tooling, parts bin, and spoil.
- **Circulation lanes** authored before fixtures, connecting every entry to every
  fixture group, with fixture placement forbidden from encroaching on them.
- Purposeful density variation: dense work and living clusters, clear transit,
  and clear space only where a firing lane or hatch approach needs it.
- Fixtures declare their tactical effect and their ambient affordance. Affordance
  is authored here; the crew model that consumes it is in `ai-nouns.md`.
- A **refit level** per compartment, which is how the upgrade chain works: the
  same floor area fitted better holds more. A berth compartment given single
  racks and generous aisles berths fewer than the same compartment given triple
  racks and working room only. Capacity is the fixture count at the fitted
  level, so an upgrade re-fits a room rather than enlarging it or adding a new
  one.
- Compartment extent follows from the room's authored footprint. A refit changes
  what the floor holds, not how much floor there is.

## Constraints

- Fixtures use registry doodad ids. This constraint was written to keep the
  story about arranging rooms rather than about drawing them, and it held until
  the rooms ran out of things to be arranged from: a gymnasium and a drive room
  cannot be built from crates under hopeful names, which is the failure
  `RoomFittings` warns about. Eleven props were generated, most of them
  multi-cell, and the rule that survives is the one that mattered — **a fitting
  only ever names an id the registry already has.** A missing id makes `place`
  return false silently, and a fitting whose kit half-exists comes out bare with
  nothing to say it went wrong.
- Decorative placement cannot silently alter topology; anything that blocks,
  covers, or breaks a sightline declares it.
- Capacity is not duplicated. The fixture count is the capacity; do not introduce
  a parallel number alongside it.

## Acceptance

Structural criteria, against the arrangements that already work:

- A mech bay generated at four gantries retains the gantry count, service
  access, and bulkhead relationship of today's Mech Lab. A barracks at twelve
  berths and three lanes retains hull-side berthing and the blast wall dividing
  range from commons.
- Changing a parameter changes both the fixture count and the compartment extent,
  and the result stays connected and deployable.

Fill criteria, which today's rooms would fail:

- **No unargued empty region.** A seed sweep reports every open floor region
  above a stated cell threshold; each one must be a circulation lane or carry a
  declared tactical reason. A region that is neither fails the sweep.
- **Fixtures appear in groups.** No fixture is placed as an isolated point
  except where the theme explicitly declares a solitary one, and group members
  share a coherent orientation.
- **Density varies by function.** Work and living clusters measurably exceed
  transit density within the same compartment, so the boundary is legible from
  fixtures alone rather than from floor decals.
- **Scale is anchored in-plane.** The mech bay places human-sized fixtures
  adjacent to its gantries, and its structure subdivides the floor. No fixture,
  tile, or flag implies a walkable surface above the deck.
- **Circulation survives the fill.** Every entry reaches every fixture group
  along authored lanes, and no fixture encroaches on a lane.
- A deterministic seed sweep produces no room whose fixtures block its own
  circulation or seal a hatch.
- Neither `MechLabSceneLayout` nor `BarracksSceneLayout` is consulted by the
  fill, and neither is used as a fill target. They remain in place as the
  structural reference; both have since been retired.

Judge the result against the criteria above rather than against
`mech-lab-wide.png` and `barracks-wide.png`. Those snapshots show the structure
to keep and the fill to beat.

## Out of scope

Ambient routes, adoption by the operations screens, and any facility beyond mech
bay and barracks. Armory and medical themes follow the same shape once these two
prove it.
