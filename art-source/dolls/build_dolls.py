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

Every outline is one damageable location; thinner lines inside it are detail (seams,
gun tubes). A leg is one outline, not a stack of panels.

The mechs follow the owner's references (2026-10-20): the Hound is a Locust (a
pod in three thirds on straight, heavy legs, the chaingun under its nose, one rocket pod
on top), the Warden a Catapult (a flat pod whose side thirds carry the missile
boxes, a cannon under the nose, heavy legs), the Bulwark a Fafnir (one wide shell
with missile pods on its top corners, guns held at the chest, thick short legs).
A pod's side thirds are its shoulder sections: a shoulder mount lives in its side
of the hull, as MWO's side torsos carry their weapons.

The Hermes plan doubles as the Store's Shuttles section icon
(``mod/graphics/ui/store/section-boats.svg``), drawn nose-right in the section's
blue the way the Mechs / Vehicles icon draws the Bulwark and a tank.

Run from the repository root:  python art-source/dolls/build_dolls.py
"""

from math import cos, radians, sin
from pathlib import Path

OUT = Path("mod/graphics/ui/dolls")
STORE_OUT = Path("mod/graphics/ui/store")
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


def flip(points):
    """An outline mirrored across the view's centre line, kept in the same winding."""
    return [(-x, y) for x, y in reversed(points)]


def flip_lines(lines):
    return [((-x0, y0), (-x1, y1)) for (x0, y0), (x1, y1) in lines]


class Doll:
    def __init__(self, name, width=W):
        self.name = name
        self.width = width
        self.parts = {}   # location -> list of panels, in drawing order
        self.details = {}  # location -> thin lines drawn inside its panels

    def panel(self, location, points, cx, convex=True):
        """One outline. A mech part may be concave (a leg, a side torso with its pod), a hull panel may not."""
        placed = [(cx + x, y) for x, y in points]
        if convex:
            check_convex(placed, f"{self.name}/{location}")
        self.parts.setdefault(location, []).append(inset(placed, GAP / 2))

    def detail(self, location, segments, cx):
        """Thin lines inside a location's outline: seams and gun tubes, not a damageable boundary."""
        self.details.setdefault(location, []).extend(
            ((cx + x0, y0), (cx + x1, y1)) for (x0, y0), (x1, y1) in segments)

    def shape(self, location, points, cx, details=()):
        self.panel(location, points, cx, convex=False)
        self.detail(location, details, cx)

    def mirrored(self, left, right, points, cx, details=()):
        """A concave outline authored on the viewer's left, with its details, and its mirror on the right."""
        self.shape(left, points, cx, details)
        self.shape(right, flip(points), cx, flip_lines(details))

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
            lines = "".join(f'<line x1="{p[0]:.1f}" y1="{p[1]:.1f}" x2="{q[0]:.1f}" y2="{q[1]:.1f}"/>'
                            for p, q in self.details.get(location, ()))
            thin = (f'<g fill="none" stroke-opacity=".6" stroke-width=".7" stroke-linecap="butt">{lines}</g>'
                    if lines else "")
            body.append(f'<g id="part-{location}" fill="currentColor" fill-opacity="{FILL_OPACITY}" '
                        f'stroke="currentColor" stroke-width="{STROKE}" stroke-linejoin="miter">{shapes}{thin}</g>')
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.width} {H}">'
               f'<title>{self.name} damage doll</title>' + "".join(body) + "</svg>\n")
        (OUT / f"{self.name}.svg").write_text(svg, encoding="utf-8", newline="\n")
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

def tank(name, turret_w=24, turret_top=50, heavy=False, dozer=False):
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
        out = 50
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


def apc(name, launcher=False):
    d = Doll(name)
    for cx, rear in views():
        vl, vr = sides(rear)
        if launcher:
            d.panel("TURRET", [(-24, 36), (24, 36), (24, 58), (-24, 58)], cx)
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

