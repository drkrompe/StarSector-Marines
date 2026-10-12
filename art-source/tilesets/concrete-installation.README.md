# Concrete installation materials

Built-in ImageGen reskins, exported through the tileset authoring pipeline.
The geometry comes from `ConcreteInstallationTemplate`, which reuses the
wall/opening/rubble generators with two palette inputs and renders three
flat floor swatches. The original colony guides remain unchanged.

- `concrete-walls`: dark reinforced perimeter and light cast-concrete building shells.
- `concrete-openings`: paired windows and open-door jambs, all four cardinal poses.
- `concrete-rubble`: low fragments with the original sixteen exposed-face masks.
- `installation-floors`: poured concrete, neutral command resin and warmer accommodation resin.

Each sheet retains its exact built-in prompt in `<name>.prompt.txt`, accepted
output in `<name>.raw.png`, full-frame cuts and material pairings in
`<name>.tileset-authoring.json`. Guides are in `art-source/templates/`.
Raw dimensions are 1774x887 for walls/rubble, 1254x1254 for openings, and
2172x724 for floors. The authoring rectangles record inspected registration;
wall cuts exclude gutter antialiasing, openings/rubble retain their full
transparent canvas, and floor cuts stay inside the measured swatch boundaries.
No recoloring, keying or retouching is applied after generation.

Export each sheet with `tools/authoring.cmd tileset_export -`, passing
`{"name":"concrete-walls"}` (and the other three names) on stdin.
Set `JAVA_HOME` according to `CLAUDE.md`. The matching runtime catalog and
PNG files live in `mod/data/tilesets/` and `mod/graphics/tilesets/`.

`createSnapshots -Psnapshot=installation-ground` renders all four planet
palettes and a clearly labeled controlled wall-breach material preview.
It is a render/destruction-path diagnostic, not combat balance evidence.
Opening and rubble tests probe empty regions spatially; floor tests require
opaque exported cells and a valid ground-damage overlay. Floor dressing owns
only packed interiors, aperture cells and fixture underlays. Floor IDs carry
opening fallback pairs so a collapsed jamb does not reveal legacy blue trim.
Dark concrete keeps the perimeter's authored HP; artwork never supplies durability.
