"""Longbow support bomber: blockout module for build_fighter.py.

longbow.ship: 34 x 35 sprite, centre (17, 14). A single-seat HIGH_TECH
strike craft built as an X: a short central fuselage with the cockpit
dome, two forward arms carrying unequal launchers (a pointed emitter
housing to port, a boxed missile rack to starboard), two aft arms carrying
the drive pods, and a steel web filling the crotches between the arms.

Silhouette from the vanilla sprite (longbow_intg.png); forms read off the
up-res master-v2, which restores the unequal forward launchers. The master
paints a longer nose and wider, more outboard pods than the sprite; where
they disagree the sprite wins (short nose, twin forward sensor prongs at
Y +-4.5, pods centred on Y +-9).
"""
import math
import bpy
import fg_kit as K
from fg_kit import prism, section, mirror_y, section_loft

NAME = 'Longbow'
HULL_ID = 'longbow'
SHIP = 'data/hulls/longbow.ship'
SPRITE = 'graphics/ships/longbow_intg.png'
MASTER = 'art-source/aircraft/fighter-upres/longbow/master-v2.png'
SOURCE_NOTES = {'variant': 'data/variants/fighters/longbow_Support.variant (WS 001 pdburst, WS 002 sabot_fighter)'}

# Blue-grey armour read off the sprite's light plates (sRGB ~150, 165, 178).
KIT = {'armour': (.29, .36, .42), 'armour_name': 'Longbow blue-grey armour', 'glass': (.30, .55, .68)}

SLOTS = [  # exactly as in longbow.ship
    {'id': 'WS 001', 'location': (14, 12), 'angle': 0, 'arc': 90, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'ENERGY'},
    {'id': 'WS 002', 'location': (9, -11), 'angle': 0, 'arc': 5, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'MISSILE'},
]
ENGINES = [(-13, 9, 8), (-13, -8, 8)]  # (x, y, width) exactly as in longbow.ship

# Render-only nozzle offsets from the up-res manifest, native px (+X right,
# +Y nose): the master paints its nozzles at slot + offset, i.e. model
# (slot x + oy, slot y - ox) = (-12.5, 11.5) and (-12.5, -11).
NOZZLE_OFFSETS = [(-2.5, .5), (3, .5)]
NOZZLES = [(x+oy, y-ox) for (x, y, _), (ox, oy) in zip(ENGINES, NOZZLE_OFFSETS)]
POD_Y = 9.0  # sprite pods span Y 5..13 each side

METRES_PER_UNIT = 0.45
SCALE = {
    'summary': 'Single-seat support bomber: X-frame, emitter housing to port, missile rack to starboard, twin drive pods',
    'method': ('Crew 1 (ship_data min/max crew 1). The cockpit dome is about 5.9 units long and 2.5 wide at the '
               'sill; a single-seat canopy of 2.6-3.0 m by 1.1 m gives 0.44-0.5 m/unit. At 0.45 the seated '
               '1.8 m proxy clears the glass, each 8-unit drive pod is 3.6 m across around a 1.4 m nozzle '
               '(the class of a single fighter turbofan), the starboard rack is a 1.5 m-wide missile cell and '
               'the airframe is 15 m long by 14.4 m across the arms, a light strike aircraft. Below 0.4 the '
               'canopy shrinks under 2.4 m and the 1.8 m pilot loses head room; above 0.55 the fuselage passes '
               '18 m on a fighter-class bomber launched in numbers from a carrier.'),
    'range': [0.4, 0.55],
    'cross_checks_m': {
        'length_overall': 15.1,
        'span_across_arms': 14.4,
        'canopy_length': 2.65,
        'canopy_sill_width': 1.1,
        'drive_pod_width': 3.6,
        'nozzle_bell_diameter': 1.2,
        'starboard_missile_rack_width': 1.5,
        'A-4 Skyhawk (length, span)': [12.2, 8.4],
        'F-16 length': 15.0,
        'Su-25 (length, span)': [15.5, 14.4],
    },
}
PILOTS = [(9.2, 0.0)]

