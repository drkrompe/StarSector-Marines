# Residential sofa damage

The four directional two-cell residential sofas (`doodad.residential-sofa-h`,
`-back-s`, `-v`, `-back-e`) get damaged and destroyed states, so fire and a
wall-crushing hull break them like the residential beds. Without the pair a
sofa was a fixture nothing could bring down, and a heavy tank stopped at it.

Built-in ImageGen (through `tools/codex_imagegen.py`) reskinned
`templates/residential-sofa-damage-guide.png` using
`templates/residential-sofa-reference.png` as the intact material reference.
The retained Python guide producer `templates/residential-sofa-damage-guide.py`
renders both inputs. The exact prompt is `residential-sofa-damage.prompt.txt`;
the accepted RGBA output is `residential-sofa-damage.raw.png`, with its
provenance beside it.

Columns are back N, S, W, E; rows are damaged and destroyed. ImageGen returned
1774 by 887 pixels in registration with the guide. The authoring document
records full placement frames, including transparent margins, rather than
trimming to opaque bounds. The intact, damaged and debris states are reviewed
through `createSnapshots -Psnapshot=residential-sofa-damage`.

Export with `tools/authoring.sh tileset_export -` and
`{"name":"residential-sofa-damage"}` on stdin. Shipped output is
`mod/graphics/doodads/residential-sofa-damage.png` plus its catalog.
