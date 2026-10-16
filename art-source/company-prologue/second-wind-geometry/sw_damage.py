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

# Exposure. For a decade the hulk hung on its tether with its port side and
# deck turned into the belt's drift. Belt debris moves slowly relative to the
# ship, so the rare fast strikes that leave a lipped basin landed on that
# windward face; the lee took slow knocks that dent rather than crater, and
# glancing contact scraped seams. Salvagers did the rest.
#
# Impact basins: (name, ray origin, ray target, radius, depth, elongation
# along the strike's ground track, lip shards, patched repair). The escape
# crew patched only what the flight needed: 'collar' is the rough patch under
# the sponson's armour collar that canon puts there at arrival, 'doubler' a
# welded doubler over occupied space. Everything else was left open.
BASINS = [
    # Continuity scar 1: the impact cluster on the forward port flank,
    # around the grapple sponson and the port bow cheek above it.
    ('Port sponson cowl strike', (33,62,7.6), (33,30,7.4), 2.2, 1.0, 1.25, 8, 'collar'),
    ('Port bow cheek strike', (45,42,40), (45,21,13), 3.0, 1.3, 1.3, 9, 'doubler'),
    ('Port bow flank strike', (60,44,3), (51,21,3), 2.2, 1.0, 1.2, 7, None),
    # Continuity scar 2: the scarred port aft shoulder over the dead pod.
    ('Port aft shoulder strike', (-12,58,32), (-12,36.5,13.5), 3.6, 1.6, 1.2, 10, None),
    ('Port pod lower casing strike', (-9,62,-2), (-9,37,-2.5), 2.0, .9, 1.3, 6, None),
    ('Port intake cap strike', (-1,60,5), (-1,36,5), 1.8, .8, 1.2, 6, None),
    ('Spine housing strike', (3.5,6,60), (3.6,5.6,0), 1.6, .7, 1.1, 6, 'doubler'),
    ('Bow glacis strike', (76,-3,32), (54,-3,10), 2.2, 1.0, 1.35, 7, None),
    ('Aft deck strike', (-23.5,9.3,60), (-23.5,9.3,0), 1.6, .8, 1.0, 6, None),
    # The lee side: slow knocks, shallow dents without lips.
    ('Starboard forebody deck dent', (25,-17.5,60), (25.6,-17.2,0), 1.8, .45, 1.1, 0, None),
    ('Forward deck dent', (46,-6,60), (46.5,-6.5,0), 1.6, .4, 1.15, 0, None),
    ('Starboard cowl dent', (-14.5,-33,60), (-14,-33.4,0), 1.5, .4, 1.1, 0, None),
    ('Starboard flank dent', (43,-62,3.5), (43,-30,3.5), 1.5, .45, 1.3, 0, None),
    # The knock that bowed the starboard bay's forward frame and jammed its
    # door, the reason canon's second bay is sealed and damaged.
    ('Starboard bay door strike', (23,-60,-1), (23,-20,-.5), 2.0, .5, 1.2, 0, None),
    # The port outer can, crushed against the anchor rock as the hulk swung
    # on its tether. Its nozzle ring stays round (the art lock).
    ('Port outer engine can strike', (-38,60,5), (-38,33,2.5), 2.4, 1.2, 1.2, 0, None),
]

# Clustered small strikes, weighted to the windward face: (name, cast,
# centre, spread, count, radius range). cast 'down' drops onto (x, y);
# 'port' and 'starboard' fire inboard at (x, z) on that flank.
CLUSTERS = [
    ('Port forward flank cluster', 'port', (37,5), (8,3.5), 12, (.35,.8)),
    ('Port bow deck cluster', 'down', (41,19), (5,3), 7, (.3,.7)),
    ('Port aft shoulder cluster', 'down', (-12,32), (6,3.5), 10, (.3,.75)),
    ('Port pod flank cluster', 'port', (-14,3), (8,3), 7, (.3,.65)),
    ('Spine cluster', 'down', (16,2), (9,3), 7, (.25,.5)),
    ('Aft deck cluster', 'down', (-18,4), (6,5), 5, (.3,.6)),
    ('Starboard bow deck cluster', 'down', (28,-11), (7,5), 3, (.25,.5)),
    ('Scattered strikes', 'down', (8,8), (44,18), 12, (.25,.5)),
]

# Spalled armour: star-shaped tears through the outer plate onto backing,
# with petals peeled outward. (name, origin, target, radius, depth, petals)
SPALLS = [
    ('Starboard flank module tear', (29,-60,5.5), (29,-30,5.5), 1.8, .7, 3),
    ('Port citadel wall spall', (21,50,12.4), (21,20,12.4), 1.4, .55, 2),
    ('Starboard bow cheek spall', (54,-32,15), (47,-18,12), 1.6, .6, 3),
    ('Port nose block spall', (68,20,8), (57,4,6), 1.4, .55, 2),
]

# Gouges and scrapes from glancing, slow contact, the most belt-like damage
# on the ship: (name, start origin, end origin, cast direction, half width,
# depth). The sponson scrape is a rock that rolled along the hull.
GOUGES = [
    ('Port sponson scrape', (29.5,62,-1.2), (47,62,-1.8), (0,-1,0), .6, .4),
    ('Port pod contact scrape', (-15,62,-3.2), (-31,62,-4.2), (0,-1,0), .55, .35),
    ('Port cowl gouge', (-2.5,27,60), (-7,34.5,60), (0,0,-1), .6, .4),
    ('Port glacis gouge', (44,4,60), (53,9.5,60), (0,0,-1), .55, .35),
]

