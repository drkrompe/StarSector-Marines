# Story — Warship on station

Status: PLANNED

Written: 2026-10-11

Updated: 2026-10-13 — readiness resolved by ship-support writeback; shields shipped on the flux pool.

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
6. **Orders.** Hold fire and area fire (with optional bring-guns-to-bear heading) are shipped; station-keeping movement and a density director are shipped. A directed ship holds up to three non-overlapping areas at once (`direct(radius, bringGuns, maxAreas)`), mounts dealt among them; `onslaught-district` is the acceptance.
7. **Faction, not allegiance.** The entity is owned by a faction supplied by its
   committer; the first acceptance uses the company's own ship and an enemy one.
8. **Point defence (shipped).** Flak, vulcan and PD-laser mounts shoot down
   hostile in-flight ordnance in reach, independent of orders; acceptance is the
   `warship-point-defence` scene. Standing rule lives in `air-nouns.md`.
9. **Answerability.** The hull pool is shipped (hit points and armour from the
   hull table, one armour rule, destruction removes the ship); a bounded station
   time (`stayFor`) and an ordnance budget (`limitOrdnance`, per-weapon ammo) are shipped;
   acceptance is the `warship-budget` scene. Whether stock ground weapons should be
   able to reach a hull at all, rather than only scripted or heavy ordnance, is a
   balance question for the mission that fields a counter.

10. **Flux (shipped).** Hull `max flux` / `flux dissipation` and weapon
    `energy/shot` / `energy/second` drive one pool per ship: soft threshold
    (stop starting fire at 85%, resume at 55%), overload (4-10 s total silence),
    point defence on the same pool, `fluxScale`, off by default, debug toggle
    (briefing row and F8) with a status-line readout. Acceptance is the
    `onslaught-flux` scene. Standing rule lives in `air-nouns.md`.

11. **Shields (shipped).** The hull's shield (type, arc, upkeep, efficiency,
    radius, centre) is read from the data; raised, it draws upkeep, stops the pool
    venting, and absorbs hostile detonations from inside its arc as flux; a full
    pool drops it and overloads the ship. Automatic by default, `raiseShield` /
    `shieldAuto` override; shown in the debug status line and drawn in the
    renderer. The `Warship.SHIELD_RESERVE_SCALE` half-flux stand-in is renamed
    `BRAWLER_FLUX_SCALE` and kept at 0.5: the `warship-shields` scene's
    `guns-full-pool` loop shows the unshielded guns peak at 28% of the full pool
    and never rest, so shields (only raised under threat) do not carry the ground
    rhythm. Acceptance is the `warship-shields` scene. Standing rule lives in
    `air-nouns.md`. Still provisional: the half scale, the three-second hold and
    90% drop level, and the absence of hard flux and damage-type efficiency.

## Owner direction: overpowered by design

These ships are not balanced in battle. They are gated in the world by
role-play context (diplomacy with the local government, the fleet being busy with
hostile fleets) and later by the player choosing compatible fleet ships for
compatible missions. In-battle limits are rhythm and legibility, never a damage
nerf, and a command-power commitment is not wanted until general ship support
exists.

## Open

- **Readiness (combat readiness)** is resolved by ship support writeback
  (`ship-support.md`, slice 2): a called committed ship pays its hull's own CR-to-deploy
  at settlement, and a ship destroyed in battle comes back crippled at CR zero. The
  world-level gating above still does the gating; readiness is the fleet-level price.
- **Shield refinements.** Hard flux, damage-type multipliers on efficiency, rounds
  stopped at the bubble rather than the hull, and per-hull shield colours.

## Fitting from a fleet member (shipped) and the flagship hand-off

Warship.fit(FleetMemberAPI, ...) arms a ship from the member's own fitted
variant through the WarshipLoadout seam (stock variant file or variant API
adapter; hull is the base hull, built-ins fill unnamed slots). The campaign
supplies a flagship at the **mission launch seam**: when the player's fleet is
committed to a ground battle, the launch code that builds the battle world picks
the player's flagship member (FleetDataAPI.getFlagship / the member a command
power names) and hands it to Warship.fit. That choice, the command-power
charge and the commitment cost stay out of scope here; nothing calls the member
overload from the campaign yet.

## Acceptance

- A `BehaviorScene` with an Onslaught over a generated compound: its barrage
  razes walls and roofs, kills units in the splash, and spares dispersed units
  outside it. A control loop with the ship absent.
- Friendly fire is demonstrated in a loop with own marines in the arc.
- `createSnapshots` suite renders the sweep at 1920x1080.
- Headless hull size is measured (`HullFootprintResolver.isMeasured`).

## Out of scope

Modules (the Onslaught's armour modules included),
ship-to-ship combat, campaign commitment through a command power, and every
hull other than the Onslaught and, afterwards, the Paragon.

## Debug flow: seeing the ship in a real mission

Gated by `DevConfig.DEBUG_WARSHIP_PICKER`; production play is untouched when it
is off or the hull is Off. It exists so damage and area of effect can be judged
against real units, which the headless scenes cannot show.

1. Planet -> a DEBUG client mission -> briefing -> open the debug drawer.
2. Use the three **WARSHIP DEBUG** rows: hull (Off / Onslaught / Paragon), side
   (Marine = friendly to the player, Defender = hostile) and mode (Director /
   Manual). The side and mode rows appear once a hull is picked. The choice
   survives a mission switch, like the air picker, and is ignored for
   non-debug missions.
3. Deploy. `MissionLaunch.finishSimulation` installs the ship through
   `DebugWarship.install`: centre of that faction's map edge (marines' landing
   edge, or the defenders' opposite one), inset by half the hull's length,
   facing the battle, with a station 20 cells inward. The edge comes from the
   map's traversal axis when the sim knows it, else the longer map dimension.
   Director mode runs `direct(10, true)`; Manual holds fire.
4. In battle `WarshipDebugPanel` shows a text line (hull %, order, heading,
   rounds, lances, point-defence kills). **F6** orders `FireOnArea` (radius 10,
   guns brought to bear) on the cell under the cursor and takes targeting from
   the director; **F7** holds fire.

Not yet exercised in the live game: the briefing rows, the HUD line placement
and the key/mouse handling were verified by compile and headless tests only.
