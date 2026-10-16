# Approved kit across the line

Status: NEEDS DECISION — the proposal contradicts the shipped Cold Account model;
see "The conflict" before building.
Written: 2026-10-15

Read `campaign-kit-arc.md` (the four registers and the arc map),
`early-operation-nouns.md` (A Cold Account, Captain Kord) and `progression-nouns.md`
(Kit and armory, military warrants) before implementing.

## Problem

`campaign-kit-arc.md` says the player meets each register of kit **across the line**
before holding it: Garnir is where the company sees approved Hegemony kit fight, and
Agreus is where it starts wearing recovered versions of the same machines. That gap,
visible for a whole arc before it closes, is the story. Today nothing in any battle
shows approved kit before the warrant offers it, so the warrant reads as an unlock
rather than the end of a long look over the fence.

Two places are named:

- **Garnir (*A Cold Account*):** Kord's Hegemony marines in XIV Shock, with Arbalests
  and Arrestor packs.
- **Agreus (*A Small War*):** Greyhook fielding crude Foundry-Breaker-style rigs.
  That half belongs to the *A Small War* Chapters 4–5 work and is not in this story.

## The conflict

The shipped model of *A Cold Account* (`early-operation-nouns.md`) is built on
**institutional indifference**: Kord diverts HSS Resolute only for authenticated
evidence, turns to gunnery without thanking the company, and promises no clearance.
"A Wayline contract confers no military alliance or protection." The operation has
"no free chassis, employer infantry, fighter cover or additional strategic powers."
No Hegemony infantry is on the ground.

Two shipped texts already assume the opposite:

- the auxiliary warrant's terms say Resolute's after-action report "records the
  company fighting beside the Hegemony line";
- Counter-Battery Issue's lore says its markets sell it "to companies that stood
  beside its officers".

So either the battle gains Hegemony marines, or those texts change.

## Options

**A. Kord lands a section on her own orders (recommended).** Once the archive is
transmitted and Resolute takes station, a Hegemony marine section lands from the
Onslaught at the eastern perimeter: XIV Shock with Arbalest support and Arrestor
packs. It is a separate allied force that the player cannot command, and it is there
for the Navy's purpose: securing the battery ground before demolition, not covering
the company. It never waits for, escorts or protects the withdrawal. The company
sees approved kit fight across the field for the minutes it takes to reach pickup.
This keeps indifference intact (the marines are as unconcerned with the company as
the guns are) and makes both shipped texts true. Cost: an allied third force in a
voiced mission, its arrival beat, presentation, and fresh `commanderEvidence
-Pmission=corvus` readings, because a new force on the map moves outcomes.

**B. Keep Garnir as shipped and correct the texts.** No marines land. The auxiliary
letter cites the company's evidence that put Resolute's guns on a pirate stronghold,
and Counter-Battery lore drops "stood beside". Approved kit is shown only in the
Garnir dossier art and Kord's transmission portrait. Cheap and safe, but the
across-the-line beat becomes a picture rather than a fight.

**C. Move the across-the-line beat elsewhere.** For example, a recurring Hegemony
patrol encountered on later contract work. This defers the problem rather than
solving Garnir's texts, which would still need option B's correction.

## Scope (once decided)

Option A:

- A Hegemony section of approved kit lands from Resolute after the archive
  transmission, as its own force under its own orders, never under player command.
- It never becomes a protection promise: no escort, no hold for the company's
  pickup, no change to extraction or the release gate.
- A Castle line that marks what the company is looking at, without awe or thanks
  from Kord.
- The settled outcome, payment and debrief are unchanged except that the results
  may name the section.

## Acceptance (option A)

- A unit test that the section arrives only after transmission and is never
  commandable by the player.
- `commanderEvidence -Pmission=corvus -Dbattle.corvus.chapter=COLD_ACCOUNT` before
  and after, reported as readings rather than verdicts.
- A 1920x1080, 100% UI-scale frame of the section in the field beside the company's
  withdrawal.

## Plan

1. Owner decides A, B or C.
2. A: add the landing force and its arrival beat; B: correct the two texts.
3. Fold into `early-operation-nouns.md` (A Cold Account) and `campaign-kit-arc.md`.
