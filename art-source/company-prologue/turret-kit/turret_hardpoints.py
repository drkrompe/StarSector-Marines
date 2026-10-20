"""Hardpoints (fixed mounts) for the turret kit, one builder per weapon.

A builder draws its weapon in sprite pixels times k, +X along the line of fire, the
slot at the origin, z=0 on the mount's axis (turret_kit.py, Hardpoints), in the same
visual language as the kit's turrets: a faired or recessed housing with a visible
weapon face, barrels on 'recoil k' sub-groups (g.sub) and loaded rounds on
'missile k' sub-groups, returning one muzzle point per barrel or tube (at its mouth).
An entry's `bay` is the pocket a hull cuts for it: (x0, x1) along the fire line, the
half-width, and the floor below the axis; the housing is open to the deck above it.

register(tk) adds every entry to tk.HARDPOINT_WEAPONS. Kept apart from turret_kit.py
so hardpoint work and turret work do not edit the same file.
"""
import math
from mathutils import Matrix, Vector  # noqa: F401

tk = None


def _dz_loft(m, stations, axis='X'):
    """A closed solid through loops [(t, [(u, v), ...]), ...] (hull units) along `axis`:
    X lays them out as (t, u, v), Y as (u, t, v), Z as (u, v, t); capped at both ends."""
    def at(t, u, v):
        return (t, u, v) if axis == 'X' else (u, t, v) if axis == 'Y' else (u, v, t)
    rings = [[m.bm.verts.new(m.base @ Vector(at(t, u, v))) for u, v in loop] for t, loop in stations]
    n = len(rings[0])
    for a, b in zip(rings, rings[1:]):
        for j in range(n):
            m.bm.faces.new((a[j], a[(j+1) % n], b[(j+1) % n], b[j]))
    m.bm.faces.new(rings[0])
    m.bm.faces.new(rings[-1])


def _dz_block(m, k, c, d, ch=.15, axis='X', taper=(1.0, 1.0)):
    """A block centred c with full sizes d (x, y, z), sprite px x k, laid along `axis`:
    every edge chamfered by ch (a real facet, not the kit's capped edge bevel), and
    its +axis end scaled by taper (across, up), so a prong or a lug narrows to its tip."""
    i = 'XYZ'.index(axis)
    u_i, v_i = [j for j in range(3) if j != i]
    a0, a1 = c[i]-d[i]/2, c[i]+d[i]/2
    cu, cv, wu, wv = c[u_i], c[v_i], d[u_i], d[v_i]
    tu, tv = taper
    ch = max(min(ch, .3*min(wu*tu, wv*tv), .3*(a1-a0)), 1e-3)

    def loop(su, sv, inset):
        hu, hv = su*wu/2-inset, sv*wv/2-inset
        cc = max(min(ch, .45*hu, .45*hv), 1e-3)
        return [((cu+u)*k, (cv+v)*k) for u, v in tk.section(hu, -hv, hv, cc, cc)]
    st = [(a0, loop(1, 1, ch)), (a0+ch, loop(1, 1, 0)), (a1-ch, loop(tu, tv, 0)), (a1, loop(tu, tv, ch))]
    _dz_loft(m, [(t*k, L) for t, L in st], axis)


def _dz_bolt_ring(mesh, f, k, c, R, n, axis='X', r=.1, h=.1, phase=.5):
    """n bolt heads on a circle of radius R (sprite px) about c, on a face square to `axis`."""
    if not f.near:
        return
    i = 'XYZ'.index(axis)
    u_i, v_i = [j for j in range(3) if j != i]
    for j in range(n):
        a = 2*math.pi*(j+phase)/n
        p = list(c)
        p[u_i] += R*math.cos(a)
        p[v_i] += R*math.sin(a)
        mesh.cyl(tuple(q*k for q in p), r*k, h*k, axis, n=6)


def _hp_lightag(g, f, s, k=1.0):
    """light_assault_gun_hardpoint_base.png (14 x 26 px; the Lasher's WS 002 / WS 003).
    A faired gun housing set into its bay: the armoured shell (the sprite's dark
    frame) from 5 px behind the slot to a stepped glacis 7 px ahead, plate seams cut
    along and down its flanks, weld beads on its glacis line, louvres in the front
    roof and a bolted access plate on its back; its two gold ammunition drums (end
    rims, recessed end faces with a bolted hub, ribs, buckled straps) lying in troughs
    either side of the pale breech cover (the sprite's gold lobes and its lit centre
    strip; the cover hinged at the back, latched at the front, slotted), each fed
    through a chute into the breech; channelled side rails bolted along its flanks.
    The front is a chamfered mantlet with the barrel's port and a groove ringing it,
    and the two pale guide prongs with gold tips, tapered and grooved, run out either
    side of the barrel (rows +8..+11) on a tie bar. The barrel (the recoil sprite: a
    2-px barrel through a 4-px fluted shroud to its ported muzzle brake 18.5 px ahead)
    rides 'recoil 1' through a fixed recoil sleeve with a notched gland nut. Muzzle
    anchor at the mouth. Built to a bay 9.4 wide: the sprite's outer frame pixels are
    inside the hull. The bay this wants is HARDPOINT_WEAPONS['lightag']['bay']."""
    sh = g['paint#housing']
    plan = tk._ccw([(-5.0, -4.4), (5.6, -4.4), (7.2, -3.4), (7.2, 3.4), (5.6, 4.4), (-5.0, 4.4)])
    sh.prism([(x*k, y*k) for x, y in plan], -2.8*k, 1.4*k, .7*k, 2.1*k, glacis=True)
    for sy in (1, -1):                                   # troughs the drums lie in
        sh.cut().cyl((-.2*k, sy*2.25*k, 2.1*k), 1.45*k, 7.6*k, 'X', n=24)
    sh.cut().box((-.2*k, 0, 2.3*k), (8.2*k, 1.4*k, 1.4*k))   # the breech cover's channel
    for sy in (1, -1):
        sh.cut().box((.3*k, sy*4.4*k, .55*k), (9.4*k, .26*k, .16*k))       # plate seam along the flank
        sh.cut().box((-2.4*k, sy*4.4*k, -1.25*k), (.16*k, .26*k, 3.2*k))   # and down it
        for xx in (4.35, 5.0, 5.65):                                        # louvres in the front roof
            sh.cut().box((xx*k, sy*2.25*k, 2.1*k), (.28*k, 1.8*k, .36*k))
            g['dark'].box((xx*k, sy*2.25*k, 1.95*k), (.24*k, 1.7*k, .04*k))
        f.weld(g['steel'], (-5.0*k, sy*4.42*k, 1.4*k), (5.6*k, sy*4.42*k, 1.4*k))
    # Bolted access plate on the housing's back.
    _dz_block(g['plate'], k, (-5.1, 0, -.7), (.24, 5.0, 2.8), ch=.08)
    for zz in (.5, -1.9):
        f.bolt_row(g['steel'], (-5.24*k, -2.1*k, zz*k), (-5.24*k, 2.1*k, zz*k), axis='X')
    for sy in (1, -1):
        drum = g['accent']
        c = (-.2*k, sy*2.25*k, 1.75*k)
        # Rim, recessed end face and a hub boss at each end of the drum.
        drum.lathe([(.5*k, -3.6*k), (.5*k, -3.4*k), (1.02*k, -3.4*k), (1.1*k, -3.52*k), (1.22*k, -3.52*k),
                    (1.3*k, -3.4*k), (1.3*k, 3.4*k), (1.22*k, 3.52*k), (1.1*k, 3.52*k), (1.02*k, 3.4*k),
                    (.5*k, 3.4*k), (.5*k, 3.6*k)], 24, c, 'X')
        for xx in (-1.0, .6):                                # rolled stiffening ribs
            drum.ring((xx*k, sy*2.25*k, 1.75*k), 1.29*k, 1.37*k, .22*k, 'X', n=24)
        for xe in (-3.48, 3.08):                             # hub bolts on both end faces
            _dz_bolt_ring(g['steel'], f, k, (xe, sy*2.25, 1.75), .76, 6, 'X', r=.08, h=.1)
        for xx in (-2.6, 2.2):                               # straps over each drum, buckled on top
            g['steel'].ring((xx*k, sy*2.25*k, 1.75*k), 1.3*k, 1.42*k, .35*k, 'X', n=24)
            _dz_block(g['steel'], k, (xx, sy*2.25, 3.2), (.5, .6, .16), ch=.05)
        # Feed chute from the drum into the breech, its throat dark.
        _dz_block(g['steel'], k, (3.5, sy*1.5, 1.95), (1.2, .8, .95), ch=.15, axis='Y')
        g['dark'].box((3.5*k, sy*1.3*k, 1.9*k), (.9*k, .55*k, .6*k))      # feed throat into the breech
        # Side rail: a channelled bar, bolted through its web.
        _dz_block(g['steel'], k, (1.0, sy*4.65, -.9), (11.5, .5, 1.3), ch=.12)
        for zz in (-.45, -1.35):
            g['steel'].cut().box((1.0*k, sy*4.9*k, zz*k), (11.0*k, .14*k, .18*k))
        f.bolt_row(g['steel'], (-4.2*k, sy*4.92*k, -.9*k), (6.4*k, sy*4.92*k, -.9*k), axis='Y')
    # Breech cover: chamfered, slotted, hinged at the back and latched at the front.
    cover = g['plate']
    _dz_block(cover, k, (-.2, 0, 2.15), (8.0, 1.2, .55), ch=.16)
    for xx in (-3.0, -1.0, 1.0, 3.0):
        cover.cut().box((xx*k, 0, 2.43*k), (.25*k, .9*k, .2*k))
        g['dark'].box((xx*k, 0, 2.33*k), (.25*k, .9*k, .04*k))
    for yy in (-.38, .38):
        g['steel'].cyl((-4.3*k, yy*k, 2.25*k), .17*k, .32*k, 'Y', n=18)
    g['steel'].cyl((-4.3*k, 0, 2.25*k), .09*k, 1.2*k, 'Y', n=12)              # hinge pin
    _dz_block(g['steel'], k, (3.9, 0, 2.22), (.22, .5, .34), ch=.05)          # latch
    # Mantlet: a chamfered plate across the glacis front, the port cut through it.
    face = g['steel#face']
    _dz_block(face, k, (7.5, 0, -.6), (.7, 6.6, 4.0), ch=.22, taper=(.94, .93))
    face.cut().cyl((7.5*k, 0, 0), 1.0*k, 2*k, 'X', n=20)
    face.cut().ring((7.85*k, 0, 0), 1.5*k, 1.66*k, .2*k, 'X', n=24)
    f.bolt_row(g['steel'], (7.88*k, -2.8*k, 1.1*k), (7.88*k, 2.8*k, 1.1*k), axis='X')
    f.bolt_row(g['steel'], (7.88*k, -2.8*k, -2.3*k), (7.88*k, 2.8*k, -2.3*k), axis='X')
    # The guide prongs either side of the barrel, tapered and grooved, gold tips.
    for sy in (1, -1):
        _dz_block(g['plate'], k, (9.3, sy*2.5, -.2), (3.8, .9, 1.2), ch=.14, taper=(.86, .82))
        _dz_block(g['accent'], k, (11.5, sy*2.5, -.2), (.8, .78, .98), ch=.1, taper=(.7, .6))
        g['plate'].cut().box((9.3*k, sy*2.5*k, .38*k), (2.6*k, .3*k, .2*k))
        g['dark'].box((9.3*k, sy*2.5*k, .3*k), (2.6*k, .28*k, .04*k))
    _dz_block(g['steel'], k, (8.2, 0, -1.6), (1.8, 5.9, .7), ch=.14, axis='Y')   # tie bar under the prongs
    f.bolt_row(g['steel'], (8.2*k, -2.5*k, -1.22*k), (8.2*k, 2.5*k, -1.22*k))
    # Fixed recoil sleeve in the port, a notched gland nut on it; the barrel slides back through it.
    sleeve = g['metal']
    sleeve.lathe([(1.12*k, -.8*k), (1.25*k, -.68*k), (1.25*k, .3*k), (1.38*k, .36*k), (1.38*k, .7*k),
                  (1.24*k, .8*k)], 24, (8.4*k, 0, 0), 'X')
    for i in range(6):
        a = 2*math.pi*i/6
        sleeve.cut().box((8.98*k, 1.38*k*math.cos(a), 1.38*k*math.sin(a)), (.3*k, .22*k, .22*k),
                         rot=Matrix.Rotation(a, 4, 'X'))
    g['dark'].ring((9.25*k, 0, 0), .95*k, 1.3*k, .12*k, 'X', n=24)
    rg = g.sub('recoil 1', (5.0*k, 0, 0), 1.5*k)
    rg['metal'].lathe([(1.05*k, 0), (1.05*k, .9*k), (.9*k, 1.3*k), (.9*k, 13.4*k)], 20, (5.0*k, 0, 0), 'X')  # barrel x 5..18.4
    prof, x = [(1.2*k, 0), (1.45*k, .15*k)], .15                      # fluted shroud x 9.1 .. 13.3
    for gx in (1.3, 2.3, 3.15):
        prof += [(1.45*k, (gx-.2)*k), (1.32*k, (gx-.12)*k), (1.32*k, (gx+.12)*k), (1.45*k, (gx+.2)*k)]
    prof += [(1.45*k, 4.05*k), (1.2*k, 4.2*k)]
    rg['metal'].lathe(prof, 24, (9.1*k, 0, 0), 'X')
    for xx in (9.9, 10.9, 11.9, 12.6):
        rg['dark'].ring((xx*k, 0, 0), 1.44*k, 1.5*k, .18*k, 'X', n=24)
    rg['dark'].ring((14.0*k, 0, 0), .89*k, .96*k, .35*k, 'X', n=20)
    brake = rg['steel#brake']
    brake.lathe([(.98*k, 0), (1.08*k, .12*k), (1.08*k, 1.18*k), (.98*k, 1.3*k)], 20, (17.15*k, 0, 0), 'X')
    brake.cut().cyl((18.5*k, 0, 0), .5*k, 1.6*k, 'X', n=16)
    for sy in (1, -1):
        brake.cut().box((17.85*k, sy*1.0*k, 0), (.4*k, .6*k, 1.0*k))
        brake.cut().box((17.4*k, sy*1.0*k, 0), (.22*k, .6*k, .8*k))
    return [(18.5*k, 0.0, 0.0)]


