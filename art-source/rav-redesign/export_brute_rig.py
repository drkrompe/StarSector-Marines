"""Export the Brute's rig sprites for the game: the approved hero (round 3 #6) cut as motion study 3
(`exploration/motion_brute.py`) cuts it, into a body, a head, four hooves and two shanks.

  python export_brute_rig.py

Writes mod/graphics/battle/rav-brute/*.png, every sprite head-up (forward is the top of the image):

  body.png        the hero without its head and hooves, over the generated headless, legless body
                  (body-nohead.png) that fills what a moved head or hoof uncovers; the whole 1024x1536
                  layer canvas, so the hero's rows and columns are the sprite's, times SCALE
  head.png        the head, rows 0..HEAD_ROWS of the layer canvas; it pivots at the neck (512, 384)
  hoof-fl/fr/hl/hr.png   each hoof cut from the hero, centred on the hoof
  shank-f/h-l/r.png      the shank above each hoof from the generated leg layers, the hip end at the top,
                  SHANK_W layer px wide

`RavBruteComposer` holds the matching numbers (the layer rows and columns, SCALE, HEAD_ROWS, SHANK_W);
keep it in step when anything here changes.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE / "exploration"))
from motion_brute import (HEAD_POLY, HOOVES, RAW, SHANK_W, key, largest_blob, soft_mask,  # noqa: E402
                          with_alpha)

OUT = HERE.parent.parent / "mod" / "graphics" / "battle" / "rav-brute"
SCALE = 0.25         # sprite px per layer px
HEAD_ROWS = 448      # the head sprite is the top of the canvas, down past the neck pivot and its soft edge


def scaled(img):
    return img.resize((max(2, round(img.width * SCALE)), max(2, round(img.height * SCALE))), Image.LANCZOS)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    hero = key(RAW / "hero.png")
    size = hero.size
    ha = np.asarray(hero.getchannel("A")).astype(float)
    head_m = np.asarray(soft_mask(size, lambda d: d.polygon(HEAD_POLY, fill=255), 6)) / 255
    keep = 1 - head_m
    hooves = {}
    for name in ("FL", "FR", "HL", "HR"):
        (cx, cy), (rx, ry) = HOOVES[name[0] + "L"]
        if name[1] == "R":
            cx = 1024 - cx
        m = np.asarray(soft_mask(size, lambda d, cx=cx, cy=cy, rx=rx, ry=ry:
                                 d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=255), 5)) / 255
        keep = keep * (1 - m)
        hooves[name] = (m, (cx, cy), (rx, ry))

    body = Image.new("RGBA", size, (0, 0, 0, 0))
    body.alpha_composite(largest_blob(key(RAW / "body-nohead.png")))
    body.alpha_composite(with_alpha(hero, ha * keep))
    scaled(body).save(OUT / "body.png")

    head = with_alpha(hero, ha * head_m).crop((0, 0, 1024, HEAD_ROWS))
    scaled(head).save(OUT / "head.png")

    for name, (m, (cx, cy), (rx, ry)) in hooves.items():
        r = int(max(rx, ry) + 16)   # past the soft edge; square and centred on the hoof
        cut = with_alpha(hero, ha * m).crop((int(cx - r), int(cy - r), int(cx + r), int(cy + r)))
        scaled(cut).save(OUT / f"hoof-{name.lower()}.png")

    for part, path in (("f", "leg-front.png"), ("h", "leg-hind.png")):
        leg = largest_blob(key(RAW / path))
        leg = leg.crop(leg.getchannel("A").point(lambda v: 255 if v > 60 else 0).getbbox())
        leg = leg.crop((0, int(leg.height * 0.3), leg.width, leg.height))   # the shank, without the hoof
        leg = leg.transpose(Image.FLIP_TOP_BOTTOM)                           # the hip end at the top
        w = SHANK_W[part.upper()]
        leg = leg.resize((w, int(leg.height * w / leg.width)), Image.LANCZOS)
        scaled(leg).save(OUT / f"shank-{part}-l.png")
        scaled(leg.transpose(Image.FLIP_LEFT_RIGHT)).save(OUT / f"shank-{part}-r.png")

    for p in sorted(OUT.glob("*.png")):
        print(p.name, Image.open(p).size)


if __name__ == "__main__":
    main()
