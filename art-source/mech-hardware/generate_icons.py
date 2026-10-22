"""Writes the Mech Lab hardware icons to mod/graphics/ui/mech-hardware/.

Each icon is a flat pictogram in two semi-transparent tones (a black shade and a white
highlight) laid over the piece's hardpoint-coloured bricks, after MechWarrior 4's lab, so
one drawing reads on any colour. The viewBox is the footprint at the lab's brick ratio
(75 x 100 per cell), so the drawing fills the piece it is drawn on.

Run from the repository root:  python art-source/mech-hardware/generate_icons.py
"""
import math
import os

OUT = os.path.join("mod", "graphics", "ui", "mech-hardware")
D = 'fill="#000000" fill-opacity="0.55"'
M = 'fill="#000000" fill-opacity="0.3"'
L = 'fill="#ffffff" fill-opacity="0.45"'


def svg(w, h, body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}">\n{body}</svg>\n'


def ell(cx, cy, rx, ry, attr):
    return f'  <ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" {attr}/>\n'


def rect(x, y, w, h, attr, rx=0):
    return (f'  <rect x="{x}" y="{y}" width="{w}" height="{h}"'
            + (f' rx="{rx}"' if rx else '') + f' {attr}/>\n')


def stroke(d, col, op, w, extra=''):
    return (f'  <path d="{d}" fill="none" stroke="{col}" stroke-opacity="{op}" stroke-width="{w}"'
            f' stroke-linecap="round" stroke-linejoin="round"{extra}/>\n')


def poly(pts, attr):
    return '  <polygon points="' + ' '.join(f'{x},{y}' for x, y in pts) + f'" {attr}/>\n'


def plate(w, h):
    return rect(12, 12, w - 24, h - 24, M, rx=6)


def srm_tube(cx, cy, r):
    return (ell(cx, cy, r, r, D)
            + stroke(f'M {cx - r * .75} {cy - r * .35} A {r * .8} {r * .8} 0 0 1 {cx + r * .35} {cy - r * .75}',
                     '#ffffff', .5, max(2, r * .22))
            + ell(cx, cy, r * .38, r * .38, L))


def lrm_cell(cx, cy, s):
    return (rect(cx - s, cy - s, 2 * s, 2 * s, D, rx=s * .15)
            + stroke(f'M {cx - s + 3} {cy + s - 3} L {cx - s + 3} {cy - s + 3} L {cx + s - 3} {cy - s + 3}',
                     '#ffffff', .45, max(2, s * .18))
            + ell(cx, cy, s * .32, s * .32, L))


def wave(y0, x0, x1, amp, wl):
    d = f'M {x0} {y0}'
    x, up = x0, True
    while x < x1 - 1:
        nx = min(x1, x + wl / 2)
        d += f' Q {(x + nx) / 2} {y0 - amp if up else y0 + amp} {nx} {y0}'
        x, up = nx, not up
    return d


def breech(x, y, h):
    return rect(x, y - h / 2, 44, h, D, rx=6) + rect(x + 7, y - h / 2 + 7, 9, h - 14, L, rx=3)


def barrel(x0, x1, y, t):
    return (rect(x0, y - t / 2, x1 - x0, t, D, rx=3)
            + rect(x0 + 4, y - t / 2 + 3, x1 - x0 - 8, max(3, t * .25), L))