def _hp_heatseeker(g, f, s, k=1.0):
    """missile_autoloader_1x_hardpoint_base.png (16 x 26 px; the Lasher's WS 001 / WS 004
    heatseeker, the Salamander MRM). An autoloading launch rail: the pale magazine
    block at the back (the sprite's white rows -3..0), chamfered, grooved down its
    flanks, a bolted plate on its back, its loading hatch lid hinged on three knuckles
    with a handle and latch, and the autoloader's two rams lying along its roof; a
    steel breech collar where the round feeds out. Two grey cheeks down the sides
    carry the grooved copper launch rails (the sprite's red columns either side),
    lightening pockets in their outer faces, pale bumper lobes on them, the open
    channel between them on bolted cross ties with one Salamander lying on the rails
    (an umbilical plug on its back), its nose at the fire point 12 px ahead of the slot
    (hardpointOffsets), and the two tapered front horns. The round is its own
    'missile 1' empty, so a shot can launch it; the muzzle anchor is at its nose."""
    mag = g['plate#mag']
    _dz_block(mag, k, (-2.6, 0, -.4), (5.4, 7.0, 4.4), ch=.35)
    mag.cut().box((-2.6*k, 0, 1.9*k), (3.0*k, 3.6*k, .5*k))                   # loading hatch well
    for sy in (1, -1):
        for xx in (-4.4, -.8):                                               # flank grooves
            mag.cut().box((xx*k, sy*3.5*k, -.9*k), (.22*k, .26*k, 2.8*k))
        mag.cut().box((-2.6*k, sy*3.5*k, .6*k), (3.4*k, .26*k, .2*k))
    _dz_block(g['steel'], k, (-5.38, 0, -.6), (.18, 4.6, 2.6), ch=.06)       # plate on the back
    for zz in (.5, -1.7):
        f.bolt_row(g['steel'], (-5.48*k, -1.9*k, zz*k), (-5.48*k, 1.9*k, zz*k), axis='X')
    _dz_block(g['steel'], k, (-2.6, 0, 1.72), (2.8, 3.4, .2), ch=.06)        # the hatch lid
    _dz_block(g['steel'], k, (-1.6, 0, 1.9), (.2, 1.4, .16), ch=.04)         # its handle
    for yy in (-1.15, 0, 1.15):                                              # hinge knuckles
        g['steel'].cyl((-4.05*k, yy*k, 1.85*k), .17*k, .62*k, 'Y', n=18)
    g['steel'].cyl((-4.05*k, 0, 1.85*k), .09*k, 3.0*k, 'Y', n=12)             # hinge pin
    for sy in (1, -1):                                                       # autoloader rams on the roof
        g['steel'].lathe([(.3*k, 0), (.36*k, .1*k), (.36*k, 2.4*k), (.26*k, 2.5*k)], 18,
                         (-4.8*k, sy*2.75*k, 1.95*k), 'X')
        g['metal'].cyl((-1.55*k, sy*2.75*k, 1.95*k), .15*k, 1.5*k, 'X', n=18)
        _dz_block(g['steel'], k, (-.7, sy*2.75, 1.85), (.4, .7, .5), ch=.06)
    g['dark'].box((.05*k, 0, -.2*k), (.3*k, 2.8*k, 2.8*k))                    # breech the round feeds from
    g['steel'].ring((.22*k, 0, 0), 1.25*k, 1.6*k, .26*k, 'X', n=24)            # breech collar
    for sy in (1, -1):
        ch = g[f'paint#cheek{sy}']
        prof = tk._ccw([(-1.0, 3.6), (15.8, 3.6), (16.4, 4.2), (16.4, 5.0), (-1.0, 5.0)])
        ch.prism([(x*k, sy*y*k) for x, y in (prof if sy > 0 else tk._ccw([(x, -y) for x, y in prof]))],
                 -2.6*k, .8*k, .3*k, 1.2*k, glacis=True)
        for xx in (2.6, 5.5, 11.3):                                          # lightening pockets
            ch.cut().box((xx*k, sy*5.0*k, -.9*k), (2.0*k, .36*k, 1.6*k))
        ch.cut().box((7.6*k, sy*4.3*k, 1.2*k), (16.4*k, .16*k, .12*k))       # seam along the top
        for x0, x1 in ((7.0, 9.0), (13.6, 15.4)):                            # bumper lobes
            _dz_block(g['plate'], k, ((x0+x1)/2, sy*5.2, -.6), (x1-x0, .7, 2.2), ch=.22)
        rail = g['accent']                                                   # copper rail, grooved
        _dz_block(rail, k, (8.0, sy*3.15, -.7), (16.0, .5, .8), ch=.1)
        rail.cut().box((8.0*k, sy*3.15*k, -.3*k), (15.6*k, .16*k, .14*k))
        g['dark'].box((8.0*k, sy*3.15*k, -1.15*k), (16.0*k, .55*k, .1*k))
        f.bolt_row(g['steel'], (0.5*k, sy*4.3*k, 1.22*k), (15.0*k, sy*4.3*k, 1.22*k))
        _dz_block(g['steel'], k, (17.2, sy*4.4, -.4), (1.8, .8, 1.6), ch=.15, taper=(.7, .6))   # front horn
        g['dark'].box((17.0*k, sy*4.4*k, .42*k), (1.0*k, .26*k, .04*k))
    g['dark'].box((8.0*k, 0, -2.2*k), (16.0*k, 6.4*k, .4*k))                   # the channel's floor
    for xx in (2.0, 9.0, 15.5):                                                # cross ties, bolted at the ends
        _dz_block(g['steel'], k, (xx, 0, -1.85), (.6, 6.4, .4), ch=.08, axis='Y')
        for sy in (1, -1):
            g['steel'].cyl((xx*k, sy*2.7*k, -1.62*k), .12*k, .08*k, 'Z', n=6)
    m = g.sub('missile 1', (.5*k, 0, 0), 1.5*k)
    tk._missile(m, f, .5*k, 0, 0, 11.5*k, 1.1*k, True)
    for sy in (1, -1):                                                         # seeker lamps
        m['glow'].box((10.0*k, sy*.75*k, .45*k), (.4*k, .3*k, .25*k))
    _dz_block(m['steel'], k, (2.6, 0, 1.12), (.8, .44, .26), ch=.06)           # umbilical plug
    return [(12.0*k, 0.0, 0.0)]


def _glacis(m, poly, z0, z1, inset, z2):
    m.prism(poly, z0, z1, inset, z2, glacis=True)


