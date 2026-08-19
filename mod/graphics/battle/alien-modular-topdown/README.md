# Modular alien actor

Generic aliens and swarm runners use the infantry compositor's continuous body
facing, independent head look, and alternating-foot locomotion. They never draw
the firearm layers; `alien.png` remains the live load-failure fallback and
`alien-dead.png` remains the corpse sheet.

Runtime layers are `body.png`, `head.png`, and `foot.png`. The accepted built-in
ImageGen outputs are retained under `sources/`; `build_assets.py` crops their
alpha, normalizes them to the marine compositor's 150/72/38 px registration
widths, and emits an ignored composition preview under `build/sprite-previews/`.

Run `python build_assets.py` after changing a retained source.
