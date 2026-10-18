"""Shared turrets for every cinematic hull: one catalogue, built at any ship's scale.

A hull's driver seats a barbette and places a pivot empty at each turret slot,
then asks this module for the turret:

    import turret_kit as tk
    mats = {'armour': ..., 'plate': ..., 'steel': ..., 'dark': ...}   # 'glass' optional
    tk.barbette('Weapon WS 007 barbette', ring, lift, mats, mpu)      # fixed, at the slot
    t = tk.build('heavyac', 'Weapon WS 007', pivot, ring, mats, mpu)  # trainable, on the pivot

`ring` is the turret ring radius in hull units, read off the sprite. `mpu` is
the hull's metres per unit. The silhouette is proportioned to the ring, so
the turret fills the sprite's ring at any scale. Human-scale fittings are
sized in true metres: hatches, rungs, grab rails, bolts, periscopes. They
read correctly however big the ring is.

The pivot's frame: +X along the line of fire, +Z up, origin on top of the
barbette. Rotating the pivot about its local Z trains the turret. Each gun or
launcher group hangs on an elevation empty, `<prefix> elevation`, at its
trunnions. Rotating that about its local Y elevates it (negative angle lifts
the muzzles); keying a large elevation is checked against the house.

`build` returns {'parts': objects that train, 'elevation': the empty,
'muzzles': '<prefix> muzzle anchor <k>' empties (+X along the bore),
'spec': the catalogue entry}. Geometry is joined to one object per material
per group, so a turret costs a handful of objects however detailed it is.

detail='near' (default) models fittings down to about 0.1 m. That is for
shots that pass within a few hundred metres, like the Legion's C2 flyby.
detail='mid' drops everything under about 0.4 m, for formation shots at a
kilometre or more.

Proportions follow the game's own turret sprites. A hull overrides any
catalogue field per slot: build(..., barrels=2).
"""
import math
import bmesh
import bpy
from mathutils import Matrix, Vector

GAP = .08          # traverse gap, x ring radius: the trainable part starts this far up

# --- the catalogue -------------------------------------------------------------
# house: 'naval' (gunhouse), 'low' (flak gunhouse with sight frames), 'dome'
#        (small mount), 'box' (missile launcher on a yoke)
# gun:   'cannon', 'gatling', 'cluster'; for 'box', cells = (rows, cols) and cell = 'round' / 'square' / 'door'
# calibre and length are x ring radius: barrel radius at the muzzle, barrel length
WEAPONS = {
    'vulcan':      dict(house='dome', gun='gatling', barrels=6, calibre=.05, length=1.05),
    'lightac':     dict(house='dome', gun='cannon', barrels=1, calibre=.075, length=1.2, drum=True),
    'lightmg':     dict(house='dome', gun='cannon', barrels=2, calibre=.045, length=1.1, spacing=.2),
    'none':        dict(house='dome', gun='cannon', barrels=1, calibre=.07, length=1.15),
    'heavyac':     dict(house='naval', gun='cannon', barrels=3, calibre=.07, length=1.28, spacing=.27, roof=.62),
    'flak':        dict(house='low', gun='cannon', barrels=2, calibre=.055, length=1.35, spacing=.34, roof=.52,
                        brake='flak'),
    'dualflak':    dict(house='low', gun='cannon', barrels=2, calibre=.06, length=.95, spacing=.36, roof=.5,
                        brake='flak'),
    'heavymauler': dict(house='naval', gun='cannon', barrels=1, calibre=.075, length=1.6, roof=.58),
    'hveldriver':  dict(house='naval', gun='cannon', barrels=1, calibre=.08, length=1.75, roof=.56, bands='coil',
                        brake='none'),
    'devastator':  dict(house='naval', gun='cannon', barrels=2, calibre=.085, length=1.3, spacing=.38, roof=.64,
                        brake='heavy'),
    'mark9':       dict(house='naval', gun='cannon', barrels=2, calibre=.075, length=1.45, spacing=.34, roof=.6),
    'hellbore':    dict(house='naval', gun='cannon', barrels=1, calibre=.14, length=1.1, roof=.66, brake='heavy'),
    'hephaestus':  dict(house='naval', gun='cannon', barrels=1, calibre=.1, length=1.3, roof=.6, drum=True),
    'needler':     dict(house='naval', gun='cluster', barrels=6, calibre=.035, length=1.2, roof=.56),
    # Launchers follow the game's layered sprites: a rack sprite with the missiles
    # themselves drawn on it (RENDER_LOADED_MISSILES), so the missiles lie on
    # rails along the line of fire. missiles = (rows, cols); mlen and mrad are
    # x ring radius; mcol is the missile body colour off its sprite.
    'squall':      dict(house='box', rack='open', missiles=(1, 5), mlen=1.25, mrad=.065, mcol=(.42, .55, .62)),
    'hurricane':   dict(house='box', rack='fork', missiles=(1, 1), mlen=1.6, mrad=.16, mcol=(.6, .56, .48)),
    'harpoonpod':  dict(house='box', rack='open', missiles=(1, 3), mlen=1.2, mrad=.09, mcol=(.5, .6, .5)),
    'sabotpod':    dict(house='box', rack='open', missiles=(1, 3), mlen=.85, mrad=.095, mcol=(.66, .66, .62)),
    'salamanderpod': dict(house='box', rack='open', missiles=(1, 2), mlen=.9, mrad=.11, mcol=(.6, .3, .2)),
    'pilum':       dict(house='box', rack='tubes', missiles=(1, 3), mlen=1.2, mrad=.09, mcol=(.45, .45, .5)),
    'typhoon':     dict(house='box', rack='tubes', missiles=(1, 2), mlen=1.25, mrad=.16, mcol=(.4, .4, .5)),
    'annihilatorpod': dict(house='box', rack='tubes', missiles=(2, 3), mlen=1.0, mrad=.07, mcol=(.6, .55, .45)),
}
ALIASES = {'heavy_ac': 'heavyac', 'light_ac': 'lightac', 'hvd': 'hveldriver', 'harpoon': 'harpoonpod'}

CHAMFER_M = .1     # the largest edge chamfer anywhere, in metres: crisp plate, not pillows
_CAP = [1e9]       # CHAMFER_M in units, set per build

AX = {'X': Matrix.Rotation(math.pi/2, 4, 'Y'), 'Y': Matrix.Rotation(-math.pi/2, 4, 'X'), 'Z': Matrix.Identity(4)}


# --- geometry -----------------------------------------------------------------