def _hp_swarmer(g, f, s, k=1.0):
    """swarmer_srm_launcher_hardpoint_base.png (16 x 24 px; the Kite (P)'s WS 002, the
    Falcon (P)'s WS 001 / 002, the Shepherd's). A four-tube rocket pack: the red
    breech housing (sprite rows 15-22) from 6 px behind the slot to its front face
    3 px ahead, a seam groove across its roof, weld beads on its glacis line, a bolted
    access panel on its starboard flank and vent slots in its back between the breech
    caps, the white stencilled chevron, four pale igniter caps let into wells over the
    tube breeches and the stepped, bolted steel breech caps out of its back; the port-
    side fuse box with its red lamp in a guard ring (the bright lobe in column 0),
    hinged and latched. Out of the housing's face run the four orange-red launch tubes
    at y +-1.5 / +-4.5 (the sprite's three-pixel tube columns), banded, clamped at
    mid-length in a hinged, latched clamp, each ending in a chamfered pale collar 12 px
    ahead of the slot (rows 7-8) with its bore cut open, dark-lined, and the SRM's grey
    nose sitting 1.4 px down it. Each round is its own 'missile k' empty; the muzzle
    anchors are at the tube mouths (the .wpn fires at x 15, y +-2 / +-4; the anchors
    keep to the tubes the model has)."""
    TY = (4.5, 1.5, -1.5, -4.5)
    R, RB = 1.45, 1.05                                   # tube and bore radius
    # The breech housing, its roof stepped in on a glacis.
    hs = g['paint#housing']
    plan = tk._ccw([(-4.6, -6.3), (-3.9, -7.0), (2.4, -7.0), (3.2, -6.2), (3.2, 6.2), (2.4, 7.0),
                    (-3.9, 7.0), (-4.6, 6.3)])
    _glacis(hs, [(x*k, y*k) for x, y in plan], -2.6*k, 1.6*k, .6*k, 2.3*k)
    hs.cut().box((-1.6*k, 0, 2.3*k), (.35*k, 15*k, .5*k))            # roof seam (row 19)
    for yy in (0, 3.0, -3.0):                                          # vent slots in the back
        hs.cut().box((-4.6*k, yy*k, -.6*k), (.36*k, .34*k, 2.0*k))
    hs.cut().box((-.7*k, -7.0*k, .9*k), (5.6*k, .24*k, .16*k))        # flank seam
    for y in TY:                                                       # igniter wells (rows 20-21)
        hs.cut().cyl((-3.0*k, y*k, 2.3*k), .62*k, .7*k, 'Z', n=16)
        g['plate'].cyl((-3.0*k, y*k, 2.12*k), .55*k, .12*k, 'Z', n=16, bevel=.03*k)
        g['steel'].cyl((-3.0*k, y*k, 2.2*k), .16*k, .1*k, 'Z', n=8)
    for sy in (1, -1):
        f.bolt_row(g['steel'], (-3.4*k, sy*6.05*k, 2.3*k), (1.9*k, sy*6.05*k, 2.3*k))
        f.weld(g['steel'], (-3.9*k, sy*7.02*k, 1.6*k), (2.4*k, sy*7.02*k, 1.6*k))
    # Access panel on the starboard flank, bolted round.
    _dz_block(g['plate'], k, (-.7, -7.04, -.7), (3.8, .1, 2.4), ch=.03)
    for zz in (.35, -1.75):
        f.bolt_row(g['steel'], (-2.4*k, -7.1*k, zz*k), (1.0*k, -7.1*k, zz*k), axis='Y')
    # The white chevron stencilled on the starboard roof (rows 16-18).
    g['mark'].prism([(x*k, y*k) for x, y in tk._ccw([(2.0, -2.6), (-1.0, -.3), (-1.0, -.9), (1.3, -2.6),
                                                     (-1.0, -4.3), (-1.0, -4.9)])], 2.3*k, 2.34*k)
    # Breech caps out of the housing's back (row 22): stepped, a dimple and four bolts in each.
    caps = g['metal#caps']
    for y in TY:
        caps.lathe([(1.02*k, -.45*k), (1.2*k, -.3*k), (1.2*k, .05*k), (1.08*k, .12*k), (1.08*k, .45*k)], 20,
                   (-4.9*k, y*k, 0), 'X')
        caps.cut().cyl((-5.4*k, y*k, 0), .45*k, .5*k, 'X', n=12)
        _dz_bolt_ring(g['steel'], f, k, (-5.36, y, 0), .74, 4, 'X', r=.09, h=.08)
    # Port fuse box (column 0) with its lamp, wired into the housing's flank.
    fb = g['accent#fusebox']
    _dz_block(fb, k, (0.0, 7.45, -.5), (4.4, 1.5, 2.6), ch=.2)
    fb.cut().box((0.0, 8.2*k, -.5*k), (3.2*k, .3*k, .12*k))              # lid seam
    for xx in (-1.4, 1.4):                                               # hinge knuckles under the lid
        g['steel'].cyl((xx*k, 8.1*k, -1.15*k), .12*k, .5*k, 'X', n=18)
    _dz_block(g['steel'], k, (0.0, 8.2, .25), (.5, .1, .36), ch=.03)    # latch
    g['steel'].cyl((1.0*k, 7.45*k, .8*k), .45*k, .2*k, 'Z', n=12)
    g['lamp_red'].lathe([(.38*k, 0), (.3*k, .2*k), (0, .32*k)], 12, (1.0*k, 7.45*k, .9*k), 'Z')
    g['steel'].ring((1.0*k, 7.45*k, 1.02*k), .44*k, .52*k, .26*k, 'Z', n=18)   # lamp guard
    f.bolt_row(g['steel'], (-1.7*k, 8.2*k, .5*k), (1.7*k, 8.2*k, .5*k), axis='Y')
    tk._cable(g['cable'], [(-1.6, 7.6, -1.0), (-3.0, 7.9, -1.6), (-3.4, 7.2, -1.9), (-3.6, 6.8, -1.9)],
              .28, k, 0, n=8, segs=10)
    g['steel'].cyl((-1.6*k, 7.45*k, -1.0*k), .36*k, .3*k, 'X', n=18)          # cable gland
    # The four launch tubes: orange-red shells out of the housing face, bores cut open.
    tubes = g['accent#tubes']
    for y in TY:
        tubes.cyl((2.9*k, y*k, 0), R*k, 14.6*k, 'X', n=24, bevel=.08*k)  # x -4.4 .. 10.2
        tubes.cut().cyl((8.6*k, y*k, 0), (RB+.03)*k, 8.4*k, 'X', n=24)   # bore x 4.4 .. 12.8
        for xx in (5.3, 9.2):                                            # rolled grooves
            tubes.cut().ring((xx*k, y*k, 0), (R-.08)*k, (R+.2)*k, .16*k, 'X', n=24)
        g['dark'].ring((7.3*k, y*k, 0), .95*k, (RB+.02)*k, 5.8*k, 'X', n=24)   # bore liner
        g['dark'].cyl((4.45*k, y*k, 0), (RB+.02)*k, .1*k, 'X', n=24)          # bore floor
    col = g['plate#collars']
    for y in TY:
        col.lathe([(1.34*k, 0), (1.48*k, .12*k), (1.48*k, 1.5*k), (1.36*k, 1.8*k)], 24,
                  (10.2*k, y*k, 0), 'X')                                    # mouth collar (rows 7-8)
        col.cut().cyl((11.6*k, y*k, 0), (RB+.03)*k, 2.2*k, 'X', n=24)
        col.cut().ring((10.75*k, y*k, 0), 1.4*k, 1.6*k, .14*k, 'X', n=24)
        g['steel'].ring((3.65*k, y*k, 0), 1.4*k, 1.62*k, .5*k, 'X', n=24)      # root band (row 14)
    # Clamp at mid-length: saddle under the tubes, cap bar over them, hinge and latch at the ends.
    _dz_block(g['steel'], k, (7.0, 0, -1.35), (1.0, 12.2, .7), ch=.12, axis='Y')
    _dz_block(g['steel'], k, (7.0, 0, 1.55), (1.0, 12.2, .32), ch=.08, axis='Y')
    for sy in (1, -1):
        _dz_block(g['steel'], k, (7.0, sy*6.05, .1), (1.0, .35, 3.2), ch=.08, axis='Z')
    g['steel'].cyl((7.0*k, 6.32*k, 1.45*k), .2*k, 1.1*k, 'X', n=18)         # hinge knuckle (port)
    _dz_block(g['steel'], k, (7.0, -6.36, .9), (.5, .3, .8), ch=.06, axis='Z')   # latch (starboard)
    f.bolt_row(g['steel'], (7.0*k, -5.0*k, 1.72*k), (7.0*k, 5.0*k, 1.72*k))
    mouths = []
    for i, y in enumerate(TY):
        m = g.sub(f'missile {i+1}', (4.5*k, y*k, 0), 1.0*k)
        m['missile'].lathe([(.6*k, 0), (.85*k, .15*k), (.85*k, 4.5*k)], 20, (4.5*k, y*k, 0), 'X')
        m['plate'].lathe([(.86*k, 4.45*k), (.78*k, 5.1*k), (.5*k, 5.8*k), (.15*k, 6.08*k), (0, 6.1*k)], 20,
                         (4.5*k, y*k, 0), 'X')                                 # grey nose, tip x 10.6
        m['dark'].ring((8.95*k, y*k, 0), .84*k, .88*k, .12*k, 'X', n=20)
        m['dark'].ring((6.6*k, y*k, 0), .84*k, .88*k, .1*k, 'X', n=20)         # body joint
        mouths.append((12.0*k, y*k, 0.0))
    return mouths


def _hp_hammer(g, f, s, k=1.0):
    """torpedo_mount_2x_lowtech_small_hardpoint.png (16 x 28 px; the Kite (P)'s WS 001).
    A low-tech twin torpedo cradle (RENDER_LOADED_MISSILES: the rounds are drawn).
    A brown-grey magazine block from 6 px behind the slot to 3 px ahead (rows 18-26),
    a seam groove across it (rows 18-19), riveted plate seams down its flanks, the
    yellow hazard plates on its rear roof with diagonal grooves and corner bolts
    either side of a pale access hatch (rows 22-25; hinged, with a handle), tapered
    lifting lugs at its shoulders with their pins, and two launch ports in its face,
    each in a stepped flange collar bolted round. From the ports two open troughs run
    out to a front cross-bar 14 px ahead (rows 7-8), each a grey channel, chamfered,
    ribbed outside, with steel rails the torpedo lies on, a sunk web between them at
    the root with lightening holes (the dark-green rows 14-17) and open ground between
    them further out (the sprite's gap). In each lies a Hammer: grey body with its
    section joints, red warhead (the round's sprite), two pale fuse prongs, its tail in
    the dark port with tapered fins and a shrouded propulsor; its nose at x 15. Rounds
    on 'missile k'. The .wpn fires at x 8, y +-4; the anchors are at the noses, on the
    lane centres."""
    TY = (3.5, -3.5)
    RT, RC = 1.5, 1.75                                   # torpedo and channel radius
    mag = g['paint#mag']
    plan = tk._ccw([(-5.8, -6.0), (-5.0, -7.0), (2.2, -7.0), (3.0, -6.4), (3.0, 6.4), (2.2, 7.0),
                    (-5.0, 7.0), (-5.8, 6.0)])
    _glacis(mag, [(x*k, y*k) for x, y in plan], -2.6*k, 1.8*k, .5*k, 2.4*k)
    mag.cut().box((1.3*k, 0, 2.4*k), (.35*k, 15*k, .5*k))                  # seam (rows 18-19)
    for y in TY:                                                            # launch ports
        mag.cut().cyl((2.0*k, y*k, 0), 2.05*k, 2.3*k, 'X', n=28)
        g['dark'].cyl((.95*k, y*k, 0), 2.05*k, .12*k, 'X', n=28)             # port floor
        g['steel'].lathe([(2.05*k, -.25*k), (2.6*k, -.25*k), (2.6*k, .05*k), (2.4*k, .25*k), (2.05*k, .25*k)],
                         28, (3.1*k, y*k, 0), 'X', loop=True)               # stepped port flange
        _dz_bolt_ring(g['steel'], f, k, (3.32, y, 0), 2.38, 8, 'X', r=.09, h=.08)
    for sy in (1, -1):
        f.bolt_row(g['steel'], (-4.6*k, sy*6.2*k, 2.4*k), (.6*k, sy*6.2*k, 2.4*k))
        mag.cut().box((-1.8*k, sy*7.0*k, -.4*k), (.2*k, .24*k, 3.6*k))      # flank plate seams
        mag.cut().box((-1.8*k, sy*7.0*k, -.4*k), (6.4*k, .24*k, .16*k))
        f.bolt_row(g['steel'], (-1.55*k, sy*7.02*k, 1.2*k), (-1.55*k, sy*7.02*k, -2.0*k), axis='Y')
        # Lifting lug at the shoulder (row 20): a tapered ear, an eye through it, the pin.
        _dz_block(g['steel'], k, (.4, sy*7.15, .6), (1.0, .4, 1.2), ch=.08, axis='Z', taper=(.6, 1.0))
        g['steel'].cut().cyl((.4*k, sy*7.15*k, .85*k), .2*k, .6*k, 'Y', n=12)
        g['steel'].cyl((.4*k, sy*7.4*k, .85*k), .14*k, .5*k, 'Y', n=12)
        g['steel'].cyl((.4*k, sy*7.62*k, .85*k), .22*k, .06*k, 'Y', n=12)
        # Yellow hazard plate, diagonal grooves cut into it, bolted at its corners (rows 23-25).
        hz = g[f'accent#hazard{sy}']
        hz.box((-3.6*k, sy*3.95*k, 2.47*k), (2.8*k, 4.3*k, .16*k), .04*k)
        for i in range(4):
            hz.cut().box(((-4.5+i*.95)*k, sy*3.95*k, 2.56*k), (.3*k, 5.5*k, .12*k),
                         rot=Matrix.Rotation(sy*math.radians(40), 4, 'Z'))
        if f.near:
            for dx in (-1.2, 1.2):
                for dy in (-1.95, 1.95):
                    g['steel'].cyl(((-3.6+dx)*k, (sy*3.95+dy)*k, 2.57*k), .09*k, .06*k, 'Z', n=6)
    hatch = g['mark#hatch']                                                  # pale centre hatch
    _dz_block(hatch, k, (-3.4, 0, 2.46), (3.0, 2.6, .14), ch=.05)
    hatch.cut().box((-3.4*k, 0, 2.55*k), (2.2*k, .2*k, .1*k))
    for yy in (-.8, .8):                                                     # hinge knuckles
        g['steel'].cyl((-4.95*k, yy*k, 2.55*k), .15*k, .55*k, 'Y', n=18)
    g['steel'].cyl((-4.95*k, 0, 2.55*k), .08*k, 2.2*k, 'Y', n=12)              # hinge pin
    _dz_block(g['steel'], k, (-2.2, 0, 2.6), (.18, 1.2, .14), ch=.04)         # handle
    # The troughs: a grey channel each, rails under the round, from the face to the bar.
    for i, y in enumerate(TY):
        sy = 1 if y > 0 else -1
        tr = g[f'plate#trough{i+1}']
        _dz_block(tr, k, (7.6, y, -1.1), (9.4, 5.0, 2.6), ch=.2)            # x 2.9 .. 12.3, y 1 .. 6
        tr.cut().cyl((8.0*k, y*k, 0), RC*k, 11*k, 'X', n=32)
        for xx in (5.4, 8.2, 11.0):                                        # rib slots in the cheeks
            tr.cut().box((xx*k, (y+sy*2.3)*k, .2*k), (.4*k, .6*k, .8*k))
        for xx in (6.8, 9.6):                                              # stiffening ribs outside
            _dz_block(g['plate'], k, (xx, y+sy*2.56, -1.0), (.35, .2, 2.0), ch=.05, axis='Z')
        for dy in (.9, -.9):
            _dz_block(g['steel'], k, (7.6, y+dy, -1.36), (9.4, .3, .35), ch=.06)
        f.bolt_row(g['steel'], (4.0*k, (y+sy*2.1)*k, .2*k), (11.6*k, (y+sy*2.1)*k, .2*k))
    web = g['steel']                                                        # sunk web (rows 14-17)
    _dz_block(web, k, (5.0, 0, -1.9), (4.2, 2.4, .5), ch=.08)
    for xx in (3.9, 5.0, 6.1):
        web.cut().cyl((xx*k, 0, -1.9*k), .32*k, .8*k, 'Z', n=18)
    _dz_block(g['steel'], k, (13.1, 0, -1.75), (1.6, 13.2, 1.3), ch=.16, axis='Y')   # front bar (rows 7-8)
    if f.near:
        for yy in (-5.2, -2.6, 0.0, 2.6, 5.2):
            g['steel'].cyl((13.1*k, yy*k, -1.08*k), .12*k, .08*k, 'Z', n=6)
    for sy in (1, -1):
        _dz_block(g['plate'], k, (13.5, sy*1.75, -1.0), (1.2, .7, .9), ch=.1, taper=(.7, .7))   # guide horns
        _dz_block(g['plate'], k, (13.3, sy*6.15, -1.1), (1.4, .9, 1.2), ch=.12)              # outer lugs
    noses = []
    for i, y in enumerate(TY):
        m = g.sub(f'missile {i+1}', (1.0*k, y*k, 0), 1.5*k)
        c = (1.0*k, y*k, 0)
        m['missile'].lathe([(1.0*k, 0), (RT*k, .45*k), (RT*k, 9.9*k)], 24, c, 'X')
        m['accent2'].lathe([(RT*k, 9.85*k), (1.46*k, 10.8*k), (1.2*k, 12.1*k), (.7*k, 13.3*k), (0, 14.0*k)], 24,
                           c, 'X')                                          # red warhead, tip x 15
        m['steel'].ring((10.9*k, y*k, 0), RT*k, 1.56*k, .3*k, 'X', n=24)     # warhead band
        for xx in (4.2, 7.4):                                                # section joints
            m['dark'].ring((xx*k, y*k, 0), (RT-.03)*k, (RT+.02)*k, .1*k, 'X', n=24)
        m['dark'].cyl((1.0*k, y*k, 0), .6*k, .12*k, 'X', n=12)               # nozzle
        m['steel'].ring((1.3*k, y*k, 0), .7*k, .85*k, .5*k, 'X', n=20)        # propulsor shroud
        for ang in (.25*math.pi, .75*math.pi, 1.25*math.pi, 1.75*math.pi):   # tail fins inside the port
            rot = Matrix.Rotation(ang, 4, 'X')
            m['missile'].box((2.0*k, y*k+math.cos(ang)*1.7*k, math.sin(ang)*1.7*k), (1.8*k, .5*k, .1*k),
                             rot=rot)
            m['steel'].cyl((1.4*k, y*k+math.cos(ang)*.78*k, math.sin(ang)*.78*k), .06*k, .3*k, 'X', n=6)
        for dy in (.45, -.45):                                               # fuse prongs (round's rows 1-2)
            m['plate'].cyl((14.2*k, (y+dy)*k, .2*k), .1*k, 1.6*k, 'X', n=6)
        noses.append((15.0*k, y*k, 0.0))
    return noses


