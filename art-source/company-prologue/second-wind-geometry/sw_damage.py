"""Scars and condition states for the Second Wind.

The hull's campaign life is one ladder of builds of the same model (see
second-wind-pipeline.md):

derelict  ~8 years tethered in the belt as an unpowered parts bin.
patched   the escape: only what the flight needed.
refit     the limited berthing/command conversion: a few patches replaced by
          proper plates with fresh seams; heavy craters and the dead port pod
          remain.
restored  structurally whole, both pods running, replaced plates repainted;
          old seams and scars still read under the paint.

clean is the reference and never runs this module. apply(kit, state) is the
last phase of build_reference.py, so surfacing can be laid on the clean parts
before scars cut them.

Every damage site is one Scar row: a stable id, a kind, a status per state and
the parameters that place it. Placement is resolved by ray casts against the
clean ship and every change to a base object is deferred to the end of the
build, so every cast in every state sees the same clean ship and a scar id
lands in the same place in all four states.

Rules that keep the surfacing composable:
- A base object is never renamed, replaced or deleted. Scars Boolean-cut it,
  deform it with a shape key named 'Scar <id>', hide it (a part stripped or
  replaced; the object and its skin stay in the file), or override its look
  with an object-level material slot (the mesh keeps its base material).
- Everything added is named 'Scar <id> <part>' and takes scar or repair
  materials (named 'Scar ...', 'Salvage ...', 'Weld ...' etc.), never a base
  material. Faces a cut opens take the cutter's material.

Rows live in one module per damage area (AREAS), each owning its rows and any
kinds of its own; the generic kinds here cover the common scar shapes. Laws
(owner decisions): damage never moves or removes the bays, the four turret
slots, the four engine centres, the grapple module or the silhouette; no open
breach into occupied spaces, no fire, no new engines. Construction stays
faceted and crisp.
"""
import importlib
import math
import random
import bpy
import bmesh
from mathutils import Vector
import sw_kit
from sw_hull import armor_patch, crisp
from sw_turrets import MOUNTS

TAU = math.tau
# Cut material meaning 'repaint of whichever base part this cut opens'.
REPAINT = 'repaint'

STATES = ('derelict', 'patched', 'refit', 'restored')
# A scar's life. A ladder never goes backwards; 'gone' is terminal.
SCAR_STATUSES = ('open', 'patched', 'replaced', 'faded', 'gone')
# A drive pod's life: stripped and dead, cold with its lines capped, running
# on jury-rigged salvage, running properly.
ENGINE_STATUSES = ('dead', 'capped', 'jury', 'running')
AREAS = ('sw_scars_forebody', 'sw_scars_pods', 'sw_scars_aft', 'sw_scars_spine', 'sw_scars_rcs', 'sw_scars_dorsal')

# Nothing a strike or cut may land on or reach into.
UNCUT = ('Turret', 'Scar', 'RCS ', 'Grapple arm', 'Boom', 'Forearm', 'Arm ', 'Engine ',
         'Shoulder', 'Grapple head', 'Grapple jaw', 'Grapple clamp', 'Grapple wrist')
NO_STRIKE = UNCUT + ('Mast', 'Whip', 'Scanner', 'radome', 'Radome', 'viewport', 'Viewport',
                     'glazing', 'light', 'Light', 'lamp', 'Bay ', 'bay ', 'cradle', 'Cradle',
                     'Hoist', 'Winch', 'winch', 'Davit', 'Sponson knee', 'Heat-sink fin')


def V(t):
    return Vector(t)


def basis(n):
    ref = Vector((0,0,1)) if abs(n.z) < .9 else Vector((1,0,0))
    u = n.cross(ref).normalized()
    return u, n.cross(u).normalized()


class Scar:
    """One damage site and its status in each state.

    ladder: four statuses, derelict..restored, as a list or a spaced string.
    vocab: SCAR_STATUSES (ordered, never backwards) or ENGINE_STATUSES."""
    def __init__(self, id, kind, ladder, note='', vocab=SCAR_STATUSES, **params):
        ladder = ladder.split() if isinstance(ladder, str) else list(ladder)
        assert len(ladder) == len(STATES), (id, ladder)
        assert all(s in vocab for s in ladder), (id, ladder)
        if vocab is SCAR_STATUSES:
            order = [vocab.index(s) for s in ladder]
            assert order == sorted(order), f'{id}: ladder goes backwards {ladder}'
        self.id, self.kind, self.note, self.params = id, kind, note, params
        self.states = dict(zip(STATES, ladder))
        self.area = None

    def status(self, state):
        return self.states[state]

    def history(self, state):
        """Statuses this scar has passed through up to and including state."""
        return [self.states[s] for s in STATES[:STATES.index(state)+1]]


class Site:
    """A resolved hit on the clean ship: point, outward normal, track (u),
    cross-track (v) and the object hit."""
    def __init__(self, p, n, u, v, ob):
        self.p, self.n, self.u, self.v, self.ob = p, n, u, v, ob

    def __iter__(self):
        return iter((self.p, self.n, self.u, self.v, self.ob))


BEND_STEP = 7.0   # degrees: a tube's path turns no more than this between rings


def bend(path):
    """A tube path with every bend rounded (Catmull-Rom through the given points),
    so a cable or pipe turns through rings no more than BEND_STEP apart, as the
    segment rule asks of any round, instead of kinking at each point."""
    pts = [V(p) for p in path]
    if len(pts) < 3:
        return pts
    out = [pts[0]]
    for i in range(len(pts)-1):
        p0, p1, p2 = pts[max(i-1, 0)], pts[i], pts[i+1]
        p3 = pts[min(i+2, len(pts)-1)]
        turn = 0.0
        for a, b, c in ((p0, p1, p2), (p1, p2, p3)):
            u, w = b-a, c-b
            if u.length > 1e-6 and w.length > 1e-6:
                turn = max(turn, math.degrees(u.angle(w)))
        k = max(1, math.ceil(turn/BEND_STEP))
        for j in range(1, k+1):
            t = j/k
            out.append(.5*((2*p1)+(-p0+p2)*t+(2*p0-5*p1+4*p2-p3)*t*t+(-p0+3*p1-3*p2+p3)*t*t*t))
    return out


