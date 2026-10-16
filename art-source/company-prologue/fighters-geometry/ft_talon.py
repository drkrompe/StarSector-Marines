"""Talon interceptor (pass 2: plausible detail on the pass-1 blockout).

Read off talon.png (24x24, .ship centre (13, 10)) for the silhouette and
fighter-upres/talon/master-v3.png for forms. Sprite column c covers model
Y 12-c..13-c and row r covers X 13-r..14-r, so the sprite's centre line is
Y = 1, not Y = 0.

The Talon is asymmetric forward and symmetric aft:
- port (Y 1..6, X 2..12): the gun nacelle, a long armoured housing whose
  stepped barrel runs out to X 13 on Y = 3.5;
- starboard (Y -6..0, centred Y = -3, X 2..11): a shorter rounded cockpit
  pod with the canopy on its own centre line;
- between them, X 2..10, the void the sprite leaves open (Y 0..1);
- aft (X -9..2): a broad body, symmetric about Y = 1, with two drive drums
  under the pods' armoured shoulders, a steel centre spine between them that
  runs out to the tail, and thin faceted wing plates out to Y 12 / -10.
"""
import math
import bpy
import fg_kit as K
from fg_kit import prism, section, section_loft, loft_x, octagon, at

NAME = 'Talon'
HULL_ID = 'talon'
SHIP = 'data/hulls/talon.ship'
SPRITE = 'graphics/ships/talon/talon.png'
MASTER = 'art-source/aircraft/fighter-upres/talon/master-v3.png'
SOURCE_NOTES = {'variant': 'data/variants/fighters/talon_Interceptor.variant (WS 001 vulcan, WS 003 swarmer_fighter; '
                           'WS 002 SYSTEM unfitted)'}
# Sprite armour red (median of the red pixels, linear) and its cockpit cyan.
KIT = {'armour': (.36, .12, .09), 'armour_name': 'Talon armour red', 'glass': (.33, .73, .72)}

SLOTS = [
    {'id': 'WS 001', 'location': (0, 4), 'angle': 0, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'BALLISTIC'},
    {'id': 'WS 002', 'location': (-8, 2), 'angle': 180, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'SYSTEM'},
    {'id': 'WS 003', 'location': (6, 1), 'angle': 0, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'MISSILE'},
]
ENGINES = [(-9, 6, 8), (-9, -4, 8)]  # exactly as in talon.ship (x, y, width)

