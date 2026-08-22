# S8 — Roster legibility: aptitude, career, traits

> Aptitude is rolled, load-bearing, and shown to the player as one letter.
> Traits have no UI at all.

**Status:** not started. Depends on
[S3](s3-per-soldier-telemetry.md) for career data.

## Problem

Every quality axis the mod tracks is effectively invisible:

- `SoldierAptitude` — rolled at recruitment (5% Exceptional / 20% Gifted /
  65% Steady / 10% Limited), permanent, affects accuracy and spread.
  Surfaced as one letter inside `SoldierProfile.shortLabel()` (`"V/G"`).
- `ExperienceTier` — same one-letter treatment plus a raw XP integer.
- `Trait` — **no surface whatsoever**. Eleven traits, four functional, and
  nothing anywhere lets the player read or compare them.
- Career history — does not exist (S3 creates it).

The player cannot answer "who are my best marines", "who should get the
Masterwork rifle", or "what does this captain actually do for me" from any
screen in the mod.

## Goal

A tightly composed roster view where the player can compare marines at a
glance, drill into one, and understand what a captain's traits mean.

## Slice 1 — Comparable roster rows

A compact per-marine row carrying, at minimum:

- Name, fireteam, status (ACTIVE / WIA with return day / KIA / MIA).
- **Aptitude as a relative bar** — the requested skill-bar treatment.
- **Experience as a bar with its tier named**, not a raw XP integer.
- Current kit at a glance: primary family, grade tier mark, armor tier.

Design decisions worth making deliberately:

- **Absolute scale with a company-median marker** rather than
  company-relative bars. A relative bar makes a company of Limited marines
  look uniformly excellent, which is exactly backwards.
- Sorting and filtering by aptitude, experience, kit, and status — the
  point of the view is comparison.

### The font-floor tension

This is the real design constraint and should be confronted up front, not
discovered mid-implementation. The project has a hard **Orbitron 20px
minimum** ([[ui_font_minimum]], established after a playtest that removed
the 10px face). "Tightly composed" and a 20px floor are in genuine
tension.

The resolution is to make bars and icons carry density instead of text:
a skill bar communicates at any size, an 8-character stat label does not.
Budget the text, spend the space on graphics.

## Slice 2 — Career readout

On selecting a marine, the career block from S3:

- Missions deployed / won.
- Landed-round rate, damage dealt, kills.
- Times wounded, days in service.

This is the "meaningful change over time" payoff. A marine with 40
missions and a visible record is a different object to the player than a
name in a list — and it is what makes losing one land.

## Slice 3 — Trait surface

Captains carry traits; nothing shows them.

- Trait cards on the captain row and in the formation view: name, what it
  actually does, and where it came from (recruitment, discovery source,
  moral outlook).
- Traits with no mechanic yet must not claim one. Until
  [S10](s10-trait-mechanics.md) lands, an inert trait should read as
  flavor/background rather than as a stat the player is failing to notice.
- `IDEALIST` / `CYNICAL` are deliberately mechanic-free and should present
  as character, not as a buff — the moral-outlook story was explicit that
  it adds no combat modifiers, and the UI must not undercut that.
- Commendations already accumulate on `MarineCaptain` as free-form text and
  currently go nowhere. They are the ready-made narrative surface for this
  view.

## Out of scope

- Changing aptitude, XP, or trait *mechanics*. This story is presentation
  only, with the single exception of surfacing data S3 already persists.
- In-battle conveyance — [S9](s9-in-battle-quality-conveyance.md).
- Any new screen shell. This extends `ArmoryScreen`'s PERSONNEL surface
  rather than adding another full-canvas takeover.

## Acceptance

- A player can rank their company by quality without arithmetic.
- Every axis the sim reads (aptitude, experience, grade, armor) is legible
  somewhere in this view.
- Orbitron 20 floor respected throughout; no exceptions carved for density.
- Sprite handling follows the shipped pattern — cache the `SpriteAPI` per
  screen and load textures before measuring ([[sprite_lazy_load]]).
- GL state discipline: the bracket pattern around every draw
  ([[gl_state_gotchas]]).
- In-game feel pass. Layout density is not testable.

## Open questions

- Should rank-and-file marines eventually carry traits too — a specialist
  mark earned in the field? It would give the roster view much more to
  show and make individual marines more distinct. Deferred to
  [S10](s10-trait-mechanics.md)'s open questions; noted here because it
  changes how much room the row layout should reserve.
- Does the career readout want a per-mission timeline rather than lifetime
  totals? S3 ships totals only. A timeline is a bigger save and UI
  commitment; decide once the totals view exists and the gap is felt.
