"""The two outboard sponsons and their four flight decks.

Each sponson (Y 74-147 at the beam) carries two hangar flight decks, the red-lit
recesses of legion_xiv.png, which are the .ship's four hidden LAUNCH_BAY slots:
- the forward deck (X 46-118, Y 88-120) is open above for most of its length
  and runs forward under a hood to a mouth cut through the sponson's angled
  leading edge (X 100, Y 88 to X 76, Y 118), so the mouth faces forward and
  outboard and the deck launches forward (slot angle +-10);
- the aft deck (X -64 to -14, Y 88-110) is open above with a rounded head and
  runs aft onto the ramp at X -64, where the notch between the sponson's tail
  leg and the engine block opens behind it, so it launches aft (slot angle
  170 / 190).
Slots and their four launch points come from legion.ship unchanged.
"""
import math
from lg_kit import boolean, ccw, clip_rect, mirror_y, prism, section

KEEL, DECK = -12.0, 10.0
FLOOR = -6.0                 # flight deck surface
HOOD_Z = (6.0, 14.0)         # forward hood: mouth runs from FLOOR up to its underside

SPONSON_PORT = [(104, 70), (104, 82), (96, 94), (88, 106), (84, 113), (76, 118), (72, 122), (60, 124),
                (52, 126), (40, 131), (28, 135), (20, 138), (-8, 139), (-24, 139), (-40, 142), (-52, 142),
                (-60, 145), (-68, 147), (-72, 146), (-76, 142), (-80, 140), (-92, 140), (-96, 132),
                (-98, 126), (-96, 125), (-92, 120), (-80, 119), (-76, 118), (-72, 113), (-68, 110),
                (-64, 108), (-64, 88), (-60, 76), (84, 74), (96, 72)]
SPONSON = section(KEEL, DECK, 5.0, 2.5)

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
# Port-side deck geometry; starboard decks mirror it.
DECKS = {
    'forward': {'well': (46, 118, 88, 120), 'open_above': (46, 80), 'mouth': (88.0, 104.0), 'mouth_width': 36.0,
                'normal': (.83, .56, 0), 'mouth_h': HOOD_Z[0]-FLOOR,
                'hood': [(78, 86), (102, 86), (96, 94), (88, 106), (84, 113), (78, 120)]},
    'aft': {'well': (-76, -14, 88, 110), 'open_above': (-64, -14), 'mouth': (-64.0, 99.0), 'mouth_width': 22.0,
            'normal': (-1, 0, 0), 'mouth_h': DECK-FLOOR, 'hood': None},
}
LIGHT_ROWS = 4               # red deck light strips along each deck (the sprite's red lines)


def bay_of(slot):
    side = 'port' if slot['locations'][1] > 0 else 'starboard'
    end = 'forward' if abs(slot['angle']) < 90 else 'aft'
    return side, end


def deck_record(slot):
    """The flight deck as the cinematic needs it: launch points, mouth and direction."""
    side, end = bay_of(slot)
    d = DECKS[end]
    sy = 1 if side == 'port' else -1
    a = math.radians(slot['angle'])
    loc = slot['locations']
    x0, x1, wy0, wy1 = d['well']
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
        'mouth': {'anchor': f'{name} mouth', 'centre': [mx, sy*my, FLOOR+d['mouth_h']/2],
                  'normal': [nx, sy*ny, nz], 'width': d['mouth_width'], 'height': d['mouth_h'],
                  'note': 'open above as well over deck.open_above_x; fighters may leave either way'},
        'deck': {'floor_z': FLOOR, 'x': [x0, x1], 'y': sorted((sy*wy0, sy*wy1)),
                 'open_above_x': list(d['open_above'])},
    }


def build(kit):
    for side in ('port', 'starboard'):
        plan = ccw(SPONSON_PORT) if side == 'port' else mirror_y(ccw(SPONSON_PORT))
        sponson = prism(kit, f'Wing {side} sponson', plan, SPONSON, kit.plate)
        kit.hull.append(sponson)
        for slot in LAUNCH_BAYS:
            s, end = bay_of(slot)
            if s != side:
                continue
            d = DECKS[end]
            sy = 1 if side == 'port' else -1
            x0, x1, y0, y1 = d['well']
            yc, yw = sy*(y0+y1)/2, y1-y0
            name = f"Flight deck {slot['id']}"
            cut = kit.box('Temporary well', ((x0+x1)/2, yc, (FLOOR+60)/2), (x1-x0, yw, 60-FLOOR), kit.dark, bevel=0)
            boolean(sponson, cut)
            # Deck plate, then the red light strips running fore and aft along it.
            ylo, yhi = sorted((sy*y0, sy*y1))
            floor = ccw(clip_rect(plan, x0, x1, ylo+.2, yhi-.2))
            prism(kit, name+' deck', floor, [(0, FLOOR-.8), (0, FLOOR+.1)], kit.deck, bevel=.05)
            lx0, lx1 = (d['open_above'][0]+2, d['open_above'][1]-2)
            for k in range(LIGHT_ROWS):
                ly = sy*(y0+(k+1)*yw/(LIGHT_ROWS+1))
                kit.box(f'{name} red light {k+1}', ((lx0+lx1)/2, ly, FLOOR+.18), (lx1-lx0, .7, .15), kit.bay_light, bevel=0)
            if d['hood']:
                hood = [(x, sy*y) for x, y in d['hood']]
                prism(kit, name+' hood', ccw(hood), [(0, HOOD_Z[0]), (0, HOOD_Z[1]-1.5), (1.5, HOOD_Z[1])], kit.plate)
            rec = deck_record(slot)
            kit.empty(name+' mouth', tuple(rec['mouth']['centre']),
                      math.degrees(math.atan2(rec['mouth']['normal'][1], rec['mouth']['normal'][0])))
            for k, p in enumerate(rec['launch_points'], 1):
                kit.empty(f'{name} launch point {k}', tuple(p), slot['angle'])