class Geo:
    """World-space polygon soup gathered for one object."""
    def __init__(self):
        self.v, self.f = [], []

    def add(self, verts, faces):
        o = len(self.v)
        self.v += [tuple(p) for p in verts]
        self.f += [tuple(i+o for i in face) for face in faces]

    def loft(self, rings):
        n = len(rings[0])
        verts = [p for r in rings for p in r]
        faces = [(r*n+i, r*n+(i+1) % n, (r+1)*n+(i+1) % n, (r+1)*n+i)
                 for r in range(len(rings)-1) for i in range(n)]
        faces += [tuple(reversed(range(n))), tuple(range((len(rings)-1)*n, len(rings)*n))]
        self.add(verts, faces)

    def obox(self, c, ax, ay, az, hx, hy, hz):
        c = V(c)
        pts = [c+ax*sx*hx+ay*sy*hy+az*sz*hz for sz in (-1,1)
               for sx,sy in ((-1,-1),(1,-1),(1,1),(-1,1))]
        self.add(pts, [(3,2,1,0),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)])

    def bar(self, a, b, up, w, h):
        """Flat bar from a to b, lying with its face toward `up`."""
        a, b = V(a), V(b)
        ax = (b-a).normalized()
        side = V(up).cross(ax).normalized()
        self.obox((a+b)/2, ax, side, ax.cross(side).normalized(), (b-a).length/2, w/2, h/2)

    def tube(self, path, r, sides=8):
        sides = sw_kit.sides(r, sides)      # the shared segment rule, round and along its bends
        path = bend(path)
        rings = []
        for i,p in enumerate(path):
            t = (path[min(i+1,len(path)-1)]-path[max(i-1,0)]).normalized()
            u, v = basis(t)
            rings.append([p+(u*math.cos(TAU*k/sides)+v*math.sin(TAU*k/sides))*r for k in range(sides)])
        self.loft(rings)

    def disc(self, c, axis, r, depth, sides=12):
        if sides != 6:
            sides = sw_kit.sides(r, sides)
        c, axis = V(c), V(axis).normalized()
        u, v = basis(axis)
        ring = [c+(u*math.cos(TAU*(k+.5)/sides)+v*math.sin(TAU*(k+.5)/sides))*r for k in range(sides)]
        self.loft([[p-axis*depth/2 for p in ring], [p+axis*depth/2 for p in ring]])

    def bead(self, a, b, n, size=.12):
        """Fillet weld from a to b: a low triangular ridge with a slight ripple."""
        a, b, n = V(a), V(b), V(n)
        length = (b-a).length
        if length < .05:
            return
        t = (b-a)/length
        side = n.cross(t).normalized()
        count = max(2, round(length/.3))
        rings = []
        for k in range(count+1):
            c = a+t*(length*k/count)
            h = size*.7*(1+.12*((k*5) % 3-1))
            rings.append([c+side*size, c+n*h, c-side*size, c-n*size*.3])
        self.loft(rings)

    def flat(self, ring, n, thick):
        """A flat plate on a ring of points."""
        k = len(ring)
        verts = list(ring)+[q+n*thick for q in ring]
        faces = [tuple(reversed(range(k))), tuple(range(k, 2*k))]
        faces += [(i, (i+1) % k, k+(i+1) % k, k+i) for i in range(k)]
        self.add(verts, faces)

    def bounds(self):
        xs, ys, zs = zip(*self.v)
        return V((min(xs),min(ys),min(zs))), V((max(xs),max(ys),max(zs)))


