"""Writes the damage-doll line art under mod/graphics/ui/dolls/.

A doll is one SVG holding a side elevation (top) and a top plan (bottom), both nose
left, the way every sprite faces. Each location the damage model names is a group
``<g id="part-NAME">`` whose shapes fill with ``currentColor``; the renderer paints
that colour per part from the location's state. Outline and detail strokes carry
fixed colours. See ``damage-doll.md`` and ``d5-located-armor.md``.

Proportions are read off the shipped sprites (``mod/graphics/battle/...``) and drawn
as clean, stylised geometry rather than traced, so the doll reads at the size of a
HUD corner. Plans are stretched along the length a little so a short hull still
fills the strip. The side elevation is a drawing to match the plan; the game has no
side views of its own.

Side elevation: y 8..72, ground at 70.  Top plan: y 80..164, centre line 122.
In the top plan the hull's RIGHT is the upper edge and LEFT the lower edge; the
side elevation shows the LEFT flank.

Run from the repository root:  python art-source/dolls/build_dolls.py
"""

from pathlib import Path

OUT = Path("mod/graphics/ui/dolls")
W, H = 240, 170
LINE = "#dbe5df"
FILL = ".62"
SOLID = ".9"   # parts drawn over another part (turrets, pods), so the one beneath does not show through
GROUND = 70
CY = 122       # top-plan centre line
MID = 120      # horizontal centre


def pts(points):
    return " ".join(f"{x:g},{y:g}" for x, y in points)


def poly(points):
    return f'<polygon points="{pts(points)}"/>'


def rect(x, y, w, h, rx=0):
    r = f' rx="{rx:g}"' if rx else ""
    return f'<rect x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}"{r}/>'


def circle(cx, cy, r):
    return f'<circle cx="{cx:g}" cy="{cy:g}" r="{r:g}"/>'


def ellipse(cx, cy, rx, ry):
    return f'<ellipse cx="{cx:g}" cy="{cy:g}" rx="{rx:g}" ry="{ry:g}"/>'


def line(x1, y1, x2, y2):
    return f'<line x1="{x1:g}" y1="{y1:g}" x2="{x2:g}" y2="{y2:g}"/>'


def path(d):
    return f'<path d="{d}"/>'


def mirror(points):
    """A top-plan outline given as its upper half, closed through its mirror below the centre line."""
    lower = [(x, 2 * CY - y) for x, y in reversed(points)]
    return points + lower


class Doll:
    """Accumulates parts and detail, in drawing order, then writes one SVG."""

    def __init__(self, name):
        self.name = name
        self.body = []

    def part(self, location, *shapes, opacity=FILL):
        inner = "".join(shapes)
        self.body.append(
            f'<g id="part-{location}" fill="currentColor" fill-opacity="{opacity}" '
            f'stroke="{LINE}" stroke-width="1.1" stroke-linejoin="round">{inner}</g>')

    def detail(self, *shapes, opacity=".45", width=".7"):
        inner = "".join(shapes)
        self.body.append(
            f'<g fill="none" stroke="{LINE}" stroke-opacity="{opacity}" stroke-width="{width}" '
            f'stroke-linecap="round" stroke-linejoin="round">{inner}</g>')

    def write(self):
        OUT.mkdir(parents=True, exist_ok=True)
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">'
               f'<title>{self.name} damage doll</title>'
               + "".join(self.body) + "</svg>\n")
        (OUT / f"{self.name}.svg").write_text(svg, encoding="utf-8")
        return self.name


