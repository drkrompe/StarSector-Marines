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
