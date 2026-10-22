"""Export the Matriarch's rig sprites for the game: the approved hero (round 3 #5) cut as motion study 4
(`exploration/motion_matriarch.py`) cuts it, into a body, a head and its roaring gape, two scythes, four
feet and the shanks.

  python export_matriarch_rig.py

Writes mod/graphics/battle/rav-matriarch/*.png, every sprite head-up (forward is the top of the image):

  body.png             the hero without its head, scythes and feet, over the generated body-under.png that
                       fills what a moved part uncovers; the whole 1024x1536 layer canvas, so the hero's rows
                       and columns are the sprite's, times SCALE
  head.png, gape.png   the straight-ahead head and its roar, rows 0..HEAD_ROWS of the canvas, full width;
                       both pivot at the neck
  scythe-l/r.png       each scythe cut from the hero, cropped to SCYTHE_BOX; it turns about its PIVOT
  foot-fl/fr/hl/hr.png each foot cut from the hero, centred on the foot
  shank-f/h-l/r.png    the leg layers, the hip end at the top, SHANK_W layer px wide

`RavMatriarchComposer` holds the matching numbers (SCALE, HEAD_ROWS, SCYTHE_BOX, PIVOT, NECK, SHANK_W);
keep it in step when anything here changes. The script prints them.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE / "exploration"))
from motion_brute import key, largest_blob, soft_mask, with_alpha  # noqa: E402
from motion_matriarch import (FEET, HEAD_POLY, NECK, PIVOT, RAW, SCYTHE_POLY, SHANK_W, STRAIGHT,  # noqa: E402
                              STRAIGHT_GAPE, poly_mask, trim)

OUT = HERE.parent.parent / "mod" / "graphics" / "battle" / "rav-matriarch"
SCALE = 0.4          # sprite px per layer px
HEAD_ROWS = 672      # the head sprites are the top of the canvas, down past the neck and the soft edge
SCYTHE_BOX = {"L": (330, 300, 520, 640), "R": (670, 230, 930, 600)}


def scaled(img):
    return img.resize((max(2, round(img.width * SCALE)), max(2, round(img.height * SCALE))), Image.LANCZOS)


def placed(name, spec, size):
    k, (sx, sy), (dx, dy) = spec
    g = largest_blob(key(RAW / name))
    g = g.resize((round(g.width * k), round(g.height * k)), Image.LANCZOS)
    lay = Image.new("RGBA", size, (0, 0, 0, 0))
    lay.alpha_composite(g, (round(dx - sx * k), round(dy - sy * k)))
    return lay


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    hero = key(RAW / "hero.png")
    size = hero.size
    ha = np.asarray(hero.getchannel("A")).astype(float)
    head_m = np.asarray(poly_mask(size, HEAD_POLY)) / 255
    blade_m = {k: np.clip(np.asarray(poly_mask(size, p)) / 255 - head_m, 0, 1) for k, p in SCYTHE_POLY.items()}
    keep = 1 - head_m
    for m in blade_m.values():
        keep = keep * (1 - m)
    feet = {}
    for n, ((cx, cy), (rx, ry)) in FEET.items():
        m = np.asarray(soft_mask(size, lambda d, cx=cx, cy=cy, rx=rx, ry=ry:
                                 d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=255), 5)) / 255
        keep = keep * (1 - m)
        feet[n] = (m, (cx, cy), (rx, ry))

    body = Image.new("RGBA", size, (0, 0, 0, 0))
    body.alpha_composite(largest_blob(key(RAW / "body-under.png")))
    body.alpha_composite(with_alpha(hero, ha * keep))
    scaled(body).save(OUT / "body.png")

    for name, spec, out in (("head-straight.png", STRAIGHT, "head.png"),
                            ("head-straight-gape.png", STRAIGHT_GAPE, "gape.png")):
        lay = placed(name, spec, size)
        bottom = lay.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()[3]
        assert bottom <= HEAD_ROWS, (out, bottom)
        scaled(lay.crop((0, 0, size[0], HEAD_ROWS))).save(OUT / out)

    for side, m in blade_m.items():
        cut = with_alpha(hero, ha * m)
        bbox = cut.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
        box = SCYTHE_BOX[side]
        assert box[0] <= bbox[0] and box[1] <= bbox[1] and box[2] >= bbox[2] and box[3] >= bbox[3], (side, bbox)
        scaled(cut.crop(box)).save(OUT / f"scythe-{side.lower()}.png")

    for n, (m, (cx, cy), (rx, ry)) in feet.items():
        r = int(max(rx, ry) + 16)   # past the soft edge; square and centred on the foot
        cut = trim(with_alpha(hero, ha * m)).crop((int(cx - r), int(cy - r), int(cx + r), int(cy + r)))
        scaled(cut).save(OUT / f"foot-{n.lower()}.png")

    for part, path in (("f", "leg-front.png"), ("h", "leg-hind.png")):
        leg = trim(largest_blob(key(RAW / path)))
        leg = leg.crop(leg.getchannel("A").point(lambda v: 255 if v > 60 else 0).getbbox())
        leg = leg.crop((0, 0, leg.width, int(leg.height * 0.80)))     # hip at the top, without the foot
        w = SHANK_W[part.upper()]
        leg = leg.resize((w, int(leg.height * w / leg.width)), Image.LANCZOS)
        scaled(leg).save(OUT / f"shank-{part}-l.png")
        scaled(leg.transpose(Image.FLIP_LEFT_RIGHT)).save(OUT / f"shank-{part}-r.png")

    for p in sorted(OUT.glob("*.png")):
        print(p.name, Image.open(p).size)
    print("SCALE", SCALE, "HEAD_ROWS", HEAD_ROWS, "SCYTHE_BOX", SCYTHE_BOX, "PIVOT", PIVOT, "NECK", NECK,
          "SHANK_W", SHANK_W)


if __name__ == "__main__":
    main()
