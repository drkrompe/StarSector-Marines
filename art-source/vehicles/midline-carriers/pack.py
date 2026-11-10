"""Pack the midline carriers' sheets from the retained ImageGen raws (guide.py's layout; mech-lines.md, Midline).

  mod/graphics/battle/vehicles/midline-carrier-<weight>.png

Each raw is one square generation over guide.py's 1024 px canvas: the live machine in the top row, the wreck in the
bottom, laid out straight and facing LEFT. A raw is resized to the guide canvas (premultiplied Lanczos), then to OUT_PX
a cell. Every frame is the same size, the whole straight machine's canvas centred on the hull's centre (half way nose
to tail), in rows of four:

  row 0   hull live, (no turret), hull wreck, (no turret)      the whole machine, for previews and every rigid reader
  row 1+  each segment live, then each segment wreck           cab, (power,) module: one layer each, cut at the hinge
                                                               centres, so each can swing at its hinge in the game

A two-segment hull keeps its live and wreck segments in one row; the heavy's three take a row each. The segment
frames keep the straight registration, so the game draws a segment by turning its whole frame about its front hinge
(VehicleType.articulation, TrailerKinematic). Wrecks are graded darker. Generator alpha haze at or below 8 is cleared.
The figures the hull's VehicleType constant needs are printed.

Run: python art-source/vehicles/midline-carriers/pack.py
"""
from pathlib import Path

from PIL import Image, ImageEnhance

from guide import CANVAS, HULLS, ROW, layout, length, px_per_cell

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[2] / "mod" / "graphics" / "battle" / "vehicles"
OUT_PX = 176
MARGIN = 0.12


def load(weight):
    raw = Image.open(HERE / f"{weight}-raw.png").convert("RGBA")
    if raw.size != (CANVAS, CANVAS):
        raw = raw.convert("RGBa").resize((CANVAS, CANVAS), Image.LANCZOS).convert("RGBA")
    raw.putalpha(raw.getchannel("A").point(lambda v: 0 if v <= 8 else v))
    return raw


def grade_wreck(img):
    rgb = ImageEnhance.Color(img.convert("RGB")).enhance(.7)
    rgb = ImageEnhance.Brightness(rgb).enhance(.72)
    out = rgb.convert("RGBA")
    out.putalpha(img.getchannel("A"))
    return out


def pack(weight):
    hull = HULLS[weight]
    ppc = px_per_cell(hull)
    total = length(hull)
    wl, jut, depth = hull["wheel"]
    span = max(s[2] for s in hull["segments"]) + 2 * jut
    fw = 2 * round((total + 2 * MARGIN) * OUT_PX / 2)
    fh = 2 * round((span + 2 * MARGIN) * OUT_PX / 2)
    raw = load(weight)
    scale = OUT_PX / ppc
    big = raw.convert("RGBa").resize((round(CANVAS * scale), round(CANVAS * scale)), Image.LANCZOS).convert("RGBA")
    cx = big.width / 2

    def row(r):
        cy = (ROW * r + ROW / 2) * scale
        return big.crop((round(cx - fw / 2), round(cy - fh / 2), round(cx - fw / 2) + fw, round(cy - fh / 2) + fh))

    live, wreck = row(0), grade_wreck(row(1))
    edges, hinges = layout(hull)
    nose_px = fw / 2 - total / 2 * OUT_PX
    cuts = [0] + [round(nose_px + h * OUT_PX) for h in hinges] + [fw]

    def segment(img, s):
        out = Image.new("RGBA", (fw, fh))
        out.paste(img.crop((cuts[s], 0, cuts[s + 1], fh)), (cuts[s], 0))
        return out

    n = len(hull["segments"])
    frames = [live, None, wreck, None]
    if n <= 2:
        frames += [segment(live, s) for s in range(n)] + [segment(wreck, s) for s in range(n)]
    else:
        frames += [segment(live, s) for s in range(n)] + [None]
        frames += [segment(wreck, s) for s in range(n)] + [None]
    rows = (len(frames) + 3) // 4
    sheet = Image.new("RGBA", (fw * 4, fh * rows))
    for i, img in enumerate(frames):
        if img is not None:
            sheet.paste(img, ((i % 4) * fw, (i // 4) * fh))
    sheet.save(OUT / f"midline-carrier-{weight}.png", optimize=True)

    # Straight-frame figures, cells, +Y forward from the hull's centre.
    def y_of(from_nose):
        return total / 2 - from_nose
    hinge_y = [y_of(h) for h in hinges]
    axle_y = [y_of(front + sum(ax) / len(ax)) for (_, _, _, ax), (front, _) in zip(hull["segments"], edges)]
    by_name = {s[0]: e for s, e in zip(hull["segments"], edges)}
    print(f"{weight}: sheet {sheet.size}, frame {fw}x{fh}, drawn length {fw / OUT_PX:.4f} cells")
    print(f"  footprint {total:.3f} x {max(s[2] for s in hull['segments']):.3f} cells")
    print(f"  hingeY {[round(v, 4) for v in hinge_y]}  axleY {[round(v, 4) for v in axle_y]}")
    for seg, off, radius, kind in hull["mounts"]:
        print(f"  {kind} mount at y {y_of(by_name[seg][0] + off):.4f}, ring {2 * radius:.3f} cells across")


def main():
    for weight in HULLS:
        pack(weight)


if __name__ == "__main__":
    main()
