# Irradiated formations

The irradiated world's husks: bleached, hollowed, split shells of whatever once
stood there, char-black inside, with a pale olive glass glaze fused on their
sun-facing tops. `MineralFormation` owns two `HUSK` silhouettes: a pair of squat
husks (`formation.husks`, 4x3) and a chain of three husk spires
(`formation.husk-spires`, 3x5). They place, block and break exactly like the
rock and ice formations of `planet-formations.README.md`, but draw from their
own sheet; the planet sheet and its guide are unchanged.

## Retained production inputs

Made with Codex `image_gen` through `tools/codex_imagegen.py`, following
`art-source/geometry-first-imagegen.md`.

- `HuskFormationTemplate` renders the 768x1152 guide under
  `art-source/templates/irradiated-formations.png` (two columns in enum order;
  rows intact, damaged, destroyed). Unlike the planet guide, standing bodies are
  flat silhouettes: with the planet guide's facet fans, the first pass
  (`irradiated-formations-sources/irradiated-formations-first.png`, prompt
  beside it) came back as star-faceted crystals rather than hollow husks.
- `irradiated-formations.prompt.txt` is the accepted brief, with the guide as
  the only reference. Its 1024x1536 RGBA output is `irradiated-formations.raw.png`,
  provenance beside it.

`irradiated-formations.tileset-authoring.json` is the export authority. The pass
drifts down from row to row, so each standing panel is fitted separately to its
guide frame; debris keeps its column's damaged scale. The note records how. No
alpha was keyed, masked, painted or trimmed. Every visible exported pixel lies
inside a blocked cell, and debris leaves most of its footprint open.

## Export

With the configured `JAVA_HOME`, from the repository root in PowerShell:

```powershell
'{"name":"irradiated-formations"}' | tools/authoring.cmd tileset_export -
.\gradlew.bat :test --tests '*FormationBrushTest' --tests '*MineralDamageTest'
```

The exporter owns `mod/graphics/doodads/irradiated-formations.png` and its
runtime catalog. The sheet is registered in the built-in registry and the
provider catalog. Fog masks come from the code-owned silhouettes; destroyed
variants have none.
