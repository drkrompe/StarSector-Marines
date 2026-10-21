"""Borer utility drone: the Shepherd's built-in wing (borer_wing, four craft), for build_fighter.py.

drone_borer.ship: 18 x 16 sprite (graphics/ships/drones/drone_borer.png), centre (9, 8),
LOW_TECH, crew 0. One HIDDEN small energy slot at the centre (WS 001, the variant's mining
laser, the "primary cutting beam" of the Shepherd's description) and three LOW_TECH drives on
the tail: a 6-wide main drive on the centre line and two 4-wide drives at Y +-6, splayed 20
degrees outboard (angles 160 and 200).

Read off the sprite (silhouette) and the battle-map up-res master (forms; session/borer-uprez,
art-source/drones/borer.master.png, a reading reference only): a central orange spine whose
nose carries the grey cutting head; two broad orange armour pods ahead of the wings; two grey
drive pods behind them on axles out of the spine, carrying the side drives; struts between
each armour pod and its drive pod. The sprite's four grey points ahead of the armour pods are
grab tines (a mining drone holds the rock it cuts).

Scale: fitted to the Shepherd's servicing cradle, the one physical constraint on the Borer (see
SCALE). Every detail size below is in metres through kit.u / S.u; positions stay in sprite
units about the .ship centre.

Rounds are built by the segment rule (ship_detail_kit.segments) and smooth-shaded; the driver
keeps that shading (SHADING = 'as-built'). Armour shells are chamfered plate prisms with a
one-segment bevel, the guide's plate language at drone size.
"""
import math
import sys
from pathlib import Path
import bpy
from mathutils import Vector
import fg_kit as K
from fg_kit import prism, section, section_loft, mirror_y, boolean

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'ship-detail-kit'))
import ship_detail_kit as sdk  # noqa: E402

NAME = 'Borer'
HULL_ID = 'drone_borer'
SHIP = 'data/hulls/drone_borer.ship'
SPRITE = 'graphics/ships/drones/drone_borer.png'
MASTER = 'session/borer-uprez:art-source/drones/borer.master.png (battle-map remaster; forms only)'
SOURCE_NOTES = {'variant': 'data/variants/drones/drone_borer.variant (WS 001 mininglaser, LINKED)',
                'wing': 'data/hulls/wing_data.csv borer_wing: 4 craft, INTERCEPTOR role, range 6, built into shepherd.ship',
                'ship_data': 'Borer drone_borer: hitpoints 200, armour 25, speed 200, mass 5, crew 0'}

# Burnt orange armour (the Shepherd's drive-pod orange, Ko Combine), read off the sprite's
# mid plates (sRGB ~230, 110, 60).
KIT = {'armour': (.62, .16, .045), 'armour_name': 'Borer orange armour', 'glass': (.06, .045, .02)}
SHADING = 'as-built'   # build_fighter keeps each part's shading: rounds smooth, plate flat

SLOTS = [  # exactly as in drone_borer.ship
    {'id': 'WS 001', 'location': (0, 0), 'angle': 0, 'arc': 30, 'mount': 'HIDDEN', 'size': 'SMALL', 'type': 'ENERGY'},
]
ENGINES = [(-6, 0, 6), (-6, 6, 4), (-6, -6, 4)]   # (x, y, width) exactly as in drone_borer.ship
ENGINE_ANGLES = [180, 160, 200]                  # drone_borer.ship engineSlots angle (exhaust heading)

METRES_PER_UNIT = 0.25
S = sdk.Scale(METRES_PER_UNIT)
SCALE = {
    'summary': 'Crewless mining and utility drone; sized to the Shepherd drone tender\'s servicing cradle',
    'method': ('No crew (ship_data crew 0), so no cockpit sets the scale, and the craft has to live on its '
               'tender: the Shepherd\'s open bay carries it on servicing cradles at the three WS 003 launch '
               'stations (shepherd-geometry sh_dronebay.py). The cradle deck is 4.84 x 4.37 m with corner '
               'chocks 4.2 m apart fore-aft and 3.7 m across, and the bay was laid out for an assumed '
               '4.4 x 4.0 x 1.8 m Borer. The sprite is wider than long (16 x 14 px opaque), so at 0.25 m/unit '
               'the drone is 4.0 m across the pods (the assumed span) and 3.5 m long, and stands inside the '
               'chocks fore-aft with 0.35 m to spare each end. Its parts agree: a 0.6 m main nozzle and '
               '0.43 m side nozzles for a 5-tonne-class craft (ship_data mass 5), a 0.24 m emitter barrel, '
               'a 1.4 m spine. Below 0.22 the spine is too shallow for a reactor and cutting laser; above '
               '0.27 it no longer fits between the chocks.'),
    'range': [0.22, 0.27],
    'sprite_disagreement': ('Measured at the game\'s own pixel scale the Borer is much bigger: at the Shepherd\'s '
                            '0.78 m/unit it would be 11 x 12.5 m, at the fighters\' 0.45-0.55 m/unit about '
                            '7 x 8 m (the sprite is 16 px across against the Talon\'s 22). Neither fits a cradle. '
                            'The model keeps the cradle; nothing was resized on either side.'),
    'size_readings_m': {
        '_doc': ('Length of the Borer against the Shepherd (67.1 m) by each picture of the pair. The owner '
                 'decides; nothing is resized until then.'),
        'model_as_built': 3.6,
        'fleet_combat_sprites': 11.7,
        'ground_battle_2d': 1.9,
        'how': ('fleet combat draws both sprites at one pixel scale: 16 px against 92 px is 17%. The ground '
                'battle draws the Shepherd at WarshipScale.hullCells (92 px x 0.045 x HULL_PRESENCE 6 = 24.8 '
                'cells) and the drone at TenderDroneArt.cells, which multiplies by WarshipScale.cells without '
                'the hull presence (16 px x 0.045 = 0.72 cells): 2.9%, although its Javadoc says hull scale. '
                'As built the drone is 5.4%.'),
    },
    'cross_checks_m': {
        'length_overall': 3.5,
        'span_across_pods': 4.0,
        'cradle_deck_m': [4.84, 4.37],
        'cradle_chocks_clear_m': [4.21, 3.74],
        'assumed_by_the_bay_m': [4.4, 4.0, 1.8],
        'main_nozzle_diameter': 0.6,
        'side_nozzle_diameter': 0.43,
        'talon_m (lineup)': [12.1, 12.1, 2.5],
    },
}
PILOTS = []   # crewless

