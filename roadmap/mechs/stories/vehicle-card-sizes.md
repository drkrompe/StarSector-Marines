# Story — A vehicle's gun card is sized and locked by its hull

Status: PROPOSED

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

Only two mech weapons are one row today, the Thermal Lance (38 damage, 14 penetration) and the Pioneer Rocket
Cradle; the one-row MG card takes none. A small omni slot would therefore take a Thermal Lance on a 480-structure
technical. The owner chose to author light weapons: one or two one-row parts per colour (a light MG, a light rocket
pod, a light pulse laser), graded so a one-row part is clearly weaker per shot and per second than the two-row
weapon of its colour, and the Thermal Lance and Pioneer Cradle re-graded against them (re-footprinted or retuned so
they no longer out-punch a 2x2 gun from two cells). Footprint is the power cap; there is no second rating.

## The laser tanks' guns become parts

The burst laser (the laser tank's) and the PD laser (the PD laser tank's) become red weapon parts that fill the
laser tank's 3x2 red card, bought and fitted like any mech weapon, instead of being what two hulls are built
around. The PD laser tank retires as a hull: it is the laser tank with the PD laser fitted. Their turret stats are
vehicle-scale (the burst laser fires four 16-damage pulses on a 0.55-second cooldown, the PD laser ten 7-damage
pulses a second), far above a mech laser's, so each part is restated on a mech's cadence and flux (a cycle and a
flux per trigger, as `MechWeaponComponent` states them) and sized by the power-against-footprint grade above. The
burst laser is a large part and the PD laser a medium one. Emplacement and drone PD lasers, and the screened tank's
active protection, are not touched.

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

- How the laser tank ships once its gun is a part: with the burst laser fitted as a default part, or with the card
  empty and the hull's own gun still the burst laser (the existing "empty card is the stock gun" rule), which
  keeps the stock gun out of the Store.
- What becomes of the PD laser tank's livery and art (`army-tank-pd-laser`), its Prestige line and Heavy lift,
  its credits and fabrication entries, and any campaign vehicle already holding one.
- The footprints and stats of the light weapons, which belong to a balance pass against `armorMatchup` and
  `loadoutMatchup`.
- Whether the mortar carrier's 2x2 yellow card takes a mortar-class part only or any medium ballistic weapon; the
  mech catalogue has no mortar part today.

## Touches

`VehicleGunCard` (size and lock as data, not derived from the stock gun), `VehicleDoll` and `MechLabDollCanvas`
(omni card art and tone), `CampaignVehicle`, `LayeredMechAppearance`, `MechHardpointGeometry`, the mech weapon art, the Store and fabrication catalogues, `MechWeaponComponent` (light
parts, the laser parts), `VehicleType` (`PD_LASER_TANK`), `strings.json`, and the tests that name the PD laser tank.
