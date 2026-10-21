# Brute charge

Status: IN PROGRESS — rules 1–6 built and scene-checked; presentation (rule 7) next

Written: 2026-10-21

Updated: 2026-10-21 — sim rules landed with the `brute-charge` scene; the four presentation cues remain.

Model: `colony-breach-nouns.md` (Swarm strain: Brute). Art and motion belong to
the Brute art pass (`art-source/rav-redesign/exploration/motion_brute.py`, the
approved motion study 3): the head-down gallop and the two-blow ram are already
drawn. This story owns the rule.

## Why

The owner, on the approved motion study: the Brute should both charge and ram —
"cool if it did both" — and "there are also mechs and vehicles that should be
considered". And: two speeds, one to walk and one to charge. Today the Brute only
walks up and strikes, at one speed, and breaks light walls.

## What the battle has today

- **One speed per unit.** `UnitType.SWARM_BRUTE` moves at 1.8 cells/s (Marine
  2.0, Runner 3.2), read through `MovementService.moveSpeed` and its
  `UnitModifier` chain. No timed per-unit speed change exists for AI units; a
  modifier that reads per-unit state is how one is added (as `WoundModifier`
  and `MechSectionModifier` do).
- **No knockback, stagger, stun or downed state.** A hit changes hp, armour,
  limb wounds and morale. The only displacement is `SeparationSystem`'s overlap
  push, clamped to 1.5 cells/s. The Runner's impale (`colony-breach-nouns.md`,
  Impale) keeps a downed state out, and so does this story.
- **The Brute's blow**: 14 damage, penetration 8, every 1.2 s at 1.7 cells;
  wall blows of 25 through `SwarmStrikeSystem` on walls of at most 120 left.
  Its morale impact (2.0) is the highest of any type.
- **Mechs** have sections; legs at zero cripple (move speed x0).
  `MechVariant.relativeMass` exists but only drives manual-drive inertia.
- **Vehicles** have no mass or weight field. Tanks have armour arcs
  (`ArmorArcs`); the rest are flat. Nothing rams or runs anything over; the
  dozer blade pushes only walls and barricades.

## Vocabulary

One new word, fitted to the Brute:

- **Charge.** A committed straight run at charge speed at one target, begun
  with a visible tell and ended by a hit, by a wall, or by running out. A
  charge is the Brute's, and only the Brute's.

"Ram" stays the existing blow on a structure. A charge that ends on a light
wall delivers a ram.

## Rules

### 1. Two speeds

The Brute **walks** at its sim speed, 1.8 cells/s (it trots at that pace, as
the motion study shows), and **charges** at 4.0 cells/s. The run is driven by
the charge itself as a speed scale over the Brute's modified move speed, so
wounds, creep and terrain still apply on top.

### 2. When a Brute charges

All of these, checked when it would otherwise press toward its target:

- the target is between 3 and 8 cells away (closer, it just strikes; further,
  it walks on);
- the straight line to it is open ground the Brute can cross (no wall, no
  building, no deep water; a light prop or a body in the way is allowed: it
  is what gets bowled);
- its charge is off cooldown (8 s from the end of the last one).

A Brute hunting a building's occupants keeps its breach behaviour; it charges
the light wall it means to break when the wall is in the band and the line
is open (rule 4).

### 3. The tell, the run, the end

- **Tell, 0.35 s:** the head drops and it stops. This is the player's warning
  and the art's head-down pose.
- **Run:** straight along the line it chose, turning at most 30 degrees a
  second, so a target that sidesteps early is missed. Feet are the gallop.
- **End:** on its first hit that stops it (rule 4), on a wall it cannot break,
  or after 10 cells. Then it pulls up over half a second and resumes walking.
  A charge that ends on nothing leaves it no faster to strike: its blow
  cooldown runs as usual.

### 4. What a charge does to what it hits

The charge's hit is the Brute's blow, heavier: damage x1.5 (21) at its
penetration 8, through the ordinary damage path, with the hit-side armour arc
where the target has arcs. What follows depends on what was hit:

