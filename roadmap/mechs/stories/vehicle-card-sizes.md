# Story — A vehicle's gun card is sized and locked by its hull

Status: IN PROGRESS (slices 1 to 3 shipped: the small omni card and the light weapons, the laser parts and the PD laser tank's retirement, then the laser ladder in its white orb-lens look; the Thermal Lance and SRM-3 (QS) regrade and the balance pass remain)

Written: 2026-10-28

Read `mechs-nouns.md` (a vehicle's gun card) and `convoy-nouns.md` (the laser tanks) before changing this story.

## Problem

A gun card today is typed and sized by the hull's own stock gun (`VehicleGunCard`): the card is whatever that gun
was, so the hull's size class and the gun's weapon class are one decision made by accident of the stock gun. Three
things follow. A hull whose stock gun is a one-row MG has a card no mech weapon fits. A small hull whose stock gun
is a missile pod (the SRM technical) has a card as big as an APC's. And the mech weapon catalogue has no weapon
power scale against footprint: every gun but a few is 2x2, so a 2x2 slot says almost nothing about what may stand in
it. The owner's direction (2026-10-28) is to size the card by the hull and lock it by colour where the hull is a
specialist, leaving the small slot omni.

## Goal

A card is a size class, as many rows as it is wide, by two columns, and a lock, a colour or omni:

| Class | Card | Lock | Hulls |
| --- | --- | --- | --- |
| Small | 1x2 | omni | APC, dozer, militia technical, SRM technical, hover scout |
| Medium | 2x2 | yellow (ballistic) | fire-support (mortar) APC |
| Medium | 2x2 | green (missile) | LRM carrier |
| Large | 3x2 | yellow (ballistic) | breakthrough tank (Onager) |
| Large | 3x2 | red (energy) | laser tank |

The medium and large cards are the ones the stock guns already imply, so those hulls do not change; the small
class is new, and it is where the work is. A hull's own gun stays no part (an empty card is that gun), so a small
hull's stock gun may be larger than its card, which is the point of the class: it is a hull-size statement, not a
claim about the stock gun.

## The small weapons

Slice 1 is shipped: the small omni card (`VehicleGunCard.SMALL`, the APCs, dozer, technicals and hover scout) and
the light weapons it is stocked with (`LIGHT_MACHINE_GUN`, `LIGHT_PULSE_LASER`; the light rocket pod shipped with
them and was retired for the SRM-3 (QS), owner 2026-10-30), each a one-row mech part with its own drawing, liveries, selector, geometry, weapon definition, catalogue and Store
entries; their stats are first proposals awaiting the balance pass. The text below is the original problem, kept
for the rows that are not done.

Only two mech weapons were one row before it, the Thermal Lance (38 damage, 14 penetration) and the SRM-3 (QS)
rocket pod (`SRM_3_QS`); the one-row MG card takes none. A small omni slot would therefore take a Thermal Lance on a 480-structure
technical. The owner chose to author light weapons: one or two one-row parts per colour (a light MG, a light rocket
pod, a light pulse laser), graded so a one-row part is clearly weaker per shot and per second than the two-row
weapon of its colour, and the Thermal Lance and SRM-3 (QS) re-graded against them (re-footprinted or retuned so
they no longer out-punch a 2x2 gun from two cells). Footprint is the power cap; there is no second rating.

## The laser tanks' guns become parts

Slice 3 is shipped (owner, 2026-10-29). Lasers are a ladder of three strengths, 1x2, 2x2 and 3x2, in two families: the PD
laser (steady lances that run cool, a slight red motif) and the burst laser (short heavy volleys that vent, clean white
and blue). Every part has a glass blue orb lens at the front and white electronics built into the arm; the 2x2 and the 1x2
are progressively miniaturized 3x2s, drawn from the picked 3x2 designs. The six parts: light PD (`LIGHT_PULSE_LASER`,
24 dps), PD (`PD_LASER`), heavy PD (`HEAVY_PD_LASER`), light burst (`LIGHT_BURST_LASER`), burst (`MEDIUM_BURST_LASER`) and
heavy burst (`BURST_LASER`); the constants keep the names they shipped under, so BURST_LASER is the 3x2. The two heavy
parts are nose guns only (the centreline mount), like the HC-20, and are Prestige; on a mech they fit the Warden's nose, which is an omni socket for it (the Hound's stays a 2x2 ballistic one, so it takes none); the others fit any arm. One
white finish for every faction (no livery mask). On a laser tank the 3x2 laser wears the turret head the hull had before:
the heavy burst laser the white head, the heavy PD laser the red head the PD laser tank carried (`MechWeaponComponent.tankHead`,
the red head's frames on the laser tank's sheet, fired from the head's lens). Those two heads are final as they are (owner, 2026-10-29): they are not regraded to match the lighter lasers. The stats are first proposals for the balance pass.

**A turret is modular, and a turret is not an arm** (owner, 2026-10-29). There is no default turret head for a hull
that takes parts: the laser tank's gun card is the only thing that gives it a turret. A modular hull (`VehicleType.hasModularTurret`,
the laser tank and the Onager; every other hull still carries the gun its sheet was drawn with) always shows an exposed
machinery ring (`gun-mount.png`) with a collar that turns inside it (`turret-collar.png`), so the player sees the
head driven. A hull with no part fitted has the ring bare and no gun (`GroundTurret.removed`, which reads as a shot-away
gun); content that fields a laser tank by name gets `VehicleType.standardPart` (the heavy burst laser) instead.
The heavy lasers are the old white and red heads on the ring. The lighter lasers (3x2 aside) are drawn short, a turret
and not a mech arm: each stands in a round turret base on the collar (`turret-base-medium.png` for two rows,
`turret-base-light.png` for one) with its muzzle only just past the ring (`VehicleGun.lengthCells`, `baseCells`). The
mech arm drawing is a different style from the vehicle turret and is not reused at arm length on a hull. A fabricated vehicle starts with its gun card empty
(owner, 2026-10-29): outfitting it is part of the play, so a fabricated laser tank is a bare ring until a part is fitted, and a
saved one with an empty card loads the same way. Art:
`art-source/vehicles/turret-ring/`; the lineup is the `tank-variants` suite's `laser-lineup*`.

**The ring has three finishes by hull class** (owner, 2026-10-29), `VehicleType.mountStyle` / `TurretMountStyle`: the army
hulls (APCs, technicals, trucks, tanks, carriers) an olive-drab bolted ring (`gun-mount-green.png`), the silver hover hulls
(hover scout, drone skimmer) a brushed-silver ring with cyan edge lights (`gun-mount-silver.png`), and the futuristic
laser tank the white one (`gun-mount-white.png`), which alone has the turning collar and the laser bases. A hull that
becomes a modular turret later brings its own collar and bases in its finish, or heads of its own: the Onager, in army
green, has no collar, and each ballistic family wears a head painted on its sheet (`convoy-nouns.md`, the Onager's
turret is modular); the `tank-variants` suite's `mount-styles`
frame shows one hull of each.

Slice 2 shipped the laser tanks' guns as parts (since redrawn and extended to the ladder above): the burst laser (an energy arm gun of two columns by three rows, centreline only
like the HC-20, Prestige) and the PD laser (two by two, Advanced) are red weapon parts that fill
the laser tank's three-row red card, bought and fitted like any mech weapon, each with its own drawing, liveries,
selector, geometry, weapon definition and Store entry. The burst laser is restated on a mech's cadence (four
16-damage pulses on a 1.8-second cycle at 38 flux, the flux hog) and the PD laser likewise (ten 7-damage pulses on a
1.6-second cycle at 16 flux, running cool, with the held-lance loop sound); both are first proposals for the balance
pass. The PD laser tank is gone as a hull: `PD_LASER_TANK`, its turret structure and weapon, its sheet, strings,
fabrication, tier and credits entries are deleted, and a laser tank with the PD laser fitted is what it was. The hull's
own gun, with no part fitted, is still the burst laser turret; emplacement and drone PD lasers and the tank's
active protection (now a rack kit) are untouched.