METRES_PER_UNIT = 0.55
SCALE = {
    'summary': 'LOW_TECH single-seat interceptor, crew 1; offset vulcan nacelle to port, cockpit pod to starboard, twin drive drums',
    'method': ('Sized to sit below the Broadsword, as the game draws it, with a cockpit that still works. The '
               'canopy is 3.9 x 1.6 units on the sprite\'s cyan patch. At 0.55 m/unit that is a 2.15 x 0.88 m '
               'bubble, small like a Folland Gnat\'s, and a seated 1.8 m pilot clears it by 0.08 m (0.04 m at '
               'the 0.50 floor of the range, where the helmet is nearly at the glass). The 22-unit length and span come out at 12.1 m, a compact '
               'interceptor between a Gnat and an F-5.'),
    'range': [0.50, 0.62],
    'cross_checks_m': {'length': 12.1, 'span': 12.1, 'canopy_length': 2.15, 'canopy_width_at_sill': 0.88,
                       'exposed_gun_barrel': 1.0, 'drive_drum_width': 2.75,
                       'real_aircraft': 'Folland Gnat 9.0 m, F-5E 14.5 m, F-16 15.1 m long'},
}
PILOTS = [(6.4, -3.0)]
NOTES = [
    'Engine slots (-9, 6) and (-9, -4) are already symmetric about the sprite centre line Y = 1; the drive drums, '
    'bells and exhaust anchors sit on the slots exactly. The sprite paints the drive drums at Y 4..9 and -7..-2, '
    'half a unit outboard of the slots (also symmetric about Y = 1), so the drums follow the sprite and each bell '
    'sits half a unit inboard of its drum centre.',
    'WS 001 (0, 4) is the vulcan. The sprite draws its barrel in column 9 (Y 3..4), so the barrel is modelled on '
    'Y = 3.5 and its muzzle empty sits at the barrel tip there, half a unit off the integer slot, as with a slot '
    'straddling a pixel centre line. The anchor itself is at the slot.',
    'WS 003 (6, 1) swarmers and WS 002 (-8, 2) system are HIDDEN with nothing drawn on the sprite or master: '
    'anchors only, on the gun nacelle\'s inboard wall and the spine\'s tail face.',
    'The forward asymmetry is the sprite\'s: the gun nacelle is 5 px wide and longer, the cockpit pod 6 px wide '
    'and shorter, with an open slot between them. The master is not registered closely enough forward to place '
    'the pods (its cockpit pod is 3 units short), so lengths come from the sprite and forms from the master.',
    'Pass 2 (detail read off master-v3, sized in metres at 0.55 m/unit): the vulcan gets the master\'s red '
    'octagonal shroud collar, a banded barrel jacket and a six-barrel cluster in the muzzle ring, and a steel '
    'breech and feed housing on the nacelle behind the collar (the master\'s grey block). The hidden swarmer bay '
    'at WS 003 is a closed door outlined in the nacelle\'s inboard wall. The cockpit pod carries the master\'s '
    'grey nose strip with a sensor blister, a canopy sill, windscreen arch and bow, and a grey dorsal strip with '
    'a vent behind the glass. The armoured shoulders over the drums carry the master\'s intake grilles, the steel '
    'spine its slots, centre grille and a blade antenna, and the wings small outboard vents. Each drive drum has '
    'two collar ribs ahead of the lip and a centre body with stator vanes in the bell. Panel lines (5 cm wide, '
    '2.5 cm deep) split the nose caps, shoulders, wings and underside into plates.',
]

# Pass-2 close-up cameras: (name, view direction from the target, target in units, frame width in metres).
CLOSEUPS = [
    ('gun-detail', (1.0, 1.1, .7), (10.6, 3.5, .5), 4.2),
    ('cockpit-detail', (1.0, -.9, 1.3), (6.2, -3.0, 1.2), 4.0),
    ('aft-detail', (-.35, .5, 1.5), (-3.2, 1.0, .9), 7.5),
    ('engine-detail', (-1.0, .75, .4), (-8.4, 1.0, -.5), 7.5),
]

GUN_Y, GUN_Z = 3.5, .35
POD_Y = -3.0
# Gun nacelle stations: (x, y inboard, y outboard, keel z, deck z, top chamfer, keel chamfer), nose to tail.
GUN_NACELLE = [
    (12.0, 2.1, 4.0, -.2, .85, .35, .3),
    (11.0, 2.0, 4.6, -.55, 1.05, .45, .35),
    (10.0, 2.0, 5.1, -.8, 1.2, .5, .4),
    (8.1, 2.0, 6.0, -1.05, 1.35, .6, .5),
    (7.9, 1.1, 6.0, -1.1, 1.4, .6, .5),
    (3.05, 1.1, 6.0, -1.1, 1.4, .6, .5),
    (2.95, 1.1, 7.0, -1.1, 1.4, .6, .5),
    (-5.4, 1.1, 7.0, -1.1, 1.4, .6, .5),
]
# Cockpit pod: (x, half width, keel z, deck z, top chamfer, keel chamfer) about Y = POD_Y.
COCKPIT_POD = [
    (10.25, .35, -.15, .3, .12, .12),
    (9.8, .95, -.5, .7, .35, .3),
    (9.0, 1.6, -.9, 1.0, .55, .5),
    (8.5, 2.1, -1.1, 1.15, .65, .55),
    (8.0, 2.7, -1.25, 1.22, .75, .6),
    (7.4, 3.0, -1.35, 1.25, .75, .6),
    (-5.4, 3.0, -1.35, 1.25, .75, .6),
]
# Canopy: (x, half width, height above the pod deck), nose to tail.
CANOPY = [(8.3, .25, .15), (7.8, .7, .55), (6.8, .8, .85), (5.4, .78, .8), (4.4, .45, .4)]
# Drive drum about each engine slot: (x, half width, keel z, deck z, top chamfer, keel chamfer).
DRUM = [
    (-3.0, 2.5, -2.3, 1.15, 1.0, 1.0),
    (-8.95, 2.5, -2.3, 1.15, 1.0, 1.0),
]
ENGINE_Z = -.58
LIP_R, BELL_R = 1.65, 1.38
X_MOUTH = -9.0


