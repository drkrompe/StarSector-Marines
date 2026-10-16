"""Geometry only: the fort.emplacement 3x3 patch in two damage states, drawn over the intact guide.

Canvas 1536x1024, 192px frames like fort-emplacement-guide.py. The DAMAGED patch occupies x 0-575, y 0-575 and the
DESTROYED patch x 768-1343, y 0-575, each nine frames in the intact slot order (row 0 NW N NE, row 1 W C E, row 2
SW S SE). Both keep the intact silhouette and frame edges exactly so a frame swaps in place for the intact one.
DAMAGED: the same parapet with dark chipped notches along the top face edges, hairline cracks, scorch patches.
DESTROYED: the parapet top face is broken into jagged stumps with gaps down to the platform, heaped rubble pieces,
thin rebar lines and a scorched, cracked platform. The outer 11% batter edge of every frame stays so neighbours meet.
"""
import random
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
S = 192
intact = Image.open(ROOT / 'art-source/templates/fort-emplacement-guide.png').convert('RGBA')
patch = intact.crop((0, 0, 3 * S, 3 * S))
DARK = (58, 58, 56, 255)
CHIP = (92, 92, 88, 255)
SCORCH = (70, 66, 60, 255)
RUBBLE = (120, 118, 112, 255)
REBAR = (120, 78, 52, 255)
PLATFORM = (150, 150, 146, 255)


def on_parapet(x, y):
    """True where the intact patch has parapet (anything lighter or darker than the platform tone)."""
    r, g, b, a = patch.getpixel((x, y))
    return a > 0 and abs(r - 150) + abs(g - 150) + abs(b - 146) > 18


def damaged(rng):
    img = patch.copy()
    d = ImageDraw.Draw(img)
    for _ in range(70):
        x, y = rng.randrange(3 * S), rng.randrange(3 * S)
        if on_parapet(x, y):
            w = rng.randrange(5, 13)
            d.polygon([(x, y), (x + w, y + rng.randrange(1, 6)), (x + w // 2, y + w)], fill=CHIP)
    for _ in range(22):
        x, y = rng.randrange(3 * S), rng.randrange(3 * S)
        pts = [(x, y)]
        for _ in range(5):
            x += rng.randrange(-9, 10)
            y += rng.randrange(-9, 10)
            pts.append((x, y))
        d.line(pts, fill=DARK, width=2)
    for _ in range(5):
        x, y = rng.randrange(3 * S), rng.randrange(3 * S)
        d.ellipse((x - 20, y - 14, x + 20, y + 14), fill=SCORCH)
    return img


def destroyed(rng):
    img = patch.copy()
    d = ImageDraw.Draw(img)
    bites = 0
    while bites < 26:
        x, y = rng.randrange(3 * S), rng.randrange(3 * S)
        if on_parapet(x, y):
            d.rectangle((x - 6, y - 6, x + rng.randrange(14, 30), y + rng.randrange(14, 30)), fill=PLATFORM)
            bites += 1
    for _ in range(46):
        x, y = rng.randrange(3 * S), rng.randrange(3 * S)
        w = rng.randrange(6, 18)
        d.polygon([(x, y), (x + w, y + 3), (x + w - 3, y + w), (x - 2, y + w - 4)], fill=RUBBLE)
    for _ in range(9):
        x, y = rng.randrange(3 * S), rng.randrange(3 * S)
        d.line([(x, y), (x + rng.randrange(-30, 30), y + rng.randrange(-30, 30))], fill=REBAR, width=2)
    for _ in range(7):
        x, y = rng.randrange(3 * S), rng.randrange(3 * S)
        d.ellipse((x - 26, y - 18, x + 26, y + 18), fill=SCORCH)
    return img


guide = Image.new('RGBA', (1536, 1024))
guide.alpha_composite(damaged(random.Random(11)), (0, 0))
guide.alpha_composite(destroyed(random.Random(23)), (768, 0))
guide.save(ROOT / 'art-source/templates/fort-emplacement-damage-guide.png')

# Material reference only: the accepted intact patch, cropped to its own contiguous 3x3 picture.
raw = Image.open(ROOT / 'art-source/tilesets/fort-emplacement.raw.png').convert('RGBA')
raw.crop((22, 11, 22 + 543, 11 + 544)).save(ROOT / 'art-source/templates/fort-emplacement-intact-reference.png')
