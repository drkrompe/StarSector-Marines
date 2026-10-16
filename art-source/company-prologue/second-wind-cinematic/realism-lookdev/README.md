# Jangala: naturalistic cinematic look

Style direction approved by the user on 2026-10-15 for the accepted parallax
window shot. The visual direction follows
the current campaign stills: grounded cinematic realism, tactile industrial
materials, natural vegetation and convincing daylight. Keep Jangala lush and
welcoming. Wear and physical detail supply the grit.

The project [cinematic-production skill](../../../../.agents/skills/cinematic-production/SKILL.md)
records the reusable style, parallax and geometry-controlled reskinning workflow.

## Retained images

- `window-realism-v1.png`: complete window keyframe for judging the look in context.
- `landscape-realism-v1.png`: matching wide landscape source for future separation.
- `review/`: 1920×1080 JPEG presentation copies; the raw ImageGen masters are unchanged.
- `*.prompt.txt`: the exact two built-in ImageGen prompts.
- `generation.json`: input roles, source paths, hashes and native image dimensions.

Both masters were generated with the built-in ImageGen tool, not a CLI image API.
The review copies use FFmpeg Lanczos resampling for 1080p presentation; this does
not add native detail. Existing layered Blender scenes and movies remain unchanged.
The separate [forest reveal derivative](../forest-reveal-realism/README.md) applies
these masters to layered motion while retaining the Atlas lift. The
[approach continuity review](../sequence-review/README.md) places it beside the
managed-land and working-port excerpts.

## What defines this look

Vegetation has irregular crown sizes, branching glimpses, species variation and
fine foliage appropriate to the camera altitude. Smaller detail softens with
distance. Water reflects the sky and has plausible ripples and irregular banks.
Clouds have volume and luminous edges. Use thin humidity selectively in hollows;
the current proofs still have rather regular mist bands and crisp distant canopy,
which can be softened during the layer pass.

Cabin wear belongs to specific materials: small paint chips, aged welds, fastener
recesses, rubber seals and directional grime. Preserve normal daylight exposure,
readable midtones and green vegetation. Avoid broad visible brush daubs, repeated
rounded tree masses and blanket teal or brown grading.

The window proof uses `parallax-window/review/poster.jpg` as composition reference,
with `cargo-billets.master.png` and `yard-hull.master.png` for material realism.
The wide landscape uses `painted-window/assets/landscape-painted-v2.png` for layout,
the new window proof for style, and the same yard still for physical detail.
Paths above are relative to this cinematic folder or the company-prologue root.
Ship geometry in old stills is not a replacement for the accepted V3 hull.

## Applying the proof to motion

These are regenerated style references, not registered animation textures.
Major framing and landmarks are broadly retained, but treelines, cloud shapes,
riverbanks and small window details differ. Do not insert the complete window
image as a moving background or replace isolated plates without checking edges.

Use the accepted camera, depth groups and layer masks to guide new ImageGen
extractions from the wide master, with generous coverage across the full move.
Repaint the cabin with its own geometric guide and match the Atlas material pass.
Preserve raw alpha, normalize it in the material as in the current parallax study,
and validate registration, occlusion and seams at all output times. Keep the
existing stabilized root and camera motion. The layered-next-scenes coverage
measurements still govern how wide near scenery needs to be.

No revised animation, game assets, narration or runtime integration is included
in this style proof. The user has approved its realism and daylight direction;
registered motion layers and moving hull textures remain separate production work.
