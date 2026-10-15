# Crossed cover by part

Status: IN REVIEW — implemented on its session branch; awaiting owner review of the Conquest trial shift.

Written: 2026-10-15

Updated: 2026-10-15 — implemented; evidence recorded below.

Read `ballistics-nouns.md` and `body-part-cover.md` first. Follows
`body-part-exposure.md`, which made cover at the body hide parts but left a
walkable prop crossed in the lane on its flat catch.

## Scope

A walkable prop a round crosses between shooter and target (a crate in the
lane) catches the round by the part the round is headed for, instead of by a
flat 15/30/45% chance per level. A low crate mostly catches leg- and
torso-bound rounds and lets head shots pass.

In scope:

- A `crossed` profile table in `CoverProfile`, one row per cover level, shaped
  low: legs ≥ torso ≥ arms ≥ head, head at or near zero at levels 1 and 2.
- The crossing's catch chance is `crossed(level, part) × proximity ramp` when
  the round has an intended part: the trace has an intended target, the aim is
  on target, and that target is a person (has body parts, not carried). The
  part is the one the committed aim landed on (`BodyPart.atHeight` of the
  aim's height fraction).
- Every other round keeps the flat chance exactly: authored misses, point aim,
  and targets without parts (mechs, vehicles, emplacements, creatures). A
  crossing after the intended body has already been struck (a penetrating
  round flying on) also keeps the flat chance.

Out of scope: deriving profiles from prop geometry, making the round's drawn
height follow the part, prop damage from caught rounds, part consequences
(owned by another slice).

## Constraints

- **Same count, different rounds.** Each level's expected interception,
  Σ share × crossed coverage, matches the old flat chance (0.15/0.30/0.45)
  within 0.03. This slice changes which rounds a lane prop stops, not how many.
- RNG consumption: one roll per crossing, only when its chance is above zero.
  A fight with no lane props is byte-identical.
- The existing catch-band height gate on a crossing is unchanged.
- One table, read in one place (`CoverProfile`).

## Acceptance

Crossed rows chosen (head, arms, torso, legs): level 1 `0, 0, .04, .45`
(expected .151), level 2 `0, .05, .20, .70` (.300), level 3
`.05, .20, .40, .85` (.460).

- [x] `BodyPartCoverTest`: crossed rows monotone in level and ordered legs ≥
  torso ≥ arms ≥ head; expected interception within 0.03 of the flat chance;
  head zero at levels 1–2.
- [x] `BallisticResolverTest`: a level-2 crate eight cells out catches a
  leg-bound round at 70% and a head-bound round at zero without spending a
  roll; a mech target and an authored miss keep the flat 30%. Existing
  torso-bound crossing tests now pin the crossed torso anchor (20%).
- [x] Full `:test` green.
- [x] Scenes (`fighting-position`, `power-suit`, `late-arrival`,
  `assault-muster`, `squad-medic`): every verdict passes, and every metric is
  identical to the baseline — none of them puts a walkable prop in a lane a
  part-bound round crosses. Relief: all nine fixtures identical.
- [x] Conquest trial: every fixture still won by the marines; two of eight
  identical. Marine losses 210 against 176 across the eight, defenders
  remaining 127 against 115 (on-path-42 7170 ticks against 7620, on-path-43
  70 alive at the end against 82). One replay each, so this is a direction to
  review rather than a measured effect. A plausible mechanism: a crossed prop
  and the cover at the body now both favour low parts, so a man behind a lane
  prop *and* edge cover or a trench is more exposed than the two flat rolls
  made him (level-2 prop plus level-2 edge: 43% of part-bound rounds reach him,
  against 36%).
- [x] `ballistics-nouns.md` cover section and `body-part-cover.md` updated.

## Plan

1. `CoverProfile.crossed` table and `expectedCrossedInterception`.
2. In `BallisticResolver.trace`, compute the intended part once before the
   event walk (no RNG); at a doodad event use the crossed profile while the
   intended body is still ahead, else the flat chance.
3. Tests: profile calibration; resolver leg/head/miss/partless cases; adjust
   existing torso-bound QueueRandom tests to the crossed torso anchor.
4. Evidence before/after, doc updates.
