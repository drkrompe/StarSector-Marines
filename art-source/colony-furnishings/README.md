# Compact colony furnishings

Six one-cell modules: sealed metal transit case, personal locker, desk/readout,
service cabinet, kitchenette, and orderly retail shelf. Each has intact,
damaged and destroyed states. Created with the built-in ImageGen tool from the
retained geometry guide; the existing main-stores raw sheet supplied material
reference. Exact prompt: `reskin.prompt.txt`.

Producer: `../templates/colony-furnishings-guide.py`; guide canvas 1536 x 768.
Accepted raw: `../tilesets/colony-furnishings.raw.png`, actual 1774 x 887 RGBA.
The authoring document uses 288-square placement frames registered together
across states. No per-state trimming/recentering, chroma key, or painted repairs.
The normal tileset exporter preserves native alpha and downsamples to 128px per
cell. Re-export with `tools/authoring.cmd tileset_export -`, stdin
`{"name":"colony-furnishings"}`. Both runtime catalogs register the export.

Runtime consumers: compact building recipes, retail fixtures and stockrooms,
warehouse storage groups, authored building stamps, and intact generation pools.
Public seating reuses mess-galley stools. Loose door props are removed from
normal pools; real entrances retain colony material-matched opening skins.
PropDamage owns all six damage transitions; destroyed states have no cover or
ballistic height and release their physical footprint through the normal callback.

Evidence: `createSnapshots -Psnapshot=compact-buildings` renders three building
sizes across four themes, two ordinary city neighborhoods and all states at four
rotations, using the production Java2D renderer at 1920 x 1080. Retained PNGs in
`review/` are construction previews, not live game captures. Unit checks cover
all four frontages at two sizes, floor/anchor connectivity, intact pools, damage
release, retail doorway turning clearance and existing housing/factory behavior.
