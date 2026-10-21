"""Placement guide only; ImageGen supplies the finished transparent Deficit dressing.

1536x1536, transparent. Rows 0-1: six 512x512 floor overlays (2x2 cells each).
Rows 2-3: six 512x256 wall-hugging edge pieces (2x1 cells each). Every edge
piece's bulkhead is the TOP edge of its slot; its art stays in the top 45% of
the slot (the hanging conduit droops to 60%), and the rest is transparent deck
the lane keeps. Slot order is recorded in ship-dressing.tileset-authoring.json.
"""
from PIL import Image, ImageDraw
import math
import random

W = 1536
image = Image.new('RGBA', (W, W))
d = ImageDraw.Draw(image)
rng = random.Random(20261020)


def floor_slot(col, row):
    return col * 512, row * 512


def edge_slot(col, row):
    return col * 512, 1024 + row * 256


# --- Row 0 -------------------------------------------------------------
# 0,0 grime: broad irregular dark-olive smear with feathered lobes.
x, y = floor_slot(0, 0)
for i in range(26):
    cx, cy = x + 256 + rng.randint(-150, 150), y + 256 + rng.randint(-120, 120)
    r = rng.randint(28, 70)
    d.ellipse((cx - r, cy - r * 0.7, cx + r, cy + r * 0.7), fill=(74, 70, 52, 150))
# 1,0 condensation: pooled damp patches with droplet beads and rust bloom on one side.
x, y = floor_slot(1, 0)
d.polygon([(x + 90, y + 120), (x + 300, y + 90), (x + 420, y + 200), (x + 380, y + 380),
           (x + 180, y + 420), (x + 80, y + 300)], fill=(70, 86, 92, 140))
for i in range(60):
    cx, cy = x + rng.randint(90, 420), y + rng.randint(90, 420)
    r = rng.randint(3, 9)
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(150, 170, 176, 200))
d.ellipse((x + 330, y + 300, x + 440, y + 410), fill=(120, 70, 40, 150))
# 2,0 debris: flat scatter of panel shards, bolts and grit, nothing piled.
x, y = floor_slot(2, 0)
for i in range(14):
    cx, cy = x + rng.randint(110, 400), y + rng.randint(110, 400)
    a = rng.random() * math.pi
    l, w = rng.randint(30, 80), rng.randint(10, 26)
    pts = [(cx + math.cos(a) * l - math.sin(a) * w, cy + math.sin(a) * l + math.cos(a) * w),
           (cx + math.cos(a) * l + math.sin(a) * w, cy + math.sin(a) * l - math.cos(a) * w),
           (cx - math.cos(a) * l + math.sin(a) * w * .4, cy - math.sin(a) * l - math.cos(a) * w * .4)]
    d.polygon(pts, fill=(118, 120, 116, 255))
for i in range(40):
    cx, cy = x + rng.randint(90, 420), y + rng.randint(90, 420)
    d.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=(70, 66, 60, 255))

# --- Row 1 -------------------------------------------------------------
# 0,1 cable spill: loose loops of pulled data cable lying flat, cut ends.
x, y = floor_slot(0, 1)
for k, colour in enumerate([(40, 40, 42, 255), (60, 70, 90, 255), (140, 110, 40, 255)]):
    pts = []
    for t in range(0, 41):
        s = t / 40
        pts.append((x + 90 + 330 * s + 50 * math.sin(s * 9 + k), y + 150 + k * 70 + 90 * math.sin(s * 5 + k * 2)))
    d.line(pts, fill=colour, width=12, joint='curve')
# 1,1 hull patch: flush riveted repair plates over a seam, slightly mismatched steel.
x, y = floor_slot(1, 1)
d.polygon([(x + 80, y + 110), (x + 330, y + 90), (x + 360, y + 300), (x + 100, y + 330)], fill=(98, 104, 100, 255))
d.polygon([(x + 250, y + 230), (x + 440, y + 250), (x + 430, y + 430), (x + 240, y + 410)], fill=(88, 92, 96, 255))
for px, py in [(100, 130), (310, 115), (335, 285), (120, 310), (270, 250), (420, 265), (415, 410), (260, 395)]:
    d.ellipse((x + px - 6, y + py - 6, x + px + 6, y + py + 6), fill=(150, 152, 146, 255))
