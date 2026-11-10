"""Geometry guides for the ruins vault-theme props (``ruins-theme-art.md`` brief, ``RuinsThemeDressing``).

One sheet per prop family at 320 px a cell: each prop is a flat grey block-out on its exact cell footprint, with
transparent gutters between frames, so ImageGen reskins the shapes and keeps their size and placement. Floor patches
are drawn as low irregular blots (the concept sheets' ``blot``; the production ``patches`` sheet's smoother ``organic``
spills) whose outline must stay inside their frame. Run from the repository root:

    python art-source/ruins-themes/make_guide.py

Writes ``art-source/templates/ruins-<family>-guide.png`` and prints each frame (x, y, w, h) in guide pixels.
"""
import math
import random

from PIL import Image, ImageChops, ImageDraw

import hall_kinds
import weather_kinds
import bridge_kinds
import rooms_kinds
import rooms_list

CELL = 320
GUTTER = 64

# Each family: (id, cells w, cells h, kind) laid left to right, tops aligned; None starts a new row below.
FAMILIES = {
    "frost": [("cryo-rack", 1, 2, "box"), ("frost-heatsink", 2, 1, "fins"), ("frost-patch", 2, 2, "blot")],
    "vats": [("culture-vat", 2, 2, "round"), ("grow-bed", 2, 1, "trough"), ("growth-patch", 2, 2, "blot")],
    "patches": [("frost-patch-a", 2, 2, "organic"), ("frost-patch-b", 2, 2, "organic"),
                ("growth-patch-a", 2, 2, "organic"), ("growth-patch-b", 2, 2, "organic")],
    "forge": [("assembly-cradle", 2, 2, "cradle"), ("shaft-head", 3, 3, "shaft"), ("hoist-winch", 2, 1, "drum")],
    # The plant kit every theme's halls share (``ruins-vault-facility``): a line module that joins end to end along
    # its long axis, so its bed runs flush off both short ends with one cross-section; a line end carrying that same
    # cross-section in from its east edge; an empty 3x3 cradle (open middle: the product stands in it).
    "plant-line": [("line-module", 6, 3, "line"), ("line-end", 3, 3, "lineend"), ("empty-cradle", 3, 3, "ring")],
    # Rack rows, stripped and with a few crates left, and a dock door with its leveler, the door on the north edge.
    "plant-store": [("rack-stripped", 6, 2, "rack"), ("dock-door", 4, 2, "dock"), None,
                    ("rack-crates", 6, 2, "rackfull")],
    # Each theme's product (``ruins-vault-facility``), blocked out on a 2x2 frame and shipped on a 3x3 footprint, the
    # frame's margin keeping it just inside the empty cradle's ring and across a line module's bed, in three states left to right: whole (the one sealed unit in the secure store),
    # half-built (on the line) and wrecked (on the line and the floor). One row per product, two products a sheet.
    "products-a": [(f"cryo-unit-{s}", 2, 2, f"cryo:{s}") for s in ("whole", "half", "wrecked")] + [None]
                  + [(f"vat-unit-{s}", 2, 2, f"vat:{s}") for s in ("whole", "half", "wrecked")],
    "products-b": [(f"drone-frame-{s}", 2, 2, f"drone:{s}") for s in ("whole", "half", "wrecked")] + [None]
                  + [(f"forge-core-{s}", 2, 2, f"forge:{s}") for s in ("whole", "half", "wrecked")],
}
# The foundry hall's pieces (``ruins-forge-area``): crucibles whose melt (drawn in MELT) ``export.py`` splits off
# as an overlay the engine can pulse, the loose foundry props, and the overlays that sit on the hall's lava drop: a
# mould grid over a casting bed and a grate catwalk deck over a crossing (span east-west, both ends flush).
FAMILIES["foundry-a"] = [("crucible", 3, 3, "crucible"), ("crucible-tipped", 3, 3, "crucibletip"), None,
                         ("fallen-ladle", 3, 2, "ladle"), ("slag-heap", 2, 2, "slag"), ("pour-pulpit", 2, 2, "pulpit")]
# The toppled crucible on its own sheet, so its spill can be redrawn without touching the rest of foundry-a.
FAMILIES["foundry-tip"] = [("crucible-tipped", 3, 3, "crucibletip")]
FAMILIES["foundry-b"] = [("mould-grid", 8, 5, "mould"), None, ("grate-catwalk", 2, 2, "catwalk")]

# The ruins' leaf doors (``ruins-doors``, ``LeafDoorArt``): each leaf runs along x, its jamb end left and its meeting
# end right, seen straight down; sizes in cells, along x by across. Shipped at 160 px a cell like the ship's leaves, so
# drawn here at twice that.
FAMILIES["doors"] = [("ruins-leaf", 1.5, 0.55, "leaf"), ("ruins-leaf-sealed", 1.5, 0.55, "leafsealed"),
                     ("ruins-cargo-leaf", 1.0, 0.7, "cargoleaf"), ("ruins-blast-leaf", 1.5, 0.9, "blastleaf"), None,
                     ("ruins-leaf-torn", 1.5, 0.8, "leaftorn"), ("ruins-blast-clamp", 0.9, 0.95, "clamp"),
                     ("ruins-door-jamb", 0.5, 0.6, "jamb"), ("ruins-door-rubble", 1.0, 1.0, "rubble")]
# The mega forge hall's head (``ruins-themed-environments``, pick b1): the great crucible, 12x16, its pour spout on
# the east edge onto the pour line and its control core (the prize) housed on its west flank; and that core cut free
# on its skid, 3x3. Drawn at a smaller guide scale so the great crucible's guide stays a size ImageGen takes whole.
FAMILIES["forge-mega"] = [("great-crucible", 12, 16, "greatcrucible"), ("furnace-core", 3, 3, "furnacecore")]
# The mega forge hall's pour line (``ruins-themed-environments``, art wanted): the tapping furnace, the ladle on its
# crane hook upright and tipped mid-pour (the melt in each split off as an overlay), the ore car on its rail and
# derailed, cooling racks, the fettling bench and inspection table, the line's pour head and mould master, and a crane
# girder fallen to the floor.
FAMILIES["forge-line"] = [("tapping-furnace", 6, 7, "furnace"), ("ladle-hook", 3, 3, "ladle-up"),
                          ("ladle-hook-tipped", 3, 3, "ladle-tip"), ("ore-car", 2, 3, "orecar"),
                          ("ore-car-derailed", 2, 3, "orecar-off"), None,
                          ("cooling-rack", 2, 4, "coolrack"), ("cooling-rack-castings", 2, 4, "coolrack-full"),
                          ("fettling-bench", 2, 2, "bench"), ("inspection-table", 2, 2, "table"),
                          ("pour-head", 2, 2, "pourhead"), ("mould-master", 2, 2, "mouldmaster"), None,
                          ("fallen-girder", 7, 1, "girder")]
# The forge floor: the hall's movers and runs. Runs (rail bed, trench cover, drain gutter, crane runway, cable tray)
# run north-south and fill their frame edge to edge, with a pattern whose period divides the frame's length, so
# ``export.py`` heals each into a seamless repeat along its run. The curve meets the straight bed's cross-section at
# its south and east edges (rails at 0.3 and 0.7 of the bed's width).
FAMILIES["forge-floor"] = [("rail-bed", 2, 2, "rail"), ("rail-bed-curve", 4, 4, "railcurve"),
    ("rail-buffer", 2, 2, "railbuffer"), ("slag-car", 2, 3, "slagcar"), ("slag-car-tipped", 3, 3, "slagtip"),
    ("pallet-bay", 3, 3, "palletbay"), None,
    ("trench-cover", 2, 2, "trench"), ("drain-gutter", 1, 2, "gutter"), ("floor-drain", 1, 1, "drain"),
    ("crane-runway", 1, 4, "runway"), ("cable-tray", 1, 2, "tray"), ("cable-tray-fallen", 1, 4, "trayfallen")]
# The forge's wear and its b2 consoles: stains laid along the traffic (low organic blots and streaks, faded in
# ``export.py`` as the patches are), the line pulpit's roof and the recorder console.
FAMILIES["forge-wear"] = [("oil-stain", 2, 2, "organic"), ("oil-trail", 1, 3, "streak"),
    ("coolant-stain", 2, 2, "organic"), ("scorch-mark", 2, 2, "organic"), ("scorch-arc", 2, 2, "organic"),
    ("skid-marks", 2, 3, "streak"), None, ("line-pulpit", 3, 3, "linepulpit"), ("recorder-console", 1, 2, "recorder")]
# The forge's movers in the spacefaring era (owner: an ore car "does not make sense for a space faring
# civilization"), matching the raid mine's mass driver and hover haulers. ``forge-era-a``: the feed mag-rail (runs
# north-south, coil rings every half module so it heals in step; the curve meets it at its south and east edges),
# its capsule feed pods and the hover slag skid. ``forge-era-b``: the ladle in its manipulator yoke, the robot
# finishing cell and metrology bed, and the racks, runway and girder redrawn as automated engineered plant.
FAMILIES["forge-era-a"] = [("mag-rail", 2, 2, "maglev"), ("mag-rail-curve", 4, 4, "maglevcurve"),
    ("mag-rail-end", 2, 2, "maglevend"), ("feed-pod", 2, 3, "pod"), ("feed-pod-derailed", 2, 3, "pod-off"), None,
    ("slag-skid", 3, 3, "slagskid"), ("slag-skid-crashed", 3, 3, "slagcrash"), ("thrust-wash", 2, 3, "streak")]
FAMILIES["forge-era-b"] = [("ladle-lift", 3, 3, "yoke-up"), ("ladle-lift-tipped", 3, 3, "yoke-tip"),
    ("finishing-cell", 2, 2, "robotcell"), ("metrology-bed", 2, 2, "metrology"), None,
    ("cooling-rack", 2, 4, "stacker"), ("cooling-rack-castings", 2, 4, "stacker-full"),
    ("crane-runway", 1, 4, "linearbeam"), ("fallen-girder", 7, 1, "beamdown")]
# The theme works halls' pieces (``ruins-theme-halls``), drawn by ``hall_kinds``; a kind ``run/fallen`` is that run
# fallen from overhead. ``works-kit``: what several halls share: the cold head, heat exchanger and coil winder, one
# conveyor bed every hall's line lays, a wall service drop per service and the 3x1 console run.
FAMILIES["works-kit"] = [("cold-head", 6, 6, "coldhead"), ("heat-exchanger", 3, 3, "exchanger"),
    ("coil-winder", 3, 3, "winder"), ("works-conveyor", 2, 2, "conveyor"), None,
    ("coolant-drop", 1, 2, "drop/coolant"), ("nutrient-drop", 1, 2, "drop/nutrient"), ("powder-drop", 1, 2, "drop/powder"),
    ("power-drop", 1, 2, "drop/power"), ("steam-drop", 1, 2, "drop/steam"), ("line-console", 3, 1, "console")]
