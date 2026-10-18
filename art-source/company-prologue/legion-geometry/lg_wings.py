"""The two wing arms and the fly-through hangar under each of them.

Owner review of the first blockout asked for wing-like arms off a thick torso
with the hangar bays under the wings, and later: "the hangar bays should flow
through and under the wing ... each wing is a fly-through hangar under it".
Each sponson (Y 74-147 at the beam) is a thin arm held out from the torso a
step below its deck (z -8..4), and under it hangs ONE hangar ('Wing <side>
hangar', X -64..104, Y 79.5-130) open at both ends:
- its forward mouth is the arm's angled leading edge (X 100, Y 81 to X 63,
  Y 123), facing forward and outboard; the .ship's WS 012/013 launch out of it
  (slot angle +-10);
- its aft mouth opens at X -64 into the notch between the arm's tail leg and
  the engine block (its outboard part under the tail leg); WS 010/011 launch
  out of it (slot angle 170 / 190).
The sprite's two red-lit recesses on each arm are elevator wells through the
arm into it. The arm deck stays intact over the hangar (turrets WS 002/003,
014/015, 018/019 and the outer belt's arm run sit on it); lg_droop shears the
arm into its anhedral after this builds.

Pass 2 (plausible detail for C2, where the starboard arm's outboard wall is
the near flank, ~105 m away and in deep shadow): the arm's outboard wall is
real armour - the sponson core stops 3.5 inboard of the plan edge and the edge
is built out again as three stacked strakes (upper, proud main strake with an
upward ledge, lower skirt set back under it), with seam gaps, buttresses,
framed hatches and bolted covers.

Pass 2b - HANGAR LOGISTICS (owner: "make them plausibly work for future shots -
consider the sizes of the fighters and how refit might work ... the logistics of
hangar to the central hold"). The design, in Legion units (0.61 m):

Level decks. The arm keeps its 10-degree anhedral, but the hangar no longer
shears with it: nothing parks, rolls or is towed on a 10-degree cant. Every
hangar part is built here in its final position and flagged 'lg_level', and
lg_droop leaves flagged parts alone. The floor is level at z FLOOR (-26.5), the
height of the transfer route into the torso. The roof follows the drooping
arm's underside (one plane, down 4 degrees outboard: -8.7 at the inboard wall,
-12.0 at the outboard wall), so the hangar is a slightly wedge-shaped tunnel,
17.8 high inboard and 13.9 high at its lowest (outboard wall, under the roof
frames): Hoplon (6.6) on a tow dolly (1.2) clears the lowest section by 6.
The pod's bottom is level at -30.5; only its top (sunk into the arm) follows the
droop, so from the bow and stern the arms still read as angled down with a
square hangar slung under each.

Flight lane and refit. The tunnel is the launch and recovery LANE, not a parking
deck: it is 46 wide aft of X 40 and 42 under the leading edge, which passes one
fighter (Hoplon span 31) with room, and nothing parks in it. Refit happens in
the CENTRAL HOLD in the ventral keel and torso (about 130 x 120 between the keel
walls), at the same deck height. The flow, matching C2's recoveries (which come
up from astern into the aft mouths) and launches (out of the forward mouths):

  recovery: a fighter overtakes from astern, enters the AFT mouth, settles on
    the aft station's launch points (WS 010/011); the recovery barrier at X -12
    stops an overrun short of the mid station;
  lane: a tow dolly brings it onto the aft TRAVERSER (rails across the lane
    floor, Y 46-112), which carries it sideways, nose still forward, to
  door: transfer door 1 in the inboard wall (34 x 10: Thunder's 30.3 length or
    Hoplon's 6.6 height plus the dolly, with margin), into
  gallery: the TRANSFER GALLERY, a pressure trunk on the lower flank between the
    hangar's inboard wall and the ventral keel (Y 58-80, z -29.5..-15). Each door
    opens into a 36-deep LOCK through the gallery into the keel (Hoplon span 31
    with a unit each side): outer door closes, the lock cycles, the inner door
    opens into
  central hold: refit - rearm, refuel, repair - then the fighter is moved
    forward through the hold to transfer door 3's lock, and
  back: out door 3 on the forward traverser to the forward station's launch
    points (WS 012/013), the blast deflectors behind them raised, and away
    through the FORWARD mouth.
Door 2 (amidships, between the barrier and the forward station's deflectors) is
the crash and spares door: a fighter caught by the barrier, or one the gantry
crane lifts off the lane, goes inboard there without blocking either station.
Three doors per hangar suit two wings a side (Elite: three Broadswords and two
Hoplons; Strike: six Piranhas): one in, one out, one spare.

Handling gear in the lane: an ordnance and fuel SERVICE TRENCH along both walls
(pipes and feed conveyor in a 1.4-deep channel, service pedestals), two GANTRY
RAILS under the roof with a travelling crane (keyable), a glazed LAUNCH-CONTROL
BOOTH just inside each mouth on the inboard wall, a folding recovery BARRIER
(keyable), hinged BLAST DEFLECTORS behind each station (keyable), a crew CATWALK
on the outboard wall with personnel hatches and ladders, and the elevator wells
as ORDNANCE AND CREW LIFTS (platform parked at the roof with pallets and a crew
cage). Everything that moves rests in its fly-through-clear position: doors
closed, barrier and deflectors stowed flat, crane parked amidships near the
inboard rail, traversers at their doors. Each is a mesh parented to a named
EMPTY so a shot can key it (see deck_record()['keyable']).

Underside. The pod's flat bottom is now three stepped armour courses (a proud
stepped centre course between two keel girders, flatter side courses over a dark
seam backing), with recessed service bays, heat-exchanger panels, docking clamps
and sensor/light blisters; the arm's soffit outboard of the pod and under the
tail leg carries plate panels between its brackets and a radiator under the tail
leg; the transfer gallery shows ribs, maintenance hatches and lit ports from
below. All of it stays inside the plan outline.
"""
import math
import bpy
from lg_kit import boolean, ccw, clip_rect, inset, material, mirror_y, plane_cut, prism, section
from lg_droop import droop

KEEL, DECK = -8.0, 4.0       # the arm: its deck is a 6-unit step below the torso's (z 10)
FLOOR = -26.5                # level hangar floor = transfer route height (final position, not sheared)
ROOF = -8.6                  # hangar roof at the torso wall (|Y| 80); it falls outboard at ROOF_SLOPE
ROOF_SLOPE = math.tan(math.radians(6.0))-math.tan(math.radians(10.0))   # arm taper minus anhedral, per unit |Y|
POD = (-30.5, -2.5)          # pod bottom (level) and top before the anhedral (the top follows the arm down)
POD_CHAMFER = 3.5            # the pod's keel chamfer: inset at the underside, as tall as it is wide
FRAME_DEPTH = .6             # roof frames hang this far under the roof
DOLLY = 1.2                  # tow dolly / traverser deck under a fighter's gear

SPONSON_PORT = [(104, 70), (104, 82), (96, 94), (88, 106), (84, 113), (76, 118), (72, 122), (60, 124),
                (52, 126), (40, 131), (28, 135), (20, 138), (-8, 139), (-24, 139), (-40, 142), (-52, 142),
                (-60, 145), (-68, 147), (-72, 146), (-76, 142), (-80, 140), (-92, 140), (-96, 132),
                (-98, 126), (-96, 125), (-92, 120), (-80, 119), (-76, 118), (-72, 113), (-68, 110),
                (-64, 108), (-64, 88), (-60, 76), (84, 74), (96, 72)]
SPONSON = section(KEEL, DECK, 4.0, 2.5)
TAPER = (84.0, 6.0)          # the arm's underside rises outboard of |Y| 84 at 6 degrees

# Outboard armour. The sponson core is inset by CORE at vertices 0..31 of SPONSON_PORT; the
# strakes fill that band back out to the plan edge. Chains are vertex index runs.
CORE = 3.5
CORE_VERTS = range(0, 32)
CHAIN_LEAD = (0, 6)          # the leading edge over the forward hangar mouth
CHAIN_OUT = (6, 23)          # the outboard wall and the tail leg's end
CHAIN_TAIL = (23, 30)        # the tail leg's inboard edge (the notch edge 30-31 is the aft mouth's)
SEAM = .8                    # face-plate seam gap
# Rows: (backing profile, plate profile, plate length), profiles as (inset d, z), before the anhedral.
UPPER = ([(2.3, -.8), (2.3, 4.1), (3.9, 4.1), (3.9, -.8)],
         [(1.4, -.6), (1.4, 3.7), (2.0, 4.3), (2.5, 4.3), (2.5, -.6)], 14.0)
MID = ([(1.1, -5.4), (1.1, -.8), (3.9, -.8), (3.9, -5.4)],
       [(.6, -4.8), (0.0, -1.5), (.5, -1.0), (1.2, -1.0), (1.2, -4.8)], 17.0)
MID_SILL = [(.5, -5.4), (.5, -4.8), (1.2, -4.8), (1.2, -5.4)]   # continuous bar under the main plates
SKIRT = ([(3.4, -9.2), (3.4, -5.4), (4.4, -5.4), (4.4, -9.2)],
         [(1.6, -7.0), (3.2, -9.6), (3.6, -9.6), (3.6, -5.4), (1.6, -5.4)], 11.0)
# Lintel over the forward mouth, under the main strake. It is sheared with the arm, so its
# underside falls outboard with it; a slim lintel keeps the mouth's outboard end 12.9 clear.
HEADER = [(0.0, -6.0), (0.0, -5.4), (3.9, -5.4), (3.9, -6.0)]
BUTTRESS = [(1.2, -10.2), (-.5, -7.6), (-.5, 4.0), (.4, 4.9), (2.6, 4.9), (2.6, -10.2)]
BUTTRESS_W, BUTTRESS_STEP = 1.8, 7.5
HATCH_W, HATCH_H = 2.4, 3.4  # 2.1 m doors at 0.61 m per unit
# Hatches and covers on the outboard chain: (segment start vertex, t along it).
HATCHES = [(9, .55), (12, .3), (14, .6), (20, .5)]
COVERS = [(7, .5), (10, .45), (12, .75), (13, .5), (16, .5), (21, .4)]
SKIRT_COVERS = [(11, .4), (15, .5)]

# Hidden LAUNCH_BAY slots, exactly as legion.ship has them.
LAUNCH_BAYS = [
    {'id': 'WS 010', 'angle': 170, 'arc': 180, 'mount': 'HIDDEN', 'size': 'MEDIUM', 'type': 'LAUNCH_BAY',
     'locations': (-27, 103, -35, 91, -44, 105, -51, 92)},
    {'id': 'WS 011', 'angle': 190, 'arc': 180, 'mount': 'HIDDEN', 'size': 'MEDIUM', 'type': 'LAUNCH_BAY',
     'locations': (-35, -90, -26, -102, -50, -92, -40, -104)},
    {'id': 'WS 012', 'angle': 10, 'arc': 180, 'mount': 'HIDDEN', 'size': 'MEDIUM', 'type': 'LAUNCH_BAY',
     'locations': (47, 104, 59, 92, 63, 105, 72, 90)},
    {'id': 'WS 013', 'angle': -10, 'arc': 180, 'mount': 'HIDDEN', 'size': 'MEDIUM', 'type': 'LAUNCH_BAY',
     'locations': (59, -92, 48, -104, 72, -90, 64, -105)},
]
# One fly-through hangar under each arm (port values; starboard mirrors).
# pod: the shell's X and Y extent, clipped to the arm's plan (its front to the
# armour core, so the forward mouth sits behind the leading edge's lintel and
# sill); tunnel: the clear lane, two boxes (wider aft of X 40, narrower under
# the leading edge where the arm's edge comes in), open at both ends.
HANGAR = {'pod': (-64, 118, 79.5, 130),
          'tunnel': [(-80, 42, 82, 128), (38, 130, 81, 123)],
          'x': (-64.0, 104.0)}       # aft mouth plane to the forward mouth's inboard end