# Cradle seat: the four pads stand on the Shepherd's launch cradle, the .ship centre over the
# station's centre, so the cradle's charging contact (0.94 m square, 0.06 m proud) meets the
# belly socket. 'Borer cradle seat' marks the pad plane under the centre.
SOLE_Z = -5.75
CONTACT_M = .0624            # the cradle's charging contact stands 0.08 Shepherd units (0.78 m/u) proud
# Round 2 (owner: no piston legs on round pads): the drone stands on two skid rails, and a centre
# docking keel carries the charging socket and the clamp lugs the cradle grabs. Each rail bears on
# two bolted wear shoes, which are what touches the deck.
FEET = [f'Skid {s} {e} shoe' for s in ('port', 'starboard') for e in ('fore', 'aft')]
LEG_PARTS = ['Skid ', 'Docking keel']

NOTES = [
    'Crewless drone (crew 0): no canopy or seat; scale from the tender\'s cradle (SCALE).',
    'Sprite symmetric about Y = 0 (even width); WS 001 HIDDEN at (0, 0) carries its anchor there and its muzzle '
    'at the cutting head\'s lens on the nose, where the beam leaves the hull.',
    'Engines: main drive on the spine\'s tail, side drives in the drive pods\' slanted aft faces, each face '
    'square to its slot angle (160 / 200) so the bell points along the game\'s exhaust heading. Bell mouths sit '
    'on the slot XY for the side drives; the main bell is on the tail face 0.4 units aft of its slot, where '
    'the sprite\'s tail ends. Exhaust anchors are at the mouths, +X down the exhaust.',
    'The four grey points on the sprite\'s top row are grab tines on the armour pods\' noses.',
    'Round 2 (owner: a flat chip): the spine and pods are a hull mass 2.3 m deep overall, keel to deck '
    '1.5 m amidships, with deep chamfered bellies; four ventral lift thrusters (one under each armour pod '
    'and drive pod, `Lift <side> <fore|aft> thruster anchor`, +X straight down) hold the hover.',
    'The mining laser hangs under the nose on a sling: a yoke head under the nose cap, two hanger arms to '
    'trunnions on the casing\'s flanks and a rear link to the spine\'s belly; the emitter reaches past the '
    'nose, so the plan view still shows the sprite\'s grey nose.',
    'The drive pods hang off the spine on transverse axles (the dark drums between spine and pod on the '
    'master) and are braced to the armour pods by a strut and a damper; nothing folds, so there is no '
    'deployed/stowed knob. The span (4.0 m) already fits the cradle.',
    'Docking, no legs: two skid rails under the pods, each on two struts, bear on bolted wear shoes (the '
    'parked feet); clamp lugs on the skids and on the centre docking keel are what the cradle grabs, and the '
    'keel\'s sole carries the charging socket 12 mm over the cradle\'s contact.',
    'No lights layer exists for this hull; the only emissives are the two work-lamp lenses and the beacon '
    'lens, named as lamps.',
]

# Pass-2 close-up cameras: (name, view direction from the target, target in units, frame width in metres).
CLOSEUPS = [
    ('head-detail', (1.0, .8, .25), (5.0, 0.0, -3.4), 2.0),
    ('pod-detail', (.9, 1.0, .8), (2.4, 4.6, .4), 2.0),
    ('drive-detail', (-1.0, .8, .5), (-5.4, 4.6, -.6), 2.0),
    ('belly-detail', (.6, .8, -.7), (-1.0, 2.0, -4.6), 3.0),
]

# --- spine: (x, half width, keel z, deck z, top chamfer, keel chamfer) ---
# Round 2 (owner: "flat chip"): the spine is the drone's main mass, 1.5 m keel to deck amidships,
# with a deep chamfered belly the slung laser and the docking keel hang from.
SPINE = [
    (4.6, .85, -2.3, 1.25, .42, .62),
    (3.2, 1.0, -2.9, 1.7, .5, .8),
    (1.0, 1.45, -3.45, 2.15, .62, 1.0),
    (0.0, 1.95, -3.7, 2.4, .75, 1.15),
    (-3.6, 2.05, -3.7, 2.45, .8, 1.15),
    (-5.3, 2.4, -3.2, 2.2, .8, 1.0),     # the sprite's tail flare (+-3 at X -5..-6): the main drive housing
    (-6.0, 2.05, -2.6, 1.85, .65, .8),
    (-6.45, 1.25, -1.95, 1.4, .42, .55),
]
# --- armour pod (port; starboard mirrored): plan and section ---
# Every plan corner turns 45 degrees (or 90+): a plate break, never a coarse round (check_detail).
POD_PLAN = [(4.9, 2.3), (4.9, 5.95), (3.75, 7.15), (-.2, 7.15), (-.75, 6.6), (-.75, 2.6), (-.05, 1.9),
            (4.5, 1.9)]
