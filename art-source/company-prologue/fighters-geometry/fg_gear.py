"""Retractable landing gear for the fighters (after the Broadsword's bw_gear).

The owner accepted each fighter's gear for landed shots on 2026-10-18, but it
was fixed, so the fighters flew with it hanging down. This module makes it
retract, and replaces the thin piston legs and puck feet with gear that reads
as structure (owner, 2026-10-20: "a piston with a hockey puck foot is ugly"):
box-section legs that taper like wedges, broad wedge shoes, claws that curl,
runners on wedge links.

Model, the same on every fighter:
- The gear is built DOWN, in the landed pose, standing on the module's DECK_Z.
- Every moving part is parented to a hinge empty `<part> hinge`, whose local
  Y is the hinge axis. A hinge whose axis is not the hull's Y sits under a
  fixed `<part> hinge mount` that carries the axis; only the hinge turns.
- Each hinge carries how it moves as custom properties: `stowed_deg` (its
  angle at gear_down 0), `deployed_deg` (at 1) and `knob_span` (the part of
  the knob over which it moves). Doors are built shut and open early; legs are
  built down and swing after the doors have cleared; claws uncurl last.
- `<Name> control` carries the knob `gear_down` (0 stowed, flight; 1 deployed,
  landed). The driver adds one driver per hinge, so posing the knob poses the
  gear in Blender, in a .blend appended into a shot, and in the surfacing build.
- Doors are thin plates laid on the skin of the solids the gear folds into,
  hinged on an edge; a dark liner on the skin under them is the bay seen with
  the gear down. The legs fold into the solid itself, as the Broadsword's do.

sweep() is the clash check: at several knob values, no moving part may pass
through anything but the solids its bay is in (and its own liners), no leg
through its own doors, and stowed nothing may stand outside the skin.
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

KNOB = 'gear_down'
DOOR_SPAN = (0.0, 0.3)    # doors open over the first 30% of the knob, before anything swings
LEG_SPAN = (0.25, 1.0)    # legs swing down once the doors stand clear
CLAW_SPAN = (0.85, 1.0)   # claws uncurl as the foot reaches the deck
SWEEP_KNOBS = (0.0, .05, .1, .15, .2, .25, .3, .35, .4, .5, .6, .7, .8, .9, .95, 1.0)


def clamp01(t):
    return max(0.0, min(1.0, t))


def frame(axis, ref=None):
    """A rotation whose local Y is `axis` and whose local Z is as near world up as that allows."""
    y = Vector(axis).normalized()
    up = Vector(ref) if ref is not None else Vector((0, 0, 1))
    if abs(up.dot(y)) > .99:
        up = Vector((1, 0, 0))
    z = (up-y*up.dot(y)).normalized()
    x = y.cross(z)
    return Matrix((x, y, z)).transposed()


def fold(v, d):
    """(axis, degrees): the turn about axis that lays the vector v along the direction d."""
    v, d = Vector(v).normalized(), Vector(d).normalized()
    n = v.cross(d).normalized()
    deg = math.degrees(v.angle(d))
    if n.y < -.999:   # about -Y is about the hull's own Y, the other way
        return Vector((0, 1, 0)), -deg
    return n, deg


class Gear:
    """The fighter's gear: hinges, what moves with them, and the bays they fold into."""

    def __init__(self, kit, mpu):
        self.kit = kit
        self.mpu = mpu
        self.hinges = []   # hinge empties, in build order (a parent before its children)
        self.legs = []     # {'name', 'hinge', 'hosts', 'doors', 'liners', 'floor'}

    # --- Hinges -----------------------------------------------------------------

    def hinge(self, name, loc, axis=(0, 1, 0), stowed_deg=0.0, deployed_deg=0.0, span=LEG_SPAN,
              parent=None, note=''):
        """A hinge empty at loc turning about `axis` (world, at build time). Built at the deployed pose."""
        axis = Vector(axis).normalized()
        if parent is not None:
            bpy.context.view_layer.update()
            axis = (parent.matrix_world.to_3x3().inverted() @ axis).normalized()
        mount = None
        if (axis-Vector((0, 1, 0))).length > 1e-6:
            mount = bpy.data.objects.new(f'{name} mount', None)
            mount.empty_display_type, mount.empty_display_size = 'PLAIN_AXES', .4
            bpy.context.collection.objects.link(mount)
        h = bpy.data.objects.new(name, None)
        h.empty_display_type, h.empty_display_size = 'SINGLE_ARROW', 1.0
        bpy.context.collection.objects.link(h)
        rot = frame(axis).to_4x4()
        if parent is not None:
            bpy.context.view_layer.update()
            loc_local = parent.matrix_world.inverted() @ Vector(loc)
        else:
            loc_local = Vector(loc)
        top = mount or h
        top.matrix_basis = Matrix.Translation(loc_local) @ rot if mount else Matrix.Translation(loc_local)
        if parent is not None:
            top.parent = parent
        if mount:
            h.parent = mount
        h['open_axis'] = 'Y'
        h['stowed_deg'] = float(stowed_deg)
        h['deployed_deg'] = float(deployed_deg)
        h['knob_span'] = list(span)
        h['note'] = note or 'turns about local Y; angle(k) = stowed + (deployed - stowed) * clamp((k - k0) / (k1 - k0))'
        h.rotation_euler[1] = math.radians(deployed_deg)
        self.hinges.append(h)
        return h

    @staticmethod
    def adopt(parent, objs):
        bpy.context.view_layer.update()
        inv = parent.matrix_world.inverted()
        for ob in objs:
            mw = ob.matrix_world.copy()
            ob.parent = parent
            ob.matrix_parent_inverse = inv
            ob.matrix_world = mw

    def leg(self, name, hinges, hosts, floor=None):
        """Register one retracting unit: its top hinge(s), the solids its bay is in, an optional stowed
        floor z (where the unit's own underside is the skin, as the Talon's runner soles are)."""
        hinges = hinges if isinstance(hinges, (list, tuple)) else [hinges]
        rec = {'name': name, 'hinges': [h.name for h in hinges], 'hosts': [h.name for h in hosts], 'doors': [],
               'liners': [], 'floor': floor}
        self.legs.append(rec)
        return rec

    # --- Pose ---------------------------------------------------------------------

    @staticmethod
    def angle(h, k):
        k0, k1 = h['knob_span']
        t = clamp01((k-k0)/(k1-k0))
        return h['stowed_deg']+(h['deployed_deg']-h['stowed_deg'])*t

    def pose(self, k):
        for h in self.hinges:
            h.rotation_euler[1] = math.radians(self.angle(h, k))
        bpy.context.view_layer.update()

    @staticmethod
    def expression(h):
        k0, k1 = h['knob_span']
        s, d = math.radians(h['stowed_deg']), math.radians(h['deployed_deg'])
        return f'{s!r} + {d-s!r} * min(1.0, max(0.0, (k - {k0!r}) / {k1-k0!r}))'

    def drive(self, control):
        """One driver per hinge from the control's knob (the same arithmetic as pose)."""
        for h in self.hinges:
            h.driver_remove('rotation_euler', 1)
            fc = h.driver_add('rotation_euler', 1)
            d = fc.driver
            d.type = 'SCRIPTED'
            v = d.variables.new()
            v.name, v.type = 'k', 'SINGLE_PROP'
            v.targets[0].id_type, v.targets[0].id = 'OBJECT', control
            v.targets[0].data_path = f'["{KNOB}"]'
            d.expression = self.expression(h)

    # --- Skin plates: doors and liners -----------------------------------------

    def belly(self, hosts, x, y):
        """Lowest skin of the host solids under (x, y), by a ray cast up from below."""
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        best = None
        for ob in hosts:
            ev = ob.evaluated_get(dg)
            inv = ev.matrix_world.inverted()
            hit, p, _, _ = ev.ray_cast(inv @ Vector((x, y, -60)), (inv.to_3x3() @ Vector((0, 0, 1))).normalized())
            if hit:
                z = (ev.matrix_world @ p).z
                best = z if best is None else min(best, z)
        if best is None:
            raise RuntimeError(f'no skin under ({x:.2f}, {y:.2f}) among {[h.name for h in hosts]}')
        return best

    def plate(self, name, quad, hosts, mat, gap, t, n=(6, 3), bevel=0.0):
        """A plate laid under the hosts' skin over the plan quad (four XY corners, in order),
        from gap below the skin to gap + t below it."""
        (ax, ay), (bx, by), (cx, cy), (dx, dy) = quad
        nu, nv = n
        verts, idx = [], {}
        for i in range(nu+1):
            for j in range(nv+1):
                u, v = i/nu, j/nv
                x = (1-u)*(1-v)*ax+u*(1-v)*bx+u*v*cx+(1-u)*v*dx
                y = (1-u)*(1-v)*ay+u*(1-v)*by+u*v*cy+(1-u)*v*dy
                z = self.belly(hosts, x, y)-gap
                idx[i, j] = len(verts)
                verts += [(x, y, z), (x, y, z-t)]
        faces = []
        for i in range(nu):
            for j in range(nv):
                a, b, c, d = idx[i, j], idx[i+1, j], idx[i+1, j+1], idx[i, j+1]
                faces += [(a, b, c, d), (a+1, d+1, c+1, b+1)]
        ring = [idx[i, 0] for i in range(nu)]+[idx[nu, j] for j in range(nv)]
        ring += [idx[i, nv] for i in range(nu, 0, -1)]+[idx[0, j] for j in range(nv, 0, -1)]
        for a, b in zip(ring, ring[1:]+ring[:1]):
            faces.append((a, b, b+1, a+1))
        return self.kit.mesh(name, verts, faces, mat, bevel)

    def liner(self, rec, name, quad, hosts, n=(6, 3)):
        """The bay seen with the gear down: a dark skin on the hosts under the doors."""
        ob = self.plate(name, quad, hosts, self.kit.dark, self.u(.001), self.u(.003), n)
        rec['liners'].append(ob.name)
        return ob

    def door(self, rec, name, quad, hinge_edge, hosts, mat, open_deg=95.0, n=(6, 3)):
        """A door over part of a bay: quad in plan, hinged on the quad edge hinge_edge (corner indices),
        built shut and turned open_deg so it swings down, away from the bay."""
        gap, t = self.u(.008), self.u(.022)
        ob = self.plate(name, quad, hosts, mat, gap, t, n, self.u(.006))
        i, j = hinge_edge
        pa = Vector((*quad[i], self.belly(hosts, *quad[i])-gap))
        pb = Vector((*quad[j], self.belly(hosts, *quad[j])-gap))
        axis = (pb-pa).normalized()
        centre = sum((Vector((*q, 0)) for q in quad), Vector())/4
        centre.z = self.belly(hosts, centre.x, centre.y)
        mid = (pa+pb)/2
        # Turn the sign so the door's middle starts down, out of the skin (judged on a small turn: past
        # square, both ways can end lower).
        probe = Matrix.Rotation(math.radians(10.0), 3, axis) @ (centre-mid)
        sign = 1.0 if probe.z < (centre-mid).z else -1.0
        h = self.hinge(f'{name} hinge', mid, axis, 0.0, sign*open_deg, DOOR_SPAN,
                       note='built shut; turns to deployed_deg about local Y to open (it swings down from its hinge edge)')
        h.rotation_euler[1] = 0.0
        self.adopt(h, [ob])
        rec['doors'].append(ob.name)
        return ob

    def u(self, metres):
        return metres/self.mpu

    # --- Shapes -------------------------------------------------------------------

    def beam(self, name, p0, p1, side, s0, s1, mat, ch=.25, bevel_m=.012):
        """A tapered, chamfered box-section member from p0 to p1: legs, links, braces.
        side: the direction of the width (made square to the axis); s0, s1: (width, depth) at each end."""
        a, b = Vector(p0), Vector(p1)
        ax = (b-a).normalized()
        w = Vector(side)
        w = (w-ax*w.dot(ax)).normalized()
        dpt = ax.cross(w)
        verts = []
        for p, (wd, dp) in ((a, s0), (b, s1)):
            c = min(wd, dp)*ch
            for sw, sd in ((-1, -1+2*c/dp), (-1+2*c/wd, -1), (1-2*c/wd, -1), (1, -1+2*c/dp),
                           (1, 1-2*c/dp), (1-2*c/wd, 1), (-1+2*c/wd, 1), (-1, 1-2*c/dp)):
                verts.append(p+w*sw*wd/2+dpt*sd*dp/2)
        faces = [tuple(range(8)), tuple(range(8, 16))]+[(i, (i+1) % 8, 8+(i+1) % 8, 8+i) for i in range(8)]
        return self.kit.mesh(name, [tuple(v) for v in verts], faces, mat, self.u(bevel_m))

    def pin(self, name, centre, axis, r_m, length, mat):
        """A hinge pin or knuckle: a round bar along axis, segment count from the detail rule."""
        n = max(12, 4*math.ceil(math.pi/math.acos(1-.005/max(r_m, .006))/4))
        ob = self.kit.cylinder(name, (0, 0, 0), self.u(r_m), length, mat, 'Z', n, self.u(.004))
        ob.matrix_world = Matrix.Translation(Vector(centre)) @ Vector(axis).to_track_quat('Z', 'Y').to_matrix().to_4x4()
        return ob

    def wedge(self, name, sole, toe, side, L, W, H, mat, toe_run=.38, heel_run=.22, top_w=.72, bevel_m=.015,
              claws=()):
        """A wedge shoe: a flat sole L x W on the deck, its toe and heel raked up to a narrower top.
        sole: centre of the sole; toe: its forward direction (flat); claws: (y offset, length, width)
        teeth carried forward of the toe, their undersides on the sole plane."""
        x = Vector(toe).normalized()
        y = Vector(side)
        y = (y-x*y.dot(x)).normalized()
        z = x.cross(y)
        if z.z < 0:   # the sole is down whichever way the side was given
            y, z = -y, -z
        o = Vector(sole)
        P = lambda a, b, c: tuple(o+x*a+y*b+z*c)
        verts = [P(-L/2, -W/2, 0), P(L/2, -W/2, 0), P(L/2, W/2, 0), P(-L/2, W/2, 0)]
        tx0, tx1, tw = -L/2+L*heel_run, L/2-L*toe_run, W*top_w/2
        verts += [P(tx0, -tw, H), P(tx1, -tw, H), P(tx1, tw, H), P(tx0, tw, H)]
        faces = [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]
        for cy, cl, cw in claws:
            b = len(verts)
            hx = L/2-L*toe_run*.55
            hz = H*.55
            verts += [P(hx, cy-cw/2, 0), P(L/2+cl, cy-cw*.12, 0), P(L/2+cl, cy+cw*.12, 0), P(hx, cy+cw/2, 0),
                      P(hx, cy-cw*.32, hz), P(L/2+cl*.55, cy-cw*.1, hz*.35), P(L/2+cl*.55, cy+cw*.1, hz*.35),
                      P(hx, cy+cw*.32, hz)]
            faces += [(b, b+3, b+2, b+1), (b+4, b+5, b+6, b+7), (b, b+1, b+5, b+4), (b+1, b+2, b+6, b+5),
                      (b+2, b+3, b+7, b+6), (b+3, b, b+4, b+7)]
        return self.kit.mesh(name, verts, faces, mat, self.u(bevel_m))

    def talon(self, name, root, out, side, L, W, H, mat, bevel_m=.01):
        """A curved claw tooth: from a root block on the foot, out along `out` (flat) to a point on the
        deck. Its underside lies on the sole plane when deployed."""
        x = Vector(out).normalized()
        y = Vector(side)
        y = (y-x*y.dot(x)).normalized()
        z = x.cross(y)
        if z.z < 0:
            y, z = -y, -z
        o = Vector(root)
        P = lambda a, b, c: tuple(o+x*a+y*b+z*c)
        rows = [(0.0, W/2, 0.0, H), (L*.45, W*.38, 0.0, H*.72), (L*.8, W*.2, 0.0, H*.35), (L, W*.05, 0.0, H*.08)]
        verts, faces = [], []
        for a, hw, z0, z1 in rows:
            verts += [P(a, -hw, z0), P(a, hw, z0), P(a, hw*.6, z1), P(a, -hw*.6, z1)]
        faces += [(0, 3, 2, 1), (12, 13, 14, 15)]
        for r in range(3):
            q = 4*r
            faces += [(q+i, q+4+i, q+4+(i+1) % 4, q+(i+1) % 4) for i in range(4)]
        return self.kit.mesh(name, verts, faces, mat, self.u(bevel_m))

    def strut(self, name, top, bottom, side, w, d, ankle=True):
        """A box-section shock strut from its hinge trunnion (top) to its ankle (bottom): a broad upper
        sleeve, a short bright slide, a narrower lower sleeve and a clevis at the ankle. side: the
        direction of the width w (the hinge axis for a leg that folds about it); d: the depth."""
        k = self.kit
        a, b = Vector(top), Vector(bottom)
        ax = (b-a).normalized()
        L = (b-a).length
        sd = Vector(side)
        sd = (sd-ax*sd.dot(ax)).normalized()
        at = lambda f: a+ax*L*f
        parts = [
            self.beam(f'{name} trunnion', a-sd*w*.62, a+sd*w*.62, ax, (d*1.05, d*.95), (d*1.05, d*.95), k.steel, .3),
            self.pin(f'{name} hinge pin', a, sd, self.mpu*d*.2, w*1.5, k.bright),
            self.beam(f'{name} upper sleeve', at(.02), at(.62), sd, (w, d), (w*.86, d*.86), k.steel),
            self.beam(f'{name} slide', at(.58), at(.74), sd, (w*.6, d*.6), (w*.6, d*.6), k.bright, .2),
            self.beam(f'{name} lower sleeve', at(.7), b, sd, (w*.74, d*.74), (w*.68, d*.7), k.steel),
            self.beam(f'{name} sleeve collar', at(.6), at(.66), sd, (w*.96, d*.96), (w*.96, d*.96), k.dark, .3),
        ]
        if ankle:
            for s_, sgn in (('port', 1), ('starboard', -1)):
                c = b+sd*sgn*w*.42
                parts.append(self.beam(f'{name} ankle cheek {s_}', c-ax*d*.15, c+ax*d*.55, sd, (w*.16, d*.9),
                                       (w*.16, d*.75), k.steel, .2))
            parts.append(self.pin(f'{name} ankle pin', b+ax*d*.25, sd, self.mpu*d*.16, w*1.02, k.bright))
        return parts

    # --- Checks -------------------------------------------------------------------

    def parts_of(self, rec):
        return [o for n in rec['hinges'] for o in bpy.data.objects[n].children_recursive if o.type == 'MESH']

    def sweep(self, meshes, knobs=SWEEP_KNOBS, tol_m=.01):
        """The clash check. Returns {'clashes': [...], 'stowed_outside': [...], 'knobs': [...]}."""
        dg = bpy.context.evaluated_depsgraph_get()
        tol = self.u(tol_m)

        def tree(ob):
            ev = ob.evaluated_get(dg)
            me = ev.to_mesh()
            t = BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices],
                                     [tuple(p.vertices) for p in me.polygons])
            ev.to_mesh_clear()
            return t

        by_name = {o.name: o for o in meshes}
        moving = set()
        units = []
        for rec in self.legs:
            legp = {o.name for o in self.parts_of(rec)}
            doors = set(rec['doors'])
            moving |= legp | doors
            units.append((rec, legp, doors))
        static = [o for o in meshes if o.name not in moving]
        clashes = set()
        for k in knobs:
            self.pose(k)
            dg = bpy.context.evaluated_depsgraph_get()
            trees = {o.name: tree(o) for o in meshes}
            for rec, legp, doors in units:
                allowed = legp | set(rec['hosts']) | set(rec['liners'])
                for a in sorted(legp):
                    for b, tb in trees.items():
                        if b not in allowed and trees[a].overlap(tb):
                            clashes.add((rec['name'], a, b, k))
                for a in sorted(doors):
                    ok = doors | set(rec['hosts']) | set(rec['liners'])
                    for b, tb in trees.items():
                        if b not in ok and trees[a].overlap(tb):
                            clashes.add((rec['name'], a, b, k))
        # Stowed: every leg vertex inside the skin, between the hosts' (or doors') underside and top.
        self.pose(0.0)
        dg = bpy.context.evaluated_depsgraph_get()
        outside = []

        def first(obs, p, d):
            best = None
            for ob in obs:
                ev = ob.evaluated_get(dg)
                inv = ev.matrix_world.inverted()
                hit, q, _, _ = ev.ray_cast(inv @ p, (inv.to_3x3() @ d).normalized())
                if hit:
                    z = (ev.matrix_world @ q).z
                    best = z if best is None else (min(best, z) if d.z > 0 else max(best, z))
            return best

        for rec, legp, doors in units:
            hosts = [by_name[n] for n in rec['hosts']]
            under = hosts+[by_name[n] for n in doors]
            worst = 0.0
            for name in sorted(legp):
                ev = by_name[name].evaluated_get(dg)
                me = ev.to_mesh()
                for v in me.vertices:
                    p = ev.matrix_world @ v.co
                    lo = rec['floor'] if rec['floor'] is not None else first(under, Vector((p.x, p.y, -60)), Vector((0, 0, 1)))
                    hi = first(hosts, Vector((p.x, p.y, 60)), Vector((0, 0, -1)))
                    if lo is None or hi is None:
                        worst = max(worst, 99.0)
                        continue
                    worst = max(worst, lo-tol-p.z, p.z-hi-tol)
                ev.to_mesh_clear()
            if worst > 0:
                outside.append((rec['name'], round(worst*self.mpu, 3)))
        return {'knobs': list(knobs), 'clashes': sorted(clashes, key=lambda c: (c[0], c[3], c[1], c[2])),
                'stowed_outside_m': outside}