# Per slot end: the floor plate it launches from (clear of the service trenches), its
# elevator well (the sprite's red-lit recess) and its mouth (centre and chord, model XY).
DECKS = {
    'forward': {'deck_x': (40, 118), 'deck_y': (83.6, 120.4), 'open_above': (46, 80), 'well_y': (88, 110),
                'mouth': (82.0, 101.5), 'mouth_width': 56.0, 'clear_width': 42.0, 'normal': (.742, .671, 0)},
    'aft': {'deck_x': (-64, 40), 'deck_y': (84.6, 125.4), 'open_above': (-60, -16), 'well_y': (88, 110),
            'mouth': (-64.0, 105.0), 'mouth_width': 46.0, 'clear_width': 41.5, 'normal': (-1, 0, 0)},
}
LIGHT_ROWS = 4               # red light strips along each hangar floor (the sprite's red lines)
# Amber lamps the sprite shows beside the wells (port X, Y).
AMBER = {'forward': [(53, 111.15), (59, 111.15), (65, 111.15)],
         'aft': [(-50, 83), (-56, 83), (-62, 83), (-68, 83), (-62, 118), (-68, 118)]}

# --- logistics ---------------------------------------------------------------------
# The aft mouth's inboard jamb is widened to |Y| 86.5: the aft body's sprite flare (|Y| 80-86
# at X -64..-76, its keel chamfer down to z -15 behind the mouth) then stands behind the jamb
# rather than in the opening. Clear aft mouth: Y 86.5-128.
AFT_JAMB_Y = 86.5
# Transfer doors in the inboard wall (port X centre), numbered aft to forward: 1 the aft
# (recovery) station's, 2 the crash and spares door amidships, 3 the forward (launch) station's.
DOOR_X = (-39.0, 8.0, 54.0)
DOOR_ROLE = ('recovery: aft station (WS 010/011) to the central hold',
             'crash and spares: barrier catches and crane lifts, clear of both stations',
             'launch: central hold to the forward station (WS 012/013)')
DOOR_HALF = 17.0             # 34 wide along X
DOOR_TOP = FLOOR+10.0        # 10 high
DOOR_LEAF_Y = (80.0, 80.8)   # the leaf runs in the inboard wall; it slides up into the wall to open
DOOR_OPEN = 10.6             # open: lift the slide empty this far along its local Z
LOCK_Y = 44.0                # the lock's inner door face (inboard end); the outer leaf's inner face is at 80
LOCK_CUT = (43.0, 83.0)      # the bay cutter's Y extent
RAIL_DX = (-10.0, 0.0, 10.0)  # traverser rails about each door's centre
RAIL_Y = (44.5, 112.0)
GALLERY = {'x': (-58.0, 72.5), 'y_out': 79.6, 'z': (-29.5, -15.0), 'chamfer': 2.0, 'min_y': 58.0}
KEEL_WALL = [(-60, 62), (70, 60), (96, 48)]     # lg_hull's ventral keel wall (port), for the gallery's inboard face
TRENCH_W, TRENCH_D = 2.4, 1.4
BARRIER = {'x': -12.0, 'y': (84.2, 125.8), 'length': 9.0}     # hinge line; it lies stowed toward +X
DEFLECT = {'aft': (-21.0, 1), 'forward': (35.0, -1)}           # hinge X and the side the panels lie on
DEFLECT_LEN, DEFLECT_Y = 7.0, (87.0, 123.0)
GANTRY_Y = (87.0, 117.0)     # crane rails under the roof (outside the wells, Y 88-110)
GANTRY_DEPTH = 1.0
CRANE_PARK_X = 8.0
CATWALK_W, CATWALK_Z = 2.4, FLOOR+8.6   # deck top; it runs on the outboard wall
LIFT_Z = (-11.3, -10.5)      # the lift platforms, parked at the hangar roof
METRES_PER_UNIT = .61        # must match build_legion.py (used by the fit table)
# Fighters at their own scales, in metres: (name, length, span, height).
FIGHTERS = [('Broadsword', 15.5, 14.1, 3.3), ('Hoplon', 18.0, 19.0, 4.0), ('Piranha', 17.0, 15.0, 3.3),
            ('Longbow', 15.0, 14.4, 2.6), ('Thunder', 18.5, 10.8, 2.6), ('Dagger', 16.4, 12.1, 2.3),
            ('Talon', 12.1, 12.1, 2.5), ('Wasp', 9.8, 8.1, 2.4)]


def roof_z(y):
    """The hangar roof at lateral position y (final, level-floor coordinates)."""
    return ROOF+ROOF_SLOPE*(abs(y)-80.0)


def pod_top(y):
    """The pod's top, sunk into the arm, after the anhedral."""
    return POD[1]+droop(y)


def deck_z(y):
    """The arm deck after the anhedral."""
    return DECK+droop(y)


def wall_face(x):
    """Lane-side face of the inboard wall at station x (port)."""
    return 82.0 if x < 38.0 else 81.0


def lv(ob):
    """Flag a part as built in its final, level position, so lg_droop leaves it alone."""
    if ob is not None:
        ob['lg_level'] = True
    return ob


def bay_of(slot):
    side = 'port' if slot['locations'][1] > 0 else 'starboard'
    end = 'forward' if abs(slot['angle']) < 90 else 'aft'
    return side, end


# --- clearances (one source for the geometry, the records and the fit table) ---------

def clearances():
    """Clear widths and heights of the lane, the mouths and the transfer doors, in units."""
    lane_h = min(roof_z(t[3])-FRAME_DEPTH-FLOOR for t in HANGAR['tunnel'])
    lane_h = min(lane_h, min(roof_z(y)-GANTRY_DEPTH-FLOOR for y in GANTRY_Y), LIFT_Z[0]-FLOOR)
    fwd_top = min(min(roof_z(y), HEADER[0][1]+droop(y)) for y in (81+k*.5 for k in range(85)))
    narrow = min(t[3]-t[2] for t in HANGAR['tunnel'])
    return {
        'lane': {'clear_width_below_catwalk': narrow, 'clear_width_full_height': narrow-CATWALK_W,
                 'catwalk_underside_above_floor': round(CATWALK_Z-.4-FLOOR, 2),
                 'clear_height': round(lane_h, 2),
                 'clear_height_inboard': round(roof_z(81)-FRAME_DEPTH-FLOOR, 2),
                 'under_parked_crane_hook': round(-17.4-FLOOR, 2),
                 'note': 'narrowest section: width under the leading edge (Y 81-123), height at the outboard '
                         'wall under the roof frames; the crane is mobile and parks near the inboard rail'},
        'aft_mouth': {'clear_width': 128.0-AFT_JAMB_Y, 'clear_height': round(roof_z(128)-.4-FLOOR, 2),
                      'clear_y': [AFT_JAMB_Y, 128.0]},
        'forward_mouth': {'clear_width': 42.0, 'chord': 56.0, 'clear_height': round(fwd_top-FLOOR, 2),
                          'clear_y': [81.0, 123.0],
                          'note': 'the lintel under the leading edge falls with the anhedral; height is at the '
                                  'outboard jamb'},
        'transfer_door': {'clear_width': 2*DOOR_HALF, 'clear_height': DOOR_TOP-FLOOR,
                          'lock_depth': DOOR_LEAF_Y[0]-LOCK_Y},
    }


def fit_table(mpu=METRES_PER_UNIT):
    """Every fighter in the brief at its own scale, in Legion units, against the narrowest openings."""
    c = clearances()
    lane, am, fm, door = c['lane'], c['aft_mouth'], c['forward_mouth'], c['transfer_door']
    rows = []
    for name, lm, sm, hm in FIGHTERS:
        L, S, H = lm/mpu, sm/mpu, hm/mpu
        checks = {
            'lane': S+3.0 <= lane['clear_width_full_height'] and H+DOLLY+1.0 <= lane['clear_height'],
            'aft_mouth': S+3.0 <= am['clear_width'] and H+2.0 <= am['clear_height'],
            'forward_mouth': S+3.0 <= fm['clear_width'] and H+2.0 <= fm['clear_height'],
            'transfer_door': L+2.0 <= door['clear_width'] and H+DOLLY+1.0 <= door['clear_height']
                             and S+2.0 <= door['lock_depth'],
        }
        rows.append({'fighter': name, 'metres': [lm, sm, hm],
                     'units': [round(L, 1), round(S, 1), round(H, 2)],
                     'margins_units': {
                         'lane_width': round(lane['clear_width_full_height']-S, 1),
                         'lane_height_on_dolly': round(lane['clear_height']-H-DOLLY, 1),
                         'aft_mouth': [round(am['clear_width']-S, 1), round(am['clear_height']-H, 1)],
                         'forward_mouth': [round(fm['clear_width']-S, 1), round(fm['clear_height']-H, 1)],
                         'door_width_vs_length': round(door['clear_width']-L, 1),
                         'door_height_on_dolly': round(door['clear_height']-H-DOLLY, 1),
                         'lock_depth_vs_span': round(door['lock_depth']-S, 1)},
                     'fits': checks, 'pass': all(checks.values())})
    return {'metres_per_unit': mpu, 'clearances': c,
            'criteria': 'lane: span+3 <= full-height width, height+dolly+1 <= height; mouths: span+3 and '
                        'height+2; door (moved sideways, nose forward): length+2 <= width, height+dolly+1 <= '
                        'height, span+2 <= lock depth. dolly = %.1f' % DOLLY,
            'fighters': rows, 'all_pass': all(r['pass'] for r in rows)}


def print_fit_table(table):
    c = table['clearances']
    print('HANGAR FIT (units at %.2f m/unit): lane %.1f wide (%.1f full height) x %.2f high; aft mouth %.1f x %.2f; '
          'forward mouth %.1f x %.2f; door %.0f x %.0f, lock %.0f deep' % (
              table['metres_per_unit'], c['lane']['clear_width_below_catwalk'], c['lane']['clear_width_full_height'],
              c['lane']['clear_height'], c['aft_mouth']['clear_width'], c['aft_mouth']['clear_height'],
              c['forward_mouth']['clear_width'], c['forward_mouth']['clear_height'],
              c['transfer_door']['clear_width'], c['transfer_door']['clear_height'], c['transfer_door']['lock_depth']))
    print('  %-11s %6s %6s %6s  %-5s %-5s %-5s %-5s' % ('fighter', 'L', 'span', 'h', 'lane', 'aftM', 'fwdM', 'door'))
    for r in table['fighters']:
        f = r['fits']
        print('  %-11s %6.1f %6.1f %6.2f  %-5s %-5s %-5s %-5s' % (
            r['fighter'], *r['units'], *('ok' if f[k] else 'FAIL' for k in ('lane', 'aft_mouth', 'forward_mouth',
                                                                             'transfer_door'))))


def doors_record(side):
    sy = 1 if side == 'port' else -1
    out = []
    for k, (cx, role) in enumerate(zip(DOOR_X, DOOR_ROLE), 1):
        out.append({'k': k, 'role': role, 'slide_empty': f'Wing {side} transfer door {k} slide',
                    'leaf': f'Wing {side} transfer door {k} leaf',
                    'inner_door': f'Wing {side} transfer bay {k} inner door',
                    'centre': [cx, sy*sum(DOOR_LEAF_Y)/2, (FLOOR+DOOR_TOP)/2],
                    'normal_into_torso': [0.0, -float(sy), 0.0],
                    'clear_width_x': 2*DOOR_HALF, 'clear_height': DOOR_TOP-FLOOR, 'x': [cx-DOOR_HALF, cx+DOOR_HALF],
                    'sill_z': FLOOR, 'lock': {'y': sorted((sy*LOCK_Y, sy*DOOR_LEAF_Y[0])),
                                              'depth': DOOR_LEAF_Y[0]-LOCK_Y},
                    'open': {'translate_local': [0.0, 0.0, DOOR_OPEN], 'rest': 'closed',
                             'note': 'the leaf rises into the inboard wall'}})
    return out


def rails_record(side):
    sy = 1 if side == 'port' else -1
    return [{'door': k, 'x': [cx+dx for dx in RAIL_DX], 'y': sorted((sy*RAIL_Y[0], sy*RAIL_Y[1])), 'z': FLOOR+.4,
             'traverser_empty': f'Wing {side} traverser {k} slide',
             'rest_y': sy*(wall_face(cx)+7.6),
             'travel_local_y': {'to_lock_centre': round(sy*((LOCK_Y+DOOR_LEAF_Y[0])/2-wall_face(cx)-7.6), 2),
                                'to_launch_points': round(sy*(102.0-wall_face(cx)-7.6), 2)},
             'note': 'rails run from outboard of the launch points across the lane, through the door and the lock '
                     'to its inner door; the traverser parks at its door (rest) and moves along local Y'}
            for k, cx in enumerate(DOOR_X, 1)]


