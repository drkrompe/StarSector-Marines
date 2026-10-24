"""Cut a Skirr concept sheet (three poses in a row on flat grey) into glide / flap / dive sprites.

python art-source/rav-redesign/exploration/flier/cut_flier.py SHEET.png OUT_DIR PREFIX

The sheet is a round-1 ImageGen master: the same creature three times, left to right glide, flap, dive, on a flat
grey ground. The grey is keyed out by its distance from the sampled corner colour (soft over a few levels so the
edge keeps its antialiasing), the poses are split at the widest empty column runs, and each pose is cropped to its
own alpha box with a small margin. Each sprite is written facing up, exactly as generated.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

POSES = ("glide", "flap", "dive")
HARD, SOFT = 10.0, 26.0   # colour distance from the ground: below HARD is ground, above SOFT is body
MARGIN = 6


def key(img):
    a = np.asarray(img.convert("RGB")).astype(float)
    corners = np.concatenate([a[:8, :8].reshape(-1, 3), a[:8, -8:].reshape(-1, 3),
                              a[-8:, :8].reshape(-1, 3), a[-8:, -8:].reshape(-1, 3)])
    ground = np.median(corners, axis=0)
    d = np.sqrt(((a - ground) ** 2).sum(axis=2))
    alpha = np.clip((d - HARD) / (SOFT - HARD), 0, 1)
    # Unmix the grey out of the soft edge, so an edge pixel does not carry a grey halo onto other ground.
    al = np.maximum(alpha, 1e-3)[..., None]
    rgb = np.clip((a - ground * (1 - al)) / al, 0, 255)
    out = np.dstack([rgb, alpha * 255]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def split(alpha, parts=3):
    """Column ranges of the `parts` figures: cut at the widest empty column runs."""
    cols = (alpha > 40).sum(axis=0) > 0
    runs, start = [], None
    for x, filled in enumerate(cols):
        if not filled and start is None:
            start = x
        elif filled and start is not None:
            runs.append((start, x)); start = None
    inner = [r for r in runs if r[0] > 0 and r[1] < len(cols)]
    inner.sort(key=lambda r: r[1] - r[0], reverse=True)
    cuts = sorted((s + e) // 2 for s, e in inner[:parts - 1])
    edges = [0] + cuts + [len(cols)]
    return list(zip(edges[:-1], edges[1:]))


def main(sheet, out_dir, prefix):
    img = key(Image.open(sheet))
    alpha = np.asarray(img.getchannel("A"))
    out = Path(out_dir); out.mkdir(parents=True, exist_ok=True)
    for pose, (x0, x1) in zip(POSES, split(alpha)):
        sub = img.crop((x0, 0, x1, img.height))
        box = sub.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox()
        l, t, r, b = box
        sub = sub.crop((max(0, l - MARGIN), max(0, t - MARGIN), min(sub.width, r + MARGIN), min(sub.height, b + MARGIN)))
        sub.save(out / f"{prefix}-{pose}.png")
        print(f"{prefix}-{pose}: {sub.size}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