class Damage:
    def __init__(self, kit, state):
        assert state in STATES, state
        self.kit, self.state = kit, state
        self.original = {o.name for o in bpy.data.objects if o.type == 'MESH'}
        self.cuts = []      # (verts, faces, explicit targets or None, material)
        self.parts = {}     # object name -> (Geo, material, edge)
        self.hides = {}     # base object name -> scar id
        self.shapes = []    # (object, scar id, {vertex index: local co})
        self.overrides = [] # (object, material, scar id)
        self.tags = []      # (object, key, value, scar id)
        self.anchors = []   # (name, position, direction, props)
        self.sites = {}     # scar id -> Site or None (resolved once)
        self.record = {'state': state, 'scars': [], 'hidden': {}, 'shape_keys': [],
                       'overrides': []}
        self._torn, self._repaint = {}, {}
        m = sw_kit.material
        self.m = m
        self.backing = m('Scar spalled backing', (.075,.07,.065), .6, .55)
        self.scorch = m('Scar scorched plating', (.03,.026,.024), .25, .85)
        self.weld = m('Weld bead', (.12,.11,.1), .75, .7)
        self.salvage = [m('Salvage plate grey-green primer', (.2,.23,.19), .35, .6),
                        m('Salvage plate bare steel', (.34,.34,.33), .8, .4),
                        m('Salvage plate faded blue-grey', (.17,.2,.24), .4, .6),
                        m('Salvage plate chalk cream', (.5,.47,.38), .3, .65)]
        self.primer = m('Replacement plate shop primer', (.36,.37,.35), .35, .55)
        self.sealant = m('Replacement seam sealant', (.6,.6,.57), 0, .7)
        self.fastener = m('Replacement plate fastener', (.42,.42,.4), .85, .35)
        self.steel = m('Scar fitting steel', (.19,.21,.22), .65)
        self.dark = m('Scar recess dark', (.055,.065,.072), .5)
        self.rail = m('Cargo rail safety yellow', (.5,.36,.05), .35, .55)
        self.copper = m('Salvaged drive coil copper', (.5,.22,.09), .9, .35)
        self.cable = m('External cable run', (.03,.03,.03), 0, .6)
        self.dead = m('Scar dead lamp', (.08,.07,.06), .3, .4)
        self.unlit_glass = m('Scar unlit glazing', (.05,.09,.11), .2, .15)
        self.rubber = m('Borrowed seal rubber', (.05,.045,.04), 0, .9)
        self.glow = m('Drive throat glow', (.3,.5,.8), emission=(.45,.68,1))
        self.glow.node_tree.nodes['Principled BSDF'].inputs['Emission Strength'].default_value = 7
        self.heat = m('Drive bell heat', (.05,.055,.07), .5, .4, emission=(.25,.4,.9))
        self.heat.node_tree.nodes['Principled BSDF'].inputs['Emission Strength'].default_value = .18

    # ---- scar materials -----------------------------------------------------
    def base_mat(self, ob):
        mat = ob.active_material if ob is not None else None
        return mat or self.kit.orange

    @staticmethod
    def colour(base):
        """A material's flat colour: its BSDF base, or for a turret-kit paint (whose
        base colour is a node tree) the calibrated colour it carries as diffuse."""
        if 'paint_target' in base:
            return tuple(base.diffuse_color[:3])
        return tuple(base.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value[:3])

    def torn(self, base):
        """Torn painted metal from a base part: a scar material that matches it."""
        if base.name not in self._torn:
            col = self.colour(base)
            self._torn[base.name] = self.m(f'Scar torn {base.name}', tuple(col[:3]), .4, .6)
        return self._torn[base.name]

    def repaint(self, base):
        """New paint matched by eye to a base finish: close, never the same."""
        if base.name not in self._repaint:
            r, g, b = self.colour(base)
            self._repaint[base.name] = self.m(f'Scar repaint over {base.name}',
                                              (r*1.08+.01, g*1.05+.008, b*.97+.006), .3, .55)
        return self._repaint[base.name]

    # ---- queries ---------------------------------------------------------
    def find(self, name, near, sign=0):
        best = None
        for o in bpy.data.objects:
            if not o.name.startswith(name) or o.name not in self.original:
                continue
            c = o.matrix_world @ (sum((V(b) for b in o.bound_box), Vector())/8)
            if sign and c.y*sign <= 0:
                continue
            d = (c.xy-V(near[:2])).length if near else 0
            if best is None or d < best[0]:
                best = (d, o)
        return best[1] if best else None

    def named(self, name, sign=0):
        out = []
        for o in bpy.data.objects:
            if o.name.startswith(name) and o.name in self.original:
                c = o.matrix_world @ (sum((V(b) for b in o.bound_box), Vector())/8)
                if not sign or c.y*sign > 0:
                    out.append(o)
        return out

    def box(self, ob):
        """World bounding box (lo, hi) of an object."""
        pts = [ob.matrix_world @ V(c) for c in ob.bound_box]
        return (V([min(q[k] for q in pts) for k in range(3)]),
                V([max(q[k] for q in pts) for k in range(3)]))

    def cast(self, origin, target, only=None):
        """First hit on the clean ship from origin toward target: (point, normal, object).
        `only` restricts hits to those object names; anything else is passed through."""
        o, d = V(origin), (V(target)-V(origin)).normalized()
        dg = bpy.context.evaluated_depsgraph_get()
        for _ in range(40):
            ok, p, n, _, ob, _ = bpy.context.scene.ray_cast(dg, o, d)
            if not ok:
                return None
            if ob.name in self.original and (only is None or ob.name in only):
                return p, (n if n.dot(d) < 0 else -n), ob
            o = p+d*.02
        return None

    def ground(self, x, y):
        hit = self.cast((x,y,60), (x,y,-20))
        return hit[0].z if hit else 12

    def snap(self, point, n, lift=0, strict=False):
        hit = self.cast(point+n*2.5, point-n*1.5)
        if hit and (hit[0]-point).length < 2.5:
            return hit[0]+n*lift
        return None if strict else point+n*lift

    @property
    def skin(self):
        """Outer skin and armour a plate may rest on (no lamps, hatches, vents)."""
        keep = ('hull', 'armor', 'armour', 'plate', 'cowl', 'casing', 'module', 'cap', 'body',
                'Sponson', 'sponson', 'housing', 'Spine', 'block', 'belt', 'lip', 'shield')
        drop = ('Scar', 'lamp', 'light', 'hatch', 'Hatch', 'vent', 'louvre', 'louver', 'strap',
                'hood', 'Intake mouth', 'manifold', 'frame', 'skin')
        return {n for n in self.original if any(k in n for k in keep) and not any(d in n for d in drop)}

    def site(self, scar, origin, target, key=None):
        """Resolve (once) where a ray from origin toward target meets the clean
        ship. u follows the ray's ground track across the surface."""
        key = key or scar_id(scar)
        if key not in self.sites:
            hit = self.cast(origin, target)
            if hit is None:
                print('scar: no surface for', key)
                self.sites[key] = None
            else:
                p, n, ob = hit
                aim = (V(target)-V(origin)).normalized()
                track = aim-n*aim.dot(n)
                u = track.normalized() if track.length > .2 else basis(n)[0]
                self.sites[key] = Site(p, n, u, n.cross(u), ob)
        return self.sites[key]

    # ---- deferred changes ----------------------------------------------------
    def part(self, scar, part, mat, edge=.04):
        """Geometry for an added object named 'Scar <id> <part>'."""
        name = f'Scar {scar_id(scar)} {part}'
        if name not in self.parts:
            self.parts[name] = (Geo(), mat, edge)
        return self.parts[name][0]

    def cut(self, geo, targets=None, mat=None):
        """Boolean-subtract a closed Geo from base objects (the ones its bounds
        touch, or explicit target names). The opened faces take mat."""
        self.cuts.append((geo.v, geo.f, targets, mat or self.backing))

    def hide(self, ob, scar):
        """A base part absent in this state: hidden, never deleted."""
        if ob is not None:
            self.hides[ob.name] = scar_id(scar)

    def shape(self, ob, scar, fn):
        """Deform a base object with a shape key 'Scar <id>'. fn maps a world
        point to its new world point."""
        if ob.data.users > 1:
            print('scar: shape key on shared mesh', ob.name, 'skipped')
            return
        mw = ob.matrix_world
        inv = mw.inverted()
        moved = {}
        for vert in ob.data.vertices:
            w = mw @ vert.co
            q = fn(w)
            if (q-w).length > 1e-6:
                moved[vert.index] = inv @ q
        if moved:
            self.shapes.append((ob, scar_id(scar), moved))

    def anchor(self, scar, label, position, direction, **props):
        """An effect anchor owned by a scar: an empty 'Scar <id> <label>
        anchor' at position, local +X along direction (a spark spray, say),
        carrying props. Created at the end with the scar's other parts.
        Spark anchors use label 'spark', 'spark-2', ... and a 'sparking'
        prop for this state."""
        self.anchors.append((f'Scar {scar_id(scar)} {label} anchor', V(position),
                             V(direction).normalized(), dict(props, scar=scar_id(scar))))

    def tag(self, ob, key, value, scar):
        """Set a custom property on a base object (an anchor's 'running' or
        'working' for this state). Metadata only, applied at the end."""
        self.tags.append((ob, key, value, scar_id(scar)))

    def override(self, ob, mat, scar):
        """Change a base part's look for this state without touching its mesh
        material: an object-level material slot."""
        self.overrides.append((ob, mat, scar_id(scar)))

    # ---- shared scar primitives -------------------------------------------------
    def basin_cut(self, scar, site, r, d, elong=1., sides=8, mat=None, key=None):
        """A faceted impact basin, its floor downrange of the entry."""
        p, n, u, v, _ = site
        rng = random.Random(key or scar_id(scar))
        down = u*r*.18
        def ring(rad, h, jit, shift):
            pts = []
            for k in range(sides):
                a = TAU*(k+rng.uniform(-jit,jit))/sides
                rr = rad*rng.uniform(1-jit*.7, 1+jit*.7)
                pts.append(p+shift+u*math.cos(a)*rr*elong+v*math.sin(a)*rr+n*h)
            return pts
        geo = Geo()
        geo.loft([ring(r*1.18,.55,.22,V((0,0,0))), ring(r*.62,-d*.55,.28,down*.6), ring(r*.26,-d,.3,down)])
        self.cut(geo, None, mat)

    def lip_shards(self, scar, site, r, elong, shards, key=None):
        """Crushed-lip shards round a basin, in torn base paint."""
        p, n, u, v, ob = site
        rng = random.Random((key or scar_id(scar))+' lip')
        lips = self.part(scar, 'crushed lip', self.torn(self.base_mat(ob)), .03)
        for k in range(shards):
            a = TAU*(k+.5+rng.uniform(-.2,.2))/shards
            span = TAU/shards*rng.uniform(.55,.85)
            lift = r*rng.uniform(.1,.28)
            r0, r1 = r*rng.uniform(.86,.98), r*rng.uniform(1.3,1.6)
            at = lambda rad, ang: p+u*math.cos(ang)*rad*elong+v*math.sin(ang)*rad
            outer = [self.snap(at(r1,a+span*.3),n,-.06,True), self.snap(at(r1,a-span*.3),n,-.06,True)]
            if None in outer or any((q-p).dot(n) < -r*.5 for q in outer):
                continue  # off the edge of a curved part: no lip to crush
            top = [at(r0,a-span/2)+n*lift*rng.uniform(.55,.9), at(r0*.92,a)+n*lift*1.15,
                   at(r0,a+span/2)+n*lift*rng.uniform(.55,.9)]+outer
            lips.loft([[q-n*.17 for q in top], top])

    def grid_plate(self, scar, part, p, n, u, v, a, b, mat, cut=.14, lift=.07, thick=.12, k=3,
                   only=None, weld=True):
        """A cut sheet brake-bent onto the original skin line.

        The plate folds only along lines across its curving direction, so it
        is k flat strips with crisp bends, never a dome; corners are clipped.
        Each fold line sits at the highest skin under it, so the sheet
        bridges dips and never sinks into a crater or a seam. Returns the rim."""
        def height(q):
            hit = self.cast(q+n*6, q-n*6, only)
            return (hit[0]-p).dot(n) if hit else None
        def profile(axis, across, half, other):
            hs = []
            for i in range(k+1):
                s = 2*i/k-1
                got = [height(p+axis*s*half+across*t*other) for t in (-1,-.67,-.33,0,.33,.67,1)]
                got = [h for h in got if h is not None]
                hs.append(max(got) if got else None)
            for i in range(len(hs)):
                if hs[i] is None:
                    near = sorted((abs(i-j), h) for j, h in enumerate(hs) if h is not None)
                    hs[i] = near[0][1] if near else 0
            return hs
        hu, hv = profile(u, v, a, b), profile(v, u, b, a)
        if max(hv)-min(hv) > max(hu)-min(hu):
            u, v, a, b, hs = v, u, b, a, hv
        else:
            hs = hu
        rows = []
        for i in range(k+1):
            s = 2*i/k-1
            w = b*(1-cut) if i in (0, k) else b
            c = p+u*a*s+n*(hs[i]+lift)
            rows.append((c-v*w, c+v*w))
        verts = [q for r in rows for q in r]+[q+n*thick for r in rows for q in r]
        m = 2*(k+1)
        faces = []
        for i in range(k):
            a0, a1, b0, b1 = 2*i, 2*i+1, 2*i+2, 2*i+3
            faces += [(a0,b0,b1,a1), (m+a0,m+a1,m+b1,m+b0), (a0,m+a0,m+b0,b0), (a1,b1,m+b1,m+a1)]
        faces += [(0,1,m+1,m), (m-2,m+m-2,m+m-1,m-1)]
        self.part(scar, part, mat, .05).add(verts, faces)
        rim = [r[0] for r in rows]+[r[1] for r in reversed(rows)]
        if weld:
            beads = self.part(scar, 'weld bead', self.weld, .01)
            for q0, q1 in zip(rim, rim[1:]+rim[:1]):
                beads.bead(q0-n*lift, q1-n*lift, n, .1)
        return rim, n

    def bolted_plate(self, scar, part, ring, n, mat, thick=.09, fastener=None):
        """A flat plate on a ring of points, bolted round its edge."""
        self.part(scar, part, mat, .03).flat(ring, n, thick)
        studs = self.part(scar, 'bolt heads', fastener or self.steel, .005)
        k = len(ring)
        centre = sum(ring, Vector())/k
        for i in range(k):
            a, b = ring[i], ring[(i+1) % k]
            for t in (.2,.4,.6,.8):
                studs.disc(a.lerp(b, t).lerp(centre, .1)+n*(thick+.02), n, .08, .07, 6)

    def salvage_plate(self, scar, site, a, b, key=None):
        """Thin salvage sheet squared to the hull's lines, welded all round."""
        p, n, u, v, _ = site
        rng = random.Random(key or scar_id(scar))
        turn = rng.uniform(-.07,.07)
        uu, vv = u*math.cos(turn)+v*math.sin(turn), v*math.cos(turn)-u*math.sin(turn)
        mat = self.salvage[sum(map(ord, key or scar_id(scar))) % len(self.salvage)]
        self.grid_plate(scar, 'salvage plate', p, n, uu, vv, a, b, mat, only=self.skin)

    def pressure_patch(self, scar, site, r, key=None):
        """Bolted square patch over a strike into occupied space, a borrowed
        hab-door gasket squeezed out under its edge. Brake-bent onto the
        skin like any plate, so it stays flat on an uneven roof."""
        p, n, u, v, _ = site
        half = r*1.4+.25
        self.grid_plate(scar, 'seal gasket', p, n, u, v, half+.12, half+.12, self.rubber,
                        cut=.1, lift=.01, thick=.06, only=self.skin, weld=False)
        mat = self.salvage[sum(map(ord, key or scar_id(scar))) % 3]
        rim, n = self.grid_plate(scar, 'pressure patch', p, n, u, v, half, half, mat,
                                 cut=.1, lift=.06, thick=.09, only=self.skin, weld=False)
        studs = self.part(scar, 'bolt heads', self.steel, .005)
        centre = sum(rim, Vector())/len(rim)
        for q0, q1 in zip(rim, rim[1:]+rim[:1]):
            steps = max(2, round((q1-q0).length/.45))
            for i in range(steps):
                q = q0.lerp(q1, (i+.5)/steps).lerp(centre, .1)
                studs.disc(q+n*.11, n, .08, .07, 6)

    def panel(self, scar, site, a, b, status, history, part='replacement plate'):
        """A proper replacement plate laid flush over a repaired area.

        replaced: shop primer, fresh sealant seams, bright fasteners.
        faded:    repainted to match the part it sits on, seams still visible."""
        p, n, u, v, ob = site
        base = self.base_mat(ob)
        mat = self.primer if status == 'replaced' else self.repaint(base)
        rim, n = self.grid_plate(scar, part, p, n, u, v, a, b, mat, cut=.06, lift=.02,
                                 thick=.07, only=self.skin, weld=False)
        lift = .02
        seam = self.part(scar, 'seam', self.sealant if status == 'replaced' else self.repaint(base), .005)
        for q0, q1 in zip(rim, rim[1:]+rim[:1]):
            seam.bead(q0-n*lift, q1-n*lift, n, .05)
        studs = self.part(scar, 'fasteners', self.fastener if status == 'replaced' else mat, .005)
        centre = sum(rim, Vector())/len(rim)
        for q0, q1 in zip(rim, rim[1:]+rim[:1]):
            steps = max(2, round((q1-q0).length/.8))
            for i in range(steps):
                q = q0.lerp(q1, (i+.5)/steps).lerp(centre, .07)
                studs.disc(q+n*.08, n, .07, .05, 6)


