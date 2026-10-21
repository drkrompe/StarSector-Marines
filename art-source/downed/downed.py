"""Downed infantry: pose guide and mechanical export. Run from the repository root.

    python art-source/downed/downed.py guide
    python art-source/downed/downed.py export <outfit> [<outfit> ...]
    python art-source/downed/downed.py pool-guide
    python art-source/downed/downed.py pool-export

A fallen body is drawn the way a live one is: straight down, head toward the sprite's
north, at the layered compositor's shoulder scale (150 source pixels to one shoulder
width), and rotated at runtime to the facing the unit died with. Each pose frame is
FRAME_W x FRAME_H source pixels with the body's centre at the frame centre.

The guide is built from anatomy in shoulder widths, not drawn by hand, so every outfit
shares the same four bodies: 0 face down, 1 on the back with arms flung out, 2 curled
on one side, 3 sprawled with an arm overhead. One generation per outfit reskins all
four; export cuts them back out with nearest sampling, keeping the generated alpha
near the guide figure and nothing far from it.
"""

from pathlib import Path
import math
import sys

from PIL import Image, ImageDraw, ImageFilter

SW = 150
FRAME_W, FRAME_H = 300, 460
POSES = 4
GUIDE_SCALE = 1.2
CANVAS = (1536, 1024)
GUIDE_GRAY = (163, 163, 163, 255)
SUPER = 4
SOURCE = Path("art-source/downed")

# outfit -> directory its downed-<pose>.png files ship in
CREW = "mod/graphics/battle/ship-crew-modular-topdown/"
ARMOR = "mod/graphics/battle/marine-modular-topdown/variants/armor/"
OUTFITS = {
    "command": CREW + "command",
    "engineering": CREW + "engineering",
    "medical": CREW + "medical",
    "service": CREW + "service",
    "armorless": ARMOR + "armorless",
    "charcoal": ARMOR + "charcoal",
    "blue-scout": ARMOR + "blue-scout",
    "outlaw": ARMOR + "outlaw",
    "militia": ARMOR + "militia",
}


def capsule(d, a, b, width, scale, end_width=None):
    """A limb from a to b, tapering from width to end_width, with round joints."""
    (ax, ay), (bx, by) = a, b
    wb = width * 0.8 if end_width is None else end_width
    length = math.hypot(bx - ax, by - ay) or 1.0
    nx, ny = -(by - ay) / length, (bx - ax) / length
    quad = [(ax + nx * width / 2, ay + ny * width / 2), (bx + nx * wb / 2, by + ny * wb / 2),
            (bx - nx * wb / 2, by - ny * wb / 2), (ax - nx * width / 2, ay - ny * width / 2)]
    d.polygon([(x * scale, y * scale) for x, y in quad], fill=255)
    for (x, y), w in ((a, width), (b, wb)):
        r = w / 2 * scale
        d.ellipse([x * scale - r, y * scale - r, x * scale + r, y * scale + r], fill=255)


def smooth(points, steps=10):
    """Closed Catmull-Rom curve through points."""
    out = []
    n = len(points)
    for i in range(n):
        p0, p1, p2, p3 = (points[(i + k - 1) % n] for k in range(4))
        for j in range(steps):
            t = j / steps
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[c]) + (-p0[c] + p2[c]) * t
                                    + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t2
                                    + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * t3) for c in (0, 1)))
    return out


def oval(d, centre, size, scale, angle=0.0):
    """An ellipse of size (w, h) turned by angle degrees, as a polygon."""
    cx, cy = centre
    w, h = size
    pts = []
    for i in range(48):
        t = 2 * math.pi * i / 48
        x, y = w / 2 * math.cos(t), h / 2 * math.sin(t)
        c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
        pts.append(((cx + x * c - y * s) * scale, (cy + x * s + y * c) * scale))
    d.polygon(pts, fill=255)


