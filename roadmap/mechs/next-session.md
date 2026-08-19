# Mech roster — next session

## State of play

The modular hardpoint substrate and first specialist family shipped in
`2d3f044b`. Production encounters and the player's Mech Support power still use
Bulwark exclusively.

The battle debug panel now exposes **Spawn mech family**, which creates:

- Bulwark: unchanged heavy, with SRM-15 and LRM-15 racks;
- Hound: fast nose-chaingun/single-SRM-5 breacher with no LRM;
- Sirocco: fragile heavy-cannon/paired-LRM-5 support with no SRM.

Hound now uses the narrow pointed hull and Sirocco the broader wedge, both
flipped to face their intended direction. Hound's one SRM and Bulwark's heavy
racks are visible above their chassis layers; Sirocco's paired LRMs tuck under
its hull. Bulwark's chainguns are horizontally compressed by half.

A reproducible static contact sheet is available at
[`previews/layered-mech-variants.png`](previews/layered-mech-variants.png). It
uses the runtime layer order and anchors, showing both gameplay-relative sizes
and a normalized 208-pixel comparison. Regenerate it with
`mod/graphics/battle/mech-modular-topdown/render_variants.py` after sprite work.

Needle, a fast unpodded scout, is documented but deferred until it can ship with
real recon/spotting behavior.

## First action

Review the revised static contact sheet, then run an ordinary debug battle,
expand the battle debug panel, and click **Spawn mech family**. Confirm the new
hulls and external racks remain readable while moving and turning; then compare
range behavior, time-to-kill, missile density, and whether Sirocco's heavy
cannon remains an anti-armor fallback rather than a second primary role.

## What shipped

1. Stable `MechVariant` chassis profiles without multiplying `UnitType`.
2. Generic swappable arm/left-shoulder/right-shoulder weapon components and
   per-mount live state.
3. `LINEAR_CANNON`, `HEAVY_CANNON`, SRM/LRM -5 and -15 rack classes, profile/component-driven
   layered appearance, and component-aware resupply.
4. Profile stats and physical dimensions shared by rendering, picking,
   separation, ballistics, and AoE.
5. Deterministic in-battle family comparison plus focused tests; full build
   passed before integration.
6. Dedicated Hound/Sirocco hull silhouettes, per-chassis rack layering, a
   centerline weapon mounting mode, and a generated heavy-cannon module.

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
- Bulwark's rockets are retained for comparison; removing them for a pure
  frontline-tank identity remains an explicit playtest decision.
