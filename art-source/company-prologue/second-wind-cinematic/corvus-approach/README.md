# C1 CORVUS: arrival over Jangala

Shot C1 of `storyboard-v3.md`, about 38 seconds at 1920x1080 / 24 fps, under hail
lines 1–4. It is image first: painted depth layers that only translate and scale,
plus moving traffic riding its own layers, and Second Wind's entrance from
off-screen bottom left. `c1-layout.md` is the shared camera, sun and layer sheet
every layer was painted to.

| Piece | Source |
|---|---|
| Star sky, Hegemony flotilla and hero warship, Jangala Station, freighter traffic | Codex ImageGen masters in `assets/` with `.provenance.json` and briefs in `prompts/` (`tools/codex_imagegen.py`, skill `codex-imagegen`). Vanilla sprites were identity references only. |
| Jangala | ImageGen albedo and cloud maps on a Blender sphere with an atmosphere rim: `render_planet.py` writes `layers/planet.png`. |
| Second Wind | `render_ship_card.py` renders the damaged master (`../second-wind-full-surface/`) as alpha cards: `entrance` (astern, engines to camera) and `dorsal`. |
| Motion and audio | `compose_c1.py` places, keys and composites everything, then muxes the locked hail from `../arrival-audio/build_hail_preview.py`. |

## Build

```bash
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background --factory-startup --python render_ship_card.py -- --view entrance
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background --factory-startup --python render_planet.py
python compose_c1.py --stills      # review/ stills only
python compose_c1.py               # frames and build/cinematic/c1-corvus/c1-corvus.mp4
```

Run `:asset-pipeline:processAudio` first so the hail clips exist. `--stand-ins`
substitutes vanilla sprites for any painted layer that is missing.

## Limits

- This is a layered animatic of a shot, not runtime integration. The game does
  not play it yet.
- The entrance card keeps one viewing angle. It holds because the ship travels
  away along roughly the camera's line of sight; a turn would need geometry.
- Traffic cards are fixed-angle and travel across the frame, per the
  moving-element rules in `storyboard-v3.md`.
