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
| `build_castle.py` | Builds her headless and writes `castle-figure.blend` (gitignored). `python build_castle.py [--hair mesh\|curves] [--face portrait\|plain] [--out PATH]`. |
| `castle_face.py` | Her face, half-smile and textured skin, eyes, brows and lashes: MakeHuman face targets from her portrait, the expression units masked to her left, MakeHuman's CC0 diffuses (packed into the .blend). `--face plain` skips it. |
| `face_review.py` | Her face beside her portrait, and a front / three-quarter / profile turn, in EEVEE and Workbench. `python face_review.py --out DIR [--blend PATH] [--prefix NAME-]`. |
| `face_texture.py` | Experiment: an ImageGen repaint of her face skin, two ways. `guides` renders the head's UV island and three orthographic albedo views into `face-texture/guides/`; Codex (Astra, `--effort ultra`) repaints each from `face-texture/prompts/` into `face-texture/generated/`; `measure` finds where eleven landmarks landed (NCC patch match, mm) and registers each generation with a robust affine; `bake` writes a UV-direct and a projected skin and a .blend for each under `build/castle-face-texture/`; `compare` renders both on her beside the portrait. Not yet adopted into `castle_face.py`. |
| `castle_review.py` | Review sheets, 1920x1080: turnarounds, the C2 close-shot distance, and the bridge framings. `python castle_review.py --out DIR [--blend PATH] [--engine workbench\|eevee] [--sheets ...] [--prefix NAME-] [--no-bridge]`. |

## Hair

`--hair mesh` (the default) is MakeHuman's `short04` cards plus a modelled UV-sphere bun.
`--hair curves` is a prototype groom styled after `castle-v2.master.png`: a loose, fist-sized
updo high on the back of the crown, hair gathered up into it with some volume, a fringe swept
to her right that hangs at the temple, wisps at her right cheek, and silver sweeping back from
the temples (most on her left). It is Blender hair curves on a hidden copy of the scalp skin,
which is the skin under `short04` without its sideburns, with the hairline thinned over its
outer rings. The script lays the guides: one per scalp vertex, gathered into the bun; arcs
wrapping a lumpy core mesh for the bun; and the fringe and wisps, kept off the skin. Blender's
own procedural hair node groups, bundled with Blender (CC0), do the rest: Attach, Interpolate
(about 45,000 strands), Clump, Frizz, Hair Curves Noise, Set Hair Curve Profile and Duplicate
(bun and loose hair). The armature deforms the scalp copy, and the groom follows it through Deform Curves on Surface.
This matters at the nape, which is weighted to the neck: hair fixed rigidly to the head bone
sinks into the skin when she bows her head. Each strand's `grey` attribute marks the temples
and a sprinkle of stray grey strands. The material is a Principled Hair BSDF with two outputs:
melanin for Cycles, and direct colour for EEVEE, which ignores the melanin parametrisation.
Workbench draws the flat `diffuse_color`. The owner has not yet chosen between the two modes.

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
that. The bun, collar and headset are modelled here. The curves groom uses Blender's bundled
procedural hair node assets (CC0). No other assets are used.

## Open

- Hair: the pack has no bun. Either `short04` (swept back) carries a modelled bun and the
  repaint does the rest, or the curves groom replaces both (see Hair). The owner decides.
- Boots: `shoes02` reads as low shoes, not work boots.
- Clothes, boots and hair materials are flat colours. Skin, eyes, brows and lashes are
  textured (castle_face.py) from MakeHuman's CC0 diffuses, packed into the .blend so it
  opens without MPFB; every material keeps a flat diffuse_color for Workbench.
- The face is MakeHuman's, shaped toward the portrait: it carries her proportions and
  expression for the repaint, not a likeness.
- The earpiece stays on her right ear, with her right hand at it, as C2 poses her, although the
  portrait and cards wear it on her left (owner, 2026-10-19).
