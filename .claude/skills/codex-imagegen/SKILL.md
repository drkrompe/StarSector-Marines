---
name: codex-imagegen
description: Generate images (cinematic plates, ship and traffic cards with transparency, texture maps, concept art) from a Claude agent by driving Codex CLI's built-in image_gen through tools/codex_imagegen.py. Use whenever a task needs ImageGen/image_gen output and this session has no image-generation tool of its own.
---

# Codex image generation from a Claude agent

A Claude session here has no image generator. Codex CLI does (`image_gen`, enabled
by default), and the earlier cinematic art was made with it. Reach it through the
wrapper, never by calling `codex exec` by hand:

```bash
python tools/codex_imagegen.py --prompt <brief.txt> --out <master.png> \
  --ref <guide.png>="Geometry/layout authority: ..." \
  --ref <reference.png>="Material/style only; not layout"
```

Each call is **one paid generation** and nothing retries. A normal run takes
30–90 seconds. Run it from the session worktree; it writes only `--out` and
`<out>.provenance.json`.

## Why the wrapper, and the failure it prevents

`codex exec` reads stdin to EOF whenever stdin is not a terminal, even when the
prompt is passed as an argument. From an agent's shell, stdin is a pipe that never
closes. The call then sits silent until the caller's timeout, which once cost 15
minutes and looked like a slow generation. **Ten minutes with no output is a
hung call, not a slow one.** The wrapper feeds the prompt on stdin, applies a
timeout, and:

- runs in an empty scratch directory with `-s read-only`, so the Codex agent
  cannot touch the repository;
- prefixes the brief with an instruction to call `image_gen` exactly once and
  do nothing else;
- finds the image by the run's own **thread id**
  (`~/.codex/generated_images/<thread>/`). Other Codex sessions write to the
  same folder, so "newest file" has already returned another session's ship;
- copies the lossless native master and records the prompt as sent, each
  reference's role, hashes, dimensions, Codex version, token usage and
  elapsed time.

`--adopt-thread <id>` records provenance for an image an earlier run produced,
without generating again. Find the id in that run's `thread.started` event.

## Writing the brief

The brief file holds the art direction only; the wrapper adds the tool
instruction and numbers the references. Follow `cinematic-production` and its
`references/style.md` for the project look. Things that matter for image_gen:

- **Give every reference one explicit role**: geometry/layout, identity (hull
  plan, livery), or material/style only. A reference without a role gets copied
  wholesale.
- **Ask for transparency explicitly** for any layer that composites: "true
  transparent background, including negative spaces; no ground, shadow slab,
  border or text". Masters come back RGBA when that works. Check the alpha
  before using the layer.
- **Say what is a separate layer** and must not appear: "no planets, stations or
  ships; those are separate layers." Otherwise the model fills the frame.
- **Name the canvas**: the widest landscape canvas for plates; 2:1 for
  equirectangular texture maps; a long strip for traffic cards. Output sizes vary
  by request: 1916x821, 1672x941, 2048x768 and 1254x1254 have all come back.
- **Vanilla identity**: for Starsector hulls, attach the install's sprite
  (`starsector-core/graphics/ships/...`) as the hull-plan reference, and ask
  for the perspective and lighting you need. A top-down sprite is not a pose.

## Control pose with a silhouette, not with words

A brief that only *describes* an orientation gets whatever the model prefers. The
first fleet sheet came back with every hull on its own heading. When pose,
heading, placement or scale matter, **build the silhouette first and have
ImageGen fill it in**:

1. Block the subject out in Blender. For a Starsector hull, an image-alpha-masked
   plane of its vanilla top-down sprite, given thickness, has real volume in
   perspective. Pose it in 3D and render it from the shot's camera as a
   transparent guide with plain grey shading. Render at two to three times the
   final pixel size.
2. Brief it as sketch-to-render. The guide is the exact silhouette, pose,
   heading, placement and scale authority, to be filled in and never moved; the
   vanilla sprite is hull-plan and livery identity only; a finished card is
   material reference only.
3. Overlay the result on its guide and check registration before using it.

**Layer by depth.** A formation is several layers (far escorts, mid capitals, a
near hero), each its own guide and generation. That gives each layer its own
parallax rate and haze, and keeps any one generation small enough to obey its
guide.

## After generation

- Look at the master before building on it. A numeric success is not art
  approval.
- Keep masters, briefs and provenance in the shot's `art-source/` package. Keep
  scratch views out of the repository.
- One generation can be wrong in ways a prompt edit fixes. Change the brief and
  run again as a new version (`-v2`); never overwrite an accepted master without
  `--replace` and a reason.