POD_Z = (-3.0, 1.95)                               # 1.24 m deep: a hull pod, not a plate
POD_KEEL_C = 1.05                                  # the deep keel chamfer that turns the pod's belly in
CHEEK_Y, CHEEK_W = 7.55, .45                       # grey outer cheek, centre and half width
# --- drive pod (port) ---
# The sprite pulls the drive pods in to Y 6 forward of X -4, widens them to 8 aft and leaves a notch
# between pod and spine at X -5..-6; the side drive's bell is canted 20 degrees out of the flat aft face.
DRIVE_PLAN = [(-1.0, 2.35), (-1.0, 5.4), (-1.6, 6.0), (-3.9, 6.0), (-5.8, 7.9), (-6.6, 7.9), (-7.0, 7.5),
              (-7.0, 3.0), (-6.1, 3.0), (-6.1, 4.8), (-5.0, 4.8), (-5.0, 2.35)]
DRIVE_Z = (-3.35, 1.25)
AXLE = (-2.5, -.55)                                 # transverse axle (x, z) out of the spine
GIMBAL = (-4.6, 1.35)                               # gimbal drum on the pod's back (x, z)
# --- engines: (mouth xyz, angle, lip radius m, bell radius m) ---
ENGINE_SET = [((-6.45, 0.0, -.1), 180, .27, .22),
              ((-6.92, 6.0, -.7), 160, .215, .175),
              ((-6.92, -6.0, -.7), 200, .215, .175)]
# --- the slung mining laser (round 2: hung under the nose, not set into it) ---
TOOL_Z = -4.35                                      # laser axis: below the spine's keel, on its sling
LENS_X = 7.1                                        # front of the emitter lens
LASER_X = (2.7, 6.55)                               # laser casing, aft to fore
LASER_HW, LASER_HH = .9, .72                        # casing half width and half height
TRUNNION_X = 4.55                                   # the yoke's trunnions on the casing's flanks
# --- lift thrusters (ventral, the hover the owner asked for): (name, x, |y|, host, ring radius m) ---
LIFTS = [('fore', 2.55, 5.05, 'Pod {side} armour shell', .2), ('aft', -2.15, 4.3, 'Drive {side} pod housing', .17)]
# --- docking hard points: two skids and a centre keel the cradle's clamps grab ---
SKID_Y = 3.85
SKID_X = (-4.15, 2.25)
SKID_STRUTS = [(1.1, 'Pod {side} armour shell'), (-3.5, 'Drive {side} pod housing')]
KEEL_X = (-3.9, 1.7)


def _mats(kit):
    kit.grey = kit.material('Borer grey machinery', (.30, .30, .29), .55, .45)
    kit.lens = sdk.material('Borer lamp lens', (1.0, .86, .62), 0, .2, emission=(1.0, .85, .6), strength=6.0)
    kit.beacon = sdk.material('Borer beacon lens', (1.0, .45, .08), 0, .2, emission=(1.0, .42, .06), strength=5.0)
    kit.emitter = sdk.material('Borer emitter glass', (.09, .05, .02), 0, .06)


PLATE_BEVEL_DEG = 50.0   # bevel only edges sharper than this: the 45-degree plate chamfers stay crisp lines


def _plate_bevel(ob, kind):
    return sdk.bevel(ob, S.bevel(kind), angle=PLATE_BEVEL_DEG)


def _smooth_loft(kit, name, table, mat, step=.25):
    """section_loft through a station table resampled every `step` units with a smoothstep
    between stations, so a planform change bends in steps of a few degrees, not one crease."""
    rows = sorted(table, key=lambda r: -r[0])
    xs = []
    for a, b in zip(rows, rows[1:]):
        n = max(1, round((a[0]-b[0])/step))
        xs += [a[0]-(a[0]-b[0])*k/n for k in range(n)]
    xs.append(rows[-1][0])
    out = []
    for x in xs:
        for a, b in zip(rows, rows[1:]):
            if b[0] <= x <= a[0]:
                t = (a[0]-x)/(a[0]-b[0])
                t = t*t*(3-2*t)
                out.append((x,)+tuple(av+(bv-av)*t for av, bv in zip(a[1:], b[1:])))
                break
    return section_loft(kit, name, out, mat, bevel=0)


def _round(ob):
    return sdk.smooth(ob, 35.0)


def _lathe(kit, name, prof, origin, axis, mat, r_max, bevel_m=0):
    """A smooth solid of revolution with the segment rule's count for its largest radius (units)."""
    ob = sdk.revolve_dir(kit, name, prof, origin, axis, mat, n=S.n(r_max))
    if bevel_m:
        sdk.bevel(ob, S.u(bevel_m))
    return _round(ob)


def _drum(kit, name, p0, p1, r, mat, rim=True):
    """A drum between two points with rolled rims (a chamfer ring each end)."""
    a, b = Vector(p0), Vector(p1)
    L = (b-a).length
    c = min(r*.18, L*.2) if rim else 0
    prof = [(0, 0), (0, r-c), (c, r), (L-c, r), (L, r-c), (L, 0)]
    return _lathe(kit, name, prof, a, b-a, mat, r)


def _bar(kit, name, p0, p1, w_m, h_m, mat, side=(0, 0, 1)):
    v, f = [], []
    sdk.beam(v, f, p0, p1, S.u(w_m), S.u(h_m), side)
    ob = sdk.solid(kit, name, v, f, mat)
    sdk.bevel(ob, S.u(min(w_m, h_m)*.18))
    return ob


def _shell(kit, name, plan, z0, z1, kc, sc, mat, bevel='fitting'):
    ob = prism(kit, name, plan, section(z0, z1, kc, sc), mat, 0)
    _plate_bevel(ob, bevel)
    return ob


