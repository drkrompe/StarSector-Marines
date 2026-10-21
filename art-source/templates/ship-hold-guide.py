"""Placement guide only; ImageGen supplies the finished ship-hold kit (look-dev:
art-source/ship-deck-lookdev). 1536x1536, transparent, 256px per world cell.

Rows 0-1: twelve 1x1 floor tiles, opaque to their slot edge so they tile:
  row 0: plate x3, rail plate x3 (tie-down slot row runs image top to bottom)
  row 1: worn plate x3, worn rail plate x3
Row 2: six 1x1 wall-edge strips. The bulkhead is the TOP edge of the slot;
  art stays in the top 40%, the rest is transparent deck:
  cable run, slack cable run, caged lamp, dead caged lamp, wall patch plate, wall-foot grime
Rows 3-4: six 2x1 storage pieces against the wall (bulkhead at slot top), art
  within the top 70%: crate stack, strapped pallet, duffels and bedroll,
  ammo cans and gear, netted cargo, bedroll camp
Row 5: six 1x1 loose items, centred: crate, ammo cans, duffel, drum, cable coil, toolbox
Slot order is recorded in ship-hold.tileset-authoring.json.
"""
from PIL import Image, ImageDraw
import math

C = 256
image = Image.new('RGBA', (6 * C, 6 * C))
d = ImageDraw.Draw(image)


def cell(col, row):
    return col * C, row * C


