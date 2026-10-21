"""Brute concept rounds: numbered ImageGen plates, each beside the same design at
32 and 17.1 px per cell next to the chosen Runner and a Marine, with live unit
shadows.

  python brute_sheet.py <round dir> <render root> <out.png> "<title>" <names...>

<render root> holds the colony close-ups round2.py documents. The Brute is drawn
at its sim size against the Runner's: UnitType radius 0.45 against 0.30, so
1.5x the Runner's length and 2.25x its opaque area.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

from boards import font
from motion import marine_cutouts
from round2 import ACC, BG, DIM, INK, PPC17, PPC32, cut_marines, frames, ground_tiles, place, scaled
from round_sheet import key_plate

HERE = Path(__file__).resolve().parent
RUNNER = HERE / "motion-runner" / "raw" / "hero.png"
BRUTE_LINEAR = 0.45 / 0.30


def sized(cut, ppc, linear=1.0):
    s = scaled(cut, ppc)
    if linear == 1.0:
        return s
    return s.convert("RGBa").resize((round(s.width * linear), round(s.height * linear)),
                                    Image.LANCZOS).convert("RGBA")


def sheet(round_dir, root, out, title, names):
    raws = sorted((p for p in Path(round_dir, "raw").glob("c*.png") if p.stem[1:].isdigit()),
                  key=lambda p: int(p.stem[1:]))
    runner = key_plate(RUNNER).rotate(-90, expand=True, resample=Image.BICUBIC)
    r32, r17 = sized(runner, PPC32), sized(runner, PPC17)
    m32 = cut_marines(root, "rav-zoom32-marines", PPC32)
    m17 = marine_cutouts(str(frames(root, "rav-zoom")[1]))
    pav32 = ground_tiles(root, (242, 170))[0][1]
    f17 = Image.open(frames(root, "rav-zoom")[0]).convert("RGBA")
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 10), title, font=font(24, True), fill=INK)
    d.text((24, 40), "plate | 32 px per cell on paving: Brute, Runner, Marine, live unit shadows | 17.1 px per cell "
                     "(opening-view scale) 1:1 and x2 | Brute at sim size, 1.5x the Runner's length", font=font(14), fill=DIM)
    cw, ch = 470, 500
    for i, p in enumerate(raws):
        col, row = i % 4, i // 4
        x0, y0 = 12 + col * (cw + 6), 66 + row * (ch + 6)
        d.rectangle([x0, y0, x0 + cw, y0 + ch], fill=(24, 28, 36))
        num = int(p.stem[1:])
        d.text((x0 + 10, y0 + 6), f"#{num}", font=font(26, True), fill=ACC)
        if num - 1 < len(names):
            d.text((x0 + 58, y0 + 12), names[num - 1], font=font(16, True), fill=INK)
        cut = key_plate(p)
        k = min(200 / cut.width, 440 / cut.height)
        pl = cut.convert("RGBa").resize((int(cut.width * k), int(cut.height * k)), Image.LANCZOS).convert("RGBA")
        pb = Image.new("RGBA", (200, 446), (180, 180, 180, 255))
        pb.alpha_composite(pl, ((200 - pl.width) // 2, (446 - pl.height) // 2))
        img.paste(pb.convert("RGB"), (x0 + 10, y0 + 44))
        rot = cut.rotate(-90, expand=True, resample=Image.BICUBIC)
        t32 = pav32.copy()
        place(t32, sized(rot, PPC32, BRUTE_LINEAR), 70, 85, PPC32, radius=0.45)
        place(t32, r32, 160, 60, PPC32)
        place(t32, m32[i % len(m32)], 200, 110, PPC32)
        img.paste(t32.convert("RGB"), (x0 + 218, y0 + 44))
        d.text((x0 + 218, y0 + 216), "32 px/cell", font=font(13), fill=DIM)
        t17 = f17.crop((720 + i * 30, 985, 720 + i * 30 + 121, 1060)).copy()
        place(t17, sized(rot, PPC17, BRUTE_LINEAR), 35, 38, PPC17, radius=0.45)
        place(t17, r17, 78, 26, PPC17)
        place(t17, m17[i % len(m17)], 100, 50, PPC17)
        img.paste(t17.convert("RGB"), (x0 + 218, y0 + 244))
        d.text((x0 + 350, y0 + 250), "17.1 px/cell 1:1", font=font(13), fill=DIM)
        img.paste(t17.resize((242, 150), Image.NEAREST).convert("RGB"), (x0 + 218, y0 + 326))
        d.text((x0 + 218, y0 + 480), "17.1 px/cell x2", font=font(13), fill=DIM)
    img.save(out)


if __name__ == "__main__":
    sheet(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5:])