NOTES = [
    'The sprite is symmetric about Y = 0 except for its launchers, which are unequal on purpose (master v2 '
    'restores them): a pointed emitter housing on the port arm, a boxed rack on the starboard arm.',
    'WS 001 (pdburst, HIDDEN) anchors exactly at (14, 12) in the port housing; the housing\'s pointed '
    'emitter runs forward on the sprite\'s spike axis Y 11.3 with its muzzle at the tip (20.4, 11.3).',
    'WS 002 (sabot_fighter, HIDDEN) anchors exactly at (9, -11) inside the starboard rack; its muzzle is '
    'on the rack\'s open front face (13.1, -11).',
    'Engine slots (-13, 9) and (-13, -8) straddle the even-width sprite\'s centre line; the drive pods are '
    'modelled symmetric on the sprite\'s pods at Y +-9 (8 units wide = slot width).',
    'The visible bells sit at the master\'s painted nozzles, slot + renderEngineOffsetsNativePx = '
    '(-12.5, 11.5) and (-12.5, -11), outboard in each pod and recessed 0.5 into its aft face (-13); '
    'exhaust anchors are at those bells.',
    'The master\'s long pointed nose and outboard pods are not followed: the sprite ends the fuselage at '
    'X 13 between two forward sensor prongs and centres its pods on Y +-9.',
    'Pass 2 (detail read off master-v2, sized in metres at 0.45 m/unit): the forward prongs carry sensor blisters '
    'and the nose the master\'s dark centre strip and lamp; the canopy dome a sill, windscreen arch and bow. Behind '
    'it are the master\'s square dorsal sensor housing, a blade antenna and the slatted aft grille between the aft '
    'arms; the aft arms carry the master\'s small vents. The port emitter gets coil bands and a collar where it '
    'leaves the housing, and the housing the master\'s window. The starboard rack shows four sabot tubes (bright '
    'rims, dark bores) in its open mouth, with hold-down latches on top. Each pod has the master\'s top slot, a '
    'lamp, two collar ribs and a centre body with stator vanes. Panel lines split fuselage, arms, housing and '
    'plates.',
]

# Pass-2 close-up cameras: (name, view direction from the target, target in units, frame width in metres).
CLOSEUPS = [
    ('nose-detail', (1.0, .6, .8), (13.6, 0.0, 1.2), 6.0),
    ('cockpit-detail', (.8, -1.0, 1.2), (9.4, 0.0, 2.6), 4.2),
    ('emitter-detail', (1.0, 1.0, .65), (15.6, 11.6, .5), 5.4),
    ('rack-detail', (1.0, -1.0, .55), (12.2, -11.0, .3), 4.8),
    ('engine-detail', (-1.0, .75, .5), (-12.2, 9.6, 0.0), 5.6),
]


def beam(p0, p1, hw):
    """Plan of a straight arm of half width hw from p0 to p1."""
    dx, dy = p1[0]-p0[0], p1[1]-p0[1]
    n = math.hypot(dx, dy)
    nx, ny = -dy/n*hw, dx/n*hw
    return [(p0[0]+nx, p0[1]+ny), (p0[0]-nx, p0[1]-ny), (p1[0]-nx, p1[1]-ny), (p1[0]+nx, p1[1]+ny)]


# --- Fuselage: (x, half width, keel z, deck z, top chamfer, keel chamfer). ---
FUSELAGE = [
    (13.3, .35, -.4, .6, .15, .15),
    (12.4, 2.0, -1.1, 1.6, .7, .5),
    (11.0, 3.1, -1.7, 2.2, 1.0, .8),
    (8.0, 3.6, -2.0, 2.4, 1.2, .9),
    (4.0, 3.6, -2.0, 2.2, 1.2, .9),
    (0.0, 3.4, -1.9, 1.9, 1.1, .9),
    (-6.9, 3.0, -1.6, 1.4, .9, .8),
]

# Canopy dome on the fuselage deck: (x, half width, sill z, crown z, chamfer, sill chamfer).
CANOPY = [
    (12.3, .35, 1.55, 1.8, .12, .05),
    (11.3, 1.0, 1.95, 2.85, .45, .05),
    (9.5, 1.25, 2.15, 3.5, .6, .05),
    (7.6, 1.2, 2.15, 3.35, .55, .05),
    (6.4, .7, 2.15, 2.55, .3, .05),
]

