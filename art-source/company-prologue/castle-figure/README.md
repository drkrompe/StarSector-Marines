# Castle figure

Status: ACTIVE — grey blockout, animated for C2
Written: 2026-10-19

Castle as a 3D character for shots on Second Wind's bridge (C2, `c2-layout.md`): a
MakeHuman body built in Blender by the MPFB extension, rigged with MakeHuman's default
skeleton and posed at the port comms bank, where she plays her part in C2 (an action on C2's
frames: listening with her hands on the bank, looking out at the Legion as it comes abeam at
8.0 s, her hand to the earpiece and the bank keyed for her read-back at 9.76-13.76 s, a small
brace as the deck pitches down from 9.6 s, and her hand down after). A moving camera keeps a 3D figure consistent
from frame to frame; close shots are repainted over its frames with Codex ImageGen
(`geometry-first-imagegen.md`), so the figure owns silhouette, proportion, pose and
light, and the repaint owns face, hair and cloth finish. Her look is the portrait's
(`art-source/portraits/castle/prompt.txt`, `art-source/hq-command-deck/castle-v2.brief.txt`).
The modelling reference is `turnaround/`: two ImageGen sheets made from the concept and the
portrait (owner, 2026-10-19), a full-body turnaround (front, both sides, back) and a head
turnaround, with their briefs and provenance. They are the only views of her back, which is
how C2 sees her.

| File | What it is |
|---|---|
| `build_castle.py` | Builds her headless and writes `castle-figure.blend` (gitignored). `python build_castle.py [--hair mesh\|curves] [--face portrait\|plain] [--out PATH]`. |
| `castle_dress.py` | Her clothes after the turnaround: MakeHuman's `male_casualsuit05` (jacket over a shirt, trousers) sorted by UV island into duty jacket, undershirt and trousers, its lapels and shirt collar taken off, each part a weathered procedural fabric over MakeHuman's normal and AO maps (the jacket faintly quilted); `shoes03` boots; modelled flap pockets on the left sleeve and both thighs (the jacket's own chest and hip pockets are kept), and a belt with a squared buckle. |
| `castle_face.py` | Her face, half-smile and textured skin, eyes, brows and lashes: MakeHuman face targets from her portrait, the expression units masked to her left, MakeHuman's CC0 diffuses (packed into the .blend). `--face plain` skips it. |
| `face_review.py` | Her face beside her portrait, and a front / three-quarter / profile turn, in EEVEE and Workbench. `python face_review.py --out DIR [--blend PATH] [--prefix NAME-]`. |
| `face_texture.py` | Experiment: an ImageGen repaint of her face skin, two ways. `guides` renders the head's UV island and three orthographic albedo views into `face-texture/guides/`; Codex (Astra, `--effort ultra`) repaints each from `face-texture/prompts/` into `face-texture/generated/`; `measure` finds where eleven landmarks landed (NCC patch match, mm) and registers each generation with a robust affine; `bake` writes a UV-direct and a projected skin and a .blend for each under `build/castle-face-texture/`; `compare` renders both on her beside the portrait. Not yet adopted into `castle_face.py`. |
| `castle_review.py` | Review sheets, 1920x1080: turnarounds, the C2 close-shot distance, and the bridge framings. `python castle_review.py --out DIR [--blend PATH] [--engine workbench\|eevee] [--sheets ...] [--prefix NAME-] [--no-bridge]`. |

## Hair

`--hair curves` (the default) is a groom after the head turnaround: the hair drawn back off
the forehead with some lift, gathered into a loose twisted knot at the back of the head a
little above the ears, a few wavy tendrils escaping at both temples and the nape, and silver
streaks at the temples (most on her right). `--hair mesh` is MakeHuman's `short04` cards plus
a modelled UV-sphere bun, a quick stand-in. The groom is Blender hair curves on a hidden copy
of the scalp skin, which is the skin under `short04` without its sideburns, with the hairline
thinned over its outer rings. The script lays the guides: one per scalp vertex, gathered into
the bun; arcs wound round the knot's axis over a lumpy core mesh; and the tendrils, kept off
the skin. Blender's
own procedural hair node groups, bundled with Blender (CC0), do the rest: Attach, Interpolate
(about 45,000 strands), Clump, Frizz, Hair Curves Noise, Set Hair Curve Profile and Duplicate
(bun and loose hair). The armature deforms the scalp copy, and the groom follows it through Deform Curves on Surface.
This matters at the nape, which is weighted to the neck: hair fixed rigidly to the head bone
sinks into the skin when she bows her head. Each strand's `grey` attribute marks the temples
and a sprinkle of stray grey strands. The material is a Principled Hair BSDF with two outputs:
melanin for Cycles, and direct colour for EEVEE, which ignores the melanin parametrisation.
Workbench draws the flat `diffuse_color`.

## Using her

`sw_bridge_interior.figures()` appends her in place of the 'Bridge figure Castle'
mannequin whenever `castle-figure.blend` exists, at `sw_bridge_interior.CASTLE_AT` (the
comms bank's forward panel, facing +Y, in Second Wind units); without it, or with `castle=False`, the
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
that. The bun, collar, pockets, belt and headset are modelled here. The curves groom uses Blender's bundled
procedural hair node assets (CC0). No other assets are used.

## Open

- Hair: the front hairline is still a hard edge set high at the temples (it is `short04`'s),
  where the turnaround's is soft, wavy and lower.
- Clothes: `shoes03` are ankle boots, not the turnaround's tall laced boots; the jacket is a
  blazer cut with its lapels removed rather than a zip jacket, and its cuffs are not rolled.
- Every material keeps a flat diffuse_color for Workbench; the images are packed into the
  .blend so it opens without MPFB.
- The face is MakeHuman's, shaped toward the portrait: it carries her proportions and
  expression for the repaint, not a likeness. It is narrower and smoother than the
  turnaround's.
- The earpiece stays on her right ear, with her right hand at it while she speaks, although the
  portrait and cards wear it on her left (owner, 2026-10-19).
