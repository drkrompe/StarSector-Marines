"""Matriarch concept rounds: numbered ImageGen plates judged at play zoom beside the
rest of the family and a mech.

  python matriarch_sheet.py <round dir> <render root> <out dir> "<title>" [--linear=3.9] <names...>

Writes two 1920x1080 sheets into <out dir>:
  01-concepts.png  each plate beside the same design at 32 px per cell with a Bulwark,
                   the Brute, the Runner and a Marine (live unit shadows), and at
                   17.1 px per cell 1:1;
  02-lineup.png    every concept in one line-up on colony ground at 32 px per cell,
                   then the same at 17.1 px per cell, each labelled with its length;
  03-plates.png    the plates themselves, large.

<render root> holds the colony close-ups round2.py documents. Sizes: the Runner by
round_sheet.RUNNER_AREA_PX, the Brute 1.5x its length (UnitType radius 0.45 against
0.30, as brute_sheet.py), the Bulwark's chassis at LayeredMechAppearance.hullWidthCells
(renderScale 1.60: 1.84 cells), and the Matriarch --linear times the Runner's length
(a proposal: it has no UnitType yet).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from boards import font
from motion import marine_cutouts
from round2 import ACC, BG, DIM, INK, PPC17, PPC32, cut_marines, frames, place, scaled
from round_sheet import key_plate

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
RUNNER = HERE / "rounds" / "round-5" / "raw" / "c4.png"
BRUTE = HERE / "motion-brute" / "raw" / "hero.png"
BULWARK = REPO / "mod" / "graphics" / "battle" / "mech-modular-topdown" / "chassis.png"
BRUTE_LINEAR = 0.45 / 0.30
BULWARK_CELLS = 1.60 * 0.82 * 1.40


def sized(cut, ppc, linear=1.0):
    """The cut at the Runner's area times linear squared, resampled once from full size."""
    s = scaled(cut, ppc)
    k = linear * s.width / cut.width
    return cut.convert("RGBa").resize((max(1, round(cut.width * k)), max(1, round(cut.height * k))),
                                      Image.LANCZOS).convert("RGBA")


def facing_right(cut):
    return cut.rotate(-90, expand=True, resample=Image.BICUBIC)


def bulwark(ppc):
    im = Image.open(BULWARK).convert("RGBA")
    k = BULWARK_CELLS * ppc / im.width
    im = im.resize((round(im.width * k), round(im.height * k)), Image.LANCZOS)
    im = facing_right(im)
    box = im.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox()
    return im.crop(box)


def length_cells(body, ppc):
    a = np.asarray(body.getchannel("A")) > 128
    xs = np.where(a.any(0))[0]
    ys = np.where(a.any(1))[0]
    return (xs.max() - xs.min()) / ppc, (ys.max() - ys.min()) / ppc


class Cast:
    def __init__(self, root):
        runner = facing_right(key_plate(RUNNER))
        brute = facing_right(key_plate(BRUTE))
        self.runner = {p: sized(runner, p) for p in (PPC32, PPC17)}
        self.brute = {p: sized(brute, p, BRUTE_LINEAR) for p in (PPC32, PPC17)}
        self.mech = {p: bulwark(p) for p in (PPC32, PPC17)}
        self.marine = {PPC32: cut_marines(root, "rav-zoom32-marines", PPC32),
                       PPC17: marine_cutouts(str(frames(root, "rav-zoom")[1]))}

    def group(self, tile, x, y, ppc, i=0):
        """Bulwark, Brute, Runner and a Marine stacked in a column centred at x."""
        u = ppc
        place(tile, self.mech[ppc], x, y - 1.8 * u, ppc, radius=0.6)
        place(tile, self.brute[ppc], x, y + 0.1 * u, ppc, radius=0.45)
        place(tile, self.runner[ppc], x, y + 1.4 * u, ppc)
        m = self.marine[ppc]
        place(tile, m[i % len(m)], x - 0.1 * u, y + 2.4 * u, ppc)


def concepts(round_dir, root, linear):
    raws = sorted((p for p in Path(round_dir, "raw").glob("c*.png") if p.stem[1:].isdigit()),
                  key=lambda p: int(p.stem[1:]))
    return [(int(p.stem[1:]), key_plate(p)) for p in raws]