# Port emitter spike, on the sprite's axis Y 11.5.
SPIKE_Y, SPIKE_Z = 11.3, .25
SPIKE = [
    (20.4, .12, SPIKE_Z-.12, SPIKE_Z+.12, .04, .04),
    (19.0, .7, SPIKE_Z-.6, SPIKE_Z+.6, .25, .25),
    (17.0, 1.7, SPIKE_Z-1.1, SPIKE_Z+1.1, .55, .55),
    (16.0, 2.05, SPIKE_Z-1.2, SPIKE_Z+1.2, .6, .6),
    (13.5, 2.05, SPIKE_Z-1.2, SPIKE_Z+1.2, .6, .6),
]

# Drive pods: centred on Y +-POD_Y.
POD = [
    (-6.4, 3.0, -1.5, 1.4, .8, .8),
    (-8.6, 3.4, -2.0, 1.9, 1.0, 1.0),
    (-9.1, 4.0, -2.1, 2.0, 1.0, 1.0),
    (-12.6, 4.0, -2.1, 2.0, 1.0, 1.0),
    (-13.0, 3.8, -1.95, 1.85, .95, .95),
]


def build(kit):
    arm = section(-.95, .9, .3, .35)
    web = section(-.75, .35, .2, .15)

    fus = section_loft(kit, 'Hull fuselage', FUSELAGE, kit.armour, xs=[13.3, 12.4, 11.0, 8.0, 4.0, 0.0, -6.9])
    kit.hull.append(fus)
    section_loft(kit, 'Canopy glass dome', CANOPY, kit.glass, bevel=.03)

    # Steel webs in the crotches between the arms (the sprite is solid there).
    fore_web = prism(kit, 'Hull fore web', [(12.0, 13.0), (12.0, -5.0), (4.0, -5.0), (3.2, 2.0),
                                            (4.2, 9.8), (5.6, 13.0)], web, kit.steel)
    aft_half = [(-1.0, 10.0), (-2.6, 10.2), (-5.4, 12.0), (-9.0, 12.0), (-9.0, 3.0), (-8.1, 2.9),
                (-8.0, 2.0), (-7.0, 1.9)]
    aft_web = prism(kit, 'Hull aft web', aft_half+mirror_y(aft_half), web, kit.steel)
    kit.hull += [fore_web, aft_web]

    # The X: two forward arms to the launchers, two aft arms to the pods.
    for side, s in (('port', 1), ('starboard', -1)):
        fa = prism(kit, f'Wing {side} fore arm', beam((1.5, s*2.2), (11.6, s*11.4), 2.9), arm, kit.armour)
        aa = prism(kit, f'Wing {side} aft arm', beam((-0.8, s*1.5), (-8.6, s*POD_Y), 2.5), arm, kit.armour)
        kit.hull += [fa, aa]

    # Forward sensor prongs flanking the short nose.
    prong = section(-.25, .55, .1, .15)
    kit.hull.append(prism(kit, 'Hull port prong', [(18.3, 4.5), (17.0, 5.05), (15.2, 6.05), (13.0, 6.1),
                                                   (12.0, 7.1), (10.5, 7.1), (10.5, 5.0), (12.2, 4.9),
                                                   (14.0, 3.95), (17.0, 3.95)],
                          prong, kit.armour))
    kit.hull.append(prism(kit, 'Hull starboard prong', [(17.4, -4.5), (16.5, -3.95), (14.0, -3.95),
                                                        (12.0, -4.1), (10.5, -4.4), (10.5, -6.0),
                                                        (14.0, -5.1), (16.5, -5.0)],
                          prong, kit.armour))

    # Port launcher: armoured housing with a forward fin and the pointed emitter.
    housing = section(-1.2, 1.35, .35, .4)
    kit.hull.append(prism(kit, 'Weapon WS 001 housing', [(16.0, 8.4), (16.0, 14.0), (14.9, 14.3), (14.9, 16.0),
                                                         (10.2, 16.0), (9.6, 14.2), (8.5, 13.0), (6.0, 13.0),
                                                         (6.0, 8.4)],
                          housing, kit.armour))
    kit.hull.append(prism(kit, 'Weapon WS 001 ventral fin', [(6.0, 12.3), (6.0, 14.0), (5.0, 15.05), (0.0, 15.0),
                                                             (0.0, 14.0), (2.0, 13.9), (2.6, 13.0),
                                                             (4.0, 12.0)],
                          section(-.9, .4, .15, .15), kit.armour))
    section_loft(kit, 'Weapon WS 001 emitter', SPIKE, kit.bright, cy=SPIKE_Y, bevel=.03)

    # Starboard launcher: an open missile cell between an inner and an outer plate.
    plate = section(-1.45, 1.55, .35, .4)
    kit.hull.append(prism(kit, 'Weapon WS 002 inner plate', [(14.0, -4.4), (14.2, -5.9), (15.1, -6.1),
                                                             (15.1, -7.0), (14.0, -7.2), (14.0, -9.4),
                                                             (5.0, -9.4), (5.0, -4.6)],
                          plate, kit.armour))
    kit.hull.append(prism(kit, 'Weapon WS 002 outer plate', [(13.9, -12.6), (14.0, -13.0), (14.0, -15.0),
                                                             (15.1, -15.1), (15.1, -16.0), (5.0, -16.0),
                                                             (5.0, -12.6)], plate, kit.armour))
    rack = kit.box('Weapon WS 002 missile rack', (9.05, -11.0, .05), (8.1, 3.4, 2.7), kit.steel, .05)
    kit.hull.append(rack)
    kit.box('Weapon WS 002 cell mouth', (13.08, -11.0, .05), (.1, 2.8, 2.1), kit.dark, 0)
    for k, y in enumerate((-9.6, -12.5, -15.5), 1):
        kit.box(f'Weapon WS 002 rack rail {k}', (4.15, y, -.25), (2.3, .9, .8), kit.steel, .05)

    # Drive pods, with the bells at the master's painted nozzles.
    pods = []
    for i, s in ((1, 1), (2, -1)):
        side = 'port' if s > 0 else 'starboard'
        pod = section_loft(kit, f'Engine {i} {side} drive pod', POD, kit.armour, cy=s*POD_Y)
        kit.hull.append(pod)
        pods.append(pod)
    lip_r, z = 1.55, -.05
    for i, (pod, (nx, ny)) in enumerate(zip(pods, NOZZLES), 1):
        kit.well(pod, nx, ny, z, lip_r*.82)
        kit.engine(i, nx, ny, z, lip_r)

    a1 = SLOTS[0]
    kit.slot_anchor(a1, (*a1['location'], SPIKE_Z), muzzle=(20.4, SPIKE_Y, SPIKE_Z))
    a2 = SLOTS[1]
    kit.slot_anchor(a2, (*a2['location'], .05), muzzle=(13.13, -11.0, .05))
    if kit.detail:
        detail(kit)