# ``works-runs``: the halls' floor runs (2x2, north-south) and their overhead runs (1x2) with each fallen (1x4).
FAMILIES["works-runs"] = [("coolant-return", 2, 2, "trench-frost"), ("coolant-loop", 2, 2, "twinpipe"),
    ("transfer-pipe", 2, 2, "bigpipe"), ("tensile-bed", 2, 2, "tensile"), ("spoil-conveyor", 2, 2, "troughbelt"), None,
    ("coolant-header", 1, 2, "header"), ("coolant-header-fallen", 1, 4, "header/fallen"),
    ("feed-manifold", 1, 2, "manifoldrun"), ("feed-manifold-fallen", 1, 4, "manifoldrun/fallen"),
    ("pipe-run", 1, 2, "piperun"), ("pipe-run-fallen", 1, 4, "piperun/fallen"),
    ("powder-line", 1, 2, "duct"), ("powder-line-fallen", 1, 4, "duct/fallen")]
# ``works-blocks``: walls laid a cell at a time (shield block, blast wall, baffle, cage fence) flush to their edges so
# a run reads as one wall, their rubble, the dispatch crates, and the hover skids and mag-rail carriers.
FAMILIES["works-blocks"] = [("waste-skid", 3, 3, "skid/tank"), ("reject-skid", 3, 3, "skid/bin"),
    ("bed-changer-derailed", 3, 3, "skid/changer-crashed"), ("unit-carrier", 2, 3, "carrier"),
    ("unit-carrier-derailed", 2, 3, "carrier/derailed"), None,
    ("shield-block", 1, 1, "shieldblock"), ("shield-rubble", 1, 1, "rubble/shield"),
    ("blast-wall", 1, 1, "blastwall"), ("blast-baffle", 1, 1, "baffle"), ("blast-rubble", 1, 1, "rubble/blast"),
    ("cage-fence", 1, 1, "fence"), ("housing-crate", 3, 2, "crate/housing"), ("stack-crate", 3, 2, "crate/stack"),
    None, ("hardware-crate", 3, 2, "crate/hardware")]
# ``works-vessels-a`` and ``-b``: the halls' round vessels and towers seen from above, two sheets so each keeps its
# detail: silos, the reactor column, tanks, the draw tower, coating chamber and lattice tank.
FAMILIES["works-vessels-a"] = [("feedstock-silo", 5, 5, "silo"), ("feedstock-silo-split", 5, 5, "silo/split"),
    ("reactor-column", 5, 5, "tank/column"), None, ("product-tank", 3, 3, "tank/product"),
    ("lattice-tank", 2, 2, "tower/lattice"), ("coating-chamber", 4, 4, "tower/coating")]
FAMILIES["works-vessels-b"] = [("nutrient-tank", 4, 4, "tank/nutrient"), ("feed-tank", 4, 4, "tank/feed"),
    None, ("precursor-tank", 4, 4, "tank/precursor"), ("draw-tower", 4, 4, "tower/draw")]
# ``works-stations``: the halls' benches and small stations, and their 3x3 presses, sealers and anchor.
FAMILIES["works-stations"] = [("lattice-bench", 3, 2, "bench/lattice"), ("decanter", 2, 2, "bench/decanter"),
    ("powder-hopper", 2, 2, "bench/hopper"), ("arming-bench", 2, 2, "bench/arming"), ("test-pylon", 2, 2, "bench/pylon"),
    ("emitter-bench", 2, 2, "bench/emitter"), None,
    ("holo-pedestal", 2, 2, "bench/pedestal"), ("charge-hopper", 2, 2, "bench/chargehopper"),
    ("beam-diagnostics", 2, 2, "bench/diagnostics"), ("firing-point", 3, 1, "bench/firing"),
    ("dancer-tower", 1, 2, "bench/dancer"), ("guide-roller", 2, 1, "bench/roller"),
    ("guide-roller-fallen", 2, 1, "bench/roller-fallen"), None,
    ("seal-press", 3, 3, "press/seal"), ("vat-filler", 3, 3, "press/filler"), ("cabinet-sealer", 3, 3, "press/cabinet"),
    ("filling-press", 3, 3, "press/filling"), ("tether-anchor", 3, 3, "press/anchor")]
FAMILY_CELL = {"doors": 320, "forge-mega": 112, "forge-line": 160, "forge-floor": 160, "forge-wear": 160,
               "forge-era-a": 160, "forge-era-b": 160, "works-kit": 160, "works-runs": 160,
               "works-blocks": 160, "works-vessels-a": 160, "works-vessels-b": 160,
               "works-stations": 160}
# ``works-plant-a`` and ``-b``: the halls' mid-size plant: chillers, the preheat furnace, the cold manifold, cryostat,
# feed pumps and ice ridge; the hoist and spool winders, beam dump, ring segment, RF cavity, linac and two racks.
FAMILIES["works-plant-a"] = [("chiller", 5, 4, "plant/chiller"), ("chiller-burst", 5, 4, "plant/chiller-burst"),
    ("preheat-furnace", 5, 4, "plant/preheat"), None, ("cold-manifold", 4, 3, "plant/manifold"),
    ("cryostat", 4, 3, "plant/cryostat"), ("feed-pumps", 4, 2, "plant/pumps"), ("ice-ridge", 3, 2, "plant/ridge")]
FAMILIES["works-plant-b"] = [("hoist-winder", 4, 4, "plant/hoist"), ("winder", 4, 4, "plant/spoolwinder"),
    ("beam-dump", 4, 4, "plant/beamdump"), None, ("ring-segment", 3, 3, "magnet/segment"),
    ("rf-cavity", 2, 3, "magnet/cavity"), ("linac-section", 2, 3, "magnet/linac"), ("cutter-rack", 2, 4, "rack/cutter"),
    ("drum-rack", 2, 4, "rack/drum")]
# ``works-cells``: the bays' big pieces: the charge cell (open to the east; ``export.py`` mirrors it for the east
# bays) whole and torn open, the bore stand; the great vat whole and burst, and the print bed.
FAMILIES["works-cells-a"] = [("charge-cell", 5, 6, "cell/charge"), ("charge-cell-open", 5, 6, "cell/charge-open"),
    ("bore-stand", 5, 6, "cell/borestand")]
FAMILIES["works-cells-b"] = [("culture-vat-large", 4, 4, "cell/vat"), ("culture-vat-burst", 4, 4, "cell/vat-burst"),
    ("print-bed", 6, 6, "cell/printbed")]
# ``works-rows``: long pieces laid east-west (rack rows, the sorter, the stacker rail) and what fell across a bay.
FAMILIES["works-rows-a"] = [("core-rack-row", 6, 2, "row/corerack"), ("launch-rack", 6, 2, "row/launchrack"), None,
    ("core-racks-toppled", 6, 3, "row/toppled"), ("sorter", 6, 3, "row/sorter"), None,
    ("stacker-rail", 6, 2, "row/stackerrail"), ("stacker-toppled", 7, 2, "row/stackerdown")]
FAMILIES["works-rows-b"] = [("reactor-column-fallen", 9, 3, "row/columndown"), ("pipe-rack", 2, 6, "row/piperack"),
    ("director-booth", 2, 5, "row/booth"), None, ("print-gantry-fallen", 7, 2, "row/gantrydown"),
    ("spoil-heap", 4, 3, "row/heap"), ("spoil-slide", 4, 2, "row/slide")]
# ``works-mega``: the giants, drawn smaller so each sheet stays whole: the shaft's headframe and the print gantry
# (overhead, open in the middle so the shaft and bed show through), the proving torus, precursor furnace, proof butt.
FAMILIES["works-mega-a"] = [("headframe", 12, 12, "mega/headframe"), ("print-gantry", 6, 6, "mega/gantry")]
FAMILIES["works-mega-b"] = [("proving-torus", 10, 8, "mega/torus"), ("precursor-furnace", 6, 5, "mega/precursor"),
    None, ("proof-butt", 12, 4, "mega/butt")]
FAMILY_CELL.update({"works-mega-a": 112, "works-mega-b": 112})
# Every works sheet is drawn at 160 px a cell unless it says otherwise.
FAMILY_CELL.update({f: 160 for f in FAMILIES if f.startswith("works-") and f not in FAMILY_CELL})
# The delve's weather (``RuinsWeather``): what the world and the years leave on a dead site's floors. The top row is
# wear every world shares; the bottom row the world's own ground blown in through a mouth, and what grows in.
FAMILIES["delve-weather"] = [("fallen-ceiling", 2, 2, "w/debris"), ("fallen-panel", 1, 1, "w/debris"),
    ("crack-web", 2, 2, "w/crack"), ("crack-run", 1, 3, "w/crack"), ("seep-stain", 2, 2, "organic"),
    ("dust-tongue", 2, 3, "w/fan"), None,
    ("sand-tongue", 2, 3, "w/fan"), ("snow-tongue", 2, 3, "w/fan"), ("ash-tongue", 2, 3, "w/fan"),
    ("sulphur-tongue", 2, 3, "w/fan"), ("root-mat", 2, 2, "organic"), ("root-run", 1, 3, "w/roots")]
FAMILY_CELL["delve-weather"] = 160
# The mega forge's transfer bridge over the breakout's lava pool (``RuinsDelveStage.transferBridge``): a span that
# joins end to end east-west, the abutment at the west bank (mirrored for the east) and a damaged span.
FAMILIES["forge-bridge"] = [("transfer-bridge-span", 2, 4, "b/span"), ("transfer-bridge-end", 2, 4, "b/end"),
                            ("transfer-bridge-span-damaged", 2, 4, "b/damaged")]
FAMILY_CELL["forge-bridge"] = 160
# The ruins rooms' furniture (``ruins-theme-rooms``): the art list and block-out kinds are ``rooms_list.py``.
for _fam, _entries in rooms_list.FAMILIES.items():
    FAMILIES[_fam] = [e[:4] if e else None for e in _entries]
    FAMILY_CELL[_fam] = 160