def plates(d, x0, x1, half, nose, rear, strip=7, nose_tip=None, rear_tip=None):
    """The four plates and the hull core of a top plan between x0 (nose) and x1 (tail).

    ``half`` is the body's half width, ``nose``/``rear`` the depth of the front and rear
    bands, ``strip`` the depth of the side plates. ``nose_tip`` narrows the nose to a
    point of that half width (a wedge); ``rear_tip`` chamfers the tail.
    """
    top, bottom = CY - half, CY + half
    tip = half if nose_tip is None else nose_tip
    rt = half if rear_tip is None else rear_tip
    d.part("FRONT", poly([(x0, CY - tip), (x0 + nose * (1 - tip / half) if tip < half else x0, top),
                          (x0 + nose, top), (x0 + nose, bottom),
                          (x0 + nose * (1 - tip / half) if tip < half else x0, bottom), (x0, CY + tip)]))
    d.part("RIGHT", rect(x0 + nose, top, x1 - rear - x0 - nose, strip))
    d.part("LEFT", rect(x0 + nose, bottom - strip, x1 - rear - x0 - nose, strip))
    cham = rear * 0.6 if rt < half else 0
    d.part("REAR", poly([(x1 - rear, top), (x1 - cham, top), (x1, CY - rt), (x1, CY + rt),
                         (x1 - cham, bottom), (x1 - rear, bottom)]))
    d.part("HULL", rect(x0 + nose, top + strip, x1 - rear - x0 - nose, 2 * (half - strip)))


# --------------------------------------------------------------------- tracked

def tracked(name, gun_from=20, gun_width=3.0, screened=False, dozer=False):
    d = Doll(name)
    nose = 30 if dozer else 14
    # Side elevation: track run, then the hull's front glacis, left flank and rear plate.
    d.part("DRIVE", rect(24, 52, 194, 17, rx=8.5))
    d.detail(*[circle(42 + i * 31.5, 61, 6) for i in range(6)], circle(30, 59, 4), circle(210, 59, 4))
    d.part("FRONT", poly([(nose, 52), (nose + 16, 34), (nose + 28, 34), (nose + 14, 52)]))
    d.part("LEFT", poly([(nose + 14, 52), (nose + 28, 34), (206, 34), (200, 52)]))
    d.part("REAR", poly([(200, 52), (206, 34), (220, 38), (222, 52)]))
    d.detail(line(nose + 30, 44, 198, 44))
    if dozer:
        # A small MG cupola: the dozer's only gun.
        d.part("TURRET", poly([(118, 34), (122, 25), (150, 25), (154, 34)]),
               rect(96, 27, 22, 2.4), opacity=SOLID)
        d.part("FRONT", path("M 12 28 Q 3 48 12 68 L 19 68 Q 11 48 19 28 Z"))
        d.detail(line(19, 40, nose + 4, 46), line(19, 58, nose + 6, 54))
    else:
        tur = [poly([(88, 34), (94, 21), (150, 19), (166, 24), (168, 34)]), rect(80, 23, 10, 10, rx=1.5),
               rect(gun_from, 28 - gun_width / 2, 82 - gun_from, gun_width)]
        if screened:
            tur.append(ellipse(140, 17, 9, 4))
        d.part("TURRET", *tur, opacity=SOLID)
        d.detail(rect(130, 15, 10, 5), line(100, 28, 158, 28))

    # Top plan: tracks either side, then the plates and core.
    top_nose = 26 if dozer else 14
    d.part("DRIVE", rect(22, 82, 196, 12, rx=2), rect(22, 150, 196, 12, rx=2))
    d.detail(*[line(28 + i * 12, 83, 28 + i * 12, 93) for i in range(16)],
             *[line(28 + i * 12, 151, 28 + i * 12, 161) for i in range(16)], opacity=".3")
    d.part("FRONT", poly([(top_nose, 106), (top_nose + 10, 94), (40, 94), (40, 150),
                          (top_nose + 10, 150), (top_nose, 138)]))
    d.part("RIGHT", rect(40, 94, 164, 8))
    d.part("LEFT", rect(40, 142, 164, 8))
    d.part("REAR", poly([(204, 94), (216, 94), (222, 100), (222, 144), (216, 150), (204, 150)]))
    d.part("HULL", rect(40, 102, 164, 40))
    d.detail(*[line(176 + i * 6, 107, 176 + i * 6, 137) for i in range(4)], rect(48, 108, 20, 28, rx=2))
    if dozer:
        d.part("TURRET", circle(136, CY, 11), rect(100, CY - 1.3, 26, 2.6), opacity=SOLID)
        d.part("FRONT", rect(8, 78, 7, 88, rx=2))
    else:
        tur_top = [poly([(84, 108), (138, 105), (156, 111), (158, 133), (138, 139), (84, 136), (78, 129), (78, 115)]),
                   rect(gun_from - 4, CY - gun_width / 2, 82 - gun_from + 4, gun_width)]
        if screened:
            tur_top.append(circle(132, CY, 7))
        d.part("TURRET", *tur_top, opacity=SOLID)
        d.detail(line(96, 112, 96, 132))
    return d.write()


