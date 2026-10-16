"""Wasp interceptor drone: blockout module for build_fighter.py.

wasp.ship: 18 x 21 sprite, centre (9, 11). A crewless HIGH_TECH drone: a
central spine carrying one point-defence emitter at the nose, a flat hub
where four swept arms meet on round pivot housings, and two drive cans
tucked between the aft arms.

Silhouette from the vanilla sprite (wasp_ftr.png); forms and proportions
read off the up-res master-v2. The master was registered on its gun
barrel, so its arm tips sit about a unit inboard of the sprite's; where
they disagree the sprite wins.
"""
import math
import bpy
import fg_kit as K
from fg_kit import prism, section, mirror_y, section_loft

NAME = 'Wasp'
HULL_ID = 'wasp'
SHIP = 'data/hulls/wasp.ship'
SPRITE = 'graphics/ships/wasp_ftr.png'
MASTER = 'art-source/aircraft/fighter-upres/wasp/master-v2.png'
SOURCE_NOTES = {'variant': 'data/variants/fighters/wasp_Interceptor.variant (WS 001 pdlaser, WS 002 fragbomb)'}

# Gray-blue armour read off the sprite's mid plates (sRGB ~130, 148, 163).
KIT = {'armour': (.22, .29, .36), 'armour_name': 'Wasp gray-blue armour', 'glass': (.12, .72, .92)}

SLOTS = [  # exactly as in wasp.ship
    {'id': 'WS 001', 'location': (1, 0), 'angle': 0, 'arc': 360, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'ENERGY'},
    {'id': 'WS 002', 'location': (-7, 0), 'angle': 180, 'arc': 360, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'ENERGY'},
]
ENGINES = [(-7, 4, 4), (-7, -4, 4)]  # (x, y, width) exactly as in wasp.ship

# Render-only nozzle offsets from the up-res manifest, native px (+X right,
# +Y nose): the master paints its nozzles at slot + offset, i.e. model
# (slot x + oy, slot y - ox).
NOZZLE_OFFSETS = [(2, -1.25), (-2, -1.25)]
NOZZLES = [(x+oy, y-ox) for (x, y, _), (ox, oy) in zip(ENGINES, NOZZLE_OFFSETS)]  # (-8.25, +-2)

METRES_PER_UNIT = 0.5
SCALE = {
    'summary': 'Crewless interceptor drone: one PD emitter, two drive cans, four drooping arms it lands on',
    'method': ('No crew (ship_data min/max crew 0), so no cockpit sets the scale. Sized from its parts: '
               'each 4-unit engine slot is a drive can about 2.8 units across with a 1.6-unit bell, which at '
               '0.5 m/unit is a 1.4 m can around a 0.8 m nozzle, the class of small turbofan a 9-10 m '
               'unmanned combat aircraft carries; the PD emitter barrel is about 1.7 m; the airframe comes '
               'out 9.5 m long and 8 m across the arms, the size of current loyal-wingman drones and about '
               'two thirds of a 14-15 m crewed fighter, which suits a drone carriers launch in numbers. '
               'Below 0.4 the cans shrink under 0.65 m nozzles and the hub under 1 m deep, too small for a '
               'drive, reactor and emitter; above 0.65 it reaches crewed-fighter length with nobody aboard.'),
    'range': [0.4, 0.65],
    'cross_checks_m': {
        'length_overall': 9.5,
        'span_across_arm_tips': 8.0,
        'drive_can_diameter': 1.4,
        'nozzle_bell_diameter': 0.8,
        'pd_emitter_barrel_length': 1.7,
        'XQ-58A Valkyrie (length, span)': [9.1, 8.2],
        'MQ-28 Ghost Bat length': 11.7,
        'F-16 length (crewed reference)': 15.0,
    },
}
PILOTS = []  # crewless drone

