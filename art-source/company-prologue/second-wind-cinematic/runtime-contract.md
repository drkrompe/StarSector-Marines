# Arrival cinematic: runtime contract

Status: ACTIVE
Written: 2026-10-16
Updated: 2026-10-16 — switched from in-game layered playback to pre-rendered video played by a player the mod ships (owner's call).

The arrival (C1, C2, entry transition, C3, C4, C5) ships as pre-rendered video played by
a video player bundled in the mod. Starsector cannot play video itself, so the mod carries
its own decoder. This supersedes `storyboard-v3.md`'s "layered images in the game's own
renderer" decision. The art direction is unchanged: image-first plates with parallax and
scale, moving elements chosen against the camera. Only the delivery changed.

This sheet is the seam between the shots and the player, so a shot can be built without
knowing the player and the player without knowing any shot.

## What a shot delivers

Each shot folder delivers:

- **Frames:** 1920x1080, 24 fps, sRGB, rendered by the shot's own composer
  (`compose_c1.py`, `compose_c2.py`, ...) as a numbered PNG sequence under
  `build/cinematic/<shot>/frames/`, and a review mp4 beside it.
- **No baked text.** Captions, speaker tags and the C1 planet lock are drawn by the player
  at runtime in the game's fonts and the `ui-overlay.md` style, so they stay sharp,
  translatable and switchable. A shot provides them as data:
  - `captions()`: `(t_in, t_out, speaker_id, text)` from the dialogue catalog and the
    real clip lengths, the way `c1_overlay.py` times them;
  - `overlays()`: anything else drawn over the picture, such as C1's planet lock, given
    as the planet's screen centre and radius over time.
- **Audio as cues, not a mix.** `audio_cues()` returns `(t, sound_id, volume)` (and
  optionally `until` for beds and loops). `sound_id` names a mod sound built by
  `:asset-pipeline:processAudio` from `audio.json`. The player schedules the cues
  against the video clock, so dialogue stays separately mixable and subtitled.
- **Duration** in seconds: `DURATION`, or `duration()` when it is derived from audio.

## The packager and the player

`cinematic_export.py` (owned by the player workstream) takes every shot's frames, encodes
the one shipped video in the codec and profile the player decodes, and writes the cue,
caption and overlay timeline JSON into the mod. The player workstream records the chosen
codec, resolution, bitrate, file layout and the measured decode speed here.

The player plays the shots in order as one sequence. Missing shots are skipped, so shots
can land one at a time. Escape or a click skips, and the arrival plays once only.

## Rules for shot authors

- Keep to the art direction: image-first plates, parallax and scale, and motion chosen
  against the camera (`storyboard-v3.md`). Real 3D renders are welcome where a body must
  turn, as with Second Wind in C1 and the Legion in C2.
- Hold the CLAUDE.md preview rule: anything shown to the owner is 1920x1080 at 100% scale.
- Hard cuts are fine. Shots join at their boundaries, and a transition (the entry) is its
  own shot.