# ----------------------------------------------------------------- wheeled APC

def apc(name, pods=False):
    """Six-wheeled APC: wedge nose, slab flanks, a twin-gun turret or (``pods``) two missile pods."""
    d = Doll(name)
    x0, x1 = 22, 218
    L = x1 - x0
    axles = [x0 + L * f for f in (.22, .5, .78)]
    # Side elevation.
    d.part("DRIVE", *[circle(x, 60, 9.5) for x in axles])
    d.detail(*[circle(x, 60, 4) for x in axles])
    d.part("FRONT", poly([(x0, 50), (x0 + 6, 38), (x0 + 30, 30), (x0 + 36, 30), (x0 + 36, 54), (x0 + 4, 54)]))
    d.part("LEFT", poly([(x0 + 36, 30), (x1 - 10, 30), (x1 - 10, 54), (x0 + 36, 54)]))
    d.part("REAR", poly([(x1 - 10, 30), (x1 - 2, 31), (x1, 36), (x1, 54), (x1 - 10, 54)]))
    d.detail(line(x0 + 40, 46, x1 - 14, 46), rect(x0 + 120, 34, 30, 9, rx=1), rect(x0 + 52, 34, 8, 6))
    if pods:
        d.part("TURRET", rect(x0 + 62, 13, 44, 17, rx=2), opacity=SOLID)
        d.detail(*[circle(x0 + 68 + i * 8, 21.5, 2.6) for i in range(5)])
    else:
        d.part("TURRET", poly([(x0 + 64, 30), (x0 + 68, 21), (x0 + 108, 21), (x0 + 112, 30)]),
               rect(x0 + 20, 23.5, 46, 2.6), opacity=SOLID)
    # Top plan: three wheel pairs outboard, plates and core.
    half = 30
    d.part("DRIVE", *[rect(x - 11, CY - half - 9, 22, 9, rx=2) for x in axles],
           *[rect(x - 11, CY + half, 22, 9, rx=2) for x in axles])
    plates(d, x0, x1, half, nose=36, rear=10, strip=7, nose_tip=14, rear_tip=24)
    d.detail(rect(x0 + 120, CY - 13, 40, 26, rx=2), circle(x0 + 88, CY, 10))
    if pods:
        d.part("TURRET", rect(x0 + 64, CY - half - 6, 34, 26, rx=2), rect(x0 + 64, CY + half - 20, 34, 26, rx=2),
               opacity=SOLID)
        d.detail(*[circle(x0 + 72 + c * 9, CY - half + 1 + r * 7, 2.4) for c in range(3) for r in range(2)],
                 *[circle(x0 + 72 + c * 9, CY + half - 8 + r * 7, 2.4) for c in range(3) for r in range(2)])
    else:
        d.part("TURRET", rect(x0 + 62, CY - 16, 46, 32, rx=6),
               rect(x0 + 16, CY - 10, 48, 3), rect(x0 + 16, CY + 7, 48, 3), opacity=SOLID)
    return d.write()


# --------------------------------------------------------------------- pickups

