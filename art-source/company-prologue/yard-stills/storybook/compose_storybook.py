"""Joins the yard storybook's layered pages and packs them.

    python compose_storybook.py [--out DIR] [--pack]

Page 1 is a counter background with Castle's hand as a separate cut-out (the
yard pictures are layered: owner, 2026-10-19). The cut-out came back larger than
its placement brief asked, so it is scaled and moved here so the chit she holds
sits at the empty end of the row, the size of the three already down. Pages 2-4
are single generations; pages 2 and 4 are cropped toward their subject (CROPS), the rest are
only resized.

Without --pack it writes full-size composites to --out (default
build/yard-storybook); with --pack it also writes the 1200x800 page JPEGs to
mod/graphics/illustrations/company/ under the storybook's image ids.
"""
import argparse
from pathlib import Path

from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]

# Held chit's centre in the cut-out, and where it must land on the background.
HAND_CHIT = (605, 700)
HAND_TARGET = (1262, 642)
HAND_SCALE = 0.6

PAGES = {
    # storybook image id: (background, optional hand layer)
    "yard-chits": ("p1-counter-bg-v1.png", "p1-castle-hand-v1.png"),
    "yard-tally": ("p2-tally-v1.png", None),
    "yard-road": ("p3-road-v2.png", None),
    "yard-ore-dust": ("p4-ore-dust-v1.png", None),
}


def place_hand(bg, hand):
    w, h = hand.size
    s = HAND_SCALE
    small = hand.resize((round(w * s), round(h * s)), Image.LANCZOS)
    tx = round(HAND_TARGET[0] - HAND_CHIT[0] * s)
    ty = round(HAND_TARGET[1] - HAND_CHIT[1] * s)
    out = bg.convert("RGBA")
    # Soft shade on the counter under the hovering hand: its silhouette, dropped
    # toward the viewer (the light is behind it), blurred and faint.
    shade = Image.new("RGBA", out.size, (0, 0, 0, 0))
    a = small.getchannel("A").point(lambda v: v * 0.42)
    blob = Image.new("RGBA", small.size, (20, 14, 10, 255))
    blob.putalpha(a)
    shade.paste(blob, (tx + 6, ty + 40), blob)
    shade = shade.filter(ImageFilter.GaussianBlur(14))
    out.alpha_composite(shade)
    layer = Image.new("RGBA", out.size, (0, 0, 0, 0))
    layer.paste(small, (tx, ty), small)
    out.alpha_composite(layer)
    return out.convert("RGB")


# Page 2's tally is the point of the picture and sits on the near pillar at the
# frame's left edge; a 1.25x crop toward it gives up the sealed hatch's far side.
# Page 4's sack is the joke; a gentle crop toward the truck drops empty sky.
CROPS = {"yard-tally": (0, 50, 1230, 870), "yard-ore-dust": (50, 110, 1420, 1024)}


def compose(page):
    bg_name, hand_name = PAGES[page]
    bg = Image.open(HERE / bg_name).convert("RGB")
    if page in CROPS:
        bg = bg.crop(CROPS[page]).resize((1536, 1024), Image.LANCZOS)
    if hand_name:
        bg = place_hand(bg, Image.open(HERE / hand_name).convert("RGBA"))
    return bg


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(REPO / "build" / "yard-storybook"))
    ap.add_argument("--pack", action="store_true")
    ap.add_argument("--page", action="append")
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    for page in args.page or PAGES:
        if not (HERE / PAGES[page][0]).exists():
            print(f"skip {page}: no {PAGES[page][0]}")
            continue
        img = compose(page)
        img.save(out / f"{page}.png")
        if args.pack:
            dest = REPO / "mod" / "graphics" / "illustrations" / "company" / f"{page}.jpg"
            img.resize((1200, 800), Image.LANCZOS).save(dest, quality=85, optimize=True)
            print(f"packed {dest.relative_to(REPO)} ({dest.stat().st_size // 1024} KiB)")
        print(f"wrote {out / (page + '.png')}")


if __name__ == "__main__":
    main()
