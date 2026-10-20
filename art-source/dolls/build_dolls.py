"""Writes the damage-doll line art under mod/graphics/ui/dolls/.

A doll is one SVG holding two elevations side by side, the way MechWarrior draws a
paper doll: the machine seen from the front (left half, centred on x 60) and from
behind (right half, centred on x 180). Each location the damage model names is a
group ``<g id="part-NAME">`` whose shapes fill with ``currentColor``; the renderer
paints that colour per part from the location's state. Outline and detail strokes
carry fixed colours. See ``damage-doll.md`` and ``d5-located-armor.md``.

There is no side view. A side's state reads off the parts at that edge of both
views: a hull's cheeks and running gear, a mech's shoulder and arm. Seen from the
front the machine's right is on the viewer's left; from behind its left is. The
renderer writes the ``R FRONT L`` / ``L REAR R`` captions under the views.

A mech's back view drops the legs: they are thin, the same from either side, and
already drawn in front. Shapes are stylised from the shipped sprites' proportions,
blocky enough to read at the size of a HUD corner.

Run from the repository root:  python art-source/dolls/build_dolls.py
"""

from pathlib import Path

OUT = Path("mod/graphics/ui/dolls")
W, H = 240, 170
LINE = "#dbe5df"
FILL = ".62"
SOLID = ".9"   # parts drawn over another part (turrets, pods), so the one beneath does not show through
GROUND = 148   # where the machines stand; the captions sit beneath, drawn by the renderer
FRONT_X, BACK_X = 60, 180


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


def dome(cx, base, rx, ry):
    return (f'<path d="M {cx - rx:g} {base:g} Q {cx - rx:g} {base - ry:g} {cx:g} {base - ry:g} '
            f'Q {cx + rx:g} {base - ry:g} {cx + rx:g} {base:g} Z"/>')


def flip(points, cx):
    """The same outline mirrored about the vertical line through cx."""
    return [(2 * cx - x, y) for x, y in points]


class Doll:
    """Accumulates parts and detail, in drawing order, then writes one SVG."""

    def __init__(self, name):
        self.name = name
        self.body = []

    def part(self, location, *shapes, opacity=FILL):
        inner = "".join(shapes)
        self.body.append(
            f'<g id="part-{location}" fill="currentColor" fill-opacity="{opacity}" '
            f'stroke="{LINE}" stroke-width="1.2" stroke-linejoin="round">{inner}</g>')

    def detail(self, *shapes, opacity=".45", width=".8"):
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


def sides(rear):
    """The locations on the viewer's left and right: from the front the machine's right is on the left."""
    return ("LEFT", "RIGHT") if rear else ("RIGHT", "LEFT")


VIEWS = ((FRONT_X, False), (BACK_X, True))


# -------------------------------------------------------------------- vehicles

def hull_ends(d, half, drive, deck, cheek, face_style="glacis", hull_band=10):
    """Both end views of a hull: running gear, the two cheeks, the end plate, the hull band.

    ``half`` is the body's half width, ``deck`` the y of its top deck, ``cheek`` how
    wide each side cheek shows. ``drive`` is 'track', 'wheel' or 'skirt'.
    """
    for cx, rear in VIEWS:
        vl, vr = sides(rear)
        end = "REAR" if rear else "FRONT"
        x0, x1 = cx - half, cx + half
        if drive == "track":
            gear_top = GROUND - 36
            d.part("DRIVE", rect(x0, gear_top, 18, 36, rx=5), rect(x1 - 18, gear_top, 18, 36, rx=5))
            d.detail(*[line(x0 + 3, gear_top + 6 + i * 6, x0 + 15, gear_top + 6 + i * 6) for i in range(5)],
                     *[line(x1 - 15, gear_top + 6 + i * 6, x1 - 3, gear_top + 6 + i * 6) for i in range(5)],
                     opacity=".35")
            inner0, inner1 = x0 + 18, x1 - 18
            body_bottom = GROUND - 4
        elif drive == "wheel":
            gear_top = GROUND - 30
            d.part("DRIVE", rect(x0 - 4, gear_top, 15, 30, rx=5), rect(x1 - 11, gear_top, 15, 30, rx=5))
            d.detail(line(x0 + 3.5, gear_top + 6, x0 + 3.5, GROUND - 6),
                     line(x1 - 3.5, gear_top + 6, x1 - 3.5, GROUND - 6))
            inner0, inner1 = x0 + 11, x1 - 11
            body_bottom = GROUND - 10
        else:  # skirt
            gear_top = GROUND - 18
            d.part("DRIVE", rect(x0 - 6, gear_top, 2 * half + 12, 18, rx=9))
            d.detail(line(x0 + 4, gear_top + 9, x1 - 4, gear_top + 9), opacity=".35")
            inner0, inner1 = x0 + cheek, x1 - cheek
            body_bottom = gear_top
        # Cheeks: the hull's sides as they show past the end plate, above (or beside) the running gear.
        cheek_shape = [(x0 - 2, deck + 6), (x0 + cheek, deck), (x0 + cheek, gear_top), (x0 - 2, gear_top)]
        d.part(vl, poly(cheek_shape))
        d.part(vr, poly(flip(cheek_shape, cx)))
        # The hull band along the top of the end: the structure behind the plates.
        band = deck + hull_band
        d.part("HULL", rect(x0 + cheek, deck, 2 * (half - cheek), hull_band))
        a, b = x0 + cheek, x1 - cheek
        if face_style == "glacis":
            face = [(a, band), (b, band), (min(b, inner1), body_bottom), (max(a, inner0), body_bottom)]
        elif face_style == "nose":   # a hover nose: a chevron to the centre
            face = [(a, band), (b, band), (b - 4, body_bottom), (cx, body_bottom - 4), (a + 4, body_bottom)]
        else:
            face = [(a, band), (b, band), (b, body_bottom), (a, body_bottom)]
        d.part(end, poly(face))
        if rear:
            d.detail(*[line(cx - 14, band + 7 + i * 5, cx + 14, band + 7 + i * 5) for i in range(3)])
        else:
            d.detail(rect(cx - 6, band + 6, 12, 5, rx=1))


