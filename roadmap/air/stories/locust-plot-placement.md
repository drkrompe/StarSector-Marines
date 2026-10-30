# Story — Locust plot placement and clearance

Status: PLANNED — scoped and investigated; nothing built

Written: 2026-10-30

Read `tender-ship.md` (Staged builds) first. This story finishes the staged Locust builds (the 3x3 nest and the 5x5
fire base) with four pieces of placement quality the owner asked for after the first cut shipped. It adds no new noun:
it changes what a plot's pad clears, when its gun stands, and what the player sees while placing it.

## What the owner asked for

After reviewing the staged build (owner, 2026-10-30):

1. **Clear loose props** from the plot rather than leaving holes in the rim.
2. **Don't build the gun over a marine.**
3. **A hover preview**: a ghost of the finished layout under the cursor (picked over an outline-only marker).
4. **Right-click cancel**: make sure right-click backs out of placement; nothing more (picked over right-clicking a
   laid site to cancel it).

## How it behaves today

- **Placement** (`Tender.siteOpen`) refuses a plot when any cell is unseen, off the map, unwalkable (walls, buildings,
  blocking props and boulders), a doorway, under the hull, or shared with another live or kept site. Walkable props
  (scrub, passable rocks, loose sandbags, crates, drums, earlier fort works) do not refuse it.
- **Stages** (`PlotWorks`): the pad paves under walkable props and clears the passable nature scatter and its cover;
  the rim and stores skip any cell that already carries a prop (`dressed`), so a loose crate leaves a gap in the rim.
- **Marines**: the pad, rim and stores never block movement (pieces are walkable cover marked see-through fixtures),
  so a marine on a rim cell simply stands in cover. The Locust is a one-cell structure, which
  `BattleSimulation.sealMultiCellStructure` deliberately leaves unsealed, so a marine on the gun cell overlaps the
  finished turret and units path through it afterwards. Every one-cell tender turret already behaves this way.
- **Right-click**: `CommandPowerTargetingPanel.handleInput` disarms on right-click or Escape, and
  `ShipOrderTargetingPanel.handleInput` cancels on right-click or Escape. Both already exist; neither has been
  confirmed in a played battle.
- **Preview**: all three placement cursors (yard power, ship order, direct-control reticle) outline the plot in the
  valid or refused tone (`CommandPowerTargetingPanel.paintPlot`, `DirectControlPanel.siteReticle`). The facing is
  chosen when the order sets out (`TenderSystem.facing`), so nothing shows it before the click.

## The changes

**1. The pad clears loose props.** When the pad stage lays, every walkable prop on a plot cell that is loose (natural
scrub and small rocks, sandbag, crate, drum and debris props) is removed. Removal goes through a new
`DoodadService` removal that also takes the prop's cover cells and fighting-position entry back out and drops the
`topology` fixture flag, so cover and the render list agree. Placement additionally refuses a plot over props that
matter: mission or objective props, wrecks and parked vehicles, and any piece of fort works (an existing parapet or
barricade). Decide "loose" from the doodad's catalog data (its wearable-cover family or natural flag), never from a
hard-coded id list.

**2. The gun waits for a clear cell.** At completion, while any live unit stands on the gun cell (or within half a
cell of its centre), the order holds at full progress, like a stood-down site: builders keep station, no charge is
spent twice, and the gun stands on the first tick the cell is clear. A held gun shows the contested tone on the site
marker. The pad, rim and stores still go down under marines.

**3. The ghost.** While a staged build is being placed, the cursor shows the finished layout at low alpha: the pad
cells, rim pieces, stores and the gun (its catalog collar and head), at the facing the order would take, tinted to the
refused tone when the plot is refused, over the existing outline.

- **One facing, chosen once.** Move the facing choice out of the order's start into a public
  `TenderSystem.plotFacing(sim, tender, x, y)` (nearest known hostile, else away from the hull, as today). The ghost
  calls it every frame, and the order is faced on the first tender tick after it is laid (a `faced` flag on
  `ConstructionOrder`), so a queued build keeps the facing the player saw.
- **Drawing.** Pieces come from `BuildPlot.pieces`. The rim and stores draw through
  `DoodadRenderSystem.collectPlacements` with a low alpha multiplier, from a list of ghost `Doodad`s. The gun draws
  its structure's mount sprite from `BattleSprites` (`ensureTurretSprites`) with the collar from the structure's
  `art.base`. The panels are UI and the doodad path is a render system, so the panels post a presentation-only
  `BuildPreview` (pieces, gun cell, structure id, valid) that a render system reads and drops once it is older than a
  few frames; it is never read by the simulation. The pad draws as a translucent concrete-tone fill per cell.

**4. Right-click.** No code change unless the check fails: in a played Working Passage, right-click and Escape must
back out of yard-power placement and the ship order menu without laying anything or swallowing the next order.

## Acceptance

- Unit: a loose prop on a plot cell is gone after the pad stage and its cover with it; a plot over a mission prop or
  a fort piece is refused; a completed order whose gun cell is occupied holds and stands the gun once the cell
  clears; a laid order keeps the facing `plotFacing` gave at the lay. Each under a second.
- Look: a `morrow-look` view with a crate and a marine on the fire base's plot shows the crate cleared, the rim whole
  and the gun waiting until the marine moves; a still of the ghost in valid and refused tones at play zoom
  (1920x1080).
- Played: right-click and Escape back out of both placement modes on Working Passage.