def gallery_record(side):
    sy = 1 if side == 'port' else -1
    return {'name': f'Wing {side} transfer gallery', 'x': list(GALLERY['x']),
            'y': sorted((sy*GALLERY['min_y'], sy*GALLERY['y_out'])), 'z': list(GALLERY['z']),
            'floor_z': FLOOR, 'locks': [f'Wing {side} transfer bay {k}' for k in range(1, len(DOOR_X)+1)],
            'pressure_hull': True,
            'note': 'enclosed pressure trunk on the lower flank between the hangar inboard wall and the ventral keel; '
                    'each transfer door opens into a lock that crosses it into the keel, whose inner door opens on '
                    'the central hold (not modelled). Ribs, maintenance hatches and lit ports show from below.'}


LOGISTICS = ('recovery: overtake from astern into the AFT mouth, settle on the aft station (WS 010/011 launch '
             'points), recovery barrier at X -12 against overruns -> lane: tow dolly onto the aft traverser -> '
             'traverser: sideways, nose forward, to transfer door 1 -> door: into its lock across the transfer '
             'gallery (outer door closes, lock cycles) -> gallery: inner door into the central hold in the ventral '
             'keel/torso at the same deck height -> central hold: refit (rearm, refuel, repair) -> forward through '
             'the hold to door 3 -> traverser out to the forward station (WS 012/013), deflectors up -> launch out of '
             'the FORWARD mouth. Door 2 amidships is the crash and spares door (barrier catches, gantry-crane lifts). '
             'In-game the aft slots also launch aft through the aft mouth; the lane is fly-through when barrier and '
             'deflectors are stowed (their rest state).')


def keyable_record(side):
    sy = 1 if side == 'port' else -1
    aft, fwd = [s['id'] for s in LAUNCH_BAYS if bay_of(s) == (side, 'aft')][0], \
        [s['id'] for s in LAUNCH_BAYS if bay_of(s) == (side, 'forward')][0]
    return {
        'transfer_doors': [f'Wing {side} transfer door {k} slide' for k in range(1, len(DOOR_X)+1)],
        'traversers': [f'Wing {side} traverser {k} slide' for k in range(1, len(DOOR_X)+1)],
        'recovery_barrier': {'empty': f'Wing {side} recovery barrier hinge', 'child': f'Wing {side} recovery barrier',
                             'rest': 'stowed flat', 'raise': 'rotate about local Y by -90 deg'},
        'gantry_crane': {'empty': f'Wing {side} gantry crane slide', 'child': f'Wing {side} gantry crane',
                         'rest_x': CRANE_PARK_X, 'travel_local_x': [-62.0-CRANE_PARK_X, 60.0-CRANE_PARK_X]},
        'blast_deflectors': {aft: {'empty': f'Flight deck {aft} blast deflector hinge',
                                   'raise': 'rotate about local Y by -50 deg'},
                             fwd: {'empty': f'Flight deck {fwd} blast deflector hinge',
                                   'raise': 'rotate about local Y by +50 deg'}},
        'note': 'every keyable part is a mesh parented (identity parent inverse) to the named empty, which rests '
                'in the closed/stowed/parked state; key the empty',
    }


def deck_record(slot):
    """The flight deck as the cinematic needs it: launch points, its own mouth, the shared hangar and its logistics."""
    side, end = bay_of(slot)
    d = DECKS[end]
    sy = 1 if side == 'port' else -1
    a = math.radians(slot['angle'])
    loc = slot['locations']
    mx, my = d['mouth']
    nx, ny, nz = d['normal']
    name = f"Flight deck {slot['id']}"
    ys = [y for t in HANGAR['tunnel'] for y in t[2:]]
    c = clearances()
    mc = c['aft_mouth' if end == 'aft' else 'forward_mouth']
    mouth_roof = roof_z(my)
    return {
        'slot': slot['id'], 'side': side, 'end': end, 'objects': name+' ...',
        'slot_angle': slot['angle'], 'arc': slot['arc'],
        'launch_direction': [round(math.cos(a), 4), round(math.sin(a), 4), 0.0],
        'recovery_approach': [round(-math.cos(a), 4), round(-math.sin(a), 4), 0.0],
        'role': 'recovery station (aft mouth) and aft launches' if end == 'aft' else 'launch station (forward mouth)',
        'launch_points': [[loc[i], loc[i+1], FLOOR+.5] for i in range(0, 8, 2)],
        'launch_point_anchors': [f'{name} launch point {k}' for k in range(1, 5)],
        'mouth': {'anchor': f'{name} mouth', 'centre': [mx, sy*my, round((FLOOR+mouth_roof)/2, 3)],
                  'normal': [nx, sy*ny, nz], 'width': d['mouth_width'], 'height': round(mc['clear_height'], 2),
                  'clear_width': mc['clear_width'], 'clear_height': mc['clear_height'],
                  'clear_y': sorted((sy*mc['clear_y'][0], sy*mc['clear_y'][1])),
                  'note': 'the forward mouth is the angled leading edge, the aft mouth opens into the notch; '
                          'the aft mouth inboard jamb is widened to |Y| 86.5 to keep the aft body flare behind it'},
        'deck': {'floor_z': FLOOR, 'level': True,
                 'roof_z': {'inboard': round(roof_z(wall_face(0 if end == 'aft' else 60)), 3),
                            'outboard': round(roof_z(128 if end == 'aft' else 123), 3),
                            'plane': f'z = {ROOF} + {round(ROOF_SLOPE, 4)} * (|y| - 80)'},
                 'x': list(d['deck_x']), 'y': sorted((sy*d['deck_y'][0], sy*d['deck_y'][1])), 'under_wing': True,
                 'elevator_well_x': list(d['open_above']),
                 'elevator_well_y': sorted((sy*d['well_y'][0], sy*d['well_y'][1])),
                 'elevator_lift': f'{name} well lift', 'control_booth': f'{name} control booth',
                 'blast_deflector': f'{name} blast deflector hinge'},
        'hangar': {'name': f'Wing {side} hangar', 'x': list(HANGAR['x']),
                   'y': sorted((sy*min(ys), sy*max(ys))), 'floor': FLOOR,
                   'roof': {'inboard': round(roof_z(81), 3), 'outboard': round(roof_z(128), 3)},
                   'fly_through': True, 'lane_clearance': c['lane'],
                   'clear_sections': [{'x': [t[0], t[1]], 'y': sorted((sy*t[2], sy*t[3]))} for t in HANGAR['tunnel']],
                   'note': 'one launch and recovery lane per arm shared by both slots, open at both mouths, level '
                           'floor; the roof carries transverse frames (0.6 deep), two longitudinal girders and two '
                           'gantry crane rails (1.0 deep)'},
        'transfer_doors': doors_record(side),
        'traverser_rails': rails_record(side),
        'gallery': gallery_record(side),
        'keyable': keyable_record(side),
        'logistics': LOGISTICS,
    }


# --- merged-geometry helpers -------------------------------------------------

class Acc:
    """Vertices and faces of many small closed shells, merged into one object."""
    def __init__(self):
        self.verts, self.faces = [], []

    def add(self, verts, faces):
        o = len(self.verts)
        self.verts += [tuple(v) for v in verts]
        self.faces += [tuple(o+i for i in f) for f in faces]

    def hexa(self, c):
        """Hexahedron from 8 corners: bottom loop then top loop, same winding."""
        self.add(c, [(0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)])

    def box(self, x0, x1, y0, y1, z0, z1):
        self.hexa([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
                   (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)])

    def slope(self, x0, x1, y0, y1, zb, zt):
        """Box whose bottom and top heights are functions of y (planar when they are linear)."""
        fb = zb if callable(zb) else (lambda y: zb)
        ft = zt if callable(zt) else (lambda y: zt)
        self.hexa([(x0, y0, fb(y0)), (x1, y0, fb(y0)), (x1, y1, fb(y1)), (x0, y1, fb(y1)),
                   (x0, y0, ft(y0)), (x1, y0, ft(y0)), (x1, y1, ft(y1)), (x0, y1, ft(y1))])

    def rod_x(self, x0, x1, y, z, r, n=8):
        ring = [(r*math.cos(2*math.pi*(k+.5)/n), r*math.sin(2*math.pi*(k+.5)/n)) for k in range(n)]
        verts = [(x, y+a, z+b) for x in (x0, x1) for a, b in ring]
        self.add(verts, [tuple(reversed(range(n))), tuple(range(n, 2*n))] +
                 [(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)])

    def frustum(self, x, y, z0, z1, r0, r1, n=8):
        """Vertical n-sided frustum: radius r0 at z0, r1 at z1."""
        ring = [(math.cos(2*math.pi*(k+.5)/n), math.sin(2*math.pi*(k+.5)/n)) for k in range(n)]
        verts = [(x+r*a, y+r*b, z) for z, r in ((z0, r0), (z1, r1)) for a, b in ring]
        self.add(verts, [tuple(reversed(range(n))), tuple(range(n, 2*n))] +
                 [(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)])

    def loft_x(self, x0, x1, section):
        """Prism of a YZ section swept from x0 to x1."""
        n = len(section)
        verts = [(x, y, z) for x in (x0, x1) for y, z in section]
        self.add(verts, [tuple(reversed(range(n))), tuple(range(n, 2*n))] +
                 [(k, (k+1) % n, n+(k+1) % n, n+k) for k in range(n)])

    def build(self, kit, name, mat, sy=1, hull=False, level=False):
        if not self.faces:
            return None
        verts = [(x, sy*y, z) for x, y, z in self.verts]
        ob = kit.mesh(name, verts, self.faces, mat)
        if hull:
            kit.hull.append(ob)
        return lv(ob) if level else ob


class Edge:
    """Mitred offset lines of the sponson plan: point at (vertex i -> i+1, t, inset d)."""
    def __init__(self, plan):
        self.p0 = plan
        self.p1 = inset(plan, 1.0)
        self.n = len(plan)

    def off(self, i, d):
        (ax, ay), (bx, by) = self.p0[i % self.n], self.p1[i % self.n]
        return ax+d*(bx-ax), ay+d*(by-ay)

    def pt(self, i, t, d):
        a, b = self.off(i, d), self.off(i+1, d)
        return a[0]+t*(b[0]-a[0]), a[1]+t*(b[1]-a[1])

    def length(self, i):
        (ax, ay), (bx, by) = self.p0[i % self.n], self.p0[(i+1) % self.n]
        return math.hypot(bx-ax, by-ay)

    def module(self, acc, i, t0, t1, profile):
        """Prism of a (d, z) profile swept along segment i between t0 and t1."""
        m = len(profile)
        verts = [(*self.pt(i, t, d), z) for t in (t0, t1) for d, z in profile]
        faces = [tuple(reversed(range(m))), tuple(range(m, 2*m))]
        faces += [(k, (k+1) % m, m+(k+1) % m, m+k) for k in range(m)]
        acc.add(verts, faces)


def _plates(L, length, cuts=()):
    """Plate intervals (t0, t1) along a segment of length L, seams SEAM wide, minus cut intervals."""
    n = max(1, round(L/length))
    g = SEAM/2/L
    out = [(j/n+g, (j+1)/n-g) for j in range(n)]
    for c0, c1 in cuts:
        nxt = []
        for a, b in out:
            if b <= c0 or a >= c1:
                nxt.append((a, b))
                continue
            if c0-a > 1.0/L:
                nxt.append((a, c0))
            if b-c1 > 1.0/L:
                nxt.append((c1, b))
        out = nxt
    return [(a, b) for a, b in out if b > a]


def _blunt(poly, cut=3.0, limit=75.0):
    """A CCW plan with every corner sharper than `limit` degrees cut back by `cut`, so the pod's
    keel chamfer (a mitred inset) cannot fold through itself at a clipped sliver corner."""
    out = []
    n = len(poly)
    for i in range(n):
        (ax, ay), (bx, by), (cx, cy) = poly[i-1], poly[i], poly[(i+1) % n]
        u, v = (ax-bx, ay-by), (cx-bx, cy-by)
        lu, lv_ = math.hypot(*u), math.hypot(*v)
        ang = math.degrees(math.acos(max(-1.0, min(1.0, (u[0]*v[0]+u[1]*v[1])/(lu*lv_)))))
        if ang < limit and lu > cut and lv_ > cut:
            out += [(bx+u[0]/lu*cut, by+u[1]/lu*cut), (bx+v[0]/lv_*cut, by+v[1]/lv_*cut)]
        else:
            out.append((bx, by))
    return out