def _engine(kit, i, mouth, angle, lip_m, bell_m, host):
    """An angled exhaust, named as on the Second Wind: Engine {i} mouth lip, aft-facing bell, dark
    throat, and Engine {i} <side> exhaust anchor on the mouth plane, local +X down the exhaust."""
    a = math.radians(angle)
    d = Vector((math.cos(a), math.sin(a), 0))
    m = Vector(mouth)
    lip, br = S.u(lip_m), S.u(bell_m)
    depth, L = S.u(.32), S.u(.09)
    wall = S.u(.018)
    well = sdk.revolve_dir(kit, 'Temporary drive well', [(-depth-.1, 0), (-depth-.1, br+wall*1.5), (2, br+wall*1.5), (2, 0)],
                           m, d, kit.dark, n=S.n(br))
    boolean(host, well)
    _lathe(kit, f'Engine {i} mouth lip',
           [(-L, br+wall*.6), (-L, lip), (L*.15, lip), (L*.45, lip-S.u(.012)), (L*.45, br+wall*.6)], m, d, kit.bright, lip)
    _lathe(kit, f'Engine {i} aft-facing bell',
           [(-depth, br*.62), (0, br), (0, br+wall), (-depth, br*.62+wall)], m, d, kit.dark, br)
    _lathe(kit, f'Engine {i} dark throat',
           [(-depth-.03, 0), (-depth-.03, br*.66), (-depth+.03, br*.66), (-depth+.03, 0)], m, d, kit.dark, br)
    # Centre body and stator vanes on the throat.
    _lathe(kit, f'Engine {i} centre body',
           [(-depth+.02, 0), (-depth+.02, br*.3), (-depth*.55, br*.24), (-depth*.25, br*.08), (-depth*.2, 0)],
           m, d, kit.steel, br*.3)
    _, u, w = sdk.frame(d)
    for k in range(6):
        t = 2*math.pi*(k+.5)/6
        r0, r1 = br*.26, br*.66
        p = m-d*(depth*.75)
        kit.rod(f'Engine {i} stator vane {k+1}', p+(u*math.cos(t)+w*math.sin(t))*r0,
                p+(u*math.cos(t)+w*math.sin(t))*r1, .008, kit.steel, 8)
    side = 'port' if m.y > 1e-6 else ('starboard' if m.y < -1e-6 else 'centre')
    anchor = kit.empty(f'Engine {i} {side} exhaust anchor', tuple(m), angle)
    anchor['radius_m'] = bell_m
    anchor['bell_radius'] = br
    return anchor


