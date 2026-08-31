"""Derive the shipped drop-shadow blob.

A body's shadow is one soft ellipse, so it needs one soft ellipse to draw with.
This exists because the layer spent its whole life borrowing
`graphics/fx/engineglow32.png` on the reasoning that a radial falloff is a
radial falloff whatever it was drawn for -- and that sprite is not a radial
falloff. It is a four-lobed flare: a bright cross with concave notches bitten
out of its corners. Stretched down-sun into an ellipse those notches become a
chevron, which is what every marine on the field was standing on, and which
read from outside as the shadow being clipped or as several shadows stacked.

So: an actual disc, generated rather than drawn, because what it has to be is a
falloff curve and a curve is better written than painted.

The curve is the decision here. A linear ramp reads as a flat grey disc with a
visible rim; a Gaussian never quite reaches zero, so at the alpha a shadow wants
it leaves a faint square of haze around the sprite. This uses smoothstep on the
normalised radius, which is zero at the rim with a zero derivative -- no rim,
no haze -- raised to a power that keeps the interior dense so the middle still
reads as opaque ground rather than as a soft cloud.

White with the shape in the alpha, so the draw tint alone sets the colour. That
keeps the sun's shade in `SunLight` where the terrain composite reads it from,
rather than baking a grey in here where nothing could tune it.

Run: python art-source/fx/build_shadow_blob.py
"""

from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SHIPPED = ROOT / "mod" / "graphics" / "battle" / "fx" / "shadow-blob.png"

"""Generous for a sprite drawn at roughly a cell across: the falloff is most of
the image, and a coarse one bands visibly when a mech stretches it wide."""
SIZE = 64

"""How much of the radius stays at full alpha before the falloff starts. A
shadow has a solid middle; without a core the whole blob is falloff and reads
as fog."""
CORE = 0.30

"""Applied to the smoothstep. Above 1 it holds the interior dense and spends the
falloff near the rim, which is what a contact shadow looks like from above."""
DENSITY = 1.45


def smoothstep(t: float) -> float:
    """Hermite ease with zero slope at both ends -- the reason there is no rim."""
    t = min(1.0, max(0.0, t))
    return t * t * (3.0 - 2.0 * t)


def build() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (255, 255, 255, 0))
    pixels = image.load()
    centre = (SIZE - 1) / 2.0
    # Half a pixel short of the edge so the outermost ring is exactly zero and
    # the sprite has no hard boundary to alias against when it is rotated.
    limit = centre + 0.5
    for y in range(SIZE):
        for x in range(SIZE):
            dx = (x - centre) / limit
            dy = (y - centre) / limit
            radius = (dx * dx + dy * dy) ** 0.5
            if radius >= 1.0:
                continue
            if radius <= CORE:
                alpha = 1.0
            else:
                alpha = smoothstep(1.0 - (radius - CORE) / (1.0 - CORE)) ** DENSITY
            pixels[x, y] = (255, 255, 255, int(round(alpha * 255)))
    return image


def main() -> None:
    SHIPPED.parent.mkdir(parents=True, exist_ok=True)
    build().save(SHIPPED)
    print(f"wrote {SHIPPED.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