class Mesh:
    """Geometry for one object of one material, built in its owner's frame."""

    def __init__(self, origin=(0, 0, 0)):
        self.bm = bmesh.new()
        self.base = Matrix.Translation(-Vector(origin))

    def _m(self, c, axis='Z', rot=None):
        m = self.base @ Matrix.Translation(Vector(c))
        if rot is not None:
            m = m @ rot
        return m @ AX[axis]

    def box(self, c, d, bevel=0.0, seg=1, rot=None):
        m = self._m(c, rot=rot) @ Matrix.Diagonal((*d, 1))
        verts = bmesh.ops.create_cube(self.bm, size=1.0, matrix=m)['verts']
        b = min(bevel, .45*min(d), _CAP[0])
        if b > 1e-4:
            edges = list({e for v in verts for e in v.link_edges})
            bmesh.ops.bevel(self.bm, geom=verts+edges, offset=b, offset_type='OFFSET', segments=seg,
                            profile=.5, affect='EDGES', clamp_overlap=True)

    def lathe(self, profile, n, c=(0, 0, 0), axis='Z', phase=0.0, loop=False, rot=None):
        """Solid of revolution about the local axis from [(radius, along), ...].
        Ends are capped (a zero radius just marks a closed end); loop=True joins
        the last point to the first for a ring with a hole and no caps."""
        m = self._m(c, axis, rot)
        pts = [p for p in profile if loop or p[0] > 1e-6]
        ang = [phase+2*math.pi*i/n for i in range(n)]
        rings = [[self.bm.verts.new(m @ Vector((r*math.cos(a), r*math.sin(a), z))) for a in ang] for r, z in pts]
        for i in range(len(rings) if loop else len(rings)-1):
            a, b = rings[i], rings[(i+1) % len(rings)]
            for k in range(n):
                self.bm.faces.new((a[k], a[(k+1) % n], b[(k+1) % n], b[k]))
        if not loop:
            self.bm.faces.new(rings[0])
            self.bm.faces.new(rings[-1])

    def cyl(self, c, r, depth, axis='X', n=24, bevel=0.0, rot=None):
        b = min(bevel, .45*r, .45*depth, _CAP[0])
        h = depth/2
        prof = [(r-b, -h), (r, -h+b), (r, h-b), (r-b, h)] if b > 1e-4 else [(r, -h), (r, h)]
        self.lathe(prof, n, c, axis, rot=rot)

    def ring(self, c, r_in, r_out, depth, axis='X', n=24, rot=None):
        h = depth/2
        self.lathe([(r_in, -h), (r_out, -h), (r_out, h), (r_in, h)], n, c, axis, loop=True, rot=rot)

    def loft_x(self, stations):
        """Closed solid through YZ loops [(x, [(y, z), ...]), ...], capped at both ends."""
        rings = [[self.bm.verts.new(self.base @ Vector((x, y, z))) for y, z in loop] for x, loop in stations]
        n = len(rings[0])
        for a, b in zip(rings, rings[1:]):
            for k in range(n):
                self.bm.faces.new((a[k], a[(k+1) % n], b[(k+1) % n], b[k]))
        self.bm.faces.new(rings[0])
        self.bm.faces.new(rings[-1])

    def emit(self, name, mat, parent, smooth_deg=32):
        if not self.bm.faces:
            self.bm.free()
            return None
        bmesh.ops.remove_doubles(self.bm, verts=self.bm.verts, dist=1e-5)
        bmesh.ops.recalc_face_normals(self.bm, faces=self.bm.faces)
        me = bpy.data.meshes.new(name)
        self.bm.to_mesh(me)
        self.bm.free()
        me.materials.append(mat)
        for p in me.polygons:
            p.use_smooth = True
        try:
            me.set_sharp_from_angle(angle=math.radians(smooth_deg))
        except AttributeError:
            pass
        ob = bpy.data.objects.new(name, me)
        bpy.context.collection.objects.link(ob)
        ob.parent = parent
        return ob


class Group:
    """The meshes of one moving group, one per material, named '<prefix> <part> <material key>'."""

    def __init__(self, prefix, part, mats, parent, origin=(0, 0, 0)):
        self.prefix, self.part, self.mats, self.parent, self.origin = prefix, part, mats, parent, origin
        self.meshes = {}
        self.subs, self.empties = [], []

    def sub(self, part, at, display=.2):
        """A child group on its own empty '<prefix> <part>' at `at` (this group's frame)."""
        e = bpy.data.objects.new(f'{self.prefix} {part}', None)
        bpy.context.collection.objects.link(e)
        e.empty_display_size = display
        e.parent = self.parent
        e.location = Vector(at)-Vector(self.origin)
        g = Group(self.prefix, part, self.mats, e, origin=at)
        self.subs.append(g)
        self.empties.append(e)
        return g

    def __getitem__(self, key):
        if key not in self.meshes:
            self.meshes[key] = Mesh(self.origin)
        return self.meshes[key]

    def emit(self):
        out = []
        for key, m in self.meshes.items():
            ob = m.emit(f'{self.prefix} {self.part} {key}', self.mats.get(key) or self.mats['steel'], self.parent)
            if ob:
                out.append(ob)
        for g in self.subs:
            out += g.emit()
        return out

    def all_empties(self):
        return self.empties+[e for g in self.subs for e in g.all_empties()]


def section(hw, z0, z1, top=0.0, bot=0.0, tumble=0.0, round_=0):
    """A YZ loop: half-width hw at the base narrowing by `tumble` at the top,
    chamfered `top` and `bot`, each chamfer rounded through `round_` extra points."""
    def arc(cy, cz, ry, rz, a0, a1):
        return [(cy+ry*math.cos(a0+(a1-a0)*t/(round_+1)), cz+rz*math.sin(a0+(a1-a0)*t/(round_+1)))
                for t in range(round_+2)]
    ht = hw-tumble
    pts = []
    pts += arc(-hw+bot, z0+bot, bot, bot, math.pi, 1.5*math.pi) if bot else [(-hw, z0)]
    pts += arc(hw-bot, z0+bot, bot, bot, 1.5*math.pi, 2*math.pi) if bot else [(hw, z0)]
    pts += arc(ht-top, z1-top, top, top, 0, .5*math.pi) if top else [(ht, z1)]
    pts += arc(-ht+top, z1-top, top, top, .5*math.pi, math.pi) if top else [(-ht, z1)]
    return pts


# --- fittings in true metres ----------------------------------------------------

