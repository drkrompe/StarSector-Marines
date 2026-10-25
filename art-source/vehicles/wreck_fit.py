"""Lay an ImageGen damage edit back over the live sprite it was made from.

ImageGen rescales and moves an edit, and torn plate sticks out past the live outline, so neither the canvas size nor
the bounding box registers a wreck. `fit_to` searches the scale and offset at which the wreck's solid (alpha > 128)
shape overlaps the live one's most, and returns the wreck on the live sprite's canvas.
"""
import numpy as np
from PIL import Image


def solid(img, step):
    a = np.asarray(img.getchannel("A").resize((img.width // step, img.height // step), Image.BILINEAR))
    return a > 128


def fit_to(wreck, live, step=4):
    """The wreck edit on the live raw's canvas, scaled and moved to cover the live turret's solid outline best."""
    target = solid(live, step)
    best = (-1.0, 1.0, 0, 0)
    for k in np.arange(0.40, 1.42, 0.02):
        small = wreck.resize((max(1, round(wreck.width * k / step)), max(1, round(wreck.height * k / step))),
                             Image.BILINEAR)
        mask = np.asarray(small.getchannel("A")) > 128
        ys, xs = np.nonzero(mask)
        ty, tx = np.nonzero(target)
        # Start from the centroids and search a few cells round them.
        dx0 = round(tx.mean() - xs.mean())
        dy0 = round(ty.mean() - ys.mean())
        for dy in range(dy0 - 6, dy0 + 7):
            for dx in range(dx0 - 6, dx0 + 7):
                py, px = ys + dy, xs + dx
                ok = (py >= 0) & (py < target.shape[0]) & (px >= 0) & (px < target.shape[1])
                inter = target[py[ok], px[ok]].sum()
                score = inter / (target.sum() + len(ys) - inter)
                if score > best[0]:
                    best = (score, k, dx * step, dy * step)
    _, k, dx, dy = best
    scaled = wreck.convert("RGBa").resize((round(wreck.width * k), round(wreck.height * k)),
                                          Image.LANCZOS).convert("RGBA")
    out = Image.new("RGBA", live.size, (0, 0, 0, 0))
    out.alpha_composite(scaled, (dx, dy)) if dx >= 0 and dy >= 0 else out.paste(scaled, (dx, dy), scaled)
    print(f"  wreck fit: scale {k:.2f}, offset ({dx}, {dy}), overlap {best[0]:.2f}")
    return out
