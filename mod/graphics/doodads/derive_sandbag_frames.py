"""Derive the eight defense-ring sandbag frames from two ImageGen masters."""

from pathlib import Path

from PIL import Image


HERE = Path(__file__).resolve().parent
MASTERS = HERE / "imagegen-masters"
SOURCES = HERE / "sources"


def content(image: Image.Image) -> Image.Image:
    bounds = image.getchannel("A").getbbox()
    if bounds is None:
        raise ValueError("Expected non-empty alpha content")
    return image.crop(bounds)


def save(image: Image.Image, name: str) -> None:
    destination = SOURCES / name
    image.save(destination, optimize=True)
    print(destination)


def main() -> None:
    SOURCES.mkdir(parents=True, exist_ok=True)
    corner = content(Image.open(MASTERS / "sandbag-corner-nw.png").convert("RGBA"))
    save(corner, "sandbag-corner-nw.png")
    save(corner.transpose(Image.Transpose.FLIP_LEFT_RIGHT), "sandbag-corner-ne.png")
    save(corner.transpose(Image.Transpose.FLIP_TOP_BOTTOM), "sandbag-corner-sw.png")
    save(corner.transpose(Image.Transpose.ROTATE_180), "sandbag-corner-se.png")

    straight = content(
        Image.open(MASTERS / "sandbag-straight-horizontal.png").convert("RGBA")
    )
    # ImageGen drew eight bag columns. The ring art wants approximately four,
    # matching either arm of the corner master. Retain the centered half with a
    # small seam allowance and let stitch_atlas perform the final 32px fit.
    target_width = round(straight.width * 0.52)
    x0 = (straight.width - target_width) // 2
    horizontal = content(straight.crop((x0, 0, x0 + target_width, straight.height)))
    vertical = horizontal.transpose(Image.Transpose.ROTATE_90)
    save(horizontal, "sandbag-straight-n.png")
    save(horizontal.copy(), "sandbag-straight-s.png")
    save(vertical, "sandbag-straight-e.png")
    save(vertical.copy(), "sandbag-straight-w.png")


if __name__ == "__main__":
    main()
