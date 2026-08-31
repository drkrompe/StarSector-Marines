"""Bake faction weapon paint from ImageGen-assisted material masks.

ImageGen identifies which pixels belong to painted armor; its replacement RGB
never enters a shipped sprite.  The original weapon supplies alpha, geometry,
lighting, wear, outlines, and protected hardware.  Each faction's three
accepted chassis skins supply a robust shadow/mid/highlight color ramp.
"""

from dataclasses import dataclass
from pathlib import Path
from statistics import median

from PIL import Image, ImageChops


HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parent.parent
SOURCES = HERE / "sources"
ROOT = REPOSITORY / "mod" / "graphics" / "battle" / "mech-modular-topdown"
MASK_ROOT = HERE / "masks" / "weapon-paint"

FACTIONS = (
    "hegemony",
    "tri-tachyon",
    "persean-league",
    "luddic-church",
    "knights-of-ludd",
    "luddic-path",
    "sindrian-diktat",
    "lions-guard",
    "pirates",
    "independent",
)


@dataclass(frozen=True)
class Weapon:
    source: str
    material_id: str
    output: str
    size: tuple[int, int]


WEAPONS = (
    Weapon("chaingun-arm-v2.png", "chaingun-arm.png", "chaingun-arm.png", (62, 112)),
    Weapon("linear-cannon-concept.png", "linear-cannon-variant.png",
           "linear-cannon-variant.png", (58, 138)),
    Weapon("heavy-cannon.png", "heavy-cannon.png", "heavy-cannon.png", (64, 128)),
    Weapon("srm-pod.png", "srm-pod.png", "srm-pod.png", (62, 88)),
    Weapon("lrm-pod.png", "lrm-pod.png", "lrm-pod.png", (76, 96)),
    Weapon("weapon-concepts/shoulder-laser-cannon.png", "shoulder-laser-cannon.png",
           "shoulder-laser-cannon.png", (76, 128)),
    Weapon("weapon-concepts/pulse-laser-arm.png", "pulse-laser-arm.png",
           "pulse-laser-arm.png", (62, 112)),
    Weapon("weapon-concepts/hegemony-bastion-autocannon.png",
           "hegemony-bastion-autocannon.png",
           "hegemony-bastion-autocannon.png", (64, 132)),
    Weapon("weapon-concepts/pather-demolition-cannon.png",
           "pather-demolition-cannon.png",
           "pather-demolition-cannon.png", (72, 124)),
    Weapon("weapon-concepts/lions-guard-thermal-lance.png",
           "lions-guard-thermal-lance.png",
           "lions-guard-thermal-lance.png", (76, 132)),
)

CHASSIS = ("chassis.png", "chassis-hound.png", "chassis-sirocco.png")


def clamp01(value: float) -> float:
    return max(0.0, min(1.0, value))


def percentile(values: list[float], position: float) -> float:
    ordered = sorted(values)
    index = round(position * (len(ordered) - 1))
    return ordered[index]


def srgb_channel_to_linear(value: int) -> float:
    encoded = value / 255.0
    return encoded / 12.92 if encoded <= 0.04045 else ((encoded + 0.055) / 1.055) ** 2.4


def linear_channel_to_srgb(value: float) -> int:
    value = clamp01(value)
    encoded = 12.92 * value if value <= 0.0031308 else 1.055 * value ** (1 / 2.4) - 0.055
    return round(clamp01(encoded) * 255)


def rgb_to_oklab(rgb: tuple[int, int, int]) -> tuple[float, float, float]:
    red, green, blue = (srgb_channel_to_linear(channel) for channel in rgb)
    ll = 0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue
    mm = 0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue
    ss = 0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue
    ll, mm, ss = ll ** (1 / 3), mm ** (1 / 3), ss ** (1 / 3)
    return (
        0.2104542553 * ll + 0.7936177850 * mm - 0.0040720468 * ss,
        1.9779984951 * ll - 2.4285922050 * mm + 0.4505937099 * ss,
        0.0259040371 * ll + 0.7827717662 * mm - 0.8086757660 * ss,
    )


