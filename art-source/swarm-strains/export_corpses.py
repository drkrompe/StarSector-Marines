"""Cuts the corpse raws into the horizontal sheets the corpse sweep reads.

Each raw holds four corpses on a 2x2 grid. A quarter is cut whole, cropped to
its own painted extent, scaled so the longest corpse side is ``FRAME_LONG``
pixels, and the four are laid left to right with ``GAP`` transparent columns
between them, which is what ``SpriteSheetSlicer`` splits a strip on. The
renderer fits each frame's longest side to the body's size, so the absolute
pixel scale only sets resolution.

Run from the repository root: ``python art-source/swarm-strains/export_corpses.py``.
"""

from pathlib import Path

from PIL import Image

ALPHA_FLOOR = 16
FRAME_LONG = 200
GAP = 16
PAD = 4

TARGETS = {
    "brute-corpse": "swarm-brute-dead.png",
    "spitter-corpse": "swarm-spitter-dead.png",
}


def main() -> None:
    here = Path(__file__).resolve().parent
    graphics = here.parent.parent / "mod" / "graphics" / "battle"
    for part, target in TARGETS.items():
        raw_path = here / "raw" / f"{part}.raw.png"
        if not raw_path.exists():
            print(f"{part}: no raw yet, skipped")
            continue
        raw = Image.open(raw_path).convert("RGBA")
        alpha = raw.getchannel("A").point(
            lambda value: 0 if value < ALPHA_FLOOR else value)
        raw.putalpha(alpha)
        hw, hh = raw.width // 2, raw.height // 2
        frames = []
        for i in range(4):
            x, y = (i % 2) * hw, (i // 2) * hh
            quarter = raw.crop((x, y, x + hw, y + hh))
            bounds = quarter.getchannel("A").getbbox()
            if bounds is None:
                raise ValueError(f"{part}: quarter {i} is empty")
            quarter = quarter.crop(bounds)
            scale = FRAME_LONG / max(quarter.size)
            frames.append(quarter.resize(
                (max(1, round(quarter.width * scale)),
                 max(1, round(quarter.height * scale))),
                Image.Resampling.LANCZOS))
        width = sum(f.width for f in frames) + GAP * (len(frames) - 1) + PAD * 2
        height = max(f.height for f in frames) + PAD * 2
        sheet = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        x = PAD
        for frame in frames:
            sheet.alpha_composite(frame, (x, PAD + (height - 2 * PAD - frame.height) // 2))
            x += frame.width + GAP
        out = graphics / target
        sheet.save(out, optimize=True)
        print(f"{part}: {len(frames)} frames -> {out.name} {sheet.size}")


if __name__ == "__main__":
    main()