class Fit:
    """Human-scale fittings: every size below is metres, converted with mpu."""

    def __init__(self, mpu, detail):
        self.u = 1/mpu
        self.near = detail == 'near'

    def bolts(self, mesh, centre, radius, z, axis='Z', spacing=.8, r=.07, h=.05):
        u = self.u
        if not self.near:
            return
        n = max(8, int(2*math.pi*radius/(spacing*u)))
        for k in range(n):
            a = 2*math.pi*(k+.5)/n
            p = Vector((radius*math.cos(a), radius*math.sin(a), z))
            mesh.cyl(Vector(centre)+p, r*u, h*u, axis, n=6)

    def bolt_row(self, mesh, a, b, spacing=.7, r=.07, h=.05, axis='Z'):
        if not self.near:
            return
        a, b = Vector(a), Vector(b)
        n = max(2, int((b-a).length/(spacing*self.u))+1)
        for k in range(n):
            mesh.cyl(a.lerp(b, k/(n-1)), r*self.u, h*self.u, axis, n=6)

    def hatch(self, g, c, size=.9, axis='Z'):
        """A roof hatch: coaming, lid, hinge and handle. c is the coaming base."""
        u, s = self.u, size*self.u
        x, y, z = c
        if axis == 'Z':
            g['plate'].box((x, y, z+.06*u), (s+.16*u, s+.16*u, .12*u), .03*u)
            g['steel'].box((x, y, z+.15*u), (s, s, .08*u), .03*u)
            if self.near:
                g['steel'].cyl((x-s/2-.02*u, y, z+.16*u), .06*u, s*.7, 'Y', n=8)
                g['steel'].box((x+s*.3, y, z+.22*u), (.05*u, s*.5, .05*u))
        else:   # a door in a +-Y facing cheek: c is the door's foot centre on the face
            sy = 1 if axis == '+Y' else -1
            g['plate'].box((x, y+sy*.04*u, z+1.05*u), (1.0*u, .08*u, 2.2*u), .03*u)
            g['steel'].box((x, y+sy*.08*u, z+1.0*u), (.8*u, .06*u, 1.95*u), .02*u)
            if self.near:
                g['steel'].box((x+.28*u, y+sy*.13*u, z+1.0*u), (.06*u, .06*u, .3*u))
                for hz in (.35, 1.65):
                    g['steel'].cyl((x-.42*u, y+sy*.1*u, z+hz*u), .05*u, .2*u, 'Z', n=8)

    def weld(self, mesh, a, b):
        """A weld bead along a plate join from a to b."""
        if not self.near:
            return
        a, b = Vector(a), Vector(b)
        d = b-a
        if d.length < 1e-6:
            return
        rot = Vector((0, 0, 1)).rotation_difference(d.normalized()).to_matrix().to_4x4()
        mesh.cyl((a+b)/2, .035*self.u, d.length, 'Z', n=6, rot=rot)

    def seam(self, mesh, c, length, axis='Z', across='Y'):
        """A dark plate seam: a narrow groove line laid on a face."""
        u = self.u
        d = {'X': 0, 'Y': 1, 'Z': 2}
        dims = [.05*u, .05*u, .05*u]
        dims[d[axis]] = length
        dims[d[across]] = .03*u
        mesh.box(c, dims)

    def panel(self, mesh, c, w, h, face='Y'):
        """A raised access-panel outline on a +-Y face (w along X, h along Z)."""
        u = self.u
        if not self.near:
            return
        x, y, z = c
        t = .06*u
        for dz in (-h/2, h/2):
            mesh.box((x, y, z+dz), (w, .04*u, t))
        for dx in (-w/2, w/2):
            mesh.box((x+dx, y, z), (t, .04*u, h))

    def conduit(self, mesh, a, b, r=.12, bracket=1.5):
        """A cable conduit with brackets every `bracket` metres."""
        u = self.u
        if not self.near:
            return
        a, b = Vector(a), Vector(b)
        d = b-a
        axis = 'X' if abs(d.x) >= max(abs(d.y), abs(d.z)) else 'Y' if abs(d.y) >= abs(d.z) else 'Z'
        mesh.cyl((a+b)/2, r*u, d.length, axis, n=10)
        n = max(2, int(d.length/(bracket*u))+1)
        for k in range(n):
            p = a.lerp(b, k/(n-1))
            mesh.box(p, (.12*u if axis != 'X' else .1*u, .12*u if axis != 'Y' else .1*u,
                         .12*u if axis != 'Z' else .1*u))

    def ladder(self, mesh, x, y, z0, z1, axis='X'):
        """Rungs up a face that looks along -X (the rear of a house)."""
        u = self.u
        if not self.near or z1-z0 < 1.0*u:
            return
        for s in (-1, 1):
            mesh.box((x-.08*u, y+s*.22*u, (z0+z1)/2), (.05*u, .05*u, z1-z0))
        z = z0+.3*u
        while z < z1-.1*u:
            mesh.cyl((x-.1*u, y, z), .025*u, .44*u, 'Y', n=6)
            z += .3*u

    def rail(self, mesh, a, b, height=.9):
        """A grab rail standing on a roof from a to b."""
        u = self.u
        if not self.near:
            return
        a, b = Vector(a), Vector(b)
        n = max(2, int((b-a).length/(1.2*u))+1)
        for k in range(n):
            p = a.lerp(b, k/(n-1))
            mesh.box(p+Vector((0, 0, height*u/2)), (.04*u, .04*u, height*u))
        mid = (a+b)/2+Vector((0, 0, height*u))
        d = b-a
        mesh.box(mid, (abs(d.x)+.04*u, abs(d.y)+.04*u, .05*u))

    def periscope(self, g, c, facing=0.0):
        u = self.u
        x, y, z = c
        rot = Matrix.Rotation(facing, 4, 'Z')
        g['plate'].box((x, y, z+.2*u), (.5*u, .4*u, .4*u), .05*u, rot=rot)
        g['glass' if 'glass' in g.mats else 'dark'].box(
            (x+math.cos(facing)*.25*u, y+math.sin(facing)*.25*u, z+.3*u), (.04*u, .32*u, .1*u), rot=rot)

    def vent(self, mesh, c):
        u = self.u
        if not self.near:
            return
        mesh.lathe([(.18*u, 0), (.18*u, .25*u), (.4*u, .3*u), (.4*u, .4*u), (0, .45*u)], 16, c)

    def eye(self, mesh, c, axis='Y'):
        u = self.u
        if self.near:
            mesh.ring(c, .1*u, .18*u, .08*u, axis, n=10)


# --- the fixed barbette ----------------------------------------------------------

