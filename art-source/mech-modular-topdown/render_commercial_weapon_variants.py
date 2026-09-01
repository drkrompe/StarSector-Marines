"""Render the common-market weapon family against every supported livery."""

from dataclasses import replace
from pathlib import Path

from PIL import Image, ImageDraw

from render_faction_weapon_variants import FACTIONS, faction_path
from render_variants import VARIANTS, font, render_mech


HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parent.parent
OUTPUT = REPOSITORY / "roadmap" / "mechs" / "previews" / "commercial-mech-weapons.png"


def commercial_fit(variant):
    if variant.name == "BULWARK":
        return replace(
            variant,
            arms="muster-autogun.png",
            arm_layout="dual-narrow",
            left_pod="pioneer-rocket-cradle.png",
            right_pod="pioneer-rocket-cradle.png",
            loadout="DUAL MUSTER AUTOGUNS  /  PIONEER  /  PIONEER",
        )
    if variant.name == "HOUND":
        return replace(
            variant,
            arms="muster-autogun.png",
            arm_layout="nose",
            left_pod="pioneer-rocket-cradle.png",
            right_pod=None,
            loadout="NOSE MUSTER AUTOGUN  /  PIONEER",
        )
    return replace(
        variant,
        arms="quarry-breaker-cannon.png",
        arm_layout="nose-heavy",
        left_pod="pioneer-rocket-cradle.png",
        right_pod="pioneer-rocket-cradle.png",
        loadout="QUARRY BREAKER  /  PIONEER  /  PIONEER",
    )


def main() -> None:
    width = 1740
    header = 148
    row_height = 300
    sheet = Image.new("RGBA", (width, header + row_height * len(FACTIONS) + 38),
                      (14, 18, 21, 255))
    draw = ImageDraw.Draw(sheet)
    draw.text((38, 24), "COMMON-MARKET MECH WEAPON FIT CHECK", font=font(34, bold=True),
              fill=(232, 237, 236))
    draw.text((40, 70),
              "Commercial floor hardware shown in every livery — not faction default loadouts",
              font=font(17), fill=(148, 164, 169))
    draw.text((40, 99), "Muster autogun  /  Quarry breaker  /  Pioneer rocket cradle",
              font=font(16), fill=(225, 181, 81))

    column_left = 330
    card_width = 450
    for row, (folder, label) in enumerate(FACTIONS):
        top = header + row * row_height
        fill = (22, 28, 32) if row % 2 == 0 else (18, 24, 28)
        draw.rectangle((24, top + 6, width - 24, top + row_height - 6),
                       fill=fill, outline=(57, 69, 75), width=1)
        draw.text((48, top + 114), label, font=font(21, bold=True),
                  fill=(225, 181, 81))

        for column, base_variant in enumerate(VARIANTS):
            variant = commercial_fit(base_variant)
            skinned = replace(
                variant,
                chassis=faction_path(folder, variant.chassis),
                arms=faction_path(folder, variant.arms),
                left_pod=faction_path(folder, variant.left_pod),
                right_pod=faction_path(folder, variant.right_pod),
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
