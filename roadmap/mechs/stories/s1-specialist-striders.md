# S1 — Specialist striders

**Status:** Slice A shipped (`2d3f044b`, 2026-08-19). Manual comparison/tuning
and production-roster integration remain.

## Shipped slice A

The first vertical slice now exists behind **Spawn mech family** in the battle
debug panel. It deterministically places Bulwark, Hound, and Sirocco together
using the current battle's center-nearest unoccupied walkable cells.

The implementation landed:

- physical `ARMS`, `LEFT_SHOULDER`, and `RIGHT_SHOULDER` hardpoints;
- generic installed-component state with independent cooldown, representative
  burst/salvo, target lock, ammunition, and resupply behavior per mount;
- dual/nose chaingun, dual linear-cannon, and single heavy-cannon arm components;
- SRM-5, SRM-15, LRM-5, and LRM-15 shoulder components;
- persistent Bulwark, Hound, and Sirocco chassis profiles with profile-driven
  health, speed, accuracy, vision, render scale, morale weight, radius, hit
  height, layered chassis, arms, and pod selectors;
- profile-aware picking, separation, ballistic contact, blast contact, live
  rendering, and corpse rendering;
- generic firing/continuation, torso aim, animation, and resupply paths that
  operate only on installed mounts;
- focused variant/component/debug-fixture regressions plus the full build.

The launcher number is a MechWarrior-style rack/readability class, not a demand
to simulate every physical tube as a projectile. Small -5 racks emit a two-shot
representative packet. Bulwark's -15 racks preserve the exact old four-SRM and
five-LRM packets and ammunition capacities, avoiding a stealth balance change.

Hound now uses one dorsal SRM-5; Sirocco uses paired LRM-5 shoulders. A
public authored-loadout constructor can independently replace the arms and
either shoulder component, and appearance follows the installed hardware.

Production defender allocation and the player's Mech Support power deliberately
remain Bulwark-only until the comparison is manually accepted.

## Player-facing outcome

A defender mech contact no longer always means the same enormous all-range
machine. The player can read three distinct threats at gameplay zoom:

- the heavy Bulwark anchors a position and remains dangerous at every range;
- the Hound runs down weak ground and breaches dense compounds with close
  weapons, but can be engaged from outside its reach;
- the Sirocco shapes the fight with indirect missiles, but becomes a rescue
  problem for its allies when marines close on it.

The result should be counterplay and target-priority decisions, not a linear
enemy power increase.

## Proposed roster contract

The following values are tuning seeds, not final balance promises.

| Profile | HP | Speed | Render scale | Body radius | Vision | Weapons | Default doctrine |
| --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| Bulwark | 540 | 1.15 | 1.60 | 0.60 | 55 | Chaingun, SRM, LRM | Either current role |
| Hound | 300 | 1.70 | 1.35 | 0.50 | 50 | Nose chaingun, one SRM-5 | `ARMORED_SUPPORT` initially |
| Sirocco | 230 | 1.45 | 1.35 | 0.48 | 55 | Heavy cannon, paired LRM-5 | `LR_SUPPORT` |

### Bulwark — heavy control

The current `HEAVY_MECH` behavior and numbers remain unchanged. It is the rare
apex chassis, the regression control for the new substrate, and the mech used
by the player's existing Mech Support command power.

### Hound — breach strider

The Hound uses a single centerline nose chaingun and one compact dorsal SRM-5. Higher speed lets it
cross a road, exploit a broken wall, or reinforce a contested compound before
the heavy could arrive. Its smaller health pool makes that commitment risky.
With no LRM track, open ground and disciplined standoff fire are real counters.

S1 assigns it `ARMORED_SUPPORT` so it can ship on proven planning behavior.
A later `ASSAULT` doctrine may make it more aggressive, but the hardware must
already work and remain legible without that behavior expansion.

### Sirocco — missile strider

The Sirocco uses a single heavy cannon as a modest anti-armor direct-fire
backup and carries paired compact LRM-5 shoulders. It has neither the heavy's health nor an SRM
panic button. Its `LR_SUPPORT` doctrine should keep it behind friendly bodies,
while flanking or overrunning it meaningfully shuts down its advantage.

Its `HEAVY_CANNON` fires one accurate kinetic shell for modest infantry damage
and triple damage against hardened targets. Its 26-cell band keeps the paired
LRMs primary at standoff range and avoids erasing the weakness created by
removing close missiles.

## Architecture decision

The shipped implementation does not add a `UnitType` for every hardpoint
combination. Its `MechVariant` profile/catalog owns:

- display/debug label;
- default doctrine;
- weapon definitions and ammunition for each available slot;
- layered appearance selectors for chassis, arms, and shoulders;
- health, movement, accuracy, and vision seeds;
- render scale, body radius, hit extent, and morale footprint.

`MechRole` remains the doctrine stored with the live mech loadout. The profile
is physical configuration. The two may have a recommended pairing without
being collapsed into one enum.

For the first slice, `UnitType.HEAVY_MECH` may remain the compatibility tag that
routes every strider through mech ECS construction. Its name is imperfect, but
renaming the pre-spawn capability and all consumers is unnecessary risk. The
variant profile must be the source of the live body's actual properties.

