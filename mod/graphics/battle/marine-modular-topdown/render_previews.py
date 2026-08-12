"""Render pose previews from the independent runtime sprites and manifest transforms."""

from pathlib import Path
from math import cos, radians, sin

from PIL import Image


COMPOSITION_SIZE = 224
WORLD_PIVOT = (112, 112)
ACTOR_PIVOT = (71, 52)
FOOT_PIVOT = (19, 26)
WEAPON_PIVOT = (12, 73)
FLASH_PIVOT = (16, 28)

IDLE_FEET = ((-18, 35), (18, 35))
MOVING_FEET = ((-18, 50), (18, 35))
IDLE_WEAPON = ((28, -12), 45.0)
AIMED_WEAPON = ((26, -18), 0.0)


def place_by_pivot(layer: Image.Image, sprite: Image.Image,
                   sprite_pivot: tuple[int, int],
                   world_position: tuple[int, int]) -> None:
    layer.alpha_composite(sprite, (
        world_position[0] - sprite_pivot[0],
        world_position[1] - sprite_pivot[1],
    ))


def rotate_layer(layer: Image.Image, angle_degrees: float,
                 center: tuple[int, int]) -> Image.Image:
    if angle_degrees == 0:
        return layer
    return layer.rotate(
        angle_degrees,
        resample=Image.Resampling.NEAREST,
        center=center,
        expand=False,
    )


def rotated_point(point: tuple[int, int], center: tuple[int, int],
                  angle_degrees: float) -> tuple[int, int]:
    angle = radians(-angle_degrees)
    dx = point[0] - center[0]
    dy = point[1] - center[1]
    return (
        round(center[0] + dx * cos(angle) - dy * sin(angle)),
        round(center[1] + dx * sin(angle) + dy * cos(angle)),
    )


def compose_preview(actor: Image.Image, foot: Image.Image,
                    weapon: Image.Image, flash: Image.Image,
                    foot_offsets: tuple[tuple[int, int], tuple[int, int]],
                    weapon_pose: tuple[tuple[int, int], float],
                    show_flash: bool = False) -> Image.Image:
    preview = Image.new("RGBA", (COMPOSITION_SIZE, COMPOSITION_SIZE), (0, 0, 0, 0))

    for foot_offset in foot_offsets:
        place_by_pivot(preview, foot, FOOT_PIVOT, (
            WORLD_PIVOT[0] + foot_offset[0],
            WORLD_PIVOT[1] + foot_offset[1],
        ))

    weapon_offset, weapon_angle = weapon_pose
    weapon_world_pivot = (
        WORLD_PIVOT[0] + weapon_offset[0],
        WORLD_PIVOT[1] + weapon_offset[1],
    )
    weapon_layer = Image.new("RGBA", preview.size, (0, 0, 0, 0))
    place_by_pivot(weapon_layer, weapon, WEAPON_PIVOT, weapon_world_pivot)
    preview = Image.alpha_composite(
        preview,
        rotate_layer(weapon_layer, weapon_angle, weapon_world_pivot),
    )

    place_by_pivot(preview, actor, ACTOR_PIVOT, WORLD_PIVOT)

    if show_flash:
        muzzle_unrotated = (
            weapon_world_pivot[0],
            weapon_world_pivot[1] - WEAPON_PIVOT[1],
        )
        muzzle_world = rotated_point(muzzle_unrotated, weapon_world_pivot, weapon_angle)
        flash_layer = Image.new("RGBA", preview.size, (0, 0, 0, 0))
        place_by_pivot(flash_layer, flash, FLASH_PIVOT, muzzle_world)
        preview = Image.alpha_composite(
            preview,
            rotate_layer(flash_layer, weapon_angle, muzzle_world),
        )
    return preview


def main() -> None:
    directory = Path(__file__).resolve().parent
    actor = Image.open(directory / "marine-actor.png").convert("RGBA")
    foot = Image.open(directory / "marine-foot.png").convert("RGBA")
    weapon = Image.open(directory / "marine-rifle.png").convert("RGBA")
    flash = Image.open(directory / "marine-muzzle-flash.png").convert("RGBA")

    previews = (
        ("marine-idle-preview.png", IDLE_FEET, IDLE_WEAPON, False),
        ("marine-aimed-preview.png", IDLE_FEET, AIMED_WEAPON, False),
        ("marine-moving-preview.png", MOVING_FEET, IDLE_WEAPON, False),
        ("marine-firing-preview.png", IDLE_FEET, AIMED_WEAPON, True),
    )
    for filename, feet_pose, weapon_pose, show_flash in previews:
        compose_preview(
            actor, foot, weapon, flash,
            feet_pose, weapon_pose, show_flash,
        ).save(directory / filename, optimize=True)


if __name__ == "__main__":
    main()
