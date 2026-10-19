# Title logo — STARSECTOR: MARINES

Status: DRAFT — word order decided (STARSECTOR big, MARINES small); six directions made in that order; no direction chosen.
Written: 2026-10-18
Updated: 2026-10-18 — owner flipped the hierarchy after the first round; second round made to match.

The title comes up at the end of the Jangala arrival cinematic, in the last six
seconds of C5 (`c5-v2-closing.md`, beats 26–32 s). It is the first time the
player sees the mod's name, and it divides the cinematic from play: cinematic,
title, then the yard report (`storyboard-v3.md`).

## Where it is seen

- **Over a bright, busy overhead view.** By the time the title arrives the camera
  has risen straight up, 1–3 km over Greywater Down: grey concrete, cradles,
  freighters, a container yard in many colours, and green jungle along one edge.
  Warm daylight, with cloud shadows moving across. There is no dark sky to put
  the title against.
- **So the mark has to carry its own contrast.** Light letters need a dark edge,
  or the mark needs its own solid shape (a plate, a patch, a bar). The picture
  may be darkened softly under the title, but no box or banner is drawn behind
  it.
- **Size and place.** About half the screen wide, centred, a little above the
  middle so it sits over the port and leaves the jungle edge visible. It must
  still read at a quarter of the screen width, since it will also serve on a
  menu or loading screen later.
- **Still picture under it.** The camera is near the top of its rise and barely
  moving, so the title does not have to fight motion.

## How it comes in

Keep it simple and let the music do the work.

- Castle's last line ("That was the easy part, Commander.") finishes over the
  rising camera. The title comes in after it, never on top of her words.
- **A slow fade, about 1.5–2 s, as the music swells.** Possible extra for the
  chosen direction only: STARSECTOR fades in a beat before MARINES, or a slow
  wipe across a painted or stencilled mark, as if the paint were going on.
- Hold about 3 s while the music resolves, then fade the whole picture out to
  black together with the yard sound.
- No spinning, no zooming at the camera, no flashes, glitches or lens flares.

## Tone

- **Hopeful but fragile.** These are refugees from a failing station who have just
  landed somewhere with sky. The title should feel like a beginning, not a
  victory.
- **Worn and industrial.** Patched hulls, stencil paint, riveted plate, a yard
  that mends ships. Hand-made beats glossy.
- **Colour.** Hegemony orange is everywhere in the world (the customs car, the
  port's safety markings). The logo may use it as a small accent or not at all;
  it is the Hegemony's colour, not the company's.

## What it must not do

- **It is a fan mod.** It must not copy, trace or echo Fractal Softworks' own
  Starsector logo — its lettering, layout or emblem — or look like an official
  release. No trademark symbols.
- **STARSECTOR is the big word and MARINES the smaller one** (owner,
  2026-10-18, after the first round, which had it the other way): Starsector is
  the draw, and MARINES names this particular game. Because the big word is the
  game's own name, the look has to be clearly ours, so the lettering, layout and
  treatment must stay well away from the official logo. Our styles (stencil,
  riveted plate, hull paint, patch) do the work that a big MARINES did before.
- No other words, taglines, version numbers or credits in the mark itself.
- Nothing that needs the cinematic to read: it must also work alone on a plain
  screen.

## The six directions

Each was made with ImageGen from the brief in `title-logo/prompts/` and put over
the end frame of C4's port pan, the same wide view the C5 pull-out ends on, by
`title-logo/compose_title.py`. The first round (`<name>.txt`) had MARINES big;
the second (`<name>-v2.txt`) is the same six ideas with STARSECTOR big.

| # | Direction | Idea |
|---|---|---|
| 1 | Stencil | Spray-painted through a cut stencil, like crate markings. Bone white and charcoal. |
| 2 | Stamped plate | A riveted steel nameplate from old machinery, letters raised and worn bright. |
| 3 | Unit patch | An embroidered company patch (a boat coming down to a planet) beside a clean wordmark. |
| 4 | Hull paint | The title hand-painted on patched hull plating, like the ship's own name. |
| 5 | Clean horizon | Quiet and airy: thin wide letters over a planet's curve at dawn. |
| 6 | Hazard industrial | Port signage: a dark bar with worn orange-and-black hazard stripes. |

The owner picks one or two to develop. Developing one means: redrawing the
lettering so it is exact and our own (ImageGen lettering is a sketch, not
final type), a clean vector version, a plain-background version for menus, and
the fade timed against the music.
