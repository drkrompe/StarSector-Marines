"""Matriarch corpse concepts at play zoom: each plate, then the corpse at 32 px per cell beside the living
Matriarch, on paving and on the in-game hive creep. A corpse is drawn at the living plate's scale (the
plates share their canvas scale), not sized by area, which its ichor pool would shrink.

  python corpse_sheet.py <round dir> <render root> <out.png> "<title>" <names...>
  python corpse_sheet.py --each <round dir> <render root> <out dir> <names...>
      one image per corpse, lettered A, B, ..., for a pick-one page
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

from boards import font
from matriarch_sheet import facing_right, sized
from round2 import ACC, BG, DIM, INK, PPC32, ground_tiles, place
from round_sheet import key_plate

HERE = Path(__file__).resolve().parent
HERO = HERE / "motion-matriarch" / "raw" / "hero.png"
LINEAR = 3.9


def sheet(round_dir, root, out, title, names):
    raws = sorted((p for p in Path(round_dir, "raw").glob("c*.png") if p.stem[1:].isdigit()),
                  key=lambda p: int(p.stem[1:]))
    hero = facing_right(key_plate(HERO))
    live = sized(hero, PPC32, LINEAR)
    # the same canvas-to-screen scale for every plate: the uncropped canvas, rotated
    k = live.width / hero.width
    img = Image.new("RGB", (1920, 1080), BG)
    d = ImageDraw.Draw(img)
    d.text((24, 10), title, font=font(24, True), fill=INK)
    d.text((24, 40), "plate | 32 px per cell (play zoom) on paving and on hive creep: the living Matriarch at left, "
                     "the corpse at right, both at the plates' shared scale", font=font(14), fill=DIM)
    pav = ground_tiles(root, (560, 190))[0][1]
    creep = Image.open(Path(root) / "creep-play-zoom.png").convert("RGBA").crop((0, 0, 560, 190))
    for i, p in enumerate(raws):
        num = int(p.stem[1:])
        col, row = i % 3, i // 3
        x0, y0 = 12 + col * 636, 66 + row * 506
        d.rectangle([x0, y0, x0 + 630, y0 + 500], fill=(24, 28, 36))
        d.text((x0 + 10, y0 + 6), f"#{num}", font=font(26, True), fill=ACC)
        if num - 1 < len(names):
            d.text((x0 + 58, y0 + 12), names[num - 1], font=font(16, True), fill=INK)
        cut = key_plate(p)
        s = min(200 / cut.width, 440 / cut.height)
        pl = cut.resize((int(cut.width * s), int(cut.height * s)), Image.LANCZOS)
        pb = Image.new("RGBA", (210, 446), (180, 180, 180, 255))
        pb.alpha_composite(pl, ((210 - pl.width) // 2, (446 - pl.height) // 2))
        img.paste(pb.convert("RGB"), (x0 + 8, y0 + 44))
        dead = facing_right(cut)
        dead = dead.resize((max(1, round(dead.width * k)), max(1, round(dead.height * k))), Image.LANCZOS)
        for j, g in enumerate((pav, creep)):
            t = g.copy()
            place(t, live, 100, 95, PPC32, radius=1.0)
            t.alpha_composite(dead, (int(300 - dead.width / 2), int(95 - dead.height / 2)))
            img.paste(t.crop((0, 0, 400, 190)).convert("RGB"), (x0 + 226, y0 + 44 + j * 206))
    img.save(out)


def each(round_dir, root, out_dir, names):
    """One option image per corpse: its letter and name, the plate, and the corpse beside the living
    Matriarch at 32 px per cell on paving and on creep."""
    raws = sorted((p for p in Path(round_dir, "raw").glob("c*.png") if p.stem[1:].isdigit()),
                  key=lambda p: int(p.stem[1:]))
    hero = facing_right(key_plate(HERO))
    live = sized(hero, PPC32, LINEAR)
    k = live.width / hero.width
    pav = ground_tiles(root, (560, 190))[0][1]
    creep = Image.open(Path(root) / "creep-play-zoom.png").convert("RGBA").crop((0, 0, 560, 190))
    Path(out_dir).mkdir(parents=True, exist_ok=True)
    files = []
    for i, p in enumerate(raws):
        letter = chr(ord("A") + i)
        img = Image.new("RGB", (640, 500), (24, 28, 36))
        d = ImageDraw.Draw(img)
        d.text((10, 6), letter, font=font(30, True), fill=ACC)
        d.text((48, 14), names[i] if i < len(names) else "", font=font(18, True), fill=INK)
        cut = key_plate(p)
        s = min(200 / cut.width, 440 / cut.height)
        pl = cut.resize((int(cut.width * s), int(cut.height * s)), Image.LANCZOS)
        pb = Image.new("RGBA", (210, 446), (180, 180, 180, 255))
        pb.alpha_composite(pl, ((210 - pl.width) // 2, (446 - pl.height) // 2))
        img.paste(pb.convert("RGB"), (8, 46))
        dead = facing_right(cut)
        dead = dead.resize((max(1, round(dead.width * k)), max(1, round(dead.height * k))), Image.LANCZOS)
        for j, g in enumerate((pav, creep)):
            t = g.copy()
            place(t, live, 100, 95, PPC32, radius=1.0)
            t.alpha_composite(dead, (int(300 - dead.width / 2), int(95 - dead.height / 2)))
            img.paste(t.crop((0, 0, 400, 190)).convert("RGB"), (230, 46 + j * 206))
        d.text((230, 458), "living | dead, 32 px per cell: paving, creep", font=font(13), fill=DIM)
        name = f"corpse-{letter}.png"
        img.save(Path(out_dir) / name)
        files.append(name)
    return files


if __name__ == "__main__":
    if sys.argv[1] == "--each":
        print(each(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5:]))
        sys.exit()
    sheet(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5:])