def sheet_concepts(items, root, cast, out, title, names, linear):
    m0, _ = frames(root, "rav-zoom32-ground")
    g32 = Image.open(m0).convert("RGBA")
    f17 = Image.open(frames(root, "rav-zoom")[0]).convert("RGBA")
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 10), title, font=font(24, True), fill=INK)
    d.text((24, 40), "plate | 32 px per cell (play zoom): Matriarch beside a Bulwark mech, the Brute, the Runner and "
                     "a Marine, live unit shadows | 17.1 px per cell 1:1 (opening view)", font=font(14), fill=DIM)
    cw, ch = 470, 500
    for i, (num, cut) in enumerate(items):
        col, row = i % 4, i // 4
        x0, y0 = 12 + col * (cw + 6), 66 + row * (ch + 6)
        d.rectangle([x0, y0, x0 + cw, y0 + ch], fill=(24, 28, 36))
        d.text((x0 + 10, y0 + 6), f"#{num}", font=font(26, True), fill=ACC)
        if num - 1 < len(names):
            d.text((x0 + 58, y0 + 12), names[num - 1], font=font(16, True), fill=INK)
        k = min(160 / cut.width, 300 / cut.height)
        pl = cut.convert("RGBa").resize((int(cut.width * k), int(cut.height * k)), Image.LANCZOS).convert("RGBA")
        pb = Image.new("RGBA", (170, 306), (180, 180, 180, 255))
        pb.alpha_composite(pl, ((170 - pl.width) // 2, (306 - pl.height) // 2))
        img.paste(pb.convert("RGB"), (x0 + 10, y0 + 44))
        rot = facing_right(cut)
        mat32 = sized(rot, PPC32, linear)
        t32 = g32.crop((900 + 40 * i, 300, 900 + 40 * i + 280, 300 + 306)).copy()
        place(t32, mat32, 98, 150, PPC32, radius=1.0)
        cast.group(t32, 228, 140, PPC32, i)
        img.paste(t32.convert("RGB"), (x0 + 186, y0 + 44))
        L, W = length_cells(mat32, PPC32)
        d.text((x0 + 186, y0 + 354), f"32 px/cell   Matriarch {L:.1f} x {W:.1f} cells", font=font(13), fill=DIM)
        t17 = f17.crop((300 + 60 * i, 880, 300 + 60 * i + 450, 880 + 116)).copy()
        place(t17, sized(rot, PPC17, linear), 80, 58, PPC17, radius=1.0)
        cast.group(t17, 170, 50, PPC17, i)
        img.paste(t17.convert("RGB"), (x0 + 10, y0 + 374))
        d.text((x0 + 10, y0 + 494 - 14), "17.1 px/cell 1:1", font=font(13), fill=DIM)
    img.save(out)


def sheet_lineup(items, root, cast, out, title, linear):
    g0, _ = frames(root, "rav-zoom32-ground")
    m0, _ = frames(root, "rav-zoom32-marines")
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 10), title + " - line-up", font=font(24, True), fill=INK)
    d.text((24, 40), "Every concept on colony ground at 32 px per cell (play zoom), then 17.1 px per cell; left: Bulwark "
                     "mech, Brute, Runner, Marine. Lengths are nose to tail including blades.", font=font(14), fill=DIM)
    # 32 px band: two rows of four on full-frame ground
    band = Image.open(g0).convert("RGBA").crop((20, 200, 1900, 200 + 640)).copy()
    cast.group(band, 70, 160, PPC32, 0)
    cast.group(band, 70, 480, PPC32, 1)
    step = (band.width - 160) / 4
    labels = []
    for i, (num, cut) in enumerate(items):
        body = sized(facing_right(cut), PPC32, linear)
        cx, cy = 160 + step * (i % 4 + 0.5), 160 + 320 * (i // 4)
        place(band, body, cx, cy, PPC32, radius=1.0)
        L, W = length_cells(body, PPC32)
        labels.append((cx, cy, num, L))
    img.paste(band.convert("RGB"), (20, 66))
    for cx, cy, num, L in labels:
        d.text((20 + cx - 40, 66 + cy + 96), f"#{num}  {L:.1f} cells", font=font(16, True), fill=ACC)
    # 17.1 px band
    f17 = Image.open(frames(root, "rav-zoom")[0]).convert("RGBA").crop((20, 780, 1900, 780 + 250)).copy()
    cast.group(f17, 50, 120, PPC17, 2)
    step = (f17.width - 120) / 8
    xs = []
    for i, (num, cut) in enumerate(items):
        cx = 120 + step * (i + 0.5)
        place(f17, sized(facing_right(cut), PPC17, linear), cx, 120, PPC17, radius=1.0)
        xs.append((cx, num))
    img.paste(f17.convert("RGB"), (20, 66 + 650))
    for cx, num in xs:
        d.text((20 + cx - 10, 66 + 650 + 256), f"#{num}", font=font(16, True), fill=ACC)
    img.save(out)


def sheet_plates(round_dir, out, title, names):
    raws = sorted((p for p in Path(round_dir, "raw").glob("c*.png") if p.stem[1:].isdigit()),
                  key=lambda p: int(p.stem[1:]))
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 10), title + " - plates", font=font(24, True), fill=INK)
    for i, p in enumerate(raws):
        num = int(p.stem[1:])
        x0, y0 = 12 + (i % 4) * 476, 50 + (i // 4) * 515
        pl = Image.open(p).convert("RGB")
        k = 505 / pl.height
        img.paste(pl.resize((round(pl.width * k), 505), Image.LANCZOS), (x0 + 60, y0))
        d.text((x0, y0 + 4), f"#{num}", font=font(26, True), fill=ACC)
        if num - 1 < len(names):
            d.text((x0, y0 + 40), "\n".join(names[num - 1].split()), font=font(13, True), fill=INK)
    img.save(out)


def main(argv):
    round_dir, root, out_dir, title = argv[:4]
    args = argv[4:]
    linear = 3.9
    if args and args[0].startswith("--linear="):
        linear = float(args[0][9:])
        args = args[1:]
    items = concepts(round_dir, root, linear)
    cast = Cast(root)
    Path(out_dir).mkdir(parents=True, exist_ok=True)
    sheet_concepts(items, root, cast, Path(out_dir, "01-concepts.png"), title, args, linear)
    sheet_lineup(items, root, cast, Path(out_dir, "02-lineup.png"), title, linear)
    sheet_plates(round_dir, Path(out_dir, "03-plates.png"), title, args)


if __name__ == "__main__":
    main(sys.argv[1:])
