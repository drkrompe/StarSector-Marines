"""Block-out kinds for the theme works halls' pieces (``ruins-theme-halls``), drawn by ``make_guide.draw``.

Each kind is a flat grey block-out on its frame, in the guide palette ``make_guide`` uses (plate, edge, dark recess,
light, copper coil, lit lens, melt), so the reskin keeps every piece's outline. Runs fill their frame to the north and
south edges with a pattern whose period divides the length, so ``export.heal_run`` meets them in step.
"""
import math
import random

from PIL import Image, ImageDraw

BODY, EDGE, DARK, LIGHT = (128, 132, 138, 255), (70, 74, 80, 255), (40, 42, 46, 255), (176, 180, 186, 255)
COIL = (150, 98, 64, 255)
GLOW = (236, 200, 120, 255)
# Frost, culture and water in a guide: colours no plate uses, so a brief can name them.
FROST = (214, 226, 236, 255)
CULTURE = (112, 128, 84, 255)


def _box(d, x0, y0, x1, y1, fill=BODY, line=EDGE, width=8):
    d.rectangle((x0, y0, x1, y1), fill=fill, outline=line, width=width)


def _disc(d, cx, cy, r, fill=BODY, line=EDGE, width=8):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill, outline=line, width=width)


def _run_rungs(d, x0, x1, y, h, period, thick, fill):
    """Bars across a run every ``period`` px, offset half a period so a seam falls between two."""
    k = 0
    while True:
        cy = y + (k + 0.5) * period
        if cy > y + h:
            break
        d.rectangle((x0, cy - thick / 2, x1, cy + thick / 2), fill=fill)
        k += 1


