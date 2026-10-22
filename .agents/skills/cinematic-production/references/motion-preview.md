# Motion previews

Written 2026-10-22, after Asharu's ships were found flying in lockstep and in straight lines, and
C4's traffic flying with no drive plumes, each only after hours of lit frames.

A motion preview is the whole shot in Blender's Workbench engine: no lighting, no volumes, no
materials, half size, every frame or every second frame, labelled and assembled into an MP4 that
plays at the shot's real speed. It costs a few minutes in the light lane, so it is the first thing
every new or re-timed shot is reviewed on.

## What it shows

- **Every craft in its own flat colour**, named in a legend and tagged on screen. Merchant or
  friendly hulls take cool colours, hostile ones warm. A craft whose pose the shot does not move
  is marked `(held)` in the legend.
- **Planets and ground keep their base image**, shown flat, so the composition against the
  limb, the terrain and the horizon still reads.
- **Effects as cheap proxies** that keep timing and direction: drive plumes as cones whose length
  follows the throttle (none at zero), missiles as short streaks with a trail, gunfire as dashed
  lines, blasts as brief wire shells, fires and smoke as cones, flares and debris as dots,
  shields as wire shells. Lines and plumes are held to a few pixels wide at any range.
- **Burnt in at 1920x1080:** shot id and title, frame number, frame within the shot, timecode,
  preview rate, action-safe (93%) and title-safe (90%) guides, a KEY FRAME badge where the shot's
  approved still falls, the shot's notes, and each craft's screen trail over the last two seconds.
- A shot with no blockout yet is a slate card for its length, so a sequence preview runs at the
  sequence's real timing.

## What it cannot judge

Lighting, shadows and exposure; materials and textures beyond a base colour; volume and plume
look (density, glow, colour); motion blur; depth of field; anything about a finished surface.
Judge those on a lit still or a short lit span after the preview has settled motion and framing.

## Running one

Queue it in the light lane, `machine: any` (render-queue skill). Never render it from a session's
own shell.

- **Asharu**: `battle_blockout.py --motion-preview [--step 2] [--shots S6:S7] [--weave-all]`,
  with `--dry` to stage and key everything without rendering. `asharu_preview.py --job <dir>
  --date <D>` writes the whole-sequence light job with every finished build as a pinned input.
  Drifting ships play `motion.drift` on the cinematic's clock; ships with authored poses hold
  them until a path is authored (`--weave-all` weaves them about their pose instead).
- **An animated Jangala shot**: `motion_preview.py run --out <dir> -- --python <scene>.py --
  --motion-preview --preview-out <dir>`. `c4x_scene.py` is wired; the scene's own animation moves
  the crafts and its thruster markers (`thrust-markers/README.md`) give the plumes.
  `cinematic-tools/motion-preview-job.template.json` is the queue job for it.

Output, in the run's `preview/` folder: `<name>.mp4` (1920x1080), `<name>-web.mp4` (1280 wide,
for a review page), `jpg/` with a 1600 px still of each shot's key moment, `NOTES.md`,
`preview.json` (shots, stills, timing) and the raw half-size frames.

## Wiring another builder

Keep the hook small; put the shot's glue in its own file.

1. Build and pose the shot as the builder already does.
2. `mp = motion_preview.Preview(scene, out, fps, step)`, then `mp.begin_shot(id, title,
   start_s, end_s, key_s=...)`.
3. `mp.craft(name, obj, label, group, pose=...)` for each craft. `obj` is a collection-instance
   empty (baked into one flat mesh) or a hull root (painted in place). Give `pose(t)` (a world
   matrix) when the preview moves it, leave it out when the scene's own animation does.
4. A proxy per effect: `plume`, `tracer`, `missile`, `burst`, `line`, `cone`, `dots`, `shield`.
   Each takes callables of `t`, seconds on the sequence's clock, in world space.
5. `mp.render_shot()` keys every frame and renders the shot as one Workbench animation;
   `mp.end_shot()` clears the proxies. Then, in plain Python, `motion_preview.finish(out, ...)`.

`Preview.hide_effects` hides volumes, objects under thruster markers, effect-named objects and any
materials a shot lists; a builder still hides its own stand-in effects by name.
