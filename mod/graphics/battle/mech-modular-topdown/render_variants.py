"""Render a reproducible contact sheet for the runtime layered mech variants.

The placement rules mirror LayeredMechComposer at zero facing and zero recoil.
This makes the sheet useful for reviewing the source sprites without launching
Starsector, while retaining the variants' relative in-battle render scales.
"""

from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parent
REPOSITORY = ROOT.parents[3]
OUTPUT = REPOSITORY / "roadmap" / "mechs" / "previews" / "layered-mech-variants.png"
SOURCE_HULL_WIDTH = 208


@dataclass(frozen=True)
class Variant:
    name: str
    subtitle: str
    chassis: str
    arms: str
    left_pod: str
    right_pod: str
    render_scale: float
    loadout: str


VARIANTS = (
    Variant(
        "BULWARK",
        "HEAVY ALL-RANGE ANCHOR",
        "chassis.png",
        "chaingun-arm.png",
        "lrm-pod.png",
        "lrm-pod.png",
        1.60,
        "DUAL CHAINGUNS  /  SRM-15  /  LRM-15",
    ),
    Variant(
        "HOUND",
        "FAST CLOSE-ASSAULT STRIDER",
        "chassis-hound.png",
        "chaingun-arm.png",
        "srm-pod.png",
        "srm-pod.png",
        1.35,
        "DUAL CHAINGUNS  /  SRM-5  /  SRM-5",
    ),
    Variant(
        "SIROCCO",
        "MOBILE LONG-RANGE SUPPORT",
        "chassis-sirocco.png",
        "linear-cannon-variant.png",
        "srm-pod.png",
        "srm-pod.png",
        1.35,
        "DUAL LINEAR CANNONS  /  LRM-5  /  LRM-5",
    ),
)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    try:
        return ImageFont.truetype(name, size)
    except OSError:
        return ImageFont.load_default()


def scaled_sprite(filename: str, hull_width: int) -> Image.Image:
    sprite = Image.open(ROOT / filename).convert("RGBA")
    scale = hull_width / SOURCE_HULL_WIDTH
    size = (max(1, round(sprite.width * scale)), max(1, round(sprite.height * scale)))
    return sprite.resize(size, Image.Resampling.LANCZOS)


def centered(canvas: Image.Image, sprite: Image.Image, origin: tuple[int, int],
             hull_width: int, local_x: float, local_y: float) -> None:
    """Mirror emitCentered, converting Starsector's +Y-up to Pillow's +Y-down."""
    center_x = origin[0] + local_x * hull_width
    center_y = origin[1] - local_y * hull_width
    canvas.alpha_composite(sprite, (round(center_x - sprite.width / 2),
                                    round(center_y - sprite.height / 2)))


def rear_pivot(canvas: Image.Image, sprite: Image.Image, origin: tuple[int, int],
               hull_width: int, local_x: float, local_y: float) -> None:
    """Mirror emitFromRearPivot for an authored sprite pointing straight up."""
    pivot_x = origin[0] + local_x * hull_width
    pivot_y = origin[1] - local_y * hull_width
    canvas.alpha_composite(sprite, (round(pivot_x - sprite.width / 2),
                                    round(pivot_y - sprite.height)))