def build(kit):
    _mats(kit)
    # Spine: the reactor and laser body, orange armour on a plate section.
    spine = _smooth_loft(kit, 'Hull spine', SPINE, kit.armour)
    _plate_bevel(spine, 'fitting')
    kit.hull.append(spine)

    # Armour pods: chamfered plate prisms, the grey outer cheek and the root drum into the spine.
    for side, sg in (('port', 1), ('starboard', -1)):
        plan = POD_PLAN if sg > 0 else mirror_y(POD_PLAN)
        pod = _shell(kit, f'Pod {side} armour shell', plan, POD_Z[0], POD_Z[1], POD_KEEL_C, .7, kit.armour)
        kit.hull.append(pod)
        cy = sg*CHEEK_Y
        # A plate prism: plan corners turn 45 degrees, one keel and one shoulder chamfer (round 2:
        # the deeper cheek as a loft read as a coarse round at its tapered ends).
        w, c = CHEEK_W, .4
        cplan = [(5.95, cy-w+c), (5.95, cy+w-c), (5.95-c, cy+w), (1.05+c, cy+w), (1.05, cy+w-c), (1.05, cy-w+c),
                 (1.05+c, cy-w), (5.95-c, cy-w)]
        cheek = _shell(kit, f'Pod {side} outer cheek', cplan, -1.85, 1.6, .3, .32, kit.grey, 'small')
        kit.hull.append(cheek)
        _drum(kit, f'Pod {side} root drum', (2.6, sg*1.15, .35), (2.6, sg*2.2, .35), .8, kit.grey)
        # Mounting webs under the gaps between spine and pods (the sprite's dark columns there).
        for nm, lo, hi in ((f'Pod {side} root web', (-1.1, .7, -2.4), (4.5, 2.1, -.15)),
                           (f'Drive {side} axle web', (-4.8, 1.75, -2.6), (-.6, 2.6, -.55))):
            lo, hi = (lo[0], min(sg*lo[1], sg*hi[1]), lo[2]), (hi[0], max(sg*lo[1], sg*hi[1]), hi[2])
            v, f = [], []
            sdk.block(v, f, lo, hi)
            sdk.solid(kit, nm, v, f, kit.dark)

        # Drive pod on its axle, with the gimbal drum on its back.
        dplan = DRIVE_PLAN if sg > 0 else mirror_y(DRIVE_PLAN)
        drive = _shell(kit, f'Drive {side} pod housing', dplan, DRIVE_Z[0], DRIVE_Z[1], .55, .6, kit.grey)
        kit.hull.append(drive)
        _drum(kit, f'Drive {side} axle', (AXLE[0], sg*1.7, AXLE[1]), (AXLE[0], sg*3.25, AXLE[1]), .72, kit.steel)
        _drum(kit, f'Drive {side} gimbal drum', (GIMBAL[0], sg*3.55, GIMBAL[1]), (GIMBAL[0], sg*5.75, GIMBAL[1]),
              .95, kit.grey)
        drv = drive
        i = 2 if sg > 0 else 3
        mouth, ang, lip, bell = ENGINE_SET[i-1]
        _engine(kit, i, mouth, ang, lip, bell, drv)

    # Main drive on the spine's tail.
    mouth, ang, lip, bell = ENGINE_SET[0]
    _engine(kit, 1, mouth, ang, lip, bell, spine)

    # Nose cap on the spine's bow, where the laser's sling hangs from.
    st = [(5.35, K.octagon(-.62, .62, -1.55, .55, .22)),
          (4.95, K.octagon(-.8, .8, -1.95, .95, .3)),
          (4.3, K.octagon(-.92, .92, -2.25, 1.15, .34))]
    cap = K.loft_x(kit, 'Hull nose cap', st, kit.grey, 0)
    _plate_bevel(cap, 'small')
    kit.hull.append(cap)
    # The mining laser, slung under the nose: a casing hung on a yoke's trunnions and a rear link,
    # its emitter reaching past the nose, so the cut happens below the drone and ahead of it.
    hw, hh, z = LASER_HW, LASER_HH, TOOL_Z
    st = [(LASER_X[1], K.octagon(-hw*.78, hw*.78, z-hh*.78, z+hh*.78, .24)),
          (LASER_X[1]-.45, K.octagon(-hw, hw, z-hh, z+hh, .3)),
          (LASER_X[0]+.4, K.octagon(-hw, hw, z-hh, z+hh, .3)),
          (LASER_X[0], K.octagon(-hw*.8, hw*.8, z-hh*.8, z+hh*.8, .25))]
    head = K.loft_x(kit, 'Tool head housing', st, kit.grey, 0)
    _plate_bevel(head, 'small')
    kit.hull.append(head)
    rb = S.u(.12)
    # The casing's bore: the emitter barrel sits in it, its rim just proud of the casing's face.
    boolean(head, sdk.revolve_dir(kit, 'Temporary bore', [(0, 0), (0, rb*1.18), (2, rb*1.18), (2, 0)],
                                  (6.15, 0, TOOL_Z), (1, 0, 0), kit.dark, n=S.n(rb*1.4)))
    _sling(kit)
    lb = LENS_X-6.0-S.u(.055)      # barrel from inside the housing to just behind the lens
    _lathe(kit, 'Tool head emitter barrel',
           [(0, 0), (0, rb*1.1), (lb*.15, rb*1.12), (lb*.32, rb*1.12), (lb*.38, rb*.95), (lb*.72, rb*.92),
            (lb*.75, rb*1.08), (lb*.88, rb*1.08), (lb*.91, rb*.92), (lb, rb*.92), (lb, 0)],
           (6.0, 0, TOOL_Z), (1, 0, 0), kit.grey, rb*1.3)
    _lathe(kit, 'Tool head emitter rim',
           [(-S.u(.065), rb*.62), (-S.u(.065), rb*1.04), (S.u(.012), rb*1.04), (S.u(.012), rb*.62)],
           (LENS_X, 0, TOOL_Z), (1, 0, 0), kit.bright, rb*1.04)
    _lathe(kit, 'Tool head emitter glass',
           [(-S.u(.05), 0), (-S.u(.05), rb*.66), (-S.u(.02), rb*.62), (-S.u(.012), 0)],
           (LENS_X, 0, TOOL_Z), (1, 0, 0), kit.emitter, rb*.66)
    kit.slot_anchor(SLOTS[0], (0, 0, 0), muzzle=(LENS_X, 0, TOOL_Z))

    # Grab tines: two per armour pod nose, the sprite's top-row points.
    for side, sg in (('port', 1), ('starboard', -1)):
        for tag, ty in (('outer', 6.45), ('inner', 4.5)):
            y = sg*ty
            _drum(kit, f'Pod {side} tine {tag} knuckle', (4.7, y-.45, -.35), (4.7, y+.45, -.35), .42, kit.steel)
            st = [(4.6, K.octagon(y-.46, y+.46, -.8, .15, .14)),
                  (6.3, K.octagon(y-.42, y+.42, -.68, .02, .12)),
                  (7.0, K.octagon(y-.4, y+.4, -.52, -.16, .1))]
            t = K.loft_x(kit, f'Pod {side} tine {tag}', st, kit.bright, 0)
            sdk.bevel(t, S.bevel('small')*.5, angle=PLATE_BEVEL_DEG)

    # Ventral lift thrusters: the hover. Docking gear: skids and the centre keel.
    for side, sg in (('port', 1), ('starboard', -1)):
        for tag, x, y, host, r_m in LIFTS:
            _lift(kit, f'Lift {side} {tag}', (x, sg*y), bpy.data.objects[host.format(side=side)], r_m)
        _skid(kit, side, sg)
    _docking_keel(kit)

    if kit.detail:
        detail(kit)


# --- Pass 2 -------------------------------------------------------------------------------

def _struts(kit, side, sg):
    """The strut and damper bracing an armour pod to its drive pod, and their knuckles."""
    a, b = Vector((-.1, sg*4.15, -.6)), Vector((-1.5, sg*3.8, -.5))
    _drum(kit, f'Arm {side} strut', a, b, .36, kit.steel)
    for tag, p in (('upper', a), ('lower', b)):
        _drum(kit, f'Arm {side} strut {tag} knuckle', p+Vector((0, -.42, 0)), p+Vector((0, .42, 0)), .44, kit.grey)
    c, d = Vector((-.45, sg*6.0, -.35)), Vector((-1.95, sg*6.0, -.35))
    _drum(kit, f'Arm {side} damper cylinder', c, c.lerp(d, .62), .3, kit.grey)
    _drum(kit, f'Arm {side} damper rod', c.lerp(d, .55), d, .16, kit.bright)
    _drum(kit, f'Arm {side} damper eye', d+Vector((0, -.3, 0)), d+Vector((0, .3, 0)), .3, kit.steel)
    _drum(kit, f'Arm {side} knuckle post', (0.0, sg*4.6, -1.35), (0.0, sg*4.6, -.2), .42, kit.grey)


