# Ruins pressure: the site stirs

Status: IN PROGRESS — slices 1 to 4 built: the stir, its wake points, its
pushes fielded per occupant and its cues (comms line, Delve row). Open: the
owner's pick `ruins-stir-row` (built on a), and a step sting. The owner picked `ruins-pressure-push` (a);
`ruins-pressure-clock` (a) and `ruins-pressure-reserve` (a) are built on their
recommended options while open.

Written: 2026-11-09

Read `ruins-expedition.md` (Where phase B's threats come from, Pressing on or
pulling out, Occupants, Works) first.

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
  noise. If the company has held still for about 45 seconds (the middle of its
  forward element, the marines in its deepest layer, within eight cells, so
  squads still landing behind never count), it is **dug in**, and the next push comes up in the layer
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

- **The Delve row** (pick `ruins-stir-row`, built on its recommended a while
  open): the level leads the row's short line ("Site roused / 12 in a circle
  / ..."), so a narrow rail cuts the depth before the level; the row's bar
  fills toward the next push; the whole reading ends with the level and the
  reserve as a band (untouched, thinned, spent).
- **Dug in.** When the company counts as dug in, the warning ("Dug in: the
  site is going round you") takes the short line and the row's blocker, and
  the way back reads "contested" while a push holds the stand behind.
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
2. **Wake points.** *Built* (`RuinsWorks.WakePoint`). Each layer inside gets
   one on the floor of its first side room past its stores, named for who
   comes up through it: a service hatch for derelict security, a crawlway for
   the scavengers, a bore mound for the Rav. A push comes up at its layer's
   wake point while it stands, and on the layer's open ground where there is
   none (`RuinsPushes.wakeCell`). Each wake point is a rail marker and offers
   the works' force prompt; a company charge going off over it collapses it
   for good (`RuinsWorks.collapses`). A collapsed layer sends nothing: a
   way-back push into it hunts from ahead instead, and a hunt comes up in the
   next open layer deeper in. The vault's layer, where the reserve is kept,
   is never shut. So a company can spend charges to close its way back.
3. **Fielding a push, per occupant.** *Built* (`RuinsPushes`, raised by
   `BattleSetup.fieldRuinsPushes`). Derelict security: a line frame per squad,
   each its own lance, commanded by the push and not by the network director,
   and tended by no repair drone (`DerelictSecurity.raisePush`). Scavengers: a
   patrol-sized crew squad per squad, called up out of the crew's own mix,
   commanded by the push and not by the crew commander. Rav: each squad wakes
   the nearest closed bore to the wake cell, or surges the nearest open one
   with none closed, and the swarm director commands what comes out
   (`RuinsSleepers.push`); time below no longer stirs the bores, since the
   site's stir keeps it (`RuinsSleepers.foldTimeIntoStir`). A hunt walks at
   the company's last noise (where it last fired; where it stands before its
   first shot) and keeps after it; a way-back push walks to the stand of the
   layer behind (its junction's) and holds it until killed. The breach's push
   hunts from the vault's layer.
4. **The row and the cues.** *Built*: each push's comms line
   (`RuinsPressureCommsPresenter`) and the Delve row's stir reading
   (`RuinsDelveRow.Stir`, read by `RuinsDelveWatch.stir`): the level leading
   the short line, the bar toward the next push, the dug-in warning, the way
   back contested, and the reserve band in the whole reading. Look shots:
   `ruins-push-cue-look`; picks: `ruins-stir-row-picks`. Still to build: a
   step sting per occupant.

## Seams

- The mini-boss and vault sessions own guardians and the vault; a guardian is
  never part of the reserve, and the breach's push is separate from a vault
  guardian. A hive's relaunches are the hive's own cadence: a push is never a
  flock and never relaunches one.
- The old colony's batteries (`RuinsBatteries`) fire on anyone, pushes
  included. Their waking adds no stir of its own: the hall restart that wakes
  them already lifts the stir by a third of a step
  (`RuinsPressure.HALL_RESTART`), and the guns are their own cost, on the way
  out. The stir stays the company's own noise.
- **Stakes.** A vault prize's stake (`RuinsVaultPrize.stakes`, 0 to 3) holds
  one more squad in the reserve per stake and stirs the site 15% faster per
  stake (`RuinsPressure.of(grade, stakes, delve)`, UNTUNED). The prize does not
  ride the mission yet, so battles pass stake 0. It should be drawn where the
  occupant is (`RuinsExpeditionMissionFactory.create`), ride as
  `Mission.ruinsPrize` and the fixture's `prize`, and reach
  `BattleSetup.createRuins` beside the occupant, which hands
  `prize.stakes()` to the stir.
- `ruins-expedition.md` gains a short Pressure paragraph under Where phase B's
  threats come from when slice 3 lands.

## Acceptance

Unit tests prove the rules where they are decided. Whether the numbers make
phase B tense without being unwinnable is the owner's playtest; no session runs
a mission to judge it.
