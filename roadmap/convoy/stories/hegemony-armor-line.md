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
   moved. The carriers do not wear the closed head, since a mortar and an LRM pod stand up to fire: they carry them in
   the open Hegemony barbette (owner's pick; `TurretHead.HEGEMONY_BARBETTE`, art-source/vehicles/turret-barbette),
   an armoured wall round an open well fixed to the hull and drawn under the weapon in place of the ring.
   The owner then grew the line to the medium tank's length (`VehicleType.Apc.SCALE` 1.6: 3.84 drawn, 3.2 x 1.44
   footprint, its front edge a quarter cell behind the painted nose so the nose overhangs as though lifted off the
   ground: `VehicleType.noseInsetCells`), keeping the 1.6-cell wheelbase so it turns where the old hull did and a radius-1 route clearance so map
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
   laser tank is (owner, grown from 4.0 so it reads above the Scorpion; `VehicleType.Onager.SCALE`), a 3.38 by 2.21 footprint, its nose lifted a quarter cell as the APC's is and its flanks a little inside the paint,
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
7. **Shipped.** **Liveries** (owner: the line reads as old, low-tech Hegemony issue; an Independent variant in blue
   and a pirate one worn and red): `VehiclePaint`. The Hegemony sheets and heads are the masters; a paint is the same
   sheet recoloured pixel for pixel (`art-source/vehicles/liveries/paint.py`): Independent turns the hazard panels
   blue and fades the plate grey, pirates turn them red and darken, soot, chip and rust the plate. A side drives in
   the paint of the faction its mechs wear the livery of (employer for the marines, target for the defenders); every
   faction but those two keeps the Hegemony's. Evidence: `createSnapshots -Psnapshot=vehicle-liveries`.
8. **Shipped.** **Weapon-point variants** (owner: a difference in the doll, not a locked turret; yellow, green and red,
   one of each): the Scorpion and the Onager are built in any of the three colours, the same hull with its gun card
   locked to ballistic, missile or energy at the same rows (`VehicleType.weaponPoints`, `VehicleGunCard.of(type,
   point)`: the Scorpion's two-row cards, the Onager's three-row ones). The colour is chosen when the hull is built:
   the pattern is bought once and the lab lists one build per colour at its price
   (`MechFabricationCatalog.vehicleBuilds`), and the hull keeps it (`CampaignVehicle.point`); a hull from a save from
   before is the yellow one. The doll's turret card, its fitting and the pad preview read it. A missile rack in a
   closed head's place stands in the open barbette, as the carriers' do (`VehicleType.turretHead(point)`), so a green
   tank raises its rack to fire; red and yellow keep the closed head. The APC carriers are already the Packhorse's
   yellow and green, and the laser tank keeps its own card. A stock spawn fits its colour's part
   (`VehicleType.standardPart(point)`: SRM-5 and PL-2 on the Scorpion, SRM-15 and PL-3 on the Onager), and a battle's
   defenders field their faction's colour (`FactionVehiclePoints`: the Diktat and the Lion's Guard red, the Luddic
   factions green, everyone else, independents and pirates among them, yellow). Evidence: `createSnapshots
   -Psnapshot=vehicle-colours` (each colour firing) and `-Psnapshot=bought-machines` (the three Scorpion dolls); the
   proving ground's third rank fields the green and red ones.

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
- An empty green hull on the lab pad shows the closed head until a rack goes in: the pad's battle render reads the
  head from the fitted weapon, and a bare hull has none. The store's pattern card shows the yellow doll.
- Independents and pirates field only yellow; a mix of whatever they could buy or steal is not built.
- Only the defenders take their faction's colour; allies and the marines' stock hulls are yellow.
