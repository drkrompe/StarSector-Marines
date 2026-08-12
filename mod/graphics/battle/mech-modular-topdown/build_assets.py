"""Normalize the retained ImageGen sources and render reference compositions.

The chassis is the visual mass. Every other sprite is intentionally drawn first
with its rear pivot buried beneath the chassis; only its functional tip remains
visible. Runtime dimensions use chassis width as their shared unit.
"""

from pathlib import Path
from PIL import Image


ROOT = Path(__file__).resolve().parent
SOURCES = ROOT / "sources"
PREVIEWS = ROOT.parents[3] / "build" / "sprite-previews" / "mech"


def content_crop(image: Image.Image, threshold: int = 24) -> Image.Image:
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A").point(lambda a: 255 if a >= threshold else 0)
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError("source has no visible pixels")
    return rgba.crop(bbox)


def normalize(source: str, output: str, size: tuple[int, int]) -> Image.Image:
    image = content_crop(Image.open(SOURCES / source))
    image.thumbnail(size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", size)
    canvas.alpha_composite(image, ((size[0] - image.width) // 2,
                                   (size[1] - image.height) // 2))
    canvas.save(ROOT / output)
    return canvas


def derive_foot_from_hull(size: tuple[int, int]) -> Image.Image:
    """Reuse the hull's small bottom-center armor tab as the hidden toe blob."""
    hull = Image.open(SOURCES / "hull.png").convert("RGBA")
    toe = content_crop(hull.crop((535, 918, 700, 1072)))
    toe.thumbnail(size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", size)
    canvas.alpha_composite(toe, ((size[0] - toe.width) // 2,
                                 (size[1] - toe.height) // 2))
    canvas.save(ROOT / "foot.png")
    return canvas


def paste_pivot(canvas: Image.Image, part: Image.Image,
                pivot_x: int, pivot_y: int, mirror: bool = False) -> None:
    sprite = part.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if mirror else part
    canvas.alpha_composite(sprite, (pivot_x - sprite.width // 2,
                                    pivot_y - sprite.height))


def preview(name: str, moving: bool, firing: bool,
            arm_name: str = "chaingun-arm.png") -> None:
    canvas = Image.new("RGBA", (384, 384), (30, 33, 30, 255))
    hull = Image.open(ROOT / "chassis.png").convert("RGBA")
    foot = Image.open(ROOT / "foot.png").convert("RGBA")
    arm = Image.open(ROOT / arm_name).convert("RGBA")
    srm = Image.open(ROOT / "srm-pod.png").convert("RGBA")
    lrm = Image.open(ROOT / "lrm-pod.png").convert("RGBA")

    # Feet and arm roots are deliberately under the hull. Movement reveals no
    # more than the toe; firing pulls one barrel back a few pixels.
    foot_y = 269 if moving else 259
    paste_pivot(canvas, foot, 157, foot_y)
    paste_pivot(canvas, foot, 227, 259, mirror=True)
    paste_pivot(canvas, arm, 115, 223 - (4 if firing else 0))
    paste_pivot(canvas, arm, 269, 223, mirror=True)

    canvas.alpha_composite(hull, (192 - hull.width // 2, 192 - hull.height // 2))

    # Pods are shoulder-top layers, but their long plain rear casings remain
    # buried. Only the barrel tops and slim muzzle edge break the hull outline.
    # Stock chassis uses the larger pod box on both shoulders. Weapon identity
    # remains per-slot runtime data; this is only the installed visual shell.
    paste_pivot(canvas, lrm, 144, 271)
    paste_pivot(canvas, lrm, 240, 271, mirror=True)
    canvas.save(PREVIEWS / name)


def main() -> None:
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    normalize("hull-clean-v2.png", "chassis.png", (208, 208))
    normalize("hull.png", "chassis-socketed-variant.png", (208, 208))
    derive_foot_from_hull((44, 38))
    # Heavy hardpoints are sized deliberately against the 208px chassis width:
    # arms ~= 27%, SRM ~= 30%, LRM ~= 36%. Rear pivots remain buried.
    normalize("chaingun-arm-v2.png", "chaingun-arm.png", (62, 112))
    normalize("linear-cannon-concept.png", "linear-cannon-variant.png", (58, 138))
    normalize("srm-pod.png", "srm-pod.png", (62, 88))
    normalize("lrm-pod.png", "lrm-pod.png", (76, 96))
    preview("idle.png", moving=False, firing=False)
    preview("moving-and-firing.png", moving=True, firing=True)


if __name__ == "__main__":
    main()
