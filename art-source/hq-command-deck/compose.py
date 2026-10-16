"""Place figure cards into the deck plate at their stands from the Blender layout.

Run from the repository root:  python -I art-source/hq-command-deck/compose.py castle

Each card is scaled so its painted extent spans the stand's projected height, anchored
by the centre of its lowest pixels on the stand, and cut with that stand's occluder
matte (`blender/deck_blockout.py -- --masks`) so the table and anything else nearer
than the person covers them as it should.

With one figure, writes <figure>-v2.composite-review.png and a 3x crop around it; with
several (the default is every card), writes deck-v4.composite-review.png.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
PLATE = "deck-plate-v4.master.png"
LAYOUT = "deck-guide-v6.layout.json"
# card, and where its lowest painted pixels sit in metres above the stand's floor: 0 for
# soles; a seated figure seen from behind shows no feet, so its bottom is the seat.
CARDS = {
    "castle": ("castle-v2.master.png", 0.0),
    "commander": ("commander-v2.master.png", 0.0),
    "comms-left": ("comms-left-v2.master.png", 0.45),
    "comms-right": ("comms-right-v2.master.png", 0.45),
    "quartermaster": ("quartermaster-v2.master.png", 0.0),
}
FOCAL, CY = 1050.0, 170.0


def place(plate, card, stand, matte, bottom_m):
    a = np.asarray(card.getchannel("A")).astype(float) / 255
    ys, xs = np.nonzero(a > 0.9)
    top, bottom = ys.min(), ys.max()
    soles = xs[ys > bottom - 0.04 * (bottom - top)]
    sole_x = (soles.min() + soles.max()) / 2
    feet, head = stand["feetPx"], stand["headPx"]
    # the bottom anchor rises bottom_m above the floor at the stand's depth
    feet = [feet[0], feet[1] - FOCAL * bottom_m / stand["z"]]
    scale = (feet[1] - head[1]) / (bottom - top)
    w, h = round(card.width * scale), round(card.height * scale)
    # premultiply before resampling so transparent pixels' colour cannot bleed in
    rgba = np.asarray(card).astype(float) / 255
    rgba[..., :3] *= rgba[..., 3:]
    small = np.stack([np.asarray(Image.fromarray((rgba[..., i] * 255).astype(np.uint8)).resize((w, h), Image.LANCZOS))
                      for i in range(4)], -1).astype(float) / 255
    ox, oy = round(feet[0] - sole_x * scale), round(feet[1] - bottom * scale)
    base = np.asarray(plate.convert("RGB")).astype(float) / 255
    cover = 1 - np.asarray(matte.convert("L")).astype(float) / 255
    x0, y0 = max(ox, 0), max(oy, 0)
    x1, y1 = min(ox + w, base.shape[1]), min(oy + h, base.shape[0])
    src = small[y0 - oy:y1 - oy, x0 - ox:x1 - ox]
    keep = cover[y0:y1, x0:x1, None]
    region = base[y0:y1, x0:x1]
    base[y0:y1, x0:x1] = region * (1 - src[..., 3:] * keep) + src[..., :3] * keep
    return Image.fromarray((base * 255).round().astype(np.uint8)), (ox, oy, w, h), scale


def main(names):
    layout = json.loads((HERE / LAYOUT).read_text(encoding="utf-8"))
    plate = Image.open(HERE / PLATE)
    for name in names:
        path, bottom_m = CARDS[name]
        card = Image.open(HERE / path).convert("RGBA")
        matte = Image.open(HERE / "masks" / f"{name}.occluder.png")
        plate, (ox, oy, w, h), scale = place(plate, card, layout["people"][name], matte, bottom_m)
        print(name, "box", ox, oy, w, h, "scale", round(scale, 4))
    out = HERE / ("deck-v4.composite-review.png" if len(names) > 1 else f"{names[0]}-v2.composite-review.png")
    plate.save(out)
    if len(names) > 1:
        return
    cx, cy = ox + w // 2, oy + h // 2
    half_w, half_h = 240, 160
    crop = plate.crop((cx - half_w, cy - half_h, cx + half_w, cy + half_h))
    crop.resize((crop.width * 3, crop.height * 3), Image.LANCZOS).save(out.with_name(out.stem + "-crop.png"))


if __name__ == "__main__":
    main(sys.argv[1:] or list(CARDS))
