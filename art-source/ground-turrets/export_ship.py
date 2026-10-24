"""Cut the shipped ground-turret sets into mod/graphics/structures/ground-turrets/<look>/ (every emplacement style).

Mechanical, from the retained raws and their guides (make_guide.py), at 128 px a cell. Every look has the same pieces
at the same sizes. The low-tech looks' heads are fitted to the brutalist head's opaque box; the family looks' heads are
registered to their own guides (fit_registered), which hold the pivot and the brutalist muzzle geometry. Either way
one set of muzzles serves every look (the table LOOKS says where each piece comes from):

  collar-octagon.png, collar-round.png         1x1 bases, 128x128
  <role>-head.png + <role>-barrel.png          rotating head and its recoil layer, 160x160 (visualCells 1.25),
                                               pivot at the centre, muzzle up; role = autogun, cannon, grenade, pd
  plinth.png                                   3x3 raised plinth, 384x384, cast shadow drawn in code
  heavy-head.png + heavy-barrel.png            384x384 (visualCells 3.0)

The barrel layer is everything above the row where the head's body begins, extended a further eighth of the sprite
down under the body (copied from its lowest visible row), so a barrel pulled back by recoil (a tenth of the sprite
height in `BattleRenderer`) never shows a gap at the mantlet. Drawn first, under the head, it composes back to the
painted piece exactly at rest.

Muzzles are measured off the barrel layer: one per separate tube, at its tip, in cells from the pivot
([lateral, forward], lateral positive to the right of the bearing), and written to muzzles.json beside this file.

Midline and high-tech looks (modular, barrier: League standard; export, blockwork: Diktat export; composite, deployed:
field emitter, from its '-hex' raws) come from family_drafts.py's sheets.

Usage: python art-source/ground-turrets/export_ship.py
"""
import json
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from export import contact_shadow, masked, plinth_with_shadow  # noqa: E402

ROOT = HERE.parent.parent
OUT = ROOT / "mod" / "graphics" / "structures" / "ground-turrets"
PPC = 128
SMALL = 160          # 1.25 cells
SMALL_CELLS = 1.25
EXTEND = 0.125       # barrel continued under the body, as a fraction of the sprite


def split_row(img):
    """First row from the top whose opaque width passes half the widest row: where the body begins."""
    a = img.getchannel("A").load()
    w, h = img.size
    widths = [sum(1 for x in range(w) if a[x, y] > 128) for y in range(h)]
    limit = max(widths) * 0.5
    top = next(y for y in range(h) if widths[y] > 0)
    for y in range(top, h):
        if widths[y] > limit:
            return y
    raise ValueError("no body row")


def split(img):
    """(head, barrel) layers of one composed head, and the barrel's split row."""
    w, h = img.size
    row = split_row(img)
    src = img.load()
    head = img.copy()
    hp = head.load()
    barrel = Image.new("RGBA", img.size, (0, 0, 0, 0))
    bp = barrel.load()
    for y in range(row):
        for x in range(w):
            bp[x, y] = src[x, y]
            hp[x, y] = (0, 0, 0, 0)
    last = row - 1
    for y in range(row, min(h, row + int(round(EXTEND * h)))):
        for x in range(w):
            if src[x, last][3] > 0:
                bp[x, y] = src[x, last]
    return head, barrel, row


def muzzles(barrel, cells):
    """[lateral, forward] of each separate tube's tip, in cells from the pivot."""
    a = barrel.getchannel("A").load()
    w, h = barrel.size
    px = w / cells
    top = next(y for y in range(h) if any(a[x, y] > 128 for x in range(w)))
    probe = top + max(2, int(0.02 * h))
    runs, start = [], None
    for x in range(w + 1):
        on = x < w and a[x, probe] > 128
        if on and start is None:
            start = x
        elif not on and start is not None:
            runs.append((start, x - 1))
            start = None
    out = []
    for x0, x1 in runs:
        tip = next(y for y in range(h) if any(a[x, y] > 128 for x in range(x0, x1 + 1)))
        cx = (x0 + x1 + 1) / 2
        out.append([round((cx - w / 2) / px, 3), round((h / 2 - tip) / px, 3)])
    return out


def objects(img):
    """Bounding boxes of the sheet's objects, left to right: runs of columns holding any opaque pixel."""
    a = img.getchannel("A").point(lambda v: 255 if v > 32 else 0)
    w, h = img.size
    px = a.load()
    occupied = [any(px[x, y] for y in range(0, h, 2)) for x in range(w)]
    boxes, start = [], None
    for x in range(w + 1):
        on = x < w and occupied[x]
        if on and start is None:
            start = x
        elif not on and start is not None:
            boxes.append(a.crop((start, 0, x, h)).getbbox())
            boxes[-1] = (start, boxes[-1][1], x, boxes[-1][3])
            start = None
    return boxes