def _inside(p, poly):
    x, y = p
    c = False
    for (ax, ay), (bx, by) in zip(poly, poly[1:]+poly[:1]):
        if (ay > y) != (by > y) and x < ax+(bx-ax)*(y-ay)/(by-ay):
            c = not c
    return c


def _underside(y):
    """The arm's soffit height at |y| (taper outboard of TAPER[0]), before the anhedral."""
    return KEEL+max(0.0, abs(y)-TAPER[0])*math.tan(math.radians(TAPER[1]))


def _edge_y(chain, x):
    """Y of a plan polyline (list of (x, y)) at station x: the first segment spanning x."""
    for (ax, ay), (bx, by) in zip(chain, chain[1:]):
        if min(ax, bx) <= x <= max(ax, bx) and ax != bx:
            return ay+(by-ay)*(x-ax)/(bx-ax)
    return None


def _edge_x(chain, y):
    for (ax, ay), (bx, by) in zip(chain, chain[1:]):
        if min(ay, by) <= y <= max(ay, by) and ay != by:
            return ax+(bx-ax)*(y-ay)/(by-ay)
    return None


def _cut(target, cutter, keep=False):
    """Boolean difference; falls back to the exact solver where the manifold one refuses the input."""
    before = len(target.data.polygons)
    if keep:
        boolean(target, cutter, keep=True)
    else:
        boolean(target, cutter, keep=True)
    if len(target.data.polygons) == before:
        bpy.context.view_layer.objects.active = target
        mod = target.modifiers.new('Hull boolean', 'BOOLEAN')
        mod.operation, mod.object, mod.solver = 'DIFFERENCE', cutter, 'EXACT'
        bpy.ops.object.modifier_move_to_index(modifier=mod.name, index=0)
        bpy.ops.object.modifier_apply(modifier=mod.name)
    if not keep:
        bpy.data.objects.remove(cutter, do_unlink=True)


# --- the outboard armour ----------------------------------------------------

