"""Colossus bridge: the modest deckhouse on the aft machinery body.

colossus.png puts the bridge at the aft centre: a pale rounded shell (X -42..-71,
Y +-25) inside a red frame, standing on the aft body and looking forward over
the cargo body along the spine. A freighter's bridge, not a warship's: one
low deckhouse with "primitive accommodations".

Read at 13x, the shell is two steps. The forward third (X -42..-52) is a low
annex whose roof the sprite draws pink-red, ending at a row of lit panes
(X -53.5, Y -8.5..+9); behind it the pale cab rises to the roof, with a
D-shaped ring on its roof (X -57..-65, Y +-6), a row of three dark ports each
side near its aft edge (X ~-68, Y rel +-10..15) and a dark notch on the aft
centreline (X -70): the personnel door, opening onto the red landing the
sprite draws aft of it between two pale housings (cl_aft). Pale-yellow dots
along the red frame's flanks (Y rel +-27, X -62..-70) are its running lamps.

So the deckhouse is cut down forward to a one-deck annex, and the cab's
forward face is a raked windscreen of framed panes over that roof; wing ports
sit on the cab's side shoulders; a red coaming frame runs round the base; the
roof carries a sensor ring with a dish and a short mast with a yard, whips and
lamps. The door is person-sized at ~0.5 m per unit (4.6 units tall) with a step
and hand rails. 'Bridge deckhouse' is a scale solid and stays closed.
"""
import math
import bmesh
import bpy
from mathutils import Vector
from cl_kit import prism, section, inset, boolean, material
from cl_aft import beam, block, solid, seat, lamp
import cl_hull

X0, X1 = -71.0, -42.0   # aft / forward faces
HALF = 24.0
DECK = cl_hull.AFT_DECK
ROOF = DECK+11.0
ANNEX = DECK+5.5        # roof of the forward annex
RAKE = (-51.5, -54.0)   # windscreen foot / head X: the face leans aft as it rises
SHOULDER = ROOF-4.5     # where the cab's shoulder chamfer starts
YC = cl_hull.YC


def _screen_x(z):
    return RAKE[0]+(RAKE[1]-RAKE[0])*(z-ANNEX)/(ROOF-ANNEX)


def revolve_z(kit, name, profile, mat, n=32):
    """Closed (r, z) outline revolved about the local Z axis."""
    verts = [(r*math.cos(2*math.pi*i/n), r*math.sin(2*math.pi*i/n), z) for r, z in profile for i in range(n)]
    m = len(profile)
    faces = []
    for a in range(m):
        b = (a+1) % m
        faces += [(a*n+i, a*n+(i+1) % n, b*n+(i+1) % n, b*n+i) for i in range(n)]
    return solid(kit, name, verts, faces, mat)


