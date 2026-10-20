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


def _hp_lightag(g, f, s, k=1.0):
    """light_assault_gun_hardpoint_base.png (14 x 26 px; the Lasher's WS 002 / WS 003).
    A faired gun housing set into its bay: the armoured shell (the sprite's dark
    frame) from 5 px behind the slot to a stepped glacis 7 px ahead, its two gold
    ammunition drums lying in troughs either side of the pale breech cover (the
    sprite's gold lobes and its lit centre strip), side rails bolted along its
    flanks; the front is a mantlet plate with the barrel's port, and the two pale
    guide prongs with gold tips run out either side of the barrel (rows +8..+11).
    The barrel (the recoil sprite: a 2-px barrel through a 4-px shroud to its mouth
    18.5 px ahead) rides 'recoil 1' through a fixed recoil sleeve. Muzzle anchor at
    the mouth. Built to a bay 9.4 wide: the sprite's outer frame pixels are inside
    the hull. The bay this wants is HARDPOINT_WEAPONS['lightag']['bay']."""
    sh = g['paint#housing']
    plan = tk._ccw([(-5.0, -4.4), (5.6, -4.4), (7.2, -3.4), (7.2, 3.4), (5.6, 4.4), (-5.0, 4.4)])
    sh.prism([(x*k, y*k) for x, y in plan], -2.8*k, 1.4*k, .7*k, 2.1*k, glacis=True)
    for sy in (1, -1):                                   # troughs the drums lie in
        sh.cut().cyl((-.2*k, sy*2.25*k, 2.1*k), 1.45*k, 7.6*k, 'X', n=24)
    sh.cut().box((-.2*k, 0, 2.3*k), (8.2*k, 1.4*k, 1.4*k))   # the breech cover's channel
    for sy in (1, -1):
        drum = g['accent']
        drum.cyl((-.2*k, sy*2.25*k, 1.75*k), 1.3*k, 7.0*k, 'X', n=24, bevel=.2*k)
        for xx in (-2.6, 2.2):                           # straps over each drum
            g['steel'].ring((xx*k, sy*2.25*k, 1.75*k), 1.3*k, 1.42*k, .35*k, 'X', n=24)
        g['dark'].box((3.5*k, sy*1.3*k, 1.9*k), (.9*k, .55*k, .6*k))      # feed throat into the breech
        g['steel'].box((1.0*k, sy*4.65*k, -.9*k), (11.5*k, .5*k, 1.3*k), .1*k)   # side rail
        f.bolt_row(g['steel'], (-4.2*k, sy*4.92*k, -.9*k), (6.4*k, sy*4.92*k, -.9*k), axis='Y')
    g['plate'].box((-.2*k, 0, 2.15*k), (8.0*k, 1.2*k, .55*k), .12*k)       # breech cover
    for xx in (-3.0, -1.0, 1.0, 3.0):
        g['dark'].box((xx*k, 0, 2.44*k), (.25*k, 1.0*k, .04*k))
    # Mantlet plate across the glacis front, the port cut through it.
    face = g['steel#face']
    face.box((7.5*k, 0, -.6*k), (.7*k, 6.6*k, 4.0*k), .12*k)
    face.cut().cyl((7.5*k, 0, 0), 1.0*k, 2*k, 'X', n=20)
    f.bolt_row(g['steel'], (7.88*k, -2.8*k, 1.1*k), (7.88*k, 2.8*k, 1.1*k), axis='X')
    f.bolt_row(g['steel'], (7.88*k, -2.8*k, -2.3*k), (7.88*k, 2.8*k, -2.3*k), axis='X')
    # The guide prongs either side of the barrel, gold tips.
    for sy in (1, -1):
        g['plate'].box((9.3*k, sy*2.5*k, -.2*k), (3.8*k, .9*k, 1.2*k), .15*k)
        g['accent'].box((11.5*k, sy*2.5*k, -.2*k), (.8*k, .9*k, 1.2*k), .15*k)
        g['dark'].box((9.3*k, sy*2.5*k, .42*k), (2.6*k, .3*k, .05*k))
    g['steel'].box((8.2*k, 0, -1.6*k), (1.8*k, 5.9*k, .7*k), .1*k)          # tie bar under the prongs
    # Fixed recoil sleeve in the port; the barrel slides back through it.
    g['metal'].cyl((8.4*k, 0, 0), 1.25*k, 1.6*k, 'X', n=24, bevel=.1*k)
    g['dark'].ring((9.25*k, 0, 0), .95*k, 1.3*k, .12*k, 'X', n=24)
    rg = g.sub('recoil 1', (5.0*k, 0, 0), 1.5*k)
    rg['metal'].cyl((11.7*k, 0, 0), .9*k, 13.4*k, 'X', n=20, bevel=.06*k)           # barrel x 5..18.4
    rg['metal'].cyl((11.2*k, 0, 0), 1.45*k, 4.2*k, 'X', n=24, bevel=.15*k)          # shroud
    for xx in (9.9, 10.9, 11.9, 12.6):
        rg['dark'].ring((xx*k, 0, 0), 1.44*k, 1.5*k, .18*k, 'X', n=24)
    rg['dark'].ring((14.0*k, 0, 0), .89*k, .96*k, .35*k, 'X', n=20)
    brake = rg['steel#brake']
    brake.cyl((17.8*k, 0, 0), 1.08*k, 1.3*k, 'X', n=20, bevel=.1*k)                 # muzzle brake
    brake.cut().cyl((18.5*k, 0, 0), .5*k, 1.6*k, 'X', n=16)
    for sy in (1, -1):
        brake.cut().box((17.8*k, sy*1.0*k, 0), (.45*k, .6*k, 1.2*k))
    return [(18.5*k, 0.0, 0.0)]