def build():
    f = {}
    # Missile: SRM racks are round tubes, LRM racks square cells.
    b = plate(150, 200)
    for cx, cy in [(48, 58), (102, 58), (75, 100), (48, 142), (102, 142)]:
        b += srm_tube(cx, cy, 21)
    f['srm-5'] = svg(150, 200, b)
    b = plate(150, 300)
    for r in range(5):
        for c in range(3):
            b += srm_tube(38 + c * 37, 46 + r * 52, 15)
    f['srm-15'] = svg(150, 300, b)
    b = plate(150, 200)
    for cx, cy in [(50, 58), (100, 58), (75, 100), (50, 142), (100, 142)]:
        b += lrm_cell(cx, cy, 19)
    f['lrm-5'] = svg(150, 200, b)
    b = plate(150, 300)
    for r in range(5):
        for c in range(3):
            b += lrm_cell(38 + c * 37, 46 + r * 52, 15)
    f['lrm-15'] = svg(150, 300, b)

    pts = []
    for i in range(16):
        a = math.pi * 2 * i / 16 - math.pi / 2
        rr = 46 if i % 2 == 0 else 20
        pts.append((round(50 + rr * math.cos(a), 1), round(50 + rr * math.sin(a), 1)))
    inner = [(round(50 + (x - 50) * .55, 1), round(50 + (y - 50) * .55, 1)) for x, y in pts]
    f['frag-mark'] = svg(100, 100, poly(pts, 'fill="#000000" fill-opacity="0.7"')
                         + poly(inner, 'fill="#ffffff" fill-opacity="0.75"'))

    b = poly([(37.5, 8), (50, 26), (50, 70), (62, 86), (50, 84), (46, 92), (29, 92), (25, 84), (13, 86),
              (25, 70), (25, 26)], D)
    b += poly([(37.5, 14), (45, 27), (30, 27)], L) + rect(31, 34, 6, 34, L)
    f['pioneer-rocket'] = svg(75, 100, b)

    # Energy: beam waves.
    b = rect(14, 40, 36, 120, D, rx=6) + rect(20, 52, 10, 96, L, rx=3)
    for y in (62, 100, 138):
        w = wave(y, 52, 210, 15, 44)
        b += stroke(w, '#000000', .6, 13) + stroke(w, '#ffffff', .55, 5)
    f['shoulder-laser'] = svg(225, 200, b)
    b = ''
    for y in (60, 140):
        b += breech(14, y, 56)
        w = wave(y, 62, 210, 17, 38)
        b += (stroke(w, '#000000', .6, 16, ' stroke-dasharray="22 10"')
              + stroke(w, '#ffffff', .55, 6, ' stroke-dasharray="22 10"'))
    f['pulse-laser-dual'] = svg(225, 200, b)
    b = rect(26, 92, 23, 96, D, rx=4) + rect(30, 100, 6, 80, L) + rect(20, 86, 35, 12, D, rx=3)
    b += f'  <path d="M 37.5 10 C 58 34 60 56 50 80 L 25 80 C 15 56 17 34 37.5 10 Z" {D}/>\n'
    b += f'  <path d="M 37.5 30 C 48 46 49 60 44 74 L 31 74 C 26 60 27 46 37.5 30 Z" {L}/>\n'
    f['thermal-lance'] = svg(75, 200, b)

    # Ballistic: barrels, read side-on.
    def bundle(y):
        s = breech(14, y, 66)
        for k in (-1, 0, 1):
            s += barrel(56, 212, y + k * 17, 11)
        for bx in (100, 160):
            s += rect(bx, y - 30, 12, 60, D, rx=3) + rect(bx + 3, y - 26, 4, 52, L)
        return s
    f['chaingun-dual'] = svg(225, 200, bundle(56) + bundle(144))
    b = ell(75, 100, 58, 58, D) + stroke('M 31 78 A 48 48 0 0 1 97 52', '#ffffff', .45, 7)
    for i in range(6):
        a = math.pi * 2 * i / 6
        x, y = round(75 + 31 * math.cos(a), 1), round(100 + 31 * math.sin(a), 1)
        b += ell(x, y, 12, 12, D) + ell(x, y, 5, 5, L)
    b += ell(75, 100, 9, 9, L)
    f['chaingun-nose'] = svg(150, 200, b)
    b = ''
    for y in (56, 144):
        b += breech(14, y, 56) + barrel(56, 212, y, 22)
        for k in range(5):
            b += rect(70 + k * 27, y - 26, 11, 52, D, rx=3) + rect(73 + k * 27, y - 22, 3, 44, L)
    f['linear-cannon-dual'] = svg(225, 200, b)
    b = rect(14, 52, 58, 96, D, rx=8) + rect(22, 62, 12, 76, L, rx=3)
    b += barrel(70, 180, 100, 46)
    b += rect(176, 62, 36, 76, D, rx=5)
    for k in range(3):
        b += rect(182 + k * 10, 70, 5, 60, L)
    f['heavy-cannon'] = svg(225, 200, b)
    b = ''
    for y in (46, 112):
        b += breech(14, y, 50) + barrel(56, 196, y, 24) + rect(192, y - 16, 20, 32, D, rx=3)
    for k in range(7):
        b += rect(22 + k * 27, 146, 22, 42, D, rx=6) + rect(26 + k * 27, 150, 7, 18, L, rx=2)
    f['bastion-autocannon-dual'] = svg(225, 200, b)

    def autogun(y, x1):
        s = breech(14, y, 50) + barrel(56, x1, y, 22)
        return s + rect(32, y + 22, 30, 40, D, rx=4) + rect(37, y + 27, 8, 30, L)
    f['muster-autogun-dual'] = svg(225, 200, autogun(36, 212) + autogun(118, 212))
    f['muster-autogun-nose'] = svg(150, 200, autogun(78, 138))
    b = (ell(75, 96, 58, 58, D) + ell(75, 96, 40, 40, 'fill="#000000" fill-opacity="0.5"')
         + ell(75, 96, 24, 24, L))
    b += stroke('M 30 70 A 52 52 0 0 1 100 44', '#ffffff', .5, 7) + rect(46, 160, 58, 26, D, rx=5)
    f['demolition-cannon'] = svg(150, 200, b)
    b = (poly([(75, 12), (112, 70), (102, 150), (48, 150), (38, 70)], D)
         + poly([(75, 24), (92, 70), (75, 96), (58, 70)], L))
    b += rect(32, 148, 86, 40, D, rx=6)
    for x in (48, 75, 102):
        b += ell(x, 168, 7, 7, L)
    f['quarry-breaker'] = svg(150, 200, b)
    return f


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    icons = build()
    for name, text in icons.items():
        with open(os.path.join(OUT, name + '.svg'), 'w', newline='\n') as handle:
            handle.write(text)
    print('wrote', len(icons), 'icons to', OUT)
