"""Greywater Down's public port at work, through C4's sky pan and on through C5's rise.

Kilo Nine goes to work once Second Wind is down; this module makes the rest of the port look as
busy, and keeps it moving after C4 ends: C5 plays on from C4's last frame (`c5_scene.py`) and rises
to about 1.8 km over the whole port for its title, so everything here is keyed through `end_frame()`, C5's
last frame, and the port traffic `c4x_scene.py` already runs is carried on to it (`carry_on`).

    build():  c4x_scene.build() calls add(c4) after the port and its hulls are built, before the
              farm and the paint (so the farm's and the vehicle sheets paint what this adds).
    report:   blender --background --factory-startup --python port_life.py -- report
              builds the scene and prints how many vehicle-frames end up inside something.

What is at work:
  traffic     vans, box trucks, container haulers and tankers on the port road, the cross streets
              and the strip between the warehouse rows; a MuniSec patrol car among them.
  container   the four yard gantries: trolleys running, boxes lifted off stacks and set down on
  yard        others, and at the gantry over aisle -125 a loop of yard tractors, each one either
              having its box lifted off or a box set onto it.
  berths      forklifts running stores up the gaps beside the worked freighters, a second bowser
              coming in to the one fuelling, tugs with cargo dollies on the heavy berth road
              serving the lighter pads beside it.
  terminal    a loop between the ferry, the arrivals hall and the freight terminal: the port bus,
              baggage tugs, vans and trucks stopping at the terminal doors.
  belt        forklifts along the warehouse faces.
  lifters     a Hermes lifting off its pad late in C4 and in C5 another coming down on that pad,
              each with its drive glow and a puff of apron dust.
  signs       amber beacons on the working vehicles, more steam off the belt's roofs.

With PORT_ROADS=1 (`port_roads`, `port-roads.md`) the traffic follows the road network by job: the
belt and port loops turn on the container yard's loading strip instead of the port road; the heavy
berth's tugs and dollies run a circuit down the heavy berth road, round pad 5's and pad 95's service
rings and back; and the port road carries trips (`TRIPS`): the second bowser on its way from the fuel
farm round pad 5's ring to the berth it fuels, and trucks to and from the truck park by the west
connector and the back road. Trips are simulated one after another, each yielding to those before it.

How the vehicles keep out of everything. Every road vehicle drives a fixed route: a lane's
centreline, rounded at the corners, laid on open road clear of anything standing (`ROUTES`
documents why each lane is where it is). On one route vehicles follow each other with a gap; two
of this module's routes never cross. What does cross them is the traffic that was already here:
the container carriers to and from the berths, and the customs car. Each such crossing is a zone,
the stretch of route that any of those vehicles ever covers; a vehicle may enter a zone only when
nothing will be in it for as long as the vehicle takes to clear it, and only when there is room
for the whole vehicle beyond it, so nobody ever stops inside one (`simulate`). `report()` checks
the outcome against everything in the scene: every moving footprint against every other, and
against the outline of every standing thing at knee and chest height.
"""
import math
import os
import random
import sys
from pathlib import Path

import bmesh
import bpy
import numpy as np
from mathutils import Matrix, Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
import port_roads  # noqa: E402

HERE = Path(__file__).resolve().parent
C5_LENGTH = 40.5            # C5 (40 s with its title) and a little over; it starts on C4's last frame
KEY_STEP = 2                # frames between baked keys
HAZE_SKIP = {'Port beacon', 'Lifter plume'}

c4 = None                   # c4x_scene, set by add()


def end_frame():
    return c4.FRAMES - 1 + round(C5_LENGTH * c4.FPS)


# ---------------------------------------------------------------- animation plumbing

def fcurves(ob):
    ad = ob.animation_data
    if ad is None or ad.action is None:
        return []
    act = ad.action
    out = list(getattr(act, 'fcurves', []) or [])
    for layer in getattr(act, 'layers', []):
        for strip in layer.strips:
            for bag in strip.channelbags:
                out += list(bag.fcurves)
    return out


def linear(ob, extrapolate=False):
    for fc in fcurves(ob):
        for k in fc.keyframe_points:
            k.interpolation = 'LINEAR'
        if extrapolate:
            fc.extrapolation = 'LINEAR'


def bake(ob, frames, cols):
    """Key `ob` at `frames` from per-frame arrays: cols maps (data_path, index) to values. Every
    key is linear, so the motion is exactly what was simulated."""
    ob.animation_data_clear()
    frames = np.asarray(frames, float)
    for (path, i), vals in cols.items():
        prop = getattr(ob, path)
        prop[i] = float(vals[0])
        ob.keyframe_insert(path, index=i, frame=frames[0])
    for fc in fcurves(ob):
        vals = np.asarray(cols[(fc.data_path, fc.array_index)], float)
        kp = fc.keyframe_points
        kp.add(len(frames) - 1)
        co = np.empty(2 * len(frames))
        co[0::2], co[1::2] = frames, vals
        kp.foreach_set('co', co)
        for k in kp:
            k.interpolation = 'LINEAR'
        fc.update()


def bake_pose(ob, frames, x, y, z, h, scale=None):
    cols = {('location', 0): x, ('location', 1): y, ('location', 2): z, ('rotation_euler', 2): np.unwrap(h)}
    if scale is not None:
        for i in range(3):
            cols[('scale', i)] = scale[i]
    bake(ob, frames, cols)


def track(ob, frames):
    """(F, 4) x, y, z, heading of `ob` at each frame, read off its curves."""
    out = np.tile([ob.location.x, ob.location.y, ob.location.z, ob.rotation_euler.z], (len(frames), 1))
    for fc in fcurves(ob):
        if fc.data_path == 'location':
            col = fc.array_index
        elif fc.data_path == 'rotation_euler' and fc.array_index == 2:
            col = 3
        else:
            continue
        out[:, col] = [fc.evaluate(f) for f in frames]
    return out


def extent(root):
    """(x0, x1, y0, y1, z0, z1) of a vehicle root's meshes in its own frame."""
    pts = []
    for o in root.children_recursive:
        if o.type != 'MESH':
            continue
        m = o.matrix_parent_inverse @ o.matrix_basis
        if o.parent is not root:
            m = o.parent.matrix_basis @ m
        pts += [m @ v.co for v in o.data.vertices]
    if not pts:
        return None
    a = np.array([tuple(p) for p in pts])
    return a[:, 0].min(), a[:, 0].max(), a[:, 1].min(), a[:, 1].max(), a[:, 2].min(), a[:, 2].max()


# ---------------------------------------------------------------- vehicles

def mat(name, rgb, rough=0.6, metal=0.0):
    return bpy.data.materials.get(name) or c4.material(name, rgb, rough, metal)


def make(name, parts):
    """A vehicle root with its parts, unkeyed (c4x_scene's builder, keys cleared)."""
    root = c4.vehicle(name, parts, [(0, 0.0, 0.0)])
    root.animation_data_clear()
    return root


def dims(parts):
    """front, rear (metres ahead of and behind the root along +x) and half width of a part list."""
    xs, ys = [], []
    for size, centre, _ in parts:
        if size[0] == 'tube':
            hx, hy = size[2] / 2, size[1]
        elif size[0] == 'cyl':
            hx = hy = size[1]
        else:
            hx, hy = size[0] / 2, size[1] / 2
        xs += [centre[0] - hx, centre[0] + hx]
        ys += [abs(centre[1]) + hy]
    return max(xs), -min(xs), max(ys)