NOTES = [
    'Crewless drone (crew 0): no canopy and no pilot seat; scale from engines, gun and size.',
    'Sprite is symmetric about Y = 0 (even width, centre column boundary), as are both slots; engine slots '
    '(-7, +-4) are symmetric already.',
    'Drive cans are centred at Y +-2.6 to cover the sprite\'s engine mounds (Y 1..4); the visible bells sit at '
    'the master\'s painted nozzles, slot + renderEngineOffsetsNativePx = (-8.25, +-2.0), not at the slot '
    'location (-7, +-4). Exhaust anchors are at those bells.',
    'WS 001 (pdlaser, HIDDEN) anchors on the spine deck at (1, 0); the master draws its emitter barrel running '
    'out past the nose tip, modelled as Weapon WS 001 emitter barrel with its muzzle at the barrel tip.',
    'WS 002 (fragbomb, HIDDEN, aft) anchors at (-7, 0) in the gap between the drive cans, where the sprite is '
    'empty; no visible launcher is modelled.',
    'The master was registered on its gun tip, so its arm tips read about one unit inboard of the sprite\'s; '
    'the arms follow the sprite.',
    'Arm edges run straight through the midpoints of the sprite\'s pixel stairs rather than tracing them (owner: '
    'the traced stairs read as pixelated wings). The stair corners the line cuts and leaves account for most of '
    'the drop in silhouette IoU from 0.951 to 0.909; on arms one to three pixels wide they are a large share.',
    'Landing stance (owner): the drone has no gear and stands on a hooked claw under each arm tip. Each arm droops '
    'from its pivot and thins to 40 per cent of its depth at the tip; plan positions are unchanged. The deck is '
    'pitched 6 degrees in the hull frame (Z -2.2 under the centre, rising toward the nose), so parked it crouches '
    'nose-down with the tail raised, while the hull axes stay the flight frame. The fore arms barely droop and carry '
    'short claws; the aft arms droop steeply onto long ones. parked_check records the fitted deck and clearances.',
    'Pass 2 (detail read off master-v2, sized in metres at 0.5 m/unit). Arm articulation: each pivot housing is the '
    'sweep bearing, capped with a bright slewing ring and bolt circle; a fifth of the way out each arm is cut by a '
    'hinge seam carrying two knuckles and a pin across it, the joint the arm droops about; and a two-stage droop '
    'actuator runs from the hub\'s underside to the arm\'s. Claw articulation: a knuckle pin through cheek plates '
    'either side of each claw\'s root, and a tendon actuator from the arm\'s underside to the claw\'s back. None of '
    'it reaches the deck: parked_check still finds only the four claws on it. The emitter carries three focusing '
    'coils, a lens at the muzzle and cooling fins on its housing; the spine the master\'s shoulder vents, flank '
    'sensor windows, a tail grille and lamp, and panel lines; each blade two seams and a tip sensor window; each '
    'drive can a top vent, two collar ribs and a centre body with stator vanes in its bell.',
]

# Pass-2 close-up cameras: (name, view direction from the target, target in units, frame width in metres).
CLOSEUPS = [
    ('nose-detail', (1.0, .9, .85), (5.4, 0.0, .9), 3.6),
    ('hinge-detail', (.35, 1.0, 1.1), (1.6, 4.9, .3), 3.0),
    ('claw-detail', (-.25, 1.0, .2), (-9.0, 7.3, -2.5), 3.0),
    ('engine-detail', (-1.0, .6, .45), (-7.6, 0.0, -.25), 4.6),
]