def fit_object(raw, guide, index, frame):
    """Raw object `index` scaled uniformly (by width) onto guide object `index`, centred across and standing on its
    rear edge, returned as the guide's `frame` crop. Generations move and enlarge small pieces (objects here spill
    across the guide's frame lines), so pieces are found by separation, not by frame."""
    rb = objects(raw)[index]
    gb = objects(guide)[index]
    k = (gb[2] - gb[0]) / (rb[2] - rb[0])
    w, h = gb[2] - gb[0], max(1, round((rb[3] - rb[1]) * k))
    canvas = Image.new("RGBA", guide.size, (0, 0, 0, 0))
    canvas.alpha_composite(raw.crop(rb).resize((w, h), Image.LANCZOS), (gb[0], gb[3] - h))
    return canvas.crop(frame)


def fit_registered(raw, guide, index, frame):
    """Raw object `index` moved and scaled (per axis) onto guide object `index`, returned as the guide's `frame` crop.

    The family looks' guides draw each head with its turret ring on the frame centre (the pivot) and its barrel tips
    where the brutalist guide puts them, so the guide is the fit: a head registered to its own guide object sits on
    its mount and fires from the shared muzzles. Pieces are found by separation, as in fit_object."""
    rb = objects(raw)[index]
    gb = objects(guide)[index]
    canvas = Image.new("RGBA", guide.size, (0, 0, 0, 0))
    canvas.alpha_composite(raw.crop(rb).resize((gb[2] - gb[0], gb[3] - gb[1]), Image.LANCZOS), (gb[0], gb[1]))
    return canvas.crop(frame)


def match_extent(piece, reference):
    """`piece` moved and scaled (per axis) so its opaque extent equals `reference`'s: one set of muzzles then serves
    every look, because each look's head and barrel span exactly the brutalist head's box."""
    pb = piece.getchannel("A").point(lambda v: 255 if v > 32 else 0).getbbox()
    rb = reference.getchannel("A").point(lambda v: 255 if v > 32 else 0).getbbox()
    out = Image.new("RGBA", piece.size, (0, 0, 0, 0))
    out.alpha_composite(piece.crop(pb).resize((rb[2] - rb[0], rb[3] - rb[1]), Image.LANCZOS), (rb[0], rb[1]))
    return out


def sand_khaki(image):
    """Fallback only: recolour a painted piece to sand-and-khaki, keeping its luminance."""
    from PIL import ImageOps
    alpha = image.getchannel("A")
    lum = ImageOps.autocontrast(image.convert("L"), cutoff=1)
    out = ImageOps.colorize(lum, black=(38, 32, 22), mid=(150, 128, 88), white=(222, 206, 166)).convert("RGBA")
    out.putalpha(alpha)
    return out


def fill_pit(collar, colour=(34, 28, 22, 255)):
    """A collar whose well came back see-through gets a dark earth pit: transparent pixels the outside cannot reach."""
    from PIL import ImageDraw
    w, h = collar.size
    mask = collar.getchannel("A").point(lambda v: 255 if v > 64 else 0).convert("RGB")
    ImageDraw.floodfill(mask, (0, 0), (255, 0, 0))
    m = mask.load()
    out = collar.copy()
    px = out.load()
    for y in range(h):
        for x in range(w):
            if m[x, y] == (0, 0, 0):
                px[x, y] = colour
    return out


CX = [18 + 500 * i + 250 for i in range(3)]
COLLAR_BOX = (CX[0] - 200, 312, CX[0] + 200, 712)
HEAVY_BOX = (768, 128, 1536, 896)
PLINTH_BOX = (0, 128, 768, 896)
SHADOW = {"autogun": 0.22, "cannon": 0.24, "grenade": 0.22, "pd": 0.19, "heavy": 0.2}


def frame(i):
    return CX[i] - 250, 262, CX[i] + 250, 762


def sheet(raw_name, guide_name):
    guide = Image.open(HERE / "guides" / guide_name)
    return masked(Image.open(HERE / "raw" / raw_name), guide), guide


