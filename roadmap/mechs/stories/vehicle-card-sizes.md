# Story — A vehicle's gun card is sized and locked by its hull

Status: IN PROGRESS (slices 1 and 2 shipped: the small omni card and the light weapons, then the laser parts and the PD laser tank's retirement; the Thermal Lance and Pioneer regrade and the balance pass remain)

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
| Small | 1x2 | omni | heavy APC, escort APC, up-armored APC, dozer, militia technical, SRM technical, hover scout |
| Medium | 2x2 | yellow (ballistic) | fire-support (mortar) APC |
| Medium | 2x2 | green (missile) | LRM carrier |
| Large | 3x2 | yellow (ballistic) | breakthrough tank, screened tank |
| Large | 3x2 | red (energy) | laser tank |

The medium and large cards are the ones the stock guns already imply, so those hulls do not change; the small
class is new, and it is where the work is. A hull's own gun stays no part (an empty card is that gun), so a small
hull's stock gun may be larger than its card, which is the point of the class: it is a hull-size statement, not a
claim about the stock gun.

## The small weapons

Slice 1 is shipped: the small omni card (`VehicleGunCard.SMALL`, the APCs, dozer, technicals and hover scout) and
the three light weapons it is stocked with (`LIGHT_MACHINE_GUN`, `LIGHT_PULSE_LASER`, `LIGHT_ROCKET_POD`), each
a one-row mech part with its own drawing, liveries, selector, geometry, weapon definition, catalogue and Store
entries; their stats are first proposals awaiting the balance pass. The text below is the original problem, kept
for the rows that are not done.

Only two mech weapons were one row before it, the Thermal Lance (38 damage, 14 penetration) and the Pioneer Rocket
Cradle; the one-row MG card takes none. A small omni slot would therefore take a Thermal Lance on a 480-structure
technical. The owner chose to author light weapons: one or two one-row parts per colour (a light MG, a light rocket
pod, a light pulse laser), graded so a one-row part is clearly weaker per shot and per second than the two-row
weapon of its colour, and the Thermal Lance and Pioneer Cradle re-graded against them (re-footprinted or retuned so
they no longer out-punch a 2x2 gun from two cells). Footprint is the power cap; there is no second rating.

## The laser tanks' guns become parts

Owner direction (2026-10-29), partly built: lasers come in three strengths, 1x2, 2x2 and 3x2, in two families, the PD
laser (a slight red motif) and the burst laser (clean white and blue), every one with a glass blue orb lens at the
front and white electronics behind it, built into the arm; the 2x2 and the 1x2 are progressively miniaturized 3x2s. On a
laser tank the 3x2 laser wears the turret head the hull had before, the PD laser the red head the PD laser tank carried
and the burst laser the white head the laser tank carries (built: `MechWeaponComponent.tankHead`, the red head's frames
on the laser tank's sheet); the olive part drawing no longer appears on a tank. The new mech drawings, the three missing
parts and the renaming of the existing ones are the remaining work.

Slice 2 is shipped. The burst laser (`BURST_LASER`, an energy arm gun of two columns by three rows, centreline only
like the heavy cannon, Prestige) and the PD laser (`PD_LASER`, two by two, Advanced) are red weapon parts that fill
the laser tank's three-row red card, bought and fitted like any mech weapon, each with its own drawing, liveries,
selector, geometry, weapon definition and Store entry. The burst laser is restated on a mech's cadence (four
16-damage pulses on a 1.8-second cycle at 38 flux, the flux hog) and the PD laser likewise (ten 7-damage pulses on a
1.6-second cycle at 16 flux, running cool, with the held-lance loop sound); both are first proposals for the balance
pass. The PD laser tank is gone as a hull: `PD_LASER_TANK`, its turret structure and weapon, its sheet, strings,
fabrication, tier and credits entries are deleted, and a laser tank with the PD laser fitted is what it was. The hull's
own gun, with no part fitted, is still the burst laser turret; emplacement and drone PD lasers and the screened tank's
active protection are untouched.

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
own burst-laser and PD-laser turret art stays as the hull's stock look until a hull carries the part.

## Not decided

- Whether the laser tank should ship with the burst laser part fitted, rather than the card empty (decided for
  now: empty, the hull's own gun the burst laser turret, which keeps the stock gun out of the Store).
- A campaign vehicle saved as a `PD_LASER_TANK` no longer loads: the constant is deleted, with no migration.
- The footprints and stats of the light weapons, which belong to a balance pass against `armorMatchup` and
  `loadoutMatchup`.
- Whether the mortar carrier's 2x2 yellow card takes a mortar-class part only or any medium ballistic weapon; the
  mech catalogue has no mortar part today.

## Touches

`VehicleGunCard` (size and lock as data, not derived from the stock gun), `VehicleDoll` and `MechLabDollCanvas`
(omni card art and tone), `CampaignVehicle`, `LayeredMechAppearance`, `MechHardpointGeometry`, the mech weapon art, the Store and fabrication catalogues, `MechWeaponComponent` (light
parts, the laser parts), `VehicleType` (`PD_LASER_TANK`), `strings.json`, and the tests that name the PD laser tank.
