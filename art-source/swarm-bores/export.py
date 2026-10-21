"""Mechanical export of the swarm bore and spore-colony sprites from their retained raws.

Each raw is the ImageGen reskin of one variant's guide, written by ``make_guide.py``. The raw is scaled to the
guide canvas and its opaque extent fitted back onto the guide's (ImageGen holds shape better than absolute
scale), then the whole square canvas is resampled to the runtime size: the canvas centre is the structure's
pivot, so nothing is trimmed or recentred. Faint alpha below ``ALPHA_FLOOR`` is cleared.

The guide's **ground layer** (the body's cast shadow, a contact shade and the cracks it heaved into the ground,
all drawn from the same heightfield and sun as the guide's lighting) is laid under the reskin, so the shipped
sprite carries its own shadow and the reskin never has to paint one.

Each variant also gets a ``-glow`` layer: the reskin's own warm amber pixels lifted out by colour, drawn over
the body with a pulse, so the nodes and the heat in a pit breathe while the chitin stays still.

``--palette chitin`` recolours the creature materials (chitin, sinew, bone, flesh, sac; read from the guide's
material map, so thrown earth and paving keep their colours) to the Rav Bloom chitin proposal by a luminance
gradient map, and writes under ``build/swarm-bores/chitin/`` instead of the mod: a preview of the recolour,
not a shipped palette, until the creature redesign lands.

Prints the silhouette IoU of each raw against its guide (the registration check) and the glow coverage.

Run from the repository root: ``python art-source/swarm-bores/export.py [--palette runner|chitin]``.
"""

import argparse
import colorsys
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import make_guide  # noqa: E402

ALPHA_FLOOR = 16
CANVAS = make_guide.N
PX_PER_CELL = 128

# part -> shipped name under mod/graphics/structures (a variant adds "-<n>")
NAMES = {
    "bore": "swarm-bore",
    "mother-bore": "swarm-mother-bore",
    "spore-colony-fissure": "spore-colony-fissure",
    "spore-colony-bore": "spore-colony-bore",
    "spore-colony-maw": "spore-colony-maw",
}

# Rav Bloom CHITIN proposal (rav-palettes): luminance in -> colour out, for creature materials.
CHITIN_RAMP = [
    (0, (6, 4, 3)),
    (40, (0x1a, 0x13, 0x0e)),
    (75, (0x2c, 0x20, 0x17)),
    (110, (0x3e, 0x2d, 0x1f)),
    (145, (0x5e, 0x46, 0x30)),
    (180, (0xa0, 0x86, 0x60)),
    (210, (0xd6, 0xc2, 0x96)),
    (240, (0xea, 0xda, 0xb6)),
    (255, (0xf4, 0xe8, 0xcc)),
]
# A shaft's lining keeps its own value, so its lit far wall still reads as depth; only its hue goes to umber.
FLESH_TINT = (1.0, 0.80, 0.62)
CHITIN_AMBER = (0xff, 0xa8, 0x36)


def edge_px(part: str) -> int:
    return 2 * round(make_guide.CANVAS_CELLS[part] * PX_PER_CELL / 2)


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


def amber_weight(rgba: np.ndarray) -> np.ndarray:
    """0..1 per pixel: how much of a lit warm amber node it is."""
    rgb = rgba[..., :3].astype(np.float32) / 255
    mx, mn = rgb.max(-1), rgb.min(-1)
    v = mx
    s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    hue = np.zeros_like(v)
    d = np.maximum(mx - mn, 1e-6)
    is_r = mx == r
    hue = np.where(is_r, ((g - b) / d) % 6, hue)
    hue = np.where(~is_r & (mx == g), (b - r) / d + 2, hue)
    hue = np.where(~is_r & (mx != g), (r - g) / d + 4, hue) / 6
    warm = (hue >= 0.04) & (hue <= 0.15)
    w = np.clip((s - 0.45) / 0.3, 0, 1) * np.clip((v - 0.45) / 0.3, 0, 1)
    return np.where(warm, w, 0) * (rgba[..., 3] / 255)


