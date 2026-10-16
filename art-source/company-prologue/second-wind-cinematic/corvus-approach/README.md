# C1 CORVUS: arrival over Jangala

Shot C1 of `storyboard-v3.md`, about 38 seconds at 1920x1080 / 24 fps, under hail
lines 1–4. It is image first: painted depth layers that only translate and scale,
plus moving traffic riding its own layers, and Second Wind's entrance from
off-screen bottom left. `c1-layout.md` is the shared camera, sun and layer sheet
every layer was painted to.

| Piece | Source |
|---|---|
| Star sky, Hegemony flotilla and hero warship, Jangala Station | Codex ImageGen masters in `assets/` with `.provenance.json` and briefs in `prompts/` (`tools/codex_imagegen.py`, skill `codex-imagegen`). Near hulls are generated over grey guides rendered under the C1 sun, so pose and light come from the guide; the station and flotilla are graded to that light. |
| Lane traffic | `traffic_render_cards.py` renders vanilla sprites as lit heightfield cards at their real headings under the shared sun (identity references, read from the install, never copied). `c1_traffic.py` flies every body along a 3D path and projects it. |
| Jangala | ImageGen albedo and cloud maps (v2), plus ocean, height and night-light maps derived from them by `planet_maps.py`, on a Blender sphere with a Rayleigh atmosphere volume: `render_planet.py` writes `layers/planet.png`. |
| Second Wind | `render_ship_sequence.py` renders the damaged master as a real 24 fps alpha sequence under the plate camera, flying the path and attitude `c1_ship.py` plans (generated into `layers/second-wind-seq/`, gitignored, about 11 minutes). Its length comes from `metres_per_unit` in `../../second-wind-geometry/states/patched/geometry.json`. Arrival is the patched state: only the starboard engines (3 and 4) are lit. |
| Screen overlay | `c1_overlay.py` (style in `ui_style.py`, reasoning in `ui-overlay.md`): the Jangala sensor lock and hail captions. `C1_UI=0` renders the clean plate. |
| Motion and audio | `compose_c1.py` composites the modules (`c1_core.py` holds the shared camera and parallax model) farthest first, then muxes the locked hail from `../arrival-audio/build_hail_preview.py`. |

## Build

```bash
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background --factory-startup --python render_ship_card.py -- --view entrance
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" --background --factory-startup --python render_planet.py
python render_ship_sequence.py     # Second Wind's sequence (re-run after any c1_ship.py SW_* change)
python compose_c1.py --stills      # review/ stills only
python compose_c1.py               # frames and build/cinematic/c1-corvus/c1-corvus.mp4
```

Run `:asset-pipeline:processAudio` first so the hail clips exist. `--stand-ins`
substitutes vanilla sprites for any painted layer that is missing.

## Limits

- This is a layered animatic of a shot, not runtime integration. The game does
  not play it yet.
- Second Wind renders from the textured full-surface master, rescaled to the
  shared length. The patched `second-wind-geometry` state replaces it once it is
  surfaced; that is a change of `SHIP_BLEND` plus a load-time root.
- Traffic cards are fixed-angle and travel across the frame, per the
  moving-element rules in `storyboard-v3.md`.
