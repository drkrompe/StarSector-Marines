"""The public port's ships for C4 and C5: which of our finished hulls stand where, and what flies.

Called from `c4x_scene.add_port_traffic`. Every hull is one of the cinematic models as published
(`ship-model-status.md`), appended once and placed as collection instances, at its own real scale.
Nothing here edits a model; a second operator's livery is a hue shift laid over its materials.

The berths keep their jobs (`c4x_scene.BERTHS`: what the people, forklifts and carriers around each
one are doing, read by port_life, port_people and port_detail); this module only says which hull
does the job:

  berth 0 (-400)  hound      Hound (A), Hegemony auxiliary livery (port_life copies it for the
                             Hound that lands on berth 2 in C5)
  berth 1 (-310)  unloading  Shepherd, Ko Combine livery
  berth 3 (-130)  lifting    Mudskipper in a teal-trimmed operator's livery (port_life keys its climb
                             for a hull that rests at z 11, which the Mudskipper shares with the Hound)
  berth 4 (-40)   fuelling   Shepherd in a second operator's slate-blue
  berth 5 (140)   ferry      Mudskipper, Wayline's own livery
  heavy berth                Colossus, loaded and leaving: it lifts out of the bay in C5 (`climb`),
                             its gantry already rolled north clear of it, the containers staged for
                             the next hull off its stern quarter
  lighter pads               Hermes, textured

A freighter pad is 68 m across; the Hound (61 m), Mudskipper (67 m) and Shepherd (67 m) fit it nose
to tail. The Colossus is 108 x 89 m and fits only the heavy berth's 130 x 100 m walled deck.

The heavy-berth Colossus's departure is flown, not posed: it climbs off the deck on its lift jets at
a heavy hull's slow acceleration, turning onto its departure heading, then lights its mains and
accelerates up and away, curving south-west across the open port (`climb`).
Its size in the C5 rise is whatever that altitude gives against the camera's. It is the one hull
linked with its own drive effects (the shared VFX kit's `SWFX`, `with_effects`): cores, plumes, bell
glows, glow lights and haze on its own engine anchors, each engine's throttle driven by its thruster
marker (`after_markers`, called after `thrust_shots.mark_c4`).

In the air, well below the camera and never across its path:
  'Shepherd inbound'   a Shepherd letting down northward over the belt in C4's first seconds, and on
                       north out of the frame at the same speed.
From C5 24 s, the title, to the end the departing Colossus is the only ship in the air: the berth 3
Mudskipper flies off north out of the frame before then (port_life keys it).

Every part of every appended hull is left out of the overhead painting (`hull_part_names`), which
otherwise takes them for static ground and prints the pad markings beneath onto their decks.

Inputs: build/c1v2/inputs/{hound-skinned,mudskipper-skinned}.blend as before; shepherd.blend,
colossus.blend and hermes.blend from each hull's `*-cinematic/` folder (built there by its
finalize.sh) or copied into build/c1v2/inputs; the Colossus file must carry its SWFX effects. A
missing hull stops the build (PORT_FLEET missing) rather than standing in as another hull.
"""
import math
import random
from pathlib import Path

import bmesh
import bpy
from mathutils import Matrix, Vector

PROLOGUE = Path(__file__).resolve().parents[2]

# name: (blend file, collection, metres per unit, rest z on a 3 m freighter pad, +X is the bow).
# Every hull here is +X bow (each geometry.json says so), so a parked hull is turned to its lane's
# heading and its stern, where the cargo comes out, points back down the lane.
HULLS = {
    'hound': ('hound-skinned.blend', 'Collection', 0.88, 11.0, True),
    'mudskipper': ('mudskipper-skinned.blend', 'Collection', 0.85, 11.0, True),
    'shepherd': ('shepherd.blend', 'SHEPHERD', 0.78, 3.0 + 16.76 * 0.78, True),
    'colossus': ('colossus.blend', 'COLOSSUS', 0.50, 52.9 * 0.50, True),
    'hermes': ('hermes.blend', 'HERMES', 0.11, 2.5, True),
}
# (hue, saturation, value) over every base colour: another operator's paint on the same hull. Hue
# 0.5 leaves a colour alone; 0 or 1 turns it half round the wheel.
LIVERIES = {
    'teal': (0.03, 0.9, 0.85),        # the Mudskipper's red trim turns teal, its white a little greyer
    'slate': (1.0, 0.5, 0.95),        # the Shepherd's tan and orange turn a dull slate blue
}
BERTH_HULLS = {'hound': ('hound', None), 'unloading': ('shepherd', None), 'lifting': ('mudskipper', 'teal'),
               'fuelling': ('shepherd', 'slate'), 'ferry': ('mudskipper', None)}