def armour(edge, accs):
    """Strakes, buttresses, hatches and covers along the armour chains (port side)."""
    plate, tan, back, frame, door = accs

    def row(i, spec, cuts=(), phase=0):
        backing, prof, length = spec
        edge.module(back, i, 0.0, 1.0, backing)
        for k, (a, b) in enumerate(_plates(edge.length(i), length, cuts)):
            p = prof
            if spec is UPPER and (i+k) % 4 == 1:     # some rim plates stand a little prouder
                p = [(d, z+.4 if z > 4.0 else z) for d, z in prof]
            edge.module(tan if (i*7+k+phase) % 3 == 0 else plate, i, a, b, p)

    for i in range(*CHAIN_LEAD):
        row(i, UPPER)
        row(i, MID, phase=1)
        edge.module(frame, i, 0.0, 1.0, HEADER)
    for i in range(*CHAIN_TAIL):
        row(i, UPPER)
        row(i, MID, phase=1)
        edge.module(back, i, 0.0, 1.0, MID_SILL)
    row(CHAIN_TAIL[1], UPPER)          # the notch edge: upper strake over the aft mouth's header
    hatches = {}
    for i, t in HATCHES:
        hatches.setdefault(i, []).append(t)
    for i in range(*CHAIN_OUT):
        L = edge.length(i)
        cuts = [(t-(HATCH_W/2+.6)/L, t+(HATCH_W/2+.6)/L) for t in hatches.get(i, [])]
        row(i, UPPER)
        row(i, MID, cuts, phase=1)
        edge.module(back, i, 0.0, 1.0, MID_SILL)
        row(i, SKIRT, phase=2)
        # Framed hatches: a recessed door in a proud frame, set in the main strake.
        for t in hatches.get(i, []):
            hw, fw = HATCH_W/2/L, (HATCH_W/2+.5)/L
            z0, z1 = -4.75, -4.75+HATCH_H
            edge.module(door, i, t-hw, t+hw, [(.7, z0), (.7, z1), (1.3, z1), (1.3, z0)])
            for a, b in ((t-fw, t-hw), (t+hw, t+fw)):
                edge.module(frame, i, a, b, [(-.25, z0-.3), (-.25, z1+.3), (1.3, z1+.3), (1.3, z0-.3)])
            edge.module(frame, i, t-hw, t+hw, [(-.25, z1), (-.25, z1+.3), (1.3, z1+.3), (1.3, z1)])
            edge.module(frame, i, t-hw, t+hw, [(-.25, z0-.3), (-.25, z0), (1.3, z0), (1.3, z0-.3)])
        # Buttresses tie the three strakes together, avoiding the hatches.
        n = int(L//BUTTRESS_STEP)
        for j in range(1, n+1):
            t = j/(n+1)
            if any(abs(t-h)*L < 4 for h in hatches.get(i, [])):
                continue
            w = BUTTRESS_W/2/L
            edge.module(frame, i, t-w, t+w, BUTTRESS)
    # Bolted service covers: proud plates on the upper strake and the skirt.
    for i, t in COVERS:
        w = 1.5/edge.length(i)
        edge.module(frame, i, t-w, t+w, [(1.0, .4), (1.0, 2.8), (1.5, 2.8), (1.5, .4)])
    for i, t in SKIRT_COVERS:
        w = 1.6/edge.length(i)
        edge.module(frame, i, t-w, t+w, [(1.2, -6.9), (1.2, -5.6), (1.7, -5.6), (1.7, -6.9)])


# --- the arm's underside (built before the anhedral and sheared with the arm) ------------

def _soffit(acc, x0, x1, ya, yb, below, above):
    """A part on the arm's soffit across y ya..yb, `below` under it to `above` into it."""
    ys = [ya] + ([TAPER[0]] if ya < TAPER[0] < yb else []) + [yb]
    for a, b in zip(ys, ys[1:]):
        za, zb = _underside(a), _underside(b)
        acc.hexa([(x0, a, za-below), (x1, a, za-below), (x1, b, zb-below), (x0, b, zb-below),
                  (x0, a, za+above), (x1, a, za+above), (x1, b, zb+above), (x0, b, zb+above)])


def under_structure(acc, plan, plates, rad):
    """Ribs, panels and a radiator under the arm outboard of the hangar and under the tail leg (port side)."""
    outer = [plan[k] for k in range(CHAIN_OUT[0], CHAIN_OUT[1]+1)]
    inner = [plan[k] for k in range(CHAIN_TAIL[0], CHAIN_TAIL[1]+1)]
    wall = HANGAR['pod'][3]

    def rib(x, y0, y1, w, depth):
        _soffit(acc, x-w/2, x+w/2, y0, y1, depth, .8)

    # Brackets from the hangar's outboard wall out to the skirt, where the arm reaches past it,
    # with soffit panels between them (two courses each side of the girder, seams between).
    xs = []
    x = -61.0
    while x < 40:
        y1 = _edge_y(outer, x)-4.4
        if y1-wall > 2.0:
            rib(x, wall-.5, y1, 1.3, 2.4)
            xs.append((x, y1))
        x += 7.5
    for (xa, ya), (xb, yb) in zip(xs, xs[1:]):
        ye = min(ya, yb)-.6
        for y0, y1 in ((wall+.4, wall+2.1), (wall+3.9, ye)):
            if y1-y0 > 1.2:
                _soffit(plates, xa+1.05, xb-1.05, y0, y1, .55, .3)
    # A girder along the arm under those brackets.
    z = _underside(wall+3.0)
    acc.box(-58.0, 20.0, wall+2.4, wall+3.6, z-3.2, z+.8)
    # Under the tail leg: ribs across it, one keel girder along it, a heat exchanger between the ribs.
    ribs = []
    for x in (-71.0, -78.0, -85.0, -92.0):
        y0, y1 = _edge_y(inner, x), _edge_y(outer, x)
        if y0 is None or y1 is None:
            continue
        rib(x, y0+2.0, y1-4.4, 1.3, 2.0)
        ribs.append((x, y0+2.0, y1-4.4))
    yk = 131.0
    z = _underside(yk)
    acc.box(-95.0, -66.0, yk-.6, yk+.6, z-3.0, z+.8)
    for (xa, a0, a1), (xb, b0, b1) in zip(ribs, ribs[1:]):
        y0, y1 = max(a0, b0)+.6, min(a1, b1)-.6
        for lo, hi in ((y0, yk-1.2), (yk+1.2, y1)):
            if hi-lo < 2.0:
                continue
            _soffit(rad, xa-.65-.1, xb+.65+.1, lo, lo+.5, 1.4, .3)        # frame rails
            _soffit(rad, xa-.65-.1, xb+.65+.1, hi-.5, hi, 1.4, .3)
            f = xb+1.4
            while f < xa-1.4:                                              # fins across the panel
                _soffit(rad, f-.15, f+.15, lo+.5, hi-.5, 1.2, .3)
                f += .9


# --- the hangar (built level, in final position) ------------------------------------

def _tunnel_y(x, lead):
    """The lane's clear Y range at station x (port), or None past the forward mouth."""
    for x0, x1, y0, y1 in reversed(HANGAR['tunnel']):
        if x0 <= x <= x1:
            yf = _edge_y(lead, x)
            if yf is not None and yf < y0+6:
                return None
            return y0, (min(y1, yf-5.5) if yf is not None else y1)
    return None


def _in_well(x, y):
    return any(DECKS[e]['open_above'][0]-.5 <= x <= DECKS[e]['open_above'][1]+.5 and
               DECKS[e]['well_y'][0]-.5 <= y <= DECKS[e]['well_y'][1]+.5 for e in DECKS)


def _near_door(x, pad=1.5):
    return any(abs(x-c) < DOOR_HALF+pad for c in DOOR_X)


def hangar_shell_detail(kit, side, sy, plan):
    """Frames and strakes on the pod's outer walls; roof frames, girders and wall posts inside the lane."""
    frames, ribs = Acc(), Acc()
    lead = [plan[k] for k in range(0, 9)]   # (104,70) .. (52,126): the arm's leading edge
    px0, px1, py0, py1 = HANGAR['pod']
    zlo = POD[0]+POD_CHAMFER+.3

    def band(fixed, a0, a1, w, z_top, z_in, z_out):
        """A proud strake along a wall at Y = fixed whose underside slopes down to the wall (planet-shine)."""
        f1 = fixed+w
        frames.hexa([(a0, fixed, z_in), (a1, fixed, z_in), (a1, f1, z_out), (a0, f1, z_out),
                     (a0, fixed, z_top), (a1, fixed, z_top), (a1, f1, z_top), (a0, f1, z_top)])

    def wall_frames(fixed, a0, a1, step, out):
        lo, hi = sorted((fixed, fixed+out))
        zt = pod_top(fixed)-.9
        a = a0+step/2
        while a < a1-1:
            frames.box(a-.7, a+.7, lo, hi, zlo, zt)
            a += step
        band(fixed, a0, a1, 1.5*out, FLOOR+8.0, FLOOR+5.4, FLOOR+7.0)   # rubbing strake
        band(fixed, a0, a1, 1.3*out, zt, zt-3.2, zt-2.0)               # cap strake under the arm

    wall_frames(py1, px0+1.0, 41.0, 6.0, .9)     # the long outboard wall
    wall_frames(79.5, GALLERY['x'][1]+1.0, 99.0, 7.0, -.9)   # inboard wall forward of the transfer gallery
    # Roof: transverse frames following the roof plane (broken over the lifts), wall posts clear of the
    # transfer doors, two girders along it (broken over the lifts).
    x = px0+3.0
    while True:
        r = _tunnel_y(x, lead)
        if r is None or x > 101:
            break
        y0, y1 = r
        spans = [(y0, y1)]
        for e in DECKS:
            wx0, wx1 = DECKS[e]['open_above']
            if wx0-.5 <= x <= wx1+.5:
                wy0, wy1 = DECKS[e]['well_y']
                spans = [(y0, wy0-.3), (wy1+.3, y1)]
        for a, b in spans:
            if b-a > .5:
                ribs.slope(x-.4, x+.4, a, b, lambda y: roof_z(y)-FRAME_DEPTH, lambda y: roof_z(y)+.3)
        if not _near_door(x, 1.2):
            ribs.slope(x-.35, x+.35, y0, y0+.5, FLOOR, lambda y: roof_z(y)+.1)
        if y1 >= HANGAR['tunnel'][0 if x <= 40 else 1][3]-.01:
            ribs.slope(x-.35, x+.35, y1-.5, y1, FLOOR, lambda y: roof_z(y)+.1)
        x += 6.0
    for y in (95.0, 115.0):
        xe = _edge_x(lead, y)-6.0
        segs = [(px0+1.0, xe)]
        for e in DECKS:
            wx0, wx1 = DECKS[e]['open_above']
            wy0, wy1 = DECKS[e]['well_y']
            if wy0 <= y <= wy1:
                segs = [s for a, b in segs for s in ((a, min(b, wx0-.3)), (max(a, wx1+.3), b)) if s[1]-s[0] > 1]
        for a, b in segs:
            ribs.slope(a, b, y-.6, y+.6, lambda yy: roof_z(yy)-.9, lambda yy: roof_z(yy)+.3)
    frames.build(kit, f'Wing {side} hangar frames', kit.plate, sy, level=True)
    ribs.build(kit, f'Wing {side} hangar roof frames', kit.steel, sy, level=True)


def deck_detail(kit, name, end, sy, plan, mats):
    """One slot's end of the hangar: its mouth frame, ceiling lights, well coaming and lamps."""
    d = DECKS[end]
    wx0, wx1 = d['open_above']
    wy0, wy1 = d['well_y']
    mouth, coaming = Acc(), Acc()
    lead = [plan[k] for k in range(0, 9)]
    px0, px1, py0, py1 = HANGAR['pod']
    PB = POD[0]
    sill = [(-.3, FLOOR-.2), (0.0, PB-.6), (4.5, PB-.6), (4.5, FLOOR-.2)]
    if end == 'forward':
        ty0, ty1 = HANGAR['tunnel'][1][2:]
        e = Edge(plan)
        for i in range(0, 8):          # apron sill along the leading edge under the mouth
            ya, yb = plan[i][1], plan[i+1][1]
            if yb <= ya or yb <= 79.0 or ya >= ty1+2:
                continue
            t0 = max(0.0, (79.0-ya)/(yb-ya))
            t1 = min(1.0, (ty1+2-ya)/(yb-ya))
            e.module(mouth, i, t0, t1, sill)
        mouth.box(100.0, 104.4, 77.8, ty0, PB-.4, roof_z(ty0)+.5)                         # inboard jamb
        x_out = _edge_x(lead, ty1)
        mouth.box(x_out-4.0, x_out, ty1, ty1+2.0, PB-.4, roof_z(ty1+2)+.5)                 # outboard jamb
        lights = [(ty0+3.0, 40.0, _edge_x(lead, ty0+3.0)-6), (ty1-3.0, 40.0, _edge_x(lead, ty1-3.0)-10)]
        # The outer wall of the forward hangar box on the deck, outboard of the well (sprite X 40-76).
        box = Acc()
        box.box(40.0, 75.0, wy1+1.4, 119.8, DECK-.5, DECK+2.2)
        box.box(43.0, 72.0, wy1+2.6, 118.6, DECK+2.2, DECK+3.0)
        box.build(kit, name+' box wall', kit.plate, sy)        # on the arm deck: sheared with it
        lamp_z = DECK+1.4
    else:
        ty0, ty1 = HANGAR['tunnel'][0][2:]
        # (no two boxes share a face plane: coincident faces in one mesh rendered black)
        mouth.slope(-66.5, -63.0, py0+.2, py1+1.1, lambda y: roof_z(y)-.4, lambda y: pod_top(y)-.2)   # header
        mouth.box(-68.5, -61.6, py0-.3, py1+1.3, PB-.6, FLOOR-.2)           # apron sill
        # Inboard jamb, widened to |Y| 86.5 so the aft body's flare stands behind it, not in the mouth.
        mouth.box(-67.5, -62.2, py0-1.5, AFT_JAMB_Y, PB-.4, pod_top(AFT_JAMB_Y)-.3)
        mouth.box(-62.2, -60.4, AFT_JAMB_Y-1.2, AFT_JAMB_Y, PB-.4, roof_z(AFT_JAMB_Y)-.2)   # its lane-side return
        mouth.box(-67.5, -62.2, ty1, py1+1.6, PB-.4, pod_top(py1+1.6))
        lights = [(ty0+3.0, -63.0, 40.0), (ty1-3.0, -63.0, 40.0)]
        lamp_z = None
    c = 1.2                            # coaming round the elevator well on the arm deck (sheared)
    coaming.box(wx0-c, wx1+c, wy0-c, wy0, DECK-.5, DECK+1.0)
    coaming.box(wx0-c, wx1+c, wy1, wy1+c, DECK-.5, DECK+1.0)
    coaming.box(wx0-c, wx0, wy0, wy1, DECK-.5, DECK+1.0)
    coaming.box(wx1, wx1+c, wy0, wy1, DECK-.5, DECK+1.0)
    mouth.build(kit, name+' mouth frame', kit.steel, sy, level=True)
    coaming.build(kit, name+' well coaming', kit.steel, sy)
    for k, (y, xa, xb) in enumerate(lights, 1):        # red light lines along the roof
        lv(kit.box(f'{name} ceiling light {k}', ((xa+xb)/2, sy*y, roof_z(y)-.1), (xb-xa, .7, .3), kit.bay_light,
                   bevel=0))
    for k, y in enumerate((wy0+.15, wy1-.15), 1):       # and on the well walls (in the arm: sheared)
        kit.box(f'{name} well light {k}', ((wx0+wx1)/2, sy*y, -1.0), (wx1-wx0-2, .3, .5), kit.bay_light, bevel=0)
    for k, (x, y) in enumerate(AMBER[end], 1):          # amber edge lamps where the sprite shows them
        if lamp_z is None:
            hit = kit.surface(x, sy*y, top=120)
            z = (hit[0].z if hit else DECK)+.25
            kit.box(f'{name} amber lamp {k}', (x, sy*y, z), (.9, .9, .5), mats['amber'], bevel=0)
        else:
            kit.box(f'{name} amber lamp {k}', (x, sy*y, lamp_z), (.9, .5, .9), mats['amber'], bevel=0)


def _keyed(kit, empty_name, loc, sy, parts, note):
    """A named empty (no rotation) at a port-side location, with child meshes built in its local frame."""
    e = lv(kit.empty(empty_name, (loc[0], sy*loc[1], loc[2]), 0.0))
    e.empty_display_size = 4
    e['keyable'] = note
    for acc, name, mat in parts:
        ob = acc.build(kit, name, mat, sy, level=True)
        if ob is not None:
            ob.parent = e
    return e


def lane_gear(kit, side, sy, plan, mats):
    """Transfer doors and locks, traverser rails, service trench, gantry, barrier, deflectors,
    booths, catwalk and lifts (port values, mirrored by sy). Returns the cutters for the pod."""
    lead = [plan[k] for k in range(0, 9)]
    steel, plate, dark, deck = kit.steel, kit.plate, kit.dark, kit.deck
    work, port_light, glass = mats['work'], mats['port'], kit.glass

    # Transfer doors: frames on the lane face, a leaf in the wall on a slide empty, a lit lock behind.
    frames, bays, lights, inner, amber = Acc(), Acc(), Acc(), Acc(), Acc()
    for k, cx in enumerate(DOOR_X, 1):
        wf = wall_face(cx)
        x0, x1 = cx-DOOR_HALF, cx+DOOR_HALF
        for a, b in ((x0-1.4, x0), (x1, x1+1.4)):                       # jambs, proud of the wall
            frames.box(a, b, wf-.8, wf+.7, FLOOR, DOOR_TOP+1.4)
        frames.box(x0-1.4, x1+1.4, wf-.8, wf+.7, DOOR_TOP, DOOR_TOP+1.4)   # header
        frames.box(x0-.2, x1+.2, wf-.4, wf+.35, DOOR_TOP+1.4, DOOR_TOP+2.0)  # leaf guide housing
        for xa in (x0-.7, x1+.7):                                       # warning lamps on the header
            amber.box(xa-.45, xa+.45, wf+.7, wf+1.1, DOOR_TOP+.5, DOOR_TOP+1.1)
        leaf = Acc()
        ly0, ly1 = DOOR_LEAF_Y
        leaf.box(-DOOR_HALF-.4, DOOR_HALF+.4, ly0-80.4, ly1-80.4, 0.0, DOOR_TOP-FLOOR+.3)
        for z in (1.6, 4.6, 7.6):                                        # stiffeners on its lane face
            leaf.box(-DOOR_HALF+.6, DOOR_HALF-.6, ly1-80.4, ly1-80.4+.35, z, z+.7)
        for x in (-DOOR_HALF/3, DOOR_HALF/3):
            leaf.box(x-.35, x+.35, ly1-80.4, ly1-80.4+.3, .4, DOOR_TOP-FLOOR-.4)
        _keyed(kit, f'Wing {side} transfer door {k} slide', (cx, 80.4, FLOOR), sy,
               [(leaf, f'Wing {side} transfer door {k} leaf', plate)],
               f'transfer door {k}: rests closed; open = translate local Z +{DOOR_OPEN}')
        # The lock: floor plate, wall posts, ceiling lights, and a closed inner door at its inboard end.
        bays.box(x0+.2, x1-.2, LOCK_Y+1.0, DOOR_LEAF_Y[0]-.1, FLOOR-.2, FLOOR+.08)
        for xa, xb in ((x0-.05, x0+.3), (x1-.3, x1+.05)):                 # wall linings
            bays.box(xa, xb, LOCK_Y-.2, DOOR_LEAF_Y[0]-.1, FLOOR, DOOR_TOP+.05)
        bays.box(x0, x1, LOCK_Y-.2, DOOR_LEAF_Y[0]-.1, DOOR_TOP-.05, DOOR_TOP+.3)   # ceiling lining
        y = LOCK_Y+4.0
        while y < 78:
            for a, b in ((x0, x0+.6), (x1-.6, x1)):
                bays.box(a, b, y-.45, y+.45, FLOOR, DOOR_TOP)
            bays.box(x0, x1, y-.4, y+.4, DOOR_TOP-.5, DOOR_TOP)        # ceiling frame
            y += 6.0
        for xl in (cx-9.0, cx+9.0):
            lights.box(xl-.45, xl+.45, LOCK_Y+2.0, 78.5, DOOR_TOP-.25, DOOR_TOP+.02)
        inner.box(x0-.3, x1+.3, LOCK_Y-1.0, LOCK_Y, FLOOR-.2, DOOR_TOP+.3)
        for z in (2.0, 5.0, 8.0):
            inner.box(x0+.8, x1-.8, LOCK_Y, LOCK_Y+.35, FLOOR+z, FLOOR+z+.6)
        inner.box(cx-.25, cx+.25, LOCK_Y, LOCK_Y+.4, FLOOR, DOOR_TOP)        # the leaves' meeting seam
        ib = inner.build(kit, f'Wing {side} transfer bay {k} inner door', steel, sy, level=True)
        inner = Acc()
        # Traverser rails from outboard of the launch points, across the lane, through the door to the inner door.
        rails = Acc()
        for dx in RAIL_DX:
            rails.box(cx+dx-.4, cx+dx+.4, RAIL_Y[0], RAIL_Y[1], FLOOR+.05, FLOOR+.4)
        rails.build(kit, f'Wing {side} traverser {k} rails', steel, sy, level=True)
        # The traverser deck, parked at its door; it moves along local Y.
        trav = Acc()
        trav.box(-13.0, 13.0, -6.5, 6.5, .35, 1.0)
        trav.box(-12.0, 12.0, -5.8, 5.8, 1.0, 1.25)
        for dx in RAIL_DX:
            for dy in (-5.0, 5.0):
                trav.box(dx-.7, dx+.7, dy-1.0, dy+1.0, .0, .55)
        for dx in (-6.0, 6.0):                                           # wheel chocks / gear cradles
            trav.box(dx-1.6, dx+1.6, -1.0, 1.0, 1.25, 1.75)
        _keyed(kit, f'Wing {side} traverser {k} slide', (cx, wall_face(cx)+7.6, FLOOR), sy,
               [(trav, f'Wing {side} traverser {k}', steel)],
               f'traverser {k}: rests at its door; travel along local Y')
    frames.build(kit, f'Wing {side} transfer door frames', steel, sy, level=True)
    amber.build(kit, f'Wing {side} transfer door lamps', mats['amber'], sy, level=True)
    bays.build(kit, f'Wing {side} transfer bays', plate, sy, level=True)
    lights.build(kit, f'Wing {side} transfer bay lights', work, sy, level=True)

    # Service trench along both walls (cut into the pod floor), with its lines and service pedestals.
    trench_cut, lines, peds = Acc(), Acc(), Acc()
    runs = []
    gaps = sorted([(c-DOOR_HALF-1.6, c+DOOR_HALF+1.6) for c in DOOR_X] + [(BARRIER['x']-2.4, BARRIER['x']+2.4)])
    x = -62.5
    for a, b in gaps + [(96.0, 96.0)]:
        if a-x > 3:
            runs.append((x, a, wall_face(x), wall_face(x)+TRENCH_W))
        x = b
    xr = _edge_x(lead, 121.0)-7.0
    bx = BARRIER['x']
    runs += [(-62.5, bx-2.4, 128.0-TRENCH_W, 128.0), (bx+2.4, 38.0, 128.0-TRENCH_W, 128.0),
             (42.0, xr, 123.0-TRENCH_W, 123.0)]
    for x0, x1, y0, y1 in runs:
        trench_cut.box(x0, x1, y0, y1, FLOOR-TRENCH_D, FLOOR+.5)
        yc = (y0+y1)/2
        lines.rod_x(x0+.3, x1-.3, yc-.6, FLOOR-TRENCH_D+.45, .35)       # fuel
        lines.rod_x(x0+.3, x1-.3, yc+.15, FLOOR-TRENCH_D+.4, .3)        # coolant
        lines.box(x0+.3, x1-.3, yc+.55, yc+1.05, FLOOR-TRENCH_D, FLOOR-.5)   # ordnance feed conveyor
        xs = x0+4.0
        while xs < x1-3.0:                                              # trench bridges / grating bars
            lines.box(xs-.25, xs+.25, y0-.1, y1+.1, FLOOR-.25, FLOOR-.02)
            xs += 6.0
    for x in (-50.0, -26.0, -2.0, 22.0, 46.0, 64.0):                    # service pedestals on the outboard wall
        y1 = 128.0 if x < 38 else 123.0
        if x > xr-2:
            continue
        peds.box(x-.9, x+.9, y1-TRENCH_W-1.7, y1-TRENCH_W-.2, FLOOR, FLOOR+2.6)
        peds.box(x-1.2, x+1.2, y1-TRENCH_W-1.9, y1-TRENCH_W-.1, FLOOR+2.6, FLOOR+3.1)
        lines.rod_x(x-.7, x+.7, y1-TRENCH_W-2.0, FLOOR+1.6, .55)          # hose reel
    lines.build(kit, f'Wing {side} service lines', steel, sy, level=True)
    peds.build(kit, f'Wing {side} service pedestals', mats['tan'], sy, level=True)

    # Gantry rails under the roof and the travelling crane (keyable).
    grail = Acc()
    for y in GANTRY_Y:
        xe = min(_edge_x(lead, y)-6.0, 96.0)
        r = roof_z(y)
        grail.box(-62.0, xe, y-.25, y+.25, r-GANTRY_DEPTH+.3, r+.2)        # web
        grail.box(-62.0, xe, y-.7, y+.7, r-GANTRY_DEPTH, r-GANTRY_DEPTH+.3)  # running flange
        x = -58.0
        while x < xe:
            grail.box(x-.4, x+.4, y-.9, y+.9, r-.35, r+.2)                 # hangers to the roof frames
            x += 12.0
    grail.build(kit, f'Wing {side} gantry rails', steel, sy, level=True)
    cz = -13.0
    yc = sum(GANTRY_Y)/2
    crane = Acc()
    crane.box(-1.0, 1.0, GANTRY_Y[0]-1.5-yc, GANTRY_Y[1]+1.5-yc, -1.2, 0.0)       # bridge girder
    crane.box(-.4, .4, GANTRY_Y[0]-1.5-yc, GANTRY_Y[1]+1.5-yc, 0.0, .6)
    for y in GANTRY_Y:                                                              # end trucks up to the rails
        top = roof_z(y)-GANTRY_DEPTH-cz
        crane.box(-2.6, 2.6, y-yc-.9, y-yc+.9, -.4, top)
    ty = GANTRY_Y[0]+4.0-yc                                                         # hoist trolley, near the inboard rail
    crane.box(-1.7, 1.7, ty-1.6, ty+1.6, -2.4, -1.2)
    crane.box(-.12, .12, ty-.12, ty+.12, -3.4, -2.4)
    crane.box(-.8, .8, ty-.8, ty+.8, -4.4, -3.4)                                    # hook block
    _keyed(kit, f'Wing {side} gantry crane slide', (CRANE_PARK_X, yc, cz), sy,
           [(crane, f'Wing {side} gantry crane', mats['tan'])],
           'gantry crane: parked amidships; travel along local X')

    # Recovery barrier: fixed stanchions, and the net frame stowed flat on a hinge empty.
    bx = BARRIER['x']
    by0, by1 = BARRIER['y']
    posts = Acc()
    for a, b in ((by0-1.2, by0), (by1, by1+1.2)):
        posts.box(bx-.8, bx+.8, a, b, FLOOR, FLOOR+9.6)
        posts.box(bx-1.6, bx+1.6, a-.3, b+.3, FLOOR, FLOOR+1.4)                 # winch housing
    posts.build(kit, f'Wing {side} recovery barrier posts', steel, sy, level=True)
    net = Acc()
    L = BARRIER['length']
    hw = (by1-by0)/2
    net.box(0.0, .7, -hw, hw, .0, .55)                                            # hinge tube
    net.box(L-.6, L, -hw, hw, .0, .5)                                             # top beam
    for s in (-1, 1):
        net.box(0.0, L, s*hw-(.6 if s > 0 else 0), s*hw+(0 if s > 0 else .6), .0, .5)
    y = -hw+2.4
    while y < hw-1.5:
        net.box(.7, L-.6, y-.18, y+.18, .1, .3)                                   # straps
        y += 2.6
    for xs in (3.0, 6.0):
        net.box(xs-.1, xs+.1, -hw, hw, .1, .3)
    _keyed(kit, f'Wing {side} recovery barrier hinge', (bx, (by0+by1)/2, FLOOR+.1), sy,
           [(net, f'Wing {side} recovery barrier', mats['tan'])],
           'recovery barrier: rests stowed flat; raise = rotate about local Y by -90 deg')

    # Blast deflectors behind each station: hinged panels lying flat, keyable; fixed hinge housings.
    for slot in LAUNCH_BAYS:
        s, end = bay_of(slot)
        if s != side:
            continue
        hx, sgn = DEFLECT[end]
        y0, y1 = DEFLECT_Y
        pan, hous = Acc(), Acc()
        yc2 = (y0+y1)/2
        w = (y1-y0)/3
        for j in range(3):
            a, b = y0+j*w+.3-yc2, y0+(j+1)*w-.3-yc2
            xa, xb = sorted((0.0, sgn*DEFLECT_LEN))
            pan.box(xa, xb, a, b, .0, .45)
            for f in (.3, .55, .8):                                                # cooling channels
                xf = sgn*DEFLECT_LEN*f
                pan.box(xf-.2, xf+.2, a+.4, b-.4, .45, .65)
        for j in range(4):
            yy = y0+j*w
            hous.box(hx-.8, hx+.8, yy-.6, yy+.6, FLOOR, FLOOR+1.0)
            hous.box(hx+sgn*1.0-.6, hx+sgn*1.0+.6, yy-.35, yy+.35, FLOOR, FLOOR+.7)   # ram foot
        hous.build(kit, f"Flight deck {slot['id']} blast deflector housings", steel, sy, level=True)
        _keyed(kit, f"Flight deck {slot['id']} blast deflector hinge", (hx, yc2, FLOOR+.1), sy,
               [(pan, f"Flight deck {slot['id']} blast deflectors", steel)],
               'blast deflectors: rest flat; raise = rotate about local Y by %+d deg' % (-50*sgn))

        # Glazed launch-control booth just inside the mouth, on the inboard wall, above head height.
        wf = wall_face(-60 if end == 'aft' else 85)
        bx0, bx1 = (-62.0, -51.0) if end == 'aft' else (79.0, 90.0)
        sec = [(wf-.2, -15.6), (wf+3.2, -15.6), (wf+4.6, -13.4), (wf+4.0, -11.2), (wf+2.6, -10.4),
               (wf-.2, -10.4)]
        body, glaze, mull = Acc(), Acc(), Acc()
        body.loft_x(bx0, bx1, sec)
        body.box(bx0+.5, bx1-.5, wf-.2, wf+3.0, -16.4, -15.6)                       # floor sill
        for xb in (bx0+1.0, bx1-1.0):                                                # brackets to the wall
            body.box(xb-.3, xb+.3, wf-.2, wf+2.2, -18.0, -16.4)
        def offset(p, q, o):
            (ya, za), (yb, zb) = p, q
            ny, nz = zb-za, -(yb-ya)
            n = math.hypot(ny, nz)
            return [(ya+ny/n*o, za+nz/n*o), (yb+ny/n*o, zb+nz/n*o)]
        for p, q in ((sec[1], sec[2]), (sec[2], sec[3])):                          # canted windows
            (ya, za), (yb, zb) = offset(p, q, .05)
            (yc_, zc), (yd, zd) = offset(p, q, .2)
            glaze.add([(x, yy, zz) for x in (bx0+.4, bx1-.4) for yy, zz in ((ya, za), (yb, zb), (yd, zd), (yc_, zc))],
                      [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)])
            xm = bx0+.4
            while xm < bx1:
                (ya, za), (yb, zb) = offset(p, q, .25)
                (yc_, zc), (yd, zd) = offset(p, q, -.05)
                mull.add([(x, yy, zz) for x in (xm-.15, xm+.15) for yy, zz in ((ya, za), (yb, zb), (yd, zd), (yc_, zc))],
                         [(0, 1, 2, 3), (7, 6, 5, 4), (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0)])
                xm += (bx1-bx0-.8)/4
        xe = bx0-.05 if end == 'aft' else bx1+.05                                   # end window toward the mouth
        glaze.box(min(xe, xe-.15*(1 if end == 'aft' else -1)), max(xe, xe-.15*(1 if end == 'aft' else -1)),
                  wf+.6, wf+3.6, -14.8, -11.2)
        name = f"Flight deck {slot['id']}"
        body.build(kit, name+' control booth', plate, sy, level=True)
        glaze.build(kit, name+' control booth glazing', glass, sy, level=True)
        mull.build(kit, name+' control booth mullions', steel, sy, level=True)

    # Crew catwalk on the outboard wall, with railings, ladders and personnel hatches.
    cw, rail, hatch, hdoor = Acc(), Acc(), Acc(), Acc()
    zt = CATWALK_Z
    xr2 = _edge_x(lead, 121.5)-7.0
    for x0, x1, yw in ((-60.0, 40.4, 128.0), (40.4, xr2, 123.0)):
        cw.box(x0, x1, yw-CATWALK_W, yw, zt-.4, zt)
        x = x0+1.5
        while x < x1-.5:
            rail.box(x-.12, x+.12, yw-CATWALK_W, yw-CATWALK_W+.24, zt, zt+1.8)    # stanchions
            if (x-x0) % 6.0 < 3.0:
                rail.box(x-.25, x+.25, yw-1.6, yw, zt-1.9, zt-.4)                  # brackets
            x += 3.0
        rail.box(x0, x1, yw-CATWALK_W, yw-CATWALK_W+.24, zt+1.65, zt+1.85)          # handrail
        rail.box(x0, x1, yw-CATWALK_W, yw-CATWALK_W+.2, zt+.85, zt+1.0)             # mid rail
    cw.box(38.0, 42.8, 123.0-CATWALK_W, 128.0, zt-.4, zt)                           # landing at the wall step
    for x, yw in ((-44.0, 128.0), (2.0, 128.0), (30.0, 128.0), (60.0, 123.0)):     # ladders, floor to catwalk
        if x > xr2-2:
            continue
        ye = yw-CATWALK_W
        for xs in (x-.75, x+.75):
            rail.box(xs-.12, xs+.12, ye-.5, ye-.25, FLOOR, zt+1.8)
        z = FLOOR+.8
        while z < zt:
            rail.box(x-.75, x+.75, ye-.45, ye-.3, z, z+.15)
            z += 1.0
    for x, yw in ((-52.0, 128.0), (-22.0, 128.0), (16.0, 128.0), (52.0, 123.0)):   # personnel hatches
        if x > xr2-3:
            continue
        hatch.box(x-1.7, x-1.2, yw-.25, yw, zt, zt+3.9)
        hatch.box(x+1.2, x+1.7, yw-.25, yw, zt, zt+3.9)
        hatch.box(x-1.7, x+1.7, yw-.25, yw, zt+3.4, zt+3.9)
        hdoor.box(x-1.2, x+1.2, yw-.12, yw, zt, zt+3.4)
    for x in (-17.0, 31.0):                                                   # inboard hatches to the gallery
        wf = wall_face(x)
        hatch.box(x-1.7, x+1.7, wf, wf+.25, FLOOR+3.4, FLOOR+3.9)
        for s in (-1, 1):
            hatch.box(x+s*1.45-.25, x+s*1.45+.25, wf, wf+.25, FLOOR, FLOOR+3.9)
        hdoor.box(x-1.2, x+1.2, wf, wf+.12, FLOOR, FLOOR+3.4)
    cw.build(kit, f'Wing {side} catwalk', deck, sy, level=True)
    rail.build(kit, f'Wing {side} catwalk railings', steel, sy, level=True)
    hatch.build(kit, f'Wing {side} personnel hatch frames', steel, sy, level=True)
    hdoor.build(kit, f'Wing {side} personnel hatch doors', dark, sy, level=True)

    # Ordnance and crew lifts in the elevator wells, parked at the roof.
    for slot in LAUNCH_BAYS:
        s, end = bay_of(slot)
        if s != side:
            continue
        d = DECKS[end]
        wx0, wx1 = d['open_above']
        wy0, wy1 = d['well_y']
        lift, guides = Acc(), Acc()
        z0, z1 = LIFT_Z
        lift.box(wx0+.4, wx1-.4, wy0+.4, wy1-.4, z0, z1)
        lift.box(wx0+.4, wx1-.4, wy0+.4, wy0+.9, z1, z1+.6)                            # rims
        lift.box(wx0+.4, wx1-.4, wy1-.9, wy1-.4, z1, z1+.6)
        for k in range(3):                                                               # ordnance pallets
            yp = wy0+4.0+k*5.0
            lift.box(wx0+3.0, wx1-9.0, yp-1.2, yp+1.2, z1, z1+.5)
            for xm in range(int(wx0+4), int(wx1-10), 3):
                lift.rod_x(xm, xm+2.4, yp-.5, z1+1.0, .45, n=6)
                lift.rod_x(xm, xm+2.4, yp+.5, z1+1.0, .45, n=6)
        cx0 = wx1-7.0                                                                    # crew cage
        for xa in (cx0, wx1-1.0):
            for ya in (wy1-6.0, wy1-1.0):
                lift.box(xa-.15, xa+.15, ya-.15, ya+.15, z1, z1+3.6)
        lift.box(cx0-.15, wx1-.85, wy1-6.15, wy1-.85, z1+3.4, z1+3.6)
        for xa in (wx0+.1, wx1-.9):
            for ya in (wy0+.1, wy1-.9):
                guides.box(xa, xa+.8, ya, ya+.8, z0, deck_z(ya+.4)-2.8)
        name = f"Flight deck {slot['id']}"
        lift.build(kit, name+' well lift', mats['tan'], sy, level=True)
        guides.build(kit, name+' well lift guides', steel, sy, level=True)

    cut = trench_cut
    return cut


