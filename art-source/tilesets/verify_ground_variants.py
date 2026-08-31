"""Check generated ground tiles against the contract in GROUND-VARIANT-PROMPTS.md.

ImageGen does not report when it has ignored a constraint, so every claim in
that document is checked here mechanically. Thresholds are calibrated against
the cells that already ship in Floors_Tiles.png -- run with --baseline to print
what the shipped art scores, which is what "acceptable" actually means here
rather than a number picked in advance.

Usage:
    python verify_ground_variants.py --baseline
    python verify_ground_variants.py <dir-of-56x56-pngs> [--family NAME]
"""

import argparse
import pathlib
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit("needs Pillow: python -m pip install Pillow")

CELL = 56
SHEET = pathlib.Path("mod/graphics/tilesets/Floors_Tiles.png")
SHIPPED = {
    "floors.stone": [(8, 0), (9, 0), (10, 0)],
    "floors.sand": [(0, 1), (1, 1), (2, 1)],
    "floors.grass": [(5, 0), (6, 0), (7, 0)],
    "floors.dirt": [(11, 0), (12, 0), (13, 0)],
}


def rows(img):
    px = img.load()
    return [[px[x, y][:3] for x in range(img.width)] for y in range(img.height)]


def mean_abs_diff(a, b):
    return sum(abs(p[c] - q[c]) for p, q in zip(a, b) for c in range(3)) / (3 * len(a))


def seam_scores(img):
    """Seam energy across the wrap versus a typical interior step.

    A seamlessly tileable cell continues into its own opposite edge, so the
    wrap difference should sit in the same range as an ordinary neighbouring
    row or column. A ratio far above 1 means a visible lattice when tiled.
    """
    r = rows(img)
    cols = list(zip(*r))
    v_wrap = mean_abs_diff(r[-1], r[0])
    h_wrap = mean_abs_diff(cols[-1], cols[0])
    v_int = sum(mean_abs_diff(r[i], r[i + 1]) for i in range(len(r) - 1)) / (len(r) - 1)
    h_int = sum(mean_abs_diff(cols[i], cols[i + 1]) for i in range(len(cols) - 1)) / (len(cols) - 1)
    return (v_wrap / v_int if v_int else 99.0, h_wrap / h_int if h_int else 99.0)


def mean_rgb(img):
    r = rows(img)
    n = img.width * img.height
    return tuple(sum(px[c] for row in r for px in row) / n for c in range(3))


def opaque(img):
    if img.mode != "RGBA":
        return True
    return min(p[3] for p in img.convert("RGBA").getdata()) == 255


def report(name, tiles):
    print(f"\n{name}  ({len(tiles)} tiles)")
    means = []
    worst = 0.0
    for label, img in tiles:
        v, h = seam_scores(img)
        m = mean_rgb(img)
        means.append(m)
        worst = max(worst, v, h)
        flags = []
        if img.size != (CELL, CELL):
            flags.append(f"SIZE {img.size[0]}x{img.size[1]}")
        if not opaque(img):
            flags.append("NOT OPAQUE")
        print(f"  {label:28s} seam v={v:5.2f} h={h:5.2f}  "
              f"mean=({m[0]:5.1f},{m[1]:5.1f},{m[2]:5.1f})"
              + ("  <-- " + ", ".join(flags) if flags else ""))
    spread = max(max(abs(m[c] - n[c]) for c in range(3)) for m in means for n in means)
    print(f"  worst seam ratio {worst:.2f}   sibling colour spread {spread:.1f}")
    return worst, spread


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dir", nargs="?")
    ap.add_argument("--baseline", action="store_true")
    ap.add_argument("--family", default="generated")
    args = ap.parse_args()

    if args.baseline:
        if not SHEET.exists():
            sys.exit(f"run from the repo root; {SHEET} not found")
        sheet = Image.open(SHEET).convert("RGB")
        for fam, cells in SHIPPED.items():
            tiles = [(f"{fam}[{cx},{cy}]",
                      sheet.crop((cx * CELL, cy * CELL, (cx + 1) * CELL, (cy + 1) * CELL)))
                     for cx, cy in cells]
            report(fam, tiles)
        return

    if not args.dir:
        sys.exit("give a directory of PNGs, or --baseline")
    files = sorted(pathlib.Path(args.dir).glob("*.png"))
    if not files:
        sys.exit(f"no PNGs in {args.dir}")
    report(args.family, [(f.name, Image.open(f)) for f in files])


if __name__ == "__main__":
    main()
