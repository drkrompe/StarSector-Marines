# Runtime-composed true-overhead infantry

Runtime bodies, independently rotating heads, and weapons live under `variants/`.
`marine-muzzle-flash.png` is shared by every layered infantry family. Feet use
`marine-foot.png` except Specter, which has an independent boot texture beside
its torso and helmet. Specter also supplies `shoulder-left.png` and
`shoulder-right.png`, articulated independently over dark joint backing.
Specter's `paints/` directory holds matching casing colors;
its default is slate. `marine-rifle.png` is the retained canonical source used by
`art-source/marine-modular-topdown/build_variants.py` to rebuild the service-rifle layer.

There are no baked directional or animation frames in this asset set. Walking,
look direction, weapon placement, recoil, and firing are runtime transforms described
in `LAYERED_RUNTIME.md`.

Run `python art-source/marine-modular-topdown/build_variants.py` to normalize retained sources, rebuild runtime
layers, and write disposable composition previews beneath
`build/sprite-previews/infantry/`. The legacy directional sheets in the parent battle
directory remain intentional load-failure and corpse-rendering fallbacks.
