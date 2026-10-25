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

The creature materials (chitin, sinew, bone, flesh, sac; read from the guide's material map, so thrown earth and
paving keep their colours) are recoloured by luminance gradient maps to the settled Rav Bloom palette, ``bloom``:
lilac carapace, crimson sinew, ivory bone, amber. That is what ships. ``--palette purple`` or ``chitin`` (earlier
proposals) and ``raw`` (the generations' own colours) write previews under ``build/swarm-bores/<palette>/``.

Prints the silhouette IoU of each raw against its guide (the registration check) and the glow coverage.

Run from the repository root: ``python art-source/swarm-bores/export.py [--palette bloom|raw|purple|chitin]``.
"""

import argparse
import re
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
    "ground-colony": "ground-colony",
    "brood-spire": "brood-spire",
    "nest-clutch": "nest-clutch",
    "nest-pods": "nest-pods",
    "nest-husk": "nest-husk",
}
# Parts drawn dried out like a dead body's remains, and without a glow layer: the nest's shed husk.
DRIED = ("nest-husk",)

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

# Rav Bloom round 3 (purples): ramps sampled from the round-3 Runner plates (rav-redesign
# `rounds/round-3/raw/c1-c4`), by luminance. Violet and plum carapace running up to ivory blade highlights;
# magenta-crimson sinew for roots and a shaft's lining.
PURPLE_RAMP = [
    (0, (6, 3, 7)),
    (20, (21, 11, 21)),
    (40, (42, 26, 43)),
    (60, (62, 40, 65)),
    (80, (83, 57, 88)),
    (100, (105, 75, 111)),
    (120, (126, 95, 133)),
    (140, (147, 114, 155)),
    (165, (180, 150, 170)),
    (195, (205, 188, 184)),
    (220, (226, 208, 198)),
    (245, (242, 232, 226)),
    (255, (251, 246, 243)),
]
PURPLE_SINEW = [
    (0, (8, 2, 4)),
    (20, (28, 5, 9)),
    (40, (56, 17, 26)),
    (60, (86, 31, 43)),
    (80, (115, 46, 60)),
    (100, (142, 63, 79)),
    (120, (166, 80, 98)),
    (140, (190, 96, 120)),
    (160, (213, 112, 143)),
    (255, (240, 170, 195)),
]
PURPLE_AMBER = (0xf2, 0x9a, 0x3c)

# The settled Rav Bloom palette (owner, 2026-10-21): sampled by luminance from the approved Runner and Brute plates
# (rav-redesign `motion-runner/raw/hero.png`, `motion-brute/raw/hero.png`). Lilac to dusky-violet carapace,
# crimson-magenta rope sinew for roots and a shaft's lining, ivory bone, amber eyes. The shipped export.
BLOOM_CARAPACE = [
    (0, (8, 4, 8)),
    (30, (48, 26, 44)),
    (50, (71, 40, 66)),
    (70, (92, 55, 85)),
    (90, (115, 73, 107)),
    (110, (137, 92, 129)),
    (130, (156, 113, 148)),
    (150, (173, 134, 167)),
    (170, (190, 155, 185)),
    (190, (207, 178, 202)),
    (210, (222, 198, 217)),
    (230, (238, 218, 233)),
    (255, (252, 246, 250)),
]
BLOOM_SINEW = [
    (0, (14, 0, 4)),
    (10, (37, 1, 11)),
    (30, (74, 6, 31)),
    (50, (113, 16, 50)),
    (70, (143, 30, 69)),
    (90, (154, 56, 87)),
    (110, (156, 85, 105)),
    (130, (178, 105, 125)),
    (150, (202, 119, 145)),
    (170, (231, 135, 166)),
    (255, (245, 185, 205)),
]
BLOOM_BONE = [
    (0, (20, 14, 12)),
    (40, (55, 42, 38)),
    (70, (86, 67, 60)),
    (90, (111, 85, 76)),
    (110, (137, 103, 92)),
    (130, (158, 122, 108)),
    (150, (179, 141, 125)),
    (170, (198, 161, 145)),
    (190, (216, 182, 164)),
    (210, (232, 204, 187)),
    (230, (245, 225, 210)),
    (255, (253, 245, 236)),
]
BLOOM_AMBER = (0xf2, 0x9a, 0x3c)

# palette -> (creature ramp, sinew ramp or None, materials the sinew ramp takes, amber, bone ramp or None). Without a
# sinew ramp a shaft's lining keeps its value and only takes FLESH_TINT; without a bone ramp bone takes the creature
# ramp.
PALETTES = {
    "bloom": (BLOOM_CARAPACE, BLOOM_SINEW, (make_guide.FLESH, make_guide.UNDER), BLOOM_AMBER, BLOOM_BONE),
    "chitin": (CHITIN_RAMP, None, (make_guide.FLESH,), CHITIN_AMBER, None),
    "purple": (PURPLE_RAMP, PURPLE_SINEW, (make_guide.FLESH, make_guide.UNDER), PURPLE_AMBER, None),
}
# The palette the export ships to mod/; any other writes a preview under build/swarm-bores/<palette>/. "raw" ships
# nothing either: it is the generations' own colours, previewed.
SHIPPED = "bloom"


CREEP_PALETTE_JAVA = "src/main/java/com/dillon/starsectormarines/ops/battleview/CreepPalette.java"


def creep_palette(repository: Path) -> dict:
    """The creep's default palette as the game draws it, read from ``CreepPalette.java``: name -> RGB 0..255.

    The bore's berm is creep heaved up out of the shaft, so it takes the creep's own colours. Reading them from the
    Java keeps the two in step; re-export after the creep palette changes.
    """
    text = (repository / CREEP_PALETTE_JAVA).read_text(encoding="utf-8")
    default = re.search(r"System\.getProperty\(PROPERTY,\s*(\w+)\.id\(\)\)", text).group(1)
    entry = re.search(default + r"\s*=\s*new CreepPalette\(\s*\"\w+\",((?:\s*rgb\(0x[0-9a-fA-F]{6}\),?){6})", text)
    hexes = re.findall(r"0x([0-9a-fA-F]{6})", entry.group(1))
    names = ("base", "flesh", "vein", "glow", "rim", "dead")
    return {n: tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) for n, h in zip(names, hexes)}


def creep_ramp(palette: dict) -> list:
    """Luminance in -> creep colour out: veins in the shadows, film in the mid-tones, nodules in the lights."""
    def scale(c, k):
        return tuple(min(255, round(v * k)) for v in c)
    return [
        (0, scale(palette["vein"], 0.45)),
        (45, palette["vein"]),
        (95, scale(palette["base"], 0.9)),
        (140, palette["base"]),
        (185, palette["flesh"]),
        (230, scale(palette["flesh"], 1.2)),
        (255, scale(palette["flesh"], 1.35)),
    ]


HUSK_DRAIN = 0.6
HUSK_DIM = 0.9


def husk(body: Image.Image, materials: np.ndarray) -> Image.Image:
    """A dead body's creature materials drained towards grey and dimmed: dry, dull, no longer the living palette."""
    arr = np.asarray(body).astype(np.float32)
    lum = (0.299 * arr[..., 0] + 0.587 * arr[..., 1] + 0.114 * arr[..., 2])[..., None]
    creature = soft_mask(materials, make_guide.CREATURE)
    drained = (arr[..., :3] * (1 - HUSK_DRAIN) + lum * HUSK_DRAIN) * HUSK_DIM
    rgb = arr[..., :3] * (1 - creature) + drained * creature
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), arr[..., 3]]).astype(np.uint8), "RGBA")


