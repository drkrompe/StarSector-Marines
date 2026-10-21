"""Hand props for ship crew work motions: guide and export.

    python art-source/crew-work-props/props.py guide    # writes ../templates/crew-work-props-guide.png
    python art-source/crew-work-props/props.py export   # raw/props.png -> mod/graphics/battle/crew-work-props/

The guide is the geometry authority: one flat grey silhouette per prop on a
1536x1024 transparent sheet, each prop pointing UP the image (away from the
holder) and drawn at six times its shipped size. Export cuts each slot out of
the accepted raw, keeps alpha only inside the guide's silhouette, and
downsamples with nearest-neighbour sampling to the marine layer scale, where
150 pixels is one shoulder width.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
GUIDE = ROOT / "art-source/templates/crew-work-props-guide.png"
RAW = HERE / "raw/props.png"
OUT = ROOT / "mod/graphics/battle/crew-work-props"
SCALE = 6
GREY, DARK, LIGHT = (128, 128, 128, 255), (96, 96, 96, 255), (160, 160, 160, 255)

# name -> (slot box on the guide, shipped size). Slot boxes hold the prop centred.
SLOTS = {
    "crate": ((0, 0, 640, 1024), (84, 66)),
    "wrench": ((640, 0, 940, 1024), (22, 76)),
    "knife": ((940, 0, 1240, 1024), (16, 62)),
    "ladle": ((1240, 0, 1536, 1024), (24, 70)),
}


def centred(slot, size):
    x0, y0, x1, y1 = slot
    w, h = size[0] * SCALE, size[1] * SCALE
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    return cx - w // 2, cy - h // 2, cx + w // 2, cy + h // 2


def guide():
    sheet = Image.new("RGBA", (1536, 1024), (0, 0, 0, 0))
    d = ImageDraw.Draw(sheet)
    # A stores crate seen from above: lid with a raised rim and two cross battens.
    x0, y0, x1, y1 = centred(*SLOTS["crate"])
    d.rectangle((x0, y0, x1, y1), fill=GREY)
    d.rectangle((x0 + 24, y0 + 24, x1 - 24, y1 - 24), fill=LIGHT)
    d.rectangle((x0 + 24, (y0 + y1) // 2 - 14, x1 - 24, (y0 + y1) // 2 + 14), fill=DARK)
    # A spanner: open jaw at the top, long handle, ring end at the bottom.
    x0, y0, x1, y1 = centred(*SLOTS["wrench"])
    cx = (x0 + x1) // 2
    d.rectangle((cx - 22, y0 + 90, cx + 22, y1 - 80), fill=GREY)
    d.ellipse((x0, y0, x1, y0 + 130), fill=GREY)
    d.rectangle((cx - 20, y0, cx + 20, y0 + 60), fill=(0, 0, 0, 0))
    d.ellipse((x0 + 6, y1 - 120, x1 - 6, y1), fill=GREY)
    d.ellipse((cx - 22, y1 - 82, cx + 22, y1 - 38), fill=(0, 0, 0, 0))
    # A cook's knife: blade at the top, darker grip at the bottom.
    x0, y0, x1, y1 = centred(*SLOTS["knife"])
    cx = (x0 + x1) // 2
    d.polygon([(x0, y1 - 130), (x0, y0 + 40), (cx, y0), (x1, y0 + 40), (x1, y1 - 130)], fill=LIGHT)
    d.rectangle((cx - 30, y1 - 130, cx + 30, y1), fill=DARK)
    # A ladle: bowl at the top, handle down to the hand.
    x0, y0, x1, y1 = centred(*SLOTS["ladle"])
    cx = (x0 + x1) // 2
    d.ellipse((x0, y0, x1, y0 + (x1 - x0)), fill=GREY)
    d.rectangle((cx - 12, y0 + (x1 - x0) - 10, cx + 12, y1), fill=DARK)
    GUIDE.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(GUIDE)
    print("wrote", GUIDE)


def export():
    raw = Image.open(RAW).convert("RGBA")
    shape = Image.open(GUIDE).convert("RGBA")
    if raw.size != shape.size:
        raw = raw.resize(shape.size, Image.LANCZOS)
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (slot, size) in SLOTS.items():
        # One shipped pixel of margin all round, so the outline drawn on the
        # silhouette's edge is not cut off by the crop.
        x0, y0, x1, y1 = centred(slot, size)
        box = (x0 - SCALE, y0 - SCALE, x1 + SCALE, y1 + SCALE)
        size = (size[0] + 2, size[1] + 2)
        art = raw.crop(box)
        # Keep only what lies inside the silhouette, grown by the outline's width:
        # the guide owns the shape and the generation draws its outline on the edge.
        mask = shape.crop(box).getchannel("A").filter(ImageFilter.MaxFilter(9))
        alpha = Image.composite(art.getchannel("A"), Image.new("L", art.size, 0), mask)
        art.putalpha(alpha)
        art.resize(size, Image.NEAREST).save(OUT / f"{name}.png")
        print("wrote", OUT / f"{name}.png", size)


if __name__ == "__main__":
    {"guide": guide, "export": export}[sys.argv[1]]()
