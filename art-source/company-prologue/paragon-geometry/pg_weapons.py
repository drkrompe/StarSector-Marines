"""The Paragon's guns, read off the game's weapon sprites (graphics/weapons, Elite variant).

Each builder makes a turret's trainable parts at the origin, facing +X, with the
pivot (the traverse gap on the barbette's top) at z = 0, and returns the parts
and the muzzle points. pg_turrets parents them to the slot's pivot. They sit low
in their dimpled wells, filling them (owner, pass 3: the mounts read empty;
never lift a turret out of the hull). Sizes follow the slot's barbette radius.

- tachyon lance (LARGE, WS 003/004): a forked pair of long prong blades with a
  dark emitter channel and a glow line between them, capacitor pods at the
  shoulders, a fan of fins aft;
- graviton beam (MEDIUM): a faceted teardrop housing pointing forward with a
  lens at its point, swept shoulder wings;
- tactical laser (SMALL): a round faceted dome with a short emitter spike;
- reaper launcher (SMALL missile): a launcher box between two rails, a round in
  the rails, two status lamps.
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
    """A tapered blade along +X: width w0 at x0 to w1 at x1, a little drop at the tip."""
    verts = []
    for x, w, dz in ((x0, w0, 0.0), (x1, w1, (z1-z0)*.35)):
        verts += [(x, y-w/2, z0), (x, y+w/2, z0), (x, y+w/2, z1-dz), (x, y-w/2, z1-dz)]
    faces = [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)]
    return kit.mesh(name, verts, faces, mat, 0, False)


def tachyonlance(kit, tag, s):
    r = s['r']
    k = r/16.0
    parts = [K.revolve(kit, f'{tag} housing', [(0, 0), (10.5*k, 0), (10.5*k, 1.2*k), (8.5*k, 3.2*k), (5.0*k, 3.8*k),
                                               (0, 3.8*k)], kit.pale, segments=8, smooth=False)]
    for j, y in enumerate((-3.4*k, 3.4*k), start=1):
        parts.append(_blade(kit, f'{tag} prong {j}', -2*k, 25*k, y, 3.0*k, 1.0*k, 2.6*k, 5.4*k, kit.pale))
    parts.append(kit.box(f'{tag} emitter channel', (9*k, 0, 3.4*k), (20*k, 3.0*k, 1.4*k), kit.tech, 0))
    parts.append(kit.box(f'{tag} emitter line', (11*k, 0, 4.15*k), (20*k, .5*k, .25*k), kit.glow, 0))
    for j, y in enumerate((-8.2*k, 8.2*k), start=1):
        parts.append(kit.cylinder(f'{tag} capacitor {j}', (1.0*k, y, 2.2*k), 2.0*k, 7.0*k, kit.steel, axis='X',
                                  vertices=12, bevel=0))
        parts.append(kit.box(f'{tag} capacitor {j} light', (4.6*k, y, 2.2*k), (.4*k, .9*k, .9*k), kit.glow, 0))
    for j, a in enumerate((-55, -32, -12, 12, 32, 55), start=1):
        fin = kit.box(f'{tag} fin {j}', (0, 0, 0), (9*k, .6*k, 1.6*k), kit.slate, 0)
        t = math.radians(180+a)
        fin.location = (-4*k+math.cos(t)*4.5*k, math.sin(t)*4.5*k, 1.6*k)
        fin.rotation_euler = (0, 0, t)
        parts.append(fin)
    return parts, [(25.5*k, 0, 4.0*k)]


def gravitonbeam(kit, tag, s):
    k = s['r']/10.5
    plan = [(11*k, 0), (7*k, 2.6*k), (1*k, 5.6*k), (-4*k, 6.0*k), (-6.5*k, 3.2*k), (-6.5*k, 0)]
    plan = K.ccw(plan+[(x, -y) for x, y in reversed(plan) if y > 0])
    parts = [K.profile_mass(kit, f'{tag} housing', plan, [(0, .6*k), (1.6*k, 3.0*k), (3.2*k, 3.6*k)], [(0, 0)],
                            mat=kit.pale, step=.6*k)]
    for j, side in enumerate((-1, 1), start=1):
        wing = [(1*k, side*4.5*k), (-3*k, side*8.5*k), (-6*k, side*8.0*k), (-5*k, side*4.0*k)]
        parts.append(K.profile_mass(kit, f'{tag} wing {j}', K.ccw(wing), [(0, .4*k), (.8*k, 1.6*k)], [(0, 0)],
                                    mat=kit.pale, step=.5*k))
    lens = K.revolve(kit, f'{tag} lens', [(0, 0), (1.3*k, 0), (1.0*k, .7*k), (0, 1.0*k)], kit.glow, segments=16,
                     smooth=40)
    lens.rotation_euler = (0, math.pi/2, 0)
    lens.location = (8.6*k, 0, 2.0*k)
    parts.append(lens)
    parts.append(kit.box(f'{tag} spine', (-1*k, 0, 3.5*k), (8*k, 1.2*k, .5*k), kit.slate, 0))
    return parts, [(11.3*k, 0, 2.0*k)]


def taclaser(kit, tag, s):
    k = s['r']/6.5
    parts = [K.revolve(kit, f'{tag} housing', [(0, 0), (4.6*k, 0), (4.6*k, .7*k), (3.6*k, 2.2*k), (1.8*k, 2.8*k),
                                               (0, 2.9*k)], kit.pale, segments=10, smooth=False)]
    spike = K.revolve(kit, f'{tag} emitter', [(0, 0), (.9*k, 0), (.45*k, 6*k), (0, 6.3*k)], kit.steel, segments=12,
                      smooth=40)
    spike.rotation_euler = (0, math.pi/2, 0)
    spike.location = (2.6*k, 0, 1.7*k)
    parts.append(spike)
    parts.append(kit.cylinder(f'{tag} emitter collar', (8.4*k, 0, 1.7*k), .55*k, .5*k, kit.glow_white, axis='X',
                              vertices=12, bevel=0))
    return parts, [(9.0*k, 0, 1.7*k)]


def reaper(kit, tag, s):
    k = s['r']/6.5
    parts = [kit.box(f'{tag} base', (0, 0, .5*k), (6.0*k, 5.6*k, 1.0*k), kit.slate, .15*k),
             kit.box(f'{tag} launcher', (-.6*k, 0, 1.9*k), (4.4*k, 4.4*k, 1.9*k), kit.pale, .2*k)]
    for j, y in enumerate((-1.7*k, 1.7*k), start=1):
        parts.append(kit.box(f'{tag} rail {j}', (3.2*k, y, 2.0*k), (5.2*k, .5*k, .8*k), kit.steel, 0))
        parts.append(kit.box(f'{tag} lamp {j}', (-2.9*k, y, 2.4*k), (.3*k, .6*k, .5*k), kit.glow_white, 0))
    parts.append(kit.cylinder(f'{tag} round', (3.0*k, 0, 2.0*k), .55*k, 4.6*k, kit.tech, axis='X', vertices=10,
                              bevel=0))
    return parts, [(5.9*k, -1.0*k, 2.0*k), (5.9*k, 1.0*k, 2.0*k)]


BUILDERS = {'tachyonlance': tachyonlance, 'gravitonbeam': gravitonbeam, 'taclaser': taclaser, 'reaper': reaper}
