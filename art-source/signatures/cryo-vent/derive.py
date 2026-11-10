"""Derive the shipped cryovolcanic vent mound from its ImageGen master.

Usage: python art-source/signatures/cryo-vent/derive.py
Writes mod/graphics/battle/signatures/cryo-vent-mound.png (256x256 RGBA): the master's mound,
its hard silhouette feathered so the outer frost fades into the snowfield it is drawn over.
"""
from pathlib import Path

from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
MASTER = HERE / "cryo-vent-mound-master.png"
OUT = ROOT / "mod" / "graphics" / "battle" / "signatures" / "cryo-vent-mound.png"
SIZE = 256


def main() -> None:
    im = Image.open(MASTER).convert("RGBA")
    bbox = im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    im = im.crop(bbox)
    side = max(im.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.paste(im, ((side - im.width) // 2, (side - im.height) // 2))
    im = square.resize((SIZE, SIZE), Image.LANCZOS)
    alpha = im.getchannel("A")
    # Pull the edge in and blur it: the outer frost thins to nothing instead of ending on a line.
    feathered = alpha.filter(ImageFilter.MinFilter(9)).filter(ImageFilter.GaussianBlur(7))
    im.putalpha(Image.eval(feathered, lambda a: min(255, int(a * 1.05))))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    im.save(OUT)
    print(f"wrote {OUT.relative_to(ROOT)} {im.size}")


if __name__ == "__main__":
    main()