def bird_leg(d, cx, inner, width, top=80):
    """One leg as one outline: a straight, heavy column from the hip, a broad knee, a planted foot."""
    o = inner - width
    points = [(o, top), (inner, top), (inner + 1, 108), (inner - 1, 134), (inner + 4, 137), (inner + 6, 146),
              (o - 6, 146), (o - 4, 137), (o + 1, 134), (o - 2, 108)]
    seams = [((o - 2, 108), (inner + 1, 108)), ((o + 1, 134), (inner - 1, 134))]
    d.mirrored("LEGS", "LEGS", points, cx, seams)


def arc(cx, cy, r, start, end, steps=4):
    """Points along a circular corner, in degrees with y down (180 is left, 270 is up)."""
    return [(cx + r * cos(radians(start + (end - start) * i / steps)),
             cy + r * sin(radians(start + (end - start) * i / steps))) for i in range(steps + 1)]


def tubes(x0, y0, x1, y1, cols, rows):
    """A launcher face: a grid of thin seams, one cell per tube."""
    out = []
    for c in range(1, cols):
        x = x0 + (x1 - x0) * c / cols
        out.append(((x, y0 + 2), (x, y1 - 2)))
    for r in range(1, rows):
        y = y0 + (y1 - y0) * r / rows
        out.append(((x0 + 2, y), (x1 - 2, y)))
    return out


def hound(name):
    """A Locust: a wide pod in three armoured thirds, the chaingun under its nose, one rocket pod on top.

    The pod's side thirds are the shoulder sections: a shoulder mount lives in its side of the hull, as
    MWO's side torsos carry their weapons. The Hound fits only its left shoulder, the rocket pod; its
    empty right third draws as outline.
    """
    d = Doll(name)
    third = [(-12, 46), (-12, 78), (-26, 76), (-36, 66), (-36, 54), (-28, 46)]
    podded = [(-12, 46), (-12, 78), (-26, 76), (-36, 66), (-36, 54), (-28, 46), (-28, 32), (-14, 32), (-14, 46)]
    pod = [((-28, 46), (-14, 46))] + tubes(-28, 32, -14, 46, 2, 2)
    for cx, rear in views():
        sl, sr = shoulders(rear)
        d.panel("TORSO_REAR" if rear else "TORSO", [(-12, 46), (12, 46), (12, 78), (-12, 78)], cx)
        # The rocket pod rides the mech's left third: the viewer's right from the front, left from behind.
        if rear:
            d.shape(sl, podded, cx, pod)
            d.shape(sr, flip(third), cx)
        else:
            d.shape(sl, third, cx)
            d.shape(sr, flip(podded), cx, flip_lines(pod))
        if rear:
            continue
        d.panel("HEAD", [(-10, 36), (10, 36), (12, 46), (-12, 46)], cx)
        d.shape("ARMS", [(-7, 78), (7, 78), (5, 90), (-5, 90)], cx, [((-3, 86), (3, 86)), ((0, 80), (0, 90))])
        bird_leg(d, cx, inner=-10, width=16)
    return d.write()


def warden(name):
    """A Catapult: a hull much wider than its legs, in three thirds of even depth, a round-topped
    missile pod standing on each wing and a cannon under the nose. Seen from in front or behind the
    arrowhead plan does not show: the hull is one level band."""
    d = Doll(name)
    # Each wing carries an upright, round-topped missile pod as one outline.
    side = ([(-12, 80), (-47, 80), (-52, 74), (-52, 56), (-46, 56)] + arc(-40, 34, 6, 180, 270)
            + arc(-30, 34, 6, 270, 360) + [(-24, 56), (-12, 56)])
    pod = [((-44.5, 56), (-25.5, 56))] + tubes(-44, 30, -26, 54, 2, 3)
    for cx, rear in views():
        sl, sr = shoulders(rear)
        d.panel("TORSO_REAR" if rear else "TORSO", [(-12, 56), (12, 56), (12, 80), (-12, 80)], cx)
        d.mirrored(sl, sr, side, cx, pod)
        if rear:
            continue
        d.panel("HEAD", [(-9, 46), (9, 46), (12, 56), (-12, 56)], cx)
        d.shape("ARMS", [(-5, 80), (5, 80), (4, 96), (-4, 96)], cx, [((-3, 92), (3, 92)), ((0, 82), (0, 96))])
        bird_leg(d, cx, inner=-12, width=18, top=84)
    return d.write()


