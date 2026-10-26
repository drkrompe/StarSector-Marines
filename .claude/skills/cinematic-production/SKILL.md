---
name: cinematic-production
description: Create and refine Starsector Marines cinematics using the approved grounded realism, Blender blocking, layered parallax, and geometry-controlled reskinning. Use for campaign cinematic storyboards, animatics, environment plates, and moving ship or machinery shots; not ordinary UI art, tactical tilesets, or narration-only work.
---

# Cinematic production

Build cinematics that belong beside this project's campaign stills: tangible,
weathered science fiction, believable scale and motion, and natural scenery.
The Jangala daylight realism proofs are the approved surface-style target.

Work from the repository checkout containing this skill. Paths below resolve
within that checkout; follow its `CLAUDE.md` and session worktree workflow.
Keep authoring files under `art-source/`. This skill does not authorize deploying
assets, changing gameplay, generating narration, or replacing the user's current
shot with a larger production.

## Select the relevant guidance

- Read [style and reference authority](references/style.md) when creating or
  changing imagery. Inspect the actual reference images before art generation.
- Read [parallax and compositing](references/parallax.md) for window views,
  landscape layers, traffic cards, camera projection, ground joins and ownership,
  or motion troubleshooting.
- Read [dynamic objects](references/dynamic-objects.md) for ships, shuttles,
  cranes, landing mechanisms, and any object whose details must survive movement.
- Read [render layers](references/render-layers.md) when staging a shot that will
  go through review rounds or carries effects or a large backdrop.
- Read [motion previews](references/motion-preview.md) before reviewing any new or
  re-timed shot's motion, and when wiring a builder for previews.

The references distinguish demonstrated techniques from work still to be proved.
Do not describe a concept sheet as a registered texture or the approved still
look as an already completed animation.

## Work from story to motion to surfaces

1. Establish the current beat, viewpoint, arrival/repair state, and required
   screen action from the campaign docs and production text. For Jangala, start
   with `art-source/company-prologue/second-wind-cinematic/storyboard-v2.txt`,
   then the campaign documents it names. Resolve exact dialogue against
   `CompanyPrologueScreen` and `mod/data/strings/strings.json`. A camera may ride
   with an unnamed crew member; it need not represent the Commander.
2. Reuse accepted blocking when it already answers the shot. For new mechanics,
   block the camera, object scale, approach, supports and contact before painting.
   For ground additions, verify both one visible surface owner and a continuous
   height join; an overlap-free slab can still be detached from its surroundings.
   Use background Blender with `--factory-startup` and saved source scenes;
   do not overwrite a live unsaved Blender session.
3. Choose the simplest representation that survives the entire camera move:
   distant scenery as depth-separated plates; close silhouettes, rotations,
   disocclusion and physical contact as geometry. A ship card can work in a
   distant fixed-aspect shot; a turning hero ship needs the accepted model.
4. Export the exact camera guide and relevant object/depth masks. Give every
   ImageGen reference an explicit role: geometry/layout, material style, or
   damage state. Use the available `imagegen` skill/tool for generation and edits.
   Generate reusable surfaces/layers, not unrelated replacements for each frame.
5. Composite and render a short representative motion span before completing
   the shot. Check both the visible result and the geometry/registration facts
   affected by the change. Continue an already authorized stage without adding
   approval gates; pause at review when the user explicitly requested it.

## Review motion on a preview first

Every new or re-timed shot's motion and composition is reviewed on a motion preview
before its first lit review render (owner, 2026-10-22: motion is the hardest thing to
judge, so judge it early and nearly free). A preview is the whole shot in Workbench at
half size: each craft a flat colour with a legend, plumes as cones sized by throttle,
effects as timed proxies, frame, timecode, shot id and safe areas burnt in, assembled
into an MP4 at real speed. See [motion previews](references/motion-preview.md).

- Queue it as a light-lane job on `machine: any`, never from a session's shell.
- Show the owner the MP4 and a still per shot, with the decision questions first.
- Use it to judge paths, timing, spacing, speed, attitude and bank, framing, what is
  in frame when, and whether every moving craft's drive is lit. It would have caught
  Asharu's lockstep, straight-line ships and C4's traffic flying with no plumes.
- It cannot judge lighting, materials, volume or plume look, motion blur or depth of
  field. Those wait for a lit still or a short lit span once motion is settled.
- After a re-time, preview again before re-rendering lit frames.

## A lit animatic gates every final

