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
import bpy
from mathutils import Vector
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
    'Pass 2 (detail read off master-v2, sized in metres at 0.5 m/unit): each prong carries the master\'s slatted '
    'vent behind its tip, a small sensor blister ahead of it and seams at its tip cap and root. The unfitted WS 001 '
    'energy mount is a closed aperture (bright ring, dark iris) on the body\'s front face at the notch root. The '
    'canopy has a sill, windscreen arch and bow; the body the master\'s flank intakes and plate seams. The drive '
    'block carries the master\'s dorsal grille, a blade antenna, a collar rib ahead of the lip and a centre body '
    'with stator vanes in its bell. The cream store gets joint bands, cruciform tail fins, a seeker window and two '
    'sway braces to its pod; the fins and stub carry vents and seams.',
    'Pass 3 rework (owner, 2026-10-18: "bend its wings" and "a 3 point landing gear, centralized in the hull"): '
    'the wings are an inverted gull, dropping 30 degrees (port pylon and strut) and 25 (starboard stub) from the '
    'prongs\' outer wall, with the port pod and store hanging level below the hull; the prongs droop at their tips '
    'and their decks fall 6 degrees outward; the drive block swells along its middle; the keel chamfers are deeper. '
    'It lands on three oleo legs inside the hull\'s plan, one under each prong root and one under the drive block, '
    'and stands level. The plan view is unchanged.',
]

# Pass-2 close-up cameras: (name, view direction from the target, target in units, frame width in metres).
CLOSEUPS = [
    ('nose-detail', (1.0, .55, .75), (9.6, 0.0, .5), 7.6),
    ('cockpit-detail', (.8, -.9, 1.2), (-3.2, 0.0, 2.0), 4.4),
    ('store-detail', (.6, 1.0, .8), (7.6, 10.0, 1.2), 5.0),
    ('engine-detail', (-1.0, .7, .5), (-13.6, 0.0, .3), 6.4),
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
    (17.7, 3.75, 4.25, -.15, .45, .2, .2),
    (16.6, 3.05, 4.95, -.65, 1.0, .5, .4),
    (15.0, 3.0, 5.6, -.8, 1.25, .6, .5),
    (12.4, 3.0, 6.0, -.85, 1.42, .65, .55),
    (11.4, 2.0, 6.4, -.85, 1.48, .65, .55),
    (10.3, 1.0, 7.0, -.85, 1.5, .7, .55),
    (-.9, 1.0, 7.0, -.85, 1.5, .7, .55),
]
# Cockpit body and waist, symmetric: front face at the notch root X 2.
BODY = [
    (2.0, -6.0, 6.0, -1.0, 1.05, .6, .95),
    (1.2, -6.8, 6.8, -1.3, 1.55, .9, 1.15),
    (-.9, -6.8, 6.8, -1.35, 1.6, .9, 1.15),
    (-1.3, -6.0, 6.0, -1.35, 1.6, .9, 1.15),
    (-2.8, -6.0, 6.0, -1.35, 1.6, .9, 1.15),
    (-3.3, -5.0, 5.0, -1.3, 1.6, .85, 1.1),
    (-3.8, -5.0, 5.0, -1.3, 1.6, .85, 1.1),
    (-4.3, -4.0, 4.0, -1.25, 1.6, .8, 1.05),
    (-6.8, -4.0, 4.0, -1.25, 1.6, .8, 1.05),
    (-7.6, -4.8, 4.8, -1.3, 1.6, .8, 1.05),
]
# Drive block: the 12-unit engine unit, closing on its aft face at X -15.
DRIVE = [
    (-7.1, -4.0, 4.0, -1.3, 1.5, .7, 1.05),
    (-7.6, -5.0, 5.0, -1.5, 1.7, .8, 1.15),
    (-8.1, -6.0, 6.0, -1.6, 1.85, .9, 1.2),
    (-11.0, -6.0, 6.0, -1.95, 2.15, 1.05, 1.25),
    (-13.9, -6.0, 6.0, -1.6, 1.85, .9, 1.2),
    (-14.4, -4.6, 4.6, -1.55, 1.85, .7, 1.05),
    (-15.0, -4.2, 4.2, -1.5, 1.8, .5, .9),
]
# Canopy: (x, half width, height above the waist deck).
CANOPY = [(-.4, .55, .35), (-1.2, 1.1, 1.0), (-3.0, 1.15, 1.18), (-5.0, 1.05, 1.05), (-6.6, .5, .4)]
DECK = 1.6
ENGINE_Z = .15
MOUTH_X = -15.15
LIP_R = 1.6

