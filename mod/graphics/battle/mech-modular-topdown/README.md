# Modular top-down heavy mech

This set replaces directional live-mech frames with one true-overhead runtime
composition. The legacy sheet remains the load-failure fallback and continues
to provide corpse art.

`chassis.png` defines the master visual width. `foot.png` is derived from the
accepted hull's bottom armor tab (neither generated leg is used). `chaingun-arm.png`,
`srm-pod.png`, and `lrm-pod.png` each represent one reusable part. The shoulder
weapons use classic walking-tank / Patriot-style rectangular canister boxes:
only recessed missile caps are visible near the forward lip. The renderer
mirrors the foot and chaingun for the opposite side. Rear/anchor regions are
intentionally buried beneath the chassis: locomotion exposes only a toe tip,
chaingun recoil exposes no joint, and shoulder pods show only their overhead
barrel tops and narrow forward muzzle edge.

Current hull-relative authored sizes are approximately 30% width for each
chaingun arm, 30% for the SRM pod, and 36% for the heavier LRM pod.

The stock chassis uses `chassis.png`, whose four former circular rear sockets
are plated over. `chassis-socketed-variant.png` preserves the original hull for
future customization. The stock loadout displays the larger pod box on both
shoulders; `srm-pod.png` remains an installable compact variant. The rejected
long six-rail roll is retained as `linear-cannon-variant.png` because it reads
well as a future energy or linear weapon.

Run `python build_assets.py` after replacing a retained source. The generated
previews use the same hull-relative placement model as the runtime composer and
are written beneath `build/sprite-previews/mech/` rather than packaged with the mod.
