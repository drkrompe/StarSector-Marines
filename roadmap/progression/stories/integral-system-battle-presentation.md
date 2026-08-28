# Integral system battle presentation

> A marine suddenly moves half again as fast and nothing on screen changes. The
> player's only available reading is that something is wrong.

Status: PLANNED — depends on nothing; blocks the story's conveyance acceptance.
Written: 2026-08-28

Read `progression-nouns.md` and `integral-armor-systems.md` before implementing.
Coordinates with `s9-in-battle-quality-conveyance.md`, which owns person-driven
battlefield signal, and `s7-grade-visual-identity.md`, which owns equipment
signal.

## Problem

An integral system is invisible while it runs. The breacher assist changes a
suit's movement by 45% for three seconds and the compositor draws exactly what
it drew before. Three things go wrong at once:

1. **The player cannot attribute the change.** A rig that was the slowest thing
   on the field is briefly faster than an unarmoured marine, with no cause on
   screen. Unexplained speed reads as a physics bug, not a capability.
2. **The clock is unreadable.** The whole design of the effect is that it is a
   window someone has to cover — but nobody can see the window closing.
3. **The moment is the point.** A breacher going through a doorway is the thing
   the entire concept was written for, and it currently has no image.

## Goal

A running integral system is legible from the battlefield: the wearer looks
different while it runs, and the difference ends when it does.

## Standing rules

- **Presentation stays downstream.** Appearance is authored component data
  written by presentation systems and read by the renderer. The simulation never
  reads it, and no activation decision may depend on render state.
- **Key on the capability, not the carrier.** A system-active treatment is a
  composable appearance capability that any carrier may declare, in the same
  shape the shipped render capabilities already use. It is not a branch on
  "is this a foundry-breaker".
- **Do not stack another overlay.** `progression-nouns.md` explicitly warns off
  redundant battlefield overlays. The first answer should be in-world — pose,
  the screen itself, the rams — rather than a status icon floating above a
  sprite, and an icon should have to earn its place against that.

## Scope

- An appearance capability describing "this actor's integral system is running",
  written by a presentation system from the live component state and cleared on
  expiry.
- A treatment for the breacher assist: the deployed screen is the readable part
  and is also the part the player needs to understand, because the arc is a
  rule they already have to play around (`combat-durability-nouns.md`).
  Orienting the drawn screen to the arc it actually protects is the whole
  requirement.
- Audio, positional, following the shipped mono/positional convention.
- Deterministic snapshot evidence in the existing catalog rather than a new
  per-domain task.

## Out of scope

- A campaign-side readout; the Armory already carries that.
- Cooldown UI for the player to plan around. These fire on an authored policy,
  not on a player click, so a cooldown readout is a solution to a problem nobody
  has yet.
- The shoulder pod's launch treatment, which belongs with the pod.

## Acceptance

- A marine with a running system is distinguishable from the same marine
  without one, in a still frame, with no overlay.
- The treatment appears on activation and is gone on the tick the effect
  expires — not a frame later, and not lingering on a corpse.
- The drawn screen faces the arc the simulation is actually resolving against.
- Snapshot evidence covers both states of the same pattern.

## Open questions

- Whether a defender's running system should read the same way as the player's.
  Symmetry is honest and makes an incoming breacher legible; it also hands the
  player free information about a capability `integral-armor-systems.md` has
  already flagged as possibly better discovered the hard way.