def _fallen(im, x, y, w, h, draw_run, tilt=7):
    """A run fallen from overhead: the run drawn over 0.8 of the frame, broken in two, tilted, debris at the break."""
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    mid = int(h * 0.52)
    top = Image.new("RGBA", (w, mid), (0, 0, 0, 0))
    draw_run(ImageDraw.Draw(top), 0, 0, w, mid)
    bot = Image.new("RGBA", (w, h - mid), (0, 0, 0, 0))
    draw_run(ImageDraw.Draw(bot), 0, 0, w, h - mid)
    s = 0.72
    top = top.resize((round(w * s), round(mid * 0.94)), Image.LANCZOS).rotate(tilt, expand=True,
                                                                               resample=Image.BICUBIC)
    bot = bot.resize((round(w * s), round((h - mid) * 0.9)), Image.LANCZOS).rotate(-tilt * 1.4, expand=True,
                                                                                   resample=Image.BICUBIC)
    layer.alpha_composite(top, (max(0, (w - top.width) // 2 - 6), 6))
    layer.alpha_composite(bot, (max(0, (w - bot.width) // 2 + 6), min(h - bot.height - 6, mid + 4)))
    for k in range(5):
        px, py = w * (0.25 + 0.12 * k), mid + (k % 3 - 1) * 16
        ld.ellipse((px - 9, py - 9, px + 9, py + 9), fill=EDGE)
    im.alpha_composite(layer, (x, y))


# --- runs (north-south, fill their frame) ---------------------------------------------------------------------------

def conveyor(d, x, y, w, h):
    """A conveyor bed sunk in floor plate: side frames, a dark bed, rollers across it."""
    _box(d, x, y, x + w, y + h, fill=BODY, line=BODY, width=0)
    for sx in (0.14, 0.86):
        d.rectangle((x + sx * w - 0.05 * w, y, x + sx * w + 0.05 * w, y + h), fill=EDGE)
    d.rectangle((x + 0.19 * w, y, x + 0.81 * w, y + h), fill=DARK)
    _run_rungs(d, x + 0.21 * w, x + 0.79 * w, y, h, h / 8, h / 8 * 0.5, LIGHT)


def covered_trench(d, x, y, w, h, frost=False):
    _box(d, x, y, x + w, y + h, fill=EDGE, line=EDGE, width=0)
    for k in range(4):
        y0 = y + k * h / 4 + 8
        _box(d, x + 0.12 * w, y0, x + 0.88 * w, y0 + h / 4 - 16, fill=BODY, line=DARK, width=6)
        for g in range(5):
            gx = x + (0.22 + 0.14 * g) * w
            d.rectangle((gx - 5, y0 + 14, gx + 5, y0 + h / 4 - 30), fill=DARK)
    if frost:
        for k in range(4):
            cy = y + (k + 0.5) * h / 4
            d.ellipse((x + 0.3 * w, cy - 0.06 * h, x + 0.62 * w, cy + 0.05 * h), fill=FROST)


def twin_pipes(d, x, y, w, h):
    _box(d, x, y, x + w, y + h, fill=EDGE, line=EDGE, width=0)
    d.rectangle((x + 0.1 * w, y, x + 0.9 * w, y + h), fill=DARK)
    for px in (0.32, 0.68):
        d.rectangle((x + (px - 0.11) * w, y, x + (px + 0.11) * w, y + h), fill=BODY)
        d.rectangle((x + (px - 0.04) * w, y, x + (px - 0.01) * w, y + h), fill=LIGHT)
    _run_rungs(d, x + 0.18 * w, x + 0.82 * w, y, h, h / 2, 26, EDGE)


def big_pipe(d, x, y, w, h):
    d.rectangle((x + 0.18 * w, y, x + 0.82 * w, y + h), fill=BODY)
    d.rectangle((x + 0.36 * w, y, x + 0.44 * w, y + h), fill=LIGHT)
    _run_rungs(d, x + 0.12 * w, x + 0.88 * w, y, h, h / 2, 34, EDGE)
    for k in range(2):
        cy = y + (k + 0.5) * h / 2
        for bx in (0.16, 0.84):
            _disc(d, x + bx * w, cy, 10, fill=DARK, line=DARK, width=1)


def tensile(d, x, y, w, h):
    _box(d, x, y, x + w, y + h, fill=BODY, line=BODY, width=0)
    for sx in (0.12, 0.88):
        d.rectangle((x + sx * w - 18, y, x + sx * w + 18, y + h), fill=LIGHT)
    d.rectangle((x + 0.44 * w, y, x + 0.56 * w, y + h), fill=DARK)
    _run_rungs(d, x + 0.2 * w, x + 0.8 * w, y, h, h / 4, 16, EDGE)


def trough_belt(d, x, y, w, h):
    for sx in (0.08, 0.92):
        d.rectangle((x + sx * w - 16, y, x + sx * w + 16, y + h), fill=EDGE)
    d.rectangle((x + 0.16 * w, y, x + 0.84 * w, y + h), fill=DARK)
    _run_rungs(d, x + 0.1 * w, x + 0.9 * w, y, h, h / 4, 20, BODY)
    d.rectangle((x + 0.3 * w, y, x + 0.7 * w, y + h), fill=EDGE)


def header(d, x, y, w, h):
    """One lagged overhead header pipe on hangers."""
    d.rectangle((x + 0.22 * w, y, x + 0.78 * w, y + h), fill=BODY)
    d.rectangle((x + 0.36 * w, y, x + 0.44 * w, y + h), fill=LIGHT)
    _run_rungs(d, x + 0.16 * w, x + 0.84 * w, y, h, h / 4, 14, EDGE)
    _run_rungs(d, x + 0.04 * w, x + 0.96 * w, y, h, h / 2, 22, DARK)


def manifold_run(d, x, y, w, h):
    d.rectangle((x + 0.08 * w, y, x + 0.92 * w, y + h), fill=DARK)
    for px in (0.3, 0.7):
        d.rectangle((x + (px - 0.14) * w, y, x + (px + 0.14) * w, y + h), fill=BODY)
    _run_rungs(d, x + 0.04 * w, x + 0.96 * w, y, h, h / 2, 22, EDGE)


def pipe_rack_run(d, x, y, w, h):
    _run_rungs(d, x + 0.02 * w, x + 0.98 * w, y, h, h / 2, 26, EDGE)
    for px, r in ((0.22, 0.12), (0.52, 0.15), (0.8, 0.09)):
        d.rectangle((x + (px - r) * w, y, x + (px + r) * w, y + h), fill=BODY)
        d.rectangle((x + (px - r * 0.4) * w, y, x + (px - r * 0.1) * w, y + h), fill=LIGHT)


def duct(d, x, y, w, h):
    d.rectangle((x + 0.18 * w, y, x + 0.82 * w, y + h), fill=BODY)
    _run_rungs(d, x + 0.14 * w, x + 0.86 * w, y, h, h / 4, 18, EDGE)
    d.rectangle((x + 0.46 * w, y, x + 0.54 * w, y + h), fill=EDGE)


RUN_DRAW = {"conveyor": conveyor, "trench": lambda d, x, y, w, h: covered_trench(d, x, y, w, h),
            "trench-frost": lambda d, x, y, w, h: covered_trench(d, x, y, w, h, True), "twinpipe": twin_pipes,
            "bigpipe": big_pipe, "tensile": tensile, "troughbelt": trough_belt, "header": header,
            "manifoldrun": manifold_run, "piperun": pipe_rack_run, "duct": duct}


# --- the shared works kit -------------------------------------------------------------------------------------------

def cold_head(d, x, y, w, h):
    """A cryo compressor stack: a plinth, four compressor cylinders, a central cold box, header stubs north."""
    _box(d, x + 14, y + 14, x + w - 14, y + h - 14, width=12)
    for cx, cy in ((0.28, 0.3), (0.72, 0.3), (0.28, 0.72), (0.72, 0.72)):
        _disc(d, x + cx * w, y + cy * h, 0.17 * w, fill=LIGHT, width=12)
        _disc(d, x + cx * w, y + cy * h, 0.07 * w, fill=EDGE, width=6)
    _box(d, x + 0.4 * w, y + 0.38 * h, x + 0.6 * w, y + 0.64 * h, fill=EDGE, line=DARK, width=8)
    for sx in (0.42, 0.58):
        d.rectangle((x + sx * w - 18, y + 14, x + sx * w + 18, y + 0.38 * h), fill=EDGE)
    for k in range(5):
        d.ellipse((x + (0.1 + 0.18 * k) * w - 30, y + 0.5 * h - 24, x + (0.1 + 0.18 * k) * w + 30, y + 0.5 * h + 24),
                  fill=FROST)


def exchanger(d, x, y, w, h):
    """A shell-and-tube heat exchanger lying north-south on two saddles, nozzles east and west."""
    for sy in (0.26, 0.74):
        d.rectangle((x + 0.12 * w, y + sy * h - 22, x + 0.88 * w, y + sy * h + 22), fill=EDGE)
    d.rounded_rectangle((x + 0.26 * w, y + 0.06 * h, x + 0.74 * w, y + 0.94 * h), radius=0.24 * w, fill=BODY,
                        outline=EDGE, width=10)
    d.rectangle((x + 0.36 * w, y + 0.1 * h, x + 0.42 * w, y + 0.9 * h), fill=LIGHT)
    for nx, ny in ((0.1, 0.38), (0.9, 0.62)):
        _box(d, x + nx * w - 0.08 * w, y + ny * h - 0.06 * h, x + nx * w + 0.08 * w, y + ny * h + 0.06 * h,
             fill=LIGHT, width=6)
    d.rectangle((x + 0.24 * w, y + 0.8 * h, x + 0.76 * w, y + 0.85 * h), fill=EDGE)


def winder(d, x, y, w, h):
    """A coil winder: base plate, a mandrel across it wound with copper, a winding head on its traverse."""
    _box(d, x + 14, y + 0.1 * h, x + w - 14, y + 0.9 * h, width=10)
    _box(d, x + 0.06 * w, y + 0.28 * h, x + 0.18 * w, y + 0.72 * h, fill=EDGE, line=DARK)
    _box(d, x + 0.82 * w, y + 0.28 * h, x + 0.94 * w, y + 0.72 * h, fill=EDGE, line=DARK)
    d.rectangle((x + 0.18 * w, y + 0.34 * h, x + 0.82 * w, y + 0.66 * h), fill=COIL)
    for k in range(1, 8):
        gx = x + (0.18 + k * 0.08) * w
        d.line((gx, y + 0.34 * h, gx, y + 0.66 * h), fill=EDGE, width=4)
    d.rectangle((x + 0.2 * w, y + 0.14 * h, x + 0.8 * w, y + 0.2 * h), fill=DARK)
    _box(d, x + 0.44 * w, y + 0.1 * h, x + 0.62 * w, y + 0.3 * h, fill=LIGHT)


DROP_SERVICE = {"coolant", "nutrient", "powder", "power", "steam"}


def drop(d, x, y, w, h, service):
    """A wall service cabinet: a plated cabinet, a vent, and the service's couplings on its top."""
    _box(d, x + 0.1 * w, y + 0.04 * h, x + 0.9 * w, y + 0.96 * h, width=10)
    for k in range(4):
        d.rectangle((x + 0.22 * w, y + (0.08 + 0.05 * k) * h, x + 0.78 * w, y + (0.1 + 0.05 * k) * h), fill=DARK)
    cx = x + w / 2
    if service == "coolant":
        for cy in (0.48, 0.74):
            _disc(d, cx, y + cy * h, 0.24 * w, fill=FROST, width=8)
            _disc(d, cx, y + cy * h, 0.1 * w, fill=EDGE, width=4)
    elif service == "nutrient":
        for cy in (0.46, 0.66, 0.84):
            _disc(d, cx, y + cy * h, 0.18 * w, fill=CULTURE, width=8)
    elif service == "powder":
        d.polygon([(x + 0.2 * w, y + 0.42 * h), (x + 0.8 * w, y + 0.42 * h), (x + 0.62 * w, y + 0.72 * h),
                   (x + 0.38 * w, y + 0.72 * h)], fill=EDGE, outline=DARK)
        _disc(d, cx, y + 0.84 * h, 0.14 * w, fill=DARK, width=4)
    elif service == "power":
        for k in range(3):
            d.rectangle((x + (0.24 + 0.2 * k) * w, y + 0.42 * h, x + (0.32 + 0.2 * k) * w, y + 0.9 * h), fill=COIL)
        d.rectangle((x + 0.3 * w, y + 0.36 * h, x + 0.7 * w, y + 0.39 * h), fill=GLOW)
    elif service == "steam":
        d.rectangle((cx - 0.16 * w, y + 0.4 * h, cx + 0.16 * w, y + 0.94 * h), fill=LIGHT)
        _disc(d, cx, y + 0.62 * h, 0.3 * w, fill=None, line=EDGE, width=10)
        d.line((cx - 0.3 * w, y + 0.62 * h, cx + 0.3 * w, y + 0.62 * h), fill=EDGE, width=8)


def console_run(d, x, y, w, h):
    """A run of control desks along the line, seen from above: three screens and a key shelf to the south."""
    _box(d, x + 10, y + 12, x + w - 10, y + h - 12, width=10)
    for k in range(3):
        x0 = x + (0.05 + k * 0.32) * w
        d.rectangle((x0, y + 0.18 * h, x0 + 0.26 * w, y + 0.55 * h), fill=DARK)
        d.rectangle((x0 + 0.02 * w, y + 0.66 * h, x0 + 0.24 * w, y + 0.8 * h), fill=LIGHT)
    d.ellipse((x + w - 44, y + 22, x + w - 22, y + 44), fill=GLOW)


def draw_kind(im, d, kind, x, y, w, h):
    """Draw ``kind`` if it is a works-hall kind; False if not, so ``make_guide`` draws it."""
    base, _, state = kind.partition("/")
    if base in RUN_DRAW:
        if state == "fallen":
            _fallen(im, x, y, w, h, RUN_DRAW[base])
        else:
            RUN_DRAW[base](d, x, y, w, h)
        return True
    if base == "drop" and state in DROP_SERVICE:
        drop(d, x, y, w, h, state)
        return True
    simple = {"coldhead": cold_head, "exchanger": exchanger, "winder": winder, "console": console_run}
    if base in simple:
        simple[base](d, x, y, w, h)
        return True
    return draw_more(im, d, base, state, x, y, w, h)


# --- blocks, crates, skids ------------------------------------------------------------------------------------------

def shield_block(d, x, y, w, h):
    """A shield block: flush to all four edges so a run of them reads as one wall."""
    d.rectangle((x, y, x + w, y + h), fill=BODY)
    _box(d, x + 0.14 * w, y + 0.14 * h, x + 0.86 * w, y + 0.86 * h, fill=LIGHT, width=6)
    for bx, by in ((0.07, 0.07), (0.93, 0.07), (0.07, 0.93), (0.93, 0.93)):
        _disc(d, x + bx * w, y + by * h, 7, fill=EDGE, line=EDGE, width=1)


def blast_wall(d, x, y, w, h):
    d.rectangle((x, y, x + w, y + h), fill=EDGE)
    d.rectangle((x, y + 0.12 * h, x + w, y + 0.88 * h), fill=BODY)
    for k in (0.33, 0.67):
        d.rectangle((x + k * w - 8, y + 0.12 * h, x + k * w + 8, y + 0.88 * h), fill=EDGE)


def baffle(d, x, y, w, h):
    d.rectangle((x, y + 0.16 * h, x + w, y + 0.84 * h), fill=BODY)
    d.rectangle((x, y + 0.16 * h, x + w, y + 0.28 * h), fill=LIGHT)
    d.rectangle((x, y + 0.72 * h, x + w, y + 0.84 * h), fill=EDGE)
    for k in (0.25, 0.75):
        _disc(d, x + k * w, y + 0.5 * h, 9, fill=DARK, line=DARK, width=1)


def rubble(d, x, y, w, h, seed):
    rng = random.Random(seed)
    for k in range(5):
        cx, cy = x + rng.uniform(0.22, 0.78) * w, y + rng.uniform(0.22, 0.78) * h
        r = rng.uniform(0.12, 0.24) * w
        pts = [(cx + r * rng.uniform(0.6, 1) * math.cos(a), cy + r * rng.uniform(0.6, 1) * math.sin(a))
               for a in (i * math.pi / 3 + rng.uniform(0, 0.5) for i in range(6))]
        d.polygon(pts, fill=(BODY, LIGHT, EDGE)[k % 3], outline=DARK)


def fence(d, x, y, w, h):
    """A test cage's mesh run east-west across its cell, a post at its middle."""
    d.rectangle((x, y + 0.44 * h, x + w, y + 0.56 * h), fill=EDGE)
    for k in range(9):
        gx = x + (k + 0.5) * w / 9
        d.line((gx, y + 0.44 * h, gx, y + 0.56 * h), fill=LIGHT, width=4)
    _box(d, x + 0.4 * w, y + 0.36 * h, x + 0.6 * w, y + 0.64 * h, fill=BODY, width=6)


def crate(d, x, y, w, h, style):
    if style == "housing":
        _box(d, x + 14, y + 14, x + w - 14, y + h - 14, width=10)
        for k in (0.25, 0.5, 0.75):
            d.rectangle((x + k * w - 12, y + 14, x + k * w + 12, y + h - 14), fill=EDGE)
        for cx in (0.06, 0.94):
            for cy in (0.1, 0.9):
                _disc(d, x + cx * w, y + cy * h, 14, fill=LIGHT, width=4)
    elif style == "stack":
        for x0, x1 in ((0.04, 0.48), (0.52, 0.96)):
            _box(d, x + x0 * w, y + 0.08 * h, x + x1 * w, y + 0.92 * h, width=10)
            _box(d, x + (x0 + 0.06) * w, y + 0.24 * h, x + (x1 - 0.06) * w, y + 0.76 * h, fill=LIGHT, width=6)
    else:
        _box(d, x + 14, y + 0.12 * h, x + w - 14, y + 0.88 * h, fill=EDGE, line=DARK, width=10)
        _box(d, x + 0.08 * w, y + 0.2 * h, x + 0.92 * w, y + 0.8 * h, width=6)
        for k in range(4):
            d.rectangle((x + (0.14 + 0.2 * k) * w, y + 0.38 * h, x + (0.26 + 0.2 * k) * w, y + 0.62 * h),
                        fill=LIGHT)


def hover_skid(im, x, y, w, h, load, crashed=False):
    """A hover skid on four lift-fan rings (as the forge's slag skid), carrying its load."""
    sk = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(sk)
    fans = [(0.2, 0.2), (0.8, 0.2), (0.2, 0.8), (0.8, 0.8)]
    if crashed:
        fans = fans[:3]
    sd.rectangle((0.22 * w, 0.12 * h, 0.78 * w, 0.88 * h), fill=EDGE, outline=DARK, width=10)
    for fx, fy in fans:
        sd.ellipse(((fx - 0.14) * w, (fy - 0.14) * h, (fx + 0.14) * w, (fy + 0.14) * h), fill=BODY, outline=EDGE,
                   width=10)
        sd.ellipse(((fx - 0.09) * w, (fy - 0.09) * h, (fx + 0.09) * w, (fy + 0.09) * h), fill=DARK)
    if load == "tank":
        sd.rounded_rectangle((0.3 * w, 0.18 * h, 0.7 * w, 0.82 * h), radius=0.2 * w, fill=BODY, outline=EDGE,
                             width=10)
        sd.ellipse((0.42 * w, 0.24 * h, 0.58 * w, 0.36 * h), fill=CULTURE)
    elif load == "bin":
        sd.rectangle((0.28 * w, 0.2 * h, 0.72 * w, 0.8 * h), fill=DARK, outline=EDGE, width=10)
        for k in range(4):
            sd.line((0.3 * w, (0.26 + 0.14 * k) * h, 0.7 * w, (0.3 + 0.14 * k) * h), fill=LIGHT, width=8)
    elif load == "changer":
        sd.rectangle((0.26 * w, 0.24 * h, 0.74 * w, 0.76 * h), fill=BODY, outline=EDGE, width=10)
        sd.rectangle((0.36 * w, 0.34 * h, 0.64 * w, 0.66 * h), fill=DARK)
        for k in (0.4, 0.6):
            sd.rectangle((0.3 * w, k * h - 8, 0.7 * w, k * h + 8), fill=LIGHT)
    if crashed:
        sk = sk.rotate(16, expand=True, resample=Image.BICUBIC)
        sk = sk.resize((round(0.78 * w), round(0.78 * h)), Image.LANCZOS)
        im.alpha_composite(sk, (x + int(0.03 * w), y + int(0.04 * h)))
        _disc(ImageDraw.Draw(im), x + 0.86 * w, y + 0.86 * h, 0.09 * w, fill=BODY, width=8)
    else:
        im.alpha_composite(sk, (x, y))


def carrier(im, x, y, w, h, derailed=False):
    """A mag-rail carrier with a cradle holding one cryo unit."""
    c = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    cd = ImageDraw.Draw(c)
    m = 0.07 if derailed else 0
    cd.rounded_rectangle((w * (0.16 + m), h * (0.04 + m), w * (0.84 - m), h * (0.96 - m)), radius=0.12 * w,
                         fill=EDGE, outline=DARK, width=10)
    for by in (0.14, 0.86):
        cd.rectangle((w * (0.12 + m), h * by - 14, w * (0.88 - m), h * by + 14), fill=COIL)
    cd.rectangle((w * (0.28 + m), h * (0.22 + m * 0.5), w * (0.72 - m), h * (0.78 - m * 0.5)), fill=FROST,
                 outline=EDGE, width=8)
    cd.rectangle((w * (0.36 + m), h * 0.3, w * (0.64 - m), h * 0.7), fill=LIGHT)
    if derailed:
        c = c.rotate(-20, expand=True, resample=Image.BICUBIC)
        k = min(w / c.width, h / c.height) * 0.96
        c = c.resize((round(c.width * k), round(c.height * k)), Image.LANCZOS)
    im.alpha_composite(c, (x + (w - c.width) // 2, y + (h - c.height) // 2))


# --- vessels ----------------------------------------------------------------------------------------------------------

def tank(d, x, y, w, h, style):
    """A round upright vessel seen from above: its dome, a ring of bolts or a walkway, a manway, nozzle stubs."""
    cx, cy, r = x + w / 2, y + h / 2, min(w, h) / 2 - 16
    if style in ("column", "product"):
        _box(d, x + 12, y + 12, x + w - 12, y + h - 12, fill=EDGE, line=DARK, width=8)
        for k in range(1, 6):
            gx = x + k * w / 6
            d.line((gx, y + 14, gx, y + h - 14), fill=DARK, width=4)
        r *= 0.86
    _disc(d, cx, cy, r, width=12)
    _disc(d, cx, cy, r * 0.78, fill=LIGHT if style != "column" else BODY, width=6)
    if style == "column":
        for k in range(16):
            a = k * math.pi / 8
            _disc(d, cx + r * 0.88 * math.cos(a), cy + r * 0.88 * math.sin(a), 9, fill=DARK, line=DARK, width=1)
        _disc(d, cx, cy, r * 0.3, fill=EDGE, width=8)
        _disc(d, cx, cy, r * 0.12, fill=DARK, width=4)
    else:
        _disc(d, cx + r * 0.32, cy - r * 0.3, r * 0.16, fill=EDGE, width=6)
        for a in (math.pi * 0.75, math.pi * 1.25):
            _box(d, cx + r * math.cos(a) - 18, cy + r * math.sin(a) - 18, cx + r * math.cos(a) + 18,
                 cy + r * math.sin(a) + 18, fill=EDGE, width=4)
    if style == "nutrient":
        _disc(d, cx - r * 0.25, cy + r * 0.25, r * 0.14, fill=CULTURE, width=6)
    if style == "precursor":
        for k in range(3):
            d.arc((cx - r * 0.6, cy - r * 0.6, cx + r * 0.6, cy + r * 0.6), 30 + k * 120, 70 + k * 120, fill=EDGE,
                  width=14)


def silo(im, d, x, y, w, h, split=False):
    """A feedstock silo: its round roof with radial ribs and a fill hatch; the split one torn open, powder spilled."""
    cx, cy, r = x + w / 2, y + h / 2, min(w, h) / 2 - 20
    _disc(d, cx, cy, r, width=14)
    for k in range(12):
        a = k * math.pi / 6
        d.line((cx + r * 0.25 * math.cos(a), cy + r * 0.25 * math.sin(a), cx + r * 0.96 * math.cos(a),
                cy + r * 0.96 * math.sin(a)), fill=EDGE, width=8)
    _disc(d, cx, cy, r * 0.22, fill=LIGHT, width=8)
    if split:
        d.polygon([(cx + r * 0.1, cy + r * 0.05), (cx + r * 0.98, cy + r * 0.1), (cx + r * 0.9, cy + r * 0.5),
                   (cx + r * 0.3, cy + r * 0.3)], fill=DARK)
        pts = [(cx + r * 0.7, cy + r * 0.2), (x + w - 8, cy + r * 0.05), (x + w - 8, y + h - 10),
               (cx + r * 0.6, y + h - 10), (cx + r * 0.55, cy + r * 0.6)]
        d.polygon(pts, fill=(196, 192, 182, 255))


def tower(d, x, y, w, h, style):
    """A square tower or chamber on its plinth: the draw tower's furnace bore, or a coating chamber's round door."""
    _box(d, x + 12, y + 12, x + w - 12, y + h - 12, width=12)
    for cx_, cy_ in ((0.12, 0.12), (0.88, 0.12), (0.12, 0.88), (0.88, 0.88)):
        _box(d, x + (cx_ - 0.07) * w, y + (cy_ - 0.07) * h, x + (cx_ + 0.07) * w, y + (cy_ + 0.07) * h, fill=EDGE,
             line=DARK, width=4)
    cx, cy = x + w / 2, y + h / 2
    if style == "draw":
        _disc(d, cx, cy, 0.3 * w, fill=EDGE, width=10)
        _disc(d, cx, cy, 0.16 * w, fill=DARK, width=6)
        _disc(d, cx, cy, 0.05 * w, fill=GLOW, line=GLOW, width=1)
        d.rectangle((x + 0.2 * w, y + 0.06 * h, x + 0.8 * w, y + 0.1 * h), fill=COIL)
    elif style == "coating":
        _disc(d, cx, cy, 0.34 * w, fill=LIGHT, width=10)
        _disc(d, cx, cy, 0.24 * w, fill=BODY, width=6)
        d.rectangle((cx + 0.3 * w, cy - 0.06 * h, x + w - 20, cy + 0.06 * h), fill=EDGE)
    elif style == "lattice":
        _box(d, x + 0.22 * w, y + 0.22 * h, x + 0.78 * w, y + 0.78 * h, fill=FROST, width=8)
        _disc(d, cx, cy, 0.14 * w, fill=EDGE, width=6)


# --- benches and presses ----------------------------------------------------------------------------------------------

def bench(d, x, y, w, h, style):
    """Small stations: a plate top with the feature that names it."""
    cx, cy = x + w / 2, y + h / 2
    if style == "lattice":
        _box(d, x + 12, y + 14, x + w - 12, y + h - 14, width=10)
        _box(d, x + 0.08 * w, y + 0.2 * h, x + 0.56 * w, y + 0.8 * h, fill=FROST, width=6)
        for k in range(1, 4):
            d.line((x + (0.08 + 0.12 * k) * w, y + 0.2 * h, x + (0.08 + 0.12 * k) * w, y + 0.8 * h), fill=EDGE,
                   width=4)
        _disc(d, x + 0.78 * w, cy, 0.14 * h * 1.4, fill=EDGE, width=6)
    elif style == "decanter":
        _box(d, x + 14, y + 14, x + w - 14, y + h - 14, fill=EDGE, line=DARK, width=8)
        _disc(d, cx, cy, 0.3 * w, width=10)
        _disc(d, cx, cy, 0.12 * w, fill=CULTURE, width=6)
    elif style == "hopper":
        _box(d, x + 14, y + 14, x + w - 14, y + h - 14, width=10)
        d.polygon([(x + 0.2 * w, y + 0.2 * h), (x + 0.8 * w, y + 0.2 * h), (x + 0.62 * w, y + 0.62 * h),
                   (x + 0.38 * w, y + 0.62 * h)], fill=DARK, outline=EDGE)
        _disc(d, cx, y + 0.78 * h, 0.08 * w, fill=EDGE, width=4)
    elif style in ("arming", "emitter"):
        _box(d, x + 14, y + 0.12 * h, x + w - 14, y + 0.88 * h, width=10)
        if style == "arming":
            _box(d, x + 0.2 * w, y + 0.24 * h, x + 0.8 * w, y + 0.62 * h, fill=LIGHT, width=6)
            d.line((x + 0.84 * w, y + 0.2 * h, x + 0.6 * w, y + 0.44 * h), fill=EDGE, width=26)
        else:
            for k in range(3):
                _disc(d, x + (0.25 + 0.25 * k) * w, y + 0.38 * h, 0.08 * w, fill=GLOW, width=6)
            d.rectangle((x + 0.14 * w, y + 0.62 * h, x + 0.86 * w, y + 0.76 * h), fill=DARK)
    elif style == "pylon":
        _disc(d, cx, cy, 0.42 * w, fill=EDGE, line=DARK, width=8)
        _disc(d, cx, cy, 0.24 * w, width=8)
        _disc(d, cx, cy, 0.08 * w, fill=GLOW, width=4)
    elif style == "pedestal":
        _disc(d, cx, cy, 0.4 * w, fill=EDGE, width=8)
        _disc(d, cx, cy, 0.28 * w, fill=DARK, width=6)
        _disc(d, cx, cy, 0.1 * w, fill=GLOW, width=4)
    elif style == "chargehopper":
        _box(d, x + 14, y + 14, x + w - 14, y + h - 14, fill=EDGE, line=DARK, width=8)
        _box(d, x + 0.2 * w, y + 0.2 * h, x + 0.8 * w, y + 0.8 * h, fill=DARK, width=6)
        d.line((x + 0.2 * w, y + 0.2 * h, x + 0.8 * w, y + 0.8 * h), fill=EDGE, width=10)
        d.line((x + 0.8 * w, y + 0.2 * h, x + 0.2 * w, y + 0.8 * h), fill=EDGE, width=10)
    elif style == "diagnostics":
        _box(d, x + 14, y + 14, x + w - 14, y + h - 14, width=10)
        d.rectangle((x + 14, cy - 0.1 * h, x + w - 14, cy + 0.1 * h), fill=LIGHT)
        _box(d, x + 0.3 * w, y + 0.18 * h, x + 0.7 * w, y + 0.82 * h, fill=EDGE, line=DARK, width=6)
        d.rectangle((x + 0.38 * w, cy - 10, x + 0.62 * w, cy + 10), fill=GLOW)
    elif style == "firing":
        _box(d, x + 10, y + 14, x + w - 10, y + h - 14, fill=EDGE, line=DARK, width=8)
        for k in range(3):
            _box(d, x + (0.06 + 0.32 * k) * w, y + 0.26 * h, x + (0.3 + 0.32 * k) * w, y + 0.74 * h, width=6)
    elif style == "dancer":
        _box(d, x + 0.14 * w, y + 12, x + 0.86 * w, y + h - 12, fill=EDGE, line=DARK, width=8)
        _disc(d, cx, y + 0.3 * h, 0.3 * w, fill=LIGHT, width=8)
        d.rectangle((cx - 8, y + 0.3 * h, cx + 8, y + 0.85 * h), fill=DARK)
    elif style in ("roller", "roller-fallen"):
        r = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        rd = ImageDraw.Draw(r)
        for sx in (0.08, 0.92):
            _box(rd, sx * w - 0.06 * w, 0.18 * h, sx * w + 0.06 * w, 0.82 * h, fill=EDGE, line=DARK, width=4)
        rd.rectangle((0.14 * w, 0.34 * h, 0.86 * w, 0.66 * h), fill=LIGHT, outline=EDGE, width=4)
        rd.line((0, 0.5 * h, w, 0.5 * h), fill=DARK, width=4)
        if style == "roller-fallen":
            r = r.rotate(-12, resample=Image.BICUBIC)
        d._image.alpha_composite(r, (x, y))
    else:
        return False
    return True


def press(d, x, y, w, h, style):
    """Heavy 3x3 stations: a press frame or sealer over a product nest, or a tall anchor."""
    cx, cy = x + w / 2, y + h / 2
    _box(d, x + 14, y + 14, x + w - 14, y + h - 14, width=12)
    if style in ("seal", "cabinet", "filler", "filling"):
        for sx in (0.14, 0.86):
            _box(d, x + (sx - 0.08) * w, y + 0.1 * h, x + (sx + 0.08) * w, y + 0.9 * h, fill=EDGE, line=DARK, width=6)
        _box(d, x + 0.26 * w, y + 0.24 * h, x + 0.74 * w, y + 0.76 * h, fill=LIGHT, width=8)
        if style == "seal":
            _box(d, x + 0.34 * w, y + 0.32 * h, x + 0.66 * w, y + 0.68 * h, fill=FROST, width=6)
        elif style == "filler":
            _disc(d, cx, cy, 0.16 * w, fill=CULTURE, width=6)
        elif style == "cabinet":
            for k in range(3):
                d.rectangle((x + 0.32 * w, y + (0.32 + 0.12 * k) * h, x + 0.68 * w, y + (0.38 + 0.12 * k) * h),
                            fill=DARK)
        else:
            _disc(d, cx, cy, 0.14 * w, fill=DARK, width=6)
            d.rectangle((x + 0.2 * w, y + 0.06 * h, x + 0.8 * w, y + 0.14 * h), fill=EDGE)
    elif style == "anchor":
        _disc(d, cx, cy, 0.36 * w, fill=EDGE, width=10)
        _disc(d, cx, cy, 0.18 * w, fill=LIGHT, width=8)
        d.rectangle((cx - 0.06 * w, y + 14, cx + 0.06 * w, cy), fill=DARK)
        for a in range(4):
            ang = a * math.pi / 2 + math.pi / 4
            _disc(d, cx + 0.38 * w * math.cos(ang), cy + 0.38 * w * math.sin(ang), 18, fill=DARK, width=4)
    return True


def magnet(d, x, y, w, h, style):
    """Ring hall pieces: a ring segment (beam pipe north-south through a dipole), an RF cavity, a linac section."""
    cx = x + w / 2
    if style == "segment":
        d.rectangle((cx - 0.08 * w, y, cx + 0.08 * w, y + h), fill=LIGHT)
        _box(d, x + 0.08 * w, y + 0.12 * h, x + 0.92 * w, y + 0.88 * h, width=12)
        for sx in (0.14, 0.86):
            d.rectangle((x + (sx - 0.05) * w, y + 0.16 * h, x + (sx + 0.05) * w, y + 0.84 * h), fill=COIL)
        d.rectangle((cx - 0.1 * w, y + 0.12 * h, cx + 0.1 * w, y + 0.88 * h), fill=EDGE)
        d.rectangle((cx - 0.04 * w, y, cx + 0.04 * w, y + h), fill=DARK)
    elif style == "cavity":
        d.rectangle((cx - 0.06 * w, y, cx + 0.06 * w, y + h), fill=LIGHT)
        for cy in (0.3, 0.7):
            _disc(d, cx, y + cy * h, 0.36 * w, width=10)
            _disc(d, cx, y + cy * h, 0.14 * w, fill=COIL, width=6)
        _box(d, x + 0.78 * w, y + 0.44 * h, x + w - 10, y + 0.56 * h, fill=EDGE, width=4)
    elif style == "linac":
        d.rectangle((cx - 0.06 * w, y, cx + 0.06 * w, y + h), fill=LIGHT)
        _box(d, x + 0.14 * w, y + 0.06 * h, x + 0.86 * w, y + 0.94 * h, fill=FROST, width=10)
        for k in range(5):
            d.rectangle((x + 0.14 * w, y + (0.14 + 0.17 * k) * h, x + 0.86 * w, y + (0.17 + 0.17 * k) * h),
                        fill=EDGE)
    return True


def rack(d, x, y, w, h, style):
    """A tall rack seen from above: shelves across, its contents (cutters, drums)."""
    _box(d, x + 12, y + 12, x + w - 12, y + h - 12, fill=DARK, width=12)
    n = 4
    for k in range(1, n):
        d.rectangle((x + 12, y + k * h / n - 8, x + w - 12, y + k * h / n + 8), fill=EDGE)
    for k in range(n):
        y0 = y + k * h / n
        if style == "cutter":
            for j in range(2):
                _disc(d, x + (0.3 + 0.4 * j) * w, y0 + h / n / 2, 0.15 * w, fill=LIGHT, width=6)
                _disc(d, x + (0.3 + 0.4 * j) * w, y0 + h / n / 2, 0.05 * w, fill=EDGE, width=2)
        elif style == "drum":
            r = min(0.36 * w, 0.42 * h / n)
            _disc(d, x + 0.5 * w, y0 + h / n / 2, r, fill=BODY, width=8)
            _disc(d, x + 0.5 * w, y0 + h / n / 2, r * 0.32, fill=EDGE, width=4)
    return True


# --- mid-size plant ---------------------------------------------------------------------------------------------------

def plant(im, d, x, y, w, h, style):
    cx, cy = x + w / 2, y + h / 2
    if style in ("chiller", "chiller-burst"):
        _box(d, x + 14, y + 14, x + w - 14, y + h - 14, width=12)
        for k in range(3):
            fx = x + (0.2 + 0.3 * k) * w
            _disc(d, fx, y + 0.36 * h, 0.12 * w, fill=EDGE, width=8)
            _disc(d, fx, y + 0.36 * h, 0.04 * w, fill=DARK, width=2)
        d.rectangle((x + 0.08 * w, y + 0.66 * h, x + 0.92 * w, y + 0.84 * h), fill=LIGHT, outline=EDGE, width=6)
        if style == "chiller-burst":
            d.polygon([(x + 0.56 * w, y + 0.6 * h), (x + 0.86 * w, y + 0.56 * h), (x + 0.82 * w, y + 0.9 * h),
                       (x + 0.6 * w, y + 0.86 * h)], fill=DARK)
            d.polygon([(x + 0.7 * w, y + 0.8 * h), (x + w - 6, y + 0.74 * h), (x + w - 6, y + h - 6),
                       (x + 0.62 * w, y + h - 6)], fill=FROST)
    elif style == "preheat":
        _box(d, x + 14, y + 14, x + w - 14, y + h - 14, width=12)
        _box(d, x + 0.08 * w, y + 0.16 * h, x + 0.64 * w, y + 0.84 * h, fill=EDGE, line=DARK, width=8)
        for k in range(4):
            _disc(d, x + (0.16 + 0.13 * k) * w, y + 0.5 * h, 0.045 * w, fill=DARK, width=4)
        _disc(d, x + 0.8 * w, y + 0.32 * h, 0.12 * w, fill=DARK, width=10)
        d.rectangle((x + 0.7 * w, y + 0.6 * h, x + 0.9 * w, y + 0.82 * h), fill=LIGHT, outline=EDGE, width=6)
    elif style == "manifold":
        for k in range(4):
            px = x + (0.16 + 0.22 * k) * w
            d.rectangle((px - 0.05 * w, y, px + 0.05 * w, y + h), fill=EDGE)
        _box(d, x + 0.06 * w, y + 0.24 * h, x + 0.94 * w, y + 0.76 * h, width=12)
        for k in range(4):
            _disc(d, x + (0.16 + 0.22 * k) * w, cy, 0.07 * w, fill=FROST, width=6)
    elif style == "cryostat":
        _box(d, x + 0.04 * w, y + 0.16 * h, x + 0.14 * w, y + 0.84 * h, fill=EDGE, line=DARK)
        _box(d, x + 0.86 * w, y + 0.16 * h, x + 0.96 * w, y + 0.84 * h, fill=EDGE, line=DARK)
        d.rounded_rectangle((x + 0.1 * w, y + 0.12 * h, x + 0.9 * w, y + 0.88 * h), radius=0.3 * h, fill=FROST,
                            outline=EDGE, width=10)
        for k in range(1, 5):
            d.rectangle((x + (0.1 + 0.16 * k) * w - 8, y + 0.12 * h, x + (0.1 + 0.16 * k) * w + 8, y + 0.88 * h),
                        fill=EDGE)
    elif style == "pumps":
        _box(d, x + 10, y + 12, x + w - 10, y + h - 12, fill=EDGE, line=DARK, width=8)
        for k in range(2):
            x0 = x + (0.06 + 0.48 * k) * w
            d.rounded_rectangle((x0, y + 0.24 * h, x0 + 0.24 * w, y + 0.76 * h), radius=30, fill=BODY, outline=EDGE,
                                width=8)
            _disc(d, x0 + 0.33 * w, cy, 0.09 * w, fill=LIGHT, width=6)
    elif style == "ridge":
        rng = random.Random(pid_seed(x, y))
        pts = []
        for i in range(40):
            a = i / 40 * 2 * math.pi
            rr = 1 + 0.12 * math.sin(3 * a + rng.uniform(0, 6)) + 0.06 * math.sin(7 * a)
            pts.append((cx + (w / 2 - 20) * rr * math.cos(a), cy + (h / 2 - 20) * rr * math.sin(a)))
        d.polygon(pts, fill=FROST, outline=LIGHT)
        for k in range(5):
            px = x + (0.15 + 0.17 * k) * w
            d.line((px, y + 0.3 * h, px + 0.06 * w, y + 0.7 * h), fill=LIGHT, width=10)
    elif style == "hoist":
        _box(d, x + 14, y + 0.2 * h, x + w - 14, y + h - 14, width=12)
        _box(d, x + 0.08 * w, y + 0.3 * h, x + 0.18 * w, y + 0.82 * h, fill=EDGE, line=DARK)
        _box(d, x + 0.82 * w, y + 0.3 * h, x + 0.92 * w, y + 0.82 * h, fill=EDGE, line=DARK)
        d.rectangle((x + 0.18 * w, y + 0.36 * h, x + 0.82 * w, y + 0.76 * h), fill=LIGHT, outline=EDGE, width=6)
        for k in range(1, 10):
            d.line((x + (0.18 + 0.064 * k) * w, y + 0.36 * h, x + (0.18 + 0.064 * k) * w, y + 0.76 * h), fill=EDGE,
                   width=4)
        for sx in (0.4, 0.6):
            d.line((x + sx * w, y + 0.36 * h, x + sx * w, y), fill=DARK, width=12)
    elif style == "spoolwinder":
        _box(d, x + 14, y + 14, x + w - 14, y + h - 14, width=12)
        _disc(d, cx, cy, 0.38 * w, fill=EDGE, width=8)
        _disc(d, cx, cy, 0.1 * w, fill=LIGHT, width=8)
        d.rectangle((x + 0.84 * w, y + 0.14 * h, x + 0.94 * w, y + 0.86 * h), fill=DARK)
        _box(d, x + 0.8 * w, y + 0.4 * h, x + 0.96 * w, y + 0.6 * h, fill=LIGHT, width=6)
    elif style == "beamdump":
        d.rectangle((cx - 0.06 * w, y, cx + 0.06 * w, y + 0.3 * h), fill=LIGHT)
        _box(d, x + 14, y + 0.18 * h, x + w - 14, y + h - 14, fill=EDGE, line=DARK, width=12)
        _box(d, x + 0.18 * w, y + 0.32 * h, x + 0.82 * w, y + 0.86 * h, width=10)
        _disc(d, cx, y + 0.42 * h, 0.08 * w, fill=DARK, width=6)
        for k in range(3):
            d.rectangle((x + 0.24 * w, y + (0.56 + 0.1 * k) * h, x + 0.76 * w, y + (0.6 + 0.1 * k) * h), fill=EDGE)
    return True


SPOIL = (124, 106, 86, 255)


def _blob(d, cx, cy, rx, ry, seed, fill, outline=None, n=36):
    rng = random.Random(seed)
    ph = [rng.uniform(0, 6) for _ in range(3)]
    pts = []
    for i in range(n):
        a = i / n * 2 * math.pi
        rr = 1 + 0.1 * math.sin(2 * a + ph[0]) + 0.07 * math.sin(3 * a + ph[1]) + 0.05 * math.sin(5 * a + ph[2])
        pts.append((cx + rx * rr * math.cos(a), cy + ry * rr * math.sin(a)))
    d.polygon(pts, fill=fill, outline=outline)


def cell(im, d, x, y, w, h, style):
    """Big bay pieces: a charge cell open to the east, a great vat, a bore stand, a print bed."""
    cx, cy = x + w / 2, y + h / 2
    if style in ("charge", "charge-open"):
        t = 0.12 * w
        d.rectangle((x + 10, y + 10, x + w - 10, y + h - 10), fill=DARK)
        for box in ((x + 10, y + 10, x + w - 10, y + 10 + t), (x + 10, y + h - 10 - t, x + w - 10, y + h - 10),
                    (x + 10, y + 10, x + 10 + t, y + h - 10)):
            _box(d, *box, fill=BODY, line=EDGE, width=8)
        for k in range(4):
            d.ellipse((x + 0.2 * w + k * 0.16 * w, y + 0.2 * h, x + 0.28 * w + k * 0.16 * w, y + 0.26 * h), fill=FROST)
        _box(d, x + 0.3 * w, y + 0.32 * h, x + 0.78 * w, y + 0.68 * h, fill=EDGE, line=DARK, width=8)
        if style == "charge":
            _box(d, x + 0.38 * w, y + 0.38 * h, x + 0.7 * w, y + 0.62 * h, fill=FROST, width=6)
            d.rectangle((x + w - 10 - t * 0.5, y + 10 + t, x + w - 10, y + h - 10 - t), fill=LIGHT)
        else:
            _box(d, x + 0.38 * w, y + 0.44 * h, x + 0.7 * w, y + 0.62 * h, fill=FROST, width=6)
            _box(d, x + 0.38 * w, y + 0.38 * h, x + 0.54 * w, y + 0.44 * h, fill=LIGHT, width=4)
            door = Image.new("RGBA", (int(t * 0.6), int(h * 0.5)), (0, 0, 0, 0))
            ImageDraw.Draw(door).rectangle((0, 0, door.width - 1, door.height - 1), fill=LIGHT, outline=EDGE, width=6)
            door = door.rotate(-28, expand=True, resample=Image.BICUBIC)
            im.alpha_composite(door, (int(x + w - door.width - 12), int(y + 0.36 * h)))
            for k in range(6):
                d.ellipse((x + 0.12 * w + k * 0.13 * w, y + 0.74 * h, x + 0.2 * w + k * 0.13 * w, y + 0.8 * h),
                          fill=FROST)
    elif style in ("vat", "vat-burst"):
        _disc(d, cx, cy, 0.46 * w, width=14)
        _disc(d, cx, cy, 0.36 * w, fill=LIGHT, width=8)
        for k in range(8):
            a = k * math.pi / 4
            _disc(d, cx + 0.41 * w * math.cos(a), cy + 0.41 * w * math.sin(a), 12, fill=EDGE, width=2)
        _disc(d, cx - 0.1 * w, cy - 0.1 * h, 0.1 * w, fill=CULTURE, width=6)
        _disc(d, cx + 0.16 * w, cy + 0.12 * h, 0.06 * w, fill=EDGE, width=4)
        if style == "vat-burst":
            d.polygon([(cx, cy), (cx + 0.46 * w, cy + 0.04 * h), (cx + 0.4 * w, cy + 0.34 * h)], fill=DARK)
            _blob(d, x + 0.8 * w, y + 0.74 * h, 0.2 * w, 0.24 * h, "burst", CULTURE)
            _blob(d, x + 0.86 * w, y + 0.44 * h, 0.12 * w, 0.14 * h, "burst2", CULTURE)
    elif style == "borestand":
        for sx in (0.16, 0.84):
            _box(d, x + (sx - 0.06) * w, y + 14, x + (sx + 0.06) * w, y + h - 14, fill=EDGE, line=DARK, width=6)
        for k in range(5):
            yy = y + (0.12 + 0.19 * k) * h
            d.rectangle((x + 0.22 * w, yy - 12, x + 0.78 * w, yy + 12), fill=BODY)
        for bx, by in ((0.16, 0.08), (0.84, 0.08), (0.16, 0.92), (0.84, 0.92)):
            _box(d, x + (bx - 0.09) * w, y + (by - 0.05) * h, x + (bx + 0.09) * w, y + (by + 0.05) * h, fill=LIGHT,
                 width=6)
    elif style == "printbed":
        _box(d, x + 12, y + 12, x + w - 12, y + h - 12, fill=EDGE, line=DARK, width=10)
        _box(d, x + 0.08 * w, y + 0.08 * h, x + 0.92 * w, y + 0.92 * h, fill=BODY, width=6)
        for k in range(1, 8):
            d.line((x + (0.08 + 0.105 * k) * w, y + 0.08 * h, x + (0.08 + 0.105 * k) * w, y + 0.92 * h), fill=EDGE,
                   width=3)
            d.line((x + 0.08 * w, y + (0.08 + 0.105 * k) * h, x + 0.92 * w, y + (0.08 + 0.105 * k) * h), fill=EDGE,
                   width=3)
    return True


def row(im, d, x, y, w, h, style):
    """Long pieces laid east-west: rack rows, a sorter, overhead rails, and things fallen across a bay."""
    if style == "corerack":
        _box(d, x + 10, y + 10, x + w - 10, y + h - 10, fill=EDGE, line=DARK, width=8)
        n = 8
        for k in range(n):
            x0 = x + 0.08 * w + k * 0.84 * w / n
            _box(d, x0 + 6, y + 0.16 * h, x0 + 0.84 * w / n - 6, y + 0.84 * h, width=6)
            for j in range(3):
                d.rectangle((x0 + 18, y + (0.28 + 0.2 * j) * h, x0 + 0.84 * w / n - 18, y + (0.32 + 0.2 * j) * h),
                            fill=DARK)
        for sx in (0.04, 0.96):
            d.rectangle((x + (sx - 0.03) * w, y + 0.1 * h, x + (sx + 0.03) * w, y + 0.9 * h), fill=LIGHT)
    elif style == "toppled":
        for k in range(6):
            r = Image.new("RGBA", (int(0.12 * w), int(0.62 * h)), (0, 0, 0, 0))
            rd = ImageDraw.Draw(r)
            rd.rectangle((0, 0, r.width - 1, r.height - 1), fill=BODY, outline=EDGE, width=6)
            for j in range(3):
                rd.rectangle((10, (0.2 + 0.25 * j) * r.height, r.width - 10, (0.26 + 0.25 * j) * r.height), fill=DARK)
            r = r.rotate(-38 - 6 * k, expand=True, resample=Image.BICUBIC)
            im.alpha_composite(r, (int(x + 0.03 * w + k * 0.14 * w), int(y + 0.12 * h)))
    elif style == "launchrack":
        _box(d, x + 10, y + 10, x + w - 10, y + h - 10, fill=DARK, line=EDGE, width=14)
        for k in range(1, 4):
            d.rectangle((x + k * w / 4 - 8, y + 10, x + k * w / 4 + 8, y + h - 10), fill=EDGE)
        for k in range(4):
            fx = x + (k + 0.5) * w / 4
            d.polygon([(fx, y + 0.18 * h), (fx + 0.08 * w, y + 0.5 * h), (fx, y + 0.82 * h), (fx - 0.08 * w,
                       y + 0.5 * h)], fill=BODY, outline=EDGE)
    elif style == "sorter":
        _box(d, x + 10, y + 10, x + w - 10, y + h - 10, width=10)
        d.rectangle((x + 10, y + 0.4 * h, x + w - 10, y + 0.6 * h), fill=DARK)
        for k in range(3):
            x0 = x + (0.2 + 0.28 * k) * w
            d.polygon([(x0, y + 0.4 * h), (x0 + 0.1 * w, y + 0.4 * h), (x0 + 0.16 * w, y + 0.1 * h),
                       (x0 + 0.06 * w, y + 0.1 * h)], fill=EDGE)
            _box(d, x0 - 0.04 * w, y + 0.66 * h, x0 + 0.12 * w, y + 0.88 * h, fill=LIGHT, width=6)
    elif style == "stackerrail":
        for sy in (0.24, 0.76):
            d.rectangle((x, y + sy * h - 0.1 * h, x + w, y + sy * h + 0.1 * h), fill=BODY, outline=EDGE)
        for k in range(6):
            d.rectangle((x + (k + 0.5) * w / 6 - 8, y + 0.14 * h, x + (k + 0.5) * w / 6 + 8, y + 0.86 * h), fill=EDGE)
        _box(d, x + 0.4 * w, y + 0.08 * h, x + 0.56 * w, y + 0.92 * h, fill=LIGHT, width=8)
    elif style in ("stackerdown", "gantrydown"):
        d.polygon([(x + 10, y + 0.3 * h), (x + 0.76 * w, y + 0.18 * h), (x + 0.8 * w, y + 0.62 * h),
                   (x + 14, y + 0.8 * h)], fill=BODY, outline=EDGE)
        for k in range(1, 9):
            gx = x + k * 0.085 * w
            d.line((gx, y + 0.26 * h, gx, y + 0.76 * h), fill=EDGE, width=8)
        if style == "stackerdown":
            _box(d, x + 0.8 * w, y + 0.1 * h, x + w - 12, y + 0.9 * h, fill=LIGHT, width=8)
        else:
            _box(d, x + 0.36 * w, y + 0.1 * h, x + 0.5 * w, y + 0.9 * h, fill=EDGE, line=DARK, width=6)
            # Loose cable ends lying beside the sheared end, not radiating from it.
            for k in range(3):
                yy = y + (0.3 + 0.18 * k) * h
                d.line((x + 0.8 * w, yy, x + 0.86 * w, yy + 10, x + 0.92 * w, yy - 6), fill=DARK, width=8)
    elif style == "columndown":
        _blob(d, x + 0.66 * w, y + 0.5 * h, 0.3 * w, 0.46 * h, "sludge", (86, 92, 70, 255))
        d.rounded_rectangle((x + 0.04 * w, y + 0.16 * h, x + 0.82 * w, y + 0.84 * h), radius=0.2 * h, fill=BODY,
                            outline=EDGE, width=12)
        for k in range(1, 6):
            d.rectangle((x + (0.04 + 0.13 * k) * w - 8, y + 0.16 * h, x + (0.04 + 0.13 * k) * w + 8, y + 0.84 * h),
                        fill=EDGE)
        # The open east end: a flange ring round a dark bore, square on (a flared cone read as a gun muzzle).
        _box(d, x + 0.8 * w, y + 0.12 * h, x + 0.86 * w, y + 0.88 * h, fill=EDGE, line=DARK, width=6)
        d.rectangle((x + 0.81 * w, y + 0.28 * h, x + 0.85 * w, y + 0.72 * h), fill=DARK)
        for k in range(4):
            px, py = x + (0.88 + 0.025 * k) * w, y + (0.3 + 0.13 * k) * h
            d.rectangle((px - 10, py - 10, px + 10, py + 10), fill=EDGE)
    elif style == "piperack":
        for k in range(4):
            d.rectangle((x + 10, y + (k + 0.5) * h / 4 - 14, x + w - 10, y + (k + 0.5) * h / 4 + 14), fill=EDGE)
        for px, r in ((0.24, 0.12), (0.52, 0.14), (0.78, 0.1)):
            d.rectangle((x + (px - r) * w, y + 10, x + (px + r) * w, y + h - 10), fill=BODY, outline=EDGE, width=4)
    elif style == "booth":
        _box(d, x + 12, y + 12, x + w - 12, y + h - 12, width=12)
        _box(d, x + 0.58 * w, y + 0.08 * h, x + 0.92 * w, y + 0.92 * h, fill=FROST, width=6)
        for k in range(5):
            d.line((x + 0.6 * w, y + (0.2 + 0.14 * k) * h, x + 0.9 * w, y + (0.26 + 0.14 * k) * h), fill=EDGE,
                   width=5)
        for k in range(3):
            d.rectangle((x + 0.14 * w, y + (0.16 + 0.26 * k) * h, x + 0.46 * w, y + (0.3 + 0.26 * k) * h), fill=DARK)
    elif style in ("heap", "slide"):
        _blob(d, x + w / 2, y + h / 2, w / 2 - 20, h / 2 - 20, style, SPOIL, outline=EDGE)
        rng = random.Random(style + "rocks")
        for k in range(14):
            px, py = x + rng.uniform(0.2, 0.8) * w, y + rng.uniform(0.25, 0.75) * h
            r = rng.uniform(10, 26)
            d.ellipse((px - r, py - r * 0.8, px + r, py + r * 0.8), fill=(BODY, EDGE, LIGHT)[k % 3])
    return True


def mega(im, d, x, y, w, h, style):
    """The halls' giant pieces: the headframe and print gantry (overhead, open in the middle), the proving torus,
    the precursor furnace and the range's proof butt."""
    cx, cy = x + w / 2, y + h / 2
    if style == "headframe":
        t = 0.1 * w
        for box in ((x, y, x + w, y + t), (x, y + h - t, x + w, y + h), (x, y, x + t, y + h), (x + w - t, y, x + w, y + h)):
            _box(d, *box, fill=BODY, line=EDGE, width=8)
        for k in range(1, 8):
            d.line((x + k * w / 8, y + 6, x + k * w / 8 + w / 16, y + t - 6), fill=EDGE, width=8)
            d.line((x + k * w / 8, y + h - t + 6, x + k * w / 8 + w / 16, y + h - 6), fill=EDGE, width=8)
        for sx in (0.3, 0.7):
            d.rectangle((x + sx * w - 0.04 * w, y + t, x + sx * w + 0.04 * w, y + h - t), fill=EDGE)
            _disc(d, x + sx * w, cy, 0.12 * w, fill=LIGHT, width=12)
            _disc(d, x + sx * w, cy, 0.03 * w, fill=EDGE, width=4)
        for bx, by in ((0, 0), (1, 0), (0, 1), (1, 1)):
            _box(d, x + bx * (w - 1.4 * t), y + by * (h - 1.4 * t), x + bx * (w - 1.4 * t) + 1.4 * t,
                 y + by * (h - 1.4 * t) + 1.4 * t, fill=EDGE, line=DARK, width=8)
    elif style == "gantry":
        for sy in (0.06, 0.94):
            d.rectangle((x, y + sy * h - 0.05 * h, x + w, y + sy * h + 0.05 * h), fill=BODY, outline=EDGE)
        _box(d, x + 0.42 * w, y, x + 0.58 * w, y + h, width=10)
        for k in range(1, 8):
            d.line((x + 0.42 * w, y + k * h / 8, x + 0.58 * w, y + k * h / 8), fill=EDGE, width=6)
        _box(d, x + 0.36 * w, y + 0.42 * h, x + 0.64 * w, y + 0.58 * h, fill=LIGHT, width=8)
        _disc(d, cx, cy, 0.04 * w, fill=DARK, width=4)
    elif style == "torus":
        d.ellipse((x + 12, y + 12, x + w - 12, y + h - 12), fill=BODY, outline=EDGE, width=16)
        d.ellipse((x + 0.3 * w, y + 0.3 * h, x + 0.7 * w, y + 0.7 * h), fill=DARK, outline=EDGE, width=12)
        for k in range(16):
            a = k * math.pi / 8
            px, py = cx + 0.4 * w * math.cos(a), cy + 0.4 * h * math.sin(a)
            _box(d, px - 0.035 * w, py - 0.035 * w, px + 0.035 * w, py + 0.035 * w, fill=COIL, line=EDGE, width=4)
        d.ellipse((x + 0.38 * w, y + 0.38 * h, x + 0.62 * w, y + 0.62 * h), fill=EDGE)
        d.ellipse((x + 0.45 * w, y + 0.45 * h, x + 0.55 * w, y + 0.55 * h), fill=GLOW)
    elif style == "precursor":
        _box(d, x + 14, y + 14, x + w - 14, y + h - 14, width=12)
        _box(d, x + 0.06 * w, y + 0.1 * h, x + 0.6 * w, y + 0.9 * h, fill=EDGE, line=DARK, width=10)
        for k in range(3):
            d.rectangle((x + 0.1 * w, y + (0.2 + 0.24 * k) * h, x + 0.56 * w, y + (0.3 + 0.24 * k) * h), fill=DARK)
        for k in range(2):
            _disc(d, x + 0.8 * w, y + (0.28 + 0.44 * k) * h, 0.1 * w, fill=DARK, width=10)
    elif style == "butt":
        _blob(d, cx, y + 0.42 * h, 0.48 * w, 0.38 * h, "butt", SPOIL, outline=EDGE, n=60)
        d.rectangle((x + 0.04 * w, y + 0.72 * h, x + 0.96 * w, y + 0.92 * h), fill=BODY, outline=EDGE, width=10)
        for k in range(1, 12):
            d.line((x + (0.04 + 0.0767 * k) * w, y + 0.72 * h, x + (0.04 + 0.0767 * k) * w, y + 0.92 * h), fill=EDGE,
                   width=6)
        rng = random.Random("butt-holes")
        for k in range(20):
            px, py = x + rng.uniform(0.08, 0.92) * w, y + rng.uniform(0.75, 0.9) * h
            d.ellipse((px - 7, py - 7, px + 7, py + 7), fill=DARK)
    return True


def pid_seed(x, y):
    return x * 7919 + y


def draw_more(im, d, base, state, x, y, w, h):
    one = {"shieldblock": shield_block, "blastwall": blast_wall, "baffle": baffle, "fence": fence}
    if base in one:
        one[base](d, x, y, w, h)
    elif base == "rubble":
        rubble(d, x, y, w, h, state or base)
    elif base == "crate":
        crate(d, x, y, w, h, state)
    elif base == "skid":
        load, _, crash = state.partition("-")
        hover_skid(im, x, y, w, h, load, crash == "crashed")
    elif base == "carrier":
        carrier(im, x, y, w, h, state == "derailed")
    elif base == "tank":
        tank(d, x, y, w, h, state)
    elif base == "silo":
        silo(im, d, x, y, w, h, state == "split")
    elif base == "tower":
        tower(d, x, y, w, h, state)
    elif base == "bench":
        return bench(d, x, y, w, h, state)
    elif base == "press":
        press(d, x, y, w, h, state)
    elif base == "magnet":
        magnet(d, x, y, w, h, state)
    elif base == "rack":
        rack(d, x, y, w, h, state)
    elif base == "plant":
        plant(im, d, x, y, w, h, state)
    elif base == "cell":
        cell(im, d, x, y, w, h, state)
    elif base == "row":
        row(im, d, x, y, w, h, state)
    elif base == "mega":
        mega(im, d, x, y, w, h, state)
    else:
        return False
    return True
