"""Flattens the felled trees of forest-destroyed.raw.png into forest-destroyed-flat/forest-destroyed.flat.png, the sheet
the tileset exports.

A felled tree is ground a vehicle drives over (a heavy hull crushes young and creep-killed trees), and the accepted
generation's long fallen limbs read as a tree still lying in the way (owner, 2026-10-25: "crushed trees maybe we just
remove the limbs or make them smaller"). Each cut keeps its stump and the butts of its limbs, broken off at a ragged
line FLAT_REACH of the way out from the cut's centre (its pivot: cuts are full-frame and centred), and drops the rest.
The limbs of the destroyed guide lie apart from the stump, so the butts are short stubs.

forest-destroyed.raw.png is the accepted generation and is never altered; the script reads it back, so it can be
re-run. The authoring document's sheet points at the flattened sheet; export with
`tools/authoring.cmd tileset_export -` and `{"name":"forest-destroyed"}`, then re-bake the blight's felled wood
(`gradlew.bat bakeCreepBlight`), which is derived from the exported sheet.
usage: python art-source/tilesets/forest-destroyed-flatten.py
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
RAW = HERE / "forest-destroyed.raw.png"
# A folder of its own: every PNG directly under tilesets/ is listed as raw art awaiting a document.
FLAT = HERE / "forest-destroyed-flat" / "forest-destroyed.flat.png"
DOC = HERE / "forest-destroyed.tileset-authoring.json"
# How far out the limbs break, as a share of the cut's half-size, and how ragged the break runs either way.
FLAT_REACH = 0.40
RAGGED = 0.07
# The break fades over this share, so a butt's end is not a hard arc.
FADE = 0.03


def ragged(w, h, seed):
    """Smooth noise in -1..1 over a w x h cut: a coarse random grid, bilinearly upsampled."""
    rng = np.random.default_rng(seed)
    grid = rng.uniform(-1.0, 1.0, size=(9, 9)).astype(np.float32)
    small = Image.fromarray(((grid + 1.0) * 127.5).astype(np.uint8), mode="L")
    return np.asarray(small.resize((w, h), Image.BILINEAR), dtype=np.float32) / 127.5 - 1.0


def main():
    doc = json.loads(DOC.read_text(encoding="utf-8"))
    raw = np.asarray(Image.open(RAW).convert("RGBA"), dtype=np.float32)
    out = np.zeros_like(raw)
    for k, entry in enumerate(doc["entries"]):
        x, y, w, h = entry["rect"]
        cut = raw[y:y + h, x:x + w]
        half = min(w, h) / 2.0
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        rr = np.hypot(xx + 0.5 - w / 2.0, yy + 0.5 - h / 2.0) / half
        reach = FLAT_REACH + RAGGED * ragged(w, h, 0xF1A7 + k)
        keep = np.clip((reach - rr) / FADE + 0.5, 0.0, 1.0)
        flat = cut.copy()
        flat[..., 3] *= keep
        # Cuts may overlap (wider full frames than the panel pitch): keep the more opaque pixel.
        target = out[y:y + h, x:x + w]
        take = flat[..., 3] > target[..., 3]
        target[take] = flat[take]
    Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), mode="RGBA").save(FLAT)
    doc["sheet"] = "art-source/tilesets/" + FLAT.parent.name + "/" + FLAT.name
    DOC.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {FLAT} and pointed {DOC.name} at it")


if __name__ == "__main__":
    main()
