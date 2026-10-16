"""The two wing arms and the four hangars slung beneath them.

Owner review of the first blockout asked for wing-like arms off a thick torso
with the hangar bays under the wings. Each sponson (Y 74-147 at the beam) is
now a thin arm held out from the torso a step below its deck (z -8..4). Under it, where legion_xiv.png shows
its red-lit recesses, hang two hangars, which are the .ship's four hidden
LAUNCH_BAY slots. Each hangar is a tunnel inside a pod, and the sprite's
red-lit recess is an elevator well opening through the arm into the hangar
below:
- the forward hangar (X 44-118, Y 86-122) runs forward to a mouth in the pod's
  angled front, under the arm's leading edge (X 100, Y 88 to X 76, Y 118), so
  the mouth faces forward and outboard and fighters launch forward (slot angle
  +-10);
- the aft hangar (X -66 to -12, Y 86-112) is closed at its head (X -12) and
  runs aft to a mouth at X -64, where the notch between the arm's tail leg and
  the engine block opens behind it (slot angle 170 / 190).
Slots and their four launch points come from legion.ship unchanged; the launch
points sit on the hangar floor.
"""
import math
from lg_kit import boolean, ccw, clip_rect, mirror_y, plane_cut, prism, section

KEEL, DECK = -8.0, 4.0       # the arm: its deck is a 6-unit step below the torso's (z 10)
FLOOR = -21.0                # hangar floor
ROOF = -9.0                  # hangar ceiling, under the arm
POD = (-25.0, -7.5)          # hangar pod underside and top (sunk into the arm)

SPONSON_PORT = [(104, 70), (104, 82), (96, 94), (88, 106), (84, 113), (76, 118), (72, 122), (60, 124),
                (52, 126), (40, 131), (28, 135), (20, 138), (-8, 139), (-24, 139), (-40, 142), (-52, 142),
                (-60, 145), (-68, 147), (-72, 146), (-76, 142), (-80, 140), (-92, 140), (-96, 132),
                (-98, 126), (-96, 125), (-92, 120), (-80, 119), (-76, 118), (-72, 113), (-68, 110),
                (-64, 108), (-64, 88), (-60, 76), (84, 74), (96, 72)]
SPONSON = section(KEEL, DECK, 4.0, 2.5)
TAPER = (84.0, 6.0)          # the arm's underside rises outboard of |Y| 84 at 6 degrees

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
# Port-side hangar geometry; starboard hangars mirror it.
# pod: the hangar pod's X and Y extent (clipped to the arm's plan); tunnel: its
# hollow, open at the mouth end; open_above: the elevator well through the arm.
DECKS = {
    'forward': {'pod': (44, 118, 86, 122), 'tunnel': (48, 130, 88, 120), 'open_above': (46, 80),
                'mouth': (88.0, 104.0), 'mouth_width': 36.0, 'normal': (.83, .56, 0)},
    'aft': {'pod': (-66, -12, 86, 112), 'tunnel': (-80, -16, 88, 110), 'open_above': (-60, -16),
            'mouth': (-64.0, 99.0), 'mouth_width': 22.0, 'normal': (-1, 0, 0)},
}
LIGHT_ROWS = 4               # red light strips along each hangar (the sprite's red lines)


def bay_of(slot):
    side = 'port' if slot['locations'][1] > 0 else 'starboard'
    end = 'forward' if abs(slot['angle']) < 90 else 'aft'
    return side, end