def pickup(name, gun=None, short=False, cargo=False):
    """Four-wheeled pickup: hood, cab, bed. ``gun`` is None, 'mg' or 'srm'."""
    d = Doll(name)
    x0, x1 = (34, 206) if short else (18, 222)
    L = x1 - x0
    hood, cab = x0 + L * .2, x0 + L * .44
    axles = [x0 + L * .17, x0 + L * .78]
    # Side elevation: wheels, hood (front), cab and bed (left flank), tailgate (rear).
    d.part("DRIVE", *[circle(x, 60, 9.5) for x in axles])
    d.detail(*[circle(x, 60, 4) for x in axles])
    d.part("FRONT", poly([(x0, 56), (x0, 42), (x0 + 4, 38), (hood, 37), (hood, 56)]))
    d.part("LEFT", poly([(hood, 37), (hood + 6, 24), (cab - 6, 22), (cab, 36), (x1 - 6, 36),
                         (x1 - 6, 56), (hood, 56)]))
    d.part("REAR", rect(x1 - 6, 34, 6, 22))
    d.detail(line(hood + 8, 26, hood + 14, 36), line(cab - 6, 24, cab - 4, 36),
             line(hood + 2, 46, x1 - 10, 46))
    # Top plan.
    half = 30
    d.part("DRIVE", *[rect(x - 12, CY - half - 6, 24, 7, rx=2) for x in axles],
           *[rect(x - 12, CY + half - 1, 24, 7, rx=2) for x in axles])
    plates(d, x0, x1, half, nose=hood - x0, rear=7, strip=6, rear_tip=27)
    d.detail(rect(hood + 2, CY - half + 8, cab - hood - 4, 2 * half - 16, rx=4),
             line(hood + 8, CY - half + 9, hood + 8, CY + half - 9),
             *[line(cab + 8 + i * 9, CY - half + 9, cab + 8 + i * 9, CY + half - 9) for i in range(int((x1 - cab - 16) / 9))],
             opacity=".35")
    if cargo:
        d.detail(rect(cab + 10, CY - 20, 26, 22, rx=1), rect(cab + 40, CY - 4, 22, 22, rx=1),
                 rect(cab + 66, CY - 22, 20, 18, rx=1), rect(cab + 20, 28, 40, 8), opacity=".55")
    if gun == "mg":
        d.part("TURRET", rect(cab + 14, 26, 4, 10), rect(cab - 18, 23, 40, 3), opacity=SOLID)
        d.part("TURRET", circle(cab + 16, CY, 7), rect(cab - 22, CY - 1.5, 38, 3), opacity=SOLID)
    elif gun == "srm":
        d.part("TURRET", rect(cab + 4, 18, 36, 18, rx=2), opacity=SOLID)
        d.detail(*[circle(cab + 11 + i * 8, 27, 2.6) for i in range(4)])
        d.part("TURRET", rect(cab + 2, CY - 14, 40, 28, rx=3), opacity=SOLID)
        d.detail(*[circle(cab + 10 + c * 8, CY - 6 + r * 12, 2.6) for c in range(2) for r in range(2)])
    elif short:
        # The scout's sensor mast is detail: there is nothing to shoot away.
        d.detail(line(cab + 50, 36, cab + 50, 14), ellipse(cab + 50, 13, 8, 3), circle(cab + 50, CY, 9),
                 opacity=".7", width="1")
    return d.write()


# ----------------------------------------------------------------------- hover

