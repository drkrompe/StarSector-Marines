"""Cut a family's flux device out of its raw (make_flux_device_guide.py) into the shipped mod art.

  mod/graphics/structures/ground-turrets/<family>/flux-device.png      the device cold, 3x3 cells at 128 px a cell
  mod/graphics/structures/ground-turrets/<family>/flux-device-hot.png  the same device at full charge, cut to the
                                                                       guide's emissive mask (fins and core window)

Each frame is registered to its guide box's opaque extent (export.fitted), so neither the cold nor the hot frame can
move or resize the device; the hot layer keeps only what the emissive mask lights, so a hot frame that drifted a pixel
cannot repaint the housing or light the lamp sockets.

Usage: python art-source/ground-turrets/export_flux_device.py [<family> ...]   (brutalist, modular, composite)
"""
import sys

from PIL import Image, ImageChops, ImageFilter

from export import HERE, ROOT, PPC, fitted, masked
from make_flux_device_guide import FAMILIES

FRAMES = {"cold": 0, "hot": 768}


def export(family):
    guide = Image.open(HERE / "guides" / f"flux-device-{family}-guide.png")
    raw = masked(Image.open(HERE / "raw" / f"flux-device-{family}.raw.png"), guide)
    size = 3 * PPC
    cold, hot = (fitted(raw, guide, (x0, 128, x0 + 768, 896)).resize((size, size), Image.LANCZOS)
                 for x0 in (FRAMES["cold"], FRAMES["hot"]))
    # The glow's reach: the fins and core window, widened a little for the light round them.
    emissive = Image.open(HERE / "guides" / f"flux-device-{family}-emissive.png").convert("L")
    emissive = emissive.resize((size, size), Image.LANCZOS).filter(ImageFilter.MaxFilter(3)).filter(
        ImageFilter.GaussianBlur(1.2))
    hot.putalpha(ImageChops.multiply(hot.getchannel("A"), emissive))
    target = ROOT / "mod" / "graphics" / "structures" / "ground-turrets" / family
    target.mkdir(parents=True, exist_ok=True)
    cold.save(target / "flux-device.png")
    hot.save(target / "flux-device-hot.png")
    print("exported", family, "->", target)


def main(argv):
    for family in argv or FAMILIES:
        export(family)


if __name__ == "__main__":
    main(sys.argv[1:])