def _hp_chaingun(g, f, s, k=1.0):
    """rotary/rotary_cannon_hardpoint_base00..03.png (30 x 42 px; the Hound (A)'s spine
    gun, the Assault Chaingun). A tan armoured breech housing sunk in its bay from 8.5 px
    behind the slot to its glacis 9.8 ahead, wider to port where the brown ribbed drive
    motor lies along its flank (the sprite's brown lobe: a stepped casing between bolted
    end bells, strapped down to its saddle), a feed well cut into its starboard top with
    the feed sprocket and the belt of rounds in it, fed by the covered chute that runs
    out to starboard to the ribbed, latched ammunition box at its end (the sprite's tan
    arm, rows 23..27). Plate seams, louvres, weld beads and a roof hatch on the housing.
    The front is the fixed finned cooling jacket (rows 17..21) the rotor turns in. The
    six-barrel cluster (the sprite's 8 px barrel block, x 14.5..28.5) is a 'rotor' group
    spinning about +X, carried on 'recoil 1' so the whole assembly can kick back through
    the jacket: a stepped, fluted hub with a bolt circle on its face, three scalloped
    clamps and the barrels with their bores open. One muzzle anchor on the cluster's
    axis at the fire point (29 px ahead, hardpointOffsets); the barrels take turns
    firing past it."""
    # -- the housing: a faceted shell off the sprite's body outline, glacis all round.
    sh = g['paint#housing']
    plan = tk._ccw([(-8.5, -3.0), (-6.5, -4.6), (-2.5, -6.0), (0.5, -7.2), (3.5, -8.0), (8.5, -6.6),
                    (9.8, -5.4), (9.8, 6.4), (8.5, 7.2), (-5.5, 7.2), (-7.6, 6.4), (-8.5, 3.6)])
    _glacis(sh, [(x*k, y*k) for x, y in plan], -4.4*k, 2.4*k, .9*k, 3.4*k)
    sh.cut().box((1.8*k, -3.4*k, 3.3*k), (9.0*k, 4.6*k, 2.8*k))          # the feed well (floor 1.9)
    sh.cut().box((6.0*k, -6.6*k, 2.9*k), (2.6*k, 2.6*k, 1.6*k))          # the chute's throat into it
    sh.cut().cyl((9.9*k, 0, 0), 3.6*k, 1.6*k, 'X', n=32)                  # socket the rotor hub turns in
    for x in (-6.0, -2.8):                                               # plate seams across the roof
        sh.cut().box((x*k, 1.8*k, 3.4*k), (.3*k, 8.0*k, .5*k))
    for x in (-7.0, -5.6, -4.2):                                         # louvres in the rear glacis
        sh.cut().box((x*k, 4.6*k, 3.0*k), (.5*k, 2.4*k, .8*k))
    sh.cut().box((1.0*k, 7.2*k, 0.0), (14.0*k, .26*k, .18*k))             # flank seam (port)
    sh.cut().box((3.0*k, 7.2*k, -2.2*k), (.18*k, .26*k, 4.0*k))
    f.weld(g['steel'], (-8.52*k, -2.6*k, 1.0*k), (-8.52*k, 3.2*k, 1.0*k))
    f.weld(g['steel'], (-5.5*k, 7.22*k, 2.4*k), (8.5*k, 7.22*k, 2.4*k))
    f.hatch(g, (-5.0*k, -2.6*k, 3.4*k), size=1.6)
    # Port roof rail (the sprite's pale column at y +5.5) and bolt rows along the eaves.
    _dz_block(g['steel'], k, (1.0, 5.4, 3.6), (13.0, .7, .4), ch=.1)
    f.bolt_row(g['steel'], (-5.0*k, 5.4*k, 3.82*k), (7.0*k, 5.4*k, 3.82*k))
    f.bolt_row(g['steel'], (-7.0*k, 6.9*k, 2.0*k), (8.0*k, 6.9*k, 2.0*k), axis='Y')
    # In the feed well: the sprocket drum and the belt crossing from the chute's throat.
    g['steel'].lathe([(.9*k, -1.9*k), (1.0*k, -1.8*k), (1.0*k, 1.8*k), (.9*k, 1.9*k)], 20,
                     (-.6*k, -3.4*k, 2.6*k), 'Y')
    for yy in (-4.6, -3.4, -2.2):
        g['dark'].ring((-.6*k, yy*k, 2.6*k), .95*k, 1.04*k, .3*k, 'Y', n=20)
    g['steel'].cyl((-.6*k, -1.4*k, 2.6*k), .35*k, .3*k, 'Y', n=18)             # sprocket axle boss
    for i in range(5):                                                   # rounds in their links
        yy = -5.4+.95*i
        g['accent2'].cyl((3.6*k, yy*k, 2.3*k), .38*k, 2.6*k, 'X', n=10)
        g['accent2'].lathe([(.38*k, 0), (.2*k, .5*k), (0, .7*k)], 10, (4.9*k, yy*k, 2.3*k), 'X')
        g['dark'].box((3.6*k, yy*k, 1.95*k), (1.6*k, .7*k, .12*k))
    # -- the feed chute out to starboard (the sprite's tan arm) and the ammunition box
    # at its end; the top of the chute is open over the belt.
    ch = g['plate#chute']
    _dz_block(ch, k, (6.0, -9.5, 2.6), (2.6, 5.0, 1.6), ch=.15, axis='Y')
    ch.cut().box((6.0*k, -9.5*k, 3.4*k), (1.4*k, 6.0*k, 1.0*k))
    for yy in (-7.9, -9.5, -11.1):
        _dz_block(g['steel'], k, (6.0, yy, 2.6), (2.9, .35, 1.9), ch=.06, axis='Y')
    if f.near:
        for yy in (-7.9, -9.5, -11.1):
            for sx in (1, -1):
                g['steel'].cyl(((6.0+sx*1.48)*k, yy*k, 2.6*k), .1*k, .08*k, 'X', n=6)
    for i in range(5):
        yy = -7.6-.9*i
        g['accent2'].cyl((6.0*k, yy*k, 3.1*k), .32*k, 1.3*k, 'X', n=10)
    box = g['plate#box']
    _dz_block(box, k, (6.0, -12.6, 1.4), (5.0, 3.6, 5.0), ch=.22)
    box.cut().box((6.0*k, -12.8*k, 3.95*k), (4.0*k, 2.6*k, .5*k))           # the lid sunk in its rim
    for xx in (4.6, 7.4):                                                  # pressed ribs on the box's ends
        box.cut().box((xx*k, -12.6*k, .9*k), (.2*k, 2.6*k, 3.0*k))
    for sx in (1, -1):                                                     # stiffeners on its ends
        _dz_block(g['steel'], k, (6.0+sx*2.58, -12.6, .9), (.18, 2.4, .3), ch=.05)
        _dz_block(g['steel'], k, (6.0+sx*2.58, -12.6, -.3), (.18, 2.4, .3), ch=.05)
    _dz_block(g['steel'], k, (6.0, -12.8, 3.75), (3.6, 2.2, .12), ch=.04)
    _dz_block(g['steel'], k, (6.0, -13.6, 3.95), (2.0, .25, .25), ch=.06)    # handle
    for x in (4.4, 7.6):                                                   # latches
        _dz_block(g['steel'], k, (x, -14.45, 3.0), (.6, .2, 1.0), ch=.05, axis='Z', taper=(.7, 1.0))
        g['steel'].cyl((x*k, -14.42*k, 3.4*k), .1*k, .7*k, 'X', n=12)
    g['mark'].box((6.0*k, -14.42*k, 1.4*k), (2.4*k, .06*k, .6*k))
    # -- the drive motor along the port flank: a stepped casing, ribbed, bolted end bells,
    # strapped to its saddle, a cable into the hull.
    mot = g['accent#motor']
    mot.lathe([(1.85*k, -4.5*k), (2.1*k, -4.25*k), (2.1*k, 4.25*k), (1.85*k, 4.5*k)], 28, (1.5*k, 8.3*k, -.6*k), 'X')
    for x in (-1.6, -.4, .8, 2.0, 3.2, 4.4):                             # cooling grooves turned in
        mot.cut().ring((x*k, 8.3*k, -.6*k), 1.85*k, 2.3*k, .35*k, 'X', n=28)
    g['steel'].lathe([(1.6*k, -.4*k), (1.6*k, .2*k), (1.3*k, .4*k), (.6*k, .4*k), (.6*k, .55*k)], 24,
                     (-3.3*k, 8.3*k, -.6*k), 'X')
    _dz_bolt_ring(g['steel'], f, k, (-3.72, 8.3, -.6), 1.32, 6, 'X', r=.1, h=.1)
    g['steel'].lathe([(1.2*k, -.45*k), (1.2*k, .2*k), (.95*k, .45*k)], 24, (6.3*k, 8.3*k, -.6*k), 'X')
    _dz_bolt_ring(g['steel'], f, k, (6.65, 8.3, -.6), .9, 6, 'X', r=.08, h=.08)
    g['dark'].cyl((6.8*k, 8.3*k, -.6*k), .55*k, .2*k, 'X', n=12)
    _dz_block(g['steel'], k, (1.5, 7.0, -2.9), (7.0, 1.8, .7), ch=.12)       # its saddle
    for x in (-2.55, 5.55):                                              # hold-down straps
        g['steel'].ring((x*k, 8.3*k, -.6*k), 2.06*k, 2.16*k, .4*k, 'X', n=28)
    tk._cable(g['cable'], [(-3.6, 8.3, -.6), (-5.6, 8.3, -.6), (-6.6, 7.0, -2.0), (-7.4, 6.0, -3.6)], .45, k, 0)
    # -- the cooling jacket: fixed sleeve and fins the rotor turns in, a lipped front bearing.
    jac = g['steel#jacket']
    jac.lathe([(3.55*k, 0), (5.1*k, 0), (5.1*k, 1.0*k), (4.4*k, 1.4*k), (4.4*k, 4.2*k), (5.2*k, 4.5*k),
               (5.2*k, 5.0*k), (3.55*k, 5.0*k)], 40, (9.6*k, 0, 0), 'X', loop=True)
    for x in (11.4, 12.2, 13.0, 13.8):
        g['metal'].ring((x*k, 0, 0), 4.3*k, 5.0*k, .35*k, 'X', n=40)
    for i in range(8):                                                   # tie bolts on the front lip
        a = 2*math.pi*(i+.5)/8
        g['steel'].cyl((14.65*k, 4.75*k*math.cos(a), 4.75*k*math.sin(a)), .18*k, .2*k, 'X', n=6)
    for i in range(4):                                                   # tie rods through the fins
        a = 2*math.pi*(i+.5)/4
        g['steel'].cyl((12.6*k, 4.65*k*math.cos(a), 4.65*k*math.sin(a)), .12*k, 2.7*k, 'X', n=12)
    # -- the rotating assembly: recoil carriage, rotor, barrels.
    rc = g.sub('recoil 1', (9.5*k, 0, 0), 2*k)
    rc['steel'].lathe([(3.2*k, -.45*k), (3.4*k, -.3*k), (3.4*k, .3*k), (3.2*k, .45*k)], 32, (9.7*k, 0, 0), 'X')
    rot = rc.sub('rotor', (9.5*k, 0, 0), 2*k)
    hub = rot['metal#hub']
    hub.lathe([(3.2*k, -3.0*k), (3.4*k, -2.8*k), (3.4*k, 2.4*k), (3.25*k, 2.6*k), (3.25*k, 2.85*k),
               (3.05*k, 3.0*k)], 36, (12.9*k, 0, 0), 'X')
    for i in range(6):                                                   # flutes down the hub
        a = 2*math.pi*i/6
        hub.cut().box((13.6*k, 3.4*k*math.cos(a), 3.4*k*math.sin(a)), (4.0*k, .4*k, .3*k),
                      rot=Matrix.Rotation(a, 4, 'X'))
    _dz_bolt_ring(rot['steel'], f, k, (15.9, 0, 0), 2.2, 6, 'X', r=.14, h=.12, phase=0.0)
    rot['dark'].cyl((21.9*k, 0, 0), .95*k, 13.6*k, 'X', n=16)                 # spindle between the barrels
    rot['steel'].lathe([(1.3*k, 0), (1.3*k, .15*k), (.9*k, .22*k), (.9*k, .3*k)], 18, (28.45*k, 0, 0), 'X')  # nose cap
    n, pitch, rad = 6, 2.6, 1.05
    L, x0 = 12.8, 15.8
    for i in range(n):
        a = 2*math.pi*(i+.5)/n
        y, z = pitch*math.cos(a), pitch*math.sin(a)
        rot['metal'].lathe([(0, 0), (rad*k, 0), (rad*k, L*k), (rad*.5*k, L*k), (rad*.5*k, (L-1.8)*k),
                            (0, (L-1.8)*k)], 16, (x0*k, y*k, z*k), 'X')
        rot['steel'].lathe([(1.15*k, -.9*k), (1.25*k, -.8*k), (1.25*k, .8*k), (1.15*k, .9*k)], 18,
                           ((x0+.9)*k, y*k, z*k), 'X')                                    # chamber
    for xc, w, R in ((16.4, 1.0, 3.95), (22.2, .8, 3.75), (27.6, 1.1, 3.95)):
        cl = rot[f'steel#clamp{xc}']
        cl.cyl((xc*k, 0, 0), R*k, w*k, 'X', n=36, bevel=.12*k)
        for i in range(n):                                               # scallops between the barrels
            a = 2*math.pi*i/n
            cl.cut().cyl((xc*k, R*1.08*k*math.cos(a), R*1.08*k*math.sin(a)), .85*k, 2*w*k, 'X', n=12)
        if f.near:
            for i in range(n):                                           # tie bolts between the bores
                a = 2*math.pi*i/n
                rot['steel'].cyl(((xc+w/2+.02)*k, 2.9*k*math.cos(a), 2.9*k*math.sin(a)), .16*k, .12*k, 'X', n=6)
    return [(29.0*k, 0.0, 0.0)]


