# S2G — Martyr rigs and carried IEDs

Status: PLANNED — faction-specific defender equipment; depends on the shared
special-equipment and detonation seams.

Written: 2026-08-24

Read `progression-nouns.md`, `target-faction-ground-rosters.md`, `ai-nouns.md`,
and `combat-durability-nouns.md` before implementing this story.

## Problem

The Luddic Path currently differs through generic explosives and doctrine
prose, but it has no ground-equipment identity as frightening and self-costly
as a martyr rig. Pirates and Pathers likewise have no explicit carried IED
family; quietly reskinning the precise Breachhand would miss the instability,
collateral risk, and counterplay that make improvised charges distinctive.

A generic “fanatic” buff is the wrong answer. It hides the equipment, grants no
counterplay, and turns faction identity into immunity rather than a dangerous
material choice.

## Goal

Author two visible demolition items selected by the frozen target-faction
roster: a rare Pather martyr rig and a carried improvised charge used by Pather
or pirate demolition specialists. Both use ordinary damage, penetration,
friendly fire, belief, morale, casualty, and telemetry authority. Neither is a
blanket property of its faction.

## Equipment identities

### Martyr rig

A martyr rig is a worn volatile charge with a short, loudly telegraphed arming
commitment and a compact anti-armor/shrapnel detonation centered on its carrier.
Completion defeats the carrier through ordinary self-damage; it does not call a
scripted death around the durability model. The blast can harm allied Pathers,
civilians, and the intended target alike.

Only a rare specialist entry may carry one in the first catalog. The item is
not randomly added to every Pather squad, not inferred from morale, and not
available to pirates merely because both use improvised explosives.

### Carried improvised charge

A carried IED is a finite, crude contact-planted demolition pack. It reuses the
shared placement, fuse, detonation, and hazard-knowledge seams where possible,
but differs from the reusable Breachhand through limited stock, a less stable
payload, louder/rougher presentation, and wider collateral risk. Path and
pirate provenance may share the tactical family without sharing identical art
or quality.

The first slice has no pre-placed road mines, civilian booby traps, remote map
detonators, or hidden campaign infiltration state. “IED” here means equipment
carried into the visible battle.

## Commitment and AI policy

- A martyr carrier may arm only against an honestly identified nearby hostile
  concentration or hardened line whose projected value clears a high threshold.
  It cannot path from hidden knowledge, select an exact unseen target, or
  abandon a mission-critical hold merely because the player exists somewhere.
- Commitment requires a bounded reachable approach inside the squad's current
  maneuver authority. Other squad members do not receive immunity or exact
  future-path knowledge; they may react only to their faction's known armed
  hazard.
- The arming tell creates counterplay. Defeating, interrupting, or separating
  the carrier before completion prevents the armed state. Once genuinely armed,
  the rig follows one explicit cancel/drop/death rule and cannot oscillate to
  dodge consequences.
- IED carriers use the ordinary close-contact demolition opportunity and target
  reservation. They do not seek infantry victims, lay speculative traps, or
  stack several charges after projected damage is sufficient.
- The execution is data- and item-driven. A scenario may issue the same rig to
  another faction for a story reason without copying Pather-only Java logic.

## Campaign, recovery, and presentation

- Martyr rigs are defender-only in the first implementation and are explicitly
  exempt from player reachability with a catalog reason. Recovery yields only
  eligible volatile/common or advanced components, never an automatic printable
  suicide-rig recipe.
- Carried IED packs may be defender-only initially or receive a later outlaw
  player recipe through `s6-unlock-ladder-expansion.md`; the decision must be
  explicit for the stranded-asset check.
- Carrier art, armed tell, fuse, danger footprint, detonation, audio report, and
  after-action casualty attribution all describe the same simulation state.
  Presentation cannot conceal an armed rig that opposing observers have
  honestly seen.
- `target-faction-ground-rosters.md` controls rarity and eligible specialist
  issue without changing force count. `target-faction-command-doctrine.md` may
  bias legal use only through its ordinary belief-honest command boundaries.

## Acceptance

- Representative Path rosters issue martyr rigs only at the authored rarity and
  risk gates; pirate and non-Path fixtures never gain them by fallback.
- Arming, interruption, armed-state transition, self-damage, blast, friendly
  fire, casualty, morale, belief, and telemetry use shared authorities.
- An observed arming tell gives a real chance to interrupt or disperse; an
  unobserved opponent receives no exact warning or carrier identity.
- Carried IEDs remain finite contact-demolition items and do not create hidden
  persistent traps.
- No faction-wide explosion, damage resistance, morale immunity, movement
  bonus, or targeting knowledge appears when the item is absent.
- Deterministic scenarios cover successful martyr commitment, interruption,
  carrier death at each state, allied collateral, overkill reservation, and
  unknown/modded roster fallback.

## Out of scope

- Pre-battle infiltration, disguised bombers, civilian identity simulation, or
  campaign terrorism consequences.
- Mines, remote-trigger networks, disarming, prisoner equipment, and persistent
  unexploded ordnance.
- A normal player martyr-rig recipe or an order commanding a named marine to
  self-destruct.
- Faction doctrine outside the legal use of an actually issued item.