def beacon_material():
    m = bpy.data.materials.get('Port beacon')
    if m is not None:
        return m
    m = c4.material('Port beacon', (1.0, 0.6, 0.2))
    p = m.node_tree.nodes['Principled BSDF']
    p.inputs['Emission Color'].default_value = (1.0, 0.6, 0.18, 1)
    sock = p.inputs['Emission Strength']
    for f in range(0, end_frame() + 1, 5):          # turning: bright, dim
        sock.default_value = 90.0 if (f // 5) % 2 else 6.0
        sock.keyframe_insert('default_value', frame=f)
    return m


def with_beacon(parts, x, z, w=0.6):
    """A part list with an amber beacon on the roof; a part named for it is left out of paint."""
    return parts + [((0.5, w, 0.35), (x, 0, z), beacon_material())]


def yard_tractor_parts(cab, chassis, box=None):
    """A terminal tractor under a container chassis: front toward +x, the box over the rear."""
    parts = [((3.0, 2.8, 2.4), (4.6, 0, 1.9), cab),                 # the tractor's cab
             ((13.5, 2.5, 0.5), (-0.8, 0, 1.1), chassis)            # tractor frame and skeletal chassis
             ] + c4.wheels((5.0, -3.8, -6.0), 1.05, 0.5, 0.45)
    if box is not None:
        parts.append(((12.0, 2.44, 2.6), (-1.6, 0, 2.65), box))
    return parts


def container_truck_parts(cab, chassis, box):
    """A rigid truck with a 20-foot box: short enough for the loops' corners, where the belt's
    articulated haulers (on the straight strip) would swing their trailers across the next lane."""
    return [((2.4, 2.5, 2.6), (3.7, 0, 1.7), cab),
            ((7.2, 2.4, 0.5), (-0.8, 0, 1.05), chassis),
            ((6.06, 2.44, 2.6), (-1.4, 0, 2.6), box)] + c4.wheels((3.7, -2.4, -3.6), 1.05, 0.5, 0.45)


def baggage_tug_parts(paint):
    return [((3.4, 1.8, 1.0), (0, 0, 0.8), paint),                   # body
            ((1.4, 1.7, 0.1), (-0.5, 0, 2.0), paint)                # canopy over the seat
            ] + c4.wheels((-1.1, 1.1), 0.75, 0.32, 0.3)


def dolly_parts(deck, load):
    return [((3.2, 2.0, 0.3), (0, 0, 0.65), deck),
            ((2.6, 1.7, 1.6), (0, 0, 1.6), load)] + c4.wheels((-1.1, 1.1), 0.85, 0.28, 0.25)


def bus_parts(paint):
    return [((12.0, 2.6, 2.6), (0, 0, 1.8), paint),
            ((11.0, 2.3, 0.3), (-0.3, 0, 3.25), paint)] + c4.wheels((-4.0, 4.2), 1.15, 0.5, 0.4)


# ---------------------------------------------------------------- routes

class Route:
    """A lane's centreline through `pts`, corners rounded to `radius`, sampled every `step` m."""

    def __init__(self, name, pts, closed=True, radius=8.0, step=0.5):
        self.name, self.closed = name, closed
        pts = [np.array(p, float) for p in pts]
        n = len(pts)
        out = []
        corners = range(n) if closed else range(1, n - 1)
        start = None if closed else pts[0]
        for i in corners:
            a, b, c = pts[i - 1], pts[i], pts[(i + 1) % n]
            d0, d1 = (b - a) / np.linalg.norm(b - a), (c - b) / np.linalg.norm(c - b)
            turn = math.atan2(d0[0] * d1[1] - d0[1] * d1[0], d0 @ d1)
            t = min(radius * abs(math.tan(turn / 2)), np.linalg.norm(b - a) / 2.01, np.linalg.norm(c - b) / 2.01)
            r = t / max(abs(math.tan(turn / 2)), 1e-9)
            p0, p1 = b - d0 * t, b + d1 * t
            if start is not None:
                out += self._line(start, p0, step)
            if abs(turn) > 1e-6:
                normal = np.array([-d0[1], d0[0]]) * (1 if turn > 0 else -1)
                centre = p0 + normal * r
                a0 = math.atan2(*(p0 - centre)[::-1])
                k = max(2, int(abs(turn) * r / step))
                out += [centre + r * np.array([math.cos(a0 + turn * j / k), math.sin(a0 + turn * j / k)])
                        for j in range(k)]
            start = p1
        if closed:
            out += self._line(start, out[0], step)
        else:
            out += self._line(start, pts[-1], step) + [pts[-1]]
        P = np.array(out)
        seg = np.linalg.norm(np.diff(np.vstack([P, P[:1]]) if closed else P, axis=0), axis=1)
        keep = np.concatenate([[True], seg[:len(P) - 1] > 1e-6])
        self.P = P[keep]
        seg = np.linalg.norm(np.diff(np.vstack([self.P, self.P[:1]]) if closed else self.P, axis=0), axis=1)
        self.s = np.concatenate([[0.0], np.cumsum(seg)])[:len(self.P)]
        self.L = float(self.s[-1] + (seg[-1] if closed else 0.0))
        d = np.diff(np.vstack([self.P, self.P[:1]]) if closed else np.vstack([self.P, 2 * self.P[-1] - self.P[-2]]), axis=0)
        self.h = np.unwrap(np.arctan2(d[:, 1], d[:, 0]))

    @staticmethod
    def _line(a, b, step):
        n = max(1, int(np.linalg.norm(b - a) / step))
        return [a + (b - a) * j / n for j in range(n)]

    def wrap(self, s):
        return np.mod(s, self.L) if self.closed else np.clip(s, 0.0, self.L)

    def at(self, s):
        """x, y, heading at arc length(s) s."""
        s = self.wrap(np.asarray(s, float))
        if self.closed:
            sx = np.concatenate([self.s, [self.L]])
            Px = np.vstack([self.P, self.P[:1]])
            hx = np.concatenate([self.h, [self.h[0] + 2 * math.pi * round((self.h[-1] - self.h[0]) / (2 * math.pi))]])
        else:
            sx, Px, hx = self.s, self.P, self.h
        x = np.interp(s, sx, Px[:, 0])
        y = np.interp(s, sx, Px[:, 1])
        i = np.clip(np.searchsorted(sx, s, side='right') - 1, 0, len(self.h) - 1)
        return x, y, self.h[i]

    def index(self, s):
        return np.clip(np.searchsorted(self.s, self.wrap(s)), 0, len(self.s) - 1)

    def nearest_s(self, x, y):
        return float(self.s[np.argmin(np.hypot(self.P[:, 0] - x, self.P[:, 1] - y))])


# The lanes, world metres. Each is a lane centre; vehicles are up to 3 m wide.
#  belt-east-outer / -inner: two concentric loops in opposite directions round the east block of
#    the container yard: up cross street 300 and down cross street 0 (the slots there are left
#    empty, about +-16 m clear), along the port road (366-378) and back along the open strip
#    between the second warehouse row (front face y 197) and the yard's south row (y 222). The
#    outer corners are wide (17 m) to clear Nessa's open gate leaf at x 295, y 375-384.
#  port-west / port-mid: loops along the port road's south lane and back along the loading strip at
#    y 354, clear of the carriers that stop at y 345 (their ends reach 351.3). The west one crosses
#    the carrier lanes from berth -400 twice, so it has zones there; the two turn short of the busy
#    lanes from berth -310 (x -312..-297), which leave no gap long enough to cross from a stop.
#  yard: a loop under the gantry at x -120: up aisle -125, along the clear band north of the last
#    row (y 318-336), down aisle -215 (neither aisle has carriers: their berths are lifting and
#    empty), back along the south strip west of the belt loops.
#  terminal: the apron between the arrivals hall (north face y 432), the ferry's covered walk
#    (x 175-180) and the freight terminal (doors at y 645), kept east of the steel stock at x 168-192
#    and south of the parked terminal truck (y 628-645).
#  heavy: a narrow loop on the heavy berth's road (x 43-57) from the lighter pads up to the staging.
#  strip-east / -west: through traffic on the strip between the warehouse rows (y 87-113).
ROUTES = {
    'belt-east-outer': dict(pts=[(303, 203), (303, 374), (-3, 374), (-3, 203)], radius=17),
    'belt-east-inner': dict(pts=[(3, 209), (3, 368), (297, 368), (297, 209)], radius=11),
    'port-west': dict(pts=[(-590, 354), (-590, 368), (-326, 368), (-326, 354)], radius=6.5),
    'port-mid': dict(pts=[(-284, 354), (-284, 368), (-150, 368), (-150, 354)], radius=6.5),
    'yard': dict(pts=[(-125, 207), (-125, 327), (-215, 327), (-215, 207)], radius=6.5),
    'terminal': dict(pts=[(278, 445), (278, 622), (198, 622), (198, 445)], radius=8),
    'heavy': dict(pts=[(46, 392), (46, 598), (54, 598), (54, 392)], radius=3.9),
    'strip-east': dict(pts=[(-1150, 96), (1150, 96)], closed=False),
    'strip-west': dict(pts=[(1150, 104), (-1150, 104)], closed=False),
}


# PORT_ROADS: the loops off the port road onto the loading strip (keeping right: east along y 341
# and west along 354, or 348 and 354 for the two belt loops), and the heavy berth's tugs on a circuit
# down the heavy berth road, west along the port road, clockwise round pad 5's ring, east, round pad
# 95's and back up: in at each ring's west link, out at its east one.
def _lighters():
    R = port_roads
    pts = [(46.0, 598.0), (46.0, R.LANE_W)]
    for cx, back in ((5.0, R.LANE_E), (95.0, R.LANE_W)):
        pts.append((cx - R.LINK, pts[-1][1]))
        pts += R.ring_points(cx, 412.0, R.junction_angle(-1), R.junction_angle(1))
        pts.append((cx + R.LINK, back))
    pts += [(54.0, R.LANE_W), (54.0, 598.0)]
    return pts


if port_roads.ON:
    ROUTES.update({
        'belt-east-outer': dict(pts=[(303, 203), (303, 354), (-3, 354), (-3, 203)], radius=11),
        'belt-east-inner': dict(pts=[(3, 209), (3, 348), (297, 348), (297, 209)], radius=8),
        'port-west': dict(pts=[(-590, 354), (-590, 341), (-326, 341), (-326, 354)], radius=6.5),
        'port-mid': dict(pts=[(-284, 354), (-284, 341), (-150, 341), (-150, 354)], radius=6.5),
    })
    del ROUTES['heavy']
    ROUTES['lighters'] = dict(pts=_lighters(), radius=5.0)


def _tanker():
    """The second bowser's way: east along the port road, up pad 5's west link, clockwise round its
    ring to the top and north up the gap to berth -40 (`berth_work` had it appear in the gap)."""
    R = port_roads
    top = math.degrees(math.acos((-1.0 - 5.0) / R.RING_R))           # where the ring crosses x -1
    return ([(-600.0, R.LANE_E), (5.0 - R.LINK, R.LANE_E)]
            + R.ring_points(5.0, 412.0, R.junction_angle(-1), top) + [(-1.0, 511.0)])


# Trips (PORT_ROADS): open routes, one vehicle each, simulated in this order. `start` is where the
# vehicle is at frame 0; `go` the second it pulls away, or None if it is already driving. Lanes keep
# right: the back road's 2.25 m either side of its line, the connector's 3 m.
TRIPS = {} if not port_roads.ON else {
    # It starts east of berth -310's cargo lane, whose three carriers leave no gap to cross from a stop.
    'fuel': dict(kind='bowser', pts=_tanker(), start=(-285.0, 369.0), go=None, cruise=10.0,
                 name='Port bowser in -40'),
    'truck park in 1': dict(kind='container', pts=[(-435.75, 590.0), (-435.75, 605.75), (-257.75, 605.75),
                                                    (-257.75, 680.5), (-195.0, 680.5)],
                            start=(-420.0, 605.75), go=None, cruise=9.0, name='Container truck back road 0'),
    'truck park in 2': dict(kind='box', pts=[(-300.0, 375.0), (-542.0, 375.0), (-542.0, 581.75), (-435.75, 581.75),
                                             (-435.75, 605.75), (-257.75, 605.75), (-257.75, 680.5), (-212.0, 680.5)],
                            start=(-350.0, 375.0), go=None, cruise=10.0, name='Box truck back road 1'),
    'truck park out': dict(kind='container', pts=[(-150.0, 685.5), (-262.25, 685.5), (-262.25, 610.25),
                                                  (-440.25, 610.25), (-440.25, 586.25), (-548.0, 586.25),
                                                  (-548.0, 369.0), (-303.0, 369.0), (-303.0, 300.0)],
                           start=(-150.0, 685.5), go=4.0, cruise=10.0, name='Container truck back road 2'),
}


def route(name):
    r = dict(ROUTES[name])
    return Route(name, r.pop('pts'), closed=r.pop('closed', True), **r)


# ---------------------------------------------------------------- the traffic simulation

class Car:
    def __init__(self, name, parts, cruise, s, stops=(), body=None, crane=None, loaded=None):
        self.name, self.parts, self.cruise, self.s = name, parts, cruise, float(s)
        self.front, self.rear, self.half = dims(parts) if body is None else body
        self.v = 0.0
        self.stops = list(stops)            # (s on the route, dwell seconds or 'crane')
        self.crane = crane
        self.loaded = loaded
        self.served = -1e9                  # arc length (unwrapped) of the last stop served
        self.hold_until = -1
        self.committed = None
        self.log = []                       # (frame, s)
        self.events = []                    # crane stops: (arrive frame, depart frame, was loaded)


def movers_near(route_, movers, margin):
    lo, hi = route_.P.min(0) - margin, route_.P.max(0) + margin
    for m in movers:
        tr = m['track']
        if (tr[:, 0].max() + m['r'] < lo[0] or tr[:, 0].min() - m['r'] > hi[0]
                or tr[:, 1].max() + m['r'] < lo[1] or tr[:, 1].min() - m['r'] > hi[1]):
            continue
        yield m


def occupancy(route_, movers, half_width, F):
    """occ[f, i]: route sample i lies within half_width of some mover's footprint at frame f."""
    occ = np.zeros((F, len(route_.P)), bool)
    w = half_width + 0.6
    for m in movers_near(route_, movers, half_width + 2):
        tr = m['track'][:F]
        x0, x1, y0, y1, z0, z1 = m['ext']
        ground = tr[:, 2] + z0 < 4.5
        cx, cy = tr[:, 0], tr[:, 1]
        near = np.zeros(len(route_.P), bool)
        for k in range(0, F, 12):
            near |= np.hypot(route_.P[:, 0] - cx[k], route_.P[:, 1] - cy[k]) < m['r'] + w + 12
        idx = np.nonzero(near)[0]
        if not len(idx):
            continue
        P = route_.P[idx]
        c, s = np.cos(tr[:, 3])[:, None], np.sin(tr[:, 3])[:, None]
        dx, dy = P[None, :, 0] - cx[:, None], P[None, :, 1] - cy[:, None]
        u, v = dx * c + dy * s, -dx * s + dy * c
        inside = (u >= x0 - w) & (u <= x1 + w) & (v >= y0 - w) & (v <= y1 + w) & ground[:, None]
        occ[:, idx] |= inside
    return occ


def zones_of(route_, occ, pad=1.0):
    """(s0, s1, i0, i1) stretches of the route any of the others ever cover."""
    ever = occ.any(axis=0)
    out = []
    i = 0
    n = len(ever)
    while i < n:
        if ever[i]:
            j = i
            while j + 1 < n and ever[j + 1]:
                j += 1
            out.append([route_.s[i] - pad, route_.s[j] + pad, i, j])
            i = j + 1
        else:
            i += 1
    if route_.closed and len(out) > 1 and ever[0] and ever[-1]:   # one zone across the start
        last = out.pop()
        out[0] = [last[0] - route_.L, out[0][1], last[2], out[0][3]]
    return out


def crossing_time(d, v0, vmax, a):
    """Seconds to cover d metres from v0, accelerating at a up to vmax."""
    run_up = max(0.0, (vmax ** 2 - v0 ** 2) / (2 * a))
    if d <= run_up:
        return (math.sqrt(v0 ** 2 + 2 * a * d) - v0) / a
    return (vmax - v0) / a + (d - run_up) / vmax


def simulate(route_, cars, occ, F, cranes=None, gap=4.0, accel=1.6, decel=3.5):
    """Drive `cars` along `route_` for F frames: following with a gap, stopping where told to,
    and entering a zone only when it will stay clear while they cross it and there is room
    beyond it (`zones_of`). Arc lengths are kept unwrapped; a loop wraps them when sampled."""
    dt = 1.0 / c4.FPS
    zones = zones_of(route_, occ)
    L = route_.L
    closed = route_.closed
    look = 70.0

    def free(z, f, t):
        i0, i1 = z[2], z[3]
        f1 = min(F, f + int(math.ceil(t * c4.FPS)) + 1)
        if i0 <= i1:
            return not occ[f:f1, max(0, i0 - 4):i1 + 5].any()
        return not (occ[f:f1, i0 - 4:].any() or occ[f:f1, :i1 + 5].any())

    def next_entry(z, s):
        """Unwrapped arc length of zone z's start at or ahead of s (on an open route, its start)."""
        if not closed:
            return z[0]
        return z[0] + L * math.ceil((s - z[0]) / L)

    for car in cars:                                   # a car standing in a zone at the start owns it
        car.owned = []
        for z in zones:
            starts = [next_entry(z, car.s) - L, next_entry(z, car.s)] if closed else [z[0]]
            for start in starts:
                if start - car.front < car.s < start + (z[1] - z[0]) + car.rear:
                    car.owned.append((z, start + (z[1] - z[0])))
    for f in range(F):
        for car in sorted(cars, key=lambda c: -c.s):
            limit = math.inf
            lead = None
            best = math.inf
            for o in cars:
                if o is car:
                    continue
                d = (o.s - car.s) % L if closed else o.s - car.s
                if 1e-6 < d < best:
                    best, lead = d, o
            if lead is not None:
                limit = min(limit, best - lead.rear - car.front - gap)
            car.owned = [(z, end) for z, end in car.owned if car.s - car.rear <= end]
            mine = {id(z) for z, _ in car.owned}
            front = car.s + car.front
            for z in sorted(zones, key=lambda z: next_entry(z, front) - front):
                start = next_entry(z, front)
                dz = start - front
                if id(z) in mine or dz < 0:
                    continue
                if dz > look:
                    break
                span = dz + (z[1] - z[0]) + car.front + car.rear + 2.0
                t = crossing_time(span, car.v, car.cruise, accel) + 1.0
                room = lead is None or best - lead.rear >= dz + (z[1] - z[0]) + 2 * car.front + car.rear + gap
                if dz < car.v ** 2 / (2 * decel) + 6 and free(z, f, t) and room:
                    car.owned.append((z, start + (z[1] - z[0])))
                else:
                    limit = min(limit, dz - 0.5)
                break
            for s_stop, dwell in car.stops:
                target = s_stop + (L * math.ceil((car.s - 1e-3 - s_stop) / L) if closed else 0.0)
                if target <= car.served + 1.0:
                    target += L if closed else math.inf
                d = target - car.s
                if 0 <= d < limit:
                    limit = d
                if abs(d) < 0.05 and car.v < 0.05 and car.hold_until < f:
                    if dwell == 'crane':
                        car.hold_until = cranes[car.crane].request(car, f)
                    else:
                        car.hold_until = f + round(dwell * c4.FPS)
                    car.served = target
            if car.hold_until >= f:
                car.v = 0.0
            else:
                vt = min(car.cruise, math.sqrt(2 * decel * max(0.0, limit)))
                car.v = min(max(vt, car.v - decel * dt * 1.5), car.v + accel * dt)
                car.v = max(0.0, min(car.v, max(0.0, limit) / dt))
                car.s += car.v * dt
            car.log.append(car.s)


def place(route_, cars, base_z=0.0, beacons=()):
    """Bake each simulated car's run onto a vehicle; a car with `train` pulls trailers behind."""
    F = len(cars[0].log)
    frames = list(range(0, F, KEY_STEP))
    if frames[-1] != F - 1:
        frames.append(F - 1)
    for car in cars:
        s = np.array(car.log)[frames]
        x, y, h = route_.at(s)
        root = make(car.name, car.parts)
        bake_pose(root, frames, x, y, np.full(len(frames), base_z), h)
        car.root = root
        for k, (tname, tparts, offset) in enumerate(getattr(car, 'train', ())):
            tx, ty, th = route_.at(s - offset)
            t = make(tname, tparts)
            bake_pose(t, frames, tx, ty, np.full(len(frames), base_z), th)


# ---------------------------------------------------------------- what was already moving

def carry_on():
    """C4's own port traffic keyed to C4's end: carry it on through C5."""
    end = end_frame()
    h = c4.ROW_HEADING
    n = 0
    for x, off, who in c4.BERTHS:
        cx, cy = x, c4.BERTH_Y + c4.BERTH_DY[x]
        s0 = c4.at(cx, cy, h, -(c4.BERTH_R + 4), 0)
        if who in ('hound', 'unloading', 'fuelling'):
            lats = (-5.0, 0.0, 5.0) if who == 'unloading' else (-4.0, 4.0)
            for k, lat in enumerate(lats):
                ob = bpy.data.objects.get('Carrier %d %d' % (n, k))
                if ob is not None:
                    shuttle(ob, (s0[0] + lat, s0[1]), (s0[0] + lat, 345.0), 9.0, 2.5, k / len(lats) + 0.17 * n, end)
        n += 1
    for name in [o.name for o in bpy.data.objects if o.name.startswith(('Hauler ', 'Freight train',
                                                                          'Second freight train', 'Road '))]:
        ob = bpy.data.objects[name]
        if ob.parent is None:
            linear(ob, extrapolate=True)
    # The freighter lifting off berth -130 climbs away faster once it is clear, and from C5's start
    # accelerates off along its bow (the row faces nearly north), out of the frame well before the
    # title, which has only the Colossus in the air; the Hermes lifting off pad -355 goes on climbing;
    # the one landing on pad -175 stays down.
    T = c4.DURATION
    lift = bpy.data.objects.get('Lifting at berth 3')
    if lift is not None:
        frames = list(range(0, end + 1, KEY_STEP))
        t = np.array(frames) / c4.FPS
        away = np.where(t > T, 0.5 * 3.0 * (t - T) ** 2, 0.0)                # m along its bow
        z = 11.0 + 4.0 * t + np.where(t > T, 0.6 * (t - T) ** 2, 0.0)
        hd = lift.rotation_euler.z
        x, y = lift.location.x + away * math.cos(hd), lift.location.y + away * math.sin(hd)
        bake_pose(lift, frames, x, y, z, np.full(len(t), hd))
    up = bpy.data.objects.get('Lighter 1 lifting')
    if up is not None:
        linear(up, extrapolate=True)


def shuttle(ob, a, b, speed, hold, phase, end):
    """Re-key a vehicle running back and forth between a and b (as c4x_scene.shuttle) to `end`."""
    dist = math.hypot(b[0] - a[0], b[1] - a[1])
    run = dist / speed
    period = 2 * (run + hold)
    frames = list(range(0, end + 1, KEY_STEP))
    ks = []
    for f in frames:
        u = (f / c4.FPS + phase * period) % period
        if u < hold:
            k = 0.0
        elif u < hold + run:
            k = (u - hold) / run
        elif u < 2 * hold + run:
            k = 1.0
        else:
            k = 1.0 - (u - 2 * hold - run) / run
        ks.append(k)
    ks = np.array(ks)
    hd = ob.rotation_euler.z
    bake_pose(ob, frames, a[0] + (b[0] - a[0]) * ks, a[1] + (b[1] - a[1]) * ks, np.zeros(len(ks)),
              np.full(len(ks), hd))


def fixed_movers(exclude=()):
    """Every vehicle already moving at ground level, with its footprint and per-frame track."""
    F = end_frame() + 1
    frames = list(range(F))
    out = []
    for ob in bpy.data.objects:
        if ob.parent is not None or ob.name in exclude or 'SECOND WIND' in ob.name or not fcurves(ob):
            continue
        if ob.type != 'EMPTY' or ob.instance_type == 'COLLECTION':
            continue
        ext = extent(ob)
        if ext is None:
            continue
        tr = track(ob, frames)
        r = math.hypot(max(abs(ext[0]), abs(ext[1])), max(abs(ext[2]), abs(ext[3])))
        out.append(dict(name=ob.name, ext=ext, track=tr, r=r))
    return out


# ---------------------------------------------------------------- the container yard

def stack_tops():
    """(cx, cy, top z) of every static container, read from the yard's container meshes."""
    out = []
    for i in range(6):
        ob = bpy.data.objects.get('Containers %d' % i)
        if ob is None:
            continue
        v = np.array([tuple(ob.matrix_world @ p.co) for p in ob.data.vertices])
        for k in range(0, len(v) - 7, 8):
            b = v[k:k + 8]
            out.append((b[:, 0].mean(), b[:, 1].mean(), b[:, 2].max()))
    return np.array(out)


class Crane:
    """One gantry: a trolley running along its portal, a spreader on cables, and the boxes it moves.
    `moves` are planned as (start frame, pick pose, drop pose, box); a pose is (x, y, top z of what
    the box sits on, heading). Poses are sampled per frame afterwards."""
    UP = 16.0            # the spreader's working height: a box's underside clears four high piles
    TRAVEL = 4.0         # m/s along the portal
    HOIST = 4.5          # m/s up and down

    def __init__(self, x, y, h):
        self.x, self.y, self.h = x, y, h
        self.ops = []                      # (t0, t1, phase function)
        self.busy_until = 0.0
        self.idle_x = x
        self.trucks = None

    def duration(self, pick, drop, from_x):
        a = abs(pick[0] - from_x) / self.TRAVEL
        down1 = (self.UP - (pick[2] + 2.6)) / self.HOIST
        down2 = (self.UP - (drop[2] + 2.6)) / self.HOIST
        b = abs(drop[0] - pick[0]) / self.TRAVEL
        return a, down1, b, down2

    def plan(self, t, pick, drop, box):
        """Move `box` from pick to drop starting at t (seconds); returns (lifted off, set down) times."""
        a, d1, b, d2 = self.duration(pick, drop, self.idle_x)
        t0 = t
        t1 = t0 + a                # over the pick
        t2 = t1 + d1               # down on it
        t3 = t2 + 1.0              # gripped
        t4 = t3 + d1               # back up with it
        t5 = t4 + max(b, 1.5)      # over the drop, turned
        t6 = t5 + d2               # down
        t7 = t6 + 1.0              # released
        t8 = t7 + d2               # up again
        self.ops.append(dict(t=(t0, t1, t2, t3, t4, t5, t6, t7, t8), x0=self.idle_x, pick=pick, drop=drop, box=box))
        self.idle_x = drop[0]
        self.busy_until = t8
        return t4, t6, t7

    def request(self, car, f):
        """A yard tractor has stopped under the gantry: lift its box off, or set one on it."""
        t = max(f / c4.FPS, self.busy_until)
        x, y, hd = self.trucks.at(car.s)
        deck = (x + math.cos(hd) * -1.6, y + math.sin(hd) * -1.6, 1.35, hd)   # the chassis bed under the box
        if car.loaded is not None:
            slot = min(self.slots, key=lambda s: s['top'] + 2.6 * len(s['boxes']))
            drop = (slot['x'], slot['y'], slot['top'] + 2.6 * len(slot['boxes']), 0.0)
            box = car.loaded
            box.segments.append(('truck_end', t))
            off, down, _ = self.plan(t, deck, drop, box)
            box.segments.append(('rest', down, drop))
            slot['boxes'].append(box)
            car.loaded = None
            car.events.append((f, round(off * c4.FPS) + 2, True))
            return round(off * c4.FPS) + 2
        slot = max((s for s in self.slots if s['boxes']), key=lambda s: len(s['boxes']), default=None)
        if slot is None:
            return f + 2 * c4.FPS
        box = slot['boxes'].pop()
        pick = (slot['x'], slot['y'], slot['top'] + 2.6 * len(slot['boxes']), 0.0)
        off, down, free = self.plan(t, pick, deck, box)
        box.segments.append(('truck', down, car))
        car.loaded = box
        car.events.append((f, round(free * c4.FPS) + 1, False))
        return round(free * c4.FPS) + 1


class Box:
    def __init__(self, name, material):
        self.name, self.material = name, material
        self.segments = []      # ('rest', t, pose) / ('truck', t, car) / ('truck_end', t)


def crane_pose(cr, t):
    """trolley x, spreader (x, y, z of the spreader's underside, heading) and the box it holds, at t."""
    op = None
    for o in cr.ops:
        if o['t'][0] <= t <= o['t'][8]:
            op = o
            break
    if op is None:
        prev = [o for o in cr.ops if o['t'][8] < t]
        x = prev[-1]['drop'][0] if prev else cr.ops[0]['x0'] if cr.ops else cr.x
        hd = prev[-1]['drop'][3] if prev else 0.0
        return x, (x, cr.y, cr.UP, hd), None
    t0, t1, t2, t3, t4, t5, t6, t7, t8 = op['t']
    p, d = op['pick'], op['drop']

    def lerp(a, b, k):
        k = min(1.0, max(0.0, k))
        k = k * k * (3 - 2 * k)
        return a + (b - a) * k
    if t < t1:
        x = lerp(op['x0'], p[0], (t - t0) / max(t1 - t0, 1e-6))
        return x, (x, cr.y, cr.UP, p[3]), None
    if t < t2:
        z = lerp(cr.UP, p[2] + 2.6, (t - t1) / max(t2 - t1, 1e-6))
        return p[0], (p[0], p[1], z, p[3]), None
    if t < t3:
        return p[0], (p[0], p[1], p[2] + 2.6, p[3]), op['box']
    if t < t4:
        z = lerp(p[2] + 2.6, cr.UP, (t - t3) / max(t4 - t3, 1e-6))
        return p[0], (p[0], p[1], z, p[3]), op['box']
    if t < t5:
        k = (t - t4) / max(t5 - t4, 1e-6)
        x = lerp(p[0], d[0], k)
        return x, (x, lerp(p[1], d[1], k), cr.UP, lerp(p[3], d[3], k)), op['box']
    if t < t6:
        z = lerp(cr.UP, d[2] + 2.6, (t - t5) / max(t6 - t5, 1e-6))
        return d[0], (d[0], d[1], z, d[3]), op['box']
    if t < t7:
        return d[0], (d[0], d[1], d[2] + 2.6, d[3]), None
    z = lerp(d[2] + 2.6, cr.UP, (t - t7) / max(t8 - t7, 1e-6))
    return d[0], (d[0], d[1], z, d[3]), None


def add_cranes():
    """The yard gantries at work. The portal stays where `add_yard` built it (and the paint shows
    it); its trolley is rebuilt as a moving part with a spreader on cables beneath."""
    yellow = bpy.data.materials['Crane yellow']
    steel = bpy.data.materials['Steel']
    old = bpy.data.objects.get('Yard cranes')
    if old is not None:
        bpy.data.objects.remove(old)
    xs, cy, h, span = (-380.0, -120.0, 150.0, 410.0), 280.0, 34.0, 44.0
    bm = bmesh.new()
    for x in xs:
        for dx in (-span / 2, span / 2):
            for dy in (-8, 8):
                c4.strut(bm, (x + dx, cy + dy, 0), (x + dx, cy + dy, h), 1.4)
            c4.strut(bm, (x + dx, cy - 8, h * 0.5), (x + dx, cy + 8, h * 0.5), 0.8)
        for dy in (-8, 8):
            c4.box(bm, (span + 6, 2.0, 2.4), (x, cy + dy, h))
    c4.mesh('Yard cranes', bm, yellow)
    tops = stack_tops()
    cmats = [bpy.data.materials['Container %d' % i] for i in range(6)]
    r = random.Random(41)
    cranes = []
    for ci, x in enumerate(xs):
        cr = Crane(x, cy, h)
        # Slots under the gantry in the row it straddles, far enough in that a box clears the legs.
        row = tops[(np.abs(tops[:, 1] - cy) < 4) & (np.abs(tops[:, 0] - x) < 20.5)] if len(tops) else []
        slots = {}
        for bx, by, bz in row:
            key = round((bx + 554) / 12.6)
            s = slots.setdefault(key, dict(x=-554 + 12.6 * key, y=by, top=0.0, boxes=[]))
            s['top'] = max(s['top'], bz)
        cr.slots = sorted(slots.values(), key=lambda s: s['x'])
        cranes.append(cr)
    boxes = []

    def loose(slot):
        b = Box('Yard box %d' % len(boxes), cmats[r.randrange(6)])
        b.segments.append(('rest', 0.0, (slot['x'], slot['y'], slot['top'] + 2.6 * len(slot['boxes']), 0.0)))
        slot['boxes'].append(b)
        boxes.append(b)
        return b
    for ci, cr in enumerate(cranes):
        if ci == 1 or len(cr.slots) < 2:
            continue
        # Restacking: a box at a time from the tallest loose pile to the lowest, back and forth.
        loose(cr.slots[0])
        loose(cr.slots[0])
        restack(cr, 1.0 + 3.0 * ci, r)
    loader = cranes[1]
    for s in loader.slots[:3]:
        loose(s)
    return cranes, boxes, loader


def restack(cr, t, r):
    """From t to the end of C5: a box at a time from the tallest loose pile to the lowest, back and forth."""
    T_END = end_frame() / c4.FPS
    while t < T_END:
        piles = [s for s in cr.slots if s['boxes']]
        if not piles or len(cr.slots) < 2:
            return
        src = max(piles, key=lambda s: (len(s['boxes']), -abs(s['x'] - cr.idle_x)))
        dst = min((s for s in cr.slots if s is not src), key=lambda s: (s['top'] + 2.6 * len(s['boxes']),
                                                                       abs(s['x'] - src['x'])))
        b = src['boxes'].pop()
        pick = (src['x'], src['y'], src['top'] + 2.6 * len(src['boxes']), 0.0)
        drop = (dst['x'], dst['y'], dst['top'] + 2.6 * len(dst['boxes']), 0.0)
        off, down, _ = cr.plan(t, pick, drop, b)
        b.segments.append(('rest', down, drop))
        dst['boxes'].append(b)
        t = cr.busy_until + r.uniform(2.0, 6.0)


def bake_cranes(cranes, boxes, truck_cars):
    F = end_frame() + 1
    frames = list(range(0, F, KEY_STEP))
    if frames[-1] != F - 1:
        frames.append(F - 1)
    t = np.array(frames) / c4.FPS
    yellow = bpy.data.materials['Crane yellow']
    steel = bpy.data.materials['Steel']
    holder = {}                       # box -> per-frame spreader pose while held
    for i, cr in enumerate(cranes):
        poses = [crane_pose(cr, tt) for tt in t]
        tx = np.array([p[0] for p in poses])
        bm = bmesh.new()
        c4.box(bm, (6, 18, 3), (0, 0, 0))
        trolley = c4.mesh('Crane trolley %d' % i, bm, yellow)
        bake_pose(trolley, frames, tx, np.full(len(t), cr.y), np.full(len(t), cr.h + 1.5), np.zeros(len(t)))
        sp = np.array([p[1] for p in poses])
        bm = bmesh.new()
        c4.box(bm, (12.2, 2.5, 0.5), (0, 0, 0.25))                    # the spreader frame
        spreader = c4.mesh('Crane spreader %d' % i, bm, yellow)
        bake_pose(spreader, frames, sp[:, 0], sp[:, 1], sp[:, 2], sp[:, 3])
        bm = bmesh.new()
        for cx_, cy_ in ((-4, -0.9), (4, -0.9), (-4, 0.9), (4, 0.9)):  # cables, unit length, scaled down
            c4.box(bm, (0.12, 0.12, 1.0), (cx_, cy_, -0.5))
        cables = c4.mesh('Crane cables %d' % i, bm, steel)
        length = np.maximum(0.1, cr.h - 0.0 - (sp[:, 2] + 0.5))
        bake_pose(cables, frames, sp[:, 0], sp[:, 1], np.full(len(t), cr.h), sp[:, 3],
                  scale=(np.ones(len(t)), np.ones(len(t)), length))
        for k, p in enumerate(poses):
            if p[2] is not None:
                holder.setdefault(p[2], {})[k] = p[1]
    cars = {c.name: c for c in truck_cars}
    for b in boxes:
        x, y, z, h = (np.zeros(len(t)) for _ in range(4))
        segs = sorted(b.segments, key=lambda s: s[1])
        held = holder.get(b, {})
        for k, tt in enumerate(t):
            if k in held:
                sx, sy, sz, sh = held[k]
                x[k], y[k], z[k], h[k] = sx, sy, sz - 1.3, sh
                continue
            cur = None
            for sg in segs:
                if sg[1] <= tt + 1e-6:
                    cur = sg
            if cur is None:
                cur = segs[0]
            if cur[0] == 'rest':
                px, py, pz, ph = cur[2]
                x[k], y[k], z[k], h[k] = px, py, pz + 1.3, ph
            elif cur[0] == 'truck':
                car = cur[2]
                rx, ry, rh = car.route.at(car.log[min(frames[k], len(car.log) - 1)])
                x[k], y[k], z[k], h[k] = rx - 1.6 * math.cos(rh), ry - 1.6 * math.sin(rh), 2.65, rh
            else:                               # 'truck_end' before the crane takes it: still aboard
                prev = [sg for sg in segs if sg[0] == 'truck' and sg[1] <= cur[1]]
                car = prev[-1][2] if prev else None
                if car is None:
                    x[k], y[k], z[k], h[k] = x[k - 1], y[k - 1], z[k - 1], h[k - 1]
                else:
                    rx, ry, rh = car.route.at(car.log[min(frames[k], len(car.log) - 1)])
                    x[k], y[k], z[k], h[k] = rx - 1.6 * math.cos(rh), ry - 1.6 * math.sin(rh), 2.65, rh
        bm = bmesh.new()
        c4.box(bm, (12.0, 2.44, 2.6), (0, 0, 0))
        ob = c4.mesh(b.name, bm, b.material)
        bake_pose(ob, frames, x, y, z, h)


# ---------------------------------------------------------------- the build

def fleet():
    """Paint and parts for the road vehicles, shared so a slot's shape repeats exactly."""
    import c4_farm as farm
    m = dict(
        cab=[mat('Road cab %d' % i, c, 0.5) for i, c in
             enumerate(((0.16, 0.30, 0.50), (0.70, 0.66, 0.58), (0.50, 0.12, 0.08), (0.20, 0.36, 0.18)))],
        box=mat('Box body', (0.84, 0.82, 0.76), 0.6),
        tank=mat('Road tanker', (0.70, 0.72, 0.72), 0.4, 0.4),
        load=bpy.data.materials['Hauler load'],
        container=[bpy.data.materials['Container %d' % i] for i in range(6)],
    )
    kinds = {
        'van': lambda r: farm.van_parts(c4, m['cab'][r.randrange(4)]),
        'box': lambda r: farm.box_truck_parts(c4, m['cab'][r.randrange(4)], m['box']),
        'hauler': lambda r: farm.hauler_parts(c4, m['cab'][r.randrange(4)], m['container'][r.randrange(6)]),
        'tanker': lambda r: farm.tanker_parts(c4, m['cab'][r.randrange(4)], m['tank']),
        'container': lambda r: container_truck_parts(m['cab'][r.randrange(4)], bpy.data.materials['Steel'],
                                                     m['container'][r.randrange(6)]),
        'patrol': lambda r: with_beacon(c4.car_parts(mat('MuniSec blue', (0.16, 0.22, 0.34), 0.4)), -0.2, 1.9, 1.5),
    }
    names = {'van': 'Road van', 'box': 'Box truck', 'hauler': 'Road hauler', 'tanker': 'Road tanker',
             'patrol': 'MuniSec patrol', 'container': 'Container truck'}
    return kinds, names


def road_cars(route_, plan, cruise, r, kinds, names, tag):
    """Cars for a loop, spaced evenly round it: plan is a list of kinds."""
    cars = []
    spacing = route_.L / len(plan)
    for k, kind in enumerate(plan):
        parts = kinds[kind](r)
        cars.append(Car('%s %s %d' % (names[kind], tag, k), parts, cruise * r.uniform(0.97, 1.0), k * spacing))
    return cars


def add(c4x):
    """Everything in the port that works and moves, outside Kilo Nine."""
    global c4
    c4 = c4x
    if str(HERE) not in sys.path:
        sys.path.insert(0, str(HERE))
    F = end_frame() + 1
    r = random.Random(77)
    carry_on()
    movers = fixed_movers()
    kinds, names = fleet()
    trips(kinds, movers, F)

    # Road loops.
    plans = {
        'belt-east-outer': (['container', 'box', 'container', 'patrol', 'box', 'container', 'van', 'container',
                             'box', 'container', 'van', 'box', 'container', 'box'], 11.0, 'belt outer'),
        'belt-east-inner': (['box', 'container', 'van', 'container', 'box', 'container', 'box', 'container',
                             'van', 'container', 'box', 'container'], 10.5, 'belt inner'),
        'port-west': (['container', 'box', 'van', 'container', 'box', 'container'], 10.0, 'port road'),
        'port-mid': (['box', 'container', 'van', 'container', 'box', 'container'], 9.0, 'port road mid'),
    }
    sims = []
    for name, (plan, cruise, tag) in plans.items():
        rt = route(name)
        cars = road_cars(rt, plan, cruise, r, kinds, names, tag)
        sims.append((rt, cars, 0.0))

    # The terminal loop: the port bus stopping at the hall and the ferry, baggage trains, a truck
    # and a van stopping at the terminal doors.
    rt = route('terminal')
    hall, ferry, doors = rt.nearest_s(240, 445), rt.nearest_s(198, 530), rt.nearest_s(232, 622)
    white = mat('Port bus white', (0.86, 0.86, 0.82), 0.5)
    orange = bpy.data.materials['Hegemony orange']
    bus = Car('Port bus 0', bus_parts(white) + [((11.0, 0.4, 0.05), (-0.3, 0, 3.42), orange)], 7.0, 0.0,
              stops=[(hall, 9.0), (ferry, 9.0)])
    tugp = mat('Baggage tug blue', (0.20, 0.34, 0.52), 0.5)
    deck = bpy.data.materials['Steel']
    terminal = [bus]
    for k in range(3):
        parts = with_beacon(baggage_tug_parts(tugp), -0.5, 2.15, 0.5)
        car = Car('Baggage tug %d' % k, parts, 6.0, rt.L * (0.22 + 0.22 * k), stops=[(ferry, 6.0), (hall, 6.0)])
        car.train = [('Cargo dolly %d %d' % (k, j), dolly_parts(deck, bpy.data.materials['Module %d' % ((k + j) % 4)]),
                      4.6 + 3.9 * j) for j in range(3)]
        car.rear = 4.6 + 3.9 * 2 + 1.6
        terminal.append(car)
    terminal.append(Car('Box truck terminal 0', kinds['box'](r), 7.0, rt.L * 0.75, stops=[(doors, 12.0)]))
    terminal.append(Car('Road van terminal 0', kinds['van'](r), 7.0, rt.L * 0.88, stops=[(doors, 8.0)]))
    terminal.append(Car('Road van terminal 1', kinds['van'](r), 7.0, rt.L * 0.12, stops=[(hall, 7.0)]))
    terminal.append(Car('Port bus 1', bus.parts, 7.0, rt.L * 0.56, stops=[(hall, 9.0), (ferry, 9.0)]))
    sims.append((rt, terminal, 0.0))

    # The heavy berth road: tugs with dollies between the staging and the lighter pads. With
    # PORT_ROADS they serve pads 5 and 95 from their rings, stopping on the far side of each.
    if port_roads.ON:
        rt = route('lighters')
        pads = [(rt.nearest_s(29, 412), 10.0), (rt.nearest_s(71, 412), 10.0)]
    else:
        rt = route('heavy')
        pads = [(rt.nearest_s(46, 412), 10.0)]
    heavy = []
    for k in range(3):
        parts = with_beacon(baggage_tug_parts(tugp), -0.5, 2.15, 0.5)
        car = Car('Baggage tug heavy %d' % k, parts, 5.0, rt.L * k / 3, stops=pads + [(rt.nearest_s(54 if port_roads.ON else 46, 590), 8.0)])
        car.train = [('Cargo dolly heavy %d %d' % (k, j), dolly_parts(deck, bpy.data.materials['Module %d' % j]),
                      4.6 + 3.9 * j) for j in range(2)]
        car.rear = 4.6 + 3.9 + 1.6
        heavy.append(car)
    sims.append((rt, heavy, 0.0))

    # The gantry over aisle -125 and its yard tractors.
    cranes, boxes, loader = add_cranes()
    rt = route('yard')
    loader.trucks = rt
    stop = rt.nearest_s(-125, 280 + 1.6)          # the box under the gantry's centre line
    tractor = mat('Yard tractor orange', (0.85, 0.45, 0.10), 0.5)
    yard = []
    cmats = [bpy.data.materials['Container %d' % i] for i in range(6)]
    for k in range(YARD_TRACTORS):
        b = None
        if k % 2 == 0:
            b = Box('Yard box truck %d' % k, cmats[r.randrange(6)])
            boxes.append(b)
        parts = with_beacon(yard_tractor_parts(tractor, deck), 4.6, 3.3)
        car = Car('Yard tractor %d' % k, parts, 7.5, stop - rt.L * k / YARD_TRACTORS,   # the first under the gantry
                  stops=[(stop, 'crane')], crane=0, loaded=b)
        car.route = rt
        if b is not None:
            b.segments.append(('truck', 0.0, car))
        yard.append(car)
    sims.append((rt, yard, 0.0))

    for rt, cars, z in sims:
        half = max(c.half for c in cars)
        occ = occupancy(rt, movers, half, F)
        for c in cars:
            c.route = rt
        simulate(rt, cars, occ, F, cranes={0: loader})
        place(rt, cars, z)
        if os.environ.get('PORT_LIFE_DEBUG'):
            for c in cars:
                lg = np.array(c.log)
                print('PORT_LIFE_DEBUG %s s0 %.0f s1 %.0f stood %.0f%% events %s' % (
                    c.name, lg[0], lg[-1], 100 * np.mean(np.diff(lg) < 1e-3), c.events), flush=True)
        stood = np.mean([np.mean(np.diff(c.log) < 1e-3) for c in cars])
        print('PORT_LIFE route %-16s %5.0f m  cars %d  zones %d  standing %.0f%%' % (
            rt.name, rt.L, len(cars), len(zones_of(rt, occ)), 100 * stood), flush=True)
    for cr in cranes:                           # once its last tractor is served, the loader restacks too
        restack(cr, cr.busy_until + r.uniform(2.0, 6.0), r)
    bake_cranes(cranes, boxes, yard)
    print('PORT_LIFE gantries: moves %s; tractor stops %s' % (
        [len(c.ops) for c in cranes], [len(c.events) for c in yard]), flush=True)

    strip_traffic(r, kinds, names, F)
    print('PORT_LIFE freight road trucks=%d' % freight_road(r, kinds, names, movers, F), flush=True)
    forklifts(r, F)
    berth_work(r, F)
    lifters(F)
    clear_sky()
    steam()
    print('PORT_LIFE vehicles=%d boxes=%d' % (sum(len(c) for _, c, _ in sims), len(boxes)), flush=True)


def trips(kinds, movers, F):
    """PORT_ROADS: each trip driven in turn on its open route, yielding at crossings to everything
    already moving, the trips before it included; each then joins `movers` for the ones after it."""
    r = random.Random(78)
    white = bpy.data.materials['Hauler load']
    for key, t in TRIPS.items():
        rt = Route(key, t['pts'], closed=False, radius=6.0)
        if t['kind'] == 'bowser':
            parts = with_beacon(c4.bowser_parts(white), 5.0, 3.55)
        else:
            parts = kinds[t['kind']](r)
        s0 = rt.nearest_s(*t['start'])
        car = Car(t['name'], parts, t['cruise'], s0, stops=[(rt.L, 1e6)])
        if t['go'] is None:
            car.v = t['cruise']
        else:
            car.hold_until = round(t['go'] * c4.FPS)
        car.route = rt
        occ = occupancy(rt, movers, car.half, F)
        simulate(rt, [car], occ, F)
        place(rt, [car])
        s = np.array(car.log)
        x, y, h = rt.at(s)
        movers.append(dict(name=car.name, ext=(-car.rear, car.front, -car.half, car.half, 0.0, 4.0),
                           track=np.stack([x, y, np.zeros(len(s)), h], axis=1),
                           r=math.hypot(max(car.front, car.rear), car.half)))
        stood = np.mean(np.diff(s) < 1e-3)
        print('PORT_LIFE trip %-16s %5.0f m  from %.0f m  zones %d  standing %.0f%%  at end %.0f m' % (
            key, rt.L, s0, len(zones_of(rt, occ)), 100 * stood, s[-1]), flush=True)


def strip_traffic(r, kinds, names, F):
    """Through traffic along the strip between the warehouse rows: steady streams each way, off
    either end of the belt."""
    frames = list(range(0, F, KEY_STEP))
    t = np.array(frames) / c4.FPS
    for name, plan, v, tag in (('strip-east', ['box', 'van', 'hauler', 'van', 'tanker', 'box', 'van', 'hauler', 'box', 'van'], 12.0, 'strip eastbound'),
                          ('strip-west', ['van', 'hauler', 'box', 'van', 'box', 'tanker', 'van', 'box', 'hauler', 'van'], 11.0, 'strip westbound')):
        rt = route(name)
        spacing = rt.L / len(plan)
        # Enough of them, queued up beyond the west end too, that the stream keeps coming the whole shot.
        n = len(plan) * math.ceil((rt.L + v * t[-1]) / rt.L)
        for k in range(n):
            kind = plan[k % len(plan)]
            s = k * spacing - v * t[-1] + r.uniform(-20, 20) + v * t
            x, y, h = rt.at(np.clip(s, 0.0, rt.L))
            off = (s < 0) | (s > rt.L - 5)
            root = make('%s %s %d' % (names[kind], tag, k), kinds[kind](r))
            z = np.where(off, -50.0, 0.0)          # not yet on the belt, or driven off its end: under the ground
            bake_pose(root, frames, x, y, z, h)


YARD_TRACTORS = 7
FREIGHT_LANES = ((-90.0, 18.0, 0.0), (-80.0, -16.0, math.pi))   # y, speed, heading: c4_farm's lanes
FREIGHT_X = (-2000.0, 2000.0)                                     # the freight road's ends
FREIGHT_MIX = ['hauler'] * 7 + ['container'] * 4 + ['tanker'] * 3 + ['box'] * 4 + ['van'] * 2


FREIGHT = []                         # this build's freight trucks, for after_farm
FARM_ROAD = ('Road hauler east 0', 'Road tanker east 1', 'Box truck east 2', 'Road van east 3',
             'Road hauler west 0', 'Road tanker west 1', 'Box truck west 2', 'Road van west 3',
             'Second freight train')     # c4_farm's, built after add() has run


def freight_clash(xs, sign, front, rear, m):
    """Whether a truck at xs (per frame) ever comes within 5 m of mover m on its lane."""
    mx = m['track'][:, 0]
    lo = np.minimum(xs - sign * rear, xs + sign * front)
    hi = np.maximum(xs - sign * rear, xs + sign * front)
    return bool(np.any((mx + m['r'] + 5 > lo) & (mx - m['r'] - 5 < hi)))


def after_farm(c4x):
    """Run once c4_farm has built its traffic: its freight-road trucks and second train drive on
    through C5 instead of stopping where C4 ends, and any freight truck of ours they would run into
    is taken off the road."""
    global c4
    c4 = c4x
    for name in FARM_ROAD:
        ob = bpy.data.objects.get(name)
        if ob is not None:
            linear(ob, extrapolate=True)
    movers = [m for m in fixed_movers() if m['name'] in FARM_ROAD]
    gone = 0
    for t in FREIGHT:
        for m in movers:
            if abs(m['track'][0, 1] - t['lane']) < 3 and freight_clash(t['xs'], t['sign'], t['front'], t['rear'], m):
                root = bpy.data.objects[t['name']]
                for o in [root] + list(root.children_recursive):
                    bpy.data.objects.remove(o, do_unlink=True)
                gone += 1
                break
    print('PORT_LIFE after farm: %d farm movers carried on, %d freight trucks cleared' % (len(movers), gone), flush=True)


def freight_road(r, kinds, names, movers, F):
    """Heavy freight both ways on the freight road, nose to tail at a steady 18 / 16 m/s, so the belt
    reads as industry from the top of the pan. A slot is skipped wherever one of the trucks already
    on the lane (c4x_scene's and c4_farm's, at their own speeds) would ever run into it."""
    frames = np.arange(0, F, KEY_STEP)
    t = frames / c4.FPS
    all_frames = np.arange(F)
    FREIGHT.clear()
    sizes = {}
    n = 0
    for lane, v, h in FREIGHT_LANES:
        there = [m for m in movers if abs(m['track'][0, 1] - lane) < 3 and abs(m['track'][-1, 1] - lane) < 3]
        sweep = abs(v) * t[-1]
        # Positions at t = 0, in the direction of travel: d is distance along the lane from its start.
        sign = 1.0 if v > 0 else -1.0
        start = FREIGHT_X[0] if v > 0 else FREIGHT_X[1]
        edge = -sweep - 40.0                            # the front of the last truck placed, as d
        count = 0
        while edge < FREIGHT_X[1] - FREIGHT_X[0]:
            kind = r.choice(FREIGHT_MIX)
            if kind not in sizes:                       # measured off the built model: dims() misreads some
                probe = make('Freight probe', kinds[kind](r))
                ext = extent(probe)
                sizes[kind] = (ext[1], -ext[0])
                for o in [probe] + list(probe.children_recursive):
                    bpy.data.objects.remove(o, do_unlink=True)
            front, rear = sizes[kind]
            d = edge + rear + r.uniform(9.0, 26.0)
            x0 = start + sign * d
            xs = x0 + v * all_frames / c4.FPS
            if any(freight_clash(xs, sign, front, rear, m) for m in there):
                edge += 12.0
                continue
            x = x0 + v * t
            on = (x > FREIGHT_X[0] + 20) & (x < FREIGHT_X[1] - 20)
            root = make('%s freight %s %d' % (names[kind], 'east' if v > 0 else 'west', count), kinds[kind](r))
            bake_pose(root, frames, x, np.full_like(x, lane), np.where(on, 0.0, -50.0), np.full_like(x, h))
            FREIGHT.append(dict(name=root.name, lane=lane, xs=xs, sign=sign, front=front, rear=rear))
            count += 1
            edge = d + front
        n += count
    return n


def forklifts(r, F):
    """Forklifts along the warehouse faces either side of the strip, running modules in and out."""
    paint = bpy.data.materials['Crane yellow']
    mods = [bpy.data.materials['Module %d' % i] for i in range(4)]
    n = 0
    blocks = ((-890, -320), (-280, -20), (20, 280), (320, 890))
    for y, hd in ((90.4, math.pi), (109.6, 0.0)):
        for xa0, xb0 in blocks:
            for half in range(2):                       # one to each half of a block's face
                xa = xa0 + (xb0 - xa0) * half / 2
                xb = xa + (xb0 - xa0) / 2
                x = r.uniform(xa + 4, xb - 36)
                if y > 100 and x < -625 and x + 30 > -690:   # the plant stands against this face
                    x = -625.0
                root = make('Yard forklift belt %d' % n, c4.forklift_parts(paint, mods[r.randrange(4)]))
                root.rotation_euler.z = hd
                shuttle(root, (x, y), (x + r.uniform(18, 30), y), 3.5, r.uniform(2, 5), r.random(), F - 1)
                n += 1


def berth_work(r, F):
    """Stores going up the gaps beside the worked freighters, and a second bowser coming in to the
    one fuelling."""
    paint = bpy.data.materials['Crane yellow']
    mods = [bpy.data.materials['Module %d' % i] for i in range(4)]
    h = c4.ROW_HEADING
    for x, off, who in c4.BERTHS:
        if who not in ('hound', 'unloading', 'fuelling'):
            continue
        cy = c4.BERTH_Y + c4.BERTH_DY[x]
        gx = x + 39.0
        if who == 'fuelling' and port_roads.ON:
            continue                                   # it drives in from the port road (`TRIPS`)
        if who == 'fuelling':
            white = bpy.data.materials['Hauler load']
            parts = with_beacon(c4.bowser_parts(white), 5.0, 3.55)
            root = make('Port bowser in %d' % int(x), parts)
            frames = list(range(0, F, KEY_STEP))
            t = np.array(frames) / c4.FPS
            k = np.clip((t - 3.0) / 9.0, 0, 1)
            k = k * k * (3 - 2 * k)
            ys = (cy - 60) + ((cy - 9) - (cy - 60)) * k
            bake_pose(root, frames, np.full(len(t), gx), ys, np.zeros(len(t)), np.full(len(t), h))
            continue
        root = make('Yard forklift berth %d' % int(x), c4.forklift_parts(paint, mods[r.randrange(4)]))
        root.rotation_euler.z = h
        shuttle(root, (gx, cy - 40), (gx, cy + 22), 3.0, r.uniform(3, 6), r.random(), F - 1)


def lifters(F):
    """A Hermes lifting off pad 95 late in C4 and in C5 another coming down on it, each with its
    drive glow and a puff of apron dust. The C5 departure over the title is the Colossus, which is
    port_fleet's."""
    hermes = bpy.data.objects.get('Lighter 6 parked')
    if hermes is None:
        return
    frames = list(range(0, F, KEY_STEP))
    t = np.array(frames) / c4.FPS
    T = c4.DURATION
    glow = mat('Lifter plume', (0.6, 0.8, 1.0))
    gp = glow.node_tree.nodes['Principled BSDF']
    gp.inputs['Emission Color'].default_value = (0.6, 0.8, 1.0, 1)
    gp.inputs['Emission Strength'].default_value = 40.0

    def craft(ob, z, size):
        x, y = np.full(len(t), ob.location.x), np.full(len(t), ob.location.y)
        hd = np.full(len(t), ob.rotation_euler.z) + 0.02 * np.sin(t * 0.6) * (z > z.min() + 1)
        bake_pose(ob, frames, x, y, z, hd)
        bm = bmesh.new()
        bmesh.ops.create_uvsphere(bm, u_segments=10, v_segments=6, radius=1.0)
        g = c4.mesh(ob.name + ' plume', bm, glow)
        on = (z > z.min() + 0.3) | (np.gradient(z) != 0)
        sc = np.where(on, 1.0, 0.001) * size
        bake_pose(g, frames, x, y, z - size * 0.8, hd, scale=(sc, sc, sc * 0.6))
    # Up from pad 95 at C4 9.5 s.
    k = np.clip(t - 9.5, 0, None)
    z = 2.5 + np.where(k < 18, 0.5 * 2.2 * k ** 2, 0.5 * 2.2 * 18 ** 2 + 2.2 * 18 * (k - 18))   # to 40 m/s
    craft(hermes, z, 2.2)
    dust(hermes.location.x, hermes.location.y, 9.4, 26.0, 18, 'Lift dust hermes')
    # Back down on it in C5: a second Hermes, in from 200 m at C5 10 s (under the camera's 220 m,
    # about 27 m/s at first and easing), down at C5 22 s.
    t0, t1 = T + 10.0, T + 22.0
    e = bpy.data.objects.new('Lighter 6 return', None)
    c4.link(e)
    e.instance_type, e.instance_collection, e.scale = 'COLLECTION', hermes.instance_collection, hermes.scale
    e.location = hermes.location.copy()
    e.rotation_euler = (0, 0, hermes.rotation_euler.z + 0.3)
    u = np.clip((t - t0) / (t1 - t0), 0, 1)
    z = np.where(t < t0, -60.0, 2.5 + 200.0 * (1 - u) ** 1.6)
    craft(e, z, 2.2)
    dust(e.location.x, e.location.y, t1 - 1.8, 26.0, 18, 'Lift dust hermes return')


CLIMBERS = ('Lighter 6 parked', 'Lighter 1 lifting')   # 'Lifting at berth 3' is port_fleet's to fly out
SKY_HIDE_Z = 300.0       # well above C5's 220 m camera, which holds from C5 4 s to 18 s
SKY_WINDOW = (4.0, 17.5)


def clear_sky():
    """The owner wants the Colossus the only ship in the air under the title, so each craft still
    climbing out of the port is put away once it is above C5's low camera, out of sight, before the
    rise could find it again."""
    C4E = c4.FRAMES - 1
    f0, f1 = (C4E + round(w * c4.FPS) for w in SKY_WINDOW)
    for name in CLIMBERS:
        ob = bpy.data.objects.get(name)
        if ob is None:
            continue
        zs = track(ob, list(range(f0, f1 + 1)))[:, 2]
        above = np.nonzero(zs > SKY_HIDE_Z)[0]
        f = f0 + int(above[0]) if len(above) else f1
        parts = [ob] + list(ob.children_recursive) + [o for o in bpy.data.objects if o.name.startswith(name + ' ')]
        for o in parts:
            for frame, hidden in ((0, False), (f, True)):
                o.hide_render = hidden
                o.keyframe_insert('hide_render', frame=frame)
            for fc in fcurves(o):
                if fc.data_path == 'hide_render':
                    for k in fc.keyframe_points:
                        k.interpolation = 'CONSTANT'
        print('PORT_LIFE sky: %s put away at C5 %.1f s, %.0f m up%s' % (
            name, (f - C4E) / c4.FPS, zs[f - f0], '' if len(above) else ' (BELOW the camera: may show)'), flush=True)


def dust(x, y, ts, reach, n, name):
    """Apron dust thrown out from under a lifter as it comes off or onto its pad."""
    m = bpy.data.materials.get('Landing dust')
    if m is None:
        return
    r = random.Random(hash(name) & 0xffff)
    F = end_frame() + 1
    for i in range(n):
        ang = r.uniform(0, 2 * math.pi)
        r0, r1 = reach * r.uniform(0.25, 0.4), reach * r.uniform(0.8, 1.3)
        t0 = ts + r.uniform(0.0, 1.6)
        life = r.uniform(3.0, 5.0)
        s0, s1 = reach * r.uniform(0.06, 0.1), reach * r.uniform(0.16, 0.26)
        bm = bmesh.new()
        bmesh.ops.create_icosphere(bm, subdivisions=2, radius=1.0)
        ob = c4.mesh('%s %d' % (name, i), bm, m)
        f0 = round(t0 * c4.FPS)
        frames = [max(0, f0 - 1)] + list(range(f0, min(F, f0 + round(life * c4.FPS)) + 1, 3))
        if f0 >= F:
            bpy.data.objects.remove(ob)
            continue
        for f in frames:
            k = max(0.0, (f - f0) / (life * c4.FPS))
            run = 1 - (1 - min(k, 1.0)) ** 2.5
            rad = r0 + (r1 - r0) * run
            ob.location = (x + rad * math.cos(ang), y + rad * math.sin(ang), 3 + 3 * run)
            sz = s0 + (s1 - s0) * run
            ob.scale = (sz, sz, sz * 0.35)
            a = 0.0 if f < f0 else 0.7 * c4.smoothstep(0.0, 0.08, k) * (1 - c4.smoothstep(0.35, 1.0, k))
            ob.color = (1, 1, 1, a)
            ob.keyframe_insert('location', frame=f)
            ob.keyframe_insert('scale', frame=f)
            ob.keyframe_insert('color', frame=f)


def steam():
    """More vents working on the belt's roofs, the plant and the freight terminal."""
    m = bpy.data.materials.get('Steam')
    if m is None:
        return
    r = random.Random(5)
    sites = [w for w in c4.WAREHOUSES if w[2] > 50]
    r.shuffle(sites)
    vents = [(x + r.uniform(-w / 3, w / 3), y + r.uniform(-d / 4, d / 4), hh + 2.5, r.uniform(9, 14))
             for x, y, w, d, hh in sites[:9]]
    vents += [(-655.0, 125.0, 44.0, 22.0), (232.0, 676.0, 14.5, 12.0), (-370.0, 690.0, 12.0, 10.0)]
    for i, (x, y, z, size) in enumerate(vents):
        bm = bmesh.new()
        uv = bm.loops.layers.uv.new()
        w, tall = size * 0.9, size * 2.2
        f = bm.faces.new([bm.verts.new(c) for c in ((-w * 0.15, 0, 0), (w * 0.15, 0, 0),
                                                     (w * 0.9, 0, tall), (-w * 0.1, 0, tall))])
        for loop, co in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
            loop[uv].uv = co
        ob = c4.mesh('Steam port %d' % i, bm, m)
        ob.location = (x, y, z)
        ob.rotation_euler = (math.radians(-80), 0, 0)


# ---------------------------------------------------------------- checking

def report():
    """How many vehicle-frames are inside something: moving against moving, and against the
    outline of what stands, at knee and chest height above each vehicle's own ground."""
    scene = bpy.context.scene
    F = end_frame() + 1
    frames = list(range(0, F, 6))
    allm = []
    for ob in bpy.data.objects:
        if ob.parent is not None or not fcurves(ob) or ob.type != 'EMPTY' or ob.instance_type == 'COLLECTION':
            continue
        ext = extent(ob)
        if ext is None or ext[1] - ext[0] < 0.5:
            continue
        allm.append(dict(name=ob.name, ext=ext, track=track(ob, frames)))
    mine = [m for m in allm if is_port_life(m['name'])]
    print('PORT_LIFE_CHECK movers=%d mine=%d frames=%d' % (len(allm), len(mine), len(frames)), flush=True)
    hits = {}
    for a in mine:
        for b in allm:
            if a is b or (is_port_life(b['name']) and b['name'] < a['name']) or same_train(a['name'], b['name']):
                continue
            ta, tb = a['track'], b['track']
            close = (np.hypot(ta[:, 0] - tb[:, 0], ta[:, 1] - tb[:, 1]) < 40) & (ta[:, 2] > -10) & (tb[:, 2] > -10)
            for k in np.nonzero(close)[0]:
                if obb_overlap(a, ta[k], b, tb[k]):
                    hits.setdefault((a['name'], b['name']), []).append(frames[k])
    for (a, b), fs in sorted(hits.items()):
        print('PORT_LIFE_HIT %s x %s frames %s' % (a, b, fs[:6]), flush=True)
    print('PORT_LIFE_CHECK moving-pairs=%d' % len(hits), flush=True)
    stat = static_clips(mine, frames)
    print('PORT_LIFE_CHECK static-clips=%d' % stat, flush=True)


def is_port_life(name):
    return name.startswith(('Road van ', 'Box truck ', 'Road hauler ', 'Road tanker ', 'MuniSec patrol', 'Container truck',
                            'Port bus', 'Baggage tug', 'Cargo dolly', 'Yard tractor', 'Yard forklift belt',
                            'Yard forklift berth', 'Port bowser in')) and not name.startswith(
        ('Road hauler east 0', 'Road hauler west 0', 'Road tanker east 1', 'Road tanker west 1',
         'Box truck east 2', 'Box truck west 2', 'Road van east 3', 'Road van west 3'))


def same_train(a, b):
    def key(n):
        p = n.split()
        if n.startswith('Cargo dolly'):
            return ' '.join(p[2:-1])
        if n.startswith('Baggage tug'):
            return ' '.join(p[2:])
        return None
    return key(a) is not None and key(a) == key(b)


def obb_overlap(a, ta, b, tb):
    if ta[2] + a['ext'][4] > tb[2] + b['ext'][5] or tb[2] + b['ext'][4] > ta[2] + a['ext'][5]:
        return False

    def corners(m, t):
        x0, x1, y0, y1 = m['ext'][:4]
        c, s = math.cos(t[3]), math.sin(t[3])
        return np.array([(t[0] + u * c - v * s, t[1] + u * s + v * c) for u, v in ((x0, y0), (x1, y0), (x1, y1), (x0, y1))])
    A, B = corners(a, ta), corners(b, tb)
    for P in (A, B):
        for i in range(4):
            e = P[(i + 1) % 4] - P[i]
            n = np.array([-e[1], e[0]])
            pa, pb = A @ n, B @ n
            if pa.max() < pb.min() - 1e-3 or pb.max() < pa.min() - 1e-3:
                return False
    return True


def static_clips(mine, frames, cell=0.5):
    """Vehicle-frames whose footprint covers the outline of a standing thing at 0.8 or 2.0 m above
    the vehicle's base: every static mesh and hull instance, sliced."""
    lo = np.array([-1200.0, 60.0])
    hi = np.array([1200.0, 720.0])
    nx, ny = int((hi[0] - lo[0]) / cell), int((hi[1] - lo[1]) / cell)
    grids = {}
    bases = sorted({round(float(m['track'][0, 2]), 1) for m in mine})
    dg = bpy.context.evaluated_depsgraph_get()
    tris = []
    for inst in dg.object_instances:
        ob = inst.object
        if ob.type != 'MESH':
            continue
        orig = ob.original
        p = orig.parent if not inst.is_instance else None
        root = orig
        while root.parent is not None:
            root = root.parent
        if not inst.is_instance and (fcurves(root) or fcurves(orig)):
            continue                                       # moving: checked pairwise
        if inst.is_instance and inst.parent is not None and fcurves(inst.parent.original):
            continue
        mw = np.array(inst.matrix_world)
        me = ob.to_mesh() if False else ob.data
        if me is None or not len(me.polygons):
            continue
        co = np.empty(len(me.vertices) * 3)
        me.vertices.foreach_get('co', co)
        co = co.reshape(-1, 3) @ mw[:3, :3].T + mw[:3, 3]
        if co[:, 0].max() < lo[0] or co[:, 0].min() > hi[0] or co[:, 1].max() < lo[1] or co[:, 1].min() > hi[1]:
            continue
        if co[:, 2].min() > 8 or co[:, 2].max() < 0.5:
            continue
        me.calc_loop_triangles()
        t = np.empty(len(me.loop_triangles) * 3, np.int64)
        me.loop_triangles.foreach_get('vertices', t)
        tris.append(co[t.reshape(-1, 3)])
    T = np.concatenate(tris) if tris else np.zeros((0, 3, 3))
    for base in bases:
        g = np.zeros((ny, nx), bool)
        for hgt in (base + 0.8, base + 2.0):
            z = T[:, :, 2]
            cross = (z.min(1) < hgt) & (z.max(1) > hgt)
            for tri in T[cross]:
                pts = []
                for i in range(3):
                    a, b = tri[i], tri[(i + 1) % 3]
                    if (a[2] - hgt) * (b[2] - hgt) < 0:
                        k = (hgt - a[2]) / (b[2] - a[2])
                        pts.append(a[:2] + (b[:2] - a[:2]) * k)
                if len(pts) == 2:
                    n = max(2, int(np.linalg.norm(pts[1] - pts[0]) / (cell * 0.5)))
                    for k in range(n + 1):
                        q = pts[0] + (pts[1] - pts[0]) * k / n
                        i, j = int((q[0] - lo[0]) / cell), int((q[1] - lo[1]) / cell)
                        if 0 <= i < nx and 0 <= j < ny:
                            g[j, i] = True
        grids[base] = g
    clips = 0
    for m in mine:
        g = grids[round(float(m['track'][0, 2]), 1)]
        x0, x1, y0, y1 = m['ext'][:4]
        us, vs = np.meshgrid(np.arange(x0, x1, cell), np.arange(y0, y1, cell))
        us, vs = us.ravel(), vs.ravel()
        bad = 0
        for k, t in enumerate(m['track']):
            if t[2] < -10:
                continue
            c, s = math.cos(t[3]), math.sin(t[3])
            px, py = t[0] + us * c - vs * s, t[1] + us * s + vs * c
            i = ((px - lo[0]) / cell).astype(int)
            j = ((py - lo[1]) / cell).astype(int)
            ok = (i >= 0) & (i < nx) & (j >= 0) & (j < ny)
            if g[j[ok], i[ok]].any():
                bad += 1
                if bad <= 2:
                    print('PORT_LIFE_STATIC %s frame %d at %.1f %.1f' % (m['name'], frames[k], t[0], t[1]), flush=True)
        clips += bad
    return clips


if __name__ == '__main__':
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
    sys.path.insert(0, str(HERE))
    import c4x_scene
    c4x_scene.build()
    if argv and argv[0] == 'report':
        c4 = c4x_scene
        report()