def _hp_harpoonpod(g, f, s, k=1.0):
    """launcher_m4_2x_hardpoint_base.png (32 x 44 px; the Manticore (P)'s WS 004 / WS 005).
    Two open launch rails side by side, a Harpoon on each at y -8 and +8 (hardpointOffsets).
    Each rail is a grey trough from 6 px behind the slot to its front hoop 14 ahead: the
    channel cut down its length (the sprite's dark channel and its open gap at rows
    23..25 and at the back legs), grooved along its outer wall, tan rail bars along both
    walls with tapered, capped pale end posts standing past the hoop (rows 18..20),
    orange marks, windows cut through the outer wall, the round riding flanged rollers
    on the channel floor. The dark magazine block bridges both rails behind the slot with
    the rounds' tails in bores through it, its loading hatches hinged with handles; a
    tapered centre spine with a lifting eye joins the rails ahead (the sprite's arch).
    The chamfered front hoops have lipped round mouths the rounds leave through. Each
    Harpoon (22 long, centred on its fire point) is its own 'missile k' empty; muzzle
    anchors at the hoop mouths."""
    mouths = []
    for i, sy in enumerate((-1, 1)):
        yc = sy*8.0
        pod = g[f'paint#pod{i}']
        prof = [(-6.0, 1.0), (1.2, 1.0), (1.6, 2.0), (11.4, 2.0), (11.4, 13.0), (1.6, 13.0), (1.2, 15.0),
                (-6.0, 15.0)]
        _glacis(pod, tk._ccw([(x*k, sy*y*k) for x, y in prof]), -3.0*k, 1.0*k, .3*k, 1.4*k)
        pod.cut().box((3.0*k, yc*k, 1.0*k), (20*k, 3.5*k, 5.8*k))            # the channel (floor -1.9)
        for x in (3.4, 6.2, 9.0):                                           # windows through the outer wall
            pod.cut().box((x*k, sy*12.5*k, -.6*k), (1.6*k, 2.0*k, 1.4*k))
        pod.cut().box((-2.4*k, sy*15.0*k, -.6*k), (7.0*k, .24*k, .16*k))      # seam along the outer wall
        pod.cut().box((-5.9*k, yc*k, -1.4*k), (.3*k, 12.0*k, .2*k))           # and across the back
        g['dark'].box((3.0*k, yc*k, -1.85*k), (17*k, 3.5*k, .1*k))            # channel liner
        for x in (-4.0, .2, 5.0, 9.6):                                       # flanged rollers the round rides
            g['steel'].lathe([(.42*k, -1.7*k), (.42*k, -1.5*k), (.3*k, -1.4*k), (.3*k, 1.4*k), (.42*k, 1.5*k),
                              (.42*k, 1.7*k)], 18, (x*k, yc*k, -1.6*k), 'Y')
        # Tan rail bars along both walls, tapered pale end posts standing past the hoop.
        for yb in (11.2, 4.0):
            _dz_block(g['accent'], k, (6.4, sy*yb, 1.65), (10.8, 1.2, .5), ch=.1)
            _dz_block(g['plate'], k, (14.3, sy*yb, -.2), (1.6, 1.4, 4.8), ch=.16, axis='Z', taper=(.8, .8))
            _dz_block(g['steel'], k, (14.3, sy*yb, 2.25), (1.2, 1.1, .2), ch=.05)
            f.bolt_row(g['steel'], (2.0*k, sy*yb*k, 1.92*k), (10.8*k, sy*yb*k, 1.92*k))
        for x in (2.5, 4.5):                                                 # orange marks on the outer bar
            g['mark'].box((x*k, sy*11.2*k, 1.92*k), (.7*k, 1.0*k, .06*k))
        # The front hoop with its lipped round mouth.
        hoop = g[f'steel#hoop{i}']
        _dz_block(hoop, k, (12.8, sy*7.5, -.3), (2.8, 11.0, 5.4), ch=.25)
        hoop.cut().cyl((12.8*k, yc*k, 0), 2.2*k, 4.0*k, 'X', n=28)
        g['dark'].ring((13.0*k, yc*k, 0), 2.05*k, 2.22*k, 2.2*k, 'X', n=28)
        g['steel'].lathe([(2.2*k, -.08*k), (2.5*k, -.08*k), (2.5*k, .04*k), (2.36*k, .12*k), (2.2*k, .12*k)],
                         28, (14.2*k, yc*k, 0), 'X', loop=True)                # mouth lip
        f.bolt_row(g['steel'], (14.22*k, sy*2.8*k, 1.9*k), (14.22*k, sy*12.2*k, 1.9*k), axis='X')
        mouths.append((14.2*k, yc*k, 0.0))
        # The loaded Harpoon, tail 12 behind its fire point, fins rolled clear of the walls.
        m = g.sub(f'missile {i+1}', (-4.0*k, yc*k, 0), 1.5*k)
        tk._missile(m, f, -4.0*k, yc*k, 0, 22.0*k, 1.2*k, True, roll=math.pi/4)
        m['glow'].box((15.0*k, yc*k, 1.0*k), (.5*k, .3*k, .25*k))          # seeker window
        _dz_block(m['steel'], k, (1.2, yc, 1.22), (.9, .5, .28), ch=.06)    # umbilical plug
    # The magazine block across both rails, the rounds' tails in bores through it.
    mag = g['accent2#mag']
    plan = [(-2.4, -15.0), (1.2, -15.0), (1.2, 15.0), (-2.4, 15.0)]
    _glacis(mag, tk._ccw([(x*k, y*k) for x, y in plan]), -3.0*k, 1.6*k, .5*k, 2.4*k)
    for sy in (-1, 1):
        mag.cut().cyl((-.6*k, sy*8.0*k, 0), 2.2*k, 5.0*k, 'X', n=28)
        mag.cut().box((-.6*k, sy*12.6*k, 2.4*k), (2.0*k, 2.6*k, .6*k))       # loading hatch wells
        _dz_block(g['steel'], k, (-.6, sy*12.6, 2.12), (1.8, 2.4, .16), ch=.05)
        g['steel'].cyl((-1.55*k, sy*12.6*k, 2.25*k), .12*k, 2.0*k, 'Y', n=12)  # hinge
        _dz_block(g['steel'], k, (.1, sy*12.6, 2.26), (.14, 1.0, .14), ch=.03)  # handle
        g['dark'].ring((1.0*k, sy*8.0*k, 0), 2.2*k, 2.5*k, .5*k, 'X', n=28)
        g['mark'].box((-1.9*k, sy*2.5*k, 2.42*k), (.6*k, .9*k, .06*k))
    f.bolt_row(g['steel'], (-2.0*k, -14.0*k, 2.42*k), (-2.0*k, 14.0*k, 2.42*k), axis='Z')
    f.weld(g['steel'], (1.25*k, -14.6*k, -1.0*k), (1.25*k, 14.6*k, -1.0*k))
    # The centre spine between the rails and its lifting eye (the sprite's arch).
    sp = g['paint#spine']
    _dz_block(sp, k, (7.0, 0, -1.5), (6.4, 4.4, 3.0), ch=.25, taper=(.85, 1.0))
    sp.cut().box((7.0*k, 0, 0), (4.4*k, 2.4*k, .8*k))
    g['steel'].ring((7.6*k, 0, .25*k), 1.1*k, 1.7*k, .5*k, 'Z', n=24)
    _dz_bolt_ring(g['steel'], f, k, (7.6, 0, .5), 1.4, 6, 'Z', r=.1, h=.08)
    _dz_block(g['steel'], k, (3.0, 0, -2.6), (2.0, 26.0, .8), ch=.14, axis='Y')   # cross tie under the rails
    return mouths


