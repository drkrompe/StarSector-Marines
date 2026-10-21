# Ship dressing (Deficit dressing, first pass)

Built-in ImageGen (via `tools/codex_imagegen.py`) produced the retained 1254x1254
RGBA reskin `ship-dressing.raw.png` from the geometry guide
`art-source/templates/ship-dressing-guide.png` (producer `ship-dressing-guide.py`).
The exact prompt is `ship-dressing.prompt.txt`; references and hashes are in
`ship-dressing.raw.png.provenance.json`. No generated pixels were repainted.

Twelve slots on a 3-column grid (418 px columns at the returned size):

- Rows 1-2, 2x2-cell floor overlays: `grime`, `condensation`, `debris`,
  `cable-spill`, `hull-patch`, `buckled-plate`.
- Rows 3-4, 2x1-cell edge pieces: `dead-panel`, `conduit`, `grille`,
  `buckled-panel`, `bracing`, `cable-run`. Each one's bulkhead is the TOP edge
  of its frame; the deck planner turns it onto the wall it hugs.

Export with `tools/authoring.cmd tileset_export -` and `{"name":"ship-dressing"}`
on stdin. Registry (`TileRegistry.BUILTIN_TILESETS`) and the provider catalog
both declare the exported sheet.

These are presentation only: the deck scene draws them through
`ShipDeckDressing` / `ShipDressingRenderSystem`; they are never added to a map's
doodads and own no navigation, cover or sight. Which pieces show is the ship's
per-core condition (`CoreDressing`). First pass for art review; not final.

`createSnapshots -Psnapshot=second-wind-dressing` renders the deck at all
Deficit, each core restored alone, all Restored, and the haze control.
