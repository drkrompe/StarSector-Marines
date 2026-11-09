"""Geometry guides for the high-tech row's legs, to go with the owner's pick of the heavy-shell bodies (<weight>/d):
the shared foot pad and thigh link outlines (mod/graphics/battle/mech-modular-topdown/foot.png, thigh-bone.png) blown
up as flat grey silhouettes, so the generated foot and link keep the runtime registration exactly.

  foot/guide.png    foot.png's outline at 8x (the pad the composer stamps under each leg), toe to the top
  thigh/guide.png   thigh-bone.png's outline at 8x (the link stretched from the waist to the foot), hip end at the
                    bottom, foot end at the top

Run from anywhere: python art-source/mech-modular-topdown/sources/hightech/legs_guide.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
MECHS = HERE.parents[3] / "mod" / "graphics" / "battle" / "mech-modular-topdown"
GREY = (128, 128, 124)
SCALE = 8
CANVAS = 1024


def silhouette(name: str) -> Image.Image:
    source = Image.open(MECHS / name).convert("RGBA")
    alpha = source.getchannel("A").resize((source.width * SCALE, source.height * SCALE), Image.Resampling.LANCZOS)
    alpha = alpha.point(lambda v: 255 if v > 128 else 0)
    shape = Image.new("RGBA", alpha.size, (*GREY, 255))
    shape.putalpha(alpha)
    canvas = Image.new("RGBA", (CANVAS, CANVAS))
    canvas.alpha_composite(shape, ((CANVAS - shape.width) // 2, (CANVAS - shape.height) // 2))
    return canvas


def main() -> None:
    for folder, name in (("foot", "foot.png"), ("thigh", "thigh-bone.png")):
        (HERE / folder).mkdir(exist_ok=True)
        silhouette(name).save(HERE / folder / "guide.png")
    print("wrote foot/guide.png and thigh/guide.png")


if __name__ == "__main__":
    main()
