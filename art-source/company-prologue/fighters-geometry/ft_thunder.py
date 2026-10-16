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
        (2.0, 2.6, -.9, 1.9, 1.0, .8), (-3.0, 1.9, -.6, 1.5, .8, .6), (-5.2, 1.1, -.3, .9, .4, .3)], A)
    # Port gun block: Y 0..8 from the wing root to X 20, carrying the two prongs.
    block = section_loft(kit, 'Hull port gun block', [
        (20.1, 3.8, -.3, 1.05, .45, .45), (17.0, 3.9, -.4, 1.3, .55, .5), (12.5, 3.9, -.4, 1.3, .55, .5),
        (10.5, 3.0, -.3, 1.0, .45, .4)], A, cy=4.0)
    # Starboard lance: Y -1..-4 to the nose; its root is widened to Y -5 by the MG housing.
    lance = section_loft(kit, 'Hull starboard lance', [
        (29.2, .5, .5, 1.0, .2, .2), (28.4, 1.1, .3, 1.35, .4, .3), (25.0, 1.4, .1, 1.5, .5, .4),
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
