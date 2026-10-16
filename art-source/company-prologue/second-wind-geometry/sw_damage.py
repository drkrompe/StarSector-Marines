"""Damage states for the Second Wind: real geometry layered on the clean ship.

Built only when build_reference.py is given --state derelict or patched; the
clean build never imports this module's effects. Everything here runs after
the clean ship exists and changes it locally:

derelict  Nearly a decade tethered in an asteroid belt as an unpowered parts
          bin. A few legible impact basins with crushed lips, spalled and
          torn outer armour opening onto dark backing, missing and buckled
          deck plates, gouged plate seams, bent heat-sink fins, a sheared
          sensor mast, a smashed port engine can, scattered small strikes in
          clusters, scorched waist service lines, a bowed bay frame and
          stripped pump machinery. Every light is dead.
patched   derelict plus the emergency work that made the escape: mismatched
          salvage plates welded over the craters, emergency backing in open
          ones, splints across buckled plates, the bowed bay frame shored
          with cargo rail, bolted pressure patches, external cable runs, a
          split sleeve on the burst service line and a jury whip on the mast
          stub. Only the starboard pod runs, on a salvaged drive coil bolted
          to its housing deck; the port pod is dead, stripped and capped.

Laws (owner decisions): damage never moves or removes the bays, the four
turret slots, the four engine centres, the grapple module or the silhouette;
no open breach into occupied spaces, no fire, no new engines. Construction
stays faceted and crisp: craters are faceted bowls, lips are flat shards,
patches are tented flat plates, beads are faceted lumps.

Placement is data, resolved by ray casts against the clean ship's objects
(never against damage parts), so it follows future hull edits. Cuts are
live Boolean modifiers added last, over each object's own bevel; the faces
they open take the dark spalled-backing material.
"""
import math
import random
import bpy
import bmesh
from mathutils import Vector
import sw_kit
from sw_hull import armor_patch, crisp
from sw_engines import ENGINES, BELL_R, CAN_FACE_R, FIN_X, bell_face
from sw_turrets import MOUNTS

TAU = math.tau

# Impact basins: (name, ray origin, ray target, radius, depth, elongation
# along the strike's ground track, lip shards, patched repair). Repairs:
# 'plate' a welded salvage plate, 'plates' two overlapping, 'backing'
# emergency backing in the open crater with splints over it, None open.
BASINS = [
    # Continuity scar 1: the impact cluster on the forward port flank,
    # around the grapple sponson and the port bow cheek above it.
    ('Port sponson cowl strike', (33,62,7.6), (33,30,7.4), 2.2, 1.0, 1.25, 8, 'plate'),
    ('Port bow cheek strike', (45,42,40), (45,21,13), 3.0, 1.3, 1.3, 9, 'backing'),
    ('Port bow flank strike', (60,44,3), (51,21,3), 2.2, 1.0, 1.2, 7, 'plate'),
    # Continuity scar 2: the scarred port aft shoulder over the dead pod.
    ('Port aft shoulder strike', (-12,58,32), (-12,36.5,13.5), 3.6, 1.6, 1.2, 10, 'plates'),
    ('Port pod lower casing strike', (-9,62,-2), (-9,37,-2.5), 2.0, .9, 1.3, 6, None),
    ('Port intake cap strike', (-1,60,5), (-1,36,5), 1.8, .8, 1.2, 6, 'backing'),
    ('Starboard forebody deck strike', (25,-17.5,60), (25.6,-17.2,0), 2.8, 1.2, 1.1, 8, 'plate'),
    ('Forward deck strike', (46,-6,60), (46.5,-6.5,0), 2.2, 1.0, 1.15, 7, 'plate'),
    ('Spine housing strike', (3.5,6,60), (3.6,5.6,0), 1.6, .7, 1.1, 6, 'plate'),
    ('Starboard cowl strike', (-14.5,-33,60), (-14,-33.4,0), 2.1, .9, 1.1, 7, 'plate'),
    ('Starboard cowl flank strike', (-8,-62,9), (-8,-36,9.5), 2.4, 1.1, 1.3, 8, 'backing'),
    ('Starboard intake strike', (5,-46,13), (0,-30,8), 2.0, .9, 1.2, 6, 'plate'),
    ('Bow glacis strike', (76,-3,32), (54,-3,10), 2.2, 1.0, 1.35, 7, 'plate'),
    ('Aft deck strike', (-23.5,9.3,60), (-23.5,9.3,0), 1.6, .8, 1.0, 6, None),
    ('Aft deck starboard strike', (-16,-9.2,60), (-16.3,-9,0), 1.5, .7, 1.1, 6, 'plate'),
    ('Starboard flank strike', (43,-62,3.5), (43,-30,3.5), 2.9, 1.2, 1.3, 9, 'backing'),
    # The smashed port engine can: struck from aft, over the outer can.
    ('Port outer engine can strike', (-50,47,14), (-39.6,33,2.5), 2.4, 1.4, 1.2, 6, None),
]

# Clustered small strikes, not uniform pockmarks: (name, cast, centre,
# spread, count, radius range). cast 'down' drops onto (x, y); 'port' and
# 'starboard' fire inboard at (x, z) on that flank.
CLUSTERS = [
    ('Port forward flank cluster', 'port', (37,5), (8,3.5), 9, (.35,.8)),
    ('Port bow deck cluster', 'down', (41,19), (5,3), 6, (.3,.7)),
    ('Port aft shoulder cluster', 'down', (-12,32), (6,3.5), 7, (.3,.75)),
    ('Starboard bow deck cluster', 'down', (28,-11), (7,5), 7, (.3,.7)),
    ('Aft deck cluster', 'down', (-18,-4), (6,5), 6, (.3,.6)),
    ('Starboard pod flank cluster', 'starboard', (-12,2), (6,3), 6, (.3,.65)),
    ('Spine cluster', 'down', (16,0), (9,3), 5, (.25,.5)),
    ('Scattered strikes', 'down', (8,0), (44,26), 16, (.25,.55)),
]

# Spalled armour: star-shaped tears through the outer plate onto backing,
# with petals peeled outward. (name, origin, target, radius, depth, petals)
SPALLS = [
    ('Starboard flank module tear', (29,-60,5.5), (29,-30,5.5), 1.8, .7, 3),
    ('Port citadel wall spall', (21,50,12.4), (21,20,12.4), 1.4, .55, 2),
    ('Starboard bow cheek spall', (54,-32,15), (47,-18,12), 1.6, .6, 3),
    ('Starboard intake cap spall', (1,-60,4), (1,-30,4), 1.3, .5, 2),
    ('Starboard belt plate tear', (31,-60,-1.2), (31,-30,-1.2), 1.5, .6, 3),
    ('Starboard lower casing tear', (-15,-60,-1), (-15,-35,-1), 1.6, .6, 3),
    ('Port nose block spall', (68,20,8), (57,4,6), 1.4, .55, 2),
]