def _block(kit, name, lo, hi, mat, bevel_kind='small'):
    v, f = [], []
    sdk.block(v, f, lo, hi)
    ob = sdk.solid(kit, name, v, f, mat)
    sdk.bevel(ob, S.bevel(bevel_kind)*.6)
    return ob


def _sling(kit):
    """The laser's sling: a yoke head under the nose cap, two hanger arms down to trunnions on the
    casing's flanks, and a rear link from the spine's belly to the casing's back, with clevises."""
    x, z = TRUNNION_X, TOOL_Z
    _block(kit, 'Sling yoke head', (x-.38, -1.3, -2.55), (x+.38, 1.3, -1.95), kit.steel)
    for side, sg in (('port', 1), ('starboard', -1)):
        top, low = Vector((x, sg*1.12, -2.45)), Vector((x, sg*1.12, z))
        _bar(kit, f'Sling {side} hanger arm', top, low, .1, .16, kit.steel, (1, 0, 0))
        _drum(kit, f'Sling {side} trunnion', (x, sg*(LASER_HW-.05), z), (x, sg*(LASER_HW+.42), z), .34, kit.grey)
        _drum(kit, f'Sling {side} trunnion cap', (x, sg*(LASER_HW+.42), z), (x, sg*(LASER_HW+.52), z), .22, kit.bright)
    a, b = Vector((2.95, 0, -2.95)), Vector((3.25, 0, z+LASER_HH-.05))
    _bar(kit, 'Sling rear link', a, b, .12, .1, kit.steel, (0, 1, 0))
    for tag, p in (('upper', a), ('lower', b)):
        _drum(kit, f'Sling rear link {tag} clevis', p+Vector((0, -.42, 0)), p+Vector((0, .42, 0)), .28, kit.grey)


def _lift(kit, name, xy, host, r_m):
    """A ventral lift thruster set into a pod's belly: a well, a bright lip ring, a dark bell and
    throat, guide vanes across the mouth, and `<name> thruster anchor` (+X straight down)."""
    x, y = xy
    z = min((host.matrix_world @ v.co).z for v in host.data.vertices)     # the flat belly under the chamfer
    m, d = Vector((x, y, z)), Vector((0, 0, -1))
    br = S.u(r_m)
    lip = br+S.u(.035)
    depth, wall = S.u(.22), S.u(.016)
    well = sdk.revolve_dir(kit, 'Temporary lift well', [(-depth-.1, 0), (-depth-.1, br+wall*1.5), (2, br+wall*1.5), (2, 0)],
                           m, d, kit.dark, n=S.n(br))
    boolean(host, well)
    _lathe(kit, f'{name} lift ring', [(-S.u(.03), br+wall*.6), (-S.u(.03), lip), (S.u(.012), lip),
                                       (S.u(.02), lip-S.u(.01)), (S.u(.02), br+wall*.6)], m, d, kit.bright, lip)
    _lathe(kit, f'{name} lift bell', [(-depth, br*.7), (0, br), (0, br+wall), (-depth, br*.7+wall)], m, d, kit.dark, br)
    _lathe(kit, f'{name} lift throat', [(-depth-.03, 0), (-depth-.03, br*.74), (-depth+.03, br*.74), (-depth+.03, 0)],
           m, d, kit.dark, br)
    for k, t in enumerate((-.5, 0, .5), 1):
        q = m+Vector((t*br, 0, S.u(.05)))
        half = br*math.sqrt(1-t*t)*.92
        _bar(kit, f'{name} lift vane {k}', q+Vector((0, -half, 0)), q+Vector((0, half, 0)), .012, .05, kit.steel, (1, 0, 0))
    ob = bpy.data.objects.new(f'{name} thruster anchor', None)
    ob.empty_display_type, ob.empty_display_size = 'SINGLE_ARROW', 1.0
    ob.location, ob.rotation_euler = m, (0, math.pi/2, 0)
    bpy.context.collection.objects.link(ob)
    kit.anchors.append(ob)
    ob['radius_m'] = r_m
    ob['kind'] = 'lift'


def _skid(kit, side, sg):
    """A skid rail under the pods on two struts, with bolted wear shoes at its ends (the feet) and
    clamp lugs on its outer face that the cradle's clamps close on."""
    y = sg*SKID_Y
    x0, x1 = SKID_X
    shoe_t = S.u(.05)
    rz0, rz1 = SOLE_Z+shoe_t, SOLE_Z+shoe_t+S.u(.13)
    hw = S.u(.08)
    st = [(x1+.55, K.octagon(y-hw*.8, y+hw*.8, rz1-.12, rz1, .08)),     # toe turned up at the bow
          (x1, K.octagon(y-hw, y+hw, rz0, rz1, .1)),
          (x0, K.octagon(y-hw, y+hw, rz0, rz1, .1)),
          (x0-.45, K.octagon(y-hw*.8, y+hw*.8, rz1-.12, rz1, .08))]
    rail = K.loft_x(kit, f'Skid {side} rail', st, kit.steel, 0)
    _plate_bevel(rail, 'small')
    for end, x in (('fore', x1-.55), ('aft', x0+.55)):
        L = S.u(.2)
        _block(kit, f'Skid {side} {end} shoe', (x-L, y-hw*1.05, SOLE_Z), (x+L, y+hw*1.05, rz0+.02), kit.dark)
        for k, dx in enumerate((-L*.6, L*.6), 1):
            kit.bolt(f'Skid {side} {end} shoe bolt {k}', x+dx, y, S.u(.012))
    for k, (x, host) in enumerate(SKID_STRUTS, 1):
        host = bpy.data.objects[host.format(side=side)]
        top_z = min((host.matrix_world @ v.co).z for v in host.data.vertices)+.25
        _bar(kit, f'Skid {side} strut {k}', (x, y, rz1-.05), (x, y, top_z), .12, .16, kit.steel, (1, 0, 0))
        _block(kit, f'Skid {side} strut {k} shoe plate', (x-.5, y-.45, top_z-.35), (x+.5, y+.45, top_z-.05), kit.grey)
        _drum(kit, f'Skid {side} clamp lug {k}', (x+(.75 if k == 1 else -.75), y+sg*hw*.9, (rz0+rz1)/2),
              (x+(.75 if k == 1 else -.75), y+sg*(hw+S.u(.07)), (rz0+rz1)/2), .3, kit.bright)


