"""Derive six industrial fence frames from two transparent ImageGen masters."""

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
    content(image).save(destination, optimize=True)
    print(destination)


def main() -> None:
    SOURCES.mkdir(parents=True, exist_ok=True)
    corner = content(
        Image.open(MASTERS / "industrial-fence-corner-nw.png").convert("RGBA")
    )
    save(corner, "industrial-fence-corner-nw.png")
    save(corner.transpose(Image.Transpose.FLIP_LEFT_RIGHT),
         "industrial-fence-corner-ne.png")
    save(corner.transpose(Image.Transpose.FLIP_TOP_BOTTOM),
         "industrial-fence-corner-sw.png")
    save(corner.transpose(Image.Transpose.ROTATE_180),
         "industrial-fence-corner-se.png")

    straight = content(
        Image.open(MASTERS / "industrial-fence-straight.png").convert("RGBA")
    )
    # The master is a long multi-bay run. Retain its centered quarter as a
    # repeatable one-cell segment; the atlas normalizer handles the final fit.
    target_width = round(straight.width * 0.25)
    x0 = (straight.width - target_width) // 2
    horizontal = content(straight.crop((x0, 0, x0 + target_width, straight.height)))
    vertical = horizontal.transpose(Image.Transpose.ROTATE_90)
    save(horizontal, "industrial-fence-straight-h.png")
    save(vertical, "industrial-fence-straight-v.png")


if __name__ == "__main__":
    main()
