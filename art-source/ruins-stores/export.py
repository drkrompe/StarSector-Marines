"""Cuts the ruins stores' loads (``stores_list.py``) from the ImageGen sheet, adds each cacheable load's field-cache
variant and packs the ``ruins-stores`` doodad sheet. Run from the worktree root:
``python art-source/ruins-stores/export.py``.

A field cache (``loot-nouns.md``) is the same load on an open recovery skid: the skid's amber lifting rails down both
sides, dark steel cross-members at the ends and an amber lifting tab at the front, the silhouette the field salvage
pickups share. It is drawn over the load here rather than generated, so a cache always shows exactly the load a store
holds. Loads stand with medium cover; caches carry none, as pickup art supplies no cover.
"""
import json
import random
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "art-source" / "doodads"))
import make_guide  # noqa: E402
import stores_list  # noqa: E402
from stitch_atlas import BuildOptions, build_atlas  # noqa: E402

CELL_PX = 128
SHEET = "ruins-stores"
AMBER, AMBER_LIT, AMBER_DARK = (206, 146, 38), (238, 186, 84), (130, 86, 22)
STEEL, STEEL_LIT, STEEL_DARK = (46, 48, 52), (78, 82, 88), (24, 25, 28)


# Pieces cut from a raw of their own instead of the sheet: the first heavy machinery was its side elevation laid on
# the ground (owner: "placed side ways"), redrawn as a plan view.
REDRAWN = {"store-heavy-machinery": "ruins-stores-machinery.raw.png"}


def register(raw, frames):
    """Scale and offset taking guide pixels to raw pixels: the frames' union onto the raw's drawn extent."""
    a = np.asarray(raw.getchannel("A")) > 64
    ys, xs = np.nonzero(a)
    gx0, gy0 = min(f[1] for f in frames), min(f[2] for f in frames)
    gx1, gy1 = max(f[1] + f[3] for f in frames), max(f[2] + f[4] for f in frames)
    sx = (xs.max() + 1 - xs.min()) / (gx1 - gx0)
    sy = (ys.max() + 1 - ys.min()) / (gy1 - gy0)
    return sx, sy, xs.min() - gx0 * sx, ys.min() - gy0 * sy


def cut():
    """Each piece fitted to its footprint: its drawn extent within its frame, half a gutter either side."""
    raw = Image.open(HERE / "raw" / "ruins-stores.raw.png").convert("RGBA")
    raw.putalpha(raw.getchannel("A").point(lambda v: 0 if v < 8 else v))
    frames = make_guide.frames()
    sx, sy, ox, oy = register(raw, frames)
    half = make_guide.GUTTER / 2
    sizes = {e[0]: (e[1], e[2]) for e in stores_list.loads()}
    for pid, x, y, w, h in frames:
        if pid in REDRAWN:
            # Drawn again on a raw of its own (make_guide.machinery_guide): the whole raw is the piece.
            piece = Image.open(HERE / "raw" / REDRAWN[pid]).convert("RGBA")
            piece = piece.crop(piece.getchannel("A").point(lambda v: 255 if v > 64 else 0).getbbox())
            cw, ch = sizes[pid]
            yield pid, piece.resize((cw * CELL_PX, ch * CELL_PX), Image.LANCZOS)
            continue
        window =(round(ox + (x - half) * sx), round(oy + (y - half) * sy), round(ox + (x + w + half) * sx),
                  round(oy + (y + h + half) * sy))
        piece = raw.crop(window)
        piece = piece.crop(piece.getchannel("A").point(lambda v: 255 if v > 64 else 0).getbbox())
        cw, ch = sizes[pid]
        yield pid, piece.resize((cw * CELL_PX, ch * CELL_PX), Image.LANCZOS)


def bar(d, box, base, lit, dark, edge=3):
    """A bevelled bar: lit along its top and left edges, dark along its bottom and right."""
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=base)
    d.line((x0, y0, x1, y0), fill=lit, width=edge)
    d.line((x0, y0, x0, y1), fill=lit, width=edge)
    d.line((x0, y1, x1, y1), fill=dark, width=edge)
    d.line((x1, y0, x1, y1), fill=dark, width=edge)


