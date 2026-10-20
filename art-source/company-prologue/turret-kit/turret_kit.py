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
import json
import math
import os
import sys
import bmesh
import bpy
from mathutils import Matrix, Vector

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import turret_gimbal  # noqa: E402

GAP = .08          # traverse gap, x ring radius: the trainable part starts this far up

# --- the catalogue -------------------------------------------------------------
# house 'sprite' builds the turret from the game's own layered sprites, measured
# into sprite_outlines.json by derive_sprites.py: the base sprite's outline is the
# house's plan, its dark detail and second paint become slots and raised panels,
# and every part of the recoil sprite becomes a barrel lathed to its profile.
# Other fields of a sprite entry are leftovers of the earlier hand builds.
# house: 'naval' (gunhouse), 'low' (flak gunhouse with sight frames), 'dome'
#        (small mount), 'box' (missile launcher on a yoke)
# gun:   'cannon', 'gatling', 'cluster'; for 'box', cells = (rows, cols) and cell = 'round' / 'square' / 'door'
# calibre and length are x ring radius: barrel radius at the muzzle, barrel length
WEAPONS = {
    # The game's Vulcan is not a rotary: vulcan.wpn draws two side-by-side barrels
    # (turretOffsets y -2/+2, ALTERNATING, a separate recoil layer) over a low
    # tan cowl, narrow at the muzzle end, with a dark vent slot at the rear.
    'vulcan':    dict(house='hand', paints={'paint': (.22, .12, .03), 'accent': (.7, .52, .3), 'plate': (.2, .2, .21)}),
    'lightac':   dict(house='hand', paints={'paint': (.134, .146, .122), 'plate': (.46, .48, .42)}),
    'lightmg':   dict(house='hand', paints={'paint': (.09, .095, .1), 'plate': (.3, .31, .32),
                                             'accent': (.8, .55, .08)}),
    'none':        dict(house='dome', gun='cannon', barrels=1, calibre=.07, length=1.15),
    'heavyac':     dict(house='hand', paints={'plate': (.5, .53, .42)}),
    'flak':      dict(house='hand', paints={'paint': (.24, .16, .1), 'accent': (.25, .025, .02), 'mark': (.87, .5, .08)}),
    'dualflak':  dict(house='hand', paints={'paint': (.24, .15, .08), 'accent': (.75, .4, .09),
                                              'accent2': (.2, .06, .04), 'mark': (.45, .06, .04)}),
    'heavymauler':dict(house='hand', paints={'paint': (.148, .143, .105), 'accent': (.05, .048, .03),
                                              'plate': (.67, .65, .51)}),
    'hveldriver':dict(house='hand', paints={'paint': (.157, .143, .136), 'accent': (.5, .41, .16),
                                             'plate': (.49, .48, .48)}),
    'devastator':dict(house='hand', paints={'paint': (.36, .34, .4), 'plate': (.55, .53, .59),
                                             'accent': (.158, .143, .171), 'mark': (.4, .17, .07)}),
    'mark9':     dict(house='hand', paints={'paint': (.385, .19, .1), 'accent': (.142, .106, .102),
                                        'plate': (.687, .637, .572), 'mark': (.5, .05, .03)}),
    'hellbore':  dict(house='hand', paints={'paint': (.27, .235, .21), 'plate': (.58, .49, .455),
                                           'accent2': (.05, .05, .055)}),
    'hephaestus':dict(house='hand', paints={'paint': (.33, .23, .084), 'plate': (.67, .57, .41),
                                             'steel': (.164, .141, .108)}),
    'needler':   dict(house='hand', paints={'paint': (.38, .32, .17), 'accent': (.2, .19, .08),
                                             'plate': (.55, .52, .42)}),
    'squall':      dict(house='hand', mcol=(.18, .18, .18),
                        paints={'paint': (.033, .045, .056), 'accent': (.25, .5, .64),
                                'plate': (.15, .17, .19), 'mark': (.6, .05, .03)}),
    'hurricane': dict(house='hand', mcol=(.105, .136, .145),
                        paints={'paint': (.24, .19, .13), 'accent': (.67, .6, .52),
                                'plate': (.4, .38, .34), 'mark': (.55, .08, .03)}),
    # Tri-Tachyon. The paints are halfway between the sprite's colour and the pale
    # blue-white ceramic of Tri-Tachyon armour (owner, 2026-10-19: recognisable, not
    # cartoonish), and they keep more of their colour than the kit's military
    # livery does (chroma .7 against .35), since a Tri-Tachyon gun is known by its
    # blue-white. A hull with its own finish passes hull_finish=True, and
    # sprite_tint=0.5 to carry the same halfway mix into its materials.
    # roller: the traverse roller path's radius, x ring radius (default .9); the lance's
    # sits under its small turntable instead of showing through the beam slot and legs.
    'tachyonlance': dict(house='hand', chroma=.7, roller=.4, paints={'paint': (.41, .45, .51), 'accent': (.55, .57, .62)}),
    'gravitonbeam': dict(house='hand', chroma=.7, paints={'paint': (.29, .47, .56)}),
    'taclaser':     dict(house='hand', chroma=.7, paints={'paint': (.46, .52, .56), 'accent': (.6, .64, .68)}),
    'reaper':       dict(house='hand', chroma=.7, paints={'paint': (.35, .38, .40), 'accent': (.55, .56, .58),
                                               'accent2': (.16, .17, .28)}),
    # Point defence (common tech, the Paragon's PD): dark housing, red collar, light emitter drum.
    'pdlaser':      dict(house='hand', chroma=.7, paints={'paint': (.2, .2, .22), 'accent': (.42, .12, .09),
                                                          'accent2': (.62, .63, .64)}),
    # The Wolf (P)'s WS 001: grey prongs and studs, the green shell as its accent.
    # The Cerberus' railgun (cerberus_Standard WS 003): grey house, copper rails, a silver bulb.
    'railgun':   dict(house='hand', paints={'accent2': (.62, .66, .7)}),
    'ioncannon':    dict(house='hand', chroma=.75, paints={'paint': (.42, .46, .43), 'accent': (.109, .262, .156)}),
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
        self.origin = origin
        self._cut = None

    def cut(self):
        """A Mesh in the same frame whose solids are carved out of this one when
        it is emitted (exact boolean): true recesses, dimples and sunk pockets,
        where the sprite's dark pixels are depth, not dark paint laid on top."""
        if self._cut is None:
            self._cut = Mesh(self.origin)
        return self._cut

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

    def prism(self, poly, z0, z1, inset=0.0, z2=None, glacis=False):
        """A plan polygon [(x, y), ...] (counter-clockwise) stood up from z0 to z1.
        With an inset, the top steps in by that much on a sloped glacis up to z2,
        which is how a sprite outline becomes faceted armour, not a cookie cut."""
        lo = [self.bm.verts.new(self.base @ Vector((x, y, z0))) for x, y in poly]
        hi = [self.bm.verts.new(self.base @ Vector((x, y, z1))) for x, y in poly]
        n = len(poly)
        for k in range(n):
            self.bm.faces.new((lo[k], lo[(k+1) % n], hi[(k+1) % n], hi[k]))
        self.bm.faces.new(lo[::-1])
        top = self.bm.faces.new(hi)
        if glacis:
            # Without a normal the inset below loses its depth and the roof stays flat at
            # z1. Older builders were tuned to that flat roof, so the true glacis is opt-in.
            top.normal_update()
        if inset > 1e-4 and z2 is not None and z2 > z1:
            bmesh.ops.inset_region(self.bm, faces=[top], thickness=inset, depth=z2-z1, use_even_offset=True)

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
        if self._cut is not None and self._cut.bm.faces:
            cutter = self._cut.emit(name+' cutter', mat, parent)
            mod = ob.modifiers.new('cut', 'BOOLEAN')
            mod.operation, mod.solver, mod.object = 'DIFFERENCE', 'EXACT', cutter
            mod.use_self = True
            bpy.context.view_layer.update()
            carved = bpy.data.meshes.new_from_object(ob.evaluated_get(bpy.context.evaluated_depsgraph_get()))
            ob.modifiers.remove(mod)
            ob.data = carved
            bpy.data.meshes.remove(me)
            carved.name = name
            for p in carved.polygons:
                p.use_smooth = True
            try:
                carved.set_sharp_from_angle(angle=math.radians(smooth_deg))
            except AttributeError:
                pass
            cm = cutter.data
            bpy.data.objects.remove(cutter)
            bpy.data.meshes.remove(cm)
        return ob


class Group:
    """The meshes of one moving group, one per material, named '<prefix> <part> <material key>'.
    A key may carry a tag, 'paint#body': its own object in that material, so a
    cut (Mesh.cut) carves that part alone."""

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
            ob = m.emit(f'{self.prefix} {self.part} {key}', self.mats.get(key.split('#')[0]) or self.mats['steel'],
                        self.parent)
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
    plinth = foot > .5*r and sponson <= 0
    if sponson > 0:
        prof = [(0, -foot-sponson), (.45*r, -foot-sponson), (1.1*r, -foot), (1.1*r, .3), (1.04*r, .42), (r, .5)]
    elif plinth:
        prof = [(0, -.6), (1.1*r, -.6), (1.1*r, .3), (1.04*r, .42), (r, .5)]
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
    if plinth:
        # A deep foot is a bastion in the hull's armour, flaring to the deck with
        # chamfered courses, so a mount carried on a belt reads as part of the belt.
        p = Mesh()
        top, base = 1.16*r, 1.16*r+min(.35*foot, .5*r)
        c = min(.06*r, .25*foot)
        p.lathe([(0, -foot), (base, -foot), (base, -foot+.25*foot), (top, -.2*foot+c), (top, -c), (top-c, 0),
                 (0, 0)], 16, phase=math.pi/16)
        for t in (.45, .72):                                   # plate courses
            zc = -foot*t
            rc = top+(base-top)*max(0, (t-.2)/.55)
            p.ring((0, 0, zc), rc-.01, rc+.06*f.u, .1*f.u, 'Z', n=16)
        pl = p.emit(name+' plinth', mats['armour'], None)
        pl.parent = ob
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
    if s['house'] in ('sprite', 'hand') or s.get('paints'):
        mats = _sprite_mats(s, mats)
    house = Group(prefix, 'house', mats, pivot)
    z0 = GAP*r
    # Traverse: the dark roller path in the gap, and the house's lit skirt above it.
    house['dark'].cyl((0, 0, z0/2), s.get('roller', .9)*r, z0+.04, 'Z', n=48)
    if s['house'] == 'box':
        trunnion, deck_top, extra = _launcher_base(house, f, s, r, z0)
    elif s['house'] == 'dome':
        trunnion, extra = _dome(house, f, s, r, z0)
    elif s['house'] == 'cowl':
        trunnion, extra = _cowl(house, f, s, r, z0)
    elif s['house'] == 'sprite':
        trunnion, extra = _sprite_house(house, f, s, r, z0)
    elif s['house'] == 'hand':
        trunnion, extra = HANDS[s.get('builder', s['weapon'])](house, f, s, r, z0)
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
    elif s['house'] in ('sprite', 'hand'):
        muzzles = _sprite_gun(gun, f, s, r, trunnion, extra)
    elif s['gun'] == 'twin':
        muzzles = _twin(gun, f, s, r, trunnion, extra)
    elif s['gun'] == 'gatling':
        muzzles = _gatling(gun, f, s, r, trunnion, extra)
    elif s['gun'] == 'cluster':
        muzzles = _cluster(gun, f, s, r, trunnion, extra)
    else:
        muzzles = _cannons(gun, f, s, r, trunnion, extra)
    lim = (extra or {}).get('elevation_limits') if isinstance(extra, dict) else None
    if lim:
        # Degrees of elevation, positive up; the empty lifts the muzzles with negative Y rotation.
        elev['elevation_min_deg'], elev['elevation_max_deg'] = lim
    anchors = []
    for k, (x, y, z) in enumerate(muzzles):
        a = bpy.data.objects.new(f'{prefix} muzzle anchor {k+1}', None)
        bpy.context.collection.objects.link(a)
        a.empty_display_size = .15*r
        a.parent = elev
        a.location = (x-trunnion[0], y-trunnion[1], z-trunnion[2])
        anchors.append(a)
    parts = house.emit()+gun.emit()
    moving = gun.all_empties()
    if s.get('gimbal', True):
        # The slewing ring, yoke, trunnions, cradle or mantlet and recoil sleeves (turret_gimbal.py).
        parts += turret_gimbal.fit(sys.modules[__name__], prefix, pivot, elev, s, f, r, trunnion, parts,
                                   moving, mats)
    return {'parts': parts, 'elevation': elev, 'muzzles': anchors, 'spec': s, 'moving': moving}


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


# --- turrets built from the game's sprites ------------------------------------------

_SPRITES = {}
NOMINAL_RING = {'SMALL': 5.5, 'MEDIUM': 9.5, 'LARGE': 12.5}   # ring radius at which 1 px = 1 unit
# Heights in sprite pixels (one pixel = one hull unit at the nominal ring):
# wall, roof, glacis width. The sprites give the plan; these give the section.
SECTION = {'SMALL': (1.8, 3.2, .9), 'MEDIUM': (2.6, 4.6, 1.3), 'LARGE': (3.4, 6.0, 1.7)}


def sprite(weapon):
    if not _SPRITES:
        with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'sprite_outlines.json'),
                  encoding='utf-8') as fh:
            _SPRITES.update(json.load(fh))
    return _SPRITES[weapon]


# --- paints: the sprite's colours as weathered materials -------------------------
# A paint is named by its target: the linear colour the sprite shows (its
# measured mean, or a catalogue override). Its base colour is not the target:
# under the review studio's lights (studio.py) a flat roof of base colour C
# renders paler and greyer than C, so calibrate_paints.py solves, per target,
# the base whose flat top view renders the sprite's colour, and records it in
# paint_calibration.json. A target missing from the table falls back to the
# table's median gain (and says so): rerun the calibration after adding one.
_CALIB = {}
SPEC = {'level': .12, 'rough': .62}
FINISH = {'paint': 'paint', 'accent': 'paint', 'accent2': 'paint', 'mark': 'paint', 'plate': 'paint',
          'armour': 'paint', 'metal': 'metal', 'steel': 'metal'}


def _key(rgb):
    return ','.join(f'{c:.4f}' for c in rgb)