def transfer_gallery(kit, side, sy, mats, targets):
    """The pressure trunk on the lower flank and the locks cut through it into the keel (port values)."""
    gx0, gx1 = GALLERY['x']
    gz0, gz1 = GALLERY['z']

    def keel_y(x):
        return _edge_y(KEEL_WALL, x)

    def inner_y(x):
        return max(GALLERY['min_y'], keel_y(x)-1.0)

    pts = [(gx0, inner_y(gx0)), (70.0, inner_y(70.0)), (gx1, inner_y(gx1)), (gx1, GALLERY['y_out']),
           (gx0, GALLERY['y_out'])]
    plan = ccw([(x, sy*y) for x, y in pts])
    ch = GALLERY['chamfer']
    gal = lv(prism(kit, f'Wing {side} transfer gallery', plan, [(ch, gz0), (0, gz0+ch), (0, gz1)], kit.plate, bevel=.1))
    # The locks: one bay per transfer door through the gallery, the pod's inboard wall and into the keel.
    cut_body, cut_keel = Acc(), Acc()
    for cx in DOOR_X:
        cut_body.box(cx-DOOR_HALF, cx+DOOR_HALF, LOCK_CUT[0], LOCK_CUT[1], FLOOR, DOOR_TOP)
        cut_keel.box(cx-DOOR_HALF, cx+DOOR_HALF, LOCK_CUT[0], 70.0, FLOOR-.01, -15.9)
    cb = cut_body.build(kit, f'Temporary Wing {side} transfer lock cutter', kit.dark, sy)
    ck = cut_keel.build(kit, f'Temporary Wing {side} transfer lock keel cutter', kit.dark, sy)
    _cut(gal, cb, keep=True)
    for t in targets['body']:
        _cut(t, cb, keep=True)
    for t in targets['keel']:
        _cut(t, ck, keep=True)
    bpy.data.objects.remove(ck, do_unlink=True)

    # Exterior: ribs round its belly, maintenance hatches between them, lit ports on both chamfers.
    ribs, hatch, hdark, ports = Acc(), Acc(), Acc(), Acc()
    x = gx0+3.0
    k = 0
    while x < gx1-2.0:
        yi = inner_y(x)
        yo = GALLERY['y_out']
        ribs.box(x-.55, x+.55, yi+ch-.2, yo-ch+.2, gz0-.75, gz0+.1)
        for ya, yb, za, zb in ((yi-.05, yi+ch+.05, gz0+ch, gz0), (yo-ch-.05, yo+.05, gz0, gz0+ch)):  # chamfer bands
            # a band lying on the chamfer face, 0.6 proud along its normal
            n = .6/math.sqrt(2)
            dy = -n if yb < 70 else n
            ribs.hexa([(x-.55, ya, za), (x+.55, ya, za), (x+.55, yb, zb), (x-.55, yb, zb),
                       (x-.55, ya+dy, za-n), (x+.55, ya+dy, za-n), (x+.55, yb+dy, zb-n), (x-.55, yb+dy, zb-n)])
        xm = x+3.0
        if xm < gx1-2.0:
            if k % 2 == 0:
                yh = (yi+yo)/2
                hatch.box(xm-1.9, xm+1.9, yh-1.9, yh+1.9, gz0-.35, gz0+.05)
                hdark.box(xm-1.4, xm+1.4, yh-1.4, yh+1.4, gz0-.42, gz0-.3)
                hatch.box(xm-.3, xm+.3, yh-1.0, yh+1.0, gz0-.6, gz0-.4)         # handle
            for ya, za, sgn in ((yi+ch/2, gz0+ch/2, -1), (yo-ch/2, gz0+ch/2, 1)):   # ports on the chamfers
                n = 1/math.sqrt(2)
                u = (sgn*n, -n)          # chamfer outward normal (y, z)
                t = (n, sgn*n)           # along the chamfer
                def pt(xx, a, o):
                    return (xx, ya+t[0]*a+u[0]*o, za+t[1]*a+u[1]*o)
                hdark.hexa([pt(xm-1.0, -.7, -.05), pt(xm+1.0, -.7, -.05), pt(xm+1.0, .7, -.05), pt(xm-1.0, .7, -.05),
                            pt(xm-1.0, -.7, .25), pt(xm+1.0, -.7, .25), pt(xm+1.0, .7, .25), pt(xm-1.0, .7, .25)])
                ports.hexa([pt(xm-.7, -.4, .2), pt(xm+.7, -.4, .2), pt(xm+.7, .4, .2), pt(xm-.7, .4, .2),
                            pt(xm-.7, -.4, .32), pt(xm+.7, -.4, .32), pt(xm+.7, .4, .32), pt(xm-.7, .4, .32)])
        x += 6.0
        k += 1
    for xe, s in ((gx0, -1), (gx1, 1)):                                          # end bulkheads: a lit port
        yi = inner_y(xe)
        yo = GALLERY['y_out']
        ym = (yi+yo)/2
        a, b = sorted((xe-s*.05, xe+s*.15))
        hdark.box(a, b, ym-1.4, ym+1.4, -24.6, -21.8)
        a, b = sorted((xe, xe+s*.25))
        ports.box(a, b, ym-1.0, ym+1.0, -24.2, -22.2)
        for zz in (gz0+ch+.6, -19.4):                                             # bulkhead frame bands
            a, b = sorted((xe-s*.05, xe+s*.5))
            ribs.box(a, b, yi+.3, yo-.3, zz-.4, zz+.4)
    ribs.build(kit, f'Wing {side} transfer gallery ribs', kit.plate, sy, level=True)
    hatch.build(kit, f'Wing {side} transfer gallery hatches', kit.steel, sy, level=True)
    hdark.build(kit, f'Wing {side} transfer gallery recesses', kit.dark, sy, level=True)
    ports.build(kit, f'Wing {side} transfer gallery ports', mats['port'], sy, level=True)
    return gal, cb


