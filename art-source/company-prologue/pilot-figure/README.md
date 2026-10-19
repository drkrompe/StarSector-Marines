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

- **The helm yoke** (owner, 2026-10-19: a futuristic steering wheel, as on the Rodger Young
  or the Aliens dropship) is part of this package because it moves with him: a housing
  bolted under the forward console's sloped face (clear of the helm's screen), a graduated
  column that telescopes out of it rising toward him, and on its head a wheel with two rubber
  grips (his left one taped). It turns to roll and pushes or pulls to pitch. The column is how
  the helm fits its pilot (owner, 2026-10-19: a pilot sits there for hours): stowed against the
  console, drawn out to where his hands rest with his back in the seat.
- **Solved poses and the moves between them.** Relaxed: sat back against the seat,
  forearms on the armrests, hands loose over their ends. Reaching: leaned in to take the
  stowed wheel. Flying: sat back with his elbows bent at his sides (owner, 2026-10-19: fully
  extended arms read badly), hands round the grips of the drawn-out yoke; the yoke pushed and
  turned are poses of their own, so his hands follow it, and a half-drawn pose keeps them on
  the grips while he draws it out. Each limb is a two-bone solve to an ankle on the dais or a
  wrist target.
- **The action** is keyed on every frame of C2 and its entry (24 fps, frame = C2 seconds x
  24), eased, on the rig and the yoke together: relaxed with the yoke stowed; leaning in to it
  over 8.3-8.8 s; drawing it out as he sits back over 8.8-9.45 s; pushed over 9.5-10.4 s as the
  de-orbit starts at 9.6 s; a small turn to port and back over 10.7-12.5 s; eased back over
  12.5-13.2 s as the pitch-down ends; letting go and settling over 13.5-14.7 s, the yoke left
  drawn out where he set it. On top of that, small life (owner, 2026-10-19): a slow breath,
  and where he looks, shared by neck, head and eyes with the eyes leading. He looks ahead at
  the Legion, follows each fighter flight past to port (3.0 and 4.2 s), glances down at the
  console, turns to port as the Legion comes abeam (8.0 s), looks ahead to the yoke, checks
  the slits as it rises, and once he has let go watches it climb out of them.
  The windows are constants in `build_pilot.py`. The C2 blockout and render set the scene
  frame per sample, so the bridge shots play it; the bridge review takes `--frame`.
- **Modelled parts:** the collar, the harness, the belt, the headset and the patches. They are
  fitted to the flying pose. The harness, belt, collar and patches are then skinned to the rig
  with the weights of the nearest coverall vertex, so they follow the cloth in both poses; the
  headset rides the head bone. The patches are cut from the coverall's own surface.
- **Getting the .blend:** rebuild it, or copy it from Drive (`starsector/builds/pilot/<commit>/`).
  The build needs MPFB 2.0.17 and the MakeHuman system-asset pack.

## Licensing

Everything MakeHuman supplies here is CC0, from the system-asset pack MPFB installs:

- **Body:** the base mesh, the default rig and its weights, and the face targets.
- **Modelled here:** the yoke, collar, harness, belt, patches and headset.
- **Assets:** `high-poly` eyes with the `brown` eye texture, `eyebrow009`, `eyelashes01`,
  `short01` hair, the `male_casualsuit02` top and trousers as the coverall, and `shoes03` boots.
- **Skin:** the `middleage_caucasian_male` texture.

MPFB, the Blender add-on that builds him, is GPL. Its output is not covered by that licence.
Everything else is modelled in `build_pilot.py`.
