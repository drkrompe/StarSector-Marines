# Modular alien actor

Generic aliens and swarm runners use the infantry compositor's continuous body
facing, independent head look, alternating-foot locomotion, and alternating
contact swipes. Two rotated instances of the same neutral fore-claw tuck under
the carapace at rest. On impact, one moves forward and is painted above the body
so its three talons stay readable. Aliens never draw firearm layers;
`alien.png` remains the live load-failure fallback and `alien-dead.png` remains
the corpse sheet.

Runtime layers are `body.png`, `head.png`, `foot.png`, and `fore-claw.png`. The
accepted built-in ImageGen outputs are retained under `sources/`;
`build_assets.py` crops their alpha, normalizes body/head/foot to the marine
compositor's 150/72/38 px registration widths, normalizes the claw to 96 px
high, and emits ignored idle/impact/retraction composition previews under
`build/sprite-previews/alien/`.

Run `python build_assets.py` after changing a retained source.
