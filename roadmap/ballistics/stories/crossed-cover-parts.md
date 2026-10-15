# Crossed cover by part

Status: IN PROGRESS — story written; implementation next.

Written: 2026-10-15

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

- [ ] `CoverProfile` unit test: crossed rows monotone in level and ordered
  legs ≥ torso ≥ arms ≥ head; expected interception within 0.03 of the flat
  chance; head zero at levels 1–2.
- [ ] Resolver unit tests: a level-2 crate between shooter and target catches
  a leg-bound round at the crossed rate and a head-bound round at zero; an
  authored miss and a partless target keep the flat chance.
- [ ] Full `:test` green.
- [ ] Scenes (`fighting-position`, `power-suit`, `late-arrival`,
  `assault-muster`, `squad-medic`), Conquest trial and Relief before/after.
- [ ] `ballistics-nouns.md` cover section and `body-part-cover.md` updated.

## Plan

1. `CoverProfile.crossed` table and `expectedCrossedInterception`.
2. In `BallisticResolver.trace`, compute the intended part once before the
   event walk (no RNG); at a doodad event use the crossed profile while the
   intended body is still ahead, else the flat chance.
3. Tests: profile calibration; resolver leg/head/miss/partless cases; adjust
   existing torso-bound QueueRandom tests to the crossed torso anchor.
4. Evidence before/after, doc updates.
