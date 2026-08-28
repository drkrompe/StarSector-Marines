# Defender integral systems

> The player recovers a pirate foundry-breaker and it charges through doorways.
> The pirates it was taken from stand there and walk.

Status: PLANNED — deliberately deferred until the player-side model proved out.
Written: 2026-08-28

Read `progression-nouns.md`, `integral-armor-systems.md`, and
`powered-assault-armor-roles.md` before implementing. Depends on
`integral-system-use-policy.md`.

## Problem

`InfantryLoadoutRolls` builds defender loadouts from the same armour catalog the
player draws from, and does not thread a pattern's integral system through. A
defender wearing a foundry-breaker has its capacity, its rating, its terrible
speed, and none of the thing that makes the rig worth wearing.

That was the right call while the player-side model was unproven — a capability
that fires badly on twelve defenders is a much worse first impression than one
that does not fire at all. It stops being the right call now, for two reasons:

- **The asymmetry is visible and it favours the player.** It is now six patterns
  wide, not one. Every faction that fields a heavy breach suit — Hegemony XIV,
  League Bulwark, Tri-Tachyon Specter, Knightly Reliquary, Sindrian Lion's
  Mantle, and the pirate foundry-breaker — builds the capability and cannot use
  it. The pirate case is the sharpest, since the rig is named after the act, but
  a player who breaches a Hegemony position with a captured XIV that the Hegemony
  never braced will notice too.
- **It quietly weakens the equipment fantasy.** Recovering a suit is supposed to
  be taking something that worked. If it only works once you own it, the recovery
  is a stat transfer rather than a capture.

## Goal

Defenders carry the integral systems their armour patterns declare, through the
same data path the player uses.

## Standing rules

- **One data path.** A defender's system comes from the pattern, parsed once,
  with no defender-only tuning field and no separate defender catalog. If a
  defender's use of a system is too strong or too weak, the fix is the authored
  numbers or the policy, not a second set of numbers.
- **The policy has to be good first.** A defender spending a capability at an
  obviously wrong moment reads worse than one that never spends it, because a
  player cannot distinguish a bad decision from a broken one. This is why the
  use-policy story is a dependency rather than a nice-to-have.
- **Composition, not escalation.** Defender rosters already weight patterns by
  faction and defense level. Systems ride on that weighting; they do not become a
  new difficulty lever, and no roster should start reaching for system-carrying
  patterns to make a fight harder.

## Scope

- Threading the pattern's declared system through defender loadout rolls into
  the same spawn path the player side uses.
- Checking the roster weights that already exist. The foundry-breaker appears at
  25% weight in high-defense outlaw tables, so a hard pirate fight would suddenly
  contain several charging rigs, and the state factions weight their own heavies
  similarly. Whether that is exciting or absurd is a measurement, not a guess —
  and it now has to be taken six times, because the six versions differ most in
  how often they come around.
- Mission-harness evidence for a fight with system-carrying defenders on both
  the winning and losing side of it.

## Out of scope

- New defender patterns or roster weights beyond correcting what this exposes.
- Turret, mech, and vehicle carriers.
- Player-visible intelligence about a hostile suit's capability before contact,
  which is the open question below and deserves its own decision.

## Acceptance

- A defender in a system-carrying pattern spends it, on the same authored policy
  the player's marines use, with no defender-specific branch in the sweep.
- Measured outcomes for a fight against system-carrying defenders are reported
  before and after, and any resulting change is made in authored numbers rather
  than in a defender-only path.
- The player's own recovered suit behaves identically to the one it was taken
  from.

## Open questions

- Whether a hostile pattern's system is readable before contact. Reading it at a
  distance is powerful and makes the fight plannable; discovering it when a rig
  comes through a door is memorable and is more in keeping with how the rest of
  the equipment model treats recovery. This question was raised when the concept
  was written and deferred to exactly this story.
