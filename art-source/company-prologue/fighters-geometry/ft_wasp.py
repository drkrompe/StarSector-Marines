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
    'summary': 'Crewless interceptor drone: one PD emitter, two drive cans, four swept arms',
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
            kit.hull += [f, b]
            px, py = PIVOTS[part]
            kit.cylinder(f'Wing {side} {part} pivot housing', (px, s*py, .1), PIVOT_R, 1.6, kit.steel, 'Z', 20, .06)

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