def bulwark(name):
    """A Fafnir: a broad chest tapering to a narrow waist, a small sunken head, missile pods on its top corners, guns held at the chest."""
    d = Doll(name)
    arm = [(-38, 50), (-38, 84), (-44, 90), (-54, 90), (-56, 84), (-56, 56), (-50, 48)]
    barrels = [((-52, 62), (-52, 88)), ((-46, 62), (-46, 88)), ((-56, 60), (-38, 60))]
    leg = [(-26, 96), (-6, 96), (-6, 120), (-8, 136), (-2, 138), (-2, 146), (-34, 146), (-34, 138), (-28, 136),
           (-30, 120)]
    for cx, rear in views():
        sl, sr = shoulders(rear)
        d.shape("TORSO_REAR" if rear else "TORSO",
                # A broad chest tapering to a narrow armoured waist: a V, not a barrel.
                [(-36, 46), (36, 46), (38, 52), (38, 70), (22, 94), (-22, 94), (-38, 70), (-38, 52)], cx,
                [((-37, 70), (-14, 70)), ((37, 70), (14, 70)), ((-14, 48), (-14, 70)), ((14, 48), (14, 70)),
                 ((-14, 70), (-8, 92)), ((14, 70), (8, 92))])
        d.mirrored(sl, sr, [(-34, 30), (-12, 30), (-12, 46), (-34, 46)], cx, tubes(-34, 30, -12, 46, 3, 2))
        if rear:
            continue
        d.panel("HEAD", [(-8, 36), (8, 36), (10, 46), (-10, 46)], cx)
        # Each arm one outline and its own location: the shoulder joint, then the gun held level with
        # the chest. Seen from the front the machine's right arm is on the viewer's left.
        d.mirrored("RIGHT_ARM", "LEFT_ARM", arm, cx, barrels)
        d.mirrored("LEGS", "LEGS", leg, cx, [((-30, 120), (-6, 120)), ((-28, 136), (-8, 136))])
    return d.write()


# --------------------------------------------------------------------- marines
#
# A Marine is one front figure in a narrow drawing of its own (MARINE_W wide):
# there is no rear view, because a person's armor is one pool with no back plate.
# Its locations are the parts a round can strike (``body-part-cover.md``): head,
# torso, arms and legs. Only the limbs carry wounds of their own; the head and
# torso read the body's one armor and health pool.

MARINE_W, MARINE_X = 76, 38


def marine(name, suit=False):
    """A Marine seen from the front; a power suit is the same person in a broader, blockier shell."""
    d = Doll(name, MARINE_W)
    cx = MARINE_X
    if suit:
        d.panel("HEAD", octagon(-10, 8, 10, 32, 5), cx)
        d.shape("TORSO", [(-20, 34), (20, 34), (23, 44), (20, 72), (15, 94), (-15, 94), (-20, 72), (-23, 44)], cx,
                [((-20, 60), (20, 60)), ((0, 62), (0, 92))])
        arm = [(-25, 34), (-36, 38), (-39, 50), (-37, 98), (-26, 98), (-25, 50)]
        d.mirrored("ARMS", "ARMS", arm, cx, [((-38, 52), (-25, 52)), ((-38, 74), (-25, 74))])
        leg = [(-16, 96), (-1, 96), (-2, 128), (-1, 150), (1, 160), (-18, 160), (-17, 150), (-17, 128)]
        d.mirrored("LEGS", "LEGS", leg, cx, [((-17, 128), (-2, 128))])
    else:
        d.panel("HEAD", octagon(-9, 6, 9, 30, 5), cx)
        d.shape("TORSO", [(-17, 34), (17, 34), (20, 42), (17, 68), (13, 90), (-13, 90), (-17, 68), (-20, 42)], cx,
                [((-17, 62), (17, 62))])
        arm = [(-22, 35), (-31, 40), (-34, 66), (-33, 96), (-24, 96), (-23, 66), (-22, 46)]
        d.mirrored("ARMS", "ARMS", arm, cx, [((-34, 66), (-23, 66))])
        leg = [(-14, 93), (-1, 93), (-2, 126), (-2, 152), (1, 162), (-16, 162), (-15, 152), (-15, 126)]
        d.mirrored("LEGS", "LEGS", leg, cx, [((-15, 126), (-2, 126))])
    return d.write()



