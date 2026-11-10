"""Build the ruins-themes prop sheet from the accepted ImageGen raws (``RuinsThemeDressing``).

Each piece is cut mechanically from its raw at its guide frame (``make_guide.py``) carried through the raw's measured
scale, widened by half a gutter so a piece that bled past its block keeps its edge, then packed by
``art-source/doodads/stitch_atlas.py`` at 128 px a cell, 24 cells wide, which crops to the visible pixels and fits each piece to its
footprint. Floor patches lose their faint outer ring (the near-white feather ImageGen drew round every spill, which
reads as a halo on the floor) and are capped below opaque so the floor shows through. Run from the repository root:

    python art-source/ruins-themes/export.py

Writes ``mod/graphics/doodads/ruins-themes.png`` and ``mod/data/tilesets/ruins-themes.tileset.json``.
"""

import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(ROOT / "art-source" / "doodads"))

import make_guide  # noqa: E402
from stitch_atlas import BuildOptions, build_atlas  # noqa: E402

# Family -> accepted raw, and the pieces of it that ship (the concept sheets' own patches do not).
RAWS = {
    "frost": ("ruins-frost.raw.png", ("cryo-rack", "frost-heatsink")),
    "vats": ("ruins-vats.raw.png", ("culture-vat", "grow-bed")),
    "patches": ("ruins-patches.raw.png", ("frost-patch-a", "frost-patch-b", "growth-patch-a", "growth-patch-b")),
    "plant-store": ("ruins-plant-store.raw.png", ("rack-stripped", "rack-crates", "dock-door")),
    "foundry-a": ("ruins-foundry-a.raw.png", ("crucible", "fallen-ladle", "slag-heap", "pour-pulpit")),
    "foundry-tip": ("ruins-foundry-tip.raw.png", ("crucible-tipped",)),
    "foundry-b": ("ruins-foundry-b.raw.png", ("mould-grid", "grate-catwalk")),
    "products-a": ("ruins-products-a.raw.png", tuple(f"{p}-{s}" for p in ("cryo-unit", "vat-unit")
                                                     for s in ("whole", "half", "wrecked"))),
    "products-b": ("ruins-products-b.raw.png", tuple(f"{p}-{s}" for p in ("drone-frame", "forge-core")
                                                     for s in ("whole", "half", "wrecked"))),
    "products-c": ("ruins-products-c.raw.png", tuple(f"{p}-{s}" for p in ('core-cabinet', 'field-housing', 'magnet-segment')
                                                     for s in ("whole", "half", "wrecked"))),
    "products-d": ("ruins-products-d.raw.png", tuple(f"{p}-{s}" for p in ('catalyst-bed', 'spool-drum')
                                                     for s in ("whole", "half", "wrecked"))),
    "products-e": ("ruins-products-e.raw.png", tuple(f"{p}-{s}" for p in ('bore-head', 'projector-stack')
                                                     for s in ("whole", "half", "wrecked"))),
}

# The mega forge hall's head ships on a sheet of its own (``ruins-forge``): the great crucible alone is 12x16 cells.
# The era audit (owner: an ore car "does not make sense for a space faring civilization") retired the forge's
# 20th-century movers and benches: the ore cars, rail bed, slag cars, ladle on its hook, fettling bench, inspection
# table and skid marks no longer ship; the racks, runway and girder are redrawn on ``forge-era-b`` under their ids.
FORGE_LINE = ("tapping-furnace", "pour-head", "mould-master")
FORGE_FLOOR = ("pallet-bay", "trench-cover", "drain-gutter", "floor-drain", "cable-tray", "cable-tray-fallen")
FORGE_ERA_A = ("mag-rail", "mag-rail-curve", "mag-rail-end", "feed-pod", "feed-pod-derailed", "slag-skid",
               "slag-skid-crashed", "thrust-wash")
FORGE_ERA_B = ("ladle-lift", "ladle-lift-tipped", "finishing-cell", "metrology-bed", "cooling-rack",
               "cooling-rack-castings", "crane-runway", "fallen-girder")
FORGE_WEAR = ("oil-stain", "oil-trail", "coolant-stain", "scorch-mark", "scorch-arc", "line-pulpit",
              "recorder-console")
