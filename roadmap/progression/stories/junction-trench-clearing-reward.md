# Trench-Clearing Issue as the Junction reward

Status: IN PROGRESS
Written: 2026-10-15

Read `campaign-kit-arc.md` (the arc map), `progression-nouns.md` (Kit and armory,
arc issue cards) and `broken-supply-route.md` before implementing.

## Problem

Broken Supply Route's first beat, *Open the Junction*, is the campaign's first
assault on a dug-in position: trenches across the road, a Weigh Station, a bonded
warehouse. The arc map gives it **Trench-Clearing Issue** (service rifles, four
rocket tubes, grenades), the card whose whole job is opening a defended position.
Today Junction pays only credits, so the lesson has no kit that names it, while
Thunder Run after it stocks Convoy Escort Issue.

## Scope

- A won *Open the Junction* puts **one Trench-Clearing Issue card** in fleet cargo,
  from Wayline's militia stores. It is a card to learn, not a fitting: nobody's
  equipment changes.
- Once only. The Junction is won once; a replayed result, a defeat, recurring Route
  Security and Thunder Run never award it.
- No duplicate: a company that already knows the card or carries one gets nothing
  further, the same rule the Agreus suit cards follow.
- The terms Mara offers and the debrief name the card, so the reward is promised
  before the battle and accounted for after it.

This is a **reward**, not a listing. Thunder Run's Convoy Escort Issue is bought
at the employer's market; Junction hands its card over, because a paid assault
contract that ends with the employer's stores opened is the earlier, simpler kind
of payment the Wayline opening already uses (A Proper Issue).

## Out of scope

- Changing the card itself. Its shape was settled on the `loadoutMatchup` ladder
  and it is the control the other arc cards were measured against.
- Pool weights. The card still circulates through ordinary channels; the reward is
  the guaranteed route.

## Acceptance

- A unit test on the settlement seam: a won Junction adds one card; a lost one, a
  second settlement and an already-known card add none; Thunder Run adds none.
- The Junction terms and story debrief name the card.

## Plan

1. Award the card from the Junction settlement, reusing the unavailable-card rule.
2. Name it in the terms and debrief.
3. Fold into `progression-nouns.md` beside the arc issue cards; update
   `campaign-kit-arc.md`.
