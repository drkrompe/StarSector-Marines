"""Pack the high-tech mech row's accepted bodies (owner pick: the heavy shell, <weight>/d-master.png) and their own foot
and thigh link into the Tri-Tachyon livery's runtime sprites, in place of the repaint of the shared outlines it wore.

The row is a concept on today's mounts: the light body sits where the Hound's chassis does, the medium where the
Warden's does and the heavy where the Bulwark's does, because the guides (guide_v2.py, legs_guide.py) were drawn round
those places. Each master is cropped to its content and fitted to its runtime size as the low-tech bodies are
(sources/lowtech/build.py): the chassis filling the 208 px square's height, north up; the foot the shared foot's
44 x 38; the thigh link the shared link's 40 x 112, hip end down.

The weapon casings are derived from these bodies' paint afterwards (derive_faction_weapons.py, OWN_BODIES).

Run from the repository root through build_assets.py, or alone: python art-source/mech-modular-topdown/sources/hightech/build.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parents[3]
MECHS = REPOSITORY / "mod" / "graphics" / "battle" / "mech-modular-topdown"
LIVERY = "tri-tachyon"

MASTERS = {
    "chassis.png": ("heavy/d-master.png", (208, 208)),
    "chassis-hound.png": ("light/d-master.png", (208, 208)),
    "chassis-warden.png": ("medium/d-master.png", (208, 208)),
    "foot.png": ("foot/foot-master.png", (44, 38)),
    "thigh-bone.png": ("thigh/thigh-master.png", (40, 112)),
}


def fitted(master: Path, size: tuple[int, int]) -> Image.Image:
    image = Image.open(master).convert("RGBA")
    bbox = image.getchannel("A").point(lambda a: 255 if a >= 24 else 0).getbbox()
    if bbox is None:
        raise ValueError(f"{master} has no visible pixels")
    image = image.crop(bbox)
    image.thumbnail(size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", size)
    canvas.alpha_composite(image, ((size[0] - image.width) // 2, (size[1] - image.height) // 2))
    return canvas


def build() -> None:
    root = MECHS / "factions" / LIVERY
    root.mkdir(parents=True, exist_ok=True)
    for output, (master, size) in MASTERS.items():
        fitted(HERE / master, size).save(root / output)


if __name__ == "__main__":
    build()
    print(f"wrote {', '.join(MASTERS)} for {LIVERY}")
