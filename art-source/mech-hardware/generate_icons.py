"""Writes the Mech Lab hardware icons to mod/graphics/ui/mech-hardware/.

Each icon is a side-view silhouette pointing right, after MechWarrior 4's lab: a dark shade
for the body and a light highlight for edges, lenses and bores, both semi-transparent, laid
over the piece's hardpoint-coloured bricks so one drawing reads on any colour. Parts are
separate shapes with small gaps between them, which reads as machined panels and keeps the
translucent shade from doubling where shapes would otherwise overlap. The viewBox is the
footprint at the lab's brick ratio (75 x 100 per cell), so the drawing fills its piece.

Run from the repository root:  python art-source/mech-hardware/generate_icons.py
"""
import math
import os

OUT = os.path.join("mod", "graphics", "ui", "mech-hardware")
D = 'fill="#000000" fill-opacity="0.58"'
L = 'fill="#ffffff" fill-opacity="0.5"'
LS = 'fill="#ffffff" fill-opacity="0.3"'
MID = 'fill="#000000" fill-opacity="0.34"'


def svg(w, h, body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}">\n{body}</svg>\n'


def n(v):
    return f'{round(v, 1):g}'


def ell(cx, cy, rx, ry, attr):
    return f'  <ellipse cx="{n(cx)}" cy="{n(cy)}" rx="{n(rx)}" ry="{n(ry)}" {attr}/>\n'


def rect(x, y, w, h, attr, rx=0):
    return (f'  <rect x="{n(x)}" y="{n(y)}" width="{n(w)}" height="{n(h)}"'
            + (f' rx="{n(rx)}"' if rx else '') + f' {attr}/>\n')


def path(d, attr):
    return f'  <path d="{d}" {attr}/>\n'


def stroke(d, col, op, w, extra=''):
    return (f'  <path d="{d}" fill="none" stroke="{col}" stroke-opacity="{op}" stroke-width="{n(w)}"'
            f' stroke-linecap="round" stroke-linejoin="round"{extra}/>\n')


def poly(pts, attr):
    return '  <polygon points="' + ' '.join(f'{n(x)},{n(y)}' for x, y in pts) + f'" {attr}/>\n'


def block(x0, y0, x1, y1, cut, attr=D):
    """A machined block: a rectangle with all four corners cut at 45 degrees."""
    return poly([(x0 + cut, y0), (x1 - cut, y0), (x1, y0 + cut), (x1, y1 - cut),
                 (x1 - cut, y1), (x0 + cut, y1), (x0, y1 - cut), (x0, y0 + cut)], attr)


def hbar(x0, x1, y, t, attr=D, rx=2):
    """A horizontal bar centred on y."""
    return rect(x0, y - t / 2, x1 - x0, t, attr, rx)


# --- missiles -------------------------------------------------------------------------------

def missile(x, y, length, h, kind):
    """One missile, tail at x, nose pointing right, as a single silhouette plus highlights.

    kind 'srm' is stubby with a blunt ogive nose and short fins; 'lrm' is slim with a sharp
    nose, long swept fins and a motor nozzle; 'rocket' is a plain finned rocket.
    """
    hh = h / 2
    if kind == 'lrm':
        fl, fh, nose = length * .30, h * .55, length * .26
    elif kind == 'srm':
        fl, fh, nose = length * .20, h * .36, length * .2
    else:
        fl, fh, nose = length * .24, h * .45, length * .22
    body_end = x + length - nose
    tip = x + length
    if kind == 'lrm':
        nose_top = f'L {n(tip)} {n(y)}'
        nose_bottom = f'L {n(body_end)} {n(y + hh)}'
    else:
        nose_top = (f'C {n(body_end + nose * .55)} {n(y - hh)} {n(tip)} {n(y - hh * .45)} {n(tip)} {n(y)}')
        nose_bottom = (f'C {n(tip)} {n(y + hh * .45)} {n(body_end + nose * .55)} {n(y + hh)} '
                       f'{n(body_end)} {n(y + hh)}')
    d = (f'M {n(x)} {n(y - hh - fh)} L {n(x + fl * .45)} {n(y - hh - fh)} L {n(x + fl)} {n(y - hh)} '
         f'L {n(body_end)} {n(y - hh)} {nose_top} {nose_bottom} '
         f'L {n(x + fl)} {n(y + hh)} L {n(x + fl * .45)} {n(y + hh + fh)} L {n(x)} {n(y + hh + fh)} Z')
    s = path(d, D)
    # Motor nozzle behind the tail.
    s += rect(x - h * .45, y - hh * .6, h * .35, h * .6, D, rx=1)
    # Lit top edge of the body and a band where the warhead seats.
    s += rect(x + fl, y - hh + h * .14, body_end - x - fl - 2, max(1.5, h * .16), L, rx=1)
    s += rect(body_end - h * .3, y - hh + 2, max(2, h * .14), h - 4, LS)
    return s


