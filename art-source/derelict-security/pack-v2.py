"""Cuts the second round's painted sheets into the sprites the game draws (`derelict-security-network.md`, Looks).

    python pack-v2.py --node cabinet --root <overlay dir>           # a node candidate, outside mod/
    python pack-v2.py --node array --post --root <overlay dir>      # with the restyled pop-up post too

--sheet picks the painted sheets, <subject>-<sheet>-raw.png: v2b (the default) is painted after Starsector's own
derelict ship sprites, the owner's style target; v2, painted after the company mechs, was superseded and not kept.

Each sheet (guide-v2.py) is painted once for both lineages, Domain on the top row and contingency on the bottom. A
node is cut from its panel, cropped to its alpha bounds and scaled so its long side is NODE_PX, unrotated, as pack.py
did. The post's parts are cut by the guide's own registration, never by their painted outline, so the head keeps
its pivot on the mount: the base the one-cell square about its panel's centre, scaled to BASE_PX; the folded and
unfolded heads the 1.25-cell square, scaled to HEAD_PX. Written under <root>/graphics/battle/derelict/ at the paths
`DerelictLook` names. The default root is mod/, for when the owner has picked.
"""
import argparse
import importlib.util
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter

HERE = Path(__file__).resolve().parent
MOD = HERE.parents[1] / "mod"
_spec = importlib.util.spec_from_file_location("guide_v2", HERE / "guide-v2.py")
guide = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(guide)

BASE_PX = 128
HEAD_PX = 160
NODE_PX = 192
# How far past the guide's silhouettes a painted post part may reach (its rim and greebles), in guide pixels.
MASK_GROW = 28
POST_FILES = {"base": "post-base", "folded": "post-folded", "unfolded": "post-head"}


def sheet(name):
    img = Image.open(HERE / name).convert("RGBA")
    if abs(img.width / img.height - guide.WIDTH / guide.HEIGHT) > 0.01:
        raise SystemExit(f"{name} is {img.size}, not the guide's {guide.WIDTH}x{guide.HEIGHT} aspect")
    return img


def square(img, cx, cy, side):
    """The square of guide-pixel side {@code side} about guide point (cx, cy), in the painted sheet's pixels."""
    k = img.width / guide.WIDTH
    half = side * k / 2
    return img.crop((round(cx * k - half), round(cy * k - half), round(cx * k + half), round(cy * k + half)))


def fit(img, px):
    scale = px / max(img.size)
    return img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)


def clean(img):
    """Drops the faint haze an image model leaves in empty alpha."""
    img.putalpha(img.getchannel("A").point(lambda a: 0 if a < 12 else a))
    return img


def guide_mask(img, guide_img, grow):
    """Clears painted alpha outside the guide's silhouettes grown by {@code grow} guide pixels, and faint alpha
    outside the silhouettes themselves: the v2b post sheet came back with a smoky haze over its empty canvas."""
    g = guide_img.getchannel("A").point(lambda v: 255 if v else 0).resize(img.size, Image.NEAREST)
    k = img.width / guide.WIDTH
    grown = g.filter(ImageFilter.MaxFilter(2 * round(grow * k) + 1))
    alpha = img.getchannel("A")
    alpha = ImageChops.multiply(alpha, grown)
    faint = alpha.point(lambda v: 255 if v >= 48 else 0)
    alpha = ImageChops.multiply(alpha, ImageChops.lighter(faint, g))
    img.putalpha(alpha)
    return img


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--node", choices=guide.NODES)
    p.add_argument("--post", action="store_true", help="also write the restyled pop-up post")
    p.add_argument("--root", type=Path, default=MOD)
    p.add_argument("--sheet", default="v2b")
    a = p.parse_args()
    out = a.root / "graphics" / "battle" / "derelict"
    out.mkdir(parents=True, exist_ok=True)
    nodes = sheet(f"node-{a.sheet}-raw.png") if a.node else None
    posts = sheet(f"post-{a.sheet}-raw.png") if a.post else None
    if posts is not None:
        posts = guide_mask(posts, guide.post_guide(), MASK_GROW)
    for row, lineage in enumerate(guide.LINEAGES):
        if nodes is not None:
            cx, cy = guide.panel_centre(guide.NODES.index(a.node), row)
            panel = square(nodes, cx, cy, guide.PANEL)
            box = panel.getchannel("A").point(lambda v: 255 if v > 24 else 0).getbbox()
            clean(fit(panel.crop(box), NODE_PX)).save(out / f"node-{lineage}.png")
        if posts is not None:
            for col, part in enumerate(guide.POST_PARTS):
                cx, cy = guide.panel_centre(col, row)
                side, px = (guide.POST_CELL, BASE_PX) if part == "base" else (guide.POST_CELL * 1.25, HEAD_PX)
                img = square(posts, cx, cy, side).resize((px, px), Image.LANCZOS)
                clean(img).save(out / f"{POST_FILES[part]}-{lineage}.png")
    print(a.node, "post" if a.post else "", "->", out)


if __name__ == "__main__":
    main()
