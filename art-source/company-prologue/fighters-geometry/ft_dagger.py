"""Dagger torpedo bomber: blockout module for build_fighter.py.

dagger.ship: 26 x 34 sprite, centre (15, 16). A crewed (crew 1) HIGH_TECH
strike craft built as an open fork: two armoured prongs run forward either
side of an open notch (|Y| < 1, X 2..17) that ends at the cockpit body; a
narrow waist carries the canopy, and a single 12-unit drive block closes
the tail. The wings are unequal: to port a pylon carries the cream torpedo
store on its pod, with a strut out to a tip fin at Y 13..14; to starboard
a short stub carries a tip fin at Y -10..-9.

The sprite's axis is Y = 0: the fork, waist and drive block are symmetric
about the boundary between columns 14 and 15, which is the .ship centre
column 15 counted from the left. Only the wings are asymmetric.

Silhouette from the vanilla sprite (dagger_trp.png); forms and proportions
read off the up-res master-v2. Where they disagree the sprite wins.
"""
import math
import fg_kit as K
from fg_kit import prism, section

NAME = 'Dagger'
HULL_ID = 'dagger'
SHIP = 'data/hulls/dagger.ship'
SPRITE = 'graphics/ships/dagger_trp.png'
MASTER = 'art-source/aircraft/fighter-upres/dagger/master-v2.png'
SOURCE_NOTES = {'variant': 'data/variants/fighters/dagger_Bomber.variant (WS 002 atropos_single; WS 001 empty)'}

# Blue-gray armour read off the sprite's lit plates (sRGB ~167, 183, 188).
KIT = {'armour': (.36, .44, .48), 'armour_name': 'Dagger blue-gray armour', 'glass': (.20, .55, .68)}

SLOTS = [  # exactly as in dagger.ship
    {'id': 'WS 001', 'location': (2, 0), 'angle': 0, 'arc': 360, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'ENERGY'},
    {'id': 'WS 002', 'location': (7, 0), 'angle': 0, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'MISSILE'},
]
ENGINES = [(-13, 0, 12)]  # (x, y, width) exactly as in dagger.ship

METRES_PER_UNIT = 0.5
SCALE = {
    'summary': 'Single-seat torpedo bomber: forked twin-prong nose, offset port torpedo store, one centre-line drive',
    'method': ('Sized from the cockpit. The sprite\'s pale canopy patch is 6 units long on the waist; a '
               'single-seat canopy is 2.6-3.5 m long, so 0.43-0.58 m/unit. At 0.5 the canopy is 3.0 x 1.15 m '
               'at the sill, a seated 1.8 m pilot clears it by the reported margin, and the airframe is '
               '16.4 m long and 12 m across the fins: the A-6 / Su-24 class of two-engine strike aircraft, '
               'the size a torpedo bomber carrying one heavy store should be. The port store comes out 0.95 m '
               'across and 5 m long with its seeker, heavyweight-torpedo proportions (Mk 48: 0.53 x 5.8 m); '
               'the 12-unit drive block is 6 m wide around a 3.2 m nozzle ring. Below 0.43 the canopy is '
               'shorter than any real single-seat canopy; above 0.58 it is longer than one, and the hull '
               'passes 19 m.'),
    'range': [0.43, 0.58],
    'cross_checks_m': {
        'length_overall': 16.4,
        'span_across_tip_fins': 12.0,
        'canopy_length_width': [3.0, 1.15],
        'torpedo_store_length_diameter': [5.0, 0.95],
        'drive_block_width': 6.0,
        'nozzle_ring_diameter': 3.2,
        'A-6 Intruder (length, span)': [16.7, 16.2],
        'Su-24 (length, span spread)': [22.7, 17.6],
        'F-16 length (single seat reference)': 15.0,
    },
}
PILOTS = [(-3.0, 0.0)]

