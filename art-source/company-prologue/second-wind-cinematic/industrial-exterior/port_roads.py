"""The public port's road network as `port-roads.md` plans it. On by default (owner, 2026-10-21);
PORT_ROADS=0 is the roads-off control.

The roads as built:

  main haul road   the port road, the cross streets and the heavy berth road are already there and
                   stay as they are, Kilo Nine's gate and the road outside it included.
  service roads    the west connector (x -545, port road to the west hangars) and the back road
                   behind the berths, from the connector south of the depot, up between the depot
                   and berth -400's stores and along behind the berths to the truck park; on the east
                   the arrivals link (x 285, past the bus bays) and the terminal road (x 278, the
                   terminal apron up to the lorry park), one road with a jog where they meet.
  spurs            fuel (x -422, the back road to the tanker rack), the west truck lot and the spares
                   yard; the others open off a road directly.
  pad rings        one-way service rings 8 m outside each lighter pad's marker ring (centre line 24 m
                   out, 6 m wide, so the inner edge is the 21 m blast keep-out), clockwise, each with
                   two links to the port road 8 m either side of the pad: in at the west link, out at
                   the east one.
  removed          the fuel farm stub behind berth -310 and the terminal road through the heavy
                   berth's blast wall.

Where the plan's lines met what stands, the road moved, not the plan (`port-roads.md`, "As built"):
the back road turns north at x -438 (the plan's diagonal cut the depot's corner), runs at y 608 (the
pipe bridge's portals stand at 614) and goes into the truck park at its west end, x -260 (between
berth -130's steel stock and the pipe bridge there is no 9 m to drive in); the depot's east fence
comes in 6 m to make room for it, and the truck park's gate moves to where the road arrives. The
west lot spur comes in on the lot's drive aisle (y 457) through a new gate in its east fence; the
spares spur meets the yard's existing gate (y 584).

Drawing them. The port's ground is one overhead painting (`ortho_paint`), which shows the roads as
they were. A road drawn as plain geometry would be painted over with whatever was beneath it, so
each new road is a ribbon textured from the painting's own cargo lane (its asphalt and yellow edge
lines), and each removed road, or clutter that had to move, is covered by a patch cut from plain
painted apron (or lot asphalt, inside the truck park). Both sit just above the ground and are left
out of the overhead paint, which skips image-textured materials.

    ON              the switch
    ROADS, rings()  the network, for `port_detail` (keep clear), `port_life` (traffic) and the checks
    build(c4, ghosts)  ribbons and patches, from `c4x_scene.build()` after `port_detail`
"""
import math
import os
from pathlib import Path

ON = os.environ.get('PORT_ROADS', '1') not in ('', '0')

HERE = Path(__file__).resolve().parent
PORT_Y = (366.0, 378.0)                 # the port road's edges
LANE_E, LANE_W = 369.0, 375.0           # its eastbound and westbound lanes (keep right)
RING_R, RING_W = 24.0, 6.0              # pad service ring: centre line radius, width
LINK = 8.0                              # links either side of a pad's centre
RIBBON_Z, PATCH_Z = 0.25, 0.2           # above the ground planes and their painted lines

# (name, width, centre line). Widths are the surface; lanes keep 2.25-3 m either side of the line.
ROADS = [
    ('west connector', 12.0, [(-545.0, 377.0), (-545.0, 650.0)]),
    ('back road', 9.0, [(-545.0, 584.0), (-438.0, 584.0), (-438.0, 608.0), (-260.0, 608.0), (-260.0, 629.0)]),
    ('fuel spur', 8.0, [(-422.0, 608.0), (-422.0, 734.0), (-400.0, 734.0)]),
    ('west lot spur', 8.0, [(-545.0, 457.0), (-568.0, 457.0)]),
    ('spares spur', 8.0, [(-545.0, 584.0), (-598.0, 584.0)]),
    ('arrivals link and terminal road', 8.0, [(285.0, 377.0), (285.0, 441.0), (278.0, 452.0), (278.0, 702.0)]),
]
REMOVED = [('fuel farm road', (-300.0, -285.0, 560.0, 645.0)),
           ('terminal road', (72.0, 180.0, 680.0, 692.0))]          # east of the blast wall; the deck covers the rest
