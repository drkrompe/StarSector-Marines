# C1 CORVUS: screen-space UI pass

Status: DRAFT. Recommends captions and a planet ID, and no portraits. Both are implemented in `c1_overlay.py` and await the owner's review.

Written: 2026-10-16

The owner asked for "some kind of UI pass here to indicate 'planet'", and whether
C1 should carry the hail's dialog and speaker portraits.

## Recommendation

1. **Identify Jangala with a sensor lock, once.** As Approach says "Jangala" in
   line 1, four short arcs close on the globe's limb. A leader then runs from the
   upper-right arc to a label: **JANGALA**, and beneath it **CORVUS · HEGEMONY**
   with Hegemony in the faction's orange. It clears as Approach keys down at the
   end of line 1, before Castle answers. It is up for about seven seconds.
2. **Caption every hail line.** Each caption has a small speaker tag:
   **JANGALA APPROACH** in Hegemony orange, **CASTLE · SECOND WIND** in the
   player's UI cyan. They sit lower-centre over a feathered dark field, not a
   boxed panel.
3. **No portraits, crest or comms card in C1.**

## Why

**Captions are not optional.** The storyboard's hail section already requires
them: "each line also needs a subtitle, so the exchange reads in full with voice
off." Approach is deliberately degraded (`orbital_comms`: narrow band,
saturation, bit crush, noise floor) under a cabin bed. It is the hardest voice in
the game to understand, and it carries the plot facts (the transponder, the
customs list). Neither speaker is on screen, so a caption without a name would
leave a player who has the sound off unable to tell who is talking. The tag is
words, not only colour, so the colour coding is a reinforcement, not the only cue.

**The game's own dialog UI will not show these lines.** The arrival plays the
first time the player docks at Jangala, before the yard panel, as a full-screen
cinematic. The interaction dialog under it prints only the text a plugin adds,
and nothing adds the hail. The mod's existing subtitle surface
(`StorySubtitleOverlay`) belongs to the battle screen, not to this cinematic.
Without captions in the cinematic the lines appear nowhere, so there is no risk
of showing them twice.

**Portraits fight this shot.**

- The storyboard's beat modes separate *Dialog* (portrait or panel), *Image +
  dialog* and *Cinematic*. C1 is "Cinematic + voiced hail". A portrait panel
  turns it into a dialog beat over a moving background, and its payoff is place
  and scale.
- The camera is in the passenger compartment, overhearing the bridge on an open
  intercom (the voices section of the storyboard). A face card puts the viewer on
  the bridge, at the microphone. The audio design depends on the passenger point
  of view: Approach far and degraded, Castle close and human, line 9 marked only
  by the missing key-up.
- Approach has no portrait. The Hegemony crest is the only stand-in, and as a
  speaker image it reads as a faction banner, giving a routine controller more
  weight than the storyboard wants ("Approach is routine").
- Castle's face is established in eight prologue pages (P1–P8). It adds nothing
  here.
- The mod's portrait subtitle (`story-subtitle.mlx`: an 88 px portrait in a
  bordered 640 px panel) would cover the lower-left quarter, which is where
  Second Wind makes its entrance.

Portraits remain right for the Dialog beats that follow (Y3, Y4, M1).

**The planet ID earns its place because the voice says the name.** The lock lands
on the word "Jangala", so the label confirms what the player hears and leaves.
A persistent label would turn the plate into a map, and a title card at frame 0
would arrive before the ship's entrance has landed. It is one HUD gesture,
consistent with what the ship's sensors would do on approach. It uses
Starsector's own UI values: the player faction's base UI cyan (170, 222, 255)
for lines and the name, and the Hegemony faction colour (245, 150, 30) for the
owner. No game art is copied.

## Timing (all derived, nothing hard-coded)

Times come from `build_hail_preview.timeline()` over the catalog
`mod/data/marines/jangala_arrival_hail.json`, measured on the current treated
takes. C1 is 37.70 s.

