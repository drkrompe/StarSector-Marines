"""Spitter concept rounds: numbered ImageGen plates, each beside the same design at 32 and
17.1 px per cell, standing behind a Runner with a Marine ahead of both, live unit shadows.

  python spitter_round.py <round dir> <render root> <out.png> "<title>" [names...]

<render root> is the build/reports folder holding the colony-breach close-ups round2.py
describes (rav-zoom, rav-zoom32-marines, rav-zoom32-ground). The Runner is the chosen
design's hero plate (motion-runner/raw/hero.png). A Spitter draws at 1.1x the Runner's
linear size (UnitType.SWARM_SPITTER's sprite scale against SWARM_RUNNER's), so its opaque
area is 1.21x a Runner's.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from boards import font
from motion import marine_cutouts
from round2 import ACC, BG, DIM, INK, PPC17, PPC32, cut_marines, frames, ground_tiles, place
from round_sheet import RUNNER_AREA_PX, key_plate

HERE = Path(__file__).parent
RUNNER = HERE / "motion-runner" / "raw" / "hero.png"
SPITTER_AREA = 1.21


def at_zoom(cut, ppc, area=1.0):
    a = np.asarray(cut.getchannel("A")) > 128
    k = (area * RUNNER_AREA_PX * (ppc / PPC17) ** 2 / max(1, a.sum())) ** 0.5
    size = (max(1, round(cut.width * k)), max(1, round(cut.height * k)))
    return cut.convert("RGBa").resize(size, Image.LANCZOS).convert("RGBA")


def facing_right(cut):
    return cut.rotate(-90, expand=True, resample=Image.BICUBIC)


def concepts(round_dir, nums=None):
    raws = sorted((p for p in Path(round_dir, "raw").glob("c*.png") if p.stem[1:].isdigit()),
                  key=lambda p: int(p.stem[1:]))
    return [p for p in raws if not nums or int(p.stem[1:]) in nums]


def sheet(round_dir, root, out, title, names, nums=None):
    raws = concepts(round_dir, nums)
    runner = facing_right(key_plate(RUNNER))
    m32 = cut_marines(root, "rav-zoom32-marines", PPC32)
    m17 = marine_cutouts(str(frames(root, "rav-zoom")[1]))
    pav32 = ground_tiles(root, (270, 170))[0][1]
    f17 = Image.open(frames(root, "rav-zoom")[0]).convert("RGBA")
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 10), title, font=font(24, True), fill=INK)
    d.text((24, 40), "plate | 32 px per cell on paving: Spitter (left) behind a Runner, a Marine ahead, live unit shadows "
                     "| 17.1 px per cell 1:1 and x2 below", font=font(14), fill=DIM)
    cw, ch = 472, 500
    for i, p in enumerate(raws):
        col, row = i % 4, i // 4
        x0, y0 = 12 + col * (cw + 6), 66 + row * (ch + 6)
        d.rectangle([x0, y0, x0 + cw, y0 + ch], fill=(24, 28, 36))
        num = int(p.stem[1:])
        d.text((x0 + 10, y0 + 6), f"#{num}", font=font(26, True), fill=ACC)
        if num - 1 < len(names):
            d.text((x0 + 58, y0 + 12), names[num - 1], font=font(16, True), fill=INK)
        cut = key_plate(p)
        k = min(180 / cut.width, 440 / cut.height)
        pl = cut.convert("RGBa").resize((int(cut.width * k), int(cut.height * k)), Image.LANCZOS).convert("RGBA")
        pb = Image.new("RGBA", (180, 444), (180, 180, 180, 255))
        pb.alpha_composite(pl, ((180 - pl.width) // 2, (444 - pl.height) // 2))
        img.paste(pb.convert("RGB"), (x0 + 8, y0 + 46))
        rot = facing_right(cut)
        tx = x0 + 196
        t32 = pav32.copy()
        place(t32, at_zoom(rot, PPC32, SPITTER_AREA), 55, 85, PPC32)
        place(t32, at_zoom(runner, PPC32), 145, 85, PPC32)
        place(t32, m32[i % len(m32)], 230, 85, PPC32)
        img.paste(t32.convert("RGB"), (tx, y0 + 46))
        d.text((tx, y0 + 220), "32 px/cell:   spitter   |   runner   |   marine", font=font(13), fill=DIM)
        t17 = f17.crop((700 + i * 24, 995, 700 + i * 24 + 135, 1055)).copy()
        place(t17, at_zoom(rot, PPC17, SPITTER_AREA), 28, 30, PPC17)
        place(t17, at_zoom(runner, PPC17), 72, 30, PPC17)
        place(t17, m17[i % len(m17)], 112, 30, PPC17)
        img.paste(t17.convert("RGB"), (tx, y0 + 246))
        d.text((tx + 145, y0 + 252), "17.1 px/cell 1:1", font=font(13), fill=DIM)
        d.text((tx + 145, y0 + 270), "and x2 below", font=font(13), fill=DIM)
        img.paste(t17.resize((270, 120), Image.NEAREST).convert("RGB"), (tx, y0 + 318))
    img.save(out)



PACK = [(0.62, 0.22, 0), (0.70, 0.50, 0), (0.60, 0.78, 0), (0.78, 0.36, 0), (0.80, 0.66, 0),
        (0.30, 0.34, 1), (0.24, 0.70, 1)]  # (x, y, 1 = spitter) as fractions of the tile


def pack_sheet(round_dir, root, out, title, names, nums=None):
    """Each concept in a pack at 32 px per cell: five Runners ahead, two Spitters behind, Marines to
    the east, on paving and on grass. The question is whether the ranged ones are found at a glance."""
    raws = concepts(round_dir, nums)
    runner = at_zoom(facing_right(key_plate(RUNNER)), PPC32)
    m32 = cut_marines(root, "rav-zoom32-marines", PPC32)
    tiles = ground_tiles(root, (466, 206))
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 10), title, font=font(24, True), fill=INK)
    d.text((24, 40), "32 px per cell, live unit shadows: five Runners ahead, two Spitters behind them, Marines to the "
                     "east. Left: paving. Right: grass.", font=font(14), fill=DIM)
    for i, p in enumerate(raws):
        col, row = i % 2, i // 2
        x0, y0 = 12 + col * 954, 66 + row * 252
        num = int(p.stem[1:])
        sp = at_zoom(facing_right(key_plate(p)), PPC32, SPITTER_AREA)
        for j, (gname, g) in enumerate((tiles[0], tiles[1])):
            t = g.copy()
            for k, (fx, fy, is_sp) in enumerate(PACK):
                place(t, sp if is_sp else runner, fx * t.width, fy * t.height, PPC32)
            for k, fy in enumerate((0.3, 0.68)):
                place(t, m32[(i + k) % len(m32)], 0.95 * t.width, fy * t.height, PPC32)
            img.paste(t.convert("RGB"), (x0 + j * 472, y0 + 34))
        d.text((x0 + 4, y0 + 4), f"#{num}", font=font(22, True), fill=ACC)
        if num - 1 < len(names):
            d.text((x0 + 50, y0 + 8), names[num - 1], font=font(15, True), fill=INK)
    img.save(out)


if __name__ == "__main__":
    fn = pack_sheet if "--pack" in sys.argv else sheet
    a = [x for x in sys.argv[1:] if x != "--pack"]
    fn(a[0], a[1], a[2], a[3], a[4:])
