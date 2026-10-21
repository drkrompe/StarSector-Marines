"""Mechanical export of the swarm-strain layers from their retained raws.

Each raw is the ImageGen reskin of a guide written by ``make_guide.py``. The
raw is scaled to the guide canvas, the part's registration window recorded in
``geometry.json`` is cut out and resampled to its runtime size, and faint alpha
below ``ALPHA_FLOOR`` is cleared. Nothing is trimmed or recentred, so the
window, not the painted extent, decides where a layer sits on the actor.

Also prints the silhouette IoU of each raw against its guide, which is the
registration check: a reskin that drifted shows up here before it shows up in
a frame.

Corpse sheets are exported by ``export_corpses.py``.

Run from the repository root: ``python art-source/swarm-strains/export.py``.
"""

import json
from pathlib import Path

from PIL import Image

ALPHA_FLOOR = 16

TARGETS = {
    "brute-body": "swarm-brute-modular-topdown/body.png",
    "brute-head": "swarm-brute-modular-topdown/head.png",
    "brute-foot": "swarm-brute-modular-topdown/foot.png",
    "brute-fore-claw": "swarm-brute-modular-topdown/fore-claw.png",
    "spitter-body": "swarm-spitter-modular-topdown/body.png",
    "spitter-head": "swarm-spitter-modular-topdown/head.png",
}


def mask(image: Image.Image) -> list[bool]:
    return [a >= 128 for a in image.getchannel("A").get_flattened_data()]


def iou(a: list[bool], b: list[bool]) -> float:
    inter = sum(1 for x, y in zip(a, b) if x and y)
    union = sum(1 for x, y in zip(a, b) if x or y)
    return inter / union if union else 0.0


def fitted(raw: Image.Image, guide: Image.Image) -> Image.Image:
    """Scales and moves the raw so its opaque extent lands on the guide's.

    ImageGen holds a guide's shape far better than its absolute scale, so the
    silhouette's bounding box is mapped back onto the guide's with one uniform
    scale (the larger of the two axis ratios' mean) about the box centres.
    """
    floor = lambda value: 255 if value >= 128 else 0
    rb = raw.getchannel("A").point(floor).getbbox()
    gb = guide.getchannel("A").point(floor).getbbox()
    if rb is None or gb is None:
        return raw
    sx = (gb[2] - gb[0]) / (rb[2] - rb[0])
    sy = (gb[3] - gb[1]) / (rb[3] - rb[1])
    scale = (sx + sy) / 2
    scaled = raw.resize((round(raw.width * scale), round(raw.height * scale)),
                        Image.Resampling.LANCZOS)
    rcx, rcy = (rb[0] + rb[2]) / 2 * scale, (rb[1] + rb[3]) / 2 * scale
    gcx, gcy = (gb[0] + gb[2]) / 2, (gb[1] + gb[3]) / 2
    out = Image.new("RGBA", raw.size, (0, 0, 0, 0))
    # paste, not alpha_composite: the offset may be negative when the raw came back larger.
    out.paste(scaled, (round(gcx - rcx), round(gcy - rcy)), scaled)
    return out


def main() -> None:
    here = Path(__file__).resolve().parent
    repository = here.parent.parent
    geometry = json.loads((here / "geometry.json").read_text())
    canvas = geometry["canvas"]
    graphics = repository / "mod" / "graphics" / "battle"
    for part, target in TARGETS.items():
        raw_path = here / "raw" / f"{part}.raw.png"
        if not raw_path.exists():
            print(f"{part}: no raw yet, skipped")
            continue
        raw = Image.open(raw_path).convert("RGBA")
        if raw.width != raw.height:
            print(f"{part}: raw is {raw.size}, not square; scaled anyway")
        raw = raw.resize((canvas, canvas), Image.Resampling.LANCZOS)
        guide = Image.open(repository / "art-source" / "templates"
                           / f"swarm-{part}-guide.png").convert("RGBA")
        small = (256, 256)
        before = iou(mask(raw.resize(small)), mask(guide.resize(small)))
        raw = fitted(raw, guide)
        score = iou(mask(raw.resize(small)), mask(guide.resize(small)))
        window = geometry["windows"][part]
        x0, y0, x1, y1 = (round(v) for v in window["guide"])
        layer = raw.crop((x0, y0, x1, y1)).resize(
            tuple(window["runtime"]), Image.Resampling.LANCZOS)
        alpha = layer.getchannel("A").point(
            lambda value: 0 if value < ALPHA_FLOOR else value)
        layer.putalpha(alpha)
        out = graphics / target
        out.parent.mkdir(parents=True, exist_ok=True)
        layer.save(out, optimize=True)
        print(f"{part}: IoU {before:.3f} raw, {score:.3f} fitted -> {out.relative_to(repository)} {layer.size}")


if __name__ == "__main__":
    main()