def pod_underside(kit, side, sy, pod_plan_port, mats):
    """Stepped armour courses, keel girders, service bays, heat exchangers, clamps and blisters on the
    pod's level bottom (port values). Returns the service-bay cutter for the pod."""
    PB = POD[0]
    edge = inset(pod_plan_port, POD_CHAMFER+.6)

    def ok(x0, x1, y0, y1):
        return all(_inside(p, edge) for p in ((x0, y0), (x1, y0), (x1, y1), (x0, y1)))

    courses = [((84.6, 97.0), .8, False), ((99.0, 115.0), 1.0, True), ((117.0, 126.0), .8, False)]
    bays = [(-30.0, -20.0, 'out'), (14.0, 24.0, 'out'), (6.0, 16.0, 'in'), (56.0, 64.0, 'in')]
    rads = [(-56.0, -20.0, 'in'), (32.0, 48.0, 'out')]
    clamps = [-45.0, 5.0, 55.0]

    def blocked(x0, x1, which):
        for a, b, w in bays+rads:
            if w == which and x0 < b+.8 and x1 > a-.8:
                return True
        return False

    plates, back, step, gird, bayc, bayd, rad, clamp, blis, lens = (Acc() for _ in range(10))
    for (y0, y1), proud, stepped in courses:
        which = 'in' if y1 < 98 else 'out' if y0 > 116 else 'mid'
        x = -60.0
        while x < 112.0:
            x0, x1 = x+SEAM/2, x+14.0-SEAM/2
            x += 14.0
            if not ok(x0, x1, y0, y1):
                # the leading edge: a short closing plate where a full one will not fit
                xs = x0
                while xs < x1 and ok(xs, xs+3.0, y0, y1):
                    xs += 3.0
                if xs-x0 < 3.0:
                    continue
                x1 = xs
            if which != 'mid' and blocked(x0, x1, which):
                continue
            if which == 'mid' and any(x0 < c+4.0 and x1 > c-4.0 for c in clamps):
                # split round the clamp pad
                for c in clamps:
                    if x0 < c+4.0 and x1 > c-4.0:
                        segs = [(x0, c-3.6), (c+3.6, x1)]
                        break
            else:
                segs = [(x0, x1)]
            for a, b in segs:
                if b-a < 2.0:
                    continue
                plates.box(a, b, y0+.2, y1-.2, PB-proud, PB+.15)
                if stepped:
                    step.hexa([(a+1.4, y0+1.6, PB-proud-.7), (b-1.4, y0+1.6, PB-proud-.7),
                               (b-1.4, y1-1.6, PB-proud-.7), (a+1.4, y1-1.6, PB-proud-.7),
                               (a+.9, y0+1.1, PB-proud+.05), (b-.9, y0+1.1, PB-proud+.05),
                               (b-.9, y1-1.1, PB-proud+.05), (a+.9, y1-1.1, PB-proud+.05)])
        # dark backing under the course so its seams read as gaps
        xa, xb = -60.0, -60.0
        while ok(xb+2.0, xb+4.0, y0, y1) and xb < 112:
            xb += 2.0
        back.box(xa+.2, xb, y0+.4, y1-.4, PB-.35, PB+.1)
    # keel girders in the course seams, with cross girders at the clamps
    for yg in (98.0, 116.0):
        xb = -59.0
        while ok(xb+2.0, xb+4.0, yg-1, yg+1) and xb < 112:
            xb += 2.0
        gird.box(-59.0, xb, yg-.7, yg+.7, PB-2.6, PB+.1)
        gird.box(-59.0, xb, yg-1.1, yg+1.1, PB-2.6, PB-2.2)                    # bottom flange
    for c in clamps:
        if not ok(c-4, c+4, 99, 115):
            continue
        gird.box(c-.7, c+.7, 98.0, 116.0, PB-2.0, PB+.1)
        clamp.box(c-3.2, c+3.2, 103.8, 110.2, PB-2.6, PB-1.6)                   # hardpoint pad
        for ya, yb in ((102.6, 103.8), (110.2, 111.4)):                         # clamp jaws
            clamp.box(c-1.6, c+1.6, ya, yb, PB-3.8, PB-1.0)
            clamp.box(c-1.6, c+1.6, ya+(.0 if ya < 105 else -.5), yb+(.5 if ya < 105 else 0), PB-3.8, PB-3.3)
        bayd.box(c-1.2, c+1.2, 105.8, 108.2, PB-2.75, PB-2.5)                   # socket
    # recessed service bays (cut into the pod), lined dark, with lines and a work lamp
    for a, b, which in bays:
        y0, y1 = (86.0, 96.0) if which == 'in' else (118.0, 125.0)
        if not ok(a, b, y0, y1):
            continue
        bayc.box(a, b, y0, y1, PB-.5, PB+1.6)
        bayd.box(a+.1, b-.1, y0+.1, y1-.1, PB+1.35, PB+1.5)
        for yy in (y0+1.5, y0+3.0):
            gird.rod_x(a+.4, b-.4, yy, PB+.9, .3, n=6)
        gird.box(a+1.5, a+3.5, y1-2.6, y1-.6, PB+.2, PB+1.4)
        lens.box((a+b)/2-.4, (a+b)/2+.4, y1-.5, y1-.3, PB+.9, PB+1.3)
        for xr in (a, b):                                                       # proud coaming
            gird.box(xr-.5, xr+.5, y0-.5, y1+.5, PB-.7, PB+.1)
        for yr in (y0, y1):
            gird.box(a-.5, b+.5, yr-.5, yr+.5, PB-.7, PB+.1)
    # heat exchanger panels: a frame with fins across it
    for a, b, which in rads:
        y0, y1 = (85.4, 96.2) if which == 'in' else (117.6, 125.4)
        if not ok(a, b, y0, y1):
            b2 = a
            while ok(a, b2+2, y0, y1):
                b2 += 2
            b = b2
        if b-a < 6:
            continue
        for xr in (a, b):
            rad.box(xr-.5, xr+.5, y0, y1, PB-1.2, PB+.1)
        for yr in (y0, y1):
            rad.box(a, b, yr-.5, yr+.5, PB-1.2, PB+.1)
        back.box(a+.5, b-.5, y0+.5, y1-.5, PB-.3, PB+.1)
        f = a+1.1
        while f < b-.8:
            rad.box(f-.15, f+.15, y0+.5, y1-.5, PB-1.05, PB-.25)
            f += .8
        rad.box(a+.5, b-.5, (y0+y1)/2-.3, (y0+y1)/2+.3, PB-1.2, PB-.25)       # header pipe
    # sensor and light blisters
    for x, y, lit in ((-50.0, 121.5, True), (0.0, 121.5, False), (66.0, 112.0, True), (-6.0, 90.5, False),
                      (36.0, 90.5, True)):
        zb = PB-.8 if (y < 98 or y > 116) else PB-1.0
        blis.frustum(x, y, zb+.05, zb-.7, 1.5, 1.1)
        blis.frustum(x, y, zb-.7, zb-1.2, 1.1, .6)
        if lit:
            lens.frustum(x, y, zb-1.15, zb-1.35, .55, .35, n=6)
        else:
            bayd.frustum(x, y, zb-1.15, zb-1.35, .55, .35, n=6)
    plates.build(kit, f'Wing {side} pod armour courses', kit.plate, sy, level=True)
    step.build(kit, f'Wing {side} pod armour course steps', mats['tan'], sy, level=True)
    back.build(kit, f'Wing {side} pod armour backing', kit.dark, sy, level=True)
    gird.build(kit, f'Wing {side} pod keel girders', kit.steel, sy, level=True)
    clamp.build(kit, f'Wing {side} pod docking clamps', kit.steel, sy, level=True)
    bayd.build(kit, f'Wing {side} pod service bay linings', kit.dark, sy, level=True)
    rad.build(kit, f'Wing {side} pod heat exchangers', kit.steel, sy, level=True)
    blis.build(kit, f'Wing {side} pod blisters', kit.bone, sy, level=True)
    lens.build(kit, f'Wing {side} pod blister lamps', mats['port'], sy, level=True)
    return bayc


