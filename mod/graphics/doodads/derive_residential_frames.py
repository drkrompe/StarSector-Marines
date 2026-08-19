"""Derive cardinal residential frames from transparent ImageGen masters."""

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


def derive_axis_pair(master_name: str, output_stem: str) -> None:
    horizontal = content(Image.open(MASTERS / master_name).convert("RGBA"))
    save(horizontal, f"{output_stem}-h.png")
    save(horizontal.transpose(Image.Transpose.ROTATE_90), f"{output_stem}-v.png")


def derive_bed() -> None:
    horizontal = content(Image.open(MASTERS / "residential-bed.png").convert("RGBA"))
    vertical = horizontal.transpose(Image.Transpose.ROTATE_90)
    save(horizontal, "residential-bed-h.png")  # head at west edge
    save(vertical, "residential-bed-v.png")  # head at south edge
    save(horizontal.transpose(Image.Transpose.ROTATE_180), "residential-bed-head-e.png")
    save(vertical.transpose(Image.Transpose.ROTATE_180), "residential-bed-head-n.png")


def derive_sofa() -> None:
    horizontal = content(Image.open(MASTERS / "residential-sofa.png").convert("RGBA"))
    vertical = horizontal.transpose(Image.Transpose.ROTATE_90)
    save(horizontal, "residential-sofa-h.png")  # back at north edge
    save(vertical, "residential-sofa-v.png")  # back at west edge
    save(horizontal.transpose(Image.Transpose.ROTATE_180), "residential-sofa-back-s.png")
    save(vertical.transpose(Image.Transpose.ROTATE_180), "residential-sofa-back-e.png")


def main() -> None:
    SOURCES.mkdir(parents=True, exist_ok=True)
    derive_bed()
    derive_sofa()
    derive_axis_pair("residential-planter.png", "residential-planter")


if __name__ == "__main__":
    main()
