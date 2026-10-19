"""Colossus Mk.III bridge: the modest deckhouse on the aft machinery body.

Forked unchanged in shape from the accepted Colossus (colossus-geometry/cl_bridge.py):
the Mk.III keeps the Colossus's waist, aft body, bridge and drive pods (the sprites agree aft of X -20).

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
sprite draws aft of it between two pale housings (c3_aft). Pale-yellow dots
along the red frame's flanks (Y rel +-27, X -62..-70) are its running lamps.

So the deckhouse is cut down forward to a one-deck annex, and the cab's
forward face is a raked windscreen of framed panes over that roof; wing ports
sit on the cab's side shoulders; a red coaming frame runs round the base; the
roof carries a sensor ring with a dish and a short mast with a yard, whips and
lamps. The door is person-sized at ~0.5 m per unit (4.6 units tall) with a step
and hand rails. 'Bridge deckhouse' is a scale solid and stays closed.

Pass 3 round 5 (owner: "the bridge could use some detailing work"): the shell
is plated rather than smooth -- a strata groove round the walls, vertical
plate seams and roof seams cut skin-deep, louvred wall vents, bolted service
covers and conduit runs up the walls. The glazing sits in recesses: the
windscreen has a proud surround, deep mullions, a sill ledge, wipers, shutter
rails and a rolled shutter box under an armoured brow on gussets; the wing
ports are recessed with surrounds and eyebrows; the annex has bolted round
ports. People-scale fittings (0.5 m per unit: handrails 2.2 units, a 1.8 m
person 3.6): the annex roof is a railed lookout deck with a hatch, skylights
and mushroom vents, opening onto grated bridge wings on knee braces either
side of the cab; ladders climb each side wall and over the shoulder to a
railed roof. The roof carries a 48-segment sensor ring on brackets, a ribbed
dish on an elevation yoke with a feed horn on struts, a navigation scanner, a
sensor dome, comm whips and life-raft canisters in cradles; the frame's
running lamps get guards. No new lights: only the sprite's lamps glow.
"""
import math
import bmesh
import bpy
from mathutils import Vector, Matrix
from c3_kit import prism, section, inset, boolean, material, planar
from c3_aft import beam, block, solid, seat, lamp, pipes
from c3_kit import revolve_x
import c3_hull

X0, X1 = -71.0, -42.0   # aft / forward faces
HALF = 24.0
DECK = c3_hull.AFT_DECK
ROOF = DECK+11.0
ANNEX = DECK+5.5        # roof of the forward annex
RAKE = (-51.5, -54.0)   # windscreen foot / head X: the face leans aft as it rises
SHOULDER = ROOF-4.5     # where the cab's shoulder chamfer starts
YC = c3_hull.YC
STRATA = DECK+4.2       # the horizontal groove round the walls
RAIL = 2.2              # handrail height: 1.1 m at 0.5 m per unit
WPX, WPL = -57.25, 6.0  # wing ports: centre X and length (three panes)
LAD = (-62.25, -63.35)  # side-wall ladder stiles X


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


def _frame(axis):
    """Two unit vectors perpendicular to axis."""
    axis = Vector(axis).normalized()
    s = Vector((0, 0, 1)) if abs(axis.z) < .9 else Vector((1, 0, 0))
    u = (s-axis*s.dot(axis)).normalized()
    return axis, u, axis.cross(u)


def disc(v, f, c, axis, r, depth, n=24):
    """Append a closed n-sided cylinder centred on c along axis."""
    a, u, w = _frame(axis)
    c = Vector(c)
    k = len(v)
    for e in (-depth/2, depth/2):
        for i in range(n):
            t = 2*math.pi*i/n
            v.append(tuple(c+a*e+u*(r*math.cos(t))+w*(r*math.sin(t))))
    f.append(tuple(range(k+n-1, k-1, -1)))
    f.append(tuple(range(k+n, k+2*n)))
    f += [(k+i, k+(i+1) % n, k+n+(i+1) % n, k+n+i) for i in range(n)]


def annulus(v, f, c, axis, r0, r1, depth, n=24):
    """Append a closed ring (washer) centred on c along axis."""
    a, u, w = _frame(axis)
    c = Vector(c)
    k = len(v)
    for e, r in ((-depth/2, r0), (-depth/2, r1), (depth/2, r1), (depth/2, r0)):
        for i in range(n):
            t = 2*math.pi*i/n
            v.append(tuple(c+a*e+u*(r*math.cos(t))+w*(r*math.sin(t))))
    for ring in range(4):
        nr = (ring+1) % 4
        f += [(k+ring*n+i, k+ring*n+(i+1) % n, k+nr*n+(i+1) % n, k+nr*n+i) for i in range(n)]


def _cut(kit, target, name, v, f, mat, cutter=None):
    """Boolean difference whose cut faces keep the cutter's material (a dark reveal)."""
    cutter = cutter or solid(kit, 'Temporary '+name+' cutter', v, f, mat)
    if mat.name not in [m.name for m in target.data.materials]:
        target.data.materials.append(mat)
    bpy.context.view_layer.objects.active = target
    mod = target.modifiers.new('Hull boolean', 'BOOLEAN')
    mod.operation = 'DIFFERENCE'
    mod.object = cutter
    mod.solver = 'MANIFOLD'
    try:
        mod.material_mode = 'TRANSFER'
    except (AttributeError, TypeError):
        pass
    bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
    bpy.ops.object.modifier_apply(modifier=mod.name)
    bpy.data.objects.remove(cutter, do_unlink=True)
    planar(target)


