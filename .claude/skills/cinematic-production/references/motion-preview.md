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

**A sequence is not ready for the owner's motion review while any shot is still a slate**
(owner, 2026-10-22). A slate is a working state for your own previews, never something put in
front of the owner. Block every shot before the owner sees the sequence, not only the key-frame
shots:
- **A camera shot** gets its move and every ship's flight, the same spec as a key frame's. In
  Asharu that is `BLOCKED` in `battle_blockout.py`: never lit, but played by the preview.
- **A shot that is a screen rather than a camera** (a playback screen, a title) is a timed 2D
  layout synced to its audio: a timeline entry's `screen(f)` in `motion_preview.finish`.
- **An effect the kit does not have yet** is blocked as a proxy and flagged as new effects work,
  not left out.

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

## Lit animatic

The workbench preview settles motion; the lit animatic settles everything a final shows,
before any final renders (owner, 2026-10-23). Render it as the finals are rendered, at the
commit they will use, with the same flags apart from size, rate and samples: quarter size
(`--percent 25`), 12 fps spans, at most 16 samples, the same layers and composite. One
review-lane job per shot (a whole shot outlasts the light lane's 30-minute cap), each assembled
to an MP4 at 12 fps, then the shots joined into one sequence. Estimate the GPU-hours before
submitting.

Check it yourself before the owner sees it, and write the result down shot by shot:

- **Guns.** Every firing turret traverses and elevates onto its target at its turn rate, fires in
  bursts only while on target, in range and with a clear line, and never at empty space.
  Bursts may walk and miss around a ship (owner, 2026-10-23: "tasteful missing of burst fire
  weapons around a ship is good"); a hit lands where the round's line meets the hull.
- **Rounds.** Projectiles fly in world space, independent of the ship that fired them: once
  out of the muzzle they neither turn nor move with her, and trails stay where they were laid.
  Ballistic rounds and bolts fly straight; only seekers home.
- **No orbs.** No round, bright disc or sphere anywhere, projectiles included: no near-isotropic
  emissive head, glow or light larger than a few pixels. No effect lingers past its life.
- **Nothing pops in or out.** Nothing appears or vanishes in frame: every craft enters and
  exits by moving, a jump is a visible dash, and a shield grows on over its raise time
  (owner, 2026-10-23, S6c's Wolf).
- **Every effect has a visible cause.** Flares go out only against an inbound seeker on screen,
  thrown as it closes (ideally decoying it); a ship with no missile-armed enemy in the shot drops
  none (owner, 2026-10-23, S6d's Hound: a Hound mounts no missiles).
- **Burst rounds fly roughly parallel.** Within a burst each round gets a small, seeded aim
  spread; rounds converging on one point read as a laser (owner, 2026-10-23, S6).
- **No round reads as a dot.** A gun round is a calibre-width streak of finite length on screen
  at every render size (`swfx_guns.MIN_STREAK_PX`), never a point and never a muzzle-to-target
  rod.
- **A beam looks like its weapon.** Only a beam weapon draws a continuous line, in that weapon's
  own vanilla colours (the Mining Laser: pink core, red fringe), never a white rod that reads as
  some other gun (owner, 2026-10-23, S8-S9: "wonky laser effects").
- **No white blob holds.** An HE blast is a short bright flash that breaks into ragged,
  directional debris, fire and smoke: the white-hot core lasts one frame at 24 fps, then the
  flash tears into orange tongues thrown outward (`swfx_burst.burst(tear=True)`, the
  `swfx_impacts.he` default). Step through the frames after each hit: a solid white lobed shape
  on a second frame is a fail (coordinator, 2026-10-23, S6 f658 and f674). `bench_he.py` shows
  the old and the torn flash side by side.
- **Drives.** Every moving craft shows its drive (the `thrust_shots` guard).
- **Solidity.** Nothing passes through a hull: rounds, debris, flares, other ships.
- **Continuity.** No pops: turrets, ships, effects and cameras move smoothly frame to frame.
- **Layers.** Every effect is in the right layer and occluded correctly by the hulls in front.

Then the coordinator shows the owner the MP4s with the writeup. Finals start only after the
owner accepts, at exactly the animatic's commit.
