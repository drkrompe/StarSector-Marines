# Story — Staged Locust build

Status: PLANNED — owner picks open on the Morrow handling picks page (`locust-3x3-form`, `locust-5x5-layout`, `locust-menu`); nothing built

Written: 2026-10-30

Read `tender-ship.md` (build options, construction orders, stand-down), `ground-turret-tiers.md` (nests and raised
hardpoints) and `fort-works.md` (the parapet and barricade pieces) first. This story adds no new noun for the gun or
the works: it is a construction order that lays a fort's prepared position in stages.

## What the owner asked for

"Locust artillery" should be a multi-cell build: the drones build up the ground in a 3x3, put a short concrete
outer barrier round it, then build up the turret head. A larger option may build a prepared fire base, 5x5, with the
gun not necessarily in the middle (owner, 2026-10-30).

## The model

**A staged build is one construction order over a plot.** A `BuildOption` gains a **plot** (the cells it occupies,
3x3 or 5x5, laid out relative to a centre and a forward facing) and a list of **stages**. The order keeps its single
progress, threat stand-down, builder stations and charge exactly as `tender-ship.md` describes; progress crossing a
stage's share of the build time completes that stage, and each completed stage changes the ground at once.

| Stage | What appears | Made of |
| --- | --- | --- |
| Pad | The plot's open cells are cleared and paved: concrete, ground scatter gone | The fort pad's paving (`FortStage.pad`) |
| Parapet | The short concrete rim round the gun, or round the plot, with any gap the layout leaves | `TileManifest.emplacementTightParapet`: wearable cover, intact to destroyed by its own hits |
| Stores | Sandbag runs and ammunition stacks inside the rim (fire base only) | `doodad.sandbag-straight-*`, `doodad.industrial-crate-stack` |
| Gun | The Locust on its collar, or the Locust Hardpoint on its plinth | `structure.turret-locust` or `structure.hardpoint-locust` through `MapTurret.create` |

**A finished stage stays.** A cancelled or failed order (stood down for good, builders lost, Morrow lost) leaves the
pad, parapet and stores it completed: real cover the company can use, and ground the next order can build on. Only an
unfinished stage is lost. A site whose stages all stand is not re-laid.

**Placement.** The whole plot must be seen, open, walkable ground off the hull, clear of other sites' plots (the rule
`Tender.buildableSite` already applies to a single cell, applied to every plot cell). The placement marker draws the
plot's outline, so a refused cramped spot is visible before the click.

**Facing.** A plot's forward side faces the nearest known hostile at the moment it is laid, or straight away from
Morrow when none is known (recommended). Dragging to set the facing is a later refinement.

**Time.** The stages split the option's full-crew time. Draft: the 3x3 nest is 20 s (pad 4, parapet 6, gun 10),
today's Locust time; the 5x5 fire base is 34 s (pad 8, parapet 10, stores 4, gun 12).

## Layouts (owner picks)

The mock-ups are `createSnapshots -Psnapshot=locust-fire-base-mock` (`LocustFireBaseMockSuite`): each layout on arid
ground with a four-marine fire team for scale, forward up the frame.

**The 3x3.**

- **Nest (recommended):** a one-cell Locust on its round collar at the centre of a paved 3x3, the tight parapet ring
  round it. What the owner described, and the three stages read plainly (`nest-1-pad`, `nest-2-parapet`,
  `nest-3-gun`).
- **Hardpoint:** the existing Locust Hardpoint, its one-storey plinth filling the 3x3 (`hardpoint`). It sees over
  its own line from height and carries a flux shield; its stages would be pad, plinth, gun, with no separate barrier.

**The 5x5 fire base.**

- **Forward gun (recommended):** paved 5x5, parapet rim on three sides and open at the rear for the crew, the Locust
  a cell forward of centre, sandbags either side of it and two ammunition stacks behind (`base-forward`).
- **Corner gun:** the Locust in the forward-left corner, a sandbag wing along the front right, ammunition at the rear
  right, a one-cell opening at the rear (`base-corner`).
- **Mount:** the Locust Hardpoint centred with the rim round it, a fort's missile mount as `FortStage` builds it
  (`base-mount`).

**The menu.** Both the 3x3 and the 5x5 as build options beside the heavy MG (recommended: a quick nest and a slow,
stronger base), or the 5x5 alone in place of the Locust.

## Open

- The owner's three picks above.
- Whether the fire base's parapet and stores are worth more than cover (a reload or protection rule for the gun
  inside). Recommended: no; the rim is cover and the stores are dressing.
- A built plot's art in the lot's works style follows `fort-works.md` once a recovery yard has a style; brutalist
  (the base art) until then.

## Acceptance

- Unit: a staged order completes its stages at their shares of the progress and leaves completed stages standing on
  cancel; a plot is refused when any of its cells is closed, unseen, under the hull or inside another plot.
- Look: the `morrow-look` build sequence shows each stage appear under the welding builders, and the mock-up layout
  the owner picked.
