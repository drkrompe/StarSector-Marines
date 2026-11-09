"""Pack the low-tech mech family (owner: the narrow-strider Hound with the faceted canopy, and the Warden, Bulwark and
foot drawn to match) into the runtime sprites of the liveries that wear it: the Hegemony's masters, and the
Independent and pirate paints of them, the same per-pixel recolour the Hegemony vehicle line wears
(art-source/vehicles/liveries/paint.py: blue hazard panels and greyer plate; red panels, darker plate, soot and chips).

Unlike the other liveries, which repaint the shared outlines under the base sprites' exact alpha
(build_assets.normalize alpha_from), these are bodies of their own: each master is cropped to its content and fitted to
the 208 px chassis square, filling its height as every chassis does, north up. The foot is fitted to the shared foot's
44 x 38. Every mount stays where the composer puts it, because the guides (family_guide.py, hound/guide.py) were drawn
round those places. The thigh link is the shared one with its olive plate turned to the Hegemony bodies' tan
(derive_faction_weapons.recolor), so the legs match the body.

The weapon casings are derived from these bodies' paint afterwards (derive_faction_weapons.py, OWN_BODIES).

Run from the repository root through build_assets.py, or alone: python art-source/mech-modular-topdown/sources/lowtech/build.py
"""
import importlib.util
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parents[3]
MECHS = REPOSITORY / "mod" / "graphics" / "battle" / "mech-modular-topdown"
sys.path.insert(0, str(HERE.parents[1]))
from derive_faction_weapons import base_paint_pixel, palette, recolor  # noqa: E402


def _vehicle_paint():
    spec = importlib.util.spec_from_file_location("vehicle_liveries_paint",
                                                  REPOSITORY / "art-source" / "vehicles" / "liveries" / "paint.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.paint


# The liveries that wear the family: the masters, then each paint of them with its fixed noise seed.
MASTER_LIVERY = "hegemony"
PAINTED = {"independent": 2101, "pirates": 2203}
LIVERIES = (MASTER_LIVERY, *PAINTED)

# Each runtime file's master.
MASTERS = {
    "chassis.png": ("bulwark/bulwark-master.png", (208, 208)),
    "chassis-hound.png": ("hound/canopy-1-master.png", (208, 208)),
    "chassis-warden.png": ("warden/warden-master.png", (208, 208)),
    "foot.png": ("foot/foot-master.png", (44, 38)),
}
THIGH = "thigh-bone.png"


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


def tan_thigh() -> Image.Image:
    """The shared thigh link with its olive plate in the Hegemony bodies' tan; steel, lamps and outline kept."""
    base = Image.open(MECHS / THIGH).convert("RGBA")
    mask = Image.new("L", base.size)
    mask.putdata([255 if base_paint_pixel(p[:3], p[3]) else 0 for p in base.getdata()])
    return recolor(base, mask, palette(MASTER_LIVERY))


def build() -> None:
    master_root = MECHS / "factions" / MASTER_LIVERY
    master_root.mkdir(parents=True, exist_ok=True)
    for output, (master, size) in MASTERS.items():
        fitted(HERE / master, size).save(master_root / output)
    tan_thigh().save(master_root / THIGH)
    paint = _vehicle_paint()
    for livery, seed in PAINTED.items():
        root = MECHS / "factions" / livery
        root.mkdir(parents=True, exist_ok=True)
        for i, output in enumerate((*MASTERS, THIGH)):
            painted = paint(Image.open(master_root / output), livery, seed + i)
            painted.save(root / output)


if __name__ == "__main__":
    build()
    print(f"wrote {', '.join((*MASTERS, THIGH))} for {', '.join(LIVERIES)}")
