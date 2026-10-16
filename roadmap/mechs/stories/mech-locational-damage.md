# Mech locational damage

Status: IN PROGRESS — slices 1 (body plan) and 2 (mech plan, section pools,
paper-doll) shipped; slice 3 (cover rows, presentation, AI) is next.
Written: 2026-10-16
Updated: 2026-10-16 — slice 2 shipped; its standing model is in `mechs-nouns.md`.

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
  Plating fittings (`mechs-nouns.md`, internal) may later shift capacity between
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

1. **Body plan refactor** (shipped 2026-10-16). `BodyPlan` replaces the closed
   `BodyPart` enum and the ordinal-indexed cover tables; a unit type declares
   its plan through `UnitType.bodyPlan()`, and the infantry plan reproduces the
   old numbers exactly. A `loadoutMatchup` control (open field and trench, two
   seeds, 276 fights) was identical before and after. The seam slice 2 builds
   on: a part's `consequence` (only `LIMB_WOUND` today; crippling, mount loss
   and sensor loss join it), separate band and part order, and the
   per-plan expected-interception readings. Combat telemetry's part tallies
   still count infantry parts only.
2. **Mech plan and section pools** (shipped 2026-10-16). Model folded into
   `mechs-nouns.md` (section, mech plan, section pools, section loss). Each
   chassis's `MechBodyPlan` gives its bands, arm share and pool shares; mech
   sections have no cover rows, so cover still meets a mech as one body. The
   battle keeps a `MechSections.Report` per mech for slice 4. The paper-doll is
   in the direct-control HUD and above the selected-mech panel
   (`direct-control-ui` snapshots). `-Dbattle.mech.locationalDamage=false` is
   the whole-body control.
   - Scene `mech-locational-damage` (`sceneEvidence`, `createSnapshots`): the
     recorded seed loses the pod at 0.9s, the legs at 8.2s, fires 18 rounds
     crippled without moving, and dies to a torso kill at 19.6s (control 20.8s).
     The full order is a roll: across 16 seeded plays every death is a torso
     kill and every crippled Hound stands, but pod-then-legs happens 10 times
     and the whole sequence 5.
   - Time-to-kill against the whole-body control (scene sweep): with a rocket
     tube, median 20.6s against 19.5s, but spread 4.5–55.6s against 15–21s;
     rifles only, median 42.9s against 47.4s. In `commanderEvidence`
     (conquest-trial offset/on-path, Corvus Dust Ledger and Blind Watch) no
     marine mech was worn down to a kill, so those runs give no time-to-kill;
     the Corvus Hound ended both chapters alive with structure whole in both arms.
   - **Open for slice 3:** structure a small section cannot hold goes straight
     into torso structure, skipping torso armor. That is the model as written,
     and it is what makes a heavy rocket swingy (a 162-damage round erases a
     small section and puts about 100 into the torso). Whether spill should meet
     torso armor first is a balance decision to take with the cover rows.
3. **Cover rows, presentation and AI.** Mech cover by section, destroyed-mount
   visuals, and doctrine that accounts for a crippled or disarmed mech (fall
   back, become a turret, withdraw).
4. **Campaign consequences**: what a damaged or destroyed section costs after
   the battle, as decided below.

## Decisions

- **After the battle** (owner, 2026-10-16): a destroyed mount loses its
  assembly from stores, a real loss that feeds the fabrication economy. Every
  other damaged section comes back repairable for materials and time, as boats
  do (`boat-condition-and-repairs.md`). A destroyed torso loses the chassis.
- **Aimed sections:** no. The roll is always the ballistic height; aiming at a
  section in direct control is a later, separate feature.
- **Legs-only kills:** no. Crippled is a state the enemy has to finish through
  the torso.

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