No final frame renders until the owner has accepted a lit animatic of every final shot,
rendered at the commit the finals will use (owner, 2026-10-23, after every Asharu final was
scrapped for guns that did not train, rounds that homed and white orbs that a workbench preview
could not show). The animatic is the finals' own pipeline cut down: the same layers and
composite, quarter size, 12 fps, at most 16 samples, one MP4 per shot plus the sequence joined.
With it goes a self-QA writeup checked against the list in
[motion previews](references/motion-preview.md#lit-animatic): turrets slew, elevate and fire
only at targets; projectiles fly in world space, straight, and only seekers home; no orbs or
discs, projectiles included, and nothing lingering; nothing appears or vanishes in frame; every
moving craft shows its drive; nothing passes through a hull; no pops; every effect in the right
layer with correct occlusion. Finals start only after acceptance, at that exact commit.

Before that, every shot's blockout (motion preview) is watched in the cut, in story order, with its
neighbours (owner, 2026-10-26, after S6d to S7 and S1 to S4 only failed once seen in sequence). The
render director sends each new preview to the render coordinator, who places it in the cut page in
story order and puts it to the owner; a shot whose preview was judged alone, out of sequence, does
not go to final. When a shot changes, preview it together with the shot before and after it.

## Plan render layers before the first final

Decide a shot's layers when it is blocked, not after a final comes back wrong.
Read [render layers](references/render-layers.md) before staging any shot that
will take more than one review round or carries effects. Separate layers by how
often they will change and how much they cost, keep every light source in the
layer whose surfaces it lights, and composite deterministically (owner,
2026-10-22: "it might be good for us to start doing that more"). Both C4's
missing-plume repair and Asharu's backdrop work were composite jobs; planned
layers make that a recomposite in seconds instead of a two-hour frame.

## Every moving craft shows its drive

A craft that moves under power shows plumes that match its motion: aft on its
mains while it travels or accelerates, down on its lift jets while it climbs,
sinks or hovers, and nothing while it is parked (owner, 2026-10-21: "plumes
should match forward momentum or implied acceleration ... We cannot make this
mistake again in cinematics"). C4's first finals let an inbound Shepherd down
through the opening seconds with no drive firing, and had to be repaired with a
composite pass.

- Give every craft thruster markers
  (`art-source/company-prologue/second-wind-cinematic/thrust-markers/README.md`)
  and key their throttle from the motion; `thrust_markers.auto` does this for
  traffic the shot does not name.
- Hang the plume off the markers so it follows the same throttle
  (`port_fleet.far_plume` in an EEVEE shot; the VFX kit's thrusters where the
  hull is linked with its SWFX).
- The shot's build calls `thrust_shots.require_plumes` once every plume is on.
  It refuses to build or render (SystemExit naming the craft and nozzles) when a
  craft's main or lift nozzles thrust on any frame with nothing drawing them. Do
  not work around it: add the plume, or, for a craft whose drives the camera can
  never see (a ship filmed from its own bridge), set `thrust_plume_exempt` on the
  craft with the reason. `CINE_PLUMES=0` is a plumes-off control for review
  builds only; a final (`CINE_FINAL=1`) is refused all the same.
- Plume size follows implied acceleration, and the same guard enforces it
  (`thrust_markers.throttle_faults`, model in `thrust_markers.drive_demand`).
  A steady cruise is a short trim burn (mains at 0.2, never above 0.3 while
  not accelerating). Acceleration and braking raise it. In air, a craft that
  is low and slow, climbing, sinking or hovering rides on its lift jets at a
  moderate throttle. Nothing burns at full over a settled area unless it
  really is accelerating hard. Key traffic with `thrust_markers.auto`, which
  uses this model. A craft keyed off the model on purpose carries
  `thrust_motion_exempt` with the reason. C4's first repair drew a Shepherd
  cruising at a steady 64 m/s with full-length mains, and it had to be redone.
- Review the first seconds of a shot for traffic as well as for the hero: a
  craft at the edge of frame is still in the film.

## Deliver useful evidence

Retain source blend/scripts, raw generated images, exact prompts and reference
roles, actual image dimensions, derived texture settings, frame/time mapping and
the review output. Reuse the existing shot's manifests and validators; they are
examples to adapt, not universal exporters. Do not copy raw assets into the skill.

Present review frames and movies at 1920×1080. Preserve native ImageGen masters;
identify resampled presentation copies without claiming extra native detail.
Select texture resolution by the largest visible screen footprint over the move
plus necessary overscan, then inspect at output resolution. Avoid blanket 4K
upscaling. Use lossless files where alpha matters and measure the encoded movie's
size and visual quality. Shipping resolution/codec and runtime playback remain
separate integration decisions.

For a changed shot, inspect endpoints, intermediate occlusion/contact moments,
and the encoded movie at playback speed. Report what was actually reviewed,
remaining visual limitations, and whether it is a style proof, animatic, textured
motion test, or production-ready asset. A successful numerical report alone
does not establish convincing art or mechanics.
