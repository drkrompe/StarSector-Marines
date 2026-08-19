"""Derive horizontal/vertical residential frames from transparent ImageGen masters."""

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


def derive(master_name: str, output_stem: str) -> None:
    horizontal = content(Image.open(MASTERS / master_name).convert("RGBA"))
    save(horizontal, f"{output_stem}-h.png")
    save(horizontal.transpose(Image.Transpose.ROTATE_90), f"{output_stem}-v.png")


def main() -> None:
    SOURCES.mkdir(parents=True, exist_ok=True)
    derive("residential-bed.png", "residential-bed")
    derive("residential-sofa.png", "residential-sofa")
    derive("residential-planter.png", "residential-planter")


if __name__ == "__main__":
    main()
