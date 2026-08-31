"""Render the three chassis across every retained human faction skin family."""

from dataclasses import replace
from pathlib import Path

from PIL import Image, ImageDraw

from render_variants import VARIANTS, font, render_mech


HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parent.parent
OUTPUT = REPOSITORY / "roadmap" / "mechs" / "previews" / "factional-mech-variants.png"
FACTIONS = (
    ("hegemony", "HEGEMONY"),
    ("tri-tachyon", "TRI-TACHYON"),
    ("persean-league", "PERSEAN LEAGUE"),
    ("luddic-church", "LUDDIC CHURCH"),
    ("knights-of-ludd", "KNIGHTS OF LUDD"),
    ("luddic-path", "LUDDIC PATH"),
    ("sindrian-diktat", "SINDRIAN DIKTAT"),
    ("lions-guard", "LION'S GUARD"),
    ("pirates", "PIRATES"),
    ("independent", "INDEPENDENT / MERCENARY"),
)


def main() -> None:
    width = 1740
    header = 132
    row_height = 300
    sheet = Image.new("RGBA", (width, header + row_height * len(FACTIONS) + 38),
                      (14, 18, 21, 255))
    draw = ImageDraw.Draw(sheet)
    draw.text((38, 24), "FACTIONAL MECH CHASSIS SKINS", font=font(35, bold=True),
              fill=(232, 237, 236))
    draw.text((40, 70),
              "Bulwark / Hound / Sirocco — shared weapon layers retained — north/up",
              font=font(17), fill=(148, 164, 169))

    column_left = 330
    card_width = 450
    for row, (folder, label) in enumerate(FACTIONS):
        top = header + row * row_height
        fill = (22, 28, 32) if row % 2 == 0 else (18, 24, 28)
        draw.rectangle((24, top + 6, width - 24, top + row_height - 6),
                       fill=fill, outline=(57, 69, 75), width=1)
        draw.text((48, top + 114), label, font=font(21, bold=True),
                  fill=(225, 181, 81))

        for column, variant in enumerate(VARIANTS):
            chassis_name = {
                "BULWARK": "chassis.png",
                "HOUND": "chassis-hound.png",
                "SIROCCO": "chassis-sirocco.png",
            }[variant.name]
            skinned = replace(
                variant,
                chassis=f"factions/{folder}/{chassis_name}",
            )
            left = column_left + column * card_width
            draw.rounded_rectangle(
                (left, top + 20, left + card_width - 18, top + row_height - 20),
                radius=12, fill=(27, 34, 38), outline=(67, 81, 87), width=1)
            hull_width = round(126 * variant.render_scale)
            render = render_mech(skinned, hull_width, (card_width - 18, 228))
            sheet.alpha_composite(render, (left, top + 18))
            text_box = draw.textbbox((0, 0), variant.name, font=font(15, bold=True))
            text_width = text_box[2] - text_box[0]
            draw.text((left + (card_width - 18 - text_width) / 2, top + 252),
                      variant.name, font=font(15, bold=True), fill=(164, 179, 184))

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(OUTPUT, quality=95)
    print(OUTPUT)


if __name__ == "__main__":
    main()