# --- Spine: (x, half width, keel z, deck z, top chamfer, keel chamfer). ---
# A narrow nose ahead of a shoulder at X 4, where the sprite steps from +-1 to +-2.
SPINE = [
    (7.0, .4, -.25, .45, .17, .15),
    (5.9, .9, -.5, .8, .36, .3),
    (4.2, 1.05, -.65, 1.0, .42, .35),
    (3.85, 1.95, -.95, 1.35, .75, .55),
    (1.0, 2.1, -1.1, 1.5, .8, .6),
    (-3.0, 1.95, -1.1, 1.4, .75, .6),
    (-4.5, 1.25, -.85, 1.05, .5, .45),
    (-5.0, .95, -.65, .8, .38, .32),
]
HUB_PLAN = [(1.0, 4.3), (.5, 4.95), (-3.4, 4.95), (-4.0, 4.3), (-4.45, 1.4), (-4.45, -1.4), (-4.0, -4.3),
            (-3.4, -4.95), (.5, -4.95), (1.0, -4.3)]
HUB_Z = (-.8, .75)

# Port arms (starboard mirrored). Each arm is a dark frame under a lighter
# diagonal armour blade, as the master draws its blade plates over darker
# structure. The frame edges are straight lines through the midpoints of
# the sprite's pixel stairs (the fore arm's edges rise 1 in 2, the aft's
# 1 in 1), with a square-cut tip where the sprite's tip runs flat; the
# stairs themselves are the sprite's sampling, not the shape. Fore arm: tip
# at the sprite's top corner; aft arm: tip at the bottom corner; both roots
# buried in the hub.
FORE_FRAME = [(9.0, 8.0), (5.0, 8.0), (0.0, 5.5), (0.0, 2.5), (9.0, 7.0)]
FORE_BLADE = [(.3, 3.25), (8.85, 7.45), (8.85, 7.88), (6.0, 7.88), (.3, 5.2)]
AFT_FRAME = [(-10.0, 8.0), (-5.5, 8.0), (-2.4, 4.9), (-2.4, 4.0), (-6.5, 4.0), (-10.0, 7.5)]
AFT_BLADE = [(-9.85, 7.88), (-6.1, 7.88), (-2.75, 4.5), (-2.75, 4.12), (-5.9, 4.12)]
FRAME_Z = (-.45, .2)
BLADE_Z = (-.12, .55)
CORNER_CHAMFER = .3

PIVOTS = {'fore': (1.5, 4.45), 'aft': (-3.5, 4.4)}
PIVOT_R = .75

# Landing stance: the drone has no gear and stands on four hooked claws,
# one under each arm tip. Each arm droops from its pivot and thins toward
# the tip; the claw grows out of its underside, swells outboard and curls
# back to a blunt point on the deck. Only height changes, so the plan and
# the sprite match are untouched. The deck is pitched in the hull frame,
# so parked the body crouches nose-down with its tail raised: the hull's
# own axes stay the flight frame, and only the ground under it is tilted.
ARM_TIPS = {'fore': (9.0, 7.5), 'aft': (-10.0, 7.75)}  # tip mid-points; the axis runs pivot -> tip
DROOP = {'fore': .3, 'aft': 2.0}  # units the frame's underside drops from pivot to tip
TIP_TAPER = .6     # share of arm depth lost by the tip
STANCE_PITCH_DEG = 6.0  # nose-down crouch: the deck plane rises toward the nose in the hull frame
DECK_Z0 = -2.2     # deck height under the hull centre (X = 0), hull frame
# Claw rings, base to point: (share of pivot -> tip distance, half length along the arm, half width,
# share of the drop from the arm's underside to the deck). The middle ring sits furthest out, so the
# point curls back under: a talon rather than a post.
CLAW = [(.88, .55, .40, -.25), (.99, .30, .25, .55), (.97, .09, .07, 1.0)]
FEET = [f'Wing {side} {part} claw' for side in ('port', 'starboard') for part in ('fore', 'aft')]
LEG_PARTS = ['Wing ']  # the arms carry the claws; body clearance is measured without them


def deck_z(x):
    return DECK_Z0+x*math.tan(math.radians(STANCE_PITCH_DEG))


