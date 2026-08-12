"""Normalize ImageGen variant sources and render mix-and-match composition previews."""

from pathlib import Path

from PIL import Image


CANVAS_SIZE = 224
WORLD_PIVOT = (112, 112)
SHOULDER_WIDTH = 150


def sw(ratio: float) -> int:
    """Resolve an anatomical measurement expressed in shoulder-width units."""
    return round(SHOULDER_WIDTH * ratio)


BODY_WIDTH = sw(1.0)
HEAD_WIDTH = sw(0.48)
STANDARD_WEAPON_HEIGHT = sw(0.6533)
SMG_HEIGHT = sw(0.52)
DMR_HEIGHT = sw(0.82)
ROCKET_HEIGHT = sw(1.04)

BODY_CENTER_OFFSET = (sw(0.0), sw(0.12))
HEAD_CENTER_OFFSET = (sw(0.0), sw(-0.08))
STANDARD_WEAPON_OFFSET = (sw(0.1733), sw(-0.12))
ROCKET_WEAPON_OFFSET = (sw(0.3333), sw(-0.12))
STANDARD_WEAPON_PIVOT_Y = sw(0.4867)
ROCKET_WEAPON_PIVOT_Y = sw(0.56)


def content_crop(image: Image.Image) -> Image.Image:
    box = image.getchannel("A").getbbox()
    if box is None:
        raise ValueError("Expected non-empty variant source")
    return image.crop(box)


def normalize(source: Path, target_width: int) -> Image.Image:
    image = content_crop(Image.open(source).convert("RGBA"))
    scale = target_width / image.width
    return image.resize(
        (target_width, max(1, round(image.height * scale))),
        Image.Resampling.NEAREST,
    )


def normalize_height(source: Path, target_height: int) -> Image.Image:
    image = content_crop(Image.open(source).convert("RGBA"))
    scale = target_height / image.height
    return image.resize(
        (max(1, round(image.width * scale)), target_height),
        Image.Resampling.NEAREST,
    )


def place_centered(canvas: Image.Image, sprite: Image.Image,
                   center: tuple[int, int]) -> None:
    canvas.alpha_composite(sprite, (
        center[0] - sprite.width // 2,
        center[1] - sprite.height // 2,
    ))