# The other themes' products, the same way: one row per product.
for _fam, _prods in (("products-c", (("core-cabinet", "core"), ("field-housing", "lamp"), ("magnet-segment", "magnet"))),
                     ("products-d", (("catalyst-bed", "catalyst"), ("spool-drum", "spool"))),
                     ("products-e", (("bore-head", "bore"), ("projector-stack", "projector")))):
    FAMILIES[_fam] = []
    for _pid, _shape in _prods:
        if FAMILIES[_fam]:
            FAMILIES[_fam].append(None)
        FAMILIES[_fam] += [(f"{_pid}-{s}", 2, 2, f"{_shape}:{s}") for s in ("whole", "half", "wrecked")]

# The line's bed: the middle half of its width, flush to the short ends, shared by the module and the line end.
BED = (0.3, 0.7)


def frames(family):
    x, y, row_h = GUTTER, GUTTER, 0
    out = []
    for entry in FAMILIES[family]:
        if entry is None:
            x, y, row_h = GUTTER, y + row_h + GUTTER, 0
            continue
        pid, cw, ch, kind = entry
        cell = FAMILY_CELL.get(family, CELL)
        w, h = round(cw * cell), round(ch * cell)
        out.append((pid, x, y, w, h, kind))
        x += w + GUTTER
        row_h = max(row_h, h)
    return out


BODY, EDGE, DARK, LIGHT = (128, 132, 138, 255), (70, 74, 80, 255), (40, 42, 46, 255), (176, 180, 186, 255)
# The melt in a foundry guide: a colour no other block uses, so ``export.py`` finds it in the guide.
MELT = (214, 120, 48, 255)
# Copper coil and a lit lens in the era guides, so the reskin knows them from plate.
COIL = (150, 98, 64, 255)
GLOW = (236, 200, 120, 255)
# Painted floor marks: ``export.py`` paints these flat from the guide rather than taking ImageGen's.
PAINT = (196, 170, 70, 255)


def crucible_melt(x, y, w, h, kind):
    """The melt's outline in guide pixels: the crucible's open mouth, or the hot core of the toppled one's pool."""
    if kind == "crucible":
        cx, cy, r = x + w / 2, y + h / 2 - 20, w * 0.27
        return [(cx + r * math.cos(a), cy + r * math.sin(a)) for a in (k * math.pi / 60 for k in range(120))]
    return spill_outline(x, y, w, h, 0.72)


def spill_outline(x, y, w, h, scale):
    """The toppled crucible's pool: lobes fanning out from its mouth toward the south-east corner, as metal spreads on
    a floor; ``scale`` 1 is the crusted edge, less the hot core inside it."""
    ox, oy = x + w * 0.54, y + h * 0.52
    pts = [(ox - w * 0.02, oy - w * 0.12 * scale)]
    for k in range(73):
        a = -0.75 + 2.2 * k / 72
        lobes = 1 + 0.16 * math.sin(3 * a + 0.7) + 0.09 * math.sin(7 * a + 1.9)
        r = w * 0.40 * scale * lobes * (0.25 + 0.75 * math.sin(math.pi * k / 72) ** 0.6)
        pts.append((ox + r * math.cos(a), oy + r * math.sin(a)))
    pts.append((ox - w * 0.02, oy + w * 0.12 * scale))
    return pts


