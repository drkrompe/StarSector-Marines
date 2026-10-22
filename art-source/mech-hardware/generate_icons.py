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
    f['shoulder-laser'] = svg(225, 200, laser_cannon(100, 10, 214, 54, rings=4))
    f['pulse-laser-dual'] = svg(225, 200, pulse_laser(56, 10, 214, 46) + pulse_laser(144, 10, 214, 46))

    # Thermal lance, a 2x1 piece: fuel tank, feed pipe, nozzle, and the flame it throws.
    b = rect(8, 26, 38, 48, D, rx=12) + rect(14, 32, 26, 8, L, rx=3)
    b += hbar(50, 82, 50, 14)
    b += poly([(84, 38), (102, 44), (102, 56), (84, 62)], D)
    b += path('M 104 44 C 118 36 128 42 146 50 C 128 58 118 64 104 56 Z', D)
    b += path('M 106 47 C 116 43 124 46 134 50 C 124 54 116 57 106 53 Z', L)
    f['thermal-lance'] = svg(150, 100, b)

    # Ballistic.
    f['chaingun-dual'] = svg(225, 200, gatling(52, 10, 214, 52) + gatling(144, 10, 214, 52))
    f['chaingun-nose'] = svg(150, 200, gatling(100, 6, 144, 62, barrels=5))
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
