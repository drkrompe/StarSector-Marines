"""Cut the fallen hull's painted sheets (`generate.py`) into the sprites the game and the look cards draw.

  python art-source/fallen-hull/export.py [--root <dir>]

Every cut is mechanical: a raw is scaled back to its guide's 1536x1024 canvas and each piece cut at the frame
`make_guide.py` gave it (guides/layout.json), then scaled to its shipped density. Nothing is trimmed or recentred, so a
piece's frame is its footprint in whole cells. Written under <root>/graphics/battle/fallen-hull/; the default root is
build/fallen-hull-art, every candidate, for the look cards. `--ship` writes only the picked looks into mod/ (SHIP_*
below): graphics/battle/fallen-hull/forge-lit|out.png, stake-planted|raised|store.png and the walkers' parts under
graphics/battle/derelict-stripper/ and derelict-hauler/.

  hull-<lineage>.png               the whole hull, guide rows 256-767 (64 x 21.33 cells), HULL_PX a cell
  forge-<design>-<lit|out>.png     6 x 6 cells, FORGE_PX a cell
  stake-<design>-<stage>.png       2, 3 or 2 cells square, STAKE_PX a cell
  debris-<name>.png                its frame's cells, DEBRIS_PX a cell
  trench-field.png                 2 x 2 cells seamless, TRENCH_PX a cell; trench-lip.png one cell long, 0.5 deep;
  trench-<patch>.png               the loose patches, alpha feathered to nothing at their frame
  walker-<design>/torso|thigh|shin|joint.png
                                   the walker parts at 128 px a cell (DerelictWalkerAssets.PX_PER_CELL), cut as
                                   art-source/derelict-walker/export.py cuts the sentinel; assembled.png, the whole
                                   walker from the master's left panel at the same density, for the look cards
"""
import argparse
import importlib.util
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
LAYOUT = json.loads((HERE / "guides" / "layout.json").read_text())
CANVAS = tuple(LAYOUT["canvas"])

HULL_PX = 32
FORGE_PX = 96
STAKE_PX = 128
DEBRIS_PX = 64
TRENCH_PX = 64
WALKER_PX = 128

_spec = importlib.util.spec_from_file_location("walker_export", ROOT / "art-source" / "derelict-walker" / "export.py")
walker = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(walker)
sys.path.insert(0, str(ROOT / "art-source" / "roofs"))
_spec = importlib.util.spec_from_file_location("roof_export", ROOT / "art-source" / "roofs" / "export.py")
roof_export = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(roof_export)
seamless = roof_export.seamless


def sheet(name):
    img = Image.open(HERE / "raw" / f"{name}.png").convert("RGBA")
    if img.size != CANVAS:
        img = img.resize(CANVAS, Image.LANCZOS)
    a = img.getchannel("A").point(lambda v: 0 if v < 12 else v)
    img.putalpha(a)
    return img


def cut(img, box, cell_px, out_px):
    x0, y0, x1, y1 = (round(v) for v in box)
    piece = img.crop((x0, y0, x1, y1))
    k = out_px / cell_px
    small = piece.resize((max(1, round(piece.width * k)), max(1, round(piece.height * k))), Image.LANCZOS)
    return small.filter(ImageFilter.UnsharpMask(radius=1.0, percent=50, threshold=2)) if k < 1 else small


def feather(img, margin):
    """Fades a patch's alpha to nothing over the last {@code margin} px of its frame, so it never shows a cut."""
    a = np.asarray(img.getchannel("A")).astype(np.float32)
    h, w = a.shape
    fx = np.clip(np.minimum(np.arange(w), w - 1 - np.arange(w)) / margin, 0, 1)
    fy = np.clip(np.minimum(np.arange(h), h - 1 - np.arange(h)) / margin, 0, 1)
    a *= np.minimum(fy[:, None], fx[None, :])
    img.putalpha(Image.fromarray(a.astype(np.uint8)))
    return img