def scar_id(scar):
    return scar if isinstance(scar, str) else scar.id


def repaired(history):
    """True if a scar was plated properly at some point in its life."""
    return 'replaced' in history


# ---- generic kinds ----------------------------------------------------------
# A handler(d, scar, status) builds one scar in one state. 'gone' never calls
# it. Each says what every status means for that shape of damage.

def kind_basin(d, scar, status):
    """An impact basin.
    params: origin, target, r, depth, elong=1.2, shards=0, sides=8,
            patch: 'salvage' (welded sheet, default), 'pressure' (bolted
            patch with a borrowed gasket) or 'none' (patched = left open),
            panel=1.5 (replacement plate half size, in radii).
    open: cut, crushed-lip shards. patched: cut with lips dressed flat, then
    the patch. replaced: a proper plate over the area (no crater shown).
    faded: that plate repainted, or a painted-over shallow dent if it was
    never plated."""
    P = scar.params
    site = d.site(scar, P['origin'], P['target'])
    if site is None:
        return
    r, depth, el = P['r'], P['depth'], P.get('elong', 1.2)
    hist = scar.history(d.state)
    if status == 'open':
        d.basin_cut(scar, site, r, depth, el, P.get('sides', 8))
        if P.get('shards'):
            d.lip_shards(scar, site, r, el, P['shards'])
    elif status == 'patched':
        d.basin_cut(scar, site, r, depth, el, P.get('sides', 8))
        patch = P.get('patch', 'salvage')
        if patch == 'salvage':
            d.salvage_plate(scar, site, r*1.3*el, r*1.3)
        elif patch == 'pressure':
            d.pressure_patch(scar, site, r)
    elif status == 'replaced' or (status == 'faded' and repaired(hist)):
        k = P.get('panel', 1.5)
        d.panel(scar, site, r*k*el, r*k, status, hist)
    elif status == 'faded':
        d.basin_cut(scar, site, r, depth*.3, el, P.get('sides', 8), REPAINT)