def _hp_heatseeker(g, f, s, k=1.0):
    """missile_autoloader_1x_hardpoint_base.png (16 x 26 px; the Lasher's WS 001 / WS 004
    heatseeker, the Salamander MRM). An autoloading launch rail: the pale magazine
    block at the back (the sprite's white rows -3..0) with its loading hatch, two grey
    cheeks down the sides carrying the copper launch rails (the sprite's red columns
    either side), pale bumper lobes on the cheeks, the open channel between them with
    one Salamander lying on the rails, its nose at the fire point 12 px ahead of the
    slot (hardpointOffsets), and the two front horns. The round is its own 'missile 1'
    empty, so a shot can launch it; the muzzle anchor is at its nose."""
    mag = g['plate#mag']
    mag.box((-2.6*k, 0, -.4*k), (5.4*k, 7.0*k, 4.4*k), .3*k)
    mag.cut().box((-2.6*k, 0, 1.9*k), (3.0*k, 3.6*k, .5*k))                   # loading hatch well
    g['steel'].box((-2.6*k, 0, 1.72*k), (2.8*k, 3.4*k, .2*k), .05*k)          # its lid
    g['steel'].cyl((-4.05*k, 0, 1.85*k), .14*k, 3.0*k, 'Y', n=8)              # hinge
    g['dark'].box((.05*k, 0, -.2*k), (.3*k, 2.8*k, 2.8*k))                    # breech the round feeds from
    for sy in (1, -1):
        ch = g[f'paint#cheek{sy}']
        prof = tk._ccw([(-1.0, 3.6), (15.8, 3.6), (16.4, 4.2), (16.4, 5.0), (-1.0, 5.0)])
        ch.prism([(x*k, sy*y*k) for x, y in (prof if sy > 0 else tk._ccw([(x, -y) for x, y in prof]))],
                 -2.6*k, .8*k, .3*k, 1.2*k, glacis=True)
        for x0, x1 in ((7.0, 9.0), (13.6, 15.4)):                            # bumper lobes
            g['plate'].box(((x0+x1)/2*k, sy*5.2*k, -.6*k), ((x1-x0)*k, .7*k, 2.2*k), .2*k)
        g['accent'].box((8.0*k, sy*3.15*k, -.7*k), (16.0*k, .5*k, .8*k), .1*k)  # copper rail
        g['dark'].box((8.0*k, sy*3.15*k, -1.15*k), (16.0*k, .55*k, .1*k))
        f.bolt_row(g['steel'], (0.5*k, sy*4.3*k, 1.22*k), (15.0*k, sy*4.3*k, 1.22*k))
        g['steel'].box((17.2*k, sy*4.4*k, -.4*k), (1.8*k, .8*k, 1.6*k), .15*k)  # front horn
    g['dark'].box((8.0*k, 0, -2.2*k), (16.0*k, 6.4*k, .4*k))                   # the channel's floor
    for xx in (2.0, 9.0, 15.5):                                                # cross ties
        g['steel'].box((xx*k, 0, -1.85*k), (.6*k, 6.4*k, .4*k), .05*k)
    m = g.sub('missile 1', (.5*k, 0, 0), 1.5*k)
    tk._missile(m, f, .5*k, 0, 0, 11.5*k, 1.1*k, True)
    for sy in (1, -1):                                                         # seeker lamps
        m['glow'].box((10.0*k, sy*.75*k, .45*k), (.4*k, .3*k, .25*k))
    return [(12.0*k, 0.0, 0.0)]


