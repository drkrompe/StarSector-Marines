# C3 FIRST OPEN SKY: miners see sky

Shot C3 of `storyboard-v3.md`: 12 seconds at 1920x1080 / 24 fps, silent. Miners who
grew up inside an asteroid see open sky for the first time, through the cabin window,
as Second Wind descends over Jangala's forest. `c3-layout.md` is the shared camera,
sun, scale and layer sheet, with the open questions.

This is groundwork and a **blockout animatic**, not finished plates. It salvages the
approved forest still (`realism-lookdev/landscape-realism-v1.png`), cut into four
depth bands, behind the approved painted cabin re-viewed at C3's camera.

| Piece | Source |
|---|---|
| Camera and cabin guides | `c3_cabin_guide.py` (Blender, greybox + depth), `c3_cabin_render.py` (Blender, the painted cabin re-viewed at C3's pitch) |
| Cabin head | Codex completion `assets/c3-cabin-top-v1.png` (brief in `prompts/`, provenance beside it) |
| Depth bands and fill maps | `c3_bands.py` writes `guides/c3-band-*.png`, `c3-inpaint-*.png`, `c3-bands.json` |
| Blockout | `compose_c3.py` writes `review/*.png`; `--movie` adds `build/c3-blockout.mp4` (ignored) |

## Build

From this directory:

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe' --background --factory-startup --python-exit-code 1 --python c3_cabin_guide.py -- --pitch -9
& 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe' --background --factory-startup --python-exit-code 1 --python c3_cabin_render.py -- --pitch -9
python c3_bands.py
python compose_c3.py            # review stills
python compose_c3.py --movie    # plus build/c3-blockout.mp4
```

Both Blender scripts open retained scenes read-only and never save them.

## Review stills

`review/c3-01-pickup.png` … `c3-04-out.png` are four moments of the shot;
`c3-05-bands.png` tints each layer; `c3-06-parallax-proof.png` overlays the first and
last frames so each layer's slide reads as an offset. All are 1920x1080 at 100%.

## Limits

- The plates are the 1672-px master upscaled; the plate rule is 3840. Resolution is a
  separate pass (`c3-layout.md`, open question 6).
- The sky is one plate on the horizon layer; its upper clouds should slide faster
  (open question 4).
- The Atlas card is the earlier painted style, and the flock is a procedural stand-in.
- Valley mist is still baked into the bands, so the drifting mist cards double it
  until the de-misted bands exist.
- The cabin head's v1 completion is lifted into place, stretching the jamb paint
  between y = 77 and 213 (open question 3).