TRUCK_PARK_GATE = (2.0, 18.0)          # metres along its south fence from x -270: the back road's end
WEST_LOT_GATE = (45.0, 61.0)           # metres along the west truck lot's east fence from y 404
DEPOT_EAST = -446.0                     # the depot's east fence, in from -440 for the back road
TRUCK_PARK_AISLE = (-266.0, -252.5)     # x: no trucks parked across the way in from the gate


def ring_junction(cx, cy, side):
    """Where a pad's link (side -1 west, +1 east) meets its ring's centre line."""
    return cx + side * LINK, cy - math.sqrt(RING_R ** 2 - LINK ** 2)


def ring_points(cx, cy, a0, a1, step=10.0):
    """Points on a ring's centre line, clockwise from angle a0 to a1 (degrees)."""
    while a1 > a0:
        a1 -= 360.0
    n = max(2, int(math.ceil((a0 - a1) / step)))
    return [(cx + RING_R * math.cos(math.radians(a0 + (a1 - a0) * k / n)),
             cy + RING_R * math.sin(math.radians(a0 + (a1 - a0) * k / n))) for k in range(n + 1)]


def junction_angle(side):
    """Angle (degrees) of a link's junction on its ring."""
    return math.degrees(math.atan2(-math.sqrt(RING_R ** 2 - LINK ** 2), side * LINK))


def rings(c4):
    """(cx, cy) of every lighter pad's ring."""
    return [(x, c4.LIGHTER_Y) for x, _ in c4.LIGHTERS]


def links(c4):
    """Link centre lines, port road edge to ring."""
    out = []
    for cx, cy in rings(c4):
        for side in (-1, 1):
            jx, jy = ring_junction(cx, cy, side)
            out.append([(jx, PORT_Y[1] - 1.0), (jx, jy + 2.0)])
    return out


def crate_ghosts(c4):
    """Where the crates south of each parked lighter stood before they moved inside its ring
    (`c4x_scene.add_port`)."""
    if not ON:
        return []
    y = c4.LIGHTER_Y - c4.LIGHTER_R - 3
    return [('apron', (x - 7.5, x + 7.5, y - 9.5, y - 3.5)) for x, who in c4.LIGHTERS if who == 'parked']


def keep_out(c4, margin=1.5):
    """What `port_detail` must keep clear: rectangles round every road segment and link, and the
    rings as annuli (cx, cy, r_in, r_out)."""
    rects = []
    for _, w, pts in ROADS:
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            h = w / 2 + margin
            rects.append((min(ax, bx) - h, max(ax, bx) + h, min(ay, by) - h, max(ay, by) + h))
    for pts in links(c4):
        (ax, ay), (bx, by) = pts
        h = RING_W / 2 + margin
        rects.append((ax - h, ax + h, ay, by))
    annuli = [(cx, cy, RING_R - RING_W / 2 - margin, RING_R + RING_W / 2 + margin) for cx, cy in rings(c4)]
    return rects, annuli


# ---------------------------------------------------------------- drawing (inside Blender)

# Where in the painting (`port-berths-w`) each surface is cut from: (x0, x1, y0, y1) in world metres.
# The road is berth -310's cargo lane with its yellow edge lines, across its width and 40 m along it;
# the apron and the lot are plain stretches beside the first berth and in the truck park's aisle.
TILE = 'port-berths-w'
SAMPLES = {'road': (-313.7, -295.7, 432.0, 472.0),
           'apron': (-478.0, -448.0, 440.0, 470.0),
           'lot': (-240.0, -200.0, 677.0, 689.0)}
PLAIN = {'road': (0.13, 0.13, 0.14), 'apron': (0.18, 0.18, 0.17), 'lot': (0.15, 0.15, 0.16)}