def barbette(name, r, lift, mats, mpu, detail='near', foot=None, location=(0, 0, 0), sponson=0.0, sponson_dir=0.0):
    """The fixed ring a turret trains on: foot flange sunk into the deck, wall,
    a stepped ledge on a tall drum, and a chamfered lip at the pivot. Returns
    the steel object; bolts and the lip ring are in it. `lift` is the drum
    height in units above the seat; `foot` how far below the seat the flange
    runs (default 2 units plus 0.35 r, enough to meet a sloped deck).

    A ring that overhangs the hull's edge gets a sponson: `sponson` > 0 turns
    the flat foot into a tub tapering that far further down, so it runs into
    the side wall instead of floating, with gusset ribs on the overhanging side
    (`sponson_dir`, radians, the direction the overhang faces)."""
    f = Fit(mpu, detail)
    u = f.u
    _CAP[0] = CHAMFER_M*u
    m = Mesh()
    foot = 2+.35*r if foot is None else foot
    lip = min(.35, lift*.25)
    if sponson > 0:
        prof = [(0, -foot-sponson), (.45*r, -foot-sponson), (1.1*r, -foot), (1.1*r, .3), (1.04*r, .42), (r, .5)]
    else:
        prof = [(0, -foot), (1.1*r, -foot), (1.1*r, .3), (1.04*r, .42), (r, .5)]
    rr = r
    if lift >= 3:
        mid = .3+(lift-.3)*.45
        prof += [(r, mid), (.955*r, mid+.12)]
        rr = .955*r
    prof += [(rr, lift-lip), (rr-lip, lift), (0, lift)]
    m.lathe(prof, 64, phase=math.pi/64)
    f.bolts(m, (0, 0, 0), 1.06*r, .33, spacing=.9, r=.09, h=.08)
    if f.near:
        top = (mid if lift >= 3 else lift-lip)
        for k in range(16):
            a = 2*math.pi*(k+.5)/16
            m.box((r*math.cos(a), r*math.sin(a), (.5+top)/2), (.06*u, .06*u, max(top-.5, .1)),
                  rot=Matrix.Rotation(a, 4, 'Z'))
        m.ring((0, 0, .47), r*.995, r+.04*u, .07*u, 'Z', n=64)
    if lift >= 3:
        f.bolts(m, (0, 0, 0), .98*r, mid+.08, spacing=1.0, r=.07, h=.06)
    if sponson > 0:
        # Gussets under the overhang: plates from the tub's rim down its cone.
        for da in (-.5, 0, .5):
            a = sponson_dir+da
            rot = Matrix.Rotation(a, 4, 'Z')
            cx, cy = .78*r*math.cos(a), .78*r*math.sin(a)
            m.box((cx, cy, -foot-sponson*.45), (.6*r, .1*u+.02*r, sponson*.9), rot=rot @ Matrix.Rotation(
                -math.atan2(sponson, .65*r), 4, 'Y'))
        m.ring((0, 0, -foot+.05), 1.06*r, 1.13*r, .12*u+.02, 'Z', n=64)
    ob = m.emit(name, mats['steel'], None)
    ob.location = location
    return ob


# --- the trainable turret ---------------------------------------------------------

def spec_for(weapon, **over):
    s = dict(WEAPONS.get(ALIASES.get(weapon, weapon)) or WEAPONS['none'])
    s.update(over)
    s['weapon'] = weapon
    return s


def build(weapon, prefix, pivot, r, mats, mpu, detail='near', **over):
    """The trainable turret for `weapon` on `pivot`, sized to ring radius r (units)."""
    s = spec_for(weapon, **over)
    f = Fit(mpu, detail)
    _CAP[0] = CHAMFER_M*f.u
    house = Group(prefix, 'house', mats, pivot)
    z0 = GAP*r
    # Traverse: the dark roller path in the gap, and the house's lit skirt above it.
    house['dark'].cyl((0, 0, z0/2), .9*r, z0+.04, 'Z', n=48)
    if s['house'] == 'box':
        trunnion, deck_top, extra = _launcher_base(house, f, s, r, z0)
    elif s['house'] == 'dome':
        trunnion, extra = _dome(house, f, s, r, z0)
    else:
        trunnion, extra = _gunhouse(house, f, s, r, z0)
    elev = bpy.data.objects.new(f'{prefix} elevation', None)
    bpy.context.collection.objects.link(elev)
    elev.empty_display_type, elev.empty_display_size = 'ARROWS', .3*r
    elev.parent = pivot
    elev.location = trunnion
    gun = Group(prefix, 'gun', mats, elev, origin=trunnion)
    if s['house'] == 'box':
        muzzles = _launcher_rack(gun, f, s, r, trunnion, deck_top)
    elif s['gun'] == 'gatling':
        muzzles = _gatling(gun, f, s, r, trunnion, extra)
    elif s['gun'] == 'cluster':
        muzzles = _cluster(gun, f, s, r, trunnion, extra)
    else:
        muzzles = _cannons(gun, f, s, r, trunnion, extra)
    anchors = []
    for k, (x, y, z) in enumerate(muzzles):
        a = bpy.data.objects.new(f'{prefix} muzzle anchor {k+1}', None)
        bpy.context.collection.objects.link(a)
        a.empty_display_size = .15*r
        a.parent = elev
        a.location = (x-trunnion[0], y-trunnion[1], z-trunnion[2])
        anchors.append(a)
    return {'parts': house.emit()+gun.emit(), 'elevation': elev, 'muzzles': anchors, 'spec': s,
            'moving': gun.all_empties()}