def product_shape(name, size, solid):
    """A product's block-out on its own transparent canvas; ``solid`` False leaves the outer shells as bare frames."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    s, i = size, 30
    shell = BODY if solid else None
    if name == "cryo":
        d.rectangle((i, i + 40, s - i, s - i), fill=shell, outline=EDGE, width=12)
        d.rectangle((i + 40, i, s - i - 40, i + 70), fill=DARK)
        for px in (s // 3, 2 * s // 3):
            d.rectangle((px - 26, i - 10, px + 26, i + 40), fill=EDGE)
        for k in range(4):
            y0 = i + 140 + k * 105
            d.rectangle((i + 60, y0, s - i - 60, y0 + 60), fill=LIGHT, outline=EDGE, width=5)
    elif name == "vat":
        for cx, cy in ((i + 40, i + 40), (s - i - 40, i + 40), (i + 40, s - i - 40), (s - i - 40, s - i - 40)):
            d.rectangle((cx - 40, cy - 40, cx + 40, cy + 40), fill=EDGE)
        d.ellipse((i + 30, i + 30, s - i - 30, s - i - 30), fill=shell, outline=EDGE, width=12)
        d.ellipse((s // 2 - 150, s // 2 - 150, s // 2 + 150, s // 2 + 150), fill=LIGHT, outline=EDGE, width=8)
        d.rectangle((s - i - 70, s // 2 - 30, s - i, s // 2 + 30), fill=DARK)
    elif name == "drone":
        c = s // 2
        for cx, cy in ((150, 150), (s - 150, 150), (150, s - 150), (s - 150, s - 150)):
            d.line((c, c, cx, cy), fill=EDGE, width=46)
            d.ellipse((cx - 115, cy - 115, cx + 115, cy + 115), outline=EDGE, width=18)
            d.ellipse((cx - 40, cy - 40, cx + 40, cy + 40), fill=shell or EDGE, outline=EDGE, width=6)
        d.rectangle((c - 110, c - 130, c + 110, c + 130), fill=shell, outline=EDGE, width=12)
        d.rectangle((c - 60, c - 70, c + 60, c + 10), fill=LIGHT)
    elif name == "forge":
        d.rectangle((i, i, s - i, s - i), fill=shell, outline=EDGE, width=14)
        c = s // 2
        for k in range(8):
            a = k / 8 * 2 * math.pi
            ex, ey = c + 210 * math.cos(a), c + 210 * math.sin(a)
            d.rectangle((ex - 42, ey - 42, ex + 42, ey + 42), fill=LIGHT, outline=EDGE, width=6)
        d.ellipse((c - 140, c - 140, c + 140, c + 140), fill=DARK, outline=EDGE, width=14)
        for cx, cy in ((i + 50, i + 50), (s - i - 50, i + 50), (i + 50, s - i - 50), (s - i - 50, s - i - 50)):
            d.ellipse((cx - 20, cy - 20, cx + 20, cy + 20), fill=EDGE)
    elif name == "core":
        d.rectangle((i, i, s - i, s - i), fill=shell, outline=EDGE, width=14)
        for y0 in range(i + 60, s - i - 60, 70):
            d.rectangle((i + 40, y0, i + 110, y0 + 40), fill=DARK)
            d.rectangle((s - i - 110, y0, s - i - 40, y0 + 40), fill=DARK)
        c = s // 2
        d.ellipse((c - 150, c - 150, c + 150, c + 150), fill=EDGE)
        d.ellipse((c - 105, c - 105, c + 105, c + 105), fill=LIGHT, outline=EDGE, width=8)
    elif name == "lamp":
        c, r = s // 2, s // 2 - i
        d.polygon([(c + r * math.cos(a), c + r * math.sin(a)) for a in (k * math.pi / 4 + math.pi / 8 for k in range(8))],
                  fill=shell, outline=EDGE, width=14)
        for k in range(4):
            a = k * math.pi / 2 + math.pi / 4
            d.line((c + 120 * math.cos(a), c + 120 * math.sin(a), c + (r - 30) * math.cos(a), c + (r - 30) * math.sin(a)),
                   fill=EDGE, width=40)
        d.ellipse((c - 130, c - 130, c + 130, c + 130), fill=LIGHT, outline=EDGE, width=14)
        d.ellipse((c - 55, c - 55, c + 55, c + 55), fill=DARK)
    elif name == "magnet":
        cx, cy, ro, ri = -s * 0.25, s * 0.5, s * 1.1, s * 0.62
        d.pieslice((cx - ro, cy - ro, cx + ro, cy + ro), -24, 24, fill=shell, outline=EDGE, width=14)
        d.pieslice((cx - ri, cy - ri, cx + ri, cy + ri), -40, 40, fill=(0, 0, 0, 0))
        rm = (ro + ri) / 2
        d.arc((cx - rm, cy - rm, cx + rm, cy + rm), -25, 25, fill=DARK, width=50)
        for a in (-17, -6, 6, 17):
            t = math.radians(a)
            px, py = cx + rm * math.cos(t), cy + rm * math.sin(t)
            d.rectangle((px - 70, py - 34, px + 70, py + 34), fill=LIGHT, outline=EDGE, width=6)
    elif name == "catalyst":
        d.rectangle((i, i + 60, s - i, s - i - 60), fill=shell, outline=EDGE, width=14)
        for gy in range(3):
            for gx in range(4):
                x0, y0 = i + 50 + gx * 130, i + 120 + gy * 130
                d.rectangle((x0, y0, x0 + 105, y0 + 105), fill=LIGHT, outline=EDGE, width=6)
        d.rectangle((i + 40, i, s - i - 40, i + 60), fill=DARK)
        d.rectangle((i + 40, s - i - 60, s - i - 40, s - i), fill=DARK)
    elif name == "spool":
        d.rectangle((i + 70, i + 60, s - i - 70, s - i - 60), fill=shell, outline=EDGE, width=12)
        for y0 in range(i + 90, s - i - 90, 34):
            d.line((i + 80, y0, s - i - 80, y0), fill=LIGHT, width=12)
        for x0 in (i, s - i - 70):
            d.rectangle((x0, i, x0 + 70, s - i), fill=EDGE)
        d.rectangle((i - 10, s // 2 - 40, s - i + 10, s // 2 + 40), outline=DARK, width=10)
    elif name == "bore":
        c = s // 2
        d.ellipse((i, i, s - i, s - i), fill=shell, outline=EDGE, width=14)
        for k in range(6):
            a = k * math.pi / 3
            ex, ey = c + 185 * math.cos(a), c + 185 * math.sin(a)
            d.ellipse((ex - 55, ey - 55, ex + 55, ey + 55), fill=LIGHT, outline=EDGE, width=8)
            d.line((c, c, c + 260 * math.cos(a + math.pi / 6), c + 260 * math.sin(a + math.pi / 6)), fill=EDGE, width=16)
        d.ellipse((c - 70, c - 70, c + 70, c + 70), fill=DARK)
    elif name == "projector":
        c = s // 2
        d.rectangle((i + 40, i + 40, s - i - 40, s - i - 40), fill=shell, outline=EDGE, width=12)
        for k in range(3):
            a = k * 2 * math.pi / 3 - math.pi / 2
            d.line((c, c, c + 230 * math.cos(a), c + 230 * math.sin(a)), fill=EDGE, width=34)
            d.ellipse((c + 230 * math.cos(a) - 45, c + 230 * math.sin(a) - 45, c + 230 * math.cos(a) + 45,
                       c + 230 * math.sin(a) + 45), fill=LIGHT, outline=EDGE, width=6)
        for r, w in ((170, 12), (115, 10)):
            d.ellipse((c - r, c - r, c + r, c + r), outline=EDGE, width=w)
        d.ellipse((c - 55, c - 55, c + 55, c + 55), fill=LIGHT, outline=EDGE, width=8)
    return im


def product(name, state, size, seed):
    """A product's state, seeded by its id so no two products break the same way.

    Whole as drawn. Half-built: its outer shell a bare frame, plated panel by panel on a 4x4 grid, the lower rows
    whole and the upper ones gappy, so the plating stops at panel seams, never along one straight cut. Wrecked: at
    nearly its whole size, tipped a little, a jagged piece torn from one seeded side, that piece and two smaller ones
    lying against the tear, and a low debris and scorch spill on the floor under the tear; all inside the frame."""
    rng = random.Random(seed.rsplit("-", 1)[0])
    if state == "whole":
        return product_shape(name, size, True)
    if state == "half":
        im = product_shape(name, size, False)
        solid = product_shape(name, size, True)
        mask = Image.new("L", (size, size), 0)
        md = ImageDraw.Draw(mask)
        p = size / 4
        for gy in range(4):
            for gx in range(4):
                keep = (0.0, 0.15, 0.6, 1.0)[gy]
                if rng.random() < keep:
                    md.rectangle((gx * p, gy * p, (gx + 1) * p, (gy + 1) * p), fill=255)
        im.alpha_composite(Image.composite(solid, Image.new("RGBA", (size, size)), mask))
        return im
    body = product_shape(name, int(size * 0.84), True)
    w = body.width
    side = rng.randrange(4)
    # The tear: a jagged bite out of one side, the piece it took kept to fall beside it.
    pts = [(w * 0.5, w * 0.02)]
    for k in range(1, 6):
        t = k / 6
        pts.append((w * (0.5 + 0.45 * t) + rng.uniform(-18, 18), w * (0.06 + 0.42 * t * rng.uniform(0.6, 1.1))))
    pts += [(w * 0.98, w * 0.55), (w * 0.98, w * 0.02)]
    torn = Image.new("L", body.size, 255)
    ImageDraw.Draw(torn).polygon(pts, fill=0)
    torn = torn.rotate(90 * side)
    chunk = Image.composite(body, Image.new("RGBA", body.size), ImageChops.invert(torn))
    body.putalpha(ImageChops.multiply(body.getchannel("A"), torn))
    tilt = rng.choice((-9, -6, 5, 8, 11))
    body = body.rotate(tilt, expand=True, resample=Image.BICUBIC)
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    # The spill of debris and soot under the tear, drawn first so the wreck lies on it.
    d = ImageDraw.Draw(im)
    ang = math.radians(-45 - 90 * side - tilt)
    cx, cy = size / 2 + size * 0.22 * math.cos(ang), size / 2 + size * 0.22 * math.sin(ang)
    blot = [(cx + size * 0.2 * (0.7 + 0.3 * rng.random()) * math.cos(a), cy + size * 0.16 * (0.7 + 0.3 * rng.random())
             * math.sin(a)) for a in (k * math.pi / 10 for k in range(20))]
    d.polygon(blot, fill=(96, 92, 88, 150))
    im.alpha_composite(body, ((size - body.width) // 2, (size - body.height) // 2))
    box = chunk.getbbox()
    if box:
        frag = chunk.crop(box).rotate(rng.uniform(25, 60), expand=True, resample=Image.BICUBIC)
        frag.thumbnail((int(size * 0.32), int(size * 0.32)))
        for k, f in enumerate((frag, frag.resize((frag.width // 2, frag.height // 2)).rotate(70, expand=True),
                               frag.resize((frag.width // 3, frag.height // 3)).rotate(-40, expand=True))):
            r = size * (0.36 + 0.06 * k)
            a2 = ang + (k - 1) * 0.45
            fx = int(min(max(size / 2 + r * math.cos(a2) - f.width / 2, 8), size - f.width - 8))
            fy = int(min(max(size / 2 + r * math.sin(a2) - f.height / 2, 8), size - f.height - 8))
            im.alpha_composite(f, (fx, fy))
    return im


def draw(family):
    fr = frames(family)
    width = max(f[1] + f[3] for f in fr) + GUTTER
    height = max(f[2] + f[4] for f in fr) + GUTTER
    im = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    body, edge, dark, light = (128, 132, 138, 255), (70, 74, 80, 255), (40, 42, 46, 255), (176, 180, 186, 255)
    rng = random.Random(family)
    for pid, x, y, w, h, kind in fr:
        inset = 18
        if hall_kinds.draw_kind(im, d, kind, x, y, w, h) or weather_kinds.draw_kind(im, d, kind, x, y, w, h) \
                or bridge_kinds.draw_kind(im, d, kind, x, y, w, h) \
                or rooms_kinds.draw_kind(im, d, kind, x, y, w, h):
            continue
        if kind == "box":
            d.rectangle((x + inset, y + inset, x + w - inset, y + h - inset), fill=body, outline=edge, width=8)
            d.rectangle((x + 60, y + 60, x + w - 60, y + h // 2 - 20), fill=light, outline=edge, width=4)
        elif kind == "fins":
            d.rectangle((x + inset, y + 90, x + w - inset, y + h - 90), fill=edge)
            for i in range(9):
                fx = x + 50 + i * (w - 100) // 8
                d.rectangle((fx - 14, y + inset, fx + 14, y + h - inset), fill=body, outline=edge, width=4)
        elif kind == "round":
            d.ellipse((x + inset, y + inset, x + w - inset, y + h - inset), fill=body, outline=edge, width=10)
            d.ellipse((x + 110, y + 110, x + w - 110, y + h - 110), fill=light, outline=edge, width=6)
        elif kind == "trough":
            d.rectangle((x + inset, y + inset + 30, x + w - inset, y + h - inset - 30), fill=body, outline=edge,
                        width=8)
            d.rectangle((x + 50, y + 80, x + w - 50, y + h - 80), fill=dark)
        elif kind == "cradle":
            d.rectangle((x + inset, y + inset, x + w - inset, y + h - inset), outline=edge, width=26)
            d.rectangle((x + 120, y + 90, x + w - 120, y + h - 90), fill=body, outline=edge, width=6)
        elif kind == "shaft":
            d.rectangle((x + inset, y + inset, x + w - inset, y + h - inset), fill=body, outline=edge, width=10)
            d.rectangle((x + 200, y + 200, x + w - 200, y + h - 200), fill=dark)
            d.line((x + inset, y + inset, x + w - inset, y + h - inset), fill=edge, width=30)
            d.line((x + w - inset, y + inset, x + inset, y + h - inset), fill=edge, width=30)
        elif kind == "drum":
            d.rectangle((x + inset, y + 60, x + w - inset, y + h - 60), fill=edge)
            d.rectangle((x + 140, y + inset + 20, x + w - 140, y + h - inset - 20), fill=light, outline=edge,
                        width=6)
        elif kind in ("line", "lineend"):
            b0, b1 = y + round(h * BED[0]), y + round(h * BED[1])
            bed_x0 = x if kind == "line" else x + 150
            d.rectangle((bed_x0, b0, x + w, b1), fill=body)
            d.rectangle((bed_x0, b0, x + w, b0 + 16), fill=edge)
            d.rectangle((bed_x0, b1 - 16, x + w, b1), fill=edge)
            # Rollers across the bed at a pitch that divides the module, so a seam falls between two rollers.
            for rx in range(bed_x0 + 20, x + w, 80):
                d.rectangle((rx, b0 + 24, rx + 36, b1 - 24), fill=light)
            if kind == "line":
                for sx in (x + 220, x + w - 620):
                    d.rectangle((sx, y + inset, sx + 400, b0 - 20), fill=body, outline=edge, width=8)
                    d.rectangle((sx + 100, b1 + 20, sx + 300, y + h - inset), fill=body, outline=edge, width=8)
            else:
                d.rectangle((x + inset, y + 120, x + 420, y + h - 120), fill=body, outline=edge, width=10)
                d.rectangle((x + 80, y + 200, x + 340, y + h - 200), fill=dark)
        elif kind == "ring":
            d.rectangle((x + inset, y + inset, x + w - inset, y + h - inset), outline=edge, width=60)
            for cx, cy in ((x + 140, y + 140), (x + w - 140, y + 140), (x + 140, y + h - 140),
                           (x + w - 140, y + h - 140)):
                d.rectangle((cx - 50, cy - 50, cx + 50, cy + 50), fill=body, outline=edge, width=6)
        elif kind in ("rack", "rackfull"):
            d.rectangle((x + inset, y + inset, x + w - inset, y + h - inset), fill=dark, outline=edge, width=24)
            for i in range(1, 6):
                bx = x + i * w // 6
                d.rectangle((bx - 10, y + inset, bx + 10, y + h - inset), fill=edge)
            if kind == "rackfull":
                for bay, wide in ((0, 1), (2, 2), (5, 1)):
                    bx = x + bay * w // 6
                    d.rectangle((bx + 40, y + 70, bx + wide * w // 6 - 40, y + h - 70), fill=light, outline=edge,
                                width=6)
        elif kind == "dock":
            d.rectangle((x, y, x + w, y + 120), fill=edge)
            d.rectangle((x + 60, y + 20, x + w - 60, y + 110), fill=body)
            d.rectangle((x + 80, y + 130, x + w - 80, y + h - inset), fill=light, outline=edge, width=10)
        elif kind == "crucible":
            cx, cy = x + w / 2, y + h / 2 - 20
            d.rectangle((x + inset, cy - 80, x + w - inset, cy + 80), fill=EDGE)
            for sx in (x + inset, x + w - inset - 110):
                d.rectangle((sx, cy - 120, sx + 110, cy + 120), fill=BODY, outline=EDGE, width=8)
            d.polygon([(cx - 90, y + h - 200), (cx + 90, y + h - 200), (cx + 50, y + h - inset),
                       (cx - 50, y + h - inset)], fill=BODY, outline=EDGE, width=8)
            r = w * 0.38
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=BODY, outline=EDGE, width=16)
            d.polygon(crucible_melt(x, y, w, h, kind), fill=MELT)
        elif kind == "crucibletip":
            # Lying on its side, seen straight down: a long rounded body, its axis toward the south-east, the
            # mouth's rim a band across that end and the trunnions standing out of its long sides. No oblique view
            # into the mouth.
            d.polygon(spill_outline(x, y, w, h, 1.0), fill=DARK)
            body = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            bd = ImageDraw.Draw(body)
            L, D = w * 0.46, w * 0.38
            x0, y0 = w * 0.08, h * 0.33
            bd.rounded_rectangle((x0, y0, x0 + L, y0 + D), radius=D * 0.18, fill=BODY, outline=EDGE, width=14)
            bd.rectangle((x0 + L - 70, y0 - 6, x0 + L, y0 + D + 6), fill=EDGE)
            for tx in (x0 + L * 0.42,):
                bd.rectangle((tx - 55, y0 - 90, tx + 55, y0), fill=LIGHT, outline=EDGE, width=8)
                bd.rectangle((tx - 55, y0 + D, tx + 55, y0 + D + 90), fill=LIGHT, outline=EDGE, width=8)
            im.alpha_composite(body.rotate(-24, center=(x0 + L, y0 + D / 2), resample=Image.BICUBIC), (x, y))
            d.polygon(crucible_melt(x, y, w, h, kind), fill=MELT)
        elif kind in ("leaf", "leafsealed", "cargoleaf", "blastleaf"):
            ribs = {"leaf": 3, "leafsealed": 3, "cargoleaf": 2, "blastleaf": 4}[kind]
            d.rectangle((x, y, x + w - 1, y + h - 1), fill=BODY, outline=EDGE, width=14)
            for k in range(1, ribs + 1):
                rx = x + k * (w * 0.85) / (ribs + 1)
                d.rectangle((rx - 9, y + 14, rx + 9, y + h - 14), fill=EDGE)
            d.rectangle((x + w * 0.85, y, x + w - 1, y + h - 1), fill=LIGHT, outline=EDGE, width=8)
            if kind == "blastleaf":
                for by in (y + h * 0.3, y + h * 0.7):
                    d.rectangle((x + w * 0.55, by - 16, x + w * 0.97, by + 16), fill=DARK)
                for k in range(8):
                    d.ellipse((x + 30 + k * w * 0.1 - 7, y + 22 - 7, x + 30 + k * w * 0.1 + 7, y + 22 + 7), fill=EDGE)
                    d.ellipse((x + 30 + k * w * 0.1 - 7, y + h - 22 - 7, x + 30 + k * w * 0.1 + 7, y + h - 22 + 7),
                              fill=EDGE)
        elif kind == "leaftorn":
            leaf = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            ld = ImageDraw.Draw(leaf)
            ld.polygon([(w * 0.03, h * 0.30), (w * 0.52, h * 0.20), (w * 0.97, h * 0.34), (w * 0.95, h * 0.80),
                        (w * 0.50, h * 0.72), (w * 0.05, h * 0.84)], fill=BODY, outline=EDGE)
            for k in range(1, 6):
                rx = w * k / 6
                ld.ellipse((rx - 14, h * 0.83 - 14, rx + 14, h * 0.83 + 14), fill=LIGHT, outline=EDGE)
            ld.rectangle((w * 0.82, h * 0.26, w * 0.95, h * 0.76), fill=LIGHT)
            im.alpha_composite(leaf, (x, y))
        elif kind == "clamp":
            d.rectangle((x, y + h * 0.35, x + w - 1, y + h * 0.65), fill=EDGE)
            d.rectangle((x + w * 0.22, y, x + w * 0.78, y + h - 1), fill=BODY, outline=EDGE, width=10)
            d.ellipse((x + w / 2 - 26, y + h * 0.18 - 26, x + w / 2 + 26, y + h * 0.18 + 26), fill=LIGHT)
        elif kind == "jamb":
            d.rectangle((x, y, x + w - 1, y + h - 1), fill=BODY, outline=EDGE, width=10)
            d.rectangle((x + w * 0.45, y + 20, x + w * 0.8, y + h - 20), fill=DARK)
            d.ellipse((x + 18, y + 18, x + 58, y + 58), fill=LIGHT, outline=EDGE)
        elif kind == "rubble":
            for k in range(26):
                cx, cy = x + rng.uniform(40, w - 40), y + rng.uniform(40, h - 40)
                r = rng.uniform(8, 34)
                d.polygon([(cx + r * math.cos(a), cy + r * math.sin(a) * 0.8) for a in
                           (rng.uniform(0, 6.28) for _ in range(6))], fill=(DARK, EDGE, BODY)[k % 3])
            d.rectangle((x + 20, y + h / 2 - 12, x + w - 20, y + h / 2 + 12), fill=EDGE)
        elif kind == "greatcrucible":
            c = FAMILY_CELL["forge-mega"]
            cx, cy, r = x + 6.6 * c, y + 8 * c, 4.6 * c
            d.rectangle((x + 1.2 * c, y + 0.3 * c, x + w - 0.6 * c, y + h - 0.3 * c), fill=EDGE)
            for ty in (cy - r - 1.2 * c, cy + r - 0.1 * c):
                d.rectangle((cx - 1.6 * c, ty, cx + 1.6 * c, ty + 1.3 * c), fill=LIGHT, outline=DARK, width=8)
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=BODY, outline=DARK, width=12)
            d.polygon([(cx + r - 0.6 * c, cy - 0.9 * c), (x + w, cy - 0.6 * c), (x + w, cy + 0.6 * c),
                       (cx + r - 0.6 * c, cy + 0.9 * c)], fill=BODY, outline=DARK)
            d.rectangle((cx + r, cy - 0.3 * c, x + w, cy + 0.3 * c), fill=MELT)
            d.rectangle((x + 0.2 * c, cy - 1.6 * c, x + 2.4 * c, cy + 1.6 * c), fill=LIGHT, outline=DARK, width=10)
            d.ellipse((x + 0.6 * c, cy - 0.9 * c, x + 2.0 * c, cy + 0.9 * c), fill=DARK)
            for k in range(3):
                d.line((x + 2.4 * c, cy - 0.8 * c + k * 0.8 * c, cx - r + 0.3 * c, cy - 0.8 * c + k * 0.8 * c),
                       fill=DARK, width=14)
            d.polygon([(cx + rr * math.cos(a), cy + rr * math.sin(a)) for rr, a in
                       ((3.5 * c, k * math.pi / 60) for k in range(120))], fill=MELT)
        elif kind == "furnacecore":
            d.rectangle((x + 30, y + 30, x + w - 30, y + h - 30), fill=EDGE)
            for sx in (x + 30, x + w - 70):
                d.rectangle((sx, y + 30, sx + 40, y + h - 30), fill=DARK)
            cx, cy, r = x + w / 2, y + h / 2, w * 0.32
            d.polygon([(cx + r * math.cos(a), cy + r * math.sin(a)) for a in
                       (k * math.pi / 4 + math.pi / 8 for k in range(8))], fill=BODY, outline=DARK, width=10)
            for k in range(8):
                a = k * math.pi / 4
                d.line((cx + 0.45 * r * math.cos(a), cy + 0.45 * r * math.sin(a), cx + 0.95 * r * math.cos(a),
                        cy + 0.95 * r * math.sin(a)), fill=EDGE, width=12)
            d.ellipse((cx - 0.4 * r, cy - 0.4 * r, cx + 0.4 * r, cy + 0.4 * r), fill=LIGHT, outline=DARK, width=8)
            for lx, ly in ((x + 50, y + 50), (x + w - 50, y + 50), (x + 50, y + h - 50), (x + w - 50, y + h - 50)):
                d.ellipse((lx - 16, ly - 16, lx + 16, ly + 16), fill=LIGHT)
        elif kind == "furnace":
            c = FAMILY_CELL["forge-line"]
            d.rectangle((x + 0.2 * c, y + 0.2 * c, x + w - 0.2 * c, y + h - 1.6 * c), fill=BODY, outline=EDGE, width=12)
            d.ellipse((x + 1.4 * c, y + 0.9 * c, x + w - 1.4 * c, y + 4.3 * c), fill=DARK, outline=EDGE, width=12)
            for k in range(6):
                d.rectangle((x + 0.4 * c + k * 0.9 * c, y + 0.3 * c, x + 0.7 * c + k * 0.9 * c, y + 0.6 * c), fill=LIGHT)
            d.polygon([(x + w / 2 - 0.6 * c, y + h - 1.6 * c), (x + w / 2 + 0.6 * c, y + h - 1.6 * c),
                       (x + w / 2 + 0.4 * c, y + h), (x + w / 2 - 0.4 * c, y + h)], fill=BODY, outline=EDGE)
            d.rectangle((x + w / 2 - 0.2 * c, y + h - 1.9 * c, x + w / 2 + 0.2 * c, y + h), fill=MELT)
            d.ellipse((x + w / 2 - 0.35 * c, y + 4.3 * c, x + w / 2 + 0.35 * c, y + 5.0 * c), fill=MELT)
        elif kind in ("ladle-up", "ladle-tip"):
            cx, cy = x + w / 2, y + h / 2
            d.line((x + 0.1 * w, cy, x + 0.9 * w, cy), fill=EDGE, width=26)
            for sx in (x + 0.1 * w, x + 0.9 * w - 40):
                d.rectangle((sx, cy - 40, sx + 40, cy + 40), fill=LIGHT, outline=EDGE)
            if kind == "ladle-up":
                d.ellipse((cx - 0.33 * w, cy - 0.33 * w, cx + 0.33 * w, cy + 0.33 * w), fill=BODY, outline=EDGE, width=12)
                d.ellipse((cx - 0.24 * w, cy - 0.24 * w, cx + 0.24 * w, cy + 0.24 * w), fill=MELT)
            else:
                d.ellipse((cx - 0.36 * w, cy - 0.28 * w, cx + 0.24 * w, cy + 0.28 * w), fill=BODY, outline=EDGE, width=12)
                d.ellipse((cx - 0.26 * w, cy - 0.18 * w, cx + 0.12 * w, cy + 0.18 * w), fill=DARK)
                d.polygon([(cx + 0.18 * w, cy - 0.08 * w), (cx + 0.46 * w, cy - 0.05 * w), (cx + 0.48 * w, cy + 0.05 * w),
                           (cx + 0.18 * w, cy + 0.08 * w)], fill=MELT)
            d.rectangle((cx - 30, cy - 0.45 * h, cx + 30, cy - 0.33 * w), fill=EDGE)
        elif kind in ("orecar", "orecar-off"):
            car = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            cd = ImageDraw.Draw(car)
            m = 0.08 if kind == "orecar-off" else 0
            cd.rectangle((w * (0.12 + m), h * (0.08 + m), w * (0.88 - m), h * (0.92 - m)), fill=BODY, outline=EDGE,
                         width=12)
            cd.rectangle((w * (0.22 + m), h * (0.16 + m), w * (0.78 - m), h * (0.84 - m)), fill=DARK)
            for wy in (h * (0.2 + m), h * (0.8 - m)):
                for wx in (w * (0.04 + m), w * (0.84 - m)):
                    cd.rectangle((wx, wy - 26, wx + 0.12 * w, wy + 26), fill=EDGE)
            if kind == "orecar-off":
                car = car.rotate(-16, resample=Image.BICUBIC)
                for k in range(5):
                    d.ellipse((x + w * (0.55 + 0.08 * k), y + h * (0.86 + 0.02 * (k % 2)) - 16,
                               x + w * (0.55 + 0.08 * k) + 32, y + h * (0.86 + 0.02 * (k % 2)) + 16), fill=EDGE)
            im.alpha_composite(car, (x, y))
        elif kind in ("coolrack", "coolrack-full"):
            d.rectangle((x + 16, y + 16, x + w - 16, y + h - 16), outline=EDGE, width=22)
            for k in range(1, 6):
                yy = y + k * h / 6
                d.rectangle((x + 16, yy - 8, x + w - 16, yy + 8), fill=EDGE)
            if kind == "coolrack-full":
                for k in (0, 1, 3, 4):
                    yy = y + k * h / 6 + h / 12
                    d.rectangle((x + 50, yy - 4, x + w - 50, yy + h / 6 - 30), fill=BODY, outline=EDGE)
        elif kind in ("bench", "table"):
            d.rectangle((x + 20, y + 40, x + w - 20, y + h - 40), fill=BODY, outline=EDGE, width=12)
            if kind == "bench":
                d.ellipse((x + 50, y + 80, x + 150, y + 180), fill=LIGHT, outline=EDGE)
                d.rectangle((x + 170, y + 90, x + w - 50, y + 150), fill=DARK)
            else:
                d.rectangle((x + 60, y + 80, x + w - 60, y + h - 80), fill=LIGHT, outline=EDGE)
                d.rectangle((x + w - 110, y + 50, x + w - 50, y + 110), fill=DARK)
        elif kind == "pourhead":
            d.ellipse((x + 24, y + 24, x + w - 24, y + h - 24), fill=BODY, outline=EDGE, width=12)
            d.ellipse((x + w / 2 - 50, y + h / 2 - 50, x + w / 2 + 50, y + h / 2 + 50), fill=DARK)
            for k in range(4):
                a = k * math.pi / 2 + math.pi / 4
                d.rectangle((x + w / 2 + 110 * math.cos(a) - 22, y + h / 2 + 110 * math.sin(a) - 22,
                             x + w / 2 + 110 * math.cos(a) + 22, y + h / 2 + 110 * math.sin(a) + 22), fill=LIGHT)
        elif kind == "mouldmaster":
            d.rectangle((x + 20, y + 20, x + w - 20, y + h - 20), fill=BODY, outline=EDGE, width=12)
            for gx in range(2):
                for gy in range(2):
                    d.rectangle((x + 60 + gx * 110, y + 60 + gy * 110, x + 150 + gx * 110, y + 150 + gy * 110),
                                fill=LIGHT, outline=EDGE)
        elif kind == "girder":
            d.polygon([(x + 10, y + 30), (x + w - 10, y + 50), (x + w - 14, y + h - 30), (x + 14, y + h - 50)],
                      fill=BODY, outline=EDGE)
            for k in range(1, 14):
                gx = x + k * w / 14
                d.line((gx - 40, y + 40, gx + 40, y + h - 40), fill=EDGE, width=10)
        elif kind == "rail":
            d.rectangle((x, y, x + w, y + h), fill=BODY)
            for k in range(8):
                sy = y + (k + 0.5) * h / 8
                d.rectangle((x + 0.12 * w, sy - 0.035 * h, x + 0.88 * w, sy + 0.035 * h), fill=EDGE)
            for rx in (0.3, 0.7):
                d.rectangle((x + rx * w - 10, y, x + rx * w + 10, y + h), fill=LIGHT)
        elif kind == "railcurve":
            c = FAMILY_CELL["forge-floor"]
            ox, oy = x + w, y + h
            d.pieslice((ox - 4 * c, oy - 4 * c, ox + 4 * c, oy + 4 * c), 180, 270, fill=BODY)
            d.pieslice((ox - 2 * c, oy - 2 * c, ox + 2 * c, oy + 2 * c), 180, 270, fill=(0, 0, 0, 0))
            for k in range(13):
                a = math.radians(180 + (k + 0.5) * 90 / 13)
                d.line((ox + 2.24 * c * math.cos(a), oy + 2.24 * c * math.sin(a), ox + 3.76 * c * math.cos(a),
                        oy + 3.76 * c * math.sin(a)), fill=EDGE, width=22)
            for r in (2.6, 3.4):
                d.arc((ox - r * c, oy - r * c, ox + r * c, oy + r * c), 180, 270, fill=LIGHT, width=20)
        elif kind == "railbuffer":
            d.rectangle((x, y, x + w, y + h), fill=BODY)
            for k in range(4, 8):
                sy = y + (k + 0.5) * h / 8
                d.rectangle((x + 0.12 * w, sy - 0.035 * h, x + 0.88 * w, sy + 0.035 * h), fill=EDGE)
            for rx in (0.3, 0.7):
                d.rectangle((x + rx * w - 10, y + 0.3 * h, x + rx * w + 10, y + h), fill=LIGHT)
            d.rectangle((x + 0.1 * w, y + 0.12 * h, x + 0.9 * w, y + 0.36 * h), fill=EDGE, outline=DARK, width=8)
            for rx in (0.3, 0.7):
                d.ellipse((x + rx * w - 26, y + 0.33 * h, x + rx * w + 26, y + 0.33 * h + 52), fill=LIGHT,
                          outline=DARK, width=6)
        elif kind == "slagcar":
            d.rectangle((x + 0.14 * w, y + 0.06 * h, x + 0.86 * w, y + 0.94 * h), fill=EDGE, outline=DARK, width=10)
            for wy in (0.16, 0.84):
                for wx in (0.04, 0.84):
                    d.rectangle((x + wx * w, y + wy * h - 22, x + (wx + 0.12) * w, y + wy * h + 22), fill=DARK)
            d.ellipse((x + 0.06 * w, y + 0.5 * h - 0.44 * w, x + 0.94 * w, y + 0.5 * h + 0.44 * w), fill=BODY,
                      outline=EDGE, width=12)
            d.ellipse((x + 0.2 * w, y + 0.5 * h - 0.3 * w, x + 0.8 * w, y + 0.5 * h + 0.3 * w), fill=DARK)
            d.ellipse((x + 0.38 * w, y + 0.5 * h - 0.1 * w, x + 0.6 * w, y + 0.5 * h + 0.1 * w), fill=MELT)
        elif kind == "slagtip":
            car = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            cd = ImageDraw.Draw(car)
            cd.rectangle((0.12 * w, 0.18 * h, 0.52 * w, 0.82 * h), fill=EDGE, outline=DARK, width=10)
            for wy in (0.26, 0.74):
                for wx in (0.06, 0.48):
                    cd.rectangle((wx * w, wy * h - 18, (wx + 0.08) * w, wy * h + 18), fill=DARK)
            car = car.rotate(9, resample=Image.BICUBIC)
            im.alpha_composite(car, (x, y))
            d.polygon([(x + 0.5 * w, y + 0.36 * h), (x + 0.72 * w, y + 0.3 * h), (x + w - 6, y + 0.4 * h),
                       (x + w - 6, y + 0.66 * h), (x + 0.74 * w, y + 0.7 * h), (x + 0.52 * w, y + 0.62 * h)],
                      fill=MELT)
            d.ellipse((x + 0.3 * w, y + 0.26 * h, x + 0.66 * w, y + 0.74 * h), fill=BODY, outline=EDGE, width=12)
            d.ellipse((x + 0.38 * w, y + 0.36 * h, x + 0.62 * w, y + 0.64 * h), fill=DARK)
        elif kind == "palletbay":
            paint, t, ln = PAINT, 16, 0.22 * w
            for cx_, cy_, sx_, sy_ in ((x + 20, y + 20, 1, 1), (x + w - 20, y + 20, -1, 1),
                                       (x + 20, y + h - 20, 1, -1), (x + w - 20, y + h - 20, -1, -1)):
                d.rectangle((min(cx_, cx_ + sx_ * ln), min(cy_, cy_ + sy_ * t), max(cx_, cx_ + sx_ * ln),
                             max(cy_, cy_ + sy_ * t)), fill=paint)
                d.rectangle((min(cx_, cx_ + sx_ * t), min(cy_, cy_ + sy_ * ln), max(cx_, cx_ + sx_ * t),
                             max(cy_, cy_ + sy_ * ln)), fill=paint)
            d.rectangle((x + 0.38 * w, y + 0.8 * h, x + 0.62 * w, y + 0.9 * h), fill=paint)
        elif kind == "trench":
            d.rectangle((x, y, x + w, y + h), fill=EDGE)
            for rx in (0, 0.9 * w):
                d.rectangle((x + rx, y, x + rx + 0.1 * w, y + h), fill=DARK)
            for k in range(4):
                d.rectangle((x + 0.12 * w, y + k * h / 4 + 6, x + 0.88 * w, y + (k + 1) * h / 4 - 6), fill=BODY,
                            outline=EDGE, width=6)
        elif kind == "gutter":
            d.rectangle((x, y, x + w, y + h), fill=BODY)
            d.rectangle((x + 0.28 * w, y, x + 0.72 * w, y + h), fill=DARK)
            for k in range(16):
                sy = y + (k + 0.5) * h / 16
                d.rectangle((x + 0.3 * w, sy - 4, x + 0.7 * w, sy + 4), fill=EDGE)
        elif kind == "drain":
            d.rectangle((x + 12, y + 12, x + w - 12, y + h - 12), fill=EDGE, outline=DARK, width=8)
            d.rectangle((x + 34, y + 34, x + w - 34, y + h - 34), fill=DARK)
            for k in range(1, 7):
                d.line((x + 34 + k * (w - 68) / 7, y + 34, x + 34 + k * (w - 68) / 7, y + h - 34), fill=EDGE,
                       width=6)
        elif kind == "runway":
            d.rectangle((x + 0.22 * w, y, x + 0.78 * w, y + h), fill=BODY)
            d.rectangle((x + 0.44 * w, y, x + 0.56 * w, y + h), fill=LIGHT)
            for k in range(8):
                sy = y + (k + 0.5) * h / 8
                d.rectangle((x + 0.22 * w, sy - 8, x + 0.78 * w, sy + 8), fill=EDGE)
            d.rectangle((x + 0.06 * w, y + 0.44 * h, x + 0.94 * w, y + 0.56 * h), fill=EDGE, outline=DARK, width=6)
        elif kind == "tray":
            for rx in (0.12, 0.8):
                d.rectangle((x + rx * w, y, x + (rx + 0.08) * w, y + h), fill=LIGHT)
            for k in range(8):
                sy = y + (k + 0.5) * h / 8
                d.rectangle((x + 0.12 * w, sy - 5, x + 0.88 * w, sy + 5), fill=EDGE)
            for k in range(5):
                d.rectangle((x + (0.24 + 0.11 * k) * w, y, x + (0.31 + 0.11 * k) * w, y + h), fill=DARK)
        elif kind == "trayfallen":
            tray = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            td = ImageDraw.Draw(tray)
            for rx in (0.3, 0.62):
                td.rectangle((rx * w, 0.06 * h, (rx + 0.08) * w, 0.94 * h), fill=LIGHT)
            for k in range(12):
                sy = (0.08 + k * 0.074) * h
                td.rectangle((0.3 * w, sy - 4, 0.7 * w, sy + 4), fill=EDGE)
            tray = tray.rotate(-7, resample=Image.BICUBIC)
            im.alpha_composite(tray, (x, y))
            for k in range(4):
                d.line((x + (0.36 + 0.07 * k) * w, y + 0.1 * h, x + (0.2 + 0.2 * k) * w, y + (0.3 + 0.18 * k) * h,
                        x + (0.42 + 0.06 * k) * w, y + 0.92 * h), fill=DARK, width=12)
        elif kind == "maglev":
            d.rectangle((x, y, x + w, y + h), fill=BODY)
            d.rectangle((x + 0.26 * w, y, x + 0.74 * w, y + h), fill=DARK)
            for k in range(8):
                sy = y + (k + 0.5) * h / 8
                d.rectangle((x + 0.26 * w, sy - 6, x + 0.74 * w, sy + 6), fill=EDGE)
            for rx in (0.2, 0.8):
                d.rectangle((x + rx * w - 9, y, x + rx * w + 9, y + h), fill=LIGHT)
            for cy_ in (0.25, 0.75):
                d.rectangle((x + 0.08 * w, y + cy_ * h - 0.06 * h, x + 0.92 * w, y + cy_ * h + 0.06 * h),
                            fill=COIL, outline=EDGE, width=4)
        elif kind == "maglevcurve":
            c = FAMILY_CELL[family]
            ox, oy = x + w, y + h
            d.pieslice((ox - 4 * c, oy - 4 * c, ox + 4 * c, oy + 4 * c), 180, 270, fill=BODY)
            d.pieslice((ox - 3.48 * c, oy - 3.48 * c, ox + 3.48 * c, oy + 3.48 * c), 180, 270, fill=DARK)
            d.pieslice((ox - 2.52 * c, oy - 2.52 * c, ox + 2.52 * c, oy + 2.52 * c), 180, 270, fill=BODY)
            d.pieslice((ox - 2 * c, oy - 2 * c, ox + 2 * c, oy + 2 * c), 180, 270, fill=(0, 0, 0, 0))
            for r in (2.4, 3.6):
                d.arc((ox - r * c, oy - r * c, ox + r * c, oy + r * c), 180, 270, fill=LIGHT, width=18)
            for k in range(4):
                a = math.radians(180 + (k + 0.5) * 90 / 4)
                d.line((ox + 2.16 * c * math.cos(a), oy + 2.16 * c * math.sin(a), ox + 3.84 * c * math.cos(a),
                        oy + 3.84 * c * math.sin(a)), fill=COIL, width=36)
        elif kind == "maglevend":
            d.rectangle((x, y, x + w, y + h), fill=BODY)
            d.rectangle((x + 0.26 * w, y + 0.42 * h, x + 0.74 * w, y + h), fill=DARK)
            for rx in (0.2, 0.8):
                d.rectangle((x + rx * w - 9, y + 0.42 * h, x + rx * w + 9, y + h), fill=LIGHT)
            d.rectangle((x + 0.1 * w, y + 0.08 * h, x + 0.9 * w, y + 0.42 * h), fill=EDGE, outline=DARK, width=8)
            for k in range(3):
                d.rectangle((x + 0.16 * w, y + (0.12 + 0.1 * k) * h, x + 0.84 * w, y + (0.17 + 0.1 * k) * h),
                            fill=COIL)
            d.rectangle((x + 0.08 * w, y + 0.7 * h, x + 0.92 * w, y + 0.8 * h), fill=COIL, outline=EDGE, width=4)
        elif kind in ("pod", "pod-off"):
            pod = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            pd = ImageDraw.Draw(pod)
            m = 0.07 if kind == "pod-off" else 0
            pd.rounded_rectangle((w * (0.2 + m), h * (0.04 + m), w * (0.8 - m), h * (0.96 - m)), radius=0.28 * w,
                                 fill=BODY, outline=EDGE, width=10)
            for by in (0.3, 0.5, 0.7):
                pd.rectangle((w * (0.18 + m), h * by - 14, w * (0.82 - m), h * by + 14), fill=EDGE)
            pd.ellipse((w * (0.3 + m), h * (0.06 + m), w * (0.7 - m), h * (0.06 + m) + 0.4 * w), fill=GLOW)
            if kind == "pod-off":
                pod = pod.rotate(-18, resample=Image.BICUBIC)
                for k in range(6):
                    px = x + w * (0.58 + 0.06 * k)
                    py = y + h * (0.8 + 0.025 * (k % 3))
                    d.ellipse((px - 14, py - 14, px + 14, py + 14), fill=EDGE)
            im.alpha_composite(pod, (x, y))
        elif kind in ("slagskid", "slagcrash"):
            sk = Image.new("RGBA", (w, h), (0, 0, 0, 0))
            sd = ImageDraw.Draw(sk)
            fans = [(0.2, 0.2), (0.8, 0.2), (0.2, 0.8), (0.8, 0.8)]
            if kind == "slagcrash":
                fans = fans[:3]
            sd.rectangle((0.24 * w, 0.14 * h, 0.76 * w, 0.86 * h), fill=EDGE, outline=DARK, width=10)
            for fx_, fy_ in fans:
                sd.ellipse(((fx_ - 0.14) * w, (fy_ - 0.14) * h, (fx_ + 0.14) * w, (fy_ + 0.14) * h), fill=BODY,
                           outline=EDGE, width=10)
                sd.ellipse(((fx_ - 0.09) * w, (fy_ - 0.09) * h, (fx_ + 0.09) * w, (fy_ + 0.09) * h), fill=DARK)
            if kind == "slagskid":
                sd.ellipse((0.3 * w, 0.3 * h, 0.7 * w, 0.7 * h), fill=BODY, outline=EDGE, width=12)
                sd.ellipse((0.37 * w, 0.37 * h, 0.63 * w, 0.63 * h), fill=DARK)
                sd.ellipse((0.45 * w, 0.46 * h, 0.56 * w, 0.55 * h), fill=MELT)
                im.alpha_composite(sk, (x, y))
            else:
                sk = sk.rotate(14, resample=Image.BICUBIC).resize((round(0.84 * w), round(0.84 * h)), Image.LANCZOS)
                im.alpha_composite(sk, (x + int(0.02 * w), y + int(0.08 * h)))
                d.polygon([(x + 0.58 * w, y + 0.38 * h), (x + 0.78 * w, y + 0.34 * h), (x + w - 6, y + 0.42 * h),
                           (x + w - 6, y + 0.68 * h), (x + 0.8 * w, y + 0.7 * h), (x + 0.6 * w, y + 0.62 * h)],
                          fill=MELT)
                d.ellipse((x + 0.4 * w, y + 0.3 * h, x + 0.72 * w, y + 0.7 * h), fill=BODY, outline=EDGE, width=12)
                d.ellipse((x + 0.47 * w, y + 0.39 * h, x + 0.68 * w, y + 0.61 * h), fill=DARK)
        elif kind in ("yoke-up", "yoke-tip"):
            cx, cy = x + w / 2, y + h / 2
            d.rectangle((cx - 0.22 * w, y + 0.02 * h, cx + 0.22 * w, y + 0.16 * h), fill=EDGE, outline=DARK, width=8)
            for sx_ in (-1, 1):
                d.polygon([(cx + sx_ * 0.16 * w, y + 0.14 * h), (cx + sx_ * 0.24 * w, y + 0.14 * h),
                           (cx + sx_ * 0.46 * w, cy - 0.04 * h), (cx + sx_ * 0.46 * w, cy + 0.08 * h),
                           (cx + sx_ * 0.38 * w, cy + 0.08 * h)], fill=EDGE)
            if kind == "yoke-up":
                d.ellipse((cx - 0.32 * w, cy - 0.3 * w, cx + 0.32 * w, cy + 0.34 * w), fill=BODY, outline=EDGE,
                          width=12)
                d.ellipse((cx - 0.23 * w, cy - 0.21 * w, cx + 0.23 * w, cy + 0.25 * w), fill=MELT)
            else:
                d.ellipse((cx - 0.34 * w, cy - 0.24 * w, cx + 0.22 * w, cy + 0.3 * w), fill=BODY, outline=EDGE,
                          width=12)
                d.ellipse((cx - 0.25 * w, cy - 0.15 * w, cx + 0.12 * w, cy + 0.21 * w), fill=DARK)
                d.polygon([(cx + 0.17 * w, cy - 0.04 * w), (cx + 0.47 * w, cy - 0.01 * w), (cx + 0.48 * w,
                           cy + 0.1 * w), (cx + 0.17 * w, cy + 0.12 * w)], fill=MELT)
            for sx_ in (-1, 1):
                d.rectangle((cx + sx_ * 0.42 * w - 22, cy - 22, cx + sx_ * 0.42 * w + 22, cy + 22 + 0.04 * h),
                            fill=LIGHT)
        elif kind == "robotcell":
            d.rectangle((x + 0.52 * w, y + 0.5 * h, x + 0.92 * w, y + 0.9 * h), fill=EDGE, outline=DARK, width=8)
            d.rectangle((x + 0.6 * w, y + 0.6 * h, x + 0.84 * w, y + 0.8 * h), fill=LIGHT, outline=EDGE, width=4)
            d.ellipse((x + 0.08 * w, y + 0.1 * h, x + 0.48 * w, y + 0.5 * h), fill=BODY, outline=EDGE, width=10)
            d.line((x + 0.28 * w, y + 0.3 * h, x + 0.55 * w, y + 0.28 * h, x + 0.68 * w, y + 0.56 * h), fill=EDGE,
                   width=34, joint="curve")
            d.ellipse((x + 0.62 * w, y + 0.5 * h, x + 0.74 * w, y + 0.62 * h), fill=DARK)
        elif kind == "metrology":
            d.rectangle((x + 0.14 * w, y + 0.08 * h, x + 0.86 * w, y + 0.92 * h), fill=BODY, outline=EDGE, width=10)
            d.rectangle((x + 0.22 * w, y + 0.16 * h, x + 0.78 * w, y + 0.84 * h), fill=LIGHT)
            d.rectangle((x + 0.04 * w, y + 0.4 * h, x + 0.96 * w, y + 0.52 * h), fill=EDGE, outline=DARK, width=6)
            d.rectangle((x + 0.2 * w, y + 0.45 * h, x + 0.8 * w, y + 0.47 * h), fill=GLOW)
        elif kind in ("stacker", "stacker-full"):
            d.rectangle((x + 16, y + 16, x + 0.22 * w, y + h - 16), fill=DARK)
            d.rectangle((x + 0.22 * w, y + 16, x + w - 16, y + h - 16), outline=EDGE, width=18)
            for k in range(1, 6):
                yy = y + k * h / 6
                d.rectangle((x + 0.22 * w, yy - 10, x + w - 16, yy + 10), fill=EDGE)
            d.rectangle((x + 20, y + 0.36 * h, x + 0.2 * w, y + 0.46 * h), fill=LIGHT)
            if kind == "stacker-full":
                for k in (0, 1, 3, 4):
                    yy = y + k * h / 6 + h / 12
                    d.rounded_rectangle((x + 0.3 * w, yy - 4, x + w - 40, yy + h / 6 - 34), radius=16, fill=BODY,
                                        outline=EDGE)
        elif kind == "linearbeam":
            d.rectangle((x + 0.2 * w, y, x + 0.8 * w, y + h), fill=BODY)
            d.rectangle((x + 0.42 * w, y, x + 0.58 * w, y + h), fill=COIL)
            for k in range(8):
                sy = y + (k + 0.5) * h / 8
                d.rectangle((x + 0.2 * w, sy - 7, x + 0.8 * w, sy + 7), fill=EDGE)
            d.rectangle((x + 0.06 * w, y + 0.44 * h, x + 0.94 * w, y + 0.56 * h), fill=EDGE, outline=DARK, width=6)
        elif kind == "beamdown":
            d.polygon([(x + 10, y + 34), (x + 0.72 * w, y + 30), (x + 0.86 * w, y + 46), (x + w - 70, y + h - 36),
                       (x + 0.84 * w, y + h - 34), (x + 0.7 * w, y + h - 30), (x + 14, y + h - 34)], fill=BODY,
                      outline=EDGE)
            d.rectangle((x + 20, y + h / 2 - 12, x + 0.7 * w, y + h / 2 + 12), fill=COIL)
            for k in range(1, 10):
                gx = x + k * 0.07 * w
                d.rectangle((gx - 6, y + 36, gx + 6, y + h - 36), fill=EDGE)
            for k in range(5):
                d.line((x + w - 80, y + h / 2, x + w - 14, y + 20 + k * (h - 40) / 4), fill=DARK, width=8)
        elif kind == "streak":
            lanes = (0.5,) if w < h * 0.5 else (0.3, 0.7)
            for lx in lanes:
                pts = [(x + lx * w + 0.06 * w * math.sin(t / 10 * math.pi * 1.3 + lx * 7), y + 40 + t / 10 * (h - 80))
                       for t in range(11)]
                d.line(pts, fill=(150, 154, 160, 140), width=int(0.16 * w / len(lanes)) + 10, joint="curve")
        elif kind == "linepulpit":
            d.rectangle((x + 16, y + 16, x + w - 16, y + h - 70), fill=BODY, outline=EDGE, width=12)
            for vy in (0.16, 0.36):
                d.rectangle((x + 0.12 * w, y + vy * h, x + 0.62 * w, y + vy * h + 0.1 * h), fill=DARK)
            d.ellipse((x + 0.74 * w, y + 0.12 * h, x + 0.86 * w, y + 0.24 * h), fill=LIGHT, outline=EDGE, width=6)
            d.rectangle((x + 0.7 * w, y + 0.36 * h, x + 0.9 * w, y + 0.62 * h), fill=EDGE)
            d.rectangle((x + 0.08 * w, y + 0.62 * h, x + 0.62 * w, y + 0.72 * h), fill=LIGHT, outline=EDGE, width=6)
            d.rectangle((x + 0.12 * w, y + h - 70, x + 0.88 * w, y + h - 16), fill=EDGE, outline=DARK, width=6)
        elif kind == "recorder":
            d.rectangle((x + 14, y + 14, x + w - 14, y + h - 14), fill=BODY, outline=EDGE, width=10)
            d.rectangle((x + 34, y + 40, x + w - 34, y + 0.4 * h), fill=DARK)
            d.ellipse((x + w / 2 - 34, y + 0.55 * h, x + w / 2 + 34, y + 0.55 * h + 68), fill=EDGE, outline=DARK,
                      width=6)
            d.rectangle((x + 30, y + h - 60, x + w - 30, y + h - 34), fill=LIGHT)
        elif kind == "ladle":
            d.ellipse((x + inset, y + 60, x + w * 0.55, y + h - 60), fill=BODY, outline=EDGE, width=12)
            d.ellipse((x + 90, y + 130, x + w * 0.55 - 70, y + h - 130), fill=DARK)
            d.rectangle((x + w * 0.5, y + h / 2 - 40, x + w - inset, y + h / 2 + 40), fill=EDGE)
            d.rectangle((x + w - 200, y + h / 2 - 110, x + w - inset, y + h / 2 + 110), fill=BODY, outline=EDGE,
                        width=8)
        elif kind == "slag":
            pts = [(x + w / 2 + (w / 2 - 60) * (0.75 + 0.25 * rng.random()) * math.cos(a),
                    y + h / 2 + (h / 2 - 60) * (0.75 + 0.25 * rng.random()) * math.sin(a))
                   for a in (k * math.pi / 9 for k in range(18))]
            d.polygon(pts, fill=DARK, outline=EDGE)
            d.ellipse((x + w / 2 - 90, y + h / 2 - 70, x + w / 2 + 80, y + h / 2 + 90), fill=BODY)
        elif kind == "pulpit":
            # Its roof, seen straight down: vent grilles, a beacon, the heat-shield visor a narrow strip along the
            # south edge, the hatch on the east side.
            d.rectangle((x + inset, y + inset, x + w - inset - 70, y + h - inset), fill=BODY, outline=EDGE, width=14)
            for vy in (y + 90, y + 200, y + 310):
                d.rectangle((x + 80, vy, x + w - 230, vy + 60), fill=DARK)
            d.ellipse((x + w - 240, y + 80, x + w - 170, y + 150), fill=LIGHT, outline=EDGE, width=6)
            d.rectangle((x + 50, y + h - 120, x + w - 130, y + h - 60), fill=LIGHT, outline=EDGE, width=6)
            d.rectangle((x + w - inset - 70, y + 200, x + w - inset, y + 420), fill=EDGE)
        elif kind == "mould":
            d.rectangle((x + inset, y + inset, x + w - inset, y + h - inset), outline=EDGE, width=70)
            for k in range(1, 4):
                bx = x + k * w // 4
                d.rectangle((bx - 22, y + inset, bx + 22, y + h - inset), fill=EDGE)
            d.rectangle((x + inset, y + h / 2 - 22, x + w - inset, y + h / 2 + 22), fill=EDGE)
        elif kind == "catwalk":
            d.rectangle((x, y + 60, x + w, y + h - 60), fill=BODY)
            for gx in range(x + 20, x + w, 40):
                d.line((gx, y + 70, gx, y + h - 70), fill=EDGE, width=6)
            for ry in (y + 30, y + h - 54):
                d.rectangle((x, ry, x + w, ry + 24), fill=LIGHT)
            for px in range(x + 40, x + w, 160):
                for ry in (y + 22, y + h - 62):
                    d.rectangle((px, ry, px + 40, ry + 40), fill=EDGE)
        elif ":" in kind:
            name, state = kind.split(":")
            im.alpha_composite(product(name, state, w, pid), (x, y))
        elif kind == "blot":
            cx, cy, r = x + w / 2, y + h / 2, min(w, h) / 2 - 50
            pts = []
            for i in range(28):
                a = i / 28 * 2 * math.pi
                rr = r * (0.65 + 0.35 * rng.random())
                pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
            d.polygon(pts, fill=(150, 154, 160, 150))
        elif kind == "organic":
            # A lopsided low-frequency outline: a few slow harmonics and an off-centre middle, so it reads as a
            # spill, never as a star or a circle.
            cx = x + w / 2 + rng.uniform(-30, 30)
            cy = y + h / 2 + rng.uniform(-30, 30)
            r = min(w, h) / 2 - 70
            harm = [(k, rng.uniform(0.06, 0.16) / k ** 0.5, rng.uniform(0, 2 * math.pi)) for k in (2, 3, 5)]
            stretch, tilt = rng.uniform(0.75, 0.95), rng.uniform(0, math.pi)
            pts = []
            for i in range(180):
                a = i / 180 * 2 * math.pi
                rr = r * (1 + sum(amp * math.sin(k * a + ph) for k, amp, ph in harm))
                px, py = rr * math.cos(a), rr * stretch * math.sin(a)
                pts.append((cx + px * math.cos(tilt) - py * math.sin(tilt),
                            cy + px * math.sin(tilt) + py * math.cos(tilt)))
            d.polygon(pts, fill=(150, 154, 160, 140))
    return im, fr


if __name__ == "__main__":
    for fam in FAMILIES:
        im, fr = draw(fam)
        path = f"art-source/templates/ruins-{fam}-guide.png"
        im.save(path)
        print(path, im.size)
        for pid, x, y, w, h, _ in fr:
            print(f"  {pid}: {x} {y} {w} {h}")
