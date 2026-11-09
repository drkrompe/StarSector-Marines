"""Second-pass geometry guides for the high-tech (Tri-Tachyon) mech row, after the owner's note on the first pass
(guide.py: "Feels too clean for all of these. Look at Tri-Tach ship sprites for clues").

Vanilla's high-tech hulls (Tempest, Aurora, Medusa, Paragon, Fury) are built as rounded armour LOBES laid over a dense,
dark MACHINE CORE that shows in every gap between them, with weapons in round recessed SOCKET rings set into the lobes
and ribbed engine banks exposed at the rear. These guides draw that construction in grey values:

  MACHINE   the exposed core (dark): the spine, the gaps between lobes, the rear engine bank
  BODY      an armour lobe (mid grey), each its own smooth shell
  DECK      the flat mounting plate a weapon pod stands on (light grey)
  SOCKET    a round recessed ring set into a lobe (dark ring): a mount socket, or on the medium a drone cradle
  GLASS     the small sensor bulb (light grey)
  EMITTER   the shield emitter bulb (lightest grey)

Mounts are kept as guide.py keeps them (the Hound's, Warden's and Bulwark's composer anchors). Two takes per weight:

  c   open carapace: separate lobes with wide machine channels between them (Tempest, Aurora)
  d   heavy shell: fewer, larger lobes wrapping the flanks, machine showing at the spine and rear (Paragon, Medusa)

Writes <weight>/guide-<c|d>.png, 1254 px square (H = 1254 px), and ref-tt-hulls.png: the vanilla Tempest, Aurora,
Medusa and Paragon from the game install, upscaled side by side, as the material reference.

Run from anywhere: python art-source/mech-modular-topdown/sources/hightech/guide_v2.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
GAME_SHIPS = Path(r"C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core\graphics\ships")
S = 1254
CX = S / 2
CY = S / 2

MACHINE = (82, 82, 80, 255)
BODY = (140, 140, 136, 255)
DECK = (162, 162, 158, 255)
SOCKET = (60, 60, 60, 255)
GLASS = (190, 190, 190, 255)
EMITTER = (222, 222, 222, 255)
LINE = (36, 36, 36, 255)


def px(x, y):
    return CX + x * S, CY - y * S


def smooth(points, rounds=3):
    for _ in range(rounds):
        out = []
        for i, (x0, y0) in enumerate(points):
            x1, y1 = points[(i + 1) % len(points)]
            out += [(0.75 * x0 + 0.25 * x1, 0.75 * y0 + 0.25 * y1), (0.25 * x0 + 0.75 * x1, 0.25 * y0 + 0.75 * y1)]
        points = out
    return points


def lobe(d, points, fill=BODY, mirror=True):
    """A smooth armour lobe from its outline on the right (+x) side, and its mirror on the left."""
    for side in ((1, -1) if mirror else (1,)):
        d.polygon([px(side * x, y) for x, y in smooth(points)], fill=fill, outline=LINE, width=6)


def centre_lobe(d, half, fill=BODY):
    pts = smooth(half + [(-x, y) for x, y in reversed(half)])
    d.polygon([px(x, y) for x, y in pts], fill=fill, outline=LINE, width=6)


def rect(d, x0, y0, x1, y1, fill, radius=0.02):
    a, b = px(x0, y1), px(x1, y0)
    d.rounded_rectangle((a[0], a[1], b[0], b[1]), radius=radius * S, fill=fill, outline=LINE, width=6)


def circle(d, x, y, r, fill, mirror=False):
    for side in ((1, -1) if mirror else (1,)):
        cx, cy = px(side * x, y)
        d.ellipse((cx - r * S, cy - r * S, cx + r * S, cy + r * S), fill=fill, outline=LINE, width=6)


def socket(d, x, y, r, mirror=True):
    circle(d, x, y, r, SOCKET, mirror)
    circle(d, x, y, r * 0.55, MACHINE, mirror)


def canvas():
    im = Image.new("RGBA", (S, S))
    return im, ImageDraw.Draw(im)


def core(d, half_width, front, rear, engine_half):
    """The machine spine and the ribbed engine bank behind it."""
    rect(d, -half_width, rear + 0.08, half_width, front, MACHINE, 0.03)
    rect(d, -engine_half, rear, engine_half, rear + 0.12, MACHINE, 0.02)


def bridge(d, half, y0, y1):
    """Machinery under the lobes, joining them to the spine: what shows in the gaps between them."""
    rect(d, -half, y0, half, y1, MACHINE, 0.04)


def light_c():
    im, d = canvas()
    core(d, 0.12, 0.30, -0.48, 0.15)
    bridge(d, 0.25, -0.20, 0.10)
    lobe(d, [(0.04, 0.20), (0.12, 0.22), (0.12, 0.40), (0.07, 0.48), (0.04, 0.44)])          # nose cheeks
    lobe(d, [(0.13, 0.16), (0.24, 0.12), (0.31, -0.04), (0.29, -0.22), (0.20, -0.26), (0.14, -0.10)])  # leg lobes
    lobe(d, [(0.06, -0.30), (0.17, -0.30), (0.19, -0.40), (0.12, -0.45), (0.06, -0.42)])     # rear lobes
    rect(d, -0.08, -0.28, 0.08, 0.12, DECK, 0.015)
    socket(d, 0.235, -0.06, 0.045)
    circle(d, 0.0, 0.20, 0.045, GLASS)
    circle(d, 0.0, -0.36, 0.045, EMITTER)
    return im


def light_d():
    im, d = canvas()
    core(d, 0.10, 0.28, -0.48, 0.14)
    bridge(d, 0.24, -0.24, 0.10)
    # One hood lobe each side, wrapping from the nose cheek back over the leg.
    lobe(d, [(0.04, 0.46), (0.10, 0.44), (0.14, 0.24), (0.27, 0.10), (0.33, -0.14), (0.27, -0.30), (0.17, -0.30),
             (0.11, -0.12), (0.10, 0.16), (0.04, 0.22)])
    rect(d, -0.08, -0.28, 0.08, 0.12, DECK, 0.015)
    socket(d, 0.22, -0.08, 0.05)
    circle(d, 0.0, 0.19, 0.045, GLASS)
    circle(d, 0.0, -0.38, 0.05, EMITTER)
    return im


def medium_c():
    im, d = canvas()
    core(d, 0.16, 0.30, -0.48, 0.20)
    bridge(d, 0.30, -0.34, 0.12)
    lobe(d, [(0.045, 0.20), (0.13, 0.22), (0.13, 0.40), (0.08, 0.48), (0.045, 0.44)])
    lobe(d, [(0.10, 0.18), (0.26, 0.14), (0.31, -0.02), (0.22, -0.06), (0.10, 0.02)])         # fore drone lobes
    lobe(d, [(0.10, -0.12), (0.28, -0.10), (0.36, -0.24), (0.32, -0.40), (0.14, -0.40)])      # aft drone lobes
    rect(d, -0.075, -0.36, 0.075, 0.10, DECK, 0.012)
    socket(d, 0.20, 0.06, 0.055)
    socket(d, 0.24, -0.25, 0.06)
    circle(d, 0.0, 0.20, 0.045, GLASS)
    circle(d, 0.0, -0.42, 0.04, EMITTER)
    return im


def medium_d():
    im, d = canvas()
    core(d, 0.14, 0.28, -0.48, 0.20)
    bridge(d, 0.32, -0.36, 0.10)
    lobe(d, [(0.045, 0.46), (0.11, 0.44), (0.15, 0.24), (0.30, 0.08), (0.40, -0.18), (0.36, -0.38), (0.22, -0.42),
             (0.12, -0.30), (0.12, 0.16), (0.045, 0.22)])
    rect(d, -0.075, -0.36, 0.075, 0.10, DECK, 0.012)
    socket(d, 0.22, 0.02, 0.055)
    socket(d, 0.26, -0.24, 0.06)
    circle(d, 0.0, 0.19, 0.045, GLASS)
    circle(d, 0.0, -0.42, 0.04, EMITTER)
    return im


def heavy_c():
    """Shoulders stop at y 0.06, as the Bulwark's do, so the arm guns' barrels show either side of the head."""
    im, d = canvas()
    core(d, 0.20, 0.30, -0.48, 0.24)
    bridge(d, 0.38, -0.34, 0.02)
    centre_lobe(d, [(0.0, 0.47), (0.10, 0.44), (0.17, 0.30), (0.15, 0.18), (0.06, 0.14)])     # head
    lobe(d, [(0.22, 0.06), (0.40, 0.06), (0.46, -0.10), (0.42, -0.36), (0.28, -0.38), (0.22, -0.20)])  # shoulders
    lobe(d, [(0.08, -0.32), (0.22, -0.30), (0.24, -0.42), (0.14, -0.47), (0.08, -0.44)])      # rear lobes
    rect(d, -0.09, -0.28, 0.09, 0.10, DECK, 0.015)
    socket(d, 0.34, -0.14, 0.06)
    circle(d, 0.0, 0.32, 0.05, GLASS)
    circle(d, 0.0, -0.10, 0.085, EMITTER)
    return im


def heavy_d():
    im, d = canvas()
    core(d, 0.18, 0.28, -0.48, 0.24)
    bridge(d, 0.40, -0.38, 0.00)
    centre_lobe(d, [(0.0, 0.47), (0.12, 0.43), (0.20, 0.26), (0.16, 0.14), (0.06, 0.12)])
    lobe(d, [(0.17, 0.10), (0.30, 0.06), (0.44, 0.02), (0.48, -0.20), (0.40, -0.42), (0.22, -0.46), (0.16, -0.32),
             (0.20, -0.10)])
    rect(d, -0.09, -0.28, 0.09, 0.08, DECK, 0.015)
    socket(d, 0.33, -0.08, 0.06)
    socket(d, 0.30, -0.32, 0.05)
    circle(d, 0.0, 0.30, 0.05, GLASS)
    circle(d, 0.0, -0.10, 0.085, EMITTER)
    return im


def hull_reference():
    files = ["tempest.png", "aurora/aurora.png", "medusa.png", "paragon.png"]
    height = 600
    tiles = []
    for name in files:
        hull = Image.open(GAME_SHIPS / name).convert("RGBA")
        scale = height / hull.height
        tiles.append(hull.resize((round(hull.width * scale), height), Image.Resampling.LANCZOS))
    sheet = Image.new("RGBA", (sum(t.width for t in tiles) + 40 * (len(tiles) - 1), height))
    x = 0
    for tile in tiles:
        sheet.alpha_composite(tile, (x, 0))
        x += tile.width + 40
    return sheet


def main():
    for weight, c, dd in (("light", light_c, light_d), ("medium", medium_c, medium_d), ("heavy", heavy_c, heavy_d)):
        (HERE / weight).mkdir(exist_ok=True)
        c().save(HERE / weight / "guide-c.png")
        dd().save(HERE / weight / "guide-d.png")
    hull_reference().save(HERE / "ref-tt-hulls.png")
    print("wrote light, medium and heavy guide-c/d.png and ref-tt-hulls.png")


if __name__ == "__main__":
    main()
