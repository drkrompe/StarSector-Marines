"""Writes the damage-doll line art under mod/graphics/ui/dolls/.

A doll is one SVG holding two elevations side by side, the way MechWarrior Online
draws its paper doll: the machine seen from the front (left half, centred on x 60)
and from behind (right half, centred on x 180). Each location the damage model
names is a group ``<g id="part-NAME">``; its panels fill and stroke with
``currentColor``, which the renderer replaces per part with the location's state.

The style is MWO's: flat, angular **panels that tile without overlapping**, each
pulled in from its neighbours by the same small gap, a bright outline over a dim
fill, and no ornament. Every panel is drawn as a convex polygon on a shared
skeleton and then inset by half the gap (``inset``), so two panels that share an
edge on the skeleton end up a constant distance apart however they are angled.
A faint fixed outline sits under each part so a location that is not fitted still
shows its shape when the renderer leaves its colour out.

There is no side view. A side's state reads off the panels at that edge of both
views: a hull's sponsons and running gear, a mech's side torso and arm. Seen from
the front the machine's right is on the viewer's left; from behind its left is.
The renderer writes the ``R FRONT L`` / ``L REAR R`` captions under the views.
A mech's back view is its torso alone: legs and arms are the same from behind.

The mechs follow the owner's references (2026-10-20): the Hound is a Locust (a
pod on long bird legs, gun pods slung beneath), the Sirocco a Catapult (missile
boxes standing over a narrow torso, bird legs), the Bulwark a Fafnir (a wide,
squat torso, huge arm cannons, thick short legs).

Run from the repository root:  python art-source/dolls/build_dolls.py
"""

from pathlib import Path

OUT = Path("mod/graphics/ui/dolls")
W, H = 240, 170
FRONT_X, BACK_X = 60, 180
GAP = 2.6            # between neighbouring panels, in viewBox units
FILL_OPACITY = ".34"
STROKE = "1.5"
GHOST = "#dbe5df"    # the faint outline that shows an unfitted location


def inset(points, distance):
    """A convex polygon pulled in by ``distance`` along every edge."""
    n = len(points)
    area = sum(points[i][0] * points[(i + 1) % n][1] - points[(i + 1) % n][0] * points[i][1] for i in range(n))
    sign = 1 if area > 0 else -1
    lines = []
    for i in range(n):
        (x0, y0), (x1, y1) = points[i], points[(i + 1) % n]
        dx, dy = x1 - x0, y1 - y0
        length = (dx * dx + dy * dy) ** .5
        nx, ny = -dy / length * sign, dx / length * sign
        lines.append(((x0 + nx * distance, y0 + ny * distance), (dx, dy)))
    out = []
    for i in range(n):
        (p, r), (q, s) = lines[i - 1], lines[i]
        denom = r[0] * s[1] - r[1] * s[0]
        if abs(denom) < 1e-9:
            out.append(q)
            continue
        t = ((q[0] - p[0]) * s[1] - (q[1] - p[1]) * s[0]) / denom
        out.append((p[0] + r[0] * t, p[1] + r[1] * t))
    return out


def check_convex(points, where):
    n = len(points)
    signs = set()
    for i in range(n):
        (ax, ay), (bx, by), (cx, cy) = points[i - 1], points[i], points[(i + 1) % n]
        cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
        if abs(cross) > 1e-6:
            signs.add(cross > 0)
    if len(signs) > 1:
        raise ValueError(f"panel in {where} is not convex: {points}")


def svg_points(points):
    return " ".join(f"{x:.1f},{y:.1f}" for x, y in points)


