"""Derive the Runner's and the Brute's growth-stage sprite sets from their adult exports (`growth-stages.md`, `colony-breach-nouns.md` Growth stage).

  python stage_sprites.py              # both creatures
  python stage_sprites.py runner|brute

Reads mod/graphics/battle/rav-<creature>/ and writes rav-<creature>-whelp/ and rav-<creature>-elder/ beside it,
the same file names, the same sizes and the same bone layout, so `RavRunnerAssets.forStage` /
`RavBruteAssets.forStage` pose them on the adult's skeleton unchanged. The game draws each stage at its own size
(`SwarmStage.scale`) and a Runner's spikes at their stage length (`RavRunnerComposer.spikeScale`); this pass only
recolours, as `exploration/growth_stages_sheet.py` settled it:

  whelp  plates lifted toward a pale lilac and a little desaturated (bone less so);
  elder  plates darkened toward plum with the spine ridge kept pale, healed scars across the carapace, the bone
         a touch yellowed.

`export_runner_rig.py` and `export_brute_rig.py` call this after they write the adult set, so the stages never
drift from it. Ichor is the adult's: it is not re-exported per stage.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).parent
GRAPHICS = HERE.parent.parent / "mod" / "graphics" / "battle"

# Runner sprite px per cell of spine: the body layer's 834 layer px per cell times the export's 0.2
RUNNER_PX_PER_CELL = (958 - 541) / 0.5 * 0.2
RIDGE_CELLS = 0.07


def recolour(img, stage, part, ridge=None):
    a = np.asarray(img.convert("RGBA")).astype(float)
    rgb, al = a[..., :3], a[..., 3:]
    orig = rgb.copy()
    if stage == "whelp":
        rgb = rgb + (255 - rgb) * (0.34 if part == "body" else 0.22)
        grey = rgb.mean(2, keepdims=True)
        rgb = grey + (rgb - grey) * 0.78
    elif stage == "elder":
        if part == "body":
            rgb = rgb * np.array([0.50, 0.40, 0.54])
            if ridge is not None:  # the spine ridge keeps its pale plates so an elder stands off the creep
                bright = (orig.mean(2) > 150)[..., None]
                rgb = np.where(ridge[..., None] & bright, orig * 0.92, rgb)
        else:  # old bone: a touch yellowed, not darkened
            rgb = rgb * np.array([1.0, 0.96, 0.84])
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), al]).astype(np.uint8), "RGBA")


def scars(img, seed):
    """Healed scars: pale raised seams across the carapace, kept inside the body."""
    rnd = np.random.default_rng(seed)
    a = np.asarray(img.getchannel("A")) > 200
    if not a.any():
        return img
    ys, xs = np.nonzero(a)
    cx, cy = xs.mean(), ys.mean()
    w = xs.max() - xs.min()
    h = ys.max() - ys.min()
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(3):
        x0 = cx + rnd.uniform(-0.30, 0.30) * w
        y0 = cy + rnd.uniform(-0.30, 0.30) * h
        ang = rnd.uniform(-0.9, 0.9)
        ln = rnd.uniform(0.18, 0.32) * w
        pts = [(x0 + math.cos(ang) * t * ln + rnd.uniform(-1, 1), y0 + math.sin(ang) * t * ln)
               for t in np.linspace(-0.5, 0.5, 6)]
        d.line(pts, fill=(196, 150, 176, 210), width=max(1, round(w * 0.022)))
    la = np.asarray(layer.getchannel("A")).astype(float) * a
    layer.putalpha(Image.fromarray(la.astype(np.uint8)))
    out = img.copy()
    out.alpha_composite(layer)
    return out


def ridge_columns(img, half_px):
    w, h = img.size
    cols = np.abs(np.arange(w) - (w - 1) / 2) < half_px
    return np.broadcast_to(cols[None, :], (h, w))


def runner_part(name):
    """(part, ridge half-width in px or None, scarred) for one Runner sprite."""
    if name.startswith("arm-"):
        return "arm", None, False
    if name.startswith("trunk-"):
        return "body", RIDGE_CELLS * RUNNER_PX_PER_CELL, True
    if name == "fore":
        return "body", RIDGE_CELLS * RUNNER_PX_PER_CELL, False
    return "body", None, False


def brute_part(name, width):
    if name in ("body", "body-front", "body-rear"):
        return "body", width * 0.035, True
    if name == "head":
        return "body", width * 0.035, False
    return "body", None, False


def write(creature):
    src = GRAPHICS / f"rav-{creature}"
    for stage in ("whelp", "elder"):
        out = GRAPHICS / f"rav-{creature}-{stage}"
        out.mkdir(parents=True, exist_ok=True)
        for i, path in enumerate(sorted(src.glob("*.png"))):
            name = path.stem
            if name.startswith("ichor"):
                continue
            img = Image.open(path).convert("RGBA")
            base = name[:-2] if name.endswith(("-l", "-r")) else name
            if creature == "runner":
                part, ridge_px, scarred = runner_part(base)
            else:
                part, ridge_px, scarred = brute_part(base, img.width)
            ridge = ridge_columns(img, ridge_px) if (stage == "elder" and ridge_px) else None
            img = recolour(img, stage, part, ridge)
            if stage == "elder" and scarred:
                img = scars(img, seed=sum(map(ord, base)))
            img.save(out / path.name)
        print(f"{out.name}: {len(list(out.glob('*.png')))} sprites")


def main(argv):
    for creature in (argv or ["runner", "brute"]):
        write(creature)


if __name__ == "__main__":
    main(sys.argv[1:])