FORGE_RAWS = {"forge-mega": ("ruins-forge-mega.raw.png", ("great-crucible", "furnace-core")),
              "forge-line": ("ruins-forge-line.raw.png", FORGE_LINE),
              "forge-floor": ("ruins-forge-floor.raw.png", FORGE_FLOOR),
              "forge-wear": ("ruins-forge-wear.raw.png", FORGE_WEAR),
              "forge-era-a": ("ruins-forge-era-a.raw.png", FORGE_ERA_A),
              "forge-era-b": ("ruins-forge-era-b.raw.png", FORGE_ERA_B)}
# The delve's weather (``RuinsWeather``): flat decals on a sheet of their own, faded as the forge's stains are. The
# crack network and crack run came back as thorny twigs, not cracks, and do not ship.
WEATHER = ("fallen-ceiling", "fallen-panel", "seep-stain", "dust-tongue", "sand-tongue", "snow-tongue", "ash-tongue",
           "sulphur-tongue", "root-mat", "root-run")
WEATHER_RAWS = {"delve-weather": ("ruins-delve-weather.raw.png", WEATHER)}
# The theme works halls' pieces (``ruins-theme-halls``) ship on a third sheet, ``ruins-works``: every piece is cut at
# its exact guide frame, so a run and a wall block keep their edges flush.
WORKS_RAWS = {f: (f"ruins-{f}.raw.png", tuple(e[0] for e in make_guide.FAMILIES[f] if e))
              for f in ("works-kit", "works-runs", "works-blocks", "works-vessels-a", "works-vessels-b",
                        "works-stations", "works-plant-a", "works-plant-b", "works-cells-a", "works-cells-b",
                        "works-rows-a", "works-rows-b", "works-mega-a", "works-mega-b")}
WORKS = tuple(pid for f in WORKS_RAWS for pid in WORKS_RAWS[f][1])
# Pieces laid two ways from one drawing: the cage fence and ring segment run north-south too (turned a quarter), and a
# charge cell, drawn open to the east for the west bays, opens west for the east bays (mirrored).
TURNED = {"cage-fence": "cage-fence-v", "ring-segment": "ring-segment-ew"}
MIRRORED = {"charge-cell": "charge-cell-west", "charge-cell-open": "charge-cell-open-west"}
# The forge floor's runs, laid end to end north-south: each is healed into a seamless repeat along its length as the
# foundry textures are (``art-source/voids/foundry/derive.py``): a band at each end blended with the piece rolled by
# half its length. Their guides repeat with a period that divides the length, so the rolled copy meets in step.
RUNS = ("mag-rail", "trench-cover", "drain-gutter", "crane-runway", "cable-tray", "works-conveyor", "coolant-return",
        "coolant-loop", "transfer-pipe", "tensile-bed", "spoil-conveyor", "coolant-header", "feed-manifold", "pipe-run",
        "powder-line")
RUN_BAND = .08

# The ruins' leaf doors ship as single images beside the ship's (``graphics/doors/``), at the leaves' 160 px a cell,
# for ``LeafDoorArt``. A leaf must be solid to its ends, since the game cuts it from its meeting end: it is cut a
# little inside its guide frame, past any rounded corner ImageGen drew, and stretched back to its exact size.
DOORS_RAW = "ruins-doors.raw.png"
DOOR_PX = 160
DOOR_LEAVES = {"ruins-leaf", "ruins-leaf-sealed", "ruins-cargo-leaf", "ruins-blast-leaf"}
LEAF_INSET = 0.03

# Pieces cut at their exact guide frame instead of half a gutter wider: the dock door bled into the gutter beside
# the racks, so a wider rack cut picks up its edge.
# Products are cut at their frame too and keep the whole frame as their canvas, so a product's three states share one
# scale and the wrecked one's fallen fragments stay where they lie.
TIGHT = {"rack-stripped", "rack-crates", "crucible", "crucible-tipped", "mould-grid", "grate-catwalk",
         "great-crucible", "furnace-core", *WORKS, *FORGE_LINE, *FORGE_FLOOR, *FORGE_ERA_A, *FORGE_ERA_B, "line-pulpit",
         "recorder-console", *WEATHER}.union(*(RAWS[f][1] for f in RAWS if f.startswith("products-")))

# The plant line, cut by measured boxes in the raw's own pixels rather than by its guide: ImageGen drew the sheet a
# sixth narrower than the guide and joined the module to the line end by its bed. The module and the line end share
# one vertical window centred on the bed (rows 247-462), so the bed's cross-section is the same at every seam, and
# each is cut at the middle of a roller gap one pitch (about 37.8 px) in from its end cap: the module from gap 75 to
# gap 1020 (25 pitches), the line end from the hopper's edge to gap 1539. Each is then stretched to its exact
# footprint, so a module meets the next module, or a line end, gap to gap. The cradle is cut at its own bounds.
CELL_PX = 128
LINE_ROWS = (65, 644)
PLANT_LINE = {
    "line-module": ((75, LINE_ROWS[0], 1020, LINE_ROWS[1]), (6, 3)),
    "line-end": ((1083, LINE_ROWS[0], 1539, LINE_ROWS[1]), (3, 3)),
    "empty-cradle": ((1612, 86, 2145, 627), (3, 3)),
}