def cached(load, pid):
    """The load on the recovery skid's amber rails."""
    w, h = load.size
    c = CELL_PX
    rail, inset, cross = round(0.14 * c), round(0.04 * c), round(0.1 * c)
    skid = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(skid)
    # Steel cross-members at both ends, under the rails.
    for yy in (inset + round(0.06 * c), h - inset - round(0.06 * c) - cross):
        bar(d, (inset, yy, w - inset, yy + cross), STEEL, STEEL_LIT, STEEL_DARK)
    # The amber lifting rails down both sides, proud of the cross-members at each end.
    for xx in (inset, w - inset - rail):
        bar(d, (xx, 2, xx + rail, h - 3), AMBER, AMBER_LIT, AMBER_DARK)
        for yy in (inset + round(0.06 * c) + cross // 2, h - inset - round(0.06 * c) - cross // 2):
            r = max(2, rail // 5)
            d.ellipse((xx + rail / 2 - r, yy - r, xx + rail / 2 + r, yy + r), fill=AMBER_DARK)
    # The lifting tab at the front.
    tw, th = round(0.34 * c), round(0.1 * c)
    bar(d, (w / 2 - tw / 2, h - th - 2, w / 2 + tw / 2, h - 3), AMBER, AMBER_LIT, AMBER_DARK)
    # Wear: dust and chipped paint, so the rails sit in the same world as the load.
    rng = random.Random(pid)
    px = skid.load()
    for _ in range(w * h // 18):
        x, y = rng.randrange(w), rng.randrange(h)
        p = px[x, y]
        if p[3]:
            k = rng.uniform(0.72, 1.08)
            px[x, y] = tuple(min(255, round(v * k)) for v in p[:3]) + (p[3],)
    shadow = skid.getchannel("A").filter(ImageFilter.GaussianBlur(2)).point(lambda v: v * 0.5)
    out = load.copy()
    out.alpha_composite(Image.merge("RGBA", (*(Image.new("L", (w, h), 0),) * 3, shadow)), (2, 2))
    out.alpha_composite(skid)
    return out


def main():
    assets = {}
    order = 0
    with tempfile.TemporaryDirectory() as scratch:
        staged = Path(scratch)
        info = {e[0]: e for e in stores_list.loads()}
        for pid, piece in cut():
            _, w, h, _, cache, name, what = info[pid]
            piece.save(staged / f"{pid}.png")
            order += 1
            assets[f"{pid}.png"] = {"id": f"doodad.ruins-{pid}", "name": name, "description": what,
                                    "footprintCells": [w, h], "preserveCanvas": True, "padding": 0, "order": order,
                                    "cover": "med", "ballisticHalfHeight": 0.4}
            if cache:
                cid = f"{pid}-cache"
                cached(piece, pid).save(staged / f"{cid}.png")
                order += 1
                assets[f"{cid}.png"] = {"id": f"doodad.ruins-{cid}", "name": f"{name}, field cache",
                                        "description": f"{what} On a recovery skid's amber rails: a field cache, "
                                                       "pickup art only (loot-nouns).",
                                        "footprintCells": [w, h], "preserveCanvas": True, "padding": 0,
                                        "order": order, "cover": "none"}
        meta = HERE / "_atlas.json"
        with open(meta, "w") as f:
            json.dump({"defaults": {"cover": "med", "padding": 4, "scale": 1.0, "offset": [0, 0]}, "assets": assets},
                      f, indent=2)
            f.write("\n")
        _, _, count = build_atlas(BuildOptions(
            input_dir=staged,
            output_image=ROOT / "mod" / "graphics" / "doodads" / f"{SHEET}.png",
            output_manifest=ROOT / "mod" / "data" / "tilesets" / f"{SHEET}.tileset.json",
            sheet_path=f"graphics/doodads/{SHEET}.png",
            metadata_path=meta,
            columns=16,
            cell_size=CELL_PX,
            padding=4,
            id_prefix="doodad.",
            resample="lanczos",
            alpha_threshold=1,
            allow_opaque=False,
            only_prefix=None,
        ))
    print(f"Packed {count} {SHEET} assets")


if __name__ == "__main__":
    main()
