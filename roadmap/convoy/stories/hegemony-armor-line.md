# Story — The Hegemony armour line: a shared turret head, a new APC, a medium tank and a repulsor Onager

Status: IN PROGRESS (slices 1 to 3 shipped: the Hegemony head, the APC and its carriers on the Hegemony hull, then weight split from hover; slices 4 to 6 remain)

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
Onager has today. Its gun card, price, unlock station and numbers are this story's to propose and the owner's to
pick.

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
  how fast is read from its weight, and the bearing strip is the hull's own nose, not a blade wider than it. Whether a
  plain `HEAVY` hull also crushes barricades (it rams them today, `BarricadeRam`) or only ultra-heavy crushes walls is
  the slice's to propose.
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
3. **Shipped.** **Weight and hover split** (`convoy-nouns.md`, mass decides the shove; weight crushes walls):
   `VehicleType.Weight` gains `ULTRA_HEAVY` (80 t); the shove, the crush, the ram and the mass are rules of the
   weight, hover only of the ground (a light skirt alone shoves nothing); a heavy skirt's view is the hover view over
   the crush view. `DozerBlade` reads its ceiling, rate and bearing width from the hull: an ultra-heavy hull crushes
   at 100 and 80 a second across its own width, the dozer by its blade as before. No hull is ultra-heavy yet.
4. **The repulsor Onager**: art, footprint, wreck, no treads, the dozer retired and its saves carried. It moves into
   `ULTRA_HEAVY`; `DozerBlade` and `hasBlade` go with the dozer (the class renamed for the weight rule). A heavy,
   not ultra-heavy, hover hull would ram barricades it meets but plan round them, since the rammer's view is not
   laid over the skirt's; none is planned.
5. **The medium tank**: a new `VehicleType`, its art, wreck, treads, card, store and ladder placement (`kit-ladder.md`).
6. **Balance pass** against a player giving orders, not the autopilot.

## Open

- The medium tank's numbers, gun card, price and station.
- Proposed in slice 3, the owner's to confirm: `HEAVY` hulls keep ramming barricades (`BarricadeRam`) and crush no
  walls; the ultra-heavy ceiling is 100, the blade's, so fortified walls (150 and up) hold until fire wears them
  under and a fort is still opened by fire and charges. Its mass shows in how far through a wall row it carries.
- The repulsor Onager's grip and drift; a heavy skirt's first figure (1.5 along, 4 across) is the playtest's to tune.
- The laser tank stays on today's hull until a Tri-Tachyon head and hull are drawn.
