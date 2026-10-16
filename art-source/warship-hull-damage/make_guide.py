"""Geometry guides for the warship hull-damage interior art (warship-hull-damage.md).

Writes art-source/templates/hull-interior-frames-guide.png, hull-interior-core-guide.png and
hull-torn-edges-guide.png. Every interior guide is periodic (what leaves one edge enters the
opposite one), so a reskin of it can tile; the edge guide is four torn plate lips on a flat
magenta key. Deterministic: same seed, same guides.
"""
import math, random, sys
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "art-source" / "templates"
N = 1024

def wrap_line(d, a, b, fill, width):
    for ox in (-N, 0, N):
        for oy in (-N, 0, N):
            d.line([(a[0] + ox, a[1] + oy), (b[0] + ox, b[1] + oy)], fill=fill, width=width)

def wrap_poly(d, pts, fill):
    for ox in (-N, 0, N):
        for oy in (-N, 0, N):
            d.polygon([(x + ox, y + oy) for x, y in pts], fill=fill)

def frames():
    rnd = random.Random(11)
    im = Image.new("RGB", (N, N), (38, 36, 34))  # dark void between frames
    d = ImageDraw.Draw(im)
    # torn deck plating patches, mid grey
    for _ in range(14):
        cx, cy = rnd.uniform(0, N), rnd.uniform(0, N)
        pts = [(cx + math.cos(a) * rnd.uniform(40, 120), cy + math.sin(a) * rnd.uniform(40, 120))
               for a in sorted(rnd.uniform(0, 2 * math.pi) for _ in range(7))]
        wrap_poly(d, pts, (96, 92, 86))
    # ribs (vertical) and stringers (horizontal), lighter steel
    for x in range(0, N, 128):
        wrap_line(d, (x + 20, 0), (x + 20, N), (150, 146, 138), 22)
    for y in range(64, N, 256):
        wrap_line(d, (0, y), (N, y), (128, 124, 118), 14)
    # bent and broken rib ends
    for _ in range(10):
        x, y = rnd.uniform(0, N), rnd.uniform(0, N)
        a = rnd.uniform(0, math.pi)
        wrap_line(d, (x, y), (x + math.cos(a) * 90, y + math.sin(a) * 90), (170, 164, 150), 12)
    return im

def core():
    rnd = random.Random(23)
    im = Image.new("RGB", (N, N), (70, 68, 64))  # deck
    d = ImageDraw.Draw(im)
    for x in range(0, N, 256):
        wrap_line(d, (x, 0), (x, N), (44, 42, 40), 18)  # bulkheads
    for y in range(0, N, 256):
        wrap_line(d, (0, y + 128), (N, y + 128), (44, 42, 40), 18)
    for _ in range(12):  # machinery blocks
        x, y = rnd.uniform(0, N), rnd.uniform(0, N)
        w, h = rnd.uniform(60, 150), rnd.uniform(50, 120)
        wrap_poly(d, [(x, y), (x + w, y), (x + w, y + h), (x, y + h)], (120, 116, 108))
    for _ in range(8):  # conduit runs
        x, y = rnd.uniform(0, N), rnd.uniform(0, N)
        pts = [(x, y)]
        for _ in range(5):
            x += rnd.uniform(-160, 160); y += rnd.uniform(-160, 160); pts.append((x, y))
        for a, b in zip(pts, pts[1:]):
            wrap_line(d, a, b, (160, 132, 96), 10)
    return im

def edges():
    rnd = random.Random(31)
    im = Image.new("RGB", (N, N), (255, 0, 255))
    d = ImageDraw.Draw(im)
    for row in range(4):
        top = row * 256
        # the plate fills the lower part of the band, its upper edge torn and jagged
        pts = [(0, top + 200)]
        x = 0
        while x < N:
            pts.append((x, top + rnd.uniform(70, 150)))
            x += rnd.uniform(18, 60)
        pts += [(N, top + 120), (N, top + 200)]
        d.polygon(pts, fill=(120, 116, 110))
        # a curled, bent lip just under the tear
        for i in range(1, len(pts) - 3):
            d.line([pts[i], (pts[i][0], pts[i][1] + 22)], fill=(160, 154, 144), width=6)
    return im

OUT.mkdir(parents=True, exist_ok=True)
frames().save(OUT / "hull-interior-frames-guide.png")
core().save(OUT / "hull-interior-core-guide.png")
edges().save(OUT / "hull-torn-edges-guide.png")
print("guides written")