# Wings and landing (owner, 2026-10-18). The wings are an inverted gull:
# each drops from the prong's outer wall, the port pod hangs level below the
# hull with the store riding on it, and each tip fin is carried down with its
# wing. The bends are applied after detail, so everything built on a wing
# moves with it, and the plan view the sprite draws is unchanged.
# The bomber lands on three legs kept inside the hull's own plan: a main leg
# under each prong root and a tail leg under the drive block, each an oleo
# strut with a drag brace on a foot pad. The wings carry no load.
WING_ROOT_Y = 7.0          # the prongs' outer wall: every wing bends from here
PRONG_ANHEDRAL_DEG = 6.0   # the prongs' decks fall toward their outer walls
PYLON_DROOP_DEG = 30.0     # port pylon, prong to pod
STRUT_DROOP_DEG = 30.0     # port strut, pod to tip fin
STUB_DROOP_DEG = 25.0      # starboard stub, prong to tip fin
FIN_KEEL, FIN_TOP = -1.0, 1.1
_t = lambda d: math.tan(math.radians(d))
POD_DROP = _t(PYLON_DROOP_DEG)*(8.2-WING_ROOT_Y)
FIN_DROP = {'port': POD_DROP+_t(STRUT_DROOP_DEG)*(13.1-11.7), 'starboard': _t(STUB_DROOP_DEG)*(9.0-WING_ROOT_Y)}
DECK_Z = -3.4              # parked deck, level in the hull frame
PAD_T = .3
# (name, x, y, z where the strut enters the hull, drag brace run fore (+) or aft (-))
GEAR = [('Hull port main gear', 7.0, 3.9, -1.0, -2.2),
        ('Hull starboard main gear', 7.0, -3.9, -1.0, -2.2),
        ('Hull tail gear', -11.6, 0.0, -1.7, 2.2)]
FEET = [f'{g[0]} pad' for g in GEAR]
LEG_PARTS = tuple(g[0] for g in GEAR)


def build(kit):
    a = kit.armour
    body = loft(kit, 'Hull cockpit body', BODY, a, .06)
    drive = loft(kit, 'Engine 1 drive block', DRIVE, a, .06)
    prong_p = loft(kit, 'Hull port prong', PRONG, a, .05)
    prong_s = loft(kit, 'Hull starboard prong', mirrored(PRONG), a, .05)
    kit.hull += [body, drive, prong_p, prong_s]
    for prong in (prong_p, prong_s):  # anhedral: the deck falls toward the outer wall
        warp(prong, lambda p: (p.x, p.y, p.z-_t(PRONG_ANHEDRAL_DEG)*max(0.0, abs(p.y)-1.0)))

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
                  section(FIN_KEEL, FIN_TOP, .2, .2), a, .03)
    # Starboard wing: a short stub to its tip fin.
    stub = prism(kit, 'Wing starboard stub',
                 [(6.0, -6.6), (6.9, -7.1), (6.9, -8.0), (6.0, -9.1), (0.0, -9.1), (0.0, -6.6)],
                 section(-.5, .9, .25, .25), a, .04)
    fin_s = prism(kit, 'Wing starboard tip fin',
                  [(8.0, -9.5), (7.3, -8.95), (-.6, -8.95), (-1.0, -9.5), (-.6, -10.05), (7.3, -10.05)],
                  section(FIN_KEEL, FIN_TOP, .2, .2), a, .03)
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
    if kit.detail:
        detail(kit)
    bend_wings()
    landing_gear(kit)


