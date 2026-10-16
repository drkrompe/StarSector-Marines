# Arrival cinematic: runtime contract

Status: ACTIVE
Written: 2026-10-16

The arrival (C1, C2, entry transition, C3, C4, C5) plays in the game's own renderer as
layered images, not as a video (`storyboard-v3.md`, Decisions). Every shot is authored
in Python and previewed offline; the game plays a baked export of the same thing. This
sheet is the seam between the two, so that a shot can be built without knowing the
player and the player without knowing any shot.

## What a shot module provides

Each shot's composer (`compose_c1.py`, `compose_c2.py`, ...) exposes:

- `DURATION`: seconds, or `duration()` when it is derived from audio.
- `draw_list(t)`: the frame at time `t` as a back-to-front list of draws, with the camera
  already applied, on a 1920x1080 reference frame. Each draw is a dict:
  - `key`: a string naming the image. The same key always means the same pixels, so
    the exporter stores each image once. A rendered sequence uses one key per frame.
  - `image`: the PIL RGBA image, at the resolution it should be stored at.
  - `x`, `y`: the top-left corner on the reference frame, in pixels (floats).
  - `scale`: reference-frame pixels per image pixel. Uniform scale only. **No rotation,
    no skew, no per-pixel distortion**: the images-first rule.
  - `alpha`: 0..1 opacity multiplier.
  - `blend`: `"normal"` or `"add"`.
  - `tint` (optional): an (r, g, b) multiplier, 0..1, for a whole-layer grade over time
    such as the entry glow lighting the cabin.
- `frame_image(t)`: the preview frame. It **must** composite `draw_list(t)` and nothing
  else, so the preview and the game cannot disagree.
- `captions(...)` and `audio_cues()`: see below. Screen overlays such as captions and the
  planet lock are not baked into images; the runtime draws them.

## Audio and captions

- `audio_cues()` returns `(t, sound_id, volume)` tuples. `sound_id` is an entry in
  `mod/data/config/sounds.json` (or the mod's equivalent registry) whose file is under
  `mod/sounds/story/...`, produced by `:asset-pipeline:processAudio` from `audio.json`.
  A bed or loop is a cue too, with an optional `until` time.
- Captions are `(t_in, t_out, speaker_id, text)` taken from the dialogue catalog and the
  real clip lengths, the way `c1_overlay.py` times them. The runtime draws them in the
  game's own fonts and the `ui-overlay.md` style.
- The C1 planet lock is a runtime overlay too: it needs only the planet's screen centre
  and radius over time, which the export samples.

## The export

`cinematic_export.py` (owned by the runtime workstream) imports a shot module, samples
`draw_list` at 24 fps, writes each distinct image once, and writes one JSON timeline per
shot into the mod. Its output layout, image format and texture budget are the runtime
workstream's call, documented here when chosen. Shots never write runtime files
themselves.

## Rules for shot authors

- Keep everything translate-and-scale. If something must turn or deform, render it as a
  sequence (one key per frame) or an animation sheet.
- Store images no larger than they will ever appear on a 1920x1080 frame, at most about
  1.15 times that. Texture memory in the game is finite.
- Prefer few large layers plus small moving cards. A shot should stay within about 64
  distinct images, not counting rendered sequences, and sequences should be cropped
  tight to their subject.
- Camera: a shot may push (scale about a focus) and truck (translate by depth), and
  nothing else. Apply it inside `draw_list`.