C5_SECONDS = 40.0                     # how far past C4's end anything moving is keyed (C5 is 40 s)
HAZE_SKIP = set()                     # the drive effects' materials, filled as they are loaded
# The heavy-berth Colossus's departure, in C5 seconds: lift jets spool up from LIFT_SPOOL (at 40%),
# full at LIFT_OFF when it leaves the deck; mains light at MAIN_ON and are full at MAIN_FULL.
# The mains light as the camera starts its rise, so the burn is under way when the title comes up.
LIFT_SPOOL, LIFT_OFF, MAIN_ON, MAIN_FULL = 12.0, 15.0, 19.5, 21.0
LIFT_ACCEL = 0.6                      # m/s^2 up on the lift jets alone: a heavy hull, slow off the deck
MAIN_ACCEL, MAIN_LIFT = 1.6, 0.8      # m/s^2 along its bow and extra up once the mains are full
PITCH = math.radians(4.0)             # nose up as the mains take over
# Its turn onto the departure heading (headings in degrees, 180 = bow west as it is parked). As it comes
# off the deck it yaws 35 deg, eased in and out, while sliding a little south: a corner swinging north
# would otherwise pass through the parked gantry's beams, and early in the turn the hull is barely over
# the bay walls. Under the mains it keeps turning, more gently, onto TURN_HEADING with a small bank,
# so its course curves south-west across the open port and it is still in frame, travelling, as the
# title fades.
YAW_LIFT, YAW_LIFT_FROM, YAW_LIFT_TO = 35.0, 18.5, 23.0
TURN_HEADING, TURN_TO = 255.0, 32.0
DRIFT_SOUTH, DRIFT_FROM, DRIFT_TO = 16.0, 16.0, 22.0
BANK_PER_YAW_RATE = 0.26              # s: degrees of bank per degree a second of turn (into the turn)
# The mains' wash under the title: puffs, full until WASH_HOLD, a faint haze by WASH_THIN (C5 seconds).
WASH_PUFFS, WASH_HOLD, WASH_THIN = 90, 30.0, 38.0
GANTRY_CLEAR = 67.0                   # the heavy gantry stands this far north, clear of the bay
PARTS = set()                         # names of every object in an appended hull collection


def hull_part_names():
    """Every object the fleet's hulls are built from, for `ortho_paint.apply` to leave alone."""
    return set(PARTS)


def missing(c4, hull, fname):
    return 'PORT_FLEET missing %s: %s is in neither %s nor %s-cinematic/' % (
        hull, fname, c4.INPUTS, fname.split('.')[0].split('-')[0])


def find(c4, filename):
    stem = filename.split('.')[0].split('-')[0]
    for p in (c4.INPUTS / filename, PROLOGUE / ('%s-cinematic' % stem) / filename):
        if p.exists():
            return p
    return None


class Fleet:
    """Appends each hull (and each livery of it) once; `place` makes an instance of it."""

    def __init__(self, c4):
        self.c4 = c4
        self.colls = {}

    def coll(self, hull, livery=None):
        key = (hull, livery)
        if key in self.colls:
            return self.colls[key]
        fname, cname, mpu, rest, bow = HULLS[hull]
        path = find(self.c4, fname)
        if path is None:
            raise FileNotFoundError(missing(self.c4, hull, fname))
        lights = {o.name for o in bpy.data.objects if o.type == 'LIGHT'}
        mats = set(bpy.data.materials)
        coll = self.c4.append_collection(path, cname)
        bpy.context.view_layer.layer_collection.children[coll.name].exclude = True
        # The hull files' own lamps blow out from 700 m in daylight.
        for o in [o for o in bpy.data.objects if o.type == 'LIGHT' and o.name not in lights]:
            bpy.data.objects.remove(o)
        if livery:
            tint(set(bpy.data.materials) - mats, *LIVERIES[livery])
        PARTS.update(o.name for o in coll.all_objects)
        self.colls[key] = (coll, mpu, rest, bow)
        return self.colls[key]

    def place(self, label, hull, livery=None):
        coll, mpu, rest, bow = self.coll(hull, livery)
        e = self.c4.link(bpy.data.objects.new(label, None))
        e.instance_type = 'COLLECTION'
        e.instance_collection = coll
        e.scale = (mpu,) * 3
        e['port_fleet_hull'] = hull
        return e, rest, bow