def build(kit):
    mats = {'tan': bpy.data.materials.get('XIV tan hull plating') or
            material('XIV tan hull plating', (.36, .30, .24), .3, .62),
            'amber': bpy.data.materials.get('Hangar amber lamp') or
            material('Hangar amber lamp', (.1, .05, .01), 0, .4, emission=(1.0, .55, .12), strength=10.0),
            'work': bpy.data.materials.get('Hangar work light') or
            material('Hangar work light', (.2, .2, .18), 0, .4, emission=(1.0, .86, .66), strength=8.0),
            'port': bpy.data.materials.get('Hangar gallery port') or
            material('Hangar gallery port', (.1, .1, .08), 0, .3, emission=(1.0, .8, .5), strength=6.0)}
    plan_port = ccw(SPONSON_PORT)
    core_in = inset(plan_port, CORE)
    core_port = [core_in[i] if i in CORE_VERTS else p for i, p in enumerate(plan_port)]
    # The hangar's front follows the armour core, so its forward mouth opens behind the lintel and sill.
    hpod_port = [core_in[i] if i <= CHAIN_LEAD[1] else p for i, p in enumerate(plan_port)]
    hull = {n: bpy.data.objects.get(n) for n in ('Hull fore body', 'Hull aft body', 'Hull ventral keel',
                                                 'Hull ventral keel upper plate course',
                                                 'Hull ventral keel lower plate course')}
    targets = {'body': [hull[n] for n in ('Hull fore body', 'Hull aft body') if hull[n]],
               'keel': [hull[n] for n in ('Hull ventral keel', 'Hull ventral keel upper plate course',
                                          'Hull ventral keel lower plate course') if hull[n]]}
    for side in ('port', 'starboard'):
        sy = 1 if side == 'port' else -1
        flip = (lambda p: p) if side == 'port' else mirror_y
        plan, core = flip(plan_port), flip(core_port)
        sponson = prism(kit, f'Wing {side} sponson', core, SPONSON, kit.plate)
        a = math.radians(TAPER[1])
        plane_cut(sponson, f'Wing {side} sponson underside taper', (0, sy*TAPER[0], KEEL),
                  (0, sy*math.sin(a), -math.cos(a)), size=600)
        kit.hull.append(sponson)
        # Outboard armour, built on the port plan and mirrored (sheared with the arm).
        accs = [Acc() for _ in range(5)]
        armour(Edge(plan_port), accs)
        accs[0].build(kit, f'Wing {side} armour plates', kit.plate, sy, hull=True)
        accs[1].build(kit, f'Wing {side} armour plates tan', mats['tan'], sy, hull=True)
        accs[2].build(kit, f'Wing {side} armour backing', kit.dark, sy, hull=True)
        accs[3].build(kit, f'Wing {side} armour frames', kit.steel, sy, hull=True)
        accs[4].build(kit, f'Wing {side} armour hatch doors', kit.dark, sy)
        under, soffit, srad = Acc(), Acc(), Acc()
        under_structure(under, plan_port, soffit, srad)
        under.build(kit, f'Wing {side} underside girders', kit.steel, sy)
        soffit.build(kit, f'Wing {side} arm underside plates', kit.plate, sy)
        srad.build(kit, f'Wing {side} arm underside heat exchanger', kit.steel, sy)
        # The one fly-through hangar under the arm, clipped to the arm's plan, open at both ends.
        # Built level in its final position: bottom level, top sunk into the arm and following its droop.
        px0, px1, py0, py1 = HANGAR['pod']
        pod_port = _blunt(ccw(clip_rect(hpod_port, px0, px1, py0, py1)))
        pod_plan = ccw([(x, sy*y) for x, y in pod_port])
        hangar = lv(prism(kit, f'Wing {side} hangar', pod_plan,
                          [(POD_CHAMFER, POD[0]), (0, POD[0]+POD_CHAMFER), (0, POD[1])], kit.plate))
        for v in hangar.data.vertices:
            if v.co.z > POD[1]-.01:
                v.co.z += droop(v.co.y)
        hangar.data.update()
        for tx0, tx1, ty0, ty1 in HANGAR['tunnel']:
            t = Acc()
            t.slope(tx0, tx1, ty0, ty1, FLOOR, roof_z)
            _cut(hangar, t.build(kit, 'Temporary tunnel', kit.dark, sy))
        transfer_gallery_obj, lock_cutter = transfer_gallery(kit, side, sy, mats, targets)
        _cut(hangar, lock_cutter)
        hangar_shell_detail(kit, side, sy, plan_port)
        trench = lane_gear(kit, side, sy, plan_port, mats)
        _cut(hangar, trench.build(kit, 'Temporary trench', kit.dark, sy))
        bayc = pod_underside(kit, side, sy, pod_port, mats)
        if bayc.faces:
            _cut(hangar, bayc.build(kit, 'Temporary service bays', kit.dark, sy))
        for slot in LAUNCH_BAYS:
            s, end = bay_of(slot)
            if s != side:
                continue
            d = DECKS[end]
            name = f"Flight deck {slot['id']}"
            # The elevator well through the arm into the hangar (the sprite's red-lit recess).
            ox0, ox1 = d['open_above']
            wlo, whi = sorted((sy*d['well_y'][0], sy*d['well_y'][1]))
            well = kit.box('Temporary well', ((ox0+ox1)/2, (wlo+whi)/2, (KEEL+30)/2),
                           (ox1-ox0, whi-wlo, 30-KEEL+.5), kit.dark, bevel=0)
            boolean(sponson, well)
            pwell = kit.box('Temporary pod well', ((ox0+ox1)/2, (wlo+whi)/2, 10.0),
                            (ox1-ox0, whi-wlo, 2*(10.0-roof_z(d['well_y'][1])+1.0)), kit.dark, bevel=0)
            _cut(hangar, pwell)
            # This slot's deck plate on the hangar floor, then the red light strips along it.
            dx0, dx1 = d['deck_x']
            dlo, dhi = sorted((sy*d['deck_y'][0], sy*d['deck_y'][1]))
            floor = ccw(clip_rect(plan, dx0+.5, dx1, dlo+.2, dhi-.2))
            lv(prism(kit, name+' deck', floor, [(0, FLOOR-.8), (0, FLOOR+.1)], kit.deck, bevel=.05))
            for k in range(LIGHT_ROWS):
                ly = wlo+(k+1)*(whi-wlo)/(LIGHT_ROWS+1)
                lv(kit.box(f'{name} red light {k+1}', ((ox0+ox1)/2, ly, FLOOR+.18), (ox1-ox0-4, .7, .15),
                           kit.bay_light, bevel=0))
            deck_detail(kit, name, end, sy, plan_port, mats)
            rec = deck_record(slot)
            lv(kit.empty(name+' mouth', tuple(rec['mouth']['centre']),
                         math.degrees(math.atan2(rec['mouth']['normal'][1], rec['mouth']['normal'][0]))))
            for k, p in enumerate(rec['launch_points'], 1):
                lv(kit.empty(f'{name} launch point {k}', tuple(p), slot['angle']))
        kit.hull.append(hangar)
    print_fit_table(fit_table())