### Weapon tracks

`MechLoadoutComponent` now holds optional generic mounts for arms and both
shoulders. The arms can carry chainguns or linear cannons; either shoulder can
carry any compatible SRM/LRM component or remain empty. Firing, continuation,
AI utility, ammunition, debug, resupply, and appearance paths iterate only real
installed mounts rather than manufacturing dummy weapons.

The heavy's three existing tracks and cadence must be preserved exactly.

### Physical properties

Body properties previously came directly from `UnitType`. The shipped profile
identity now makes render scale and gameplay geometry variant-aware across:

- click/picking bounds;
- separation and avoidance radius;
- ballistic hit bounds;
- explosion and area-effect distance checks;
- wreck/impact placement where applicable.

The profile persists on the entity's identity across the corpse transition, and
`UnitRosterService` is the shared source for these physical values.

### Construction and appearance

`EntitySpec.mechVariant` applies profile values before spawn, while the profile
creates its loadout after spawn. Initial layered selectors come from the
profile, and loadout attachment reauthors arms/shoulders from the actual
installed components so custom authored configurations render correctly.

The first comparison used existing assets, but the shared socketed hull failed
the immediate-recognition criterion. The revised visual contract is:

- Bulwark: current clean chassis, exposed heavy pods, and half-width chainguns;
- Hound: flipped narrow pointed chassis, nose chaingun, one top-layer SRM-5,
  and no LRM pod;
- Sirocco: flipped broad wedge chassis, generated centerline heavy cannon,
  paired compact LRM treatment, and no SRM pod.

Rack layer order is part of each hull: Bulwark and Hound expose their racks
above the body, while Sirocco's pair remains tucked beneath its wedge.

Exact left/right pod symmetry can be chosen during the comparison-fixture pass.
The criterion is immediate visual recognition, not adherence to a fixed socket
diagram.

## Delivery slices

### A. Variant substrate and comparison fixture — shipped

1. Add the catalog, `LINEAR_CANNON`, optional weapon slots, and profile-driven
   appearance/body construction.
2. Keep every production spawn on Bulwark.
3. Add a deterministic debug battle/gallery that places Bulwark, Hound, and
   Sirocco together against the same targets and terrain.
4. Add automated contracts for profile values, missing slots, geometry source
   consistency, and unchanged heavy behavior.

This shipped in `2d3f044b` and produces a safe visual/combat comparison before
changing encounter composition.

### B. Tune battlefield identities

Playtest the fixture for silhouette readability, travel time, time-to-kill,
minimum/maximum useful range, and whether the Sirocco's backup gun is genuinely
defensive. Adjust the provisional numbers, but preserve each weakness.

### C. Budgeted defender integration

Replace the flat assumption that every `mechCount` entry is equivalent with a
small deterministic threat budget. Suggested starting costs are Bulwark 3,
Hound 2, Sirocco 2, and future Needle 1. Heavy-industry/high-risk generation
still gates mech availability and should retain a guaranteed Bulwark where the
current roster promises one.

Lighter variants replace part of the existing mech allocation; they do not
increase total bodies on top of it. Prefer complementary mixed lances over
unbounded random duplicate rolls. Record the chosen profile in roster/debug
output so a seed can be reproduced.

The player Mech Support payload remains Bulwark-only in S1.

## Automated verification

- Every profile has a stable unique id and complete physical/stat data.
- Bulwark still spawns with its exact current stats, three weapon tracks,
  ammunition, cadence, appearance, and accepted GOAP behavior.
- Hound has no LRM track; no planner or firing path can select or continue one.
- Sirocco has no SRM track and cannot substitute an SRM-like close salvo.
- Empty shoulders render empty and optional tracks never produce null failures.
- Profile render scale, picking, separation, ballistic hits, and AoE queries
  agree on the light body's dimensions.
- Appearance selection is deterministic from profile, not insertion order.
- Roster generation is seed-stable, respects its threat budget, and does not
  inflate the old encounter's maximum mech cost.
- The existing player command-power drop continues to produce the heavy.

## Manual acceptance

- At normal gameplay zoom, a player can distinguish all three before the first
  missile lands.
- Hound reaches and pressures a close objective noticeably faster than Bulwark,
  but dies substantially sooner and cannot answer a long-range contact.
- Sirocco tries to preserve range and creates useful indirect pressure, but a
  successful close approach feels like a decisive counter.
- Bulwark remains the most individually frightening and flexible machine.
- A mixed defender lance changes target priority without feeling strictly more
  lethal than the former all-heavy allocation.
- Smaller bodies do not exhibit mismatched clicks, invisible collisions,
  suspicious misses, or oversized blast interactions.

## Deferred

- Needle, `RECON`, target painting, and other information mechanics.
- An `ASSAULT` doctrine tuned specifically for Hound.
- Player variant selection, ownership, salvage, refit, and hardpoint UI.
- Procedural/custom hardpoint combinations.
- Additional animation sets or multi-cell bodies.
- Final balance values outside representative playtest encounters.

## Decisions to confirm

Before code starts, confirm the three working names and whether Hound/Sirocco
are the right first pair. Numeric seeds can move in the fixture; their missing
weapon bands and stated weaknesses are the durable design contract.