def tank(name, muzzle=4.0, screened=False, dozer=False):
    d = Doll(name)
    half, deck = 46, 88
    hull_ends(d, half, "track", deck, cheek=20)
    for cx, rear in VIEWS:
        if dozer:
            d.part("TURRET", poly([(cx - 14, deck), (cx - 11, deck - 14), (cx + 11, deck - 14), (cx + 14, deck)]),
                   opacity=SOLID)
            if not rear:
                d.detail(circle(cx, deck - 8, 2.4), width="1.2", opacity=".8")
            continue
        shapes = [poly([(cx - 32, deck), (cx - 25, deck - 24), (cx + 25, deck - 26), (cx + 32, deck)])]
        if screened:
            shapes.append(ellipse(cx + 12, deck - 28, 9, 4))
        if not rear:
            shapes.append(rect(cx - 10, deck - 20, 20, 16, rx=3))   # mantlet
        d.part("TURRET", *shapes, opacity=SOLID)
        if rear:
            d.detail(rect(cx - 16, deck - 18, 32, 10, rx=2))
        else:
            d.detail(circle(cx, deck - 12, muzzle), circle(cx, deck - 12, muzzle * .45), width="1.1", opacity=".85")
    if dozer:
        # The blade stands in front of everything in the front view.
        d.part("FRONT", rect(FRONT_X - 52, GROUND - 32, 104, 26, rx=4))
        d.detail(*[line(FRONT_X - 46, GROUND - 25 + i * 7, FRONT_X + 46, GROUND - 25 + i * 7) for i in range(3)])
    return d.write()


def apc(name, pods=False):
    d = Doll(name)
    half, deck = 38, 84
    hull_ends(d, half, "wheel", deck, cheek=14)
    for cx, rear in VIEWS:
        if pods:
            d.part("TURRET", rect(cx - 36, deck - 26, 24, 22, rx=2), rect(cx + 12, deck - 26, 24, 22, rx=2),
                   opacity=SOLID)
            if not rear:
                d.detail(*[circle(cx + sx + c * 7, deck - 20 + r * 7, 2.2)
                           for sx in (-31, 17) for c in range(3) for r in range(2)])
        else:
            d.part("TURRET", poly([(cx - 20, deck), (cx - 16, deck - 16), (cx + 16, deck - 16), (cx + 20, deck)]),
                   opacity=SOLID)
            if not rear:
                d.detail(circle(cx - 7, deck - 9, 2.6), circle(cx + 7, deck - 9, 2.6), width="1.1", opacity=".85")
    return d.write()