def warp(ob, fn):
    """Move every vertex of ob through fn, in the hull frame."""
    if ob.data.users > 1:
        ob.data = ob.data.copy()
    mw = ob.matrix_world.copy()
    inv = mw.inverted()
    for v in ob.data.vertices:
        p = mw @ v.co
        v.co = inv @ Vector(fn(p))
    ob.data.update()


def wing_bend(name, p):
    """Where the inverted gull puts a point of the named wing part."""
    x, y, z = p
    if name.startswith('Wing port pylon'):
        return x, y, z-_t(PYLON_DROOP_DEG)*max(0.0, y-WING_ROOT_Y)
    if name.startswith(('Wing port torpedo pod', 'Weapon WS 002 ')):
        return x, y, z-POD_DROP
    if name.startswith('Wing port strut'):
        return x, y, z-POD_DROP-_t(STRUT_DROOP_DEG)*max(0.0, y-11.7)
    if name.startswith('Wing starboard stub'):
        return x, y, z-_t(STUB_DROOP_DEG)*max(0.0, -WING_ROOT_Y-y)
    for side in ('port', 'starboard'):
        if name.startswith(f'Wing {side} tip fin'):
            return x, y, z-FIN_DROP[side]
    return None


def bend_wings():
    bpy.context.view_layer.update()
    for ob in list(bpy.data.objects):
        if ob.type == 'MESH' and wing_bend(ob.name, (0, 0, 0)) is not None:
            warp(ob, lambda p, n=ob.name: wing_bend(n, p))


def landing_gear(kit):
    """Three oleo legs under the hull: a strut cylinder from the belly, its piston, a drag brace and a foot pad."""
    S, B, D = kit.steel, kit.bright, kit.dark
    for name, x, y, top, brace in GEAR:
        sole = DECK_Z+PAD_T
        knee = top+(sole-top)*.45
        kit.rod(f'{name} strut', (x, y, top+.4), (x, y, knee), .24, S, 12)
        kit.rod(f'{name} piston', (x, y, knee+.2), (x, y, sole-.1), .14, B, 12)
        kit.rod(f'{name} gland', (x, y, knee+.12), (x, y, knee-.12), .29, D, 12)
        kit.rod(f'{name} drag brace', (x+brace, y, top+.3), (x, y, knee+.1), .08, S, 8)
        kit.box(f'{name} pad', (x, y, DECK_Z+PAD_T/2), (2.0, 1.5, PAD_T), D, .06)