def tint(mats, hue, sat, val):
    for m in mats:
        if not m.use_nodes:
            continue
        nt = m.node_tree
        for n in [n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED']:
            inp = n.inputs['Base Color']
            hs = nt.nodes.new('ShaderNodeHueSaturation')
            hs.inputs['Hue'].default_value = hue
            hs.inputs['Saturation'].default_value = sat
            hs.inputs['Value'].default_value = val
            if inp.links:
                nt.links.new(inp.links[0].from_socket, hs.inputs['Color'])
            else:
                hs.inputs['Color'].default_value = inp.default_value
            nt.links.new(hs.outputs['Color'], inp)


def add(c4):
    fleet = Fleet(c4)
    F, D = c4.FRAMES, c4.DURATION
    for i, (x, off, who) in enumerate(c4.BERTHS):
        if who == 'empty':
            continue
        hull, livery = BERTH_HULLS[who]
        e, rest, bow = fleet.place('%s at berth %d' % (who.title(), i), hull, livery)
        e.location = (x, c4.BERTH_Y + c4.BERTH_DY[x], rest)
        if who == 'lifting':
            for f, z in ((0, rest), (F - 1, rest + 4.0 * D)):
                e.location.z = z
                e.keyframe_insert('location', frame=f)
        # Bow up the lane, stern (where the cargo comes out) back down it toward the container yard.
        e.rotation_euler = (0, 0, c4.ROW_HEADING + math.radians(off) + (0.0 if bow else math.pi))
    for i, (x, who) in enumerate(c4.LIGHTERS):
        if who == 'empty':
            continue
        e, rest, bow = fleet.place('Lighter %d %s' % (i, who), 'hermes')
        e.rotation_euler = (0, 0, math.pi / 2 + math.radians((i * 37) % 9 - 4))
        z0, z1 = {'parked': (rest, rest), 'lifting': (rest, rest + 9.0 * D),
                  'landing': (rest + 12.0 * D, rest)}[who]
        for f, z in ((0, z0), (F - 1, z1)):
            e.location = (x, c4.LIGHTER_Y, z)
            e.keyframe_insert('location', frame=f)
    heavy(c4, fleet)
    air(c4, fleet)


def heavy(c4, fleet):
    """The Colossus in the heavy berth: square in the walled bay, bow west, the staged containers off
    its stern quarter. It is loaded and leaves in C5; its gantry stands rolled north out of the way."""
    hx0, hx1, hy0, hy1 = c4.HEAVY
    e, rest = with_effects(c4, 'Colossus at the heavy berth', 'colossus')
    # Its bounds run -126..91 units along X and -87..90 across (centre -17.5, 1.5); turned bow west,
    # that centre lies (+8.8, -0.75) m from the root, so the root sits that far back of the bay's middle.
    cx, cy = (hx0 + hx1) / 2, (hy0 + hy1) / 2
    e.rotation_euler = (0, 0, math.pi)
    e.location = (cx - 17.535 * 0.5, cy + 1.5 * 0.5, 0.2 + rest)
    climb(c4, e)
    # Keyed rather than just moved, so the overhead painting (which skips what is animated) leaves it
    # its own yellow instead of printing the ground it now stands over onto it.
    g = bpy.data.objects.get('Heavy berth gantry')
    if g is not None:
        g.location.y = GANTRY_CLEAR
        g.keyframe_insert('location', frame=0)


def with_effects(c4, label, hull):
    """A finished hull linked with the shared VFX kit's effects from the same load (`SWFX`: its
    drive cores, plumes, bell glows, glow lights and exhaust haze, riding the hull's own engine
    anchors), under an empty named `label` that carries its motion. Linked rather than instanced,
    because the effects hang off anchors in the hull's own hierarchy (as `c1v2_scene.effects_asset`).
    The kit's gun flashes and running lamps are left out: nothing fires, and the lamps blow out in
    daylight. Returns (empty, rest z)."""
    fname, cname, mpu, rest, bow = HULLS[hull]
    path = find(c4, fname)
    if path is None:
        raise FileNotFoundError(missing(c4, hull, fname))
    mats = set(bpy.data.materials)
    with bpy.data.libraries.load(str(path), link=False) as (src, dst):
        if 'SWFX' not in src.collections:
            raise RuntimeError('PORT_FLEET %s has no SWFX effects collection; rebuild it with export_effects.py' % path)
        dst.collections = [cname, 'SWFX']
    ship, fx = dst.collections
    for coll in (ship, fx):
        bpy.context.scene.collection.children.link(coll)
    for child in fx.children_recursive:
        if child.name in ('SWFX | Guns', 'SWFX | Lighting'):
            for o in child.all_objects:
                if o is not None:
                    o.hide_render = o.hide_viewport = True
    HAZE_SKIP.update(m.name for m in set(bpy.data.materials) - mats if m.name.startswith('SWFX'))
    PARTS.update(o.name for o in ship.all_objects if o is not None)
    PARTS.update(o.name for o in fx.all_objects if o is not None)
    e = c4.link(bpy.data.objects.new(label, None))
    e['port_fleet_hull'] = hull
    e['aloft'] = True                        # c5_crowd: its drive volumes reach the ground; it is no ground mover
    root = next(o for o in ship.all_objects if o is not None and o.parent is None)
    root.parent = e
    root.matrix_parent_inverse = Matrix.Identity(4)
    root.location, root.rotation_euler, root.scale = (0, 0, 0), (0, 0, 0), (mpu,) * 3
    e['swfx_controls'] = next(o for o in fx.all_objects if o is not None and o.name.startswith('SWFX controls')).name
    return e, rest


def smooth(t, t0, t1):
    k = min(max((t - t0) / (t1 - t0), 0.0), 1.0)
    return k * k * (3 - 2 * k)


def heading(t):
    """The bow's heading in degrees at C5 second t."""
    return (180.0 + YAW_LIFT * smooth(t, YAW_LIFT_FROM, YAW_LIFT_TO)
            + (TURN_HEADING - 180.0 - YAW_LIFT) * smooth(t, YAW_LIFT_TO, TURN_TO))


def climb(c4, e):
    """Key the lift-off, turn and climb, integrated frame by frame from the accelerations above (the
    mains push along the bow as it turns, so the course follows the heading round), and print the
    altitude above the deck at C5 22, 26, 30 s and the end."""
    T, fps = c4.DURATION, c4.FPS
    x, y, z0 = e.location
    e['rest_xy'] = (x, y)
    z, vx, vy, vz = z0, 0.0, 0.0, 0.0
    alt = {}
    f_off, f_end = round((T + LIFT_OFF) * fps), round((T + C5_SECONDS) * fps)
    e.keyframe_insert('location', frame=0)
    e.keyframe_insert('rotation_euler', frame=0)
    dt = 1.0 / fps
    for f in range(f_off, f_end + 1):
        t = f / fps - T
        ramp = min(max((t - MAIN_ON) / (MAIN_FULL - MAIN_ON), 0.0), 1.0)
        h = math.radians(heading(t))
        vz += (LIFT_ACCEL + MAIN_LIFT * ramp) * dt
        vx += MAIN_ACCEL * ramp * math.cos(h) * dt
        vy += MAIN_ACCEL * ramp * math.sin(h) * dt
        x, y, z = x + vx * dt, y + vy * dt, z + vz * dt
        alt[f] = z - z0
        if (f - f_off) % 3 == 0 or f == f_end:
            pr = smooth(t, MAIN_ON, MAIN_ON + 3.0)
            rate = (heading(t + 0.05) - heading(t - 0.05)) / 0.1
            e.location = (x, y - DRIFT_SOUTH * smooth(t, DRIFT_FROM, DRIFT_TO), z)
            e.rotation_euler = (-math.radians(BANK_PER_YAW_RATE * rate), -PITCH * pr, h)
            e.keyframe_insert('location', frame=f)
            e.keyframe_insert('rotation_euler', frame=f)
    import port_life
    port_life.linear(e)
    print('PORT_FLEET colossus above its deck: %s' % ', '.join(
        'C5 %g s %.1f m' % (s, alt.get(round((T + s) * fps), 0.0)) for s in (22, 26, 30, C5_SECONDS)),
        flush=True)


def fly(e, keys, F, carry_on=False):
    """Key `e` through (C4+C5 second, x, y, z, heading) at a steady speed between keys; carry_on
    keeps it going at its last speed past the last key."""
    for t, x, y, z, hd in keys:
        f = round(t * 24)
        e.location = (x, y, z)
        e.rotation_euler = (0, 0, hd)
        e.keyframe_insert('location', frame=f)
        e.keyframe_insert('rotation_euler', frame=f)
    import port_life
    port_life.linear(e, extrapolate=carry_on)


def air(c4, fleet):
    T = c4.DURATION
    END = T + C5_SECONDS
    F = round(END * c4.FPS)
    # A Shepherd letting down northward over the belt in C4's first seconds, from 420 m to 320 m, left
    # of the camera's track: under it for the opening seconds of the pan, a hull plainly in the air at
    # well under half the camera's height, keeping pace with the pan. It goes on north past the port and
    # out of every later frame.
    e, rest, bow = fleet.place('Shepherd inbound', 'shepherd', 'slate')
    hd = math.atan2(850.0, 60.0)
    # It starts 300 m from where Second Wind opens (c4x_scene.P0) and the two paths only diverge.
    fly(e, [(0.0, -320.0, -230.0, 420.0, hd), (c4.PAN, -260.0, 620.0, 320.0, hd),
            (T, -220.0, 620.0 + 850.0 * (T - c4.PAN) / c4.PAN, 280.0, hd)], F, carry_on=True)
    print('PORT_FLEET hulls', sorted('%s%s' % (h, '/' + l if l else '') for h, l in fleet.colls), flush=True)


def after_markers(c4):
    """After `thrust_shots.mark_c4`: the heavy-berth Colossus's throttle as `climb` flies it on its
    nozzle markers (`thrust-markers/README.md`), its own drive effects driven from them
    (`hang_effects`), and dust off the deck as it lifts (`lift_dust`)."""
    import thrust_markers as tm
    craft = bpy.data.objects.get('Colossus at the heavy berth')
    if craft is None:
        return
    T, fps = c4.DURATION, c4.FPS
    marks = [o for o in bpy.data.objects if o.parent == craft and o.name.startswith(tm.PREFIX)]
    lift = [(0.0, 0.0), (T + LIFT_SPOOL - 0.1, 0.0), (T + LIFT_SPOOL, 0.4), (T + LIFT_OFF, 1.0)]
    main = [(0.0, 0.0), (T + MAIN_ON - 0.1, 0.0), (T + MAIN_ON, 0.3), (T + MAIN_FULL, 1.0)]
    tm.key(marks, lift, fps, kinds=('lift',))
    tm.key(marks, main, fps, kinds=('main',))
    n = hang_effects(craft, marks, c4)
    lift_dust(c4, craft, [m for m in marks if m['thrust_kind'] == 'lift' and m['thrust_working']], lift)
    cover_old_gantry(c4)
    print('PORT_FLEET colossus drives on markers=%d; lift spool C5 %g s, off %g s, mains %g-%g s' % (
        n, LIFT_SPOOL, LIFT_OFF, MAIN_ON, MAIN_FULL), flush=True)


def lift_dust(c4, craft, nozzles, lift):
    """Downwash off the heavy berth as the Colossus lifts out: the same dust, grit-brown billows and
    grey smoke as Second Wind's landing on Kilo Nine (`c4x_scene.add_landing_dust`, whose material
    it shares), at this hull's size. It stirs as the lift jets spool at LIFT_SPOOL, is thickest as
    the hull leaves the deck at LIFT_OFF and thins as the jets rise away from it. The bay's blast
    walls stop it on three sides, where it piles up and rolls over the top; the open south side lets
    it run out across the road. As the mains light, still low, their wash raises a fresh, thick cloud
    off the stern that hangs over the bay through the camera's arrival (about 23 s) and thins under the
    title (WASH_HOLD to WASH_THIN). A thin pall hangs over the bay after it has gone."""
    m = bpy.data.materials.get('Landing dust')
    if m is None or not nozzles:
        return
    T, fps = c4.DURATION, c4.FPS
    hx0, hx1, hy0, hy1 = c4.HEAVY
    deck = 0.2
    wind = Vector((1.6, 0.3, 0.0))
    r = random.Random(57)

    def thr(t):
        for (t0, v0), (t1, v1) in zip(lift, lift[1:]):
            if t0 <= t <= t1:
                return v0 + (v1 - v0) * (t - t0) / max(t1 - t0, 1e-6)
        return lift[-1][1] if t > lift[-1][0] else 0.0

    # Where each jet stands over the deck, and how hard it hits it, along the climb: read off the
    # evaluated scene every third frame (the keys alone are not evaluated yet while the shot builds).
    scene = bpy.context.scene
    f_a, f_b = round((T + LIFT_SPOOL) * fps), round((T + LIFT_OFF + 12.0) * fps)
    sampled = {}
    for f in list(range(f_a, f_b, 3)) + [f_b]:
        scene.frame_set(f)
        sampled[f] = (craft.matrix_world.translation.to_2d(), [n.matrix_world.translation.copy() for n in nozzles])
    scene.frame_set(0)
    keys = sorted(sampled)
    track = {}
    for f in range(f_a, f_b + 1):
        f0 = max(k for k in keys if k <= f)
        f1 = min(k for k in keys if k >= f)
        u = 0.0 if f1 == f0 else (f - f0) / (f1 - f0)
        centre = sampled[f0][0].lerp(sampled[f1][0], u)
        k = thr(f / fps)
        hits = []
        for a, b in zip(sampled[f0][1], sampled[f1][1]):
            w = a.lerp(b, u)
            h = max(0.0, w.z - deck)
            hits.append((Vector((w.x, w.y, deck)), k * (1 - c4.smoothstep(15.0, 130.0, h))))
        track[f] = (centre, hits)
    gone = T + C5_SECONDS

    def spawn(name, ts, p0, life, motion, tint):
        bm = bmesh.new()
        bmesh.ops.create_icosphere(bm, subdivisions=2, radius=1.0)
        ob = c4.mesh(name, bm, m)
        ob['aloft'] = True                       # dust, not a ground vehicle (`c5_crowd`)
        f0 = round(ts * fps)
        f1 = min(round(gone * fps), f0 + round(life * fps))
        for f in [f0 - 1] + list(range(f0, f1, 3)) + [f1]:
            tau = max(0.0, (f - f0) / fps)
            off, sc, a = motion(tau)
            alive = f0 <= f < f1
            ob.location = p0 + off
            ob.scale = sc if alive else (0.001,) * 3      # nothing on the deck before or after: walk maps read it
            ob.color = tint + (a if alive else 0.0,)
            for prop in ('location', 'scale', 'color'):
                ob.keyframe_insert(prop, frame=f)

    def to_wall(p, u):
        """Metres along u from p to the first blast wall (west, east, north); the south is open."""
        best = 1e9
        if u.x < -1e-3:
            best = min(best, (hx0 - p.x) / u.x)
        if u.x > 1e-3:
            best = min(best, (hx1 - p.x) / u.x)
        if u.y > 1e-3:
            best = min(best, (hy1 - p.y) / u.y)
        return max(best, 2.0)

    budget = {'dust': 440, 'smoke': 130}
    total = sum(s_ for f in track for _, s_ in track[f][1]) or 1.0
    acc = {k: r.random() for k in budget}
    n = {k: 0 for k in budget}
    for f in range(f_a, f_b + 1):
        centre, hits = track[f]
        for p, s_ in hits:
            if s_ <= 0.0:
                continue
            ts = f / fps + r.uniform(0.0, 1.0 / fps)
            for kind in budget:
                acc[kind] += budget[kind] * s_ / total
                while acc[kind] >= 1.0:
                    acc[kind] -= 1.0
                    n[kind] += 1
                    d = p.to_2d() - centre
                    ang = (math.atan2(d.y, d.x) if d.length > 3.0 else r.uniform(0, 2 * math.pi)) + r.gauss(0.0, 0.6)
                    u = Vector((math.cos(ang), math.sin(ang), 0.0))
                    if kind == 'dust':
                        reach = r.uniform(45.0, 80.0) * (0.45 + 0.55 * s_)
                        wall = to_wall(p, u)
                        run_out = min(reach, wall + 4.0)                # a wall stops it; it piles up
                        pile = max(0.0, reach - run_out)                # and rolls up over the top
                        drag = r.uniform(1.2, 1.8)
                        life = min(r.uniform(3.5, 6.0), gone - ts)
                        size0, size1 = r.uniform(5.0, 8.0), r.uniform(18.0, 30.0) * (0.6 + 0.4 * s_)
                        peak = min(1.0, r.uniform(0.6, 1.4)) * (0.45 + 0.55 * s_)
                        lum = r.uniform(0.82, 1.12)
                        tint = (0.40 * lum, 0.335 * lum, 0.25 * lum)

                        def motion(tau, u=u, run_out=run_out, pile=pile, drag=drag, life=life, size0=size0,
                                   size1=size1, peak=peak):
                            run = 1 - math.exp(-tau / drag)
                            k = tau / life
                            off = (u * (6.0 + run_out * run) + wind * tau
                                   + Vector((0, 0, 2.0 + (8.0 + 0.6 * pile) * run + 0.8 * tau)))
                            sz = size0 + (size1 - size0) * (1 - math.exp(-tau / (0.6 * drag + 0.4)))
                            a = peak * (0.25 + 0.75 * c4.smoothstep(0.05, 0.4, k)) * c4.smoothstep(0.0, 0.1, tau) \
                                * (1 - c4.smoothstep(0.55, 1.0, k))
                            return off, (sz * 1.2, sz * 0.9, sz * (0.4 + 0.25 * run)), a
                        spawn('Lift dust colossus %d' % n[kind], ts, p, life, motion, tint)
                    else:
                        life = min(r.uniform(4.0, 6.5), gone - ts)
                        rise = r.uniform(16.0, 32.0)
                        size0, size1 = r.uniform(5.0, 7.0), r.uniform(15.0, 26.0)
                        peak = r.uniform(0.4, 0.65) * (0.45 + 0.55 * s_)
                        g = r.uniform(0.17, 0.24)

                        def motion(tau, u=u, life=life, rise=rise, size0=size0, size1=size1, peak=peak):
                            k = tau / life
                            off = (u * (10.0 + 16.0 * (1 - math.exp(-tau))) + wind * 1.6 * tau
                                   + Vector((0, 0, 3.0 + rise * (1 - math.exp(-tau / 1.8)))))
                            sz = size0 + (size1 - size0) * c4.smoothstep(0.0, 0.8, k)
                            a = peak * c4.smoothstep(0.0, 0.2, tau) * (1 - c4.smoothstep(0.25, 1.0, k))
                            return off, (sz, sz, sz * 0.7), a
                        spawn('Lift dust colossus smoke %d' % n[kind], ts, p, life, motion, (g, g * 0.98, g * 0.95))
    # The mains' wash: as they light, low over the deck, they blow a fresh, thick cloud of dust and
    # smoke back off the stern and across the bay. It is what the camera finds when its rise brings the
    # berth into frame at about 23 s: dense and slowly spreading over and around the bay to about 30 s,
    # then thinning under the title to a faint haze by 38 s.
    keys_sorted = sorted(sampled)
    for i in range(WASH_PUFFS):
        ts = T + MAIN_ON + r.uniform(0.0, MAIN_FULL - MAIN_ON + 2.0)
        f = min(keys_sorted, key=lambda k: abs(k - ts * fps))
        centre = sampled[f][0]
        stern = math.radians(heading(ts - T)) + math.pi + r.gauss(0.0, 0.7)
        back = r.uniform(10.0, 75.0)
        side = r.uniform(-40.0, 40.0)
        p = Vector((centre.x + back * math.cos(stern) - side * math.sin(stern),
                    centre.y + back * math.sin(stern) + side * math.cos(stern), 3.0))
        out = Vector((math.cos(stern), math.sin(stern), 0.0)) * r.uniform(1.0, 2.6) \
            + Vector((r.uniform(-0.8, 0.8), r.uniform(-0.8, 0.8), 0.0))
        life = gone - ts
        rise = r.uniform(12.0, 30.0)
        size0, size1 = r.uniform(14.0, 22.0), r.uniform(40.0, 70.0)
        peak = r.uniform(0.45, 0.8)
        smoke = r.random() < 0.35
        lum = r.uniform(0.85, 1.1)
        tint = (0.22 * lum, 0.21 * lum, 0.2 * lum) if smoke else (0.42 * lum, 0.35 * lum, 0.26 * lum)

        def motion(tau, ts=ts, out=out, rise=rise, size0=size0, size1=size1, peak=peak):
            t = ts + tau - T                                        # C5 seconds
            off = out * tau + wind * tau + Vector((0, 0, 2.0 + rise * (1 - math.exp(-tau / 4.0))))
            sz = size0 + (size1 - size0) * (1 - math.exp(-tau / 5.0))
            a = (peak * c4.smoothstep(0.0, 1.5, tau) * (1 - 0.88 * c4.smoothstep(WASH_HOLD, WASH_THIN, t))
                 * (1 - c4.smoothstep(WASH_THIN + 0.5, C5_SECONDS, t)))
            return off, (sz, sz * 0.9, sz * 0.38), a
        spawn('Lift dust colossus wash %d' % i, ts, p, life, motion, tint)

    # Settling: a broad, thin pall over the bay once the hull is off the deck, still hanging there as
    # the camera's rise brings the berth into frame, drifting off and gone before the title ends.
    cx, cy = (hx0 + hx1) / 2, (hy0 + hy1) / 2
    for i in range(18):
        ts = T + LIFT_OFF + r.uniform(0.0, 5.0)
        p = Vector((cx + r.uniform(-55, 55), cy + r.uniform(-60, 40), 3.0))
        life = min(r.uniform(14.0, 19.0), gone - ts)
        size, peak = r.uniform(35.0, 55.0), r.uniform(0.12, 0.2)

        def motion(tau, life=life, size=size, peak=peak):
            k = tau / life
            a = peak * c4.smoothstep(0.0, 0.25, k) * (1 - c4.smoothstep(0.35, 1.0, k))
            sz = size * (0.8 + 0.4 * k)
            return wind * tau + Vector((0, 0, 2.0 * tau)), (sz, sz, sz * 0.22), a
        spawn('Lift dust colossus pall %d' % i, ts, p, life, motion, (0.42, 0.36, 0.28))
    print('PORT_FLEET colossus lift dust', n, 'nozzles', len(nozzles), flush=True)


def cover_old_gantry(c4):
    """The overhead painting was made with the gantry standing over the bay, so its beams and trolley
    are in the deck's paint, and show once the Colossus has gone. A strip over them (above the rails,
    under the walls' tops) carries the same painting read from clean deck 28 m north. Runs after
    `ortho_paint.apply` and `c4x_scene.darken_pads`, so the deck's material is already painted and
    toned when it is copied."""
    deck = bpy.data.objects.get('Heavy berth deck')
    src = deck.active_material if deck is not None else None
    if src is None or not any(n.type == 'NEW_GEOMETRY' for n in src.node_tree.nodes):
        return
    m = src.copy()
    m.name = 'Heavy berth deck patch'
    nt = m.node_tree
    for geo in [n for n in nt.nodes if n.type == 'NEW_GEOMETRY']:
        shift = nt.nodes.new('ShaderNodeVectorMath')
        shift.operation = 'ADD'
        shift.inputs[1].default_value = (0.0, 28.0, 0.0)
        for link in list(geo.outputs['Position'].links):
            nt.links.new(shift.outputs[0], link.to_socket)
        nt.links.new(geo.outputs['Position'], shift.inputs[0])
    hx0, hx1 = c4.HEAVY[0], c4.HEAVY[1]
    y0 = c4.HEAVY[2] + 35                      # beams at +38 and +56, the trolley between
    p = c4.plane('Heavy berth deck patch', hx0 - 8, hx1 + 8, y0, y0 + 24, m, 0.9)
    p.visible_shadow = False
    p['ortho'] = 1.0


def hang_effects(craft, marks, c4):
    """Drive the hull's own drive effects off its thruster markers: each engine's throttle
    (`thr_throttle_e<i>` on the kit's controls) follows its marker by a driver, so the markers stay
    the one record of what each nozzle is doing (mains 1-6 are engines 1-6; lift 01-10 are 7-16, both
    in the hull's declared order). Daylight asks for more than the kit's close-up defaults: brighter
    bell glows and a stronger glow light on hull and deck.

    The kit's cores and plumes are volumes made for Cycles (`second-wind-vfx/README.md`), and C4/C5
    render in EEVEE, whose volume grid cannot resolve a plume tens of metres long from 1.8 km (it
    leaves a faint smudge at best). So each marker also carries `far_plume`: the same core and plume,
    in the kit's own Colossus colours (`colossus-cinematic/vfx_hull.py`), as soft emissive surfaces
    that EEVEE draws at any range, sized by the same throttle."""
    ctl = bpy.data.objects.get(craft.get('swfx_controls', ''))
    if ctl is None:
        return 0
    ctl['thr_throttle'] = 1.0
    for knob, value in (('thr_brightness', 3.0), ('thr_plume_length', 2.5), ('thr_glow', 4.0)):
        ctl[knob] = value
    n = 0
    for m in marks:
        kind, num = m.name.rsplit(' | ', 1)[1].split()
        engine = int(num) + (6 if kind == 'lift' else 0) if kind in ('main', 'lift') else None
        prop = 'thr_throttle_e%d' % engine if engine else None
        if prop is None or prop not in ctl:
            continue
        ctl.driver_remove('["%s"]' % prop)
        drv = ctl.driver_add('["%s"]' % prop).driver
        drv.type = 'AVERAGE'
        var = drv.variables.new()
        var.type = 'SINGLE_PROP'
        var.targets[0].id = m
        var.targets[0].data_path = '["thrust_throttle"]'
        far_plume(m, kind, float(m.get('radius_m', 1.5)))
        n += 1
    return n


# The Colossus's clean drive colours (colossus-cinematic/vfx_hull.py COLOURS): hot core to orange plume.
CORE_RGB, PLUME_RGB = (1.0, 0.80, 0.52), (1.0, 0.42, 0.08)
# Per kind: (shell, length and width in bell radii, emission strength, opacity at the mouth). The kit's
# plume is 11 radii long; a main's is drawn longer so a burn reads from 1.8 km. The lift jets are short.
FAR_SHAPES = {
    'main': (('core', 7.0, 0.75, 25.0, 1.0), ('plume', 40.0, 1.9, 4.0, 0.9)),
    'lift': (('core', 2.5, 0.7, 20.0, 1.0), ('plume', 8.0, 1.4, 4.0, 0.8)),
}


def far_plume(marker, kind, r):
    """A drive's core and plume as soft emissive cones along the marker's +X (`thrust-markers`
    convention, `radius_m` the bell's radius in metres), each scaled by the marker's throttle through
    a driver: full length and width at 1, narrowing to nothing at 0."""
    me = bpy.data.meshes.get('Fleet far plume cone') or far_cone_mesh()
    out = []
    for shell, length, width, strength, alpha in FAR_SHAPES[kind]:
        ob = bpy.data.objects.new('Fleet %s | %s' % (shell, marker.name.split(' | ', 1)[1]), me)
        bpy.context.scene.collection.objects.link(ob)
        ob.material_slots[0].link = 'OBJECT'
        ob.material_slots[0].material = far_plume_material(shell, strength, alpha)
        ob.parent = marker
        ob.matrix_parent_inverse = Matrix.Identity(4)
        ob.visible_shadow = False
        L, W = length * r, width * r
        for axis, expr in ((0, '%g * (0.35 + 0.65 * t)' % L), (1, '%g * t' % W), (2, '%g * t' % W)):
            drv = ob.driver_add('scale', axis).driver
            drv.type = 'SCRIPTED'
            var = drv.variables.new()
            var.name = 't'
            var.type = 'SINGLE_PROP'
            var.targets[0].id = marker
            var.targets[0].data_path = '["thrust_throttle"]'
            drv.expression = expr
        out.append(ob)
    return out


def far_cone_mesh():
    """Unit cone along +X from the mouth at the origin: radius 1 there, a point at x = 1. One empty
    material slot, filled per object."""
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=False, segments=16, radius1=1.0, radius2=0.02, depth=1.0)
    bmesh.ops.rotate(bm, verts=bm.verts, cent=(0, 0, 0), matrix=Matrix.Rotation(math.pi / 2, 3, 'Y'))
    bmesh.ops.translate(bm, verts=bm.verts, vec=(0.5, 0, 0))
    me = bpy.data.meshes.new('Fleet far plume cone')
    bm.to_mesh(me)
    bm.free()
    for poly in me.polygons:
        poly.use_smooth = True
    me.materials.append(None)
    return me


