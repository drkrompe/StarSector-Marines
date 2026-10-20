"""C4/C5: the public port at work, standing still. What a working spaceport leaves on its ground.

Kilo Nine is dense with props, painted vehicles and painted ground; the public side of the port
was apron. This fills it with the static things a busy port has lying about, so the whole place
reads as one working port from C4's 480 m to C5's 1.8 km:

  berth gaps     the apron between the freighter berths and above the lighter pads: a ground
                 equipment park for each pair of berths (tugs and fuel bowsers on their painted
                 bays, power carts, hose reels, stair trucks, dolly trains), a crew hut, a light mast
  lighter pads   dolly trains, power and fuel carts, a stores cage at each
  behind berths  a pipe bridge from the fuel farm to every berth's day tanks; a truck park with
                 bonded stores against the tree line; a maintenance depot and a second tank farm at
                 the west end; overflow container stacks between the heavy berth and the terminal;
                 the terminal's lorry park
  arrivals       the arrivals hall's car park, buses, MuniSec cars, a taxi rank, a kiosk
  belt ends      container overflow and a haulage depot on the dirt past each end of the yard
  apron          painted lanes and bays, light masts on a grid, puddles

Nothing here moves. Moving traffic (port-life) keeps to `LANES`, which nothing here stands on.
With PORT_ROADS=1 the new roads and pad rings (`port_roads`) are kept clear too, and whatever would
have stood on them is moved or left out; `GHOSTS` lists where it stood, for `port_roads` to cover
the overhead painting's picture of it.
Parked port vehicles are built from the yard's own part shapes and named for their slot on the
painted vehicle sheet (`vehicle_paint.SLOTS`), so they wear the same paint; everything else is
ordinary static geometry the overhead paint (`ortho_paint.py`) lays over. Buildings are named
`Warehouses port ...` so `wall_paint.py` clads them by the warehouse rule, and their footprints go
on the scene's WAREHOUSES list so they get roof plant.

    add(c4)   from `c4x_scene.build()`, after the port and before any paint is applied.
              PORT_DETAIL_OFF=1 leaves the port as it was, for a before/after control.
"""
import math
import os
import random

import bmesh
import bpy
from mathutils import Matrix

import port_roads

# Ground moving vehicles use: (x0, x1, y0, y1). Filled in by `add` from the scene's layout.
LANES = []
# What this module put down, (x0, x1, y0, y1): for anyone placing walkers or vehicles later.
FOOTPRINTS = []
# PORT_ROADS: (sample kind, rect) of things the roads displaced, still in the overhead painting.
GHOSTS = []


class Ground:
    """Keep-out bookkeeping: rectangles and circles nothing new may overlap."""

    def __init__(self):
        self.rects, self.circles = [], []
        self.roads, self.rings = [], []        # PORT_ROADS keep-outs: rects, annuli (cx, cy, r_in, r_out)

    def rect(self, x0, x1, y0, y1):
        self.rects.append((min(x0, x1), max(x0, x1), min(y0, y1), max(y0, y1)))

    def circle(self, x, y, r):
        self.circles.append((x, y, r))

    def free(self, x0, x1, y0, y1, pad=0.0, ghost=True):
        """Whether nothing is in the way. Something only the new roads are in the way of is noted in
        GHOSTS (unless `ghost` is off, for a mere candidate such as a puddle)."""
        x0, x1, y0, y1 = x0 - pad, x1 + pad, y0 - pad, y1 + pad
        self.road_blocked = False
        for a0, a1, b0, b1 in self.rects:
            if x0 < a1 and x1 > a0 and y0 < b1 and y1 > b0:
                return False
        for cx, cy, r in self.circles:
            nx, ny = min(max(cx, x0), x1), min(max(cy, y0), y1)
            if math.hypot(nx - cx, ny - cy) < r:
                return False
        if self.on_road(x0, x1, y0, y1):
            self.road_blocked = True
            if ghost:
                GHOSTS.append(('apron', (x0 + pad, x1 - pad, y0 + pad, y1 - pad)))
            return False
        return True

    def on_road(self, x0, x1, y0, y1):
        for a0, a1, b0, b1 in self.roads:
            if x0 < a1 and x1 > a0 and y0 < b1 and y1 > b0:
                return True
        for cx, cy, r0, r1 in self.rings:
            nx, ny = min(max(cx, x0), x1), min(max(cy, y0), y1)
            far = max(math.hypot(x - cx, y - cy) for x in (x0, x1) for y in (y0, y1))
            if math.hypot(nx - cx, ny - cy) < r1 and far > r0:
                return True
        return False

    def take(self, x0, x1, y0, y1):
        self.rect(x0, x1, y0, y1)
        FOOTPRINTS.append((min(x0, x1), max(x0, x1), min(y0, y1), max(y0, y1)))


def add(c4):
    """Everything, in one pass. `c4` is the scene module (`c4x_scene`)."""
    if os.environ.get('PORT_DETAIL_OFF'):
        return
    r = random.Random(57)
    del LANES[:]
    del FOOTPRINTS[:]
    del GHOSTS[:]
    g = Ground()
    P = Parts(c4)
    keep_out(c4, g)
    trace = os.environ.get('PORT_DETAIL_TRACE')
    for zone in (berth_gaps, ferry_side, west_end, boneyard, fuel_loading, lighter_pads, pipe_bridge,
                 truck_park, west_depot, container_overflow, terminal_lorries, arrivals, belt_ends, apron):
        if zone is pipe_bridge:
            zone(c4, g, P)
        else:
            zone(c4, g, P, r)
        if trace:                                          # where the random stream stands after each zone
            print('PORT_DETAIL_TRACE %s %d' % (zone.__name__, hash(r.getstate()) & 0xffffff), flush=True)
    P.finish()
    print('PORT_DETAIL footprints=%d lanes=%d' % (len(FOOTPRINTS), len(LANES)), flush=True)


# ---------------------------------------------------------------- shared geometry

