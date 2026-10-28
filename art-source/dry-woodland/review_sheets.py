"""Review sheets for the dry-woodland candidate round (1920x1080, owner-facing).

python art-source/dry-woodland/review_sheets.py <out-dir> <rim-still.png> [direction ...]

<rim-still.png> is a basin-rim-look play-zoom still (createSnapshots -Psnapshot=basin-rim-look),
whose green basin carries the shipped forest and whose sand rim is bare. The candidates are
cut with the shipped forest's own authoring rects and scaled to their footprints, so a
crown covers exactly the cells the shipped tree would.

Per direction: a contact sheet of its three raws; the rim still with its trees and scrub on
the sand at game scale (32 px a cell, the suite's 60-cell play zoom); and a 2x close-up of the same boundary (64 px a cell).
Then one sheet with every direction's close-up side by side.
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
from review_font import review_font  # noqa: E402

TILESETS = HERE.parent / "tilesets"
RAW = HERE / "raw"
W, H = 1920, 1080
SAND = (176, 148, 92, 255)
NAMES = {
    "acacia": "A  Acacia / umbrella-thorn",
    "palm": "B  Palm grove / oasis edge",
    "scrub": "C  Grey dry scrub (mesquite, wild olive, mopane)",
}


def entries(doc):
    d = json.loads((TILESETS / doc).read_text())
    return [(e["id"], e["footprintCells"], e["rect"]) for e in d["entries"]]


CANOPY = entries("forest-canopies.tileset-authoring.json")
UNDER = entries("forest-undergrowth.tileset-authoring.json")
DAMAGED = entries("forest-damaged.tileset-authoring.json")


GUIDES = {"canopies": HERE.parent / "templates" / "forest-canopies.png",
          "canopies-v2": HERE / "guides" / "forest-canopies-sparse.png",
          "undergrowth": HERE.parent / "templates" / "undergrowth-standing.png",
          "damaged": HERE.parent / "templates" / "tree-damaged.png"}


def opaque_box(im):
    return im.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox()


def fitted(raw_path):
    """The raw with each 512 px panel's opaque extent scaled onto its guide's extent.

    The generations came back 10-20% larger than their guides; fitting keeps the
    crowns on the shipped footprints (the pattern the boarding-jack and swarm-bore
    exporters use) so the comparison is at true tactical size.
    """
    kind = Path(raw_path).name.split("-", 1)[1].replace(".raw.png", "")
    im = Image.open(raw_path).convert("RGBA").resize((1536, 1024), Image.LANCZOS)
    guide = Image.open(GUIDES[kind]).convert("RGBA")
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    for r in range(2):
        for c in range(3):
            cell = (c * 512, r * 512, c * 512 + 512, r * 512 + 512)
            rb, gb = opaque_box(im.crop(cell)), opaque_box(guide.crop(cell))
            if not rb or not gb:
                continue
            piece = im.crop(cell).crop(rb).resize((gb[2] - gb[0], gb[3] - gb[1]), Image.LANCZOS)
            out.alpha_composite(piece, (cell[0] + gb[0], cell[1] + gb[1]))
    return out


def cuts(raw_path, table):
    """Cut a guide-fitted raw with the shipped document's rects."""
    im = fitted(raw_path)
    sx, sy = im.width / 1536, im.height / 1024
    out = []
    for _id, fp, (x, y, w, h) in table:
        box = (round(x * sx), round(y * sy), round((x + w) * sx), round((y + h) * sy))
        out.append((_id, fp, im.crop(box)))
    return out


def scaled(sprite, fp, cell):
    return sprite.resize((max(1, fp[0] * cell), max(1, fp[1] * cell)), Image.LANCZOS)


def label(draw, xy, text, size=26, bold=True, fill=(250, 246, 236)):
    f = review_font(size, bold)
    x, y = xy
    draw.text((x + 2, y + 2), text, font=f, fill=(0, 0, 0))
    draw.text((x, y), text, font=f, fill=fill)


def canopy_raw(d):
    v2 = RAW / f"{d}-canopies-v2.raw.png"
    return v2 if v2.exists() else RAW / f"{d}-canopies.raw.png"