def _docking_keel(kit):
    """The centre docking keel under the spine: a chamfered box beam with clamp lugs both sides and
    the charging socket in its sole, which stands 12 mm over the cradle's charging contact."""
    x0, x1 = KEEL_X
    z_face = SOLE_Z+S.u(CONTACT_M)+S.u(.012)
    hw = .55
    st = [(x1, K.octagon(-hw*.8, hw*.8, z_face+.25, -3.2, .2)),
          (x1-.4, K.octagon(-hw, hw, z_face, -3.2, .25)),
          (x0+.4, K.octagon(-hw, hw, z_face, -3.2, .25)),
          (x0, K.octagon(-hw*.8, hw*.8, z_face+.25, -3.2, .2))]
    keel = K.loft_x(kit, 'Docking keel', st, kit.grey, 0)
    _plate_bevel(keel, 'small')
    for k, x in enumerate((x1-1.0, x0+1.0), 1):
        for side, sg in (('port', 1), ('starboard', -1)):
            _drum(kit, f'Docking keel {side} clamp lug {k}', (x, sg*hw, z_face+.45), (x, sg*(hw+S.u(.08)), z_face+.45),
                  .32, kit.bright)
    _belly_socket(kit)


def _belly_socket(kit):
    """The charging socket the cradle's contact meets: a boss under the spine, a bright contact face."""
    z_face = SOLE_Z+S.u(CONTACT_M)+S.u(.012)
    r = S.u(.30)
    t_top = S.u(.06)               # a flush boss in the docking keel's sole
    _lathe(kit, 'Belly charging socket housing',
           [(0, 0), (0, r), (S.u(.03), r), (S.u(.05), r*.9), (S.u(.05), r*.82), (t_top, r*.82), (t_top, 0)],
           (0, 0, z_face), (0, 0, 1), kit.grey, r)
    _lathe(kit, 'Belly charging socket contact plate',
           [(-S.u(.01), 0), (-S.u(.01), r*.62), (S.u(.004), r*.62), (S.u(.004), 0)],
           (0, 0, z_face), (0, 0, 1), kit.bright, r*.62)
    seat = kit.empty('Borer cradle seat', (0, 0, SOLE_Z), 0)
    seat['note'] = ('the skid shoe plane under the .ship centre; place it on a Shepherd "Drone bay WS 003 launch point <i>" '
                    'with the Borer root scaled by 0.25 / 0.78')


def _rcs(kit, name, base, out, fore):
    """A small RCS block: a bevelled housing and two bells (outboard and fore/aft)."""
    b = Vector(base)
    h = S.u(.07)
    v, f = [], []
    sdk.block(v, f, (b.x-h, b.y-h, b.z-h*.8), (b.x+h, b.y+h, b.z+h*.8))
    ob = sdk.solid(kit, name+' housing', v, f, kit.grey)
    sdk.bevel(ob, S.bevel('small')*.6)
    br = S.u(.028)
    for k, d in enumerate((Vector(out), Vector((fore, 0, 0))), 1):
        d = d.normalized()
        p0 = b+d*h*.95
        _lathe(kit, f'{name} bell {k}', [(0, 0), (0, br*.45), (br*.6, br*.5), (br*1.6, br), (br*1.6, br*.8),
                                         (br*.6, br*.32), (br*.6, 0)], p0, d, kit.steel, br)


def _lamp(kit, name, base, aim, lens_mat, lens_m=.035):
    sdk.lamp_fitting(kit, S, name, base, aim, {'housing': kit.grey, 'lens': lens_mat}, lens_m=lens_m, guard=False)