def hover(name, gun=True):
    """Hovercraft: skirt (drive), arrow nose, fan deck; a twin pulse turret or a drone bay."""
    d = Doll(name)
    x0, x1 = 22, 218
    L = x1 - x0
    # Side elevation: the skirt rides on the ground; the body sits on it.
    d.part("DRIVE", path(f"M {x0 + 8} 54 L {x1 - 6} 54 Q {x1 + 2} 54 {x1} 62 Q {x1 - 2} 68 {x1 - 10} 68 "
                         f"L {x0 + 12} 68 Q {x0} 68 {x0 + 2} 61 Q {x0 + 2} 54 {x0 + 8} 54 Z"))
    d.part("FRONT", poly([(x0 + 4, 54), (x0 + 30, 36), (x0 + 50, 34), (x0 + 50, 54)]))
    d.part("LEFT", poly([(x0 + 50, 34), (x1 - 14, 34), (x1 - 14, 54), (x0 + 50, 54)]))
    d.part("REAR", poly([(x1 - 14, 34), (x1 - 6, 36), (x1 - 4, 54), (x1 - 14, 54)]))
    d.detail(line(x0 + 54, 44, x1 - 18, 44), rect(x1 - 70, 26, 18, 8, rx=2), rect(x1 - 44, 26, 18, 8, rx=2))
    if gun:
        d.part("TURRET", poly([(x0 + 70, 34), (x0 + 74, 25), (x0 + 100, 24), (x0 + 106, 34)]),
               rect(x0 + 30, 26.5, 44, 2.4), opacity=SOLID)
    else:
        d.detail(rect(x0 + 70, 28, 34, 6, rx=2), opacity=".6")
    # Top plan: the skirt is a rim around the whole body.
    half = 36
    d.part("DRIVE", rect(x0 - 4, CY - half - 6, L + 8, 2 * half + 12, rx=16))
    nose = 52
    plates(d, x0 + 2, x1 - 2, half - 4, nose=nose, rear=10, strip=6, nose_tip=6, rear_tip=28)
    fans = [(x1 - 46, CY - 16), (x1 - 46, CY + 16)]
    d.detail(*[circle(x, y, 12) for x, y in fans], *[line(x - 8, y, x + 8, y) for x, y in fans],
             *[line(x, y - 8, x, y + 8) for x, y in fans], opacity=".45")
    if gun:
        d.part("TURRET", circle(x0 + 88, CY, 14), rect(x0 + 34, CY - 6, 42, 3), rect(x0 + 34, CY + 3, 42, 3),
               opacity=SOLID)
    else:
        d.detail(*[rect(x0 + 70 + c * 22, CY - 18 + r * 22, 16, 14, rx=2) for c in range(2) for r in range(2)],
                 opacity=".55")
    return d.write()


# ---------------------------------------------------------------------- hauler

def hauler(name):
    """Wayline hauler: a short cab tractor coupled to a long container."""
    d = Doll(name)
    x0, x1 = 12, 228
    cab_end, box0 = 58, 64
    # Side elevation.
    axles = [26, 50, 150, 176, 202]
    d.part("DRIVE", *[circle(x, 62, 7) for x in axles])
    d.part("FRONT", poly([(x0, 58), (x0, 34), (x0 + 6, 26), (x0 + 14, 26), (x0 + 14, 58)]))
    d.part("LEFT", poly([(x0 + 14, 26), (cab_end, 26), (cab_end, 58), (x0 + 14, 58)]),
           rect(box0, 18, x1 - 8 - box0, 38))
    d.part("REAR", rect(x1 - 8, 18, 8, 38))
    d.detail(line(cab_end, 50, box0, 50), *[line(box0 + 14 + i * 22, 20, box0 + 14 + i * 22, 54) for i in range(7)],
             rect(x0 + 18, 30, 16, 10, rx=1))
    # Top plan.
    half = 22
    d.part("DRIVE", *[rect(x - 6, CY - half - 5, 12, 5, rx=1.5) for x in axles],
           *[rect(x - 6, CY + half, 12, 5, rx=1.5) for x in axles])
    d.part("FRONT", rect(x0, CY - half + 2, 14, 2 * half - 4, rx=3))
    d.part("RIGHT", rect(x0 + 14, CY - half + 2, cab_end - x0 - 14, 5), rect(box0, CY - half, x1 - 8 - box0, 6))
    d.part("LEFT", rect(x0 + 14, CY + half - 7, cab_end - x0 - 14, 5), rect(box0, CY + half - 6, x1 - 8 - box0, 6))
    d.part("REAR", rect(x1 - 8, CY - half, 8, 2 * half))
    d.part("HULL", rect(x0 + 14, CY - half + 7, cab_end - x0 - 14, 2 * half - 14),
           rect(box0, CY - half + 6, x1 - 8 - box0, 2 * half - 12))
    d.detail(line(cab_end, CY, box0, CY), *[line(box0 + 14 + i * 22, CY - half + 7, box0 + 14 + i * 22, CY + half - 7)
                                            for i in range(7)], opacity=".35")
    return d.write()