def far_plume_material(shell, strength, alpha):
    """Emission that is hottest and most opaque at the mouth and fades along the cone (object X runs
    0 at the mouth to 1 at the tip), white-hot to the kit's orange, with edges that thin out where
    the surface turns away from the camera, so no outline shows."""
    name = 'Fleet far %s %g %g' % (shell, strength, alpha)
    m = bpy.data.materials.get(name)
    if m is not None:
        return m
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    m.surface_render_method = 'BLENDED'
    m.use_backface_culling = True
    nodes, links = m.node_tree.nodes, m.node_tree.links
    bsdf = nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = (0, 0, 0, 1)

    def math_node(op, a, b, clamp=False):
        n = nodes.new('ShaderNodeMath')
        n.operation = op
        n.use_clamp = clamp
        for sock, v in ((n.inputs[0], a), (n.inputs[1], b)):
            if isinstance(v, (int, float)):
                sock.default_value = v
            else:
                links.new(v, sock)
        return n.outputs[0]

    tc = nodes.new('ShaderNodeTexCoord')
    sep = nodes.new('ShaderNodeSeparateXYZ')
    links.new(tc.outputs['Object'], sep.inputs[0])
    u = math_node('MINIMUM', sep.outputs['X'], 1.0, clamp=True)              # 0 at the mouth, 1 at the tip
    fade = math_node('POWER', math_node('SUBTRACT', 1.0, u), 1.6 if shell == 'plume' else 1.2)
    mix = nodes.new('ShaderNodeMix')                                          # white-hot to orange
    mix.data_type = 'RGBA'
    links.new(u, mix.inputs['Factor'])
    mix.inputs['A'].default_value = CORE_RGB + (1,)
    mix.inputs['B'].default_value = (PLUME_RGB if shell == 'plume' else CORE_RGB) + (1,)
    links.new(mix.outputs['Result'], bsdf.inputs['Emission Color'])
    lw = nodes.new('ShaderNodeLayerWeight')                                   # soft edges
    lw.inputs['Blend'].default_value = 0.5
    edge = math_node('POWER', math_node('SUBTRACT', 1.0, lw.outputs['Facing']), 1.5)
    links.new(math_node('MULTIPLY', math_node('MULTIPLY', fade, edge), alpha), bsdf.inputs['Alpha'])
    links.new(math_node('MULTIPLY', fade, strength), bsdf.inputs['Emission Strength'])
    return m


