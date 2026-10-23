"""Cut the phase-1 ground-turret candidates out of their raws, mechanically, into build/ground-turret-art/<direction>/.

Not shipped art: the output feeds the `ground-turret-look` snapshot suite only. Everything is cut by the guide's
own frames (make_guide.py), and each piece's opaque extent is registered to its guide's (the guide's alpha stands in
when a raw came back without transparency), so a generation cannot move or resize a silhouette. Exported at 128 px a cell, the field mortar's density.

  collar.png   1x1 base   (128x128)
  autogun.png  head, 1.25 cells (160x160), pivot centre, muzzle up
  cannon.png   head, 1.25 cells (160x160)
  plinth.png   3x3 base   (384x384), with a code-drawn cast shadow down and to the right inside the footprint
  heavy.png    head, 3 cells (384x384)

Heads get a faint round contact shadow, which reads the same at every heading.

Usage: python art-source/ground-turrets/export.py [direction ...]
"""
import sys
from pathlib import Path

from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
OUT = ROOT / "build" / "ground-turret-art"
PPC = 128
DIRECTIONS = ("brutalist", "colony", "earthwork")


def masked(raw, guide):
    """The raw at the guide's size; when it came back without transparency, the guide's silhouette is its alpha."""
    raw = raw.convert("RGBA").resize(guide.size, Image.LANCZOS)
    if raw.getchannel("A").getextrema()[0] >= 250:
        raw.putalpha(guide.getchannel("A").filter(ImageFilter.MaxFilter(5)))
    return raw


def contact_shadow(size, radius_frac, strength=110):
    shadow = Image.new("L", (size, size), 0)
    from PIL import ImageDraw
    r = size * radius_frac
    ImageDraw.Draw(shadow).ellipse([size / 2 - r, size / 2 - r, size / 2 + r, size / 2 + r], fill=strength)
    shadow = shadow.filter(ImageFilter.GaussianBlur(size * 0.05))
    layer = Image.new("RGBA", (size, size), (10, 12, 10, 0))
    layer.putalpha(shadow)
    return layer


def fitted(raw, guide, box):
    """The raw's object in this frame, moved and scaled so its opaque extent matches the guide's (per axis).

    Generations do not hold absolute scale for small pieces on a mostly empty canvas (the brutalist collar came back
    1.2x its guide), so each piece is registered to its guide box rather than trusted where it landed."""
    r = raw.crop(box)
    gb = guide.crop(box).getchannel("A").point(lambda v: 255 if v > 32 else 0).getbbox()
    rb = r.getchannel("A").point(lambda v: 255 if v > 32 else 0).getbbox()
    out = Image.new("RGBA", r.size, (0, 0, 0, 0))
    if gb is None or rb is None:
        return out
    piece = r.crop(rb).resize((gb[2] - gb[0], gb[3] - gb[1]), Image.LANCZOS)
    out.alpha_composite(piece, (gb[0], gb[1]))
    return out


def head(sheet, guide, box, size, shadow_frac):
    piece = fitted(sheet, guide, box).resize((size, size), Image.LANCZOS)
    out = contact_shadow(size, shadow_frac)
    out.alpha_composite(piece)
    return out


def plinth_with_shadow(piece, size):
    piece = piece.resize((size, size), Image.LANCZOS)
    offset = int(round(0.26 * size / 3))
    a = piece.getchannel("A")
    shifted = Image.new("L", a.size, 0)
    shifted.paste(a, (offset, offset))
    shadow_alpha = shifted.filter(ImageFilter.GaussianBlur(size * 0.012)).point(lambda v: v * 0.62)
    out = Image.new("RGBA", (size, size), (14, 16, 18, 0))
    out.putalpha(shadow_alpha)
    out.alpha_composite(piece)
    return out


def sand_khaki(image):
    """Recolour a painted piece to sand-and-khaki paint, keeping its luminance (its painted detail)."""
    from PIL import ImageOps
    alpha = image.getchannel("A")
    lum = ImageOps.autocontrast(image.convert("L"), cutoff=1)
    out = ImageOps.colorize(lum, black=(38, 32, 22), mid=(150, 128, 88), white=(222, 206, 166)).convert("RGBA")
    out.putalpha(alpha)
    return out


def export(direction):
    target = OUT / direction
    target.mkdir(parents=True, exist_ok=True)
    cx = [18 + 500 * i + 250 for i in range(3)]
    collar_box = (cx[0] - 200, 312, cx[0] + 200, 712)
    if direction == "earthwork":
        # The image tool refused the earthwork points sheet; the collar is its own generation and the heads are the
        # brutalist heads recoloured to sand-khaki (heads share one geometry across directions).
        guide = Image.open(HERE / "guides" / "earthwork-collar-guide.png")
        collar = fitted(masked(Image.open(HERE / "raw" / "earthwork-collar.raw.png"), guide), guide, collar_box)
        guide = Image.open(HERE / "guides" / "brutalist-points-guide.png")
        pts = sand_khaki(masked(Image.open(HERE / "raw" / "brutalist-points.raw.png"), guide))
    else:
        guide = Image.open(HERE / "guides" / f"{direction}-points-guide.png")
        pts = masked(Image.open(HERE / "raw" / f"{direction}-points.raw.png"), guide)
        collar = fitted(pts, guide, collar_box)
    collar = collar.resize((PPC, PPC), Image.LANCZOS)
    collar.save(target / "collar.png")
    head(pts, guide, (cx[1] - 250, 262, cx[1] + 250, 762), 160, 0.22).save(target / "autogun.png")
    head(pts, guide, (cx[2] - 250, 262, cx[2] + 250, 762), 160, 0.24).save(target / "cannon.png")
    guide = Image.open(HERE / "guides" / f"{direction}-hardpoint-guide.png")
    hp = masked(Image.open(HERE / "raw" / f"{direction}-hardpoint.raw.png"), guide)
    plinth_with_shadow(fitted(hp, guide, (0, 128, 768, 896)), 3 * PPC).save(target / "plinth.png")
    head(hp, guide, (768, 128, 1536, 896), 3 * PPC, 0.2).save(target / "heavy.png")
    print("exported", direction, "->", target)


def main(argv):
    for direction in argv or DIRECTIONS:
        if not (HERE / "raw" / f"{direction}-hardpoint.raw.png").exists():
            print("skip", direction, "(no raw yet)")
            continue
        export(direction)


if __name__ == "__main__":
    main(sys.argv[1:])