def torso(d, top, bottom, top_w, bottom_w, scale, lean=0.0):
    """Shoulders, waist and hips as one smooth back, leaned by `lean` px at the hips."""
    cx = FRAME_W / 2
    h = bottom - top
    half, hip = top_w / 2, bottom_w / 2
    waist = hip * 0.97
    pts = [(cx, top - 4), (cx + half * 0.55, top), (cx + half, top + h * 0.12),
           (cx + half * 0.92, top + h * 0.34), (cx + waist + lean * 0.6, top + h * 0.66),
           (cx + hip + lean, top + h * 0.88), (cx + hip * 0.6 + lean, bottom),
           (cx + lean, bottom + 4), (cx - hip * 0.6 + lean, bottom),
           (cx - hip + lean, top + h * 0.88), (cx - waist + lean * 0.6, top + h * 0.66),
           (cx - half * 0.92, top + h * 0.34), (cx - half, top + h * 0.12),
           (cx - half * 0.55, top)]
    d.polygon([(x * scale, y * scale) for x, y in smooth(pts)], fill=255)


ARM, FOREARM, LEG, SHIN = 40, 30, 62, 46
HAND = (24, 28)
BOOT = (34, 50)


def pose_mask(pose: int, scale: int) -> Image.Image:
    img = Image.new("L", (FRAME_W * scale, FRAME_H * scale), 0)
    d = ImageDraw.Draw(img)
    cx = FRAME_W / 2
    if pose == 0:  # face down, arms along the sides
        capsule(d, (cx - 26, 256), (cx - 42, 345), LEG, scale)
        capsule(d, (cx - 42, 345), (cx - 46, 405), SHIN, scale)
        capsule(d, (cx + 26, 256), (cx + 44, 345), LEG, scale)
        capsule(d, (cx + 44, 345), (cx + 52, 405), SHIN, scale)
        oval(d, (cx - 46, 418), BOOT, scale, 4)
        oval(d, (cx + 53, 418), BOOT, scale, -6)
        torso(d, 112, 282, 150, 116, scale)
        capsule(d, (cx - 62, 132), (cx - 80, 205), ARM, scale)
        capsule(d, (cx - 80, 205), (cx - 84, 262), FOREARM, scale)
        capsule(d, (cx + 62, 132), (cx + 82, 200), ARM, scale)
        capsule(d, (cx + 82, 200), (cx + 92, 255), FOREARM, scale)
        oval(d, (cx - 85, 278), HAND, scale)
        oval(d, (cx + 94, 270), HAND, scale, -10)
        oval(d, (cx, 74), (70, 84), scale)
    elif pose == 1:  # on the back, arms flung out, one knee fallen outward
        capsule(d, (cx - 26, 256), (cx - 40, 350), LEG, scale)
        capsule(d, (cx - 40, 350), (cx - 38, 410), SHIN, scale)
        capsule(d, (cx + 26, 256), (cx + 82, 330), LEG, scale)
        capsule(d, (cx + 82, 330), (cx + 52, 392), SHIN, scale)
        oval(d, (cx - 38, 424), BOOT, scale, 0)
        oval(d, (cx + 44, 404), BOOT, scale, 25)
        torso(d, 112, 284, 150, 118, scale)
        capsule(d, (cx - 64, 128), (cx - 112, 168), ARM, scale)
        capsule(d, (cx - 112, 168), (cx - 128, 226), FOREARM, scale)
        capsule(d, (cx + 64, 128), (cx + 110, 110), ARM, scale)
        capsule(d, (cx + 110, 110), (cx + 128, 58), FOREARM, scale)
        oval(d, (cx - 130, 244), HAND, scale, 10)
        oval(d, (cx + 132, 40), HAND, scale, 20)
        oval(d, (cx - 6, 76), (70, 84), scale, -10)
    elif pose == 2:  # curled on one side, knees drawn up, arms forward
        capsule(d, (cx + 4, 262), (cx + 76, 300), LEG, scale)
        capsule(d, (cx + 76, 300), (cx + 40, 360), SHIN, scale)
        capsule(d, (cx - 16, 268), (cx + 50, 322), LEG, scale)
        capsule(d, (cx + 50, 322), (cx + 6, 378), SHIN, scale)
        oval(d, (cx + 30, 380), BOOT, scale, 35)
        oval(d, (cx - 6, 396), BOOT, scale, 40)
        torso(d, 116, 276, 112, 100, scale, lean=-10)
        capsule(d, (cx + 40, 136), (cx + 86, 188), ARM, scale)
        capsule(d, (cx + 86, 188), (cx + 74, 244), FOREARM, scale)
        oval(d, (cx + 70, 262), HAND, scale, -15)
        capsule(d, (cx + 20, 140), (cx + 64, 206), ARM, scale)
        oval(d, (cx + 66, 222), HAND, scale, -10)
        oval(d, (cx + 8, 80), (66, 84), scale, 14)
    else:  # sprawled, one arm overhead, legs apart
        capsule(d, (cx - 26, 256), (cx - 76, 344), LEG, scale)
        capsule(d, (cx - 76, 344), (cx - 96, 404), SHIN, scale)
        capsule(d, (cx + 26, 256), (cx + 60, 348), LEG, scale)
        capsule(d, (cx + 60, 348), (cx + 76, 410), SHIN, scale)
        oval(d, (cx - 100, 420), BOOT, scale, 18)
        oval(d, (cx + 80, 424), BOOT, scale, -12)
        torso(d, 112, 282, 150, 116, scale, lean=-6)
        capsule(d, (cx - 62, 128), (cx - 96, 70), ARM, scale)
        capsule(d, (cx - 96, 70), (cx - 102, 18), FOREARM, scale)
        oval(d, (cx - 103, 6 + HAND[1] / 2), HAND, scale)
        capsule(d, (cx + 64, 134), (cx + 96, 196), ARM, scale)
        capsule(d, (cx + 96, 196), (cx + 120, 244), FOREARM, scale)
        oval(d, (cx + 128, 262), HAND, scale, -25)
        oval(d, (cx + 10, 74), (70, 84), scale, 18)
    return img