def detail(kit):
    kit.mark_detail()
    ob = bpy.data.objects
    S, D, B, G = kit.steel, kit.dark, kit.bright, kit.glass
    u = kit.u
    w, top = .05, .3
    fus = ob['Hull fuselage']

    # Nose: the master's dark centre strip and lamp; sensor blisters on the forward prongs.
    kit.skin(fus, 'Hull nose strip', kit.region(12.35, 13.4, -.55, .55, .3, 6), .03, S)
    kit.seated('Hull nose lamp', 12.75, 0, (.16, .3, .03), G, 0, -.02, 0)  # on the strip
    for side, x, y in (('port', 16.3, 4.55), ('starboard', 15.7, -4.5)):
        kit.fairing(f'Hull {side} prong sensor fairing', x, y, .7, .28, .14, S)
    # Canopy frame on the dome.
    glass = ob['Canopy glass dome']
    kit.skin(glass, 'Canopy frame sill', kit.region(6.2, 12.5, -1.6, 1.6, -3, 2.15+u(.1)), .05, S)
    kit.skin(glass, 'Canopy frame windscreen arch', kit.band_slab(10.8, .08), .045, S)
    kit.skin(glass, 'Canopy frame bow', kit.band_slab(8.4, .07), .045, S)
    # Behind the canopy: the master's square dorsal sensor housing, an antenna, the aft grille.
    kit.seated('Hull dorsal sensor housing', 3.6, 0, (.42, .42, .16), S, 0, .03, .02)
    kit.blade('Hull dorsal antenna', -1.4, 1.3, .32, .5, S)
    kit.grille('Hull aft grille', -4.7, 0, 1.3, .95, 6)
    kit.engrave(fus, [kit.band_slab(6.1, w, z0=top), kit.band_slab(.6, w, z0=top),
                      *kit.lines([(5.9, 1.9), (-6.6, 1.7)], w, top), *kit.lines([(5.9, -1.9), (-6.6, -1.7)], w, top),
                      *kit.outline(2.0, 0, 3.0, 1.8, w, -4, -1.4)])

    # Arms: the master's small vents on the aft arms, seams across all four.
    for side, s in (('port', 1), ('starboard', -1)):
        for part, p0, p1 in (('fore', (1.5, s*2.2), (11.6, s*11.4)), ('aft', (-0.8, s*1.5), (-8.6, s*POD_Y))):
            dx, dy = p1[0]-p0[0], p1[1]-p0[1]
            n = math.hypot(dx, dy)
            q = (-dy/n*3.2, dx/n*3.2)
            at_t = lambda t: (p0[0]+dx*t, p0[1]+dy*t)
            seams = []
            for t in ((.38, .72) if part == 'fore' else (.4,)):
                cx, cy = at_t(t)
                seams.append(kit.line_slab((cx-q[0], cy-q[1]), (cx+q[0], cy+q[1]), w, top))
            if part == 'aft':
                vx, vy = at_t(.62)
                kit.grille(f'Wing {side} aft arm vent', vx, vy, .7, .4, 3, math.degrees(math.atan2(dy, dx)))
            kit.engrave(ob[f'Wing {side} {part} arm'], seams)

    # Port emitter: coil bands, a collar where it leaves the housing, the housing's window and seams.
    spike = ob['Weapon WS 001 emitter']
    kit.skin(spike, 'Weapon WS 001 emitter collar', kit.band_slab(16.05, .16), .05, S)
    for k, x in enumerate((17.3, 18.4), 1):
        kit.skin(spike, f'Weapon WS 001 emitter coil {k}', kit.band_slab(x, .08), .03, D)
    housing = ob['Weapon WS 001 housing']
    kit.seated('Weapon WS 001 housing window', 12.6, 14.9, (.4, .14, .03), G, 0, .01, 0)
    kit.engrave(housing, [kit.band_slab(9.4, w, z0=top), *kit.lines([(15.8, 10.0), (6.2, 10.0)], w, top)])

    # Starboard rack: four sabot tubes in the open mouth, hold-down latches on the rack's top.
    for k, (dy, dz) in enumerate(((.62, .48), (-.62, .48), (.62, -.48), (-.62, -.48)), 1):
        kit.xcyl(f'Weapon WS 002 tube rim {k}', 13.0, 13.2, -11.0+dy, .05+dz, .22, B, 16)
        kit.xcyl(f'Weapon WS 002 tube bore {k}', 13.05, 13.22, -11.0+dy, .05+dz, .16, D, 16, 0)
    rack = ob['Weapon WS 002 missile rack']
    kit.hull.append(rack)
    for k, x in enumerate((6.2, 11.6), 1):
        kit.seated(f'Weapon WS 002 rack latch {k}', x, -11.0, (.3, .9, .1), S, 0, .02, .01)
    kit.hull.remove(rack)
    for name in ('Weapon WS 002 inner plate', 'Weapon WS 002 outer plate'):
        kit.engrave(ob[name], [kit.band_slab(9.0, w, z0=top), kit.band_slab(12.6, w, z0=top)])

    # Pods: the master's top slot and lamp, two collar ribs, and the drive core in each bell.
    for i, (s, (nx, ny)) in enumerate(((1, NOZZLES[0]), (-1, NOZZLES[1])), 1):
        side = 'port' if s > 0 else 'starboard'
        pod = ob[f'Engine {i} {side} drive pod']
        kit.grille(f'Engine {i} pod top slot', -8.4, s*POD_Y, .45, 1.0, 4, 90)
        kit.seated(f'Engine {i} pod lamp', -11.8, s*POD_Y, (.14, .34, .03), G, 0, .01, 0)
        for k, x in enumerate((-10.9, -11.6), 1):
            kit.skin(pod, f'Engine {i} collar rib {k}', kit.band_slab(x, .12), .05, S)
        kit.drive_core(i, nx, ny, -.05, 1.55*.82)
        kit.engrave(pod, kit.band_slab(-9.6, w))
