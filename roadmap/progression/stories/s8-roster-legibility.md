# S8 — Roster legibility: aptitude, career, traits

> Aptitude is rolled, load-bearing, and shown to the player as one letter.
> Traits have no UI at all.

Status: READY — the career evidence defined by `progression-nouns.md` is shipped.
Written: 2026-08-22
Updated: 2026-08-25 — accounted for the retained host's shipped resolution-fit and explicit Starsector UI-scale seam.

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

## Slice 0 — Honor the UI scale setting

**The retained-host prerequisite is shipped; roster typography verification remains.**

Retained Marine Ops screens now query `getScreenScaleMult()` at their shared host
boundary. Physical resolution fit may shrink a reference presentation, while the
player's explicit UI scale remains a separate density input for responsive layout.
Legacy and non-retained surfaces do not automatically inherit that policy.

- Query the scale mult and express the readable floor in **physical**
  pixels.
- Optionally select the type-scale step from it — Insignia 15 at scale
  >= 1.25, Insignia 17 below.
- Verify at 1.0x, 1.25x, and 1.5x. Note the existing manual
  `Display.getWidth() / getScreenWidth()` computations in `BattleScreen`,
  `GroundParallaxPipeline`, and `BridgeRenderer` are deriving the same
  quantity by hand; consider consolidating rather than adding a fourth.

The roster work must still select and verify its readable type steps at each supported
UI scale before density is tuned by eye.

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

### The font floor — resolved

"Tightly composed" and the standing **Orbitron 20** minimum
([[ui_font_minimum]]) look like a hard conflict. Investigating the actual
font infrastructure showed they are not, for three reasons. This section
supersedes the old floor for this track's surfaces.

**1. It is a typeface problem, not a size problem.** Orbitron is a
geometric *display* face — wide, low x-height, built for headings. All 224
of our text call sites use it. The 20px floor is really the floor *for
Orbitron*. Vanilla ships several genuine text faces that are smaller *and*
more legible. Measured from the `.fnt` metrics, as relative area per
character:

| Font | lineHeight | avg advance | area/char | notes |
| --- | --- | --- | --- | --- |
| `orbitron20aa` | 20 | 11.04 | **1.00** | today's floor |
| `insignia17LTaa` | 17 | 8.64 | **0.67** | AA'd; Starsector's own text face |
| `uni16` | 19 | 7.81 | 0.67 | narrow, good x-height |
| `arial14` | 16 | 7.66 | **0.56** | most legible small; tabular digits |
| `insignia15LTaa` | 15 | 7.36 | **0.50** | already shipping in this mod |
| `victor14` | 13 | 6.66 | 0.39 | pixel font, no AA |
| `victor10` | 9 | 4.72 | 0.19 | the face that failed playtest |

`Fonts.INSIGNIA_15_AA` already ships and is used by the GOAP debug overlay
— proven in-engine at half the area of Orbitron 20.

Note what the original playtest actually rejected: Victor **10**, a
9px-lineHeight pixel font with antialiasing off (`aa=1, smooth=0`) — the
smallest, lowest-fidelity option in the catalog. That verdict is sound for
Victor 10 and says nothing about Insignia 17 or Arial 14, which were never
tried. **Re-run the test against those rather than re-testing Victor 10's
conclusion.**

**2. The floor must be judged in physical units.** `SettingsAPI` documents
`getScreenWidth()`/`getScreenHeight()` as virtual pixels already divided by
`getScreenScaleMult()`. The retained host now keeps that multiplier explicit:
Orbitron 20 remains 20 virtual px, rendering as 30 physical px at a 1.5x UI scale
and 20 at 1.0x. Whatever scale the original playtest ran at silently set the old
"20", so the roster still needs the multi-scale verification in Slice 0.

**3. Tabular figures matter more than size here.** For stat columns, digit
advance uniformity is what makes numbers line up:

- `arial14`: `8 8 8 8 8 8 8 8 8 8` — perfectly tabular
- `orbitron20aa`: `13 11 13 13…` — narrow `1`, columns wobble
- `insignia15` / `victor14`: `7 5 7 7…` — same problem

Arial 14 is the only genuinely tabular option in vanilla's set.

### Resolution: a type scale, not a floor

The deeper problem is that one font serves every purpose. Adopt a scale:

| Role | Font |
| --- | --- |
| Display | `orbitron24aabold` |
| Header | `orbitron20aa` / bold — keeps the brand identity |
| Body | `insignia17LTaa` |
| Dense data rows | `insignia15LTaa` |
| Numeric columns | `arial14` (tabular digits) |

Two rules survive from the old floor and are not relaxed: **bars and icons
carry density before text does** — a skill bar communicates at any size, an
8-character stat label does not — and **no gameplay surface goes below the
body step** without a playtest backing it.

Deferred, in preference order, if the scale proves insufficient:

- **Bitmap-atlas quality under fractional fit.** `BitmapFont.drawStringScaled`
  and the retained painter now carry the host transform, but downscaling a bitmap
  atlas without mipmaps can still soften text and break the pixel-grid alignment
  that keeps it crisp. Scaling support is not a substitute for the right atlas.
- **Ship our own `.fnt`.** The mod ships **zero** fonts today — we are
  entirely on vanilla's set. BMFont/Hiero produce `.fnt` + `.png` and
  `mod/graphics/` already ships. Best ceiling (a condensed Orbitron-adjacent
  text face at 14-15 would keep brand identity at data density) and the
  most work. Defer until the vanilla set is proven inadequate.

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
  `s10-trait-mechanics.md` lands, an inert trait should read as
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
- In-battle conveyance — `s9-in-battle-quality-conveyance.md`.
- Any new screen shell. This extends `ArmoryScreen`'s PERSONNEL surface
  rather than adding another full-canvas takeover.

## Acceptance

- A player can rank their company by quality without arithmetic.
- Every axis the sim reads (aptitude, experience, grade, armor) is legible
  somewhere in this view.
- The type scale is applied per role, not one font everywhere. No gameplay
  surface sits below the body step (`insignia17LTaa`) without a playtest
  backing it.
- Readability verified at UI scale 1.0x, 1.25x, and 1.5x — not at one
  unstated setting, which is how the old floor was set.
- Numeric columns align; digits do not wobble between rows.
- Sprite handling follows the shipped pattern — cache the `SpriteAPI` per
  screen and load textures before measuring ([[sprite_lazy_load]]).
- GL state discipline: the bracket pattern around every draw
  ([[gl_state_gotchas]]).
- In-game feel pass. Layout density is not testable.

## Open questions

- Should rank-and-file marines eventually carry traits too — a specialist
  mark earned in the field? It would give the roster view much more to
  show and make individual marines more distinct. Deferred to
  `s10-trait-mechanics.md`'s open questions; noted here because it
  changes how much room the row layout should reserve.
- Does the career readout want a per-mission timeline rather than lifetime
  totals? S3 ships totals only. A timeline is a bigger save and UI
  commitment; decide once the totals view exists and the gap is felt.
