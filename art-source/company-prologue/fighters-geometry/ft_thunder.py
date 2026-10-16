"""Thunder heavy interceptor blockout (pass 1).

thunder.ship (MIDLINE, crew 1, 26x43, centre (13, 13)): three HIDDEN small
slots, WS 001 energy (20, 6) and WS 002 ballistic (20, -5) on the two
forward gun booms, WS 003 missile (15, 0) under the spine; two engines at
(-12, 7) and (-12, -6), width 14. thunder_Fighter fits an ion cannon,
light dual MG and swarmer launcher in them.

Form, read off the up-res master: a long narrow lance spine running to
the nose, a compact central fuselage with the canopy at its widest point,
a swept delta whose leading edge runs from the gun-boom roots to grey
wingtip plates, two forward-projecting gun booms, and two whole engine
pods under the trailing edge. Built symmetric about Y = 0, the sprite's
own centre line; the integer slots (6 / -5, 7 / -6) straddle it, so the
booms sit at +-5.5 and the pods and bells at +-6.5, while the slot
anchors keep the .ship coordinates exactly.
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
    'sprite_choice': ('The .ship renders thunder2.png; the up-res master (and the mod runtime texture '
                      'graphics/battle/aircraft/thunder.png) was generated from and registered to thunder.png, so '
                      'SPRITE is thunder.png. The two share the body aft of X 7 but differ forward: thunder.png has '
                      'an asymmetric twin-prong port pod and a long lance offset to starboard (Y -1..-4); thunder2.png '
                      'has two symmetric gun booms at the slots and no lance. The master keeps thunder.png\'s '
                      'full-length lance but centres it and draws thunder2-style booms at +-5.5. The model follows '
                      'the master, so it reads as a hybrid of the two sprites; IoU is reported against both.'),
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
    'summary': 'Heavy interceptor, one pilot, lance nose, twin gun booms, two engine pods',
    'method': ('Sized from the single-seat cockpit: the canopy is 7.2 x 2.6 units, which at 0.45 m/unit is a 3.2 m '
               'by 1.2 m bubble, inside the 2.6-3.5 m by 0.9-1.3 m of real single-seat canopies, and the seated '
               '1.8 m proxy clears it. Width bounds the range (0.9-1.3 m sill -> 0.35-0.50), canopy length the low '
               'end (>= 2.6 m -> 0.37), overall length the high end (41 units <= 22 m -> 0.53).'),
    'range': [.40, .50],
    'cross_checks_m': {'length': 18.6, 'span': 10.9, 'engine_pod_diameter': 2.9, 'gun_boom_width': 1.3,
                       'su_27_length': 21.9, 'f_15_length': 19.4, 'f_14_span_swept': 11.6},
}
NOTES = [
    'Integer slots straddle the sprite centre line: model symmetric about Y = 0; gun booms and muzzles at +-5.5 '
    '(slot mean), pods and engine bells at +-6.5 (engine slot mean). Anchors keep the exact .ship XY.',
    'All three slots are HIDDEN: the ion cannon (port) and light dual MG (starboard) are the boom barrels the '
    'sprite draws; the swarmer launcher is the dark bay on the spine at WS 003, no visible gun.',
    'Forward of X 14 the model follows the master (centred lance, symmetric booms), which matches neither vanilla '
    'sprite exactly; see source.sprite_choice.',
]

ZB = .45  # gun-boom axis height


def build(kit):
    A, S, D = kit.armour, kit.steel, kit.dark
    # Swept delta: one plan prism, gun-boom roots to the wingtip plates.
    port = [(14.4, 0), (14.4, 3.0), (13.6, 4.0), (13.6, 8.0), (10.6, 8.8), (9.2, 10.0), (5.2, 10.2), (3.4, 12.1),
            (-3.0, 12.1), (-3.9, 11.0), (-3.9, 3.5), (-2.0, 3.1), (-2.0, 0)]
    plan = port + mirror_y(port)[1:-1]
    wing = prism(kit, 'Hull delta wing', plan, section(-.35, .85, .25, .35), A)
    fus = section_loft(kit, 'Hull central fuselage', [
        (17.6, 1.0, .0, 1.2, .5, .4), (15.0, 2.3, -.5, 1.7, .8, .6), (9.0, 2.7, -.9, 1.9, 1.0, .8),
        (2.0, 2.6, -.9, 1.9, 1.0, .8), (-3.0, 1.9, -.6, 1.5, .8, .6), (-5.2, 1.1, -.3, .9, .4, .3)], A)
    spine = section_loft(kit, 'Hull lance spine', [
        (29.4, .5, .65, 1.15, .2, .2), (28.6, 1.0, .35, 1.5, .4, .3), (26.0, 1.25, .2, 1.65, .5, .4),
        (18.0, 1.5, .1, 1.95, .6, .5), (12.0, 1.8, .0, 2.05, .7, .5)], A)
    kit.hull += [wing, fus, spine]
    # The swarmer bay: a dark recess along the spine at WS 003.
    K.boolean(spine, kit.box('Temporary missile bay cut', (16.8, 0, 2.0), (4.6, .9, .5), D, 0))
    kit.box('Hull missile bay recess', (16.8, 0, 1.72), (4.5, .85, .1), D, 0)
    # Canopy on the fuselage, over the widest part of the body.
    section_loft(kit, 'Canopy glass', [
        (9.8, .45, 1.6, 1.95, .2, .1), (8.6, 1.05, 1.6, 2.85, .5, .1), (5.6, 1.3, 1.6, 3.4, .65, .1),
        (3.6, 1.15, 1.6, 3.15, .6, .1), (2.6, .7, 1.6, 2.35, .35, .1)], kit.glass, bevel=.04)
    for s, side in ((1, 'port'), (-1, 'starboard')):
        # Gun boom: a wide rear housing on the wing root, a narrower forward housing, a steel shroud and barrel.
        rear = section_loft(kit, f'Hull {side} gun boom rear housing', [
            (15.8, 1.5, -.35, 1.25, .5, .4), (14.6, 2.0, -.45, 1.35, .6, .45), (9.5, 2.0, -.45, 1.35, .6, .45),
            (8.5, 1.4, -.3, 1.0, .4, .3)], A, cy=s*6.0)
        front = section_loft(kit, f'Hull {side} gun boom forward housing', [
            (20.0, 1.3, -.2, 1.15, .45, .35), (15.0, 1.45, -.3, 1.25, .5, .4)], A, cy=s*5.5)
        kit.hull += [rear, front]
        section_loft(kit, f'Weapon {side} gun shroud', [
            (22.9, 1.0, -.25, 1.05, .4, .35), (22.0, 1.3, -.3, 1.15, .45, .4), (19.6, 1.3, -.3, 1.15, .45, .4)],
            S, cy=s*5.5)
        kit.cylinder(f'Weapon {side} gun barrel', (23.55, s*5.5, ZB), .5, 1.6, kit.bright, 'X', 12, .03)
        # Wingtip plate.
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
        else:
            ym = 5.5 if y > 0 else -5.5
            kit.slot_anchor(slot, (x, y, ZB), muzzle=(24.35, ym, ZB))
