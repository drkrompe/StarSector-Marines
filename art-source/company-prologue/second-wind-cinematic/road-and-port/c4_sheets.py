"""C4 plate tiles: pack the layer guides into Codex sheets, then register the paint back.

    python c4_sheets.py build              # guides/sheets/c4-sheet-N-guide.png + c4-sheets.json
    python c4_sheets.py register [N ...]   # assets/c4-sheet-N-v*.png -> plates/c4-<layer>-plate.png

Why sheets. A layer is a very wide, short band (L4f is 5,300 x 240 output px), and one Codex
generation returns about 1.6 MP at the aspect it is given. So each layer is cut into
overlapping segments 1,715 output px wide, each scaled by DENSITY, and several segments
(adjacent depths together) are stacked into one 1886 x 834 canvas with black separator
bars. Five sheets cover all five ground layers at 1.1x output density.

Why registration. Codex keeps the layout of an edit target but not to the pixel: the look
test drifted about 30 px in its lower bands. `register` finds each strip in the painted
sheet, measures a smooth displacement field against the clay guide (normalised cross-
correlation of gradient images on a grid of patches), and warps the paint back onto the
guide before anything is stitched. Overlaps between segments are feathered. The layer's
alpha comes from the guide (grown slightly), refined by distance from the flat key sky
the clay was set on, so painted silhouettes cut cleanly against the layer behind.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

BASE = Path(__file__).resolve().parent
sys.path.insert(0, str(BASE))
import c4_camera as cam  # noqa: E402

SHEET_W, SHEET_H = 1886, 834
DENSITY = 1.1               # sheet px per output px
OVERLAP = 80                # output px shared by neighbouring segments
SEP = 10                    # black separator bar, sheet px
KEY = (204, 218, 232)       # flat horizon sky behind every non-sky layer's skyline
# Plate rows (at plate scale, 2x output) each layer keeps: its band, what rises above it, and a
# margin under the nearer layer's ragged top.
ROWS = {"L1-far": (0, 640), "L2-port": (530, 906), "L3-mid": (800, 1200), "L4-near": (1050, 1460),
        "L4f-fore": (1262, 1742)}
# Which segments share a canvas: adjacent depths together, so each brief describes one scene.
SHEETS = [["L1-far"] * 2, ["L4f-fore"] * 3, ["L4f-fore", "L4-near", "L4-near"], ["L4-near", "L3-mid", "L3-mid"],
          ["L3-mid", "L2-port", "L2-port"]]
LAYER = {L["id"]: L for L in cam.LAYERS}
GUIDES, SHEET_DIR, PLATES, ASSETS = BASE / "guides", BASE / "guides" / "sheets", BASE / "plates", BASE / "assets"


def layer_guide_1x(lid):
    """The clay guide at output scale (1x), cropped to its rows, hazed as the layer will be,
    on the flat key sky. Returns (rgb float array, alpha float array, y0 at 1x)."""
    im = Image.open(GUIDES / ("c4-%s-guide.png" % lid)).convert("RGBA")
    y0, y1 = ROWS[lid]
    im = im.crop((0, y0, im.width, y1))
    im = im.resize((im.width // 2, im.height // 2), Image.LANCZOS)
    a = np.asarray(im, np.float32) / 255.0
    rgb, alpha = a[..., :3], a[..., 3]
    haze = LAYER[lid]["haze"]
    rgb = rgb * (1 - haze) + np.array(KEY, np.float32) / 255.0 * haze
    key = np.array(KEY, np.float32) / 255.0
    rgb = rgb * alpha[..., None] + key * (1 - alpha[..., None])
    return rgb, alpha, y0 // 2


def segments(lid):
    """[(x0, x1)] in output px: equal overlapping segments covering the layer."""
    w = cam.plate_size(LAYER[lid])[0] // 2
    seg = SHEET_W / DENSITY
    n = max(1, math.ceil((w - OVERLAP) / (seg - OVERLAP)))
    step = (w - seg) / (n - 1) if n > 1 else 0
    return [(round(i * step), round(i * step + seg)) for i in range(n)]


def plan():
    """Assign every layer segment to its sheet slot, in x order within each layer."""
    queue = {lid: list(segments(lid)) for lid in ROWS}
    out = []
    for n, slots in enumerate(SHEETS, 1):
        y = 0
        strips = []
        for lid in slots:
            x0, x1 = queue[lid].pop(0)
            r0, r1 = ROWS[lid]
            h = round((r1 - r0) / 2 * DENSITY)
            strips.append(dict(layer=lid, x0=x0, x1=x1, y0=r0 // 2, y1=r1 // 2, sheet_y=y, sheet_h=h))
            y += h + SEP
        assert y - SEP <= SHEET_H, (n, y)
        out.append(dict(sheet=n, strips=strips))
    left = {k: v for k, v in queue.items() if v}
    assert not left, left
    return out


def build():
    SHEET_DIR.mkdir(parents=True, exist_ok=True)
    layout = plan()
    cache = {}
    for s in layout:
        canvas = np.zeros((SHEET_H, SHEET_W, 3), np.float32)
        for st in s["strips"]:
            if st["layer"] not in cache:
                cache[st["layer"]] = layer_guide_1x(st["layer"])
            rgb, _, _ = cache[st["layer"]]
            piece = Image.fromarray((rgb[:, st["x0"]:st["x1"]] * 255).astype(np.uint8))
            piece = piece.resize((SHEET_W, st["sheet_h"]), Image.LANCZOS)
            canvas[st["sheet_y"]:st["sheet_y"] + st["sheet_h"]] = np.asarray(piece, np.float32) / 255
        Image.fromarray((canvas * 255).astype(np.uint8)).save(SHEET_DIR / ("c4-sheet-%d-guide.png" % s["sheet"]))
    (SHEET_DIR / "c4-sheets.json").write_text(json.dumps(dict(sheet=[SHEET_W, SHEET_H], density=DENSITY,
                                                              overlap=OVERLAP, sep=SEP, sheets=layout), indent=1) + "\n",
                                              encoding="utf-8")
    for s in layout:
        print("sheet", s["sheet"], [(st["layer"], st["x0"], st["x1"], st["sheet_h"]) for st in s["strips"]])


# ------------------------------------------------------------------ registration

def gray(a):
    return a[..., 0] * 0.3 + a[..., 1] * 0.59 + a[..., 2] * 0.11


def grad(g):
    gx = np.zeros_like(g)
    gy = np.zeros_like(g)
    gx[:, 1:-1] = g[:, 2:] - g[:, :-2]
    gy[1:-1] = g[2:] - g[:-2]
    return np.hypot(gx, gy)


def ncc_shift(ref, mov, search):
    """Integer (dy, dx) that best aligns mov to ref (mov[y+dy, x+dx] ~ ref[y, x]), by brute
    normalised cross-correlation over +-search; mov is ref-shaped plus a search margin."""
    h, w = ref.shape
    r = (ref - ref.mean()) / (ref.std() + 1e-6)
    best, arg = -2.0, (0, 0)
    for dy in range(-search, search + 1):
        for dx in range(-search, search + 1, 1):
            m = mov[search + dy:search + dy + h, search + dx:search + dx + w]
            mm = (m - m.mean()) / (m.std() + 1e-6)
            c = float((r * mm).mean())
            if c > best:
                best, arg = c, (dy, dx)
    return arg, best


def bilinear(img, yy, xx):
    h, w = img.shape[:2]
    yy = np.clip(yy, 0, h - 1.001)
    xx = np.clip(xx, 0, w - 1.001)
    y0, x0 = np.floor(yy).astype(int), np.floor(xx).astype(int)
    fy, fx = (yy - y0)[..., None], (xx - x0)[..., None]
    return (img[y0, x0] * (1 - fy) * (1 - fx) + img[y0, x0 + 1] * (1 - fy) * fx
            + img[y0 + 1, x0] * fy * (1 - fx) + img[y0 + 1, x0 + 1] * fy * fx)


def smooth1d(v, k):
    if len(v) < 3:
        return v
    pad = np.pad(v, k, mode="edge")
    out = np.array([np.median(pad[i:i + 2 * k + 1]) for i in range(len(v))])
    ker = np.ones(3) / 3
    return np.convolve(np.pad(out, 1, mode="edge"), ker, mode="valid")


def register_strip(paint, guide, search=14, patch=96, stride=48, rows=3):
    """Warp a painted strip (sheet px, guide-shaped plus margin rows above/below) onto its guide.
    Returns (registered rgb, field report)."""
    g = grad(gray(guide))
    p = grad(gray(paint))
    h, w = guide.shape[:2]
    m = (paint.shape[0] - h) // 2
    ph = min(patch, h - 2)
    ys = np.linspace(0, h - ph, rows).round().astype(int)
    xs = np.arange(0, w - patch + 1, stride)
    dy = np.zeros((rows, len(xs)))
    dx = np.zeros((rows, len(xs)))
    q = np.zeros((rows, len(xs)))
    pp = np.pad(p, ((0, 0), (search, search)), mode="edge")
    for i, y in enumerate(ys):
        for j, x in enumerate(xs):
            ref = g[y:y + ph, x:x + patch]
            mov = pp[m + y - search:m + y + ph + search, x:x + patch + 2 * search]
            if ref.std() < 1e-3 or mov.shape != (ph + 2 * search, patch + 2 * search):
                q[i, j] = -1
                continue
            (a, b), c = ncc_shift(ref, mov, search)
            dy[i, j], dx[i, j], q[i, j] = a, b, c
    # weak matches take their row's median; then smooth along x
    for i in range(rows):
        good = q[i] > 0.35
        med_y = np.median(dy[i][good]) if good.any() else 0.0
        med_x = np.median(dx[i][good]) if good.any() else 0.0
        dy[i][~good], dx[i][~good] = med_y, med_x
        dy[i], dx[i] = np.clip(smooth1d(dy[i], 3), -10, 10), np.clip(smooth1d(dx[i], 3), -3, 3)
    cy = ys + ph / 2
    cx = xs + patch / 2
    Y, X = np.mgrid[0:h, 0:w].astype(np.float32)
    # interpolate the grid field to every pixel (linear in x per row, then in y)
    fy = np.stack([np.interp(np.arange(w), cx, dy[i]) for i in range(rows)])
    fx = np.stack([np.interp(np.arange(w), cx, dx[i]) for i in range(rows)])
    FY = np.stack([np.interp(np.arange(h), cy, fy[:, c]) for c in range(w)], 1)
    FX = np.stack([np.interp(np.arange(h), cy, fx[:, c]) for c in range(w)], 1)
    out = bilinear(paint, Y + m + FY, X + FX)
    rep = dict(dy=[round(float(v), 1) for v in np.median(dy, 1)], dx=[round(float(v), 1) for v in np.median(dx, 1)],
               dy_range=[round(float(dy.min()), 1), round(float(dy.max()), 1)],
               match=round(float(np.median(q[q > -1])) if (q > -1).any() else 0.0, 3))
    return out, rep


def strip_spans(img, n):
    """The painted rows of each strip, found from the black separator bars Codex kept."""
    dark = gray(img).mean(1) < 12 / 255
    spans, start = [], None
    for y, d in enumerate(dark):
        if not d and start is None:
            start = y
        if d and start is not None:
            spans.append((start, y))
            start = None
    if start is not None:
        spans.append((start, len(dark)))
    spans = [sp for sp in spans if sp[1] - sp[0] > 40]
    return spans if len(spans) == n else None


def latest(n):
    vs = sorted(ASSETS.glob("c4-sheet-%d-v*.png" % n), key=lambda p: int(p.stem.rsplit("-v", 1)[1]))
    return vs[-1] if vs else None


def register(which=None):
    meta = json.loads((SHEET_DIR / "c4-sheets.json").read_text(encoding="utf-8"))
    PLATES.mkdir(exist_ok=True)
    acc = {}
    report = {}
    cache = {}
    for s in meta["sheets"]:
        src = latest(s["sheet"])
        if src is None:
            print("sheet %d: no paint yet" % s["sheet"])
            continue
        guide_sheet = np.asarray(Image.open(SHEET_DIR / ("c4-sheet-%d-guide.png" % s["sheet"])).convert("RGB"), np.float32) / 255
        im = Image.open(src).convert("RGB")
        native = im.size
        sheet = np.asarray(im.resize((SHEET_W, SHEET_H), Image.LANCZOS), np.float32) / 255
        for k, st in enumerate(s["strips"]):
            lid = st["layer"]
            if lid not in cache:
                cache[lid] = layer_guide_1x(lid)
            rgb, alpha, y0 = cache[lid]
            spans = strip_spans(sheet, len(s["strips"])) or [(t["sheet_y"], t["sheet_y"] + t["sheet_h"]) for t in s["strips"]]
            p0, p1 = spans[k]
            # stretch the painted rows onto the guide's strip height, then a margin for the field
            body = Image.fromarray((sheet[p0:p1] * 255).astype(np.uint8)).resize((SHEET_W, st["sheet_h"]), Image.LANCZOS)
            body = np.asarray(body, np.float32) / 255
            m = 12
            piece = np.pad(body, ((m, m), (0, 0), (0, 0)), mode="edge")
            gpiece = guide_sheet[st["sheet_y"]:st["sheet_y"] + st["sheet_h"]]
            off = [p0 - st["sheet_y"], p1 - st["sheet_y"] - st["sheet_h"]]
            reg, rep = register_strip(piece, gpiece)
            rep.update(strip_offset=off, source=src.name, native=list(native))
            report["sheet%d.%d %s %d-%d" % (s["sheet"], k, lid, st["x0"], st["x1"])] = rep
            # back to output scale; segments are stitched per layer below
            w, h = st["x1"] - st["x0"], rgb.shape[0]
            out = np.asarray(Image.fromarray((np.clip(reg, 0, 1) * 255).astype(np.uint8)).resize((w, h), Image.LANCZOS),
                             np.float32) / 255
            acc.setdefault(lid, []).append((st["x0"], st["x1"], out))
    stitched = {}
    for lid, segs in acc.items():
        rgb_g = cache[lid][0]
        segs.sort(key=lambda s: s[0])
        num = np.zeros_like(rgb_g)
        den = np.zeros(rgb_g.shape[:2], np.float32)
        # Each overlap is cut at the column where the two paintings agree best (inside the
        # middle 60% of the overlap), feathered over FEATHER px. Averaging a whole overlap
        # would ghost two different paintings of the same trees.
        feather = 300 if lid == "L1-far" else 100
        cuts = []
        for (a0, a1, A), (b0, b1, B) in zip(segs, segs[1:]):
            o0, o1 = b0, a1
            da = A[:, o0 - a0:o1 - a0]
            db = B[:, :o1 - o0]
            diff = np.abs(da - db).mean((0, 2))
            diff = np.convolve(diff, np.ones(31) / 31, mode="same")
            lo, hi = int((o1 - o0) * 0.2), int((o1 - o0) * 0.8)
            cuts.append(o0 + lo + int(np.argmin(diff[lo:hi])))
        for i, (x0, x1, out) in enumerate(segs):
            xs = np.arange(x0, x1)
            wgt = np.ones(len(xs), np.float32)
            if i > 0:
                wgt = np.minimum(wgt, np.clip((xs - cuts[i - 1]) / feather + 0.5, 0, 1))
            if i < len(segs) - 1:
                wgt = np.minimum(wgt, np.clip((cuts[i] - xs) / feather + 0.5, 0, 1))
            num[:, x0:x1] += out * wgt[None, :, None]
            den[:, x0:x1] += wgt[None, :]
        stitched[lid] = (num, den)
        report["cuts " + lid] = cuts
    for lid, (num, den) in stitched.items():
        rgb_g, alpha, y0 = cache[lid]
        covered = den > 1e-3
        col = np.where(covered[..., None], num / np.maximum(den, 1e-3)[..., None], rgb_g)
        if lid == "L1-far":
            a = np.ones_like(alpha)
        else:
            # Matte: the guide's silhouette, grown, decides where paint may be; distance from the
            # flat key sky decides how opaque it is. Inside the clay a pixel is cut only when it is
            # plainly key (a crown Codex drew smaller than the clay); outside, the key ramps in.
            # Then the key is taken back out of every partial pixel so no pale halo is left.
            grown = np.asarray(Image.fromarray((alpha * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(5)), np.float32) / 255
            key = np.array(KEY, np.float32) / 255
            dist = np.abs(col - key).sum(-1)
            inside = np.clip((dist - 0.07) / 0.08, 0, 1)
            outside = grown * np.clip((dist - 0.10) / 0.12, 0, 1)
            a = np.where(alpha > 0.98, inside, outside)
            a = np.asarray(Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(3)), np.float32) / 255
            a = np.asarray(Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.5)), np.float32) / 255
            soft = (a > 0.01) & (a < 0.99)
            dec = (col - (1 - a[..., None]) * key) / np.maximum(a[..., None], 0.25)
            col = np.where(soft[..., None], np.clip(dec, 0, 1), col)
        full_h = cam.OUT_H
        plate = np.zeros((full_h, col.shape[1], 4), np.float32)
        plate[y0:y0 + col.shape[0], :, :3] = col
        plate[y0:y0 + col.shape[0], :, 3] = a
        if lid == "L1-far":   # sky: carry the top row up, ground: carry the bottom row down
            plate[:y0, :, :3] = col[:1]
            plate[:y0, :, 3] = 1
        Image.fromarray((np.clip(plate, 0, 1) * 255).astype(np.uint8), "RGBA").save(PLATES / ("c4-%s-plate.png" % lid))
        print("plate", lid, plate.shape[1], "x", full_h, "coverage %.3f" % covered.mean())
    rp = PLATES / "registration.json"
    old = json.loads(rp.read_text(encoding="utf-8")) if rp.exists() else {}
    old.update(report)
    rp.write_text(json.dumps(old, indent=1) + "\n", encoding="utf-8")
    for k, v in report.items():
        print(k, v)


if __name__ == "__main__":
    if sys.argv[1:2] == ["build"]:
        build()
    elif sys.argv[1:2] == ["register"]:
        register()
    else:
        print(__doc__)
