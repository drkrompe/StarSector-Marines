# Nessa figure

Status: ACTIVE — grey blockout, two poses
Written: 2026-10-19

Nessa Venn (`character.md`) as a MakeHuman figure, built the way Castle is
(`castle-figure/README.md`): the figure owns silhouette, proportion, pose and light; an
ImageGen repaint owns face, hair and cloth finish (`geometry-first-imagegen.md`). Her
dialogue portrait is `art-source/portraits/nessa-venn.master.png`.

| File | What it is |
|---|---|
| `character.md` | Who she is, and the owner's open decisions. |
| `build_nessa.py` | Builds her headless and writes `nessa-figure.blend` (gitignored). `python build_nessa.py [--pose folded\|slate] [--mask face\|neck] [--out PATH]`. |
| `nessa_review.py` | Turnaround and head sheets, 1920x1080. `python nessa_review.py --out DIR [--blend PATH] [--engine workbench\|eevee] [--sheets ...] [--prefix NAME-]`. |

Poses: `folded` is C5's (standing apart, arms folded); `slate` holds the estimate's work slate
in her right hand, for the Y1 yard picture. `--mask neck` hangs the biofilter under her chin.

Built from MakeHuman's CC0 pack: `male_casualsuit02` (top in coat ochre, trousers dark),
`shoes03`, `short02` hair, `eyebrow010`. The pack has no long coat, so the coat's skirt is
modelled here: an open-fronted flared tube from the hips to below the knee, fitted to her legs
in the rest pose and skinned with the nearest pelvis and leg weights. The stand collar, mask,
filters, strap and slate are modelled and bone-parented.

To place her, append the collection **Nessa figure** and move or turn only **Nessa figure
root**: metres, z up from her soles, facing the root's +X. `yard-stills/y1_guide.py` does this.

## Open

- The mask is a primitive stand-in; the repaint draws the real one.
- Folded hands sit loosely on the arms rather than gripping them.
- Flat colours throughout; no face shaping toward the portrait yet.
- The C5 crowd still draws its own dark-coated stand-in for her.

## Licensing

MakeHuman's assets are CC0; MPFB is GPL-3.0 and its output is not covered by that. The coat
skirt, collar, mask and slate are modelled here.
