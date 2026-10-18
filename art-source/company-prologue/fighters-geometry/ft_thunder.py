"""Thunder heavy interceptor blockout (pass 1).

thunder.ship (MIDLINE, crew 1, 26x43, centre (13, 13)): three HIDDEN small
slots, WS 001 energy (20, 6), WS 002 ballistic (20, -5) and WS 003 missile
(15, 0); two engines at (-12, 7) and (-12, -6), width 14. thunder_Fighter
fits an ion cannon, light dual MG and swarmer launcher in them.

Form, read off graphics/ships/thunder.png, which is not mirrored. Forward of
X 12 the port side carries a gun block (Y 0..8) ending in two prongs: the
ion cannon, the outer prong at Y 6 on WS 001, and an inner prong at Y 2.
The starboard side carries a long lance offset to Y -2.5 that runs to the
nose at X 29, with the dual MG in a housing on its starboard root at WS 002.
The forward starboard quarter is empty. Aft of X 12 the body, the swept
delta, the wingtip plates and the two engine pods are near-symmetric. The
pods and bells sit at +-6.75 and +-6.5, the mean of the integer slots,
while the slot anchors keep the .ship coordinates exactly.
"""
import math
import bpy
import fg_kit as K
from fg_kit import prism, section, section_loft, mirror_y

NAME = 'Thunder'
HULL_ID = 'thunder'
SHIP = 'data/hulls/thunder.ship'
SPRITE = 'graphics/ships/thunder.png'
MASTER = 'art-source/aircraft/fighter-upres/thunder/master-v1.png'
SOURCE_NOTES = {
    'ship_sprite_name': 'graphics/ships/thunder2.png',
    'plan_reference': 'graphics/ships/thunder.png',
    'sprite_choice': ('The .ship renders thunder2.png, but the up-res master and the mod runtime texture '
                      '(graphics/battle/aircraft/thunder.png) come from thunder.png, and the owner chose its '
                      'asymmetric design: a twin-prong port gun block and a long lance offset to starboard '
                      '(Y -1..-4). The model follows thunder.png. master-v1 is a mirrored hybrid (centred lance, '
                      'symmetric booms) and is used for sections and detail only, not for the plan.'),
}
KIT = {'armour': (.49, .445, .31), 'armour_name': 'Thunder khaki armour'}