class Parts:
    """One bmesh per material, meshed once at the end so the scene gains few objects."""

    def __init__(self, c4, label='Port detail '):
        self.c4 = c4
        self.bms = {}
        self.label = label
        # PORT_ROADS: clutter moved to where the overhead painting shows bare ground, drawn in its own
        # colours (left out of the paint, `ortho_paint.moving_or_textured`).
        self.inner = Parts.__new__(Parts) if label == 'Port detail ' else None
        if self.inner is not None:
            self.inner.c4, self.inner.bms, self.inner.label, self.inner.inner = c4, {}, 'Port roads detail ', None
        M = c4.material
        D = bpy.data.materials
        self.m = {
            'steel': D['Steel'], 'concrete': D['Concrete'], 'asphalt': D['Asphalt'],
            'tank': D['Tank white'], 'crate': D['Crates'], 'stock': D['Steel stock'],
            'fence': D['Fence steel'], 'yellow': D['Crane yellow'], 'paint': D['Pad paint'],
            'cab': D['Hauler cab'], 'load': D['Hauler load'], 'orange': D['Hegemony orange'],
            'white line': M('Apron paint white', (0.86, 0.86, 0.82), 0.6),
            'gse': M('Ground equipment', (0.86, 0.66, 0.10), 0.5, 0.1),
            'gse white': M('Ground equipment white', (0.82, 0.82, 0.78), 0.5),
            'gse blue': M('Ground equipment blue', (0.18, 0.30, 0.52), 0.5, 0.1),
            'dolly': M('Cargo dollies', (0.32, 0.33, 0.34), 0.6, 0.5),
            'hose': M('Hose reels', (0.10, 0.10, 0.10), 0.7),
            'pipe': M('Pipe runs', (0.62, 0.60, 0.52), 0.5, 0.4),
            'pallet': M('Pallets', (0.56, 0.44, 0.28), 0.9),
            'tarp blue': M('Tarp blue', (0.16, 0.30, 0.55), 0.8),
            'tarp green': M('Tarp green', (0.22, 0.34, 0.22), 0.8),
            'drums': M('Drums', (0.45, 0.12, 0.08), 0.6, 0.3),
            'drums blue': M('Drums blue', (0.12, 0.22, 0.45), 0.6, 0.3),
            'gravel': M('Lot gravel', (0.27, 0.25, 0.22), 0.95),
            'lot': M('Lot asphalt', (0.15, 0.15, 0.16), 0.9),
            'bund': M('Bund concrete', (0.46, 0.45, 0.42), 0.9),
            'puddle': M('Puddles', (0.16, 0.18, 0.20), 0.06),
            'scrap': D['Hull plate'],
            'lamp': M('Mast lamps', (0.85, 0.85, 0.80), 0.3, 0.2),
            'car': [M('Parked car %d' % i, c, 0.35, 0.3) for i, c in enumerate(
                ((0.72, 0.72, 0.70), (0.12, 0.13, 0.15), (0.55, 0.12, 0.10), (0.22, 0.30, 0.45),
                 (0.60, 0.58, 0.52), (0.30, 0.34, 0.30), (0.82, 0.62, 0.20)))],
            'trailer': [D['Hauler load'], M('Trailer grey', (0.48, 0.50, 0.52), 0.6, 0.2),
                        M('Trailer blue', (0.20, 0.32, 0.50), 0.6, 0.2), D['Container 0'],
                        D['Container 2'], M('Trailer tank', (0.70, 0.72, 0.72), 0.35, 0.6)],
            'cab colours': [D['Hauler cab'], M('Cab white', (0.85, 0.85, 0.82), 0.5),
                            M('Cab red', (0.62, 0.12, 0.08), 0.5), M('Cab blue', (0.15, 0.28, 0.55), 0.5),
                            M('Cab green', (0.20, 0.36, 0.22), 0.5)],
            'roofs': [D['Roof rust'], D['Roof grey'], D['Roof white'], D['Roof green']],
            'modules': [D['Module %d' % i] for i in range(4)],
            'containers': [D['Container %d' % i] for i in range(6)],
        }
        if self.inner is not None:
            self.inner.m = self.m

    def bm(self, key):
        mat = key if isinstance(key, bpy.types.Material) else self.m[key]
        return self.bms.setdefault(mat.name, (bmesh.new(), mat))[0]

    def box(self, key, size, centre, rot=0.0):
        self.c4.box(self.bm(key), size, centre, rot)

    def cyl(self, key, radius, height, base, segs=16, top=None):
        self.c4.cylinder(self.bm(key), radius, height, base, segs, top)

    def strut(self, key, a, b, thick):
        self.c4.strut(self.bm(key), a, b, thick)

    def finish(self):
        for name, (bm, mat) in self.bms.items():
            label = 'Warehouses port ' + mat.name if name.startswith('Roof ') else self.label + mat.name
            self.c4.mesh(label, bm, mat)
        self.bms = {}
        if self.inner is not None:
            self.inner.finish()


class Null:
    """Takes the place of `Parts` where something is no longer put down, so the random draws it
    would have made are still made and everything after it stays where it was."""

    def __init__(self, P):
        self.m = P.m

    def box(self, *a, **k):
        pass

    def cyl(self, *a, **k):
        pass

    def strut(self, *a, **k):
        pass

    def bm(self, key):
        return bmesh.new()


def rot_size(w, d, h):
    """Axis-aligned extent of a w x d box turned h."""
    c, s = abs(math.cos(h)), abs(math.sin(h))
    return w * c + d * s, w * s + d * c


def park(c4, name, parts, x, y, h):
    """A parked vehicle: the scene's own vehicle builder, one key, never moves."""
    return c4.vehicle(name, parts, [(0, x, y)], [(0, h)])


# ---------------------------------------------------------------- keep-out

def keep_out(c4, g):
    """Roads, lanes, pads and everything already standing, so nothing new lands on them."""
    h = c4.ROW_HEADING
    lanes = [(-700.0, c4.GATE[1] + 4, 362.0, 382.0),          # port road with its verges
             (-600.0, 600.0, 334.0, 362.0),                   # loading strip
             (43.0 - 3, 57.0 + 3, 378.0, c4.HEAVY[2]),          # heavy berth road
             (-2000.0, 2000.0, -98.0, -72.0)]                 # freight road
    # The two roads PORT_ROADS removes stay clear (a patch covers them) so that every placement
    # and random draw off the new roads is the one the approved scene makes.
    lanes += [(-302.0, -283.0, 556.0, 645.0),                 # fuel farm road
              (57.0, 182.0, 677.0, 695.0)]                    # terminal road
    if port_roads.ON:
        g.roads, g.rings = port_roads.keep_out(c4)
        for x, off, who in c4.BERTHS:                         # the fuel bowser's way up from pad 5's ring
            if who == 'fuelling':
                gx = x + 39.0
                g.roads.append((gx - 5, gx + 5, c4.LIGHTER_Y + port_roads.RING_R, c4.BERTH_Y + c4.BERTH_DY[x] - 60))
        g.roads += moving_ground(c4, new=True)             # not in LANES: puddles draw as they do without the roads
    for x in c4.CROSS_STREETS:
        lanes.append((x - 9, x + 9, -75.0, 362.0))
    for x, off, who in c4.BERTHS:
        cy = c4.BERTH_Y + c4.BERTH_DY[x]
        s0 = c4.at(x, cy, h, -(c4.BERTH_R + 4), 0)
        if who != 'ferry':
            lanes.append((s0[0] - 11, s0[0] + 11, 342.0, s0[1] + 2))     # cargo lane, its carriers' swing
        g.circle(x, cy, c4.BERTH_R + 9)
        # Tower, tanks and supplies on the berth's north and west.
        tx, ty, _ = c4.at(x, cy, h, 4, c4.BERTH_R + 10)
        g.rect(tx - 7, tx + 7, ty - 7, ty + 7)
        for k in (0, 1):
            ux, uy, _ = c4.at(x, cy, h, c4.BERTH_R + 12, -18 - k * 11)
            g.circle(ux, uy, 7)
        a = c4.at(x, cy, h, c4.BERTH_R + 4, 24)
        b = c4.at(x, cy, h, c4.BERTH_R + 66, -14)
        g.rect(a[0], b[0], a[1], b[1])
        if who in ('hound', 'unloading', 'fuelling'):                     # stern laydown
            a = c4.at(x, cy, h, -(c4.BERTH_R + 12), 20)
            b = c4.at(x, cy, h, -(c4.BERTH_R + 3), -20)
            g.rect(a[0], b[0], a[1], b[1])
        if who == 'fuelling':
            bx, by, _ = c4.at(x, cy, h, 4, -(c4.BERTH_R + 6))
            g.rect(bx - 9, bx + 9, by - 9, by + 9)
    for x, who in c4.LIGHTERS:
        g.circle(x, c4.LIGHTER_Y, c4.LIGHTER_R + 7)
        g.rect(x - 8, x + 8, c4.LIGHTER_Y - c4.LIGHTER_R - 14, c4.LIGHTER_Y)    # its crates
    lanes += moving_ground(c4)
    LANES.extend(lanes)
    for ln in lanes:
        g.rect(*ln)
    hx0, hx1, hy0, hy1 = c4.HEAVY
    g.rect(hx0 - 8, hx1 + 8, hy0 - 48, hy1 + 4)                 # the bay, its rails, staged boxes, carriers
    g.rect(-410.0, -280.0, 640.0, 710.0)                       # fuel farm
    g.rect(95.0, 196.0, 595.0, 640.0)                          # staged modules and steel stock
    g.rect(176.0, 264.0, 620.0, 699.0)                         # terminal shed, doors, its truck
    ox, oy = c4.OFFICE
    g.rect(ox - 24, ox + 22, oy - 21, oy + 21)                 # arrivals hall
    fx, fy, _ = c4.at(140.0, c4.BERTH_Y, h, 0, -(c4.BERTH_R + 4))
    g.rect(fx - 5, fx + 5, oy - 4, fy + 2)                     # its covered walks
    g.rect(fx - 5, ox, oy - 5, oy + 5)
    x0, x1, y0, y1 = c4.YARD
    g.rect(x0 - 6, x1 + 2000, y0 - 6, 2500.0)                  # Kilo Nine and the jungle past it
    g.rect(-2000.0, 2000.0, 758.0, 2500.0)                     # the tree line
    g.rect(-600.0, 600.0, 210.0, 340.0)                        # container yard
    for x, y, w, d, hh in c4.WAREHOUSES:
        g.rect(x - w / 2 - 2, x + w / 2 + 2, y - d / 2 - 2, y + d / 2 + 2)
    g.rect(-250.0, -214.0, -165.0, -92.0)                      # gate checkpoint