def detail(kit):
    kit.mark_detail()
    spine = bpy.data.objects['Hull spine']
    # Spine panel lines: a dorsal hatch outline, the neck plate break, the tail plate break.
    kit.engrave(spine, kit.outline(-4.6, 0, .55, .62, .02, .5, 3, corner_m=.08) +
                [kit.band_slab(2.2, .02, -3, 3, .5, 3), kit.band_slab(-.15, .02, -3, 3, .5, 3),
                 kit.band_slab(-5.75, .02, -3, 3, -3, 3)], .012)
    # Dorsal vent (the master's slotted vent behind the neck) and the hatch's four bolts.
    kit.grille('Hull spine dorsal vent', -1.35, 0, .44, .22, 5, 0)
    for k, (dx, dy) in enumerate(((-.21, -.25), (-.21, .25), (.21, -.25), (.21, .25)), 1):
        kit.bolt(f'Hull spine hatch bolt {k}', -4.6+S.u(dx), S.u(dy), S.u(.014))
    # Side slot vents on the spine flanks, engraved.
    for sg in (1, -1):
        kit.engrave(spine, [kit.line_slab((-2.6-k*.35, sg*1.0), (-2.6-k*.35, sg*3.0), .022, -.6, .6) for k in range(3)], .015)
    # Hoist rings on the spine top: how the bay's servicing arms lift a drone off its cradle.
    for k, x in enumerate((1.4, -3.0), 1):
        p, n = kit.surface(x, 0)
        _lathe(kit, f'Hull spine hoist ring {k}',
               [(-S.u(.01), S.u(.035)), (-S.u(.01), S.u(.065)), (S.u(.02), S.u(.065)), (S.u(.02), S.u(.035))], p, n, kit.bright, S.u(.065))
        _lathe(kit, f'Hull spine hoist eye {k}', [(-S.u(.01), 0), (-S.u(.01), S.u(.03)), (S.u(.06), S.u(.026)),
                                                  (S.u(.06), 0)], p, n, kit.steel, S.u(.03))
    # Blade antenna and beacon on the spine's aft deck.
    kit.blade('Hull spine antenna', -5.1, 0, .32, .16, kit.steel, 180, .012, .55)
    p, n = kit.surface(-3.9, .9)
    _lamp(kit, 'Hull spine beacon lamp', p, n, kit.beacon, .025)

    # Cutting head: coil drums either side, cooling ribs on the housing, focusing rings.
    head = bpy.data.objects['Tool head housing']
    for side, sg in (('port', 1), ('starboard', -1)):
        # Coil drums along the casing's shoulders, forward of the trunnions.
        y, zc = sg*.5, TOOL_Z+LASER_HH-.05
        _drum(kit, f'Tool head {side} coil drum', (5.0, y, zc), (6.35, y, zc), .4, kit.steel)
        for k in range(4):
            x = 5.2+k*.3
            _drum(kit, f'Tool head {side} coil rib {k+1}', (x-S.u(.012), y, zc), (x+S.u(.012), y, zc), .46, kit.bright)
    kit.engrave(head, [kit.band_slab(3.2+k*.26, .018, -3, 3, TOOL_Z-3, 3) for k in range(3)], .01)

    for side, sg in (('port', 1), ('starboard', -1)):
        pod = bpy.data.objects[f'Pod {side} armour shell']
        # Panel lines read off the master: the step line across the pod, the inboard plate.
        kit.engrave(pod, kit.lines([(4.9, sg*3.4), (2.2, sg*3.4), (1.4, sg*2.7), (.6, sg*2.7)], .022, POD_Z[1]-.6, 3) +
                    kit.lines([(2.2, sg*3.4), (2.2, sg*6.3), (-.3, sg*6.3)], .022, POD_Z[1]-.6, 3), .012)
        # Raised top plate with bolts, the vent grille, and the outer cheek's slot vents.
        kit.grille(f'Pod {side} top vent', 1.35, sg*4.8, .38, .22, 4, 0)
        for k, (dx, dy) in enumerate(((3.6, 6.4), (3.6, 2.6), (-.1, 5.5), (.4, 3.0)), 1):
            kit.bolt(f'Pod {side} bolt {k}', dx, sg*dy, S.u(.014))
        cheek = bpy.data.objects[f'Pod {side} outer cheek']
        # Three slots along the cheek's crown and a three-line louvre on its outer wall (the master's).
        y_out = (7.75, 9.0) if sg > 0 else (-9.0, -7.75)
        kit.engrave(cheek, [kit.line_slab((x, sg*CHEEK_Y), (x+.42, sg*CHEEK_Y), .05, 1.0, 2.0) for x in (.95, 1.65, 2.35)] +
                    [kit.slab('Temporary louvre', [(1.55, z), (2.45, z), (2.45, z+S.u(.03)), (1.55, z+S.u(.03))],
                              *y_out, axis='Y') for z in (-.45, -.15, .15)], .02)
        # Work lamp on the pod's nose, beside the tines.
        _lamp(kit, f'Pod {side} work lamp', (4.92, sg*5.45, .55), (1, sg*.12, -.08), kit.lens)

        drive = bpy.data.objects[f'Drive {side} pod housing']
        # Collar ribs round the drive pod, an aft vent over the drive and its access panel.
        kit.engrave(drive, [kit.band_slab(-3.65, .02, -9, 9, -3, 3)] +
                    kit.outline(-2.6, sg*5.3, .35, .55, .02, 0, 3, corner_m=.06), .012)
        kit.grille(f'Drive {side} aft vent', -6.15, sg*6.75, .26, .18, 4, 0)
        for k, x in enumerate((-4.5, -4.7), 1):
            _drum(kit, f'Drive {side} gimbal band {k}', (x, sg*4.45, GIMBAL[1]), (x, sg*4.75, GIMBAL[1]),
                  1.0, kit.armour if k == 1 else kit.bright)
        _drum(kit, f'Drive {side} gimbal hub', (GIMBAL[0], sg*5.7, GIMBAL[1]), (GIMBAL[0], sg*5.95, GIMBAL[1]), .55, kit.steel)
        _drum(kit, f'Drive {side} axle collar', (AXLE[0], sg*2.95, AXLE[1]), (AXLE[0], sg*3.15, AXLE[1]), .86, kit.bright)

        _struts(kit, side, sg)
        # RCS blocks on the outer corners: fore on the armour pod, aft on the drive pod.
        _rcs(kit, f'Pod {side} RCS', (3.7, sg*7.45, 1.75), (0, sg, 0), 1)
        _rcs(kit, f'Drive {side} RCS', (-3.1, sg*5.62, 1.3), (0, sg, 0), -1)
    _belly_socket(kit)