SLOTS = [
    {'id': 'WS 001', 'location': (20, 6), 'angle': 0, 'arc': 15, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'ENERGY'},
    {'id': 'WS 002', 'location': (20, -5), 'angle': 0, 'arc': 15, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 003', 'location': (15, 0), 'angle': 0, 'arc': 120, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'MISSILE'},
]
ENGINES = [(-12, 7, 14), (-12, -6, 14)]  # exactly as in thunder.ship (x, y, width)

METRES_PER_UNIT = .45
PILOTS = [(5.5, 0)]
SCALE = {
    'summary': 'Heavy interceptor, one pilot, starboard lance, port twin-prong gun block, two engine pods',
    'method': ('Sized from the single-seat cockpit: the canopy is 7.2 x 2.6 units, which at 0.45 m/unit is a 3.2 m '
               'by 1.2 m bubble, inside the 2.6-3.5 m by 0.9-1.3 m of real single-seat canopies, and the seated '
               '1.8 m proxy clears it. Width bounds the range (0.9-1.3 m sill -> 0.35-0.50), canopy length the low '
               'end (>= 2.6 m -> 0.37), overall length the high end (41 units <= 22 m -> 0.53).'),
    'range': [.40, .50],
    'cross_checks_m': {'length': 18.6, 'span': 10.9, 'engine_pod_diameter': 2.9, 'gun_block_width': 3.5,
                       'su_27_length': 21.9, 'f_15_length': 19.4, 'f_14_span_swept': 11.6},
}
NOTES = [
    'Asymmetric forward of X 12, as thunder.png draws it: a port gun block with two prongs, and a starboard lance '
    'offset to Y -2.5. Aft of that the engine slots straddle the centre line, so the bells sit at +-6.5 (the slot '
    'mean). Anchors keep the exact .ship XY.',
    'All three slots are HIDDEN. The ion cannon is the outer port prong (muzzle at WS 001, Y 6); the inner prong '
    'at Y 2 is drawn hull, not a weapon. The light dual MG is the short twin barrel on the starboard root of the '
    'lance (WS 002). The swarmer launcher is the dark bay on the fuselage nose at WS 003, with no visible gun.',
    'master-v1 is mirrored and does not match this plan; see source.sprite_choice.',
    'Pass 2 (detail read off master-v1\'s parts, which survive its mirrored plan, sized in metres at 0.45 '
    'm/unit): the lance carries the master\'s long slatted dorsal grille, a slot near its tip and a radome seam. The '
    'ion cannon (outer prong) gets coil bands, a focus ring and a dark bore; the inner prong bands and a cap; the '
    'dual MG perforated barrel jackets, muzzle brakes and a breech cover on its housing; the gun block a capacitor '
    'vent. The swarmer bay at WS 003 shows three launch cells in its recess. The canopy has a sill and two bows; '
    'behind it runs the master\'s dark dorsal spine with a ribbed grille, flanked by the master\'s intakes on the '
    'delta. Each engine pod has a forward intake grille, an aft vent, two collar ribs and a centre body with stator '
    'vanes; the tip plates the master\'s two slots on their outer faces. Panel lines split lance, block, fuselage, '
    'delta and pods.',
    'Pass 3 rework (owner, 2026-10-18, the fighters read flat): the lance nose droops, a dorsal spine rises behind the canopy, and the delta folds 5 degrees down outboard of the fuselage, so the pods cant outward. It is the fighter that rolls: tricycle gear with a long nose leg and a twin-wheel main bogie under each pod, standing level. The plan view is unchanged.',
]

# Pass-2 close-up cameras: (name, view direction from the target, target in units, frame width in metres).
CLOSEUPS = [
    ('nose-detail', (1.0, -.8, .8), (24.6, -2.5, 1.0), 6.0),
    ('gun-detail', (1.0, .35, .5), (21.2, 1.0, .5), 6.6),
    ('cockpit-detail', (.8, -1.0, 1.2), (6.2, 0.0, 2.4), 4.8),
    ('aft-detail', (-.4, .6, 1.4), (-.5, 2.5, 1.4), 7.0),
    ('engine-detail', (-1.0, .7, .5), (-11.4, 6.6, -.2), 6.0),
]

ZB = .45  # gun axis height


def build(kit):
    A, S, D = kit.armour, kit.steel, kit.dark
    # Body and swept delta, one plan prism traced off thunder.png's rows (stair midpoints); not mirrored.
    plan = [(20.0, 8.0), (12.5, 8.0), (11.5, 9.0), (6.5, 9.0), (5.5, 10.0), (4.5, 11.0), (3.5, 12.0),
            (-3.0, 12.0), (-3.9, 11.0), (-3.9, 3.5), (-2.0, 3.1), (-2.0, -3.1), (-3.9, -3.5), (-3.9, -11.0),
            (-3.0, -12.0), (3.5, -12.0), (4.5, -11.0), (5.5, -10.0), (6.5, -9.0), (7.5, -8.0), (11.5, -8.0),
            (12.5, -7.0), (14.5, -6.0), (15.5, -5.0), (19.0, -5.0), (19.0, 0.0), (20.0, 0.0)]
    wing = prism(kit, 'Hull delta wing', plan, section(-.35, .85, .25, .35), A)
    fus = section_loft(kit, 'Hull central fuselage', [
        (17.6, 1.0, .0, 1.2, .5, .4), (15.0, 2.3, -.5, 1.7, .8, .6), (9.0, 2.7, -.9, 1.9, 1.0, .8),
        (2.0, 2.6, -.95, 2.0, 1.0, .85), (-.6, 2.4, -1.0, 2.3, 1.05, .85), (-3.0, 1.9, -.7, 1.7, .85, .65),
        (-5.2, 1.1, -.3, .9, .4, .3)], A)
    # Port gun block: Y 0..8 from the wing root to X 20, carrying the two prongs.
    block = section_loft(kit, 'Hull port gun block', [
        (20.1, 3.8, -.3, 1.05, .45, .45), (17.0, 3.9, -.4, 1.3, .55, .5), (12.5, 3.9, -.4, 1.3, .55, .5),
        (10.5, 3.0, -.3, 1.0, .45, .4)], A, cy=4.0)
    # Starboard lance: Y -1..-4 to the nose; its root is widened to Y -5 by the MG housing.
    lance = section_loft(kit, 'Hull starboard lance', [
        (29.2, .5, -.15, .35, .2, .2), (28.4, 1.1, -.2, .9, .4, .3), (25.0, 1.4, -.1, 1.3, .5, .4),
        (19.0, 1.5, .0, 1.75, .6, .5), (12.0, 1.5, -.1, 1.85, .6, .5)], A, cy=-2.5)
    mg = section_loft(kit, 'Hull starboard gun housing', [
        (20.4, .7, -.2, .95, .3, .3), (19.6, .85, -.25, 1.05, .35, .3), (14.0, .85, -.3, 1.1, .35, .3),
        (13.0, .6, -.2, .8, .25, .2)], A, cy=-4.15)
    kit.hull += [wing, fus, block, lance, mg]
    # The swarmer bay: a dark recess in the fuselage nose at WS 003.
    K.boolean(fus, kit.box('Temporary missile bay cut', (15.8, 0, 1.8), (3.4, .8, .5), D, 0))
    kit.box('Hull missile bay recess', (15.8, 0, 1.6), (3.3, .75, .1), D, 0)
    # Canopy on the fuselage, over the widest part of the body.
    section_loft(kit, 'Canopy glass', [
        (9.8, .45, 1.6, 1.95, .2, .1), (8.6, 1.05, 1.6, 2.85, .5, .1), (5.6, 1.3, 1.6, 3.4, .65, .1),
        (3.6, 1.15, 1.6, 3.15, .6, .1), (2.6, .7, 1.6, 2.35, .35, .1)], kit.glass, bevel=.04)
    # The two port prongs (outer: the ion cannon on WS 001; inner: drawn hull), roots wider as at the sprite's X 20.
    for y, name in ((6.0, 'Weapon port gun shroud'), (2.0, 'Hull port inner prong')):
        section_loft(kit, name, [
            (22.6, .85, -.2, .95, .35, .3), (21.0, 1.0, -.25, 1.05, .4, .35), (20.5, 1.45, -.3, 1.1, .45, .4),
            (19.6, 1.45, -.3, 1.1, .45, .4)], S, cy=y)
    kit.cylinder('Weapon port gun barrel', (22.8, 6.0, ZB), .45, 1.2, kit.bright, 'X', 12, .03)
    kit.cylinder('Hull port inner prong tip', (22.8, 2.0, ZB), .4, 1.0, S, 'X', 12, .03)
    # Light dual MG: two short barrels out of the housing's face.
    for dy, which in ((.3, 'inner'), (-.3, 'outer')):
        kit.cylinder(f'Weapon starboard gun barrel {which}', (20.75, -4.15+dy, ZB), .2, .9, kit.bright, 'X', 10, .02)
    for s, side in ((1, 'port'), (-1, 'starboard')):
        kit.box(f'Wing {side} tip plate', (-.2, s*11.35, .25), (6.0, 1.3, 2.2), S, .1)
    for i, (ex, ey, w) in enumerate(ENGINES, 1):
        s = 1 if ey > 0 else -1
        side = 'port' if s > 0 else 'starboard'
        cy = s*6.5  # bell on the engine-slot mean; the pod body follows the sprite's mound at +-6.75
        pod = section_loft(kit, f'Engine {i} {side} pod', [
            (2.4, 1.9, -1.1, .6, .6, .7), (-.8, 3.15, -2.1, 1.4, 1.1, 1.2), (-7.8, 3.15, -2.1, 1.4, 1.1, 1.2),
            (-9.0, 2.45, -1.95, 1.3, .95, 1.05), (-11.9, 2.4, -1.9, 1.25, .9, 1.0)], A, cy=s*6.75)
        kit.hull.append(pod)
        kit.well(pod, -12.0, cy, -.32, 1.75)
        kit.engine(i, -12.0, cy, -.32, 2.0, bell_r=1.75)
    for slot in SLOTS:
        x, y = slot['location']
        if slot['id'] == 'WS 003':
            kit.slot_anchor(slot, (x, y, kit.seat_z(x, y)))
        elif slot['id'] == 'WS 001':
            kit.slot_anchor(slot, (x, y, ZB), muzzle=(23.4, 6.0, ZB))
        else:
            kit.slot_anchor(slot, (x, y, ZB), muzzle=(21.2, -4.15, ZB))
    if kit.detail:
        detail(kit)
    fold_wing()
    tricycle_gear(kit)


# Bends and landing (owner, 2026-10-18: the fighters read flat). The lance's
# nose droops, a dorsal spine rises behind the canopy, and the whole delta
# folds down a little outboard of the fuselage, so the wing, gun block, tip
# plates and pods droop and the pods cant outward. The Thunder is the one
# fighter that rolls: conventional tricycle gear, a long steerable nose leg
# under the fuselage and a twin-wheel main bogie under each pod's inner
# belly. The plan view is unchanged.
FOLD_Y = 2.7               # the fuselage's half width: the delta folds outboard of it
FOLD_DEG = 5.0
DECK_Z = -4.2              # parked deck, level in the hull frame
# (name, x, y, z where the strut enters the hull, wheel radius, drag brace run)
GEAR = [('Hull nose gear', 13.0, 0.0, -.6, .7, 1.8),
        ('Hull port main gear', -3.6, 5.0, -2.3, .9, 2.2),
        ('Hull starboard main gear', -3.6, -5.0, -2.3, .9, 2.2)]
FEET = [f'{g[0]} wheel {w}' for g in GEAR for w in ('inner', 'outer')]
LEG_PARTS = tuple(g[0] for g in GEAR)


def fold_drop(y):
    return math.tan(math.radians(FOLD_DEG))*max(0.0, abs(y)-FOLD_Y)


def fold_wing():
    """Shear every part (and every anchor) down outboard of the fuselage."""
    bpy.context.view_layer.update()
    for ob in list(bpy.data.objects):
        if ob.type == 'EMPTY':
            ob.location.z -= fold_drop(ob.location.y)
            continue
        if ob.type != 'MESH':
            continue
        if ob.data.users > 1:
            ob.data = ob.data.copy()
        mw = ob.matrix_world.copy()
        inv = mw.inverted()
        for v in ob.data.vertices:
            p = mw @ v.co
            p.z -= fold_drop(p.y)
            v.co = inv @ p
        ob.data.update()


def tricycle_gear(kit):
    """Each leg: an oleo strut and piston, a drag brace, an axle and two wheels either side of the strut."""
    S, B, D = kit.steel, kit.bright, kit.dark
    for name, x, y, top, r, brace in GEAR:
        axle = DECK_Z+r
        w = r*.55
        kit.rod(f'{name} strut', (x, y, top+.3), (x, y, axle+r*1.2), .22, S, 12)
        kit.rod(f'{name} piston', (x, y, axle+r*1.4), (x, y, axle), .13, B, 12)
        kit.rod(f'{name} drag brace', (x+brace, y, top+.25), (x, y, axle+r*1.3), .08, S, 8)
        kit.rod(f'{name} axle', (x, y-w-.35, axle), (x, y+w+.35, axle), .07, B, 8)
        inboard = -1 if y >= 0 else 1
        for side, s in (('inner', inboard), ('outer', -inboard)):
            wy = y+s*(w/2+.25)
            kit.cylinder(f'{name} wheel {side}', (x, wy, axle), r, w, D, 'Y', 24, .06)
            kit.cylinder(f'{name} wheel {side} hub', (x, wy, axle), r*.45, w+.08, B, 'Y', 12, .02)


def detail(kit):
    kit.mark_detail()
    ob = bpy.data.objects
    S, D, B, G = kit.steel, kit.dark, kit.bright, kit.glass
    u = kit.u
    w, top = .05, .3
    lance, block, fus, wing = (ob[n] for n in ('Hull starboard lance', 'Hull port gun block', 'Hull central fuselage',
                                              'Hull delta wing'))

    # Lance: the master's long slatted grille, a slot near the tip, a radome seam.
    kit.grille('Hull lance dorsal grille', 23.4, -2.5, 2.0, .45, 9)
    kit.seated('Hull lance tip slot', 27.4, -2.5, (.36, .14, .03), D, 0, .01, 0)
    kit.engrave(lance, [kit.band_slab(28.2, w), kit.band_slab(20.6, w, z0=top),
                        *kit.lines([(19.8, -1.55), (12.4, -1.55)], w, top)])

    # Dual MG: perforated jackets, muzzle brakes, a breech cover on the housing.
    for dy, which in ((.3, 'inner'), (-.3, 'outer')):
        y = -4.15+dy
        kit.xcyl(f'Weapon starboard gun jacket {which}', 20.25, 20.75, y, ZB, .13, D, 12)
        kit.xcyl(f'Weapon starboard gun brake {which}', 21.02, 21.18, y, ZB, .12, B, 12)
    kit.seated('Weapon starboard gun breech cover', 17.4, -4.15, (1.3, .55, .16), S, 0, .03, .02)
    kit.engrave(ob['Hull starboard gun housing'], kit.band_slab(15.2, w, z0=.2))

    # Ion cannon (the outer prong): coil bands, a focus ring and a dark bore; bands on the inner prong.
    shroud, inner = ob['Weapon port gun shroud'], ob['Hull port inner prong']
    for k, x in enumerate((20.9, 21.7), 1):
        kit.skin(shroud, f'Weapon port gun coil {k}', kit.band_slab(x, .09), .04, B)
        kit.skin(inner, f'Hull port inner prong band {k}', kit.band_slab(x, .09), .04, S)
    kit.xcyl('Weapon port gun focus ring', 23.3, 23.44, 6.0, ZB, .16, G, 16, 0)
    kit.xcyl('Weapon port gun bore', 23.36, 23.46, 6.0, ZB, .1, D, 16, 0)
    kit.xcyl('Hull port inner prong cap', 23.25, 23.36, 2.0, ZB, .12, D, 12, 0)
    kit.grille('Hull port gun block vent', 14.4, 5.0, 1.6, .7, 6)
    kit.engrave(block, [kit.band_slab(17.6, w, z0=top), *kit.lines([(19.6, 2.4), (11.4, 2.4)], w, top),
                        *kit.outline(14.6, 3.6, 2.2, 3.0, w, -3, -.2)])

    # WS 003: three launch cells in the swarmer bay's recess.
    for k, x in enumerate((15.0, 15.8, 16.6), 1):
        kit.cylinder(f'Weapon WS 003 launch cell {k}', (x, 0, 1.67), u(.15), u(.04), B, 'Z', 16, 0)
        kit.cylinder(f'Weapon WS 003 launch cell {k} hatch', (x, 0, 1.69), u(.11), u(.04), D, 'Z', 16, 0)

    # Canopy frame; the master's dark dorsal spine with its ribbed grille; the flank intakes on the delta.
    glass = ob['Canopy glass']
    kit.skin(glass, 'Canopy frame sill', kit.region(2.4, 10.0, -1.7, 1.7, -3, 1.9+u(.1)), .05, S)
    kit.skin(glass, 'Canopy frame windscreen arch', kit.band_slab(8.5, .08), .045, S)
    kit.skin(glass, 'Canopy frame bow', kit.band_slab(5.0, .07), .045, S)
    spine = kit.skin(fus, 'Hull dorsal spine', kit.region(-4.6, 2.3, -.95, .95, 1.0, 6), .07, S)
    kit.hull.append(spine)
    kit.grille('Hull dorsal spine grille', -1.2, 0, 1.9, .65, 7)
    kit.hull.remove(spine)
    for side, s in (('port', 1), ('starboard', -1)):
        kit.grille(f'Hull {side} flank intake', .6, s*3.75, 1.2, .5, 4)
    kit.engrave(fus, [kit.band_slab(12.2, w, z0=.5), *kit.outline(5.0, 0, 3.2, 1.6, w, -3, -.4)])
    kit.engrave(wing, kit.lines([(5.4, 9.6), (-3.7, 9.6)], w, 0) + kit.lines([(5.4, -9.6), (-3.7, -9.6)], w, 0))

    # Engine pods: a forward intake grille, an aft vent, two collar ribs, the drive core.
    for i, (ex, ey, _) in enumerate(ENGINES, 1):
        s = 1 if ey > 0 else -1
        side = 'port' if s > 0 else 'starboard'
        pod = ob[f'Engine {i} {side} pod']
        kit.grille(f'Engine {i} pod intake grille', .2, s*6.75, 1.1, .9, 4)
        kit.grille(f'Engine {i} pod aft vent', -6.6, s*6.75, .45, 1.1, 3, 90)
        for k, x in enumerate((-9.2, -9.9), 1):
            kit.skin(pod, f'Engine {i} collar rib {k}', kit.band_slab(x, .12), .05, S)
        kit.drive_core(i, -12.0, s*6.5, -.32, 1.75)
        kit.engrave(pod, [kit.band_slab(-2.2, w), kit.band_slab(-7.9, w, z0=-.5)])

    # Tip plates: the master's two slots on each outer face.
    for side, s in (('port', 1), ('starboard', -1)):
        for k, x in enumerate((-1.3, .9), 1):
            kit.box(f'Wing {side} tip plate slot {k}', (x, s*12.0, .25), (u(.5), u(.04), u(.22)), D, 0)