class Doll:
    def __init__(self, name):
        self.name = name
        self.parts = {}   # location -> list of panels, in drawing order

    def panel(self, location, points, cx):
        placed = [(cx + x, y) for x, y in points]
        check_convex(placed, f"{self.name}/{location}")
        self.parts.setdefault(location, []).append(inset(placed, GAP / 2))

    def pair(self, left, right, points, cx):
        """A panel authored on the viewer's left, and its mirror on the right."""
        self.panel(left, points, cx)
        self.panel(right, [(-x, y) for x, y in reversed(points)], cx)

    def write(self):
        OUT.mkdir(parents=True, exist_ok=True)
        body = []
        for location, panels in self.parts.items():
            shapes = "".join(f'<polygon points="{svg_points(p)}"/>' for p in panels)
            body.append(f'<g fill="none" stroke="{GHOST}" stroke-opacity=".22" stroke-width="1" '
                        f'stroke-linejoin="miter">{shapes}</g>')
            body.append(f'<g id="part-{location}" fill="currentColor" fill-opacity="{FILL_OPACITY}" '
                        f'stroke="currentColor" stroke-width="{STROKE}" stroke-linejoin="miter">{shapes}</g>')
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}">'
               f'<title>{self.name} damage doll</title>' + "".join(body) + "</svg>\n")
        (OUT / f"{self.name}.svg").write_text(svg, encoding="utf-8")
        return self.name


def sides(rear):
    """Locations on the viewer's (left, right): from the front the machine's right is on the left."""
    return ("LEFT", "RIGHT") if rear else ("RIGHT", "LEFT")


def shoulders(rear):
    return ("LEFT_SHOULDER", "RIGHT_SHOULDER") if rear else ("RIGHT_SHOULDER", "LEFT_SHOULDER")


def views():
    return ((FRONT_X, False), (BACK_X, True))


def octagon(x0, y0, x1, y1, c):
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c),
            (x0, y0 + c)]


# -------------------------------------------------------------------- vehicles
#
# Every hull shares one skeleton: a turret (when it has one) on top, an upper band
# of hull deck between two sponsons, and below it the end plate between the
# running gear. The sponsons are the LEFT and RIGHT plates, so a flank reads at the
# matching edge of both views; the end plate is FRONT in one view and REAR in the
# other.

def tank(name, turret_w=24, turret_top=50, heavy=False, screened=False, dozer=False):
    d = Doll(name)
    for cx, rear in views():
        vl, vr = sides(rear)
        top = turret_top - (6 if heavy else 0)
        tw = turret_w + (4 if heavy else 0)
        if rear:
            d.panel("TURRET", [(-tw + 3, top + 2), (tw - 3, top + 2), (tw, 76), (-tw, 76)], cx)
        else:
            d.panel("TURRET", [(-tw + 8, top), (tw - 8, top), (tw, top + 12), (tw, 76), (-tw, 76),
                               (-tw, top + 12)], cx)
        d.panel("HULL", [(-28, 76), (28, 76), (28, 92), (-28, 92)], cx)
        out = 56 if screened else 50
        d.pair(vl, vr, [(-out + 4, 80), (-28, 76), (-28, 92), (-out, 92)], cx)
        if dozer and not rear:
            d.pair("DRIVE", "DRIVE", octagon(-50, 92, -34, 120, 3), cx)
            d.panel("FRONT", [(-34, 92), (34, 92), (34, 120), (-34, 120)], cx)
            d.panel("FRONT", [(-56, 120), (56, 120), (52, 146), (-52, 146)], cx)
            continue
        d.pair("DRIVE", "DRIVE", octagon(-50, 92, -34, 146, 4), cx)
        if rear:
            d.panel("REAR", [(-34, 92), (34, 92), (34, 138), (-34, 138)], cx)
        else:
            d.panel("FRONT", [(-34, 92), (34, 92), (34, 132), (26, 142), (-26, 142), (-34, 132)], cx)
    return d.write()


def apc(name, pods=False):
    d = Doll(name)
    for cx, rear in views():
        vl, vr = sides(rear)
        if pods:
            d.pair("TURRET", "TURRET", [(-30, 36), (-2, 36), (-2, 58), (-30, 58)], cx)
        else:
            d.panel("TURRET", [(-10, 42), (10, 42), (14, 58), (-14, 58)], cx)
        d.panel("HULL", [(-34, 58), (34, 58), (40, 68), (-40, 68)], cx)
        d.pair(vl, vr, [(-46, 72), (-40, 68), (-26, 68), (-26, 110), (-48, 110)], cx)
        if rear:
            d.panel("REAR", [(-26, 68), (26, 68), (26, 132), (-26, 132)], cx)
        else:
            d.panel("FRONT", [(-26, 68), (26, 68), (26, 124), (18, 134), (-18, 134), (-26, 124)], cx)
        d.pair("DRIVE", "DRIVE", octagon(-48, 110, -28, 146, 6), cx)
    return d.write()