POD_CY = 2.6
POD = [  # drive can: (x, half width, keel z, deck z, top chamfer, keel chamfer)
    (-1.8, 1.05, -1.2, .75, .45, .45),
    (-3.0, 1.4, -1.55, 1.05, .55, .55),
    (-7.3, 1.4, -1.55, 1.05, .55, .55),
    (-7.95, 1.3, -1.45, .95, .5, .5),
]
POD_Z = -.25
FACE_X = -7.95
LIP_R, BELL_R = .8, .66
LIP_LEN = .4
MOUTH_X = NOZZLES[0][0]


def chamfer(plan, c):
    """Cut every corner of a plan polygon back by c along both edges."""
    out = []
    n = len(plan)
    for i in range(n):
        (ax, ay), (bx, by), (cx, cy) = plan[i-1], plan[i], plan[(i+1) % n]
        la, lc = ((bx-ax)**2+(by-ay)**2)**.5, ((cx-bx)**2+(cy-by)**2)**.5
        ka, kc = min(c, la*.4)/la, min(c, lc*.4)/lc
        out += [(bx+(ax-bx)*ka, by+(ay-by)*ka), (bx+(cx-bx)*kc, by+(cy-by)*kc)]
    return out


def droop(ob, part, s):
    """Bend an arm part down and thin it along its pivot -> tip axis (linear, so faces stay flat blades)."""
    (rx, ry), (tx, ty) = PIVOTS[part], ARM_TIPS[part]
    ry, ty = s*ry, s*ty
    dx, dy = tx-rx, ty-ry
    for v in ob.data.vertices:
        t = min(max(((v.co.x-rx)*dx+(v.co.y-ry)*dy)/(dx*dx+dy*dy), 0), 1.05)
        v.co.z = FRAME_Z[0]+(v.co.z-FRAME_Z[0])*(1-TIP_TAPER*t)-DROOP[part]*t
    ob.data.update()


def claw(kit, name, part, s):
    """A hooked talon from the arm's underside near the tip down to the pitched deck."""
    (px, py), (tx, ty) = PIVOTS[part], ARM_TIPS[part]
    py, ty = s*py, s*ty
    dx, dy = tx-px, ty-py
    n = math.hypot(dx, dy)
    ax, ay = dx/n, dy/n
    qx, qy = -ay, ax
    tb = CLAW[0][0]
    top = FRAME_Z[0]-DROOP[part]*tb               # arm underside over the claw base
    floor = deck_z(px+dx*CLAW[-1][0])
    verts = []
    for t, hl, hw, k in CLAW:
        cx, cy = px+dx*t, py+dy*t
        z = top+(floor-top)*k if k >= 0 else top-k  # a negative share buries the base in the arm
        verts += [(cx+ax*a*hl+qx*b*hw, cy+ay*a*hl+qy*b*hw, z) for a, b in ((1, 1), (-1, 1), (-1, -1), (1, -1))]
    faces = [(0, 1, 2, 3), (len(verts)-4, len(verts)-3, len(verts)-2, len(verts)-1)]
    for r in range(len(CLAW)-1):
        faces += [(4*r+i, 4*r+(i+1) % 4, 4*(r+1)+(i+1) % 4, 4*(r+1)+i) for i in range(4)]
    return kit.mesh(name, verts, faces, kit.steel, .03)


