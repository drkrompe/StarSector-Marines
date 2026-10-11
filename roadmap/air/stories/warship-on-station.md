# Story — Warship on station (Paragon)

Status: PLANNED

Written: 2026-10-11

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
and ground-effect weapons. The first hull is the Paragon, its laser lance
sweeping across a compound in the manner of the Harkonnen assault in Dune:
slow, enormous, and not survivable by standing still. Friendly fire is on.

## Scope

1. **Body and flight.** Reuse `AirBody` and hull-derived footprint. A hover
   model with mass-limited translation and yaw; phases arrive, station, shift,
   depart. No sortie, landing or run.
2. **Sweep weapon.** Authored arc, duration, band width and per-tick damage. The
   swept segment damages units, cover, walls, roofs and structures through the
   existing damage and `MapEditor` paths at capital magnitude, leaving rubble
   and persistent scorch in the manner of the fence-breach debris.
3. **Director.** Picks sweep arcs over the densest opposing concentration not
   already being worked; telegraphs the arc before the beam lights.
4. **Faction, not allegiance.** The entity is owned by a faction supplied by its
   committer; the first acceptance uses the company's own ship and an enemy one.
5. **Answerability.** A bounded station time and a hull pool that only heavy
   anti-air, mechs or batteries reach.
6. **Presentation.** Large shadow on the ground, beam, burn, dust and debris.
   Rendering stays downstream of the simulation.

## Acceptance

- A `BehaviorScene` with a Paragon over a generated compound: the sweep razes
  walls and roofs, kills units in the band, and spares dispersed units outside
  it. A control loop with the ship absent.
- Friendly fire is demonstrated in a loop with own marines in the arc.
- `createSnapshots` suite renders the sweep at 1920x1080.
- Headless hull size is measured (`HullFootprintResolver.isMeasured`).

## Out of scope

Barrage weapons, point defence, modules, ship-to-ship combat, campaign
commitment through a command power, and every hull other than the Paragon.
