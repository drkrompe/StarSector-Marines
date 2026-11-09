"""Pack the low-tech Hegemony mech family (owner: the narrow-strider Hound with the faceted canopy, and the Warden,
Bulwark and foot drawn to match) into the Hegemony livery's runtime sprites.

Unlike the other liveries, which repaint the shared outlines under the base sprites' exact alpha
(build_assets.normalize alpha_from), these are bodies of their own: each master is cropped to its content and fitted to
the 208 px chassis square, filling its height as every chassis does, north up. The foot is fitted to the shared foot's
44 x 38. Every mount stays where the composer puts it, because the guides (family_guide.py, hound/guide.py) were drawn
round those places.

The Hegemony weapon casings are derived from these bodies' paint afterwards (derive_faction_weapons.py).

Run from the repository root through build_assets.py, or alone: python art-source/mech-modular-topdown/sources/lowtech/build.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parents[3]
OUTPUT = REPOSITORY / "mod" / "graphics" / "battle" / "mech-modular-topdown" / "factions" / "hegemony"

# The livery whose bodies are its own, and each runtime file's master.
FACTION = "hegemony"
MASTERS = {
    "chassis.png": ("bulwark/bulwark-master.png", (208, 208)),
    "chassis-hound.png": ("hound/canopy-1-master.png", (208, 208)),
    "chassis-warden.png": ("warden/warden-master.png", (208, 208)),
    "foot.png": ("foot/foot-master.png", (44, 38)),
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
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for output, (master, size) in MASTERS.items():
        fitted(HERE / master, size).save(OUTPUT / output)


if __name__ == "__main__":
    build()
    print(f"wrote {', '.join(MASTERS)} to {OUTPUT}")
