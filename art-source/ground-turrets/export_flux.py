"""Cut a style's flux hardware out of its raw (make_flux_guide.py) into the shipped mod art.

  mod/graphics/structures/ground-turrets/<style>/flux-bank.png      the banks cold, 3x3 cells at 128 px a cell
  mod/graphics/structures/ground-turrets/<style>/flux-bank-hot.png  the same banks at full charge, cut to the
                                                                    guide's emissive mask (fins and charge windows)

Each bank in each frame is registered to its own guide box's opaque extent (export.fitted), so neither the cold nor the hot frame
can move or resize the banks; the hot layer keeps only what the emissive mask lights, so a hot frame that drifted a
pixel cannot repaint the rails and cans.

Usage: python art-source/ground-turrets/export_flux.py <style> [<style> ...]
"""
import sys

from PIL import Image, ImageChops, ImageFilter

from export import HERE, ROOT, PPC, fitted, masked

FRAMES = {"cold": 0, "hot": 768}
# Each bank's own box within a frame (the west one and the east one, can and conduit included), so a generation that
# shifted one bank cannot drag the other with it.
BANKS = ((0, 200), (568, 768))


def frame(raw, guide, x0):
    """One frame, each bank registered to its own guide box."""
    out = Image.new("RGBA", (768, 768), (0, 0, 0, 0))
    for b0, b1 in BANKS:
        piece = fitted(raw, guide, (x0 + b0, 128, x0 + b1, 896))
        out.alpha_composite(piece, (b0, 0))
    return out


def export(style):
    guide = Image.open(HERE / "guides" / "flux-bank-guide.png")
    raw = masked(Image.open(HERE / "raw" / f"flux-bank-{style}.raw.png"), guide)
    size = 3 * PPC
    cold = frame(raw, guide, FRAMES["cold"]).resize((size, size), Image.LANCZOS)
    hot = frame(raw, guide, FRAMES["hot"]).resize((size, size), Image.LANCZOS)
    # The glow's reach: the fins and windows, widened a little for the light round them.
    emissive = Image.open(HERE / "guides" / "flux-bank-emissive.png").convert("L").resize((size, size), Image.LANCZOS)
    emissive = emissive.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(1.2))
    hot.putalpha(ImageChops.multiply(hot.getchannel("A"), emissive))
    target = ROOT / "mod" / "graphics" / "structures" / "ground-turrets" / style
    target.mkdir(parents=True, exist_ok=True)
    cold.save(target / "flux-bank.png")
    hot.save(target / "flux-bank-hot.png")
    print("exported", style, "->", target)


def main(argv):
    for style in argv:
        export(style)


if __name__ == "__main__":
    main(sys.argv[1:])
