"""Normalize retained ImageGen sources into runtime alien layers and a preview."""

from pathlib import Path

from PIL import Image


BODY_WIDTH = 150
HEAD_WIDTH = 72
FOOT_WIDTH = 38
CLAW_HEIGHT = 96
ALPHA_FLOOR = 16


def normalized(source: Path, width: int) -> Image.Image:
    image = Image.open(source).convert("RGBA")
    alpha = image.getchannel("A").point(
        lambda value: 0 if value < ALPHA_FLOOR else value
    )
    image.putalpha(alpha)
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError(f"Expected non-empty source: {source}")
    image = image.crop(bounds)
    height = max(1, round(image.height * width / image.width))
    return image.resize((width, height), Image.Resampling.LANCZOS)


def normalized_height(source: Path, height: int) -> Image.Image:
    image = Image.open(source).convert("RGBA")
    alpha = image.getchannel("A").point(
        lambda value: 0 if value < ALPHA_FLOOR else value
    )
    image.putalpha(alpha)
    bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError(f"Expected non-empty source: {source}")
    image = image.crop(bounds)
    width = max(1, round(image.width * height / image.height))
    return image.resize((width, height), Image.Resampling.LANCZOS)


def centered(canvas: Image.Image, sprite: Image.Image, center: tuple[int, int]) -> None:
    canvas.alpha_composite(sprite, (
        center[0] - sprite.width // 2,
        center[1] - sprite.height // 2,
    ))


def pivoted(canvas: Image.Image, sprite: Image.Image,
            pivot: tuple[int, int], angle: float) -> None:
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    source_pivot = (sprite.width // 2, round(sprite.height * 0.92))
    layer.alpha_composite(sprite, (
        pivot[0] - source_pivot[0],
        pivot[1] - source_pivot[1],
    ))
    rotated = layer.rotate(angle, resample=Image.Resampling.BICUBIC,
                           center=pivot)
    canvas.alpha_composite(rotated)


def preview_pose(body: Image.Image, head: Image.Image, foot: Image.Image,
                 claw: Image.Image, swipe: float) -> Image.Image:
    preview = Image.new("RGBA", (224, 224), (0, 0, 0, 0))
    centered(preview, foot, (95, 145))
    centered(preview, foot, (129, 145))

    left_pivot = (round(112 - (45 - 10.5 * swipe)), round(127 - 30 * swipe))
    right_pivot = (157, 127)
    left_angle = 20 - 28 * swipe
    pivoted(preview, claw, right_pivot, -20)
    if swipe == 0:
        pivoted(preview, claw, left_pivot, left_angle)
    centered(preview, body, (112, 130))
    if swipe > 0:
        pivoted(preview, claw, left_pivot, left_angle)
    centered(preview, head, (112, 100))
    return preview


def main() -> None:
    root = Path(__file__).resolve().parent
    sources = root / "sources"
    body = normalized(sources / "alien-body-source.png", BODY_WIDTH)
    head = normalized(sources / "alien-head-source.png", HEAD_WIDTH)
    foot = normalized(sources / "alien-foot-source.png", FOOT_WIDTH)
    claw = normalized_height(sources / "alien-fore-claw-source.png", CLAW_HEIGHT)

    body.save(root / "body.png", optimize=True)
    head.save(root / "head.png", optimize=True)
    foot.save(root / "foot.png", optimize=True)
    claw.save(root / "fore-claw.png", optimize=True)

    preview_root = root.parents[3] / "build" / "sprite-previews" / "alien"
    preview_root.mkdir(parents=True, exist_ok=True)
    preview_pose(body, head, foot, claw, 0).save(
        preview_root / "alien-layered-idle.png", optimize=True)
    preview_pose(body, head, foot, claw, 1).save(
        preview_root / "alien-layered-swipe-impact.png", optimize=True)
    preview_pose(body, head, foot, claw, 0.5).save(
        preview_root / "alien-layered-swipe-retract.png", optimize=True)


if __name__ == "__main__":
    main()
