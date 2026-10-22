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


def missile_rack(w, h, rows, kind, margin_x=18):
    s = ''
    pitch = (h - 16) / rows
    # Body plus both fins stays under the row pitch, so neighbouring missiles never touch.
    mh = pitch * (.4 if kind == 'lrm' else .5)
    for i in range(rows):
        y = 8 + pitch * (i + .5)
        s += missile(margin_x, y, w - margin_x - 8, mh, kind)
    return s


# --- guns -----------------------------------------------------------------------------------

def gatling(y, x0, x1, s):
    """A rotary gun: motor housing, rear clamp, three barrels, mid and muzzle clamps."""
    g = rect(x0, y - s * .95, 48, s * 1.9, D, rx=8)
    g += rect(x0 + 6, y - s * .8, 36, s * .22, L, rx=3)
    g += rect(x0 + 52, y - s * 1.05, 12, s * 2.1, D, rx=2)
    bx0, bx1 = x0 + 68, x1 - 14
    mid = (bx0 + bx1) / 2
    for k in (-1, 0, 1):
        by = y + k * s * .58
        g += hbar(bx0, mid - 7, by, s * .34)
        g += hbar(mid + 7, bx1 - 4, by, s * .34)
        g += hbar(bx0 + 2, bx1 - 6, by - s * .08, s * .07, LS, rx=0)
    g += rect(mid - 5, y - s * 1.0, 10, s * 2.0, D, rx=2)
    g += rect(bx1, y - s * 1.0, 14, s * 2.0, D, rx=2)
    for k in (-1, 0, 1):
        g += ell(x1 - 7, y + k * s * .58, s * .12, s * .12, L)
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
    f['srm-5'] = svg(150, 200, missile_rack(150, 200, 5, 'srm'))
    f['srm-15'] = svg(150, 300, missile_rack(150, 300, 8, 'srm'))
    f['lrm-5'] = svg(150, 200, missile_rack(150, 200, 5, 'lrm'))
    f['lrm-15'] = svg(150, 300, missile_rack(150, 300, 8, 'lrm'))
    # Pioneer: one rocket riding its cradle rail, a 2x1 piece.
    b = missile(22, 40, 116, 26, 'rocket')
    b += rect(14, 64, 124, 8, D, rx=2) + rect(30, 74, 8, 16, D) + rect(112, 74, 8, 16, D)
    f['pioneer-rocket'] = svg(150, 100, b)

    pts = []
    for i in range(16):
        a = math.pi * 2 * i / 16 - math.pi / 2
        rr = 46 if i % 2 == 0 else 20
        pts.append((50 + rr * math.cos(a), 50 + rr * math.sin(a)))
    inner = [(50 + (x - 50) * .55, 50 + (y - 50) * .55) for x, y in pts]
    f['frag-mark'] = svg(100, 100, poly(pts, 'fill="#000000" fill-opacity="0.7"')
                         + poly(inner, 'fill="#ffffff" fill-opacity="0.75"'))

    # Energy.
    b = rect(12, 64, 104, 72, D, rx=8)
    for k in range(7):
        b += rect(20 + k * 13, 42, 8, 18, D, rx=1) + rect(20 + k * 13, 140, 8, 18, D, rx=1)
    b += rect(20, 74, 88, 8, L, rx=3)
    for k, x in enumerate((122, 150, 176)):
        b += rect(x, 72 - k * 3, 22, 56 + k * 6, D, rx=3)
        b += rect(x + 3, 76 - k * 3, 16, 5, LS)
    b += ell(204, 100, 9, 26, D) + ell(206, 100, 5, 18, L)
    f['shoulder-laser'] = svg(225, 200, b)

    b = ''
    for y in (58, 142):
        b += rect(12, y - 26, 70, 52, D, rx=7) + rect(18, y - 20, 58, 7, L, rx=3)
        for k in range(3):
            b += rect(88 + k * 22, y - 20 + k * 2, 16, 40 - k * 4, D, rx=3)
        b += ell(162, y, 7, 15, L)
        for k in range(3):
            b += rect(176 + k * 14, y - 4, 9, 8, L, rx=3)
    f['pulse-laser-dual'] = svg(225, 200, b)

    # Thermal lance, a 2x1 piece: fuel tank, feed pipe, nozzle, and the flame it throws.
    b = rect(8, 26, 38, 48, D, rx=12) + rect(14, 32, 26, 8, L, rx=3)
    b += hbar(50, 82, 50, 14)
    b += poly([(84, 38), (102, 44), (102, 56), (84, 62)], D)
    b += path('M 104 44 C 118 36 128 42 146 50 C 128 58 118 64 104 56 Z', D)
    b += path('M 106 47 C 116 43 124 46 134 50 C 124 54 116 57 106 53 Z', L)
    f['thermal-lance'] = svg(150, 100, b)

    # Ballistic.
    f['chaingun-dual'] = svg(225, 200, gatling(54, 10, 214, 30) + gatling(146, 10, 214, 30))
    f['chaingun-nose'] = svg(150, 200, gatling(100, 6, 144, 36)
                             + stroke('M 30 136 C 30 160 50 172 80 176', '#000000', .58, 12))
    f['linear-cannon-dual'] = svg(225, 200, gauss(52, 10, 214, 32) + gauss(148, 10, 214, 32))

    b = rect(10, 58, 58, 84, D, rx=6) + rect(16, 64, 46, 9, L, rx=3)
    b += hbar(72, 126, 84, 9) + hbar(72, 126, 116, 9)
    b += hbar(72, 126, 100, 26)
    b += hbar(130, 160, 100, 40, D, rx=8)
    b += hbar(164, 184, 100, 24)
    b += rect(188, 74, 26, 52, D, rx=3)
    b += rect(193, 80, 5, 40, L) + rect(203, 80, 5, 40, L)
    b += hbar(76, 182, 92, 4, LS, rx=0)
    f['heavy-cannon'] = svg(225, 200, b)

    b = rect(10, 30, 76, 92, D, rx=6) + rect(16, 36, 64, 9, L, rx=3)
    for y in (56, 96):
        b += hbar(90, 196, y, 18) + rect(198, y - 13, 16, 26, D, rx=3)
        b += hbar(94, 194, y - 5, 4, LS, rx=0)
    b += ell(48, 156, 34, 34, D) + ell(48, 156, 16, 16, L) + ell(48, 156, 6, 6, D)
    f['bastion-autocannon-dual'] = svg(225, 200, b)

    f['muster-autogun-dual'] = svg(225, 200, autogun(42, 10, 214, 26) + autogun(128, 10, 214, 26))
    f['muster-autogun-nose'] = svg(150, 200, autogun(84, 8, 144, 34))

    # Demolition cannon: a stubby howitzer with a wide muzzle and recoil cylinders.
    b = rect(8, 64, 44, 76, D, rx=6) + rect(13, 70, 34, 8, L, rx=3)
    b += hbar(56, 110, 102, 52, D, rx=6)
    b += hbar(56, 104, 70, 10) + hbar(56, 104, 134, 10)
    b += rect(114, 66, 28, 72, D, rx=6)
    b += ell(128, 102, 7, 24, L)
    b += rect(60, 86, 46, 6, LS)
    f['demolition-cannon'] = svg(150, 200, b)

    # Quarry breaker: hydraulic cylinder, piston rod and a chisel bit, pointing right.
    b = rect(8, 64, 70, 72, D, rx=10)
    for k in range(3):
        b += rect(16 + k * 18, 72, 8, 56, L, rx=2)
    b += hbar(82, 100, 100, 22)
    b += poly([(104, 78), (124, 82), (146, 100), (124, 118), (104, 122)], D)
    b += poly([(108, 84), (124, 87), (136, 98), (108, 96)], L)
    b += stroke('M 30 64 C 30 40 60 36 90 46', '#000000', .58, 8)
    f['quarry-breaker'] = svg(150, 200, b)
    return f


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    icons = build()
    for name, text in icons.items():
        with open(os.path.join(OUT, name + '.svg'), 'w', newline='\n') as handle:
            handle.write(text)
    print('wrote', len(icons), 'icons to', OUT)