def pickup(name, gun=None, cargo=False):
    d = Doll(name)
    for cx, rear in views():
        vl, vr = sides(rear)
        if gun == "mg":
            d.panel("TURRET", [(-5, 46), (5, 46), (5, 56), (-5, 56)], cx)
            d.panel("TURRET", [(-14, 56), (14, 56), (14, 66), (-14, 66)], cx)
        elif gun == "srm":
            d.panel("TURRET", [(-18, 44), (18, 44), (18, 66), (-18, 66)], cx)
        if cargo and rear:
            d.panel("HULL", [(-34, 52), (34, 52), (34, 92), (-34, 92)], cx)
        else:
            d.panel("HULL", [(-18, 66), (18, 66), (26, 92), (-26, 92)], cx)
        d.pair(vl, vr, [(-42, 96), (-26, 92), (-26, 110), (-44, 110)], cx)
        if rear:
            d.panel("REAR", [(-26, 92), (26, 92), (26, 126), (-26, 126)], cx)
        else:
            d.panel("FRONT", [(-26, 92), (26, 92), (26, 122), (-26, 122)], cx)
        d.pair("DRIVE", "DRIVE", octagon(-44, 110, -28, 146, 5), cx)
    return d.write()


def hover(name, gun=True):
    d = Doll(name)
    for cx, rear in views():
        vl, vr = sides(rear)
        if gun:
            d.panel("TURRET", [(-9, 66), (9, 66), (12, 80), (-12, 80)], cx)
        d.panel("HULL", [(-18, 80), (18, 80), (26, 96), (-26, 96)], cx)
        d.pair(vl, vr, [(-46, 100), (-26, 92), (-26, 120), (-52, 120)], cx)
        if rear:
            d.panel("REAR", [(-26, 96), (26, 96), (26, 124), (-26, 124)], cx)
        else:
            d.panel("FRONT", [(-26, 96), (26, 96), (26, 118), (14, 126), (-14, 126), (-26, 118)], cx)
        d.panel("DRIVE", [(-52, 126), (52, 126), (48, 140), (-48, 140)], cx)
    return d.write()


def hauler(name):
    d = Doll(name)
    for cx, rear in views():
        vl, vr = sides(rear)
        d.panel("HULL", [(-40, 40), (40, 40), (40, 76), (-40, 76)], cx)
        d.pair(vl, vr, [(-44, 80), (-26, 76), (-26, 114), (-46, 114)], cx)
        if rear:
            d.panel("REAR", [(-26, 76), (26, 76), (26, 132), (-26, 132)], cx)
        else:
            d.panel("FRONT", [(-26, 76), (26, 76), (26, 120), (18, 130), (-18, 130), (-26, 120)], cx)
        d.pair("DRIVE", "DRIVE", octagon(-46, 114, -28, 146, 5), cx)
    return d.write()


# -------------------------------------------------------------------- mechs
#
# Front: head, centre torso, the two side torsos (the shoulder mounts), arms at
# the outside, legs beneath. Rear: the centre torso's back plate between the side
# torsos, nothing else.