def _glacis(m, poly, z0, z1, inset, z2):
    m.prism(poly, z0, z1, inset, z2, glacis=True)


def _hp_swarmer(g, f, s, k=1.0):
    """swarmer_srm_launcher_hardpoint_base.png (16 x 24 px; the Kite (P)'s WS 002, the
    Falcon (P)'s WS 001 / 002, the Shepherd's). A four-tube rocket pack: the red
    breech housing (sprite rows 15-22) from 6 px behind the slot to its front face
    3 px ahead, a seam groove across its roof, the white stencilled chevron, four
    pale igniter caps let into wells over the tube breeches and the steel breech caps
    out of its back; the port-side fuse box with its red lamp (the bright lobe in
    column 0). Out of the housing's face run the four orange-red launch tubes at
    y +-1.5 / +-4.5 (the sprite's three-pixel tube columns), clamped at mid-length,
    each ending in a pale collar 12 px ahead of the slot (rows 7-8) with its bore cut
    open, dark-lined, and the SRM's grey nose sitting 1.4 px down it. Each round is
    its own 'missile k' empty; the muzzle anchors are at the tube mouths (the .wpn
    fires at x 15, y +-2 / +-4; the anchors keep to the tubes the model has)."""
    TY = (4.5, 1.5, -1.5, -4.5)
    R, RB = 1.45, 1.05                                   # tube and bore radius
    # The breech housing, its roof stepped in on a glacis.
    hs = g['paint#housing']
    plan = tk._ccw([(-4.6, -6.3), (-3.9, -7.0), (2.4, -7.0), (3.2, -6.2), (3.2, 6.2), (2.4, 7.0),
                    (-3.9, 7.0), (-4.6, 6.3)])
    _glacis(hs, [(x*k, y*k) for x, y in plan], -2.6*k, 1.6*k, .6*k, 2.3*k)
    hs.cut().box((-1.6*k, 0, 2.3*k), (.35*k, 15*k, .5*k))            # roof seam (row 19)
    for y in TY:                                                       # igniter wells (rows 20-21)
        hs.cut().cyl((-3.0*k, y*k, 2.3*k), .62*k, .7*k, 'Z', n=16)
        g['plate'].cyl((-3.0*k, y*k, 2.12*k), .55*k, .12*k, 'Z', n=16, bevel=.03*k)
        g['steel'].cyl((-3.0*k, y*k, 2.2*k), .16*k, .1*k, 'Z', n=8)
    for sy in (1, -1):
        f.bolt_row(g['steel'], (-3.4*k, sy*6.05*k, 2.3*k), (1.9*k, sy*6.05*k, 2.3*k))
    # The white chevron stencilled on the starboard roof (rows 16-18).
    g['mark'].prism([(x*k, y*k) for x, y in tk._ccw([(2.0, -2.6), (-1.0, -.3), (-1.0, -.9), (1.3, -2.6),
                                                     (-1.0, -4.3), (-1.0, -4.9)])], 2.3*k, 2.34*k)
    # Breech caps out of the housing's back (row 22), a dimple in each.
    caps = g['metal#caps']
    for y in TY:
        caps.cyl((-4.9*k, y*k, 0), 1.2*k, .9*k, 'X', n=20, bevel=.1*k)
        caps.cut().cyl((-5.4*k, y*k, 0), .45*k, .5*k, 'X', n=12)
    # Port fuse box (column 0) with its lamp, wired into the housing's flank.
    fb = g['accent#fusebox']
    fb.box((0.0, 7.45*k, -.5*k), (4.4*k, 1.5*k, 2.6*k), .15*k)
    fb.cut().box((0.0, 8.2*k, -.5*k), (3.2*k, .3*k, .12*k))              # lid seam
    g['steel'].cyl((1.0*k, 7.45*k, .8*k), .45*k, .2*k, 'Z', n=12)
    g['lamp_red'].lathe([(.38*k, 0), (.3*k, .2*k), (0, .32*k)], 12, (1.0*k, 7.45*k, .9*k), 'Z')
    f.bolt_row(g['steel'], (-1.7*k, 8.2*k, .5*k), (1.7*k, 8.2*k, .5*k), axis='Y')
    tk._cable(g['cable'], [(-1.6, 7.6, -1.0), (-3.0, 7.9, -1.6), (-3.4, 7.2, -1.9), (-3.6, 6.8, -1.9)],
              .28, k, 0, n=8, segs=10)
    # The four launch tubes: orange-red shells out of the housing face, bores cut open.
    tubes = g['accent#tubes']
    for y in TY:
        tubes.cyl((2.9*k, y*k, 0), R*k, 14.6*k, 'X', n=24, bevel=.08*k)  # x -4.4 .. 10.2
        tubes.cut().cyl((8.6*k, y*k, 0), (RB+.03)*k, 8.4*k, 'X', n=24)   # bore x 4.4 .. 12.8
        g['dark'].ring((7.3*k, y*k, 0), .95*k, (RB+.02)*k, 5.8*k, 'X', n=24)   # bore liner
        g['dark'].cyl((4.45*k, y*k, 0), (RB+.02)*k, .1*k, 'X', n=24)          # bore floor
    col = g['plate#collars']
    for y in TY:
        col.cyl((11.1*k, y*k, 0), 1.48*k, 1.8*k, 'X', n=24, bevel=.1*k)       # mouth collar (rows 7-8)
        col.cut().cyl((11.6*k, y*k, 0), (RB+.03)*k, 2.2*k, 'X', n=24)
        g['steel'].ring((3.65*k, y*k, 0), 1.4*k, 1.62*k, .5*k, 'X', n=24)      # root band (row 14)
    # Clamp at mid-length: saddle under the tubes, cap bar over them, tie bolts.
    g['steel'].box((7.0*k, 0, -1.35*k), (1.0*k, 12.2*k, .7*k), .08*k)
    g['steel'].box((7.0*k, 0, 1.55*k), (1.0*k, 12.2*k, .32*k), .08*k)
    for sy in (1, -1):
        g['steel'].box((7.0*k, sy*6.05*k, .1*k), (1.0*k, .35*k, 3.2*k), .05*k)
    f.bolt_row(g['steel'], (7.0*k, -5.0*k, 1.72*k), (7.0*k, 5.0*k, 1.72*k))
    mouths = []
    for i, y in enumerate(TY):
        m = g.sub(f'missile {i+1}', (4.5*k, y*k, 0), 1.0*k)
        m['missile'].lathe([(.6*k, 0), (.85*k, .15*k), (.85*k, 4.5*k)], 20, (4.5*k, y*k, 0), 'X')
        m['plate'].lathe([(.86*k, 4.45*k), (.78*k, 5.1*k), (.5*k, 5.8*k), (.15*k, 6.08*k), (0, 6.1*k)], 20,
                         (4.5*k, y*k, 0), 'X')                                 # grey nose, tip x 10.6
        m['dark'].ring((8.95*k, y*k, 0), .84*k, .88*k, .12*k, 'X', n=20)
        mouths.append((12.0*k, y*k, 0.0))
    return mouths


