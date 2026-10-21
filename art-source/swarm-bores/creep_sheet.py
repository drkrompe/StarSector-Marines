"""The bores and spore colonies standing on hive creep, for review.

The creep is the hive-creep look studies' own renderer (`art-source/hive-creep/hard_edge_studies.py`, round 3:
variant #4, organic chitin plates, lit by its CPU model of the ground composite), grown from a bore's real cell on
a real colony-breach review frame taken before the bore opened. The bodies are the shipped sprites, and the same
paintings recoloured to the round-3 purples (``export.py --palette purple``), side by side. Colonies of all three
grades are placed by hand round the mouth so each grade is seen on creep; in a battle a bore grows colonies of its
own tier only.

Usage, from the repository root, after ``export.py --palette purple``:
``python art-source/swarm-bores/creep_sheet.py <frame-32px.png> <frame-64px.png> <out-dir> [hive-creep-06.png]``
where the frames are ``commanderEvidence -Pmission=colony-breach -PsnapshotAnnotations=false`` tick-0 frames
focused on cell 158,68, 60 and 30 cells across. Writes three 1920x1080 sheets, and a fourth on the creep layer as
the game renders it today when given ``createSnapshots -Psnapshot=hive-creep``'s ``06-close-bore-1.png`` (bore 1
at the centre, 72 cells across).
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "art-source" / "hive-creep"))
import hard_edge_studies as creep  # noqa: E402
import make_guide  # noqa: E402
from export import NAMES  # noqa: E402

FOCUS = (158.0, 68.0)          # the frames' centre cell
BORE_1 = (155.5, 66.5)         # bore 1's cell centre in the colony-breach battle
W, H = 1920, 1080
# Colonies round the mouth, in cells from the bore (screen axes: x right, y down), by hand.
COLONIES = [("spore-colony-fissure", 1, (3.3, 1.2)), ("spore-colony-bore", 2, (-3.1, 2.3)),
            ("spore-colony-maw", 3, (0.6, 3.9))]


def font(size):
    for name in ("arialbd.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def label(img, xy, text, size=18):
    draw = ImageDraw.Draw(img)
    f = font(size)
    box = draw.textbbox(xy, text, font=f)
    draw.rectangle((box[0] - 6, box[1] - 4, box[2] + 6, box[3] + 4), fill=(16, 16, 18))
    draw.text(xy, text, font=f, fill=(236, 230, 214))


def sprite(part, variant, palette):
    folder = ROOT / "mod" / "graphics" / "structures" if palette == "runner" else ROOT / "build" / "swarm-bores" / palette
    return Image.open(folder / f"{NAMES[part]}-{variant}.png").convert("RGBA")


def place(canvas, image, cells, ppc, cx, cy):
    edge = round(cells * ppc)
    s = image.resize((edge, edge), Image.Resampling.LANCZOS)
    canvas.alpha_composite(s, (round(cx - edge / 2), round(cy - edge / 2)))


def screen(cell, ppc):
    """A world cell centre to frame pixels (world y runs up the screen)."""
    return W / 2 + (cell[0] - FOCUS[0]) * ppc, H / 2 - (cell[1] - FOCUS[1]) * ppc


def panel(ground, ppc, centre_px, radius_cells, bodies, palette):
    """A 952-wide crop of the frame round ``centre_px``, creep grown from the centre, bodies on top."""
    pw, ph = 952, H - 40
    ox = round(centre_px[0] - pw / 2)
    oy = round(centre_px[1] - ph / 2)
    ox = max(0, min(ground.width - pw, ox))
    oy = max(0, min(ground.height - ph, oy))
    src = ((centre_px[0] - ox) / ppc, (centre_px[1] - oy) / ppc)
    creep.ORGANIC_PLATES = True
    img = creep.render(ground, ox, oy, pw, ph, ppc, src, radius_cells, 4, True).convert("RGBA")
    for part, variant, (dx, dy) in bodies:
        px = centre_px[0] - ox + dx * ppc
        py = centre_px[1] - oy + dy * ppc
        place(img, sprite(part, variant, palette), make_guide.display_cells(part), ppc, px, py)
    return img


def sheet(ground, ppc, centre_cell, radius, bodies, title):
    out = Image.new("RGBA", (W, H), (12, 12, 14))
    centre = screen(centre_cell, ppc)
    for i, (palette, name) in enumerate((("runner", "shipped (runner palette)"), ("purple", "round-3 purples"))):
        img = panel(ground, ppc, centre, radius, bodies, palette)
        out.alpha_composite(img, (8 + i * 960, 34))
        label(out, (20 + i * 960, 44), name, 16)
    label(out, (10, 6), title, 16)
    return out.convert("RGB")


def ingame_sheet(frame, bodies):
    """The bodies on the hive-creep suite's close frame of bore 1, which centres the bore 72 cells across."""
    ppc = W / 72
    out = Image.new("RGBA", (W, H), (12, 12, 14))
    for i, (palette, name) in enumerate((("runner", "shipped (runner palette)"), ("purple", "round-3 purples"))):
        crop = frame.crop((W // 2 - 476, 40, W // 2 + 476, H)).copy()
        for part, variant, (dx, dy) in bodies:
            place(crop, sprite(part, variant, palette), make_guide.display_cells(part), ppc, 476 + dx * ppc,
                  H // 2 - 40 + dy * ppc)
        out.alpha_composite(crop, (8 + i * 960, 34))
        label(out, (20 + i * 960, 44), name, 16)
    label(out, (10, 6), "On the creep layer as it renders on main today (hive-creep suite, bore 1, 26.7 px/cell)", 16)
    return out.convert("RGB")


def main():
    g32 = Image.open(sys.argv[1]).convert("RGB")
    g64 = Image.open(sys.argv[2]).convert("RGB")
    out = Path(sys.argv[3])
    out.mkdir(parents=True, exist_ok=True)
    bore = [("bore", 1, (0.0, 0.0))] + COLONIES
    sheet(g32, 32, BORE_1, 11.0, bore,
          "Bore 1 and colonies on hard-edge creep (round 3, organic plates, lit CPU model) -- play zoom 32 px/cell"
          ).save(out / "01-creep-play-zoom-32px.png")
    sheet(g64, 64, BORE_1, 6.5, bore,
          "Bore 1 and colonies on hard-edge creep (round 3, organic plates, lit CPU model) -- close 64 px/cell"
          ).save(out / "02-creep-close-64px.png")
    # The mother bore on a wider stain, a few cells off the same plaza.
    mother = [("mother-bore", 1, (0.0, 0.0)), ("spore-colony-maw", 1, (3.6, 1.6)),
              ("spore-colony-maw", 2, (-3.4, -2.4)), ("spore-colony-bore", 3, (-2.6, 3.3))]
    sheet(g64, 64, (157.5, 68.5), 9.0, mother,
          "Mother bore with maw-grade colonies on hard-edge creep -- close 64 px/cell (placed by hand on bore 1's plaza)"
          ).save(out / "03-creep-mother-64px.png")
    if len(sys.argv) > 4:
        ingame_sheet(Image.open(sys.argv[4]).convert("RGBA"), bore).save(out / "04-ingame-creep-layer-27px.png")
    for name in sorted(p.name for p in out.glob("0*.png")):
        print(out / name)


if __name__ == "__main__":
    main()