# 5 x 7 block glyphs for stamped labels; '#' is a filled cell.
GLYPHS = {
    'S': ['.####', '#....', '#....', '.###.', '....#', '....#', '####.'],
    'R': ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
    'M': ['#...#', '##.##', '#.#.#', '#...#', '#...#', '#...#', '#...#'],
    'L': ['#....', '#....', '#....', '#....', '#....', '#....', '#####'],
    'A': ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'B': ['####.', '#...#', '#...#', '####.', '#...#', '#...#', '####.'],
    'C': ['.####', '#....', '#....', '#....', '#....', '#....', '.####'],
    'D': ['####.', '#...#', '#...#', '#...#', '#...#', '#...#', '####.'],
    'G': ['.####', '#....', '#....', '#.###', '#...#', '#...#', '.###.'],
    'H': ['#...#', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'K': ['#...#', '#..#.', '#.#..', '##...', '#.#..', '#..#.', '#...#'],
    'P': ['####.', '#...#', '#...#', '####.', '#....', '#....', '#....'],
    'Q': ['.###.', '#...#', '#...#', '#...#', '#.#.#', '#..#.', '.##.#'],
    'T': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '..#..'],
    'E': ['#####', '#....', '#....', '####.', '#....', '#....', '#####'],
    'F': ['#####', '#....', '#....', '####.', '#....', '#....', '#....'],
    'N': ['#...#', '##..#', '#.#.#', '#..##', '#...#', '#...#', '#...#'],
    'V': ['#...#', '#...#', '#...#', '#...#', '#...#', '.#.#.', '..#..'],
    'I': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '#####'],
    'O': ['.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
    'U': ['#...#', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'],
    'W': ['#...#', '#...#', '#...#', '#.#.#', '#.#.#', '##.##', '#...#'],
    'X': ['#...#', '#...#', '.#.#.', '..#..', '.#.#.', '#...#', '#...#'],
    'Y': ['#...#', '#...#', '.#.#.', '..#..', '..#..', '..#..', '..#..'],
}


def stamped(text, cx, cy, cell):
    """A rack label: bold silver block letters, each stroke framed in near-black, so the word
    reads on any hardpoint colour. Both are opaque, so the frame drawn under every stroke
    never darkens where strokes meet. Runs of cells merge into one rectangle per row."""
    frame = 'fill="#0d100e"'
    silver = 'fill="#d9dcd2"'
    shade = 'fill="#9ea397"'
    width = len(text) * 5 * cell + (len(text) - 1) * cell
    x0, y0 = cx - width / 2, cy - 3.5 * cell
    runs = []
    for i, ch in enumerate(text):
        gx = x0 + i * 6 * cell
        for r, row in enumerate(GLYPHS[ch]):
            c = 0
            while c < 5:
                if row[c] != '#':
                    c += 1
                    continue
                start = c
                while c < 5 and row[c] == '#':
                    c += 1
                runs.append((gx + start * cell, y0 + r * cell, (c - start) * cell))
    edge = cell * .38
    out = ''.join(rect(x - edge, y - edge, w + 2 * edge, cell + 2 * edge, frame) for x, y, w in runs)
    out += ''.join(rect(x, y - cell * .06, w, cell * 1.12, silver) for x, y, w in runs)
    # The lower third of each stroke a step darker, so the metal reads as lit from above.
    out += ''.join(rect(x, y + cell * .66, w, cell * .4, shade) for x, y, w in runs)
    return out


def missile_rack(w, h, rows, kind, margin_x=18):
    """Stacked missiles over a debossed plate stamped SRM or LRM."""
    plate = 0
    s = ''
    pitch = (h - 16 - plate) / rows
    # Body plus both fins stays under the row pitch, so neighbouring missiles never touch.
    mh = pitch * (.4 if kind == 'lrm' else .5)
    for i in range(rows):
        y = 8 + pitch * (i + .5)
        s += missile(margin_x, y, w - margin_x - 8, mh, kind)
    return s


def label_plate(text, cx, cy, cell, pad=None):
    """A dark plate just wide enough for the stamped text, with a lit lower lip."""
    pad = cell * 2.2 if pad is None else pad
    half_w = (len(text) * 6 - 1) * cell / 2 + pad
    half_h = 3.5 * cell + cell * 1.2
    s = block(cx - half_w, cy - half_h, cx + half_w, cy + half_h, cell * 1.1, D)
    s += rect(cx - half_w + cell, cy + half_h - cell * .7, 2 * half_w - 2 * cell, cell * .45, LS)
    return s + stamped(text, cx, cy - cell * .1, cell)


CELL_W, CELL_H = 75, 100


def banded(w, h, art, text):
    """An icon with its designation plate in the bottom-right brick: the same corner on every
    piece, so the label is where the eye already looks. The art keeps its full size; the plate
    sits over its corner like an applied tag."""
    # Letters as large as the brick allows: (6n - 1) cells of text plus the plate's padding.
    cell = min(4.4, (CELL_W - 12) / (len(text) * 6 - 1 + 2.4))
    # Centred across the last brick; sat low in it, clear of the brick's bottom edge.
    # The art keeps clear of the piece's rim: scaled to 88% about the centre. The tag is not
    # scaled, so it stays aligned to the brick it is authored in.
    k = .88
    inset = (f'  <g transform="translate({n(w * (1 - k) / 2)} {n(h * (1 - k) / 2)}) scale({k})">'
             + '\n' + art + '  </g>\n')
    return svg(w, h, inset + label_plate(text, w - CELL_W / 2, h - 4.7 * cell - 8, cell, pad=cell * 1.2))


def upright(art, transform=''):
    """Long guns stand up in their 2x3 pieces, muzzle forward (up), the way the mech carries
    them. The art is authored side-on on a 300x150 canvas, muzzle to the right, and turned a
    quarter left into the 150x300 piece; ``transform`` fits hand-drawn art to that canvas."""
    inner = f'<g transform="{transform}">\n{art}</g>\n' if transform else art
    return f'<g transform="translate(0 300) rotate(-90)">\n{inner}</g>\n'


def upright2(art, transform=''):
    """The 2x2 form of ``upright``: side-on art on a 200x150 canvas, muzzle right, turned a
    quarter left into the 150x200 piece."""
    inner = f'<g transform="{transform}">\n{art}</g>\n' if transform else art
    return f'<g transform="translate(0 200) rotate(-90)">\n{inner}</g>\n'


# --- guns -----------------------------------------------------------------------------------

def gatling(y, x0, x1, s, barrels=4):
    """A rotary cannon, side-on: a compact receiver with its drive motor along the top, a long
    bundle of barrels held by a rear clamp, a mid clamp and a muzzle shroud, and the ammo
    chute under the receiver. Barrels alternate shade so the bundle reads as a turning
    cylinder. The receiver is under a third of the length; the barrels are the weapon."""
    length = x1 - x0
    h = s * 1.15
    rx1 = x0 + length * .3
    g = block(x0, y - h / 2, rx1, y + h / 2, s * .16)
    g += rect(x0 + s * .18, y - h / 2 - s * .16, (rx1 - x0) * .55, s * .14, MID)
    g += rect(x0 + s * .14, y - h / 2 + s * .1, rx1 - x0 - s * .3, max(1.5, s * .05), L)
    g += rect(x0 + s * .14, y + s * .12, rx1 - x0 - s * .28, h / 2 - s * .26, MID)
    g += poly([(x0 + (rx1 - x0) * .3, y + h / 2 + 2), (x0 + (rx1 - x0) * .7, y + h / 2 + 2),
               (x0 + (rx1 - x0) * .62, y + h / 2 + s * .32), (x0 + (rx1 - x0) * .22, y + h / 2 + s * .32)], MID)
    c0 = rx1 + 2
    bundle = s * .86
    g += block(c0, y - bundle / 2 - s * .08, c0 + s * .2, y + bundle / 2 + s * .08, s * .05)
    bx0, bx1 = c0 + s * .2 + 1, x1 - s * .3
    pitch = bundle / barrels
    t = pitch * .8
    for k in range(barrels):
        by = y - bundle / 2 + pitch * (k + .5)
        g += hbar(bx0, bx1, by, t, D if k % 2 == 0 else MID, rx=0)
    g += rect(bx0, y - bundle / 2 + pitch * .5 - t / 2, bx1 - bx0, max(1, t * .28), LS)
    mid = bx0 + (bx1 - bx0) * .5
    g += block(mid - s * .06, y - bundle / 2 - s * .06, mid + s * .06, y + bundle / 2 + s * .06, s * .03)
    g += block(bx1, y - bundle / 2 - s * .07, x1, y + bundle / 2 + s * .07, s * .08)
    g += rect(bx1 + s * .08, y - bundle / 2, max(1.5, s * .04), bundle, LS)
    return g


def laser_cannon(y, x0, x1, s, rings=3):
    """A laser cannon, side-on: a finned power housing, a focusing tube with collars, and an
    emitter head whose lens is a thin lit slit rather than a bulb."""
    h = s * 1.2
    hx1 = x0 + (x1 - x0) * .38
    g = block(x0, y - h / 2, hx1, y + h / 2, s * .2)
    fins = int((hx1 - x0 - s * .4) / (s * .2))
    for k in range(fins):
        fx = x0 + s * .2 + k * s * .2
        g += rect(fx, y - h / 2 - s * .32, s * .09, s * .3, D)
        g += rect(fx, y + h / 2 + s * .02, s * .09, s * .3, D)
    g += rect(x0 + s * .2, y - h / 2 + s * .14, hx1 - x0 - s * .4, max(1.5, s * .06), L)
    g += rect(x0 + s * .2, y + s * .12, hx1 - x0 - s * .4, h / 2 - s * .32, MID)
    g += hbar(x0 + s * .3, hx1 - s * .3, y - s * .06, max(1.5, s * .06), LS, rx=0)
    tx0, tx1 = hx1 + 3, x1 - s * .5
    g += hbar(tx0, tx1, y, s * .62, D, rx=0)
    g += hbar(tx0, tx1, y + s * .16, s * .2, MID, rx=0)
    g += hbar(tx0, tx1, y - s * .22, max(1.5, s * .05), LS, rx=0)
    for k in range(rings):
        cx = tx0 + (tx1 - tx0) * (k + 1) / (rings + 1)
        g += block(cx - s * .06, y - s * .46, cx + s * .06, y + s * .46, s * .04)
    g += block(tx1 + 2, y - s * .52, x1, y + s * .52, s * .12)
    g += rect(x1 - s * .15, y - s * .32, max(1.5, s * .05), s * .64, L)
    return g


def pulse_laser(y, x0, x1, s):
    """A pulse laser, side-on: a short finned capacitor housing and a long cooling jacket of
    close-set rings, ending in a narrow emitter."""
    h = s * 1.1
    hx1 = x0 + (x1 - x0) * .3
    g = block(x0, y - h / 2, hx1, y + h / 2, s * .18)
    for k in range(int((hx1 - x0 - s * .3) / (s * .2))):
        g += rect(x0 + s * .18 + k * s * .2, y - h / 2 - s * .26, s * .09, s * .24, D)
    g += rect(x0 + s * .18, y - h / 2 + s * .12, hx1 - x0 - s * .36, max(1.5, s * .06), L)
    g += rect(x0 + s * .18, y + s * .08, hx1 - x0 - s * .36, h / 2 - s * .26, MID)
    jx0, jx1 = hx1 + 3, x1 - s * .42
    g += hbar(jx0, jx1, y, s * .4, MID, rx=0)
    step = s * .24
    for k in range(int((jx1 - jx0) / step)):
        g += rect(jx0 + k * step, y - s * .32, step * .5, s * .64, D)
    g += hbar(jx0, jx1, y - s * .14, max(1.5, s * .04), LS, rx=0)
    g += block(jx1 + 2, y - s * .3, x1, y + s * .3, s * .08)
    g += rect(x1 - s * .14, y - s * .18, max(1.5, s * .05), s * .36, L)
    return g


def gauss(y, x0, x1, s):
    """A linear (gauss) cannon: capacitor block, twin rails, coil rings, a slug between rails."""
    g = rect(x0, y - s, 46, s * 2, D, rx=6)
    for k in range(3):
        g += rect(x0 + 8 + k * 11, y - s * .75, 5, s * 1.5, L, rx=1)
    g += hbar(x0 + 50, x1, y - s * .55, s * .28)
    g += hbar(x0 + 50, x1, y + s * .55, s * .28)
    span = x1 - x0 - 70
    for k in range(4):
        cx = x0 + 62 + k * span / 4
        g += rect(cx, y - s * .95, 9, s * 1.9, D, rx=2)
        g += rect(cx + 2, y - s * .9, 3, s * 1.8, LS)
    g += ell(x1 - 18, y, s * .3, s * .2, L)
    return g


def autogun(y, x0, x1, s, drum=False):
    """An autogun: receiver, perforated shroud, barrel tip, and a box magazine (or drum)."""
    g = rect(x0, y - s * .8, 52, s * 1.6, D, rx=5)
    g += rect(x0 + 6, y - s * .65, 40, s * .2, L, rx=2)
    sx0, sx1 = x0 + 56, x1 - 30
    g += hbar(sx0, sx1, y, s * .9, D, rx=4)
    holes = max(3, int((sx1 - sx0) / 18))
    for k in range(holes):
        g += ell(sx0 + 12 + k * (sx1 - sx0 - 24) / max(1, holes - 1), y, s * .13, s * .13, L)
    g += hbar(sx1 + 4, x1 - 6, y, s * .42)
    g += rect(x1 - 6, y - s * .55, 6, s * 1.1, D, rx=1)
    if drum:
        g += ell(x0 + 26, y + s * 1.55, s * .72, s * .72, D)
        g += ell(x0 + 26, y + s * 1.55, s * .3, s * .3, L)
    else:
        g += poly([(x0 + 14, y + s * .85), (x0 + 40, y + s * .85), (x0 + 46, y + s * 1.9), (x0 + 20, y + s * 1.9)], D)
        g += rect(x0 + 22, y + s * 1.0, 6, s * .75, LS)
    return g


def build():
    f = {}

    # Missile racks: stacked horizontal missiles.
    f['srm-5'] = banded(150, 200, missile_rack(150, 200, 4, 'srm'), 'SRM')
    f['srm-15'] = banded(150, 300, missile_rack(150, 300, 7, 'srm'), 'SRM')
    f['lrm-5'] = banded(150, 200, missile_rack(150, 200, 4, 'lrm'), 'LRM')
    f['lrm-15'] = banded(150, 300, missile_rack(150, 300, 7, 'lrm'), 'LRM')
    # Pioneer: one rocket riding its cradle rail, a 2x1 piece.
    b = missile(22, 40, 116, 26, 'rocket')
    b += rect(14, 64, 124, 8, D, rx=2) + rect(30, 74, 8, 16, D) + rect(112, 74, 8, 16, D)
    f['pioneer-rocket'] = banded(150, 100, b, 'RKT')

    pts = []
    for i in range(16):
        a = math.pi * 2 * i / 16 - math.pi / 2
        rr = 46 if i % 2 == 0 else 20
        pts.append((50 + rr * math.cos(a), 50 + rr * math.sin(a)))
    inner = [(50 + (x - 50) * .55, 50 + (y - 50) * .55) for x, y in pts]
    f['frag-mark'] = svg(100, 100, poly(pts, 'fill="#000000" fill-opacity="0.7"')
                         + poly(inner, 'fill="#ffffff" fill-opacity="0.75"'))

    # Energy.
    f['shoulder-laser'] = banded(150, 300, upright(laser_cannon(75, 10, 290, 54, rings=5)), 'LAS')
    # Arm guns are one barrel each in a 2x2 piece; a two-armed chassis carries one per arm.
    f['pulse-laser'] = banded(150, 200, upright2(pulse_laser(75, 10, 190, 80)), 'PLS')

    # Thermal lance, a 2x1 piece: fuel tank, feed pipe, nozzle, and the flame it throws.
    b = rect(8, 26, 38, 48, D, rx=12) + rect(14, 32, 26, 8, L, rx=3)
    b += hbar(50, 82, 50, 14)
    b += poly([(84, 38), (102, 44), (102, 56), (84, 62)], D)
    b += path('M 104 44 C 118 36 128 42 146 50 C 128 58 118 64 104 56 Z', D)
    b += path('M 106 47 C 116 43 124 46 134 50 C 124 54 116 57 106 53 Z', L)
    f['thermal-lance'] = banded(150, 100, b, 'TL')

    # Ballistic.
    f['chaingun'] = banded(150, 200, upright2(gatling(75, 10, 190, 84, barrels=5)), 'CG')
    f['chaingun-nose'] = banded(150, 200, gatling(100, 6, 144, 62, barrels=5), 'CG')
    f['linear-cannon'] = banded(150, 200, upright2(gauss(75, 10, 190, 46)), 'LC')

    # Heavy cannon: a tank gun. Breech block, recoil cylinders, a sleeved barrel, the tapered
    # fume extractor partway along, and a baffled muzzle brake.
    b = block(8, 48, 74, 152, 12)
    b += rect(17, 57, 48, 3, L) + rect(17, 106, 48, 36, MID)
    b += hbar(76, 120, 66, 12, MID, rx=0) + hbar(76, 120, 134, 12, MID, rx=0)
    b += block(76, 80, 130, 120, 3)
    for x in (90, 110):
        b += rect(x, 77, 5, 46, D)
    b += poly([(132, 86), (142, 76), (166, 76), (174, 86), (174, 114), (166, 124), (142, 124), (132, 114)], D)
    b += hbar(176, 192, 100, 28, D, rx=0)
    b += block(194, 72, 216, 128, 4)
    b += rect(198, 80, 4, 40, L) + rect(207, 80, 4, 40, L)
    b += hbar(78, 190, 87, 3, LS, rx=0)
    f['heavy-cannon'] = banded(150, 300, upright(b, 'scale(1.35 1) translate(0 -25)'), 'HC')

    # Bastion: one autocannon on its receiver, belt-fed from the ammunition can beneath.
    b = block(8, 40, 70, 110, 10)
    b += rect(16, 48, 46, 3, L) + rect(16, 84, 46, 20, MID)
    b += block(72, 62, 112, 88, 3)
    b += hbar(114, 166, 75, 14, D, rx=0)
    b += block(168, 60, 192, 90, 3) + rect(174, 66, 3, 18, L) + rect(182, 66, 3, 18, L)
    b += hbar(74, 164, 68, 2.5, LS, rx=0)
    b += rect(40, 112, 18, 7, MID)
    b += block(12, 120, 70, 146, 5, D)
    for k in range(2):
        b += rect(20, 126 + k * 10, 42, 3, LS)
    f['bastion-autocannon'] = banded(150, 200, upright2(b), 'AC')

    f['muster-autogun'] = banded(150, 200, upright2(autogun(56, 10, 190, 40)), 'AG')
    f['muster-autogun-nose'] = banded(150, 200, autogun(84, 8, 144, 34), 'AG')

    # Demolition cannon: a short, very wide siege barrel. Breech block, recoil buffers, a
    # tapering barrel bound by heavy bands, and a flared muzzle collar.
    b = block(6, 60, 50, 140, 8)
    b += rect(12, 68, 32, 3, L) + rect(12, 104, 32, 28, MID)
    b += hbar(52, 94, 64, 9, MID, rx=0) + hbar(52, 94, 136, 9, MID, rx=0)
    b += poly([(52, 76), (112, 72), (112, 128), (52, 124)], D)
    for x in (66, 88):
        b += block(x, 70, x + 8, 130, 2)
    b += poly([(114, 66), (128, 60), (144, 60), (144, 140), (128, 140), (114, 134)], D)
    b += rect(138, 70, 3, 60, L)
    b += hbar(54, 110, 80, 2.5, LS, rx=0)
    f['demolition-cannon'] = banded(150, 200, b, 'DC')

    # Quarry breaker: hydraulic cylinder, piston rod and a chisel bit, pointing right.
    b = rect(8, 64, 70, 72, D, rx=10)
    for k in range(3):
        b += rect(16 + k * 18, 72, 8, 56, L, rx=2)
    b += hbar(82, 100, 100, 22)
    b += poly([(104, 78), (124, 82), (146, 100), (124, 118), (104, 122)], D)
    b += poly([(108, 84), (124, 87), (136, 98), (108, 96)], L)
    b += stroke('M 30 64 C 30 40 60 36 90 46', '#000000', .58, 8)
    f['quarry-breaker'] = banded(150, 200, b, 'QB')

    internals(f)
    boats(f)
    vehicles(f)
    return f


# --- internals ------------------------------------------------------------------------------

def turbine_fan(cx, cy, rx, ry, blades=6):
    """An intake fan seen at an angle: a dark ring, a lit hub and swept blades."""
    s = ell(cx, cy, rx, ry, D)
    for k in range(blades):
        a = math.pi * 2 * k / blades
        x0, y0 = cx + math.cos(a) * rx * .22, cy + math.sin(a) * ry * .22
        x1, y1 = cx + math.cos(a + .55) * rx * .82, cy + math.sin(a + .55) * ry * .82
        s += stroke(f'M {n(x0)} {n(y0)} L {n(x1)} {n(y1)}', '#ffffff', .3, max(2, rx * .1))
    return s + ell(cx, cy, rx * .2, ry * .2, L)


def cells(x0, x1, y0, y1, count, gap=5):
    """A bank of upright capacitor cells: dark cylinders with a lit edge and a terminal cap."""
    w = (x1 - x0 - gap * (count - 1)) / count
    s = ''
    for k in range(count):
        x = x0 + k * (w + gap)
        s += rect(x, y0, w, y1 - y0, D, rx=min(6, w * .3))
        s += rect(x + w * .16, y0 + 5, max(1.5, w * .14), y1 - y0 - 10, L, rx=1)
        s += rect(x + w * .3, y0 - 6, w * .4, 5, MID)
    return s


def loader(fast):
    """A missile loader, 2x1: a magazine of rounds feeding a chute; the accelerated feed adds a
    drive motor and a third chevron."""
    s = block(8, 12, 62, 68, 6)
    for y in (22, 36, 50):
        s += poly([(16, y - 4), (44, y - 4), (54, y + 1), (44, y + 6), (16, y + 6)], L)
    s += hbar(64, 132, 40, 12, MID, rx=0)
    for k in range(3 if fast else 2):
        x = 72 + k * 20
        s += poly([(x, 28), (x + 8, 28), (x + 18, 40), (x + 8, 52), (x, 52), (x + 10, 40)], D)
    if fast:
        s += ell(120, 22, 12, 12, D) + ell(120, 22, 4, 4, L)
    return s


def internals(f):
    # Industrial engine, 2x1: an intake ring, a finned turbine casing and an exhaust cone.
    b = block(8, 14, 30, 66, 6) + rect(13, 22, 4, 36, L)
    b += block(34, 22, 110, 62, 5)
    for x in range(42, 106, 10):
        b += rect(x, 12, 5, 9, D)
    b += hbar(40, 104, 30, 3, L, rx=0) + hbar(40, 104, 50, 9, MID, rx=0)
    b += poly([(113, 26), (140, 34), (140, 50), (113, 58)], D) + rect(132, 36, 4, 12, L)
    f['engine-industrial'] = banded(150, 100, b, 'ENG')

    # Uprated engine, 2x2: an upright block with an intake fan, cooling ribs and twin stacks.
    def upright_engine(fans):
        s = block(20, 62, 130, 156, 10)
        for y in range(76, 148, 12):
            s += hbar(30, 120, y, 3, LS, rx=0)
        s += rect(28, 68, 94, 3, L)
        if fans == 1:
            s += turbine_fan(75, 34, 42, 24)
        else:
            s += turbine_fan(46, 34, 28, 20, 5) + turbine_fan(104, 34, 28, 20, 5)
        s += rect(30, 160, 14, 28, D, rx=2) + rect(50, 160, 14, 28, D, rx=2)
        return s
    f['engine-uprated'] = banded(150, 200, upright_engine(1), 'ENG')
    # Military engine: twin fans, armoured side plates and a lit core slit.
    b = upright_engine(2)
    b += rect(8, 70, 8, 80, D, rx=2) + rect(134, 70, 8, 80, D, rx=2)
    b += rect(70, 84, 10, 52, L, rx=2)
    f['engine-military'] = banded(150, 200, b, 'ENG')

    # Capacitor banks: rows of upright cells on a bus bar.
    f['capacitor-factory'] = banded(150, 100, hbar(10, 140, 12, 4, MID, rx=0) + cells(14, 136, 20, 64, 3), 'CAP')
    b = hbar(10, 140, 12, 4, MID, rx=0) + cells(12, 70, 20, 64, 3, gap=3) + cells(80, 138, 20, 64, 3, gap=3)
    f['capacitor-double'] = banded(150, 100, b, 'CAP')
    # Military bank, 2x2: two tiers of cells on their bus bars and a cooling grille between them.
    b = hbar(10, 140, 12, 4, MID, rx=0) + cells(12, 138, 20, 76, 4, gap=4)
    for y in range(90, 110, 6):
        b += hbar(14, 136, y, 3, D, rx=0)
    b += hbar(10, 140, 116, 4, MID, rx=0) + cells(12, 138, 124, 180, 4, gap=4)
    f['capacitor-military'] = banded(150, 200, b, 'CAP')

    # Auxiliary vent array, 2x1: a framed bank of slanted louvres and an exhaust fan.
    b = block(8, 12, 96, 68, 6, MID)
    for x in range(16, 88, 12):
        b += poly([(x, 18), (x + 7, 18), (x + 13, 62), (x + 6, 62)], D)
        b += poly([(x + 7, 18), (x + 9, 18), (x + 15, 62), (x + 13, 62)], LS)
    b += turbine_fan(120, 38, 22, 24, 5)
    f['vent-auxiliary'] = banded(150, 100, b, 'VNT')

    f['loader-standard'] = banded(150, 100, loader(False), 'LDR')
    f['loader-accelerated'] = banded(150, 100, loader(True), 'LDR')
    f['fab'] = fabricator_icon()


# --- boat hardware --------------------------------------------------------------------------

def armour_plate(layers, w, h):
    """A boat's plating as a w x h piece, two bricks wide like every piece: riveted plates, one
    per layer, stepped so each skin laid over the last shows its edge; heavier plating is more
    of them, thicker, and the heavier kinds fill a 2x2."""
    s = ''
    depth = (h - 28) / layers
    for k in range(layers):
        y0 = 14 + k * depth
        x0 = 10 + k * 6
        x1 = w - 10 - k * 6
        s += block(x0, y0, x1, y0 + depth - 4, 5)
        s += rect(x0 + 6, y0 + 3, x1 - x0 - 12, 2.5, LS)
        for x in range(int(x0 + 14), int(x1 - 8), 26):
            s += ell(x, y0 + depth * .55 - 2, 2.6, 2.6, L)
    return s


def hold(kind):
    """A boat's hold as a 2x2 piece seen from above, ramp at the foot. Benches for people; a
    cradle is a clamped frame for one machine, and a heavy cradle the same on a doubled keel."""
    s = block(14, 10, 136, 176, 8, MID)
    s += poly([(30, 176), (120, 176), (110, 194), (40, 194)], D)
    if kind == 'seats':
        for side in (24, 102):
            s += rect(side, 22, 24, 142, D, rx=3)
            for y in range(30, 160, 18):
                s += rect(side + 4, y, 16, 10, L, rx=2)
    else:
        keel = 10 if kind == 'heavy' else 6
        for x in (40, 104):
            s += rect(x, 18, keel, 152, D, rx=2)
        for y in (34, 98, 150):
            s += hbar(36, 114 + keel, y, 8, D, rx=1)
            s += rect(30, y - 8, 10, 16, L, rx=2) + rect(110 + keel, y - 8, 10, 16, L, rx=2)
        if kind == 'heavy':
            s += rect(70, 18, 10, 152, D, rx=2)
    return s


def grenade_launcher(y, x0, x1, s):
    """A drum-fed grenade launcher: a fat drum magazine, a short receiver and a stubby tube."""
    g = ell(x0 + s * 1.1, y, s * 1.05, s * 1.05, D) + ell(x0 + s * 1.1, y, s * .4, s * .4, L)
    g += block(x0 + s * 2.2, y - s * .55, x0 + s * 3.6, y + s * .55, s * .12)
    g += hbar(x0 + s * 3.6, x1, y, s * .7, D, rx=3)
    g += rect(x0 + s * 3.8, y - s * .3, x1 - x0 - s * 4, max(1.5, s * .12), LS)
    g += rect(x1 - 6, y - s * .45, 6, s * .9, D, rx=1)
    return g


def boats(f):
    """Door guns, plating and holds for the Boat Deck's doll; its drives are the lab's engines."""
    # The light door guns lie in a 2x1 piece, side-on and muzzle right, as the lab's engines do.
    f['boat-door-heavy-mg'] = banded(150, 100, autogun(50, 8, 142, 20), 'MG')
    f['boat-door-grenade'] = banded(150, 100, grenade_launcher(50, 8, 142, 16), 'GL')
    f['boat-door-arbalest'] = banded(150, 200, upright2(autogun(75, 10, 190, 38, drum=True)), 'AC')
    b = autogun(75, 10, 178, 48)
    b += block(178, 75 - 24, 192, 75 + 24, 4) + rect(182, 75 - 18, 3, 36, L)
    f['boat-door-hephaestus'] = banded(150, 200, upright2(b), 'HEP')
    f['boat-plating-standard'] = banded(150, 100, armour_plate(1, 150, 100), 'PLT')
    f['boat-plating-reinforced'] = banded(150, 200, armour_plate(2, 150, 200), 'PLT')
    f['boat-plating-armoured'] = banded(150, 200, armour_plate(3, 150, 200), 'PLT')
    f['boat-hold-standard'] = banded(150, 200, hold('seats'), 'HLD')
    f['boat-hold-cradle'] = banded(150, 200, hold('cradle'), 'LFT')
    f['boat-hold-heavy-cradle'] = banded(150, 200, hold('heavy'), 'LFT')

# --- vehicle hardware -----------------------------------------------------------------------

def mortar(y, x0, x1, s):
    """A breech-loaded turret mortar: a short, very wide tube on its recoil slide, banded, with
    a flared muzzle ring."""
    g = hbar(x0, x0 + s * .5, y - s * .55, s * .16, MID, rx=0)
    g += hbar(x0, x0 + s * .5, y + s * .55, s * .16, MID, rx=0)
    g += block(x0, y - s * .38, x0 + s * .5, y + s * .38, s * .08)
    g += rect(x0 + s * .08, y - s * .3, s * .34, max(2, s * .05), L)
    tx0 = x0 + s * .54
    g += poly([(tx0, y - s * .4), (x1 - s * .2, y - s * .46), (x1 - s * .2, y + s * .46), (tx0, y + s * .4)], D)
    for k in range(2):
        bx = tx0 + (x1 - s * .2 - tx0) * (k + 1) / 3
        g += block(bx - s * .05, y - s * .52, bx + s * .05, y + s * .52, s * .03)
    g += block(x1 - s * .17, y - s * .56, x1, y + s * .56, s * .06)
    g += rect(x1 - s * .08, y - s * .4, max(2, s * .04), s * .8, L)
    g += hbar(tx0 + 4, x1 - s * .24, y - s * .24, max(1.5, s * .04), LS, rx=0)
    return g


def bay_supply():
    """A supply bay from above: lashed crates in two stacks, ramp at the foot."""
    s = block(14, 10, 136, 176, 8, MID)
    s += poly([(30, 176), (120, 176), (110, 194), (40, 194)], D)
    for col in (22, 80):
        for row in range(4):
            y = 20 + row * 38
            s += block(col, y, col + 48, y + 32, 3)
            s += rect(col + 4, y + 4, 40, 3, L)
            s += rect(col + 21, y + 2, 6, 28, LS)
    return s


def bay_drones():
    """A drone bay from above: four launch cradles, each holding a quad-rotor drone."""
    s = block(14, 10, 136, 186, 8, MID)
    for cx in (48, 102):
        for cy in (52, 140):
            s += stroke(f'M {cx - 16} {cy - 16} L {cx + 16} {cy + 16} M {cx + 16} {cy - 16} L {cx - 16} {cy + 16}',
                        '#000000', .58, 5)
            for dx, dy in ((-18, -18), (18, -18), (-18, 18), (18, 18)):
                s += ell(cx + dx, cy + dy, 11, 11, D)
                s += ell(cx + dx, cy + dy, 3, 3, L)
            s += block(cx - 8, cy - 8, cx + 8, cy + 8, 3, L)
    return s


def systems_blade():
    """A dozer blade face-on: the curved moldboard with its cutting edge, and the push arms and
    rams that hold it to the hull."""
    s = path('M 10 40 Q 75 20 140 40 L 140 92 Q 75 76 10 92 Z', D)
    s += path('M 10 96 Q 75 82 140 96 L 140 106 Q 75 92 10 106 Z', MID)
    s += path('M 18 46 Q 75 30 132 46 L 132 50 Q 75 34 18 50 Z', L)
    for x in (34, 104):
        s += rect(x, 112, 12, 72, D, rx=2)
        s += rect(x + 3, 118, 3, 50, LS)
    s += rect(56, 118, 9, 50, MID, rx=2) + rect(85, 118, 9, 50, MID, rx=2)
    return s


def systems_aps():
    """An active protection suite: a radar panel over a bank of countermeasure tubes."""
    s = block(18, 14, 132, 64, 6)
    for x in range(28, 124, 10):
        s += rect(x, 22, 5, 34, LS)
    s += rect(68, 66, 14, 14, MID)
    for row in range(3):
        for col in range(3):
            cx, cy = 38 + col * 37, 102 + row * 32
            s += ell(cx, cy, 14, 13, D)
            s += ell(cx, cy, 6, 5, L)
    return s


def systems_mast():
    """A sight mast: a sensor head with two lenses on a telescoping mast."""
    s = block(22, 16, 128, 66, 8)
    s += ell(52, 41, 13, 13, L) + ell(52, 41, 5, 5, D)
    s += ell(98, 41, 13, 13, L) + ell(98, 41, 5, 5, D)
    s += rect(30, 22, 90, 3, LS)
    y = 70
    for k, wdt in enumerate((14, 20, 26)):
        s += rect(75 - wdt / 2, y, wdt, 36, D if k % 2 == 0 else MID)
        y += 38
    s += rect(40, 184, 70, 8, D, rx=2)
    return s


def kit_plate():
    """Applique armour: two riveted plates hung over the hull's own skin, stepped."""
    s = ''
    for k in range(2):
        y0 = 12 + k * 36
        x0 = 8 + k * 6
        s += block(x0, y0, 142 - k * 6, y0 + 32, 5)
        s += rect(x0 + 6, y0 + 3, 122 - k * 12, 2.5, LS)
        for x in range(int(x0 + 12), int(142 - k * 6 - 6), 22):
            s += ell(x, y0 + 18, 2.6, 2.6, L)
    return s


def kit_liner():
    """A spall liner: a quilted blanket of layered aramid, stitched in diamonds."""
    s = block(8, 12, 142, 84, 6, MID)
    s += rect(14, 18, 122, 3, L)
    for x in range(14, 136, 20):
        for y in (32, 52, 72):
            s += poly([(x + 10, y - 8), (x + 18, y), (x + 10, y + 8), (x + 2, y)], D)
    return s


def kit_ready_rack():
    """A ready rack: rounds stood in a clamped frame beside the gun."""
    s = hbar(8, 142, 88, 6, MID, rx=1)
    for k in range(6):
        x = 13 + k * 21
        s += rect(x, 36, 14, 48, D, rx=1)
        s += path(f'M {x} 34 Q {x + 7} 6 {x + 14} 34 Z', D)
        s += rect(x + 2, 42, 3, 34, LS)
        s += rect(x, 72, 14, 4, L)
    return s


def kit_sensor_pair():
    """A sensor pair: two quad-rotor drones, a lens at each hub, on a tether between them."""
    s = stroke('M 46 50 L 104 50', '#000000', .58, 3)
    for cx in (46, 104):
        s += stroke(f'M {cx - 12} 38 L {cx + 12} 62 M {cx + 12} 38 L {cx - 12} 62', '#000000', .58, 4)
        for dx, dy in ((-14, -14), (14, -14), (-14, 14), (14, 14)):
            s += ell(cx + dx, 50 + dy, 9, 9, D)
            s += ell(cx + dx, 50 + dy, 2.6, 2.6, L)
        s += block(cx - 8, 42, cx + 8, 58, 3, MID)
        s += ell(cx, 50, 4.5, 4.5, L)
    return s


def kit_skirts():
    """Spaced skirts: armour panels hung clear of the road wheels on their hangers."""
    s = ''
    for k in range(3):
        x0 = 8 + k * 46
        s += block(x0, 12, x0 + 42, 56, 4)
        s += rect(x0 + 4, 16, 34, 3, L)
        s += ell(x0 + 10, 25, 2.4, 2.4, L) + ell(x0 + 32, 25, 2.4, 2.4, L)
    for x in range(22, 140, 28):
        s += ell(x, 74, 12, 12, MID)
        s += ell(x, 74, 4, 4, LS)
    return s


def vehicles(f):
    """The Mech Lab's sealed vehicle doll, where no mech or boat drawing already shows the part: the
    turret mortar, the supply and drone bays, the systems cards, and the
    bolt-on kits for the rack. A turret gun or a seats bay draws the weapon's or the hold's own icon."""
    f['vehicle-turret-mortar'] = banded(150, 200, upright2(mortar(75, 10, 190, 100)), 'MOR')
    f['vehicle-bay-supply'] = banded(150, 200, bay_supply(), 'SUP')
    f['vehicle-bay-drones'] = banded(150, 200, bay_drones(), 'DRN')
    f['vehicle-systems-blade'] = banded(150, 200, systems_blade(), 'BLD')
    f['vehicle-systems-aps'] = banded(150, 200, systems_aps(), 'APS')
    f['vehicle-systems-mast'] = banded(150, 200, systems_mast(), 'OPT')
    f['vehicle-systems-secondary'] = banded(150, 200, upright2(autogun(75, 10, 190, 34)), 'AUX')
    f['kit-applique-plate'] = banded(150, 100, kit_plate(), 'APP')
    f['kit-spall-liner'] = banded(150, 100, kit_liner(), 'SPL')
    f['kit-ready-rack'] = banded(150, 100, kit_ready_rack(), 'RDY')
    f['kit-spaced-skirts'] = banded(150, 100, kit_skirts(), 'SKT')
    f['kit-sensor-pair'] = banded(150, 100, kit_sensor_pair(), 'SNS')
    # The hull's own systems uprated: a mech's accelerated feed, and a deeper bank of four cells.
    f['kit-accelerated-loader'] = banded(150, 100, loader(True), 'ACL')
    f['kit-capacitor-pack'] = banded(150, 100, hbar(10, 140, 12, 4, MID, rx=0) + cells(14, 136, 20, 64, 4, gap=4), 'CAP')


def fabricator_icon():
    """The stores row's build button: an amber cog with a plus struck through its hub."""
    amber, dark = '#dfc588', '#1a1608'
    pts = []
    teeth = 8
    for i in range(teeth * 4):
        a = (i / (teeth * 4)) * 2 * math.pi - math.pi / 2
        r = 30 if (i % 4) in (1, 2) else 23
        pts.append((32 + r * math.cos(a), 32 + r * math.sin(a)))
    s = poly(pts, f'fill="{amber}"')
    s += ell(32, 32, 15, 15, f'fill="{dark}"')
    s += rect(29, 22, 6, 20, f'fill="{amber}"') + rect(22, 29, 20, 6, f'fill="{amber}"')
    return svg(64, 64, s)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    icons = build()
    for name, text in icons.items():
        with open(os.path.join(OUT, name + '.svg'), 'w', newline='\n') as handle:
            handle.write(text)
    print('wrote', len(icons), 'icons to', OUT)
