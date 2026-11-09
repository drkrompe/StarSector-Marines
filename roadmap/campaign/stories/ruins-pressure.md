# Ruins pressure: the site stirs

Status: PLANNED — slice 1 (the stir, its steps, the reserve and the aim rule)
built and ticked on occupied sites; its pushes are recorded but not yet turned
into units. Built on the recommended options while the owner's picks
`ruins-pressure-clock` (a), `ruins-pressure-reserve` (a) and
`ruins-pressure-push` (a) are open.

Written: 2026-11-09

Read `ruins-expedition.md` (Where phase B's threats come from, Pressing on or
pulling out, Occupants) and `ruins-works.md` first.

## Goal

The owner: "I want us to look at constant pressure." Phase B, the delve, should
stay tense the whole time, and the company must not be able to sit in a cleared
layer and wait. The pressure feeds the owner's rule that "the player needs to
determine whether they still have the forces to continue": every minute inside
costs something, and staying still costs the way home.

Without it, phase B's threat is all ahead of the company and all reactive:
derelict zones wake on contact and settle, the scavengers' alarm answers
trouble, and only the Rav stir with time. A company that stops meets nothing
until it moves again.

## The model

- **The stir.** An occupied site has one stir for the whole delve. It rises
  with every second the company is inside (after the entry is taken), twice as
  fast while the company is firing, and at once with a loud act: the power hall
  restarting (the loudest work) or a breaching charge going off. It never
  falls. An empty site has no stir: its threat stays time and charges.
- **Levels.** Each step of stir is a level: quiet, stirring, roused, hunting.
  A quiet company meets the first step after about a minute and a half inside,
  a fighting one in half that.
- **The reserve.** The occupant holds a finite reserve deeper in, by grade (two
  squads at scattered up to seven at vast), apart from its posted strength.
  Each step sends a push out of it: one squad, then two from the hunting level.
  What is spent is gone, so a long stay costs now and thins the vault later
  (pick `ruins-pressure-reserve`). The vault's breach throws whatever is left
  in one push after the haul, beside the occupant's own contingency.
- **The aim.** A push comes up in the layer ahead and hunts the company's last
  noise. If the company has held still for about 45 seconds (its middle within
  eight cells), it is **dug in**, and the next push comes up in the layer
  behind it instead and sits on that layer's stand, across the way back.
  Nothing comes up behind the first layer inside: the entry stays the
  company's (`ruins-expedition.md`, The entrance is the company's).

Why the way back: a push that always walks at the company rewards a killing
ground, which is turtling by another name. A push that goes round a company
that sits makes waiting cost the walk home, which is exactly what the pull-out
decision weighs.

## Cues

Every rule above has a cue the player reads, none of which names a unit the
company has not seen:

- **The Delve row** reads the level beside the depth ("Layer 2 of 4 · Site
  roused") with a bar toward the next step, and the reserve as a band in the
  close reading (untouched, thinned, spent).
- **Dug in.** When the company counts as dug in, the row says so ("Dug in: the
  site is going round you") in the alert colour, and the way-back figure turns
  to "contested" once a push sits on it.
- **A step.** Each step plays the occupant's sting and a comms line in its
  voice: derelict relays chiming down the halls and "Power draw rising
  below"; the scavengers' radio and "They're calling the dig crew up"; the Rav
  as a seismic shudder and "Lifesigns rising, layer 3". A way-back push's line
  says where: "Movement behind you, layer 1".
- **Loud acts** flash the bar when they land, so the player learns that the
  hall and a charge cost stir.
- **Sightings** are ordinary: a push is on the map only once the company sees
  it.

## Slices

1. **The stir.** *Built* (`RuinsPressure`): the stir, its levels, the reserve,
   push sizes, the aim rule and stillness, unit-tested; installed on occupied
   ruins battles and ticked in the delve, hearing company gunfire, its charges
   going off and the hall restart. Pushes are recorded (`pushes()`), not yet
   fielded. Numbers UNTUNED.
2. **Wake points.** Where a push comes up: one per layer inside, in a side
   room that is not a store (a service hatch for derelict security, a crawlway
   for the scavengers, a bore mound for the Rav), published by the ruins
   ground. A charge collapses one, so a company can spend charges to close its
   way back.
3. **Fielding a push, per occupant.** Derelict security: a walker lance out of
   the vault zone's bay, under the network director as a reserve lance, free of
   any perimeter. Scavengers: crew squads called up from the dig, under the
   crew commander. Rav: a step surges the nearest closed bore, and the
   sleepers' own stir folds onto this one. A hunt walks to the company's last
   noise; a way-back push holds its stand until killed.
4. **The row and the cues.** The level, bar, reserve band and dug-in line on
   the Delve row; the step sting and comms lines per occupant; a look shot per
   level for review.

## Seams

- The mini-boss and vault sessions own guardians and the vault; a guardian is
  never part of the reserve, and the breach's push is separate from a vault
  warden.
- `ruins-expedition.md` gains a short Pressure paragraph under Where phase B's
  threats come from when slice 3 lands.

## Acceptance

Unit tests prove the rules where they are decided. Whether the numbers make
phase B tense without being unwinnable is the owner's playtest; no session runs
a mission to judge it.
