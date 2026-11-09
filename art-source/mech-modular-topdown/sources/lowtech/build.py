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

import numpy as np
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


def _noise(shape, cell_y, cell_x, seed):
    """Smooth value noise in [0, 1] on cells cell_y tall and cell_x wide: tall cells make streaks."""
    rng = np.random.default_rng(seed)
    gh, gw = shape[0] // cell_y + 2, shape[1] // cell_x + 2
    grid = Image.fromarray((rng.random((gh, gw)) * 255).astype(np.uint8))
    big = grid.resize((gw * cell_x, gh * cell_y), Image.Resampling.BICUBIC)
    return np.asarray(big, dtype=float)[: shape[0], : shape[1]] / 255.0


def _band(x, lo, hi):
    t = np.clip((x - lo) / (hi - lo), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def pirate_grime(image: Image.Image, seed: int) -> Image.Image:
    """The pirates' wear at a mech's size (owner: the pirate paint read too clean). The vehicle paint dirties only the
    tan plate and its marks are sized for a 3000 px tank sheet, so on a 208 px chassis the olive faces stay clean and
    the soot is a faint wash. Over every opaque pixel but the dark glass and hardware: heavy soot, rust weeping down from
    the seams, a few scorch burns and plenty of bare-steel chips, all from fixed seeds."""
    rgba = np.asarray(image.convert("RGBA"), dtype=float) / 255.0
    rgb, alpha = rgba[..., :3], rgba[..., 3:]
    shape = rgb.shape[:2]
    scale = max(1, shape[1] // 52)
    value = rgb.max(-1)
    painted = (_band(alpha[..., 0], 0.5, 0.9) * _band(value, 0.16, 0.30))[..., None]
    soot = _band(_noise(shape, 5 * scale, 5 * scale, seed), 0.42, 0.80)[..., None]
    out = rgb * (1 - 0.48 * soot * painted)
    streak = _noise(shape, 9 * scale, max(1, scale // 2), seed + 1) * _noise(shape, 4 * scale, 3 * scale, seed + 2)
    rust = _band(streak, 0.30, 0.48)[..., None] * painted
    out = out * (1 - 0.62 * rust) + np.array([0.36, 0.16, 0.06]) * 0.62 * rust
    burn = _band(_noise(shape, 11 * scale, 11 * scale, seed + 3), 0.74, 0.92)[..., None] * painted
    out = out * (1 - 0.70 * burn) + np.array([0.06, 0.05, 0.04]) * 0.70 * burn
    chips = _band(_noise(shape, 1, 1, seed + 4) * _noise(shape, 4 * scale, 4 * scale, seed + 5), 0.42, 0.50)[..., None]
    bare = np.array([0.42, 0.41, 0.39]) * (0.55 + 0.6 * value[..., None])
    out = out * (1 - 0.85 * chips * painted) + bare * 0.85 * chips * painted
    result = np.concatenate([np.clip(out, 0, 1), alpha], -1)
    return Image.fromarray((result * 255).round().astype(np.uint8))


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
            if livery == "pirates":
                painted = pirate_grime(painted, seed + 100 + i)
            painted.save(root / output)


if __name__ == "__main__":
    build()
    print(f"wrote {', '.join((*MASTERS, THIGH))} for {', '.join(LIVERIES)}")
