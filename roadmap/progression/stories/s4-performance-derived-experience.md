# S4 — Experience band span

> A band the player collected and issued should be a felt difference, not a
> rounding error beside the rifle.

Status: READY — required before the shipped issued-band model pays off.
Written: 2026-08-22
Updated: 2026-08-27 — performance-derived awards dropped; experience is now
issued with the armour pattern rather than earned per marine, so this story
narrows to the band span alone. The slug is retained so existing citations
resolve.

Read `progression-nouns.md` before changing this story.

## Problem

`ExperienceTier` spans accuracy x0.92 to x1.13 across four bands — about 1.23x
end to end. Against the roughly 3x effective-DPS span that equipment grade
covers after S1, the experience band is statistical noise.

That was already thin when experience was earned. It is disqualifying now that
experience is **issued**: a squad's armour pattern is one of the two things the
player reads to judge a formation, and `progression-nouns.md` requires that what
the player reads determines how the squad fights. A band the player can see but
cannot feel breaks that law in practice while satisfying it on paper.

## What this story no longer covers

Performance-derived XP awards, award guardrails, diminishing returns, and XP
threshold re-derivation are all withdrawn. Rank-and-file marines no longer
accumulate experience, so there is no award to compose and no ladder to climb.
The reasoning is recorded in `progression-nouns.md` under aptitude and
experience.

## Goal

Widen the band span so an issued standard is legible in play, without turning
experience into a second equipment-grade axis.

## Approach

Directional proposal — tune with S1's TTK harness, do not treat as final:

| Band | accuracy | cooldown | spread |
| --- | --- | --- | --- |
| Green | x0.82 | x1.15 | x1.20 |
| Regular | x1.00 | x1.00 | x1.00 |
| Veteran | x1.12 | x0.92 | x0.86 |
| Elite | x1.25 | x0.84 | x0.72 |

Keep the behavioral differences carrying real weight alongside the multipliers.
Reflex delay and friendly-fire hold discipline already separate the bands in
ways a player can watch happen; widening only the multipliers would trade the
more interesting axis for the duller one.

**Balance guard:** bands apply to defenders and employer soldiers too. A wider
span means veteran defenders are meaningfully harder, which is desirable for
threat legibility but must be verified. Re-verify risk-scaled defender rosters
as in S1, and confirm the existing mission evidence still passes.

**Quantization guard:** per `[[battle_lethality_currency]]`, TTK quantizes hard
at low round counts. A cooldown or accuracy change that looks small can move a
two-round kill to three. Measure before choosing final values.

## Out of scope

- Aptitude, which stays innate and per marine.
- Trait acquisition — `s10-trait-mechanics.md`.
- The band's source and resolution, which shipped; see `progression-nouns.md`.
- A fifth band above Elite. It was proposed as a long-campaign chase for an
  earned ladder; with bands issued, another band is a catalog decision for the
  definitions rather than a progression capstone. Revisit only if the four
  bands prove too coarse to express the definition catalog.

## Acceptance

- The Green-to-Elite span is a difference a player notices in play, stated as a
  measured TTK and landed-rate delta rather than a multiplier table.
- Defender rosters at each risk level re-verified against the wider span.
- Existing mission and commander evidence still passes.
- No band change silently crosses a TTK quantization boundary; the harness
  output is recorded with the chosen values.