def detail(kit):
    kit.mark_detail()
    ob = bpy.data.objects
    S, D, B, G = kit.steel, kit.dark, kit.bright, kit.glass
    u = kit.u
    w, top = .05, .3
    body, drive = ob['Hull cockpit body'], ob['Engine 1 drive block']

    # Prongs: the master's slatted vent behind each tip, a sensor blister ahead of it, seams.
    for side, s in (('port', 1), ('starboard', -1)):
        prong = ob[f'Hull {side} prong']
        kit.grille(f'Hull {side} prong tip vent', 14.6, s*4.3, .9, .5, 3)
        kit.fairing(f'Hull {side} prong sensor fairing', 16.4, s*4.05, .55, .26, .12, S)
        kit.engrave(prong, [kit.band_slab(15.6, w), kit.band_slab(11.4, w, z0=top),
                            *kit.lines([(13.6, s*5.5), (1.0, s*5.5)], w, top),
                            *kit.lines([(9.6, s*2.3), (-.6, s*2.3)], w, top)])

    # WS 001 (energy, unfitted): a closed aperture on the body's front face at the notch root.
    kit.xcyl('Weapon WS 001 aperture ring', 1.92, 2.08, 0, .1, .3, B, 20)
    kit.xcyl('Weapon WS 001 aperture iris', 1.95, 2.12, 0, .1, .22, D, 20, 0)

    # Body: canopy frame, the master's flank intakes, seams.
    glass = ob['Canopy glass']
    kit.skin(glass, 'Canopy frame sill', kit.region(-6.8, -.2, -1.6, 1.6, -3, DECK+u(.1)), .05, S)
    kit.skin(glass, 'Canopy frame windscreen arch', kit.band_slab(-1.25, .08), .045, S)
    kit.skin(glass, 'Canopy frame bow', kit.band_slab(-4.1, .07), .045, S)
    for side, s in (('port', 1), ('starboard', -1)):
        kit.grille(f'Hull {side} flank intake', -2.05, s*4.4, 1.3, .6, 4)
    kit.engrave(body, [kit.band_slab(-.95, w, z0=top), *kit.lines([(-1.1, 2.4), (-7.4, 2.4)], w, top),
                       *kit.lines([(-1.1, -2.4), (-7.4, -2.4)], w, top),
                       *kit.outline(-3.2, 0, 2.6, 1.8, w, -3, -.8)])

    # Drive block: the master's dorsal grille, a blade antenna, a collar rib, the drive core.
    kit.grille('Engine 1 drive block dorsal grille', -10.9, 0, 1.7, 1.2, 6)
    kit.blade('Hull dorsal antenna', -8.9, 3.0, .34, .55, S)
    kit.skin(drive, 'Engine 1 collar rib', kit.band_slab(-13.55, .14), .05, S)
    kit.drive_core(1, MOUTH_X, 0, ENGINE_Z, LIP_R*.82)
    kit.engrave(drive, [kit.band_slab(-8.7, w), kit.band_slab(-12.6, w, z0=top),
                        *kit.lines([(-8.8, 3.6), (-13.4, 3.6)], w, top), *kit.lines([(-8.8, -3.6), (-13.4, -3.6)], w, top),
                        *kit.outline(-11.0, 0, 2.6, 2.4, w, -3, -1.0)])

    # Port store: joint bands, cruciform tail fins, a seeker window and two sway braces to the pod.
    tz, ty = .9+.5, 10.0
    for k, x in enumerate((4.6, 8.4), 1):
        kit.xcyl(f'Weapon WS 002 store joint band {k}', x-u(.04), x+u(.04), ty, tz, .485, D, 20, 0)
    for k, a in enumerate((45, 135), 1):
        c, sn = math.cos(math.radians(a)), math.sin(math.radians(a))
        # The upper pair only: the store is half sunk in its pod, which hides the lower two.
        fin = kit.box(f'Weapon WS 002 store tail fin {k}', (2.9, ty+c*.7, tz+sn*.7), (.7, u(.03), .6), S, u(.006))
        fin.rotation_euler = (math.radians(a-90), 0, 0)
    kit.xcyl('Weapon WS 002 store seeker window', 12.62, 12.76, ty, tz-.15, .08, G, 12, 0)
    for k, x in enumerate((4.0, 9.0), 1):  # from the pod's deck to the store where it leaves the pod
        for side, s in (('inboard', -1), ('outboard', 1)):
            kit.rod(f'Weapon WS 002 store sway brace {k} {side}', (x, ty+s*1.4, .88), (x, ty+s*.86, 1.12), .04, S, 8)
    pod = ob['Wing port torpedo pod']
    kit.grille('Wing port torpedo pod vent', .4, 11.45, .7, .32, 3)
    kit.engrave(pod, [kit.band_slab(7.4, w, z0=-.2), kit.band_slab(1.6, w, z0=-.2), *kit.outline(5.0, 10.0, 3.0, 1.2, w, -3, -.9)])

    # Fins and stub: the master's slatted vents near the fin tops, seams.
    for side, y in (('port', 13.5), ('starboard', -9.5)):
        fin = ob[f'Wing {side} tip fin']
        kit.grille(f'Wing {side} tip fin vent', 6.2, y, .7, .34, 3)
        kit.engrave(fin, [kit.band_slab(3.6, w), kit.band_slab(.4, w, z0=0)])
    kit.engrave(ob['Wing starboard stub'], kit.lines([(5.6, -6.4), (5.6, -9.3)], w, 0) +
                kit.lines([(2.4, -6.4), (2.4, -9.3)], w, 0))
