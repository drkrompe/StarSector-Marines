# Runtime-composed true-overhead marine

This prototype contains four independent, single-frame sprites. None is a sprite sheet:

1. `marine-foot.png` — one reusable abstract foot blob, instantiated twice.
2. `marine-rifle.png` — one arm-free rectangular weapon mass.
3. `marine-actor.png` — one static helmet-and-shoulders actor mass.
4. `marine-muzzle-flash.png` — one transient flash attached at the rifle muzzle.

The default draw order is both feet, rifle, actor, then the optional muzzle flash. The
rifle therefore sits beneath the actor. Walking, recoil, weapon placement, and firing are
all runtime transforms; there are no baked animation frames.

`marine-topdown.json` defines each sprite's pivot, default offset, draw order, rifle muzzle
attachment, and the two instances of the reusable foot sprite. Coordinates are relative
to the actor rotation pivot, with north along negative Y and positive rotation
counter-clockwise.

Authored poses:

- Idle weapon: offset `(28, -12)`, placing its base between the right shoulder and pectoral,
  then rotated 45 degrees northwest/left for a relaxed right-handed carry across the body.
- Aimed weapon: offset `(26, -18)`, north-facing between the actor center and right
  shoulder, with the barrel extended farther forward.
- Idle feet: offsets `(-18, 35)` and `(18, 35)`, completely occluded by the actor.
- Moving foot tip: briefly move one foot to Y offset `50` while the other stays hidden,
  then alternate. No foot animation is baked into the image.

Run `render_previews.py` with Pillow installed to deterministically rebuild the idle,
aimed, moving, and firing previews from the four runtime sprites.

The original directional sheets and the earlier modular experiment remain unchanged.
