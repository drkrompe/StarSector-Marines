"""Make a void floor plate tile seamlessly, for the city drop's parallax background.

The plate is blended with a copy of itself offset by half its size. The offset copy
wraps cleanly at the image's edges but has seams through the middle; the original is
clean in the middle. A weight that is one at the centre lines and falls to zero at the
edges takes the original there and the offset copy elsewhere, so the result wraps with
no visible seam. A hazy, busy plate hides the ghosting in the blend band.

    python art-source/voids/sky-port/make_seamless.py <master.png> <out.png> [--size N]
"""
import argparse

import numpy as np
from PIL import Image


def seamless(rgb: np.ndarray) -> np.ndarray:
    h, w = rgb.shape[:2]
    shifted = np.roll(np.roll(rgb, h // 2, axis=0), w // 2, axis=1)
    # Distance from the nearest edge, 0 at the edge and 1 in the middle, per axis.
    wy = 1.0 - np.abs(np.linspace(-1.0, 1.0, h))
    wx = 1.0 - np.abs(np.linspace(-1.0, 1.0, w))
    weight = np.minimum.outer(wy, wx)
    weight = np.clip(weight * 2.5, 0.0, 1.0)  # a fifth of the plate each side blends
    weight = weight * weight * (3.0 - 2.0 * weight)  # smoothstep
    return rgb * weight[..., None] + shifted * (1.0 - weight[..., None])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("master")
    parser.add_argument("out")
    parser.add_argument("--size", type=int, default=1024, help="square output size")
    args = parser.parse_args()
    img = Image.open(args.master).convert("RGB")
    rgb = np.asarray(img, dtype=np.float32)
    out = seamless(rgb)
    tile = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))
    tile = tile.resize((args.size, args.size), Image.LANCZOS)
    tile.save(args.out)


if __name__ == "__main__":
    main()