def calibrated(rgb, finish='paint'):
    """The base colour that renders as linear `rgb` on a flat top under the studio."""
    if not _CALIB:
        p = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'paint_calibration.json')
        _CALIB.update(json.load(open(p, encoding='utf-8')) if os.path.exists(p) else {'paint': {}, 'metal': {}})
    table = _CALIB.get(finish, {})
    if _key(rgb) in table:
        return tuple(table[_key(rgb)])
    gains = [[b/max(float(t), 1e-4) for t, b in zip(k.split(','), v)] for k, v in table.items()]
    if not gains:
        return tuple(rgb)
    print(f'turret_kit: paint {_key(rgb)} ({finish}) not calibrated; run calibrate_paints.py')
    med = [sorted(g[i] for g in gains)[len(gains)//2] for i in range(3)]
    return tuple(min(1.0, c*m) for c, m in zip(rgb, med))


def paint_material(name, rgb, finish='paint', base=None):
    """A weathered paint (or bare gun metal, finish='metal') whose flat top view
    renders as linear `rgb` under the studio. One node tree for every paint:

      mottle  low-frequency value and roughness variation, as sprayed plate
              never is one flat colour (a few percent, zero mean)
      wear    chips through the paint to primer and bare steel on edges only:
              a Bevel-node normal against the true normal finds every hard
              edge and chamfer, broken up by noise so it is chipped, not a line
      grime   ambient occlusion darkens crevices and the roots of fittings,
              as the sprite's own dark pixels sit where parts meet

    Open flat faces carry neither wear nor grime, so they render the target."""
    m = bpy.data.materials.get(name)
    if m is not None:
        return m
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    N, L = nt.nodes, nt.links
    bsdf = N['Principled BSDF']
    metal = finish == 'metal'
    col = base if base is not None else calibrated(rgb, finish)
    m.diffuse_color = (*col, 1.0)
    m['paint_target'] = list(rgb)
    m['paint_finish'] = finish

    def node(kind, **kw):
        n = N.new(kind)
        for k, v in kw.items():
            if k.startswith('in_'):
                n.inputs[k[3:].replace('_', ' ')].default_value = v
            else:
                setattr(n, k, v)
        return n

    def maprange(src, a, b, c=0.0, d=1.0):
        n = node('ShaderNodeMapRange', clamp=True)
        n.inputs['From Min'].default_value, n.inputs['From Max'].default_value = a, b
        n.inputs['To Min'].default_value, n.inputs['To Max'].default_value = c, d
        L.new(src, n.inputs['Value'])
        return n.outputs['Result']

    def op(kind, a, b):
        n = node('ShaderNodeMath', operation=kind)
        for i, v in enumerate((a, b)):
            if isinstance(v, (int, float)):
                n.inputs[i].default_value = v
            else:
                L.new(v, n.inputs[i])
        return n.outputs['Value']

    def grey(src):
        n = node('ShaderNodeCombineColor')
        for i in range(3):
            L.new(src, n.inputs[i])
        return n.outputs[0]

    def multiply(a, b):
        n = node('ShaderNodeMix', data_type='RGBA', blend_type='MULTIPLY', in_Factor=1.0)
        L.new(a, n.inputs[6])
        L.new(b, n.inputs[7])
        return n.outputs[2]
    tc = node('ShaderNodeTexCoord')
    mott = node('ShaderNodeTexNoise', in_Scale=.35, in_Detail=4.0, in_Roughness=.55)
    L.new(tc.outputs['Object'], mott.inputs['Vector'])
    chip = node('ShaderNodeTexNoise', in_Scale=3.5, in_Detail=12.0, in_Roughness=.7)
    L.new(tc.outputs['Object'], chip.inputs['Vector'])
    c0 = node('ShaderNodeRGB')
    c0.outputs[0].default_value = (*col, 1.0)
    mottled = multiply(c0.outputs[0], grey(maprange(mott.outputs['Fac'], .3, .7, .95, 1.05)))
    # Edge mask: how far the bevelled normal leans off the true one.
    bev = node('ShaderNodeBevel', samples=8, in_Radius=.35)
    geo = node('ShaderNodeNewGeometry')
    dot = node('ShaderNodeVectorMath', operation='DOT_PRODUCT')
    L.new(bev.outputs['Normal'], dot.inputs[0])
    L.new(geo.outputs['Normal'], dot.inputs[1])
    edge = maprange(dot.outputs['Value'], .995, .94)           # 1 on a hard edge, 0 on open plate
    wear = op('MULTIPLY', op('MULTIPLY', edge, maprange(chip.outputs['Fac'], .45, .62)), .5 if metal else .7)
    # Paint chips to dark bare steel; gun metal rubs brighter.
    worn_c = node('ShaderNodeRGB')
    worn = (.1, .095, .09) if not metal else tuple(min(1, 1.4*c+.04) for c in col)
    worn_c.outputs[0].default_value = (*worn, 1.0)
    mix = node('ShaderNodeMix', data_type='RGBA', blend_type='MIX')
    L.new(wear, mix.inputs['Factor'])
    L.new(mottled, mix.inputs[6])
    L.new(worn_c.outputs[0], mix.inputs[7])
    # Grime: occlusion within about 0.6 units darkens to 60%.
    ao = node('ShaderNodeAmbientOcclusion', samples=8, in_Distance=.6)
    L.new(multiply(mix.outputs[2], grey(maprange(ao.outputs['AO'], .35, 1.0, .6, 1.0))), bsdf.inputs['Base Color'])
    if os.environ.get('TURRET_PAINT_DEBUG') == 'edge':                 # review the masks themselves
        L.new(grey(edge), bsdf.inputs['Base Color'])
    elif os.environ.get('TURRET_PAINT_DEBUG') == 'wear':
        L.new(grey(wear), bsdf.inputs['Base Color'])
    rough0, rough1 = (.36, .26) if metal else (SPEC['rough'], .45)
    # Matte service paint: a low specular level, or the studio's bright world
    # and overhead rim sun lay a grey sheen over every roof that greys the
    # sprite's saturated reds and yellows and lifts its darks (a floor of about
    # 76/255 at the default level). Chips still shine as bare steel.
    if not metal:
        L.new(op('ADD', op('MULTIPLY', wear, .5-SPEC['level']), SPEC['level']), bsdf.inputs['Specular IOR Level'])
    rv = maprange(mott.outputs['Fac'], .3, .7, rough0-.06, rough0+.06)
    L.new(op('ADD', op('MULTIPLY', wear, rough1-rough0), rv), bsdf.inputs['Roughness'])
    metallic0 = .75 if metal else 0.0
    L.new(op('ADD', op('MULTIPLY', wear, .45-metallic0), metallic0), bsdf.inputs['Metallic'])
    return m


# Military livery: how much of the sprite's paint survives on a working gun.
# The sprites are bright game art; on a cinematic hull every turret wearing
# its sprite's full colour reads as a patchwork of toys. Each paint keeps a
# hint of its sprite's hue at service-paint values: its chroma is blended
# `hint` of the way from neutral grey, and its luminance compressed into the
# range real field greys, olives and tans sit in. Markings keep most of their
# colour (they are meant to be seen); barrels and gun metal go to gunmetal.
#   key: (hint, luminance scale, luminance floor)
LIVERY = {'paint': (.35, .38, .05), 'plate': (.3, .4, .06), 'armour': (.35, .38, .05),
          'accent': (.45, .42, .05), 'accent2': (.45, .42, .05), 'mark': (.8, .7, .04),
          'missile': (.35, .5, .04)}
GUNMETAL = (.11, .11, .115)
METAL_HINT = .15


def _lum(c):
    return .2126*c[0]+.7152*c[1]+.0722*c[2]


def livery(rgb, key, chroma=None):
    """A sprite paint (linear RGB) brought to military service colour (LIVERY);
    chroma, when given, replaces the share of the paint's colour that is kept."""
    if key in ('metal', 'steel'):
        g = _lum(GUNMETAL)
        tint = [c/max(_lum(rgb), 1e-4) for c in rgb]
        return tuple(round(g*((1-METAL_HINT)+METAL_HINT*t), 4) for t in tint)
    hint, scale, floor = LIVERY[key]
    if chroma is not None:
        hint = chroma
    L = _lum(rgb)
    L2 = floor+scale*L
    return tuple(round(min(1.0, L2*((1-hint)+hint*c/max(L, 1e-4))), 4) for c in rgb)


def paint_targets(s):
    """The weapon's paints off its sprite, linear RGB by material key, in service
    livery (LIVERY). A catalogue entry's `paints` names sprite colours where the
    measured one is wrong or the builder needs another (the flak's yellow);
    plate, steel and armour stay the hull's own unless `paints` names them."""
    try:
        c = dict(sprite(s.get('sprite', s['weapon']))['colours'])
    except KeyError:            # a hardpoint with no turret sprite: its catalogue paints only
        c = {'main': s['paints']['paint']}
    c.setdefault('accent', c['main'])
    c.setdefault('metal', (.35, .35, .36))
    c['mark'] = (.85, .55, .1)
    over = s.get('paints') or {}
    out = {}
    for key in ('paint', 'accent', 'accent2', 'metal', 'mark'):
        out[key] = tuple(over.get(key, c['main'] if key == 'paint' else c.get(key, c['accent'])))
    for key in ('armour', 'plate', 'steel'):
        if key in over:
            out[key] = tuple(over[key])
    return {key: livery(rgb, key, s.get('chroma') if key in ('paint', 'accent') else None)
            for key, rgb in out.items()}


def _sprite_mats(s, mats):
    """The hull's materials with the weapon's own paints (paint_targets) laid
    over them as calibrated, weathered materials (paint_material)."""
    out = dict(mats)
    targets = paint_targets(s)
    if s.get('hull_finish'):
        # The hull dresses the turret in its own finish: its materials win wherever
        # it supplies the key, and are used as they are, not re-weathered, except
        # that sprite_tint mixes the sprite's own colour into its paints.
        targets = {key: rgb for key, rgb in targets.items() if key not in mats}
        if s.get('sprite_tint'):
            raw = sprite(s.get('sprite', s['weapon']))['colours']
            for key, src in (('paint', 'main'), ('accent', 'accent')):
                if key in mats and src in raw:
                    out[key] = tinted(mats[key], tuple(raw[src]), s['sprite_tint'])
    for key, rgb in targets.items():
        out[key] = paint_material(f"Turret {s['weapon']} {key} {_key(rgb)}", rgb, FINISH[key])
    for key in ('armour', 'plate', 'steel'):
        # The hull's own plate and machinery, weathered like the paints but at the
        # hull's colour (not calibrated: it is the hull's, not the sprite's).
        hm = mats.get(key)
        b = next((n for n in hm.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None) \
            if hm is not None and key not in targets and hm.node_tree else None
        if b is not None and not s.get('hull_finish'):
            rgb = tuple(b.inputs['Base Color'].default_value[:3])
            out[key] = paint_material(f'Turret {key} {hm.name}', rgb, FINISH[key], base=rgb)
    glow = bpy.data.materials.get('Turret lamp glow')
    if glow is None:
        glow = mats['plate'].copy()
        glow.name = 'Turret lamp glow'
        b = next((n for n in glow.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'), None) if glow.node_tree else None
        if b is not None:
            # Saturated amber, as the sprites draw it: a dark base so the studio's
            # light does not wash the lamp out to cream.
            b.inputs['Base Color'].default_value = (.25, .1, .02, 1)
            b.inputs['Emission Color'].default_value = (1.0, .5, .05, 1)
            b.inputs['Emission Strength'].default_value = 1.8
    out['glow'] = glow
    out.setdefault('glass', mats['dark'])
    # Energy weapons' lamps: emitter blue-white and status red (a hull may supply its own).
    out.setdefault('lamp_blue', lamp_material('Turret lamp blue-white', (.35, .62, 1.0), 2.0))
    out.setdefault('lamp_red', lamp_material('Turret lamp red', (1.0, .15, .1), 3.0))
    out.setdefault('lamp_core', lamp_material('Turret lamp gold core', (.95, .85, .5), 1.6))
    cable = bpy.data.materials.get('Turret power cable')
    if cable is None:
        cable = bpy.data.materials.new('Turret power cable')
        cable.use_nodes = True
        cb = cable.node_tree.nodes['Principled BSDF']
        cb.inputs['Base Color'].default_value = (.025, .025, .028, 1)
        cb.inputs['Roughness'].default_value = .55
    out.setdefault('cable', cable)
    return out


def tinted(mat, rgb, f):
    """A copy of `mat` whose base colour is mixed a fraction f toward linear `rgb`,
    whatever drives it (a constant, or a procedural finish such as hex plating)."""
    name = f'{mat.name} tint {_key(rgb)} {f:.2f}'
    if name in bpy.data.materials:
        return bpy.data.materials[name]
    m = mat.copy()
    m.name = name
    nt = m.node_tree
    b = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None) if nt else None
    if b is None:
        return mat
    inp = b.inputs['Base Color']
    mix = nt.nodes.new('ShaderNodeMix')
    mix.data_type = 'RGBA'
    a_in = next(x for x in mix.inputs if x.identifier == 'A_Color')
    b_in = next(x for x in mix.inputs if x.identifier == 'B_Color')
    mix.inputs[0].default_value = f
    b_in.default_value = (*rgb, 1)
    if inp.links:
        nt.links.new(inp.links[0].from_socket, a_in)
    else:
        a_in.default_value = inp.default_value
    nt.links.new(next(x for x in mix.outputs if x.identifier == 'Result_Color'), inp)
    return m


def lamp_material(name, rgb, strength=1.4):
    """A lit lamp or lens in the sprite's colour (the Hurricane's cyan flank lamps)."""
    m = bpy.data.materials.get(name)
    if m is None:
        m = bpy.data.materials.new(name)
        m.use_nodes = True
        b = m.node_tree.nodes['Principled BSDF']
        b.inputs['Base Color'].default_value = (*rgb, 1)
        b.inputs['Emission Color'].default_value = (*rgb, 1)
        b.inputs['Emission Strength'].default_value = strength
        b.inputs['Roughness'].default_value = .2
    return m


def _edge_distance(poly, x, y):
    best = 1e9
    for (ax, ay), (bx, by) in zip(poly, poly[1:]+poly[:1]):
        dx, dy = bx-ax, by-ay
        t = max(0, min(1, ((x-ax)*dx+(y-ay)*dy)/((dx*dx+dy*dy) or 1e-9)))
        best = min(best, math.hypot(x-ax-t*dx, y-ay-t*dy))
    return best


def _inside(poly, x, y):
    c = False
    for (ax, ay), (bx, by) in zip(poly, poly[1:]+poly[:1]):
        if (ay > y) != (by > y) and x < ax+(y-ay)*(bx-ax)/(by-ay):
            c = not c
    return c


def _centroid(pts):
    return sum(q[0] for q in pts)/len(pts), sum(q[1] for q in pts)/len(pts)


def _sprite_house(g, f, s, r, z0):
    """The base sprite stood up: its outline walls, a glacis stepping in to the
    roof, its dark detail as slots, its second paint as raised plates, its
    thin prongs (sight posts, arms) as steel, and plate bolts round the roof."""
    sp = sprite(s.get('sprite', s['weapon']))
    k = r/NOMINAL_RING[sp['size']]
    wall, roof, glacis = (v*k for v in SECTION[sp['size']])
    wall *= s.get('height', 1.0)
    roof *= s.get('height', 1.0)
    body = [(x*k, y*k) for x, y in sp['body']]
    zw, zr = z0+wall, z0+roof
    g['paint'].prism(body, z0, zw, glacis, zr)

    def surface(x, y):
        if not _inside(body, x, y):
            return None
        return zw+(zr-zw)*min(1, _edge_distance(body, x, y)/glacis)
    for p in sp['slots']:
        pts = [(x*k, y*k) for x, y in p]
        z = surface(*_centroid(pts))
        if z is None or z < zr-.02:          # on the glacis the sprite's shading is the slope itself
            continue
        g['dark'].prism(pts, z-.3*k, z+.03*k)
    for p in sp['panels']:
        pts = [(x*k, y*k) for x, y in p]
        z = surface(*_centroid(pts))
        if z is None:
            continue
        g['accent'].prism(pts, z-.2*k, z+.35*k, min(.25*k, _CAP[0]*3), z+.5*k)
    for p in sp['prongs']:
        pts = [(x*k, y*k) for x, y in p]
        g['metal'].prism(pts, z0+.2*k, zr+.6*k, min(.2*k, _CAP[0]*2), zr+.8*k)
    if f.near:
        # Bolts along the roof's edge, and a hatch where the roof is clear.
        cxb, cyb = _centroid(body)
        inner = []
        for x, y in body:
            dx, dy = cxb-x, cyb-y
            n = math.hypot(dx, dy) or 1
            inner.append((x+dx/n*(glacis+.25*k), y+dy/n*(glacis+.25*k)))
        for a, b in zip(inner, inner[1:]+inner[:1]):
            if math.dist(a, b) > 1.2*f.u:
                f.bolt_row(g['steel'], (a[0], a[1], zr), (b[0], b[1], zr), spacing=.9)
        hx = cxb-.3*(max(q[0] for q in body)-min(q[0] for q in body))
        clear = all(not _inside([(x*k, y*k) for x, y in p], hx, cyb) for p in sp['slots']+sp['panels'])
        z = surface(hx, cyb)
        if z is not None and z >= zr-.02 and clear:
            f.hatch(g, (hx, cyb, zr), size=min(.9, .25*r/f.u))
    # Where the elevating mass rides: the axis height and the trunnion line.
    lathes = [b for b in sp['barrels'] if b['kind'] == 'lathe']
    rmax = max([max(q[1] for q in b['profile']) for b in lathes] or [1.0])*k*.85
    if sp['barrel_below']:
        za = z0+max(.55*wall, rmax+.25*k)
    else:
        za = zr+rmax*.9
    xt = min([b['x_back'] for b in sp['barrels']] or [0])*k
    xt = max(xt, min(q[0] for q in body)+glacis)
    return (xt, 0.0, za), {'k': k, 'zr': zr, 'zw': zw, 'sp': sp, 'z0': z0}


def _sprite_gun(g, f, s, r, t, ex):
    """Every part of the recoil sprite: long round parts are barrels lathed to
    the sprite's own width profile (breech blocks, jackets, muzzles), with its
    dark rows as grooves, each recoiling on its own empty; short broad parts
    (a driver head, a breech block) are stood-up blocks that elevate."""
    sp, k, za = ex['sp'], ex['k'], ex.get('za', t[2])
    if ex.get('gun_parts'):
        ex['gun_parts'](g, za)
    if ex.get('muzzles'):
        return ex['muzzles']
    muzzles = []
    nb = 0
    for b in sp['barrels']:
        y = b['y']*k
        if b['kind'] == 'block':
            pts = [(x*k, yy*k) for x, yy in b['outline']]
            h = 2*b['half_width']*k*.45
            c = min(.3*k, h/4)
            g['metal'].prism(pts, za-h/2, za+h/2-c, c, za+h/2)
            continue
        nb += 1
        steps = []                            # merge equal-radius rows into steps
        for x, rr in b['profile']:
            x, rr = x*k, rr*k*.85*ex.get('barrel_scale', 1.0)
            if steps and abs(steps[-1][2]-rr) < 1e-6:
                steps[-1][1] = x+.5*k
            else:
                steps.append([x-.5*k, x+.5*k, rr])
        if ex.get('barrel_end'):                  # the builder draws the muzzle itself
            steps = [st for st in steps if st[0] < ex['barrel_end']*k]
            steps[-1][1] = min(steps[-1][1], ex['barrel_end']*k)
        if ex.get('barrel_root'):
            steps.insert(0, [steps[0][0]-ex['barrel_root']*k, steps[0][0], steps[0][2]])
        x0 = steps[0][0]
        rg = g.sub(f'recoil {nb}', (x0, y, za), .1*r)
        c = min(.12*k, _CAP[0])
        pts = [(0, 0)]
        for a, bb, rr in steps:
            pts += [(rr-c, a-x0), (rr, a-x0+c), (rr, bb-x0-c), (rr-c, bb-x0)]
        L = steps[-1][1]-x0
        bore = min(.5*steps[-1][2], .6*k)
        pts += [(bore, L), (bore, L-1.5*k), (0, L-1.5*k)]
        rg['metal'].lathe(pts, 20, (x0, y, za), 'X')
        if ex.get('muzzle_ring'):
            rr = steps[-1][2]
            rg['steel'].cyl((x0+L-.9*k, y, za), rr*1.18, 1.0*k, 'X', n=20, bevel=.08*k)
        runs = []                             # dark rows: grooves, consecutive rows merged
        for x in sorted(x*k for x in b['bands']):
            if runs and x-runs[-1][1] <= 1.01*k:
                runs[-1][1] = x
            else:
                runs.append([x, x])
        for a, bb in runs:
            xm = (a+bb)/2
            rr = next((st[2] for st in steps if st[0] <= xm <= st[1]), steps[-1][2])
            rg['dark'].cyl((xm, y, za), rr*1.03, (bb-a)+.6*k, 'X', n=20)
    for x, y in sp['offsets']:
        muzzles.append((x*k, y*k, za))
    if not muzzles:
        muzzles = [(max(b['x_front'] for b in sp['barrels'])*k, 0, za)]
    # The mantlet where barrels drawn below the base pass into the house.
    lb = [b for b in sp['barrels'] if b['kind'] == 'lathe']
    if lb and sp['barrel_below'] and not ex.get('no_mantlet'):
        ys = [b['y']*k for b in lb]
        rmax = max(max(q[1] for q in b['profile']) for b in lb)*k*.85
        xf = max(q[0] for q in sp['body'])*k
        xm = min(xf-.6*k, min(b['x_back'] for b in lb)*k+1.2*k)
        g['steel'].box((xm, (max(ys)+min(ys))/2, za), (1.0*k, max(ys)-min(ys)+2.6*rmax, 2.4*rmax), .1*k)
    return muzzles


# --- houses built by hand from their sprites ----------------------------------------
# Each reads its sprite (sprite_outlines.json) for proportions, barrels and
# muzzles, and is drawn in sprite pixels times k: at the nominal ring one pixel
# is one hull unit, so the turret covers its sprite exactly from above. The
# sprite's dark pixels are gaps and shadow between parts, so they are modelled
# as depth (louvres, gaps, recesses), never as painted stickers.

def _k(s, r):
    return r/NOMINAL_RING[sprite(s.get('sprite', s['weapon']))['size']]


def _disc_stations(xc, R, x0, x1, n, z0, h_of, top, tumble):
    """Loft stations for a drum of radius R centred at xc, cut flat at x0 / x1."""
    out = []
    for i in range(n+1):
        x = x0+(x1-x0)*i/n
        w = max(math.sqrt(max(R*R-(x-xc)**2, 0)), .25*R)
        h = h_of(x)
        out.append((x, section(w, z0, z0+h, min(top, .45*h, .45*w), .0, min(tumble, .3*w))))
    return out


def _house_heavyac(g, f, s, r, z0):
    """heavy_autocannon_turret_base.png: a round, low drum of pale plate cut
    flat at the gun face, five raised ribs running fore and aft across its
    roof, a louvred grille of four by three slots over the rear, small
    brackets round the rim, and three gun sleeves in the face."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    R, xc = 10.6*k, -1.6*k
    x_rear, x_face = xc-R+.3*k, 8.6*k
    h0 = 4.6*k

    def h_of(x):           # the roof falls toward the rim: a shallow crown
        return h0*(1-.28*((x-xc)/R)**2)
    g['paint'].loft_x(_disc_stations(xc, R, x_rear, x_face, 16, z0, h_of, 1.0*k, .7*k))
    # The face: a flat armour plate with three sleeve ports.
    g['plate'].box((x_face+.15*k, 0, z0+h0*.48), (.3*k, 2*math.sqrt(R*R-(x_face-xc)**2)-.8*k, h0*.8), .1*k)
    for b in sp['barrels']:
        g['dark'].cyl((x_face+.32*k, b['y']*k, z0+2.3*k), 1.75*k, .06*k, 'X', n=24)
    # Ribs: five raised fore-and-aft ribs across the crown.
    for y in (-6.2, -3.1, 0, 3.1, 6.2):
        yk = y*k
        half = math.sqrt(max((R-1.6*k)**2-yk*yk, 0))
        xa, xb = max(xc-half, x_rear+1.2*k), min(xc+half, x_face-.6*k)
        n = 4                                   # in segments that follow the crown down
        for i in range(n):
            a0, a1 = xa+(xb-xa)*i/n, xa+(xb-xa)*(i+1)/n
            za0, za1 = z0+h_of(a0)-.08*k, z0+h_of(a1)-.08*k
            pitch = math.atan2(za1-za0, a1-a0)
            g['plate'].box(((a0+a1)/2, yk, (za0+za1)/2+.22*k), (math.hypot(a1-a0, za1-za0)+.05*k, .7*k, .5*k),
                           .12*k, rot=Matrix.Rotation(-pitch, 4, 'Y'))
    # Rear grille: four columns by three rows of louvred slots on the back slope.
    for col in (-4.6, -1.55, 1.55, 4.6):
        for row in range(3):
            x = (-4.0-1.45*row)*k
            zt = z0+h_of(x)-.05*k
            g['dark'].box((x, col*k, zt), (.9*k, 1.9*k, .5*k))
            if f.near:
                g['steel'].box((x+.1*k, col*k, zt+.2*k), (.25*k, 1.9*k, .12*k))
    # Rim brackets (the sprite's teeth round its edge) and bolts.
    for i in range(14):
        a = math.pi*(i+.5)/7
        x, y = xc+R*math.cos(a), R*math.sin(a)
        if x > x_face-.5*k:
            continue
        g['steel'].box((x, y, z0+.9*k), (.9*k, .9*k, 1.4*k), .1*k, rot=Matrix.Rotation(a, 4, 'Z'))
    if f.near:
        for i in range(36):
            a = 2*math.pi*i/36
            x, y = xc+(R-.5*k)*math.cos(a), (R-.5*k)*math.sin(a)
            if x < x_face-.8*k:
                g['steel'].cyl((x, y, z0+h_of(x)*.97), .07*f.u, .05*f.u, 'Z', n=6)
        f.hatch(g, (xc-2.2*k, 5.0*k, z0+h_of(xc-2.2*k)-.05*k), size=min(.9, .3*r/f.u))
        f.periscope(g, (xc+3.5*k, -7.0*k, z0+h_of(xc+3.5*k)-.4*k))
        f.eye(g['steel'], (xc-6.5*k, 4.5*k, z0+h_of(xc-6.5*k)-.1*k))
        f.eye(g['steel'], (xc-6.5*k, -4.5*k, z0+h_of(xc-6.5*k)-.1*k))
        f.vent(g['steel'], (xc+1.5*k, -5.0*k, z0+h_of(xc+1.5*k)-.1*k))

    def gun_parts(gg, za):
        for b in sp['barrels']:      # sleeves: the base sprite's three short barrel housings
            gg['steel'].cyl(((x_face-1.2*k), b['y']*k, za), 1.55*k, 2.6*k, 'X', n=24, bevel=.12*k)
            gg['plate'].cyl(((x_face+.45*k), b['y']*k, za), 1.7*k, .5*k, 'X', n=24, bevel=.08*k)
    za = z0+2.3*k
    return (x_face-2.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True}


def _house_flak(g, f, s, r, z0):
    """flak_cannon_turret_base.png: two sloped tan ammunition pods either side
    of an open well the barrel recoils into, a low steel frame closing the well
    at the rear with a red crossbar, and (in the gun group) the red breech box
    with its yellow markings and twin sight prongs."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    for sy in (1, -1):
        # The pod: lofted fore and aft, wider and taller forward, rounded over.
        st = []
        for x, w, yc, h in ((-9.5, 2.0, 7.6, 2.4), (-7.0, 2.7, 7.4, 3.0), (-3.0, 3.1, 6.9, 3.6),
                            (1.0, 3.2, 6.5, 4.0), (4.0, 3.0, 6.2, 4.1), (6.2, 2.4, 6.0, 3.6)):
            loop = [(sy*yc*k+yy, zz) for yy, zz in section(w*k, z0, z0+h*k, 1.1*k, .2*k, .6*k)]
            st.append((x*k, loop if sy > 0 else loop[::-1]))
        g['paint'].loft_x(st)
        # Weld seam up the pod's spine, a filler cap and (starboard) the vents.
        f.weld(g['steel'], (-8.5*k, sy*7.5*k, z0+2.5*k), (5.5*k, sy*6.1*k, z0+4.0*k))
        g['steel'].cyl((-5.5*k, sy*7.2*k, z0+3.35*k), .55*k, .3*k, 'Z', n=16, bevel=.06*k)
        if sy < 0:
            for i in range(3):
                g['dark'].box(((-1.0+1.1*i)*k, sy*8.0*k, z0+3.6*k), (.6*k, 1.6*k, .5*k))
                if f.near:
                    g['steel'].box(((-1.0+1.1*i)*k, sy*8.0*k, z0+3.85*k), (.2*k, 1.6*k, .1*k))
        # The rear frame: a steel rail from the pod back to the crossbar.
        g['steel'].box((-10.0*k, sy*8.6*k, z0+.8*k), (4.4*k, .9*k, 1.3*k), .12*k,
                       rot=Matrix.Rotation(sy*.12, 4, 'Z'))
        f.bolt_row(g['steel'], (-11.6*k, sy*8.7*k, z0+1.45*k), (-8.4*k, sy*8.5*k, z0+1.45*k))
    g['accent'].box((-11.9*k, 0, z0+.75*k), (1.5*k, 17.0*k, 1.3*k), .15*k)          # red crossbar
    g['dark'].box((-4.0*k, 0, z0+.25*k), (12.0*k, 7.4*k, .5*k))                    # the well's floor
    for x in (-7.5, -4.5, -1.5):                                                    # well ribs
        g['steel'].box((x*k, 0, z0+.6*k), (.4*k, 7.0*k, .4*k))
    za = z0+3.0*k

    def gun_parts(gg, za):
        # Breech box: the sprite's red block, with its yellow markings and the
        # dark breech opening, carried with the barrel.
        gg['accent'].box((5.6*k, 0, za+.1*k), (7.0*k, 6.6*k, 3.6*k), .22*k)
        gg['mark'].box((7.6*k, 0, za+1.92*k), (1.1*k, 4.2*k, .12*k))
        gg['mark'].box((5.4*k, 0, za+1.92*k), (1.1*k, 3.2*k, .12*k))
        gg['dark'].box((2.25*k, 0, za+.4*k), (.4*k, 3.4*k, 2.0*k))
        gg['steel'].box((9.15*k, 0, za), (.4*k, 4.4*k, 2.8*k), .1*k)                 # face plate
        f.bolt_row(gg['steel'], (9.36*k, -1.9*k, za+1.2*k), (9.36*k, 1.9*k, za+1.2*k), axis='X')
        for sy in (1, -1):                                                         # sight prongs
            gg['steel'].box((10.4*k, sy*3.9*k, za+1.5*k), (3.4*k, .5*k, .6*k), .08*k,
                            rot=Matrix.Rotation(-sy*.25, 4, 'Z'))
            gg['steel'].box((11.8*k, sy*4.3*k, za+2.0*k), (.5*k, .5*k, 1.6*k), .08*k)
            gg['steel'].cyl((8.0*k, sy*3.35*k, za), .8*k, .6*k, 'Y', n=16, bevel=.08*k)   # trunnions
    return (5.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True}


def _house_lightac(g, f, s, r, z0):
    """light_autocannon_turret_base.png: a compact green-grey box, its light
    spine fore and aft, three rows of slots cut right through the roof either
    side of the spine, and two dark feet aft with a notch between them."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    h = 2.6*k
    body = g['paint#body']
    body.loft_x([(-5.0*k, section(4.0*k, z0, z0+h-.2*k, .3*k, .1*k, .15*k)),
                 (-4.6*k, section(4.9*k, z0, z0+h, .3*k, .1*k, .15*k)),
                 (2.2*k, section(4.9*k, z0, z0+h, .3*k, .1*k, .15*k)),
                 (3.0*k, section(4.0*k, z0, z0+h-.25*k, .45*k, .1*k, .35*k))])
    zt = z0+h
    g['plate'].box((-1.0*k, 0, zt+.15*k), (8.4*k, 1.8*k, .35*k), .12*k)                 # spine
    # Vent grooves: cut through the roof and out through the side wall, as the
    # sprite's dark rows reach its outer edge (rows 13, 15, 17), with the louvre
    # bar its mid-tone pixel draws half way along each.
    for x, y0, y1 in ((-.5, 1.1, 4.5), (-2.5, 1.1, 4.5), (-4.4, 2.0, 4.0)):
        for sy in (1, -1):
            out = 5.4 if y1 > 4.2 else y1
            body.cut().box((x*k, sy*(y0+out)/2*k, zt), (.55*k, (out-y0)*k, 1.4*k), .03*k)
            g['dark'].box((x*k, sy*(y0+y1)/2*k, zt-.66*k), (.55*k, (y1-y0)*k, .08*k))
            if y1 > 4.2:
                g['steel'].box((x*k, sy*2.6*k, zt-.25*k), (.55*k, .3*k, .4*k), .03*k)
    for sy in (1, -1):                                                                  # rear feet
        g['steel'].box((-6.0*k, sy*2.1*k, z0+.7*k), (2.6*k, 1.6*k, 1.4*k), .15*k)
        f.bolt_row(g['steel'], (-4.0*k, sy*4.6*k, zt), (2.0*k, sy*4.6*k, zt))
    g['steel'].box((-5.3*k, 0, z0+.5*k), (1.2*k, 2.6*k, 1.0*k), .1*k)
    za = z0+1.6*k

    def gun_parts(gg, za):
        gg['steel'].box((3.3*k, 0, za), (.8*k, 4.6*k, 2.4*k), .12*k)                     # mantlet
        gg['dark'].cyl((4.0*k, 0, za), 1.25*k, .8*k, 'X', n=20, bevel=.06*k)             # root collar
        for sy in (1, -1):
            gg['steel'].cyl((2.6*k, sy*2.5*k, za), .6*k, .5*k, 'Y', n=14, bevel=.06*k)   # trunnions
    return (2.6*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'barrel_scale': 1.0, 'muzzle_ring': True}


def _house_lightmg(g, f, s, r, z0):
    """mg_light_turret_base.png: an open pedestal mount, not a house. A low gunmetal
    frame on the turntable - two crossbars full width with pale lug ends, side rails
    joining their ends, a well between them - carries the yoke (turret_gimbal: two
    cheeks, bearings, trunnion pins, the feed into one trunnion and the sight on the
    other). The gun rides the cradle: the pale receiver cover over the breech, a
    charging handle, and the slim barrel in its recoil sleeve, banded and ending in a
    flash hider. The trunnion and muzzle stand where the gun always stood."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    g['paint'].box((0, 0, z0+.18*k), (10.0*k, 10.6*k, .36*k), .1*k)                    # deck plate
    for x, w in ((3.8, 2.2), (-2.6, 3.0)):                                              # crossbars
        g['plate'].box((x*k, 0, z0+.62*k), (w*k, 11.8*k, .55*k), .12*k)
        for sy in (1, -1):
            lug = g[f'steel#lug{x}{sy}']
            lug.box((x*k, sy*5.5*k, z0+.7*k), ((w+.3)*k, .8*k, .9*k), .1*k)            # lug ends
            lug.cut().cyl((x*k, sy*5.9*k, z0+.7*k), .25*k, .6*k, 'Y', n=16)             # pin socket
            g['dark'].cyl((x*k, sy*5.62*k, z0+.7*k), .25*k, .06*k, 'Y', n=16)
    for sy in (1, -1):
        g['steel'].box((.6*k, sy*5.3*k, z0+.55*k), (7.6*k, .7*k, .5*k), .08*k)          # side rails
    g['dark'].box((1.5*k, 0, z0+.37*k), (2.2*k, 3.6*k, .04*k))                         # the well's floor
    for sy in (1, -1):
        g['steel'].box((-5.6*k, sy*2.4*k, z0+.45*k), (1.6*k, 1.8*k, .9*k), .1*k)        # rear feet
    f.bolt_row(g['steel'], (-4.4*k, 4.0*k, z0+.37*k), (4.4*k, 4.0*k, z0+.37*k))
    f.bolt_row(g['steel'], (-4.4*k, -4.0*k, z0+.37*k), (4.4*k, -4.0*k, z0+.37*k))
    za = z0+2.2*k

    def gun_parts(gg, za):
        gg['plate'].box((.7*k, 0, za+1.25*k), (2.6*k, 1.9*k, .3*k), .1*k)               # receiver cover
        gg['steel'].cyl((-.4*k, 0, za+1.25*k), .18*k, 2.0*k, 'Y', n=10)                 # its hinge
        gg['steel'].box((2.4*k, -1.2*k, za+.6*k), (.9*k, .5*k, .25*k), .05*k)           # charging handle
        # A gun shield on the cradle's nose (the sprite's neck block): armoured plate
        # with its corners cut, the barrel through a slot, riding the elevation.
        sh = gg['paint#shield']
        sh.prism([(5.25*k, y*k) for y in (-2.6, 2.6)]+[(5.75*k, y*k) for y in (2.6, -2.6)], za-1.1*k, za+1.3*k)
        sh.cut().box((5.5*k, 0, za), (1.2*k, 1.3*k, 1.3*k))
        for sy in (1, -1):
            sh.cut().box((5.5*k, sy*2.6*k, za+1.3*k), (1.2*k, 1.0*k, 1.0*k), rot=Matrix.Rotation(math.pi/4, 4, 'X'))
        f.bolt_row(gg['steel'], (5.8*k, -2.1*k, za+.9*k), (5.8*k, 2.1*k, za+.9*k), axis='X')
        rg = gg.sub('recoil 1', (1.0*k, 0, za), .1*r)
        rg['metal'].cyl((7.8*k, 0, za), .55*k, 13.4*k, 'X', n=20, bevel=.06*k)          # barrel
        rg['metal'].cyl((4.6*k, 0, za), .8*k, 2.6*k, 'X', n=20, bevel=.06*k)            # chamber jacket
        for x in (9.5, 11.0, 12.5):
            rg['dark'].cyl((x*k, 0, za), .6*k, .3*k, 'X', n=20)
        rg['steel'].box((10.0*k, 0, za+.65*k), (.5*k, .2*k, .3*k), .03*k)                # front sight
        rg['steel'].cyl((14.6*k, 0, za), .8*k, .9*k, 'X', n=18, bevel=.05*k)            # flash hider
        rg['dark'].cyl((15.06*k, 0, za), .3*k, .04*k, 'X', n=10)
    return (1.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'muzzles': [(15.1*k, 0, za)], 'elevation_limits': (-2, 60)}


def _house_dualflak(g, f, s, r, z0):
    """double_flak_cannon_turret_base.png: two tan-brown breech drums either
    side of a reddish ribbed spine, each with a lit amber chamber behind its
    barrel and twin prongs forward; rounded dark lug pods out at the sides;
    a tapering tail aft, with two thin posts beside it."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    g['accent2'].loft_x([(-7.0*k, section(3.4*k, z0, z0+4.2*k, .8*k, .1*k, .5*k)),
                         (-5.5*k, section(3.6*k, z0, z0+4.8*k, .9*k, .1*k, .5*k)),
                         (6.5*k, section(3.6*k, z0, z0+4.8*k, .9*k, .1*k, .5*k)),
                         (7.6*k, section(2.8*k, z0, z0+4.2*k, .8*k, .1*k, .5*k))])        # red spine
    # Tail: a trapezoid, broader forward, notched at its rear corners.
    tail_plan = [(-14.6, 2.6, 2.4), (-13.6, 3.4, 2.8), (-12.0, 4.4, 3.2), (-9.5, 5.8, 3.6), (-7.0, 7.0, 3.8)]
    tail = g['paint#tail']
    _plan_loft(tail, tail_plan, z0, k, .7, .1, .5)

    def tail_z(x):
        return z0+_table_at(tail_plan, x)[1]*k
    g['accent'].box((-14.4*k, 0, z0+1.4*k), (.6*k, 5.0*k, 2.2*k), .1*k)                  # end cap
    for x in (-8.5, -10.0):                                    # slots cut across the tail's back
        tail.cut().box((x*k, 0, tail_z(x)), (.5*k, 5.4*k, .8*k), .03*k)
        g['dark'].box((x*k, 0, tail_z(x)-.36*k), (.5*k, 5.4*k, .06*k))
    for sy in (1, -1):
        # Lug pod: a chunky rounded dark pod off the side, just behind the chambers.
        st = []
        for x, w, h in ((-2.8, .6, 2.2), (-2.0, 1.2, 3.0), (0, 1.4, 3.4), (2.0, 1.2, 3.0), (2.8, .6, 2.2)):
            loop = [(sy*12.0*k+yy, zz) for yy, zz in section(w*2.0*k, z0, z0+h*k, .9*k, .1*k, .6*k)]
            st.append((x*k, loop if sy > 0 else loop[::-1]))
        pod = g[f'steel#pod{sy}']
        pod.loft_x(st)
        g['steel'].box((0, sy*10.6*k, z0+1.4*k), (4.2*k, 2.0*k, 2.4*k), .2*k)           # pod root
        pod.cut().box((0, sy*14.9*k, z0+1.8*k), (2.4*k, .8*k, 1.0*k), .05*k)            # slot in its face
        g['dark'].box((0, sy*14.56*k, z0+1.8*k), (2.4*k, .06*k, 1.0*k))
    f.hatch(g, (-11.3*k, 0, tail_z(-11.3)-.05*k), size=min(.9, .3*r/f.u))
    za = z0+3.4*k

    def gun_parts(gg, za):
        for b in sp['barrels']:
            y = b['y']*k
            zt = za+3.0*k
            # Breech drum: a rounded tan-brown block, raised over the spine.
            st = []
            for x, w, h in ((-6.4, 2.2, 4.4), (-5.6, 3.0, 5.4), (6.0, 3.0, 5.4), (8.8, 2.6, 4.8)):
                st.append((x*k, [(y+yy, zz) for yy, zz in section(w*k, za-2.4*k, za-2.4*k+h*k, 1.0*k, .2*k,
                                                                           .7*k)]))
            drum = gg[f'paint#drum{b["y"]}']
            drum.loft_x(st)
            # The amber chamber: the sprite's brightest pixels, behind the barrel
            # (rows 11-16, three pixels wide), a recess in the drum's top lit inside.
            drum.cut().box((6.0*k, y, zt), (4.4*k, 2.6*k, 1.8*k), .06*k)
            gg['glow'].box((6.0*k, y, zt-.86*k), (4.4*k, 2.6*k, .1*k))
            # Aft of it a dark socket in a light frame (rows 19-22: black pixels
            # two wide between light ones), sunk into the drum.
            gg['plate'].box((-1.5*k, y, zt+.04*k), (4.0*k, 4.0*k, .16*k), .06*k)
            gg['plate'].cut().box((-1.5*k, y, zt), (3.0*k, 2.0*k, 2.0*k))
            drum.cut().box((-1.5*k, y, zt), (3.0*k, 2.0*k, 1.6*k), .04*k)
            gg['dark'].box((-1.5*k, y, zt-.76*k), (3.0*k, 2.0*k, .08*k))
            gg['mark'].box((-6.1*k, y, za+.2*k), (.6*k, 4.4*k, 4.6*k), .1*k)            # red band aft
            gg['dark'].cyl((9.4*k, y, za), 1.9*k, 1.2*k, 'X', n=20, bevel=.12*k)         # collar
    return (1.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'barrel_scale': .85, 'muzzle_ring': True}


def _house_needler(g, f, s, r, z0):
    """flechette_heavy_turret_base.png: a full rounded tan-khaki body, widest
    through its rear two-thirds, with a light ribbed spine between the barrel
    roots, a low grey receiver hump, short rounded ear lobes at the front
    corners, and a near-black serrated skirt across the rear."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    H = 6.2
    plan = [(-9.0, 10.4, H-1.6), (-8.4, 11.4, H-.9), (-7.0, 11.8, H-.4), (-1.0, 11.6, H), (2.0, 10.6, H),
            (4.0, 9.6, H-.2), (6.0, 9.0, H-.5), (7.6, 8.6, H-.9)]
    body = g['paint#body']
    _plan_loft(body, plan, z0, k, 1.4, .1, .9)
    zt = z0+H*k
    # Vent grille: a well sunk into the roof, louvres slanted across it, framed
    # by a flush rim (the sprite's light ribbed spine between the barrel roots).
    body.cut().box((.4*k, 0, zt), (12.4*k, 6.6*k, 1.6*k), .05*k)
    for dx, dy, lx, ly in ((0, 3.65, 13.6, .7), (0, -3.65, 13.6, .7), (6.55, 0, .7, 8.0), (-5.75, 0, .7, 8.0)):
        g['plate'].box(((.4+dx)*k, dy*k, zt+.06*k), (lx*k, ly*k, .2*k), .06*k)            # frame
    g['dark'].box((.4*k, 0, zt-.74*k), (12.4*k, 6.6*k, .1*k))                            # well floor
    for x in (6.0, 4.4, 2.8, 1.2, -.4, -2.0, -3.6, -5.2):                                 # louvres
        g['steel'].box((x*k, 0, zt-.3*k), (.35*k, 6.6*k, .8*k), .05*k,
                       rot=Matrix.Rotation(.5, 4, 'Y'))
    g['steel'].box((7.4*k, 0, zt+.25*k), (1.6*k, 4.0*k, .7*k), .2*k)                   # receiver hump
    for sy in (1, -1):
        st = []                                                                         # olive side armour
        for x, w, h in ((-8.4, 1.4, H-1.6), (-7.0, 2.4, H-.6), (1.0, 2.6, H-.4), (2.6, 1.6, H-.9)):
            loop = [(sy*9.4*k+yy, zz) for yy, zz in section(w*k, z0+.8*k, z0+h*k+.3*k, .6*k, .1*k, .5*k)]
            st.append((x*k, loop if sy > 0 else loop[::-1]))
        g['accent'].loft_x(st)
        g['dark'].box((-2.8*k, sy*11.3*k, z0+1.0*k), (8.0*k, .5*k, 1.2*k), .1*k)          # shadow gap under it
        # Ear lobe: a short rounded olive lobe at the front corner.
        st = []
        for x, w, h in ((6.0, 1.0, H-.8), (7.6, 1.6, H), (9.2, 1.4, H+.4), (10.6, .7, H+.2)):
            loop = [(sy*7.6*k+yy, zz) for yy, zz in section(w*k, z0, z0+h*k, .6*k, .1*k, .4*k)]
            st.append((x*k, loop if sy > 0 else loop[::-1]))
        g['accent'].loft_x(st)
    # Rear skirt: a near-black serrated band, full width.
    g['dark'].box((-9.2*k, 0, z0+(H-1.8)/2*k), (1.4*k, 21.6*k, (H-1.8)*k), .3*k)
    for y in range(-10, 11, 2):
        g['dark'].box((-9.95*k, y*k, z0+.8*k), (.3*k, 1.0*k, 1.6*k))
    za = z0+3.6*k

    def gun_parts(gg, za):
        for b in sp['barrels']:
            gg['dark'].box((8.0*k, b['y']*k, za), (1.6*k, 3.8*k, 3.8*k), .2*k)            # breech slot
            gg['plate'].cyl((9.6*k, b['y']*k, za), 1.6*k, 2.0*k, 'X', n=22, bevel=.12*k)  # breech collars
            gg['dark'].cyl((10.62*k, b['y']*k, za), 1.1*k, .05*k, 'X', n=20)
    return (6.5*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'muzzle_ring': True, 'barrel_scale': .8}


def _house_squall(g, f, s, r, z0):
    """squall_turret_under.png + squall_turret_base.png: a broad dark armoured
    platform (the under sprite) skirted down to the deck, dark segmented flank
    blocks stepping out to the rear corners, a louvred rear housing with a
    pale-blue band across it, yoke cheeks, and on the trunnions an enclosed
    gunmetal launcher box with five deep cells holding the rockets (drawn
    loaded, as the game does), bored muzzle caps and a sensor box with a red
    emitter at its front right."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    under = [(x*k, y*k) for x, y in sp['under']]
    g['steel'].prism([(x*.97, y*.97) for x, y in under], z0, z0+.8*k)                     # skirt
    g['paint'].prism(under, z0+.6*k, z0+1.8*k, 1.0*k, z0+2.6*k)
    flank = g['paint#flank']
    for sy in (1, -1):                                       # segmented flank blocks
        for x0, x1, w, h in ((-11.6, -6.4, 4.6, 3.8), (-6.0, -.6, 4.2, 4.2), (-.2, 4.8, 3.8, 3.8),
                             (5.2, 8.0, 3.0, 3.0)):
            pts = [(x0*k, sy*(17.6-w)*k), (x1*k, sy*(17.6-w)*k), (x1*k, sy*17.6*k), ((x0+.8)*k, sy*(18.0+.3*(x0 < -6))*k)]
            if sy < 0:
                pts = pts[::-1]
            flank.prism(pts, z0+.6*k, z0+(h-.6)*k, .6*k, z0+h*k)
            g['dark'].box(((x1+.2)*k, sy*(17.6-w/2)*k, z0+(h-.3)*k), (.3*k, w*k, .5*k))
        f.weld(g['steel'], (-11.0*k, sy*15.6*k, z0+3.8*k), (4.5*k, sy*15.6*k, z0+3.8*k))
        for x in (-9.0, -3.3, 2.3):                     # ports in the blocks' outer faces
            flank.cut().box((x*k, sy*18.0*k, z0+2.2*k), (2.2*k, 1.4*k, 1.2*k), .05*k)
            g['dark'].box((x*k, sy*17.4*k, z0+2.2*k), (2.2*k, .06*k, 1.2*k))
            f.bolt_row(g['steel'], ((x-1.5)*k, sy*16.4*k, z0+4.0*k), ((x+1.5)*k, sy*16.4*k, z0+4.0*k))
    # Rear housing: dark, louvred on top, a pale-blue band, four feet aft.
    g['paint'].box((-8.4*k, 0, z0+3.0*k), (8.4*k, 20.0*k, 3.6*k), .3*k)
    band = g['accent#band']
    band.box((-10.2*k, 0, z0+4.9*k), (3.4*k, 20.4*k, .6*k), .2*k)                       # pale-blue band
    for y in (-7.2, -2.4, 2.4, 7.2):                                                     # its louvre slots
        band.cut().box((-10.2*k, y*k, z0+5.2*k), (2.6*k, 1.2*k, .6*k), .03*k)
        g['dark'].box((-10.2*k, y*k, z0+4.9*k), (2.6*k, 1.2*k, .06*k))
    for y in (-7.5, -2.5, 2.5, 7.5):
        g['steel'].box((-13.4*k, y*k, z0+1.4*k), (1.6*k, 1.2*k, 2.8*k), .12*k)
    # Yoke cheeks carrying the trunnions.
    for y, x0, x1 in ((13.6, -9.5, 7.5), (-12.6, -2.5, 5.5)):
        g['paint'].box(((x0+x1)/2*k, y*k, z0+4.2*k), ((x1-x0)*k, 1.3*k, 3.4*k), .2*k)
        g['steel'].cyl((0, y*k, z0+5.6*k), 1.3*k, 1.5*k, 'Y', n=24, bevel=.12*k)
        f.bolt_row(g['steel'], ((x0+.6)*k, (y+math.copysign(.66, y))*k, z0+5.6*k),
                   ((x1-.6)*k, (y+math.copysign(.66, y))*k, z0+5.6*k), axis='Y')
    za = z0+5.6*k
    L, rad = 20.0*k, 1.3*k

    def gun_parts(gg, za):
        gg.mats = dict(gg.mats, missile=_missile_mat(s.get('mcol', (.18, .18, .18)), True))
        x0, x1, half = -4.0*k, 25.5*k, 10.8*k
        zb, zt = za-2.9*k, za+2.9*k
        zr = za-.2*k                                                                        # rocket axis
        gg['paint'].box(((x0+x1)/2, 0, zb+.5*k), (x1-x0, 2*half, 1.0*k), .15*k)            # floor
        gg['dark'].box(((x0+x1)/2, 0, zr-1.3*k), (x1-x0-.6*k, 2*half-1.0*k, .4*k))         # cell floors
        for sy in (1, -1):                                                                # side walls
            gg['paint'].box(((x0+x1)/2, sy*(half-.6*k), (zb+zt)/2), (x1-x0, 1.2*k, zt-zb), .2*k)
            gg['accent'].box(((x0+x1)/2, sy*(half-.6*k), zt-.1*k), (x1-x0, 1.4*k, .4*k), .1*k)
            f.bolt_row(gg['steel'], (x0+1.0*k, sy*(half+.01*k), zt-.6*k), (x1-1.0*k, sy*(half+.01*k), zt-.6*k),
                       axis='Y', spacing=1.2)
        for y in (6.0, 2.0, -2.0, -6.0):                                                  # cell walls
            gg['accent'].box(((x0+x1)/2+.5*k, y*k, (zr-1.1*k+zt)/2), (x1-x0-1.0*k, .5*k, zt-zr+1.1*k), .06*k)

        gg['accent'].box((x1-1.4*k, 0, zt-.3*k), (2.8*k, 2*half-.4*k, .8*k), .15*k)       # front lip
        gg['accent'].box((7.8*k, 0, zt-.2*k), (1.0*k, 2*half-.4*k, .5*k), .08*k)          # cross-rail
        gg['plate'].box((x0+1.6*k, 0, (zb+zt)/2+.3*k), (1.2*k, 2*half-2.0*k, zt-zb), .12*k)  # tail plate
        for y in (8.0, 4.0, 0.0, -4.0, -8.0):
            gg['steel'].box((x1-.1*k, y*k, zr), (.3*k, 3.6*k, 3.6*k), .1*k)               # muzzle cap
            gg['dark'].cyl((x1+.06*k, y*k, zr), 1.4*k, .1*k, 'X', n=20)                    # bore
            tail = x1-1.2*k-L
            _missile(gg.sub(f'missile {int(3-y/4)}', (tail, y*k, zr), .1*r), f, tail, y*k, zr, L, rad,
                     f.near)                                                                # rockets
        # Sensor box at the front right, on a bracket off the starboard wall.
        gg['steel'].box((21.5*k, -11.8*k, za), (3.0*k, 2.6*k, 1.2*k), .1*k)
        gg['paint'].box((21.5*k, -15.6*k, za+.4*k), (6.0*k, 5.4*k, 3.4*k), .3*k)
        gg['dark'].box((24.52*k, -15.6*k, za+.8*k), (.06*k, 3.6*k, 1.2*k))
        gg['mark'].cyl((24.9*k, -13.6*k, za+1.6*k), .45*k, 1.0*k, 'X', n=12, bevel=.05*k)
        gg['steel'].cyl((20.0*k, -17.0*k, za+2.6*k), .2*k, 1.6*k, 'Z', n=8)
    muzzles = [(25.5*k, y*k, za-.2*k) for y in (8.0, 4.0, 0.0, -4.0, -8.0)]
    return (0.0, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                            'muzzles': muzzles}


def _house_hurricane(g, f, s, r, z0):
    """hurricane_mirv_launcher_turret_base.png: a round tan drum (outer rim,
    radial ribs seated inside it, toothed recesses round its edge, an inner hub
    with the cream breech block carrying red stripes and a light centre disc),
    and a long open fork forward: two rails into a crossbar head with a cream
    band and flared prong tips, the MIRV lying between them."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    g.mats = dict(g.mats, cyan=lamp_material('Turret lamp cyan', (0.0, .5, .5)))
    xc, R = -1.0*k, 14.3*k
    hd = 3.6*k
    drum = g['paint#drum']
    drum.lathe([(R, 0), (R, hd-.6*k), (R-.6*k, hd), (R-2.4*k, hd), (R-2.4*k, hd-.5*k), (0, hd-.5*k)],
               48, (xc, 0, z0))                                                          # drum and rim
    g['plate'].lathe([(8.2*k, hd-.5*k), (8.2*k, hd+.6*k), (7.6*k, hd+1.1*k), (0, hd+1.1*k)], 40, (xc, 0, z0))
    for i in range(16):
        a = 2*math.pi*(i+.5)/16
        rr = (8.2*k+R-2.4*k)/2
        g['steel'].box((xc+rr*math.cos(a), rr*math.sin(a), z0+hd-.35*k), (R-10.8*k, .7*k, .5*k), .08*k,
                       rot=Matrix.Rotation(a, 4, 'Z'))                                    # ribs, inside the rim
        b = a+math.pi/16                          # teeth: recesses cut into the rim and down its face
        rot = Matrix.Rotation(b, 4, 'Z')
        drum.cut().box((xc+(R-1.2*k)*math.cos(b), (R-1.2*k)*math.sin(b), z0+hd), (1.6*k, 1.2*k, .9*k), .04*k,
                       rot=rot)
        g['dark'].box((xc+(R-1.2*k)*math.cos(b), (R-1.2*k)*math.sin(b), z0+hd-.42*k), (1.6*k, 1.2*k, .06*k),
                      rot=rot)
        drum.cut().box((xc+R*math.cos(b), R*math.sin(b), z0+hd-1.2*k), (.6*k, 1.2*k, 1.6*k), .04*k, rot=rot)
        g['dark'].box((xc+(R-.28*k)*math.cos(b), (R-.28*k)*math.sin(b), z0+hd-1.2*k), (.06*k, 1.2*k, 1.6*k),
                      rot=rot)
    if f.near:
        for i in range(32):
            a = 2*math.pi*i/32
            g['steel'].cyl((xc+(R-.3*k)*math.cos(a), (R-.3*k)*math.sin(a), z0+hd), .07*f.u, .05*f.u, 'Z', n=6)
    # Hub: the cream breech block, its red stripes, the light centre disc.
    g['accent'].box((1.5*k, 0, z0+hd+1.3*k), (8.6*k, 12.0*k, .5*k), .2*k)
    for sy in (1, -1):
        g['mark'].box((-1.5*k, sy*4.0*k, z0+hd+1.58*k), (2.2*k, 1.4*k, .1*k))
        g['mark'].box((3.6*k, sy*4.6*k, z0+hd+1.58*k), (1.0*k, 1.8*k, .1*k))
        g['steel'].cyl((xc-5.5*k, sy*14.3*k, z0+2.0*k), .45*k, .4*k, 'Y', n=10)
        # The lamp on it: the sprite's two cyan pixels at the drum's widest point.
        g['cyan'].cyl((xc-5.5*k, sy*14.55*k, z0+2.0*k), .32*k, .14*k, 'Y', n=12)
    g['plate'].cyl((-2.0*k, 0, z0+hd+1.7*k), 3.0*k, .5*k, 'Z', n=32, bevel=.12*k)
    f.hatch(g, (-2.0*k, 0, z0+hd+1.95*k), size=min(.9, .2*r/f.u))
    drum.cut().box((-11.0*k, 0, z0+hd-.5*k), (3.4*k, 10.0*k, .8*k), .06*k)              # vent recess
    g['dark'].box((-11.0*k, 0, z0+hd-.88*k), (3.4*k, 10.0*k, .06*k))
    for x, n in ((-10.2, 7), (-11.8, 5)):                                                 # vent lights
        for i in range(n):
            g['glow'].box((x*k, (i-(n-1)/2)*1.35*k, z0+hd-.8*k), (.7*k, .7*k, .14*k))
    sq = g['plate#square']
    sq.box((8.0*k, 9.6*k, z0+hd+.6*k), (3.2*k, 3.2*k, 2.0*k), .25*k)                      # square housing
    sq.cut().box((8.0*k, 9.6*k, z0+hd+1.6*k), (2.0*k, 2.0*k, .9*k), .05*k)
    g['dark'].box((8.0*k, 9.6*k, z0+hd+1.18*k), (2.0*k, 2.0*k, .06*k))
    lad = g['steel#ladder']
    lad.box((6.0*k, -10.4*k, z0+hd+.4*k), (6.0*k, 2.6*k, 1.6*k), .2*k)                    # ladder unit
    for x in (3.8, 5.0, 6.2, 7.4, 8.6):
        lad.cut().box((x*k, -10.4*k, z0+hd+1.2*k), (.5*k, 2.2*k, .7*k), .03*k)
    for sy in (1, -1):                                                                    # rear feet
        g['steel'].box((-14.4*k, sy*4.2*k, z0+.9*k), (1.6*k, 2.4*k, 1.8*k), .12*k)
    za = z0+hd+1.1*k+4.8*k                                    # the MIRV's axis, clear of the hub
    zt = za-2.2*k                                             # trunnions
    for sy in (1, -1):                                        # yoke pillars from the drum
        g['paint'].box((1.0*k, sy*8.8*k, (z0+hd-.4*k+zt+.8*k)/2), (5.0*k, 1.8*k, zt+.8*k-(z0+hd-.4*k)), .3*k)
        g['paint'].box((1.0*k, sy*8.8*k, z0+hd+.4*k), (7.0*k, 2.6*k, 1.0*k), .3*k)       # foot
        f.weld(g['steel'], (-2.4*k, sy*8.8*k, z0+hd), (4.4*k, sy*8.8*k, z0+hd))

    def gun_parts(gg, za):
        gg.mats = dict(gg.mats, missile=_missile_mat(s.get('mcol', (.1, .13, .14)), True))
        for sy in (1, -1):
            y = sy*7.1*k
            gg['paint'].box((10.0*k, y, za+.2*k), (4.0*k, 2.0*k, 3.4*k), .25*k)          # rail roots
            gg['steel'].box((17.0*k, y, za+.4*k), (14.0*k, 1.4*k, 1.8*k), .12*k)        # rails
            f.bolt_row(gg['steel'], (11.5*k, y+sy*.71*k, za+.4*k), (23.0*k, y+sy*.71*k, za+.4*k),
                       axis='Y', spacing=1.4)
            gg['steel'].cyl((1.0*k, sy*8.0*k, zt), 1.5*k, 1.4*k, 'Y', n=20, bevel=.1*k)   # trunnions
            gg['paint'].box((3.0*k, sy*7.4*k, (za-1.6*k+zt)/2), (3.0*k, 1.4*k, za-zt+1.6*k), .15*k)
            # Prong tip, flared outward off the head.
            gg['steel'].box((31.5*k, sy*6.9*k, za+.4*k), (4.4*k, 1.4*k, 2.0*k), .15*k,
                            rot=Matrix.Rotation(sy*.12, 4, 'Z'))
        # Head: the front bar rounded up into an arch over the MIRV, so it flies out
        # under it; the cream band on its aft face.
        zb, zm, ri, ro = za-1.0*k, za+.4*k, 6.4*k, 8.4*k                  # fins reach 6.1
        def head(x0, x1, grow, m):
            arc = [math.pi*i/12 for i in range(13)]
            loop = [((ro+grow)*math.cos(a), zm+(ro+grow)*math.sin(a)) for a in arc]
            loop += [(-ro-grow, zb), (-ri+grow, zb)]
            loop += [(-(ri-grow)*math.cos(a), zm+(ri-grow)*math.sin(a)) for a in arc]
            loop += [(ri-grow, zb), (ro+grow, zb)]
            gg[m].loft_x([(x0*k, loop), (x1*k, loop)])
        head(25.0, 29.5, 0, 'paint')
        head(24.5, 25.0, .1*k, 'accent')
        # Breech housing: the turret body the MIRV's tail sits in, rounded over.
        # It is waisted ahead of the hub - an inset - and widens again into the MIRV's mount.
        _dome_loft(gg['paint'], [(-9.0, 4.8, 5.8), (-8.2, 6.6, 7.8), (-6.4, 7.4, 8.8), (.8, 7.4, 8.8),
                                 (2.0, 6.0, 8.1), (4.4, 6.0, 8.1), (5.6, 7.4, 8.8), (7.8, 7.0, 8.4),
                                 (8.8, 6.4, 7.8)], za-4.6*k, k, .45)
        gg['dark'].cyl((8.85*k, 0, za+.4*k), 4.1*k, .1*k, 'X', n=32)                         # throat
        # Orange vent lights let into the hull top, a file down each side.
        def lit(x, y):           # the sprite's bright orange pixels: lamps let into the hull, lit
            gg['glow'].box((x*k, y*k, za-4.6*k+_dome_z(y, 7.4, 8.8, .45)*k-.02*k), (.75*k, .75*k, .14*k))
        for sy in (1, -1):
            for x in (-.6, -2.0, -3.4, -4.8):
                lit(x, sy*4.2)
        for sy in (1, -1):
            gg['dark'].box((-4.5*k, sy*6.9*k, za+.2*k), (5.0*k, .2*k, 2.4*k))                # side seams
        gg['steel'].box((-9.4*k, 0, za-1.0*k), (.8*k, 6.0*k, 3.0*k), .1*k)                    # rear vent plate
        for z in (-2.0, -1.0, 0.0):
            gg['dark'].box((-9.82*k, 0, za+z*k), (.1*k, 4.8*k, .35*k))
        # Hoops over the MIRV from rail to rail: the frame holds it from above.
        R = 6.5*k
        for x in (12.0, 17.5, 23.0):
            for i in range(9):
                a = math.pi*i/8
                gg['steel'].box((x*k, R*math.cos(a), za+.4*k+R*math.sin(a)), (1.0*k, 2.7*k, .5*k), .05*k,
                                rot=Matrix.Rotation(a+math.pi/2, 4, 'X'))
            for sy in (1, -1):
                gg['steel'].box((x*k, sy*6.8*k, za+.4*k), (1.0*k, 1.0*k, .8*k), .05*k)        # hoop feet
        _missile(gg.sub('missile 1', (7.0*k, 0, za+.4*k), .1*r), f, 7.0*k, 0, za+.4*k, 21.0*k, 3.6*k, f.near,
                 roll=math.pi/4)                                                               # the MIRV
    return (1.0*k, 0.0, zt), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'muzzles': [(28.0*k, 0, za+.4*k)], 'za': za}


def _dome_section(hw, z0, h, p=.5, n=16, yc=0.0):
    """A YZ loop rounded over the top: a superellipse arch of half-width hw and
    height h on a flat base (p 1 is a diamond, .5 a half-ellipse, lower squarer)."""
    pts = []
    for i in range(n+1):
        a = math.pi*i/n
        c, sn = math.cos(a), math.sin(a)
        pts.append((yc+hw*math.copysign(abs(c)**p, c), z0+h*abs(sn)**p))
    return pts


def _table_at(table, x):
    """(half_width, height) of a plan table [(x, w, h), ...] at x, interpolated."""
    if x <= table[0][0]:
        return table[0][1:]
    for (xa, wa, ha), (xb, wb, hb) in zip(table, table[1:]):
        if xa <= x <= xb:
            t = (x-xa)/((xb-xa) or 1)
            return wa+(wb-wa)*t, ha+(hb-ha)*t
    return table[-1][1:]


def _dimple(cut, x, y, z_surface, k, r=.55, depth=.3):
    """A round dimple pressed into a surface at z_surface: a sphere carved out
    so the hole opens about r*0.9 across and `depth` deep (sprite px x k)."""
    n = 8
    prof = [(r*k*math.sin(math.pi*i/n), -r*k*math.cos(math.pi*i/n)) for i in range(n+1)]
    cut.lathe(prof, 16, (x, y, z_surface+r*k-depth*k), 'Z')


def _dome_loft(m, table, z0, k, p=.5, yc=0.0):
    """Loft a rounded house from a plan table [(x, half_width, height), ...] in sprite px."""
    m.loft_x([(x*k, _dome_section(w*k, z0, h*k, p, yc=yc*k)) for x, w, h in table])


def _dome_z(y, hw, h, p=.5):
    """Height of a _dome_section's surface at y off its centreline."""
    t = min(abs(y)/hw, 1.0)
    c = t**(1/p)
    return h*max(1-c*c, 0)**(p/2)


def _plan_loft(m, table, z0, k, top, bot=.1, tumble=.4, yc=0.0):
    """Loft a house from a plan table [(x, half_width, height), ...] in sprite px."""
    st = []
    for x, w, h in table:
        loop = [(yc*k+yy, zz) for yy, zz in section(w*k, z0, z0+h*k, min(top, .45*h, .45*w)*k, bot*k,
                                                     min(tumble, .3*w)*k)]
        st.append((x*k, loop))
    m.loft_x(st)


def _house_heavyac2(g, f, s, r, z0):
    """heavy_autocannon_turret_base.png: an egg-shaped house, rounded at the
    rear, with three rounded sleeve housings merged into its front. Each cannon
    lies in its own finger: the sleeve housing forward and the raised fairing
    carrying its shaft back over the roof. The fingers are the elevating mass
    (owner, 2026-10-20: "push UP the individual fingers"): they hinge at the
    fairings' aft ends and lift out of troughs pressed into the roof, the house
    ending in a front face under them. Vent streaks cross the fairings; a darker
    rim band round the house."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    H = 3.4
    plan = [(-11.2, 5.0, H-1.4), (-10.8, 6.6, H-.9), (-10.0, 8.0, H-.5), (-9.0, 9.0, H-.3), (-8.0, 9.8, H-.1),
            (-7.0, 10.6, H), (1.5, 10.9, H), (2.5, 10.4, H), (3.5, 9.6, H-.1), (4.6, 8.8, H-.25),
            (5.6, 8.0, H-.45), (6.2, 7.2, H-.7)]
    shell = g['paint#shell']
    _plan_loft(shell, plan, z0+.7*k, k, 1.0, .1, .5)
    _plan_loft(g['steel'], [(x, w-.25, .9) for x, w, h in plan], z0, k, .2, .05, 0)     # rim band
    zt = z0+(H+.7)*k
    za = z0+3.4*k
    rb = 1.7                                                                          # rib radius
    xh = -7.6                                                                         # the fingers' hinge
    # The house under the fingers: a trough pressed into the roof under each, its floor
    # dark, and the front cut back to a face behind the sleeve housings.
    for y in (5.0, 0.0, -5.0):
        shell.cut().box(((xh-1.6+7.0)/2*k, y*k, za+.5*k), ((7.0-xh+1.6)*k, (2*rb+.5)*k, (2*rb+.4)*k))
        g['dark'].box(((xh-1.4+7.0)/2*k, y*k, za-(rb-.2)*k), ((7.0-xh+1.2)*k, (2*rb+.4)*k, .1*k))
    shell.cut().box((11.0*k, 0, z0+3*k), (8.6*k, 30*k, 8*k))                         # front face at x 2.7
    g['steel'].box((2.75*k, 0, z0+1.5*k), (.3*k, 16.0*k, 1.6*k), .08*k)               # its sill

    def gun_parts(gg, za):
        sleeves, ribs = gg['plate#sleeves'], gg['plate#ribs']
        for y in (5.0, 0.0, -5.0):
            # Sleeve housing: a rounded lobe forward, the rib's front end, bored for the
            # cannon (the sprite's dark sleeve mouths, rows 11-13).
            st = []
            xf = 9.6 if y == 0 else 9.2
            for x, w, h in ((3.0, 2.3, 4.4), (6.5, 2.3, 4.3), (8.6, 2.1, 4.1), (xf, 1.6, 3.8)):
                st.append((x*k, [(y*k+yy, zz) for yy, zz in section(w*k, z0+1.0*k, z0+(.7+h)*k, 1.0*k, .1*k,
                                                                     .9*k)]))
            sleeves.loft_x(st)
            sleeves.cut().cyl(((xf-.6)*k, y*k, za), 1.1*k, 1.6*k, 'X', n=24)
            gg['dark'].cyl(((xf-1.35)*k, y*k, za), 1.12*k, .1*k, 'X', n=24)              # the bore's back
            # Rib: the cannon's shaft carried back over the roof to the hinge.
            x0, x1 = xh-.6, 3.5
            ribs.lathe([(0, 0), (1.2*k, .2*k), (rb*k, .9*k), (rb*k, (x1-x0)*k), (0, (x1-x0)*k)], 24,
                       (x0*k, y*k, za), 'X')
            for x in (-2.5, -4.0, -5.5):          # vents: slots cut across the rib, dark at the bottom
                ribs.cut().box((x*k, y*k, za+rb*k), (.45*k, 1.9*k, 1.0*k), .04*k)
                gg['dark'].box((x*k, y*k, za+(rb-.48)*k), (.45*k, 1.6*k, .06*k))
            # The hinge knuckle at the finger's root, on the shared elevation axis.
            gg['steel'].cyl((xh*k, y*k, za), 1.25*k, (2*rb+.2)*k, 'Y', n=24, bevel=.1*k)
        gg['metal'].cyl((xh*k, 0, za), .6*k, 15.4*k, 'Y', n=20)                         # the hinge shaft
    for sy in (1, -1):                       # its bearings in the house, either side
        g['steel'].cyl((xh*k, sy*7.75*k, za), 1.3*k, .9*k, 'Y', n=24, bevel=.1*k)
    # Rear lugs: the two light pixels between dark ones at the tail (row 32,
    # a pixel and a half either side of the centre rib's line), eyes aft.
    for sy in (1, -1):
        g['steel'].box((-11.3*k, sy*2.5*k, z0+1.6*k), (.9*k, .6*k, 1.1*k), .1*k)
        f.eye(g['steel'], (-11.75*k, sy*2.5*k, z0+1.7*k), 'X')
    return (xh*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                             'barrel_scale': .72, 'muzzle_ring': True, 'barrel_root': 4.0,
                             'elevation_limits': (0, 30)}


def _house_flak2(g, f, s, r, z0):
    """flak_cannon_turret_base.png: two ribbed brown pods either side of an
    open recoil well, wrapped on their outer faces by a maroon cradle that
    closes round the rear in a solid rounded bowl; in the gun group the maroon
    breech core, standing well above the pods, with a lit yellow aperture cut
    into its front third, and two dark forked prongs flaring forward."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    for sy in (1, -1):
        st = []
        for x, w, yc, h in ((-9.4, 2.4, 7.4, 2.8), (-7.0, 2.7, 7.4, 3.2), (-3.0, 2.8, 7.3, 3.6),
                            (1.5, 2.8, 7.2, 3.9), (4.5, 2.6, 7.1, 3.9), (6.6, 2.1, 7.0, 3.4)):
            loop = [(sy*yc*k+yy, zz) for yy, zz in section(w*k, z0, z0+h*k, .9*k, .15*k, .5*k)]
            st.append((x*k, loop if sy > 0 else loop[::-1]))
        pod = g[f'paint#pod{sy}']
        pod.loft_x(st)
        for x in (-6.5, -4.5, -2.5, -.5, 1.5):                       # rib slots, cut into the pod's crown
            ztop = z0+(3.2+(3.9-3.2)*min(1, (x+7.0)/8.5))*k
            pod.cut().box((x*k, sy*7.6*k, ztop), (.45*k, 2.8*k, 1.0*k), .03*k)
            g['dark'].box((x*k, sy*7.6*k, ztop-.46*k), (.45*k, 2.8*k, .06*k))
            g['steel'].box(((x-.3)*k, sy*7.6*k, ztop-.15*k), (.12*k, 2.8*k, .12*k))    # louvre blade
        g['dark'].box((-1.0*k, sy*4.55*k, z0+1.8*k), (14.0*k, .2*k, 3.0*k))               # dark inner face
        g['steel'].cyl((-7.8*k, sy*6.6*k, z0+3.2*k), .55*k, .35*k, 'Z', n=16, bevel=.06*k)  # filler cap
        # Maroon cradle up the pod's outer face, grey rail outboard.
        g['accent'].box((-3.0*k, sy*10.3*k, z0+1.3*k), (13.6*k, 1.0*k, 2.6*k), .15*k)
        g['steel'].box((-4.0*k, sy*11.0*k, z0+1.0*k), (10.0*k, .5*k, 1.6*k), .1*k)
        f.bolt_row(g['steel'], (-8.0*k, sy*10.82*k, z0+2.1*k), (2.5*k, sy*10.82*k, z0+2.1*k), axis='Y')
    # The bowl: the cradle closing round the rear, solid across the well.
    _plan_loft(g['accent'], [(-12.6, 3.6, 1.8), (-12.0, 5.6, 2.3), (-11.0, 7.8, 2.6), (-10.0, 9.6, 2.7),
                             (-9.0, 10.8, 2.7)], z0, k, .6, .1, .4)
    _plan_loft(g['steel'], [(-12.9, 2.6, 1.0), (-12.4, 4.2, 1.1)], z0, k, .2, .05, .1)
    f.weld(g['steel'], (-9.4*k, -6.0*k, z0+2.7*k), (-9.4*k, 6.0*k, z0+2.7*k))
    za = z0+3.0*k

    def gun_parts(gg, za):
        zt = za+3.6*k                                    # core top, well above the pods
        st = []
        for x, w, h in ((-1.0, 4.0, zt-za+1.0), (0.0, 4.6, zt-za+1.6), (8.4, 4.6, zt-za+1.6), (9.2, 4.0, zt-za+1.0)):
            st.append((x*k, section(w*k, za-1.6*k, za-1.6*k+h*k, .9*k, .2*k, .6*k)))
        core = gg['accent#core']
        core.loft_x(st)
        # The aperture, as the sprite draws it across the core's top: a narrow
        # yellow slot near the front (rows 12-13, two pixels wide), a broad one
        # behind it (row 15, four wide) and a dark slot aft of that (row 16).
        # Each is cut into the core and lit from inside; the front one runs out
        # through the face.
        for x0, x1, hw, lit in ((7.4, 9.4, 1.1, True), (5.1, 5.9, 2.1, True), (4.1, 4.85, 1.0, False)):
            core.cut().box(((x0+x1)/2*k, 0, zt), ((x1-x0)*k, 2*hw*k, 1.6*k), .04*k)
            x1 = min(x1, 9.1)
            gg['glow' if lit else 'dark'].box(((x0+x1)/2*k, 0, zt-.72*k), ((x1-x0)*k, 2*hw*k, .1*k))
        core.cut().box((-.95*k, 0, za+.6*k), (.6*k, 3.6*k, 2.4*k), .04*k)                  # breech opening
        gg['dark'].box((-.6*k, 0, za+.6*k), (.1*k, 3.6*k, 2.4*k))
        for sy in (1, -1):
            gg['steel'].cyl((3.5*k, sy*4.85*k, za), .9*k, .5*k, 'Y', n=16, bevel=.08*k)   # trunnions
        gg['plate'].cyl((18.6*k, 0, za), 1.75*k, 2.0*k, 'X', n=24, bevel=.12*k)          # muzzle collar
    return (3.5*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True}


def _house_vulcan(g, f, s, r, z0):
    """vulcan_cannon_turret_base.png: a rounded brown house - the sprite's light
    crown is the light falling on its dome - rounding down at the front into
    the two barrel ports, three dimples pressed into either side of the crown,
    a recessed vent pocket either side of the crown aft,
    and a grey rear block with two tan tines either side of a notch. The
    barrels run back into the hull, so recoil slides them in."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    H = 2.8
    dome = [(-3.1, 3.4, H*.72), (-2.8, 4.4, H*.88), (-2.2, 4.9, H*.97), (-1.2, 5.0, H),
            (1.2, 5.0, H), (2.2, 4.7, H*.95), (3.1, 4.2, H*.86), (3.9, 3.6, H*.74),
            (4.6, 2.9, H*.58), (5.1, 2.2, H*.42), (5.4, 1.4, H*.24)]
    shell = g['paint#dome']
    _dome_loft(shell, dome, z0, k, .5)

    def zs(x, y):                                       # the dome's surface
        w, h = _table_at(dome, x)
        return z0+_dome_z(y, w, h)*k
    # Dimples: the sprite's dark pixel pairs down the lit crown (rows 9, 11 and
    # 13, a pixel and a half either side of the centreline), pressed into it.
    for x in (2.5, .5, -1.5):
        for sy in (1, -1):
            _dimple(shell.cut(), x*k, sy*1.5*k, zs(x, 1.5), k, r=.5, depth=.28)
    for sy in (1, -1):
        # Vent pocket: the dark pixels low on each flank aft (rows 13-14), sunk
        # into the dome, slats across it.
        xp, yp = -1.9*k, sy*3.3*k
        top = zs(-1.9, 3.3)
        shell.cut().box((xp, yp, top), (2.2*k, 1.3*k, 1.0*k), .1*k)
        g['dark'].box((xp, yp, top-.42*k), (2.1*k, 1.2*k, .1*k))
        for j in range(3):
            g['steel'].box((xp+(-.6+.6*j)*k, yp, top-.22*k), (.14*k, 1.2*k, .1*k))
    # Rear block: grey, full width of the sprite's tail, tan tines either side of a notch.
    g['plate'].box((-4.4*k, 0, z0+.95*k), (3.0*k, 6.0*k, 1.9*k), .2*k)
    for sy in (1, -1):
        g['paint'].box((-4.5*k, sy*1.5*k, z0+1.0*k), (3.2*k, 1.0*k, 2.0*k), .12*k)
    g['dark'].box((-4.45*k, 0, z0+1.2*k), (1.2*k, 1.8*k, 1.6*k))
    g['plate'].box((-5.6*k, 0, z0+.8*k), (.9*k, 2.2*k, 1.4*k), .1*k)
    za = z0+1.3*k
    for b in sp['barrels']:                                                                 # barrel ports
        g['dark'].cyl((4.75*k, b['y']*k, za), .85*k, .9*k, 'X', n=18, bevel=.08*k)

    def gun_parts(gg, za):
        pass
    return (4.5*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'barrel_scale': .78, 'muzzle_ring': True, 'barrel_root': 3.0}


def _house_mark9(g, f, s, r, z0):
    """heavy_assault_cannon_turret_base.png: a broad copper house; copper side
    blocks forward and rounded mauve lobes aft down both flanks; a darker seam
    down the roof's centre with red marks forward; a cream collar arching under
    the barrel roots; a recessed ladder well aft of it; and a plain post at each
    front corner."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    H = 8.0
    body = g['paint#body']
    plan = [(-17.5, 6.0, H-3.0), (-16.5, 9.0, H-2.0), (-15.0, 12.0, H-1.2), (-13.0, 13.4, H-.4),
            (5.0, 13.4, H), (7.0, 12.4, H-.6), (8.0, 11.0, H-1.2)]
    _plan_loft(body, plan, z0, k, 1.3, .1, .8)

    def roof(x):
        return z0+_table_at(plan, x)[1]*k
    zt = z0+H*k
    for sy in (1, -1):
        st = []                                                                           # copper side block
        for x, w, h in ((1.6, 1.6, H-2.2), (2.6, 2.6, H-1.2), (8.6, 2.6, H-1.4), (9.8, 1.6, H-2.4)):
            loop = [(sy*17.4*k+yy, zz) for yy, zz in section((w+.8)*k, z0, z0+h*k, .9*k, .1*k, .7*k)]
            st.append((x*k, loop if sy > 0 else loop[::-1]))
        g['paint'].loft_x(st)
        st = []                                                                           # mauve rear lobe
        for x, w, h in ((-14.2, 1.4, H-3.4), (-12.5, 2.6, H-2.4), (-7.0, 3.2, H-1.8), (-1.5, 3.0, H-2.0),
                        (.8, 1.8, H-2.8)):
            loop = [(sy*17.8*k+yy, zz) for yy, zz in section((w+.9)*k, z0, z0+h*k, 1.2*k, .1*k, 1.0*k)]
            st.append((x*k, loop if sy > 0 else loop[::-1]))
        lobe = g[f'accent#lobe{sy}']
        lobe.loft_x(st)
        g['dark'].box((-3.0*k, sy*13.9*k, z0+(H-2.0)/2*k), (24.0*k, 1.6*k, (H-2.0)*k), .2*k)  # web to the hull
        for x in (-4.5, -9.5):                                       # plate overlaps: seams across the lobe
            lobe.cut().box((x*k, sy*17.8*k, z0+(H-1.8)*k), (.4*k, 8.0*k, .8*k), .03*k)
        g['mark'].box((6.6*k, sy*9.0*k, zt+.02*k), (1.0*k, 2.2*k, .08*k))                 # red marks
        f.bolt_row(g['steel'], (-13.0*k, sy*12.6*k, zt-.3*k), (4.0*k, sy*12.6*k, zt-.3*k))
        # Corner post: a plain upright stub on a footing.
        g['steel'].box((13.0*k, sy*15.6*k, z0+1.4*k), (3.0*k, 2.4*k, 2.8*k), .3*k)
        post = g[f'steel#post{sy}']
        post.cyl((14.0*k, sy*15.6*k, z0+(H-2.0)/2*k+1.0*k), 2.0*k, (H-2.0)*k, 'Z', n=20, bevel=.2*k)
        post.cut().cyl((14.0*k, sy*15.6*k, z0+H*k), 1.2*k, 1.2*k, 'Z', n=16)                # its socket
        g['dark'].cyl((14.0*k, sy*15.6*k, z0+(H-.56)*k), 1.2*k, .06*k, 'Z', n=16)
    g['accent'].box((1.0*k, 0, zt+.08*k), (12.0*k, 4.8*k, .2*k), .06*k)                  # centre seam
    # Cream collar: arched under the barrel roots.
    g['plate'].box((-3.0*k, 0, zt+.5*k), (3.2*k, 17.6*k, 1.0*k), .4*k)
    g['plate'].box((-3.0*k, 0, zt+1.1*k), (2.4*k, 10.0*k, .6*k), .3*k)
    # Ladder well: a recess sunk into the roof, rungs across it.
    body.cut().box((-10.4*k, 0, zt), (9.6*k, 7.4*k, 2.4*k), .06*k)
    g['dark'].box((-10.4*k, 0, zt-1.16*k), (9.6*k, 7.4*k, .06*k))
    xs = [-14.2+1.9*i for i in range(5)]
    for x in xs:                                      # rungs, each just under the roof it crosses
        g['steel'].box((x*k, 0, roof(x)-.3*k), (.5*k, 7.6*k, .2*k))
    for sy in (1, -1):                                # the rails, following the roof down aft
        for a, b in zip([-15.2]+xs[1:], xs[1:]+[-5.6]):
            za_, zb_ = roof(a)+.05*k, roof(b)+.05*k
            g['steel'].box(((a+b)/2*k, sy*3.9*k, (za_+zb_)/2), ((b-a)*k+.05*k, .4*k, .4*k),
                           rot=Matrix.Rotation(-math.atan2(zb_-za_, (b-a)*k), 4, 'Y'))
    za = z0+4.6*k

    def gun_parts(gg, za):
        gg['steel'].box((8.6*k, 0, za), (2.0*k, 22.0*k, 5.0*k), .3*k)                      # mantlet
    return (6.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'barrel_scale': 1.0, 'muzzle_ring': True}


def _house_hellbore(g, f, s, r, z0):
    """hellbore_cannon_turret_base.png: an asymmetric heavy house - a square
    main block with a big round breech hatch (bright centre), a slotted gun
    box forward, a tall dark box down the port side, and an angled strut aft
    to starboard; the huge barrel runs in a cage of bars with a muzzle brake."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    H = 6.6
    g['paint'].loft_x([(-9.0*k, [(y+(-1.5)*k, z) for y, z in section(11.0*k, z0, z0+(H-.8)*k, 1.2*k, .1*k, .6*k)]),
                       (-6.0*k, [(y+(-1.5)*k, z) for y, z in section(11.5*k, z0, z0+H*k, 1.4*k, .1*k, .7*k)]),
                       (9.0*k, [(y+(-1.5)*k, z) for y, z in section(11.5*k, z0, z0+H*k, 1.4*k, .1*k, .7*k)]),
                       (10.0*k, [(y+(-1.0)*k, z) for y, z in section(10.6*k, z0, z0+(H-.6)*k, 1.2*k, .1*k, .6*k)])])
    zt = z0+H*k
    # Breech hatch: a big raised ring with a bright domed centre.
    g['plate'].lathe([(6.4*k, 0), (6.4*k, .6*k), (5.6*k, 1.0*k), (4.0*k, 1.0*k), (4.0*k, .8*k), (0, .8*k)], 40,
                     (1.0*k, 0, zt))
    g['accent'].lathe([(2.4*k, 0), (2.4*k, 1.1*k), (1.6*k, 1.6*k), (0, 1.8*k)], 32, (1.0*k, 0, zt+.6*k))
    f.bolts(g['steel'], (1.0*k, 0, 0), 5.2*k, zt+1.0*k, spacing=.9)
    # Gun box forward with slots cut across its top.
    gbox = g['paint#gunbox']
    gbox.box((11.8*k, 0, z0+(H-1.0)/2*k), (4.4*k, 16.6*k, (H-1.0)*k), .4*k)
    for y in (-4.0, -1.5, 1.5, 4.0):
        gbox.cut().box((12.4*k, y*k, z0+(H-1.0)*k), (2.6*k, 1.2*k, 1.0*k), .04*k)
        g['dark'].box((12.4*k, y*k, z0+(H-1.46)*k), (2.6*k, 1.2*k, .06*k))
    # Dark box down the port side; angled strut aft to starboard; rear housing.
    g['accent2'].box((1.0*k, 14.0*k, z0+(H+1.0)/2*k), (21.0*k, 7.0*k, (H+1.0)*k), .5*k)
    for x in (-6.0, -2.0, 2.0, 6.0):
        g['steel'].box((x*k, 17.6*k, z0+(H-.5)*k), (2.2*k, .2*k, 1.2*k))
    g['paint'].box((-13.0*k, -13.0*k, z0+2.0*k), (12.0*k, 3.2*k, 4.0*k), .4*k,
                   rot=Matrix.Rotation(.35, 4, 'Z'))
    rear = g['paint#rear']
    rear.box((-13.0*k, 0, z0+2.4*k), (8.0*k, 16.0*k, 4.8*k), .5*k)
    for y in (-4.5, -1.5, 1.5, 4.5):                                 # vents cut into the rear face
        rear.cut().box((-17.0*k, y*k, z0+2.4*k), (1.0*k, 1.6*k, 3.0*k), .04*k)
        g['dark'].box((-16.56*k, y*k, z0+2.4*k), (.06*k, 1.6*k, 3.0*k))
    f.weld(g['steel'], (-6.0*k, -10.0*k, zt), (9.0*k, -10.0*k, zt))
    f.hatch(g, (-4.5*k, -6.0*k, zt), size=min(.9, .3*r/f.u))
    za = z0+3.8*k

    def gun_parts(gg, za):
        # The barrel is built here, not lathed from the sprite's width: the
        # sprite's broad middle is a cage of bars round a slimmer tube.
        rg = gg.sub('recoil 1', (-9.0*k, 0, za), .1*r)
        rg['steel'].lathe([(0, 0), (4.2*k, 0), (4.2*k, 19.0*k), (3.6*k, 20.0*k), (3.6*k, 36.0*k), (2.4*k, 36.0*k),
                           (2.4*k, 35.0*k), (0, 35.0*k)], 32, (-9.0*k, 0, za), 'X')
        # Muzzle brake: a heavy block on the barrel's end, three ports a side.
        brake = rg['metal#brake']
        brake.box((30.0*k, 0, za), (6.4*k, 9.6*k, 7.4*k), .6*k)
        brake.cut().cyl((33.0*k, 0, za), 2.4*k, 1.4*k, 'X', n=24)                            # the bore
        rg['dark'].cyl((32.36*k, 0, za), 2.4*k, .06*k, 'X', n=24)
        for i in range(3):                                                      # ports through its sides
            brake.cut().box(((28.2+1.6*i)*k, 0, za), (.9*k, 10.4*k, 3.6*k), .04*k)
        rg['dark'].box((29.8*k, 0, za), (4.6*k, 2.2*k, 3.6*k))                                 # bore wall inside
        for a in range(8):                                                                  # cage bars
            ang = 2*math.pi*(a+.5)/8
            rg['metal'].box((17.0*k, 5.0*k*math.cos(ang), za+5.0*k*math.sin(ang)), (14.0*k, .8*k, .8*k), .1*k,
                            rot=Matrix.Rotation(ang, 4, 'X'))
        for x in (10.5, 17.0, 23.5):
            rg['metal'].lathe([(3.7*k, -.5*k), (5.6*k, -.5*k), (5.6*k, .5*k), (3.7*k, .5*k)], 32, (x*k, 0, za), 'X',
                              loop=True)
        for sy in (1, -1):
            rg['accent'].box((14.0*k, sy*3.9*k, za+2.6*k), (8.0*k, .5*k, .5*k))                  # yellow rails
        gg['steel'].box((9.8*k, 0, za), (1.6*k, 13.0*k, 9.0*k), .3*k)                         # mantlet
    return (4.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'muzzles': [(33.2*k, 0, za)], 'skip_barrels': True}

def _house_hephaestus(g, f, s, r, z0):
    """hephaestus_turret_base.png: a brass core with raised ribbed bands, a
    brass arched frame forward with two dark arches the barrels run through
    and a centre post, grey machinery stacks along both flanks, and two grey
    ammunition drums aft, ends up."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    H = 6.0
    _plan_loft(g['paint'], [(-16.0, 7.0, H-2.6), (-15.0, 9.0, H-2.0), (-12.0, 10.4, H-1.4), (-9.0, 10.4, H-.6),
                            (-6.0, 10.4, H), (5.0, 10.4, H), (9.6, 9.6, H-.4)], z0, k, 1.0, .1, .6)
    zt = z0+H*k
    for x in (3.5, .5, -2.5, -5.5):                                                        # ribbed bands
        g['plate'].box((x*k, 0, zt+.25*k), (1.0*k, 19.0*k, .5*k), .15*k)
    g['accent'].box((-1.0*k, 0, zt+.55*k), (8.0*k, 6.0*k, .5*k), .2*k)
    # Arched frame: two arches with a centre post and side jambs.
    for y in (-10.0, 0.0, 10.0):          # (jambs stop just under the bar's top: no coplanar faces)
        g['paint'].box((12.5*k, y*k, z0+(H+.9)/2*k), (5.9*k, 2.0*k if y else 1.6*k, (H+.9)*k), .3*k)
    g['paint'].box((12.5*k, 0, z0+(H+.4)*k), (6.0*k, 21.0*k, 1.2*k), .3*k)
    g['paint'].box((15.0*k, 0, z0+(H+.9)*k), (1.2*k, 21.0*k, .6*k), .1*k)
    for y in (4.6, -4.6):
        g['dark'].box((12.5*k, y*k, z0+(H-1.0)/2*k+.6*k), (5.8*k, 7.4*k, (H-.6)*k))
    for sy in (1, -1):
        # Machinery stacks on the flanks: stepped blocks, pipes and a valve.
        for x, h in ((-5.5, H-1.0), (-1.0, H-.4), (3.5, H-1.4), (8.0, H-1.8)):
            g['steel'].box((x*k, sy*13.4*k, z0+h/2*k), (4.2*k, 6.6*k, h*k), .3*k)
        g['metal'].cyl((-.5*k, sy*15.6*k, z0+3.0*k), .6*k, 12.0*k, 'X', n=14, bevel=.06*k)
        g['metal'].cyl((8.8*k, sy*11.6*k, z0+3.5*k), 1.1*k, 6.0*k, 'Z', n=16, bevel=.1*k)
        # Ammunition drums aft, end-up.
        g['steel'].cyl((-12.5*k, sy*5.0*k, z0+3.3*k), 3.0*k, 6.6*k, 'Z', n=32, bevel=.25*k)
        cap = g[f'metal#cap{sy}']
        cap.cyl((-12.5*k, sy*5.0*k, z0+6.65*k), 2.2*k, .3*k, 'Z', n=28, bevel=.05*k)
        cap.cut().cyl((-12.5*k, sy*5.0*k, z0+6.8*k), .8*k, .6*k, 'Z', n=16)                # feed socket
        g['dark'].cyl((-12.5*k, sy*5.0*k, z0+6.56*k), .8*k, .06*k, 'Z', n=16)
        for x in (-10.0, -14.0):                                                            # rear stacks
            g['steel'].box((x*k, sy*13.2*k, z0+2.4*k), (3.6*k, 4.4*k, 4.8*k), .3*k)
        f.bolt_row(g['steel'], (-8.0*k, sy*9.9*k, zt), (6.0*k, sy*9.9*k, zt))
    za = z0+3.0*k

    def gun_parts(gg, za):
        gg['steel'].box((10.0*k, 0, za), (1.6*k, 12.0*k, 3.4*k), .2*k)                        # cradle
        for b in sp['barrels']:
            gg['steel'].cyl((18.5*k, b['y']*k, za), 2.0*k, 4.0*k, 'X', n=24, bevel=.15*k)       # muzzle housings
    return (8.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True}

def _house_devastator(g, f, s, r, z0):
    """devastator_turret_base.png: a tall lavender-grey house rounded over in
    both directions, three raised fairings on the barrel lines with dark
    channels between, toothed blue-grey brackets low on both flanks, two
    rounded rear lobes edged in orange with a small dark centre lobe between
    them, and a blue-grey skirt; the three barrels carry orange coil rings and
    forked muzzle clamps."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    H = 7.0
    P = .4
    plan = [(-11.8, 9.6, H*.7), (-11.2, 11.6, H*.86), (-10.2, 12.6, H*.95), (-8.6, 13.0, H), (5.0, 13.0, H),
            (6.4, 12.8, H*.95), (7.6, 12.2, H*.82), (8.6, 11.2, H*.64), (9.4, 9.8, H*.42),
            (9.9, 8.0, H*.2)]
    zb = z0+1.4*k
    body = g['paint#body']
    _dome_loft(body, plan, zb, k, P)
    _plan_loft(g['accent'], [(x, w+.15, 1.6) for x, w, h in plan], z0, k, .3, .05, .1)     # skirt
    fair = g['plate#fairings']
    for y in (9.0, 0.0, -9.0):                                                              # fairings
        zs = zb+_dome_z(y, 13.0, H, P)*k
        fair.lathe([(0, 0), (1.4*k, .2*k), (1.9*k, 1.2*k), (1.9*k, 13.0*k), (1.4*k, 14.0*k), (0, 14.4*k)],
                   24, (-10.0*k, y*k, zs-1.0*k), 'X')
        fair.cut().box((-3.2*k, y*k, zs+.9*k), (11.6*k, .8*k, .5*k), .03*k)                # its top seam
        g['dark'].box((-3.2*k, y*k, zs+.66*k), (11.6*k, .8*k, .04*k))
    for y in (4.5, -4.5):                                     # channels: sunk into the house between them
        zs = zb+_dome_z(y, 13.0, H, P)*k
        body.cut().box((-3.0*k, y*k, zs), (13.0*k, 3.4*k, 1.6*k), .1*k)
        g['dark'].box((-3.0*k, y*k, zs-.74*k), (13.0*k, 3.4*k, .06*k))
    for sy in (1, -1):
        # Toothed bracket, low on the flank, webbed into the hull.
        br = g[f'accent#bracket{sy}']
        br.box((-3.8*k, sy*13.8*k, z0+1.8*k), (8.6*k, 3.0*k, 3.6*k), .3*k)
        br.box((-3.8*k, sy*15.8*k, z0+1.4*k), (7.6*k, 1.8*k, 2.8*k), .3*k)
        for x in (-6.8, -4.8, -2.8, -.8):                                   # the teeth: notches in its face
            br.cut().box((x*k, sy*16.8*k, z0+1.4*k), (.6*k, .8*k, 2.0*k), .03*k)
        g['mark'].box((-5.2*k, sy*14.8*k, z0+3.61*k), (2.0*k, 1.6*k, .04*k))
        # Rear lobe, rounded over.
        _dome_loft(g['paint'], [(-21.6, 2.2, H*.4), (-20.6, 3.8, H*.62), (-18.6, 4.6, H*.76), (-15.0, 4.7, H*.82),
                                (-11.0, 4.5, H*.86)], z0, k, .5, yc=sy*7.0)
        g['mark'].box((-15.5*k, sy*11.3*k, z0+(H*.4)*k), (6.0*k, .6*k, 1.4*k), .1*k)        # orange edge
    _dome_loft(g['accent'], [(-15.4, 1.0, H*.3), (-14.6, 2.4, H*.5), (-12.0, 2.8, H*.6)], z0, k, .5)  # centre lobe
    za = z0+5.0*k

    def gun_parts(gg, za):
        for b in sp['barrels']:
            y = b['y']*k
            for x in (10.6, 12.2, 13.8, 15.4):                                                # coil rings
                gg['mark'].cyl((x*k, y, za), 2.7*k, 1.1*k, 'X', n=24, bevel=.1*k)
            gg['steel'].cyl((18.6*k, y, za), 2.0*k, 1.4*k, 'X', n=24, bevel=.1*k)           # neck
            # Muzzle collar: a short round sleeve, its bore open at the front.
            collar = gg[f'steel#collar{b["y"]}']
            collar.lathe([(1.7*k, 0), (3.5*k, 0), (3.8*k, .5*k), (3.8*k, 3.1*k), (3.4*k, 3.5*k),
                          (1.7*k, 3.5*k)], 32, (19.3*k, y, za), 'X', loop=True)
            gg['dark'].cyl((21.4*k, y, za), 1.72*k, .12*k, 'X', n=24)                      # bore
            for a in range(4):                                                  # gas ports, cut into it
                ang = a*math.pi/2+math.pi/4
                collar.cut().box((21.2*k, y+3.8*k*math.cos(ang), za+3.8*k*math.sin(ang)), (1.6*k, .5*k, .9*k),
                                 .03*k, rot=Matrix.Rotation(ang-math.pi/2, 4, 'X'))
    return (6.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'barrel_root': 3.0, 'barrel_end': 19.6}


def _house_heavymauler(g, f, s, r, z0):
    """assault_chaingun_turret_base.png (the heavy mauler's art): an olive-grey
    egg-shaped house, a cream breech block across its front between dark
    cheeks, small lugs at the front corners, raised dark-olive housings down
    both flanks, and the fixed main barrel with its slotted muzzle brake; the
    gun group carries two jacketed side tubes level with the main barrel and
    the ribbed recoil spring lying in a well along the rear deck."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    H = 5.0
    plan = [(-11.8, 5.6, H-2.0), (-11.0, 7.2, H-1.4), (-10.0, 8.4, H-.9), (-9.0, 9.4, H-.5), (-7.5, 10.2, H-.2),
            (-5.5, 10.6, H), (2.0, 11.0, H), (3.5, 10.6, H-.2), (4.6, 9.8, H-.5), (5.2, 8.6, H-.9)]
    body = g['paint#body']
    _plan_loft(body, plan, z0, k, 1.2, .1, .8)
    zt = z0+H*k
    g['plate'].box((3.0*k, 0, zt+.3*k), (4.0*k, 9.4*k, .8*k), .3*k)                      # cream breech block
    for sy in (1, -1):
        body.cut().box((3.0*k, sy*6.6*k, zt), (3.6*k, 2.4*k, 1.0*k), .06*k)              # dark cheeks: recesses
        g['dark'].box((3.0*k, sy*6.6*k, zt-.46*k), (3.6*k, 2.4*k, .06*k))
        g['steel'].box((6.6*k, sy*6.6*k, z0+(H-1.6)*k), (3.0*k, 2.4*k, 2.0*k), .2*k)       # front lugs
        # Flank housing: a raised dark-olive block down the flank.
        st = []
        for x, w, h in ((-10.0, 1.0, H-.6), (-8.5, 1.8, H+.4), (-1.0, 1.9, H+.5), (.8, 1.2, H)):
            loop = [(sy*8.6*k+yy, zz) for yy, zz in section(w*k, z0+1.0*k, z0+h*k, .6*k, .1*k, .5*k)]
            st.append((x*k, loop if sy > 0 else loop[::-1]))
        hous = g[f'accent#flank{sy}']
        hous.loft_x(st)
        for x in (-7.0, -5.0, -3.0):                                      # slots across its top
            hous.cut().box((x*k, sy*8.6*k, z0+(H+.5)*k), (.5*k, 2.6*k, .8*k), .03*k)
            g['dark'].box((x*k, sy*8.6*k, z0+(H+.12)*k), (.5*k, 2.4*k, .06*k))
    body.cut().box((-4.0*k, 0, zt), (9.6*k, 5.2*k, 1.6*k), .08*k)                     # the spring's well
    g['dark'].box((-4.0*k, 0, zt-.76*k), (9.6*k, 5.2*k, .06*k))
    zb = z0+3.0*k
    g['steel'].box((6.6*k, 0, zb), (3.2*k, 6.8*k, 3.6*k), .3*k)                          # barrel collar
    g['metal'].cyl((11.0*k, 0, zb), 1.0*k, 10.0*k, 'X', n=20, bevel=.1*k)               # fixed main barrel
    brake = g['metal#brake']
    brake.box((16.2*k, 0, zb), (3.6*k, 7.2*k, 2.6*k), .3*k)                             # muzzle brake
    for sy in (1, -1):                                                                  # its ports, through
        brake.cut().box((16.2*k, sy*1.6*k, zb), (2.2*k, .8*k, 3.0*k), .04*k)
    g['dark'].cyl((18.02*k, 0, zb), .7*k, .06*k, 'X', n=16)
    za = zb

    def gun_parts(gg, za):
        for i, sy in enumerate((1, -1)):                                                  # side tubes
            y = sy*3.3*k
            rg = gg.sub(f'recoil {i+1}', (3.4*k, y, za), .1*r)
            rg['steel'].cyl((8.2*k, y, za), 1.9*k, 9.6*k, 'X', n=24, bevel=.15*k)
            for x in (5.0, 7.5, 10.0, 12.5):
                rg['dark'].cyl((x*k, y, za), 1.95*k, .5*k, 'X', n=24)
            rg['dark'].cyl((13.05*k, y, za), .9*k, .06*k, 'X', n=16)
        zs = zt+.5*k                                                                       # recoil spring
        gg['steel'].cyl((-4.0*k, 0, zs), 1.4*k, 9.0*k, 'X', n=20)
        for i in range(8):
            gg['plate'].lathe([(1.6*k, -.35*k), (2.5*k, -.35*k), (2.5*k, .35*k), (1.6*k, .35*k)], 24,
                              ((-7.6+1.05*i)*k, 0, zs), 'X', loop=True)
        for x in (-8.8, .8):
            gg['dark'].box((x*k, 0, zs), (.8*k, 5.0*k, 3.2*k), .1*k)                         # spring caps
        gg['steel'].box((1.6*k, 0, (zs+za)/2), (1.4*k, 3.0*k, zs-za+1.0*k), .1*k)            # link to the tubes
    return (2.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'muzzles': [(18.0*k, 0, zb)]}


def _house_hveldriver(g, f, s, r, z0):
    """hypervelocity_driver_turret_base.png: a grey bat-wing base - two ear
    fins rising at the front corners, a central emitter with grilles either
    side, a tan core block, ribbed grille stripes down the flanks and a ribbed
    dome tapering aft; above it the tan driver head with its bore on the
    centreline and the grey coil loop off its starboard side."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    H = 4.2
    base = g['paint#base']
    _plan_loft(base, [(-12.4, 3.0, H-2.2), (-10.5, 5.2, H-1.4), (-8.5, 7.4, H-.8), (-6.5, 9.4, H-.3),
                      (1.5, 10.0, H), (2.6, 9.6, H-.4)], z0, k, .9, .1, .7)
    zt = z0+H*k
    for sy in (1, -1):                                                                          # ear fins
        st = []
        for x, w, h in ((0.0, 1.6, H), (2.5, 1.6, H+1.4), (4.5, 1.2, H+2.4), (5.6, .5, H+2.8)):
            loop = [(sy*8.2*k+yy, zz) for yy, zz in section(w*k, z0, z0+h*k, .4*k, .1*k, .3*k)]
            st.append((x*k, loop if sy > 0 else loop[::-1]))
        g['paint'].loft_x(st)
        for x in (-1.0, -2.6, -4.2, -5.8, -7.4):                                                # ribbed pods
            g['steel'].box((x*k, sy*8.9*k, z0+(H-.6)/2*k), (.7*k, 2.2*k, (H-.6)*k), .1*k)
            g['dark'].box(((x-.8)*k, sy*9.0*k, z0+(H-1.0)/2*k), (.8*k, 1.8*k, (H-1.2)*k))
        base.cut().box((.4*k, sy*4.6*k, zt), (1.8*k, 3.4*k, .8*k), .04*k)                   # grille well
        g['dark'].box((.4*k, sy*4.6*k, zt-.36*k), (1.8*k, 3.4*k, .06*k))
        for j in range(3):
            g['steel'].box((.4*k, sy*(3.4+1.1*j)*k, zt-.12*k), (1.8*k, .25*k, .24*k))
    g['plate'].cyl((.3*k, 0, zt+.3*k), 1.6*k, .6*k, 'Z', n=24, bevel=.1*k)                    # emitter
    g['glow'].cyl((.3*k, 0, zt+.62*k), .8*k, .06*k, 'Z', n=16)
    g['accent'].box((-4.0*k, 1.8*k, zt+.3*k), (3.2*k, 5.6*k, .6*k), .15*k)                    # tan core
    for x in (-7.5, -9.0, -10.5):                                                               # dome ribs
        g['steel'].box((x*k, 0, z0+(H-1.0)*k), (.4*k, 2*(9.4-(-6.5-x)*1.1)*k*.8, .4*k))
    za = zt+2.4*k

    def gun_parts(gg, za):
        st = []                                                                                 # breech housing
        for x, w, h in ((-6.4, 2.8, 2.0), (-5.6, 3.8, 3.0), (-2.0, 4.2, 3.6), (2.8, 4.2, 3.8)):
            st.append((x*k, [(1.4*k+yy, zz) for yy, zz in section(w*k, za-2.2*k, za-2.2*k+h*k, .8*k, .1*k,
                                                                         .5*k)]))
        bh = gg['accent#breech']
        bh.loft_x(st)
        for x in (-4.0, -2.0, 0.0):                                             # slots across its top
            bh.cut().box((x*k, 1.4*k, za+1.62*k), (.5*k, 6.0*k, .8*k), .03*k)
            gg['dark'].box((x*k, 1.4*k, za+1.26*k), (.5*k, 6.0*k, .06*k))
        gg['steel'].box((-6.3*k, 1.4*k, za-.8*k), (.4*k, 4.4*k, 1.6*k), .1*k)
        rg = gg.sub('recoil 1', (2.5*k, 2.5*k, za), .1*r)
        pts = [(2.5*k, 4.6*k), (2.5*k, -1.6*k), (17.5*k, -1.6*k), (17.5*k, 4.6*k)]
        head = rg['accent#head']
        head.prism(pts, za-2.2*k, za+1.6*k, .5*k, za+2.2*k)                                  # driver head
        head.cyl((10.0*k, 4.6*k, za), 2.0*k, 15.0*k, 'X', n=24, bevel=.2*k)                  # rounded port side
        for x in (5.0, 8.0, 11.0, 14.0):                                       # slots cut across the head
            head.cut().box((x*k, 1.5*k, za+2.2*k), (1.0*k, 4.4*k, 1.0*k), .04*k)
            rg['dark'].box((x*k, 1.5*k, za+1.74*k), (1.0*k, 4.4*k, .06*k))
        rg['steel'].box((18.6*k, 0, za), (2.4*k, 3.6*k, 3.0*k), .25*k)                         # muzzle block
        rg['plate'].box((19.85*k, 0, za), (.1*k, 2.6*k, 2.0*k))
        rg['dark'].cyl((19.92*k, 0, za), .8*k, .05*k, 'X', n=16)
        # C-clamp: a solid steel frame off the starboard side.
        clamp = rg['steel#clamp']
        clamp.box((17.0*k, -4.9*k, za), (1.8*k, 6.6*k, 3.0*k), .25*k)
        clamp.box((11.8*k, -4.9*k, za), (1.8*k, 6.6*k, 3.0*k), .25*k)
        clamp.box((14.4*k, -7.6*k, za), (7.0*k, 1.8*k, 3.0*k), .25*k)
        for x in (13.2, 15.6):                                                  # ports in its outer face
            clamp.cut().box((x*k, -8.5*k, za), (.8*k, 1.0*k, 1.6*k), .04*k)
            rg['dark'].box((x*k, -8.06*k, za), (.8*k, .06*k, 1.6*k))
    return (3.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'muzzles': [(19.8*k, 0, za)], 'skip_barrels': True}


# --- Tri-Tachyon energy weapons (the Paragon's Elite fit) ----------------------------
# A beam has no gun sprite: the whole weapon trains, and the elevation empty
# carries nothing but the muzzle anchors. Each stands on a thin deck cut to its
# sprite's outline (the bodies and thin prongs), which holds the silhouette, and
# is built up from it as solids read off the sprite's shading: a lit column is a
# crest with faces falling away either side, a dark band beside it a steep wall
# or a gap, a highlight running across a part says it lies across. Heights follow
# the kit's sections (SECTION: roof 6.0 / 4.6 / 3.2 px), so a gun has a profile
# from the side as well as a plan from above. Measurements are sprite pixels
# times k; z is above the deck's foot z0.

def _ccw(pts):
    a = sum(p[0]*q[1]-q[0]*p[1] for p, q in zip(pts, pts[1:]+pts[:1]))
    return list(pts) if a > 0 else list(reversed(pts))


def _turntable(g, k, z0, cx, R, h, key='steel'):
    """The base a mount trains on: a round chamfered disc h pixels high, radius R, centred
    cx forward of the pivot, a seam ring round its top. Parts reaching past it overhang;
    a plate cut to the sprite's outline read as a strange board (owner, 2026-10-19)."""
    g[key].lathe([(R*k, 0), (R*k, .5*h*k), ((R-.35)*k, h*k), (0, h*k)], 64, (cx*k, 0, z0))
    g['dark'].ring((cx*k, 0, z0+(h+.02)*k), (R-.9)*k, (R-.7)*k, .06*k, 'Z', n=64)


def _loft(m, stations, k, z0, sy=1):
    """loft_x through [(x, [(y, z), ...]), ...] in sprite pixels, mirrored to starboard by sy."""
    m.loft_x([(x*k, [(sy*y*k, z0+z*k) for y, z in sec]) for x, sec in stations])


def _beam(m, a, b, sec_a, sec_b, k, z0):
    """A solid along the plan segment a -> b, cross-sections [(across, z), ...] at each end."""
    (ax, ay), (bx, by) = a, b
    L = math.hypot(bx-ax, by-ay)
    nx, ny = -(by-ay)/L, (bx-ax)/L
    rings = [[m.bm.verts.new(m.base @ Vector(((px+v*nx)*k, (py+v*ny)*k, z0+z*k))) for v, z in sec]
             for (px, py), sec in ((a, sec_a), (b, sec_b))]
    n = len(rings[0])
    for i in range(n):
        m.bm.faces.new((rings[0][i], rings[0][(i+1) % n], rings[1][(i+1) % n], rings[1][i]))
    m.bm.faces.new(rings[0])
    m.bm.faces.new(rings[1])


def _tt_muzzles(sp, k, za):
    return [(x*k, y*k, za) for x, y in sp['offsets']]


def _cr(rows, x):
    """Catmull-Rom through a profile table [(x, v1, v2 ...), ...] (any order): the values at x,
    so a few measured stations give a smooth form rather than straight facets between them."""
    rows = sorted(rows)
    x = min(max(x, rows[0][0]), rows[-1][0])
    i = max(j for j in range(len(rows)-1) if rows[j][0] <= x) if x < rows[-1][0] else len(rows)-2
    p0, p1, p2, p3 = rows[max(i-1, 0)], rows[i], rows[i+1], rows[min(i+2, len(rows)-1)]
    t = (x-p1[0])/((p2[0]-p1[0]) or 1)
    return [.5*(2*b+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t**3)
            for a, b, c, d in zip(p0[1:], p1[1:], p2[1:], p3[1:])]


def _shrink(sec, depth, d):
    """A section pulled in by `depth` at its sides and down by it on top: the core a groove shows."""
    ys = [y for y, _ in sec]
    ym, w = (min(ys)+max(ys))/2, (max(ys)-min(ys))/2
    f = max(.1, (w-depth)/w) if w > 0 else 1
    return [(ym+(y-ym)*f, z if z <= d+1e-6 else max(d, z-depth)) for y, z in sec]


def _panelled(main, core, prof, sec, x0, x1, grooves, k, z0, d, sy=1, step=.8, gw=.45, depth=.3):
    """A smooth loft from x0 to x1 through profile table `prof` (sec(*values) gives the YZ loop),
    broken into plates by grooves at the given x: each plate is its own loft, and in each gap
    sits a core pulled in by `depth`, so a seam is a real groove with a floor, not a painted line."""
    cuts = sorted(g for g in grooves if x0+gw < g < x1-gw)
    edges = [x0]+[e for g in cuts for e in (g-gw/2, g+gw/2)]+[x1]
    for a, b in zip(edges[::2], edges[1::2]):
        n = max(1, int(math.ceil((b-a)/step)))
        _loft(main, [(a+(b-a)*i/n, sec(*_cr(prof, a+(b-a)*i/n))) for i in range(n+1)], k, z0, sy)
    for g in cuts:
        _loft(core, [(x, _shrink(sec(*_cr(prof, x)), depth, d)) for x in (g-gw/2-.05, g+gw/2+.05)], k, z0, sy)


def _house_tachyonlance(g, f, s, r, z0):
    """tachyon_lance_turret_base.png. The lit columns running the sprite's whole
    length are the crests of two keel blades: each a wedge with a steep inner wall
    down to the open beam slot (the dark columns either side of the gap), a rounded
    crest and an outer glacis falling to the deck, tallest at the shoulders, low at
    both tips; their aft ends are the two long legs. Each blade is laid in plates,
    the seams between them real grooves. A clamp band crosses both blades (the light
    line across them). The highlights across each shoulder are capacitor cylinders
    lying athwartships, ringed with cooling fins, white insulators at their outer
    ends and a red band; under them a dark machinery block with vent slats and a red
    lamp, coolant ports (the teal dots) aft of it, and three vanes each side falling
    away aft. The emitter sits in the slot's root behind its lens. It all trains on
    a round turntable; blades and vanes cantilever past it, their undersides lifting
    toward the tips."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    d = .8
    _turntable(g, k, z0, -3.0, 5.5, d)
    # Each blade's underside sits on the turntable at the shoulders and lifts toward
    # both tips (zb), so the blades cantilever off the mount rather than lying on a plate.
    #        x     yi   yc   yo   crest  under
    blade = [(35.5, 1.5, 2.6, 4.6, 2.4, 1.5), (30, 1.5, 3.4, 7.4, 3.3, 1.2), (20, 1.5, 4.6, 9.8, 4.8, .8),
             (10, 1.5, 5.0, 10.8, 5.8, d), (5, 1.8, 5.0, 11.2, 6.0, d), (0, 2.0, 5.0, 10.4, 6.0, d),
             (-8, 2.0, 5.0, 8.6, 5.2, d), (-16, 2.0, 5.0, 7.6, 3.6, 1.0), (-22.5, 2.0, 4.6, 6.8, 2.4, 1.3)]

    def sec(yi, yc, yo, zc, zb, grow=0.0):
        zo = max(1.7, zb+.6)
        return [(yi, zb), (yi, zc-.8+grow), (yi+.35*(yc-yi), zc-.2+grow), (yc, zc+grow),
                (yc+.35*(yo-yc), zc-.45+grow), (yo+grow, zo+grow), (yo+grow, zb)]
    for sy in (1, -1):
        _panelled(g['paint'], g['steel'], blade, sec, -22.5, 35.5, (-12, -3, 17, 27), k, z0, d, sy)
        _loft(g['metal'], [(x, sec(*_cr(blade, x), .35)) for x in (9, 8.4, 7.6, 7)], k, z0, sy)
        # Capacitor: a cylinder across the shoulder on a cradle braced off the blade, cooling
        # fins, insulator, red band.
        g['steel'].box((6*k, sy*10.6*k, z0+1.8*k), (3.2*k, 3.4*k, 2.0*k), .2*k)
        g['steel'].box((6*k, sy*15.2*k, z0+1.6*k), (4*k, 7.5*k, 1.6*k), .15*k)
        g['accent'].cyl((6*k, sy*17*k, z0+3.0*k), 2.6*k, 13*k, 'Y', n=32, bevel=.2*k)
        for yy in (11.6, 12.8, 14.0, 15.2):
            g['steel'].ring((6*k, sy*yy*k, z0+3.0*k), 2.5*k, 2.95*k, .35*k, 'Y', n=32)
        g['steel'].ring((6*k, sy*23.3*k, z0+3.0*k), 1.6*k, 2.7*k, .5*k, 'Y', n=32)
        g['lamp_blue'].cyl((6*k, sy*23.9*k, z0+3.0*k), 1.65*k, .8*k, 'Y', n=32)
        g['lamp_red'].ring((6*k, sy*18.8*k, z0+3.0*k), 2.5*k, 2.75*k, .6*k, 'Y', n=32)
        # Machinery block under the capacitor, vent slats in its top, its lamp; the coolant port aft.
        mach = g['steel#mach']
        mach.box((2.75*k, sy*14*k, z0+1.9*k), (3.5*k, 12*k, 2.2*k), .2*k)
        mach.box((-1.25*k, sy*12.25*k, z0+1.7*k), (4.5*k, 8.5*k, 1.8*k), .2*k)
        # The vanes' roots are webbed into one root plate (the sprite's dark mass there).
        g['steel'].prism(_ccw([(x*k, sy*y*k) for x, y in ((-4.5, 15.5), (-8, 12.5), (-9, 8.2), (-12.5, 9.0),
                                                         (-13, 13.0), (-9.5, 17.0), (-6, 18.5))]),
                         z0, z0+1.3*k, .3*k, z0+1.7*k)
        # The dark mass aft between each leg and its inner vane: a sloped fairing.
        g['steel'].loft_x([(x*k, [(sy*y*k, z0+z*k) for y, z in ((7.6, d), (12.5, d), (12.5, zt), (7.6, zt+.6))])
                           for x, zt in ((-6, 2.4), (-14, 1.4))])
        for xx in (1.6, 2.75, 3.9):
            mach.cut().box((xx*k, sy*13*k, z0+3.0*k), (.5*k, 5.0*k, .6*k))
        g['dark'].box((2.75*k, sy*13*k, z0+2.72*k), (3.0*k, 4.9*k, .05*k))
        # A fairing over each shoulder, ahead of the capacitor, sloping off the blade.
        g['paint'].prism(_ccw([(x*k, sy*y*k) for x, y in ((13.5, 9.8), (11.5, 13.8), (8.8, 15.0), (8.2, 10.6))]),
                         z0+.8*k, z0+2.2*k, .5*k, z0+2.8*k)
        g['lamp_red'].box((2.6*k, sy*15*k, z0+3.05*k), (.9*k, .9*k, .2*k))
        g['steel'].box((-5*k, sy*10*k, z0+1.6*k), (4*k, 4*k, 1.6*k), .15*k)
        g['steel'].ring((-5*k, sy*10*k, z0+2.5*k), .9*k, 1.3*k, .3*k, 'Z', n=24)
        g['lamp_blue'].cyl((-5*k, sy*10*k, z0+2.45*k), .9*k, .3*k, 'Z', n=24)
        # Vanes: root high at the body, falling to the tip, a rounded spine on each.
        for (x0, y0), (x1, y1) in (((-9, 8.5), (-20.5, 10.6)), ((-8, 12.5), (-17.4, 17.8)), ((-5, 15), (-11.6, 20.6))):
            _beam(g['steel'], (x0, sy*y0), (x1, sy*y1),
                  [(-1.8, d), (-1.8, 2.1), (-.7, 2.75), (0, 2.9), (.7, 2.75), (1.8, 2.1), (1.8, d)],
                  [(-.9, .9), (-.9, 1.2), (-.45, 1.45), (0, 1.55), (.45, 1.45), (.9, 1.2), (.9, .9)], k, z0)
    # The emitter in the slot's root, its lens in a collar facing down the slot; the dark
    # mass bridging the legs aft.
    g['dark'].box((-2.75*k, 0, z0+2.0*k), (8.5*k, 4.2*k, 2.4*k), .15*k)
    g['steel'].box((1.75*k, 0, z0+2.2*k), (.5*k, 3.0*k, 2.2*k), .1*k)
    g['lamp_blue'].box((2.05*k, 0, z0+2.2*k), (.3*k, 2.4*k, 1.6*k))
    g['steel'].box((-5.5*k, 0, z0+1.3*k), (5*k, 22*k, 1.2*k), .2*k)
    za = z0+2.2*k
    return (3.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'muzzles': _tt_muzzles(sp, k, za)}


def _house_gravitonbeam(g, f, s, r, z0):
    """graviton_beam_turret_base.png. The nose is a raised spire whose rounded crest
    carries the bright cyan line (the projector), dark recesses at its shoulders;
    behind it two cowls flank a dark sunken spine with a lamp in it and rimmed edges;
    the cowls are laid in plates with grooved seams. A lens bar lies across the body
    (the bright cross row) in collars; a base block and two feet aft. It trains on a
    round turntable, the spire's underside lifting toward its point."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    d = .6
    _turntable(g, k, z0, -.5, 9.0, d)
    spire = [(21, 1.4, 1.9, 1.3), (18, 2.4, 2.7, 1.0), (14, 3.2, 3.4, .75), (10, 3.8, 3.9, d), (6, 3.8, 3.9, d)]

    def spire_sec(yo, zs, zb):
        s1 = max(zb+.3, zs-1.0)
        return [(-yo, zb), (yo, zb), (yo, s1), (.45*yo+.35, zs-.25), (.5, zs), (-.5, zs), (-(.45*yo+.35), zs-.25),
                (-yo, s1)]
    _panelled(g['paint'], g['steel'], spire, spire_sec, 6, 21, (12.5,), k, z0, d, step=.6)
    _loft(g['lamp_blue'], [(x, [(-.3, zs-.05), (.3, zs-.05), (.3, zs+.1), (-.3, zs+.1)])
                           for x, (_, zs, _) in ((x, _cr(spire, x)) for x in (18.5, 16, 14, 13, 11, 9.5))], k, z0)
    cowl = [(10.5, 2.6, 5.4, 2.8), (7, 3.8, 6.0, 3.8), (2, 4.5, 6.4, 4.3), (-3, 4.6, 6.4, 4.2), (-7.5, 3.8, 6.6, 3.2)]

    def cowl_sec(hw, yc, h):
        return [(yc+yy, z) for yy, z in section(hw, d, h, min(.9, .3*hw), 0, .35, 2)]
    for sy in (1, -1):
        _panelled(g['paint'], g['steel'], cowl, cowl_sec, -7.5, 10.5, (4.5, -1.5), k, z0, d, sy, step=.6)
        g['metal'].box((-8.6*k, sy*9.0*k, z0+1.6*k), (2.6*k, 2.8*k, 2.0*k), .15*k)
        g['steel'].cyl((-1.5*k, sy*2.1*k, z0+3.2*k), 1.0*k, .5*k, 'Y', n=24)
        g['metal'].box((1*k, sy*1.65*k, z0+3.3*k), (14*k, .35*k, .3*k), .05*k)
    g['dark'].box((1*k, 0, z0+1.9*k), (14*k, 3.0*k, 2.6*k))
    g['lamp_blue'].box((3*k, 0, z0+3.25*k), (1.0*k, 1.0*k, .2*k))
    g['lamp_blue'].cyl((-1.5*k, 0, z0+3.2*k), .8*k, 3.6*k, 'Y', n=24)
    g['steel'].box((-8.2*k, 0, z0+1.3*k), (3.6*k, 16*k, 1.4*k), .15*k)
    za = z0+3.0*k
    return (15.0*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'muzzles': _tt_muzzles(sp, k, za)}


def _house_taclaser(g, f, s, r, z0):
    """tactical_laser_turret.png. A tall dome; the dark band across its middle with
    the olive-gold core in it is a trench cut across the crown with the focusing
    core lying in it. The dome is laid in plates: an outer shell over a darker core,
    seams cut through the shell to it. A skirt band at its foot; the emitter barrel
    runs forward from the dome's face, cooling rings along it, to a lit tip."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    d = .5
    _turntable(g, k, z0, -1.5, 8.0, d)
    # The sprite's widest points either side (its blue nodes) are sensor lugs on the
    # dome's flanks; a rear cap behind it; a neck cowl round the emitter's root.
    for sy in (1, -1):
        g['steel'].box((-3.0*k, sy*8.2*k, z0+1.6*k), (3.6*k, 2.2*k, 2.0*k), .25*k)
        g['lamp_blue'].cyl((-3.0*k, sy*9.35*k, z0+1.7*k), .55*k, .15*k, 'Y', n=16)
    g['steel'].box((-9.2*k, 0, z0+1.3*k), (1.8*k, 4.4*k, 1.6*k), .2*k)
    g['paint'].lathe([(2.0*k, 0), (1.75*k, 1.2*k), (1.4*k, 2.0*k)], 24, (4.0*k, 0, z0+2.8*k), 'X')
    prof = [(math.cos(a)*7.7, math.sin(a)*4.3) for a in (math.radians(t) for t in range(0, 91, 9))]
    prof = [(7.7, 0)]+prof[1:-1]+[(0, 4.3)]
    shell = g['paint#dome']
    c = (-1.5*k, 0, z0+d*k)
    shell.lathe([(x*k, z*k) for x, z in prof], 32, c)
    g['steel'].lathe([(max(0, x-.3)*k, max(0, z-.3)*k) for x, z in prof[:-1]]+[(0, 4.0*k)], 32, c)
    for a in (0, 60, 120):
        shell.cut().box((-1.5*k, 0, z0+(d+3.3)*k), (16*k, .35*k, 2.6*k), rot=Matrix.Rotation(math.radians(a), 4, 'Z'))
    shell.cut().box((.2*k, 0, z0+5.0*k), (4.2*k, 16*k, 2.8*k))
    g['steel'].ring((-1.5*k, 0, z0+(d+.25)*k), 7.6*k, 8.0*k, .5*k, 'Z', n=48)
    g['metal'].cyl((.2*k, 0, z0+3.3*k), 1.5*k, 9.0*k, 'Y', n=32, bevel=.15*k)
    for yy in (-3.4, 3.4):
        g['steel'].ring((.2*k, yy*k, z0+3.3*k), 1.45*k, 1.75*k, .4*k, 'Y', n=32)
    g['lamp_core'].lathe([(1.0*k, 0), (.85*k, .35*k), (.55*k, .6*k), (0, .7*k)], 24, (.2*k, 0, z0+4.6*k))
    g['accent'].lathe([(1.4*k, 0), (1.4*k, 1.2*k), (1.0*k, 1.6*k), (.85*k, 6.4*k), (1.05*k, 6.7*k), (1.05*k, 7.4*k),
                       (.5*k, 7.8*k)], 32, (3.5*k, 0, z0+2.8*k), 'X')
    for xx in (6.0, 7.2, 8.4):
        g['steel'].ring((xx*k, 0, z0+2.8*k), .8*k, 1.1*k, .3*k, 'X', n=32)
    g['lamp_blue'].cyl((10.75*k, 0, z0+2.8*k), 1.1*k, .4*k, 'X', n=32)
    za = z0+2.8*k
    return (3.5*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'muzzles': _tt_muzzles(sp, k, za)}


def _house_reaper(g, f, s, r, z0):
    """light_reaper_launcher_turret_base.png. Two tall rails (lit outer faces, dark
    inner) either side of the loading slot, chamfered along their inner top edges,
    tapering at the muzzle and laid in sections; the round loaded in the slot
    (RENDER_LOADED_MISSILES); the launcher block with its dark tube mouth and
    grooved seams; the open vent gap behind it (the sprite's hole), a rear
    block, and the base bar with red lamps and blue-grey end caps."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    d = .5
    _turntable(g, k, z0, -1.5, 5.6, d)
    rail = [(-9, 3.6, 2.5), (9, 3.6, 2.5), (10.8, 3.4, 2.6), (11.6, 2.8, 3.4)]
    for sy in (1, -1):
        def rail_sec(h, yi):
            return [(yi, d), (6.0, d), (6.0, h-.2), (5.65, h), (yi+.75, h), (yi, h-.6)]
        _panelled(g['metal'], g['dark'], rail, rail_sec, -9, 11.6, (-2.5, 5.5), k, z0, d, sy, step=.5, depth=.25)
        g['accent2'].box((-8.6*k, sy*7.3*k, z0+1.6*k), (2.6*k, 1.6*k, 2.2*k), .15*k)
        g['lamp_red'].box((-8.3*k, sy*4.8*k, z0+3.65*k), (1.0*k, 1.0*k, .2*k))
    launcher = g['paint#launcher']
    launcher.box((-.2*k, 0, z0+2.0*k), (5.6*k, 8.0*k, 3.0*k), .25*k)
    launcher.cut().box((.3*k, 0, z0+3.6*k), (1.8*k, 1.8*k, 2.0*k))
    for xx in (-1.9, 2.1):
        launcher.cut().box((xx*k, 0, z0+3.5*k), (.35*k, 8.6*k, .5*k))
    g['dark'].box((.3*k, 0, z0+2.55*k), (1.7*k, 1.7*k, .1*k))
    g['accent'].lathe([(.85*k, 0), (.85*k, 7.0*k), (.6*k, 7.9*k), (.35*k, 8.4*k), (.05*k, 8.7*k)], 24,
                      (3.0*k, 0, z0+2.2*k), 'X')
    g['paint'].box((-5.6*k, 0, z0+1.8*k), (3.0*k, 8.0*k, 2.6*k), .2*k)
    g['steel'].box((-8.4*k, 0, z0+1.3*k), (2.8*k, 16*k, 1.6*k), .2*k)
    g['steel'].box((-10.2*k, 0, z0+.9*k), (1.0*k, 12*k, .9*k), .15*k)
    za = z0+2.2*k
    return (1.5*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'muzzles': _tt_muzzles(sp, k, za)}


def _cable(m, ctrl, r, k, z0, n=12, segs=16, t0=0.0, t1=1.0):
    """A cable swept along a cubic Bezier ctrl = 4 points (x, y, z) in sprite pixels (z above z0),
    from parameter t0 to t1: round, capped, its rings kept square to the run."""
    P = [Vector(c) for c in ctrl]

    def at(t):
        u = 1-t
        return u**3*P[0]+3*u*u*t*P[1]+3*u*t*t*P[2]+t**3*P[3]

    def tan(t):
        u = 1-t
        return (3*u*u*(P[1]-P[0])+6*u*t*(P[2]-P[1])+3*t*t*(P[3]-P[2])).normalized()
    rings, ref = [], Vector((0, 0, 1))
    for i in range(segs+1):
        t = t0+(t1-t0)*i/segs
        c, tv = at(t), tan(t)
        side = tv.cross(ref)
        if side.length < 1e-4:
            side = tv.cross(Vector((0, 1, 0)))
        side.normalize()
        up = side.cross(tv).normalized()
        ref = up
        rings.append([m.bm.verts.new(m.base @ Vector(((c.x+r*(math.cos(a)*side.x+math.sin(a)*up.x))*k,
                                                      (c.y+r*(math.cos(a)*side.y+math.sin(a)*up.y))*k,
                                                      z0+(c.z+r*(math.cos(a)*side.z+math.sin(a)*up.z))*k)))
                      for a in (2*math.pi*j/n for j in range(n))])
    for a, b in zip(rings, rings[1:]):
        for j in range(n):
            m.bm.faces.new((a[j], a[(j+1) % n], b[(j+1) % n], b[j]))
    m.bm.faces.new(rings[0])
    m.bm.faces.new(rings[-1][::-1])


def _house_pdlaser(g, f, s, r, z0):
    """pd_laser_turret_base.png. A rounded dark housing aft, lofted to the sprite's
    plan (its light knob on the crown); round its middle a red armoured collar with a louvred vent on top (the
    light and dark stripes); forward, the light emitter drum lying along the line of
    fire, a collar band round it and the lens in its face. A point-defence mount:
    low, compact and quick, and busy in a fight, so the drum rides a gimbal: a yoke
    of two arms off the collar carries it on trunnion hubs, and it elevates from
    5 degrees down to 75 up (ELEVATION_LIMITS). It is a laser / plasma mount, so its
    power is heavy cabling: a junction box on the housing's crown feeds two armoured
    cables a side over the collar's shoulders into the trunnion hubs, where slip
    rings carry it across the elevation axis (the cables never flex with the drum).
    Everything stands on a round chamfered turntable, not on a plate cut to the
    sprite's outline."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    d = .6
    # Turntable: a chamfered disc under the whole mount, a seam ring round its top.
    _turntable(g, k, z0, -1.0, 5.6, d)
    # Housing: a rounded body lofted to the sprite's plan (wide at the collar, a blunt
    # rounded tail), crowned highest just behind the collar.
    body = [(-6.7, 2.2, 1.0), (-6.3, 4.2, 1.7), (-5.4, 5.6, 2.4), (-4.0, 6.4, 3.0), (-2.4, 6.7, 3.4),
            (-.8, 6.8, 3.55), (1.0, 6.6, 3.4), (2.6, 6.0, 3.0)]
    _loft(g['paint'], [(x, section(hw, d, d+h, min(1.6, .45*h), 0, .5, 3)) for x, hw, h in
                       ((x, *_cr(body, x)) for x in [-6.7+i*.4675 for i in range(21)])], k, z0)
    g['metal'].cyl((-3.4*k, 0, z0+(d+3.3)*k), .9*k, .5*k, 'Z', n=24, bevel=.1*k)
    collar = [(-1.6, 7.3, 3.4), (.2, 7.5, 3.8), (2.6, 6.4, 3.5)]
    _loft(g['accent'], [(x, section(hw, d, h, .8, 0, .4, 2)) for x, hw, h in
                        ((x, *_cr(collar, x)) for x in (-1.6, -1.0, -.2, .6, 1.4, 2.0, 2.6))], k, z0)
    for yy in (-2.2, -1.0, .2, 1.4):
        g['metal'].box((.4*k, yy*k, z0+(d+3.62)*k), (2.6*k, .55*k, .3*k), .05*k)
    g['dark'].box((.4*k, -.4*k, z0+(d+3.52)*k), (2.8*k, 4.4*k, .1*k))
    # Gimbal: the yoke's arms rise off the turntable either side of the drum, their
    # trunnion hubs (slip-ring band in red) on the elevation axis.
    xt, zt = 5.2, 3.4
    for sy in (1, -1):
        g['steel'].loft_x([(x*k, [(sy*y*k, z0+z*k) for y, z in ((3.65, d), (4.3, d), (4.3, zt+.7), (3.65, zt+.7))])
                           for x in (4.2, 6.2)])
        g['steel'].cyl((xt*k, sy*4.2*k, z0+zt*k), 1.0*k, .9*k, 'Y', n=32, bevel=.1*k)
        g['lamp_red'].ring((xt*k, sy*4.2*k, z0+zt*k), 1.0*k, 1.1*k, .25*k, 'Y', n=32)
        g['dark'].cyl((xt*k, sy*4.68*k, z0+zt*k), .5*k, .08*k, 'Y', n=24)
    # Power: junction box aft, two armoured cables a side over the collar's shoulders,
    # sleeved and clamped, into the back of each hub through a gland.
    g['steel'].box((-5.8*k, 0, z0+3.4*k), (1.8*k, 8.6*k, 1.4*k), .2*k)
    g['dark'].box((-6.72*k, 0, z0+3.4*k), (.05*k, 6.0*k, .7*k))
    for sy in (1, -1):
        for dy, rr, lift, end in ((3.0, .5, 0.0, (4.3, 4.0, 4.3)), (4.1, .36, -.25, (4.55, 4.5, 3.85))):
            y0 = sy*dy
            ex, ey, ez = end
            ctrl = [(-4.85, y0, 3.65+lift), (-1.5, y0*1.02, 5.1+lift), (2.2, sy*ey, 5.3+lift), (ex, sy*ey, ez)]
            _cable(g['cable'], ctrl, rr, k, z0)
            _cable(g['steel'], ctrl, rr+.18, k, z0, segs=2, t0=0.0, t1=.06)
            _cable(g['steel'], ctrl, rr+.18, k, z0, segs=2, t0=.9, t1=1.0)
            for t in (.22, .38, .62):
                _cable(g['steel'], ctrl, rr+.1, k, z0, segs=1, t0=t, t1=t+.025)
        g['steel'].box((.4*k, sy*3.65*k, z0+4.65*k), (.7*k, 2.6*k, .35*k), .05*k)
        for yy in (2.5, 4.8):
            g['steel'].box((.4*k, sy*yy*k, z0+4.3*k), (.7*k, .3*k, 1.0*k), .05*k)
    za = z0+zt*k

    def gun_parts(gg, _za):
        """The drum on the elevation empty: an elliptical body along the line of fire
        (as wide as the sprite draws it, about half as tall), its band, stepped down
        to the lens, trunnion pins into the hubs, coolant ports on its flanks."""
        def drum(hw, hz, n=24):
            return [(hw*math.cos(2*math.pi*i/n), zt+hz*math.sin(2*math.pi*i/n)) for i in range(n)]
        _loft(gg['accent2'], [(3.8, drum(2.9, 1.75)), (4.4, drum(3.25, 1.9)), (4.7, drum(3.1, 1.8)),
                              (5.8, drum(3.1, 1.8)), (6.15, drum(2.9, 1.6)), (6.95, drum(2.9, 1.6)),
                              (7.25, drum(2.4, 1.35))], k, z0)
        _loft(gg['steel'], [(x, drum(3.2, 1.9)) for x in (5.05, 5.35)], k, z0)
        _loft(gg['dark'], [(x, drum(2.0, 1.1)) for x in (7.2, 7.35)], k, z0)
        _loft(gg['lamp_blue'], [(7.3, drum(1.7, .95)), (7.55, drum(1.2, .65)), (7.65, drum(.5, .3))], k, z0)
        gg['metal'].cyl((xt*k, 0, za), .5*k, 7.6*k, 'Y', n=24)
        for sy in (1, -1):
            gg['steel'].cyl((6.55*k, sy*2.7*k, za+.5*k), .45*k, .6*k, 'Y', n=16)
    return (xt*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts,
                             'muzzles': _tt_muzzles(sp, k, za), 'elevation_limits': (-5, 75)}


def _sweep(m, path, sec, k, z0, sy=1):
    """A solid swept along a plan path [(x, y), ...] in sprite pixels: sec(i, t) gives its
    cross-section [(across, z), ...] at station i (t = 0 .. 1), `across` measured along the
    path's normal (+ to port of the run, so outboard for an arm on the +y side); sy mirrors."""
    rings = []
    for i, (x, y) in enumerate(path):
        a0, a1 = path[max(i-1, 0)], path[min(i+1, len(path)-1)]
        dx, dy = a1[0]-a0[0], a1[1]-a0[1]
        L = math.hypot(dx, dy)
        nx, ny = -dy/L, dx/L
        rings.append([m.bm.verts.new(m.base @ Vector(((x+v*nx)*k, sy*(y+v*ny)*k, z0+z*k)))
                      for v, z in sec(i, i/(len(path)-1))])
    n = len(rings[0])
    for ra, rb in zip(rings, rings[1:]):
        for j in range(n):
            m.bm.faces.new((ra[j], ra[(j+1) % n], rb[(j+1) % n], rb[j]))
    m.bm.faces.new(rings[0])
    m.bm.faces.new(rings[-1])


def _house_ioncannon(g, f, s, r, z0):
    """ion_cannon_turret_base.png (36 x 36 px; the Wolf (P)'s WS 001). One weapon
    read as one casting: a low grey armoured casing over the whole rear plan (its
    flanks are the sprite's grey rims, its waist the light band at rows 16-17),
    whose shoulders run on unbroken into the two field-guide arms that close toward
    their tips (rows 4-15): each arm one swept solid, full casing height at its root
    and falling to a slim cantilevered tip, a steep inner wall carrying the lit
    guide rail (the glow layer's two bright streaks) and a chamfered outer glacis.
    The green capacitor drum sits down in the casing (rows 18-25), a grey coil hub
    on its crown with the lit bore (the glow's centre), coil windows lit in its
    flanks. The emitter rides the elevation empty in the slot between the arms: a
    green breech housing rounded about its trunnions, the coil-wound spine forward
    (the sprite's lit centre line, rows 7-15), the lens in the ion glow colour
    (100, 200, 255) at the fire point, 10 px ahead (turretOffsets)."""
    k = r/NOMINAL_RING['SMALL']
    sp = sprite(s.get('sprite', s['weapon']))
    d = .5
    _turntable(g, k, z0, -1.0, 8.2, d)
    zb = z0+d*k
    H = 2.8                                           # casing roof above the turntable
    # Casing: one loft over the plan, blunt tail, full width over the drum, its flanks
    # running straight on into the arms (no waist, so the arm roots are its own flanks).
    plan = [(-9.0, 2.6, 1.6), (-8.4, 5.2, 2.2), (-7.4, 7.0, 2.6), (-6.0, 7.9, H), (-1.5, 8.0, H),
            (.6, 7.9, H), (2.0, 7.7, H)]
    casing = g['paint#casing']
    _loft(casing, [(x, section(hw, 0, h, .55, .2, .35, 2)) for x, hw, h in
                   ((x, *_cr(plan, x)) for x in [-9.0+i*.55 for i in range(21)])], k, zb)
    # The emitter's slot between the shoulders, a plate seam across the tail, the
    # drum's well, the power coupling's grille at the tail.
    casing.cut().box((2.35*k, 0, zb+(H+.2)*k), (3.9*k, 11.2*k, 2.0*H*k))
    casing.cut().box((-6.6*k, 0, zb+H*k), (.3*k, 18*k, .7*k))
    casing.cut().box((-3.4*k, 0, zb+H*k), (8.8*k, 13.4*k, 1.0*k))
    for yy in (-1.4, -.5, .4, 1.3):
        casing.cut().box((-9.0*k, yy*k, zb+1.1*k), (1.0*k, .45*k, 1.0*k))
    g['dark'].box((-8.75*k, 0, zb+1.1*k), (.1*k, 3.4*k, 1.0*k))
    # Field-guide arms: swept from inside the shoulders to the tips; root at casing
    # height, the underside lifting once clear of the turntable.
    path = [(-2.5, 7.2), (-1.0, 6.85), (.8, 6.7), (2.4, 6.6), (4.4, 6.3), (6.4, 5.7), (8.4, 4.95), (10.4, 4.2), (12.2, 3.4), (13.4, 2.85),
            (14.0, 2.55)]
    half = [.7, 1.15, 1.3, 1.45, 1.6, 1.75, 1.9, 1.7, 1.3, .85, .5]

    def arm_z(i):
        t = i/(len(path)-1)
        rise = min(1.0, i/2)                                       # out of the casing's flank
        return max(0.0, (t-.45)/.55)*.9, H-.5+.85*rise-1.7*t*t      # underside, crest

    def arm(i, t):
        # A wedge: steep inner wall down to the slot, crest near it, a long outer glacis.
        w = half[i]
        lo, h = arm_z(i)
        return [(-w, lo), (w, lo), (w, lo+.35*(h-lo)), (.05*w, h), (-.55*w, h), (-w, h-.25*(h-lo))]
    rail_i = range(5, 9)
    rail = [(path[i][0]+0*half[i], path[i][1]-half[i]) for i in rail_i]
    rail_z = [arm_z(i) for i in rail_i]
    for sy in (1, -1):
        _sweep(g['paint#arms'], path, arm, k, zb, sy)
        # The guide rail: a lit strip set into each arm's inner wall, a steel lip above it.
        _sweep(g['lamp_blue'], rail, lambda i, t: [(-.14, rail_z[i][0]+.3), (.2, rail_z[i][0]+.3),
                                                   (.2, rail_z[i][1]-.75), (-.14, rail_z[i][1]-.75)], k, zb, sy)
        _sweep(g['steel'], rail, lambda i, t: [(-.3, rail_z[i][1]-.75), (.2, rail_z[i][1]-.75),
                                               (.2, rail_z[i][1]-.45), (-.3, rail_z[i][1]-.45)], k, zb, sy)
        # Coil ports in the casing flank (the glow's flank lamps, the sprite's dark ports):
        # round, cut into the casing, a lit coil face at the bottom of each.
        for x in (-5.3, -3.3):
            casing.cut().cyl((x*k, sy*8.0*k, zb+1.45*k), .75*k, 1.6*k, 'Y', n=24)
            g['lamp_blue'].cyl((x*k, sy*7.3*k, zb+1.45*k), .75*k, .1*k, 'Y', n=24)
            g['steel'].ring((x*k, sy*7.75*k, zb+1.45*k), .75*k, .95*k, .5*k, 'Y', n=24)
    # Capacitor drum: a crowned green pod seated in its well, a grey coil hub on top
    # with its bore and the core lit at the bottom of it.
    drum = [(-7.6, 3.4, 3.4), (-7.0, 5.4, 3.9), (-5.5, 6.4, 4.2), (-3.4, 6.6, 4.3), (-1.3, 6.4, 4.2), (.6, 5.2, 3.8),
            (1.0, 4.4, 3.5)]
    dm = g['accent#drum']
    _loft(dm, [(x, section(hw, 1.2, h, .5*hw, 0, .25*hw, 3)) for x, hw, h in
               ((x, *_cr(drum, x)) for x in [-7.6+i*.43 for i in range(21)])], k, zb)
    for x in (-5.9, -.9):
        dm.cut().box((x*k, 0, zb+4.3*k), (.3*k, 14*k, .8*k))
    hub = g['steel#hub']
    hub.lathe([(2.3*k, 0), (2.3*k, .3*k), (2.0*k, .7*k), (1.3*k, .95*k), (0, 1.0*k)], 32, (-3.4*k, 0, zb+3.8*k))
    for m_ in (hub, dm):
        m_.cut().cyl((-3.4*k, 0, zb+4.8*k), .9*k, 1.6*k, 'Z', n=24)
    for a in range(0, 360, 45):
        c, sn = math.cos(math.radians(a)), math.sin(math.radians(a))
        hub.cut().box(((-3.4+1.8*c)*k, 1.8*sn*k, zb+4.6*k), (.25*k, .6*k, .5*k),
                      rot=Matrix.Rotation(math.radians(a+90), 4, 'Z'))
    g['lamp_blue'].cyl((-3.4*k, 0, zb+4.1*k), .9*k, .1*k, 'Z', n=24)
    zt = 2.1
    za = zb+zt*k

    def gun(gg, za):
        # Breech housing: rounded about the trunnion axis at its back (a mantlet, so it
        # turns in the slot without opening a gap), its flanks stepping in to the spine.
        def hous(hw, lo, hi, n=20):
            return section(hw, lo, hi, .45*hw, .25*hw, .1*hw, 2)
        st = [(1.0, hous(2.2, -.9, .9)), (1.4, hous(3.0, -1.3, 1.35)), (2.2, hous(3.3, -1.5, 1.55)),
              (4.4, hous(3.2, -1.4, 1.5)), (5.6, hous(2.8, -1.1, 1.3)), (6.4, hous(2.0, -.8, 1.0))]
        _loft(gg['accent#breech'], [(x, [(y, zt+z) for y, z in sec]) for x, sec in st], k, zb)
        for x in (3.0, 4.6):
            gg['accent#breech'].cut().box((x*k, 0, za+1.6*k), (.3*k, 8*k, .7*k))
        gg['steel'].cyl((1.6*k, 0, za), .55*k, 9.4*k, 'Y', n=24)
        # Spine: the coil-wound tube, three windings, a collar, the lens at the fire point.
        gg['accent'].lathe([(1.25*k, 0), (1.15*k, 2.2*k), (.95*k, 3.6*k), (.95*k, 4.0*k), (1.15*k, 4.1*k),
                            (1.15*k, 4.5*k), (.8*k, 4.7*k)], 32, (5.6*k, 0, za), 'X')
        for x in (6.5, 7.2, 7.9):
            gg['steel'].ring((x*k, 0, za), 1.0*k, 1.45*k, .45*k, 'X', n=32)
        gg['dark'].ring((10.05*k, 0, za), .55*k, .8*k, .1*k, 'X', n=24)
        gg['lamp_blue'].lathe([(.75*k, 0), (.6*k, .2*k), (0, .32*k)], 24, (10.1*k, 0, za), 'X')
    return (1.6*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun,
                              'muzzles': [(x*k, y*k, za) for x, y in sp['offsets']],
                              'elevation_limits': (-5, 20)}


def _house_railgun(g, f, s, r, z0):
    """railgun_turret_base.png (one sprite, no gun layer; the Cerberus' WS 003):
    a low grey house widest through its middle, a pale silver capacitor bulb
    on its crown, a copper breech block sunk in a well behind the bulb, two
    pale lugs at the rear either side of a dark notch, and forward of the bulb
    the copper rail pair, banded dark every two pixels, a pale core between
    the rails and grey guide fins along their roots. The rails ride the
    elevation empty; the game draws no recoil, so recoil 1 is still."""
    k = _k(s, r)
    sp = sprite(s.get('sprite', s['weapon']))
    H = 2.8
    plan = [(-9.6, 5.8, H-.9), (-9.0, 6.6, H-.5), (-6.0, 6.9, H-.2), (-1.0, 7.6, H), (1.0, 7.6, H-.2),
            (2.4, 5.0, H-.8)]
    body = g['paint#body']
    _plan_loft(body, plan, z0, k, .9, .1, .6)
    zt = z0+H*k
    # Rear notch between the lugs (the sprite's dark tail centre), cut into the house.
    body.cut().box((-9.4*k, 0, z0+H*.5*k), (1.6*k, 2.2*k, H*1.2*k), .05*k)
    g['dark'].box((-8.65*k, 0, z0+.7*k), (.1*k, 2.0*k, 1.2*k))
    for sy in (1, -1):
        g['accent2'].box((-8.0*k, sy*4.3*k, z0+(H-.3)*.5*k), (1.8*k, 2.6*k, (H-.3)*k), .2*k)        # lugs
        # Flank cheeks: the dark band down each side (cols 6 / 21), a raised
        # armour strip with a shadow gap under it and two lit tell-tales.
        st = []
        for x, w, h in ((-4.6, .5, H-.8), (-3.6, .9, H-.3), (.4, .9, H-.3), (1.2, .5, H-.8)):
            loop = [(sy*7.3*k+yy, zz) for yy, zz in section(w*k, z0+.5*k, z0+h*k, .3*k, .1*k, .3*k)]
            st.append((x*k, loop if sy > 0 else loop[::-1]))
        g['steel'].loft_x(st)
        g['dark'].box((-1.6*k, sy*8.0*k, z0+.45*k), (5.6*k, .3*k, .6*k))
        g['glow'].box((-2.0*k, sy*8.25*k, z0+1.4*k), (.6*k, .2*k, .5*k))
    # The breech: a copper block in a well behind the bulb (rows 15-19), its
    # lit top a raised plate, dark joints fore and aft.
    body.cut().box((-4.4*k, 0, zt), (4.6*k, 5.4*k, 1.6*k), .05*k)
    g['dark'].box((-4.4*k, 0, zt-.76*k), (4.6*k, 5.4*k, .06*k))
    g['accent#breech'].box((-4.4*k, 0, zt-.2*k), (4.0*k, 4.6*k, 1.4*k), .2*k)
    g['accent#breech'].box((-4.2*k, 0, zt+.55*k), (2.6*k, 3.0*k, .3*k), .1*k)
    for x in (-6.2, -2.6):
        g['dark'].box((x*k, 0, zt+.1*k), (.3*k, 4.8*k, .4*k))
    # Capacitor bulb: a squat dome on the crown (rows 10-15), a band round its
    # foot and a dark seam ring at its shoulder.
    bulb = g['accent2#bulb']
    n = 10
    prof = [(4.5*k*math.cos(math.pi/2*i/n)**.8, zt-.2*k+2.4*k*math.sin(math.pi/2*i/n)) for i in range(n+1)]
    bulb.lathe([(0, zt-.2*k)]+prof, 32, (-.3*k, 0, 0), 'Z')
    g['steel'].ring((-.3*k, 0, zt-.05*k), 4.3*k, 4.9*k, .5*k, 'Z', n=32)
    g['dark'].ring((-.3*k, 0, zt+1.35*k), 3.45*k, 3.62*k, .12*k, 'Z', n=32)
    za = z0+1.8*k

    def gun_parts(gg, za):
        rg = gg.sub('recoil 1', (1.5*k, 0, za), .1*r)
        for sy in (1, -1):
            # Rail: copper bar, tapering to the muzzle (rows 1-10), banded dark.
            st = []
            for x, w, h in ((1.5, .75, 1.6), (8.0, .65, 1.3), (12.0, .5, 1.0)):
                loop = [(sy*1.15*k+yy, za+zz) for yy, zz in section(w*k, -h*.5*k, h*.5*k, .2*k, .2*k, 0)]
                st.append((x*k, loop if sy > 0 else loop[::-1]))
            rg['accent'].loft_x(st)
            for x in (3.5, 5.5, 7.5, 9.5):
                rg['dark'].box((x*k, sy*1.15*k, za), (.45*k, 1.6*k, 1.55*k-(x-3.5)*.05*k))
            # Guide fin along the rail root (the grey pixels flanking rows 5-10).
            fin = [(2.0*k, sy*1.9*k), (11.0*k, sy*1.6*k), (10.0*k, sy*2.7*k), (6.0*k, sy*3.6*k), (2.4*k, sy*4.6*k)]
            rg["paint"].prism(fin if sy > 0 else fin[::-1], za-.5*k, za+.3*k)
        # Pale core between the rails, its armature lit along the top.
        rg['accent2'].box((6.5*k, 0, za+.15*k), (10.0*k, .9*k, .5*k), .1*k)
        rg['dark'].box((6.5*k, 0, za-.3*k), (10.0*k, 1.4*k, .5*k))
        # Collar where the rails leave the bulb.
        rg['steel'].box((2.2*k, 0, za), (1.4*k, 4.4*k, 2.0*k), .2*k)
    return (1.5*k, 0.0, za), {'k': k, 'sp': sp, 'z0': z0, 'gun_parts': gun_parts, 'no_mantlet': True,
                              'muzzles': [(12.0*k, 0, za)]}


HANDS = {'heavyac': _house_heavyac2, 'flak': _house_flak2, 'vulcan': _house_vulcan, 'lightac': _house_lightac,
         'lightmg': _house_lightmg, 'dualflak': _house_dualflak, 'needler': _house_needler,
         'squall': _house_squall, 'hurricane': _house_hurricane, 'mark9': _house_mark9,
         'hellbore': _house_hellbore, 'hephaestus': _house_hephaestus, 'devastator': _house_devastator,
         'heavymauler': _house_heavymauler, 'hveldriver': _house_hveldriver,
         'tachyonlance': _house_tachyonlance, 'gravitonbeam': _house_gravitonbeam, 'taclaser': _house_taclaser,
         'reaper': _house_reaper, 'pdlaser': _house_pdlaser, 'ioncannon': _house_ioncannon, 'railgun': _house_railgun}

# --- hardpoints: fixed mounts ---------------------------------------------------------
# A hardpoint does not train: it sits in its slot (often a bay cut into the hull) and
# fires along the hull's axis. The game draws its hardpoint sprite with the slot a
# quarter of the sprite's length from its back, the fire point at the .wpn's
# hardpointOffsets ahead of the slot. Builders work in sprite pixels times k (one
# pixel one hull unit at k=1), +X along the line of fire, the slot at the origin,
# z=0 on the mount's axis. A hull seats the parent empty (in its bay, at its angle)
# and asks build_hardpoint for the gun.

def _hp_autopulse(g, f, s, k=1.0):
    """autopulse_laser_hardpoint_base.png (34 x 74 px): a breech on a sled in the
    bay (the dark frame with the light core shaft), a narrow neck, the big capacitor
    bulb with a band round it, a finned cooling flange (the sprite's crenellated band
    is its fin tips), and the long emitter barrel to a lipped muzzle 48 px ahead of the slot (hardpointOffsets). A coolant
    pipe runs from the flange back into the mount on one side (the sprite's bent
    pipe) and a capacitor box rides the sled on the other; the sprite is not
    mirrored, so neither is the gun. Built to fit a bay 21 wide and 22 high: what the
    sprite draws wider at the breech (its side frames) is inside the hull."""
    # Sled and breech: rails on the bay floor, the sled, its cheeks and the core shaft.
    for sy in (1, -1):
        g['dark'].box((-5*k, sy*6.5*k, -9.1*k), (26*k, 1.6*k, 1.0*k))
        g['steel'].box((-7.5*k, sy*7.3*k, -.5*k), (13*k, 2.2*k, 11*k), .3*k)
    g['steel'].box((-9*k, 0, -7.0*k), (16*k, 17*k, 3.2*k), .4*k)
    g['steel'].box((-15.5*k, 0, -1.0*k), (3.0*k, 12*k, 9*k), .4*k)
    g['accent2'].cyl((-7*k, 0, 0), 2.1*k, 16*k, 'X', n=32, bevel=.2*k)
    for xx in (-12, -8, -4):
        g['steel'].ring((xx*k, 0, 0), 2.0*k, 2.6*k, .6*k, 'X', n=32)
    # Capacitor box on the sled (the sprite's right-hand block), with its lamps.
    g['steel'].box((4*k, -6.4*k, -2.5*k), (10*k, 3.6*k, 9*k), .3*k)
    for xx in (1.5, 4, 6.5):
        g['lamp_blue'].box((xx*k, -8.25*k, -1.0*k), (1.2*k, .15*k, 2.6*k))
    # Neck, capacitor bulb with its band, flange.
    g['metal'].cyl((3*k, 0, 0), 4.6*k, 6.4*k, 'X', n=40, bevel=.3*k)
    g['paint'].lathe([(5.2*k, 0), (7.3*k, 1.6*k), (8.4*k, 4.5*k), (8.4*k, 9.0*k), (7.8*k, 12.0*k), (6.2*k, 13.2*k)],
                     48, (6.0*k, 0, 0), 'X')
    g['steel'].ring((16.4*k, 0, 0), 8.0*k, 8.65*k, .9*k, 'X', n=48)
    # The cooling flange: a hub ring and sixteen radial fins (the sprite's crenellated
    # band is their tips seen from above), a heat sink, not a gear.
    g['steel'].lathe([(5.8*k, 0), (9.2*k, 0), (9.6*k, .5*k), (9.6*k, 3.9*k), (9.2*k, 4.4*k), (5.8*k, 4.4*k)],
                     48, (19.6*k, 0, 0), 'X', loop=True)
    for i in range(16):
        a = 2*math.pi*(i+.5)/16
        g['accent'].box((21.8*k, math.cos(a)*11.0*k, math.sin(a)*11.0*k), (3.8*k, 3.4*k, .55*k), .1*k,
                        rot=Matrix.Rotation(a, 4, 'X'))
    g['steel'].cyl((21.8*k, 0, 0), 6.2*k, 4.6*k, 'X', n=40)
    # Emitter barrel: a long tube with two cooling rings, the lip, a dark bore and the lens.
    # The barrel is a tube: the bore runs 16 units back from the muzzle, lined dark,
    # the focusing lens at its bottom.
    barrel = g['paint#barrel']
    barrel.lathe([(5.9*k, 0), (5.7*k, 1.0*k), (5.7*k, 18.2*k), (6.5*k, 18.6*k), (6.6*k, 21.8*k), (6.0*k, 22.4*k)],
                 48, (24.0*k, 0, 0), 'X')
    barrel.cut().cyl((39.0*k, 0, 0), 4.0*k, 16.0*k, 'X', n=48)
    for xx in (30, 36):
        g['steel'].ring((xx*k, 0, 0), 5.65*k, 6.1*k, .7*k, 'X', n=48)
    g['dark'].ring((38.8*k, 0, 0), 3.85*k, 4.0*k, 15.4*k, 'X', n=48)
    g['dark'].cyl((31.2*k, 0, 0), 4.0*k, .3*k, 'X', n=48)
    g['lamp_blue'].lathe([(3.0*k, 0), (2.3*k, .5*k), (0, .8*k)], 32, (31.35*k, 0, 0), 'X')
    # Coolant pipe from the flange back into the mount on the +y side (the sprite's
    # bent pipe), a gland at each end.
    ctrl = [(21.0, 11.0, 2.5), (14.0, 12.0, 3.5), (11.0, 8.4, 4.5), (8.0, 6.6, 5.2)]
    _cable(g['metal'], ctrl, 1.1, k, 0)
    _cable(g['steel'], ctrl, 1.45, k, 0, segs=2, t0=0.0, t1=.08)
    _cable(g['steel'], ctrl, 1.45, k, 0, segs=2, t0=.9, t1=1.0)
    return [(48.0*k, 0.0, 0.0)]


def _hp_tachyonlance(g, f, s, k=1.0):
    """tachyon_lance_hardpoint_base.png (48 x 72 px): the turret lance narrowed for a
    fixed mount; its fire point is only 5 px ahead of the slot (hardpointOffsets),
    at the root of the open slot between the two prongs, and the beam runs out
    along that slot. Built as a spinal beam projector for a bay: a heavy power block
    on a sled in the bay with trunks back to the bay's rear wall (the ship's reactor
    feeds it); the emitter at the slot's root, its lens sunk in a dark well; two
    long armoured prongs running out past the hull, plated and grooved, with
    focusing rails glowing along their inner faces, a clamp band across both; and
    the capacitor pods across the shoulders (shorter than the sprite's, so two
    mounts side by side clear each other); a capacitor bank on the power block. The sprite's aft vanes would lie inside
    the hull and are left out. Everything in the bay keeps within 8.6 of the axis
    across and 8 up and down."""
    # Prongs: diamond sections, symmetric above and below the axis, tapering to the tips.
    #        x    yi    yo    h
    prong = [(3.0, 1.6, 8.4, 5.4), (12, 1.6, 8.2, 5.2), (24, 1.6, 7.4, 4.6), (36, 1.6, 6.0, 3.6),
             (48, 1.7, 4.2, 2.4), (54.5, 1.9, 2.8, 1.1)]

    def sec(yi, yo, h, grow=0.0):
        yc = yi+.42*(yo-yi)
        return [(yi, -.72*h), (yi, .72*h), (yc, h+grow), (yo+grow, .3*h), (yo+grow, -.3*h), (yc, -h-grow)]

    def core(sec_pts, depth=.35):
        ym = sum(y for y, _ in sec_pts)/len(sec_pts)
        return [(ym+(y-ym)*.85, z*(1-depth/max(abs(z) for _, z in sec_pts))) for y, z in sec_pts]
    grooves = (16.0, 30.0, 43.0)
    edges = [3.0]+[e for gx in grooves for e in (gx-.25, gx+.25)]+[54.5]
    for sy in (1, -1):
        for a0, b0 in zip(edges[::2], edges[1::2]):
            n = max(1, int(math.ceil((b0-a0)/1.0)))
            _loft(g['paint'], [(a0+(b0-a0)*i/n, sec(*_cr(prong, a0+(b0-a0)*i/n))) for i in range(n+1)], k, 0, sy)
        for gx in grooves:
            _loft(g['steel'], [(x, core(sec(*_cr(prong, x)))) for x in (gx-.3, gx+.3)], k, 0, sy)
        _loft(g['metal'], [(x, sec(*_cr(prong, x), .4)) for x in (11.2, 11.8, 13.2, 13.8)], k, 0, sy)
        # Focusing rail along the prong's inner face.
        _loft(g['lamp_blue'], [(x, [(yi-.06, -.5), (yi+.02, -.5), (yi+.02, .5), (yi-.06, .5)])
                               for x, (yi, _, _) in ((x, _cr(prong, x)) for x in (7, 20, 34, 47))], k, 0, sy)
        # Capacitor pod across the shoulder: cooling rings, red band, insulator cap. A hull
        # whose mounts stand side by side shortens the inboard pods (pod_reach, by side).
        reach = (s.get('pod_reach') or {}).get(sy, 15.5)
        g['accent'].cyl((15.0*k, sy*(8.1+reach-.5)/2*k, 0), 3.0*k, (reach-.5-8.1)*k, 'Y', n=32, bevel=.2*k)
        for yy in (9.6, 10.6, 11.6):
            if yy < reach-2.6:
                g['steel'].ring((15.0*k, sy*yy*k, 0), 2.9*k, 3.35*k, .35*k, 'Y', n=32)
        g['lamp_red'].ring((15.0*k, sy*(reach-2.1)*k, 0), 2.9*k, 3.15*k, .5*k, 'Y', n=32)
        g['steel'].ring((15.0*k, sy*(reach-.6)*k, 0), 1.6*k, 3.0*k, .5*k, 'Y', n=32)
        g['lamp_blue'].cyl((15.0*k, sy*(reach-.25)*k, 0), 1.65*k, .5*k, 'Y', n=32)
    # The emitter: a root block bridging the prongs, its lens sunk in a dark well facing
    # down the slot (the fire point).
    root = g['steel#root']
    root.box((.5*k, 0, 0), (7.0*k, 17.0*k, 11.0*k), .5*k)
    root.cut().cyl((3.6*k, 0, 0), 1.6*k, 3.0*k, 'X', n=32)
    g['dark'].cyl((2.2*k, 0, 0), 1.6*k, .2*k, 'X', n=32)
    g['lamp_blue'].lathe([(1.3*k, 0), (1.0*k, .35*k), (0, .55*k)], 32, (2.3*k, 0, 0), 'X')
    # The power block on its sled in the bay, a capacitor bank across its top, and three
    # power trunks back to the bay's rear wall.
    for sy in (1, -1):
        g['dark'].box((-12*k, sy*6.5*k, -9.1*k), (26*k, 1.6*k, 1.0*k))
    g['paint'].box((-10.0*k, 0, -.5*k), (14.0*k, 17.0*k, 13.0*k), .6*k)
    # Capacitor bank lying across the block's top: four cells in a cradle, red bands.
    g['steel'].box((-10.0*k, 0, 6.3*k), (12.5*k, 14.5*k, .7*k), .15*k)
    for xx in (-14.4, -11.5, -8.6, -5.7):
        g['accent'].cyl((xx*k, 0, 7.6*k), 1.25*k, 14.0*k, 'Y', n=32, bevel=.15*k)
        for sy in (1, -1):
            g['lamp_red'].ring((xx*k, sy*5.0*k, 7.6*k), 1.2*k, 1.38*k, .35*k, 'Y', n=32)
            g['steel'].ring((xx*k, sy*6.9*k, 7.6*k), 1.2*k, 1.45*k, .4*k, 'Y', n=32)
    for sy in (1, -1):
        g['steel'].box((-10.0*k, sy*7.0*k, 1.0*k), (11*k, 3.2*k, 9*k), .3*k)
        for xx in (-13, -10, -7):
            g['lamp_blue'].box((xx*k, sy*8.65*k, 2.5*k), (1.0*k, .12*k, 3.0*k))
    for yy, zz in ((-4.0, -2.0), (0.0, -3.5), (4.0, -2.0)):
        ctrl = [(-17.0, yy, zz), (-23.0, yy*1.1, zz-2.5), (-29.0, yy*1.1, zz-1.0), (-33.6, yy, zz)]
        _cable(g['cable'], ctrl, 1.3, k, 0)
        for t0, t1 in ((0, .05), (.95, 1.0), (.4, .43), (.6, .63)):
            _cable(g['steel'], ctrl, 1.65, k, 0, segs=2, t0=t0, t1=t1)
    return [(5.0*k, 0.0, 0.0)]


def _hp_miningblaster(g, f, s, k=1.0):
    """blaster2_hardpoint_base.png (24 x 44 px; the Wolf (P)'s WS 004 mining blaster).
    The slot is a quarter of the sprite's length from its back, so the gun runs
    from 8.5 px behind it to two prongs ending 23.5 px ahead; the fire point is
    15 px ahead (hardpointOffsets), at the prongs' root. A ribbed capacitor body
    (the sprite's dark dashes are transverse cooling slots, cut, not painted),
    its rear shoulders stepped out wider than its nose, and two yellow focusing
    prongs with ribbed flanks either side of a dark emitter face whose lens glows
    in the blaster's red (255, 100, 100)."""
    body = g['paint#body']
    rear = [(-8.5, -9.6), (-8.5, 9.6), (-6.4, 10.6), (2.6, 10.6), (4.2, 9.0)]
    plan = _ccw([(x, y) for x, y in rear]+[(14.6, 8.6), (14.6, -8.6)]+[(x, -y) for x, y in reversed(rear[2:])])
    body.prism([(x*k, y*k) for x, y in plan], -3.6*k, 2.6*k, 1.4*k, 4.0*k)
    for x in (-6.0, -3.6, -1.2, 1.2):                 # cooling slots across the rear deck
        for sy in (1, -1):
            body.cut().box((x*k, sy*5.0*k, 4.0*k), (.7*k, 6.0*k, 1.6*k))
    body.cut().box((9.0*k, 0, 4.0*k), (8.0*k, 2.4*k, 1.4*k))   # spine trench
    g['dark'].box((9.0*k, 0, 3.0*k), (8.0*k, 2.4*k, .2*k))
    g['metal'].cyl((9.0*k, 0, 3.4*k), .7*k, 7.6*k, 'X', n=16)   # conduit in the trench
    g['steel'].box((-8.9*k, 0, 0), (.8*k, 16*k, 5.2*k), .15*k)  # rear plate into the bay
    for sy in (1, -1):                                 # the focusing prongs
        prong = g['accent#prong']
        prong.prism([(x*k, sy*y*k) for x, y in _ccw([(13.0, 3.4), (22.4, 3.6), (23.6, 5.0), (23.6, 8.0),
                                                       (22.0, 9.2), (13.0, 9.2)])][::(1 if sy > 0 else -1)],
                     -2.6*k, 1.6*k, .8*k, 2.6*k)
        for x in (15.2, 17.2, 19.2, 21.2):
            prong.cut().box((x*k, sy*9.2*k, 0), (.5*k, 1.2*k, 5.0*k))
            prong.cut().box((x*k, sy*3.4*k, 0), (.5*k, 1.0*k, 5.0*k))
    g['dark'].box((15.0*k, 0, 0), (.6*k, 6.6*k, 4.2*k))           # emitter face
    g['steel'].ring((15.3*k, 0, 0), 1.6*k, 2.4*k, .5*k, 'X', n=24)
    g['lamp_red'].lathe([(1.6*k, 0), (1.2*k, .4*k), (0, .6*k)], 24, (15.35*k, 0, 0), 'X')
    return [(15.0*k, 0.0, 0.0)]


def _hp_annihilator(g, f, s, k=1.0):
    """launcher_annihilator_hardpoint_base.png / _turret_base.png (the Wolf's WS 005 /
    WS 006 Annihilators). A launcher head, not a box (owner, Wolf round 6: "missile
    pods should use modeled missile turret heads"): the turret sprite's rounded
    armoured shell, from a rounded magazine cap 7 px behind the slot to its front
    face 8.4 px ahead, on a fixed mount collar sunk into the hull below it. The face
    holds the five launch tubes in one bank across the line of fire (the .wpn's fire
    points, y -4..4 at 15 px), set in a recess between two blast cheeks that run
    past the tube mouths, and strapped top and bottom. Five raised ribs run fore
    and aft over the crown (the sprite's five light stripes); trunnion bosses on
    the flanks; a slotted loading door on the back. The tubes are parallel:
    the .wpn's angle offsets splay the rockets, not the tubes. Muzzle anchors sit
    at the tube mouths, in the old order (y -4, -2, 0, 2, 4)."""
    shell = g['paint#shell']
    # Fixed collar: a chamfered ring under the shell, its foot sunk into the hull.
    g['steel#collar'].lathe([(5.4*k, -9.0*k), (5.4*k, -4.4*k), (4.8*k, -3.8*k), (0, -3.8*k)], 40, (-.5*k, 0, 0))
    rows = [(-7.4, 3.4, -2.6, 1.4), (-7.0, 5.4, -3.2, 2.6), (-5.6, 7.0, -3.6, 3.5), (-2.6, 7.9, -3.6, 4.0),
            (2.6, 8.2, -3.6, 4.0), (6.2, 7.9, -3.5, 3.5), (7.8, 7.2, -3.2, 2.9), (8.4, 6.6, -2.9, 2.4)]
    shell.loft_x([(x*k, [(y*k, z*k) for y, z in section(hw, z0, z1, min(3.0, .45*(z1-z0)), .8, 1.4, 3)])
                  for x, hw, z0, z1 in rows])
    shell.cut().box((8.2*k, 0, 0), (2.8*k, 12.4*k, 4.0*k))                        # the tube-bank recess
    g['dark'].box((6.95*k, 0, 0), (.3*k, 12.2*k, 3.8*k))
    for y in (-4.0, -2.0, 0.0, 2.0, 4.0):                                          # raised crown ribs
        g['steel'].box((0.0, y*k, 3.95*k), (10.0*k, .7*k, .5*k), .12*k)
    for sy in (1, -1):
        cheek = g['paint#cheek']                                                   # blast cheeks
        plan = [(2.0, 6.0), (10.9, 6.2), (10.9, 7.3), (8.0, 8.4), (2.0, 8.4)]
        cheek.prism([(x*k, sy*y*k) for x, y in _ccw([(x, sy*y) for x, y in plan])], -2.9*k, 2.2*k, .5*k, 2.9*k)
        g['steel'].cyl((-.5*k, sy*8.4*k, -.4*k), 1.6*k, 1.0*k, 'Y', n=24, bevel=.15*k)  # trunnion boss
        g['dark'].cyl((-.5*k, sy*8.95*k, -.4*k), .8*k, .1*k, 'Y', n=16)
    tubes = g['steel#tubes']
    mouths = []
    for y in (-4.0, -2.0, 0.0, 2.0, 4.0):
        tubes.cyl((8.4*k, y*k, 0), .98*k, 3.8*k, 'X', n=20, bevel=.1*k)
        tubes.cut().cyl((9.4*k, y*k, 0), .66*k, 2.2*k, 'X', n=16)
        g['dark'].cyl((8.25*k, y*k, 0), .66*k, .1*k, 'X', n=16)
        g['mark'].lathe([(.5*k, 0), (.42*k, .5*k), (.18*k, .9*k), (0, 1.05*k)], 12, (8.3*k, y*k, 0), 'X')
        mouths.append((10.3*k, y*k, 0.0))
    for z in (1.15, -1.15):                                                        # tube straps
        g['steel'].box((9.2*k, 0, z*k), (.6*k, 10.8*k, .35*k), .05*k)
    g['steel'].box((-7.3*k, 0, -.6*k), (.5*k, 6.0*k, 3.0*k), .1*k)                 # loading door
    for y in (-1.8, -.6, .6, 1.8):                                                 # its vent slots
        g['dark'].box((-7.56*k, y*k, -.2*k), (.05*k, .6*k, 1.8*k))
    return mouths


HARDPOINT_WEAPONS = {
    # Tachyon lance as a spinal beam projector (the Paragon's bow pair, owner 2026-10-19).
    'tachyonlance': dict(builder=_hp_tachyonlance, chroma=.7,
                         paints={'paint': (.41, .45, .51), 'accent': (.55, .57, .62)}),
    # Autopulse laser (the Paragon Elite's bow pair): cream-white emitter, steel breech,
    # the Tri-Tachyon halfway paints as for its turrets.
    'autopulse': dict(builder=_hp_autopulse, chroma=.7,
                      paints={'paint': (.62, .61, .52), 'accent': (.56, .57, .6), 'accent2': (.72, .72, .74)}),
    # The Wolf (P)'s pirate fit (wolf_d_pirates_Attack): the mining blaster in its bow
    # fork and the Annihilator pods in its missile pods; the sprites' own sand-yellow
    # and warm grey.
    'miningblaster': dict(builder=_hp_miningblaster, paints={'paint': (.43, .41, .2), 'accent': (.5, .47, .22)}),
    'annihilator': dict(builder=_hp_annihilator, paints={'paint': (.42, .36, .27)}),
}


# More hardpoints, one builder each, live in turret_hardpoints.py and register here.
import turret_hardpoints  # noqa: E402
turret_hardpoints.register(sys.modules[__name__])


def build_hardpoint(weapon, prefix, parent, mats, mpu, detail='near', k=1.0, **over):
    """The fixed gun for `weapon` on `parent` (an empty at the slot, +X along the line of
    fire, z=0 on the mount's axis), at k hull units per sprite pixel. Returns {'parts',
    'muzzles' ('<prefix> muzzle anchor <k>' empties, +X along the bore), 'spec'}.
    hull_finish / sprite_tint work as for build()."""
    s = dict(HARDPOINT_WEAPONS[weapon])
    s.update(over)
    s['weapon'] = weapon
    f = Fit(mpu, detail)
    _CAP[0] = CHAMFER_M*f.u
    mats = _sprite_mats(s, mats)
    if s.get('mcol'):
        mats['missile'] = _missile_mat(s['mcol'], painted=True)
    g = Group(prefix, 'hardpoint', mats, parent)
    muzzles = s['builder'](g, f, s, k)
    anchors = []
    for i, (x, y, z) in enumerate(muzzles):
        a = bpy.data.objects.new(f'{prefix} muzzle anchor {i+1}', None)
        bpy.context.collection.objects.link(a)
        a.empty_display_size = 3*k
        a.parent = parent
        a.location = (x, y, z)
        anchors.append(a)
    bay = s.get('bay')
    return {'parts': g.emit(), 'muzzles': anchors, 'spec': s, 'moving': g.all_empties(),
            'bay': tuple(v*k for v in bay) if bay else None}



def _cowl(g, f, s, r, z0):
    """The Vulcan's house, after vulcan_cannon_turret_base.png: a low armoured
    body that is widest just behind the trunnions and narrows to the gun slot,
    a raised cowl on top with two sight ports, a spine ridge to the rear, a
    louvred vent slot across the back and vents in both rear flanks."""
    hb = .3*r                                      # body height
    zt = z0+hb
    body = [(-1.1*r, section(.5*r, z0, zt-.04*r, .1*r, .04*r, .06*r)),
            (-.85*r, section(.8*r, z0, zt, .14*r, .04*r, .08*r)),
            (-.2*r, section(.95*r, z0, zt, .16*r, .04*r, .1*r)),
            (.45*r, section(.9*r, z0, zt, .16*r, .04*r, .1*r)),
            (.8*r, section(.6*r, z0, zt-.02*r, .14*r, .04*r, .08*r)),
            (1.0*r, section(.42*r, z0, zt-.05*r, .1*r, .04*r, .06*r))]
    g['armour'].loft_x(body)
    # The cowl: the sprite's light raised centre, sloping down to the gun slot.
    ht = zt+.2*r
    g['plate'].loft_x([(-.7*r, section(.4*r, zt-.02*r, ht-.08*r, .08*r, 0, .06*r)),
                       (-.3*r, section(.6*r, zt-.02*r, ht, .1*r, 0, .1*r)),
                       (.35*r, section(.62*r, zt-.02*r, ht, .1*r, 0, .1*r)),
                       (.72*r, section(.4*r, zt-.02*r, ht-.1*r, .06*r, 0, .06*r))])
    # Spine ridge from the cowl to the rear.
    g['armour'].box((-.82*r, 0, zt+.02*r), (.56*r, .14*r, .06*r), .02*r)
    # Two sight ports on the cowl's front slope (the sprite's dark pair).
    for sy in (1, -1):
        g['steel'].box((.5*r, sy*.26*r, ht-.03*r), (.18*r, .12*r, .07*r), .015*r)
        g['dark'].box((.595*r, sy*.26*r, ht-.03*r), (.012*r, .09*r, .04*r))
    # Rear vent slot across the back, louvred; flank vents behind the cheeks.
    g['dark'].box((-1.105*r, 0, z0+.15*r), (.02*r, .5*r, .12*r))
    for k in range(3):
        g['steel'].box((-1.11*r, 0, z0+.11*r+k*.04*r), (.02*r, .5*r, .012*r))
    for sy in (1, -1):
        yv = sy*(.82*r)
        g['dark'].box((-.5*r, yv, z0+.16*r), (.26*r, .02*r, .09*r), rot=Matrix.Rotation(sy*.25, 4, 'Z'))
        for k in range(3):
            g['steel'].box((-.5*r, yv+sy*.006*r, z0+.13*r+k*.03*r), (.26*r, .015*r, .008*r),
                           rot=Matrix.Rotation(sy*.25, 4, 'Z'))
    # Plate construction: welds down the body's upper chamfer, bolts on the cowl
    # skirt, lifting eyes, a hatch on the rear deck when it is big enough.
    for sy in (1, -1):
        f.weld(g['steel'], (-.85*r, sy*(.72*r-.14*r), zt), (.45*r, sy*(.8*r-.16*r), zt))
        f.bolt_row(g['steel'], (-.6*r, sy*.55*r, zt+.01*r), (.5*r, sy*.58*r, zt+.01*r))
        f.eye(g['steel'], (-.95*r, sy*.4*r, zt))
    if .4*r > 1.0*f.u:
        f.hatch(g, (-.5*r, 0, ht-.06*r), size=min(.8, .3*r/f.u))
    # Gun slot: a dark recess the full height of the front, where the mantlet rides.
    g['dark'].box((1.0*r, 0, z0+.17*r), (.03*r, .66*r, .2*r))
    bz = z0+.17*r
    return (.78*r, 0.0, bz), {'face': 1.0*r, 'face_h': .2*r, 'z0': z0}


def _twin(g, f, s, r, t, ex):
    """The Vulcan's elevating mass: a mantlet in the gun slot and two barrels
    side by side, each in its own recoil group (they fire alternately), with
    the sprite's banded cooling jackets."""
    bz, face = t[2], ex['face']
    L, rad, sp = s['length']*r, s['calibre']*r, s.get('spacing', .36)*r
    g['steel'].box((face-.02*r, 0, bz), (.16*r, .78*r, .18*r), .03*r)          # mantlet
    f.bolt_row(g['steel'], (face+.06*r, -.33*r, bz+.07*r), (face+.06*r, .33*r, bz+.07*r), axis='X')
    for sy in (1, -1):
        g['steel'].cyl((t[0], sy*.4*r, bz), .07*r, .06*r, 'Y', n=20, bevel=.01*r)     # trunnion pins
    muzzles = []
    for k, sy in enumerate((-1, 1)):
        y = sy*sp/2
        x0 = face+.06*r
        g['dark'].cyl((x0+.005*r, y, bz), rad*1.9, .02*r, 'X', n=20)                   # bore seat
        rg = g.sub(f'recoil {k+1}', (x0, y, bz), .08*r)
        # Breech sleeve, jacket, muzzle: lathed in one, the bore left open.
        jk = rad*1.75
        prof = [(0, 0), (jk, 0), (jk, .7*L), (rad*1.2, .74*L), (rad*1.2, L-.001), (rad*.6, L),
                (rad*.6, L-1.2*rad), (0, L-1.2*rad)]
        rg['steel'].lathe(prof, 20, (x0, y, bz), 'X')
        # Cooling bands: the sprite's light/dark stripes, as rings with slots between.
        for b in range(4):
            xb = x0+(.08+.17*b)*L
            rg['plate'].cyl((xb, y, bz), jk*1.07, .05*L, 'X', n=20, bevel=.005*L)
            if f.near and b < 3:
                for a in range(6):
                    ang = 2*math.pi*a/6
                    rg['dark'].box((xb+.085*L, y+jk*1.0*math.cos(ang), bz+jk*1.0*math.sin(ang)),
                                   (.06*L, .022*r, .022*r))
        muzzles.append((x0+L, y, bz))
    # Feed: an ammunition box on each side under the cowl, belted to the breech.
    for sy in (1, -1):
        g['steel'].box((t[0]-.25*r, sy*.2*r, bz-.02*r), (.22*r, .12*r, .12*r), .015*r)
    return muzzles


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


def _missile_mat(rgb, painted=False):
    """A loaded round's body. painted=True: a sprite-calibrated weathered paint,
    for the hand-built launchers whose rounds the game sprite draws."""
    if painted:
        rgb = livery(rgb, 'missile')
        return paint_material(f'Missile body {_key(rgb)}', rgb)
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


def _missile(g, f, x_tail, y, z, L, rad, rich, roll=0.0):
    """A missile along +X from its tail: nozzle, body, ogive warhead, cruciform
    fins (rolled by `roll` radians, so a big one can lie low in its cradle)."""
    body = [(rad*.55, 0), (rad*.8, .03*L), (rad, .06*L), (rad, .72*L), (rad*.96, .78*L), (rad*.8, .86*L),
            (rad*.55, .93*L), (rad*.22, .985*L), (0, L)]
    g['missile'].lathe(body, 20, (x_tail, y, z), 'X')
    g['dark'].cyl((x_tail-.004*L, y, z), rad*.45, .01*L, 'X', n=16)
    g['armour'].cyl((x_tail+.75*L, y, z), rad*1.01, .04*L, 'X', n=20)     # warhead band
    fin = .7*rad
    for ang in (0, math.pi/2, math.pi, 1.5*math.pi):
        ang += roll
        cy, cz = math.cos(ang)*(rad+fin/2), math.sin(ang)*(rad+fin/2)
        g['missile'].box((x_tail+.1*L, y+cy, z+cz), (.16*L, fin, .04*rad+.01), .01*rad,
                         rot=Matrix.Rotation(ang, 4, 'X'))
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