def _tile():
    import json
    for p in sorted((HERE / 'ortho').glob('tiles-*.json')):
        for t in json.loads(p.read_text())['tiles']:
            if t['name'] == TILE and t.get('accepted') and (HERE / 'ortho' / 'raw' / (TILE + '.png')).exists():
                return t
    return None


def _materials(c4):
    """'Port roads paint', textured from the painting, or plain colours when the overhead paint is
    off (a guide render) or the tile is missing."""
    import bpy
    tile = None if os.environ.get('ORTHO_PAINT_OFF') else _tile()
    if tile is None:
        return None, {k: c4.material('Port roads ' + k, rgb, 0.9) for k, rgb in PLAIN.items()}
    m = bpy.data.materials.new('Port roads paint')
    m.use_nodes = True
    nt = m.node_tree
    p = nt.nodes['Principled BSDF']
    p.inputs['Roughness'].default_value = 0.9
    tex = nt.nodes.new('ShaderNodeTexImage')
    tex.image = bpy.data.images.load(str(HERE / 'ortho' / 'raw' / (TILE + '.png')), check_existing=True)
    tex.extension = 'EXTEND'
    nt.links.new(tex.outputs['Color'], p.inputs['Base Color'])
    return tile, m


def _uv(tile, x, y):
    (cx, cy), (w, h) = tile['centre'], tile['size']
    return (x - (cx - w / 2)) / w, (y - (cy - h / 2)) / h


class _Sheet:
    """Quads with their own UVs into the painting, one mesh per surface kind."""

    def __init__(self, c4, tile, mat):
        import bmesh
        self.c4, self.tile, self.mat = c4, tile, mat
        self.bms = {}
        self.bmesh = bmesh

    def bm(self, kind):
        if kind not in self.bms:
            bm = self.bmesh.new()
            self.bms[kind] = (bm, bm.loops.layers.uv.new())
        return self.bms[kind]

    def quad(self, kind, corners, sample_pts, z):
        """corners: (x, y) on the ground; sample_pts: the matching points in the painting."""
        bm, uv = self.bm(kind)
        f = bm.faces.new([bm.verts.new((x, y, z)) for x, y in corners])
        if self.tile is not None:
            for loop, (sx, sy) in zip(f.loops, sample_pts):
                loop[uv].uv = _uv(self.tile, sx, sy)

    def finish(self):
        for kind, (bm, _) in self.bms.items():
            mat = self.mat if not isinstance(self.mat, dict) else self.mat[kind.split()[-1]]
            self.c4.mesh('Port roads %s' % kind, bm, mat)
        self.bms = {}


def _strip(sheet, a, b, w, z, kind='road', s0=0.0):
    """A road segment from a to b, w wide, its painting repeating every 40 m along it. Returns the
    distance along the sample where it ends."""
    sx0, sx1, sy0, sy1 = SAMPLES[kind]
    period = sy1 - sy0
    (ax, ay), (bx, by) = a, b
    L = math.hypot(bx - ax, by - ay)
    if L < 1e-6:
        return s0
    ux, uy = (bx - ax) / L, (by - ay) / L
    nx, ny = -uy, ux
    t = 0.0
    s = s0
    while t < L - 1e-6:
        piece = min(L - t, period - s)
        p0 = (ax + ux * t, ay + uy * t)
        p1 = (ax + ux * (t + piece), ay + uy * (t + piece))
        corners = [(p0[0] - nx * w / 2, p0[1] - ny * w / 2), (p1[0] - nx * w / 2, p1[1] - ny * w / 2),
                   (p1[0] + nx * w / 2, p1[1] + ny * w / 2), (p0[0] + nx * w / 2, p0[1] + ny * w / 2)]
        samples = [(sx1, sy0 + s), (sx1, sy0 + s + piece), (sx0, sy0 + s + piece), (sx0, sy0 + s)]
        sheet.quad(kind, corners, samples, z)
        t += piece
        s = (s + piece) % period
    return s


