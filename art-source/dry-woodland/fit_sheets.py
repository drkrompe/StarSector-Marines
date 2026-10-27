"""Fits the accepted dry-scrub raws onto their guides and writes the sheets the dry-scrub tilesets export.

The generations came back 18-38% larger than their guide outlines, so a raw cut with the green set's rects would
spill past the blocked footprint. Each 512 px panel's opaque box (alpha over ALPHA_MIN, ignoring rows and columns
with fewer than SPECK opaque pixels) is scaled uniformly (or, past MAX_SKEW, per axis) to fit inside the guide panel's opaque box, centred on it,
so every piece keeps its aspect, sits where the green cut expects it, and stays inside the guide envelope.

The destroyed sheet is then flattened as art-source/tilesets/forest-destroyed-flatten.py flattens the green one
(owner, 2026-10-25: felled trees ship flattened): each cut keeps its stump and the butts of its limbs, broken at a
ragged line FLAT_REACH of the way out from the cut's centre.

Raws are never altered; the script reads them back and can be re-run. Writes fitted/dry-scrub-<state>.png and prints
each panel's opaque box size before and after against its guide. Export with `tools/authoring.cmd tileset_export -`
and `{"name":"dry-scrub-<state>"}`.
usage: python art-source/dry-woodland/fit_sheets.py
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ART = HERE.parent
T = ART / "templates"
RAW = HERE / "raw"
OUT = HERE / "fitted"
ALPHA_MIN = 40
SPECK = 3
MAX_SKEW = 1.08
# Shipped state -> (accepted raw, guide whose opaque boxes the pieces fit).
STATES = {
    "canopies": ("scrub-canopies-v2.raw.png", HERE / "guides" / "forest-canopies-sparse.png"),
    "damaged": ("scrub-damaged.raw.png", T / "tree-damaged.png"),
    "destroyed": ("scrub-destroyed.raw.png", T / "tree-destroyed.png"),
    "undergrowth": ("scrub-undergrowth.raw.png", T / "undergrowth-standing.png"),
    "undergrowth-damaged": ("scrub-undergrowth-damaged.raw.png", T / "undergrowth-damaged.png"),
    "undergrowth-destroyed": ("scrub-undergrowth-trampled.raw.png", T / "undergrowth-trampled.png"),
}
FLAT_REACH = 0.40
RAGGED = 0.07
FADE = 0.03


def opaque_box(im):
    a = np.asarray(im.getchannel("A")) > ALPHA_MIN
    rows = np.where(a.sum(axis=1) >= SPECK)[0]
    cols = np.where(a.sum(axis=0) >= SPECK)[0]
    if len(rows) == 0:
        return None
    return int(cols[0]), int(rows[0]), int(cols[-1]) + 1, int(rows[-1]) + 1


def fit(raw, guide, name):
    out = Image.new("RGBA", raw.size, (0, 0, 0, 0))
    for r in range(2):
        for c in range(3):
            cell = (c * 512, r * 512, c * 512 + 512, r * 512 + 512)
            panel = raw.crop(cell)
            rb, gb = opaque_box(panel), opaque_box(guide.crop(cell))
            rw, rh, gw, gh = rb[2] - rb[0], rb[3] - rb[1], gb[2] - gb[0], gb[3] - gb[1]
            # Uniform while the aspects agree; past MAX_SKEW (the mopane came back round on the birch's oval)
            # the piece fills the guide box, so its crown covers the oval's cells rather than a small disc in it.
            sx, sy = gw / rw, gh / rh
            if max(sx, sy) / min(sx, sy) <= MAX_SKEW:
                sx = sy = min(sx, sy)
            w, h = max(1, round(rw * sx)), max(1, round(rh * sy))
            piece = panel.crop(rb).resize((w, h), Image.LANCZOS)
            x = round((gb[0] + gb[2]) / 2 - w / 2)
            y = round((gb[1] + gb[3]) / 2 - h / 2)
            out.alpha_composite(piece, (cell[0] + x, cell[1] + y))
            print(f"  {name} panel {r * 3 + c}: raw {rw}x{rh} guide {gw}x{gh} "
                  f"(raw {rw / gw:.0%} x {rh / gh:.0%} of guide) -> fitted {w}x{h}, scale {sx:.3f} x {sy:.3f}")
    return out


def ragged(w, h, seed):
    rng = np.random.default_rng(seed)
    grid = rng.uniform(-1.0, 1.0, size=(9, 9)).astype(np.float32)
    small = Image.fromarray(((grid + 1.0) * 127.5).astype(np.uint8), mode="L")
    return np.asarray(small.resize((w, h), Image.BILINEAR), dtype=np.float32) / 127.5 - 1.0


def flatten(im):
    """forest-destroyed-flatten.py's cut, over the dry-scrub-destroyed document's rects."""
    doc = json.loads((ART / "tilesets" / "dry-scrub-destroyed.tileset-authoring.json").read_text(encoding="utf-8"))
    raw = np.asarray(im, dtype=np.float32)
    out = np.zeros_like(raw)
    for k, entry in enumerate(doc["entries"]):
        x, y, w, h = entry["rect"]
        cut = raw[y:y + h, x:x + w]
        h, w = cut.shape[:2]
        half = min(w, h) / 2.0
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        rr = np.hypot(xx + 0.5 - w / 2.0, yy + 0.5 - h / 2.0) / half
        reach = FLAT_REACH + RAGGED * ragged(w, h, 0xF1A7 + k)
        keep = np.clip((reach - rr) / FADE + 0.5, 0.0, 1.0)
        flat = cut.copy()
        flat[..., 3] *= keep
        target = out[y:y + h, x:x + w]
        take = flat[..., 3] > target[..., 3]
        target[take] = flat[take]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), mode="RGBA")


def main():
    OUT.mkdir(exist_ok=True)
    for state, (raw_name, guide_path) in STATES.items():
        raw = Image.open(RAW / raw_name).convert("RGBA")
        guide = Image.open(guide_path).convert("RGBA")
        assert raw.size == guide.size == (1536, 1024), (raw_name, raw.size)
        sheet = fit(raw, guide, state)
        if state == "destroyed":
            sheet = flatten(sheet)
        path = OUT / f"dry-scrub-{state}.png"
        sheet.save(path)
        print("wrote", path.relative_to(ART.parent))


if __name__ == "__main__":
    main()