# Per look, where each piece comes from: (raw, guide, object index on that sheet, box on the guide).
LOOKS = {
    "brutalist": {
        "collar-octagon": ("brutalist-points.raw.png", "brutalist-points-guide.png", 0, COLLAR_BOX),
        "collar-round": ("brutalist-points2.raw.png", "brutalist-points2-guide.png", 0, COLLAR_BOX),
        "autogun": ("brutalist-points.raw.png", "brutalist-points-guide.png", 1, frame(1)),
        "cannon": ("brutalist-points.raw.png", "brutalist-points-guide.png", 2, frame(2)),
        "grenade": ("brutalist-points2.raw.png", "brutalist-points2-guide.png", 1, frame(1)),
        "pd": ("brutalist-points2.raw.png", "brutalist-points2-guide.png", 2, frame(2)),
        "plinth": ("brutalist-hardpoint.raw.png", "brutalist-hardpoint-guide.png", 0, PLINTH_BOX),
        "heavy": ("brutalist-hardpoint.raw.png", "brutalist-hardpoint-guide.png", 1, HEAVY_BOX),
    },
    # Militia: sandbag-ring collars, the brutalist heads
    # repainted as field work (earthwork-points*.raw.png edit the brutalist raws in place), the gabion mound.
    "earthwork": {
        "collar-octagon": ("earthwork-collar.raw.png", "earthwork-collar-guide.png", 0, COLLAR_BOX),
        "collar-round": ("earthwork-points2.raw.png", "brutalist-points2-guide.png", 0, COLLAR_BOX),
        # The image tool refused to repaint these two four times; field_dress.py dresses the brutalist heads in the
        # generated earthwork grenade head's paint and roof sacks instead.
        "autogun": ("brutalist-points.raw.png", "brutalist-points-guide.png", 1, frame(1)),
        "cannon": ("brutalist-points.raw.png", "brutalist-points-guide.png", 2, frame(2)),
        "grenade": ("earthwork-points2.raw.png", "brutalist-points2-guide.png", 1, frame(1)),
        "pd": ("earthwork-points2.raw.png", "brutalist-points2-guide.png", 2, frame(2)),
        "plinth": ("earthwork-hardpoint.raw.png", "earthwork-hardpoint-guide.png", 0, PLINTH_BOX),
        "heavy": ("earthwork-hardpoint.raw.png", "earthwork-hardpoint-guide.png", 1, HEAVY_BOX),
    },
    # Derelict: the colony-steel direction, with the heavy head redesigned round with a centred barrel.
    "derelict": {
        "collar-octagon": ("colony-points.raw.png", "colony-points-guide.png", 0, COLLAR_BOX),
        "collar-round": ("derelict-points2.raw.png", "brutalist-points2-guide.png", 0, COLLAR_BOX),
        "autogun": ("colony-points.raw.png", "colony-points-guide.png", 1, frame(1)),
        "cannon": ("colony-points.raw.png", "colony-points-guide.png", 2, frame(2)),
        "grenade": ("derelict-points2.raw.png", "brutalist-points2-guide.png", 1, frame(1)),
        "pd": ("derelict-points2.raw.png", "brutalist-points2-guide.png", 2, frame(2)),
        "plinth": ("colony-hardpoint.raw.png", "colony-hardpoint-guide.png", 0, PLINTH_BOX),
        "heavy": ("derelict-heavy.raw.png", "derelict-heavy-guide.png", 0, HEAVY_BOX),
    },
}


def _family_look(option, variant, family, hardpoint="hardpoint", second="points2", dressed=False, raw_suffix=""):
    """A midline or high-tech look from family_drafts.py's sheets (guides named by family, raws by option; a raw
    suffix such as '-hex' names a regeneration over the same guides)."""
    x = raw_suffix
    pts = (f"{option}-{variant}-points{x}.raw.png", f"{family}-{variant}-points-guide.png")
    hp = (f"{option}-{variant}-{hardpoint}{x}.raw.png", f"{family}-{variant}-{hardpoint}-guide.png")
    if dressed:
        # The second point sheet was refused for this variant: its round collar was generated alone, and its grenade
        # and point-defence heads are the prepared variant's in this variant's paint (dress source: its autogun).
        collar = (f"{option}-{variant}-collar2{x}.raw.png", f"{family}-{variant}-collar2-guide.png")
        heads = (f"{option}-prepared-points2{x}.raw.png", f"{family}-prepared-points2-guide.png")
        grenade = heads + (1, frame(1), "autogun")
        pd = heads + (2, frame(2), "autogun")
    else:
        collar = heads = (f"{option}-{variant}-{second}{x}.raw.png", f"{family}-{variant}-{second}-guide.png")
        grenade, pd = heads + (1, frame(1)), heads + (2, frame(2))
    return {
        "collar-octagon": pts + (0, COLLAR_BOX),
        "collar-round": collar + (0, COLLAR_BOX),
        "autogun": pts + (1, frame(1)),
        "cannon": pts + (2, frame(2)),
        "grenade": grenade,
        "pd": pd,
        "plinth": hp + (0, PLINTH_BOX),
        "heavy": hp + (1, HEAVY_BOX),
    }


