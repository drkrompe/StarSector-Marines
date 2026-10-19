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
| `pilot_review.py` | Review sheets, 1920x1080: per pose a turnaround at a grey stand-in of the helm and head and shoulders, then the bridge views of him (helm close-up, C2's forward and port stations turned onto him). `python pilot_review.py --out DIR [--blend PATH] [--engine workbench\|eevee] [--prefix NAME-] [--no-bridge] [--video]`: both poses, a strip of the move, and with `--video` an mp4 of it. |

`sw_bridge_interior.figures` puts him on the helm seat whenever `pilot-figure.blend` exists,
and uses the mannequin otherwise. The package's root is on the dais plating at the seat's axis,
in metres, and he faces +X.

How he is built:

- **Two solved poses and the move between them.** Relaxed: sat back against the seat,
  forearms on the armrests, hands loose over their ends. Working: leaning in from the hips so
  his elbows stay bent, hands flat on the forward console's sloped face (owner, 2026-10-19:
  fully extended arms read badly). His hips are placed on the seat's pan, and each limb is a
  two-bone solve to an ankle on the dais or a wrist target.
- **The action** ('Pilot leans in and back') is keyed on every frame of C2 and its entry
  (24 fps, frame = C2 seconds x 24), eased: relaxed, in to the controls over 8.3-9.3 s before
  the de-orbit starts at 9.6 s, held through the pitch-down, and back over 13.5-14.7 s.
  `LEAN_IN` / `LEAN_BACK` in `build_pilot.py` move it. The C2 blockout and render set the
  scene frame per sample, so the bridge shots play it; the bridge review takes `--frame`.
- **Skin, eyes, brows and lashes** are textured through `castle-figure/castle_face.py`'s
  material helpers.
- **Modelled parts:** the collar, the harness, the belt, the headset and the patches. They are
  fitted to the working pose. The harness, belt, collar and patches are then skinned to the rig
  with the weights of the nearest coverall vertex, so they follow the cloth in both poses; the
  headset rides the head bone. The patches are cut from the coverall's own surface.
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