def cluster_sites(d, scar):
    """The sites of a strike cluster: deterministic, cast on the clean ship."""
    if scar.id in d.sites:
        return d.sites[scar.id]
    P = scar.params
    cast, centre, spread, count, radii = P['cast'], P['centre'], P['spread'], P['count'], P['radii']
    rng = random.Random(scar.id)
    done, tries = [], 0
    while len(done) < count and tries < count*8:
        tries += 1
        a, b = centre[0]+rng.gauss(0,spread[0]/2), centre[1]+rng.gauss(0,spread[1]/2)
        if cast == 'down':
            origin, target = (a+rng.uniform(-6,6), b+rng.uniform(-6,6), 60), (a, b, -20)
            if any(math.hypot(a-x, b-y) < 3.6*s+1 for x,y,_,s,_ in MOUNTS):
                continue
        else:
            y = 70 if cast == 'port' else -70
            origin, target = (a+rng.uniform(-8,8), y, b+rng.uniform(-4,4)), (a, 0, b)
            if 1.5 < a < 27.5 and b < 5:
                continue  # the bay openings
        r = rng.uniform(*radii)
        depth, el = r*rng.uniform(.55,.8), rng.uniform(1,1.4)
        key = f'{scar.id}.{len(done)}'
        hit = d.cast(origin, target)
        if hit is None or any(k in hit[2].name for k in NO_STRIKE):
            continue
        site = d.site(key, origin, target)
        if site:
            done.append((key, site, r, depth, el))
    d.sites[scar.id] = done
    return done


def in_zone(zone, p):
    if zone is None:
        return True
    x0, x1, y0, y1, zmin = zone
    return x0 < p.x < x1 and y0 < p.y < y1 and p.z > zmin


def kind_cluster(d, scar, status):
    """A cluster of small strikes, each sub-site id '<id>.<n>'.
    params: cast ('down'|'port'|'starboard'), centre, spread, count, radii,
            zone=(x0,x1,y0,y1,zmin): only sites inside it are ever repaired
            (patched gets pressure patches, replaced small inserts); the rest
            stay open until faded paints them over.
    faded: inserts repainted; unrepaired strikes become painted shallow dents."""
    zone = scar.params.get('zone')
    hist = scar.history(d.state)
    for key, site, r, depth, el in cluster_sites(d, scar):
        mine = in_zone(zone, site.p)
        if status == 'open' or (not mine and status in ('patched', 'replaced')):
            d.basin_cut(key, site, r, depth, el, 6)
        elif status == 'patched':
            d.basin_cut(key, site, r, depth, el, 6)
            d.pressure_patch(key, site, r)
        elif status == 'replaced' or (status == 'faded' and mine and repaired(hist)):
            d.panel(key, site, r*1.4+.3, r*1.4+.3, status, hist, 'insert')
        elif status == 'faded':
            d.basin_cut(key, site, r, depth*.3, el, 6, REPAINT)