def export_walker(design, out, ship=False):
    raw = HERE / "raw" / f"{design}.png"
    if not raw.exists():
        print(f"{design}: no raw yet")
        return
    spec = LAYOUT["walkers"][design]
    rgba = walker.keyed(str(raw))
    sx, sy = rgba.shape[1] / CANVAS[0], rgba.shape[0] / CANVAS[1]
    folder = (out.parent / f"derelict-{design}") if ship else out / f"walker-{design}"
    folder.mkdir(parents=True, exist_ok=True)
    parts = spec["parts"]
    # export.py scales by its SCALE: the legs are drawn at 256 px a cell, the torso at its own density.
    walker.SCALE = WALKER_PX / spec["torsoCellPx"]
    walker.cut(rgba, parts["torso"], sx, sy, False).save(folder / "torso.png")
    walker.SCALE = WALKER_PX / spec["legCellPx"]
    walker.cut(rgba, parts["thigh"], sx, sy, True).save(folder / "thigh.png")
    shin = walker.cut(rgba, parts["shin"], sx, sy, True)
    shin.save(folder / "shin.png")
    walker.joint(shin).save(folder / "joint.png")
    if ship:
        return
    # The assembled walker, drawn at 220 px a cell about the left panel's centre (512, 512).
    walker.SCALE = WALKER_PX / 220
    walker.cut(rgba, (0, 0, 1024, 1024), sx, sy, False).save(folder / "assembled.png")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=str(ROOT / "build" / "fallen-hull-art"))
    ap.add_argument("--ship", action="store_true", help="only the picked looks, into mod/ (see SHIP)")
    args = ap.parse_args()
    if args.ship:
        ship()
        return
    out = Path(args.root) / "graphics" / "battle" / "fallen-hull"
    out.mkdir(parents=True, exist_ok=True)
    have = lambda n: (HERE / "raw" / f"{n}.png").exists()  # noqa: E731

    for lineage in ("survey", "carrier", "contingency"):
        if have(f"hull-{lineage}"):
            cut(sheet(f"hull-{lineage}"), (0, 256, 1536, 768), LAYOUT["hull"]["cellPx"], HULL_PX).save(
                out / f"hull-{lineage}.png")
    if have("forge"):
        img = sheet("forge")
        for name, box in LAYOUT["forge"]["frames"].items():
            cut(img, box, LAYOUT["forge"]["cellPx"], FORGE_PX).save(out / f"forge-{name}.png")
    if have("stake"):
        img = sheet("stake")
        for name, box in LAYOUT["stake"]["frames"].items():
            cut(img, box, LAYOUT["stake"]["cellPx"], STAKE_PX).save(out / f"stake-{name}.png")
    if have("debris"):
        img = sheet("debris")
        for name, box in LAYOUT["debris"]["frames"].items():
            cut(img, box, LAYOUT["debris"]["cellPx"], DEBRIS_PX).save(out / f"debris-{name}.png")
    if have("trench"):
        img = sheet("trench")
        t = LAYOUT["trench"]
        x, y, w, h = t["swatch"]
        field = np.asarray(img.crop((x, y, x + w, y + h)).convert("RGB"))
        Image.fromarray(seamless(field)).resize((2 * TRENCH_PX, 2 * TRENCH_PX), Image.LANCZOS).save(
            out / "trench-field.png")
        x, y, w, h = t["strip"]
        band = img.crop((x, y, x + w, y + h))
        k = (TRENCH_PX / 2) / h
        band.resize((round(w * k), round(TRENCH_PX / 2)), Image.LANCZOS).crop((0, 0, TRENCH_PX, TRENCH_PX // 2)).save(
            out / "trench-lip.png")
        for name, box in t["patches"].items():
            feather(cut(img, box, t["cellPx"], TRENCH_PX), 10).save(out / f"trench-{name}.png")
    for design in ("stripper", "hauler", "rig"):
        export_walker(design, out)
    print("wrote", out)


# The looks the game draws, picked meanwhile on each pick's recommended option (`FallenHullLook`): the ring forge, the
# spire stake, two salvage walkers. The hull's walls, roof, trench floor and debris ship in the map tilesets instead
# (`tileset.py`, `art-source/roofs`).
SHIP_FORGE = "ring"
SHIP_STAKE = "spire"
SHIP_WALKERS = ("stripper", "hauler")


def ship():
    out = ROOT / "mod" / "graphics" / "battle" / "fallen-hull"
    out.mkdir(parents=True, exist_ok=True)
    img = sheet("forge")
    for state in ("lit", "out"):
        name = f"{SHIP_FORGE}-{state}"
        cut(img, LAYOUT["forge"]["frames"][name], LAYOUT["forge"]["cellPx"], FORGE_PX).save(out / f"forge-{state}.png")
    img = sheet("stake")
    for stage in ("planted", "raised", "store"):
        cut(img, LAYOUT["stake"]["frames"][f"{SHIP_STAKE}-{stage}"], LAYOUT["stake"]["cellPx"], STAKE_PX).save(
            out / f"stake-{stage}.png")
    for design in SHIP_WALKERS:
        export_walker(design, out, ship=True)
    print("shipped", out)


if __name__ == "__main__":
    main()