# Midline (League standard for the Persean League, Diktat export for the Diktat) and high-tech (field emitter, hex
# armoured), each prepared and militia (`ground-turret-tiers.md`).
LOOKS["modular"] = _family_look("league-standard", "prepared", "midline")
LOOKS["barrier"] = _family_look("league-standard", "militia", "midline", hardpoint="hardpoint-v2", dressed=True)
LOOKS["export"] = _family_look("diktat-export", "prepared", "midline")
LOOKS["blockwork"] = _family_look("diktat-export", "militia", "midline", hardpoint="hardpoint-v2",
                                  dressed=not (HERE / "raw" / "diktat-export-militia-points2.raw.png").exists())
LOOKS["composite"] = _family_look("field-emitter", "prepared", "hightech", raw_suffix="-hex")
LOOKS["deployed"] = _family_look("field-emitter", "militia", "hightech", hardpoint="hardpoint-v2", raw_suffix="-hex")
# Looks cut by registering each piece to its own guide (fit_registered) rather than fitting it onto the brutalist
# head's box: their guides already hold the pivot and muzzle geometry, and stretching a differently proportioned head
# onto another head's box moved its ring off the pivot (the owner's "heads not fit right", phase B).
GUIDE_FIT = {"modular", "barrier", "export", "blockwork", "composite", "deployed"}


def export_look(look, reference):
    """Writes OUT/<look>/; returns this look's composed heads (before the split) and its measured muzzles."""
    target = OUT / look
    target.mkdir(parents=True, exist_ok=True)
    composed, measured = {}, {}
    # Dress sources first: earthwork's grenade and pd paint its autogun and cannon; a dressed family look's autogun
    # paints its borrowed grenade and pd.
    order = sorted(LOOKS[look], key=lambda name: (len(LOOKS[look][name]) > 4,
                                                  look == "earthwork" and name not in ("grenade", "pd")))
    for piece in order:
        raw_name, guide_name, index, box = LOOKS[look][piece][:4]
        dress_from = LOOKS[look][piece][4] if len(LOOKS[look][piece]) > 4 else None
        raw_path = HERE / "raw" / raw_name
        fallback = False
        if not raw_path.exists() and look == "earthwork":
            raw_name, fallback = raw_name.replace("earthwork-", "brutalist-"), True
        image, guide = sheet(raw_name, guide_name)
        fit = fit_registered if look in GUIDE_FIT else fit_object
        fitted_piece = fit(image, guide, index, box)
        if piece.startswith("collar"):
            collar = fitted_piece.resize((PPC, PPC), Image.LANCZOS)
            collar = fill_pit(collar)
            collar.save(target / f"{piece}.png")
            continue
        if piece == "plinth":
            plinth_with_shadow(fitted_piece, 3 * PPC).save(target / "plinth.png")
            continue
        size = 3 * PPC if piece == "heavy" else SMALL
        cells = 3.0 if piece == "heavy" else SMALL_CELLS
        composed_piece = fitted_piece.resize((size, size), Image.LANCZOS)
        if fallback:
            composed_piece = sand_khaki(composed_piece)
        if look == "earthwork" and piece in ("autogun", "cannon"):
            from field_dress import dress
            composed_piece = dress(composed_piece, composed["grenade"])
        if dress_from is not None:
            from field_dress import colour_transfer
            composed_piece = colour_transfer(composed_piece, composed[dress_from])
        if reference is not None and look not in GUIDE_FIT:
            composed_piece = match_extent(composed_piece, reference[piece])
        composed[piece] = composed_piece
        head, barrel, _ = split(composed_piece)
        shaded = contact_shadow(size, SHADOW[piece])
        shaded.alpha_composite(head)
        shaded.save(target / f"{piece}-head.png")
        barrel.save(target / f"{piece}-barrel.png")
        measured[piece] = {"visualCells": cells, "muzzles": muzzles(barrel, cells)}
    return composed, measured


def main():
    reference, measured = export_look("brutalist", None)
    report = {"brutalist": measured}
    for look in ("earthwork", "derelict", "modular", "barrier", "export", "blockwork", "composite", "deployed"):
        report[look] = export_look(look, reference)[1]
    (HERE / "muzzles.json").write_text(json.dumps(report, indent=2) + "\n")
    for look, pieces in report.items():
        print(look, {k: v["muzzles"] for k, v in pieces.items()})


if __name__ == "__main__":
    main()