def _hb_oval(hw, hz, n=48, p=2.6, zc=0.0):
    """A superellipse YZ loop: flat-shouldered like a machined casting, round at the corners."""
    pts = []
    for i in range(n):
        a = 2*math.pi*i/n
        c, s_ = math.cos(a), math.sin(a)
        pts.append((hw*math.copysign(abs(c)**(2/p), c), zc+hz*math.copysign(abs(s_)**(2/p), s_)))
    return pts


def _hb_oval_z(y, hw, hz, p=2.6):
    """Height of a _hb_oval's top at y."""
    t = min(abs(y)/hw, 1.0)
    return hz*(1-t**p)**(1/p)


def _hb_loft(m, stations, k, n=48, p=2.6, zc=0.0):
    """loft_x through oval stations [(x, hw, hz), ...] in sprite pixels."""
    m.loft_x([(x*k, [(y*k, z*k) for y, z in _hb_oval(hw, hz, n, p, zc)]) for x, hw, hz in stations])


def _hb_band(m, x0, x1, hw, hz, k, ch=.12):
    """A chamfered oval band (a clamp ring round an oval casting)."""
    _hb_loft(m, [(x0, hw-ch, hz-ch), (x0+ch, hw, hz), (x1-ch, hw, hz), (x1, hw-ch, hz-ch)], k)


def _hb_plan(pts, k, sy=1):
    """A plan polygon in sprite pixels, mirrored to starboard by sy, counter-clockwise, scaled."""
    return tk._ccw([(x*k, sy*y*k) for x, y in pts])


