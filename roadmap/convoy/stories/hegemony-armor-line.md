# Story — The Hegemony armour line: a shared turret head, a new APC, a medium tank and a repulsor Onager

Status: IN PROGRESS (slices 1 to 5 shipped: the Hegemony head, the APC and its carriers on the Hegemony hull, weight split from hover, the repulsor Onager with the dozer retired, and the medium tank; slice 6, the balance pass, remains)

Written: 2026-11-05

Read `convoy-nouns.md` (the hulls, the tank's turret section, mass and the shove) and `mechs-nouns.md` (a vehicle's
gun card, and how a fitted weapon is drawn on a hull) before changing this story.

## Problem

The army's tank and APC read as a present-day main battle tank and a modern six-wheeler. The owner wants Hegemony
armour: three concepts in one family, tan and olive plate with orange hazard panels, riveted and piped, each hull
with the same round, empty turret ring. The concepts are `heg-medium-tank.png`, `heg-apc-concept.png` and
`heg-heavy-repulsor-tank.png`, top-down, nose up, transparent; they become ImageGen masters under
`art-source/vehicles/` with their prompts, as every hull's art is.

At the same time a fitted weapon is drawn today as its mech drawing standing on a mount ring
(`VehicleGunArt`, `TurretMountStyle`), so a hull's gun reads as a mech arm bolted to a plate. The owner wants a turret
that looks like a turret, without losing the reuse of mech weapon art.

## Goal

### One turret head for every hull in the line

A **turret head** is a closed, armoured head, not much larger than the hull's turret ring, drawn by livery: the
Hegemony head first, a Tri-Tachyon or Independent head later. The fitted weapon is drawn **under** the head, so only
its barrel or launcher face pokes out through the head's front: the draw order on the ring is ring, weapon, head.
The weapon keeps its own mech drawing, registration and muzzle (`MechHardpointGeometry`), so every mech part that fits
a gun card fits every hull of every livery without new art; a new family is a head, not a set of guns. The head
turns with the weapon and is sized from the ring, not from the weapon. A wreck shows it charred and knocked off its
bearing, in its own painted wreck (`TurretHead.wreckTexture`). An empty gun card shows the head with nothing in its
slot.

This replaces the mount ring and the light-machine-gun gun plate (`gun-plate.png`) on hulls that wear a head. Hulls
that do not join the line (the technicals, the hover scout, the laser tank) keep today's drawing until a head of
their own is made.

### The APC, on the Hegemony hull

`HEAVY_APC` takes `heg-apc-concept.png`: six wheels, the ring amidships. The mortar carrier (`FIRE_SUPPORT_APC`) and
the LRM carrier move with it, since they are this hull with another gun. The concept's dark glow is trimmed to the
hull before packing. Its six tire tops are re-measured for `VehicleTreadRender`, and it gets a wreck. Seats, stats
and the small omni gun card are unchanged.

### A new medium tank

`heg-medium-tank.png` is a **new, lighter tracked tank** below the Onager (owner): faster, thinner plate, cheaper,
heavy weight class's lower end. It takes the tracked drive, tread animation and `HeavyTracks` undergrowth crush the
Onager has today. Shipped as the Scorpion (`MEDIUM_TANK`; `convoy-nouns.md`, The medium tank): a two-row ballistic
card with the HC-10 stock under the Hegemony head, a Medium lift at Junction. Those numbers are the proposal, awaiting
the owner's pick.

### The Onager becomes the heavy repulsor

`BREAKTHROUGH_TANK` takes `heg-heavy-repulsor-tank.png` (owner): the Onager stops being the modern tank and becomes a
heavy hover tank, broader and shorter than today's hull, its footprint and hit box measured from the new art. Its
tread animation goes; it hovers by the repulsor rules below.

### The dozer breacher is retired, and crushing goes by weight

`DOZER_BREACHER` goes (owner). There is no blade on any hull. What the blade did, opening ordinary walls and
barricades in its path, becomes a property of **weight**: an ultra-heavy hull crushes what it drives into because of
its mass, not because of a part. So:

- `VehicleType.Weight` gains a class above `HEAVY` (ultra-heavy); the repulsor Onager is in it. `massTonnes` follows.
- `DozerBlade`'s rule (pushable cells, structure ceiling, momentum spent through `HullMomentum`, the route-proof breach
  view) is recast around the hull's mass and weight class instead of `hasBlade`: which structure a hull can crush and
  how fast is read from its weight, and the bearing strip is the hull's own nose, not a blade wider than it. A plain
  `HEAVY` hull breaks ordinary walls too, not fortified ones (owner, 2026-11-05).
- A save or stock that names the dozer is carried to the Onager; its stock fit, store card and doll go.

### A heavy hull can hover

