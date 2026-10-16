"""Mechanical exporter: raw/*.raw.png -> mod/graphics/warship-damage/ (warship-hull-damage.md).

The two interior textures are downsampled to 512 and made exactly seamless by cross-fading each
with its own half-offset copy (ImageGen's tiling is close, not exact). The torn-edge sheet is
downsampled to 512 and its magenta key turned into alpha, with the key's tint pulled out of the
anti-aliased rim. No hand edits.
"""
from pathlib import Path
import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = ROOT / "mod" / "graphics" / "warship-damage"
SIZE = 512


def seamless(img):
    a = np.asarray(img.convert("RGB").resize((SIZE, SIZE), Image.LANCZOS), dtype=np.float32)
    rolled = np.roll(np.roll(a, SIZE // 2, axis=0), SIZE // 2, axis=1)
    t = np.linspace(0.0, 1.0, SIZE)
    edge = np.minimum(t, 1.0 - t) * 2.0          # 0 at the borders, 1 in the middle
    w1 = np.clip(edge / 0.35, 0.0, 1.0)
    w = np.outer(w1, w1)[..., None]
    w = w * w * (3 - 2 * w)                       # smoothstep
    out = a * w + rolled * (1.0 - w)
    rgba = np.dstack([out, np.full((SIZE, SIZE), 255.0)])
    return Image.fromarray(rgba.clip(0, 255).astype(np.uint8), "RGBA")


def keyed(img):
    a = np.asarray(img.convert("RGB").resize((SIZE, SIZE), Image.LANCZOS), dtype=np.float32)
    key = np.array([255.0, 0.0, 255.0])
    dist = np.sqrt(((a - key) ** 2).sum(axis=2))
    alpha = np.clip((dist - 90.0) / 110.0, 0.0, 1.0)
    # Despill: the green channel bounds how much of the key bled into red and blue.
    g = a[..., 1]
    a[..., 0] = np.minimum(a[..., 0], g + 60.0)
    a[..., 2] = np.minimum(a[..., 2], g + 60.0)
    rgba = np.dstack([a, alpha * 255.0])
    return Image.fromarray(rgba.clip(0, 255).astype(np.uint8), "RGBA")


OUT.mkdir(parents=True, exist_ok=True)
seamless(Image.open(HERE / "raw" / "interior-frames.raw.png")).save(OUT / "hull-interior-frames.png")
seamless(Image.open(HERE / "raw" / "interior-core.raw.png")).save(OUT / "hull-interior-core.png")
keyed(Image.open(HERE / "raw" / "torn-edges.raw.png")).save(OUT / "hull-torn-edges.png")
print("exported to", OUT)