# Gouges dragged across plate seams by glancing rock: (name, start origin,
# end origin, cast direction, half width, depth, patched repair).
GOUGES = [
    ('Starboard deck gouge', (17.5,-9.5,60), (33,-13.5,60), (0,0,-1), .7, .45, 'weld'),
    ('Port cowl gouge', (-2.5,27,60), (-7,34.5,60), (0,0,-1), .6, .4, 'splint'),
    ('Port glacis gouge', (44,4,60), (53,9.5,60), (0,0,-1), .55, .35, None),
    ('Starboard belt gouge', (33,-60,-.4), (46,-60,-1.6), (0,1,0), .55, .35, None),
]

# Deck armour plates torn away (object name, plate centre, kept corner
# direction, patched repair): a jagged remnant stays in one corner over
# exposed backing and stringers.
MISSING_PLATES = [
    ('Forebody replaceable armor', (32.5,16.75), (-1,1), 'salvage'),
    ('Forebody replaceable armor', (32.5,-10.25), (1,-1), None),
    ('Drive casing armour plate', (-11.85,28.6), (1,-1), None),
    ('Forebody replaceable armor', (24.5,16.75), (1,1), 'salvage'),
    ('Drive casing armour plate', (-7.7,-29.45), (-1,1), None),
    ('Aft deck armour plate', (-23.65,-9.2), (1,1), 'salvage'),
]

# Buckled deck plates (object name, plate centre, fold height, repair).
BUCKLES = [
    ('Forebody replaceable armor', (24.5,10.25), .42, 'splint'),
    ('Drive casing armour plate', (-11.85,-28.6), .36, 'splint'),
    ('Forebody replaceable armor', (24.5,-10.25), .3, None),
]

# Heat-sink fins bent by strikes: (side, fin indices, sector test, shift).
BENT_FINS = [(1, (1,2,3,4), lambda y,z: y > 30 and z > 7.5, (.75,-.55,.9,.5)),
             (-1, (4,5), lambda y,z: y < -31 and z < -1, (-.6,.7))]

# Continuity scar 3: scorched waist service lines and trunks.
SCORCHED = [('Protected waist service line', None), ('Exposed waist service trunk', 1),
            ('Trunk clamp hoop', 1)]
BURST = (9.5, 24.2, 7.2, 2.4)  # port service line: x, y, z, gap length

# The bowed rib: the port bay's forward frame jamb, pushed aft into the
# opening at mid-height. (x, y, z0, z1, (z, aft bow) stations)
RIB = (26, 23, -5.5, 4.5, [(-5.5,0),(-2.6,.7),(-.4,1.55),(1.8,.8),(4.5,0)])

# Parts-bin stripping: pump machinery lifted off both housing decks, the
# port can feed pipes and the port casing access panels.
STRIPPED = [('Turbopump housing', 0), ('Turbopump vent slot', 0), ('Pump drive drum', 0),
            ('Can feed pipe', 1), ('Casing access panel', 1), ('Panel grab strap', 1)]

# Sensor mast parts lost when it sheared.
MAST_LOST = ('Mast crosstree', 'Scanner turntable', 'Search radar scanner bar',
             'Masthead light', 'Crosstree signal light')

# Small strikes covered by bolted pressure patches in the patched state:
# (cluster name, every n-th strike).
PRESSURE_PATCHED = [('Spine cluster', 2), ('Port bow deck cluster', 3),
                    ('Starboard bow deck cluster', 3), ('Port forward flank cluster', 3)]

# External cable runs (patched): plan points; z is cast onto the ship.
CABLES = [[(-29.6,-26.8),(-27.4,-22),(-24,-17),(-20.5,-13.5),(-17.5,-11.6),(-14,-8.2),(-11.4,-4.4),(-10.6,-2.8)]]