def cut_plant_line():
    raw = Image.open(HERE / "raw" / "ruins-plant-line.raw.png").convert("RGBA")
    for pid, (box, (fx, fy)) in PLANT_LINE.items():
        yield pid, raw.crop(box).resize((fx * CELL_PX, fy * CELL_PX), Image.LANCZOS)
# Crucibles whose melt ships as its own overlay on the same footprint, so the engine can pulse it: the guide's MELT
# pixels, carried to the raw's scale and feathered a little, go to the overlay and the rest to the body; the two
# alphas sum to the piece's own, so laid together they are the piece as drawn.
MELT_SPLIT = {"crucible": "crucible-melt", "crucible-tipped": "crucible-tipped-spill",
              "great-crucible": "great-crucible-melt", "tapping-furnace": "tapping-furnace-melt",
              "ladle-lift": "ladle-lift-melt", "ladle-lift-tipped": "ladle-lift-tipped-pour",
              "slag-skid": "slag-skid-melt", "slag-skid-crashed": "slag-skid-crashed-spill"}
MELT_FEATHER = 2.5


def split_melt(piece, guide_frame):
    rgb = np.asarray(guide_frame.convert("RGB"))
    melt = Image.fromarray(np.where((rgb == make_guide.MELT[:3]).all(axis=2), 255, 0).astype(np.uint8), "L")
    melt = melt.resize(piece.size, Image.BILINEAR).filter(ImageFilter.GaussianBlur(MELT_FEATHER))
    alpha = piece.getchannel("A")
    body, over = piece.copy(), piece.copy()
    body.putalpha(ImageChops.multiply(alpha, ImageChops.invert(melt)))
    over.putalpha(ImageChops.multiply(alpha, melt))
    return body, over


# Grades applied after the cut, piece by piece, where a raw read wrong in the hall (the art audit):
# - hazard striping on the spool drum and drone frame was the loudest thing on them at play zoom: its yellow is pulled
#   down to a worn ochre, kept as a mark but no longer a band of colour;
# - the upright crucible's melt was the brightest thing on screen: its light is taken down and warmed off white, so
#   the engine's pulse rides from dull orange to orange rather than from yellow to white.
# - the forge line came back with the same bright trim, and its melt as bright as the crucible's was.
MUTE_HAZARD = ("spool-drum", "drone-frame", *WORKS, *FORGE_LINE, *FORGE_FLOOR, *FORGE_ERA_A, *FORGE_ERA_B, "line-pulpit",
               "recorder-console")
GRADE_MELT = ("crucible-melt", "great-crucible-melt", "tapping-furnace-melt", "ladle-lift-melt",
              "ladle-lift-tipped-pour", "slag-skid-melt", "slag-skid-crashed-spill")
MELT_GRADE = (0.80, 0.62, 0.50)


# Floor paint is flat: ImageGen drew the pallet bay's corners as raised ochre angle, so the bay is painted from its
# guide's own marks instead, in worn ochre with scuffed-through patches and a fine speckle, and no light or edge.
PAINT = (150, 128, 74)
PAINT_ALPHA = 190


