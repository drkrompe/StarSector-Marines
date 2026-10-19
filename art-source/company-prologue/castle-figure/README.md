# Castle figure

Status: ACTIVE — grey blockout, posed for C2
Written: 2026-10-19

Castle as a 3D character for shots on Second Wind's bridge (C2, `c2-layout.md`): a
MakeHuman body built in Blender by the MPFB extension, rigged with MakeHuman's default
skeleton and posed at the port comms bank. A moving camera keeps a 3D figure consistent
from frame to frame; close shots are repainted over its frames with Codex ImageGen
(`geometry-first-imagegen.md`), so the figure owns silhouette, proportion, pose and
light, and the repaint owns face, hair and cloth finish. Her look is the portrait's
(`art-source/portraits/castle/prompt.txt`, `art-source/hq-command-deck/castle-v2.brief.txt`).

| File | What it is |
|---|---|
| `build_castle.py` | Builds her headless and writes `castle-figure.blend` (gitignored). `python build_castle.py`. |
| `castle_review.py` | Workbench review: two turnarounds and the bridge framings, 1920x1080. `python castle_review.py --out DIR`. |

## Using her

`sw_bridge_interior.figures()` appends her in place of the 'Bridge figure Castle'
mannequin whenever `castle-figure.blend` exists, at the mannequin's spot (10.9, 3.25,
floor 16.6, facing +Y, in Second Wind units); without it, or with `castle=False`, the
mannequin is drawn. `c2_bridge_blockout.py`, `c2_bridge_render.py` and
`bridge_interior_review.py --figures` all go through it.

To place her elsewhere, append the collection **Castle figure** and move or turn only
its empty **Castle figure root**: she is in metres, z up from her soles, facing the
root's +X. Under a hull scaled to metres per unit, scale the root by 1 / m-per-unit.

## Requirements

Blender 5.2 with the MPFB extension (2.0.17 tested) and MakeHuman's system-asset pack
installed through MPFB. The script refuses to run, naming what is missing, otherwise.
Only the build needs them: the .blend opens in any Blender, `--factory-startup` included.
A Mac without MPFB takes the .blend from Drive, `starsector/builds/castle/<commit>/`.

## Licensing

MakeHuman's base mesh, targets, rigs, skins, clothes, hair and other system assets are
CC0 (released by Data Collection AB, Joel Palmius and Jonas Hauquier in 2020; each asset
file says so). MPFB, the plug-in, is GPL-3.0; the meshes it outputs are not covered by
that. The bun, collar and headset are modelled here. No other assets are used.

## Open

- Hair: the pack has no bun; `short04` (swept back) carries a modelled bun. The repaint
  does the rest.
- Boots: `shoes02` reads as low shoes, not work boots.
- Materials are flat colours, not MPFB's textured skin (whose textures live in one
  machine's Blender profile). A finished look comes from the repaint, not from Cycles.
