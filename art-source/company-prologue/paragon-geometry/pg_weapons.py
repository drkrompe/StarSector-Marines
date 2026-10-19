"""The Paragon's guns, built to their game sprites (graphics/weapons, Elite variant).

The game draws a turret sprite at one pixel per hull unit about its centre, top
row forward. Each builder places clean parts at measurements read off its
sprite at 10x, in sprite pixels (= hull units), +X forward, +Y port, the pivot at
the origin, its foot at z_off (just under the well's rim), and returns the parts
and the muzzle points (the .wpn fire offsets). Silhouettes are held to the
sprite's alpha by `sprite_iou`; the builds report it per weapon (0.95-0.98).
Each gun stands on a dark base plate cut to the sprite's traced alpha outline
(simplified to straight runs), so the sprite's dark gaps between parts read as
the base showing between the raised plates built over it. Never per-pixel relief: a shading pixel is not a
part (owner, pass 3).

- tachyon lance (LARGE, WS 003/004): two long prong blades over a dark emitter
  channel with a glow line, a body block, capacitor pods on arms either side
  with red lamps, a fan of six fins aft;
- graviton beam (MEDIUM): a blue-white teardrop housing with a lens at its point
  and a dark spine, swept wings, two feet aft;
- tactical laser (SMALL): a faceted dome behind a short emitter spike;
- reaper launcher (SMALL missile): two rails, a launcher box with a round
  loaded, a base bar with red lamps.
"""
import math
import pg_kit as K

WEAPON_OF = {'LARGE': 'tachyonlance', 'MEDIUM': 'gravitonbeam', 'SMALL': 'taclaser'}


def weapon_for(size, typ, elite):
    if typ == 'MISSILE':
        return 'reaper'
    if elite in ('tachyonlance', 'gravitonbeam', 'taclaser'):
        return elite
    return WEAPON_OF[size]


def _blade(kit, name, x0, x1, y, w0, w1, z0, z1, mat):
    """A tapered blade along +X: width w0 at x0 to w1 at x1, its top dropping a little to the tip."""
    verts = []
    for x, w, dz in ((x0, w0, 0.0), (x1, w1, (z1-z0)*.4)):
        verts += [(x, y-w/2, z0), (x, y+w/2, z0), (x, y+w/2, z1-dz), (x, y-w/2, z1-dz)]
    faces = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
    return kit.mesh(name, verts, faces, mat, 0, False)


def _sym(half):
    return K.ccw(list(half)+[(x, -y) for x, y in reversed(half) if y > 0])


def _base(kit, tag, weapon, z, h=1.3, mat=None):
    """The gun's dark base plate: the sprite's own alpha outline (traced), bevelled, its foot at z."""
    import pg_weapon_sprites as S
    d = S.SPRITES[weapon]
    return K.profile_mass(kit, f'{tag} base', [tuple(p) for p in d['outline']],
                          [(0, z+h*.55), (.5, z+h)], [(0, z)], holes=[[tuple(p) for p in hl] for hl in d['holes']],
                          mat=mat or kit.slate, step=.6)


def tachyonlance(kit, tag, z):
    p = [_base(kit, tag, 'tachyonlance', z)]
    p.append(K.profile_mass(kit, f'{tag} body', _sym([(0, 0.0), (0, 13), (-3, 15), (-8, 15), (-9, 11), (-8, 4), (-7, 0.0)]),
                            [(0, z+1.6), (1.2, z+2.8), (3, z+3.2)], [(0, z)], mat=kit.pale, step=.8))
    for j, side in enumerate((-1, 1), start=1):
        p.append(K.profile_mass(kit, f'{tag} prong {j}', K.ccw([(33.5, 2.6*side), (30, 6.6*side), (2, 8.6*side),
                                                                 (2, 2.4*side), (19, 2.4*side)]),
                                [(0, z+1.6), (.8, z+2.6), (1.6, z+2.9)], [(0, z)], mat=kit.pale, step=.5))
        p.append(_blade(kit, f'{tag} prong {j} rib', 3, 30, 4.2*side, 1.6, .9, z+2.6, z+3.4, kit.pale))
        p.append(kit.box(f'{tag} pod arm {j}', (2.5, 13.5*side, z+1.6), (5.0, 6.0, 1.6), kit.slate, .15))
        p.append(kit.cylinder(f'{tag} capacitor {j}', (5.5, 19.6*side, z+2.6), 3.0, 8.0, kit.steel, axis='X',
                              vertices=12, bevel=0))
        p.append(kit.box(f'{tag} capacitor {j} lamp', (9.6, 19.6*side, z+2.6), (.5, 1.4, 1.4), kit.glow_red, 0))
    fins = (((-9, 3), (-21.5, 3)), ((-9, 8.5), (-20.5, 10.6)), ((-8, 12.5), (-17.4, 17.8)), ((-5, 15), (-11.6, 20.6)))
    for j, ((x0, y0), (x1, y1)) in enumerate(fins, start=1):
        for k, side in enumerate((-1, 1), start=1):
            L = math.hypot(x1-x0, y1-y0)
            fin = kit.box(f'{tag} fin {j}{"ab"[k-1]}', (0, 0, 0), (L, 1.2, .9), kit.slate, .1)
            fin.location = ((x0+x1)/2, (y0+y1)/2*side, z+1.6)
            fin.rotation_euler = (0, 0, math.atan2((y1-y0)*side, x1-x0))
            p.append(fin)
    p.append(kit.box(f'{tag} emitter', (2.4, 0, z+1.8), (1.2, 2.4, 1.6), kit.tech, 0))
    p.append(kit.box(f'{tag} emitter line', (3.1, 0, z+2.2), (.4, 1.4, .6), kit.glow, 0))
    return p