## Every new part is a mech weapon too

A shared part fits a mech's sockets by the mech's own fit rule, so each light weapon and each laser part must also
stand on a mech, not only on a hull: it needs a mech drawing (an arm gun or a shoulder pod, one per livery under
`art-source/mech-modular-topdown/`: its concept, material-id and weapon-paint mask, then the shipped factions
sheets), a selector in `LayeredMechAppearance` and a muzzle and kick in `MechHardpointGeometry`, which a hull
carrying the part reuses unchanged for its drawing, mount ring and effects (`VehicleGunArt`). The arm and pod
selectors share numbers, so a new part is keyed by its mount family as well as its selector. This is the main cost
of the story: roughly one new weapon drawing per light weapon (a light MG, a light rocket pod, a light pulse laser),
plus the burst laser and the PD laser, each a mech arm or shoulder piece first and a vehicle gun second, drawn
by the geometry-first ImageGen method (`geometry-first-imagegen.md`) from the existing weapon concepts. The tank's
own burst-laser and PD-laser turret heads stay as the 3x2 laser's look on a hull.

## Not decided

- Which other hulls become modular turrets (the mortar carrier): each needs its own ring and bare state; today the
  laser tank and the Onager are, and other fitted parts stand on the machinery ring.
- Whether the white lasers should take faction colour on a trim line; they have one finish for every faction.
- A campaign vehicle saved as a `PD_LASER_TANK` no longer loads: the constant is deleted, with no migration.
- The footprints and stats of the light weapons, which belong to a balance pass against `armorMatchup` and
  `loadoutMatchup`.
- Whether the mortar carrier's 2x2 yellow card takes a mortar-class part only or any medium ballistic weapon; the
  mech catalogue has no mortar part today.

## Touches

`VehicleGunCard` (size and lock as data, not derived from the stock gun), `VehicleDoll` and `MechLabDollCanvas`
(omni card art and tone), `CampaignVehicle`, `LayeredMechAppearance`, `MechHardpointGeometry`, the mech weapon art, the Store and fabrication catalogues, `MechWeaponComponent` (light
parts, the laser parts), `VehicleType` (`PD_LASER_TANK`), `strings.json`, and the tests that name the PD laser tank.
