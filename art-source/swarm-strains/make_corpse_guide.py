"""Corpse guides for the Brute and Spitter, posed from their accepted live layers.

A corpse is the same creature fallen, so its guide is not drawn but assembled:
the reskinned body, head, claws and feet (the Spitter borrows the Runner's,
exactly as it does alive) are laid out collapsed in four poses on a 2x2 grid of
512 px panels. ImageGen then turns the assembly into a corpse; ``export.py``
re-cuts the panels into the horizontal strip ``SpriteSheetSlicer`` reads.

Run from the repository root after ``export.py`` has accepted the live raws:
``python art-source/swarm-strains/make_corpse_guide.py``.
"""

import sys
from pathlib import Path

from PIL import Image, ImageEnhance

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from export import fitted  # noqa: E402

PANEL = 512
CANVAS = PANEL * 2
GUIDE_SCALE = 6  # make_guide.SCALE: guide pixels per runtime pixel
POSE_SCALE = 1.8  # runtime pixels to corpse-guide pixels

# Per pose: body angle, head (offset along the body's forward axis, extra turn),
# each claw (side, angle off the body), each foot (offset x, offset y, angle).
POSES = [
    dict(body=-18, head=(0.52, 34), claws=((-1, -70), (1, 40)),
         feet=((-0.25, 0.55, 150), (0.3, 0.6, 205))),
    dict(body=62, head=(0.48, -28), claws=((-1, 20), (1, 105)),
         feet=((-0.3, 0.5, 190), (0.2, 0.62, 140))),
    dict(body=168, head=(0.55, 22), claws=((-1, -110), (1, -15)),
         feet=((-0.2, 0.6, 165), (0.32, 0.5, 230))),
    dict(body=-112, head=(0.5, -40), claws=((-1, 60), (1, 15)),
         feet=((-0.3, 0.58, 200), (0.25, 0.55, 130))),
]


def part(raw: Path, guide: Path, window: tuple[int, int]) -> Image.Image:
    """A strain part from its fitted raw, cut to its window, at corpse scale."""
    image = fitted(Image.open(raw).convert("RGBA").resize((1024, 1024)),
                   Image.open(guide).convert("RGBA"))
    w, h = window
    box = (512 - w * GUIDE_SCALE // 2, 512 - h * GUIDE_SCALE // 2,
           512 + w * GUIDE_SCALE // 2, 512 + h * GUIDE_SCALE // 2)
    image = image.crop(box)
    return image.resize((round(w * POSE_SCALE), round(h * POSE_SCALE)),
                        Image.Resampling.LANCZOS)


def runner_part(source: Path, width: int = 0, height: int = 0) -> Image.Image:
    """A Runner layer from its retained source, normalised as build_assets.py does."""
    image = Image.open(source).convert("RGBA")
    image = image.crop(image.getchannel("A").getbbox())
    if width:
        size = (round(width * POSE_SCALE),
                round(image.height * width * POSE_SCALE / image.width))
    else:
        size = (round(image.width * height * POSE_SCALE / image.height),
                round(height * POSE_SCALE))
    return image.resize(size, Image.Resampling.LANCZOS)


def place(canvas: Image.Image, sprite: Image.Image, cx: float, cy: float,
          angle: float, pivot=(0.5, 0.5)) -> None:
    """Rotates ``sprite`` by ``angle`` degrees clockwise about its pivot onto (cx, cy)."""
    pad = int(max(sprite.size) * 1.6)
    layer = Image.new("RGBA", (pad, pad), (0, 0, 0, 0))
    px, py = sprite.width * pivot[0], sprite.height * pivot[1]
    layer.alpha_composite(sprite, (round(pad / 2 - px), round(pad / 2 - py)))
    layer = layer.rotate(-angle, resample=Image.Resampling.BICUBIC)
    canvas.alpha_composite(layer, (round(cx - pad / 2), round(cy - pad / 2)))


def rotate(dx: float, dy: float, angle: float) -> tuple[float, float]:
    import math
    a = math.radians(angle)
    return dx * math.cos(a) - dy * math.sin(a), dx * math.sin(a) + dy * math.cos(a)


def corpse(body, head, claw, foot, body_px: float) -> Image.Image:
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    for i, pose in enumerate(POSES):
        ox = PANEL * (i % 2) + PANEL / 2
        oy = PANEL * (i // 2) + PANEL / 2
        angle = pose["body"]
        for fx, fy, fa in pose["feet"]:
            dx, dy = rotate(fx * body_px, fy * body_px, angle)
            place(canvas, foot, ox + dx, oy + dy, angle + fa)
        for side, ca in pose["claws"]:
            dx, dy = rotate(side * 0.36 * body_px, -0.05 * body_px, angle)
            place(canvas, claw, ox + dx, oy + dy, angle + ca, pivot=(0.5, 0.92))
        place(canvas, body, ox, oy, angle)
        forward, turn = pose["head"]
        dx, dy = rotate(0, -forward * body_px, angle)
        place(canvas, head, ox + dx, oy + dy, angle + turn)
    # Fallen: a touch darker and flatter than the living layers.
    rgb = ImageEnhance.Brightness(canvas).enhance(0.82)
    rgb.putalpha(canvas.getchannel("A"))
    return rgb


def main() -> None:
    raw = HERE / "raw"
    templates = HERE.parent / "templates"
    runner = HERE.parent / "alien-modular-topdown" / "sources"

    def strain(name, window):
        return part(raw / f"{name}.raw.png", templates / f"swarm-{name}-guide.png", window)

    brute = corpse(strain("brute-body", (150, 110)), strain("brute-head", (72, 92)),
                   strain("brute-fore-claw", (64, 96)), strain("brute-foot", (38, 60)),
                   150 * POSE_SCALE)
    brute.save(templates / "swarm-brute-corpse-guide.png", optimize=True)

    spitter = corpse(strain("spitter-body", (150, 150)), strain("spitter-head", (72, 116)),
                     runner_part(runner / "alien-fore-claw-source.png", height=96),
                     runner_part(runner / "alien-foot-source.png", width=38),
                     150 * POSE_SCALE)
    spitter.save(templates / "swarm-spitter-corpse-guide.png", optimize=True)


if __name__ == "__main__":
    main()