def band_loft(kit, name, stations, mat, bevel=.05):
    """Octagonal loft whose Y edges vary independently (an off-centre, one-sided taper)."""
    return loft_x(kit, name, [(x, octagon(y0, y1, z0, z1, ct, cb)) for x, y0, y1, z0, z1, ct, cb in stations],
                  mat, bevel)


def mirror_about_centre(plan):
    """Reflect a port plan to starboard about the sprite centre line Y = 1."""
    return [(x, 2-y) for x, y in reversed(plan)]


def canopy(kit):
    st = []
    for x, w, h in CANOPY:
        deck = at(COCKPIT_POD, x)[3]
        ct = min(w*.55, h*.8)
        st.append((x, octagon(POD_Y-w, POD_Y+w, deck-.25, deck+h, ct, .05)))
    return loft_x(kit, 'Canopy glass', st, kit.glass, .03)


def build(kit):
    # Aft body core, symmetric about Y = 1.
    core = prism(kit, 'Hull aft body', [(2.0, 8.0), (1.0, 8.6), (-8.0, 8.6), (-8.0, -6.6), (1.0, -6.6), (2.0, -6.0)],
                 section(-1.5, .9, .5, .45), kit.armour, .06)
    # Thin wing plates flaring out on the sprite's stepped diagonals.
    port_plan = [(1.6, 7.0), (1.6, 7.9), (-2.5, 12.0), (-6.6, 12.0), (-7.3, 10.1), (-8.0, 10.0), (-8.0, 7.0)]
    wing_p = prism(kit, 'Wing port plate', port_plan, section(-.7, .35, .25, .3), kit.armour, .05)
    wing_s = prism(kit, 'Wing starboard plate', mirror_about_centre(port_plan), section(-.7, .35, .25, .3),
                   kit.armour, .05)
    # Gun nacelle to port and cockpit pod to starboard; both run aft as the drums' armoured shoulders.
    gun = band_loft(kit, 'Hull gun nacelle', GUN_NACELLE, kit.armour, .06)
    pod = section_loft(kit, 'Hull cockpit pod', COCKPIT_POD, kit.armour, cy=POD_Y, bevel=.06)
    spine = prism(kit, 'Hull centre spine', [(1.9, 1.95), (-6.0, 2.9), (-9.0, 2.9), (-9.0, -.9), (-6.0, -.9), (1.9, .05)],
                  section(-1.2, 1.2, .35, .4), kit.steel, .05)
    kit.hull += [core, wing_p, wing_s, gun, pod, spine]
    canopy(kit)

    # Vulcan: stepped barrel out of the nacelle's nose, on the sprite's barrel column.
    kit.cylinder('Weapon WS 001 vulcan housing', (11.6, GUN_Y, GUN_Z), .72, 1.4, kit.steel, 'X', 16, .05)
    kit.cylinder('Weapon WS 001 vulcan barrel', (12.3, GUN_Y, GUN_Z), .5, 1.4, kit.steel, 'X', 16, .04)
    kit.cylinder('Weapon WS 001 vulcan muzzle ring', (12.88, GUN_Y, GUN_Z), .42, .25, kit.bright, 'X', 16, .03)
    for s in SLOTS:
        x, y = s['location']
        if s['id'] == 'WS 001':
            kit.slot_anchor(s, (x, y, GUN_Z), muzzle=(13.0, GUN_Y, GUN_Z))
        elif s['id'] == 'WS 003':
            kit.slot_anchor(s, (x, y, .2))  # hidden swarmer bay in the nacelle's inboard wall
        else:
            kit.slot_anchor(s, (x, y, 0))  # hidden system mount in the spine's tail face

    # Drive drums under the shoulders, one aft-facing bell each.
    for i, (ex, ey, w) in enumerate(ENGINES, 1):
        side = 'port' if ey > 1 else 'starboard'
        # The sprite paints the drums half a pixel outboard of the slots (Y 4..9, -7..-2).
        drum = section_loft(kit, f'Engine {i} {side} drive drum', DRUM, kit.steel, cy=ey+(ey-1)*.1, bevel=.06)
        kit.hull.append(drum)
        kit.well(drum, X_MOUTH, ey, ENGINE_Z, BELL_R)
        kit.engine(i, X_MOUTH, ey, ENGINE_Z, LIP_R, BELL_R)
    if kit.detail:
        detail(kit)


