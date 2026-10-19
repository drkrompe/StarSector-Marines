"""The Falcon's hull: two armoured prongs, the central body and bridge dome,
the waist with its exposed tanks, the outrigger thruster pods and the bow
forks round the WS 007 / WS 008 hardpoints.

Read off falcon_p.png and falcon_base.png (162 x 222, .ship centre (81, 88)
from the bottom edge): sprite column c, row r (from the top) covers
X 134-r..135-r and Y 80-c..81-c. Heights are proposed art; the game gives
none.

- Prongs (`Hull <side> prong`): the long armoured wedges that carry the
  turrets, X -46 to 108, inner wall at Y +-10..13, the outer edge running from
  Y 62 aft to 32 at the bow. Each is lofted through stations with one inner
  wall, a deck rising to a ridge on the turret line, a steep outer glacis and
  a chine, so the side profile is one wall and two chamfers, never a blob.
  They are 21 units deep aft and 10 at the bow.
- Central body (`Hull central body`): the keel between the prong roots,
  X -52 to 18, Y +-13, carrying the bridge dome (`Bridge command dome`) at
  X 5 and the dorsal machinery spine aft of it.
- Waist (`Hull <side> waist deck`): the lower deck between the prongs' aft
  taper and the drive blocks, where the pirates' patchwork shows: a big
  bare-metal tank on each side (`Hull <side> waist tank`, X -44 to -16).
- Drive blocks (`Hull <side> drive block`): the housings of the six main
  drives aft, X -78 to -37, Y 8 to 46 (fc_engines puts the drives in them).
- Outrigger pods (`Hull <side> outrigger pod`): the bracketed thruster pods
  beside the prongs' aft ends, Y 55 to 80, each holding three manoeuvring
  thrusters on its aft diagonal face and a small missile hardpoint.
- Bow forks (`Hull <side> bow fork outer|inner jaw`): the two jaws at each
  prong's tip, either side of the hardpoint at Y +-22.
"""
import math
import bpy
from fc_kit import loft_x, prism, section, sym, mirror_y, revolve_x, boolean, plane_cut

# Prong stations: x, inner wall y, outer edge y, deck z, keel z.
PRONG = [(108, 9.5, 33, 5.0, -5.0), (80, 10.5, 42, 7.0, -7.0), (47, 12, 50, 9.0, -8.5),
         (20, 9, 60, 10.5, -10.0), (-12, 10, 62.5, 11.0, -10.5), (-30, 30, 61.5, 9.0, -8.5),
         (-39, 41, 60, 7.0, -6.5), (-47, 45.5, 51, 4.0, -3.5)]


def ridge_y(x):
    """The deck's ridge runs through both turret rings: (14, 38) and (55, 29)."""
    return 38.0-0.2195*(x-14)


def prong_section(x, yi, yo, zt, zb, side):
    """YZ ring for one station (port values; side -1 mirrors)."""
    w = yo-yi
    h = zt-zb
    yr = min(max(ridge_y(x), yi+.35*w), yo-.3*w)
    pts = [(yi, zb+.12*h), (yi, zt-.06*h), (yi+.05*w, zt), (yr, zt+.08*h),
           (yo-.1*w, zt-.30*h), (yo, zb+.42*h), (yo-.12*w, zb), (yi+.1*w, zb)]
    if side < 0:
        pts = [(-y, z) for y, z in reversed(pts)]
    return pts


def prongs(kit):
    out = []
    for side, label in ((1, 'port'), (-1, 'starboard')):
        st = [(x, prong_section(x, yi, yo, zt, zb, side)) for x, yi, yo, zt, zb in reversed(PRONG)]
        ob = loft_x(kit, f'Hull {label} prong', st, kit.armour, bevel=.15)
        out.append(ob)
    return out


def central_body(kit):
    plan = sym([(17.5, 6), (14.5, 11), (6, 13.5), (-12, 13.5), (-44, 10), (-47, 6)])
    body = prism(kit, 'Hull central body', plan, section(-9.0, 9.5, 2.0, 2.5), kit.plate, bevel=.15)
    # The bridge dome: a low faceted revolve about Z at X 5, its crown at Z 18.
    prof = [(0, 18.0), (4.5, 17.6), (8.0, 16.2), (10.8, 13.8), (12.6, 10.8), (13.2, 8.0), (13.0, 6.0), (0, 6.0)]
    n = 20
    verts, faces = [], []
    for k in range(n):
        a = 2*math.pi*k/n
        for r, z in prof:
            verts.append((4+r*math.cos(a), r*math.sin(a), z))
    m = len(prof)
    for k in range(n):
        k2 = (k+1) % n
        for i in range(m):
            i2 = (i+1) % m
            faces.append((k*m+i, k*m+i2, k2*m+i2, k2*m+i))
    dome = kit.mesh('Bridge command dome', verts, faces, kit.plate)
    import bmesh
    me = dome.data
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.remove_doubles(bm, verts=bm.verts, dist=1e-4)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    return body, dome


def waist(kit):
    out = []
    port = [(-12, 10), (-12, 30), (-28, 44), (-48, 44), (-52, 10)]
    for label, plan in (('port', port), ('starboard', mirror_y(port))):
        out.append(prism(kit, f'Hull {label} waist deck', plan, section(-7.5, 3.0, 1.5, 1.2), kit.plate, bevel=.12))
    for side, label in ((1, 'port'), (-1, 'starboard')):
        y = 29.0*side
        prof = [(-45, 0), (-45, 4.0), (-43.5, 5.6), (-17.5, 5.6), (-16, 4.0), (-16, 0)]
        out.append(revolve_x(kit, f'Hull {label} waist tank', prof, y, 6.5, kit.bare, 16))
    return out


def drive_blocks(kit):
    out = []
    port = [(-40, 8.5), (-40, 40), (-50, 41.5), (-73, 47), (-79, 44), (-79, 9), (-50, 8)]
    for label, plan in (('port', port), ('starboard', mirror_y(port))):
        out.append(prism(kit, f'Hull {label} drive block', plan, section(-8.0, 6.5, 1.8, 2.2), kit.steel, bevel=.15))
    return out


def outriggers(kit):
    out = []
    port = [(-8, 63), (-8, 72), (-13, 79.5), (-30, 80), (-36, 77), (-40, 63), (-34, 59), (-20, 60)]
    for label, plan in (('port', port), ('starboard', mirror_y(port))):
        out.append(prism(kit, f'Hull {label} outrigger pod', plan, section(-4.0, 4.0, 1.0, 1.2), kit.steel, bevel=.12))
    return out


def bow_forks(kit):
    out = []
    jaws = {'outer': (26.0, 31.5, 125.0), 'inner': (10.0, 16.5, 127.0)}
    for side, label in ((1, 'port'), (-1, 'starboard')):
        for jaw, (y0, y1, x1) in jaws.items():
            ya, yb = sorted((y0*side, y1*side))
            # A wedge in plan: full width at the prong's bow face, raked toward the tip.
            plan = [(104, ya), (x1-4, ya), (x1, (ya+yb)/2), (x1-4, yb), (104, yb)]
            out.append(prism(kit, f'Hull {label} bow fork {jaw} jaw', plan, section(-3.6, 3.6, 1.0, 1.4),
                             kit.armour, bevel=.1))
    return out


def build(kit):
    parts = prongs(kit)
    body, dome = central_body(kit)
    parts += [body, dome]
    parts += waist(kit)
    parts += drive_blocks(kit)
    parts += outriggers(kit)
    parts += bow_forks(kit)
    kit.hull.extend(parts)
    return parts