def frame_mask(pose: int) -> Image.Image:
    """The pose at 1x (FRAME_W x FRAME_H), hard-edged."""
    big = pose_mask(pose, SUPER).filter(ImageFilter.GaussianBlur(SUPER))
    return big.resize((FRAME_W, FRAME_H), Image.Resampling.BOX).point(lambda a: 255 if a >= 128 else 0)


def frame_box(pose: int) -> tuple[int, int, int, int]:
    w, h = round(FRAME_W * GUIDE_SCALE), round(FRAME_H * GUIDE_SCALE)
    gap = (CANVAS[0] - POSES * w) // (POSES + 1)
    x = gap + pose * (w + gap)
    y = (CANVAS[1] - h) // 2
    return x, y, x + w, y + h


def guide() -> None:
    sheet = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
    for pose in range(POSES):
        x0, y0, x1, y1 = frame_box(pose)
        mask = pose_mask(pose, SUPER).filter(ImageFilter.GaussianBlur(SUPER))
        mask = mask.resize((x1 - x0, y1 - y0), Image.Resampling.BOX).point(lambda a: 255 if a >= 128 else 0)
        sheet.paste(Image.new("RGBA", mask.size, GUIDE_GRAY), (x0, y0), mask)
    out = Path("art-source/templates/downed-poses.png")
    sheet.save(out)
    print(out)


def export(outfits) -> None:
    for outfit in outfits:
        raw = Image.open(SOURCE / "raw" / f"{outfit}.png").convert("RGBA")
        sx, sy = raw.width / CANVAS[0], raw.height / CANVAS[1]
        target = Path(OUTFITS[outfit])
        for pose in range(POSES):
            x0, y0, x1, y1 = frame_box(pose)
            cut = raw.crop((round(x0 * sx), round(y0 * sy), round(x1 * sx), round(y1 * sy)))
            layer = cut.resize((FRAME_W, FRAME_H), Image.Resampling.NEAREST)
            r, g, b, a = layer.split()
            # The reskin keeps its own limbs where it moved them a little; the guide,
            # grown generously, only removes strays far from the body.
            bound = frame_mask(pose).filter(ImageFilter.MaxFilter(41))
            a = Image.composite(a, Image.new("L", a.size, 0), bound)
            out = target / f"downed-{pose}.png"
            Image.merge("RGBA", (r, g, b, a)).save(out, optimize=True)
            print(out)