def render_mech(variant: Variant, hull_width: int, canvas_size: tuple[int, int]) -> Image.Image:
    canvas = Image.new("RGBA", canvas_size)
    origin = (canvas_size[0] // 2, canvas_size[1] // 2)

    foot = scaled_sprite("foot.png", hull_width)
    arms = scaled_sprite(variant.arms, hull_width)
    chassis = scaled_sprite(variant.chassis, hull_width)
    left_pod = scaled_sprite(variant.left_pod, hull_width)
    right_pod = scaled_sprite(variant.right_pod, hull_width)

    # Runtime draw order and zero-motion anchors from LayeredMechComposer.
    centered(canvas, foot, origin, hull_width, -0.17, -0.28)
    centered(canvas, foot, origin, hull_width, 0.17, -0.28)
    rear_pivot(canvas, arms, origin, hull_width, -0.37, -0.15)
    rear_pivot(canvas, arms, origin, hull_width, 0.37, -0.15)
    # Racks are external shoulder equipment beneath the body layer. Their
    # inboard casing is buried; only the outboard rack changes the silhouette.
    rear_pivot(canvas, left_pod, origin, hull_width, -0.40, -0.30)
    rear_pivot(canvas, right_pod, origin, hull_width, 0.40, -0.30)
    centered(canvas, chassis, origin, hull_width, 0.0, 0.0)
    return canvas


def draw_grid(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], spacing: int = 24) -> None:
    left, top, right, bottom = box
    for x in range(left, right + 1, spacing):
        draw.line((x, top, x, bottom), fill=(41, 52, 58), width=1)
    for y in range(top, bottom + 1, spacing):
        draw.line((left, y, right, y), fill=(41, 52, 58), width=1)


def centered_text(draw: ImageDraw.ImageDraw, center_x: int, y: int, value: str,
                  text_font: ImageFont.ImageFont, fill: tuple[int, int, int]) -> None:
    box = draw.textbbox((0, 0), value, font=text_font)
    draw.text((center_x - (box[2] - box[0]) / 2, y), value, font=text_font, fill=fill)


def card(sheet: Image.Image, variant: Variant, index: int, top: int,
         hull_width: int, image_height: int, caption: bool) -> None:
    draw = ImageDraw.Draw(sheet)
    left = 45 + index * 510
    right = left + 490
    bottom = top + image_height + (112 if caption else 35)
    draw.rounded_rectangle((left, top, right, bottom), radius=14,
                           fill=(25, 31, 35), outline=(67, 81, 87), width=2)
    draw_grid(draw, (left + 1, top + 1, right - 1, top + image_height))

    render = render_mech(variant, hull_width, (470, image_height - 8))
    sheet.alpha_composite(render, (left + 10, top + 4))

    if caption:
        center = (left + right) // 2
        centered_text(draw, center, top + image_height + 14, variant.name,
                      font(27, bold=True), (225, 181, 81))
        centered_text(draw, center, top + image_height + 47, variant.subtitle,
                      font(14, bold=True), (164, 179, 184))
        centered_text(draw, center, top + image_height + 73, variant.loadout,
                      font(13), (207, 216, 218))


def main() -> None:
    sheet = Image.new("RGBA", (1600, 1120), (14, 18, 21, 255))
    draw = ImageDraw.Draw(sheet)

    draw.text((45, 30), "LAYERED MECH VARIANT PREVIEW", font=font(38, bold=True),
              fill=(232, 237, 236))
    draw.text((47, 78),
              "Runtime layer order and anchor math at idle / facing north / zero recoil",
              font=font(17), fill=(148, 164, 169))

    draw.text((45, 124), "GAMEPLAY-RELATIVE SILHOUETTES", font=font(20, bold=True),
              fill=(225, 181, 81))
    draw.text((405, 127), "Bulwark 1.60x  /  Hound + Sirocco 1.35x", font=font(16),
              fill=(148, 164, 169))
    for index, variant in enumerate(VARIANTS):
        hull_width = round(160 * variant.render_scale)
        card(sheet, variant, index, 158, hull_width, 390, caption=True)

    draw.text((45, 690), "NORMALIZED SPRITE INSPECTION", font=font(20, bold=True),
              fill=(225, 181, 81))
    draw.text((362, 693), "identical 208 px chassis width", font=font(16),
              fill=(148, 164, 169))
    for index, variant in enumerate(VARIANTS):
        card(sheet, variant, index, 724, SOURCE_HULL_WIDTH, 300, caption=False)

    draw.text((45, 1080),
              "Generated by mod/graphics/battle/mech-modular-topdown/render_variants.py",
              font=font(13), fill=(104, 119, 124))
    draw.text((1195, 1080), "SOURCE HULL: 208 x 208 PX", font=font(13),
              fill=(104, 119, 124))

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(OUTPUT, quality=95)
    print(OUTPUT)


if __name__ == "__main__":
    main()
