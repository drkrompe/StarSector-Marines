"""Deterministic edit targets and geometry guides for the screened (active-protection) turret.

Writes, beside this script:
  turret-target.png  art-source/vehicles/tank/turret-master.png with the hull erased (turret alone, same canvas/pose)
  turret-guide.png   that target with the interceptor fit blocked in as flat shapes
  wreck-target.png   wreck-master.png with the wreck hull erased
  wreck-guide.png    that target with the same fit blocked in, the upper launcher knocked askew
The fit: a launcher box on each rear flank, hanging slightly outboard, its top face a 2x3 grid of
tube mouths; a flat sensor panel on each cheek wedge near the front corner; a small sensor mast
at the rear roof. Coordinates are live master pixels (bearing (1498,454), 140.857 px/cell); the
wreck maps them through its own bearing (1344,447) and 115.32 px/cell.
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
TANK = HERE.parent / "tank"
LIVE_C, WRECK_C = (1498, 454), (1344, 447)
S = 115.32 / 140.857


def erase_left(img, x_end):
    img = img.copy()
    ImageDraw.Draw(img).rectangle((0, 0, x_end - 1, img.height), fill=(0, 0, 0, 0))
    return img


def fit(d, m, wreck=False):
    """m maps a live-master point to this canvas."""
    def box(x0, y0, x1, y1, fill):
        a, b = m(x0, y0), m(x1, y1)
        d.rectangle((min(a[0], b[0]), min(a[1], b[1]), max(a[0], b[0]), max(a[1], b[1])), fill=fill)

    def disc(x, y, r, fill):
        cx, cy = m(x, y)
        rr = r * (S if wreck else 1)
        d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=fill)

    housing, top, mouth, panel, lens = "#3c4237", "#5d6450", "#1b1d19", "#4a5560", "#5f8fa6"
    # Launchers: rear flanks, overhanging the turret side. Upper one shifted when wrecked.
    for sign, shift in ((-1, (6, -5) if wreck else (0, 0)), (1, (0, 0))):
        y_in, y_out = 454 + sign * 84, 454 + sign * 130
        sx, sy = shift
        box(1548 + sx, y_in + sy, 1648 + sx, y_out + sy, housing)
        box(1554 + sx, y_in + sign * 5 + sy, 1642 + sx, y_out - sign * 5 + sy, top)
        for col in range(3):
            for row in range(2):
                disc(1570 + col * 28 + sx, y_in + sign * (12 + row * 22) + sy, 8, mouth)
    # Sensor panels on each cheek, angled with the wedge front.
    for sign in (-1, 1):
        pts = [(1398, 454 + sign * 70), (1436, 454 + sign * 70), (1446, 454 + sign * 96), (1408, 454 + sign * 96)]
        d.polygon([m(*p) for p in pts], fill=panel)
        disc(1422, 454 + sign * 83, 6, lens)
    # Rear-roof sensor mast (small round head on a plate).
    box(1602, 446, 1622, 462, housing)
    disc(1612, 454, 9, panel)


def main():
    live = Image.open(TANK / "turret-master.png").convert("RGBA")
    target = erase_left(live, 1100)
    target.save(HERE / "turret-target.png")
    guide = target.copy()
    fit(ImageDraw.Draw(guide), lambda x, y: (x, y))
    guide.save(HERE / "turret-guide.png")

    wreck = Image.open(TANK / "wreck-master.png").convert("RGBA")
    wtarget = erase_left(wreck, 1000)
    wtarget.save(HERE / "wreck-target.png")
    wguide = wtarget.copy()
    m = lambda x, y: (WRECK_C[0] + (x - LIVE_C[0]) * S, WRECK_C[1] + (y - LIVE_C[1]) * S)
    fit(ImageDraw.Draw(wguide), m, wreck=True)
    wguide.save(HERE / "wreck-guide.png")


if __name__ == "__main__":
    main()