def oklab_to_rgb(color: tuple[float, float, float]) -> tuple[int, int, int]:
    lightness, aa, bb = color
    ll = (lightness + 0.3963377774 * aa + 0.2158037573 * bb) ** 3
    mm = (lightness - 0.1055613458 * aa - 0.0638541728 * bb) ** 3
    ss = (lightness - 0.0894841775 * aa - 1.2914855480 * bb) ** 3
    red = 4.0767416621 * ll - 3.3077115913 * mm + 0.2309699292 * ss
    green = -1.2684380046 * ll + 2.6097574011 * mm - 0.3413193965 * ss
    blue = -0.0041960863 * ll - 0.7034186147 * mm + 1.7076147010 * ss
    return tuple(linear_channel_to_srgb(value) for value in (red, green, blue))


def content_bbox(image: Image.Image, threshold: int = 24) -> tuple[int, int, int, int]:
    alpha = image.getchannel("A").point(lambda value: 255 if value >= threshold else 0)
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError("source has no visible pixels")
    return bbox


def magenta_mask(image: Image.Image) -> Image.Image:
    rgb = image.convert("RGB")
    result = Image.new("L", rgb.size)
    output: list[int] = []
    for red, green, blue in rgb.getdata():
        dominance = min(red - green, blue - green)
        brightness = min(red, blue)
        chroma_score = clamp01((dominance - 18) / 72)
        brightness_score = clamp01((brightness - 38) / 92)
        output.append(round(255 * chroma_score * brightness_score))
    result.putdata(output)
    return result


