"""Derive the shipped running-integral-system FX textures from their masters.

The masters here are 1024px generated originals. The shipped textures are drawn
at roughly a third of a battle cell (the facet) and three quarters of one (the
emitter), so they ship at 128px: anything larger is an oversample nothing ever
sees, and the headless renderers decode every shipped texture into a
BufferedImage cache.

Two things happen besides the downscale, and both are decisions rather than
housekeeping:

* The facet's interior alpha is pulled down toward its rim. Many facets are laid
  along one arc and they overlap; at the master's interior opacity the overlap
  reads as a solid plate, which is the all-round-glow failure in a different
  costume. Keeping the rim and thinning the middle leaves the wearer visible
  through their own screen.
* The emitter master was generated pointing right. The renderer's sprite frame
  has zero degrees pointing up and hands it the wearer's facing unmodified, so
  the art is rotated a quarter turn here rather than corrected by a magic
  number at every draw site.

Run: python art-source/fx/build_fx.py
"""

from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
SHIPPED = HERE.parents[1] / "mod" / "graphics" / "fx"
EDGE = 128


def build_facet() -> None:
    master = Image.open(HERE / "screen-facet.raw.png").convert("RGBA")
    width, height = master.size
    alpha = master.getchannel("A").load()
    thinned = Image.new("L", master.size)
    target = thinned.load()
    cx, cy = (width - 1) / 2.0, (height - 1) / 2.0
    longest = max(cx, cy)
    for y in range(height):
        for x in range(width):
            source = alpha[x, y]
            if not source:
                continue
            radius = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 / longest
            # 0.30 in the middle rising to full at the rim.
            target[x, y] = int(source * (0.30 + 0.70 * min(1.0, radius) ** 1.5))
    master.putalpha(thinned)
    master.resize((EDGE, EDGE), Image.Resampling.LANCZOS).save(SHIPPED / "screen_facet.png")


def build_emitter() -> None:
    master = Image.open(HERE / "system-emitter.raw.png").convert("RGBA")
    master = master.transpose(Image.Transpose.ROTATE_90)
    master.resize((EDGE, EDGE), Image.Resampling.LANCZOS).save(SHIPPED / "system_emitter.png")


if __name__ == "__main__":
    SHIPPED.mkdir(parents=True, exist_ok=True)
    build_facet()
    build_emitter()
    print(f"wrote screen_facet.png and system_emitter.png to {SHIPPED}")
