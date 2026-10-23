"""Geometry guide for the LRM carrier's single LRM-15 pod (ImageGen reskins it; see README.md).

Draws one overhead launcher box facing LEFT on a transparent 1024x1024 canvas at 4x its packed size: a tube face
of fifteen mouths (five across, three deep) at the nose, a raised top cover with a centre strap behind it, side
lugs, and a trunnion block at the tail where the box sits on the turntable. Flat grey planes only; the shade of
a plane says how high it stands. The box is centred on the canvas, so the canvas centre is the turntable pivot.

    python art-source/vehicles/missile-carriers/lrm15_guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
OUT = HERE / "lrm15-guide.png"

SIZE = 1024
LENGTH, WIDTH = 780, 740          # nose-to-tail along x, across along y
COLS, ROWS = 3, 5                 # tube mouths: deep x across
OUTLINE = (22, 23, 22, 255)


def main():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = SIZE // 2
    x0, x1 = cx - LENGTH // 2, cx + LENGTH // 2
    y0, y1 = cy - WIDTH // 2, cy + WIDTH // 2

    # Trunnion block at the tail, under the box.
    d.rectangle((x1 - 120, cy - 150, x1 + 60, cy + 150), fill=(92, 92, 92, 255), outline=OUTLINE, width=8)
    # Side lugs (lifting points), under the box.
    for y in (y0 - 34, y1 - 10):
        for x in (x0 + 170, x1 - 230):
            d.rectangle((x, y, x + 70, y + 44), fill=(104, 104, 104, 255), outline=OUTLINE, width=6)

    # Main box with chamfered corners.
    c = 40
    box = [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c),
           (x0, y0 + c)]
    d.polygon(box, fill=(150, 150, 150, 255), outline=OUTLINE, width=10)

    # Tube face: the front half, sunk, fifteen mouths.
    fx0, fx1 = x0 + 34, x0 + 424
    fy0, fy1 = y0 + 40, y1 - 40
    d.rectangle((fx0, fy0, fx1, fy1), fill=(58, 58, 58, 255), outline=OUTLINE, width=8)
    pitch_x = (fx1 - fx0) / COLS
    pitch_y = (fy1 - fy0) / ROWS
    r = min(pitch_x, pitch_y) * 0.42
    for i in range(COLS):
        for j in range(ROWS):
            mx = fx0 + pitch_x * (i + 0.5)
            my = fy0 + pitch_y * (j + 0.5)
            d.ellipse((mx - r, my - r, mx + r, my + r), fill=(18, 18, 18, 255), outline=(120, 120, 120, 255),
                      width=5)
            d.ellipse((mx - r * 0.45, my - r * 0.45, mx + r * 0.45, my + r * 0.45), fill=(200, 200, 200, 255))

    # Raised top cover behind the face, with a centre strap running nose to tail.
    d.rectangle((fx1 + 26, y0 + 40, x1 - 40, y1 - 40), fill=(176, 176, 176, 255), outline=OUTLINE, width=8)
    d.rectangle((fx1 - 10, cy - 60, x1 + 10, cy + 60), fill=(200, 200, 200, 255), outline=OUTLINE, width=8)
    # Corner bolts on the cover.
    for bx in (fx1 + 60, x1 - 74):
        for by in (y0 + 74, y1 - 74):
            d.ellipse((bx - 12, by - 12, bx + 12, by + 12), fill=(110, 110, 110, 255), outline=OUTLINE, width=4)

    img.save(OUT)
    print(f"wrote {OUT} {img.size}")


if __name__ == "__main__":
    main()