def gravitonbeam(kit, tag, z):
    p = [_base(kit, tag, 'gravitonbeam', z, mat=kit.weapon_blue)]
    p.append(K.profile_mass(kit, f'{tag} housing', _sym([(20.5, 0.0), (17, 2.6), (11, 4.0), (2, 5.0), (-5, 4.5),
                                                         (-7, 0.0)]),
                            [(0, z+1.4), (1.5, z+2.6), (3, z+3.0)], [(0, z)], mat=kit.weapon_blue, step=.6))
    for j, side in enumerate((-1, 1), start=1):
        p.append(K.profile_mass(kit, f'{tag} wing {j}', K.ccw([(8, 5.4*side), (-1, 10*side), (-6, 10*side),
                                                               (-5, 5.4*side)]),
                                [(0, z+1.3), (1, z+2.0)], [(0, z)], mat=kit.weapon_blue, step=.5))
        p.append(kit.box(f'{tag} foot {j}', (-7.5, 8.2*side, z+1.4), (2.0, 2.0, 1.0), kit.slate, .1))
    p.append(kit.box(f'{tag} spine', (5, 0, z+3.0), (14, 1.4, .5), kit.tech, 0))
    lens = K.revolve(kit, f'{tag} lens', [(0, 0), (1.6, 0), (1.3, .8), (0, 1.2)], kit.glow, segments=16, smooth=40)
    lens.rotation_euler = (0, math.pi/2, 0)
    lens.location = (15.5, 0, z+1.8)
    p.append(lens)
    return p


def taclaser(kit, tag, z):
    p = [_base(kit, tag, 'taclaser', z, h=1.0)]
    dome = K.revolve(kit, f'{tag} housing', [(0, 0), (7.2, 0), (7.2, .9), (5.6, 2.7), (2.8, 3.5), (0, 3.6)],
                     kit.pale, segments=10, smooth=False)
    dome.location = (-1.5, 0, z)
    p.append(dome)
    spike = K.revolve(kit, f'{tag} emitter', [(0, 0), (1.2, 0), (.6, 6.0), (0, 6.3)], kit.steel, segments=12, smooth=40)
    spike.rotation_euler = (0, math.pi/2, 0)
    spike.location = (5.0, .5, z+2.0)
    p.append(spike)
    p.append(kit.cylinder(f'{tag} emitter collar', (9.8, .5, z+2.0), .7, .6, kit.glow_white, axis='X', vertices=12,
                          bevel=0))
    return p


def reaper(kit, tag, z):
    p = [_base(kit, tag, 'reaper', z, h=1.0)]
    for j, side in enumerate((-1, 1), start=1):
        p.append(kit.box(f'{tag} rail {j}', (3, 4.6*side, z+1.4), (18, 2.4, 2.0), kit.steel, .15))
    p.append(kit.box(f'{tag} launcher', (-.2, 0, z+1.7), (5.6, 11, 2.6), kit.pale, .3))
    p.append(kit.box(f'{tag} launcher breech', (-5.3, 0, z+1.5), (1.6, 11, 2.2), kit.pale, .2))
    p.append(kit.cylinder(f'{tag} round', (-.2, 0, z+2.1), .9, 5.0, kit.tech, axis='X', vertices=12, bevel=0))
    p.append(kit.box(f'{tag} base bar', (-8.3, 0, z+1.2), (2.6, 15, 1.2), kit.slate, .2))
    for j, side in enumerate((-1, 1), start=1):
        p.append(kit.box(f'{tag} lamp {j}', (-7.3, 4.5*side, z+1.9), (1.0, 1.0, .4), kit.glow_red, 0))
    return p


BUILDERS = {'tachyonlance': tachyonlance, 'gravitonbeam': gravitonbeam, 'taclaser': taclaser, 'reaper': reaper}


def build_gun(kit, tag, weapon, z):
    import pg_weapon_sprites as S
    parts = BUILDERS[weapon](kit, tag, z)
    top = z+3.0
    muzzles = [(fx, fy, top) for fx, fy in S.SPRITES[weapon]['offsets']]
    return parts, muzzles


def sprite_iou(parts, weapon):
    """The gun's top silhouette against its sprite's alpha, on the sprite's 2x grid (parts built at the origin)."""
    import bpy
    from mathutils import Vector
    from mathutils.bvhtree import BVHTree
    import pg_weapon_sprites as S
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    trees = []
    for ob in parts:
        ev = ob.evaluated_get(dg)
        me = ev.to_mesh()
        trees.append(BVHTree.FromPolygons([ev.matrix_world @ v.co for v in me.vertices], [tuple(f.vertices) for f in me.polygons]))
        ev.to_mesh_clear()
    d = S.SPRITES[weapon]
    up, (w, h), rows = d['up'], d['size'], d['rows']
    cell = 1.0/up
    inter = union = 0
    mask = []
    for i in range(h):
        line = ''
        for j in range(w):
            x, y = (h/2-i-.5)*cell, (w/2-j-.5)*cell
            model = any(t.ray_cast(Vector((x, y, 200)), Vector((0, 0, -1)))[0] is not None for t in trees)
            spr = rows[i][j] != '.'
            inter += model and spr
            union += model or spr
            line += 'M' if model else '.'
        mask.append(line)
    import os
    if os.environ.get('PG_GUN_MASK_DIR'):
        import json
        with open(os.path.join(os.environ['PG_GUN_MASK_DIR'], weapon+'.json'), 'w') as f:
            json.dump(mask, f)
    return inter/union if union else 0.0
