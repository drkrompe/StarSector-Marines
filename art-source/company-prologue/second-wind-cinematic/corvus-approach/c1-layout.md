# C1 CORVUS: shared layout for every layer

Shot C1 of `storyboard-v3.md`: about 38 seconds, 1920x1080, 24 fps. Every
painted layer obeys this sheet, so the pieces composite into one camera.

## Camera and light

- **Camera:** well back from Jangala, above its orbital plane and looking slightly
  down; the action plays in open space with the planet in the background. Jangala
  is a whole distant globe, centre near (1290, 560) and about 760 px across, lit
  gibbous from the upper left with a soft terminator and night side at its lower
  right, and a thin pale-blue limb brightest on the sunlit upper-left edge. The
  camera pushes in slowly (scale 1.00 to 1.07) and trucks right; nothing rotates.
- **Sun:** Corvus, a yellow-white star off frame at the **upper left**. Hard key
  light from the upper left on every hull. Shadow sides fall lower right. A faint
  cool blue planet-shine fill comes from below on surfaces facing the planet.
- **Look:** grounded photographic science fiction. Tactile, weathered industrial
  metal, believable scale, restrained colour. Approved surface-style target:
  `../realism-lookdev/` and `../../second-wind-design-v3.reference.png`
  (materials only). No toy, low-poly or cartoon look. No lens flares, no text.

## Layers, back to front (depth = parallax rate; Second Wind is 0.6)

| Layer | Depth | Screen footprint at 1920x1080 | Owner |
|---|---|---|---|
| Star sky | 0.03 | Full frame plus overscan | done: `assets/star-sky-v1.png` |
| Jangala (3D sphere) | 0.06 | Whole globe, centre near (1290, 560), about 760 px across (`layers/planet.png`, 3840x2160, from `render_planet.py`) | planet agent |
| Distant Hegemony flotilla | 0.10 | Upper-left to upper-centre black space; 8–14 hulls of 25–110 px | fleet agent |
| Jangala Station | 0.12 | Over the planet, centred near (1250, 330), about 280 px tall | station agent |
| Hero Hegemony warship | 0.22 | Mid-ground, upper centre-left, about 340 px long, gliding slowly left | fleet agent |
| Traffic: hero Atlas + small freighters | 0.18–0.30 | Lanes crossing left to right toward the station; Atlas about 300 px long, others 50–120 px | traffic agent |
| Second Wind (entrance) | 0.6 | Enters from off-screen bottom left, recedes toward the planet | lead (Blender render) |

Hull identity comes from the installed game's own sprites (read-only,
`C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core\graphics`):
`ships/onslaught/onslaught_hegemony.png`, `ships/dominator/dominator_hegemony.png`,
`ships/eagle/eagle_hegemony.png`, `ships/enforcer/...`, `ships/atlas/atlas.png`,
`ships/tarsus.png`, `ships/buffalo_dd.png`, `stations/station_jangala.png`. They
are top-down and are identity references only (hull plan, livery, proportions);
paint the hulls in the perspective above, never as flat top-down sprites.

## Deliverables

- Generate with `python tools/codex_imagegen.py` (skill `codex-imagegen`). Each
  call is one paid generation and takes 30–90 s. Keep briefs in `prompts/`,
  masters plus `.provenance.json` in `assets/`, named `<layer>-v<N>.png`.
- Every compositing layer except the sky needs a **true transparent background**.
  Verify alpha (corners transparent, subject opaque) before handing it back.
- Cap your own generations at **four** unless one more clearly fixes a named
  defect. Report the accepted file, its dimensions, where the subject sits in it,
  and every rejected attempt with the reason.
- Do not commit, do not edit files outside your layer, and do not touch
  `compose_c1.py`. The lead integrates.
