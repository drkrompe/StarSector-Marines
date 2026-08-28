"""Derive the shipped smoke-field flipbook from the shared particle sheet.

A grenade screen and a burning wreck are the same substance, so the screen is
drawn from the same eight-frame puff the wrecks already play
(`mod/graphics/particle/smokeAndFire.png`, top two rows — the bottom two are
that sheet's fire frames) rather than from art of its own. The source is a
shipped asset rather than a master under `art-source/`, which is the one place
this directory's usual "derive from the raw original" shape does not apply:
there is no master, and matching the wreck plume exactly is the point.

Two things happen besides the crop, and both are decisions rather than
housekeeping:

* The greys are lifted toward white. The source puff sits at 102-153, which
  reads as the soot of something burning; a deployed screen is a dense white
  obscurant and has to read as deliberate equipment, not as damage. The lift is
  applied to the whole 5-step ramp at once, so the puff keeps its shading
  instead of flattening into a silhouette.
* The white is pulled slightly cool. A neutral lift on a mostly warm-lit
  battlefield reads as haze; the blue lean separates the screen from dust and
  from the fire frames it shares a source sheet with.

Draw tint stays in `marine-special-equipment.equipment.json`, so warmth and
density are tunable without re-running this. What cannot be done at draw time
is brightening: the renderer's tint multiplies, so it can only take the source
greys down.

Run: python art-source/fx/build_smoke_field.py
"""

from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = ROOT / "mod" / "graphics" / "particle" / "smokeAndFire.png"
SHIPPED = ROOT / "mod" / "graphics" / "battle" / "fx" / "smoke-field-sheet.png"

FRAME = 16
COLUMNS = 4
"""Smoke occupies the source sheet's first eight frames in image order."""
SMOKE_FRAMES = 8
"""Fraction of the distance from each source grey to white that is closed."""
LIFT = 0.58
"""Per-channel trim applied after the lift, giving the white a cool lean."""
COOL = (0.96, 0.99, 1.04)


def whiten(value: int, channel: int) -> int:
    lifted = 255 - (255 - value) * (1.0 - LIFT)
    return max(0, min(255, round(lifted * COOL[channel])))


def build() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    rows = (SMOKE_FRAMES + COLUMNS - 1) // COLUMNS
    sheet = Image.new("RGBA", (COLUMNS * FRAME, rows * FRAME), (255, 255, 255, 0))
    for index in range(SMOKE_FRAMES):
        col, row = index % COLUMNS, index // COLUMNS
        frame = source.crop((col * FRAME, row * FRAME,
                             col * FRAME + FRAME, row * FRAME + FRAME))
        pixels = frame.load()
        for y in range(FRAME):
            for x in range(FRAME):
                r, g, b, a = pixels[x, y]
                if not a:
                    continue
                pixels[x, y] = (whiten(r, 0), whiten(g, 1), whiten(b, 2), a)
        sheet.paste(frame, (col * FRAME, row * FRAME))
    SHIPPED.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(SHIPPED)
    print(f"wrote {SHIPPED.relative_to(ROOT)} ({sheet.width}x{sheet.height})")


if __name__ == "__main__":
    build()