def overgrow(body: Image.Image, materials: np.ndarray, ramp: list) -> Image.Image:
    """The thrown earth of a bore's berm turned to creep by luminance gradient map; paving slabs stay paving."""
    arr = np.asarray(body).astype(np.float32)
    lum = 0.299 * arr[..., 0] + 0.587 * arr[..., 1] + 0.114 * arr[..., 2]
    earth = soft_mask(materials, (make_guide.EARTH,))
    rgb = arr[..., :3] * (1 - earth) + gradient(lum, ramp) * earth
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), arr[..., 3]]).astype(np.uint8), "RGBA")


def edge_px(part: str) -> int:
    return 2 * round(make_guide.display_cells(part) * PX_PER_CELL / 2)


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
    return refined(out, guide)


def refined(raw: Image.Image, guide: Image.Image, small: int = 256) -> Image.Image:
    """Nudges the bbox fit by the small scale and shift that best overlays the raw's silhouette on the guide's.

    The bounding boxes agree only as far as the painting's outermost reach does: a root painted a little longer than
    the guide's shifts the whole fit, and thin roots then miss their material by a few pixels.
    """
    g = np.asarray(guide.getchannel("A").resize((small, small), Image.Resampling.BILINEAR)) >= 128
    best = (iou_np(np.asarray(raw.getchannel("A").resize((small, small), Image.Resampling.BILINEAR)) >= 128, g), 1.0, 0, 0)
    for scale in np.arange(0.95, 1.051, 0.01):
        size = round(small * scale)
        a = np.asarray(raw.getchannel("A").resize((size, size), Image.Resampling.BILINEAR)) >= 128
        canvas = np.zeros((small, small), bool)
        o = (small - size) // 2
        lo, hi = max(0, o), min(small, o + size)
        canvas[lo:hi, lo:hi] = a[lo - o:hi - o, lo - o:hi - o]
        for dy in range(-10, 11):
            rolled_y = np.roll(canvas, dy, 0)
            for dx in range(-10, 11):
                score = iou_np(np.roll(rolled_y, dx, 1), g)
                if score > best[0]:
                    best = (score, scale, dx, dy)
    _, scale, dx, dy = best
    if scale == 1.0 and dx == 0 and dy == 0:
        return raw
    k = raw.width / small
    size = round(raw.width * scale)
    scaled = raw.resize((size, size), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", raw.size, (0, 0, 0, 0))
    o = (raw.width - size) // 2
    out.paste(scaled, (o + round(dx * k), o + round(dy * k)), scaled)
    return out


def iou_np(a: np.ndarray, b: np.ndarray) -> float:
    union = np.count_nonzero(a | b)
    return np.count_nonzero(a & b) / union if union else 0.0


def reach(materials: np.ndarray, body: Image.Image, steps: int = 16) -> np.ndarray:
    """The material map grown out over whatever the painting covers past the guide's edge, nearest material first,
    so a root painted a few pixels off its guide is still recoloured as root."""
    m = materials.copy()
    painted = np.asarray(body.getchannel("A")) >= ALPHA_FLOOR
    for _ in range(steps):
        empty = (m == make_guide.NONE) & painted
        if not empty.any():
            break
        for axis, shift in ((0, 1), (0, -1), (1, 1), (1, -1)):
            near = np.roll(m, shift, axis)
            take = empty & (near != make_guide.NONE)
            m[take] = near[take]
            empty &= ~take
    return m


def ember_weight(rgba: np.ndarray) -> np.ndarray:
    """0..1 per pixel: how much of a dim warm glow it is (the light far down a throat, too dark for a node)."""
    rgb = rgba[..., :3].astype(np.float32) / 255
    mx, mn = rgb.max(-1), rgb.min(-1)
    s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    warm = (r >= g) & (g > b) & ((g - b) > 0.35 * (r - b))
    return np.where(warm, np.clip((s - 0.55) / 0.25, 0, 1) * np.clip(mx / 0.08, 0, 1), 0)


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


def gradient(lum: np.ndarray, ramp) -> np.ndarray:
    xs = [p for p, _ in ramp]
    return np.dstack([np.interp(lum, xs, [c[i] for _, c in ramp]) for i in range(3)])


def soft_mask(materials: np.ndarray, which) -> np.ndarray:
    hit = np.isin(materials, which).astype(np.uint8) * 255
    return np.asarray(Image.fromarray(hit).filter(ImageFilter.GaussianBlur(1.0)), np.float32)[..., None] / 255


def recolour(body: Image.Image, materials: np.ndarray, palette: str) -> Image.Image:
    """Recolours the creature materials to a proposed creature palette by luminance gradient maps; amber stays amber.

    Earth and paving are not creature and keep their colours.
    """
    ramp, sinew_ramp, sinew_materials, amber_colour, bone_ramp = PALETTES[palette]
    arr = np.asarray(body).astype(np.float32)
    lum = 0.299 * arr[..., 0] + 0.587 * arr[..., 1] + 0.114 * arr[..., 2]
    amber = amber_weight(arr.astype(np.uint8))[..., None]
    glowing = np.array(amber_colour, np.float32) * np.clip(lum[..., None] / 200, 0.6, 1.2)
    target = gradient(lum, ramp)
    sinew = soft_mask(materials, sinew_materials)
    if sinew_ramp is not None:
        lining = gradient(lum, sinew_ramp)
    else:
        lining = np.clip(lum[..., None] * 0.9 * np.array(FLESH_TINT, np.float32), 0, 255)
    target = target * (1 - sinew) + lining * sinew
    if bone_ramp is not None:
        bone = soft_mask(materials, (make_guide.BONE,))
        target = target * (1 - bone) + gradient(lum, bone_ramp) * bone
    ember = ember_weight(arr.astype(np.uint8))[..., None]
    target = target * (1 - ember) + arr[..., :3] * ember
    target = target * (1 - amber) + glowing * amber
    creature = soft_mask(materials, make_guide.CREATURE)
    rgb = arr[..., :3] * (1 - creature) + target * creature
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), arr[..., 3]]).astype(np.uint8), "RGBA")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--palette", choices=("raw", *PALETTES), default=SHIPPED)
    parser.add_argument("--part", action="append", help="limit to these parts")
    parser.add_argument("--state", action="append", choices=make_guide.STATES, help="limit to these damage states")
    parser.add_argument("--spire-option", choices=make_guide.SPIRE_STYLES,
                        help="export one look option's painting (raw/options/) as brood-spire-1, for the owner's pick")
    parser.add_argument("--mother-option", choices=make_guide.MOTHER_STYLES,
                        help="export one look option's painting (raw/options/) as mother-bore-1, for the owner's pick")
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    repository = here.parent.parent
    templates = repository / "art-source" / "templates"
    if args.palette == SHIPPED:
        out_dir = repository / "mod" / "graphics" / "structures"
    else:
        out_dir = repository / "build" / "swarm-bores" / args.palette
        out_dir.mkdir(parents=True, exist_ok=True)
    creep = creep_palette(repository)
    berm = creep_ramp(creep)
    withered = creep_ramp({**creep, "base": creep["dead"], "flesh": tuple(min(255, round(c * 1.25)) for c in creep["dead"]),
                           "vein": tuple(round(c * 0.6) for c in creep["dead"])})
    states = args.state or list(make_guide.STATES)
    for part in make_guide.PARTS:
        if args.part and part not in args.part:
            continue
        edge = edge_px(part)
        options = {"brood-spire": args.spire_option, "mother-bore": args.mother_option}
        option = options.get(part)
        if any(options.values()) and not option:
            continue
        for v in range(1, (1 if option else make_guide.VARIANTS[part]) + 1):
            for state in (["intact"] if option else [s for s in states if s in make_guide.part_states(part)]):
                suffix = "" if state == "intact" else f"-{state}"
                raw_path = here / "raw" / f"{part}-{v}{suffix}.raw.png"
                guide_path = make_guide.guide_path(templates, part, v, state)
                if option:
                    raw_path = here / "raw" / "options" / f"{part}-{option}-{v}.raw.png"
                    guide_path = templates / f"swarm-{part}-{option}-{v}-guide.png"
                if not raw_path.exists():
                    print(f"{part}-{v}{suffix}: no raw yet, skipped")
                    continue
                raw = Image.open(raw_path).convert("RGBA").resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)
                guide = Image.open(guide_path).convert("RGBA")
                small = (256, 256)
                before = iou(mask(raw.resize(small)), mask(guide.resize(small)))
                raw = fitted(raw, guide)
                score = iou(mask(raw.resize(small)), mask(guide.resize(small)))
                raw.putalpha(raw.getchannel("A").point(lambda value: 0 if value < ALPHA_FLOOR else value))
                field = make_guide.build(part, v, state, option)
                materials = reach(field.m, raw)
                if args.palette != "raw":
                    raw = recolour(raw, materials, args.palette)
                    if state == "remains" or part in DRIED:
                        raw = husk(raw, materials)
                    raw = overgrow(raw, materials, withered if state == "remains" else berm)
                sprite = make_guide.ground_layer(field)
                sprite.alpha_composite(raw)
                sprite = sprite.resize((edge, edge), Image.Resampling.LANCZOS)
                name = f"{NAMES[part]}-{v}{suffix}"
                body = out_dir / f"{name}.png"
                sprite.save(body, optimize=True)
                note = ""
                if state != "remains" and part not in DRIED:
                    # The dead have no glow; the living (hurt or not) breathe.
                    lit = glow(raw).resize((edge, edge), Image.Resampling.LANCZOS)
                    lit.save(out_dir / f"{name}-glow.png", optimize=True)
                    coverage = (np.asarray(lit.getchannel("A")) >= 128).sum() / max(1, (np.asarray(sprite.getchannel("A")) >= 128).sum())
                    note = f", glow {coverage:.1%} of body"
                print(f"{part}-{v}{suffix}: IoU {before:.3f} raw, {score:.3f} fitted -> {body.relative_to(repository)} "
                      f"{sprite.size}{note}", flush=True)

if __name__ == "__main__":
    main()
