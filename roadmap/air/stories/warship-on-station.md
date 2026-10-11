# Story — Warship on station

Status: PLANNED

Written: 2026-10-11

Updated: 2026-10-11 — Onslaught first (Hegemony, shells and rockets); Paragon and the lance sweep follow.

Read `air-nouns.md` (**Overhead ships**), `ballistics-nouns.md`,
`combat-durability-nouns.md`, `command-powers-nouns.md` and
`vanilla-combat-bridge-nouns.md` before accepting this story.

## Problem

Players want to bring a capital into the fight. The hybrid bridge cannot show
it: vanilla damage reaches the ground only through proxies of structures, and
infantry area damage is unmodelled. A capital's weapons are area effects on
ground forces, which only the ground simulation can resolve.

## Goal

A native sim entity, the **warship on station**, with its own hover flight model
and ground-effect weapons whose look is the game's own. Friendly fire is on.

**First hull: the Onslaught.** The opening missions are on Jangala, a Hegemony
world, and the Onslaught is the Hegemony's capital: a gun-and-rocket ship whose
stock variants carry Devastator Cannons, Hypervelocity Drivers, Mark IX and
heavy autocannon turrets, Annihilator/Sabot rocket pods and flak, with two
built-in Thermal Pulse Cannons on its large hardpoints. That is the *barrage*
side of the design: shells and rockets landing as ordinary detonations, with a
fall time to run from.

**Paragon follows, with the beam.** Its Tachyon Lances (large turrets, WS 003
and 004) are the *sweep*: slow, enormous, and not survivable by standing still.
`LanceSweep` already exists for it and waits for that hull.

## Scope

1. **Body and flight.** Reuse `AirBody` and hull-derived footprint. A hover
   model with mass-limited translation and yaw; phases arrive, station, shift,
   depart. No sortie, landing or run.
2. **Weapons come from the hull's own slots and a variant.** `TurretSlotResolver`
   already reads a hull's `weaponSlots`; a vanilla variant (or the fleet
   member's fitted one) names the weapon in each slot. Each slot's weapon is
   mapped to a ground effect, and the turret auto-fires at ground targets.
   Hardpoints and turrets are different mounts: the Onslaught's pulse cannons
   are fixed hardpoints, its guns traverse.
3. **Barrage first.** Shells and rockets resolve as `Detonations` at the landing
   point with a telegraphed fall time and capital-scale splash, wall breach and
   roof loss. Damage and radius derive from the weapon's own figures.
4. **The look is the game's.** Colours, sprites, projectile art and impact art
   are read from the weapon's own spec and the install's `fx` sprites through
   `VanillaWeaponLooks`; the mod authors none of them. Turret bodies use the
   vanilla turret sprites.
5. **Sweep, later.** Beam weapons resolve through `LanceSweep` with the vanilla
   beam textures. Not in the first slice.
6. **Orders.** Hold fire and area fire (with optional bring-guns-to-bear heading) are shipped; a director that picks the area by density, as the airfield strike does, is open.
7. **Faction, not allegiance.** The entity is owned by a faction supplied by its
   committer; the first acceptance uses the company's own ship and an enemy one.
8. **Answerability.** A bounded station time and a hull pool that only heavy
   anti-air, mechs or batteries reach.

## Acceptance

- A `BehaviorScene` with an Onslaught over a generated compound: its barrage
  razes walls and roofs, kills units in the splash, and spares dispersed units
  outside it. A control loop with the ship absent.
- Friendly fire is demonstrated in a loop with own marines in the arc.
- `createSnapshots` suite renders the sweep at 1920x1080.
- Headless hull size is measured (`HullFootprintResolver.isMeasured`).

## Out of scope

Point defence, modules (the Onslaught's armour modules included),
ship-to-ship combat, campaign commitment through a command power, and every
hull other than the Onslaught and, afterwards, the Paragon.