def rail_run(v, f, pts, z0, height=RAIL, pitch=2.8, post=.22, top=.2):
    """Stanchions along a polyline [(x, y), ...] standing on z0 (or per-point z), with top and knee rails."""
    pts = [p if len(p) == 3 else (p[0], p[1], z0) for p in pts]
    for (ax, ay, az), (bx, by, bz) in zip(pts, pts[1:]):
        a, b = Vector((ax, ay, az)), Vector((bx, by, bz))
        n = max(1, math.ceil((b-a).length/pitch))
        for i in range(n+1):
            p = a+(b-a)*(i/n)
            beam(v, f, p, p+Vector((0, 0, height)), post)
        beam(v, f, a+Vector((0, 0, height)), b+Vector((0, 0, height)), top)
        beam(v, f, a+Vector((0, 0, height/2)), b+Vector((0, 0, height/2)), top*.8)


def wall_vent(kit, name, c, nrm, hx, width, height, slats=5):
    """Louvred grille on a vertical wall: dark backing, angled slats, a frame."""
    c, nrm, hx = Vector(c), Vector(nrm).normalized(), Vector(hx).normalized()
    up = Vector((0, 0, 1))
    v, f = [], []
    beam(v, f, c-hx*width/2+nrm*.1, c+hx*width/2+nrm*.1, .3, height, nrm)
    solid(kit, name+' backing', v, f, kit.dark)
    v, f = [], []
    for i in range(slats):
        z = -height/2+.3+(height-.6)*i/(slats-1)
        p = c+up*z+nrm*.4
        beam(v, f, p-hx*(width/2-.15), p+hx*(width/2-.15), .5, .14, nrm+up*.6)
    for e in (-1, 1):
        beam(v, f, c+hx*e*(width/2+.15)-up*(height/2+.3)+nrm*.3, c+hx*e*(width/2+.15)+up*(height/2+.3)+nrm*.3, .4, .3, nrm)
        beam(v, f, c+up*e*(height/2+.15)-hx*(width/2+.3)+nrm*.3, c+up*e*(height/2+.15)+hx*(width/2+.3)+nrm*.3, .4, .3, nrm)
    solid(kit, name+' louvres', v, f, kit.steel, bevel=.03)


def wall_cover(kit, name, c, nrm, hx, width, height, mat):
    """Bolted service cover on a vertical wall, with a grab handle."""
    c, nrm, hx = Vector(c), Vector(nrm).normalized(), Vector(hx).normalized()
    up = Vector((0, 0, 1))
    v, f = [], []
    beam(v, f, c-hx*width/2+nrm*.15, c+hx*width/2+nrm*.15, .3, height, nrm)
    solid(kit, name, v, f, mat, bevel=.06)
    v, f = [], []
    nx, nz = max(2, round(width/.9)), max(2, round(height/.9))
    pts = set()
    for i in range(nx+1):
        for e in (-1, 1):
            pts.add((-width/2+.3+(width-.6)*i/nx, e*(height/2-.3)))
    for j in range(nz+1):
        for e in (-1, 1):
            pts.add((e*(width/2-.3), -height/2+.3+(height-.6)*j/nz))
    for a, b in pts:
        p = c+hx*a+up*b+nrm*.3
        beam(v, f, p, p+nrm*.16, .26)
    for e in (-1, 1):
        beam(v, f, c+hx*e*.5+nrm*.3, c+hx*e*.5+nrm*.75, .16)
    beam(v, f, c-hx*.6+nrm*.75, c+hx*.6+nrm*.75, .16)
    solid(kit, name+' bolts', v, f, kit.steel)