def glow(layer: Image.Image) -> Image.Image:
    """The layer's warm amber pixels only, alpha graded by how saturated and bright the amber is."""
    arr = np.asarray(layer)
    alpha = Image.fromarray((amber_weight(arr) * 255 + 0.5).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6))
    out = layer.copy()
    out.putalpha(alpha)
    return out


def chitin(body: Image.Image, materials: np.ndarray) -> Image.Image:
    """Recolours the creature materials to the chitin proposal by a luminance gradient map; amber stays amber."""
    arr = np.asarray(body).astype(np.float32)
    lum = 0.299 * arr[..., 0] + 0.587 * arr[..., 1] + 0.114 * arr[..., 2]
    xs = [p for p, _ in CHITIN_RAMP]
    mapped = np.dstack([np.interp(lum, xs, [c[i] for _, c in CHITIN_RAMP]) for i in range(3)])
    creature = np.isin(materials, make_guide.CREATURE)
    creature = np.asarray(Image.fromarray(creature.astype(np.uint8) * 255).filter(ImageFilter.GaussianBlur(1.0)),
                          np.float32)[..., None] / 255
    amber = amber_weight(arr.astype(np.uint8))[..., None]
    target = mapped * (1 - amber) + (np.array(CHITIN_AMBER, np.float32) * np.clip(lum[..., None] / 200, 0.6, 1.2)) * amber
    flesh = np.asarray(Image.fromarray((materials == make_guide.FLESH).astype(np.uint8) * 255).filter(
        ImageFilter.GaussianBlur(1.0)), np.float32)[..., None] / 255
    umber = np.clip(lum[..., None] * 0.9 * np.array(FLESH_TINT, np.float32), 0, 255)
    target = target * (1 - flesh) + (umber * (1 - amber) + target * amber) * flesh
    rgb = arr[..., :3] * (1 - creature) + target * creature
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), arr[..., 3]]).astype(np.uint8), "RGBA")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--palette", choices=("runner", "chitin"), default="runner")
    parser.add_argument("--part", action="append", help="limit to these parts")
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    repository = here.parent.parent
    templates = repository / "art-source" / "templates"
    if args.palette == "runner":
        out_dir = repository / "mod" / "graphics" / "structures"
    else:
        out_dir = repository / "build" / "swarm-bores" / args.palette
        out_dir.mkdir(parents=True, exist_ok=True)
    for part in make_guide.PARTS:
        if args.part and part not in args.part:
            continue
        edge = edge_px(part)
        for v in range(1, make_guide.VARIANTS[part] + 1):
            raw_path = here / "raw" / f"{part}-{v}.raw.png"
            if not raw_path.exists():
                print(f"{part}-{v}: no raw yet, skipped")
                continue
            raw = Image.open(raw_path).convert("RGBA").resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)
            guide = Image.open(make_guide.guide_path(templates, part, v)).convert("RGBA")
            small = (256, 256)
            before = iou(mask(raw.resize(small)), mask(guide.resize(small)))
            raw = fitted(raw, guide)
            score = iou(mask(raw.resize(small)), mask(guide.resize(small)))
            raw.putalpha(raw.getchannel("A").point(lambda value: 0 if value < ALPHA_FLOOR else value))
            field = make_guide.build(part, v)
            if args.palette == "chitin":
                raw = chitin(raw, field.m)
            lit = glow(raw).resize((edge, edge), Image.Resampling.LANCZOS)
            sprite = make_guide.ground_layer(field)
            sprite.alpha_composite(raw)
            sprite = sprite.resize((edge, edge), Image.Resampling.LANCZOS)
            name = f"{NAMES[part]}-{v}"
            body = out_dir / f"{name}.png"
            sprite.save(body, optimize=True)
            lit.save(out_dir / f"{name}-glow.png", optimize=True)
            coverage = (np.asarray(lit.getchannel("A")) >= 128).sum() / max(1, (np.asarray(sprite.getchannel("A")) >= 128).sum())
            print(f"{part}-{v}: IoU {before:.3f} raw, {score:.3f} fitted -> {body.relative_to(repository)} "
                  f"{sprite.size}, glow {coverage:.1%} of body", flush=True)


if __name__ == "__main__":
    main()