`isHover` today means Tri-Tachyon's light skirts: light, no body shove, no crush. A repulsor Onager is hover and
heavy, so the two are split: hover decides the ground it crosses (`HoverSkirt`: water and rubble), weight decides the
shove, the ram, the crush and the mass (`repelsBodies`, `crushesUndergrowth`, `ramsBarricades`, `massTonnes`). The
light hover hulls keep exactly today's behaviour. Grip, turning and the hover's drift for a 45-tonne-plus skirt are
tuned in the playtest, not by evidence alone.

## Slices

1. **Shipped.** **The turret head**, on today's APC: the draw order (ring, weapon, head), the Hegemony head art sized from the
   ring, its wreck, a shut head for an empty card. Proven on the current hull first, so the head is judged before any
   hull changes. Evidence: `createSnapshots` stills of the light machine gun, the SRM-3 (QS) and a cannon under the
   head at the APC's and the Onager's scale, and a full traverse.
2. **Shipped.** **The APC hull** and its two carriers, with tire-top bands and a wreck (`art-source/vehicles/apc/hegemony`):
   the concept turned and scaled to the old hull's length, its ring on the old roof bearing so no turret registration
   moved. The carriers keep their open turntables, not the head: a mortar and an LRM pod stand up to fire.
   The owner then grew the line to the medium tank's length (`VehicleType.Apc.SCALE` 1.6: 3.84 drawn, 3.6 x 1.44
   footprint), keeping the 1.6-cell wheelbase so it turns where the old hull did and a radius-1 route clearance so map
   generation is unchanged; the carriers' turret render numbers grow with it.
3. **Shipped.** **Weight and hover split** (`convoy-nouns.md`, mass decides the shove; weight crushes walls):
   `VehicleType.Weight` gains `ULTRA_HEAVY` (80 t); the shove, the crush, the ram and the mass are rules of the
   weight, hover only of the ground (a light skirt alone shoves nothing); a heavy skirt's view is the hover view over
   the crush view. `DozerBlade` reads its ceiling, rate and bearing width from the hull: heavy and ultra-heavy hulls
   crush at a ceiling of 100 across their own width, 50 and 80 a second, never a fort's works; the dozer by its blade
   as before. A crushing hull's breach view is kept and caught up cell by cell, and its route pays 8 for what it
   breaks. Every gun tank and the Scorpion crush walls; no hull is ultra-heavy yet.
4. **Shipped.** **The repulsor Onager** (`convoy-nouns.md`, the Onager; weight crushes walls): the concept turned to
   point left with an ImageGen wreck registered on it (`art-source/vehicles/heavy-repulsor`), drawn 4.3 cells long as the
   laser tank is (owner, grown from 4.0 so it reads above the Scorpion; `VehicleType.Onager.SCALE`), a 3.87 by 2.39 footprint,
   no treads, `ULTRA_HEAVY` on a heavy skirt, its cannon card under the Hegemony head sized to the painted ring. The
   dozer is retired: `DozerBlade` became `WallCrush`, `hasBlade` and the Onager's sheet heads went, and a save,
   pattern or card naming the dozer loads as the Onager (`VehicleType.fromSavedName`). Evidence: `createSnapshots
   -Psnapshot=tank` look shots (live, turning, wreck, crushing a wall row) and the `wall-crush`, `vehicle-ram` and
   `hover-line` scenes. A medium hover hull would ram
   barricades it meets but plan round them, since the rammer's view is not laid over the skirt's; none is planned.
5. **Shipped.** **The medium tank**: `MEDIUM_TANK`, the concept packed with an ImageGen wreck and the head as its
   fallback turret frames (`art-source/vehicles/medium-tank`), two tread runs a side, a two-row ballistic card, store,
   doll and Kit Ladder station. Evidence: `createSnapshots -Psnapshot=medium-tank`.
6. **Balance pass** against a player giving orders, not the autopilot.

## Open

- The owner's pick on the medium tank's role: Medium lift at Junction (built) or Heavy lift at Thunder Run.
- Settled in slice 3 (owner: heavy vehicles break ordinary walls, not fortified ones): heavy and ultra-heavy hulls
  share the ceiling of 100, so city (100) and housing (80) walls and barricades fall and fortified walls (150 and up)
  hold; heavy grinds more slowly (50 a second, against 80) and carries less mass through a row. Whether gun tanks
  now breach houses too eagerly for a player's taste is the balance pass's (slice 6): momentum at a gun tank's speed
  takes a row of city wall barely checked.
- The repulsor Onager's drive is a first figure for the playtest (owner): 2.9 cells a second, 0.8 to get going and
  2.4 to stop, a 34° turn at 110° a second, a 45° a second pivot, a heavy skirt's grip of 1.5 along and 4 across.
- The active-protection kit's deck overlay is registered to the laser tank's frame, not yet refitted to the Onager's
  or the medium tank's deck; the Onager's doll is still the tracked tank's outline.
- The laser tank stays on today's hull until a Tri-Tachyon head and hull are drawn.
