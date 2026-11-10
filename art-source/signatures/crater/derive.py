"""Derive the shipped impact crater decal from its ImageGen master.

Usage: python art-source/signatures/crater/derive.py
Writes mod/graphics/battle/signatures/impact-crater.png (256x256 RGBA). The master's own matte is
fringed (white halo, red edge pixels), so its alpha is replaced by a radial falloff well inside it:
opaque over the bowl and lip, thinning across the ejecta to nothing at INNER_EDGE of the radius.
"""
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
MASTER = HERE / "impact-crater-master.png"
OUT = ROOT / "mod" / "graphics" / "battle" / "signatures" / "impact-crater.png"
SIZE = 256
FADE_FROM, INNER_EDGE = 0.5, 0.78


def main() -> None:
    im = Image.open(MASTER).convert("RGBA").resize((SIZE, SIZE), Image.LANCZOS)
    a = np.asarray(im).astype(np.float32)
    yy, xx = np.mgrid[0:SIZE, 0:SIZE]
    c = (SIZE - 1) / 2
    r = np.hypot(xx - c, yy - c) / c
    fall = np.clip((INNER_EDGE - r) / (INNER_EDGE - FADE_FROM), 0, 1)
    a[..., 3] = 255 * fall ** 1.5
    OUT.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(a.astype(np.uint8), "RGBA").save(OUT)
    print(f"wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
