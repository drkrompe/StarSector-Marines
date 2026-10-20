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
    sh.prism([(x*k, y*k) for x, y in plan], -2.8*k, 1.4*k, .7*k, 2.1*k)
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
                 -2.6*k, .8*k, .3*k, 1.2*k)
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
}


def register(kit):
    global tk
    tk = kit
    kit.HARDPOINT_WEAPONS.update(HARDPOINTS)