def _gunhouse(g, f, s, r, z0):
    """Naval gunhouse lofted along X: rounded rear bustle, full cheeks with a
    slight tumblehome, angled cheeks into a near-vertical gun face. Roof plates
    with seams, hatches, cupola, periscopes, vents, rangefinder, side doors,
    a rear ladder."""
    u = f.u
    low = s['house'] == 'low'
    roof = s.get('roof', .6)*r
    zt = z0+roof-GAP*r
    tb = .05*r
    stations = [
        (-.84*r, section(.4*r, z0, zt-.08*r, .14*r, .06*r, tb)),
        (-.76*r, section(.62*r, z0, zt-.02*r, .16*r, .06*r, tb)),
        (-.6*r, section(.76*r, z0, zt, .16*r, .05*r, tb)),
        (.3*r, section(.78*r, z0, zt, .16*r, .05*r, tb)),
        (.56*r, section(.66*r, z0, zt-.05*r, .12*r, .05*r, tb)),
        (.64*r, section(.58*r, z0, zt-.1*r, .1*r, .05*r, tb)),
    ]
    g['armour'].loft_x(stations)
    face = .64*r
    # Roof: three armour plates proud of the roof with dark seams between them.
    gap = max(.08*u, .012*r)
    xs = (-.58*r, -.2*r, .14*r, .44*r)
    for a, b in zip(xs, xs[1:]):
        g['armour'].box(((a+b)/2, 0, zt+.02*r), (b-a-gap, 1.18*r, .04*r), .015*r)
    g['dark'].box((-.07*r, 0, zt+.005*r), (1.0*r, 1.2*r, .02*r))
    # Face armour: a chamfered slab, bolted round its edge.
    sh = zt-z0-.06*r
    g['plate'].box((face+.04*r, 0, z0+sh/2+.02*r), (.1*r, 1.2*r, sh), .03*r, seg=3)
    f.bolt_row(g['steel'], (face+.095*r, -.56*r, z0+sh), (face+.095*r, .56*r, z0+sh), axis='X')
    f.bolt_row(g['steel'], (face+.095*r, -.56*r, z0+.06*r), (face+.095*r, .56*r, z0+.06*r), axis='X')
    # Commander's cupola with vision slits, two hatches, periscopes, vents.
    g['plate'].lathe([(.2*r, 0), (.2*r, .08*r), (.17*r, .13*r), (0, .14*r)], 32, (-.4*r, .32*r, zt+.04*r))
    g['dark'].ring((-.4*r, .32*r, zt+.1*r), .19*r, .202*r, .025*r, 'Z', n=32)
    f.hatch(g, (-.15*r, .3*r, zt+.04*r))
    f.hatch(g, (-.15*r, -.3*r, zt+.04*r))
    for y in (.5*r, -.5*r):
        f.periscope(g, (.36*r, y, zt+.04*r))
    for y in (.12*r, -.12*r):
        f.vent(g['steel'], (-.62*r, y, zt+.04*r))
    f.rail(g['steel'], (.0, -.62*r, zt+.04*r), (-.5*r, -.62*r, zt+.04*r))
    f.eye(g['steel'], (.5*r, .45*r, zt+.06*r))
    f.eye(g['steel'], (.5*r, -.45*r, zt+.06*r))
    # Plate construction: seams down the cheeks, weld beads along the chamfer
    # edges, an access panel and a cable conduit on each cheek, bolted roof plates.
    for sy in (1, -1):
        yc = sy*(.78*r-.02*r)
        for x in (-.32*r, .06*r):
            f.seam(g['dark'], (x, yc, z0+.42*(zt-.16*r-z0)+.02*r), .82*(zt-.16*r-z0), 'Z', 'Y')
        f.weld(g['steel'], (-.6*r, sy*(.73*r-.16*r), zt), (.3*r, sy*(.73*r-.16*r), zt))
        f.weld(g['steel'], (-.6*r, sy*.73*r, zt-.16*r), (.3*r, sy*.73*r, zt-.16*r))
        f.panel(g['plate'], (.18*r, sy*.775*r, z0+.42*(zt-z0)), .2*r, .22*r)
        f.conduit(g['steel'], (-.62*r, sy*.79*r, z0+.07*r), (.34*r, sy*.79*r, z0+.07*r), r=.1)
    for x in xs:
        f.bolt_row(g['steel'], (x+.03*r, -.5*r, zt+.04*r), (x+.03*r, .5*r, zt+.04*r))
    for y in (-.3*r, 0, .3*r):
        g['armour'].box((-.85*r, y, (z0+zt)/2-.04*r), (.03*r, .05*r, .7*(zt-z0)), .01*r)
    # Side doors on both cheeks, a ladder up the rear bustle, a rear hatch.
    for sy in (1, -1):
        door_w = min(1.0*u, .3*r)
        if door_w >= .9*u:
            f.hatch(g, (-.35*r, sy*.775*r, z0), axis='+Y' if sy > 0 else '-Y')
    f.ladder(g['steel'], -.84*r, .18*r, z0+.05*r, zt-.1*r)
    g['plate'].box((-.845*r, -.12*r, (z0+zt)/2), (.04*r, .36*r, .5*(zt-z0)), .01*r)
    # Rangefinder across the rear roof: tube, armoured ears with dark lenses.
    if not low:
        rf_x, rf_z = -.4*r, zt+.17*r
        g['plate'].box((rf_x, 0, zt+.08*r), (.24*r, .34*r, .1*r), .02*r)
        g['steel'].cyl((rf_x, 0, rf_z), .075*r, 1.76*r, 'Y', n=24, bevel=.01*r)
        for sy in (1, -1):
            g['plate'].box((rf_x, sy*.9*r, rf_z), (.26*r, .1*r, .24*r), .03*r)
            g['dark'].cyl((rf_x+.13*r, sy*.9*r, rf_z), .045*r, .02*r, 'X', n=16)
    else:
        # The sprite's twin prongs: tall side frames carrying the sight hoods.
        for sy in (1, -1):
            g['plate'].box((.3*r, sy*.68*r, zt+.15*r), (.64*r, .1*r, .3*r), .03*r)
            g['steel'].box((.52*r, sy*.68*r, zt+.35*r), (.26*r, .16*r, .13*r), .03*r)
            g['dark'].box((.65*r, sy*.68*r, zt+.35*r), (.02*r, .12*r, .07*r))
    bz = z0+(.24 if low else .28)*r
    return (.36*r, 0.0, bz), {'face': face+.09*r, 'face_h': sh, 'z0': z0}


def _dome(g, f, s, r, z0):
    """Small mount: a smooth armoured dome, a bolted base band, a top hatch,
    a periscope and lifting eyes. The gun group carries the shield and drum."""
    u = f.u
    prof = [(.8*r, z0), (.8*r, z0+.14*r), (.77*r, z0+.24*r), (.7*r, z0+.33*r), (.58*r, z0+.42*r),
            (.42*r, z0+.49*r), (.22*r, z0+.53*r), (0, z0+.54*r)]
    g['plate'].lathe(prof, 10, phase=math.pi/10)          # ten welded plates, sharp at the joins
    g['armour'].lathe([(.82*r, z0), (.82*r, z0+.07*r), (.8*r, z0+.09*r), (0, z0+.09*r)], 48)
    for k in range(10):                                      # weld beads up the plate joins
        a = 2*math.pi*k/10
        for (r0, h0), (r1, h1) in zip(prof[1:-2], prof[2:-1]):
            f.weld(g['steel'], (r0*math.cos(a), r0*math.sin(a), h0), (r1*math.cos(a), r1*math.sin(a), h1))
    f.bolts(g['steel'], (0, 0, 0), .815*r, z0+.09*r, spacing=.6, r=.06, h=.05)
    if .5*r > 1.0*u:
        f.hatch(g, (-.22*r, .0, z0+.5*r), size=min(.8, .3*r/u))
    f.periscope(g, (.1*r, -.3*r, z0+.48*r))
    f.eye(g['steel'], (-.45*r, .45*r, z0+.42*r))
    f.eye(g['steel'], (-.45*r, -.45*r, z0+.42*r))
    bz = z0+.26*r
    return (.42*r, 0.0, bz), {'face': .62*r, 'face_h': .36*r, 'z0': z0}