def moving_ground(c4, new=False):
    """Where port-life's vehicles drive (`port_life.ROUTES`, a lane centre each, vehicles up to 3 m
    wide), and the paths its berth forklifts and second bowser take up the berth gaps
    (`port_life.berth_work`): rectangles nothing standing may overlap. The approved loops
    (`ROUTES_OFF`) are kept clear either way; `new` gives PORT_ROADS' loops and trips instead,
    which go in with the roads."""
    out = []
    try:
        import port_life
    except ImportError:
        return out
    w = 4.0
    routes = port_life.ROUTES if new else getattr(port_life, 'ROUTES_OFF', port_life.ROUTES)
    for name, rt in routes.items():
        pts = rt['pts'] + ([rt['pts'][0]] if rt.get('closed', True) else [])
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            out.append((min(ax, bx) - w, max(ax, bx) + w, min(ay, by) - w, max(ay, by) + w))
    if new:
        for name, t in port_life.TRIPS.items():             # PORT_ROADS: the trips' ways
            for (ax, ay), (bx, by) in zip(t['pts'], t['pts'][1:]):
                out.append((min(ax, bx) - w, max(ax, bx) + w, min(ay, by) - w, max(ay, by) + w))
        return out
    for x, off, who in c4.BERTHS:
        if who not in ('hound', 'unloading', 'fuelling'):
            continue
        cy = c4.BERTH_Y + c4.BERTH_DY[x]
        gx = x + 39.0
        if who == 'fuelling':
            out.append((gx - 5, gx + 5, cy - 68, cy - 2))
        else:
            out.append((gx - 5, gx + 5, cy - 46, cy + 28))
    return out


# ---------------------------------------------------------------- pieces of kit

def power_cart(P, x, y, h):
    """A ground power unit on a trailer: a boxy generator, a cable drum."""
    P.box('gse', (3.2, 1.8, 1.6), (x, y, 1.1), h)
    P.box('hose', (0.9, 1.4, 0.9), (x + 1.9 * math.cos(h), y + 1.9 * math.sin(h), 1.0), h)


def hose_reel(P, x, y, h):
    P.box('gse white', (2.6, 1.6, 1.0), (x, y, 0.8), h)
    P.cyl('hose', 0.75, 1.3, (x, y, 1.3), 12)


def stair_truck(P, x, y, h):
    """A passenger stair on a truck chassis: a long white body, the stair rising off its back."""
    c, s = math.cos(h), math.sin(h)
    P.box('gse white', (7.0, 2.4, 1.6), (x, y, 1.2), h)
    P.box('gse', (2.0, 2.4, 1.4), (x + 3.6 * c, y + 3.6 * s, 1.9), h)
    P.strut('gse white', (x - 2.5 * c, y - 2.5 * s, 2.0), (x - 6.5 * c, y - 6.5 * s, 4.6), 1.6)


def dolly_train(P, r, x, y, h, n):
    """A short tractor and its train of cargo dollies, some loaded."""
    c, s = math.cos(h), math.sin(h)
    P.box('gse blue', (2.6, 1.6, 1.5), (x, y, 1.0), h)
    for k in range(n):
        d = 3.4 + k * 3.6
        px, py = x - d * c, y - d * s
        P.box('dolly', (3.0, 2.0, 0.5), (px, py, 0.6), h)
        if r.random() < 0.7:
            key = r.choice(('crate', 'tarp blue', 'pallet', 'tarp green', 'modules'))
            if key == 'modules':
                key = r.choice(P.m['modules'])
            P.box(key, (2.6, 1.8, r.uniform(1.0, 1.8)), (px, py, 1.6), h)


def fuel_cart(P, x, y, h):
    P.box('dolly', (4.0, 1.9, 0.5), (x, y, 0.7), h)
    tube(P, 'tank', 0.85, 3.4, (x, y, 1.75), h)


def tube(P, key, radius, length, centre, h):
    """A cylinder lying along heading h: a tank on a cart or a trailer."""
    bm = P.bm(key)
    res = bmesh.ops.create_cone(bm, cap_ends=True, segments=14, radius1=radius, radius2=radius, depth=length)
    m = Matrix.Translation(centre) @ Matrix.Rotation(h, 4, 'Z') @ Matrix.Rotation(math.pi / 2, 4, 'Y')
    bmesh.ops.transform(bm, matrix=m, verts=res['verts'])


def hut(P, r, x, y, w=6.0, d=3.0, hh=3.0, roof=None):
    """A portable site office: a container-sized hut with a flat roof."""
    P.box(roof or r.choice(P.m['roofs']), (w, d, hh), (x, y, hh / 2))


def light_mast(P, x, y, height=30.0):
    """A floodlight mast: a pole, a headframe, lamps; its long shadow reads from high up."""
    P.strut('steel', (x, y, 0), (x, y, height), 0.9)
    P.box('steel', (5.0, 1.4, 0.8), (x, y, height))
    for k in (-1.6, 0, 1.6):
        P.box('lamp', (1.2, 1.0, 0.6), (x + k, y - 0.6, height - 0.6))
    P.box('concrete', (2.4, 2.4, 0.6), (x, y, 0.3))


