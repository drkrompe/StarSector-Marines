# Title logo — STARSECTOR: MARINES

Status: ACTIVE — direction A approved (the game's wordmark with white MARINES MOD): size, line and reveal timing agreed; laid over C5's layout sketch by `industrial-exterior/c5_title.py`, not yet timed to music.
Written: 2026-10-18
Updated: 2026-10-19 — the line under the wordmark is MARINES MOD; the title is in C5's layout sketch.

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

## The game's own wordmark

The installed game carries its title as a small transparent image
(`graphics/ui/starsector_title_alpha.png`, 450x50): lowercase, widely spaced,
square-cornered letters in glowing cyan. The owner asked to try it as the big
word, with MARINES beneath. It is used **as it ships**: read from the player's
install at run time and never copied into this repository. An AI redraw at a
higher resolution was tried and refused by the session's permission check, since
it means sending Fractal's art to an outside service. So the only change is
plain enlargement, which softens it a little.

At its own size it is under a quarter of the screen, and MARINES beneath it is
too small to read, so the cinematic shows it at about 2.4 times its size.
`title-logo/compose_game_wordmark.py` sets MARINES two ways: matching (thin,
cyan, glowing, so the two read as one mark) and ours (warm white, bold and
condensed). Because the big word here is the official one, this is the option
that looks most like the base game, so MARINES has to make it clearly the mod.

## Direction A (chosen)

**MARINES MOD** (owner, 2026-10-19). The line under the wordmark reads MARINES
MOD, on one line in the same lettering, a little wider than MARINES was. The
big word is the game's own name, so the title should not read as if it owned
it; MOD says plainly that this is a mod of that game, which needs the game to
play. Small taglines ("A Starsector mod", "Fan mod") under MARINES were tried
and passed over. Where the text below says MARINES, read MARINES MOD.

Chosen by the owner on 2026-10-18 from the labelled set, and approved the same
day once developed, with size, MARINES and reveal timing as described below: **the game's own
wordmark with MARINES in warm white beneath it**. The white MARINES won over the
cyan one because it reads as this mod's voice under the game's name.
`title-logo/title_a.py` builds it.

- **Sharpened, locally.** The wordmark is still read from the player's install
  and nothing leaves the machine. Its bright letters are enlarged smoothly,
  rounded and cut to a clean edge, and filled with their own colours; the dark
  blue glow stays soft, as it is in the original. This replaces the plain
  enlargement of the third round, which was visibly soft at 2.4 times its size.
- **Size and place.** The wordmark at 2.4 times (about 53% of the screen),
  centred a little above the middle; MARINES in Bahnschrift Bold SemiCondensed,
  warm off-white with a dark edge, widely spaced and a little under half the
  wordmark's width. Over the port the ground is darkened softly under both.
- **Menu version.** The same title alone on a plain dark screen, for menus and
  loading.
- **Reveal**, timed from the end of Castle's last line: the port alone for a
  second; starsector fades in over two seconds while the darkening comes up with
  it; MARINES follows 1.6 seconds after it starts; both hold about four seconds;
  then the whole picture fades to black over a second and a half, with the yard
  sound. The camera is still settling, so the port eases out the last few
  percent behind it. Timing against the actual music is still to do: the
  approved timing is the starting point, moved to land on the music's swell.

Open:
- **How it gets into the game.** If the cinematic ships as finished video, the
  title would be baked into it; if the game draws the title live over the last
  frames, the mod can read the wordmark from the install and sharpen it on load,
  so none of Fractal's art ships with the mod. The second keeps to how this was
  made.
- MARINES is set in a Windows font; it should ship as a finished image, never
  as the font file.