def kind_spall(d, scar, status):
    """Spalled armour: a star tear through the outer plate onto backing.
    params: origin, target, r, depth, petals.
    open: tear and peeled petals. patched: tear under a bolted plate.
    replaced/faded: as basin."""
    P = scar.params
    site = d.site(scar, P['origin'], P['target'])
    if site is None:
        return
    p, n, _, _, ob = site
    u, v = basis(n)
    r = P['r']
    hist = scar.history(d.state)
    if status in ('open', 'patched'):
        rng = random.Random(scar.id)
        k = 7
        star = []
        for i in range(2*k):
            a = TAU*(i+rng.uniform(-.25,.25))/(2*k)
            rr = r*(rng.uniform(.85,1.15) if i % 2 == 0 else rng.uniform(.45,.65))
            star.append(p+u*math.cos(a)*rr+v*math.sin(a)*rr)
        geo = Geo()
        geo.loft([[q-n*P['depth'] for q in star], [q+n*.6 for q in star]])
        d.cut(geo)
        if status == 'open':
            peel = d.part(scar, 'peeled petal', d.torn(d.base_mat(ob)), .03)
            for i in range(P.get('petals', 0)):
                j = 2*((i*3) % k)
                tip, l, rr2 = star[j], star[j-1], star[(j+1) % (2*k)]
                out = (tip-p).normalized()
                curl = tip+out*r*.35+n*r*.45
                tri = [l+n*.02, rr2+n*.02, curl]
                peel.loft([[q-n*.14 for q in tri], tri])
        else:
            half = r*1.3
            ring = [d.snap(p+u*sx*half+v*sy*half, n, .06) for sx,sy in ((-1,-1),(1,-1),(1,1),(-1,1))]
            d.bolted_plate(scar, 'bolted patch', ring, n, d.salvage[sum(map(ord, scar.id)) % 3])
    elif status == 'replaced' or (status == 'faded' and repaired(hist)):
        d.panel(scar, Site(p, n, u, v, ob), r*1.6, r*1.6, status, hist)
    elif status == 'faded':
        d.basin_cut(scar, Site(p, n, u, v, ob), r*.8, P['depth']*.3, 1., 7, REPAINT)


def gouge_stations(d, scar):
    if scar.id in d.sites:
        return d.sites[scar.id]
    P = scar.params
    a, b, cd = V(P['start']), V(P['end']), V(P['cast_dir'])
    w, dep = P['w'], P['depth']
    stations = []
    for i in range(9):
        t = i/8
        o = a.lerp(b, t)
        hit = d.cast(o, o+cd*200)
        if hit is None:
            continue
        p, n, ob = hit
        f = math.sin(math.pi*t)
        stations.append((p, n, w*f**.5+.04, dep*f**.8+.02, ob))
    if len(stations) < 3:
        print('scar: no surface for', scar.id)
        stations = None
    d.sites[scar.id] = stations
    return stations