def rotate_head(head: Image.Image, angle_degrees: float) -> Image.Image:
    return head.rotate(
        angle_degrees,
        resample=Image.Resampling.NEAREST,
        expand=False,
        center=(head.width // 2, head.height // 2),
    )


def compose(body: Image.Image, head: Image.Image, weapon: Image.Image,
            head_angle: float = 0.0,
            weapon_offset: tuple[int, int] = STANDARD_WEAPON_OFFSET,
            weapon_pivot: tuple[int, int] | None = None,
            weapon_layer: str = "under_body") -> Image.Image:
    canvas = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))

    if weapon_pivot is None:
        weapon_pivot = (weapon.width // 2, STANDARD_WEAPON_PIVOT_Y)
    weapon_position = (
        WORLD_PIVOT[0] + weapon_offset[0] - weapon_pivot[0],
        WORLD_PIVOT[1] + weapon_offset[1] - weapon_pivot[1],
    )

    if weapon_layer == "under_body":
        canvas.alpha_composite(weapon, weapon_position)
    elif weapon_layer != "over_shoulder":
        raise ValueError(f"Unknown weapon layer: {weapon_layer}")

    place_centered(canvas, body, (
        WORLD_PIVOT[0] + BODY_CENTER_OFFSET[0],
        WORLD_PIVOT[1] + BODY_CENTER_OFFSET[1],
    ))

    if weapon_layer == "over_shoulder":
        canvas.alpha_composite(weapon, weapon_position)

    place_centered(canvas, rotate_head(head, head_angle), (
        WORLD_PIVOT[0] + HEAD_CENTER_OFFSET[0],
        WORLD_PIVOT[1] + HEAD_CENTER_OFFSET[1],
    ))
    return canvas


def main() -> None:
    directory = Path(__file__).resolve().parent
    source_root = directory / "sources"

    families = {
        "armorless": ("armorless-body.png", "armorless-head.png"),
        "charcoal": ("charcoal-body.png", "charcoal-head.png"),
        "blue-scout": ("blue-scout-body.png", "blue-scout-head.png"),
        "red-heavy": ("red-heavy-body.png", "red-heavy-head.png"),
        "outlaw": ("outlaw-body.png", "outlaw-head.png"),
        "army-green": ("army-green-body.png", "army-green-head.png"),
        "militia": ("militia-body.png", "militia-head.png"),
    }
    built = {}
    for family, (body_source, head_source) in families.items():
        family_dir = directory / "armor" / family
        family_dir.mkdir(parents=True, exist_ok=True)
        body = normalize(source_root / body_source, BODY_WIDTH)
        head = normalize(source_root / head_source, HEAD_WIDTH)
        body.save(family_dir / "body.png", optimize=True)
        head.save(family_dir / "head.png", optimize=True)
        built[family] = (body, head)

    weapons_dir = directory / "weapons"
    weapons_dir.mkdir(parents=True, exist_ok=True)
    rifle = Image.open(directory.parent / "marine-rifle.png").convert("RGBA")
    rocket = normalize_height(source_root / "rocket-launcher.png", ROCKET_HEIGHT)
    laser = normalize_height(source_root / "laser-gun.png", STANDARD_WEAPON_HEIGHT)
    smg = normalize_height(source_root / "smg.png", SMG_HEIGHT)
    dmr = normalize_height(source_root / "dmr.png", DMR_HEIGHT)
    surplus_rifle = normalize_height(source_root / "rifle-surplus.png", STANDARD_WEAPON_HEIGHT)
    masterwork_dmr = normalize_height(source_root / "dmr-masterwork.png", DMR_HEIGHT)
    rifle.save(weapons_dir / "rifle.png", optimize=True)
    rocket.save(weapons_dir / "rocket-launcher.png", optimize=True)
    laser.save(weapons_dir / "laser-gun.png", optimize=True)
    smg.save(weapons_dir / "smg.png", optimize=True)
    dmr.save(weapons_dir / "dmr.png", optimize=True)
    grade_root = weapons_dir / "grades"
    (grade_root / "surplus").mkdir(parents=True, exist_ok=True)
    (grade_root / "masterwork").mkdir(parents=True, exist_ok=True)
    surplus_rifle.save(grade_root / "surplus" / "rifle.png", optimize=True)
    masterwork_dmr.save(grade_root / "masterwork" / "dmr.png", optimize=True)

    previews = directory / "previews"
    previews.mkdir(parents=True, exist_ok=True)
    rocket_pivot = (rocket.width // 2, ROCKET_WEAPON_PIVOT_Y)
    combinations = (
        ("charcoal-rifle.png", "charcoal", rifle, 0, STANDARD_WEAPON_OFFSET, None, "under_body"),
        ("blue-scout-laser-looking-left.png", "blue-scout", laser, 35, STANDARD_WEAPON_OFFSET, None, "under_body"),
        ("red-heavy-rocket-looking-right.png", "red-heavy", rocket, -35, ROCKET_WEAPON_OFFSET, rocket_pivot, "under_body"),
        ("red-heavy-rocket-firing-over-shoulder.png", "red-heavy", rocket, -35, ROCKET_WEAPON_OFFSET, rocket_pivot, "over_shoulder"),
        ("mixed-blue-body-red-head-rifle.png", "blue-scout", rifle, 20, STANDARD_WEAPON_OFFSET, None, "under_body"),
        ("outlaw-rifle-looking-left.png", "outlaw", rifle, 25, STANDARD_WEAPON_OFFSET, None, "under_body"),
        ("army-green-rocket.png", "army-green", rocket, 0, ROCKET_WEAPON_OFFSET, rocket_pivot, "under_body"),
        ("army-green-rocket-firing-over-shoulder.png", "army-green", rocket, 0, ROCKET_WEAPON_OFFSET, rocket_pivot, "over_shoulder"),
        ("militia-rifle-looking-right.png", "militia", rifle, -20, STANDARD_WEAPON_OFFSET, None, "under_body"),
        ("outlaw-smg.png", "outlaw", smg, 0, STANDARD_WEAPON_OFFSET,
         (smg.width // 2, round(smg.height * 0.75)), "under_body"),
        ("army-green-dmr.png", "army-green", dmr, 0, STANDARD_WEAPON_OFFSET,
         (dmr.width // 2, round(dmr.height * 0.75)), "under_body"),
        ("armorless-surplus-rifle.png", "armorless", surplus_rifle, 20,
         STANDARD_WEAPON_OFFSET,
         (surplus_rifle.width // 2, round(surplus_rifle.height * 0.75)),
         "under_body"),
        ("armorless-masterwork-dmr.png", "armorless", masterwork_dmr, -20,
         STANDARD_WEAPON_OFFSET,
         (masterwork_dmr.width // 2, round(masterwork_dmr.height * 0.75)),
         "under_body"),
    )
    for (filename, family, weapon, head_angle, weapon_offset, weapon_pivot,
         weapon_layer) in combinations:
        body, head = built[family]
        if filename.startswith("mixed-"):
            head = built["red-heavy"][1]
        compose(body, head, weapon, head_angle, weapon_offset, weapon_pivot,
                weapon_layer).save(
            previews / filename, optimize=True)


if __name__ == "__main__":
    main()