| Target | Effect | Does the charge go on? |
| --- | --- | --- |
| Infantry (Marine, militia, colonist) | **Thrown** 1.2 cells to the side away from the Brute's line, landing where open ground allows; **knocked down** 1.0 s (no move, no fire). | **Yes**, slowed by a quarter, through at most two bodies; a third stops it. |
| Light cover prop (fence, crate, sandbag) | Broken outright. | Yes, slowed by a quarter. |
| Light wall or window (at most 120 left) | One ram blow at double weight (50). | No: it rams on from a standstill (the existing breach). |
| Heavy wall, fort wall | Nothing. The Brute is **stunned** 1.0 s. | No. |
| Emplacement | The heavy blow. | No. |
| Light vehicle (technical, scout car, hover scout, supply and cargo trucks, drone skimmer) | Shoved 0.6 cells along the charge and **stalled** 0.8 s (no move). Side and rear hits land on its flat plate. | No. |
| APC (every APC line, LRM and SRM carriers, the dozer) | Stalled 0.4 s. | No. The Brute stops against it. |
| Tank | The tank's arc decides the damage; its front shrugs most of it off. | No. The Brute is **stunned** 1.0 s. |
| Mech | The hit is aimed at the **legs** (the section that cripples). A light or medium mech is **staggered** 0.6 s (no move, no turn); a heavy one is not. | No. |

Vehicle weight is new: a **weight class** (light / medium / heavy) on
`VehicleType`, which this table needs and nothing else uses yet. Mechs get the
same three classes, read off `MechVariant.relativeMass`.

**Knocked down, stalled, staggered and stunned are one short state**: "can't
act" for a fixed time. It is not a downed casualty state: the body is upright
in the sim, takes hits as usual, and its morale is untouched beyond the hit's
own drain. The art shows a thrown Marine on the ground for that second.

### 5. Morale

A charge's hit drains the victim's fire team as the Brute's blow already does.
In addition every squad member within 2 cells of a thrown body takes a near-miss
drain — the fear of the thing going through the line. No new morale system.

### 6. How it is countered

- The tell is long enough to react to, and the run is straight: sidestep or
  get behind cover the Brute cannot break.
- A heavy wall or a tank front stops it dead and stuns it: bait.
- Its plate is unchanged by charging; DMR and rocket fire into a charging Brute
  is the answer, as it is to a walking one.

### 7. Showing it to the player

A second of not being able to act is confusing unless the player sees why. Four
cues, all presentation over the rule:

- **The tell is on the ground.** For the 0.35 s tell a faint amber wedge is
  drawn along the line the Brute will run, as long as the run can go, under
  the units. The head drops at the same moment. The player sees where it is
  going before it goes.
- **The thrown body lies down.** For the knocked-down second the body's sprite
  lies on its side where it landed (the pose a fallen body already uses),
  with a dust ring on landing, then gets up. It is upright in the sim
  throughout; only the picture lies down.
- **A daze mark.** A small amber ring circles above anything that can't act:
  a thrown Marine, a stalled vehicle, a staggered mech, and the Brute itself
  when it is stunned. The same mark everywhere means the same thing.
- **The squad panel says so.** A knocked-down Marine's card reads "Knocked
  down" for the second, as a wounded one reads its wound.

## Owner decisions (2026-10-21)

1. Infantry: thrown and knocked down 1 s — "sounds fun". Shown as rule 7.
2. Through-going: up to two bodies (owner: "do whatever feels right").
3. Mechs: light and medium are staggered; heavy are not.
4. Vehicles: the three weight classes as written.
5. Numbers (speed 4.0, band 3–8, cooldown 8 s, tell 0.35 s, the effect times)
   are starting values: "we haven't felt any of this out yet". Each is a
   `-Dbattle.brute.*` switch, so a feel pass is a command line, not an edit.

## Acceptance

Mechanics, asserted by a behaviour scene (`sceneEvidence`, a new
`brute-charge` scene); outcomes stay readings in its metrics:

- A Brute charges only with a target in band, a clear line and its cooldown
  spent; with any one missing, it walks.
- The tell precedes every run, and the run holds its line within the turn
  limit.
- Each row of the table: a scripted target of that kind receives that row's
  effect, and the charge goes on or stops as the row says.
- A light wall charged takes one double-weight blow; a heavy wall leaves the
  Brute stunned and the wall untouched.
- Nothing on the swarm's own side is thrown, shoved or hit.

Unit tests (each under a second) for the decision (band, line, cooldown), the
throw landing cell, and the outcome table's lookup by target kind.

## Plan

1. Charge state on the Brute (phase: tell, run, pull-up; line; hits; cooldown)
   and the move-speed modifier. `SwarmBruteBehavior` decides and steers.
2. The "can't act" state and its modifier (move x0, fire held) for infantry,
   vehicles, mechs and the Brute itself.
3. The outcome table: throw, shove, stall, stagger, stun, the double-weight
   ram, prop breaking. `VehicleType` weight class.
4. Morale near-miss on throws.
5. The `brute-charge` scene.
6. Presentation (rule 7): the ground wedge, the daze mark, the lying pose,
   the squad-card line. The Brute's own head-down and gallop art lands with
   the Brute production pass.
