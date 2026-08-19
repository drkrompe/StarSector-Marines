# Mech roster — next session

## State of play

The modular hardpoint substrate and first specialist family shipped in
`2d3f044b`. Production defender integration shipped in `1ef74f23`: LOW fields
no mechs, MEDIUM introduces one Bulwark, and HIGH uses deterministic
mission-scaled Bulwark/Hound/Sirocco groups without increasing old mech body
counts. The player's Mech Support power remains Bulwark-only.

The heavy-cannon FX pass shipped in `39aefccb`. Sirocco's backup gun now fires
a larger visible Hellbore-style ballistic shell with a true 1-cell timed HE
blast, modest wall damage, a forward-offset muzzle flash/fire/smoke burst, a
larger dynamic light, and a cannon-specific impact made from vanilla explosion
frames plus the vanilla shock ring, existing fire/smoke particles, a heavier
crater, and positional explosion audio. The Heavy Mortar turret shares that
visual language with a 1.35-cell blast; ordinary rockets retain their softer HE
recipe. Standalone battles and the vanilla-combat bridge use the same profile.

Moving-fire target control now uses a 145-degree torso traverse to either side
of the hip heading. Every mech combat posture shares a traverse-aware target
refresh: visible threats inside 10 cells pull aggro from distant contacts, and
an out-of-arc target yields to an immediately shootable in-arc enemy. A close
target already being tracked remains sticky to prevent per-tick target churn.
The remaining 70-degree rear wedge still requires the chassis to turn.

The battle debug panel now exposes **Spawn mech family**, which creates:

- Bulwark: unchanged heavy, with SRM-15 and LRM-15 racks;
- Hound: fast nose-chaingun/single-SRM-5 breacher with no LRM;
- Sirocco: fragile heavy-cannon/paired-LRM-5 support with no SRM.

Hound now uses the narrow pointed hull and Sirocco the broader wedge, both
flipped to face their intended direction. Hound's one SRM and Bulwark's heavy
racks are visible above their chassis layers; Sirocco's paired LRMs tuck under
its hull. Both light chassis expose their two feet beside the mid-body so their
gaits are readable. Their generated thigh linkages rotate and stretch from the
waist toward each independently animated pad, with a longer light-mech stride
making the mechanism legible in motion. Bulwark's chainguns remain horizontally
compressed by half.

A reproducible static contact sheet is available at
[`previews/layered-mech-variants.png`](previews/layered-mech-variants.png). It
uses the runtime layer order and anchors, showing both gameplay-relative sizes
and a normalized 208-pixel comparison. Regenerate it with
`mod/graphics/battle/mech-modular-topdown/render_variants.py` after sprite work.

Needle, a fast unpodded scout, is documented but deferred until it can ship with
real recon/spotting behavior.

## First action

Run representative MEDIUM and HIGH production battles, then use **Spawn mech
family** for direct comparison. Confirm mixed defender groups remain readable
while moving and turning; then compare
range behavior, time-to-kill, missile density, and whether Sirocco's heavy
cannon remains an anti-armor fallback rather than a second primary role. Also
check that its shell remains visible at normal zoom, the muzzle/impact reads as
a gun rather than a missile, and the 1-cell splash does not erase Sirocco's
close-range weakness.

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
7. Shared gun-launched heavy-HE profile for Sirocco and the Heavy Mortar:
   visible ballistic shells, timed splash/structure damage, cannon muzzle and
   impact particles, vanilla explosion/ring sprites, decals, lighting, and
   positional audio (`39aefccb`).
8. Deterministic difficulty-scaled defender profiles and the Hound's
   faction-neutral ASSAULT doctrine (`1ef74f23`).

Tune profile/component numbers from the production encounters; mixed-group
adoption itself is complete.

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
- `DefenderRoster.mechVariants` is the immutable deterministic production
  composition; `mechCount` is derived from its size.
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
- Do not turn gun-launched HE into a boost-ramping missile entity. Its visual
  body is the resolved ballistic `ShotEvent`; the timed detonation owns splash.
- Do not make target selection ignore planted-hip traverse again. Movement and
  firing must agree on which contacts the upper chassis can physically reach.
- Bulwark's rockets are retained for comparison; removing them for a pure
  frontline-tank identity remains an explicit playtest decision.
