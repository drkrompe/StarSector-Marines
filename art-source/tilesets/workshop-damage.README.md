# Workshop damage

Built-in ImageGen reskin of `art-source/templates/workshop-damage-guide.png`,
with `machine-shop.raw.png` as material and machine identity reference.
The deterministic guide producer and exact prompt are retained alongside the
accepted 1536x1024 RGBA source. No generated pixels were repainted.

Six slots: torn hull/deck breach, structural collapse, broken lathe, broken mill,
scorch overlay, welded pressure patch. The authoring document records measured
cuts with transparent margins. The breach's center is deliberately opaque dark
space; outer alpha and small fragment gaps are retained. Both machines keep
their original 4x2 footprints and cover. Other overlays do not add collision.

Export through `tools/authoring.cmd tileset_export -` with
`{"name":"workshop-damage"}` on stdin. Registration belongs to both the built-in
tile registry and the mod provider catalog. Render with
`createSnapshots -Psnapshot=cerberus-start`; the matched ship/detail frames show
all four saved conditions through the production renderer.

The closed workshop is inaccessible while breached. Clearance includes a yard
pressure patch before reopening the hatch. Machinery repair removes soot and
restores equipment; the patch persists as a visible repair scar. This is saved
interior condition, not a simulated atmosphere or a vanilla ship damage event.
