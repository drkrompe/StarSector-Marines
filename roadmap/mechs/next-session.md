# Mech roster — next session

## State of play

The modular hardpoint substrate and first specialist family shipped in
`2d3f044b`. Production encounters and the player's Mech Support power still use
Bulwark exclusively.

The battle debug panel now exposes **Spawn mech family**, which creates:

- Bulwark: unchanged heavy, with SRM-15 and LRM-15 racks;
- Hound: fast chaingun/paired-SRM-5 breacher with no LRM;
- Sirocco: fragile linear-cannon/paired-LRM-5 support with no SRM.

Needle, a fast unpodded scout, is documented but deferred until it can ship with
real recon/spotting behavior.

## First action

Run an ordinary debug battle, expand the battle debug panel, and click **Spawn
mech family**. Compare silhouette readability, movement, range behavior,
time-to-kill, missile density, and whether Sirocco's linear cannons remain a
fallback rather than a second primary role.

## What shipped

1. Stable `MechVariant` chassis profiles without multiplying `UnitType`.
2. Generic swappable arm/left-shoulder/right-shoulder weapon components and
   per-mount live state.
3. `LINEAR_CANNON`, SRM/LRM -5 and -15 rack classes, profile/component-driven
   layered appearance, and component-aware resupply.
4. Profile stats and physical dimensions shared by rendering, picking,
   separation, ballistics, and AoE.
5. Deterministic in-battle family comparison plus focused tests; full build
   passed before integration.

After the comparison is accepted, tune the profile/component numbers and then
adopt the budgeted mixed-lance rules in S1.

## Relevant code seams

- `UnitType.HEAVY_MECH` remains the compatibility pre-spawn mech tag;
  `MechVariant` is persisted on identity and `UnitRosterService` resolves the
  entity's profile-aware physical values.
- `MechLoadoutComponent` owns an optional mount per physical slot; its public
  authored constructor is the spawn-time swapping seam.
- `MechWeaponComponent` owns rack class, representative packet, ammunition,
  compatibility, and art; `MechWeapon` owns projectile behavior.
- `World.attachMechLoadout` updates layered hardpoint selectors from the actual
  installed components, including custom authored loadouts.
- `DefenderRoster` exposes only a mech count; production integration will need
  a deterministic profile/budget representation.
- `MechSupportPayload` should deliberately remain heavy-only for this story.

## Known traps

- Do not render a light mech smaller while leaving heavy-sized picking,
  separation, hit, or AoE geometry underneath it.
- Do not encode doctrine into the variant enum; roles and hardware are separate.
- Do not use dummy weapons to represent empty mounts; absent bands must be real.
- Do not let light bodies increase encounter threat merely because their count
  is higher.
- Do not make the Sirocco's backup cannon strong enough to erase its close-range
  weakness.
