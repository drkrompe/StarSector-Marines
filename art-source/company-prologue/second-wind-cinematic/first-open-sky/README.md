# C3 FIRST OPEN SKY: miners see sky

Shot C3 of `storyboard-v3.md`: 12 seconds at 1920x1080 / 24 fps, no dialogue. Miners who
grew up inside an asteroid see open sky for the first time, through the cabin window,
as Second Wind descends over Jangala's forest. `c3-layout.md` is the shared camera,
sun, scale and layer sheet, with what was built and the remaining open questions.
It delivers to `runtime-contract.md`: frames, review mp4, and `captions()` (empty),
`overlays()` (empty) and `audio_cues()` from `compose_c3.py`.

| Piece | Source |
|---|---|
| Camera and cabin guides | `c3_cabin_guide.py` (Blender, greybox + depth), `c3_cabin_render.py` (Blender, the painted cabin re-viewed at C3's pitch) |
| Cabin head | `assets/c3-cabin-top-v2.png`, from the drawn-rim edit target `c3_cabin_head.py` writes |
| Depth bands, masks, fill maps | `c3_bands.py` writes `guides/c3-band-*.png`, `c3-inpaint-*.png`, `c3-bands.json` |
| Land plates (2x master, de-misted) and mist matte | `c3_plates.py`, from `assets/c3-tile-{0..3}-v1.png` |
| Atlas | `assets/c3-atlas-v1.png`, on the guide `c3_atlas_guide.py` writes |
| Audio sources | `c3_audio.py` writes `asset-pipeline/src/tool/resources/audio/jangala-arrival/jangala_arrival_{cabin_bed,wind}.flac` |
| Production layers | `c3_layers.py` bakes `build/layers/` (ignored) |
| Final frames | `compose_c3.py --movie` |

Every Codex master has its brief in `prompts/` and a provenance record beside it.

## Build

From this directory (the Blender steps only when the guides change):

```powershell
& 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe' --background --factory-startup --python-exit-code 1 --python c3_cabin_guide.py -- --pitch -9
& 'C:/Program Files/Blender Foundation/Blender 5.2/blender.exe' --background --factory-startup --python-exit-code 1 --python c3_cabin_render.py -- --pitch -9
python c3_bands.py
python c3_plates.py prep        # edit targets (only to regenerate tiles)
python c3_plates.py check       # registration of the tile completions
python c3_plates.py assemble
python c3_layers.py
python compose_c3.py            # review stills and contact sheet
python compose_c3.py --movie    # plus every frame, the review mp4 and timeline.json
```

Output: `build/cinematic/c3-first-open-sky/frames/%05d.png`, `c3-first-open-sky.mp4`
(with the audio cues mixed for review) and `timeline.json`, under the repository root.
Run `:asset-pipeline:processAudio` to encode the two cue sounds into the mod.

## Review stills

`review/c3-01-pickup.png` … `c3-05-out.png` are five moments of the shot;
`c3-06-bands.png` tints each layer; `c3-07-contact-sheet.png` is sixteen evenly spaced
frames. All are 1920x1080 at 100%.

## Limits

- The sky is one plate on the horizon layer; its upper clouds should slide faster
  (`c3-layout.md`, open question 1). It is also the 1672-px sky plate upscaled; soft
  cloud tolerates that, but it is the softest thing in frame.
- Behind-band continuations are the 1x salvage extractions; only slivers ever show.
- Cabin head v2 keeps a few mirror-repeated bolts from its draft on the upper jambs.
