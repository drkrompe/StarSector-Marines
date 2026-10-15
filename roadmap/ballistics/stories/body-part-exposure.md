# Body-part exposure

Status: PLANNED — first slice of `body-part-cover.md`.

Written: 2026-10-15

Read `ballistics-nouns.md` and `body-part-cover.md` first.

## Scope

Direct rounds against infantry bodies roll a body part and are intercepted by
cover according to that part's exposure, replacing the flat 15/30/45% block
chance and, for those rounds, the flat damage cut. Every part does the same
damage in this slice.

In scope:

- Body parts and their shares for infantry bodies; hulls stay partless.
- Cover profiles for edge cover by level, for walkable props crossed in the
  lane, and for a trench's occupant.
- The part roll on the intended target (after the hit roll) and on incidental
  body contacts; the round's height follows the part.
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
- Cover crossed in the lane (a crate between shooter and target) still catches
  by its authored height; with heights following the part, it now catches low
  shots and passes high ones.
- No long-lived feature flag. A control switch may exist while the evidence is
  recorded and is deleted before this story ships.
- Shares and profiles live in one table, read in one place.

## Acceptance

- [ ] Unit tests: part frequencies follow shares on an uncovered body; a fully
  hidden part is never reached; a trench at range leaves mostly head and arm
  hits, and at point blank every part is reached; a hull is unaffected.
- [ ] A round that reaches an exposed part takes no cover damage cut; an area
  payload still does.
- [ ] `fighting-position`, `deployable-cover` and `killing-ground` scenes
  recorded before and after at 1920x1080 / 100%, with damage taken, casualties
  and the share of hits per part reported.
- [ ] `commanderEvidence -Pmission=conquest-trial` and one story mission
  (`relief`) run before and after, so a change in how long a defended position
  holds is seen rather than assumed.
- [ ] Docs folded: `ballistics-nouns.md` cover section, this story deleted and
  ledgered.

## Plan

1. `BodyPart` and a `CoverProfile` table (shares, per-level edge profiles,
   crossed-prop profiles, trench occupant profile).
2. Aim: after the hit roll, roll the part and set the elevation within that
   part's band; incidental contacts roll a part consistent with the round's
   height at contact.
3. Body contact: replace the level block chance with the part's coverage times
   the existing ramp; keep `COVER_CLIP` as the stop kind.
4. Carry the part on `BodyHit` and the damage payload; `DamageResolver` skips
   the cover cut when a part is present.
5. Tests, before/after evidence, doc fold.