# ----------------------------------------------------------------------- mechs

def mech(name, width, length, side_length, torso_top, shoulder, head, hip=104, arrow=0.0, profile="box"):
    """A walker: its left side on the left half, its plan on the right half, both nose left.

    A walker is taller than it is long, so its two views stand side by side at full
    height rather than stacked like a hull's. ``width``/``length`` size the torso in
    plan, ``side_length`` and ``torso_top`` in elevation; ``shoulder`` is a pod's
    (length, depth); ``head`` the cockpit's plan size; ``hip`` the hip height in
    elevation; ``arrow`` tapers the torso's nose in plan. ``profile`` is the torso's
    side shape and the legs under it: ``box`` on pillar legs (Bulwark), ``lean`` on
    long reversed knees (Hound), ``wedge`` sloping to the nose (Sirocco).
    """
    d = Doll(name)
    floor = 164
    cx = 64
    tx0, tx1 = cx - side_length / 2, cx + side_length / 2
    split = tx0 + side_length * .55
    bottom = hip + 4

    # Legs: the far leg a step behind, the near leg a step ahead.
    def bird(hx):
        knee = (hx + 14, hip + (floor - hip) * .45)
        ankle = (hx + 2, floor - 10)
        return [path(f"M {hx - 7} {hip} L {knee[0] - 6} {knee[1]} L {ankle[0] - 5} {ankle[1]} "
                     f"L {ankle[0] + 5} {ankle[1]} L {knee[0] + 7} {knee[1] - 2} L {hx + 8} {hip} Z"),
                rect(ankle[0] - 14, floor - 10, 26, 8, rx=2)]

    def pillar(hx):
        knee = hip + (floor - hip) * .5
        return [poly([(hx - 10, hip), (hx + 10, hip), (hx + 8, knee), (hx - 8, knee)]),
                poly([(hx - 9, knee + 2), (hx + 9, knee + 2), (hx + 8, floor - 10), (hx - 8, floor - 10)]),
                rect(hx - 15, floor - 10, 30, 8, rx=2)]

    leg = pillar if profile == "box" else bird
    d.part("LEGS", *leg(cx + 12), opacity=SOLID)
    d.part("LEGS", *leg(cx - 10), opacity=SOLID)

    if profile == "wedge":
        front = [(tx0, bottom - 6), (tx0 + 4, bottom - 18), (split - 10, torso_top), (split, torso_top),
                 (split, bottom), (tx0 + 8, bottom)]
        head_at = split - 30
    elif profile == "lean":
        front = [(tx0, bottom - 6), (tx0 + 4, torso_top + 6), (tx0 + 12, torso_top), (split, torso_top),
                 (split, bottom), (tx0 + 8, bottom)]
        head_at = tx0 + 8
    else:
        front = [(tx0, bottom - 4), (tx0, torso_top + 6), (tx0 + 6, torso_top), (split, torso_top),
                 (split, bottom), (tx0 + 6, bottom)]
        head_at = tx0 + 10
    d.part("TORSO", poly(front))
    d.part("TORSO_REAR", poly([(split, torso_top), (tx1 - 6, torso_top), (tx1, torso_top + 8), (tx1, bottom - 8),
                                (tx1 - 8, bottom), (split, bottom)]))
    d.detail(line(split + 6, (torso_top + bottom) / 2, tx1 - 6, (torso_top + bottom) / 2),
             *[line(tx1 - 8 - i * 5, torso_top + 6, tx1 - 8 - i * 5, torso_top + 14) for i in range(3)])
    sl, sd = shoulder
    sx = split - sl * .45
    d.part("LEFT_SHOULDER", rect(sx, torso_top - sd * .55, sl, sd, rx=2.5), opacity=SOLID)
    d.detail(*[circle(sx + 6 + i * 8, torso_top - sd * .05, 2.4) for i in range(int((sl - 4) / 8))])
    d.part("HEAD", poly([(head_at, torso_top + 2), (head_at + 4, torso_top - 10), (head_at + 20, torso_top - 12),
                         (head_at + 24, torso_top + 2)]), opacity=SOLID)
    gun_y = bottom - 14
    d.part("ARMS", rect(tx0 - 26, gun_y - 3, 40, 6, rx=2), rect(tx0 + 4, gun_y - 8, 22, 16, rx=3), opacity=SOLID)

    # Plan, beside the elevation and level with its torso. RIGHT is the upper side.
    px, py = 182, (torso_top + floor) / 2 - 6
    p0, p1 = px - length / 2, px + length / 2
    psplit = p0 + length * .55
    half = width / 2
    taper = length * arrow
    d.part("TORSO", poly([(p0, py - half + taper * .7), (p0 + taper, py - half), (psplit, py - half),
                          (psplit, py + half), (p0 + taper, py + half), (p0, py + half - taper * .7)]))
    d.part("TORSO_REAR", poly([(psplit, py - half), (p1 - 6, py - half), (p1, py - half + 6),
                               (p1, py + half - 6), (p1 - 6, py + half), (psplit, py + half)]))
    ps = psplit - sl * .45
    d.part("RIGHT_SHOULDER", rect(ps, py - half - sd + 6, sl, sd, rx=2.5), opacity=SOLID)
    d.part("LEFT_SHOULDER", rect(ps, py + half - 6, sl, sd, rx=2.5), opacity=SOLID)
    rows = max(1, int((sd - 6) / 8))
    cols = int((sl - 4) / 8)
    d.detail(*[circle(ps + 6 + c * 8, py - half - sd + 10 + r * 8 + 1, 2.4) for c in range(cols) for r in range(rows)],
             *[circle(ps + 6 + c * 8, py + half - 2 + r * 8 + 1, 2.4) for c in range(cols) for r in range(rows)])
    d.part("ARMS", rect(p0 - 26, py - 3, 40, 6, rx=2), rect(p0 + 2, py - 9, 20, 18, rx=3), opacity=SOLID)
    d.part("HEAD", rect(p0 + 22, py - head[1] / 2, head[0], head[1], rx=4), opacity=SOLID)
    d.detail(line(psplit + 5, py - half + 5, psplit + 5, py + half - 5),
             *[line(p1 - 8 - i * 5, py - 8, p1 - 8 - i * 5, py + 8) for i in range(3)])
    return d.write()


def main():
    written = [
        tracked("tank"),
        tracked("tank-heavy-gun", gun_from=6, gun_width=4.2),
        tracked("tank-screened", gun_from=30, gun_width=3.0, screened=True),
        tracked("tank-dozer", dozer=True),
        apc("apc"),
        apc("lrm-carrier", pods=True),
        pickup("technical", gun="mg"),
        pickup("technical-srm", gun="srm"),
        pickup("truck", cargo=True),
        pickup("scout-car", short=True),
        hover("hover-scout"),
        hover("drone-skimmer", gun=False),
        hauler("hauler"),
        mech("mech-bulwark", width=58, length=62, side_length=66, torso_top=52, shoulder=(38, 24), head=(18, 16),
             hip=106, profile="box"),
        mech("mech-hound", width=32, length=76, side_length=74, torso_top=62, shoulder=(28, 16), head=(16, 12),
             hip=96, profile="lean"),
        mech("mech-sirocco", width=46, length=72, side_length=70, torso_top=58, shoulder=(30, 18), head=(16, 14),
             hip=100, arrow=.32, profile="wedge"),
    ]
    print("wrote", ", ".join(written))


if __name__ == "__main__":
    main()
