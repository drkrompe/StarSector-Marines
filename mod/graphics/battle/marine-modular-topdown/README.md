# Runtime-composed true-overhead infantry

Runtime bodies, independently rotating heads, and weapons live under `variants/`.
`marine-foot.png` and `marine-muzzle-flash.png` are shared by every layered infantry
family. `marine-rifle.png` is the retained canonical source used by
`variants/build_variants.py` to rebuild the service-rifle layer.

There are no baked directional or animation frames in this asset set. Walking,
look direction, weapon placement, recoil, and firing are runtime transforms described
in `LAYERED_RUNTIME.md`.

Run `python variants/build_variants.py` to normalize retained sources, rebuild runtime
layers, and write disposable composition previews beneath
`build/sprite-previews/infantry/`. The legacy directional sheets in the parent battle
directory remain intentional load-failure and corpse-rendering fallbacks.
