# Ship wear

Built-in ImageGen produced the retained 1254x1254 RGBA reskin from the geometry
guide in `art-source/templates/ship-wear-guide.png`. Its deterministic guide
producer and exact prompt are retained. The four transparent quadrants contain
boot/cargo scuffs, chipped ochre paint, an old welded plate and a soot scar.
No generated pixels were repainted. The authoring document retains each full
627px slot and its alpha; the standard exporter resizes and packs them.

Export with `tools/authoring.cmd tileset_export -` and `{"name":"ship-wear"}`
on stdin. Registry and provider catalog both declare the exported sheet.

These are sparse, nonblocking floor overlays beneath fixtures on the original
restoration hull. They persist after repairs. Active workshop debris, breach
and machine condition remain separate. Compartment lighting is rendered from
room condition using the shared scene clock, not painted into these sprites.

`createSnapshots -Psnapshot=cerberus-start` renders paired repaired/damaged
rooms, the whole ship, and 44 frames covering an eleven-second lighting cycle.
