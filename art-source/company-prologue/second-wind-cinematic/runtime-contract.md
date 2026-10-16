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

## The player (shipped)

**Decoder.** JCodec 0.2.5 (pure Java, FreeBSD / BSD-2-Clause: redistributable in a mod), H.264 only,
shaded into the mod jar. No native code, so Windows, Mac and Linux are the same. Container
parsing is the mod's own (`Mp4Index`): Starsector's script loader refuses reflection and file
access (`SecurityException: File access and reflection are not allowed to scripts`), and JCodec's
MP4 box code is reflective. The video is read once through `SettingsAPI.openStream` into memory.
`ScriptSandboxCompatibilityTest` guards the imports; the game is the final judge.

**Video.** `cinematic_export.py` encodes H.264 High, CABAC, **closed 24-frame groups starting on
IDR, no B-frames**, BT.709 limited range, 1920x1080, 24 fps, crf 25. CABAC decodes faster in
JCodec than CAVLC and is smaller. Do not change the profile or group length without re-measuring:
the player decodes whole groups on separate threads, which is how pure Java keeps up.

**Measured** (Zulu 17.0.10, the game's own runtime, inside the live game, 3 decode workers, convert
to RGB included, flat out): the 905-frame C1 decodes in 29.3 s = **30.9 fps**, 1.3x real time on
a loaded machine. One thread alone is about 15 fps. File size: C1 is 15.2 MB for 37.7 s
(0.40 MB/s), so a 90 s arrival is about 36 MB, under the 50 MB target. 24-frame groups cost 48%
less than 12-frame ones (4.7 vs 6.9 MB per 12 s), which is why the group is 24. Decoded
frames are held as YUV (3.1 MB each) in a 48-frame window, so about 150 MB of heap while it plays.
Fallbacks (1600x900, lower bitrate) were not needed.

**Playback.** The wall clock is the master. Frames are decoded ahead, converted to RGB on a
worker, uploaded to one streamed texture inside `GlStateBracket`; a late frame holds the previous
picture and the clock and audio never move. Cues fire against the clock, so sound stays in sync.
Escape or a click skips; the receipt (`sm_jangala_arrival_played`) is written when it starts.
Captions and the lock are drawn live in the game's bitmap fonts (`Fonts.ORBITRON_*`,
`INSIGNIA_LARGE`).

## Adding a shot (C2-C5)

1. Render clean frames to `build/cinematic/<shot>/frames/%05d.png`, 1920x1080, 24 fps, no baked text.
2. Your composer module provides `duration()` (or `DURATION`), `captions()` as
   `(t_in, t_out, speaker_id, text)` with speaker ids `approach` or `castle`, `overlays()` and
   `audio_cues()`. All times are seconds from the shot's own first frame.
   - `overlays()` returns dicts; only `{"kind": "lock", ...}` exists today (see `compose_c1.overlays()`
     for the shape: `t_in`, `t_out`, `title`, `detail`, and a `track` of `[t, x, y, r]` in source pixels).
     Unknown kinds are ignored, so a new overlay kind needs a player change.
   - `audio_cues()` returns dicts `{"t", "sound", "volume"}`; a bed adds `until` (may run past the shot
     into the next) plus `fade_in` / `fade_out`. `sound` must be in `mod/data/config/sounds.json`
     (add it to `audio.json` and register it there). A cue past your shot's `duration()` is another
     shot's. The hail bed is C1's and runs to the end of the hail; C2 must not add another.
3. Add a row to `SHOTS` in `cinematic_export.py` (folder, module, frames directory).
4. `python art-source/company-prologue/second-wind-cinematic/cinematic_export.py --shot <id>`
   writes `mod/cinematic/jangala-arrival/<id>.mp4` (gitignored) and
   `mod/data/cinematic/jangala-arrival/<id>.json` (checked in). The shot ids and order are in
   `mod/data/cinematic/jangala-arrival/sequence.json`: `c1, c2, entry, c3, c4, c5`.
   `--check` validates without encoding. A shot with no video is skipped at runtime.
5. Redeploy (`deployMod`) and dock at Jangala on a fresh Cerberus start.

## Rules for shot authors

- Keep to the art direction: image-first plates, parallax and scale, and motion chosen
  against the camera (`storyboard-v3.md`). Real 3D renders are welcome where a body must
  turn, as with Second Wind in C1 and the Legion in C2.
- Hold the CLAUDE.md preview rule: anything shown to the owner is 1920x1080 at 100% scale.
- Hard cuts are fine. Shots join at their boundaries, and a transition (the entry) is its
  own shot.
