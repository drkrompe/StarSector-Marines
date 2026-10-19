"""Pack the screened tank sheet: mod/graphics/battle/vehicles/army-tank-screened.png.

Four 768x768 frames facing LEFT, as every tank sheet: live hull, live turret, wreck hull, wreck turret.
Hull frames are copied verbatim from the breakthrough sheet: the screened tank is that hull.
Turret frames are the breakthrough turret with its interceptor fit drawn on, deterministically:
a launcher box on each rear flank with its tube mouths, and a sensor plate at each front corner.
The wreck turret gets the same fit, scorched. Stand-in art until an ImageGen pass on the
geometry-first workflow (art-source/geometry-first-imagegen.md) replaces it.

Run from the repository root: python art-source/vehicles/tank-screened/pack.py
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "mod/graphics/battle/vehicles/army-tank.png"
OUTPUT = ROOT / "mod/graphics/battle/vehicles/army-tank-screened.png"
SLOT = 768

LIVE = {"plate": (78, 82, 70), "face": (96, 101, 86), "edge": (44, 47, 40), "mouth": (24, 26, 22),
        "lens": (70, 120, 140), "glint": (150, 200, 215)}
WRECK = {"plate": (46, 44, 38), "face": (58, 55, 47), "edge": (26, 25, 22), "mouth": (14, 14, 12),
         "lens": (34, 40, 42), "glint": (60, 66, 66)}


def launcher(d, x0, y0, x1, y1, c, mouths_on_top):
    """A launcher box seen from above: plate, a lighter top face, and two rows of tube mouths at its outer edge."""
    d.rectangle((x0, y0, x1, y1), fill=c["edge"])
    d.rectangle((x0 + 3, y0 + 3, x1 - 3, y1 - 3), fill=c["plate"])
    d.rectangle((x0 + 7, y0 + 7, x1 - 7, y1 - 7), fill=c["face"])
    row_y = y0 + 6 if mouths_on_top else y1 - 16
    for i in range(4):
        cx = x0 + 10 + i * ((x1 - x0 - 20) // 4)
        d.ellipse((cx, row_y, cx + 10, row_y + 10), fill=c["mouth"])


def sensor(d, x, y, c):
    """A flat sensor plate with its lens."""
    d.rectangle((x, y, x + 22, y + 16), fill=c["edge"])
    d.rectangle((x + 2, y + 2, x + 20, y + 14), fill=c["plate"])
    d.ellipse((x + 6, y + 3, x + 16, y + 13), fill=c["lens"])
    d.ellipse((x + 8, y + 5, x + 11, y + 8), fill=c["glint"])


def fit(frame, c):
    out = frame.copy()
    d = ImageDraw.Draw(out)
    launcher(d, 395, 296, 470, 330, c, mouths_on_top=True)
    launcher(d, 395, 454, 470, 488, c, mouths_on_top=False)
    sensor(d, 318, 322, c)
    sensor(d, 318, 446, c)
    return out


def main():
    sheet = Image.open(SOURCE).convert("RGBA")
    frames = [sheet.crop((i * SLOT, 0, (i + 1) * SLOT, SLOT)) for i in range(4)]
    frames[1] = fit(frames[1], LIVE)
    frames[3] = fit(frames[3], WRECK)
    out = Image.new("RGBA", (SLOT * 4, SLOT), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        out.paste(frame, (i * SLOT, 0))
    out.save(OUTPUT)
    print("wrote", OUTPUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