def deck_record(slot):
    """The hangar as the cinematic needs it: launch points, mouth and direction."""
    side, end = bay_of(slot)
    d = DECKS[end]
    sy = 1 if side == 'port' else -1
    a = math.radians(slot['angle'])
    loc = slot['locations']
    x0, x1, ty0, ty1 = d['tunnel']
    mx, my = d['mouth']
    nx, ny, nz = d['normal']
    name = f"Flight deck {slot['id']}"
    return {
        'slot': slot['id'], 'side': side, 'end': end, 'objects': name+' ...',
        'slot_angle': slot['angle'], 'arc': slot['arc'],
        'launch_direction': [round(math.cos(a), 4), round(math.sin(a), 4), 0.0],
        'recovery_approach': [round(-math.cos(a), 4), round(-math.sin(a), 4), 0.0],
        'launch_points': [[loc[i], loc[i+1], FLOOR+.5] for i in range(0, 8, 2)],
        'launch_point_anchors': [f'{name} launch point {k}' for k in range(1, 5)],
        'mouth': {'anchor': f'{name} mouth', 'centre': [mx, sy*my, (FLOOR+ROOF)/2],
                  'normal': [nx, sy*ny, nz], 'width': d['mouth_width'], 'height': ROOF-FLOOR,
                  'note': 'the hangar is under the wing arm; fighters leave through this mouth'},
        'deck': {'floor_z': FLOOR, 'roof_z': ROOF, 'x': [max(x0, d['pod'][0]), min(x1, d['pod'][1])],
                 'y': sorted((sy*ty0, sy*ty1)), 'under_wing': True,
                 'elevator_well_x': list(d['open_above'])},
    }


def build(kit):
    for side in ('port', 'starboard'):
        plan = ccw(SPONSON_PORT) if side == 'port' else mirror_y(ccw(SPONSON_PORT))
        sponson = prism(kit, f'Wing {side} sponson', plan, SPONSON, kit.plate)
        sy = 1 if side == 'port' else -1
        a = math.radians(TAPER[1])
        plane_cut(sponson, f'Wing {side} sponson underside taper', (0, sy*TAPER[0], KEEL),
                  (0, sy*math.sin(a), -math.cos(a)), size=600)
        kit.hull.append(sponson)
        for slot in LAUNCH_BAYS:
            s, end = bay_of(slot)
            if s != side:
                continue
            d = DECKS[end]
            sy = 1 if side == 'port' else -1
            name = f"Flight deck {slot['id']}"
            # The hangar pod under the arm, clipped to the arm's own plan.
            px0, px1, py0, py1 = d['pod']
            plo, phi = sorted((sy*py0, sy*py1))
            pod = prism(kit, name+' hangar', ccw(clip_rect(plan, px0, px1, plo, phi)),
                        [(2.5, POD[0]), (0, POD[0]+2.5), (0, POD[1])], kit.plate)
            # Its tunnel, open at the mouth end.
            tx0, tx1, ty0, ty1 = d['tunnel']
            tlo, thi = sorted((sy*ty0, sy*ty1))
            tunnel = kit.box('Temporary tunnel', ((tx0+tx1)/2, (tlo+thi)/2, (FLOOR+ROOF)/2),
                             (tx1-tx0, thi-tlo, ROOF-FLOOR), kit.dark, bevel=0)
            boolean(pod, tunnel)
            # The elevator well through the arm into the hangar (the sprite's red-lit recess).
            ox0, ox1 = d['open_above']
            well = kit.box('Temporary well', ((ox0+ox1)/2, (tlo+thi)/2, (ROOF+30)/2),
                           (ox1-ox0, thi-tlo, 30-ROOF+.5), kit.dark, bevel=0)
            boolean(sponson, well, keep=True)
            boolean(pod, well)
            kit.hull.append(pod)
            # Deck plate on the floor, then the red light strips along it.
            floor = ccw(clip_rect(plan, max(tx0, px0)+.5, min(tx1, px1), tlo+.2, thi-.2))
            prism(kit, name+' deck', floor, [(0, FLOOR-.8), (0, FLOOR+.1)], kit.deck, bevel=.05)
            for k in range(LIGHT_ROWS):
                ly = tlo+(k+1)*(thi-tlo)/(LIGHT_ROWS+1)
                kit.box(f'{name} red light {k+1}', ((ox0+ox1)/2, ly, FLOOR+.18), (ox1-ox0-4, .7, .15),
                        kit.bay_light, bevel=0)
            rec = deck_record(slot)
            kit.empty(name+' mouth', tuple(rec['mouth']['centre']),
                      math.degrees(math.atan2(rec['mouth']['normal'][1], rec['mouth']['normal'][0])))
            for k, p in enumerate(rec['launch_points'], 1):
                kit.empty(f'{name} launch point {k}', tuple(p), slot['angle'])