# Deck armour plates gone: (object name, plate centre, kept corner, how).
# 'torn' leaves a jagged remnant from an impact; 'unbolted' is salvage, a
# clean removal with a ring of bare studs. Both expose backing and stringers.
MISSING_PLATES = [
    ('Forebody replaceable armor', (32.5,16.75), (-1,1), 'torn'),
    ('Forebody replaceable armor', (24.5,16.75), (1,1), 'torn'),
    ('Forebody replaceable armor', (32.5,-10.25), None, 'unbolted'),
    ('Drive casing armour plate', (-11.85,28.6), None, 'unbolted'),
    ('Drive casing armour plate', (-7.7,-29.45), None, 'unbolted'),
    ('Aft deck armour plate', (-23.65,-9.2), None, 'unbolted'),
]

# Buckled deck plates from slow heavy contact (object name, centre, fold).
BUCKLES = [
    ('Forebody replaceable armor', (24.5,10.25), .42),
    ('Drive casing armour plate', (-11.85,-28.6), .36),
]

# Heat-sink fins bent on the windward pod: (side, fin indices, sector, shift).
BENT_FINS = [(1, (1,2,3,4), lambda y,z: y > 30 and z > 7.5, (.75,-.55,.9,.5))]

# Continuity scar 3, scorched waist service covers: salvagers torch-cut the
# runs for their contents. No power meant no fire; the heat tint is only
# either side of each cut. (object, cut centre, gap, radius, side)
TORCH_CUTS = [('Protected waist service line', (9.5,24.2,7.2), 2.4, .7, 1),
              ('Exposed waist service trunk', (4.0,10.9,11.5), 1.4, .7, 1)]

# The bent rib: the sealed starboard bay's forward frame jamb, bowed
# outboard where the bay-door strike landed. The port bay stays clear: it
# is the working bay. (x, y, z0, z1, (z, outboard bow) stations)
RIB = (26, -23, -5.5, 4.5, [(-5.5,0),(-2.6,.6),(-.4,1.35),(1.8,.7),(4.5,0)])

# Parts-bin stripping: pump machinery off both housing decks, both pods'
# access panels, the port can feed pipes. The drive coils are gone too
# (see empty_cradle). Sign 0 means both sides.
STRIPPED = [('Turbopump housing', 0), ('Turbopump vent slot', 0), ('Pump drive drum', 0),
            ('Can feed pipe', 1), ('Casing access panel', 0), ('Panel grab strap', 0)]

# Point defence stripped: the gun houses went; the barbettes stayed bolted
# down, so all four slots remain. Reinstalled by hand for the escape.
TURRET_KEEP = ('barbette foot', 'armoured collar', 'traverse gap')
REPAINTED_TURRETS = (1, 3)

# Sensor mast parts lost when it sheared.
MAST_LOST = ('Mast crosstree', 'Scanner turntable', 'Search radar scanner bar',
             'Masthead light', 'Crosstree signal light')

# Where the hulk was moored, on the windward flank: (name, origin, target).
TETHERS = [('Bow tether padeye', (39.5,62,-2.2), (39.5,30,-2.2)),
           ('Pod tether padeye', (-12,62,-4.6), (-12,30,-4.6))]

# Occupied space the crew sealed: every strike over the spine and command
# deck gets a pressure patch, none elsewhere. (x0, x1, y0, y1, z above)
PRESSURE_ZONE = (-6, 32, -8.6, 8.6, 14)
# Access trunks given borrowed hab-door seals (name, side); one dogged shut.
SEALED_TRUNKS = [('Forward access trunk', 1), ('Forward access trunk', -1), ('Aft access trunk', 1)]
DOGGED_TRUNK = ('Aft access trunk', -1)

