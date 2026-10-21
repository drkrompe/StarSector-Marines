"""Station arms: forearms and hands reaching onto a console. Run from the repository root.

    python art-source/station-arms/station_arms.py guide
    python art-source/station-arms/station_arms.py export

A body layer's arms hang at its sides. Somebody working a console has theirs out
in front of them, so two extra layers (left and right) are drawn under the body
while an actor stands at hands-on work. Each arm layer shares the body
layer's grid: a canvas 120 rows taller than the body whose bottom rows are the body canvas, so it
is placed exactly as the body is and only extends forward of it (up the image).
The forearm leaves the sleeve at the body's side and the hand rests just ahead of
the head.

The guide draws the two arms at GUIDE_SCALE on one 1536x1024 sheet; each role is
an ImageGen reskin of it in that role's sleeve and glove (the role's own raw sheet
as reference). Export cuts each frame, scales it back with nearest sampling and
bounds alpha by the guide's own arm shape.
"""

from pathlib import Path
import math
import sys

from PIL import Image, ImageDraw

CANVAS = (150, 240)        # arm layer size for a 120-row body; its bottom rows are the body canvas
BODY_ROW = 120             # body canvas row 0 within the arm canvas
GUIDE_SCALE = 5
SHEET = (1536, 1024)
GUIDE_GRAY = (163, 163, 163, 255)
# Frame (in arm-canvas pixels) that holds each arm, and where it sits on the guide.
FRAMES = {"left": (0, 60, 75, 230), "right": (75, 60, 150, 230)}
ORIGINS = {"left": (330, 87), "right": (831, 87)}
# Body-canvas coordinates: elbow at the sleeve, wrist and hand just ahead of the head.
ELBOW = (15, 60)
WRIST = (38, 0)
ELBOW_WIDTH, WRIST_WIDTH = 24, 17
HAND = (23, 27)            # width across, length along the forearm
ROLES = {
    "ship-crew": ("command", "engineering", "medical", "service"),
    "colonist": ("civilian", "engineer", "scientist"),
    "marine": ("armorless", "army-green", "charcoal", "blue-scout", "militia", "outlaw", "red-heavy"),
}
SOURCE = Path("art-source/station-arms")
TARGETS = {"ship-crew": Path("mod/graphics/battle/ship-crew-modular-topdown"),
           "colonist": Path("mod/graphics/battle/colonist-modular-topdown"),
           "marine": Path("mod/graphics/battle/marine-modular-topdown/variants/armor")}
# Marine families have no single raw sheet, so their style reference is a render of
# the family's own shipped body and head (`refs`), written under build/.
REFS = Path("build/station-arms-refs")


def _arm(draw: ImageDraw.ImageDraw, side: str, s: float, ox: float, oy: float, fill) -> None:
    """One forearm and hand in arm-canvas pixels x s, offset by (ox, oy)."""
    def p(x, y):
        if side == "right":
            x = 150 - x
        return ((x) * s + ox, (y + BODY_ROW) * s + oy)
    ex, ey = ELBOW
    wx, wy = WRIST
    dx, dy = wx - ex, wy - ey
    length = math.hypot(dx, dy)
    nx, ny = -dy / length, dx / length
    poly = [p(ex + nx * ELBOW_WIDTH / 2, ey + ny * ELBOW_WIDTH / 2),
            p(wx + nx * WRIST_WIDTH / 2, wy + ny * WRIST_WIDTH / 2),
            p(wx - nx * WRIST_WIDTH / 2, wy - ny * WRIST_WIDTH / 2),
            p(ex - nx * ELBOW_WIDTH / 2, ey - ny * ELBOW_WIDTH / 2)]
    draw.polygon(poly, fill=fill)
    cx, cy = p(ex, ey)
    r = ELBOW_WIDTH / 2 * s
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill)
    # The hand: an ellipse along the forearm, ahead of the wrist.
    ux, uy = dx / length, dy / length
    hx, hy = wx + ux * HAND[1] * 0.4, wy + uy * HAND[1] * 0.4
    pts = []
    for i in range(48):
        t = 2 * math.pi * i / 48
        lx, ly = math.cos(t) * HAND[0] / 2, math.sin(t) * HAND[1] / 2
        pts.append(p(hx + nx * lx + ux * ly, hy + ny * lx + uy * ly))
    draw.polygon(pts, fill=fill)


