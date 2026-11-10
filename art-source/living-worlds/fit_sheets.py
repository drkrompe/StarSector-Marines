"""Cuts the accepted living-world raws to their guides and writes the sheets the jungle-* and taiga-* tilesets export.

As art-source/dry-woodland/fit_sheets.py does for the dry scrub, with one step before the fit: the guide owns the
silhouette, so each raw's alpha is clipped to its guide's.

- Two generations came back on an opaque grey backdrop instead of transparency (jungle canopies and jungle
  undergrowth). Their pieces fill their guide silhouettes, so the guide's own alpha, eroded by ERODE px and
  feathered, is their alpha (MASK_FROM_GUIDE).
- Every other raw keeps its own alpha inside its guide grown by GROW px, which clears the faint checkerboard halo the
  taiga destroyed sheet came back with; alpha under FLOOR is dropped.

Then each 512 px panel's opaque box is fitted onto its guide panel's box (dry-woodland's fit), and the destroyed sheet
is flattened (owner, 2026-10-25: felled trees ship flattened). Raws are never altered; the script can be re-run.
Writes fitted/<direction>-<state>.png. Export with `tools/authoring.cmd tileset_export -` and
`{"name":"<direction>-<state>"}`, then `gradlew.bat bakeCreepBlight`.
usage: python art-source/living-worlds/fit_sheets.py
"""
import importlib.util
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
ART = HERE.parent
T = ART / "templates"
RAW = HERE / "raw"
OUT = HERE / "fitted"
DIRECTIONS = ("jungle", "taiga")
# Shipped state -> (raw state name, guide).
STATES = {
    "canopies": ("canopies", T / "forest-canopies.png"),
    "damaged": ("damaged", T / "tree-damaged.png"),
    "destroyed": ("destroyed", T / "tree-destroyed.png"),
    "undergrowth": ("undergrowth", T / "undergrowth-standing.png"),
    "undergrowth-damaged": ("undergrowth-damaged", T / "undergrowth-damaged.png"),
    "undergrowth-destroyed": ("undergrowth-trampled", T / "undergrowth-trampled.png"),
}
MASK_FROM_GUIDE = {("jungle", "canopies"), ("jungle", "undergrowth")}
ERODE, GROW, FLOOR = 3, 10, 24

_spec = importlib.util.spec_from_file_location("dry_fit", ART / "dry-woodland" / "fit_sheets.py")
dry = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(dry)


def clip(raw, guide, from_guide):
    g = guide.getchannel("A").point(lambda v: 255 if v > dry.ALPHA_MIN else 0)
    if from_guide:
        mask = g.filter(ImageFilter.MinFilter(2 * ERODE + 1)).filter(ImageFilter.GaussianBlur(1.5))
        alpha = np.asarray(mask, dtype=np.float32)
    else:
        mask = g.filter(ImageFilter.MaxFilter(2 * GROW + 1)).filter(ImageFilter.GaussianBlur(3))
        alpha = np.asarray(raw.getchannel("A"), dtype=np.float32) * np.asarray(mask, dtype=np.float32) / 255.0
    alpha[alpha < FLOOR] = 0
    out = np.asarray(raw, dtype=np.uint8).copy()
    out[..., 3] = np.clip(alpha, 0, 255).astype(np.uint8)
    return Image.fromarray(out, mode="RGBA")


def flatten(im, direction):
    """dry-woodland's flatten, over this direction's destroyed document's rects."""
    import json
    doc = json.loads((ART / "tilesets" / f"{direction}-destroyed.tileset-authoring.json").read_text(encoding="utf-8"))
    raw = np.asarray(im, dtype=np.float32)
    out = np.zeros_like(raw)
    for k, entry in enumerate(doc["entries"]):
        x, y, w, h = entry["rect"]
        cut = raw[y:y + h, x:x + w]
        h, w = cut.shape[:2]
        half = min(w, h) / 2.0
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        rr = np.hypot(xx + 0.5 - w / 2.0, yy + 0.5 - h / 2.0) / half
        reach = dry.FLAT_REACH + dry.RAGGED * dry.ragged(w, h, 0xF1A7 + k)
        keep = np.clip((reach - rr) / dry.FADE + 0.5, 0.0, 1.0)
        flat = cut.copy()
        flat[..., 3] *= keep
        target = out[y:y + h, x:x + w]
        take = flat[..., 3] > target[..., 3]
        target[take] = flat[take]
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), mode="RGBA")


def main():
    OUT.mkdir(exist_ok=True)
    for d in DIRECTIONS:
        for state, (raw_state, guide_path) in STATES.items():
            raw = Image.open(RAW / f"{d}-{raw_state}.raw.png").convert("RGBA")
            guide = Image.open(guide_path).convert("RGBA")
            assert raw.size == guide.size == (1536, 1024), (d, raw_state, raw.size)
            sheet = dry.fit(clip(raw, guide, (d, state) in MASK_FROM_GUIDE), guide, f"{d}-{state}")
            if state == "destroyed":
                sheet = flatten(sheet, d)
            path = OUT / f"{d}-{state}.png"
            sheet.save(path)
            print("wrote", path.relative_to(ART.parent))


if __name__ == "__main__":
    main()