def plate(x, y, shade, rail=False, worn=False):
    d.rectangle((x, y, x + C - 1, y + C - 1), fill=(shade, shade + 2, shade + 2, 255))
    d.rectangle((x, y, x + C - 1, y + C - 1), outline=(shade - 40,) * 3 + (255,), width=6)
    for bx in (18, C - 18):
        for by in (18, C // 2, C - 18):
            d.ellipse((x + bx - 7, y + by - 7, x + bx + 7, y + by + 7), fill=(shade + 40,) * 3 + (255,))
    if rail:
        d.rectangle((x + C // 2 - 30, y, x + C // 2 + 30, y + C), fill=(shade - 20,) * 3 + (255,))
        for sy in (40, 128, 216):
            d.rectangle((x + C // 2 - 16, y + sy - 22, x + C // 2 + 16, y + sy + 22), fill=(28, 28, 30, 255))
    if worn:
        d.polygon([(x + 30, y + 150), (x + 120, y + 140), (x + 130, y + 230), (x + 40, y + 226)],
                  fill=(shade - 14, shade - 8, shade - 12, 255))
        d.line((x + 160, y + 40, x + 220, y + 100), fill=(shade - 50,) * 3 + (255,), width=4)


for i in range(3):
    plate(*cell(i, 0), 110 + i * 3)
    plate(*cell(3 + i, 0), 110 + i * 3, rail=True)
    plate(*cell(i, 1), 96 + i * 3, worn=True)
    plate(*cell(3 + i, 1), 96 + i * 3, rail=True, worn=True)

# Row 2: wall-edge strips, bulkhead at the slot top.
x, y = cell(0, 2)
d.line((x, y + 30, x + C, y + 30), fill=(60, 62, 62, 255), width=14)
for cx in (40, 128, 216):
    d.rectangle((x + cx - 8, y + 18, x + cx + 8, y + 42), fill=(120, 120, 116, 255))
x, y = cell(1, 2)
pts = [(x + C * t / 30, y + 30 + 60 * math.sin(math.pi * t / 30)) for t in range(31)]
d.line(pts, fill=(60, 62, 62, 255), width=14, joint='curve')
for k in range(2):
    d.line((x + 150 + k * 30, y + 60, x + 160 + k * 30, y + 100), fill=(70, 70, 72, 255), width=6)
for col, lit in ((2, True), (3, False)):
    x, y = cell(col, 2)
    d.line((x, y + 26, x + C, y + 26), fill=(60, 62, 62, 255), width=10)
    d.rectangle((x + 92, y, x + 164, y + 70), fill=(70, 70, 68, 255))
    d.ellipse((x + 100, y + 14, x + 156, y + 66), fill=(255, 190, 90, 255) if lit else (40, 38, 34, 255))
    for k in range(3):
        d.line((x + 100 + k * 28, y + 14, x + 100 + k * 28, y + 66), fill=(60, 60, 58, 255), width=5)
x, y = cell(4, 2)
d.polygon([(x + 40, y), (x + 210, y), (x + 200, y + 70), (x + 50, y + 80)], fill=(104, 108, 100, 255))
for px, py in ((56, 12), (194, 12), (190, 60), (62, 66)):
    d.ellipse((x + px - 7, y + py - 7, x + px + 7, y + py + 7), fill=(150, 150, 146, 255))
x, y = cell(5, 2)
d.polygon([(x, y), (x + C, y), (x + C, y + 40), (x + 180, y + 70), (x + 90, y + 60), (x, y + 45)],
          fill=(40, 44, 36, 180))

# Rows 3-4: 2x1 storage against the wall (bulkhead at top).
def slot2(col, row):
    return col * 2 * C, (3 + row) * C


x, y = slot2(0, 0)  # crate stack
for bx, by, w, h, s in ((30, 10, 200, 150, 120), (240, 10, 150, 120, 110), (400, 10, 90, 160, 126)):
    d.rectangle((x + bx, y + by, x + bx + w, y + by + h), fill=(s, s - 4, s - 16, 255), outline=(50, 48, 40, 255), width=6)
x, y = slot2(1, 0)  # strapped pallet
d.rectangle((x + 40, y + 10, x + 470, y + 170), fill=(108, 100, 84, 255))
for sx in (120, 250, 380):
    d.rectangle((x + sx - 8, y + 10, x + sx + 8, y + 170), fill=(160, 130, 50, 255))
x, y = slot2(2, 0)  # duffels and bedroll
for bx, by, r1, r2, s in ((80, 70, 70, 45, 90), (210, 60, 80, 50, 76), (360, 80, 90, 35, 104)):
    d.ellipse((x + bx - r1, y + by - r2, x + bx + r1, y + by + r2), fill=(s, s + 6, s - 10, 255))
x, y = slot2(0, 1)  # ammo cans and gear
for i in range(5):
    d.rectangle((x + 40 + i * 86, y + 14, x + 110 + i * 86, y + 110), fill=(84, 92, 70, 255), outline=(40, 44, 34, 255), width=5)
d.ellipse((x + 160, y + 120, x + 260, y + 170), fill=(70, 70, 66, 255))
x, y = slot2(1, 1)  # netted cargo
d.rectangle((x + 30, y + 10, x + 480, y + 175), fill=(100, 96, 88, 255))
for k in range(0, 460, 40):
    d.line((x + 30 + k, y + 10, x + 30 + k + 120, y + 175), fill=(60, 56, 48, 255), width=4)
    d.line((x + 150 + k, y + 10, x + 30 + k, y + 175), fill=(60, 56, 48, 255), width=4)
x, y = slot2(2, 1)  # bedroll camp
d.rounded_rectangle((x + 30, y + 20, x + 330, y + 120), 30, fill=(86, 92, 72, 255))
d.ellipse((x + 40, y + 30, x + 100, y + 110), fill=(150, 146, 136, 255))
d.rectangle((x + 360, y + 20, x + 470, y + 110), fill=(100, 98, 90, 255))

# Row 5: loose 1x1 items, centred.
x, y = cell(0, 5); d.rectangle((x + 50, y + 50, x + 206, y + 206), fill=(122, 116, 100, 255), outline=(50, 48, 40, 255), width=8)
x, y = cell(1, 5)
for i in range(2): d.rectangle((x + 50 + i * 82, y + 70, x + 122 + i * 82, y + 186), fill=(84, 92, 70, 255))
x, y = cell(2, 5); d.ellipse((x + 40, y + 80, x + 216, y + 176), fill=(76, 82, 66, 255))
x, y = cell(3, 5); d.ellipse((x + 58, y + 58, x + 198, y + 198), fill=(90, 96, 104, 255)); d.ellipse((x + 100, y + 100, x + 156, y + 156), fill=(60, 64, 70, 255))
x, y = cell(4, 5); d.ellipse((x + 50, y + 50, x + 206, y + 206), outline=(40, 40, 42, 255), width=26)
x, y = cell(5, 5); d.rectangle((x + 40, y + 90, x + 216, y + 170), fill=(150, 60, 40, 255)); d.rectangle((x + 100, y + 70, x + 156, y + 90), fill=(60, 60, 60, 255))

image.save('art-source/templates/ship-hold-guide.png')
