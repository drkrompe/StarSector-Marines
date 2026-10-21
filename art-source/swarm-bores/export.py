"""Mechanical export of the swarm bore and spore-colony sprites from their retained raws.

Each raw is the ImageGen reskin of a guide written by ``make_guide.py``. The raw
is scaled to the guide canvas and its opaque extent fitted back onto the
guide's (ImageGen holds shape better than absolute scale), then the whole
square canvas is resampled to the runtime size: the canvas centre is the
structure's pivot, so nothing is trimmed or recentred. Faint alpha below
``ALPHA_FLOOR`` is cleared.

Each part also gets a ``-glow`` layer: the reskin's own warm amber pixels
lifted out by colour, drawn over the body with a pulse, so the nodes and the
heat in a pit breathe while the chitin stays still.

Prints the silhouette IoU of each raw against its guide (the registration
check) and the glow layer's coverage.

Run from the repository root: ``python art-source/swarm-bores/export.py``.
"""

import colorsys
from pathlib import Path

from PIL import Image, ImageFilter

ALPHA_FLOOR = 16
CANVAS = 1024

# part -> (shipped name under mod/graphics/structures, runtime edge in pixels)
TARGETS = {
    "bore": ("swarm-bore", 384),
    "mother-bore": ("swarm-mother-bore", 512),
    "spore-colony-fissure": ("spore-colony-fissure", 192),
    "spore-colony-bore": ("spore-colony-bore", 256),
    "spore-colony-maw": ("spore-colony-maw", 320),
}


def mask(image: Image.Image) -> list[bool]:
    return [a >= 128 for a in image.getchannel("A").get_flattened_data()]


def iou(a: list[bool], b: list[bool]) -> float:
    inter = sum(1 for x, y in zip(a, b) if x and y)
    union = sum(1 for x, y in zip(a, b) if x or y)
    return inter / union if union else 0.0


def fitted(raw: Image.Image, guide: Image.Image) -> Image.Image:
    """Scales and moves the raw so its opaque extent lands on the guide's (one uniform scale about the centres)."""
    floor = lambda value: 255 if value >= 128 else 0
    rb = raw.getchannel("A").point(floor).getbbox()
    gb = guide.getchannel("A").point(floor).getbbox()
    if rb is None or gb is None:
        return raw
    scale = ((gb[2] - gb[0]) / (rb[2] - rb[0]) + (gb[3] - gb[1]) / (rb[3] - rb[1])) / 2
    scaled = raw.resize((round(raw.width * scale), round(raw.height * scale)), Image.Resampling.LANCZOS)
    rcx, rcy = (rb[0] + rb[2]) / 2 * scale, (rb[1] + rb[3]) / 2 * scale
    gcx, gcy = (gb[0] + gb[2]) / 2, (gb[1] + gb[3]) / 2
    out = Image.new("RGBA", raw.size, (0, 0, 0, 0))
    out.paste(scaled, (round(gcx - rcx), round(gcy - rcy)), scaled)
    return out


def glow(layer: Image.Image) -> Image.Image:
    """The layer's warm amber pixels only, alpha graded by how saturated and bright the amber is."""
    weights = []
    for r, g, b, a in layer.get_flattened_data():
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        # Amber through orange-gold: hue about 20-50 degrees, saturated and lit.
        warm = 0.04 <= h <= 0.15
        w = 0.0
        if warm and a > 0:
            w = min(1.0, max(0.0, (s - 0.45) / 0.3)) * min(1.0, max(0.0, (v - 0.45) / 0.3))
        weights.append(round(255 * w * a / 255))
    alpha = Image.new("L", layer.size)
    alpha.putdata(weights)
    alpha = alpha.filter(ImageFilter.GaussianBlur(0.6))
    out = layer.copy()
    out.putalpha(alpha)
    return out


def main() -> None:
    here = Path(__file__).resolve().parent
    repository = here.parent.parent
    graphics = repository / "mod" / "graphics" / "structures"
    for part, (name, edge) in TARGETS.items():
        raw_path = here / "raw" / f"{part}.raw.png"
        if not raw_path.exists():
            print(f"{part}: no raw yet, skipped")
            continue
        raw = Image.open(raw_path).convert("RGBA").resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)
        guide = Image.open(repository / "art-source" / "templates" / f"swarm-{part}-guide.png").convert("RGBA")
        small = (256, 256)
        before = iou(mask(raw.resize(small)), mask(guide.resize(small)))
        raw = fitted(raw, guide)
        score = iou(mask(raw.resize(small)), mask(guide.resize(small)))
        layer = raw.resize((edge, edge), Image.Resampling.LANCZOS)
        layer.putalpha(layer.getchannel("A").point(lambda value: 0 if value < ALPHA_FLOOR else value))
        body = graphics / f"{name}.png"
        layer.save(body, optimize=True)
        lit = glow(layer)
        lit.save(graphics / f"{name}-glow.png", optimize=True)
        coverage = sum(1 for a in lit.getchannel("A").get_flattened_data() if a >= 128) / max(
            1, sum(1 for a in layer.getchannel("A").get_flattened_data() if a >= 128))
        print(f"{part}: IoU {before:.3f} raw, {score:.3f} fitted -> {body.relative_to(repository)} "
              f"{layer.size}, glow {coverage:.1%} of body")


if __name__ == "__main__":
    main()
