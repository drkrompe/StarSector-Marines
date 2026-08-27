# Facility room themes

Status: IN PROGRESS

Written: 2026-08-26

Updated: 2026-08-27 — the mech bay is furnished and publishes affordances and
task points. Berths, lounge extent and firing lanes in the barracks, and the
density and empty-region sweeps, remain.

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
- Fixtures are placed as **fixture groups** — an anchor, its satellites, and a
  shared orientation — not as independent points. A berth is a bunk with its
  footlocker and personal clutter; a fabrication station is a bench with its
  tooling, parts bin, and spoil.
- **Circulation lanes** authored before fixtures, connecting every entry to every
  fixture group, with fixture placement forbidden from encroaching on them.
- Purposeful density variation: dense work and living clusters, clear transit,
  and clear space only where a firing lane or hatch approach needs it.
- Fixtures declare their tactical effect and their ambient affordance. Affordance
  is authored here and consumed by `fixture-derived-ambient-routes.md`.
- A **refit level** per compartment, which is how the upgrade chain works: the
  same floor area fitted better holds more. A berth compartment given single
  racks and generous aisles berths fewer than the same compartment given triple
  racks and working room only. Capacity is the fixture count at the fitted
  level, so an upgrade re-fits a room rather than enlarging it or adding a new
  one.
- Compartment extent follows from the room's authored footprint. A refit changes
  what the floor holds, not how much floor there is.

## Constraints

- Fixtures use existing registry doodad ids. This story adds no art.
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
  structural reference until `company-ship-deck-adoption.md` retires them.

Judge the result against the criteria above rather than against
`mech-lab-wide.png` and `barracks-wide.png`. Those snapshots show the structure
to keep and the fill to beat.

## Out of scope

Ambient routes, adoption by the operations screens, and any facility beyond mech
bay and barracks. Armory and medical themes follow the same shape once these two
prove it.