def detail(kit):
    kit.mark_detail()
    ob = bpy.data.objects
    gun, pod, core = ob['Hull gun nacelle'], ob['Hull cockpit pod'], ob['Hull aft body']
    A, S, D, B = kit.armour, kit.steel, kit.dark, kit.bright
    u = kit.u

    # Vulcan: the master's red octagonal shroud collar where the barrel leaves the nacelle, a banded
    # barrel jacket, six barrels in the muzzle ring, and a breech and feed housing behind the collar.
    r = .98
    loft_x(kit, 'Weapon WS 001 vulcan shroud collar',
           [(x, octagon(GUN_Y-r, GUN_Y+r, GUN_Z-r, GUN_Z+r, r*.42)) for x in (11.35, 10.55)], A, u(.02))
    for k, x in enumerate((11.95, 12.45), 1):
        kit.xcyl(f'Weapon WS 001 vulcan jacket band {k}', x-u(.05), x+u(.05), GUN_Y, GUN_Z, .3, B, 16)
    for k in range(6):
        a = 2*math.pi*k/6
        kit.xcyl(f'Weapon WS 001 vulcan barrel {k+1}', 12.7, 13.02, GUN_Y+u(.11)*math.cos(a),
                 GUN_Z+u(.11)*math.sin(a), .04, D, 8, 0)
    kit.xcyl('Weapon WS 001 vulcan spindle', 12.7, 13.03, GUN_Y, GUN_Z, .03, B, 8, 0)
    breech = kit.seated('Weapon WS 001 vulcan breech housing', 9.3, 3.55, (1.1, .78, .26), S, 0, .04, .03)
    kit.hull.append(breech)  # so its vent seats on it
    kit.grille('Weapon WS 001 vulcan breech vent', 9.35, 3.55, .6, .5, 4)
    kit.hull.remove(breech)
    kit.seated('Weapon WS 001 vulcan feed cover', 8.2, 2.75, (.9, .26, .16), D, 0, .03, .02)
    # WS 003 swarmers: a closed bay door in the nacelle's inboard wall, over the slot.
    door = kit.outline(6.0, .15, 1.5, .6, .04, .4, 1.7, axis='Y')

    # Cockpit pod: the master's grey nose strip with a sensor blister, the canopy frame, and the grey
    # dorsal strip behind the glass with its vent.
    kit.fairing('Hull cockpit pod sensor fairing', 9.55, POD_Y, .55, .26, .13, S)
    kit.seated('Hull cockpit pod dorsal vent', 2.0, POD_Y, (.34, .12, .03), D, 0, .01, 0)
    kit.skin(pod, 'Hull cockpit pod nose strip', kit.region(8.75, 10.4, POD_Y-.36, POD_Y+.36, .2, 5), .03, S)
    kit.skin(pod, 'Hull cockpit pod dorsal strip', kit.region(.6, 4.45, POD_Y-.34, POD_Y+.34, .2, 5), .03, S)
    glass = ob['Canopy glass']
    deck = at(COCKPIT_POD, 6.5)[3]
    kit.skin(glass, 'Canopy frame sill', kit.region(4.2, 8.45, POD_Y-1.2, POD_Y+1.2, -3, deck+u(.09)), .045, S)
    kit.skin(glass, 'Canopy frame windscreen arch', kit.band_slab(7.55, .07), .04, S)
    kit.skin(glass, 'Canopy frame bow', kit.band_slab(5.55, .06), .04, S)

    # Armoured shoulders over the drums: the master's intake grilles; small vents on the wing plates.
    for side, y in (('port', 4.7), ('starboard', 2-4.7)):
        kit.grille(f'Hull {side} shoulder intake', -3.4, y, 1.35, .55, 5)
    for side, y in (('port', 9.6), ('starboard', 2-9.6)):
        kit.seated(f'Wing {side} plate vent', -3.8, y, (.32, .2, .03), D, 0, .01, 0)
    # Steel spine: the master's slot, centre grille and aft slot, and a blade antenna.
    kit.seated('Hull centre spine fore slot', .7, 1.0, (.16, .7, .03), D, 0, .01, 0)
    kit.grille('Hull centre spine grille', -2.6, 1.0, 1.7, .9, 6)
    kit.seated('Hull centre spine aft slot', -7.6, 1.0, (.34, .16, .03), D, 0, .01, 0)
    kit.blade('Hull centre spine antenna', -5.6, 1.0, .32, .5, S)

    # Drives: two collar ribs ahead of the lip, and a centre body with stator vanes in each bell.
    w, top = .05, .2
    for i, (ex, ey, _) in enumerate(ENGINES, 1):
        side = 'port' if ey > 1 else 'starboard'
        drum = ob[f'Engine {i} {side} drive drum']
        for k, x in enumerate((-7.0, -7.75), 1):
            kit.skin(drum, f'Engine {i} collar rib {k}', kit.band_slab(x, .14), .05, S)
        kit.drive_core(i, X_MOUTH, ey, ENGINE_Z, BELL_R)
        # The aft body's tail face stood inside the bell, ahead of the throat, so the drive read as red
        # armour: carry the bell's well through it as well.
        kit.well(core, X_MOUTH, ey, ENGINE_Z, BELL_R)
        kit.engrave(drum, [kit.band_slab(-4.6, w, z0=-1.0), kit.band_slab(-6.2, w)])

    # Panel lines: nose caps, shoulders and plates as the master splits them.
    kit.engrave(gun, [kit.band_slab(10.1, w), kit.band_slab(7.9, w, z0=top), kit.band_slab(3.0, w, z0=top),
                      *kit.lines([(7.8, 4.75), (3.1, 4.75)], w, top),
                      *kit.lines([(2.9, 5.7), (-5.3, 5.7)], w, top), *door])
    kit.engrave(pod, [kit.band_slab(8.85, w), kit.band_slab(3.2, w, z0=top), kit.band_slab(-.6, w, z0=top),
                      *kit.lines([(7.3, POD_Y-1.75), (-5.3, POD_Y-1.75)], w, top),
                      *kit.lines([(4.2, POD_Y+1.75), (-.5, POD_Y+1.75)], w, top)])
    kit.engrave(core, [*kit.lines([(1.0, 8.6), (1.0, 7.0)], w), *kit.lines([(1.0, -6.6), (1.0, -5.0)], w),
                       *kit.outline(-2.5, 1.0, 2.2, 1.6, w, -3, -1.0),
                       *kit.lines([(-6.0, 3.6), (-6.0, -1.6)], w, -3, -1.0)])
    for side in ('port', 'starboard'):
        sgn = 1 if side == 'port' else -1
        kit.engrave(ob[f'Wing {side} plate'], kit.lines([(-.9, 1+sgn*8.4), (-5.4, 1+sgn*10.9)], w) +
                    kit.lines([(-2.4, 1+sgn*7.2), (-2.4, 1+sgn*10.6)], w, -.2))
