"""Owner review of the exported dry-scrub tilesets beside the green forest ones, on sand (1920x1080).

python art-source/dry-woodland/review_export.py <out-dir>

Reads the exported sheets and tileset JSON under mod/ (so it shows exactly what ships). Writes
0-overview.png (all six states, green beside dry, at the 32 px a cell game scale) and one close-up per state at
64 px a cell (trees) or 96 px a cell (undergrowth), green above dry, with each piece's blocked cells outlined.
"""
import json
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE.parent))
from review_font import review_font  # noqa: E402

W, H = 1920, 1080
SAND = (176, 148, 92, 255)
STATES = ["canopies", "damaged", "destroyed", "undergrowth", "undergrowth-damaged", "undergrowth-destroyed"]
TREES = {"oak": (5, 5, 8), "oak-small": (4, 4, 7), "birch": (5, 4, 9), "pine": (3, 3, 9), "pine-large": (4, 4, 11),
         "cedar": (4, 6, 10)}


def tree_lobes(w, h, n):
    rx, ry = w * .41, h * .41
    lobes = [(w * .5, h * .5, rx * .84, ry * .84)]
    for i in range(n):
        a = i * math.pi * 2 / n + .19
        s = .30 + (i % 3) * .025
        lobes.append((w * .5 + math.cos(a) * rx * .63, h * .5 + math.sin(a) * ry * .63, rx * s, ry * s))
    return lobes


def undergrowth_lobes():
    src = (ROOT / "src/main/java/com/dillon/starsectormarines/battle/world/gen/forest/Undergrowth.java").read_text()
    out = {}
    for m in re.finditer(r'\("([\w-]+)", \d, \d, \w+, 0x\w+, List\.of\((.*?)\)\)', src, re.S):
        out[m.group(1)] = [tuple(map(float, t)) for t in
                           re.findall(r"new Lobe\(([\d.]+), ([\d.]+), ([\d.]+), ([\d.]+)\)", m.group(2))]
    return out


UG = undergrowth_lobes()


def species(doodad_id):
    s = doodad_id[len("forest."):]
    for suf in ("-dry-damaged", "-dry-destroyed", "-dry", "-damaged", "-destroyed"):
        if s.endswith(suf):
            return s[:-len(suf)]
    return s


def blocked(sp, w, h):
    lobes = tree_lobes(*TREES[sp]) if sp in TREES else UG[sp]

    def hit(lx, ly, rx, ry, x, y):
        dx = max(x - lx, max(0, lx - (x + 1))) / rx
        dy = max(y - ly, max(0, ly - (y + 1))) / ry
        return dx * dx + dy * dy <= 1
    return [[any(hit(*l, x, y) for l in lobes) for x in range(w)] for y in range(h)]


def pieces(prefix, state):
    d = json.loads((ROOT / f"mod/data/tilesets/{prefix}-{state}.tileset.json").read_text())
    sheet = Image.open(ROOT / f"mod/graphics/doodads/{prefix}-{state}.png").convert("RGBA")
    out = []
    for e in d["doodads"]:
        w, h = e["footprintCells"]
        c, r = e["col"], e["row"]
        out.append((e["id"], (w, h), sheet.crop((c * 64, r * 64, (c + w) * 64, (r + h) * 64))))
    return out


def label(dr, xy, text, size=24, bold=True):
    f = review_font(size, bold)
    dr.text((xy[0] + 2, xy[1] + 2), text, font=f, fill=(0, 0, 0))
    dr.text(xy, text, font=f, fill=(250, 246, 236))


def row(im, prefix, state, x, y, cell, gap, outline=False, names=False):
    dr = ImageDraw.Draw(im)
    for pid, (w, h), spr in pieces(prefix, state):
        if outline:
            for cy, line in enumerate(blocked(species(pid), w, h)):
                for cx, b in enumerate(line):
                    box = (x + cx * cell, y + cy * cell, x + (cx + 1) * cell - 1, y + (cy + 1) * cell - 1)
                    dr.rectangle(box, outline=(120, 96, 56) if b else (200, 176, 120))
        im.alpha_composite(spr.resize((w * cell, h * cell), Image.LANCZOS), (x, y))
        if names:
            label(dr, (x, y + h * cell + 2), pid, 15, False)
        x += w * cell + gap


def overview(out):
    im = Image.new("RGBA", (W, H), SAND)
    dr = ImageDraw.Draw(im)
    label(dr, (20, 10), "Green forest (left) and grey dry scrub (right), every exported state, 32 px a cell (game scale)", 26)
    y = 60
    for st in STATES:
        h = 6 if st in ("canopies", "damaged", "destroyed") else 3
        row(im, "forest", st, 20, y, 32, 12)
        row(im, "dry-scrub", st, 980, y, 32, 12)
        label(dr, (20, y), st, 18, False)
        y += h * 32 + 12
    dr.line((960, 50, 960, H), fill=(120, 96, 56), width=2)
    im.convert("RGB").save(out / "0-overview.png")


def closeup(out, i, st):
    im = Image.new("RGBA", (W, H), SAND)
    dr = ImageDraw.Draw(im)
    tree = st in ("canopies", "damaged", "destroyed")
    cell, gap = (64, 30) if tree else (96, 40)
    label(dr, (20, 10), f"{st}: green (top) and grey dry scrub (bottom), {cell} px a cell, blocked cells outlined dark", 26)
    rows = 6 if tree else 3
    row(im, "forest", st, 20, 60, cell, gap, outline=True, names=True)
    row(im, "dry-scrub", st, 20, 60 + rows * cell + 60, cell, gap, outline=True, names=True)
    im.convert("RGB").save(out / f"{i}-{st}.png")


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    overview(out)
    for i, st in enumerate(STATES, 1):
        closeup(out, i, st)
    print("wrote", sorted(p.name for p in out.glob("*.png")))


if __name__ == "__main__":
    main()