| What | When | Rule |
|---|---|---|
| Lock lands | 5.49 s | Line 1 start, plus the take length scaled by "Jangala"'s character position in the line text |
| Arcs close, leader runs, label fades in | 5.49–6.9 s | 0.7 s close, then leader and label |
| Planet ID clears | 11.80–12.70 s | Fades from Approach's key-down at the end of line 1 |
| Line 1 caption, Approach | 3.00–12.25 s | Tag at the squelch key-up. Paged in two at sentence boundaries |
| Line 2 caption, Castle | 12.62–23.55 s | Two pages |
| Line 3 caption, Approach | 23.70–32.15 s | One page |
| Line 4 caption, Castle | 32.52–37.70 s | Fades out on the shot's last frames |

A line stays up 0.45 s after its take ends, for reading, unless the next speaker
keys up first. Pages split at sentences and are never more than two rows (960 px,
balanced). Each page holds for a share of the take proportional to its length.
Reading speed tops out near 16 characters per second. The caption field stays up
across the whole exchange, because every gap is under 1.8 s. That avoids pumping
the picture between turns. Fades are smoothstep: 0.14 s between pages, 0.2–0.25 s
on tags, and 0.5–0.6 s on the field.

## Implementation

- `c1_overlay.apply(img, t, duration)` draws onto the composite. `ui_style.py`
  holds the palette, fonts and antialiased primitives. Lines are drawn at 4x and
  box-filtered. Text and lines are placed at sub-pixel positions, so tracking does
  not step.
- The planet ID tracks the planet layer through `compose_c1`'s push and truck. It
  reads the layer's depth from the live scene, falling back to 0.04, and
  `c1_core.PLANET` / `PLANET_DIAMETER`. It reads no traffic, station or ship
  position.
- **Toggle:** `C1_UI=0` renders the clean plate. `C1_UI=planet` or
  `C1_UI=captions` renders one part only. Unset or `1` renders both.
  `c1_overlay.ENABLED` overrides the variable from code.
- Rendering is deterministic: the same frame renders byte-identical twice.

**Font:** Bahnschrift, Windows' bundled DIN-style variable face (SemiBold for the
name and tags, Regular for captions). It is Microsoft-licensed with Windows. It is
used here only to rasterise review frames and is never copied into the repo or
shipped. To use another face, drop a `.ttf` into `ui_fonts/` and it takes
precedence. Orbitron (SIL OFL 1.1) is the open face behind Starsector's own
heading fonts, and the closest match. Fetching it is a download the owner has not
yet approved.

## At runtime

The storyboard has the arrival play as layered images in the game's renderer, not
as a video. So these frames are a specification, not an asset. At runtime the
captions and planet ID should be drawn live by the mod from the same catalog
timings, with the game's bitmap fonts (`Fonts.ORBITRON_*`, `INSIGNIA_*`) and the
`MarineOpsThemes` palette. That keeps them sharp at any resolution and localisable
through the catalog text. That probably wants a portrait-less "cinematic caption"
component beside `story-subtitle.mlx` and `company-subtitle.mlx`, not the boxed
battle panel.

## Owner decisions (2026-10-16)

- The planet label stays at three parts: JANGALA, then CORVUS / HEGEMONY.
- Orbitron (SIL OFL 1.1, `ui_fonts/`) is approved and sets the planet name and
  speaker tags. Captions stay in a plain face for legibility. Orbitron has no middle
  dot, so the separators are slashes.
- Captions continue into C2 for lines 7 and 8, by the lead's call, for consistency
  with C1.

## Open questions for the owner

1. Should the planet ID name the system and owner (CORVUS · HEGEMONY), or the
   name alone? Starsector's own tooltips also give the type ("Jungle"). It was
   dropped here to keep the label short.
2. Should C2 carry the same captions for lines 7–8 (yes, by this reasoning), and
   should line 9 get an "(off channel)" tag?
3. Should a player who skips the arrival find the hail transcript in the docking
   dialog's text log?
4. May I fetch Orbitron (OFL) for review renders, so they match the in-game
   heading face?
5. Caption sync is proportional to text length within each take. Measuring each
   take's leading silence would tighten page changes by a few tenths of a second.