NOTES = [
    'Sprite axis is Y = 0 (boundary of columns 14 and 15); the .ship centre column 15 counts from the left '
    'edge, so the fork, waist and drive block are symmetric about Y = 0 and only the wings differ.',
    'The open notch between the prongs (|Y| < 1) is empty in the sprite down to X = 2, the cockpit body\'s '
    'front face. The master fills its lower half with a central spine and puts its canopy there; the sprite '
    'wins: the canopy sits on the waist (X -0.5..-6.5) under the sprite\'s pale centre patch.',
    'Unequal wings as the sprite draws them: port pylon (Y 7..8) to a pod (Y 8..12) carrying the cream torpedo '
    'store, then a strut (Y 12..13) to a tip fin (Y 13..14); starboard a stub (Y -9..-6) to a tip fin (Y -10..-9).',
    'WS 001 (ENERGY, HIDDEN, unfitted on dagger_Bomber) anchors on the cockpit body\'s front face at the notch '
    'root (2, 0). WS 002 (MISSILE, HIDDEN, atropos_single) anchors in the open notch at (7, 0); no launcher is '
    'modelled there because the sprite draws none. The visible ordnance is the painted port store, modelled '
    'as Weapon WS 002 port torpedo store on the port pod: presentation only, the game launches from (7, 0).',
    'Engine slot (-13, 0), width 12: the 12-unit drive block (X -7..-15, |Y| <= 6) is the drive unit. Its '
    'bell sits on the block\'s aft face at X -15.15, 2.15 units aft of the slot, because the sprite closes '
    'the tail there; the Engine 1 centre exhaust anchor is at that mouth. No renderEngineOffsetsNativePx '
    'is listed for the Dagger.',
]

ARMOUR_CREAM = (.72, .66, .44)  # the torpedo store, sRGB ~ (240, 232, 190) under the sprite's highlight


def loft(kit, name, rows, mat, bevel=.05):
    """Octagonal loft through (x, y0, y1, z0, z1, top chamfer, keel chamfer) rows."""
    return K.loft_x(kit, name, [(x, K.octagon(y0, y1, z0, z1, ct, cb)) for x, y0, y1, z0, z1, ct, cb in rows],
                    mat, bevel)


def mirrored(rows):
    return [(x, -y1, -y0, z0, z1, ct, cb) for x, y0, y1, z0, z1, ct, cb in rows]


def round_loft(kit, name, rows, y, mat, n=12, bevel=.03):
    """Round solid along X through (x, radius, z) rows, centred on Y = y."""
    st = [(x, [(y+r*math.cos(2*math.pi*k/n), z+r*math.sin(2*math.pi*k/n)) for k in range(n)]) for x, r, z in rows]
    return K.loft_x(kit, name, st, mat, bevel)


# Port prong, nose to root: the tip narrows to Y 3.75..4.25, the inner wall
# steps from Y 3 to Y 1 at X 12..10 and the outer wall from 6 to 7.
PRONG = [
    (17.7, 3.75, 4.25, .25, .85, .2, .2),
    (16.6, 3.05, 4.95, -.35, 1.25, .5, .4),
    (15.0, 3.0, 5.6, -.6, 1.45, .6, .5),
    (12.4, 3.0, 6.0, -.75, 1.5, .65, .55),
    (11.4, 2.0, 6.4, -.8, 1.5, .65, .55),
    (10.3, 1.0, 7.0, -.85, 1.5, .7, .55),
    (-.9, 1.0, 7.0, -.85, 1.5, .7, .55),
]
# Cockpit body and waist, symmetric: front face at the notch root X 2.
BODY = [
    (2.0, -6.0, 6.0, -1.0, 1.05, .6, .5),
    (1.2, -6.8, 6.8, -1.3, 1.55, .9, .7),
    (-.9, -6.8, 6.8, -1.35, 1.6, .9, .7),
    (-1.3, -6.0, 6.0, -1.35, 1.6, .9, .7),
    (-2.8, -6.0, 6.0, -1.35, 1.6, .9, .7),
    (-3.3, -5.0, 5.0, -1.3, 1.6, .85, .65),
    (-3.8, -5.0, 5.0, -1.3, 1.6, .85, .65),
    (-4.3, -4.0, 4.0, -1.25, 1.6, .8, .6),
    (-6.8, -4.0, 4.0, -1.25, 1.6, .8, .6),
    (-7.6, -4.8, 4.8, -1.3, 1.6, .8, .6),
]
# Drive block: the 12-unit engine unit, closing on its aft face at X -15.
DRIVE = [
    (-7.1, -4.0, 4.0, -1.3, 1.5, .7, .6),
    (-7.6, -5.0, 5.0, -1.5, 1.7, .8, .7),
    (-8.1, -6.0, 6.0, -1.6, 1.85, .9, .75),
    (-13.9, -6.0, 6.0, -1.6, 1.85, .9, .75),
    (-14.4, -4.6, 4.6, -1.55, 1.85, .7, .6),
    (-15.0, -4.2, 4.2, -1.5, 1.8, .5, .45),
]
# Canopy: (x, half width, height above the waist deck).
CANOPY = [(-.4, .55, .35), (-1.2, 1.1, 1.0), (-3.0, 1.15, 1.18), (-5.0, 1.05, 1.05), (-6.6, .5, .4)]
DECK = 1.6
ENGINE_Z = .15
MOUTH_X = -15.15
LIP_R = 1.6