def mask(side: str, scale: int) -> Image.Image:
    """The arm's silhouette on its whole arm canvas at `scale`."""
    im = Image.new("L", (CANVAS[0] * scale, CANVAS[1] * scale), 0)
    _arm(ImageDraw.Draw(im), side, scale, 0, 0, 255)
    return im


def guide() -> None:
    sheet = Image.new("RGBA", SHEET, (0, 0, 0, 0))
    for side, (x0, y0, x1, y1) in FRAMES.items():
        m = mask(side, GUIDE_SCALE).crop((x0 * GUIDE_SCALE, y0 * GUIDE_SCALE, x1 * GUIDE_SCALE, y1 * GUIDE_SCALE))
        sheet.paste(Image.new("RGBA", m.size, GUIDE_GRAY), ORIGINS[side], m)
    out = Path("art-source/templates/station-arms-silhouettes.png")
    sheet.save(out)
    print(out)


def refs() -> None:
    """The shipped body and head of each marine family, x6 on the guide's sheet size."""
    REFS.mkdir(parents=True, exist_ok=True)
    for role in ROLES["marine"]:
        root = TARGETS["marine"] / role
        body = Image.open(root / "body.png").convert("RGBA")
        head = Image.open(root / "head.png").convert("RGBA")
        unit = Image.new("RGBA", (150, 120 + body.height), (0, 0, 0, 0))
        unit.alpha_composite(body, (0, 120))
        unit.alpha_composite(head, (75 - head.width // 2, 150 - head.height // 2))
        unit = unit.resize((unit.width * 4, unit.height * 4), Image.Resampling.NEAREST)
        sheet = Image.new("RGBA", SHEET, (0, 0, 0, 0))
        sheet.alpha_composite(unit, ((SHEET[0] - unit.width) // 2, SHEET[1] - unit.height))
        sheet.save(REFS / f"{role}.png")
        print(REFS / f"{role}.png")


def export() -> None:
    for family, roles in ROLES.items():
        for role in roles:
            raw = Image.open(SOURCE / "raw" / f"{role}.png").convert("RGBA")
            sx, sy = raw.width / SHEET[0], raw.height / SHEET[1]
            for side, (x0, y0, x1, y1) in FRAMES.items():
                w, h = x1 - x0, y1 - y0
                ox, oy = ORIGINS[side]
                cut = raw.crop((round(ox * sx), round(oy * sy),
                                round((ox + w * GUIDE_SCALE) * sx), round((oy + h * GUIDE_SCALE) * sy)))
                part = cut.resize((w, h), Image.Resampling.NEAREST)
                bound = mask(side, 1).crop((x0, y0, x1, y1))
                r, g, b, a = part.split()
                a = Image.composite(a, Image.new("L", (w, h), 0), bound.point(lambda v: 255 if v >= 128 else 0))
                # The body canvas is the bottom of the arm canvas whatever the body's height.
                body_rows = Image.open(TARGETS[family] / role / "body.png").height
                layer = Image.new("RGBA", (CANVAS[0], BODY_ROW + body_rows), (0, 0, 0, 0))
                layer.paste(Image.merge("RGBA", (r, g, b, a)), (x0, y0))
                out = TARGETS[family] / role / f"station-arm-{side}.png"
                layer.save(out, optimize=True)
                print(out)


if __name__ == "__main__":
    {"guide": guide, "refs": refs, "export": export}[sys.argv[1] if len(sys.argv) > 1 else "guide"]()