# Nothing a strike or cut may land on or reach into.
UNCUT = ('Turret', 'Damage', 'Grapple arm', 'Boom', 'Forearm', 'Arm ', 'Engine ',
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
        side = up.cross(ax).normalized()
        self.obox((a+b)/2, ax, side, ax.cross(side).normalized(), (b-a).length/2, w/2, h/2)

    def tube(self, path, r, sides=8):
        rings = []
        for i,p in enumerate(path):
            t = (path[min(i+1,len(path)-1)]-path[max(i-1,0)]).normalized()
            u, v = basis(t)
            rings.append([p+(u*math.cos(TAU*k/sides)+v*math.sin(TAU*k/sides))*r for k in range(sides)])
        self.loft(rings)

    def disc(self, c, axis, r, depth, sides=12):
        c, axis = V(c), V(axis).normalized()
        u, v = basis(axis)
        ring = [c+(u*math.cos(TAU*(k+.5)/sides)+v*math.sin(TAU*(k+.5)/sides))*r for k in range(sides)]
        self.loft([[p-axis*depth/2 for p in ring], [p+axis*depth/2 for p in ring]])

    def bead(self, a, b, n, size=.17):
        """Stitched weld bead from a to b: a chain of faceted lumps."""
        a, b, n = V(a), V(b), V(n)
        length = (b-a).length
        if length < .05:
            return
        t = (b-a)/length
        side = n.cross(t).normalized()
        count = max(1, round(length/.34))
        for k in range(count):
            c = a+t*(length*(k+.5)/count)
            s = size*(.85+.3*((k*7) % 5)/4)
            half = length/count*.62
            self.add([c-t*half, c+side*s, c+n*s*.75, c-side*s, c-n*s*.4, c+t*half],
                     [(0,1,2),(0,2,3),(0,3,4),(0,4,1),(5,2,1),(5,3,2),(5,4,3),(5,1,4)])

    def bounds(self):
        xs, ys, zs = zip(*self.v)
        return V((min(xs),min(ys),min(zs))), V((max(xs),max(ys),max(zs)))


class Damage:
    def __init__(self, kit, state):
        self.kit, self.state = kit, state
        self.patched = state == 'patched'
        self.original = {o.name for o in bpy.data.objects if o.type == 'MESH'}
        self.cuts = []    # (verts, faces, explicit targets or None)
        self.parts = {}   # (name, material name) -> (Geo, material, edge)
        self.record = {'state': state, 'basins': [], 'strikes': 0, 'spalls': [], 'gouges': [],
                       'missing_plates': [], 'buckled_plates': [], 'repairs': []}
        self.strikes = {}
        m = sw_kit.material
        self.backing = m('Spalled armour backing', (.075,.07,.065), .6, .55)
        self.scorch = m('Scorched plating', (.03,.026,.024), .25, .85)
        self.weld = m('Weld bead', (.12,.11,.1), .75, .7)
        self.salvage = [m('Salvage plate grey-green primer', (.2,.23,.19), .35, .6),
                        m('Salvage plate bare steel', (.34,.34,.33), .8, .4),
                        m('Salvage plate faded blue-grey', (.17,.2,.24), .4, .6),
                        kit.cream]
        self.rail = m('Cargo rail safety yellow', (.5,.36,.05), .35, .55)
        self.blank = m('Engine blanking plate', (.27,.28,.27), .6, .5)
        self.copper = m('Salvaged drive coil copper', (.5,.22,.09), .9, .35)
        self.cable = m('External cable run', (.03,.03,.03), 0, .6)
        self.dead = m('Dead lamp', (.08,.07,.06), .3, .4)
        self.glow = m('Drive throat glow', (.3,.5,.8), emission=(.45,.68,1))
        self.glow.node_tree.nodes['Principled BSDF'].inputs['Emission Strength'].default_value = 7
        self.heat = m('Drive bell heat', (.05,.055,.07), .5, .4, emission=(.25,.4,.9))
        self.heat.node_tree.nodes['Principled BSDF'].inputs['Emission Strength'].default_value = .18

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

    def g(self, name, mat, edge=.04):
        key = (name, mat.name)
        if key not in self.parts:
            self.parts[key] = (Geo(), mat, edge)
        return self.parts[key][0]

    def remove(self, ob):
        self.original.discard(ob.name)
        bpy.data.objects.remove(ob, do_unlink=True)

    # ---- damage primitives -------------------------------------------------
    def basin(self, name, origin, target, r, d, elong=1., shards=0, sides=8):
        hit = self.cast(origin, target)
        if hit is None:
            print('damage: no surface for', name)
            return None
        p, n, ob = hit
        aim = (V(target)-V(origin)).normalized()
        track = aim-n*aim.dot(n)
        u = track.normalized() if track.length > .2 else basis(n)[0]
        v = n.cross(u)
        rng = random.Random(name)
        down = u*r*.18  # floor sits downrange of the entry
        def ring(rad, h, jit, shift):
            pts = []
            for k in range(sides):
                a = TAU*(k+rng.uniform(-jit,jit))/sides
                rr = rad*rng.uniform(1-jit*.7, 1+jit*.7)
                pts.append(p+shift+u*math.cos(a)*rr*elong+v*math.sin(a)*rr+n*h)
            return pts
        geo = Geo()
        geo.loft([ring(r*1.18,.55,.22,V((0,0,0))), ring(r*.62,-d*.55,.28,down*.6), ring(r*.26,-d,.3,down)])
        self.cuts.append((geo.v, geo.f, None))
        mat = ob.active_material or self.kit.orange
        lips = self.g('Crushed crater lip', mat, .03)
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
        return p, n, u, v, ob

    def spall(self, name, origin, target, r, d, petals):
        hit = self.cast(origin, target)
        if hit is None:
            print('damage: no surface for', name)
            return
        p, n, ob = hit
        u, v = basis(n)
        rng = random.Random(name)
        k = 7
        star = []
        for i in range(2*k):
            a = TAU*(i+rng.uniform(-.25,.25))/(2*k)
            rr = r*(rng.uniform(.85,1.15) if i % 2 == 0 else rng.uniform(.45,.65))
            star.append(p+u*math.cos(a)*rr+v*math.sin(a)*rr)
        geo = Geo()
        geo.loft([[q-n*d for q in star], [q+n*.6 for q in star]])
        self.cuts.append((geo.v, geo.f, None))
        mat = ob.active_material or self.kit.orange
        peel = self.g('Peeled armour petal', mat, .03)
        for i in range(petals):
            j = 2*((i*3) % k)
            tip, l, rr = star[j], star[j-1], star[(j+1) % (2*k)]
            out = (tip-p).normalized()
            curl = tip+out*r*.35+n*r*.45
            tri = [l+n*.02, rr+n*.02, curl]
            peel.loft([[q-n*.14 for q in tri], tri])
        self.record['spalls'].append(name)

    def gouge(self, name, start, end, cast_dir, w, d):
        a, b, cd = V(start), V(end), V(cast_dir)
        stations = []
        for i in range(9):
            t = i/8
            o = a.lerp(b, t)
            hit = self.cast(o, o+cd*200)
            if hit is None:
                continue
            p, n, _ = hit
            f = math.sin(math.pi*t)
            stations.append((p, n, w*f**.5+.04, d*f**.8+.02))
        if len(stations) < 3:
            print('damage: no surface for', name)
            return None
        track = (stations[-1][0]-stations[0][0]).normalized()
        rings = []
        for p, n, ww, dd in stations:
            side = n.cross(track).normalized()
            rings.append([p+side*ww+n*.45, p-side*ww+n*.45, p-n*dd])
        geo = Geo()
        geo.loft(rings)
        self.cuts.append((geo.v, geo.f, None))
        self.record['gouges'].append(name)
        return stations

    def strike(self, cluster, cast, centre, spread, count, radii):
        rng = random.Random(cluster)
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
            hit = self.cast(origin, target)
            if hit is None or any(k in hit[2].name for k in NO_STRIKE):
                continue
            r = rng.uniform(*radii)
            res = self.basin(f'{cluster} {len(done)}', origin, target, r, r*rng.uniform(.55,.8),
                             rng.uniform(1,1.4), 3 if r > .55 else 0, 6)
            if res:
                done.append((res, r))
        self.strikes[cluster] = done
        self.record['strikes'] += len(done)

    def crush(self, ob, centre, direction, radius, depth):
        mw = ob.matrix_world
        inv = mw.inverted()
        for vert in ob.data.vertices:
            w = mw @ vert.co
            dist = (w-centre).length
            if dist < radius:
                vert.co = inv @ (w+direction*depth*(1-dist/radius))
        ob.data.update()

    # ---- derelict ----------------------------------------------------------
    def build_derelict(self):
        self.sites = {}
        for name, o, t, r, d, el, sh, repair in BASINS:
            # Repair crews dress a crushed lip flat before a plate goes on.
            if self.patched and repair in ('plate', 'plates'):
                sh = 0
            self.sites[name] = self.basin(name, o, t, r, d, el, sh)
            self.record['basins'].append({'name': name, 'radius': r, 'depth': d,
                                          'repair': repair if self.patched else None})
        for args in CLUSTERS:
            self.strike(*args)
        for args in SPALLS:
            self.spall(*args)
        self.gouges = {name: self.gouge(name, s, e, c, w, d) for name, s, e, c, w, d, _ in GOUGES}
        self.smash_can()
        self.bend_fins()
        self.missing_plates()
        self.buckle_plates()
        self.scorch_waist()
        self.bow_rib()
        self.shear_mast()
        self.strip()

    def smash_can(self):
        site = self.sites.get('Port outer engine can strike')
        if not site:
            return
        p, n, u, v, _ = site
        x, y, w = ENGINES[0]
        for tag in ('shroud collar', 'aft-facing bell', 'mouth lip', 'throat ring'):
            ob = self.find(f'Engine 1 {tag}', None)
            if ob:
                self.crush(ob, p, -n, 4.6, 1.05)
        can = self.find('Thrust bay engine can', (x+5, y), 1)
        if can:
            self.crush(can, p, -n, 4.2, 1.0)
        self.record['smashed_can'] = 'Engine 1 (port outer)'

    def bend_fins(self):
        for s, indices, test, shifts in BENT_FINS:
            for i, dx in zip(indices, shifts):
                for ob in self.named('Heat-sink fin', s):
                    c = sum((V(b) for b in ob.bound_box), Vector())/8
                    if abs(c.x-FIN_X[i]) > .3:
                        continue
                    for vert in ob.data.vertices:
                        y, z = vert.co.y, vert.co.z
                        if test(y, z):
                            reach = min(1, (abs(y)-29)/6+max(0, (z-7.5))/6 if s > 0 else (-z)/6+(abs(y)-31)/5)
                            vert.co.x += dx*max(0, reach)
                    ob.data.update()
        self.record['bent_fins'] = 'port fin bank top-outboard, starboard bottom-outboard'

    def missing_plates(self):
        for name, centre, (kx,ky), repair in MISSING_PLATES:
            ob = self.find(name, centre)
            if ob is None:
                continue
            lo = V([min(c[i] for c in ob.bound_box) for i in range(3)])
            hi = V([max(c[i] for c in ob.bound_box) for i in range(3)])
            W, H = hi.x-lo.x, hi.y-lo.y
            cx, cy = (lo.x+hi.x)/2, (lo.y+hi.y)/2
            rng = random.Random(name+str(centre))
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
            self.cuts.append((geo.v, geo.f, [ob.name]))
            back = armor_patch(self.kit, 'Exposed plate backing', lo.x+.25, hi.x-.25, lo.y+.25, hi.y-.25, self.backing, .06)
            if back:
                back.modifiers[0].thickness = .1
            along_x = W >= H
            count = max(2, int((W if along_x else H)/2.2))
            for i in range(1, count):
                f = i/count
                if along_x:
                    x = lo.x+W*f
                    rib = armor_patch(self.kit, 'Backing stringer', x-.18, x+.18, lo.y+.35, hi.y-.35, self.kit.steel, .14)
                else:
                    y = lo.y+H*f
                    rib = armor_patch(self.kit, 'Backing stringer', lo.x+.35, hi.x-.35, y-.18, y+.18, self.kit.steel, .14)
                if rib:
                    rib.modifiers[0].thickness = .16
            self.record['missing_plates'].append({'object': name, 'centre': list(centre),
                                                  'repair': repair if self.patched else None})

    def buckle_plates(self):
        for name, centre, amp, _ in BUCKLES:
            ob = self.find(name, centre)
            if ob is None:
                continue
            xs = [vv.co.x for vv in ob.data.vertices]
            ys = [vv.co.y for vv in ob.data.vertices]
            rng = random.Random(name+str(centre))
            for vert in ob.data.vertices:
                s = (vert.co.x-min(xs))/(max(xs)-min(xs))
                t = (vert.co.y-min(ys))/(max(ys)-min(ys))
                fold = max(0, 1-abs(s+t-1)*2.2)*(1-.3*(2*s-1)**2)
                vert.co.z += amp*fold+rng.uniform(-.05,.05)*fold
            ob.data.update()
            self.record['buckled_plates'].append({'object': name, 'centre': list(centre)})

    def scorch_waist(self):
        for name, sign in SCORCHED:
            for ob in self.named(name, sign or 0):
                if name == 'Trunk clamp hoop':
                    c = ob.matrix_world.translation
                    if c.x < 5:
                        continue
                ob.data.materials[0] = self.scorch
        x, y, z, gap = BURST
        line = self.find('Protected waist service line', (x,y), 1)
        if line:
            geo = Geo()
            geo.obox((x,y,z), V((1,0,0)), V((0,1,0)), V((0,0,1)), gap/2, 1.2, 1.2)
            self.cuts.append((geo.v, geo.f, [line.name]))
            core = self.g('Burst service line cable core', self.kit.dark, .02)
            for dy, dz in ((-.15,.1),(.2,-.05),(0,-.25)):
                core.tube([V((x-gap/2-.4,y+dy,z+dz)), V((x-.2,y+dy*1.4,z+dz+.12)), V((x+gap/2+.4,y+dy,z+dz))], .17, 6)
            peel = self.g('Burst cover petal', self.scorch, .02)
            for k,(dx,a) in enumerate(((-1,1.2),(-1,-.3),(1,.6),(1,2.2))):
                base = V((x+dx*gap/2, y, z))
                out = V((0, math.cos(a), math.sin(a)))
                tip = base+out*1.1+V((-dx*.5,0,0))
                tri = [base+out*.7+V((0,0,.25)), base+out*.7+V((0,0,-.25)), tip]
                tri = [base+out*.7+V((0,-out.z,out.y)).normalized()*.3, base+out*.7-V((0,-out.z,out.y)).normalized()*.3, tip]
                peel.loft([[q-V((dx*.08,0,0)) for q in tri], tri])
        self.record['scorched'] = 'port and starboard waist service lines, port waist trunks and hoops; port line burst'

    def bow_rib(self):
        x, y, z0, z1, stations = RIB
        jamb = self.find('Bay jamb', (x,y), 1)
        if jamb is None:
            return
        mat = jamb.active_material
        self.remove(jamb)
        geo = self.g('Bowed bay frame jamb', mat, .08)
        rings = []
        for z, bow in stations:
            w = .6 if bow < .9 else .5
            rings.append([V((x-bow+dx, y+dy, z)) for dx,dy in ((-w,-1.5),(w,-1.5),(w,1.5),(-w,1.5))])
        geo.loft(rings)
        crumple = self.g('Crumpled frame flange', self.kit.steel, .02)
        for dz, dy in ((-.6,1.6),(.2,-1.6)):
            base = V((x-1.15, y+dy, dz))
            tri = [base+V((0,0,-.7)), base+V((0,0,.6)), base+V((-.5,dy*.25,0))]
            crumple.loft([[q-V((0,dy*.05,0)) for q in tri], tri])
        self.record['bowed_rib'] = 'port bay forward frame jamb, bowed aft into the opening'

    def shear_mast(self):
        pylon = self.find('Mast pylon', None)
        if pylon is None:
            return
        zs = [(pylon.matrix_world @ V(c)).z for c in pylon.bound_box]
        mx = pylon.matrix_world.translation.x+sum(c[0] for c in pylon.bound_box)/8
        z0 = min(zs)
        self.mast = (mx, z0)
        for name in MAST_LOST:
            for ob in self.named(name):
                self.remove(ob)
        whips = sorted(self.named('Whip antenna'), key=lambda o: o.location.y)
        if whips:
            self.remove(whips[0])
        if len(whips) > 1:
            w = whips[1]
            w.rotation_euler = (1.15, 0, .3)
            w.location.z -= .6
        geo = Geo()
        c = V((mx, 0, z0+1.0))
        n = V((.35, .2, 1)).normalized()
        u, v = basis(n)
        geo.obox(c+n*3, u, v, n, 4, 4, 3)
        self.cuts.append((geo.v, geo.f, [pylon.name]))
        # The scanner bar fell and lies across the roof beside the stub.
        bar = self.g('Fallen scanner bar', self.kit.dark, .05)
        roof = self.snap(V((mx-1.6, 1.2, z0)), V((0,0,1)), .2)
        bar.bar(roof+V((-.3,-1.7,.05)), roof+V((.4,1.8,-.05)), V((0,0,1)), .55, .4)
        self.record['sheared_mast'] = 'sensor mast sheared above the pedestal; scanner bar fallen, one whip lost, one bent'

    def strip(self):
        for name, sign in STRIPPED:
            for ob in self.named(name, sign):
                self.remove(ob)
        pads = self.g('Stripped mounting pad', self.kit.dark, .02)
        studs = self.g('Stripped mounting stud', self.kit.steel, .01)
        for s in (1,-1):
            top = self.snap(V((-30,s*32,9.8)), V((0,0,1)), .04)
            pads.obox(top, V((1,0,0)), V((0,1,0)), V((0,0,1)), 2.0, 2.8, .05)
            for dx in (-1.5,1.5):
                for dy in (-2.3,0,2.3):
                    studs.disc(top+V((dx,s*dy,.15)), (0,0,1), .16, .25, 6)
        self.record['stripped'] = 'turbopumps and drive drums off both housing decks; port can feed pipes and casing access panels'

    def lights_off(self):
        # Unpowered: every lamp is a dead lens, the glazing unlit.
        for ob in bpy.data.objects:
            if ob.type != 'MESH' or not ob.data.materials:
                continue
            m = ob.data.materials[0]
            if m is None or m.node_tree is None or m in (self.glow, self.heat):
                continue
            bsdf = m.node_tree.nodes.get('Principled BSDF')
            if bsdf and bsdf.inputs['Emission Strength'].default_value > 0:
                if 'glazing' in m.name:
                    bsdf.inputs['Emission Strength'].default_value = 0
                else:
                    ob.data.materials[0] = self.dead

    # ---- patched -----------------------------------------------------------
    def build_patched(self):
        for name, *_, repair in BASINS:
            site = self.sites.get(name)
            if site and repair == 'plate':
                self.salvage_plate(name, site, 1.45)
            elif site and repair == 'plates':
                self.salvage_plate(name, site, 1.25)
                self.salvage_plate(name+' overlap', site, 1.0, shift=(.9,-.7), tilt=.12)
            elif site and repair == 'backing':
                self.crater_backing(name, site)
        for cluster, every in PRESSURE_PATCHED:
            for i, (site, r) in enumerate(self.strikes.get(cluster, [])):
                if i % every == 0:
                    self.pressure_patch(f'{cluster} {i}', site, r)
        for name, *_, repair in GOUGES:
            stations = self.gouges.get(name)
            if stations and repair == 'weld':
                self.weld_gouge(stations)
            elif stations and repair == 'splint':
                self.splint_gouge(stations)
        for name, centre, _, repair in MISSING_PLATES:
            if repair == 'salvage':
                self.replacement_plate(name, centre)
        for name, centre, _, repair in BUCKLES:
            if repair == 'splint':
                self.splint_plate(name, centre)
        self.shore_rib()
        self.sleeve_burst()
        self.jury_mast()
        self.cap_port_pod()
        self.drive_coil()
        self.cables()

    def plate_poly(self, rng, p, n, u, v, a, b, sides=6, lift=.12, crown=.3):
        ring = []
        for k in range(sides):
            ang = TAU*(k+rng.uniform(-.18,.18))/sides
            q = p+u*math.cos(ang)*a*rng.uniform(.9,1.1)+v*math.sin(ang)*b*rng.uniform(.9,1.1)
            ring.append(self.snap(q, n, lift))
        top_c = p+n*(crown+lift)
        return ring, top_c

    def emit_plate(self, name, ring, centre, n, mat, thick=.26, bolts=False):
        geo = self.g(name, mat, .05)
        k = len(ring)
        top = [q+n*thick for q in ring]+[centre+n*thick]
        bot = ring+[centre]
        verts = bot+top
        faces = [(k, (i+1) % k, i) for i in range(k)]
        faces += [(k+1+k, k+1+i, k+1+(i+1) % k) for i in range(k)]
        faces += [(i, (i+1) % k, k+1+(i+1) % k, k+1+i) for i in range(k)]
        geo.add(verts, faces)
        beads = self.g('Weld bead', self.weld, .01)
        if bolts:
            studs = self.g('Patch bolt head', self.kit.steel, .01)
        for i in range(k):
            a, b = ring[i], ring[(i+1) % k]
            if bolts:
                for t in (.25,.5,.75):
                    q = a.lerp(b, t).lerp(centre, .14)+n*(thick+.05)
                    studs.disc(q, n, .14, .14, 6)
            else:
                beads.bead(a, b, n)

    def salvage_plate(self, name, site, scale, shift=(0,0), tilt=0):
        p, n, u, v, _ = site
        rng = random.Random(name)
        r = next(r for nm,_,_,r,*_ in BASINS if name.startswith(nm))
        el = next(e for nm,_,_,_,_,e,*_ in BASINS if name.startswith(nm))
        # Square-cut stock, laid skew to the hull's own plate lines.
        turn = rng.uniform(-.45,.45)+tilt
        uu, vv = u*math.cos(turn)+v*math.sin(turn), v*math.cos(turn)-u*math.sin(turn)
        q = p+u*shift[0]+v*shift[1]
        mat = self.salvage[sum(map(ord, name)) % len(self.salvage)]
        self.grid_plate('Welded salvage plate', q, n, uu, vv, r*scale*el*1.05, r*scale, mat,
                        rng.uniform(.18,.4), .12+(.2 if 'overlap' in name else 0),
                        only={site[4].name, self.kit.hull.name})
        self.record['repairs'].append(f'salvage plate over {name}')

    def grid_plate(self, name, p, n, u, v, a, b, mat, cut, lift, thick=.26, k=3, only=None):
        """A cut sheet brake-bent over a crater onto the original skin line.

        The plate folds only along lines across its curving direction, so it
        is k flat strips with crisp bends, never a dome; corners are clipped.
        Each fold line sits at the highest skin under it, so the sheet
        bridges dips and never sinks into the crater or a seam."""
        def height(q):
            hit = self.cast(q+n*6, q-n*6, only)
            return (hit[0]-p).dot(n) if hit else None
        def profile(axis, across, half, other):
            hs = []
            for i in range(k+1):
                s = 2*i/k-1
                got = [height(p+axis*s*half+across*t*other) for t in (-1,-.5,0,.5,1)]
                got = [h for h in got if h is not None]
                hs.append(max(got) if got else None)
            known = [h for h in hs if h is not None]
            base = max(known) if known else 0
            return [base if h is None else h for h in hs]
        hu, hv = profile(u, v, a, b), profile(v, u, b, a)
        # Bend along whichever direction the skin actually curves.
        if max(hv)-min(hv) > max(hu)-min(hu):
            u, v, a, b, hs = v, u, b, a, hv
        else:
            hs = hu
        rows = []
        for i in range(k+1):
            s = 2*i/k-1
            w = b*(1-cut) if i in (0, k) else b  # clipped corners
            c = p+u*a*s+n*(hs[i]+lift)
            rows.append((c-v*w, c+v*w))
        verts = [q for r in rows for q in r]+[q+n*thick for r in rows for q in r]
        m = 2*(k+1)
        faces = []
        for i in range(k):
            a0, a1, b0, b1 = 2*i, 2*i+1, 2*i+2, 2*i+3
            faces += [(a0,b0,b1,a1), (m+a0,m+a1,m+b1,m+b0), (a0,m+a0,m+b0,b0), (a1,b1,m+b1,m+a1)]
        faces += [(0,1,m+1,m), (m-2,m+m-2,m+m-1,m-1)]
        self.g(name, mat, .05).add(verts, faces)
        rim = [r[0] for r in rows]+[r[1] for r in reversed(rows)]
        beads = self.g('Weld bead', self.weld, .01)
        for q0, q1 in zip(rim, rim[1:]+rim[:1]):
            beads.bead(q0, q1, n)

    def crater_backing(self, name, site):
        p, n, u, v, _ = site
        r, d = next((r,d) for nm,_,_,r,d,*_ in BASINS if nm == name)
        rng = random.Random(name+' backing')
        q = p-n*(d*.5)
        ring = [q+u*math.cos(TAU*(k+rng.uniform(-.15,.15))/6)*r*.72+v*math.sin(TAU*(k+rng.uniform(-.15,.15))/6)*r*.62 for k in range(6)]
        self.emit_plate('Emergency crater backing', ring, q, n, self.salvage[1], .18)
        straps = self.g('Splint strap', self.kit.steel, .03)
        beads = self.g('Weld bead', self.weld, .01)
        for off in (-.45,.45):
            a = self.snap(p+v*off*r-u*r*1.75, n, .12)
            b = self.snap(p+v*off*r+u*r*1.75, n, .12)
            mid = (a+b)/2+n*(r*.32)
            for e0, e1 in ((a, mid), (mid, b)):
                straps.bar(e0, e1, n, .55, .2)
            for e in (a, b):
                beads.bead(e-v*.3, e+v*.3, n)
        self.record['repairs'].append(f'emergency backing and splints in {name}')

    def pressure_patch(self, name, site, r):
        p, n, u, v, _ = site
        half = r*1.5+.3
        ring = [self.snap(p+u*sx*half+v*sy*half, n, .08) for sx,sy in ((-1,-1),(1,-1),(1,1),(-1,1))]
        self.emit_plate('Bolted pressure patch', ring, p+n*(.08+r*.15), n,
                        self.salvage[sum(map(ord, name)) % 3], .16, bolts=True)
        self.record['repairs'].append(f'pressure patch over {name}')

    def weld_gouge(self, stations):
        beads = self.g('Weld bead', self.weld, .01)
        filler = self.g('Gouge filler bar', self.salvage[1], .03)
        for (p0,n0,_,_), (p1,n1,_,_) in zip(stations, stations[1:]):
            filler.bar(p0-n0*.1, p1-n1*.1, n0, .5, .3)
            beads.bead(p0+n0*.08, p1+n1*.08, n0, .2)
        self.record['repairs'].append('gouge filled and welded')

    def splint_gouge(self, stations):
        straps = self.g('Splint strap', self.kit.steel, .03)
        for p, n, w, _ in stations[2:-1:2]:
            track = (stations[-1][0]-stations[0][0]).normalized()
            side = n.cross(track).normalized()
            straps.bar(self.snap(p+side*1.4, n, .14), self.snap(p-side*1.4, n, .14), n, .5, .22)
        self.record['repairs'].append('gouge splinted')

    def replacement_plate(self, name, centre):
        ob = self.find(name, centre)
        if ob is None:
            return
        xs = [c[0] for c in ob.bound_box]
        ys = [c[1] for c in ob.bound_box]
        x0, x1, y0, y1 = min(xs)+.35, max(xs)-.7, min(ys)+.3, max(ys)-.2
        plate = armor_patch(self.kit, 'Replacement salvage deck plate', x0, x1, y0, y1, self.salvage[0], .5)
        if plate:
            plate.modifiers[0].thickness = .3
        beads = self.g('Weld bead', self.weld, .01)
        for a, b in (((x0,y0),(x1,y0)),((x1,y0),(x1,y1)),((x1,y1),(x0,y1)),((x0,y1),(x0,y0))):
            hz = max(self.kit.surface(*a)[0].z, self.kit.surface(*b)[0].z)
            beads.bead(V((*a,hz+.55)), V((*b,hz+.55)), V((0,0,1)))
        self.record['repairs'].append(f'replacement salvage plate where {name} was torn away')

    def splint_plate(self, name, centre):
        ob = self.find(name, centre)
        if ob is None:
            return
        mw = ob.matrix_world
        vs = [mw @ vv.co for vv in ob.data.vertices]
        x0, x1 = min(q.x for q in vs), max(q.x for q in vs)
        y0, y1 = min(q.y for q in vs), max(q.y for q in vs)
        top = max(q.z for q in vs)+.45+.25
        straps = self.g('Splint strap', self.kit.steel, .03)
        feet = self.g('Splint foot', self.kit.steel, .03)
        beads = self.g('Weld bead', self.weld, .01)
        bolts = self.g('Patch bolt head', self.kit.steel, .01)
        for f in (.22,.55,.85):
            y = y0+(y1-y0)*f
            a, b = V((x0-.7,y,top)), V((x1+.7,y,top))
            straps.bar(a, b, V((0,0,1)), .6, .22)
            for e in (a, b):
                gz = self.ground(e.x, e.y)
                feet.obox(V((e.x,e.y,(gz+top)/2)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .35, .35, (top-gz)/2+.05)
                beads.bead(V((e.x-.4,e.y-.42,gz+.05)), V((e.x+.4,e.y-.42,gz+.05)), V((0,0,1)))
            for t in (.3,.7):
                bolts.disc(a.lerp(b,t)+V((0,0,.14)), (0,0,1), .13, .12, 6)
        self.record['repairs'].append(f'splints across buckled {name}')

    def shore_rib(self):
        x, y, z0, z1, stations = RIB
        bow = max(b for _,b in stations)
        rail = self.g('Cargo rail shore', self.rail, .05)
        slots = self.g('Cargo rail tie-down slot', self.kit.dark, 0)
        clamps = self.g('Rail clamp bracket', self.kit.steel, .04)
        beads = self.g('Weld bead', self.weld, .01)
        def cargo_rail(a, b, face):
            a, b = V(a), V(b)
            rail.bar(a, b, face, .85, .4)
            L = (b-a).length
            ax = (b-a)/L
            side = face.cross(ax).normalized()
            for k in range(int(L/.9)):
                c = a+ax*(.45+k*.9)+face*.2
                slots.obox(c, ax, side, face, .22, .14, .03)
        # A sister rail standing straight across the bow, clamped to the coamings.
        face = V((-1,0,0))
        xs = x-bow-.85
        cargo_rail((xs,y-.2,z0+.55), (xs,y-.2,z1-.45), face)
        for z in (z0+.5, z1-.6):
            clamps.obox(V(((xs+x-.6)/2, y-.2, z)), V((1,0,0)), V((0,1,0)), V((0,0,1)), (x-.6-xs)/2+.3, .55, .3)
            beads.bead(V((x-.6,y-.8,z-.32)), V((x-.6,y+.4,z-.32)), V((0,0,1)))
        # A raking shore from the bay floor wedged under the bow.
        hit = self.cast((x-5.6,y-1.2,-1), (x-5.6,y-1.2,-20))
        floor = hit[0] if hit else V((x-5.6,y-1.2,-4.75))
        cargo_rail(floor+V((0,0,.25)), V((xs-.45,y-1.2,-.6)), V((-.6,0,.8)).normalized())
        clamps.obox(floor+V((0,0,.12)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .9, .8, .12)
        beads.bead(floor+V((-.9,-.85,.05)), floor+V((.9,-.85,.05)), V((0,0,1)))
        self.record['repairs'].append('bowed bay frame shored with cargo rail')

    def sleeve_burst(self):
        x, y, z, gap = BURST
        sleeve = self.g('Split clamp sleeve', self.salvage[1], .04)
        bolts = self.g('Patch bolt head', self.kit.steel, .01)
        sleeve.disc((x,y,z), (1,0,0), 1.0, gap+1.2, 10)
        for dx in (-(gap+1.2)/2, (gap+1.2)/2):
            sleeve.disc((x+dx,y,z), (1,0,0), 1.25, .3, 10)
        for dx in (-.8, .8):
            bolts.obox(V((x+dx,y,z+1.1)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .3, .5, .12)
        beads = self.g('Weld bead', self.weld, .01)
        for dx in (-(gap+1.8)/2, (gap+1.8)/2):
            for k in range(8):
                a0, a1 = TAU*k/8, TAU*(k+1)/8
                p0 = V((x+dx, y+.75*math.cos(a0), z+.75*math.sin(a0)))
                p1 = V((x+dx, y+.75*math.cos(a1), z+.75*math.sin(a1)))
                beads.bead(p0, p1, V((0, math.cos(a0), math.sin(a0))), .14)
        self.record['repairs'].append('split clamp sleeve over the burst service line')

    def jury_mast(self):
        if not hasattr(self, 'mast'):
            return
        mx, z0 = self.mast
        whip = self.g('Jury whip antenna', self.kit.steel, .01)
        whip.disc((mx+.75, 0, z0+2.4), (0,0,1), .11, 4.2, 6)
        bands = self.g('Hose clamp lashing', self.salvage[1], .01)
        for z in (z0+.9, z0+1.6):
            bands.disc((mx+.6, 0, z), (0,0,1), .28, .14, 8)
        self.g('Salvaged antenna box', self.salvage[0], .04).obox(
            V((mx+.75,0,z0+4.2)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .3, .5, .25)
        self.record['repairs'].append('jury whip antenna lashed to the mast stub')

    def cap_port_pod(self):
        plates = self.g('Engine blanking plate', self.blank, .05)
        backs = self.g('Blanking strongback', self.kit.steel, .04)
        bolts = self.g('Patch bolt head', self.kit.steel, .01)
        for x, y, w in ENGINES[:2]:
            face = bell_face(x)
            R = CAN_FACE_R[w]*math.cos(math.pi/12)
            xc = face-1.05
            plates.disc((xc,y,0), (1,0,0), R-.05, .45, 12)
            for k in range(12):
                a = TAU*(k+.5)/12
                bolts.disc((xc-.28, y+(R-.55)*math.cos(a), (R-.55)*math.sin(a)), (1,0,0), .16, .14, 6)
            backs.obox(V((xc-.45,y,0)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .22, R-.3, .32)
            backs.obox(V((xc-.45,y,0)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .22, .32, R-.3)
            backs.obox(V((xc-.8,y,0)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .2, .45, .45)
        for ob in self.named('Drive warning light', 1):
            ob.data.materials[0] = self.dead
        self.record['port_pod'] = 'dead, stripped, both nozzles blanked'

    def drive_coil(self):
        x, y = -30.0, -31.0
        deck = self.snap(V((x,y,9.8)), V((0,0,1)))
        zc = deck.z+2.35
        core = self.g('Salvaged drive coil casing', self.salvage[0], .05)
        wind = self.g('Salvaged drive coil winding', self.copper, .02)
        flange = self.g('Drive coil clamp ring', self.kit.steel, .04)
        saddle = self.g('Drive coil saddle', self.salvage[1], .05)
        bolts = self.g('Patch bolt head', self.kit.steel, .01)
        beads = self.g('Weld bead', self.weld, .01)
        core.disc((x,y,zc), (0,1,0), 1.35, 9.2, 12)
        for dy in (-3.2,-1.05,1.05,3.2):
            wind.disc((x,y+dy,zc), (0,1,0), 2.0, 1.45, 16)
        for dy in (-4.2,-2.15,0,2.15,4.2):
            flange.disc((x,y+dy,zc), (0,1,0), 2.25, .32, 12)
        for dy in (-3.6,3.6):
            saddle.obox(V((x,y+dy,(deck.z+zc)/2-.3)), V((1,0,0)), V((0,1,0)), V((0,0,1)), 1.9, .45, (zc-deck.z)/2-.3)
            saddle.obox(V((x,y+dy,deck.z+.12)), V((1,0,0)), V((0,1,0)), V((0,0,1)), 2.2, .9, .12)
            for dx in (-1.6,1.6):
                bolts.disc((x+dx,y+dy-.55,deck.z+.3), (0,0,1), .16, .2, 6)
                bolts.disc((x+dx,y+dy+.55,deck.z+.3), (0,0,1), .16, .2, 6)
            beads.bead(V((x-2.2,y+dy-.95,deck.z+.06)), V((x+2.2,y+dy-.95,deck.z+.06)), V((0,0,1)))
        # Terminal box on the outboard end and heavy leads down into both cans.
        core.obox(V((x,y-5.1,zc)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .9, .5, .9)
        leads = self.g('Drive coil lead', self.cable, .01)
        for _, ey, w in ENGINES[2:]:
            top = V((x-.3, ey, zc-1.2))
            can = V((-33.6, ey, CAN_FACE_R[w]*.75))
            leads.tube([V((x, y-5.4, zc-.3)), V((x-1.6, y-5.4, zc-1.6)), V((-32.6, ey-.6*(1 if ey < -27 else -1), 8.4)), can], .2, 6)
        self.record['drive_coil'] = 'salvaged drive coil on saddles bolted to the starboard housing deck, leads into both starboard cans'
        for i in (3, 4):
            for tag, mat in (('dark throat', self.glow), ('aft-facing bell', self.heat)):
                ob = self.find(f'Engine {i} {tag}', None)
                if ob:
                    ob.data.materials[0] = mat
        self.record['starboard_pod'] = 'running on the salvaged drive coil; throats lit'

    def cables(self):
        run = self.g('External cable run', self.cable, .01)
        clips = self.g('Cable clip', self.kit.steel, .01)
        for plan in CABLES:
            for off in (-.35, .35):
                pts = []
                for i,(x,y) in enumerate(plan):
                    t = V((plan[min(i+1,len(plan)-1)][0]-plan[max(i-1,0)][0], plan[min(i+1,len(plan)-1)][1]-plan[max(i-1,0)][1], 0)).normalized()
                    side = V((-t.y, t.x, 0))
                    q = V((x,y,0))+side*off
                    hit = self.cast(V((q.x,q.y,60)), V((q.x,q.y,-20)))
                    pts.append(V((q.x,q.y,(hit[0].z if hit else 12)+.3)))
                run.tube(pts, .19, 6)
            for p, q in zip(plan, plan[1:]):
                hit = self.cast(V((*p,60)), V((*p,-20)))
                if hit:
                    t = V((q[0]-p[0], q[1]-p[1], 0)).normalized()
                    clips.obox(hit[0]+V((0,0,.33)), t, V((-t.y,t.x,0)), V((0,0,1)), .2, .75, .22)
        self.record['repairs'].append('external cable runs from the drive coil to the engine-room hatch')

    # ---- assembly ------------------------------------------------------------
    def emit_parts(self):
        for (name, _), (geo, mat, edge) in self.parts.items():
            if not geo.v:
                continue
            ob = self.kit.mesh('Damage - '+name, geo.v, geo.f, mat)
            bm = bmesh.new()
            bm.from_mesh(ob.data)
            bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
            bm.to_mesh(ob.data)
            bm.free()
            if edge:
                crisp(ob, edge)

    def apply_cuts(self):
        groups = {}
        bpy.context.view_layer.update()
        boxes = {}
        for name in self.original:
            ob = bpy.data.objects.get(name)
            if ob is None or any(ob.name.startswith(k) or k in ob.name for k in UNCUT):
                continue
            pts = [ob.matrix_world @ V(c) for c in ob.bound_box]
            boxes[name] = (V([min(p[i] for p in pts) for i in range(3)]), V([max(p[i] for p in pts) for i in range(3)]))
        for verts, faces, targets in self.cuts:
            geo = Geo()
            geo.add(verts, faces)
            lo, hi = geo.bounds()
            if targets is None:
                targets = [n for n,(a,b) in boxes.items()
                           if all(a[i] <= hi[i]+.05 and b[i] >= lo[i]-.05 for i in range(3))]
            for t in targets:
                groups.setdefault(t, Geo()).add(verts, faces)
        for name, geo in groups.items():
            target = bpy.data.objects[name]
            cutter = self.kit.mesh('Damage cutter - '+name, geo.v, geo.f, self.backing)
            bm = bmesh.new()
            bm.from_mesh(cutter.data)
            bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
            bm.to_mesh(cutter.data)
            bm.free()
            cutter.hide_render = True
            cutter.display_type = 'WIRE'
            mod = target.modifiers.new('Impact damage', 'BOOLEAN')
            mod.operation, mod.object, mod.solver = 'DIFFERENCE', cutter, 'EXACT'
            mod.material_mode = 'TRANSFER'
            mod.use_self = True
            mod.use_hole_tolerant = True
        self.record['cut_objects'] = len(groups)


def build(kit, state):
    """Layer the named damage state onto the finished clean ship."""
    d = Damage(kit, state)
    d.build_derelict()
    if d.patched:
        d.build_patched()
    else:
        d.lights_off()
    d.emit_parts()
    d.apply_cuts()
    return d.record