def control_name(name):
    return f'{name} control'


def verify_drivers(gear, control, knobs=(0.0, .2, .5, .8, 1.0)):
    """Largest difference (radians) between what the drivers pose and fg_gear's own arithmetic."""
    worst = 0.0
    for k in knobs:
        control[KNOB] = k
        control.update_tag()
        for h in gear.hinges:
            h.rotation_euler[1] = 7.0   # nonsense: only the driver can put it right
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        for h in gear.hinges:
            got = h.evaluated_get(dg).rotation_euler[1]
            worst = max(worst, abs(got-math.radians(gear.angle(h, k))))
    gear.pose(control[KNOB])
    return worst


def summary(sweep):
    lines = [f"gear sweep over {len(sweep['knobs'])} knob values: {len(sweep['clashes'])} clashes, "
             f"{len(sweep['stowed_outside_m'])} stowed units outside the skin"]
    lines += [f'    clash  {u}: {a} | {b} at {k}' for u, a, b, k in sweep['clashes']]
    lines += [f'    outside  {u}: {m} m' for u, m in sweep['stowed_outside_m']]
    return '\n'.join(lines)


def manifest(gear, control, sweep, saved, notes=''):
    return {
        'control': control.name, 'knob': KNOB,
        'knob_meaning': '0 stowed with the doors shut (flight); 1 deployed, standing on the parked deck',
        'saved_knob': saved,
        'spans': {'doors': list(DOOR_SPAN), 'legs': list(LEG_SPAN), 'claws': list(CLAW_SPAN)},
        'hinges': [{'name': h.name, 'mount': h.parent.name if h.parent and h.parent.name.endswith(' mount') else None,
                    'axis': 'local Y', 'stowed_deg': round(h['stowed_deg'], 3), 'deployed_deg': round(h['deployed_deg'], 3),
                    'knob_span': list(h['knob_span'])} for h in gear.hinges],
        'units': [{'name': r['name'], 'hinges': r['hinges'], 'bay_in': r['hosts'], 'doors': r['doors'],
                   'liners': r['liners'], **({'stowed_floor_z': r['floor']} if r['floor'] is not None else {})}
                  for r in gear.legs],
        'clash_sweep': {'knobs': sweep['knobs'], 'clashes': [list(c) for c in sweep['clashes']],
                        'stowed_outside_m': sweep['stowed_outside_m'],
                        'rule': 'no moving part through anything but its own bay solids and liners, no leg through '
                                'its own doors, and stowed every leg vertex between the skin\'s underside and top'},
        'pose_reference': 'fg_gear.Gear.pose(k); the drivers on each hinge evaluate the same expression',
        **({'notes': notes} if notes else {}),
    }