def pickup(name, gun=None, mast=False, cargo=False):
    d = Doll(name)
    half, deck = 36, 102
    hull_ends(d, half, "wheel", deck, cheek=12, face_style="flat", hull_band=8)
    for cx, rear in VIEWS:
        # The cab rises over the bed and reads as the hull; from behind the bed's load shows in front of it.
        d.part("HULL", poly([(cx - 26, deck), (cx - 22, deck - 26), (cx + 22, deck - 26), (cx + 26, deck)]))
        d.detail(poly([(cx - 20, deck - 4), (cx - 17, deck - 22), (cx + 17, deck - 22), (cx + 20, deck - 4)]),
                 opacity=".4")
        if not rear:
            d.detail(*[line(cx - 14, deck + 13 + i * 4, cx + 14, deck + 13 + i * 4) for i in range(4)],
                     circle(cx - 18, deck + 17, 3), circle(cx + 18, deck + 17, 3))
        if cargo and rear:
            d.detail(rect(cx - 24, deck - 16, 18, 16, rx=1), rect(cx - 4, deck - 20, 22, 20, rx=1),
                     opacity=".7")
        if gun == "mg":
            d.part("TURRET", rect(cx - 2, deck - 40, 4, 14), rect(cx - 12, deck - 46, 24, 8, rx=2), opacity=SOLID)
            if not rear:
                d.detail(circle(cx, deck - 42, 2.4), width="1.1", opacity=".85")
        elif gun == "srm":
            d.part("TURRET", rect(cx - 20, deck - 46, 40, 20, rx=2), opacity=SOLID)
            if not rear:
                d.detail(*[circle(cx - 12 + c * 8, deck - 40 + r * 8, 2.6) for c in range(4) for r in range(2)])
        if mast:
            d.detail(line(cx + 10, deck - 26, cx + 10, deck - 50), ellipse(cx + 10, deck - 52, 9, 3),
                     opacity=".75", width="1.1")
    return d.write()


def hover(name, gun=True):
    d = Doll(name)
    half, deck = 48, 100
    hull_ends(d, half, "skirt", deck, cheek=16, face_style="nose")
    # The lift fans show from behind.
    d.detail(circle(BACK_X - 16, deck + 18, 8), circle(BACK_X + 16, deck + 18, 8),
             line(BACK_X - 22, deck + 18, BACK_X - 10, deck + 18), line(BACK_X + 10, deck + 18, BACK_X + 22, deck + 18))
    for cx, rear in VIEWS:
        if gun:
            d.part("TURRET", dome(cx, deck, 18, 16), opacity=SOLID)
            if not rear:
                d.detail(circle(cx - 6, deck - 7, 2.4), circle(cx + 6, deck - 7, 2.4), width="1.1", opacity=".85")
        else:
            d.detail(rect(cx - 16, deck - 8, 32, 8, rx=2), opacity=".6")
    return d.write()


def hauler(name):
    d = Doll(name)
    half, top = 40, 54
    for cx, rear in VIEWS:
        vl, vr = sides(rear)
        x0, x1 = cx - half, cx + half
        body_bottom = GROUND - 24
        d.part("DRIVE", rect(x0 - 2, GROUND - 22, 16, 22, rx=4), rect(x1 - 14, GROUND - 22, 16, 22, rx=4))
        # The container stands taller and wider than the cab: its roof is the hull band, its edges the sides.
        d.part("HULL", rect(x0, top, 2 * half, 10))
        d.part(vl, rect(x0, top + 10, 10, body_bottom - top - 10))
        d.part(vr, rect(x1 - 10, top + 10, 10, body_bottom - top - 10))
        if rear:
            d.part("REAR", rect(x0 + 10, top + 10, 2 * half - 20, body_bottom - top - 10))
            d.detail(line(cx, top + 12, cx, body_bottom - 2), line(x0 + 16, top + 14, x0 + 16, body_bottom - 4),
                     line(x1 - 16, top + 14, x1 - 16, body_bottom - 4), rect(cx - 8, top + 34, 16, 4))
        else:
            d.detail(rect(x0 + 10, top + 10, 2 * half - 20, 22), opacity=".35")
            d.part("FRONT", rect(x0 + 10, top + 32, 2 * half - 20, body_bottom - top - 32))
            d.detail(rect(cx - 22, top + 38, 44, 18, rx=2),
                     *[line(cx - 14, top + 64 + i * 4, cx + 14, top + 64 + i * 4) for i in range(3)])
    return d.write()


# ----------------------------------------------------------------------- mechs

