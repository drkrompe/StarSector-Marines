"""Normalize retained ImageGen sources into runtime alien layers and a preview."""

from pathlib import Path

from PIL import Image


BODY_WIDTH = 150
HEAD_WIDTH = 72
FOOT_WIDTH = 38
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


def centered(canvas: Image.Image, sprite: Image.Image, center: tuple[int, int]) -> None:
    canvas.alpha_composite(sprite, (
        center[0] - sprite.width // 2,
        center[1] - sprite.height // 2,
    ))


def main() -> None:
    root = Path(__file__).resolve().parent
    sources = root / "sources"
    body = normalized(sources / "alien-body-source.png", BODY_WIDTH)
    head = normalized(sources / "alien-head-source.png", HEAD_WIDTH)
    foot = normalized(sources / "alien-foot-source.png", FOOT_WIDTH)

    body.save(root / "body.png", optimize=True)
    head.save(root / "head.png", optimize=True)
    foot.save(root / "foot.png", optimize=True)

    preview = Image.new("RGBA", (224, 224), (0, 0, 0, 0))
    centered(preview, foot, (95, 145))
    centered(preview, foot, (129, 145))
    centered(preview, body, (112, 130))
    centered(preview, head, (112, 100))
    preview_root = root.parents[3] / "build" / "sprite-previews" / "alien"
    preview_root.mkdir(parents=True, exist_ok=True)
    preview.save(preview_root / "alien-layered-idle.png", optimize=True)


if __name__ == "__main__":
    main()