def _hp_heavyblaster(g, f, s, k=1.0):
    """blaster_hardpoint_base.png (28 x 48 px; the merchant Wolf's bow WS 004, the Heavy
    Blaster). The slot is 12 px up from the sprite's back, so the gun runs from its power
    coupling 9 px behind the slot to its emitter head 24 px ahead; the fire point is 15 px
    ahead (hardpointOffsets), where the lens sits at the bottom of the head's open bore.
    - On a steel sled on the bay floor (rails, notched flanks, hold-down lugs), the armoured
      capacitor housing (the sprite's body rows 30-41): a glacis-roofed casting with a
      trough in its roof holding the capacitor bank (five blue cells, the sprite's light
      stripes, in a clamp block with charge lamps in grooves), cable channels down both
      flanks, flank seams and an inspection door each side.
    - Behind it the oval power coupling (rows 42-44: fin grooves, a bolted flange, the
      reactor feed socket); two armoured power cables a side leave its crown through glands,
      run forward in the flank channels through a clamp block (the sprite's widest rows) and
      plug into the yoke cheeks.
    - The neck (rows 23-29): the blue field coil, wound and grooved, in a saddle cradle
      between the two yoke cheeks, each with a trunnion boss lit in the blaster's blue.
    - The emitter head (rows 12-22): an oval casting with two clamp bands, fin grooves
      round its front (the sprite's dark ribs), four lit vent slots over the chamber; its
      bore is open to the front, lined dark, four focusing vanes along its wall and the lens
      glowing in the bolt's colour (glowColor / fringe 100, 100, 255) at the fire point."""
    if 'lamp_blaster' not in g.mats:
        g.mats['lamp_blaster'] = tk.lamp_material('Turret lamp blaster blue', (.13, .13, 1.0), 3.0)
    LB = 'lamp_blaster'
    u = f.u
    # -- the sled on the bay floor, its rails and hold-down lugs.
    sled = g['steel#sled']
    _glacis(sled, _hb_plan([(-6.0, -8.2), (-5.2, -9.0), (11.8, -9.0), (12.6, -8.2), (12.6, 8.2), (11.8, 9.0),
                            (-5.2, 9.0), (-6.0, 8.2)], k), -7.0*k, -5.8*k, .3*k, -5.3*k)
    for x in (-2.6, 1.4, 5.4, 9.4):                                    # lightening notches in the flanks
        for sy in (1, -1):
            sled.cut().box((x*k, sy*9.0*k, -6.4*k), (2.0*k, 1.2*k, .7*k))
    for sy in (1, -1):
        g['dark'].box((3.3*k, sy*6.4*k, -7.25*k), (19.0*k, 1.4*k, .7*k), .1*k)      # rails
        for x in (-4.4, 11.2):                                         # hold-down lugs, a bolt each
            g['steel'].box((x*k, sy*9.5*k, -6.6*k), (1.4*k, 1.4*k, .9*k), .12*k)
            g['steel'].cyl((x*k, sy*9.7*k, -6.05*k), .3*k, .25*k, 'Z', n=16, bevel=.04*k)
        f.bolt_row(g['steel'], (-.6*k, sy*9.03*k, -6.4*k), (7.4*k, sy*9.03*k, -6.4*k), spacing=3.8, axis='Y')
    # -- the capacitor housing: a glacis-roofed casting, trough and cable channels cut in.
    hs = g['paint#housing']
    _glacis(hs, _hb_plan([(-6.2, -10.4), (-5.0, -12.0), (6.4, -12.0), (6.8, -11.4), (6.8, 11.4), (6.4, 12.0),
                          (-5.0, 12.0), (-6.2, 10.4)], k), -5.6*k, 2.6*k, 1.2*k, 3.8*k)
    hs.cut().box((.55*k, 0, 4.0*k), (10.1*k, 14.6*k, 4.8*k))           # the capacitor trough (floor 1.6)
    for sy in (1, -1):
        hs.cut().box((.3*k, sy*10.25*k, 4.0*k), (14.2*k, 2.6*k, 4.0*k))  # cable channel (floor 2.0)
        hs.cut().box((.3*k, sy*12.0*k, -1.6*k), (12.4*k, .5*k, .3*k))    # flank seam
        hs.cut().box((3.4*k, sy*12.0*k, -3.4*k), (.3*k, .5*k, 4.2*k))    # plate joint
        f.hatch(g, (-1.6*k, sy*12.0*k, -5.3*k), axis='+Y' if sy > 0 else '-Y')
    hs.cut().box((-5.4*k, 0, 3.8*k), (.3*k, 15.6*k, .6*k))             # roof seam behind the trough
    # Trough coamings (the sprite's pale lines either side of the bank), bolted down.
    for sy in (1, -1):
        g['accent2'].box((.55*k, sy*7.55*k, 3.95*k), (10.1*k, .5*k, .4*k), .1*k)
        f.bolt_row(g['steel'], (-4.0*k, sy*7.55*k, 4.15*k), (5.2*k, sy*7.55*k, 4.15*k))
    # The capacitor bank: five cells in the trough, grooves cut round them, a charge lamp
    # in one groove each, a bus bar over their rear terminals, a clamp block over the front.
    cells = g['accent#cells']
    CY, CR, CZ = (5.2, 2.6, 0.0, -2.6, -5.2), 1.15, 2.75
    for y in CY:
        cells.lathe([(.85*k, 0), (CR*k, .3*k), (CR*k, 7.9*k), (.85*k, 8.2*k)], 40, (-3.3*k, y*k, CZ*k), 'X')
        for x in (-1.6, .4, 2.4):
            cells.cut().ring((x*k, y*k, CZ*k), (CR-.14)*k, (CR+.4)*k, .24*k, 'X', n=40)
        g[LB].ring((.4*k, y*k, CZ*k), (CR-.2)*k, (CR-.08)*k, .16*k, 'X', n=40)
        g['steel'].cyl((-3.65*k, y*k, CZ*k), .45*k, .8*k, 'X', n=20, bevel=.06*k)         # terminal
    g['accent2'].box((-4.05*k, 0, CZ*k), (.5*k, 12.4*k, .9*k), .1*k)                     # rear bus bar
    for y in CY:
        g['steel'].cyl((-4.05*k, y*k, (CZ+.5)*k), .3*k, .2*k, 'Z', n=16)
    clamp = g['accent2#clamp']
    _glacis(clamp, _hb_plan([(4.2, -7.0), (5.8, -7.0), (5.8, 7.0), (4.2, 7.0)], k), 1.6*k, 3.9*k, .25*k, 4.3*k)
    for y in CY:
        clamp.cut().cyl((5.0*k, y*k, CZ*k), (CR+.04)*k, 3.0*k, 'X', n=40)
    f.bolt_row(g['steel'], (5.0*k, -6.4*k, 4.3*k), (5.0*k, 6.4*k, 4.3*k))
    # -- the power coupling behind the housing: an oval casting into the bay's rear wall.
    cp = g['metal#coupling']
    _hb_loft(cp, [(-9.3, 4.5, 3.4), (-9.0, 5.2, 3.9), (-7.25, 5.2, 3.9), (-7.0, 6.3, 4.4), (-6.7, 6.8, 4.6),
                  (-4.8, 6.8, 4.6)], k)
    for y in (4.5, 2.5, 0.0, -2.5, -4.5):                              # fin grooves (the sprite's rows 42-44)
        zt = _hb_oval_z(y, 5.2, 3.9)
        for sz in (1, -1):
            cp.cut().box((-8.1*k, y*k, sz*zt*k), (1.5*k, .5*k, 1.4*k))
    cp.cut().cyl((-9.3*k, 0, 0), 1.7*k, 1.2*k, 'X', n=40)              # the reactor feed socket
    g['dark'].cyl((-8.75*k, 0, 0), 1.7*k, .1*k, 'X', n=40)
    g['steel'].ring((-9.25*k, 0, 0), 1.7*k, 2.3*k, .3*k, 'X', n=40)
    for i in range(12):                                                 # flange bolts round the socket
        a = 2*math.pi*(i+.5)/12
        g['steel'].cyl((-9.33*k, 3.2*math.cos(a)*k, 2.4*math.sin(a)*k), .2*k, .16*k, 'X', n=12)
    # -- heavy power cabling: two cables a side out of the coupling's crown through glands,
    # forward along the flank channels through a clamp block, into the yoke cheeks.
    for sy in (1, -1):
        for ys, ye in ((1.6, 9.65), (3.0, 10.95)):
            a = [(-7.7, sy*ys, 2.6), (-7.7, sy*(ye-1.8), 4.9), (-6.6, sy*ye, 2.6), (-4.2, sy*ye, 2.6)]
            b = [(-4.2, sy*ye, 2.6), (-.4, sy*ye, 2.6), (3.6, sy*ye, 2.6), (7.6, sy*ye, 2.6)]
            tk._cable(g['cable'], a, .5, k, 0, n=16, segs=20)
            tk._cable(g['cable'], b, .5, k, 0, n=16, segs=12)
            tk._cable(g['steel'], a, .72, k, 0, n=16, segs=2, t0=.05, t1=.17)          # gland
            for t0, t1 in ((.05, .42), (.6, .86)):                                      # armour sleeves
                tk._cable(g['accent'], b, .62, k, 0, n=16, segs=4, t0=t0, t1=t1)
            tk._cable(g['steel'], b, .72, k, 0, n=16, segs=2, t0=.9, t1=.97)            # gland at the cheek
        cc = g[f'steel#cclamp{sy}']
        _glacis(cc, _hb_plan([(-2.6, 8.6), (1.4, 8.6), (1.4, 11.9), (.9, 12.6), (-2.1, 12.6), (-2.6, 11.9)], k, sy),
                1.8*k, 3.6*k, .3*k, 3.95*k)
        for ye in (9.65, 10.95):
            cc.cut().cyl((-.6*k, sy*ye*k, 2.6*k), .52*k, 4.2*k, 'X', n=24)
        f.bolt_row(g['steel'], (-2.0*k, sy*10.3*k, 3.97*k), (.8*k, sy*10.3*k, 3.97*k))
    # -- the neck: the field coil in its cradle between the yoke cheeks.
    coil = g['accent#coil']
    coil.cyl((9.5*k, 0, 0), 5.0*k, 6.4*k, 'X', n=48, bevel=.1*k)
    for x in (7.6, 8.55, 9.5, 10.45, 11.4):                            # winding grooves
        coil.cut().ring((x*k, 0, 0), 4.7*k, 5.4*k, .3*k, 'X', n=48)
    for x in (6.95, 12.05):
        g['steel'].ring((x*k, 0, 0), 4.9*k, 5.5*k, .5*k, 'X', n=48)
    cr = g['steel#cradle']
    _glacis(cr, _hb_plan([(7.4, -4.8), (11.8, -4.8), (11.8, 4.8), (7.4, 4.8)], k), -5.6*k, -4.0*k, .2*k, -3.7*k)
    cr.cut().cyl((9.6*k, 0, 0), 5.0*k, 6.0*k, 'X', n=48)
    for sy in (1, -1):
        ch = g[f'paint#cheek{sy}']
        _glacis(ch, _hb_plan([(6.4, 5.3), (12.9, 5.3), (12.9, 10.3), (12.3, 11.1), (7.0, 11.1), (6.4, 10.5)], k, sy),
                -5.6*k, 2.4*k, .6*k, 3.2*k)
        ch.cut().box((9.65*k, sy*11.1*k, -2.2*k), (6.0*k, .4*k, .25*k))   # seam round the cheek
        ch.cut().box((12.9*k, sy*8.2*k, .4*k), (.5*k, 4.2*k, .25*k))
        # Trunnion boss on the cheek's outer face: bolted, a blue charge ring, its cap.
        g['steel'].cyl((9.6*k, sy*11.65*k, -.6*k), 2.3*k, 1.3*k, 'Y', n=48, bevel=.1*k)
        g[LB].ring((9.6*k, sy*12.3*k, -.6*k), 1.45*k, 1.75*k, .2*k, 'Y', n=40)
        g['accent2'].cyl((9.6*k, sy*12.35*k, -.6*k), 1.3*k, .3*k, 'Y', n=40, bevel=.06*k)
        for i in range(8):
            a = 2*math.pi*(i+.5)/8
            g['steel'].cyl((9.6*k+1.98*math.cos(a)*k, sy*12.33*k, (-.6+1.98*math.sin(a))*k), .14*k, .14*k, 'Y', n=12)
        f.bolt_row(g['steel'], (7.2*k, sy*8.2*k, 3.22*k), (12.2*k, sy*8.2*k, 3.22*k))
    # -- the emitter head: an oval casting, open bore to the front, lens at the fire point.
    head = g['plate#head']
    HD = [(12.3, 7.2, 5.3), (12.7, 7.9, 5.9), (18.9, 7.9, 5.9), (19.2, 7.6, 5.6), (19.4, 7.0, 5.2), (22.4, 7.0, 5.2),
          (22.9, 6.4, 4.8), (23.4, 5.8, 4.4), (23.9, 5.0, 4.0)]
    _hb_loft(head, HD, k)
    head.cut().cyl((20.0*k, 0, 0), 3.7*k, 10.0*k, 'X', n=48)            # the bore, x 15 .. 25
    for y in (4.5, 1.5, -1.5, -4.5):                                    # vent slots over the chamber
        zt = _hb_oval_z(y, 7.9, 5.9)
        for sz in (1, -1):
            head.cut().box((16.75*k, y*k, sz*zt*k), (2.5*k, .75*k, 2.0*k))
        g[LB].box((16.75*k, y*k, (zt-.95)*k), (2.3*k, .55*k, .3*k))
    for y in (4.5, 2.5, 0.0, -2.5, -4.5):                               # fin grooves round the front
        zt = _hb_oval_z(y, 7.0, 5.2)
        for sz in (1, -1):
            head.cut().box((21.0*k, y*k, sz*zt*k), (2.4*k, .5*k, 1.4*k))
    for i in range(6):                                                  # and down its flanks
        a = math.radians(-50+20*i)
        for sy in (1, -1):
            yy, zz = _hb_oval(7.0, 5.2, 360)[int(round(math.degrees(a))) % 360]
            head.cut().box((21.0*k, sy*yy*k, zz*k), (2.4*k, 1.4*k, .5*k))
    bands = g['steel#bands']
    _hb_band(bands, 19.15, 19.85, 7.35, 5.5, k)
    _hb_band(bands, 22.45, 23.05, 7.15, 5.3, k)
    if f.near:                                                          # bolts along the rear band's crown
        for i in range(9):
            y = -4.8+1.2*i
            g['steel'].cyl((19.5*k, y*k, (_hb_oval_z(y, 7.35, 5.5)+.02)*k), .09*k, .1*k, 'Z', n=12)
    # In the bore: a dark liner, the focusing vanes along its wall (lit edges), the lens.
    g['dark'].ring((19.5*k, 0, 0), 3.55*k, 3.72*k, 8.8*k, 'X', n=48)
    g['dark'].cyl((15.05*k, 0, 0), 3.7*k, .1*k, 'X', n=48)
    for i in range(4):
        a = math.pi/4+i*math.pi/2
        rot = Matrix.Rotation(a, 4, 'X')
        g['accent2'].box((19.7*k, 3.15*math.cos(a)*k, 3.15*math.sin(a)*k), (7.2*k, 1.1*k, .55*k), .1*k, rot=rot)
        g[LB].box((19.7*k, 2.58*math.cos(a)*k, 2.58*math.sin(a)*k), (6.4*k, .08*k, .2*k), rot=rot)
    g['steel'].ring((15.3*k, 0, 0), 2.8*k, 3.7*k, .5*k, 'X', n=48)            # lens retaining ring
    g[LB].lathe([(2.85*k, 0), (2.5*k, .35*k), (1.5*k, .65*k), (0, .8*k)], 48, (15.0*k, 0, 0), 'X')
    return [(15.0*k, 0.0, 0.0)]