def _barrel(gm, f, x0, y, z, L, rad, brake, bands, near):
    """One barrel along +X from x0: breech jacket, taper, bands, a real bore at
    the muzzle and a brake."""
    jacket = rad*1.45
    prof = [(0, 0), (jacket, 0), (jacket, .3*L), (rad*1.2, .34*L), (rad*1.05, .7*L), (rad, L-.001), (rad*.55, L),
            (rad*.55, L-.8*rad), (0, L-.8*rad)]
    gm['steel'].lathe(prof, 24, (x0, y, z), 'X')
    if bands == 'coil':     # driver coils: evenly spaced rings the whole length
        for k in range(7):
            gm['plate'].cyl((x0+(.36+.08*k)*L, y, z), rad*1.32, .035*L, 'X', n=24, bevel=.004*L)
    else:
        for t in (.3, .62):
            gm['steel'].cyl((x0+t*L, y, z), rad*1.32, .04*L, 'X', n=24, bevel=.008*L)
        if L > 1.2*rad*12:   # fume extractor on a long barrel
            gm['steel'].lathe([(rad*1.05, -.06*L), (rad*1.5, -.03*L), (rad*1.5, .03*L), (rad*1.05, .06*L)],
                              24, (x0+.5*L, y, z), 'X')
    if brake == 'none':
        return (x0+L, y, z)
    xb = x0+L
    if brake == 'flak':
        bl, br = 2.6*rad, 1.5*rad
    elif brake == 'heavy':
        bl, br = 3.4*rad, 1.75*rad
    else:
        bl, br = 2.4*rad, 1.6*rad
    gm['steel'].lathe([(rad*.55, -.002), (br, 0), (br, bl), (rad*.65, bl), (rad*.65, bl-.5*rad)], 24,
                      (xb, y, z), 'X', loop=False)
    # Ported baffles: dark slots through both flanks.
    for k in range(2 if near else 1):
        gm['dark'].box((xb+(.32+.36*k)*bl, y, z), (.2*bl, 2*br+.02, .9*br))
    return (xb+bl, y, z)


def _cannons(g, f, s, r, t, ex):
    """Mantlet, cradle, recoil cylinders and barrels: everything that elevates."""
    n, L, rad = s['barrels'], s['length']*r, s['calibre']*r
    spacing = s.get('spacing', .27)*r
    face, bz = ex['face'], t[2]
    dome = s['house'] == 'dome'
    muzzles = []
    if dome:
        # The small mount's shield: a chamfered armour block carried with the gun.
        g['steel'].box((.62*r, 0, bz), (.28*r, .66*r, .38*r), .06*r, seg=3)
        face = .76*r
        if s.get('drum'):
            g['steel'].cyl((.1*r, .6*r, bz-.02*r), .2*r, .22*r, 'Y', n=32, bevel=.03*r)
            g['dark'].ring((.1*r, .71*r, bz-.02*r), .08*r, .16*r, .01*r, 'Y', n=24)
    for k in range(n):
        y = (k-(n-1)/2)*spacing
        if not dome:
            g['dark'].box((face, y, bz), (.05*r, .19*r, .32*r))               # embrasure
            g['steel'].box((face+.06*r, y, bz), (.12*r, .16*r, .2*r), .04*r, seg=3)   # mantlet
        x0 = face+.12*r
        g['dark'].cyl((x0+.01*r, y, bz), rad*1.7, .06*r, 'X', n=20)          # blast bag
        g['steel'].cyl((x0+.12*r, y, bz), rad*1.62, .2*r, 'X', n=24, bevel=.02*r)   # cradle sleeve
        if f.near:
            for sz in (1, -1):                                               # recoil cylinders
                g['steel'].cyl((x0+.15*r, y, bz+sz*rad*1.75), rad*.42, .3*r, 'X', n=12, bevel=.01*r)
        rg = g.sub(f'recoil {k+1}', (x0+.2*r, y, bz), .1*r)       # slides along -X to recoil
        muzzles.append(_barrel(rg, f, x0+.2*r, y, bz, L-.2*r, rad, s.get('brake', 'std'), s.get('bands'), f.near))
    # Trunnion bosses where the elevating mass rides (in the house's cheeks).
    hw = .79*r if not dome else .34*r
    for sy in (1, -1):
        g['steel'].cyl((t[0], sy*hw, bz), .13*r if not dome else .09*r, .08*r, 'Y', n=24, bevel=.015*r)
    if s.get('drum') and not dome:
        g['steel'].cyl((t[0]-.25*r, .55*r, bz), .22*r, .2*r, 'Y', n=32, bevel=.03*r)
    return muzzles


def _gatling(g, f, s, r, t, ex):
    """A rotary cannon: shield, drive housing, rotor with every barrel, two
    clamps, an ammunition drum and feed chute on the flank."""
    bz = t[2]
    L, rad, n = s['length']*r, s['calibre']*r, s['barrels']
    g['steel'].loft_x([(.48*r, section(.32*r, bz-.2*r, bz+.2*r, .07*r, .05*r)),
                       (.66*r, section(.3*r, bz-.18*r, bz+.17*r, .07*r, .05*r)),
                       (.76*r, section(.22*r, bz-.13*r, bz+.12*r, .05*r, .04*r))])
    for x in (.56*r, .66*r):
        f.bolt_row(g['steel'], (x, -.25*r, bz+.2*r), (x, .25*r, bz+.2*r))
    g['plate'].box((.58*r, -.24*r, bz+.24*r), (.14*r, .08*r, .07*r), .01*r)                 # sight
    g['dark'].box((.655*r, -.24*r, bz+.24*r), (.01*r, .06*r, .035*r))
    x0 = .74*r
    g['steel'].cyl((x0+.08*r, 0, bz), .2*r, .18*r, 'X', n=32, bevel=.02*r)      # drive housing
    rr = rad*2.1
    gm, g = g, g.sub('rotor', (x0+.17*r, 0, bz), .1*r)           # the rotor spins about its local X
    for k in range(n):
        a = 2*math.pi*k/n
        y, z = rr*math.cos(a), bz+rr*math.sin(a)
        g['steel'].lathe([(0, 0), (rad, 0), (rad, L-.001), (rad*.5, L), (rad*.5, L-rad*1.5), (0, L-rad*1.5)],
                         12, (x0+.17*r, y, z), 'X')
    for tt, w in ((.08, .05), (.55, .035), (.93, .05)):
        g['steel'].cyl((x0+.17*r+tt*L, 0, bz), rr+rad*1.4, w*L, 'X', n=24, bevel=.004*L)
    g['dark'].cyl((x0+.17*r+.93*L+.026*L, 0, bz), rr*.5, .005*L, 'X', n=16)
    g = gm
    # Drum and chute.
    g['steel'].cyl((.05*r, .62*r, bz-.04*r), .2*r, .26*r, 'Y', n=32, bevel=.03*r)
    g['dark'].ring((.05*r, .755*r, bz-.04*r), .07*r, .15*r, .01*r, 'Y', n=24)
    for k in range(6):                                                    # covered feed chute
        tt = k/5
        g['plate'].box((.2*r+tt*.5*r, .5*r-.3*r*tt, bz+.04*r), (.11*r, .09*r, .08*r), .008*r,
                       rot=Matrix.Rotation(-.55, 4, 'Z'))
    return [(x0+.17*r+L, 0, bz)]