def normalized_mask(weapon: Weapon) -> Image.Image:
    source = Image.open(SOURCES / weapon.source).convert("RGBA")
    material = Image.open(SOURCES / "weapon-material-id" / weapon.material_id).convert("RGB")
    if material.size != source.size:
        material = material.resize(source.size, Image.Resampling.LANCZOS)
    bbox = content_bbox(source)
    visible = source.crop(bbox)
    visible.thumbnail(weapon.size, Image.Resampling.LANCZOS)
    mask = magenta_mask(material).crop(bbox).resize(visible.size, Image.Resampling.LANCZOS)
    canvas = Image.new("L", weapon.size)
    canvas.paste(mask, ((weapon.size[0] - visible.width) // 2,
                        (weapon.size[1] - visible.height) // 2))
    base = Image.open(ROOT / weapon.output).convert("RGBA")
    canvas = ImageChops.multiply(canvas, base.getchannel("A"))
    coverage = sum(1 for value in canvas.getdata() if value >= 128)
    opaque = sum(1 for value in base.getchannel("A").getdata() if value >= 128)
    fraction = coverage / opaque
    if not 0.20 <= fraction <= 0.82:
        raise ValueError(f"implausible paint-mask coverage for {weapon.output}: {fraction:.1%}")
    return canvas


def base_paint_pixel(rgb: tuple[int, int, int], alpha: int) -> bool:
    red, green, blue = rgb
    return (alpha >= 160 and red - blue >= 8 and green - blue >= 8
            and abs(red - green) <= 48 and red + green >= 105
            and max(red, green, blue) <= 238)


def target_samples(faction: str) -> list[tuple[float, float, float]]:
    samples: list[tuple[float, float, float]] = []
    for chassis in CHASSIS:
        base = Image.open(ROOT / chassis).convert("RGBA")
        target = Image.open(ROOT / "factions" / faction / chassis).convert("RGBA")
        for source_pixel, target_pixel in zip(base.getdata(), target.getdata()):
            if not base_paint_pixel(source_pixel[:3], source_pixel[3]):
                continue
            lab = rgb_to_oklab(target_pixel[:3])
            if 0.10 <= lab[0] <= 0.94:
                samples.append(lab)
    if len(samples) < 500:
        raise ValueError(f"not enough chassis paint samples for {faction}: {len(samples)}")
    return samples


def dominant_cluster(samples: list[tuple[float, float, float]]) -> list[tuple[float, float, float]]:
    points = [(sample[1], sample[2]) for sample in samples]
    mean = (sum(point[0] for point in points) / len(points),
            sum(point[1] for point in points) / len(points))
    seeds = [mean]
    while len(seeds) < 3:
        seeds.append(max(points, key=lambda point: min(
            (point[0] - seed[0]) ** 2 + (point[1] - seed[1]) ** 2 for seed in seeds)))
    assignments = [0] * len(points)
    for _ in range(10):
        for index, point in enumerate(points):
            assignments[index] = min(range(len(seeds)), key=lambda cluster:
                (point[0] - seeds[cluster][0]) ** 2 + (point[1] - seeds[cluster][1]) ** 2)
        next_seeds = []
        for cluster in range(len(seeds)):
            members = [point for point, assignment in zip(points, assignments)
                       if assignment == cluster]
            next_seeds.append((sum(point[0] for point in members) / len(members),
                               sum(point[1] for point in members) / len(members))
                              if members else seeds[cluster])
        seeds = next_seeds
    largest = max(range(len(seeds)), key=assignments.count)
    return [sample for sample, assignment in zip(samples, assignments) if assignment == largest]


def palette(faction: str) -> tuple[tuple[float, float, float], ...]:
    cluster = dominant_cluster(target_samples(faction))
    ordered = sorted(cluster, key=lambda sample: sample[0])
    colors = []
    for position in (0.12, 0.50, 0.88):
        center = round(position * (len(ordered) - 1))
        radius = max(10, len(ordered) // 20)
        window = ordered[max(0, center - radius):min(len(ordered), center + radius + 1)]
        colors.append(tuple(median(sample[channel] for sample in window)
                            for channel in range(3)))
    return tuple(colors)


def interpolate(left: tuple[float, float, float], right: tuple[float, float, float],
                amount: float) -> tuple[float, float, float]:
    return tuple(a + (b - a) * clamp01(amount) for a, b in zip(left, right))


def recolor(base: Image.Image, mask: Image.Image,
            colors: tuple[tuple[float, float, float], ...]) -> Image.Image:
    rgba = base.convert("RGBA")
    masked_lightness = [rgb_to_oklab(pixel[:3])[0]
                        for pixel, weight in zip(rgba.getdata(), mask.getdata())
                        if pixel[3] >= 128 and weight >= 128]
    low = percentile(masked_lightness, 0.08)
    middle = percentile(masked_lightness, 0.50)
    high = percentile(masked_lightness, 0.92)
    output = []
    for pixel, weight in zip(rgba.getdata(), mask.getdata()):
        if weight == 0 or pixel[3] == 0:
            output.append(pixel)
            continue
        lightness = rgb_to_oklab(pixel[:3])[0]
        if lightness <= middle:
            target = interpolate(colors[0], colors[1], (lightness - low) / max(1e-6, middle - low))
        else:
            target = interpolate(colors[1], colors[2], (lightness - middle) / max(1e-6, high - middle))
        target_rgb = oklab_to_rgb(target)
        amount = weight / 255.0
        blended = tuple(round(source + (replacement - source) * amount)
                        for source, replacement in zip(pixel[:3], target_rgb))
        output.append((*blended, pixel[3]))
    result = Image.new("RGBA", rgba.size)
    result.putdata(output)
    return result


def build_faction_weapons() -> None:
    MASK_ROOT.mkdir(parents=True, exist_ok=True)
    masks = {}
    for weapon in WEAPONS:
        mask = normalized_mask(weapon)
        mask.save(MASK_ROOT / weapon.output)
        masks[weapon.output] = mask
    for faction in FACTIONS:
        colors = palette(faction)
        output_root = ROOT / "factions" / faction
        output_root.mkdir(parents=True, exist_ok=True)
        for weapon in WEAPONS:
            base = Image.open(ROOT / weapon.output).convert("RGBA")
            result = recolor(base, masks[weapon.output], colors)
            if result.getchannel("A").tobytes() != base.getchannel("A").tobytes():
                raise ValueError(f"alpha changed for {faction}/{weapon.output}")
            result.save(output_root / weapon.output)


if __name__ == "__main__":
    build_faction_weapons()