# External runs (patched): plan points; z is cast onto the ship.
CABLES = [[(-29.6,-26.8),(-27.4,-22),(-24,-17),(-20.5,-13.5),(-17.5,-11.6),(-14,-8.2),(-11.4,-4.4),(-10.6,-2.8)]]
# Jury propellant feed to the running pod, whose turbopumps were stripped:
# from the pylon fuel trunks to a salvaged pump on the housing deck.
FEED_HOSE = [(-21.5,-13.2,8.1), (-24.5,-15.2,8.6), (-27.4,-18.6,10.2), (-29.0,-20.6,11.0)]
FEED_PUMP = (-30.4, -21.0, 11.0)
FEED_TO_CAN = [(-31.8,-21.0,11.0), (-32.8,-22.3,10.0), (-33.2,-23.6,8.8)]

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
        self.rubber = m('Borrowed seal rubber', (.05,.045,.04), 0, .9)
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
                             rng.uniform(1,1.4), 0, 6)
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
            if self.patched and repair:
                sh = 0
            self.sites[name] = self.basin(name, o, t, r, d, el, sh)
            self.record['basins'].append({'name': name, 'radius': r, 'depth': d,
                                          'repair': repair if self.patched else None})
        for args in CLUSTERS:
            self.strike(*args)
        for args in SPALLS:
            self.spall(*args)
        for args in GOUGES:
            self.gouge(*args)
        self.smash_can()
        self.bend_fins()
        self.missing_plates()
        self.buckle_plates()
        self.torch_cuts()
        self.bow_rib()
        self.shear_mast()
        self.strip()
        self.tethers()
        if not self.patched:
            self.strip_turrets()
            self.empty_cradle(1, 'cut')
            self.empty_cradle(-1, None)

    def smash_can(self):
        site = self.sites.get('Port outer engine can strike')
        if not site:
            return
        p, n, u, v, _ = site
        x, y, w = ENGINES[0]
        # Only the can deforms: the nozzle ring, bell and lip stay round.
        can = self.find('Thrust bay engine can', (x+5, y), 1)
        if can:
            self.crush(can, p, -n, 4.2, 1.0)
        self.record['smashed_can'] = 'Engine 1 (port outer) can crushed from outboard; nozzle ring intact'

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
                            reach = min(1, (abs(y)-29)/6+max(0, (z-7.5))/6)
                            vert.co.x += dx*max(0, reach)
                    ob.data.update()
        self.record['bent_fins'] = 'port fin bank, top outboard'

    def missing_plates(self):
        studs = self.g('Bare plate stud', self.kit.steel, .005)
        for name, centre, keep, how in MISSING_PLATES:
            ob = self.find(name, centre)
            if ob is None:
                continue
            lo = V([min(c[i] for c in ob.bound_box) for i in range(3)])
            hi = V([max(c[i] for c in ob.bound_box) for i in range(3)])
            W, H = hi.x-lo.x, hi.y-lo.y
            cx, cy = (lo.x+hi.x)/2, (lo.y+hi.y)/2
            if how == 'torn':
                kx, ky = keep
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
            else:
                self.remove(ob)  # unbolted and carried off
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
            if how == 'unbolted':
                # The bare studs the plate's nuts came off.
                x0, x1, y0, y1 = lo.x+.3, hi.x-.3, lo.y+.3, hi.y-.3
                edges = [((x0,y0),(x1,y0)), ((x1,y0),(x1,y1)), ((x1,y1),(x0,y1)), ((x0,y1),(x0,y0))]
                for (ax,ay), (bx,by) in edges:
                    k = max(2, round(math.hypot(bx-ax, by-ay)/1.1))
                    for i in range(k):
                        t = i/k
                        sx, sy = ax+(bx-ax)*t, ay+(by-ay)*t
                        hit = self.kit.surface(sx, sy)
                        if hit:
                            studs.disc((sx, sy, hit[0].z+.2), (0,0,1), .09, .3, 6)
            self.record['missing_plates'].append({'object': name, 'centre': list(centre), 'how': how})

    def buckle_plates(self):
        for name, centre, amp in BUCKLES:
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

    def torch_cuts(self):
        """Salvagers cut service runs for their contents: square ends, heat
        tint only either side of each cut, the conductors cut short."""
        tint = self.g('Torch-cut heat tint', self.scorch, .02)
        core = self.g('Cut conductor stub', self.kit.dark, .01)
        X, Y, Z = V((1,0,0)), V((0,1,0)), V((0,0,1))
        for name, (x,y,z), gap, r, sign in TORCH_CUTS:
            ob = self.find(name, (x,y), sign)
            if ob is None:
                continue
            geo = Geo()
            geo.obox((x,y,z), X, Y, Z, gap/2, r+.5, r+.5)
            self.cuts.append((geo.v, geo.f, [ob.name]))
            for sx in (-1, 1):
                end = x+sx*gap/2
                tint.disc((end+sx*.4, y, z), (1,0,0), r+.04, .8, 16)
                for dy, dz in ((-.2,.15),(.22,0),(0,-.25)):
                    core.disc((end-sx*.12, y+dy*r/.7, z+dz*r/.7), (1,0,0), .14*r/.7, .3, 6)
            self.record.setdefault('torch_cuts', []).append(name)

    def bow_rib(self):
        x, y, z0, z1, stations = RIB
        jamb = self.find('Bay jamb', (x,y), -1 if y < 0 else 1)
        if jamb is None:
            return
        mat = jamb.active_material
        self.remove(jamb)
        out = -1 if y < 0 else 1
        geo = self.g('Bowed bay frame jamb', mat, .08)
        rings = []
        for z, bow in stations:
            w = .6 if bow < .9 else .5
            rings.append([V((x+dx, y+out*bow+dy, z)) for dx,dy in ((-w,-1.5),(w,-1.5),(w,1.5),(-w,1.5))])
        geo.loft(rings)
        crumple = self.g('Crumpled frame flange', self.kit.steel, .02)
        apex = max(b for _,b in stations)
        for dz, dx in ((-.6,.65),(.2,-.65)):
            base = V((x+dx, y+out*(apex+1.5), dz))
            tri = [base+V((0,0,-.7)), base+V((0,0,.6)), base+V((dx*.6,out*.3,0))]
            crumple.loft([[q-V((dx*.05,0,0)) for q in tri], tri])
        self.record['bowed_rib'] = 'starboard (sealed) bay forward frame jamb, bowed outboard'

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
        self.record['sheared_mast'] = 'sensor mast sheared above the pedestal; scanner gone, one whip lost, one bent'

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
        self.record['stripped'] = 'turbopumps off both housing decks; both pods\' access panels; port can feed pipes'

    def collar_box(self, i):
        collar = self.find(f'Turret {i} armoured collar', None)
        pts = [collar.matrix_world @ V(c) for c in collar.bound_box]
        lo = V([min(q[k] for q in pts) for k in range(3)])
        hi = V([max(q[k] for q in pts) for k in range(3)])
        return (lo+hi)/2, (hi.x-lo.x)/2, hi.z

    def strip_turrets(self):
        studs = self.g('Bare turret stud', self.kit.steel, .005)
        for i in range(1, 5):
            for ob in [o for o in bpy.data.objects if o.name.startswith(f'Turret {i} ')]:
                if not any(k in ob.name for k in TURRET_KEEP):
                    self.remove(ob)
            c, R, top = self.collar_box(i)
            for k in range(16):
                a = TAU*(k+.5)/16
                studs.disc((c.x+(R-.25)*math.cos(a), c.y+(R-.25)*math.sin(a), top+.12), (0,0,1), .09, .24, 6)
        self.record['point_defence'] = 'gun houses stripped; barbettes and bare stud rings remain at all four slots'

    def tethers(self):
        """Where the hulk was moored: padeyes on the windward flank, the cut
        tether ends still shackled on, bare chafe bands round them."""
        eye = self.g('Tether padeye', self.kit.steel, .03)
        rope = self.g('Frayed tether stub', self.cable, .01)
        chafe = self.g('Tether chafe band', self.salvage[1], .01)
        for name, o, t in TETHERS:
            hit = self.cast(o, t)
            if hit is None:
                continue
            p, n, _ = hit
            u, v = V((1,0,0)), n.cross(V((1,0,0))).normalized()
            ring = [self.snap(p+u*sx*1.5+v*sy*1.1, n, .02) for sx,sy in ((-1,-1),(1,-1),(1,1),(-1,1))]
            self.flat(chafe, ring, n, .03)
            eye.obox(p+n*.18, u, v, n, .7, .5, .14)
            for sy in (-1, 1):
                eye.obox(p+n*.65+v*sy*.2, u, v, n, .4, .06, .45)
            eye.disc(p+n*.85, v, .13, .7, 8)
            end = p+n*2.2+u*.5-v*.3
            rope.tube([p+n*.85, p+n*1.5+u*.3, end], .22, 8)
            for du, dv in ((.35,.2),(-.1,.4),(.25,-.35)):
                rope.tube([end, end+n*.5+u*du+v*dv], .07, 5)
        self.record['tethers'] = 'two padeyes on the port flank with cut tether stubs and chafe bands'

    def empty_cradle(self, s, stubs):
        """A pod with its drive coil lifted out: empty saddles on the housing
        deck. stubs: 'cut' feed lines cut short, 'flanged' cut and sealed
        with blind flanges (the escape crew's work), None feed lines intact."""
        x, y = -30.0, s*31.0
        deck = self.snap(V((x,y,9.8)), V((0,0,1)))
        zc = deck.z+2.35
        saddle = self.g('Empty coil saddle', self.salvage[1], .04)
        pads = self.g('Saddle bearing pad', self.kit.dark, .02)
        bolts = self.g('Patch bolt head', self.kit.steel, .005)
        for dy in (-3.15, 3.15):
            saddle.obox(V((x,y+dy,(deck.z+zc)/2-.4)), V((1,0,0)), V((0,1,0)), V((0,0,1)), 1.5, .22, (zc-deck.z)/2-.4)
            saddle.obox(V((x,y+dy,deck.z+.07)), V((1,0,0)), V((0,1,0)), V((0,0,1)), 1.8, .5, .07)
            for sx in (-1, 1):
                a = math.radians(38)*sx
                ax, az = V((math.cos(a),0,-math.sin(a))), V((math.sin(a),0,math.cos(a)))
                c = V((x+sx*1.05, y+dy, zc-1.55))
                saddle.obox(c, ax, V((0,1,0)), az, .75, .22, .2)
                pads.obox(c+az*.22, ax, V((0,1,0)), az, .6, .18, .03)
            for dx in (-1.45,1.45):
                bolts.disc((x+dx,y+dy,deck.z+.17), (0,0,1), .1, .08, 6)
        if stubs:
            stub = self.g('Cut feed line stub', self.kit.steel, .02)
            ends = self.g('Blind flange', self.salvage[2], .02) if stubs == 'flanged' else \
                self.g('Cut pipe end', self.kit.dark, 0)
            for (py, z0, z1) in ((32, 5.8, 6.9), (23.6, 4.4, 5.5), (32, 8.6, 7.7), (23.6, 8.6, 7.7)):
                stub.disc((-33.2, s*py, (z0+z1)/2), (0,0,1), .45, abs(z1-z0), 10)
                if stubs == 'flanged':
                    ends.disc((-33.2, s*py, z1), (0,0,1), .72, .16, 10)
                    for k in range(4):
                        a = TAU*(k+.5)/4
                        bolts.disc((-33.2+.55*math.cos(a), s*py+.55*math.sin(a), z1+.1*(1 if z1 > z0 else -1)), (0,0,1), .07, .06, 6)
                else:
                    ends.disc((-33.2, s*py, z1), (0,0,1), .38, .04, 10)

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
        """Only what the flight needed: thrust, power, pressure, navigation."""
        for name, *_, repair in BASINS:
            site = self.sites.get(name)
            if site and repair == 'collar':
                self.collar_patch(name, site)
            elif site and repair == 'doubler':
                self.salvage_plate(name, site, 1.3)
        x0, x1, y0, y1, zmin = PRESSURE_ZONE
        for cluster, done in self.strikes.items():
            for i, (site, r) in enumerate(done):
                q = site[0]
                if x0 < q.x < x1 and y0 < q.y < y1 and q.z > zmin:
                    self.pressure_patch(f'{cluster} {i}', site, r)
        self.borrowed_seals()
        self.reinstall_turrets()
        self.shore_rib()
        self.weld_bay_door()
        self.sleeve_cuts()
        self.jury_mast()
        self.empty_cradle(1, 'flanged')
        for ob in self.named('Drive warning light', 1):
            ob.data.materials[0] = self.dead
        self.record['port_pod'] = 'dead and cold: nozzles open, coil cradle empty, cut feed lines blind-flanged'
        self.drive_coil()
        self.jury_feed()
        self.cables()

    def flat(self, geo, ring, n, thick):
        k = len(ring)
        verts = ring+[q+n*thick for q in ring]
        faces = [tuple(reversed(range(k))), tuple(range(k, 2*k))]
        faces += [(i, (i+1) % k, k+(i+1) % k, k+i) for i in range(k)]
        geo.add(verts, faces)

    def emit_plate(self, name, ring, n, mat, thick=.12, bolts=False):
        """A flat plate on a ring of points, welded or bolted round its edge."""
        k = len(ring)
        verts = ring+[q+n*thick for q in ring]
        faces = [tuple(reversed(range(k))), tuple(range(k, 2*k))]
        faces += [(i, (i+1) % k, k+(i+1) % k, k+i) for i in range(k)]
        self.g(name, mat, .03).add(verts, faces)
        centre = sum(ring, Vector())/k
        if bolts:
            studs = self.g('Patch bolt head', self.kit.steel, .005)
        else:
            beads = self.g('Weld bead', self.weld, .01)
        for i in range(k):
            a, b = ring[i], ring[(i+1) % k]
            if bolts:
                for t in (.2,.4,.6,.8):
                    studs.disc(a.lerp(b, t).lerp(centre, .1)+n*(thick+.02), n, .08, .07, 6)
            else:
                beads.bead(a, b, n, .1)

    def salvage_plate(self, name, site, scale, tile=None):
        p, n, u, v, _ = site
        rng = random.Random(name)
        r = next(r for nm,_,_,r,*_ in BASINS if name.startswith(nm))
        el = next(e for nm,_,_,_,_,e,*_ in BASINS if name.startswith(nm))
        # Cut stock squared to the hull's own lines, only slightly off true.
        turn = rng.uniform(-.07,.07)
        uu, vv = u*math.cos(turn)+v*math.sin(turn), v*math.cos(turn)-u*math.sin(turn)
        a, b = r*scale*el, r*scale
        q, lift = p, .07
        if tile:
            su, sv, i = tile
            q = p+u*su*a*.82+v*sv*b*.82
            lift = .07+.13*i  # each later sheet laps over the earlier ones
        mat = self.salvage[sum(map(ord, name.split(' tile')[0])) % len(self.salvage)]
        self.grid_plate('Welded salvage plate', q, n, uu, vv, a, b, mat, .14, lift, only=self.skin)
        self.record['repairs'].append(f'salvage plate over {name}')

    @property
    def skin(self):
        keep = ('hull', 'armor', 'armour', 'plate', 'cowl', 'casing', 'module', 'cap', 'body',
                'Sponson', 'sponson', 'housing', 'Spine', 'block', 'belt', 'lip', 'shield')
        drop = ('Damage', 'lamp', 'light', 'hatch', 'Hatch', 'vent', 'louvre', 'louver', 'strap',
                'hood', 'Intake mouth', 'manifold', 'frame', 'skin')
        return {n for n in self.original if any(k in n for k in keep) and not any(d in n for d in drop)}

    def grid_plate(self, name, p, n, u, v, a, b, mat, cut, lift, thick=.12, k=3, only=None):
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
                got = [height(p+axis*s*half+across*t*other) for t in (-1,-.67,-.33,0,.33,.67,1)]
                got = [h for h in got if h is not None]
                hs.append(max(got) if got else None)
            # A station off the part takes its nearest measured neighbour.
            for i in range(len(hs)):
                if hs[i] is None:
                    near = sorted((abs(i-j), h) for j, h in enumerate(hs) if h is not None)
                    hs[i] = near[0][1] if near else 0
            return hs
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
            beads.bead(q0-n*lift, q1-n*lift, n, .1)

    def collar_patch(self, name, site):
        """The patch under the collar canon puts there at arrival: an
        irregular salvaged steel plate with rough welds, scorched round."""
        p, n, u, v, _ = site
        r = next(r for nm,_,_,r,*_ in BASINS if nm == name)
        rng = random.Random(name)
        angles = sorted(TAU*(k+rng.uniform(-.25,.25))/7 for k in range(7))
        ring = [self.snap(p+u*math.cos(a)*r*1.45*rng.uniform(.85,1.12)+v*math.sin(a)*r*1.2*rng.uniform(.85,1.12), n, .06)
                for a in angles]
        self.flat(self.g('Collar salvage patch', self.salvage[1], .03), ring, n, .14)
        beads = self.g('Weld bead', self.weld, .01)
        for i in range(len(ring)):
            beads.bead(ring[i], ring[(i+1) % len(ring)], n, .15)
        self.record['repairs'].append(f'rough salvage patch under the collar over {name}')

    def pressure_patch(self, name, site, r):
        p, n, u, v, _ = site
        half = r*1.4+.25
        corners = ((-1,-1),(1,-1),(1,1),(-1,1))
        # A borrowed hab-door seal squeezed out round the patch.
        gasket = [self.snap(p+u*sx*(half+.12)+v*sy*(half+.12), n, .03) for sx,sy in corners]
        self.flat(self.g('Borrowed seal gasket', self.rubber, .01), gasket, n, .1)
        ring = [self.snap(p+u*sx*half+v*sy*half, n, .06) for sx,sy in corners]
        self.emit_plate('Bolted pressure patch', ring, n, self.salvage[sum(map(ord, name)) % 3], .09, bolts=True)
        self.record['repairs'].append(f'pressure patch over {name}')

    def borrowed_seals(self):
        """Hab-door seals taken for the trunks the crew needed to hold."""
        rubber = self.g('Borrowed seal gasket', self.rubber, .01)
        def box(ob):
            pts = [ob.matrix_world @ V(c) for c in ob.bound_box]
            lo = V([min(q[k] for q in pts) for k in range(3)])
            hi = V([max(q[k] for q in pts) for k in range(3)])
            return (lo+hi)/2, (hi.x-lo.x)/2, hi.z
        up = V((0,0,1))
        for name, sign in SEALED_TRUNKS:
            for ob in self.named(name, sign):
                c, R, top = box(ob)
                rubber.disc((c.x, c.y, top-.3), up, R+.1, .16, 16)
        name, sign = DOGGED_TRUNK
        obs = self.named(name, sign)
        if obs:
            c, R, top = box(obs[0])
            blank = self.g('Trunk blanking plate', self.salvage[0], .02)
            bolts = self.g('Patch bolt head', self.kit.steel, .005)
            blank.disc((c.x, c.y, top+.1), up, R+.05, .2, 12)
            for k in range(10):
                a = TAU*(k+.5)/10
                bolts.disc((c.x+(R-.2)*math.cos(a), c.y+(R-.2)*math.sin(a), top+.24), up, .08, .08, 6)
            for dy in (-.45, .45):
                blank.obox(V((c.x, c.y+dy, top+.3)), V((1,0,0)), V((0,1,0)), up, R*.85, .1, .08)
        self.record['repairs'].append('borrowed hab-door seals on access trunks; one trunk dogged shut with a bolted blank')

    def reinstall_turrets(self):
        """The gantry point-defence mounts carried aboard and bolted back on:
        a bare adapter ring and fresh bolts at every barbette, and two gun
        houses still in another owner's paint."""
        ring = self.g('Turret adapter ring', self.salvage[1], .02)
        bolts = self.g('Patch bolt head', self.kit.steel, .005)
        for i in range(1, 5):
            c, R, top = self.collar_box(i)
            ring.disc((c.x, c.y, top+.07), (0,0,1), R*.97, .14, 24)
            for k in range(16):
                a = TAU*(k+.5)/16
                bolts.disc((c.x+(R-.2)*math.cos(a), c.y+(R-.2)*math.sin(a), top+.19), (0,0,1), .08, .08, 6)
            if i in REPAINTED_TURRETS:
                for ob in self.named(f'Turret {i} '):
                    if any(k in ob.name for k in ('gun house', 'rear bustle', 'mantlet')):
                        ob.data.materials[0] = self.salvage[2]
        self.record['repairs'].append('gantry point-defence reinstalled on adapter rings; two gun houses mismatched')

    def shore_rib(self):
        """A length of cargo rail bolted along the bowed frame, wedged at
        the bow and clamped to the coamings at both ends."""
        x, y, z0, z1, stations = RIB
        out = -1 if y < 0 else 1
        apex = max(b for _,b in stations)
        rail_y = y+out*(1.5+apex+.22)
        face = V((0,out,0))
        rail = self.g('Cargo rail shore', self.rail, .05)
        slots = self.g('Cargo rail tie-down slot', self.kit.dark, 0)
        clamps = self.g('Rail clamp bracket', self.kit.steel, .04)
        wedges = self.g('Shore wedge', self.salvage[1], .02)
        bolts = self.g('Patch bolt head', self.kit.steel, .005)
        a, b = V((x, rail_y, z0+.55)), V((x, rail_y, z1-.55))
        rail.bar(a, b, face, .7, .3)
        for k in range(int((b-a).length/.9)):
            slots.obox(a+V((0,0,.45+k*.9))+face*.15, V((0,0,1)), V((1,0,0)), face, .16, .09, .02)
        for z, bow in stations:
            jamb = y+out*(1.5+bow)
            gap = abs(rail_y-out*.15-jamb)
            if z in (z0, z1):
                continue
            if gap > .08:
                wedges.obox(V((x, (rail_y-out*.15+jamb)/2, z)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .3, gap/2, .25)
        for z in (z0+.8, z1-.8):
            jamb = y+out*1.5
            clamps.obox(V((x, (jamb+rail_y)/2, z)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .5, abs(rail_y-jamb)/2+.15, .22)
            for dx in (-.3, .3):
                bolts.disc((x+dx, rail_y+out*.17, z), face, .08, .06, 6)
        self.record['repairs'].append('bent rib shored with a length of cargo rail')

    def weld_bay_door(self):
        """The jammed starboard bay door, welded shut with dog plates."""
        door = self.find('Starboard bay sealed sectional door', None)
        if door is None:
            return
        pts = [door.matrix_world @ V(c) for c in door.bound_box]
        lo = V([min(q[k] for q in pts) for k in range(3)])
        hi = V([max(q[k] for q in pts) for k in range(3)])
        yf = lo.y-.4  # proud of the door stiffeners
        beads = self.g('Weld bead', self.weld, .01)
        dogs = self.g('Welded dog plate', self.salvage[1], .02)
        out = V((0,-1,0))
        # Dog plates bridge door to frame at both ends and along the sill.
        for z in (lo.z+1.4, (lo.z+hi.z)/2, hi.z-1.4):
            for x in (lo.x+.5, hi.x-.5):
                dogs.obox(V((x, yf, z)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .9, .08, .4)
                beads.bead(V((x-.9, yf-.08, z-.4)), V((x-.9, yf-.08, z+.4)), out, .07)
                beads.bead(V((x+.9, yf-.08, z-.4)), V((x+.9, yf-.08, z+.4)), out, .07)
        for x in (7.0, 14.0, 21.0):
            dogs.obox(V((x, yf, lo.z+.55)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .4, .08, .8)
        self.record['repairs'].append('starboard bay door welded shut')

    def sleeve_cuts(self):
        sleeve = self.g('Split clamp sleeve', self.salvage[1], .03)
        bolts = self.g('Patch bolt head', self.kit.steel, .005)
        for name, (x,y,z), gap, r, sign in TORCH_CUTS:
            L = gap+1.0
            sleeve.disc((x,y,z), (1,0,0), r+.14, L, 12)
            for dx in (-L/2+.12, L/2-.12):
                sleeve.disc((x+dx,y,z), (1,0,0), r+.23, .2, 12)
            for dx in (-L/3, 0, L/3):
                bolts.obox(V((x+dx,y,z+r+.2)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .18, .22, .1)
        self.record['repairs'].append('split clamp sleeves over the cut service runs')

    def jury_mast(self):
        if not hasattr(self, 'mast'):
            return
        mx, z0 = self.mast
        whip = self.g('Jury whip antenna', self.kit.steel, .01)
        whip.disc((mx+.75, 0, z0+2.4), (0,0,1), .11, 4.2, 6)
        bands = self.g('Hose clamp lashing', self.salvage[1], .01)
        for z in (z0+.9, z0+1.6):
            bands.disc((mx+.6, 0, z), (0,0,1), .28, .14, 8)
        # The salvaged navigation sensor on a bracket off the stub.
        nav = self.g('Salvaged nav sensor', self.salvage[0], .03)
        nav.obox(V((mx-.55, 0, z0+1.35)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .55, .12, .08)
        nav.disc((mx-1.05, 0, z0+1.62), (0,0,1), .48, .36, 12)
        self.g('Nav sensor window', self.kit.glass, 0).disc((mx-1.05, 0, z0+1.81), (0,0,1), .3, .04, 12)
        lead = self.g('External cable run', self.cable, .01)
        lead.tube([V((mx-1.05,.3,z0+1.45)), V((mx-.5,.45,z0+.9)), V((mx-.3,.5,z0-.4))], .07, 6)
        self.record['repairs'].append('jury whip antenna and salvaged nav sensor on the mast stub')

    def drive_coil(self):
        x, y = -30.0, -31.0
        deck = self.snap(V((x,y,9.8)), V((0,0,1)))
        zc = deck.z+2.35
        core = self.g('Salvaged drive coil casing', self.salvage[0], .04)
        wind = self.g('Salvaged drive coil winding', self.copper, .02)
        caps = self.g('Drive coil end cap', self.kit.steel, .03)
        saddle = self.g('Drive coil saddle', self.salvage[1], .04)
        bolts = self.g('Patch bolt head', self.kit.steel, .005)
        beads = self.g('Weld bead', self.weld, .01)
        # A cased coil: copper shows only in the cooling windows between
        # casing sections, recessed below the casing line.
        wind.disc((x,y,zc), (0,1,0), 1.72, 8.0, 16)
        for y0, y1 in ((-4,-2.3),(-1.5,-.4),(.4,1.5),(2.3,4)):
            core.disc((x,y+(y0+y1)/2,zc), (0,1,0), 1.9, y1-y0, 16)
        for dy in (-4.15, 4.15):
            caps.disc((x,y+dy,zc), (0,1,0), 2.0, .3, 16)
        for dy in (-3.15, 3.15):
            caps.disc((x,y+dy,zc), (0,1,0), 1.97, .26, 16)  # hold-down band over the casing
            saddle.obox(V((x,y+dy,(deck.z+zc)/2-.4)), V((1,0,0)), V((0,1,0)), V((0,0,1)), 1.5, .22, (zc-deck.z)/2-.4)
            saddle.obox(V((x,y+dy,deck.z+.07)), V((1,0,0)), V((0,1,0)), V((0,0,1)), 1.8, .5, .07)
            # Tie rods from the band down to the saddle foot: tied down.
            for sx in (-1, 1):
                caps.disc((x+sx*2.02, y+dy, (deck.z+zc)/2+.1), (0,0,1), .09, zc-deck.z+.3, 6)
            for dx in (-1.45,1.45):
                bolts.disc((x+dx,y+dy,deck.z+.17), (0,0,1), .1, .08, 6)
            beads.bead(V((x-1.8,y+dy-.52,deck.z)), V((x+1.8,y+dy-.52,deck.z)), V((0,0,1)), .08)
        # Terminal box on the outboard end; leads clipped down the deck to a
        # junction box, which takes them into the housing through one conduit.
        caps.obox(V((x,y-4.75,zc)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .7, .32, .7)
        jb = V((-31.7, -36.4, deck.z+.45))
        caps.obox(jb, V((1,0,0)), V((0,1,0)), V((0,0,1)), .45, .7, .42)
        leads = self.g('Drive coil lead', self.cable, .01)
        clips = self.g('Cable clip', self.kit.steel, .01)
        for dy in (-.25, .25):
            path = [V((x-.3, y-4.95, zc-.5+dy)), V((x-.6, y-5.2+dy, deck.z+.35)), V((jb.x+.5, jb.y+dy, deck.z+.35))]
            leads.tube(path, .13, 8)
        clips.obox(V((x-1.2, y-5.2, deck.z+.35)), V((1,0,0)), V((0,1,0)), V((0,0,1)), .15, .55, .2)
        leads.tube([jb+V((-.45,0,0)), jb+V((-1.1,0,-.6)), jb+V((-1.6,0,-1.6))], .22, 8)
        # Coolant pair from the inboard cap to the fin bank.
        hose = self.g('Coolant hose', self.cable, .01)
        for dx in (-.45, .45):
            hose.tube([V((x+dx, y+4.3, zc+.4)), V((x+dx+1.2, y+5.3, zc+1.0)), V((-26.4+dx, -24.8, 15.15)),
                       V((-24.2+dx, -24.8, 14.75))], .16, 8)
        self.record['drive_coil'] = 'cased salvaged drive coil tied down on saddles on the starboard housing deck; leads to a junction box; coolant pair to the fin bank'
        for i in (3, 4):
            for tag, mat in (('dark throat', self.glow), ('aft-facing bell', self.heat)):
                ob = self.find(f'Engine {i} {tag}', None)
                if ob:
                    ob.data.materials[0] = mat
        self.record['starboard_pod'] = 'running on the salvaged drive coil; throats lit'

    def jury_feed(self):
        """Propellant to the running pod, whose turbopumps were stripped: a
        hose off the pylon fuel trunks to a salvaged pump, then to the can feed."""
        hose = self.g('Jury feed hose', self.cable, .01)
        clips = self.g('Cable clip', self.kit.steel, .01)
        pump = self.g('Salvaged feed pump', self.salvage[2], .04)
        bolts = self.g('Patch bolt head', self.kit.steel, .005)
        path = [V(q) for q in FEED_HOSE]
        hose.tube(path+[V(FEED_PUMP)-V((1.1,0,0))], .2, 8)
        hose.tube([V(FEED_PUMP)+V((1.1,0,0))]+[V(q) for q in FEED_TO_CAN], .2, 8)
        px, py, pz = FEED_PUMP
        pump.disc((px, py, pz), (1,0,0), .75, 2.0, 10)
        pump.obox(V((px, py, pz-.75)), V((1,0,0)), V((0,1,0)), V((0,0,1)), 1.1, .7, .1)
        for dx in (-.8, .8):
            bolts.disc((px+dx, py, pz-.6), (0,0,1), .08, .08, 6)
        for a, b in zip(path, path[1:]):
            c = (a+b)/2
            t = (b-a).normalized()
            clips.obox(c, t, V((0,0,1)).cross(t).normalized(), V((0,0,1)), .15, .32, .3)
        self.record['repairs'].append('jury propellant feed and salvaged pump to the starboard cans')

    def cables(self):
        run = self.g('External cable run', self.cable, .01)
        clips = self.g('Cable clip', self.kit.steel, .01)
        for plan in CABLES:
            pts_by_off = []
            for off in (-.35, .35):
                pts = []
                for i,(x,y) in enumerate(plan):
                    t = V((plan[min(i+1,len(plan)-1)][0]-plan[max(i-1,0)][0], plan[min(i+1,len(plan)-1)][1]-plan[max(i-1,0)][1], 0)).normalized()
                    side = V((-t.y, t.x, 0))
                    q = V((x,y,0))+side*off
                    pts.append(V((q.x, q.y, self.ground(q.x, q.y)+.3)))
                run.tube(pts, .19, 6)
            # Clipped down every metre or so: a run under thrust must not move.
            for (ax,ay), (bx,by) in zip(plan, plan[1:]):
                L = math.hypot(bx-ax, by-ay)
                t = V((bx-ax, by-ay, 0)).normalized()
                for k in range(max(1, round(L/1.2))):
                    f = (k+.5)/max(1, round(L/1.2))
                    cx, cy = ax+(bx-ax)*f, ay+(by-ay)*f
                    clips.obox(V((cx, cy, self.ground(cx, cy)+.33)), t, V((-t.y,t.x,0)), V((0,0,1)), .12, .72, .22)
        # The run ends at a bolted blank on the engine-room hatch, through glands.
        hatch = self.find('Engine room access hatch', None)
        if hatch:
            pts = [hatch.matrix_world @ V(c) for c in hatch.bound_box]
            cx, cy = sum(q.x for q in pts)/8, sum(q.y for q in pts)/8
            R = (max(q.x for q in pts)-min(q.x for q in pts))/2
            top = max(q.z for q in pts)
            blank = self.g('Hatch gland blank', self.salvage[1], .02)
            bolts = self.g('Patch bolt head', self.kit.steel, .005)
            blank.disc((cx, cy, top+.08), (0,0,1), R+.05, .16, 12)
            for k in range(12):
                a = TAU*(k+.5)/12
                bolts.disc((cx+(R-.2)*math.cos(a), cy+(R-.2)*math.sin(a), top+.2), (0,0,1), .08, .07, 6)
            for dx, dy in ((-.35,-.7), (.35,-.7)):
                blank.disc((cx+dx, cy+dy, top+.35), (0,0,1), .3, .4, 8)
        self.record['repairs'].append('external cable run clipped every metre to a gland blank on the engine-room hatch')

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