def kind_gouge(d, scar, status):
    """A gouge or scrape from glancing contact.
    params: start, end (ray origins), cast_dir, w (half width), depth.
    open: the groove. patched: groove run full of weld. replaced: a fairing
    strip along it. faded: strip repainted, or (never plated) filled and
    faired to a wide shallow dish under the new paint."""
    st = gouge_stations(d, scar)
    if not st:
        return
    hist = scar.history(d.state)
    track = (st[-1][0]-st[0][0]).normalized()
    def groove(scale, mat=None, faired=False):
        rings = []
        for p, n, ww, dd, _ in st:
            side = n.cross(track).normalized()
            if faired:
                # Filled and faired under new paint: a wide, shallow dish
                # with gentle sides, so no wall stands steep enough to shade.
                rings.append([p+side*ww*2.6+n*.05, p-side*ww*2.6+n*.05, p-n*min(.09, dd*scale)])
            else:
                rings.append([p+side*ww+n*.45, p-side*ww+n*.45, p-n*dd*scale])
        geo = Geo()
        geo.loft(rings)
        d.cut(geo, None, mat)
    if status == 'open':
        groove(1)
    elif status == 'patched':
        groove(1)
        weld = d.part(scar, 'weld fill', d.weld, .01)
        for (p0, n0, w0, *_), (p1, *_ ) in zip(st, st[1:]):
            weld.bead(p0-n0*.05, p1-n0*.05, n0, max(.12, w0*.8))
    elif status == 'replaced' or (status == 'faded' and repaired(hist)):
        mid = st[len(st)//2]
        p, n = mid[0], mid[1]
        u = track-n*track.dot(n)
        u.normalize()
        half = (st[-1][0]-st[0][0]).length/2+.4
        c = (st[-1][0]+st[0][0])/2
        hit = d.snap(c, n)
        d.panel(scar, Site(hit, n, u, n.cross(u), mid[4]), half, max(s2[2] for s2 in st)+.45, status, hist,
                'fairing strip')
    elif status == 'faded':
        groove(.25, REPAINT, faired=True)


def kind_plate(d, scar, status):
    """A deck armour plate lost.
    params: object (name prefix), centre (x, y), how: 'torn' (jagged
            remnant, keep=(kx, ky) corner kept) or 'unbolted' (salvaged:
            clean removal, bare stud ring).
    open: remnant or nothing, over backing and stringers. patched: the same
    under a bolted salvage plate. replaced/faded: the base plate stays hidden
    and a replacement plate fills its footprint."""
    P = scar.params
    ob = d.find(P['object'], P['centre'])
    if ob is None:
        return
    lo, hi = d.box(ob)
    W, H = hi.x-lo.x, hi.y-lo.y
    cx, cy = (lo.x+hi.x)/2, (lo.y+hi.y)/2
    hist = scar.history(d.state)
    if status in ('open', 'patched'):
        if P['how'] == 'torn':
            kx, ky = P['keep']
            rng = random.Random(scar.id)
            chain = [(-.1,.62)]
            for i in range(1,7):
                t = i/7
                s0, t0 = .5*t, .6*(1-t)
                j = rng.uniform(.03,.09)*(1 if i % 2 else -1)
                chain.append((s0+j, t0+j))
            chain.append((.5,-.1))
            poly = [(1.1,-.1),(1.1,1.1),(-.1,1.1)]+chain
            pts = [(cx+kx*(.5-s)*W, cy+ky*(.5-t)*H) for s,t in poly]
            geo = Geo()
            geo.loft([[(x,y,lo.z-.6) for x,y in pts], [(x,y,hi.z+.6) for x,y in pts]])
            d.cut(geo, [ob.name])
        else:
            d.hide(ob, scar)
        back = armor_patch(d.kit, f'Scar {scar.id} backing', lo.x+.25, hi.x-.25, lo.y+.25, hi.y-.25, d.backing, .06)
        if back:
            back.modifiers[0].thickness = .1
        along_x = W >= H
        count = max(2, int((W if along_x else H)/2.2))
        for i in range(1, count):
            f = i/count
            if along_x:
                x = lo.x+W*f
                rib = armor_patch(d.kit, f'Scar {scar.id} stringer', x-.18, x+.18, lo.y+.35, hi.y-.35, d.steel, .14)
            else:
                y = lo.y+H*f
                rib = armor_patch(d.kit, f'Scar {scar.id} stringer', lo.x+.35, hi.x-.35, y-.18, y+.18, d.steel, .14)
            if rib:
                rib.modifiers[0].thickness = .16
        if P['how'] == 'unbolted':
            studs = d.part(scar, 'bare studs', d.steel, .005)
            x0, x1, y0, y1 = lo.x+.3, hi.x-.3, lo.y+.3, hi.y-.3
            edges = [((x0,y0),(x1,y0)), ((x1,y0),(x1,y1)), ((x1,y1),(x0,y1)), ((x0,y1),(x0,y0))]
            for (ax,ay), (bx,by) in edges:
                k = max(2, round(math.hypot(bx-ax, by-ay)/1.1))
                for i in range(k):
                    t = i/k
                    sx, sy = ax+(bx-ax)*t, ay+(by-ay)*t
                    hit = d.kit.surface(sx, sy)
                    if hit:
                        studs.disc((sx, sy, hit[0].z+.2), (0,0,1), .09, .3, 6)
        if status == 'patched':
            z = hi.z+.04
            ring = [V((x, y, z)) for x, y in ((lo.x+.1,lo.y+.1),(hi.x-.1,lo.y+.1),(hi.x-.1,hi.y-.1),(lo.x+.1,hi.y-.1))]
            d.bolted_plate(scar, 'salvage plate', ring, V((0,0,1)), d.salvage[sum(map(ord, scar.id)) % 4], .1)
    else:  # replaced, faded
        d.hide(ob, scar)
        mat = d.primer if status == 'replaced' else d.repaint(d.base_mat(ob))
        z = hi.z-.1
        ring = [V((x, y, z)) for x, y in ((lo.x+.05,lo.y+.05),(hi.x-.05,lo.y+.05),(hi.x-.05,hi.y-.05),(lo.x+.05,hi.y-.05))]
        fast = d.fastener if status == 'replaced' else mat
        d.bolted_plate(scar, 'replacement plate', ring, V((0,0,1)), mat, .12, fast)
        seam = d.part(scar, 'seam', d.sealant if status == 'replaced' else mat, .005)
        for q0, q1 in zip(ring, ring[1:]+ring[:1]):
            seam.bead(q0, q1, V((0,0,1)), .05)


def kind_buckle(d, scar, status):
    """A deck plate buckled by slow heavy contact (a shape key on the plate).
    params: object, centre, fold (peak lift).
    open: buckled. patched: buckled under bolted straps. replaced/faded: the
    plate hidden and a replacement in its footprint."""
    P = scar.params
    ob = d.find(P['object'], P['centre'])
    if ob is None:
        return
    lo, hi = d.box(ob)
    if status in ('open', 'patched'):
        rng = random.Random(scar.id)
        jit = {}
        def fold(w):
            s = (w.x-lo.x)/max(hi.x-lo.x, 1e-6)
            t = (w.y-lo.y)/max(hi.y-lo.y, 1e-6)
            f = max(0, 1-abs(s+t-1)*2.2)*(1-.3*(2*s-1)**2)
            key = (round(w.x, 3), round(w.y, 3))
            if key not in jit:
                jit[key] = rng.uniform(-.05,.05)
            return w+V((0, 0, P['fold']*f+jit[key]*f))
        d.shape(ob, scar, fold)
        if status == 'patched':
            straps = d.part(scar, 'strap', d.salvage[1], .02)
            bolts = d.part(scar, 'bolt heads', d.steel, .005)
            z = hi.z+P['fold']+.12
            for f in (.3, .7):
                y = lo.y+(hi.y-lo.y)*f
                straps.obox(V(((lo.x+hi.x)/2, y, z)), V((1,0,0)), V((0,1,0)), V((0,0,1)), (hi.x-lo.x)/2+.2, .3, .06)
                for x in (lo.x+.1, hi.x-.1):
                    bolts.disc((x, y, z+.08), (0,0,1), .08, .07, 6)
    else:
        kind_plate(d, Scar(scar.id, 'plate', scar.history(d.state)[-1:]*4, object=P['object'],
                           centre=P['centre'], how='unbolted'), status)


def kind_strip(d, scar, status):
    """Base parts stripped by salvagers.
    params: parts [(name prefix, side sign 0|1|-1)], pads [(x, y, z, hx, hy)]
            bare mounting pads with studs left where they stood.
    open and patched: parts hidden (a module's own kind adds any jury work);
    replaced, faded: reinstalled (the base parts as built)."""
    if status not in ('open', 'patched'):
        return
    for name, sign in scar.params['parts']:
        for ob in d.named(name, sign):
            d.hide(ob, scar)
    for x, y, z, hx, hy in scar.params.get('pads', ()):
        top = d.snap(V((x, y, z)), V((0,0,1)), .04)
        d.part(scar, 'stripped pad', d.dark, .02).obox(top, V((1,0,0)), V((0,1,0)), V((0,0,1)), hx, hy, .05)
        studs = d.part(scar, 'stripped stud', d.steel, .01)
        for sx in (-1, 1):
            for sy in (-1, 0, 1):
                studs.disc(top+V((sx*(hx-.5), sy*(hy-.5), .15)), (0,0,1), .16, .25, 6)


def kind_power(d, scar, status):
    """Ship power. open: unpowered, every lamp a dead lens, the glazing unlit
    (object-level overrides). Any other status: lit as built."""
    if status != 'open':
        return
    for ob in bpy.data.objects:
        if ob.name not in d.original or not ob.data.materials:
            continue
        m = ob.data.materials[0]
        if m is None or m.node_tree is None:
            continue
        bsdf = m.node_tree.nodes.get('Principled BSDF')
        if bsdf and bsdf.inputs['Emission Strength'].default_value > 0:
            d.override(ob, d.unlit_glass if 'glazing' in m.name else d.dead, scar)


KINDS = {'basin': kind_basin, 'cluster': kind_cluster, 'spall': kind_spall, 'gouge': kind_gouge,
         'plate': kind_plate, 'buckle': kind_buckle, 'strip': kind_strip, 'power': kind_power}

SCARS = [
    Scar('ship-power', 'power', 'open gone gone gone',
         'Unpowered for the years in the belt; lit from the escape on.'),
]


def catalogue():
    """Every scar row and every kind, from this module and the area modules."""
    scars, kinds = [], dict(KINDS)
    for s in SCARS:
        s.area = 'core'
        scars.append(s)
    for name in AREAS:
        mod = importlib.reload(importlib.import_module(name))
        for s in getattr(mod, 'SCARS', ()):
            s.area = name
            scars.append(s)
        for k, fn in getattr(mod, 'KINDS', {}).items():
            assert k not in kinds, f'kind {k} defined twice'
            kinds[k] = fn
    ids = [s.id for s in scars]
    dup = {i for i in ids if ids.count(i) > 1}
    assert not dup, f'duplicate scar ids {dup}'
    for s in scars:
        assert s.kind in kinds, f'{s.id}: unknown kind {s.kind}'
    return scars, kinds


def apply(kit, state):
    """Layer one condition state onto the finished clean ship. Call last."""
    d = Damage(kit, state)
    scars, kinds = catalogue()
    for s in scars:
        status = s.status(state)
        d.record['scars'].append({'id': s.id, 'kind': s.kind, 'area': s.area,
                                  'status': status, 'note': s.note})
        if status != 'gone':
            kinds[s.kind](d, s, status)
    finish(d)
    return d.record


build = apply


# ---- assembly ------------------------------------------------------------
def finish(d):
    """Commit everything deferred: added parts, cuts, shape keys, overrides
    and hides, in that order, after every cast has been made."""
    for name, (geo, mat, edge) in d.parts.items():
        if not geo.v:
            continue
        ob = d.kit.mesh(name, geo.v, geo.f, mat)
        bm = bmesh.new()
        bm.from_mesh(ob.data)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bm.to_mesh(ob.data)
        bm.free()
        if edge:
            crisp(ob, edge)
    apply_cuts(d)
    for name, pos, axis, props in d.anchors:
        ob = bpy.data.objects.new(name, None)
        ob.empty_display_type, ob.empty_display_size = 'ARROWS', .8
        ob.rotation_mode = 'QUATERNION'
        ob.rotation_quaternion = V((1, 0, 0)).rotation_difference(axis)   # local +X = spray
        ob.location = pos
        for k, v in props.items():
            ob[k] = v
        bpy.context.collection.objects.link(ob)
    for ob, sid, moved in d.shapes:
        if ob.data.shape_keys is None:
            ob.shape_key_add(name='Basis', from_mix=False)
        key = ob.shape_key_add(name=f'Scar {sid}', from_mix=False)
        for i, co in moved.items():
            key.data[i].co = co
        key.value = 1
        d.record['shape_keys'].append(f'{ob.name}: Scar {sid}')
    for ob, mat, sid in d.overrides:
        for slot in ob.material_slots:
            slot.link = 'OBJECT'
            slot.material = mat
        ob['scar_override'] = sid
        d.record['overrides'].append(f'{ob.name}: {mat.name} ({sid})')
    for ob, key, value, sid in d.tags:
        ob[key] = value
    for name, sid in d.hides.items():
        ob = bpy.data.objects[name]
        ob.hide_render = True
        ob.hide_viewport = True
        ob['scar_hidden'] = sid
    d.record['hidden'] = dict(sorted(d.hides.items()))
    d.record['sites'] = site_record(d)


def site_record(d):
    """Every resolved scar site as points on the clean ship, keyed by scar id
    (cluster strikes `<id>.<k>` fold into their id): position, surface normal
    and, where the kind knows one, a radius. Sites are cast on the clean ship,
    so a scar's sites are the same in every state; wear masks and the
    cross-state review read them. Record only; changes no geometry."""
    r3 = lambda v: [round(c, 3) for c in v]
    out = {}
    for key, val in d.sites.items():
        sid = key.split('.')[0]
        items = val if isinstance(val, list) else [val]
        for it in items:
            if it is None:
                continue
            if isinstance(it, Site):
                pt = {'p': r3(it.p), 'n': r3(it.n)}
            elif len(it) == 5 and isinstance(it[1], Site):   # cluster strike
                pt = {'p': r3(it[1].p), 'n': r3(it[1].n), 'r': round(it[2], 3)}
            else:                                              # gouge station
                pt = {'p': r3(it[0]), 'n': r3(it[1]), 'r': round(it[2], 3)}
            pts = out.setdefault(sid, [])
            if pt not in pts:
                pts.append(pt)
    return dict(sorted(out.items()))


def apply_cuts(d):
    groups = {}
    bpy.context.view_layer.update()
    boxes = {}
    for name in d.original:
        ob = bpy.data.objects.get(name)
        if ob is None or name in d.hides or any(ob.name.startswith(k) or k in ob.name for k in UNCUT):
            continue
        boxes[name] = d.box(ob)
    for verts, faces, targets, mat in d.cuts:
        geo = Geo()
        geo.add(verts, faces)
        lo, hi = geo.bounds()
        if targets is None:
            targets = [n for n,(a,b) in boxes.items()
                       if all(a[i] <= hi[i]+.05 and b[i] >= lo[i]-.05 for i in range(3))]
        for t in targets:
            if t in d.hides:
                continue
            m = d.repaint(d.base_mat(bpy.data.objects[t])) if mat == REPAINT else mat
            groups.setdefault(t, []).append((verts, faces, m))
    for name, pieces in groups.items():
        target = bpy.data.objects[name]
        geo, idx, mats = Geo(), [], []
        for verts, faces, mat in pieces:
            if mat not in mats:
                mats.append(mat)
            geo.add(verts, faces)
            idx += [mats.index(mat)]*len(faces)
        cutter = d.kit.mesh('Scar cutter - '+name, geo.v, geo.f, mats[0])
        for mat in mats[1:]:
            cutter.data.materials.append(mat)
        for poly, i in zip(cutter.data.polygons, idx):
            poly.material_index = i
        bm = bmesh.new()
        bm.from_mesh(cutter.data)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        bm.to_mesh(cutter.data)
        bm.free()
        cutter.hide_render = True
        cutter.display_type = 'WIRE'
        mod = target.modifiers.new('Scar cuts', 'BOOLEAN')
        mod.operation, mod.object, mod.solver = 'DIFFERENCE', cutter, 'EXACT'
        mod.material_mode = 'TRANSFER'
        mod.use_self = True
        mod.use_hole_tolerant = True
    d.record['cut_objects'] = len(groups)