# -------------------------------------------------------------------- aircraft
#
# An aircraft is one plan view seen from above, nose up, centred in the frame. It
# has no located damage in the briefing, so every panel is the HULL; the panels
# still tile with the doll's gap so a craft reads in the same hand as the mechs.
# Each follows its battle sprite's silhouette, not its detail.

AIR_W = 170          # a plan needs no second view, so its frame is square-ish and the craft reads larger
AIR_X = AIR_W // 2


def aircraft(name, panels, pairs=(), details=()):
    """``panels`` sit on the centre line; each of ``pairs`` is authored on the left and mirrored."""
    d = Doll(name, width=AIR_W)
    for points in panels:
        d.panel("HULL", points, AIR_X)
    for points in pairs:
        d.pair("HULL", "HULL", points, AIR_X)
    d.detail("HULL", details, AIR_X)
    return d.write()


def talon():
    """Twin rounded lobes either side of a thin spine, a long needle nose, two exhausts."""
    return aircraft("air-talon",
                    [[(-3, 12), (3, 12), (6, 40), (-6, 40)],
                     [(-8, 40), (8, 40), (9, 132), (-9, 132)]],
                    [[(-54, 84), (-40, 56), (-12, 46), (-12, 128), (-50, 128)],
                     octagon(-44, 130, -16, 150, 5)])


def wasp():
    """A drone on an X: a slim body, two swept arms forward, two back."""
    return aircraft("air-wasp",
                    [[(-3, 14), (3, 14), (7, 40), (-7, 40)],
                     [(-9, 40), (9, 40), (11, 126), (-11, 126)],
                     [(-8, 128), (8, 128), (6, 146), (-6, 146)]],
                    [[(-70, 28), (-62, 22), (-12, 70), (-12, 84)],
                     [(-12, 94), (-12, 108), (-62, 152), (-70, 146)]])


def broadsword():
    """A broad armoured wedge: a wide hull, flat side wings, gun barrels forward, twin engines."""
    return aircraft("air-broadsword",
                    [[(-16, 18), (16, 18), (26, 58), (24, 132), (-24, 132), (-26, 58)]],
                    [[(-66, 92), (-56, 64), (-30, 52), (-28, 132), (-62, 138)],
                     [(-46, 28), (-38, 28), (-38, 60), (-46, 62)],
                     octagon(-30, 134, -8, 156, 5)])


def thunder():
    """A long spear: a needle nose ahead of a tapered hull, swept rear wings, guns at the shoulders."""
    return aircraft("air-thunder",
                    [[(-4, 4), (4, 4), (9, 62), (-9, 62)],
                     [(-14, 62), (14, 62), (20, 140), (-20, 140)]],
                    [[(-60, 128), (-22, 84), (-22, 140), (-58, 146)],
                     [(-34, 40), (-27, 40), (-27, 100), (-34, 104)],
                     octagon(-24, 142, -6, 164, 4)])


def longbow():
    """A heavy X-frame: a short body, four arms, big engine pods on the rear pair, missiles forward."""
    return aircraft("air-longbow",
                    [[(-8, 14), (8, 14), (16, 32), (-16, 32)],
                     [(-18, 34), (18, 34), (22, 104), (-22, 104)]],
                    [[(-64, 34), (-56, 24), (-20, 60), (-22, 76)],
                     [(-22, 86), (-20, 102), (-48, 128), (-58, 118)],
                     octagon(-80, 118, -50, 150, 6),
                     [(-74, 6), (-66, 6), (-66, 34), (-74, 30)]])


def dagger():
    """A long fuselage between two outrigger tanks, short wings joining them, tail fins."""
    return aircraft("air-dagger",
                    [[(-4, 6), (4, 6), (9, 26), (-9, 26)],
                     [(-11, 26), (11, 26), (12, 140), (-12, 140)]],
                    [[(-48, 98), (-14, 74), (-14, 116), (-48, 118)],
                     [(-64, 34), (-54, 34), (-52, 146), (-64, 146)],
                     [(-32, 146), (-14, 132), (-14, 156), (-30, 160)]])