def _hp_hammer(g, f, s, k=1.0):
    """torpedo_mount_2x_lowtech_small_hardpoint.png (16 x 28 px; the Kite (P)'s WS 001).
    A low-tech twin torpedo cradle (RENDER_LOADED_MISSILES: the rounds are drawn).
    A brown-grey magazine block from 6 px behind the slot to 3 px ahead (rows 18-26),
    a seam groove across it (rows 18-19), the yellow hazard plates on its rear roof
    with diagonal grooves either side of a pale access hatch (rows 22-25), lifting
    lugs at its shoulders, and two launch ports in its face. From the ports two open
    troughs run out to a front cross-bar 14 px ahead (rows 7-8), each a grey channel
    with steel rails the torpedo lies on, a sunk web between them at the root (the
    dark-green rows 14-17) and open ground between them further out (the sprite's
    gap). In each lies a Hammer: grey body, red warhead (the round's sprite), two pale
    fuse prongs; its tail in the dark port, its nose at x 15. Rounds on 'missile k'.
    The .wpn fires at x 8, y +-4; the anchors are at the noses, on the lane centres."""
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
        g['steel'].ring((3.1*k, y*k, 0), 2.05*k, 2.45*k, .5*k, 'X', n=28)    # port collar
    for sy in (1, -1):
        f.bolt_row(g['steel'], (-4.6*k, sy*6.2*k, 2.4*k), (.6*k, sy*6.2*k, 2.4*k))
        g['steel'].box((.4*k, sy*7.15*k, .6*k), (1.0*k, .4*k, 1.2*k), .08*k)  # lifting lugs (row 20)
        g['steel'].cyl((.4*k, sy*7.4*k, .6*k), .25*k, .5*k, 'Y', n=10)
        # Yellow hazard plate, diagonal grooves cut into it (rows 23-25).
        hz = g[f'accent#hazard{sy}']
        hz.box((-3.6*k, sy*3.95*k, 2.47*k), (2.8*k, 4.3*k, .16*k), .04*k)
        for i in range(4):
            hz.cut().box(((-4.5+i*.95)*k, sy*3.95*k, 2.56*k), (.3*k, 5.5*k, .12*k),
                         rot=Matrix.Rotation(sy*math.radians(40), 4, 'Z'))
    hatch = g['mark#hatch']                                                  # pale centre hatch
    hatch.box((-3.4*k, 0, 2.46*k), (3.0*k, 2.6*k, .14*k), .04*k)
    hatch.cut().box((-3.4*k, 0, 2.55*k), (2.2*k, .2*k, .1*k))
    g['steel'].cyl((-4.95*k, 0, 2.55*k), .12*k, 2.2*k, 'Y', n=8)              # hinge
    # The troughs: a grey channel each, rails under the round, from the face to the bar.
    for i, y in enumerate(TY):
        sy = 1 if y > 0 else -1
        tr = g[f'plate#trough{i+1}']
        tr.box((7.6*k, y*k, -1.1*k), (9.4*k, 5.0*k, 2.6*k), .1*k)          # x 2.9 .. 12.3, y 1 .. 6
        tr.cut().cyl((8.0*k, y*k, 0), RC*k, 11*k, 'X', n=32)
        for xx in (5.4, 8.2, 11.0):                                        # rib slots in the cheeks
            tr.cut().box((xx*k, (y+sy*2.3)*k, .2*k), (.4*k, .6*k, .8*k))
        for dy in (.9, -.9):
            g['steel'].box((7.6*k, (y+dy)*k, -1.36*k), (9.4*k, .3*k, .35*k))
        f.bolt_row(g['steel'], (4.0*k, (y+sy*2.1)*k, .2*k), (11.6*k, (y+sy*2.1)*k, .2*k))
    g['steel'].box((5.0*k, 0, -1.9*k), (4.2*k, 2.4*k, .5*k), .05*k)         # sunk web (rows 14-17)
    g['steel'].box((13.1*k, 0, -1.75*k), (1.6*k, 13.2*k, 1.3*k), .1*k)      # front bar (rows 7-8)
    for sy in (1, -1):
        g['plate'].box((13.5*k, sy*1.75*k, -1.0*k), (1.2*k, .7*k, .9*k), .1*k)   # guide horns
        g['plate'].box((13.3*k, sy*6.15*k, -1.1*k), (1.4*k, .9*k, 1.2*k), .1*k)  # outer lugs
    noses = []
    for i, y in enumerate(TY):
        m = g.sub(f'missile {i+1}', (1.0*k, y*k, 0), 1.5*k)
        c = (1.0*k, y*k, 0)
        m['missile'].lathe([(1.0*k, 0), (RT*k, .45*k), (RT*k, 9.9*k)], 24, c, 'X')
        m['accent2'].lathe([(RT*k, 9.85*k), (1.46*k, 10.8*k), (1.2*k, 12.1*k), (.7*k, 13.3*k), (0, 14.0*k)], 24,
                           c, 'X')                                          # red warhead, tip x 15
        m['steel'].ring((10.9*k, y*k, 0), RT*k, 1.56*k, .3*k, 'X', n=24)     # warhead band
        m['dark'].cyl((1.0*k, y*k, 0), .6*k, .12*k, 'X', n=12)               # nozzle
        for ang in (.25*math.pi, .75*math.pi, 1.25*math.pi, 1.75*math.pi):   # tail fins inside the port
            m['missile'].box((2.0*k, y*k+math.cos(ang)*1.7*k, math.sin(ang)*1.7*k), (1.8*k, .5*k, .1*k),
                             rot=Matrix.Rotation(ang, 4, 'X'))
        for dy in (.45, -.45):                                               # fuse prongs (round's rows 1-2)
            m['plate'].cyl((14.2*k, (y+dy)*k, .2*k), .1*k, 1.6*k, 'X', n=6)
        noses.append((15.0*k, y*k, 0.0))
    return noses


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
}


def register(kit):
    global tk
    tk = kit
    kit.HARDPOINT_WEAPONS.update(HARDPOINTS)