def tries(d, out):
    """The first canopy generation beside the sparse-guide v2, both as generated."""
    im = Image.new("RGBA", (W, H), SAND)
    dr = ImageDraw.Draw(im)
    label(dr, (24, 14), NAMES[d] + "  - canopy tries, as generated", 30)
    for i, (name, cap) in enumerate((("canopies", "v1: shipped guide; came back ~15% oversized and closed"),
                                     ("canopies-v2", "v2: sparse guide (gaps and limbs drawn in the guide)"))):
        p = RAW / f"{d}-{name}.raw.png"
        if p.exists():
            im.alpha_composite(Image.open(p).convert("RGBA").resize((930, 620), Image.LANCZOS), (20 + i * 950, 120))
        label(dr, (30 + i * 950, 760), cap, 22, False)
    g = Image.open(GUIDES["canopies-v2"]).convert("RGBA").resize((330, 220), Image.LANCZOS)
    im.alpha_composite(g, (W // 2 - 165, 820))
    label(dr, (W // 2 - 165, 1046), "v2 edit target (sparse guide)", 20, False)
    im.convert("RGB").save(out / f"{d}-4-canopy-tries.png")


def contact(d, out):
    im = Image.new("RGBA", (W, H), SAND)
    dr = ImageDraw.Draw(im)
    label(dr, (24, 14), NAMES[d] + "  - candidate raws on sand", 30)
    can = Image.open(canopy_raw(d)).convert("RGBA").resize((1152, 768), Image.LANCZOS)
    im.alpha_composite(can, (16, 80))
    label(dr, (24, 856), "Canopies (latest try): same six crown silhouettes as the shipped forest", 22, False)
    label(dr, (24, 886), "(oak 5x5, small oak 4x4, birch 5x4 / pine 3x3, large pine 4x4, cedar 4x6)", 20, False)
    y = 80
    for kind, cap in (("undergrowth", "Undergrowth: shipped six floor-growth silhouettes"),
                      ("damaged", "Damaged state: shipped broken-crown guide")):
        p = RAW / f"{d}-{kind}.raw.png"
        if p.exists():
            im.alpha_composite(Image.open(p).convert("RGBA").resize((720, 480), Image.LANCZOS), (1184, y))
        else:
            label(dr, (1200, y + 200), "(not generated)", 24)
        label(dr, (1192, y + 474), cap, 20, False)
        y += 490
    im.convert("RGB").save(out / f"{d}-1-contact.png")


def is_sand(px):
    r, g, b = px[:3]
    return r > 140 and r - b > 50 and r >= g


def scatter(base, d, cell, seed, extra_damaged=True):
    """Place the direction's sprites on the still's sand, never over grass or each other."""
    rgb = base.convert("RGB")
    taken = Image.new("1", base.size, 0)
    rng = random.Random(seed)
    trees = cuts(canopy_raw(d), CANOPY)
    under_p = RAW / f"{d}-undergrowth.raw.png"
    under = cuts(under_p, UNDER) if under_p.exists() else []
    dmg_p = RAW / f"{d}-damaged.raw.png"
    dmg = cuts(dmg_p, DAMAGED) if (extra_damaged and dmg_p.exists()) else []
    queue = [t for t in trees for _ in range(3)] + [u for u in under for _ in range(2)] + dmg[:2]
    rng.shuffle(queue)
    for _id, fp, spr in queue:
        s = scaled(spr, fp, cell).rotate(90 * rng.randrange(4), expand=True)
        for _ in range(400):
            x = rng.randrange(0, base.width - s.width)
            y = rng.randrange(0, base.height - s.height)
            pts = [(x + i * s.width // 4, y + j * s.height // 4) for i in range(5) for j in range(5)]
            pts = [(min(px, base.width - 1), min(py, base.height - 1)) for px, py in pts]
            if all(is_sand(rgb.getpixel(p)) for p in pts) and not any(taken.getpixel(p) for p in pts):
                base.alpha_composite(s, (x, y))
                ImageDraw.Draw(taken).rectangle((x - 4, y - 4, x + s.width + 4, y + s.height + 4), fill=1)
                break
    return base


def rim_game_scale(d, still, out):
    base = Image.open(still).convert("RGBA")
    scatter(base, d, 32, 11)
    dr = ImageDraw.Draw(base)
    label(dr, (24, 1030), NAMES[d] + "  - on the rim at game scale (32 px a cell, the suite's 60-cell play zoom), shipped forest in the basin", 24)
    base.convert("RGB").save(out / f"{d}-2-rim-game-scale.png")


def close_base(still):
    # The play-zoom still's grass/sand boundary with the shipped birch/pine group, doubled.
    return Image.open(still).convert("RGBA").crop((960, 160, 1920, 700)).resize((W, H), Image.LANCZOS)


def rim_close(d, still, out):
    base = scatter(close_base(still), d, 64, 23)
    dr = ImageDraw.Draw(base)
    label(dr, (24, 1030), NAMES[d] + "  - close-up (64 px a cell), shipped trees top right on grass", 24)
    base.convert("RGB").save(out / f"{d}-3-rim-close.png")


def side_by_side(dirs, still, out):
    im = Image.new("RGBA", (W, H), (24, 24, 24, 255))
    dr = ImageDraw.Draw(im)
    colw = W // len(dirs)
    for i, d in enumerate(dirs):
        # Each column: the close-up base cropped to the column, its own scatter.
        base = scatter(close_base(still), d, 64, 23)
        col = base.crop(((W - colw) // 2 + 300, 0, (W - colw) // 2 + 300 + colw, H))
        im.alpha_composite(col, (i * colw, 0))
        label(dr, (i * colw + 16, 16), NAMES[d], 24)
        dr.line((i * colw, 0, i * colw, H), fill=(24, 24, 24), width=4)
    im.convert("RGB").save(out / "0-all-directions-close.png")


def main():
    out = Path(sys.argv[1])
    still = Path(sys.argv[2])
    dirs = sys.argv[3:] or ["acacia", "palm", "scrub"]
    dirs = [d for d in dirs if (RAW / f"{d}-canopies.raw.png").exists()]
    out.mkdir(parents=True, exist_ok=True)
    for d in dirs:
        contact(d, out)
        rim_game_scale(d, still, out)
        rim_close(d, still, out)
        tries(d, out)
    if dirs:
        side_by_side(dirs, still, out)
    print("wrote", sorted(p.name for p in out.glob("*.png")))


if __name__ == "__main__":
    main()