def hermes_doll():
    """The company's first shuttle: a round cockpit, a narrow body between two round pods, two engines astern."""
    d = Doll("air-hermes", width=AIR_W)
    d.panel("HULL", [(-11, 15), (11, 15), (18, 22), (18, 33), (-18, 33), (-18, 22)], AIR_X)
    d.panel("HULL", [(-20, 35), (20, 35), (22, 65), (-22, 65)], AIR_X)
    d.panel("HULL", [(-22, 67), (22, 67), (22, 94), (-22, 94)], AIR_X)
    # The stern: a short keel between two engines, each a block with its nozzle behind it.
    d.panel("HULL", [(-18, 96), (18, 96), (18, 132), (11, 140), (-11, 140), (-18, 132)], AIR_X)
    d.pair("HULL", "HULL", octagon(-45, 50, -25, 84, 6), AIR_X)
    d.pair("HULL", "HULL", [(-38, 98), (-21, 98), (-21, 136), (-40, 136), (-42, 104)], AIR_X)
    d.pair("HULL", "HULL", [(-37, 139), (-23, 139), (-19, 156), (-41, 156)], AIR_X)
    d.detail("HULL", [((-39, 112), (-23, 112)), ((-39, 124), (-23, 124)),
                      ((23, 112), (39, 112)), ((23, 124), (39, 124))], AIR_X)
    return d


def hermes():
    d = hermes_doll()
    name = d.write()
    store_icon(d, "section-boats", "#72bde8")
    return name


def store_icon(doll, name, colour, inset_more=1.7, stroke=4):
    """A Store section icon from a plan doll: its panels turned nose-right, in the section's colour."""
    panels = [inset(points, inset_more) for points in doll.parts["HULL"]]
    turned = [[(-y, x) for x, y in points] for points in panels]
    xs = [x for points in turned for x, _ in points]
    ys = [y for points in turned for _, y in points]
    pad = stroke / 2 + 1
    box = (min(xs) - pad, min(ys) - pad, max(xs) - min(xs) + 2 * pad, max(ys) - min(ys) + 2 * pad)
    shapes = "".join(f'<polygon points="{svg_points(p)}"/>' for p in turned)
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{box[0]:.1f} {box[1]:.1f} {box[2]:.1f} {box[3]:.1f}">'
           f'<g fill="{colour}" fill-opacity=".4" stroke="{colour}" stroke-width="{stroke}" stroke-linejoin="miter">'
           f'{shapes}</g></svg>\n')
    STORE_OUT.mkdir(parents=True, exist_ok=True)
    (STORE_OUT / f"{name}.svg").write_text(svg, encoding="utf-8", newline="\n")


def mudskipper():
    """A boxy gunship shuttle: a blunt cab, a deep body with turret pods, two engines astern."""
    return aircraft("air-mudskipper",
                    [[(-26, 18), (26, 18), (34, 42), (-34, 42)],
                     [(-34, 44), (34, 44), (34, 118), (-34, 118)]],
                    [[(-50, 62), (-36, 54), (-36, 104), (-50, 98)],
                     octagon(-34, 120, -6, 152, 6)])


def main():
    written = [
        tank("tank"),
        tank("tank-dozer", turret_w=16, turret_top=58, dozer=True),
        apc("apc"),
        apc("lrm-carrier", launcher=True),
        pickup("technical", gun="mg"),
        pickup("technical-srm", gun="srm"),
        pickup("truck", cargo=True),
        pickup("scout-car"),
        hover("hover-scout"),
        hover("drone-skimmer", gun=False),
        hauler("hauler"),
        bulwark("mech-bulwark"),
        hound("mech-hound"),
        warden("mech-warden"),
        marine("marine"),
        marine("marine-suit", suit=True),
        talon(),
        wasp(),
        broadsword(),
        thunder(),
        longbow(),
        dagger(),
        mudskipper(),
        hermes(),
    ]
    print("wrote", ", ".join(written))


if __name__ == "__main__":
    main()
