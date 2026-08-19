# S1 — Specialist striders

**Status:** Design ready; roster approval and implementation pending.

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
| Hound | ~300 | ~1.70 | ~1.35 | ~0.50 | ~50 | Chaingun, SRM | `ARMORED_SUPPORT` initially |
| Sirocco | ~230 | ~1.45 | ~1.35 | ~0.45–0.50 | ~55 | Linear cannon, LRM | `LR_SUPPORT` |

### Bulwark — heavy control

The current `HEAVY_MECH` behavior and numbers remain unchanged. It is the rare
apex chassis, the regression control for the new substrate, and the mech used
by the player's existing Mech Support command power.

### Hound — breach strider

The Hound uses chaingun arms and compact SRM shoulders. Higher speed lets it
cross a road, exploit a broken wall, or reinforce a contested compound before
the heavy could arrive. Its smaller health pool makes that commitment risky.
With no LRM track, open ground and disciplined standoff fire are real counters.

S1 assigns it `ARMORED_SUPPORT` so it can ship on proven planning behavior.
A later `ASSAULT` doctrine may make it more aggressive, but the hardware must
already work and remain legible without that behavior expansion.

### Sirocco — missile strider

The Sirocco uses the existing twin-linear-cannon arm art as a modest direct-fire
backup and carries LRM shoulders. It has neither the heavy's health nor an SRM
panic button. Its `LR_SUPPORT` doctrine should keep it behind friendly bodies,
while flanking or overrunning it meaningfully shuts down its advantage.

This story adds a `LINEAR_CANNON` weapon definition with restrained direct-fire
output. It should help the Sirocco disengage, not erase the weakness created by
removing close missiles.

## Architecture decision

Do not add a `UnitType` for every hardpoint combination. Introduce a
`MechVariant` profile/catalog whose stable identity owns:

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

`MechLoadoutComponent` currently requires one chaingun, one SRM, and one LRM
track. Generalize it so the direct-fire arm track can carry either chaingun or
linear cannon and the shoulder tracks can be absent. Every firing, continuation,
AI utility, ammunition, HUD/debug, and appearance path must treat an absent
slot as unavailable rather than manufacturing a dummy weapon.

The heavy's three existing tracks and cadence must be preserved exactly.

### Physical properties

Several body properties currently come directly from `UnitType`, even though
`EntitySpec` already supports many stat overrides. Before rendering a light
machine smaller, make render scale and all gameplay geometry variant-aware:

- click/picking bounds;
- separation and avoidance radius;
- ballistic hit bounds;
- explosion and area-effect distance checks;
- wreck/impact placement where applicable.

Whether these become explicit ECS columns or immutable spawned-body values is
an implementation choice. The contract is that visuals and physical behavior
use one profile-derived source of truth.

### Construction and appearance

Add one variant-aware creation seam that applies profile values before spawn,
attaches the corresponding loadout after spawn, and seeds
`LayeredMechAppearance` from the profile. Remove the current hardcoded clean
chassis/chaingun/heavy-SRM/LRM selection from the generic mech path.

Use existing assets for S1:

- Bulwark: current clean chassis and full heavy pods;
- Hound: socketed chassis, chaingun arms, compact SRM treatment, no LRM pod;
- Sirocco: socketed chassis, linear-cannon arms, LRM treatment, no SRM pod.

Exact left/right pod symmetry can be chosen during the comparison-fixture pass.
The criterion is immediate visual recognition, not adherence to a fixed socket
diagram.

## Delivery slices

### A. Variant substrate and comparison fixture

1. Add the catalog, `LINEAR_CANNON`, optional weapon slots, and profile-driven
   appearance/body construction.
2. Keep every production spawn on Bulwark.
3. Add a deterministic debug battle/gallery that places Bulwark, Hound, and
   Sirocco together against the same targets and terrain.
4. Add automated contracts for profile values, missing slots, geometry source
   consistency, and unchanged heavy behavior.

This produces a safe visual and combat comparison before changing encounter
composition.

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
- New raster assets, animation sets, or multi-cell bodies.
- Final balance values outside representative playtest encounters.

## Decisions to confirm

Before code starts, confirm the three working names and whether Hound/Sirocco
are the right first pair. Numeric seeds can move in the fixture; their missing
weapon bands and stated weaknesses are the durable design contract.