def mech(name, torso_w, torso_top, torso_bottom, shoulder, head, leg_w, hip_w, arm_w, taper=0, hunch=0):
    """A walker facing the camera, and from behind with its legs left off.

    ``torso_w`` is the torso's width at the shoulders, ``taper`` how much narrower it
    is at the waist; ``shoulder`` a pod's (width, height); ``head`` the cockpit's
    (width, height); ``leg_w`` and ``hip_w`` the legs' thickness and stance; ``arm_w``
    the forearms'; ``hunch`` sinks the head into the torso (a Hound's low cockpit).
    """
    d = Doll(name)
    sw, sh = shoulder
    tw = torso_w / 2
    waist = tw - taper
    pod_top = torso_top - sh * .35
    for cx, rear in VIEWS:
        vl, vr = sides(rear)
        torso = [(cx - tw, torso_top + 6), (cx - tw + 6, torso_top), (cx + tw - 6, torso_top), (cx + tw, torso_top + 6),
                 (cx + waist, torso_bottom), (cx - waist, torso_bottom)]
        if not rear:
            # Legs first; the torso stands over their tops.
            for side in (-1, 1):
                hx = cx + side * hip_w
                knee_y = torso_bottom + (GROUND - torso_bottom) * .48
                thigh = [(hx - leg_w / 2, torso_bottom - 4), (hx + leg_w / 2, torso_bottom - 4),
                         (hx + leg_w / 2 + side * 2, knee_y), (hx - leg_w / 2 + side * 2, knee_y)]
                shin = [(hx - leg_w / 2 + side * 2, knee_y + 3), (hx + leg_w / 2 + side * 2, knee_y + 3),
                        (hx + leg_w / 2 + side * 3, GROUND - 8), (hx - leg_w / 2 + side * 3, GROUND - 8)]
                foot = rect(hx + side * 3 - leg_w / 2 - 5, GROUND - 8, leg_w + 10, 8, rx=2)
                d.part("LEGS", poly(thigh), poly(shin), foot, opacity=SOLID)
                d.detail(circle(hx + side * 2, knee_y + 1.5, 2.6))
            d.part("TORSO", poly(torso))
            d.detail(rect(cx - 9, torso_top + 12, 18, 10, rx=2),
                     *[line(cx - 8, torso_bottom - 14 + i * 4, cx + 8, torso_bottom - 14 + i * 4) for i in range(3)])
        else:
            d.part("TORSO_REAR", poly(torso))
            d.detail(*[line(cx - tw + 10, torso_top + 12 + i * 6, cx + tw - 10, torso_top + 12 + i * 6)
                       for i in range(4)], rect(cx - 10, torso_bottom - 14, 20, 8, rx=2))
        # Shoulder pods either side of the torso, high.
        for side, location in ((-1, vl + "_SHOULDER"), (1, vr + "_SHOULDER")):
            x = cx - tw - sw + 4 if side < 0 else cx + tw - 4
            d.part(location, rect(x, pod_top, sw, sh, rx=3), opacity=SOLID)
            if not rear:
                cols = max(1, int((sw - 6) / 8))
                rows = max(1, int((sh - 8) / 8))
                d.detail(*[circle(x + sw / 2 - (cols - 1) * 4 + c * 8, pod_top + 8 + r * 8, 2.4)
                           for c in range(cols) for r in range(rows)])
        if not rear:
            # The arms mount: a forearm hanging under each shoulder, barrels at the camera.
            for side in (-1, 1):
                ax = cx + side * (tw + sw / 2 - 2)
                arm_top = pod_top + sh - 2
                d.part("ARMS", rect(ax - arm_w / 2, arm_top, arm_w, 36, rx=3), opacity=SOLID)
                d.detail(circle(ax, arm_top + 28, arm_w * .28), width="1.1", opacity=".85")
            hy = torso_top + hunch
            d.part("HEAD", poly([(cx - head[0] / 2, hy + 2), (cx - head[0] / 2 + 3, hy - head[1]),
                                 (cx + head[0] / 2 - 3, hy - head[1]), (cx + head[0] / 2, hy + 2)]), opacity=SOLID)
            d.detail(rect(cx - head[0] / 2 + 4, hy - head[1] + 4, head[0] - 8, 4, rx=1), opacity=".75")
    return d.write()


def main():
    written = [
        tank("tank"),
        tank("tank-heavy-gun", muzzle=5.6),
        tank("tank-screened", screened=True),
        tank("tank-dozer", dozer=True),
        apc("apc"),
        apc("lrm-carrier", pods=True),
        pickup("technical", gun="mg"),
        pickup("technical-srm", gun="srm"),
        pickup("truck", cargo=True),
        pickup("scout-car", mast=True),
        hover("hover-scout"),
        hover("drone-skimmer", gun=False),
        hauler("hauler"),
        mech("mech-bulwark", torso_w=48, torso_top=44, torso_bottom=88, shoulder=(24, 26), head=(20, 12),
             leg_w=15, hip_w=13, arm_w=14, taper=4),
        mech("mech-hound", torso_w=32, torso_top=52, torso_bottom=86, shoulder=(18, 18), head=(16, 10),
             leg_w=10, hip_w=10, arm_w=10, taper=4, hunch=4),
        mech("mech-sirocco", torso_w=44, torso_top=48, torso_bottom=86, shoulder=(20, 20), head=(16, 12),
             leg_w=12, hip_w=11, arm_w=12, taper=12),
    ]
    print("wrote", ", ".join(written))


if __name__ == "__main__":
    main()