def _hp_harpoon(g, f, s, k=1.0):
    """missile_rack_open_m_3x_hardpoint_base.png (26 x 40 px; the merchant Wolf's WS 005 /
    WS 006, the Harpoon MRM). An open three-round launch rack, the rounds drawn loaded
    (RENDER_LOADED_MISSILES): the slot is 10 px up from the sprite's back, the rack runs
    from 2.5 px behind it to its rail tips 17 ahead.
    - A grey bed (the sprite's mid-grey lane floors) with three round troughs cut down it
      at y -6 / 0 / 6 (the fire points), windows cut through its floor where the sprite is
      open (rows 19-20 and 28-29), a seam along its flanks.
    - Four pale launch rails between the lanes (the sprite's bright columns), each a lofted
      bar rising at the front into a guide post (rows 13-14), lightening holes through the
      outer two, a guide groove along the inner two; a tie bar under the posts.
    - Two saddles cross the lanes under the rounds; restraint latches on the rails; the rear
      blast plate with an exhaust recess per lane, a ready lamp over each and an umbilical
      from a socket in its face to each round's flank.
    - Hold-down lugs (the sprite's dark brown ears, rows 21-25 and 27-30) on both flanks,
      bolted down, trunnion pins through them.
    - In each lane a Harpoon (22 long, its centre 12 px from its tail on its fire point, as
      the game draws it), on its own 'missile k' empty at the fire point: body and seeker
      section with cut joints, swept fins rolled clear of the rails, a pale nose cap, its
      seeker window lit, a dark nozzle. Rounds and muzzle anchors are in the .wpn's order
      (y -6, 6, 0); the anchors sit on the fire points (15 px ahead)."""
    LANES = (-6.0, 6.0, 0.0)
    RAILS = ((8.5, .55), (3.0, .95), (-3.0, .95), (-8.5, .55))
    # -- the bed: three troughs and the open windows cut in it.
    bed = g['paint#bed']
    _glacis(bed, _hb_plan([(-2.0, -9.2), (-1.3, -9.9), (14.3, -9.9), (15.0, -9.2), (15.0, 9.2), (14.3, 9.9),
                           (-1.3, 9.9), (-2.0, 9.2)], k), -3.4*k, -1.9*k, .3*k, -1.6*k)
    for y in LANES:
        bed.cut().cyl((6.5*k, y*k, 0), 2.2*k, 18.0*k, 'X', n=48)
        for x in (10.25, 1.0):
            bed.cut().box((x*k, y*k, -2.6*k), (1.6*k, 2.0*k, 2.0*k))
    for sy in (1, -1):
        bed.cut().box((6.5*k, sy*9.9*k, -2.7*k), (16.0*k, .4*k, .25*k))      # flank seam
        bed.cut().box((4.0*k, sy*9.9*k, -2.5*k), (.25*k, .4*k, 2.0*k))       # plate joint
    # -- the rails: lofted bars rising into guide posts at the front.
    prof = [(-1.4, .1), (-1.0, .5), (13.8, .5), (14.5, 1.5), (16.5, 1.5), (17.0, .7), (17.2, .2)]
    for i, (yr, hw) in enumerate(RAILS):
        rail = g[f'plate#rail{i}']
        rail.loft_x([(x*k, [(yr*k+y*k, z*k) for y, z in tk.section(hw, -2.0, zt, min(.3, .3*(zt+2.0)), .1, .12, 2)])
                     for x, zt in prof])
        if abs(yr) > 5:                                                    # lightening holes, outer rails
            for x in (1.0, 3.4, 5.8, 8.2, 10.6):
                rail.cut().cyl((x*k, yr*k, -.8*k), .5*k, 2.0*k, 'Y', n=24)
        else:                                                              # guide groove, inner rails
            rail.cut().box((6.4*k, yr*k, .5*k), (14.6*k, .3*k, .4*k))
        yb = yr if abs(yr) > 5 else yr+math.copysign(.42, yr)            # beside the groove
        f.bolt_row(g['steel'], (0*k, yb*k, .52*k), (12.8*k, yb*k, .52*k), spacing=1.1)
        g['steel'].cyl((15.5*k, yr*k, 1.52*k), .3*k, .1*k, 'Z', n=16, bevel=.03*k)   # post cap bolt
    g['steel'].box((16.0*k, 0, -1.7*k), (1.0*k, 17.8*k, .7*k), .12*k)           # tie under the posts
    # -- saddles across the lanes under the rounds, restraint latches on the rails.
    for x in (6.0, 12.5):
        sd = g[f'steel#saddle{x}']
        _glacis(sd, _hb_plan([(x-.5, -8.0), (x+.5, -8.0), (x+.5, 8.0), (x-.5, 8.0)], k), -2.2*k, -.9*k, .12*k, -.7*k)
        for y in LANES:
            sd.cut().cyl((x*k, y*k, 0), 1.32*k, 2.0*k, 'X', n=48)
    for y in LANES:
        for sy in (1, -1):
            g['steel'].box((7.4*k, (y+sy*1.75)*k, .5*k), (1.0*k, 1.1*k, .36*k), .1*k)   # reaches the round
            g['steel'].cyl((7.4*k, (y+sy*2.1)*k, .72*k), .18*k, .12*k, 'Z', n=12)
    # -- the rear blast plate: exhaust recesses, ready lamps, umbilical sockets.
    rp = g['paint#rear']
    _glacis(rp, _hb_plan([(-2.6, -10.0), (-.9, -10.0), (-.9, 10.0), (-2.6, 10.0)], k), -3.4*k, .8*k, .3*k, 1.2*k)
    for y in LANES:
        rp.cut().cyl((-.9*k, y*k, 0), 1.15*k, 1.0*k, 'X', n=40)
        g['dark'].cyl((-1.38*k, y*k, 0), 1.15*k, .06*k, 'X', n=40)
        g['steel'].ring((-.84*k, y*k, 0), 1.15*k, 1.5*k, .14*k, 'X', n=40)
        g['glow'].lathe([(.35*k, 0), (.3*k, .18*k), (0, .3*k)], 24, (-1.75*k, (y-1.4)*k, 1.15*k), 'Z')
        g['steel'].ring((-1.75*k, (y-1.4)*k, 1.2*k), .35*k, .5*k, .14*k, 'Z', n=24)
        g['steel'].cyl((-.75*k, (y+1.55)*k, .9*k), .38*k, .4*k, 'X', n=24, bevel=.05*k)   # socket
        tk._cable(g['cable'], [(-.6, y+1.55, .9), (1.4, y+1.9, 1.7), (3.0, y+1.3, 1.5), (4.0, y+.95, .9)],
                  .2, k, 0, n=16, segs=16)
    f.bolt_row(g['steel'], (-1.75*k, -9.0*k, 1.22*k), (-1.75*k, 9.0*k, 1.22*k), spacing=1.4)
    # -- hold-down lugs on both flanks, bolted down, trunnion pins through them.
    for x0, x1 in ((4.6, 9.2), (-.8, 3.2)):
        for sy in (1, -1):
            lug = g[f'accent2#lug{x0}{sy}']
            _glacis(lug, _hb_plan([(x0, 9.4), (x1, 9.4), (x1, 11.4), (x1-.8, 12.2), (x0+.8, 12.2), (x0, 11.4)], k, sy),
                    -3.4*k, -.9*k, .3*k, -.6*k)
            lug.cut().box(((x0+x1)/2*k, sy*12.2*k, -2.0*k), ((x1-x0-1.2)*k, .5*k, .25*k))
            g['plate'].cyl(((x1-1.0)*k, sy*12.25*k, -1.9*k), .8*k, .5*k, 'Y', n=32, bevel=.08*k)
            g['steel'].cyl(((x1-1.0)*k, sy*12.55*k, -1.9*k), .45*k, .25*k, 'Y', n=6)
            g['plate'].cyl(((x0+.9)*k, sy*10.4*k, -.55*k), .5*k, .2*k, 'Z', n=24, bevel=.05*k)
            g['plate'].cyl(((x1-1.0)*k, sy*10.4*k, -.55*k), .5*k, .2*k, 'Z', n=24, bevel=.05*k)
    # -- the loaded Harpoons.
    mouths = []
    for i, y in enumerate(LANES):
        m = g.sub(f'missile {i+1}', (15.0*k, y*k, 0), 1.5*k)
        c = (3.0*k, y*k, 0)
        body = m['missile#body']
        body.lathe([(.75*k, 0), (1.05*k, .25*k), (1.18*k, .6*k), (1.18*k, 8.6*k), (1.3*k, 9.0*k), (1.3*k, 17.4*k),
                    (1.22*k, 18.2*k), (1.0*k, 19.6*k), (.7*k, 20.6*k), (.4*k, 21.3*k)], 40, c, 'X')
        for a in (5.0, 13.2):                                              # section joints
            body.cut().ring(((3.0+a)*k, y*k, 0), 1.2*k, 1.6*k, .14*k, 'X', n=40)
        body.cut().cyl((3.0*k, y*k, 0), .55*k, .9*k, 'X', n=24)            # nozzle
        m['dark'].cyl((3.42*k, y*k, 0), .55*k, .06*k, 'X', n=24)
        m['plate'].lathe([(.42*k, 21.25*k), (.3*k, 21.65*k), (.14*k, 21.92*k), (.05*k, 22.0*k)], 32, c, 'X')
        m['steel'].ring((20.4*k, y*k, 0), 1.3*k, 1.37*k, .3*k, 'X', n=40)  # warhead band
        m['glow'].box((22.9*k, y*k, .72*k), (.6*k, .32*k, .14*k), .03*k)   # seeker window
        for x0, x1, ro in ((9.0, 11.4, 2.05), (3.4, 5.4, 1.9)):            # swept fins, rolled 45 degrees
            fin = m['missile#fins']
            for j in range(4):
                a = math.pi/4+j*math.pi/2
                ca, sa = math.cos(a), math.sin(a)

                def loop(r0, r1, t=.07):
                    return [((y+r*ca-tt*sa)*k, (r*sa+tt*ca)*k) for r, tt in ((r0, -t), (r1, -t), (r1, t), (r0, t))]
                fin.loft_x([(x0*k, loop(1.0, ro)), ((x1-.9)*k, loop(1.0, ro)), (x1*k, loop(1.0, 1.25))])
        mouths.append((15.0*k, y*k, 0.0))
    return mouths


HARDPOINTS = {
    # The Lasher's stock fit (lasher_Standard): light assault guns in its gun pods,
    # heatseekers (Salamander MRM) in its missile pods. Paints off the sprites: the
    # LAG's gold drums on a dark frame, the autoloader's grey cheeks and copper rails.
    # `bay`: the pocket a hull cuts for it (x0, x1 along the fire line, half-width,
    # floor below the axis), so the housing sits sunk in the hull, not on it.
    'lightag': dict(builder=_hp_lightag, paints={'paint': (.24, .24, .23), 'accent': (.55, .45, .2),
                                                 'plate': (.6, .58, .52), 'metal': (.33, .33, .34)},
                    bay=(-5.4, 7.4, 4.8, -3.1)),
    'heatseeker': dict(builder=_hp_heatseeker, mcol=(.16, .16, .17),
                       paints={'paint': (.38, .37, .36), 'accent': (.5, .26, .16), 'plate': (.66, .65, .62)},
                       bay=(-5.6, 16.6, 5.6, -2.9)),
    # Kite (P) WS 001 / WS 002, Falcon (P) WS 001 / WS 002 and the Shepherd (Frontier):
    # the Swarmer SRM and Hammer torpedo mounts.

    # The Kite (P)'s fit: Swarmer SRM (WS 002) and Hammer (WS 001). Paints off the
    # sprites: the Swarmer's red housing and orange-red tubes, pale collars, white
    # stencil, the SRM's ochre body; the Hammer's brown-grey block, grey troughs,
    # yellow hazard plates, pale hatch, the torpedo's grey body and red warhead.
    'swarmer': dict(builder=_hp_swarmer, mcol=(.5, .36, .03),
                    paints={'paint': (.52, .1, .085), 'accent': (.55, .15, .12), 'plate': (.46, .48, .5),
                            'metal': (.33, .33, .35), 'mark': (.82, .82, .8)},
                    bay=(-6.0, 12.4, 8.4, -2.9)),
    'hammer': dict(builder=_hp_hammer, mcol=(.3, .25, .24),
                   paints={'paint': (.2, .17, .15), 'plate': (.26, .25, .24), 'accent': (.72, .4, .05),
                           'accent2': (.62, .1, .05), 'mark': (.55, .45, .38)},
                   bay=(-6.2, 14.0, 7.6, -2.9)),
    # Hound (A) WS 001 (its spine chaingun) and Manticore (P) WS 004 / WS 005 (Harpoon pods).

    # The Hound (A)'s spine gun (WS 001): the rotary cannon's tan
    # housing, brown drive motor, darker chute and gunmetal barrels.
    # `bay`: the breech housing and motor sunk; the chute and box ride over the deck.
    'chaingun': dict(builder=_hp_chaingun,
                     paints={'paint': (.39, .31, .19), 'accent': (.16, .11, .07), 'plate': (.26, .21, .15),
                             'metal': (.24, .23, .23), 'accent2': (.5, .36, .14)},
                     bay=(-9.0, 10.2, 10.6, -5.6)),
    # Manticore (P) WS 004 / WS 005: grey rails, tan bars, pale posts, dark magazine,
    # orange marks; the Harpoon's green-grey body.
    'harpoonpod': dict(builder=_hp_harpoonpod, mcol=(.3, .38, .27),
                       paints={'paint': (.22, .2, .16), 'accent': (.38, .31, .23), 'plate': (.73, .66, .53),
                               'accent2': (.16, .13, .1), 'mark': (.49, .22, .04)},
                       bay=(-6.4, 14.6, 15.4, -3.4)),
    # The merchant Wolf's stock fit: heavy blaster (WS 004) and Harpoon MRM racks (WS 005 / WS 006).

    # The merchant Wolf's fit: the Heavy Blaster in its bow (WS 004) and Harpoon racks
    # (WS 005 / 006). Paints off the sprites (linear medians): the blaster's dark grey
    # housing, pale head, blue-grey capacitor cells and pale trim; the rack's mid-grey bed,
    # pale rails, dark brown lugs and the Harpoon's green-grey body.
    'heavyblaster': dict(builder=_hp_heavyblaster,
                         paints={'paint': (.082, .087, .093), 'plate': (.456, .462, .474),
                                 'accent': (.349, .418, .515), 'accent2': (.552, .565, .584),
                                 'metal': (.15, .153, .153)},
                         bay=(-9.6, 13.0, 12.8, -7.8)),
    'harpoon': dict(builder=_hp_harpoon, mcol=(.178, .236, .162),
                    paints={'paint': (.165, .165, .175), 'plate': (.518, .512, .539),
                            'accent2': (.019, .011, .012)},
                    bay=(-2.8, 17.4, 12.6, -3.8)),
}


def register(kit):
    global tk
    tk = kit
    kit.HARDPOINT_WEAPONS.update(HARDPOINTS)