def bird_legs(d, cx, hip, knee, ankle, spread, width):
    """Reverse-jointed legs seen from the front: thigh splayed out, shin back in, a toe."""
    d.pair("LEGS", "LEGS", [(-width - 2, hip), (-2, hip), (-spread + width, knee), (-spread - 2, knee - 4)], cx)
    d.pair("LEGS", "LEGS", [(-spread - 2, knee - 4), (-spread + width, knee), (-spread + width - 2, ankle),
                            (-spread - 4 + width // 2, ankle)], cx)
    d.pair("LEGS", "LEGS", [(-spread - 10, ankle + 2), (-spread + width + 6, ankle + 2),
                            (-spread + width + 8, 146), (-spread - 12, 146)], cx)


def hound(name):
    """A Locust: a wide pod on long bird legs, gun pods slung under its flanks."""
    d = Doll(name)
    for cx, rear in views():
        sl, sr = shoulders(rear)
        d.panel("TORSO_REAR" if rear else "TORSO",
                [(-18, 46), (18, 46), (24, 54), (24, 70), (16, 80), (-16, 80), (-24, 70), (-24, 54)], cx)
        d.pair(sl, sr, [(-36, 56), (-24, 52), (-24, 72), (-34, 72), (-38, 64)], cx)
        if rear:
            continue
        d.panel("HEAD", [(-10, 36), (10, 36), (14, 46), (-14, 46)], cx)
        d.pair("ARMS", "ARMS", [(-34, 72), (-24, 72), (-24, 90), (-29, 94), (-34, 90)], cx)
        bird_legs(d, cx, hip=80, knee=110, ankle=136, spread=24, width=10)
    return d.write()


def sirocco(name):
    """A Catapult: two missile boxes standing over a narrow torso with a nose cockpit, bird legs."""
    d = Doll(name)
    for cx, rear in views():
        sl, sr = shoulders(rear)
        d.panel("TORSO_REAR" if rear else "TORSO",
                [(-16, 50), (16, 50), (20, 58), (20, 84), (10, 94), (-10, 94), (-20, 84), (-20, 58)], cx)
        d.pair(sl, sr, [(-50, 22), (-24, 28), (-20, 50), (-20, 66), (-48, 66)], cx)
        if rear:
            continue
        d.panel("HEAD", [(-10, 40), (10, 40), (16, 50), (-16, 50)], cx)
        d.pair("ARMS", "ARMS", [(-42, 66), (-24, 66), (-24, 84), (-34, 90), (-42, 84)], cx)
        bird_legs(d, cx, hip=94, knee=116, ankle=136, spread=28, width=12)
    return d.write()


def bulwark(name):
    """A Fafnir: a wide squat torso with a small sunken head, huge arm cannons, thick short legs."""
    d = Doll(name)
    for cx, rear in views():
        sl, sr = shoulders(rear)
        d.panel("TORSO_REAR" if rear else "TORSO",
                [(-14, 40), (14, 40), (18, 46), (18, 92), (-18, 92), (-18, 46)], cx)
        d.pair(sl, sr, [(-38, 46), (-18, 40), (-18, 86), (-38, 86)], cx)
        if rear:
            continue
        d.panel("HEAD", [(-8, 32), (8, 32), (10, 40), (-10, 40)], cx)
        # The cannons: a shoulder housing, then the long barrel block hanging past the hips.
        d.pair("ARMS", "ARMS", [(-54, 40), (-38, 44), (-38, 60), (-56, 60)], cx)
        d.pair("ARMS", "ARMS", [(-56, 60), (-38, 60), (-40, 114), (-50, 118), (-56, 112)], cx)
        d.pair("LEGS", "LEGS", [(-18, 92), (-2, 92), (-4, 116), (-26, 116), (-26, 100)], cx)
        d.pair("LEGS", "LEGS", [(-26, 116), (-4, 116), (-6, 136), (-24, 136)], cx)
        d.pair("LEGS", "LEGS", [(-30, 136), (-2, 136), (-2, 146), (-32, 146)], cx)
    return d.write()


def main():
    written = [
        tank("tank"),
        tank("tank-heavy-gun", heavy=True),
        tank("tank-screened", screened=True),
        tank("tank-dozer", turret_w=16, turret_top=58, dozer=True),
        apc("apc"),
        apc("lrm-carrier", pods=True),
        pickup("technical", gun="mg"),
        pickup("technical-srm", gun="srm"),
        pickup("truck", cargo=True),
        pickup("scout-car"),
        hover("hover-scout"),
        hover("drone-skimmer", gun=False),
        hauler("hauler"),
        bulwark("mech-bulwark"),
        hound("mech-hound"),
        sirocco("mech-sirocco"),
    ]
    print("wrote", ", ".join(written))


if __name__ == "__main__":
    main()
