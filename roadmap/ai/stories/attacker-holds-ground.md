Status: IN PROGRESS — attacker hold built on the shared assignment tier, `attacker-holds-ground` scene green; trial-ground comparison next

Written: 2026-10-20

# Attackers hold what they take

## Intent

Owner decision 2026-10-20 (`garrisonables.md`, Decided direction): the attacking
commander uses the same reactive assignment the defender uses to man a taken
fort belt against the counter-attack. Where a choice is open, prefer the one
that adds fun: a fort the company bled for should not be handed back to the
first patrol that walks up to it, and the player should see marines drop into
the trench they just cleared and turn it round.

## Scope

- **Who.** The AI attacking commander (`ConquestCommand`, the Conquest company
  commander). Only squads it may plan for — `MISSION_COMMAND` and below — are
  ever touched, so a player's squads, a born compound garrison and anything a
  higher authority holds are never sent. The player's own opt-in is a separate
  story.
- **Where.** The fighting-position runs of a fort this side **holds**: a fort it
  has disclosed with no aspect held by a hostile side. On the attacker that is
  exactly a fort it has taken, the same test that dissolves a company once
  nothing of its fort is left to take. Runs belonging to no fort, and building
  envelopes, wait for the same kinds to reach the defender's tier.
- **How.** The defender's assignment tier, unchanged in its laws, through a
  side-neutral caller (`HeldGround`): belief only, hysteresis with a dwell,
  pickets before massing, the useful-strength floor, an arrival bound, fall
  back to the fort's works on a breach or loss of strength, release when the
  pressure passes, spatial candidate lookup by the registry's block index, a
  bounded number sent per pulse.

## Momentum: what may be diverted, and when

The attack keeps its momentum by spending only the lane pool on holding, never
the work that takes ground:

1. **Never in convergence.** In keep convergence and final-compound
   convergence every holder is released to the last push and nobody is sent.
2. **Never a company, a capture or a group task.** Squads a company holds,
   squads committed to a compound capture this pulse, squads of a named
   officer's group, crews, machines, demolition squads and a squad given back
   this pulse are not candidates.
   Manning runs after the capture pass and before the lane pass, so it draws
   only on what would otherwise have been lane probes.
3. **A share of the pool.** At most a quarter of the lane pool (holders
   included) holds at once, at least one when the pool has three squads or
   more, none below that; at most two are sent per pulse. The director's own
   reserve floor still leaves a free squad unsent.
4. **Locals only.** The arrival bound (a forty-second march) means only a squad
   still near the taken works is sent; a squad that has moved on up the lane is
   not recalled.
5. **A squad in a fight is sent, and not released.** The squad already
   fighting the counter-attack is the one beside the trench, and taking the
   trench is how it fights it; once holding, it is not given back mid-fight.
   A dug-in holder outranks a muster: kept holders are taken out of the pool
   before the muster recruits.

## What counts as pressure

A counter-attack is a **believed mover**. Only a hostile contact that is not
an emplacement wakes a place, and a place is priced only on the blocks a
believed mover is within the registry's reach of. Found on the scene's first
run: the nests of a taken fort that the assault left standing are believed
hostile emplacements beside its belt, and read as pressure they held the belt
for the rest of the battle against an enemy that never came. A holder driven
off a run the enemy is believed inside (the squad's own contact reaction pulls
it out of the trench) falls back like one still standing on it.

## Acceptance

- Behaviour scene `attacker-holds-ground` (mechanics, not outcomes): a
  production battery fort taken by the attackers, the attacker's Conquest
  commander, a lane pool of marine squads in the works.
  - `quiet`: no counter-attack believed; nothing is manned.
  - `counter-attack`: tough defenders appear outside the belt and later leave.
    The pressed stretch is manned by an attacker squad within a few pulses of
    the attacker believing the pressure; the squad digs in; it holds while
    pressed; it is given back after the pressure passes; the pool is never
    drawn below its share.
  - `convergence` (or equivalent): with the keep the last compound, nothing is
    manned.
- Trial ground: the default matrix (both placements, three seeds and the
  rifles control) with the hold on, against a same-build control with
  `-Dbattle.conquest.attackerHoldsGround=false`. Readings, not acceptance:
  winner, keep and objective ticks, losses, fort holder changes, and how often
  and how long attacker squads held a belt.
- New unit tests under one second; no change to the defender's behaviour (the
  `reactive-belt` scene stays green).

## Plan

1. `GarrisonDirector.assign` gains a per-pulse budget overload; everything else
   in the tier is untouched.
2. `HeldGround`: the side-neutral caller, holding one `GarrisonDirector` per
   commander, the held-fort test, the stand/release/fall-back step and the
   bounded manning step.
3. `ConquestCommand` wiring and three assignment reasons
   (`HOLD_GROUND_MAN`, `HOLD_GROUND_STAND`, `HOLD_GROUND_FALL_BACK`), with a
   control property.
4. Scene, trial comparison, docs fold.
