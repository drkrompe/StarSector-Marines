# Mech locational damage

Status: PLANNED — design draft; open questions below need an owner answer before
slice 2.
Written: 2026-10-16

Models: `mechs-nouns.md`, `combat-durability-nouns.md`, `body-part-cover.md`
(marine body parts, cover per part, wounds). Prior art for sectioned machines:
`warship-hull-damage.md`.

## Outcome

A mech is hit somewhere, not just hit. Each round lands on a **section** — legs,
torso, an arm mount, a shoulder mount, the head — and that section has its own
armor and structure. Losing a section costs a capability (a weapon, speed,
sight) before it costs the machine, so a mech fight reads as a mech being taken
apart, and a fitting choice (where the plating goes) matters.

## The model

- **A body plan, not a second hit system.** Marines already roll a part from the
  height the round arrives at (`BodyPart.atHeight`), let cover catch it by part
  (`CoverProfile`), scale damage by part and apply part consequences (wounds).
  Generalise that into a body plan a unit type declares: its parts, their height
  bands and shares, damage multipliers, cover rows and consequences. Infantry
  keeps today's plan unchanged; mechs get their own. Vehicles can take one later
  (hull, running gear, turret).
- **Mech sections by height band:** legs (bottom), torso (middle; a lateral share
  of torso-height hits goes to the arm mount), shoulder mounts (upper; left or
  right by the round's side), head (top sliver). Shares are authored per chassis,
  so a Hound's nose gun sits differently from a Bulwark's arms.
- **Per-section pools.** The chassis's armor capacity and structure are divided
  across sections, so the totals stay what the stock frame and plating set and
  time-to-kill stays comparable. Armor rating stays per chassis.
  `DurabilityModel` resolves each section exactly as it resolves a body today.
  Plating fittings (`mech-internal-systems.md`) may later shift capacity between
  sections, which is the fitting decision this enables.
- **Consequences:**
  - Legs at zero: crippled. The mech cannot walk but can pivot, turn its torso and
    fire. Damage below zero there spills into the torso.
  - A mount section at zero: that weapon is destroyed and goes silent (an absent
    mount is already a first-class state). Spill goes to the torso.
  - Head at zero: sensors are lost, so vision and accuracy drop. Spill goes to the
    torso.
  - Torso at zero: the mech is destroyed, the only way it dies.
  - Damaged but standing: armor stripped from a section is visible and lowers that
    section's protection, nothing more. There are no hidden stat penalties above zero.
- **Cover by section.** A mech's own cover rows let a wall or berm hide its legs
  (hull-down) while shoulders stay exposed, the counterpart of a marine in a
  trench.
- **Presentation:** a section paper-doll in the direct-control HUD and the
  selection panel; a destroyed mount drops its layer from the layered appearance;
  a crippled mech smokes at the legs.

## Slices

1. **Body plan refactor**, behaviour-preserving for infantry: plan-driven parts,
   shares, multipliers and cover rows replace the closed `BodyPart` enum and the
   ordinal-indexed tables. Existing infantry tests and `body-part-cover`
   evidence are unchanged.
2. **Mech plan and section pools:** rolls, per-section durability, spill to
   torso, torso-only death. Mount destruction and leg crippling. HUD paper-doll.
3. **Cover rows, presentation and AI.** Mech cover by section, destroyed-mount
   visuals, and doctrine that accounts for a crippled or disarmed mech (fall
   back, become a turret, withdraw).
4. **Campaign consequences** (needs the answer below): what a damaged or
   destroyed section costs after the battle.

## Open questions

- **After the battle:** does a destroyed mount lose its assembly from stores
  (a real loss, feeding the fabrication economy), or come back damaged and
  repairable for materials? Does section damage carry over as repair time like
  boats (`boat-condition-and-repairs.md`)? The recommendation is that a destroyed
  mount loses its assembly, everything else is repaired for materials and time,
  and a destroyed torso loses the chassis.
- **Can the player aim at a section** in direct control (aim height), or is the
  roll always the ballistic height? The recommendation is to keep the roll only;
  aimed sections are a later, separate feature.
- **Should legs-only damage ever kill?** The recommendation is no. Crippled is a
  state the enemy has to finish through the torso.

## Acceptance

- Unit (each under one second): plan band selection, section resolution and
  spill, torso-only death, mount destruction silences the mount, crippled
  movement.
- Infantry outcomes are unchanged across the refactor: existing body-part tests
  green, and a `loadoutMatchup` control run before and after compared.
- A scene: a Hound under rifle and rocket fire loses its SRM pod, then its legs,
  and fights on crippled until a torso kill, with frames at 1920x1080 / 100%.
- Time-to-kill read against the pre-change control in a short `commanderEvidence`
  run with a mech, as a reading rather than an assertion.