def build(kit):
    half = [(X1, HALF-8), (X1-4, HALF-1.5), (X1-10, HALF), (X0+6, HALF), (X0, HALF-6)]
    house = prism(kit, 'Bridge deckhouse', c3_hull.plan(half), section(DECK-1, ROOF, .5, 4.5), kit.pale)
    # Step the forward third down to a one-deck annex; the cut face is the raked windscreen wall.
    zt = ROOF+20
    xs = [(-38, ANNEX), (RAKE[0], ANNEX), (_screen_x(zt), zt), (-38, zt)]
    verts = [(x, yy, z) for yy in (-40, 40) for x, z in xs]
    cutter = kit.mesh('Temporary annex cutter', verts, [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2),
                                                        (2, 6, 7, 3), (3, 7, 4, 0)], kit.pale)
    boolean(house, cutter)
    kit.hull.append(house)

    groove = material('Bridge seam groove shadow', (.05, .045, .045), .4, .8)
    dark_glass = material('Bridge dark port glass', (.03, .04, .05), .3, .2)
    tilt = math.atan2(RAKE[0]-RAKE[1], ROOF-ANNEX)          # lean of the windscreen from vertical
    n = Vector((math.cos(tilt), 0, math.sin(tilt)))          # forward-up face normal
    up = Vector((-math.sin(tilt), 0, math.cos(tilt)))        # up the face

    def on_screen(yy, z, off):
        return Vector((_screen_x(z), yy, z))+n*off

    # --- plating: skin-deep grooves cut into the shell ---
    # The strata groove: a slab round the walls minus the shell offset inward, so only a skin band is cut.
    v, f = [], []
    block(v, f, (-80, YC-40, STRATA-.3), (-30, YC+40, STRATA+.3))
    shell = solid(kit, 'Temporary strata slab', v, f, groove)
    core = prism(kit, 'Temporary strata core', inset(c3_hull.plan(half), .35), section(DECK-2, ROOF+2, .5, 4.5), groove, bevel=0)
    boolean(shell, core)
    _cut(kit, house, 'strata', None, None, groove, cutter=shell)
    # Vertical plate seams on the walls, transverse seams on the roof.
    v, f = [], []
    for s in (1, -1):
        a, b = sorted((c3_hull.y(23.65, s), c3_hull.y(26, s)))
        block(v, f, (-58.3, a, DECK-.5), (-57.7, b, SHOULDER-.1))               # cab side walls
        a, b = sorted((c3_hull.y(8.3, s), c3_hull.y(7.7, s)))
        block(v, f, (X1-.35, a, DECK-.5), (X1+2, b, ANNEX-.2))                   # annex front face
        a, b = sorted((c3_hull.y(9.2, s), c3_hull.y(9.8, s)))
        block(v, f, (X0-2, a, DECK-.5), (X0+.35, b, SHOULDER-.1))                # aft face
    for xs in (-56.3, -63.9):
        block(v, f, (xs-.3, YC-18.5, ROOF-.35), (xs+.3, YC+18.5, ROOF+2))           # roof
    _cut(kit, house, 'plate seam', v, f, groove)

    # --- recesses for the glazing ---
    zb, zh = ANNEX+1.0, ROOF-1.6
    zc = (zb+zh)/2
    span = (zh-zb)/math.cos(tilt)
    w = 20.0
    W2 = w/2+.7                                              # recess half-width
    v, f = [], []
    beam(v, f, on_screen(YC-W2, zc, 0), on_screen(YC+W2, zc, 0), 1.3, span+1.2, n)
    wings = {}
    for side, s in c3_hull.SIDES:
        sn = Vector((0, s, 1)).normalized()
        side_dir = Vector((0, -s, 1)).normalized()            # up the shoulder
        zc2 = SHOULDER+2.0
        c = Vector((WPX, c3_hull.y(HALF-(zc2-SHOULDER), s), zc2))
        wings[side] = (s, sn, side_dir, c)
        beam(v, f, c-Vector((WPL/2+.35, 0, 0)), c+Vector((WPL/2+.35, 0, 0)), 1.0, 2.3, sn)
    ports = []                                               # annex round ports: (centre, outward normal)
    for rel in (4.5, 11.0):
        for s in (1, -1):
            ports.append((Vector((X1, c3_hull.y(rel, s), DECK+2.8)), Vector((1, 0, 0))))
    for s in (1, -1):
        nn = Vector((6.5, s*4.0, 0)).normalized()             # outward normal of the annex corner face
        ports.append((Vector((X1-2, c3_hull.y(HALF-4.75, s), DECK+2.8)), nn))
    for p, nn in ports:
        disc(v, f, p, nn, .8, .9)
    _cut(kit, house, 'glazing recess', v, f, groove)

    # Annex roof: red plating (the sprite's pink-red forward block) with two dark seams at Y rel +-11.
    ann = [(X1-.5, HALF-8.4), (X1-4.2, HALF-2.1), (RAKE[0]-.4, HALF-.8)]
    prism(kit, 'Bridge annex roof plating', inset(c3_hull.plan(ann), .3), [(0, ANNEX-.2), (0, ANNEX+.3)], kit.red, bevel=.05)
    AR = ANNEX+.3                                            # annex roof walking surface
    v, f = [], []
    for s in (1, -1):
        yy = c3_hull.y(11.5, s)
        block(v, f, (-51.2, yy-.3, ANNEX+.25), (-43.5, yy+.3, ANNEX+.45))
    solid(kit, 'Bridge annex roof seams', v, f, kit.dark)

    # --- windscreen: five lit panes set into the recess ---
    g = kit.box('Bridge windscreen', tuple(on_screen(YC, zc, -.45)), (.3, w, span), kit.glass, bevel=0)
    g.rotation_euler = (0, -tilt, 0)
    v, f = [], []
    for k in range(6):                                       # mullions, deep in the recess
        yy = YC-w/2+w*k/5
        beam(v, f, on_screen(yy, zb-.3, -.2), on_screen(yy, zh+.3, -.2), .8, .45, n)
    for z in (zb-.25, zh+.25):                               # sill and head rails
        beam(v, f, on_screen(YC-w/2-.3, z, -.2), on_screen(YC+w/2+.3, z, -.2), .8, .5, n)
    for e in (-1, 1):                                        # recess end posts
        beam(v, f, on_screen(YC+e*(w/2+.4), zb-.6, -.2), on_screen(YC+e*(w/2+.4), zh+.6, -.2), .8, .6, n)
    solid(kit, 'Bridge windscreen frame', v, f, kit.steel)
    v, f = [], []                                            # proud surround round the recess
    for e in (-1, 1):
        beam(v, f, on_screen(YC+e*(W2+.35), zb-1.05, .12), on_screen(YC+e*(W2+.35), zh+1.05, .12), .4, .75, n)
    beam(v, f, on_screen(YC-W2-.7, zh+.85, .12), on_screen(YC+W2+.7, zh+.85, .12), .4, .55, n)
    solid(kit, 'Bridge windscreen surround', v, f, kit.pale, bevel=.05)
    v, f = [], []                                            # sill ledge over the annex roof, on brackets
    a, b = on_screen(YC-W2-.7, zb-.95, .45), on_screen(YC+W2+.7, zb-.95, .45)
    beam(v, f, a, b, 1.0, .3, n)
    for k in range(7):
        yy = YC-W2+2*W2*k/6
        beam(v, f, on_screen(yy, zb-1.25, .15), on_screen(yy, zb-1.25, .75), .25, .5)
    solid(kit, 'Bridge windscreen sill', v, f, kit.steel, bevel=.03)
    v, f = [], []                                            # one parked wiper per pane
    for k in range(5):
        yy = YC-w/2+w*(k+.5)/5
        p = on_screen(yy-1.2, zb+.15, -.22)
        q = on_screen(yy+1.3, zb+1.0, -.22)
        beam(v, f, p, q, .1, .14, n)
        beam(v, f, p, p-n*.2, .3)
    solid(kit, 'Bridge windscreen wipers', v, f, kit.dark)
    v, f = [], []                                            # storm-shutter guide rails either end
    for e in (-1, 1):
        yy = YC+e*(W2+1.15)
        beam(v, f, on_screen(yy, zb-.8, .3), on_screen(yy, zh+.7, .3), .6, .4, n)
        beam(v, f, on_screen(yy, zb-.8, .62), on_screen(yy, zh+.7, .62), .12, .9, n)
    solid(kit, 'Bridge windscreen shutter rails', v, f, kit.steel)
    sb = Vector((_screen_x(ROOF-1.0)+.8, YC, ROOF-.85))
    v, f = [], []
    disc(v, f, sb, (0, 1, 0), .42, 2*W2+2.8, n=24)
    for e in (-1, 1):
        disc(v, f, sb+Vector((0, e*(W2+1.5), 0)), (0, 1, 0), .55, .3, n=24)
    solid(kit, 'Bridge windscreen shutter box', v, f, kit.steel)
    # Armoured brow over the panes: a thick plate carried forward off the roof edge, on gussets.
    v, f = [], []
    a = Vector((_screen_x(ROOF)+1.0, YC-W2-1.8, ROOF-.15))
    b = Vector((_screen_x(ROOF)+1.0, YC+W2+1.8, ROOF-.15))
    beam(v, f, a, b, 3.2, .7, Vector((1, 0, -.12)))
    beam(v, f, a+Vector((1.55, 0, -.45)), b+Vector((1.55, 0, -.45)), .25, .6, Vector((1, 0, 0)))   # drip lip
    solid(kit, 'Bridge windscreen visor', v, f, kit.pale, bevel=.06)
    v, f = [], []
    for e in (-1, 1):
        for yy in (YC+e*(W2+.35), YC+e*(W2+1.75)):
            beam(v, f, on_screen(yy, ROOF-2.6, 0), Vector((_screen_x(ROOF)+2.2, yy, ROOF-.5)), .3, .5, (0, 1, 0))
    solid(kit, 'Bridge windscreen visor gussets', v, f, kit.steel, bevel=.03)

    # --- annex: bolted round ports in the forward and corner faces ---
    v, f, rv, rf, bv, bf = [], [], [], [], [], []
    for p, nn in ports:
        disc(v, f, p-nn*.3, nn, .82, .15)
        annulus(rv, rf, p+nn*.1, nn, .78, 1.15, .4)
        _, u, ww = _frame(nn)
        for i in range(8):
            t = 2*math.pi*(i+.5)/8
            q = p+nn*.3+u*(.97*math.cos(t))+ww*(.97*math.sin(t))
            beam(bv, bf, q, q+nn*.14, .16)
        beam(bv, bf, p+nn*.15+u*1.4-ww*.4, p+nn*.15+u*1.4+ww*.4, .3, .3)        # dead-light hinge lug
    solid(kit, 'Bridge annex port glass', v, f, dark_glass)
    solid(kit, 'Bridge annex port rims', rv, rf, kit.steel)
    solid(kit, 'Bridge annex port bolts', bv, bf, kit.steel)

    # --- annex roof: a railed lookout deck with a hatch, skylights and mushroom vents ---
    v, f = [], []
    block(v, f, (-48.9, YC-1.9, AR-.1), (-45.1, YC+1.9, AR+.7))              # hatch coaming
    solid(kit, 'Bridge annex roof hatch coaming', v, f, kit.steel, bevel=.06)
    v, f = [], []
    block(v, f, (-48.7, YC-1.7, AR+.7), (-45.3, YC+1.7, AR+1.0))
    solid(kit, 'Bridge annex roof hatch lid', v, f, kit.pale, bevel=.06)
    v, f = [], []
    disc(v, f, (-48.75, YC, AR+.85), (0, 1, 0), .22, 3.0, n=12)              # hinge pin
    for yy in (-1.0, 1.0):
        block(v, f, (-45.4, YC+yy-.15, AR+.55), (-44.8, YC+yy+.15, AR+1.15))   # dogs
    block(v, f, (-46.2, YC-.7, AR+1.0), (-45.9, YC+.7, AR+1.3))               # handle
    solid(kit, 'Bridge annex roof hatch fittings', v, f, kit.steel)
    v, f, gv, gf = [], [], [], []
    for s in (1, -1):
        y0 = c3_hull.y(6.5, s)
        block(v, f, (-47.8, y0-1.3, AR-.1), (-44.6, y0+1.3, AR+.65))         # skylight frames
        block(gv, gf, (-47.4, y0-.95, AR+.6), (-45.0, y0+.95, AR+.72))
        for xx in (-46.6, -45.8):
            block(v, f, (xx-.08, y0-1.1, AR+.72), (xx+.08, y0+1.1, AR+.86))     # guard bars
    solid(kit, 'Bridge annex roof skylights', v, f, kit.steel, bevel=.03)
    solid(kit, 'Bridge annex roof skylight glass', gv, gf, dark_glass)
    v, f = [], []
    for s in (1, -1):
        for xx in (-44.4, -49.4):
            c = Vector((xx, c3_hull.y(13.6, s), AR))
            disc(v, f, c+Vector((0, 0, .6)), (0, 0, 1), .35, 1.4, n=24)          # stalk
            disc(v, f, c+Vector((0, 0, 1.45)), (0, 0, 1), .85, .35, n=24)        # cap
            disc(v, f, c+Vector((0, 0, 1.15)), (0, 0, 1), .55, .3, n=24)         # dark gap under the cap
    solid(kit, 'Bridge annex roof mushroom vents', v, f, kit.steel, bevel=.04)
    edge = [p for p in inset(c3_hull.plan([(X1, HALF-8), (X1-4, HALF-1.5), (RAKE[0]-.1, HALF)]), .8)]
    v, f = [], []
    port = sorted([p for p in edge if p[1] > YC and p[0] > -50], key=lambda p: -p[0])
    stbd = sorted([p for p in edge if p[1] < YC and p[0] > -50], key=lambda p: -p[0])
    tail = lambda s: (RAKE[0]+.4, c3_hull.y(HALF-1.2, s))
    rail_run(v, f, [tail(1)]+list(reversed(port))+stbd+[tail(-1)], AR)
    solid(kit, 'Bridge annex roof handrails', v, f, kit.pale)

    for side, (s, sn, side_dir, c) in wings.items():
        # Wing ports on the cab's side shoulder (45 deg), set into their recess.
        g = kit.box(f'Bridge {side} wing ports', tuple(c-sn*.32), (WPL, .3, 1.7), kit.glass, bevel=0)
        g.rotation_euler = (s*math.pi/4, 0, 0)
        v, f = [], []
        for k in range(4):
            dx = -WPL/2+k*WPL/3
            beam(v, f, c-sn*.12+side_dir*(-1.15)+Vector((dx, 0, 0)),
                 c-sn*.12+side_dir*1.15+Vector((dx, 0, 0)), .65, .35, sn)
        for d in (-1.0, 1.0):
            beam(v, f, c-sn*.12+side_dir*d-Vector((WPL/2+.35, 0, 0)), c-sn*.12+side_dir*d+Vector((WPL/2+.35, 0, 0)), .65, .35, sn)
        solid(kit, f'Bridge {side} wing port frame', v, f, kit.steel)
        v, f = [], []
        for d in (-1.0, 1.0):
            beam(v, f, c+sn*.1+side_dir*d*1.5+Vector((-WPL/2-.75, 0, 0)), c+sn*.1+side_dir*d*1.5+Vector((WPL/2+.75, 0, 0)), .35, .65, sn)
            beam(v, f, c+sn*.1+Vector((d*(WPL/2+.7), 0, 0))-side_dir*1.8, c+sn*.1+Vector((d*(WPL/2+.7), 0, 0))+side_dir*1.8, .35, .55, sn)
        solid(kit, f'Bridge {side} wing port surround', v, f, kit.pale, bevel=.04)
        v, f = [], []
        e0 = c+side_dir*2.0+sn*.5
        beam(v, f, e0-Vector((WPL/2+.95, 0, 0)), e0+Vector((WPL/2+.95, 0, 0)), 1.0, .18, sn)
        solid(kit, f'Bridge {side} wing port eyebrow', v, f, kit.steel, bevel=.03)

        # The sprite's row of three dark ports near the aft edge, on the aft shoulder (45 deg, facing aft).
        an = Vector((-1, 0, 1)).normalized()
        u = Vector((1, 0, 1)).normalized()                    # up the aft shoulder
        v, f, fv, ff = [], [], [], []
        for rel in (10.5, 12.6, 14.7):
            p = Vector((X0+2.0, c3_hull.y(rel, s), SHOULDER+2.0))
            beam(v, f, p+an*.12-u*.9, p+an*.12+u*.9, .25, .8, an)
            beam(fv, ff, p+an*.04-u*1.25, p+an*.04+u*1.25, .2, 1.3, an)
        beam(fv, ff, Vector((X0+2.0, c3_hull.y(12.6, s), SHOULDER+2.0))+an*.35+u*1.55 - Vector((0, s*3.4, 0)),
             Vector((X0+2.0, c3_hull.y(12.6, s), SHOULDER+2.0))+an*.35+u*1.55 + Vector((0, s*3.4, 0)), .7, .15, an)
        solid(kit, f'Bridge {side} aft ports', v, f, dark_glass)
        solid(kit, f'Bridge {side} aft port frames', fv, ff, kit.steel)
        # Running lamps along the red frame's flank, where the sprite has pale-yellow dots, in guards.
        v, f = [], []
        for k, x in enumerate((-62.5, -66.0, -69.5)):
            yy = c3_hull.y(27.0, s)
            z = seat(kit, x, yy, DECK)
            lamp(kit, f'Bridge {side} frame lamp {k+1}', (x, yy, z+.2), .4)
            for dx in (-.65, .65):
                beam(v, f, (x+dx, yy, z-.1), (x+dx, yy, z+1.0), .14)
            beam(v, f, (x-.75, yy, z+1.0), (x+.75, yy, z+1.0), .14)
            beam(v, f, (x, yy-.75*s, z+1.0), (x, yy+.75*s, z+1.0), .12)
        solid(kit, f'Bridge {side} frame lamp guards', v, f, kit.steel)

        # Side wall: louvred vent under the wing, conduits, a ladder and a bolted service cover.
        wy = c3_hull.y(HALF, s)
        nrm = Vector((0, s, 0))
        wall_vent(kit, f'Bridge {side} wall vent', (-54.8, wy, DECK+2.85), nrm, (1, 0, 0), 2.6, 1.5)
        wall_cover(kit, f'Bridge {side} wall service cover', (-59.55, wy, DECK+3.9), nrm, (1, 0, 0), 1.8, 3.6, kit.pale)
        runs = []
        for x in (-60.95, -61.5):
            r_wall, r_roof = HALF+.3, HALF-4.5-.15
            runs += [((x, c3_hull.y(r_wall, s), DECK+1.4), (x, c3_hull.y(r_wall, s), SHOULDER+.45)),
                     ((x, c3_hull.y(r_wall, s), SHOULDER+.3), (x, c3_hull.y(r_roof, s), ROOF+.3)),
                     ((x, c3_hull.y(r_roof+.15, s), ROOF+.3), (x, c3_hull.y(17.2, s), ROOF+.3))]
        pipes(kit, f'Bridge {side} conduits', runs, .2, kit.steel)
        v, f = [], []
        for z in (DECK+2.2, DECK+4.6):
            a, b = sorted((c3_hull.y(HALF-.05, s), c3_hull.y(HALF+.6, s)))
            block(v, f, (-61.85, a, z-.2), (-60.6, b, z+.2))
        a, b = sorted((c3_hull.y(16.6, s), c3_hull.y(17.8, s)))
        block(v, f, (-62.0, a, ROOF-.1), (-60.45, b, ROOF+.9))                       # roof gland box
        solid(kit, f'Bridge {side} conduit clamps', v, f, kit.steel, bevel=.04)
        # Ladder: up the side wall from the frame, then over the shoulder, with grab hoops onto the roof.
        v, f = [], []
        ro, zw = HALF+.55, SHOULDER+.25
        z0 = DECK+1.6
        top = (HALF-4.5+.39, ROOF+.39)
        for x in LAD:
            pts = [(ro, z0), (ro, zw), top, (top[0]+.1, ROOF+2.4), (top[0]-1.6, ROOF+2.4), (top[0]-1.6, ROOF)]
            for (ra, za), (rb, zb_) in zip(pts, pts[1:]):
                beam(v, f, (x, c3_hull.y(ra, s), za), (x, c3_hull.y(rb, s), zb_), .2, .2, (1, 0, 0))
        k = 0
        z = z0+.5
        while z < zw-.2:
            beam(v, f, (LAD[0]+.1, c3_hull.y(ro, s), z), (LAD[1]-.1, c3_hull.y(ro, s), z), .14)
            z += .6
        L = math.hypot(ro-top[0], top[1]-zw)
        for i in range(1, int(L/.6)):
            t = i*.6/L
            r = ro+(top[0]-ro)*t
            z = zw+(top[1]-zw)*t
            beam(v, f, (LAD[0]+.1, c3_hull.y(r, s), z), (LAD[1]-.1, c3_hull.y(r, s), z), .14)
        for z in (DECK+2.6, DECK+5.4):                                               # wall stand-offs
            for x in LAD:
                beam(v, f, (x, c3_hull.y(HALF-.1, s), z), (x, c3_hull.y(ro, s), z), .14)
        solid(kit, f'Bridge {side} roof ladder', v, f, kit.pale)

        # Bridge wing: a grated lookout platform on knee braces off the cab side, at annex-roof level.
        v, f, gv, gf = [], [], [], []
        a, b = sorted((c3_hull.y(HALF-.2, s), c3_hull.y(HALF+3.9, s)))
        block(v, f, (-57.6, a, AR-.35), (-51.7, b, AR-.05))                           # deck frame
        for i in range(10):
            xx = -57.3+i*.62
            block(gv, gf, (xx-.1, a+.25, AR-.05), (xx+.1, b-.25, AR+.02))           # grating bars
        for xx in (-52.4, -57.0):
            beam(v, f, (xx, c3_hull.y(HALF+3.4, s), AR-.3), (xx, c3_hull.y(HALF+.1, s), DECK+2.4), .3, .3, (1, 0, 0))
        solid(kit, f'Bridge {side} wing platform', v, f, kit.steel, bevel=.03)
        solid(kit, f'Bridge {side} wing platform grating', gv, gf, kit.dark)
        v, f = [], []
        rail_run(v, f, [(-51.9, c3_hull.y(HALF+1.3, s)), (-51.9, c3_hull.y(HALF+3.6, s)),
                        (-57.35, c3_hull.y(HALF+3.6, s)), (-57.35, c3_hull.y(HALF+.35, s))], AR)
        solid(kit, f'Bridge {side} wing platform handrails', v, f, kit.pale)

        # Aft face: a louvred vent outboard of the stern housings.
        wall_vent(kit, f'Bridge {side} aft wall vent', (X0, c3_hull.y(14.6, s), DECK+2.75), (-1, 0, 0), (0, 1, 0), 2.6, 1.5)

        # Roof: railed edge with a gap at the ladder head, comm whips at the corners.
        v, f = [], []
        ry = HALF-4.5-.8
        rail_run(v, f, [(-55.6, c3_hull.y(ry, s)), (-60.3, c3_hull.y(ry, s))], ROOF)
        rail_run(v, f, [(-63.7, c3_hull.y(17.8, s)), (-65.7, c3_hull.y(15.8, s)),
                        (-65.7, c3_hull.y(4.6, s))], ROOF)
        solid(kit, f'Bridge {side} roof handrails', v, f, kit.pale)
        v, f, iv, iff = [], [], [], []
        for x, rel in ((-55.4, 16.6), (-64.3, 13.8)):
            yy = c3_hull.y(rel, s)
            disc(v, f, (x, yy, ROOF+.3), (0, 0, 1), .5, .6, n=16)
            disc(iv, iff, (x, yy, ROOF+1.0), (0, 0, 1), .28, .8, n=16)
            beam(v, f, (x, yy, ROOF+1.3), (x, yy, ROOF+9.5 if x > -60 else ROOF+7.0), .1)
        solid(kit, f'Bridge {side} comm whips', v, f, kit.steel)
        solid(kit, f'Bridge {side} comm whip insulators', iv, iff, kit.pale)
        # Life-raft canisters in cradles on the aft roof.
        for k, rel in enumerate((8.6, 10.8)):
            yy = c3_hull.y(rel, s)
            prof = [(-65.1, .01), (-65.1, .3), (-64.9, .58), (-64.55, .72), (-62.55, .72), (-62.2, .58), (-62.0, .3), (-62.0, .01)]
            revolve_x(kit, f'Bridge {side} life-raft canister {k+1}', prof, yy, ROOF+1.1, kit.orange, n=24)
        v, f = [], []
        a, b = sorted((c3_hull.y(7.4, s), c3_hull.y(12.0, s)))
        for x in (-62.9, -64.3):
            block(v, f, (x-.3, a, ROOF-.1), (x+.3, b, ROOF+.55))                    # saddles
            for rel in (8.6, 10.8):
                annulus(v, f, (x, c3_hull.y(rel, s), ROOF+1.1), (1, 0, 0), .7, .82, .22, n=24)   # straps
        solid(kit, f'Bridge {side} life-raft cradles', v, f, kit.steel, bevel=.03)

    # Red coaming frame round the deckhouse base, open at the aft door.
    outer = prism(kit, 'Bridge red frame', inset(c3_hull.plan(half), -1.9), [(0, DECK-.4), (0, DECK+1.6)], kit.red, bevel=.1)
    core = prism(kit, 'Temporary frame core', inset(c3_hull.plan(half), -.05), [(0, DECK-2), (0, DECK+4)], kit.red)
    boolean(outer, core)
    v, f = [], []
    block(v, f, (X0-4, YC-4.2, DECK-3), (X0+1, YC+4.2, DECK+4))
    boolean(outer, solid(kit, 'Temporary door gap', v, f, kit.red))
    v, f = [], []
    for s in (1, -1):                                        # the frame's stern corner posts
        a, b = sorted((c3_hull.y(20.5, s), c3_hull.y(24.5, s)))
        block(v, f, (-80, a, DECK-.4), (-76, b, DECK+2.4))
    solid(kit, 'Bridge red frame stern posts', v, f, kit.red, bevel=.1)

    # --- roof: the D-shaped sensor ring with a ribbed dish on an elevation yoke inside it ---
    rx, rz = -60.5, ROOF
    revolve_z(kit, 'Bridge roof sensor ring', [(3.9, -.3), (5.1, -.3), (5.1, .55), (4.85, .95), (4.15, .95), (3.9, .7)],
              kit.steel, n=48).location = (rx, YC, rz)
    v, f = [], []
    for i in range(12):
        t = 2*math.pi*(i+.5)/12
        d = Vector((math.cos(t), math.sin(t), 0))
        p = Vector((rx, YC, rz))
        beam(v, f, p+d*5.0+Vector((0, 0, .25)), p+d*5.75+Vector((0, 0, .05)), .35, .6, (0, 0, 1))
    solid(kit, 'Bridge roof sensor ring brackets', v, f, kit.steel, bevel=.03)
    kit.cylinder('Bridge roof dish pedestal', (rx, YC, rz+1.0), .9, 2.0, kit.steel, vertices=24)
    v, f = [], []
    disc(v, f, (rx, YC, rz+2.15), (0, 0, 1), 1.5, .4, n=24)                     # azimuth turntable
    H = Vector((rx, YC, rz+3.4))                                               # elevation trunnion
    for e in (-1, 1):
        beam(v, f, (rx, YC+e*1.15, rz+2.3), H+Vector((0, e*1.15, 0)), .45, .9, (0, 1, 0))
        disc(v, f, H+Vector((0, e*1.15, 0)), (0, 1, 0), .5, .5, n=16)
    disc(v, f, H, (0, 1, 0), .22, 2.9, n=12)
    solid(kit, 'Bridge roof dish yoke', v, f, kit.steel, bevel=.03)
    tilt_d = math.radians(30)
    a_ax = Vector((math.sin(tilt_d), 0, math.cos(tilt_d)))
    D = H+a_ax*.9
    foc = 2.2
    zf = lambda r: r*r/(4*foc)
    rs = [.35, .7, 1.1, 1.5, 1.9, 2.3, 2.7, 3.0, 3.2]
    prof = [(r, zf(r)) for r in rs]+[(3.4, zf(3.2)+.06), (3.4, zf(3.2)-.14)]+[(r, zf(r)-.18) for r in reversed(rs)]
    dish = revolve_z(kit, 'Bridge roof dish', prof, kit.pale, n=48)
    dish.location = D
    dish.rotation_euler = (0, tilt_d, 0)                       # looks forward and up
    v, f = [], []
    for i in range(12):                                         # radial ribs on the back
        t = 2*math.pi*i/12
        d = Vector((math.cos(t), math.sin(t), 0))
        sd = Vector((-math.sin(t), math.cos(t), 0))
        rr = [.45, 1.4, 2.3, 3.15]
        for r0, r1 in zip(rr, rr[1:]):
            p0 = d*r0+Vector((0, 0, zf(r0)-.38))
            p1 = d*r1+Vector((0, 0, zf(r1)-.38))
            beam(v, f, p0, p1, .16, .42, sd)
    for i in range(24):                                         # a stiffening hoop
        t0, t1 = 2*math.pi*i/24, 2*math.pi*(i+1)/24
        p0 = Vector((2.0*math.cos(t0), 2.0*math.sin(t0), zf(2.0)-.36))
        p1 = Vector((2.0*math.cos(t1), 2.0*math.sin(t1), zf(2.0)-.36))
        beam(v, f, p0, p1, .32, .16, (0, 0, 1))
    disc(v, f, (0, 0, -.4), (0, 0, 1), .55, 1.1, n=24)          # hub to the trunnion
    for i in range(3):                                          # feed struts to the focus
        t = 2*math.pi*i/3+math.pi/2
        beam(v, f, (3.05*math.cos(t), 3.05*math.sin(t), zf(3.05)+.02), (0, 0, foc-.35), .12)
    disc(v, f, (0, 0, foc-.2), (0, 0, 1), .22, .7, n=16)        # waveguide
    annulus(v, f, (0, 0, foc-.55), (0, 0, 1), .2, .45, .3, n=16)  # feed horn flare
    ribs = solid(kit, 'Bridge roof dish ribs and feed', v, f, kit.steel)
    ribs.location = D
    ribs.rotation_euler = (0, tilt_d, 0)

    # Navigation scanner (port roof) and sensor dome (starboard roof).
    sx, sy = -59.4, c3_hull.y(13.0, 1)
    v, f = [], []
    disc(v, f, (sx, sy, ROOF+.7), (0, 0, 1), .7, 1.4, n=24)
    block(v, f, (sx-.9, sy-.7, ROOF+1.4), (sx+.9, sy+.7, ROOF+2.3))
    disc(v, f, (sx, sy, ROOF+2.5), (0, 0, 1), .35, .4, n=16)
    solid(kit, 'Bridge roof navigation scanner pedestal', v, f, kit.steel, bevel=.03)
    v, f = [], []
    beam(v, f, (sx, sy-3.6, ROOF+3.15), (sx, sy+3.6, ROOF+3.15), .55, .9, (1, 0, 0))
    solid(kit, 'Bridge roof navigation scanner array', v, f, kit.pale, bevel=.08)
    v, f = [], []
    beam(v, f, (sx+.3, sy-3.4, ROOF+3.15), (sx+.3, sy+3.4, ROOF+3.15), .1, .35, (1, 0, 0))
    solid(kit, 'Bridge roof navigation scanner slot', v, f, kit.dark)
    dx, dy = -59.4, c3_hull.y(13.0, -1)
    prof = [(.01, -.1), (2.0, -.1), (2.0, .7)]+[(1.9*math.cos(math.radians(a)), .7+1.9*math.sin(math.radians(a))) for a in range(0, 91, 10)][:-1]+[(.01, 2.6)]
    revolve_z(kit, 'Bridge roof sensor dome', prof, kit.pale, n=32).location = (dx, dy, ROOF)
    v, f = [], []
    annulus(v, f, (dx, dy, ROOF+.6), (0, 0, 1), 1.85, 2.2, .3, n=32)
    for i in range(8):
        t = 2*math.pi*(i+.5)/8
        q = Vector((dx+2.1*math.cos(t), dy+2.1*math.sin(t), ROOF+.3))
        beam(v, f, q, q+Vector((0, 0, .3)), .22)
    solid(kit, 'Bridge roof sensor dome flange', v, f, kit.steel)

    # Short mast aft of the ring: base, pole, a yard with two whip antennae, lamps at head and yard ends.
    mx = -65.8
    mz = seat(kit, mx, YC, ROOF)
    kit.box('Bridge mast base', (mx, YC, mz+.8), (2.6, 2.6, 1.6), kit.steel, bevel=.2)
    kit.cylinder('Bridge mast', (mx, YC, mz+1.6+3.5), .35, 7, kit.steel, vertices=16)
    v, f = [], []
    beam(v, f, (mx, YC-3.2, mz+6.4), (mx, YC+3.2, mz+6.4), .3)
    for s in (1, -1):
        beam(v, f, (mx, YC+s*3, mz+6.4), (mx, YC+s*3, mz+10.0), .12)
        beam(v, f, (mx, YC+s*.3, mz+2.0), (mx, YC+s*2.4, mz+6.3), .12)        # yard braces
    beam(v, f, (mx, YC, mz+8.6), (mx, YC, mz+11.0), .1)
    solid(kit, 'Bridge mast yard and antennae', v, f, kit.steel)
    lamp(kit, 'Bridge masthead lamp', (mx, YC, mz+8.85), .35)
    for side, s in c3_hull.SIDES:
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