def _cluster(g, f, s, r, t, ex):
    """A needle-gun block: six thin barrels in a square shroud with a bore plate."""
    face, bz = ex['face'], t[2]
    L = s['length']*r
    g['steel'].box((face+.25*r, 0, bz), (.5*r, .44*r, .32*r), .04*r, seg=3)
    muzzles = []
    for k in range(s['barrels']):
        y = (k % 3-1)*.12*r
        z = bz+(k//3-.5)*.12*r
        g['steel'].lathe([(0, 0), (s['calibre']*r, 0), (s['calibre']*r, L), (0, L)], 12, (face+.5*r, y, z), 'X')
        g['dark'].cyl((face+.5*r+L, y, z), s['calibre']*r*.55, .01*r, 'X', n=10)
        muzzles.append((face+.5*r+L, y, z))
    g['plate'].box((face+.5*r+.6*L, 0, bz), (.05*L, .42*r, .3*r), .01*r)
    return muzzles


def _launcher_base(g, f, s, r, z0):
    """The launcher's trainable deck and yoke arms; the box elevates between them."""
    g['plate'].lathe([(.92*r, z0), (.92*r, z0+.1*r), (.86*r, z0+.15*r), (0, z0+.15*r)], 48)
    f.bolts(g['steel'], (0, 0, 0), .88*r, z0+.15*r, spacing=.7, r=.06, h=.05)
    deck_top = z0+.15*r
    half, height = _rack_size(s, r)
    hz = deck_top+.1*r+height/2
    arm_h = hz+.12*r-deck_top
    for sy in (1, -1):
        ya = sy*(half+.08*r)
        g['armour'].box((0, ya, deck_top+arm_h/2), (.5*r, .1*r, arm_h), .03*r)
        g['plate'].box((0, ya+sy*.055*r, deck_top+arm_h*.45), (.36*r, .02*r, arm_h*.55), .01*r)   # doubler
        for gx in (-.29*r, .29*r):                                                           # gussets
            g['armour'].box((gx, ya, deck_top+.12*r), (.1*r, .14*r, .24*r), .015*r)
        # Trunnion: a stepped cap, bolted round.
        cy = sy*(half+.14*r)
        g['steel'].cyl((0, cy, hz), .15*r, .05*r, 'Y', n=32, bevel=.01*r)
        g['steel'].cyl((0, cy+sy*.04*r, hz), .09*r, .05*r, 'Y', n=24, bevel=.01*r)
        if f.near:
            for k in range(8):
                a = 2*math.pi*k/8
                g['steel'].cyl((.12*r*math.cos(a), cy+sy*.028*r, hz+.12*r*math.sin(a)), .012*r, .02*r, 'Y', n=6)
        # Elevation ram: cylinder on a deck clevis, rod up to the rack's belly.
        yr = sy*(half-.15*r)
        g['steel'].box((.32*r, yr, deck_top+.03*r), (.14*r, .12*r, .06*r), .01*r)
        g['steel'].cyl((.32*r, yr, deck_top+.03*r+.16*r), .05*r, .26*r, 'Z', n=16, bevel=.01*r)
    return (0.0, 0.0, hz), deck_top, {'half': half, 'height': height}


def _rack_size(s, r):
    rows, cols = s['missiles']
    p = s['mrad']*r*(3.0 if s['rack'] == 'tubes' else 2.7)
    half = max(cols*p/2+.12*r, .32*r)
    height = max(rows*p+.2*r, .36*r)
    return half, height


_MCACHE = {}


def _missile_mat(rgb):
    key = tuple(round(c, 3) for c in rgb)
    if key not in _MCACHE or _MCACHE[key].name not in bpy.data.materials:
        m = bpy.data.materials.new(f'Missile body {key}')
        m.use_nodes = True
        b = m.node_tree.nodes['Principled BSDF']
        b.inputs['Base Color'].default_value = (*key, 1)
        b.inputs['Metallic'].default_value = .3
        b.inputs['Roughness'].default_value = .45
        _MCACHE[key] = m
    return _MCACHE[key]


def _missile(g, f, x_tail, y, z, L, rad, rich):
    """A missile along +X from its tail: nozzle, body, ogive warhead, cruciform fins."""
    body = [(rad*.55, 0), (rad*.8, .03*L), (rad, .06*L), (rad, .72*L), (rad*.96, .78*L), (rad*.8, .86*L),
            (rad*.55, .93*L), (rad*.22, .985*L), (0, L)]
    g['missile'].lathe(body, 20, (x_tail, y, z), 'X')
    g['dark'].cyl((x_tail-.004*L, y, z), rad*.45, .01*L, 'X', n=16)
    g['armour'].cyl((x_tail+.75*L, y, z), rad*1.01, .04*L, 'X', n=20)     # warhead band
    fin = .7*rad
    for ang in (0, math.pi/2, math.pi, 1.5*math.pi):
        cy, cz = math.cos(ang)*(rad+fin/2), math.sin(ang)*(rad+fin/2)
        dims = (.16*L, fin, .04*rad+.01) if abs(math.cos(ang)) > .5 else (.16*L, .04*rad+.01, fin)
        g['missile'].box((x_tail+.1*L, y+cy, z+cz), dims, .01*rad)
    if rich:
        g['missile'].cyl((x_tail+.45*L, y, z), rad*1.03, .02*L, 'X', n=20)


def _launcher_rack(g, f, s, r, t, deck_top):
    """The elevating rack, built from the game's layered sprites: a magazine
    housing at the rear, launch rails or tubes forward, and the loaded missiles
    themselves, noses forward. 'open' racks show the missiles on rails inside a
    frame (Squall, Harpoon, Sabot); 'tubes' sheath each in a launch tube
    (Pilum, Typhoon, Annihilator); 'fork' holds one big missile between two
    prongs (Hurricane)."""
    g.mats = dict(g.mats, missile=_missile_mat(s['mcol']))
    hz = t[2]
    rows, cols = s['missiles']
    half, height = _rack_size(s, r)
    mrad, mlen = s['mrad']*r, s['mlen']*r
    tubes, fork = s['rack'] == 'tubes', s['rack'] == 'fork'
    pitch_y = (2*half-.24*r)/cols
    pitch_z = (height-.2*r)/rows
    rear = -.45*r
    xm = rear+(.42*r if not fork else .5*r)          # front of the magazine housing
    zb, zt = hz-height/2, hz+height/2
    # Magazine housing: chamfered, its top in armour plates with seams.
    g['plate'].loft_x([(rear, section(half, zb, zt, .07*r, .05*r)),
                       (xm-.06*r, section(half, zb, zt, .07*r, .05*r)),
                       (xm, section(half-.03*r, zb+.03*r, zt-.05*r, .05*r, .04*r))])
    seam = max(.06*f.u, .012*r)
    n = cols if not fork else 2
    w = (2*half-.1*r)/n
    for j in range(n):
        y = (j-(n-1)/2)*w
        g['armour'].box(((rear+xm)/2-.02*r, y, zt+.025*r), (xm-rear-.12*r, w-seam, .05*r), .012*r)
    for x in (rear+.1*r, rear+.22*r, rear+.34*r):                                   # housing ribs
        for sy in (1, -1):
            g['armour'].box((x, sy*(half+.01*r), hz), (.04*r, .03*r, height-.1*r), .008*r)
    g['plate'].box((rear-.02*r, 0, hz), (.04*r, 1.2*half, .6*height), .015*r)    # rear loading door
    for k in range(5):                                                             # exhaust louvres
        g['dark'].box((rear-.045*r, 0, hz-.22*height+k*.11*height), (.02*r, .9*half, .035*height))
        g['steel'].box((rear-.055*r, 0, hz-.2*height+k*.11*height), (.03*r, .9*half, .012*height))
    for sy in (1, -1):                                                             # ram rod eye
        g['steel'].cyl((.32*r, sy*(half-.15*r), zb-.04*r), .025*r, .1*r, 'Z', n=10)
        g['steel'].cyl((.32*r, sy*(half-.15*r), zb-.01*r), .04*r, .05*r, 'Y', n=12)
    f.bolt_row(g['steel'], (rear-.045*r, -.55*half, hz+.32*height), (rear-.045*r, .55*half, hz+.32*height), axis='X')
    f.bolt_row(g['steel'], (rear-.045*r, -.55*half, hz-.32*height), (rear-.045*r, .55*half, hz-.32*height), axis='X')
    f.hatch(g, (rear+.14*r, .0, zt+.05*r), size=.8)
    muzzles = []
    if fork:
        # One big missile on a centre rail, gripped by two prongs (the sprite's fork).
        x_tail = xm-.25*r
        g['steel'].box((x_tail+.5*mlen, 0, hz-mrad-.03*r), (mlen*.8, .14*r, .06*r), .01*r)
        _missile(g, f, x_tail, 0, hz, mlen, mrad, True)
        for sy in (1, -1):
            g['armour'].box((xm+.32*r, sy*(mrad+.1*r), hz), (.7*r, .1*r, .22*r), .03*r, seg=3)
            g['steel'].box((xm+.7*r, sy*(mrad+.07*r), hz), (.12*r, .07*r, .16*r), .02*r)        # clamp pad
            g['armour'].box((xm+.82*r, sy*(mrad+.16*r), hz+.06*r), (.2*r, .08*r, .1*r), .02*r)  # prong tip
        muzzles.append((x_tail+mlen, 0, hz))
        return muzzles
    front = xm+.72*mlen
    for i in range(rows):
        for j in range(cols):
            y = (j-(cols-1)/2)*pitch_y
            z = hz+(i-(rows-1)/2)*pitch_z
            x_tail = xm-.3*mlen
            if tubes:
                tr = mrad*1.25
                g['plate'].ring((xm+(front-xm)/2, y, z), tr*.86, tr, front-xm, 'X', n=24)
                g['dark'].cyl((xm+.02*r, y, z), tr*.86, .02*r, 'X', n=20)
                _missile(g, f, x_tail, y, z, mlen*.98, mrad*.9, False)
                muzzles.append((front, y, z))
            else:
                zr = z-mrad-.012*r                                    # launch rail: an I-beam
                for dz in (0, -.05*r):
                    g['steel'].box(((xm+front)/2, y, zr+dz), (front-xm, .07*r, .012*r))
                g['steel'].box(((xm+front)/2, y, zr-.025*r), (front-xm, .014*r, .05*r))
                _missile(g, f, x_tail, y, z, mlen, mrad, f.near)
                if f.near:                                                         # umbilical
                    g['steel'].box((xm+.03*r, y, z+mrad*.7), (.06*r, mrad*.6, mrad*.5), .005*r)
                    g['dark'].cyl((xm+.06*r, y, z+mrad*.7), mrad*.12, .04*r, 'X', n=8)
                muzzles.append((x_tail+mlen, y, z))
    # The frame the sprite outlines: side rails, cross members, a front bar.
    for sy in (1, -1):
        g['armour'].box(((xm+front)/2, sy*(half-.05*r), zb+.12*r), (front-xm, .08*r, .1*r), .02*r)
        g['steel'].box(((xm+front)/2, sy*(half-.05*r), zb+.25*r), (front-xm, .05*r, .05*r), .01*r)
    for xx in (xm+.04*r, (xm+front)/2, front-.03*r):
        g['steel'].box((xx, 0, zb+.1*r), (.06*r, 2*half-.06*r, .06*r), .01*r)
    if tubes:
        for xx in ((xm+front)/2-.15*r, front-.08*r):
            g['armour'].box((xx, 0, hz), (.06*r, 2*half-.02*r, height-.1*r), .015*r)
    if s.get('director', cols >= 4):
        g['steel'].box((rear+.25*r, -(half+.32*r), hz+.1*r), (.28*r, .17*r, .28*r), .04*r)
        g['dark'].cyl((rear+.4*r, -(half+.32*r), hz+.12*r), .06*r, .02*r, 'X', n=16)
        g['steel'].cyl((rear+.25*r, -(half+.32*r), hz+.36*r), .03*r, .3*r, 'Z', n=8)
        g['armour'].cyl((rear+.25*r, -(half+.32*r), hz+.52*r), .04*r, .03*r, 'Z', n=8)
    return muzzles
