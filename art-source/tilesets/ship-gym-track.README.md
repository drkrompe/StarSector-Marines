# Ship gym track

The cruise-ship gymnasium's own pieces (`GrandGymFitting`): a seamless running-track
material and a poolside lounger. Codex `image_gen` reskin of
`art-source/templates/ship-gym-track-guide.py` (128 px per cell, layout in its
docstring), with the gym sheet as material reference only.

- Accepted raw: `ship-gym-track.raw.png`, 1254 square RGBA, with its provenance.
- Exact prompt: `ship-gym-track.prompt.txt`.
- Cuts: `ship-gym-track.tileset-authoring.json`. The lounger came back about 15%
  longer than its guide, so it is cut by its own opaque extent padded to its 3:1
  frame (a uniform scale) rather than by the guide frame; the track is one opaque
  interior cut.
- Runtime: `mod/graphics/tilesets/ship-gym-track.png` and matching tileset JSON,
  registered in `TileRegistry.BUILTIN_TILESETS` and the mod catalog.

```powershell
'{"name":"ship-gym-track"}' | .\tools\authoring.cmd tileset_export -
```