def drums(P, r, x, y, n=8):
    key = r.choice(('drums', 'drums blue'))
    for i in range(n):
        P.cyl(key, 0.45, 1.0, (x + (i % 4) * 1.0, y + (i // 4) * 1.0, 0), 8)


def crate_stack(P, r, x, y, cols, rows, h=0.0):
    for i in range(cols):
        for j in range(rows):
            for lvl in range(r.randrange(1, 3)):
                key = r.choice(('crate', 'crate', 'pallet', 'tarp blue', 'tarp green'))
                P.box(key, (2.2, 2.2, 2.0), (x + i * 2.5, y + j * 2.5, 1.0 + lvl * 2.0), h)


def fence_run(P, a, b, gap=None, step=6.0):
    """A chain-link run from a to b: posts, a top rail, and the dark line its mesh makes from above.
    `gap` is (from, to) metres along it left open for a gate."""
    ax, ay = a
    bx, by = b
    length = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / length, (by - ay) / length
    h = math.atan2(uy, ux)
    t = 0.0
    lo, hi = gap if gap else (length + 1, length + 2)
    while t <= length + 1e-6:
        if not lo < t < hi:
            P.box('fence', (0.5, 0.5, 2.8), (ax + ux * t, ay + uy * t, 1.4))
        t += step
    for s0, s1 in ((0.0, min(lo, length)), (min(hi, length), length)):
        if s1 - s0 < 0.5:
            continue
        mx, my = ax + ux * (s0 + s1) / 2, ay + uy * (s0 + s1) / 2
        P.box('fence', (s1 - s0, 0.12, 0.12), (mx, my, 2.7), h)
        P.box('hose', (s1 - s0, 0.9, 0.04), (mx, my, 0.16), h)      # the mesh's line, seen from above


def fence_box(P, x0, x1, y0, y1, gate_side='S', gate=(10.0, 22.0), second=None):
    """A fenced rectangle with a gate in one side; `second` is (side, gap) for a second gate."""
    sides = {'S': ((x0, y0), (x1, y0)), 'N': ((x0, y1), (x1, y1)),
             'W': ((x0, y0), (x0, y1)), 'E': ((x1, y0), (x1, y1))}
    for k, (a, b) in sides.items():
        gap = gate if k == gate_side else second[1] if second and k == second[0] else None
        fence_run(P, a, b, gap)


def bays(P, x0, y0, n, w, d, horizontal=True):
    """Painted parking bays: a run of n bays w wide and d deep, starting at (x0, y0)."""
    for k in range(n + 1):
        if horizontal:
            P.box('white line', (0.5, abs(d), 0.05), (x0 + k * w, y0 + d / 2, 0.11))
        else:
            P.box('white line', (d, 0.5, 0.05), (x0 + d / 2, y0 + k * w, 0.11))
    if horizontal:
        P.box('white line', (n * w, 0.5, 0.05), (x0 + n * w / 2, y0, 0.11))
    else:
        P.box('white line', (0.5, n * w, 0.05), (x0, y0 + n * w / 2, 0.11))


def truck(P, r, x, y, h, trailer=None, cab=None, length=13.5):
    """A parked articulated truck (static, so the overhead paint draws its roof)."""
    c, s = math.cos(h), math.sin(h)
    P.box(cab or r.choice(P.m['cab colours']), (3.4, 2.8, 3.2), (x, y, 1.9), h)
    t = trailer or r.choice(P.m['trailer'])
    tx, ty = x - (length / 2 + 2.0) * c, y - (length / 2 + 2.0) * s
    if t.name == 'Trailer tank':
        tube(P, t, 1.3, length - 1.5, (tx, ty, 2.5), h)
        P.box('dolly', (length, 2.4, 0.4), (tx, ty, 1.0), h)
    else:
        P.box(t, (length, 2.9, 3.2), (tx, ty, 2.3), h)


def car(P, r, x, y, h):
    P.box(r.choice(P.m['car']), (4.6, 2.0, 1.4), (x, y, 0.8), h)


def puddle(P, r, x, y, size):
    """A shallow puddle in a low spot: a few overlapping flat ellipses strung along one direction,
    as rain lies in a rut or a sunken panel joint, never a round dot."""
    h = r.uniform(0, math.pi)
    c, s = math.cos(h), math.sin(h)
    for _ in range(r.randrange(3, 6)):
        t = r.uniform(-0.5, 0.5) * size
        n = r.uniform(-0.12, 0.12) * size
        rad = size * r.uniform(0.18, 0.32)
        bm = P.bm('puddle')
        res = bmesh.ops.create_cone(bm, cap_ends=True, segments=12, radius1=rad, radius2=rad, depth=0.02)
        m = Matrix.Translation((x + t * c - n * s, y + t * s + n * c, 0.075)) @ Matrix.Rotation(h + r.uniform(-0.3, 0.3), 4, 'Z')             @ Matrix.Diagonal((r.uniform(1.8, 3.0), r.uniform(0.6, 0.9), 1.0, 1.0))
        bmesh.ops.transform(bm, matrix=m, verts=res['verts'])


# ---------------------------------------------------------------- the zones

def berth_gaps(c4, g, P, r):
    """A ground equipment park in each stretch of apron between two berths' lanes, above the lighter
    pad: painted bays, the tugs and bowsers on them, carts and dolly trains, a crew hut and a mast."""
    xs = sorted(x for x, off, who in c4.BERTHS if who != 'ferry')
    spans = [(xa + 26.0, xb - 6.0) for xa, xb in zip(xs, xs[1:])] + [(xs[-1] + 24.0, 38.0)]
    k = 0
    for x0, x1 in spans:
        y0, y1 = c4.LIGHTER_Y + c4.LIGHTER_R + 10, 488.0
        # Two rows of bays, back to back: the first noses out south toward the lighter row, the
        # second north toward the berths. Each holds a tug, a bowser, a power cart, a stair truck
        # or a pair of hose reels.
        n = int((x1 - x0 - 4) // 7)
        bx = x0 + 2
        for row, (by, h) in enumerate(((y0 + 1, -math.pi / 2), (y0 + 13, math.pi / 2))):
            for i in range(n):
                cx, cy = bx + 3.5 + i * 7, by + 6
                if not g.free(cx - 3.5, cx + 3.5, by, by + 12):
                    continue
                bays(P, cx - 3.5, by + (0 if row == 0 else 12), 1, 7.0, 12.0 if row == 0 else -12.0)
                kind = (i * 3 + k + row * 2) % 6
                if kind == 0:
                    park(c4, 'Tug port %d %d %d' % (k, row, i), c4.tug_parts(P.m['gse']), cx, cy, h)
                elif kind == 1:
                    park(c4, 'Port bowser parked %d %d %d' % (k, row, i), c4.bowser_parts(P.m['load']), cx, cy, h)
                elif kind == 2:
                    power_cart(P, cx, cy, h)
                elif kind == 3:
                    stair_truck(P, cx, cy, h)
                elif kind == 4:
                    hose_reel(P, cx - 1.2, cy - 2, h)
                    hose_reel(P, cx + 1.2, cy + 2, h)
                g.take(cx - 3.5, cx + 3.5, by, by + 12)
        # Above the bays: dolly trains parked in rows, a crew hut, drums, a mast.
        yy = y0 + 31
        for row in range(2):
            y = yy + row * 7
            x = x0 + 4
            while x + 18 < x1:
                if g.free(x - 2, x + 18, y - 2, y + 2, 1.0):
                    dolly_train(P, r, x + 16, y, 0.0, r.randrange(3, 5))
                    g.take(x - 2, x + 18, y - 2, y + 2)
                elif g.road_blocked:                     # the same draws and keep-out, so what follows stays put
                    dolly_train(Null(P), r, x + 16, y, 0.0, r.randrange(3, 5))
                    g.take(x - 2, x + 18, y - 2, y + 2)
                x += 22
        hx, hy = x1 - 6, yy + 15
        if g.free(hx - 4, hx + 4, hy - 2, hy + 2, 1.0):
            hut(P, r, hx, hy, 6.0, 3.0, 3.0)
            drums(P, r, hx - 9, hy - 1)
            g.take(hx - 11, hx + 4, hy - 2, hy + 2)
        elif g.road_blocked:
            hut(Null(P), r, hx, hy, 6.0, 3.0, 3.0)
            drums(Null(P), r, hx - 9, hy - 1)
            g.take(hx - 11, hx + 4, hy - 2, hy + 2)
        mx, my = (x0 + x1) / 2, yy + 17
        if g.free(mx - 2, mx + 2, my - 2, my + 2):
            light_mast(P, mx, my)
            g.take(mx - 1.5, mx + 1.5, my - 1.5, my + 1.5)
        k += 1


def ferry_side(c4, g, P, r):
    """The apron below the ferry's pad: baggage dolly trains, passenger stairs, a baggage hut."""
    x0, x1, y0, y1 = 66.0, 184.0, 436.0, 484.0
    for row in range(3):
        y = y0 + 4 + row * 8
        x = x0
        while x + 18 < x1:
            if g.free(x - 2, x + 18, y - 2, y + 2, 0.5):
                dolly_train(P, r, x + 16, y, 0.0, r.randrange(3, 6))
                g.take(x - 2, x + 18, y - 2, y + 2)
            elif g.road_blocked:
                dolly_train(Null(P), r, x + 16, y, 0.0, r.randrange(3, 6))
                g.take(x - 2, x + 18, y - 2, y + 2)
            x += 22
    for k in range(3):
        x, y = 160.0 + k * 7, 466.0
        if g.free(x - 2, x + 2, y - 5, y + 5):
            stair_truck(P, x, y, math.pi / 2)
            g.take(x - 2, x + 2, y - 8, y + 5)
    if g.free(64.0, 76.0, 474.0, 482.0):
        hut(P, r, 70.0, 478.0, 10.0, 5.0, 3.5)
        g.take(64.0, 76.0, 474.0, 482.0)
    elif g.road_blocked:
        hut(Null(P), r, 70.0, 478.0, 10.0, 5.0, 3.5)
        g.take(64.0, 76.0, 474.0, 482.0)


def west_end(c4, g, P, r):
    """Past the first berth to where the port road starts: the lighters' maintenance hangars along
    the tree line, an equipment apron before them, a fenced spares yard and a hauliers' lot."""
    for k, (cx, w) in enumerate(((-668.0, 54.0), (-606.0, 54.0))):
        hh, cy, d = 16.0, 732.0, 38.0
        P.box(P.m['roofs'][(k * 2 + 1) % 4], (w - 4, d, hh), (cx, cy, hh / 2))
        c4.WAREHOUSES.append((cx, cy, w - 4, d, hh))
    c4.plane('West hangar apron', -700.0, -530.0, 650.0, 713.0, P.m['concrete'], 0.06)
    g.take(-700.0, -530.0, 650.0, 752.0)
    for i in range(6):
        park(c4, 'Tug west %d' % i, c4.tug_parts(P.m['gse']), -690.0 + i * 10, 664.0, math.pi / 2)
    for i in range(3):
        stair_truck(P, -620.0 + i * 7, 664.0, math.pi / 2)
        power_cart(P, -596.0 + i * 6, 662.0, math.pi / 2)
    park(c4, 'Flatbed west', c4.flatbed_parts(P.m['cab'], P.m['steel'], P.m['scrap']), -560.0, 690.0, 0.0)
    for i in range(3):
        for lvl in range(2):
            P.box(r.choice(P.m['modules']), (7, 5, 4), (-690.0 + i * 8, 698.0, 2 + 4 * lvl))
    light_mast(P, -532.0, 652.0)
    # Spares yard: crates, module stacks and stripped parts behind a fence.
    x0, x1, y0, y1 = -700.0, -596.0, 560.0, 636.0
    c4.plane('Spares yard', x0, x1, y0, y1, P.m['gravel'], 0.06)
    for i in range(4):
        crate_stack(P, r, x0 + 6 + i * 16, y0 + 8, 4, 3)
    for i in range(6):
        for j in range(2):
            for lvl in range(r.randrange(1, 3)):
                P.box(r.choice(P.m['modules']), (7, 5, 4), (x0 + 10 + i * 9, y0 + 40 + j * 8, 2 + 4 * lvl))
    for _ in range(30):
        P.box('scrap', (r.uniform(1.5, 6), r.uniform(1.5, 4), r.uniform(0.6, 2.4)),
              (r.uniform(x1 - 30, x1 - 6), r.uniform(y1 - 22, y1 - 6), r.uniform(0.3, 1.6)), r.uniform(0, math.pi))
    fence_box(P, x0, x1, y0, y1, 'E', (12.0, 28.0))
    g.take(x0, x1, y0, y1)
    # Hauliers' lot by the head of the port road.
    x0, x1, y0, y1 = -700.0, -566.0, 404.0, 540.0
    c4.plane('West truck lot', x0, x1, y0, y1, P.m['lot'], 0.06)
    for cab_y, h in ((y0 + 6, -math.pi / 2), (y0 + 44, math.pi / 2), (y0 + 62, -math.pi / 2),
                     (y0 + 100, math.pi / 2), (y0 + 112, -math.pi / 2)):
        x = x0 + 6
        while x < x1 - 4 and cab_y + 20 < y1 + 2:
            P.box('white line', (0.4, 17.0, 0.05), (x - 3.0, cab_y + (8.5 if h < 0 else -8.5), 0.1))
            if r.random() < 0.75:
                truck(P, r, x, cab_y, h)
            x += 6.0
    for x in (x0 + 2, x1 - 2):
        light_mast(P, x, y1 - 2, 24.0)
    fence_box(P, x0, x1, y0, y1, 'S', (60.0, 76.0), ('E', port_roads.WEST_LOT_GATE) if port_roads.ON else None)
    g.take(x0, x1, y0, y1)


def boneyard(c4, g, P, r):
    """Past the head of the port road: hulls nobody came back for, cut into sections, and the
    containers they emptied, on rough gravel."""
    x0, x1, y0, y1 = -830.0, -708.0, 386.0, 700.0
    c4.plane('Boneyard', x0, x1, y0, y1, P.m['gravel'], 0.06)
    for k in range(9):
        cx, cy = r.uniform(x0 + 16, x1 - 16), y0 + 18 + k * 33 + r.uniform(-4, 4)
        h = r.uniform(-0.5, 0.5)
        w, d = r.uniform(18, 34), r.uniform(8, 14)
        P.box('scrap', (w, d, r.uniform(4, 8)), (cx, cy, 2.5), h)
        for _ in range(10):
            P.box('scrap', (r.uniform(2, 7), r.uniform(1.5, 5), r.uniform(0.5, 2.0)),
                  (cx + r.uniform(-w, w) * 0.8, cy + r.uniform(-d, d), r.uniform(0.3, 1.2)), r.uniform(0, math.pi))
    for row in range(3):
        x = x0 + 4
        y = y1 - 30 + row * 10
        while x < x1 - 12:
            if r.random() < 0.7:
                for lvl in range(r.randrange(1, 3)):
                    P.box(r.choice(P.m['containers']), (12, 2.4, 2.6), (x + 6, y, 1.3 + 2.6 * lvl), r.uniform(-0.08, 0.08))
            x += 13
    fence_run(P, (x1, y0), (x1, y1), (40.0, 56.0))
    g.take(x0, x1, y0, y1)


def fuel_loading(c4, g, P, r):
    """North of the fuel farm: the tanker loading rack under its canopy, tankers in line for it."""
    x0, x1, y0, y1 = -400.0, -290.0, 714.0, 754.0
    c4.plane('Tanker rack apron', x0, x1, y0, y1, P.m['concrete'], 0.07)
    tank = next(t for t in P.m['trailer'] if t.name == 'Trailer tank')
    for k in range(4):
        truck(P, r, -372.0 + k * 9, 744.0, -math.pi / 2, tank, P.m['cab colours'][1])
    P.box('steel', (40.0, 18.0, 0.6), (-358.0, 735.0, 7.5))            # the rack's canopy
    for x in (-376.0, -340.0):
        for y in (728.0, 742.0):
            P.strut('steel', (x, y, 0), (x, y, 7.2), 0.6)
    for k in range(3):
        truck(P, r, -322.0 + k * 9, 744.0, -math.pi / 2, tank, r.choice(P.m['cab colours']))
    hut(P, r, -394.0, 722.0, 6.0, 4.0, 3.0, P.m['roofs'][2])
    g.take(x0, x1, y0, y1)


def lighter_pads(c4, g, P, r):
    """Each Hermes pad's own clutter, on the apron beside it: a dolly train, a power cart, a fuel
    cart, a stores cage. With PORT_ROADS its service ring runs there, so a smaller set stands inside
    the ring instead (the dolly trains drive the ring now), and the draws the old set made are still
    made, so everything placed after it stays where it was."""
    for i, (x, who) in enumerate(c4.LIGHTERS):
        y = c4.LIGHTER_Y
        for side in (-1, 1):
            cx = x + side * (c4.LIGHTER_R + 12)
            if port_roads.ON:
                # Free but for the ring (and noted for a patch) is where the old set stood: only
                # there does it make the old set's draws.
                old = g.free(cx - 4, cx + 4, y - 10, y + 10) or g.road_blocked
                null = Null(P)
                if side < 0:
                    if old:
                        dolly_train(null, r, cx, y + 6, -math.pi / 2, 3)
                    ix = x - 17.5
                    power_cart(P.inner, ix, y + 0.5, math.pi / 2)
                else:
                    if old:
                        crate_stack(null, r, cx - 1.2, y - 6.2, 2, 1)
                    ix = x + 17.5
                    fuel_cart(P.inner, ix, y - 4.0, math.pi / 2)
                    P.inner.box('fence', (3.4, 3.0, 2.4), (ix, y + 3.0, 1.2))           # stores cage
                if old:
                    g.take(cx - 4, cx + 4, y - 10, y + 10)
                g.take(ix - 2, ix + 2, y - 6, y + 6)
                continue
            if g.free(cx - 4, cx + 4, y - 10, y + 10):
                if side < 0:
                    dolly_train(P, r, cx, y + 6, -math.pi / 2, 3)
                    power_cart(P, cx + 3, y - 6, math.pi / 2)
                else:
                    fuel_cart(P, cx, y + 5, math.pi / 2)
                    P.box('fence', (4.0, 3.0, 2.4), (cx, y - 5, 1.2))          # stores cage
                    crate_stack(P, r, cx - 1.2, y - 6.2, 2, 1)
                g.take(cx - 4, cx + 4, y - 10, y + 10)


def pipe_bridge(c4, g, P):
    """Fuel from the farm to every berth's day tanks: a pipe rack on portals along the back of the
    berths, high enough to drive under, with a drop down to each pair of tanks."""
    h = c4.ROW_HEADING
    y = 616.0
    z = 6.0
    xs = [x for x, off, who in c4.BERTHS if who != 'ferry']
    xw, xe = min(xs) - 10, -64.0
    for dy in (-0.7, 0.7):
        P.strut('pipe', (xw, y + dy, z), (xe, y + dy, z), 0.8)
    for x in range(int(xw), int(xe), 12):
        P.strut('steel', (x, y - 1.6, 0), (x, y - 1.6, z - 0.5), 0.5)
        P.strut('steel', (x, y + 1.6, 0), (x, y + 1.6, z - 0.5), 0.5)
        P.box('steel', (0.5, 3.6, 0.5), (x, y, z - 0.6))
    # Up from the farm, along the west side of its road.
    for dx in (-0.7, 0.7):
        P.strut('pipe', (-306 + dx, 645, 1.2), (-306 + dx, y, z), 0.8)
    for x, off, who in c4.BERTHS:
        if who == 'ferry':
            continue
        cy = c4.BERTH_Y + c4.BERTH_DY[x]
        tx, ty, _ = c4.at(x, cy, h, c4.BERTH_R + 12, -23.5)
        P.strut('pipe', (tx, y, z), (tx, ty + 5, 4.0), 0.7)
        py = (y + ty) / 2
        if port_roads.ON:                  # off the back road (y 603.5-612.5); the pipe crosses it 4.8 m up
            py = min(py, 600.5)
        P.strut('steel', (tx, py, 0), (tx, py, 4.0 + (py - ty - 5) / (y - ty - 5) * (z - 4.0)), 0.5)
    g.take(xw, xe, y - 2, y + 2)


def truck_park(c4, g, P, r):
    """Between the fuel farm and the heavy berth: a fenced lot where hauliers wait for their cargo,
    and the bonded stores along the tree line it serves."""
    x0, x1, y0, y1 = -270.0, -76.0, 628.0, 718.0
    c4.plane('Truck park', x0, x1, y0, y1, P.m['lot'], 0.06)
    # With PORT_ROADS the back road comes in at the west end: the gate is there, and the bays across
    # the way in to the aisle are left empty.
    fence_box(P, x0, x1, y0, y1, 'S', port_roads.TRUCK_PARK_GATE if port_roads.ON else (88.0, 104.0))
    ax0, ax1 = port_roads.TRUCK_PARK_AISLE
    # Three rows of articulated trucks, the first two back to back, nose out to an aisle.
    for row, (cab_y, h) in enumerate(((y0 + 8, -math.pi / 2), (y0 + 46, math.pi / 2), (y0 + 64, -math.pi / 2))):
        x = x0 + 8
        while x < x1 - 6:
            way_in = port_roads.ON and row < 2 and ax0 < x < ax1
            if not way_in:
                P.box('white line', (0.4, 17.0, 0.05), (x - 3.0, cab_y + (8.5 if h < 0 else -8.5), 0.1))
            if r.random() < 0.78:
                truck(Null(P) if way_in else P, r, x, cab_y, h)
            x += 6.0
    if port_roads.ON:
        GHOSTS.append(('lot', (ax0, ax1, y0 + 0.5, y0 + 49.0)))
    hut(P, r, x0 + 96, y0 + 4, 5.0, 4.0, 3.2, P.m['roofs'][2])      # the gate office
    for x in (x0 + 4, (x0 + x1) / 2, x1 - 4):
        light_mast(P, x, y1 - 4, 26.0)
    g.take(x0, x1, y0, y1)
    # Bonded stores: a long low warehouse row with docks on its south face.
    sx = x0
    while sx < x1 - 10:
        w = r.uniform(34, 52)
        w = min(w, x1 - sx)
        hh = r.uniform(8, 12)
        cx, cy, d = sx + w / 2, 740.0, 26.0
        P.box(r.choice(P.m['roofs']), (w - 3, d, hh), (cx, cy, hh / 2))
        c4.WAREHOUSES.append((cx, cy, w - 3, d, hh))
        sx += w
    c4.plane('Bonded stores apron', x0, x1, 718.0, 727.0, P.m['asphalt'], 0.07)
    g.take(x0, x1, 718.0, 754.0)


def west_depot(c4, g, P, r):
    """The west end behind the berths: the port's maintenance depot (a vehicle shed, parked ground
    equipment, scrap, drums) and a second, smaller tank farm, both fenced."""
    # Tank farm, north-west.
    tx0, tx1, ty0, ty1 = -520.0, -440.0, 690.0, 750.0
    P.box('bund', (tx1 - tx0, ty1 - ty0, 0.4), ((tx0 + tx1) / 2, (ty0 + ty1) / 2, 0.2))
    for i in range(3):
        for j in range(2):
            P.cyl('tank', 9.0, 11.0, (tx0 + 14 + i * 26, ty0 + 15 + j * 30, 0.3), 24)
    fence_box(P, tx0 - 3, tx1 + 3, ty0 - 3, ty1 + 3, 'S', (30.0, 40.0))
    g.take(tx0 - 3, tx1 + 3, ty0 - 3, ty1 + 3)
    # Pumps and a manifold between the two farms. With PORT_ROADS the fuel spur runs between them:
    # the pumps stand back from it and the manifold goes over it on two posts.
    px = -432.0 if port_roads.ON else -428.0
    for k in range(3):
        P.box('gse white', (3.0, 2.0, 2.0), (px, 662 + k * 8, 1.0))
    if not port_roads.ON:
        P.strut('pipe', (-440, 700, 1.5), (-410, 676, 1.5), 0.9)
        P.strut('pipe', (-440, 702, 1.2), (-410, 678, 1.2), 0.9)
    else:
        for dz, dy in ((0.0, 0.0), (0.5, 2.0)):
            a, b = (-440, 700 + dy), (-410, 676 + dy)
            up_a, up_b = (-432, 693.6 + dy), (-412, 677.6 + dy)
            P.strut('pipe', (*a, 1.5 - dz), (*up_a, 6.5 + dz), 0.9)
            P.strut('pipe', (*up_a, 6.5 + dz), (*up_b, 6.5 + dz), 0.9)
            P.strut('pipe', (*up_b, 6.5 + dz), (*b, 1.5 - dz), 0.9)
        for x, y in ((-431.0, 693.8), (-413.0, 679.4)):
            P.strut('steel', (x, y, 0), (x, y, 6.2), 0.5)
        GHOSTS.append(('apron', (-430.5, -425.5, 659.0, 681.0)))
    # The vehicle shed and its yard, south-west.
    dx0, dx1, dy0, dy1 = -524.0, -440.0, 590.0, 680.0
    ex = port_roads.DEPOT_EAST if port_roads.ON else dx1           # its east fence: in 6 m for the back road
    c4.plane('Depot yard', dx0, ex, dy0, dy1, P.m['gravel'], 0.06)
    w, d, hh = 46.0, 24.0, 11.0
    P.box(P.m['roofs'][1], (w, d, hh), (dx0 + 26, dy1 - 16, hh / 2))
    c4.WAREHOUSES.append((dx0 + 26, dy1 - 16, w, d, hh))
    for i in range(4):                                                  # equipment waiting for the shed
        park(c4, 'Tug depot %d' % i, c4.tug_parts(P.m['gse']), dx0 + 10 + i * 10, dy0 + 14, math.pi / 2)
    park(c4, 'Yard forklift depot', c4.forklift_parts(P.m['yellow'], P.m['modules'][0]), dx1 - 14, dy0 + 30, 0.0)
    park(c4, 'Flatbed depot', c4.flatbed_parts(P.m['cab'], P.m['steel'], P.m['stock']), dx0 + 18, dy0 + 34, 0.0)
    stair_truck(P, dx1 - 10, dy0 + 12, math.pi / 2)
    stair_truck(P, dx1 - 4 if ex == dx1 else dx1 - 16, dy0 + 12, math.pi / 2)
    for _ in range(26):                                                 # scrap and cast-offs in the corner
        size = (r.uniform(1.5, 5), r.uniform(1.5, 4), r.uniform(0.6, 2.2))
        sx, sy, sz, sh = r.uniform(dx1 - 22, dx1 - 6), r.uniform(dy1 - 20, dy1 - 6), r.uniform(0.3, 1.5), r.uniform(0, math.pi)
        P.box('scrap', size, (min(sx, ex - 3.5), sy, sz), sh)
    drums(P, r, dx0 + 52, dy0 + 40, 12)
    crate_stack(P, r, dx0 + 60, dy0 + 48, 3, 2)
    fence_box(P, dx0, ex, dy0, dy1, 'E', (10.0, 24.0))
    light_mast(P, ex - 3, dy0 + 3, 26.0)
    g.take(dx0, ex, dy0, dy1)
    if ex != dx1:
        GHOSTS.append(('apron', (ex + 0.5, dx1 + 1.0, dy0 - 1.0, dy1 + 1.0)))
    # The apron west of the first berth: a parked hauler line and a mast.
    for k in range(5):
        x, y = -505.0 + k * 0, 470.0 + k * 9
        if g.free(x - 18, x + 4, y - 2, y + 2):
            truck(P, r, x, y, 0.0)
            g.take(x - 18, x + 4, y - 2, y + 2)
        elif g.road_blocked:
            truck(Null(P), r, x, y, 0.0)
            g.take(x - 18, x + 4, y - 2, y + 2)
    for x, y in ((-490.0, 548.0), (-505.0, 420.0)):
        if g.free(x - 2, x + 2, y - 2, y + 2):
            light_mast(P, x, y)
            g.take(x - 2, x + 2, y - 2, y + 2)
    crate_stack(P, r, -520.0, 520.0, 4, 3)
    g.take(-521.0, -509.0, 519.0, 528.0)


def container_overflow(c4, g, P, r):
    """Between the heavy berth and the terminal, north of its road: boxes stacked three high with a
    reach stacker standing by, and a row of empties."""
    x0, x1, y0, y1 = 80.0, 176.0, 700.0, 752.0
    for row in range(4):
        y = y0 + 6 + row * 12
        x = x0 + 4
        while x < x1 - 12:
            if r.random() < 0.85:
                for lvl in range(r.randrange(1, 4)):
                    P.box(r.choice(P.m['containers']), (12, 2.4, 2.6), (x + 6, y + r.choice((-1.5, 1.5)), 1.3 + 2.6 * lvl))
            x += 12.6
    P.box(P.m['yellow'], (9.0, 4.0, 3.0), (x1 - 4, y0 - 2, 1.8))         # a reach stacker
    P.strut(P.m['yellow'], (x1 - 4, y0 - 2, 3.0), (x1 + 6, y0 - 2, 10.0), 1.2)
    g.take(x0, x1 + 8, y0 - 6, y1)
    # A row of offices along the terminal road's north side.
    for k in range(4):
        hut(P, r, 70.0 + k * 8, 700.0, 6.0, 3.0, 3.0)
    g.take(66.0, 104.0, 698.0, 702.0)


def terminal_lorries(c4, g, P, r):
    """The freight terminal's lorry park north of the shed, against the tree line."""
    x0, x1, y0, y1 = 182.0, 284.0, 702.0, 752.0
    c4.plane('Terminal lorry park', x0, x1, y0, y1, P.m['lot'], 0.06)
    for i, y in enumerate((y0 + 14, y0 + 36)):
        x = x0 + 6
        while x < x1 - 4:
            if r.random() < 0.8:
                truck(P, r, x, y, math.pi / 2 if i else -math.pi / 2)
            x += 6.5
    light_mast(P, x0 + 2, y0 + 2, 26.0)
    g.take(x0, x1, y0, y1)
    # Pallets and cages along the shed's west end.
    crate_stack(P, r, 166.0, 650.0, 2, 4)
    g.take(165.0, 172.0, 649.0, 660.0)


def arrivals(c4, g, P, r):
    """Around the arrivals hall: a car park east of the ferry, buses and a taxi rank at the hall's
    door, MuniSec on the kerb, a kiosk."""
    ox, oy = c4.OFFICE
    x0, x1, y0, y1 = 208.0, 268.0, 455.0, 526.0
    c4.plane('Arrivals car park', x0, x1, y0, y1, P.m['lot'], 0.06)
    for row in range(5):
        y = y0 + 5 + row * 14
        bays(P, x0 + 3, y - 3, 18, 3.0, 6.0)
        for k in range(18):
            if r.random() < 0.72:
                car(P, r, x0 + 4.5 + k * 3.0, y, math.pi / 2 + r.uniform(-0.04, 0.04))
    for x in (x0 + 2, x1 - 2):
        light_mast(P, x, y1 - 2, 18.0)
    g.take(x0, x1, y0, y1)
    # Bus bays on the hall's east side, buses in them.
    bx = ox + 26
    for k in range(3):
        y = oy - 12 + k * 9
        P.box('white line', (14.0, 0.5, 0.05), (bx + 7, y - 4.2, 0.12))
        if k < 2:
            park(c4, 'Crew carrier arrivals %d' % k, c4.carrier_parts(P.m['gse white']), bx + 7, y, 0.0)
    g.take(bx, bx + 14, oy - 17, oy + 14)
    # MuniSec on the kerb at the hall's front; a taxi rank along the port road's verge.
    for k in range(2):
        park(c4, 'MuniSec car arrivals %d' % k, c4.car_parts(bpy.data.materials['Customs white']),
             ox - 8 + k * 7, oy - 26, 0.0)
    for k in range(6):
        car(P, r, ox - 50 + k * 6, 386.0, 0.0)
    P.box('white line', (40.0, 0.6, 0.05), (ox - 35, 383.0, 0.12))
    hut(P, r, ox + 30, oy + 22, 4.0, 3.0, 3.0, P.m['orange'])          # kiosk
    g.take(ox - 54, ox - 14, 383.0, 389.0)
    # Customs' inspection pound between the ferry and the terminal: freight held for a look, a
    # booth, a covered inspection bay.
    x0, x1, y0, y1 = 208.0, 268.0, 534.0, 610.0
    c4.plane('Customs pound', x0, x1, y0, y1, P.m['lot'], 0.06)
    fence_box(P, x0, x1, y0, y1, 'W', (30.0, 46.0))
    for k in range(5):
        y = y0 + 8 + k * 8
        truck(P, r, x0 + 24, y, 0.0)
        P.box('white line', (22.0, 0.5, 0.05), (x0 + 14, y - 4.0, 0.1))
    P.box(P.m['roofs'][2], (20.0, 18.0, 7.0), (x1 - 12, y0 + 22, 3.5))   # the inspection shed
    c4.WAREHOUSES.append((x1 - 12, y0 + 22, 20.0, 18.0, 7.0))
    hut(P, r, x0 + 6, y0 + 56, 4.0, 3.0, 3.0, P.m['orange'])
    crate_stack(P, r, x0 + 30, y1 - 14, 6, 3)
    for k in range(3):
        P.box(P.m['containers'][k + 1], (12, 2.4, 2.6), (x1 - 10, y1 - 24 - k * 3.4, 1.3))
    light_mast(P, x1 - 3, y1 - 3, 22.0)
    g.take(x0, x1, y0, y1)


def belt_ends(c4, g, P, r):
    """The dirt past each end of the container yard: overflow stacks, a haulage depot, skips."""
    # East: a haulage depot on the open ground below the jungle.
    x0, x1, y0, y1 = 606.0, 740.0, 216.0, 300.0
    c4.plane('Haulage depot', x0, x1, y0, y1, P.m['gravel'], 0.065)
    for cab_y, h in ((y0 + 6, -math.pi / 2), (y0 + 44, math.pi / 2)):     # back to back
        x = x0 + 8
        while x < x1 - 32:
            if r.random() < 0.75:
                truck(P, r, x, cab_y, h)
            x += 6.5
    for row in range(2):                                             # empties stacked at the back
        y = y1 - 26 + row * 12
        x = x0 + 6
        while x < x1 - 36:
            if r.random() < 0.8:
                for lvl in range(r.randrange(1, 4)):
                    P.box(r.choice(P.m['containers']), (12, 2.4, 2.6), (x + 6, y, 1.3 + 2.6 * lvl))
            x += 12.6
    w, d, hh = 26.0, 40.0, 9.0                                        # the depot's workshop
    P.box(P.m['roofs'][0], (w, d, hh), (x1 - 16, y0 + 24, hh / 2))
    c4.WAREHOUSES.append((x1 - 16, y0 + 24, w, d, hh))
    fence_box(P, x0, x1, y0, y1, 'W', (40.0, 56.0))
    light_mast(P, x0 + 3, y1 - 3, 24.0)
    light_mast(P, x1 - 3, y0 + 3, 24.0)
    g.take(x0, x1, y0, y1)
    # West: container overflow and skips on the dirt past the yard's west end.
    x0, x1, y0, y1 = -760.0, -608.0, 216.0, 330.0
    c4.plane('West overflow', x0, x1, y0, y1, P.m['gravel'], 0.065)
    for row in range(6):
        y = y0 + 8 + row * 18
        x = x0 + 6
        while x < x1 - 14:
            if r.random() < 0.8:
                for lvl in range(r.randrange(1, 4)):
                    P.box(r.choice(P.m['containers']), (12, 2.4, 2.6), (x + 6, y + r.choice((-2, 2)), 1.3 + 2.6 * lvl))
            x += 12.6
    for k in range(6):
        P.box(P.m['containers'][k % 6], (6, 2.4, 1.8), (x1 - 6, y0 + 10 + k * 8, 0.9))   # skips
    fence_box(P, x0, x1, y0, y1, 'E', (50.0, 66.0))
    light_mast(P, x1 - 3, y0 + 3, 24.0)
    g.take(x0, x1, y0, y1)


def apron(c4, g, P, r):
    """Paint and weather on the open apron: an edge line along the port road, light masts on a
    grid where nothing else stands, and puddles in the low spots after the rain."""
    P.box('white line', (c4.GATE[0] + 700 - 10, 0.8, 0.05), ((-700 + c4.GATE[0] - 10) / 2, 383.5, 0.1))
    P.box('paint', (c4.GATE[0] + 700 - 10, 0.6, 0.05), ((-700 + c4.GATE[0] - 10) / 2, 385.0, 0.1))
    for ln in LANES:                                       # cargo lanes: a yellow edge line each side
        x0, x1, y0, y1 = ln
        if x1 - x0 == 22.0 and y0 == 342.0 and y1 > 400:
            for x in (x0 + 2.5, x1 - 2.5):
                P.box('paint', (0.6, y1 - 390.0, 0.05), (x, (y1 + 390.0) / 2, 0.13))
    masts = 0
    for x in range(-500, 280, 90):
        for y in (455.0, 600.0):
            px, py = x + 45.0, y
            if g.free(px - 2, px + 2, py - 2, py + 2, 2.0):
                light_mast(P, px, py)
                g.take(px - 1.5, px + 1.5, py - 1.5, py + 1.5)
                masts += 1
    n = 0
    for _ in range(400):
        if n >= 48:
            break
        x, y = r.uniform(-524, 286), r.uniform(384, 756)
        size = r.uniform(6.0, 18.0)
        if g.free(x - size, x + size, y - size, y + size, ghost=False) or g.road_blocked or r.random() < 0.15:
            if any(x0 <= x <= x1 and y0 <= y <= y1 for x0, x1, y0, y1 in LANES) and r.random() < 0.5:
                continue
            puddle(P, r, x, y, size)
            n += 1
    print('PORT_DETAIL masts=%d puddles=%d' % (masts, n), flush=True)
