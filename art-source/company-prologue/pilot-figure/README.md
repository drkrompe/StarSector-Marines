# Pilot figure

Second Wind's pilot as a MakeHuman figure, seated at the helm for C2's bridge shot
(`patrol-abeam/c2-layout.md`). He has no name or design anywhere else. The owner calls him
"him", and he is an ordinary crewman of Castle's escaped Claim company: faded, patched olive
flight coverall, webbing harness, worn boots, a cropped haircut and a battered over-ear headset.
The cameras stand to port of him and behind, so the detail goes on his left side, his
shoulders and the back of his head.

| File | What it is |
|---|---|
| `build_pilot.py` | Builds him headless and writes `pilot-figure.blend` (gitignored). `python build_pilot.py [--out PATH]`. |
| `pilot_review.py` | Review sheets, 1920x1080: a turnaround at a grey stand-in of the helm, head and shoulders, then the bridge views of him (helm close-up, C2's forward and port stations turned onto him). `python pilot_review.py --out DIR [--blend PATH] [--engine workbench\|eevee] [--prefix NAME-] [--no-bridge]`. |

`sw_bridge_interior.figures` puts him on the helm seat whenever `pilot-figure.blend` exists,
and uses the mannequin otherwise. The package's root is on the dais plating at the seat's axis,
in metres, and he faces +X.

How he is built:

- **The pose is solved.** His hips are placed on the seat's pan. Each leg is a two-bone solve
  to an ankle on the dais, and each arm is a two-bone solve to a wrist on the forward console's
  sloped face, with the hand turned flat onto it.
- **Skin, eyes, brows and lashes** are textured through `castle-figure/castle_face.py`'s
  material helpers.
- **Modelled parts:** the collar, the harness, the belt, the headset and the patches. They are
  fitted to the posed body and parented to bones. The patches are cut from the coverall's own
  surface, so they follow the cloth.
- **Getting the .blend:** rebuild it, or copy it from Drive (`starsector/builds/pilot/<commit>/`).
  The build needs MPFB 2.0.17 and the MakeHuman system-asset pack.

## Licensing

Everything MakeHuman supplies here is CC0, from the system-asset pack MPFB installs:

- **Body:** the base mesh, the default rig and its weights, and the face targets.
- **Assets:** `high-poly` eyes with the `brown` eye texture, `eyebrow009`, `eyelashes01`,
  `short01` hair, the `male_casualsuit02` top and trousers as the coverall, and `shoes03` boots.
- **Skin:** the `middleage_caucasian_male` texture.

MPFB, the Blender add-on that builds him, is GPL. Its output is not covered by that licence.
Everything else is modelled in `build_pilot.py`.
