# Body-part exposure

Status: IN REVIEW — implemented and merged; awaiting owner review of the evidence, including Relief seed 8103 with a late player arrival.

Written: 2026-10-15

Updated: 2026-10-15 — implemented; parts are read from where the aim lands, so no extra roll is spent.

Read `ballistics-nouns.md` and `body-part-cover.md` first.

## Scope

Direct rounds against infantry bodies land on a body part and are intercepted by
cover according to that part's exposure, replacing the flat 15/30/45% block
chance and, for those rounds, the flat damage cut. Every part does the same
damage in this slice.

In scope:

- Body parts and their shares for infantry bodies; hulls stay partless.
- Cover profiles for edge cover by level and for a trench's occupant.
- The part on the intended target read from where the committed aim landed
  up its silhouette, and on incidental contacts from the round's height there.
- The part's coverage roll replacing the body contact's cover roll, scaled by
  the existing muzzle and shooter-distance ramps.
- The part carried on the body hit and the damage payload. A payload with a
  part skips the cover damage cut; one without (area, strafe, unattributed)
  keeps today's cut.
- `ballistics-nouns.md` cover section rewritten to the new model.

Out of scope: part damage multipliers, wound effects, per-part armour, area
exposure, AI exposure scoring (see `body-part-cover.md`).

## Constraints

- Hulls (mechs, vehicles, emplacements, aircraft) are unchanged: no part, no
  terrain cover.
- Cover crossed in the lane (a crate between shooter and target) keeps its
  flat catch by authored height. Having it catch by part needs the aim pattern
  to span the whole silhouette, which is deferred (`body-part-cover.md`).
- No long-lived feature flag. A control switch may exist while the evidence is
  recorded and is deleted before this story ships.
- Shares and profiles live in one table, read in one place.

## Acceptance

- [x] Unit tests (`BodyPartCoverTest`, `TrenchOccupantCoverTest`): part bands
  follow shares; profiles are monotone and match the old block-plus-cut stop
  rate within five points; a trench at range is never hit in the legs and is
  hit mostly in head and arms; at point blank it hides nothing; only people
  have parts.
- [x] A round that reaches an exposed part takes no cover damage cut; a payload
  without a part still does.
- [x] Scenes before and after (`fighting-position`, `power-suit`,
  `late-arrival`, `assault-muster`): every verdict passes on both. Fights in
  the open are byte-identical, because the part costs no extra roll. Moving
  onto a trench under fire: 8 of 12 alive against 4. The `assault-muster` plan
  loop lost 70 marines against 62. Per-part hit shares are measured in the unit
  tests, not in the scenes.
- [x] Conquest trial before and after: every fixture still won, with marine
  losses 176 against 191 across the eight. Relief: eight of nine fixtures
  unchanged; seed 8103 with a late player arrival went from a timeout with six
  of the company alive to a defeat at tick 4453.
- [ ] Docs folded: `ballistics-nouns.md` cover section, this story deleted and
  ledgered.

## Plan

1. `BodyPart` and a `CoverProfile` table (shares, per-level edge profiles,
   trench occupant profile).
2. Aim: the hit roll's elevation sample doubles as the height up the body,
   which picks the part; incidental contacts take the part at the round's
   height.
3. Body contact: replace the level block chance with the part's coverage times
   the existing ramp; keep `COVER_CLIP` as the stop kind.
4. Carry the part on `BodyHit` and the damage payload; `DamageResolver` skips
   the cover cut when a part is present.
5. Tests, before/after evidence, doc fold.