def build(kit):
    a = kit.armour
    body = loft(kit, 'Hull cockpit body', BODY, a, .06)
    drive = loft(kit, 'Engine 1 drive block', DRIVE, a, .06)
    prong_p = loft(kit, 'Hull port prong', PRONG, a, .05)
    prong_s = loft(kit, 'Hull starboard prong', mirrored(PRONG), a, .05)
    kit.hull += [body, drive, prong_p, prong_s]

    # Canopy glass on the waist deck.
    st = []
    for x, w, h in CANOPY:
        ct = min(w*.6, h*.75)
        st.append((x, K.octagon(-w, w, DECK-.3, DECK+h, ct, .05)))
    K.loft_x(kit, 'Canopy glass', st, kit.glass, .03)

    # Port wing: pylon to the torpedo pod, strut to the tip fin.
    pylon = prism(kit, 'Wing port pylon', [(7.0, 6.6), (7.0, 8.4), (0.0, 8.4), (0.0, 6.6)],
                  section(-.35, .55, .15, .15), a, .04)
    pod = prism(kit, 'Wing port torpedo pod',
                [(10.6, 8.0), (11.2, 8.6), (11.2, 11.4), (10.6, 12.0), (0.0, 12.0), (-1.0, 11.7),
                 (-2.85, 10.9), (-2.85, 10.05), (-1.5, 8.8), (.2, 8.0)],
                section(-1.25, .9, .45, .5), a, .05)
    strut = prism(kit, 'Wing port strut', [(7.0, 11.7), (7.0, 13.2), (2.0, 13.2), (2.0, 11.7)],
                  section(-.25, .45, .12, .12), a, .03)
    fin_p = prism(kit, 'Wing port tip fin',
                  [(8.0, 13.5), (7.3, 14.05), (-.6, 14.05), (-1.0, 13.5), (-.6, 12.95), (7.3, 12.95)],
                  section(-1.0, 1.1, .2, .2), a, .03)
    # Starboard wing: a short stub to its tip fin.
    stub = prism(kit, 'Wing starboard stub',
                 [(6.0, -6.6), (6.9, -7.1), (6.9, -8.0), (6.0, -9.1), (0.0, -9.1), (0.0, -6.6)],
                 section(-.5, .9, .25, .25), a, .04)
    fin_s = prism(kit, 'Wing starboard tip fin',
                  [(8.0, -9.5), (7.3, -8.95), (-.6, -8.95), (-1.0, -9.5), (-.6, -10.05), (7.3, -10.05)],
                  section(-1.0, 1.1, .2, .2), a, .03)
    kit.hull += [pylon, pod, strut, fin_p, stub, fin_s]

    # The painted port store: a cream torpedo half sunk into the pod's deck,
    # with a dark seeker running out past the pod's nose.
    cream = kit.material('Torpedo store cream', ARMOUR_CREAM, .1, .55)
    tz = .9+.5
    round_loft(kit, 'Weapon WS 002 port torpedo store',
               [(2.6, .45, tz), (3.4, .95, tz), (10.4, .95, tz), (11.0, .75, tz)], 10.0, cream)
    round_loft(kit, 'Weapon WS 002 port torpedo seeker',
               [(10.9, .78, tz), (11.6, .7, tz-.05), (12.7, .22, tz-.15)], 10.0, kit.steel)

    # Single centre-line drive on the drive block's aft face.
    bell_r = LIP_R*.82
    kit.well(drive, MOUTH_X, 0, ENGINE_Z, bell_r)
    kit.engine(1, MOUTH_X, 0, ENGINE_Z, LIP_R)

    # Hidden slots: WS 001 on the body's front face at the notch root,
    # WS 002 in the open notch between the prongs.
    kit.slot_anchor(SLOTS[0], (2.0, 0, .1))
    kit.slot_anchor(SLOTS[1], (7.0, 0, .35))