def build(kit):
    half = [(X1, HALF-8), (X1-4, HALF-1.5), (X1-10, HALF), (X0+6, HALF), (X0, HALF-6)]
    house = prism(kit, 'Bridge deckhouse', cl_hull.plan(half), section(DECK-1, ROOF, .5, 4.5), kit.pale)
    # Step the forward third down to a one-deck annex; the cut face is the raked windscreen wall.
    zt = ROOF+20
    xs = [(-38, ANNEX), (RAKE[0], ANNEX), (_screen_x(zt), zt), (-38, zt)]
    verts = [(x, yy, z) for yy in (-40, 40) for x, z in xs]
    cutter = kit.mesh('Temporary annex cutter', verts, [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2),
                                                        (2, 6, 7, 3), (3, 7, 4, 0)], kit.pale)
    boolean(house, cutter)
    kit.hull.append(house)

    dark_glass = material('Bridge dark port glass', (.03, .04, .05), .3, .2)
    tilt = math.atan2(RAKE[0]-RAKE[1], ROOF-ANNEX)          # lean of the windscreen from vertical
    n = Vector((math.cos(tilt), 0, math.sin(tilt)))          # forward-up face normal
    up = Vector((-math.sin(tilt), 0, math.cos(tilt)))        # up the face

    def on_screen(yy, z, off):
        return Vector((_screen_x(z), yy, z))+n*off

    # Annex roof: red plating (the sprite's pink-red forward block) with two dark seams at Y rel +-11.
    ann = [(X1-.5, HALF-8.4), (X1-4.2, HALF-2.1), (RAKE[0]-.4, HALF-.8)]
    prism(kit, 'Bridge annex roof plating', inset(cl_hull.plan(ann), .3), [(0, ANNEX-.2), (0, ANNEX+.3)], kit.red, bevel=.05)
    v, f = [], []
    for s in (1, -1):
        yy = cl_hull.y(11.5, s)
        block(v, f, (-51.2, yy-.3, ANNEX+.25), (-43.5, yy+.3, ANNEX+.45))
    solid(kit, 'Bridge annex roof seams', v, f, kit.dark)

    # Windscreen: five lit panes, Y -8.5..+11.5 about the centreline, on the raked face.
    zb, zh = ANNEX+1.0, ROOF-1.6
    zc = (zb+zh)/2
    span = (zh-zb)/math.cos(tilt)
    w = 20.0
    g = kit.box('Bridge windscreen', tuple(on_screen(YC, zc, .1)), (.3, w, span), kit.glass, bevel=0)
    g.rotation_euler = (0, -tilt, 0)
    v, f = [], []
    for k in range(6):                                       # mullions
        yy = YC-w/2+w*k/5
        beam(v, f, on_screen(yy, zb-.3, .3), on_screen(yy, zh+.3, .3), .5, .4, n)
    for z in (zb-.2, zh+.2):                                 # sill and head rails
        beam(v, f, on_screen(YC-w/2-.4, z, .3), on_screen(YC+w/2+.4, z, .3), .55, .4, (0, 0, 1))
    solid(kit, 'Bridge windscreen frame', v, f, kit.steel)
    # A shallow visor over the panes.
    v, f = [], []
    a = on_screen(YC-w/2-.8, ROOF-.6, .2)
    b = on_screen(YC+w/2+.8, ROOF-.6, .2)
    beam(v, f, a+Vector((.9, 0, 0)), b+Vector((.9, 0, 0)), 2.2, .3, (1, 0, 0))
    solid(kit, 'Bridge windscreen visor', v, f, kit.pale)

    for side, s in cl_hull.SIDES:
        # Wing ports on the cab's side shoulder (45 deg), just aft of the windscreen.
        sn = Vector((0, s, 1)).normalized()
        zc2 = SHOULDER+2.0
        yc = cl_hull.y(HALF-(zc2-SHOULDER), s)
        c = Vector((-58.5, yc, zc2))
        g = kit.box(f'Bridge {side} wing ports', tuple(c+sn*.08), (8.5, .3, 1.7), kit.glass, bevel=0)
        g.rotation_euler = (s*math.pi/4, 0, 0)
        v, f = [], []
        side_dir = Vector((0, -s, 1)).normalized()            # up the shoulder
        for k in range(4):
            x = -62.75+k*8.5/3
            beam(v, f, c+sn*.25+side_dir*(-1.15)+Vector((x+58.5, 0, 0)),
                 c+sn*.25+side_dir*1.15+Vector((x+58.5, 0, 0)), .4, .35, sn)
        for d in (-1.0, 1.0):
            beam(v, f, c+sn*.25+side_dir*d+Vector((-4.6, 0, 0)), c+sn*.25+side_dir*d+Vector((4.6, 0, 0)), .45, .35, sn)
        solid(kit, f'Bridge {side} wing port frame', v, f, kit.steel)
        # The sprite's row of three dark ports near the aft edge, on the aft shoulder (45 deg, facing aft).
        an = Vector((-1, 0, 1)).normalized()
        u = Vector((1, 0, 1)).normalized()                    # up the aft shoulder
        v, f, fv, ff = [], [], [], []
        for rel in (10.5, 12.6, 14.7):
            p = Vector((X0+2.0, cl_hull.y(rel, s), SHOULDER+2.0))
            beam(v, f, p+an*.12-u*.9, p+an*.12+u*.9, .25, .8, an)
            beam(fv, ff, p+an*.04-u*1.25, p+an*.04+u*1.25, .2, 1.3, an)
        solid(kit, f'Bridge {side} aft ports', v, f, dark_glass)
        solid(kit, f'Bridge {side} aft port frames', fv, ff, kit.steel)
        # Running lamps along the red frame's flank, where the sprite has pale-yellow dots.
        for k, x in enumerate((-62.5, -66.0, -69.5)):
            yy = cl_hull.y(27.0, s)
            lamp(kit, f'Bridge {side} frame lamp {k+1}', (x, yy, seat(kit, x, yy, DECK)+.2), .4)

    # Red coaming frame round the deckhouse base, open at the aft door.
    outer = prism(kit, 'Bridge red frame', inset(cl_hull.plan(half), -1.9), [(0, DECK-.4), (0, DECK+1.6)], kit.red, bevel=.1)
    core = prism(kit, 'Temporary frame core', inset(cl_hull.plan(half), -.05), [(0, DECK-2), (0, DECK+4)], kit.red)
    boolean(outer, core)
    v, f = [], []
    block(v, f, (X0-4, YC-4.2, DECK-3), (X0+1, YC+4.2, DECK+4))
    boolean(outer, solid(kit, 'Temporary door gap', v, f, kit.red))
    v, f = [], []
    for s in (1, -1):                                        # the frame's stern corner posts
        a, b = sorted((cl_hull.y(20.5, s), cl_hull.y(24.5, s)))
        block(v, f, (-80, a, DECK-.4), (-76, b, DECK+2.4))
    solid(kit, 'Bridge red frame stern posts', v, f, kit.red, bevel=.1)

    # Roof: the D-shaped sensor ring with a tilted dish inside it.
    rx, rz = -60.5, ROOF
    revolve_z(kit, 'Bridge roof sensor ring', [(4.0, -.3), (5.0, -.3), (5.0, 1.0), (4.0, 1.0)], kit.steel).location = (rx, YC, rz)
    kit.cylinder('Bridge roof dish pedestal', (rx, YC, rz+1.0), .8, 2.2, kit.steel, vertices=10)
    dish = revolve_z(kit, 'Bridge roof dish', [(.4, 0), (3.2, 1.0), (3.2, 1.3), (.4, .35)], kit.pale)
    dish.location = (rx, YC, rz+2.0)
    dish.rotation_euler = (0, math.radians(30), 0)           # looks forward and up
    # Short mast aft of the ring: base, pole, a yard with two whip antennae, lamps at head and yard ends.
    mx = -65.8
    mz = seat(kit, mx, YC, ROOF)
    kit.box('Bridge mast base', (mx, YC, mz+.8), (2.6, 2.6, 1.6), kit.steel, bevel=.2)
    kit.cylinder('Bridge mast', (mx, YC, mz+1.6+3.5), .35, 7, kit.steel, vertices=8)
    v, f = [], []
    beam(v, f, (mx, YC-3.2, mz+6.4), (mx, YC+3.2, mz+6.4), .3)
    for s in (1, -1):
        beam(v, f, (mx, YC+s*3, mz+6.4), (mx, YC+s*3, mz+10.0), .12)
    beam(v, f, (mx, YC, mz+8.6), (mx, YC, mz+11.0), .1)
    solid(kit, 'Bridge mast yard and antennae', v, f, kit.steel)
    lamp(kit, 'Bridge masthead lamp', (mx, YC, mz+8.85), .35)
    for side, s in cl_hull.SIDES:
        lamp(kit, f'Bridge {side} yard lamp', (mx, YC+s*3.0, mz+6.75), .25)

    # Personnel door in the aft face: leaf, frame, landing, step and hand rails at person scale.
    zd = DECK+.6                                             # door sill on the landing
    kit.box('Bridge aft personnel door', (X0-.1, YC, zd+2.3), (.3, 2.2, 4.6), kit.steel, bevel=.05)
    v, f = [], []
    for yy in (-1.35, 1.35):
        block(v, f, (X0-.45, YC+yy-.25, zd-.1), (X0+.1, YC+yy+.25, zd+4.85))
    block(v, f, (X0-.45, YC-1.6, zd+4.6), (X0+.1, YC+1.6, zd+5.1))
    solid(kit, 'Bridge aft personnel door frame', v, f, kit.pale)
    kit.box('Bridge aft door landing', (-74.1, YC, DECK+.3), (6.0, 8.0, .6), kit.red, bevel=.06)
    kit.box('Bridge aft door step', (-77.7, YC, DECK+.15), (1.4, 4.0, .3), kit.steel, bevel=.04)
    v, f = [], []
    for s in (1, -1):
        yy = YC+s*3.8
        for x in (-71.6, -74.1, -76.6):
            beam(v, f, (x, yy, DECK+.6), (x, yy, DECK+2.8), .22)
        beam(v, f, (-71.6, yy, DECK+2.8), (-76.6, yy, DECK+2.8), .2)
        beam(v, f, (-71.6, yy, DECK+1.7), (-76.6, yy, DECK+1.7), .16)
    solid(kit, 'Bridge aft door hand rails', v, f, kit.pale)