def _joint(sheet, c, w, z, segs=16):
    """A round join where two segments of a road meet: a disc of the road's middle."""
    sx0, sx1, sy0, sy1 = SAMPLES['road']
    mx, my = (sx0 + sx1) / 2, (sy0 + sy1) / 2
    r = w / 2
    for k in range(segs):
        a0, a1 = 2 * math.pi * k / segs, 2 * math.pi * (k + 1) / segs
        pts = [c, (c[0] + r * math.cos(a0), c[1] + r * math.sin(a0)), (c[0] + r * math.cos(a1), c[1] + r * math.sin(a1))]
        sheet.quad('road', pts, [(mx, my), (mx + 1, my), (mx + 1, my + 1)], z)


def _ring(sheet, cx, cy, z, segs=72):
    """A pad's ring: the lane's painting bent round, its edge lines on the ring's edges."""
    sx0, sx1, sy0, sy1 = SAMPLES['road']
    r0, r1 = RING_R - RING_W / 2, RING_R + RING_W / 2
    period = sy1 - sy0
    s = 0.0
    for k in range(segs):
        a0, a1 = 2 * math.pi * k / segs, 2 * math.pi * (k + 1) / segs
        arc = RING_R * (a1 - a0)
        if s + arc > period:
            s = 0.0
        c0, s0_, c1, s1_ = math.cos(a0), math.sin(a0), math.cos(a1), math.sin(a1)
        corners = [(cx + r0 * c0, cy + r0 * s0_), (cx + r0 * c1, cy + r0 * s1_),
                   (cx + r1 * c1, cy + r1 * s1_), (cx + r1 * c0, cy + r1 * s0_)]
        samples = [(sx0, sy0 + s), (sx0, sy0 + s + arc), (sx1, sy0 + s + arc), (sx1, sy0 + s)]
        sheet.quad('road', corners, samples, z)
        s += arc


def _patch(sheet, rect, kind, z):
    """Cover a rectangle with plain painted ground, tiled from its sample."""
    x0, x1, y0, y1 = rect
    sx0, sx1, sy0, sy1 = SAMPLES[kind]
    pw, ph = sx1 - sx0, sy1 - sy0
    y = y0
    while y < y1 - 1e-6:
        dy = min(ph, y1 - y)
        x = x0
        while x < x1 - 1e-6:
            dx = min(pw, x1 - x)
            sheet.quad('patch ' + kind, [(x, y), (x + dx, y), (x + dx, y + dy), (x, y + dy)],
                       [(sx0, sy0), (sx0 + dx, sy0), (sx0 + dx, sy0 + dy), (sx0, sy0 + dy)], z)
            x += dx
        y += dy


def build(c4, ghosts=()):
    """Ribbons for the new roads and rings, patches over what went (the two removed roads and the
    clutter `port_detail` and `c4x_scene` moved off them: `ghosts`, as (kind, rect))."""
    if not ON:
        return
    tile, mat = _materials(c4)
    sheet = _Sheet(c4, tile, mat)
    patches = [('apron', r) for _, r in REMOVED] + list(ghosts)
    for kind, rect in patches:
        _patch(sheet, rect, kind, PATCH_Z)
    for _, w, pts in ROADS:
        s = 0.0
        for a, b in zip(pts, pts[1:]):
            s = _strip(sheet, a, b, w, RIBBON_Z, s0=s)
        for c in pts[1:-1]:
            _joint(sheet, c, w, RIBBON_Z + 0.005)
    for pts in links(c4):
        _strip(sheet, pts[0], pts[1], RING_W, RIBBON_Z)
    for cx, cy in rings(c4):
        _ring(sheet, cx, cy, RIBBON_Z + 0.01)
    sheet.finish()
    print('PORT_ROADS roads=%d rings=%d patches=%d painted=%s' % (len(ROADS), len(rings(c4)), len(patches),
                                                                  tile is not None), flush=True)
