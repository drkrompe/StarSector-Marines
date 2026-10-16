"""Place figure cards into deck plate v3 at their stands from the Blender layout.

Run from the repository root:  python -I art-source/hq-command-deck/compose.py castle

Each card is scaled so its head-to-sole height equals the stand's projected height,
anchored by the centre of its soles on the stand's feet point, and cut with that
stand's occluder matte (`blender/deck_blockout.py -- --masks`) so the table and
anything else nearer than the person covers them as it should.

Writes <figure>-v2.composite-review.png and, as a second look, a 3x crop around the
figure.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
PLATE = "deck-plate-v3.master.png"
LAYOUT = "deck-guide-v5.layout.json"
CARDS = {"castle": "castle-v2.master.png"}


def place(plate, card, stand, matte):
    a = np.asarray(card.getchannel("A")).astype(float) / 255
    ys, xs = np.nonzero(a > 0.9)
    top, bottom = ys.min(), ys.max()
    soles = xs[ys > bottom - 0.04 * (bottom - top)]
    sole_x = (soles.min() + soles.max()) / 2
    feet, head = stand["feetPx"], stand["headPx"]
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
        card = Image.open(HERE / CARDS[name]).convert("RGBA")
        matte = Image.open(HERE / "masks" / f"{name}.occluder.png")
        plate, (ox, oy, w, h), scale = place(plate, card, layout["people"][name], matte)
        print(name, "box", ox, oy, w, h, "scale", round(scale, 4))
    out = HERE / f"{'-'.join(names)}-v2.composite-review.png"
    plate.save(out)
    cx, cy = ox + w // 2, oy + h // 2
    half_w, half_h = 240, 160
    crop = plate.crop((cx - half_w, cy - half_h, cx + half_w, cy + half_h))
    crop.resize((crop.width * 3, crop.height * 3), Image.LANCZOS).save(out.with_name(out.stem + "-crop.png"))


if __name__ == "__main__":
    main(sys.argv[1:] or list(CARDS))