# Blood pools: four irregular shapes drawn under a body, shared by every outfit and
# picked by the same death roll as the pose. POOL px at the 150-px shoulder reference,
# centred on the body's frame centre and turned with it.
POOL = 240
POOL_GUIDE_SCALE = 1.4
POOL_TARGET = Path("mod/graphics/battle/downed")


def pool_mask(index: int, scale: int) -> Image.Image:
    """A pool: a seeded union of overlapping discs with a few outlying drops."""
    import random
    rng = random.Random(9100 + index)
    img = Image.new("L", (POOL * scale, POOL * scale), 0)
    d = ImageDraw.Draw(img)
    c = POOL / 2

    def disc(x, y, r):
        d.ellipse([(x - r) * scale, (y - r) * scale, (x + r) * scale, (y + r) * scale], fill=255)

    disc(c, c, 46)
    for _ in range(9):
        a = rng.uniform(0, 2 * math.pi)
        dist = rng.uniform(22, 54)
        disc(c + math.cos(a) * dist, c + math.sin(a) * dist * 1.15, rng.uniform(20, 36))
    for _ in range(4):
        a = rng.uniform(0, 2 * math.pi)
        dist = rng.uniform(86, 104)
        disc(c + math.cos(a) * dist, c + math.sin(a) * dist, rng.uniform(4, 8))
    return img


def pool_frame_mask(index: int) -> Image.Image:
    big = pool_mask(index, SUPER).filter(ImageFilter.GaussianBlur(SUPER * 2))
    return big.resize((POOL, POOL), Image.Resampling.BOX).point(lambda a: 255 if a >= 128 else 0)


def pool_box(index: int) -> tuple[int, int, int, int]:
    w = round(POOL * POOL_GUIDE_SCALE)
    gap = (CANVAS[0] - POSES * w) // (POSES + 1)
    x = gap + index * (w + gap)
    y = (CANVAS[1] - w) // 2
    return x, y, x + w, y + w


def pool_guide() -> None:
    sheet = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
    for index in range(POSES):
        x0, y0, x1, y1 = pool_box(index)
        mask = pool_mask(index, SUPER).filter(ImageFilter.GaussianBlur(SUPER * 2))
        mask = mask.resize((x1 - x0, y1 - y0), Image.Resampling.BOX).point(lambda a: 255 if a >= 128 else 0)
        sheet.paste(Image.new("RGBA", mask.size, GUIDE_GRAY), (x0, y0), mask)
    out = Path("art-source/templates/blood-pools.png")
    sheet.save(out)
    print(out)


def pool_export() -> None:
    raw = Image.open(SOURCE / "raw" / "blood-pools.png").convert("RGBA")
    sx, sy = raw.width / CANVAS[0], raw.height / CANVAS[1]
    POOL_TARGET.mkdir(parents=True, exist_ok=True)
    for index in range(POSES):
        x0, y0, x1, y1 = pool_box(index)
        cut = raw.crop((round(x0 * sx), round(y0 * sy), round(x1 * sx), round(y1 * sy)))
        layer = cut.resize((POOL, POOL), Image.Resampling.NEAREST)
        r, g, b, a = layer.split()
        bound = pool_frame_mask(index).filter(ImageFilter.MaxFilter(9))
        a = Image.composite(a, Image.new("L", a.size, 0), bound)
        out = POOL_TARGET / f"blood-pool-{index}.png"
        Image.merge("RGBA", (r, g, b, a)).save(out, optimize=True)
        print(out)


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else "guide"
    if command == "guide":
        guide()
    elif command == "pool-guide":
        pool_guide()
    elif command == "pool-export":
        pool_export()
    else:
        export(sys.argv[2:] or list(OUTFITS))