def build(kit):
    spine = section_loft(kit, 'Hull spine', SPINE, kit.armour, bevel=.04)
    hub = prism(kit, 'Hull hub', HUB_PLAN, section(HUB_Z[0], HUB_Z[1], .35, .35), kit.steel, bevel=.04)
    kit.hull += [spine, hub]

    for side, s in (('port', 1), ('starboard', -1)):
        flip = (lambda p: p) if s > 0 else mirror_y
        for part, frame, blade in (('fore', FORE_FRAME, FORE_BLADE), ('aft', AFT_FRAME, AFT_BLADE)):
            f = prism(kit, f'Wing {side} {part} arm frame', flip(chamfer(frame, CORNER_CHAMFER)),
                      section(FRAME_Z[0], FRAME_Z[1], .18, .15), kit.steel, bevel=.03)
            b = prism(kit, f'Wing {side} {part} arm blade', flip(blade), section(BLADE_Z[0], BLADE_Z[1], .15, .22),
                      kit.armour, bevel=.04)
            droop(f, part, s)
            droop(b, part, s)
            kit.hull += [f, b]
            px, py = PIVOTS[part]
            kit.cylinder(f'Wing {side} {part} pivot housing', (px, s*py, .1), PIVOT_R, 1.6, kit.steel, 'Z', 20, .06)
            claw(kit, f'Wing {side} {part} claw', part, s)

    for i, ((ex, ey, w), (nx, ny)) in enumerate(zip(ENGINES, NOZZLES), 1):
        side = 'port' if ey > 0 else 'starboard'
        s = 1 if ey > 0 else -1
        pod = section_loft(kit, f'Engine {i} {side} drive can', POD, kit.steel, cy=s*POD_CY, bevel=.04)
        kit.hull.append(pod)
        kit.well(pod, FACE_X, ny, POD_Z, BELL_R, depth=.9)
        kit.engine(i, nx, ny, POD_Z, LIP_R, BELL_R, depth=.9, lip_len=LIP_LEN)

    # Sensor slot on the spine deck (the master's cyan strip), sunk flush.
    deck = kit.seat_z(1.6, 0)
    kit.box('Hull spine sensor window', (1.6, 0, deck-.02), (1.7, .5, .12), kit.glass, .03)

    # WS 001: point-defence emitter. Anchor on the spine deck at the slot;
    # the barrel the master draws runs out along the centre line past the nose.
    ws1, ws2 = SLOTS
    z1 = kit.seat_z(1, 0)
    zb = kit.seat_z(6.3, 0)
    kit.box('Weapon WS 001 emitter housing', (5.6, 0, zb+.12), (1.6, .62, .42), kit.steel, .06)
    kit.cylinder('Weapon WS 001 emitter barrel', (7.1, 0, zb+.2), .17, 3.0, kit.dark, 'X', 12, .03)
    kit.slot_anchor(ws1, (1, 0, z1), muzzle=(8.6, 0, zb+.2))
    # WS 002: aft frag-bomb release between the drive cans, no visible launcher.
    kit.slot_anchor(ws2, (-7, 0, POD_Z))
    if kit.detail:
        detail(kit)


def arm_axis(part, s):
    """Pivot, tip, unit axis and unit horizontal normal (toward the arm's leading side) for one arm."""
    (px, py), (tx, ty) = PIVOTS[part], ARM_TIPS[part]
    p, t = (px, s*py), (tx, s*ty)
    n = math.hypot(t[0]-p[0], t[1]-p[1])
    a = ((t[0]-p[0])/n, (t[1]-p[1])/n)
    return p, t, a, (-a[1], a[0])


def underside(part, t):
    return FRAME_Z[0]-DROOP[part]*t


def oriented_box(kit, name, centre, axis, dims_m, mat, bevel_m=.008):
    """A box whose length runs along the horizontal unit vector axis."""
    from mathutils import Matrix
    L, W, T = (kit.u(v) for v in dims_m)
    ob = kit.box(name, (0, 0, 0), (L, W, T), mat, kit.u(bevel_m))
    yaw = math.atan2(axis[1], axis[0])
    ob.matrix_world = Matrix.Translation(centre) @ Matrix.Rotation(yaw, 4, 'Z')
    return ob