def flat_paint(guide_frame, size):
    rgb = np.asarray(guide_frame.convert("RGB")).astype(int)
    mark = (np.abs(rgb - np.array(make_guide.PAINT[:3])).sum(axis=2) < 30).astype(np.uint8) * 255
    mask = np.asarray(Image.fromarray(mark, "L").resize(size, Image.BILINEAR)).astype(np.float32) / 255
    rng = np.random.default_rng(7)
    w, h = size
    wear = np.asarray(Image.fromarray((rng.random((h // 12 + 1, w // 12 + 1)) * 255).astype(np.uint8), "L")
                      .resize(size, Image.BICUBIC)).astype(np.float32) / 255
    speck = rng.random((h, w)).astype(np.float32)
    alpha = mask * PAINT_ALPHA * np.clip((wear - 0.1) * 4, 0, 1) * (0.72 + 0.28 * speck)
    tint = (0.88 + 0.12 * wear)[..., None] * np.array(PAINT, dtype=np.float32)
    out = np.dstack([np.clip(tint, 0, 255), alpha]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def heal_run(piece):
    a = np.asarray(piece.convert("RGBA")).astype(np.float32)
    h = a.shape[0]
    t = np.arange(h, dtype=np.float32) / (h - 1)
    e = np.clip(np.minimum(t, 1 - t) / RUN_BAND, 0, 1)
    keep = (e * e * (3 - 2 * e))[:, None, None]
    a = a * keep + np.roll(a, h // 2, axis=0) * (1 - keep)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def mute_hazard(piece):
    a = np.asarray(piece.convert("RGBA")).astype(np.float32)
    rgb = a[..., :3] / 255
    mx, mn = rgb.max(axis=2), rgb.min(axis=2)
    sat = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    # Yellow to amber: red and green both high, blue well below them.
    yellow = (sat > 0.35) & (r > 0.35) & (g > 0.6 * r) & (b < 0.6 * g)
    grey = rgb.mean(axis=2, keepdims=True)
    muted = (grey + (rgb - grey) * 0.35) * 0.82
    rgb = np.where(yellow[..., None], muted, rgb)
    a[..., :3] = np.clip(rgb * 255, 0, 255)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def grade_melt(piece):
    a = np.asarray(piece).astype(np.float32)
    a[..., :3] *= np.array(MELT_GRADE, dtype=np.float32)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def grade(pid, piece):
    if pid == "pallet-bay":
        return piece
    if pid == "thrust-wash":
        # Drawn a pale brown that vanished on the hall floor: taken down to soot.
        a = np.asarray(piece).astype(np.float32)
        a[..., :3] *= 0.45
        return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")
    if pid in GRADE_MELT:
        return grade_melt(piece)
    if pid.startswith(MUTE_HAZARD):
        return mute_hazard(piece)
    return piece


# Patch alpha: below FEATHER is the halo ring and goes; the rest is rescaled to at most the family's cap. Frost is
# pale and reads as a white disc on the ruin floor unless it lets more of the floor through than growth does.
FEATHER = 110
CAP = {"frost": 150, "growth": 205, "oil": 225, "coolant": 215, "scorch": 225, "thrust": 200,
       "fallen": 245, "seep": 200, "dust": 185, "sand": 215, "snow": 220, "ash": 210, "sulphur": 200, "root": 225}
# The forge's stains were drawn translucent to their edges, so the halo cut is lower: only the faintest haze goes.
STAIN_FEATHER = 30
# A stain drawn fainter than the rest is stretched so its densest part reaches the cap: the thrust wash came back at
# most 162 alpha.
STAIN_TOP = {"thrust-wash": 140}


def cut(family):
    raw_name, keep = {**RAWS, **FORGE_RAWS, **WORKS_RAWS, **WEATHER_RAWS}[family]
    raw = Image.open(HERE / "raw" / raw_name).convert("RGBA")
    guide = Image.open(ROOT / "art-source" / "templates" / f"ruins-{family}-guide.png")
    sx, sy = raw.width / guide.width, raw.height / guide.height
    half = make_guide.GUTTER / 2
    for pid, x, y, w, h, kind in make_guide.frames(family):
        if pid not in keep:
            continue
        m = 0 if pid in TIGHT else half
        box = (round((x - m) * sx), round((y - m) * sy), round((x + w + m) * sx), round((y + h + m) * sy))
        piece = raw.crop(box)
        if pid in WORKS:
            # ImageGen leaves a faint coloured haze (alpha 1 to 3) in the gutters of the works sheets: cleared.
            piece.putalpha(piece.getchannel("A").point(lambda v: 0 if v < 8 else v))
        if pid in RUNS:
            piece = heal_run(piece)
        if kind in ("organic", "streak") or pid in WEATHER:
            f = STAIN_FEATHER if pid in FORGE_WEAR or pid in WEATHER or pid == "thrust-wash" else FEATHER
            top = STAIN_TOP.get(pid, 255)
            alpha = piece.getchannel("A").point(
                lambda v, cap=CAP[pid.split("-")[0]], f=f, top=top:
                0 if v < f else min(cap, round((v - f) * cap / (top - f))))
            piece.putalpha(alpha)
        if pid == "pallet-bay":
            piece = flat_paint(guide.crop((x, y, x + w, y + h)), piece.size)
        if pid in MELT_SPLIT:
            body, over = split_melt(piece, guide.crop((x, y, x + w, y + h)))
            yield pid, body
            yield MELT_SPLIT[pid], over
            continue
        yield pid, piece
        if pid in TURNED:
            yield TURNED[pid], piece.rotate(90, expand=True)
        if pid in MIRRORED:
            yield MIRRORED[pid], piece.transpose(Image.FLIP_LEFT_RIGHT)


def export_doors():
    raw = Image.open(HERE / "raw" / DOORS_RAW).convert("RGBA")
    guide = Image.open(ROOT / "art-source" / "templates" / "ruins-doors-guide.png")
    sx, sy = raw.width / guide.width, raw.height / guide.height
    cell = make_guide.FAMILY_CELL["doors"]
    out = ROOT / "mod" / "graphics" / "doors"
    for pid, x, y, w, h, kind in make_guide.frames("doors"):
        i = LEAF_INSET if pid in DOOR_LEAVES else 0
        box = (round((x + w * i) * sx), round((y + h * i) * sy), round((x + w * (1 - i)) * sx),
               round((y + h * (1 - i)) * sy))
        size = (round(w / cell * DOOR_PX), round(h / cell * DOOR_PX))
        piece = raw.crop(box).resize(size, Image.LANCZOS)
        if pid in DOOR_LEAVES:
            piece.putalpha(255)
        piece.save(out / f"{pid}.png", optimize=True)
        print("door", pid, size)


# The works pieces are split over four sheets, 32 cells wide, so each pads to a smaller power-of-two texture than one
# tall sheet would (``texture-fit.md``) and a hall loads only the sheets its pieces are on. They share one metadata file.
WORKS_SHEETS = {
    "ruins-works-kit": ("works-kit", "works-runs", "works-blocks", "works-stations"),
    "ruins-works-plant": ("works-vessels-a", "works-vessels-b", "works-plant-a", "works-plant-b"),
    "ruins-works-bays": ("works-cells-a", "works-cells-b", "works-rows-a", "works-rows-b"),
    "ruins-works-giants": ("works-mega-a", "works-mega-b"),
}


def build_works():
    for sheet, families in WORKS_SHEETS.items():
        build_sheet(sheet, "_atlas-works.json", {f: WORKS_RAWS[f] for f in families}, columns=32)


def build_forge():
    build_sheet("ruins-forge", "_atlas-forge.json", FORGE_RAWS)


def build_weather():
    build_sheet("ruins-delve-weather", "_atlas-weather.json", WEATHER_RAWS, columns=16)


def build_sheet(sheet, metadata, raws, columns=24):
    with tempfile.TemporaryDirectory() as scratch:
        staged = Path(scratch)
        for family in raws:
            for pid, piece in cut(family):
                grade(pid, piece).save(staged / f"{pid}.png")
        image, manifest, count = build_atlas(BuildOptions(
            input_dir=staged,
            output_image=ROOT / "mod" / "graphics" / "doodads" / f"{sheet}.png",
            output_manifest=ROOT / "mod" / "data" / "tilesets" / f"{sheet}.tileset.json",
            sheet_path=f"graphics/doodads/{sheet}.png",
            metadata_path=HERE / metadata,
            columns=columns,
            cell_size=CELL_PX,
            padding=4,
            id_prefix="doodad.",
            resample="lanczos",
            alpha_threshold=1,
            allow_opaque=False,
            only_prefix=None,
        ))
    print(f"Packed {count} {sheet} assets")


def main() -> None:
    export_doors()
    build_forge()
    build_weather()
    build_works()
    with tempfile.TemporaryDirectory() as scratch:
        staged = Path(scratch)
        for family in RAWS:
            for pid, piece in cut(family):
                grade(pid, piece).save(staged / f"{pid}.png")
        for pid, piece in cut_plant_line():
            piece.save(staged / f"{pid}.png")
        image, manifest, count = build_atlas(BuildOptions(
            input_dir=staged,
            output_image=ROOT / "mod" / "graphics" / "doodads" / "ruins-themes.png",
            output_manifest=ROOT / "mod" / "data" / "tilesets" / "ruins-themes.tileset.json",
            sheet_path="graphics/doodads/ruins-themes.png",
            metadata_path=HERE / "_atlas.json",
            columns=24,
            cell_size=CELL_PX,
            padding=4,
            id_prefix="doodad.",
            resample="lanczos",
            alpha_threshold=1,
            allow_opaque=False,
            only_prefix=None,
        ))
    print(f"Packed {count} assets")
    print(image)
    print(manifest)


if __name__ == "__main__":
    main()