# 2,1 buckled deck plate: one plate heaved along a split seam, low and flat-lit.
x, y = floor_slot(2, 1)
d.rectangle((x + 100, y + 120, x + 412, y + 392), fill=(104, 106, 104, 255))
d.line((x + 110, y + 260, x + 220, y + 230, x + 300, y + 285, x + 400, y + 250), fill=(30, 30, 30, 255), width=10)
d.polygon([(x + 110, y + 260), (x + 220, y + 230), (x + 300, y + 285), (x + 400, y + 250),
           (x + 400, y + 220), (x + 300, y + 250), (x + 220, y + 200), (x + 110, y + 230)], fill=(140, 142, 138, 255))

# --- Row 2: edge pieces (wall at top) -------------------------------------
# 0,2 dead panel: wall light/console panel, unlit, cracked lens, hanging from the bulkhead face.
x, y = edge_slot(0, 0)
d.rectangle((x + 120, y + 0, x + 392, y + 70), fill=(52, 54, 56, 255))
d.rectangle((x + 140, y + 12, x + 372, y + 54), fill=(24, 26, 28, 255))
d.line((x + 200, y + 14, x + 240, y + 52), fill=(90, 92, 94, 255), width=4)
# 1,2 conduit: a run of conduit torn loose from the overhead, drooping in a loop off the wall.
x, y = edge_slot(1, 0)
d.line((x + 20, y + 18, x + 492, y + 18), fill=(70, 72, 70, 255), width=16)
pts = [(x + 120 + 280 * t / 30, y + 18 + 130 * math.sin(math.pi * t / 30)) for t in range(31)]
d.line(pts, fill=(110, 90, 60, 255), width=14, joint='curve')
d.line((x + 380, y + 18, x + 430, y + 120), fill=(60, 60, 64, 255), width=8)
# 2,2 stained grille: a scrubber return grille on the bulkhead foot, rust/black streaks spreading onto the deck.
x, y = edge_slot(2, 0)
d.rectangle((x + 150, y + 0, x + 362, y + 64), fill=(82, 86, 84, 255))
for i in range(8):
    d.line((x + 162 + i * 25, y + 8, x + 162 + i * 25, y + 56), fill=(30, 32, 30, 255), width=8)
d.polygon([(x + 170, y + 64), (x + 340, y + 64), (x + 380, y + 110), (x + 140, y + 105)], fill=(90, 70, 45, 130))

# --- Row 3 ---------------------------------------------------------------
# 0,3 buckled wall panel: a bulkhead lining panel bowed out and torn at one corner.
x, y = edge_slot(0, 1)
d.polygon([(x + 60, y + 0), (x + 452, y + 0), (x + 440, y + 60), (x + 300, y + 98), (x + 120, y + 80), (x + 70, y + 40)],
          fill=(96, 98, 98, 255))
d.line((x + 300, y + 98, x + 330, y + 40, x + 360, y + 0), fill=(30, 30, 30, 255), width=6)
# 1,3 bracing: two welded I-beam braces shoring the bulkhead, feet on the deck strip.
x, y = edge_slot(1, 1)
for bx in (110, 330):
    d.polygon([(x + bx, y + 0), (x + bx + 70, y + 0), (x + bx + 90, y + 100), (x + bx + 20, y + 100)], fill=(120, 108, 70, 255))
    d.rectangle((x + bx + 10, y + 96, x + bx + 100, y + 112), fill=(80, 80, 78, 255))
d.line((x + 140, y + 30, x + 400, y + 30), fill=(110, 100, 66, 255), width=14)
# 2,3 cable run: bundled cables pulled out of the overhead tray, hanging in loops against the wall.
x, y = edge_slot(2, 1)
d.rectangle((x + 20, y + 0, x + 492, y + 22), fill=(64, 66, 66, 255))
for k, colour in enumerate([(36, 36, 38, 255), (50, 64, 92, 255), (150, 120, 40, 255), (90, 30, 30, 255)]):
    pts = [(x + 90 + k * 30 + 300 * t / 30, y + 22 + (60 + k * 12) * math.sin(math.pi * t / 30)) for t in range(31)]
    d.line(pts, fill=colour, width=9, joint='curve')

image.save('art-source/templates/ship-dressing-guide.png')