def detail(kit):
    kit.mark_detail()
    ob = bpy.data.objects
    A, S, D, B, G = kit.armour, kit.steel, kit.dark, kit.bright, kit.glass
    u = kit.u
    spine, hub = ob['Hull spine'], ob['Hull hub']
    w = .05

    # Emitter: focusing coils along the barrel, a lens in its tip, cooling fins on the housing.
    zb = ob['Weapon WS 001 emitter barrel'].location.z
    for k, x in enumerate((6.9, 7.45, 8.0), 1):
        kit.xcyl(f'Weapon WS 001 emitter coil {k}', x-u(.05), x+u(.05), 0, zb, .12, B, 12)
    kit.xcyl('Weapon WS 001 emitter lens', 8.52, 8.62, 0, zb, .055, G, 12, 0)
    housing = ob['Weapon WS 001 emitter housing']
    hz = housing.location.z
    for k, x in enumerate((5.15, 5.55, 5.95), 1):
        for side, s in (('port', 1), ('starboard', -1)):
            kit.box(f'Weapon WS 001 emitter fin {side} {k}', (x, s*(.31+u(.02)), hz), (u(.08), u(.08), .36), S, u(.008))

    # Spine: the master's shoulder vents, flank sensor windows, tail grille and lamp, panel lines.
    for side, s in (('port', 1), ('starboard', -1)):
        kit.grille(f'Hull spine {side} shoulder vent', 3.0, s*1.15, .7, .34, 3)
        kit.seated(f'Hull spine {side} sensor window', -1.6, s*1.75, (.34, .12, .03), G, s*0, .01, 0)
    kit.grille('Hull spine tail grille', -3.85, 0, .55, .7, 4)
    kit.seated('Hull spine tail lamp', -4.75, 0, (.2, .14, .03), G, 0, .01, 0)
    kit.engrave(spine, [kit.band_slab(3.95, w, z0=-.2), kit.band_slab(-3.3, w, z0=-.2),
                        *kit.lines([(3.6, .62), (-3.0, .62)], w, .6), *kit.lines([(3.6, -.62), (-3.0, -.62)], w, .6),
                        *kit.outline(.2, 0, 2.2, 1.3, w, -3, -.6)])
    kit.engrave(hub, kit.outline(-1.7, 3.3, 1.4, .9, w, -3, -.4) + kit.outline(-1.7, -3.3, 1.4, .9, w, -3, -.4))

    for side, s in (('port', 1), ('starboard', -1)):
        for part in ('fore', 'aft'):
            p, t, a, q = arm_axis(part, s)
            d = math.hypot(t[0]-p[0], t[1]-p[1])
            at_t = lambda k: (p[0]+a[0]*d*k, p[1]+a[1]*d*k)
            name = f'Wing {side} {part}'
            housing = ob[f'{name} pivot housing']
            top = housing.location.z+.8
            # Sweep bearing: a bright slewing ring and bolt circle on the pivot housing.
            kit.cylinder(f'{name} pivot slewing ring', (p[0], p[1], top+u(.02)), PIVOT_R*.86, u(.06), B, 'Z', 24, u(.01))
            kit.cylinder(f'{name} pivot cap', (p[0], p[1], top+u(.05)), PIVOT_R*.5, u(.06), S, 'Z', 16, u(.01))
            for k in range(6):
                c = 2*math.pi*k/6
                kit.cylinder(f'{name} pivot bolt {k+1}', (p[0]+math.cos(c)*PIVOT_R*.7, p[1]+math.sin(c)*PIVOT_R*.7,
                                                          top+u(.05)), u(.03), u(.04), D, 'Z', 6, 0)
            # Droop hinge: a seam across frame and blade a fifth of the way out, two knuckles and a pin.
            hx, hy = at_t(.2)
            seam = lambda: kit.line_slab((hx-q[0]*2.2, hy-q[1]*2.2), (hx+q[0]*2.2, hy+q[1]*2.2), .06)
            kit.engrave(ob[f'{name} arm blade'], seam(), .04)
            kit.engrave(ob[f'{name} arm frame'], seam(), .04)
            hz = kit.seat_z(hx, hy)
            for k, off in enumerate((-.42, .42), 1):
                kit.rod(f'{name} hinge knuckle {k}', (hx+q[0]*(off-.2), hy+q[1]*(off-.2), hz+u(.03)),
                        (hx+q[0]*(off+.2), hy+q[1]*(off+.2), hz+u(.03)), .1, S, 12)
            kit.rod(f'{name} hinge pin', (hx-q[0]*.75, hy-q[1]*.75, hz+u(.03)), (hx+q[0]*.75, hy+q[1]*.75, hz+u(.03)),
                    .045, B, 8)
            # Droop actuator: a cylinder from the hub's underside and its rod to the arm's.
            r0 = (p[0]-a[0]*.6, p[1]-a[1]*.6, HUB_Z[0]+.05)
            ax, ay = at_t(.48)
            r2 = (ax, ay, underside(part, .48)+u(.03))
            r1 = tuple(r0[k]+(r2[k]-r0[k])*.55 for k in range(3))
            kit.rod(f'{name} droop actuator cylinder', r0, r1, .07, S, 10)
            kit.rod(f'{name} droop actuator rod', r1, r2, .04, B, 8)
            # Blade seams and the master's tip sensor window.
            sx, sy = at_t(.55)
            sx2, sy2 = at_t(.75)
            kit.engrave(ob[f'{name} arm blade'],
                        [kit.line_slab((sx-q[0]*2, sy-q[1]*2), (sx+q[0]*2, sy+q[1]*2), w, -.2),
                         kit.line_slab((sx2-q[0]*2, sy2-q[1]*2), (sx2+q[0]*2, sy2+q[1]*2), w, -.2)])
            wx, wy = at_t(.86)
            kit.seated(f'{name} tip sensor window', wx+q[0]*.1, wy+q[1]*.1, (.34, .13, .03), G,
                       math.degrees(math.atan2(a[1], a[0])), .01, 0)
            # Claw articulation: cheek plates either side of the claw's root, a knuckle pin through them,
            # and a tendon actuator from the arm's underside to the claw's back.
            tb, hw = .94, .34  # where the claw leaves the arm's underside, and its half width there
            cx, cy = at_t(tb)
            cz = underside(part, tb)-u(.05)
            for k, sgn in enumerate((1, -1), 1):
                oriented_box(kit, f'{name} claw knuckle cheek {k}', (cx+q[0]*sgn*(hw+u(.03)), cy+q[1]*sgn*(hw+u(.03)), cz),
                             a, (.36, .05, .24), S)
            kit.rod(f'{name} claw knuckle pin', (cx-q[0]*(hw+u(.11)), cy-q[1]*(hw+u(.11)), cz),
                    (cx+q[0]*(hw+u(.11)), cy+q[1]*(hw+u(.11)), cz), .05, B, 10)
            ex, ey = at_t(.7)
            tx2, ty2 = at_t(CLAW[1][0]-.04)
            back = CLAW[1][1]
            fz = underside(part, CLAW[1][0])
            floor = deck_z(p[0]+(t[0]-p[0])*CLAW[-1][0])
            kit.rod(f'{name} claw tendon', (ex, ey, underside(part, .7)+u(.02)),
                    (tx2-a[0]*back*.6, ty2-a[1]*back*.6, fz+(floor-fz)*.3), .035, B, 8)

    # Drive cans: a top vent, two collar ribs and the drive core in each bell.
    for i, ((ex, ey, _), (nx, ny)) in enumerate(zip(ENGINES, NOZZLES), 1):
        side = 'port' if ey > 0 else 'starboard'
        s = 1 if ey > 0 else -1
        can = ob[f'Engine {i} {side} drive can']
        kit.grille(f'Engine {i} can top vent', -4.6, s*POD_CY, .7, .5, 3)
        for k, x in enumerate((-6.3, -6.95), 1):
            kit.skin(can, f'Engine {i} collar rib {k}', kit.band_slab(x, .1), .04, S)
        kit.drive_core(i, nx, ny, POD_Z, BELL_R, depth=.9, vanes=6)
        kit.engrave(can, kit.band_slab(-3.6, w))
