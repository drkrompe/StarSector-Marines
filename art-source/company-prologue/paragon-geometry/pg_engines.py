"""The Paragon's ten drive units, at the engine slots of paragon.ship.

Each is a whole drive unit, not a nozzle: a smooth nacelle (`Engine <i> drive
pod`) sunk into the aft pod and standing out past it as one of the castellated
blocks the sprite shows along the stern, an aft-facing bell, its mouth lip and
the dark throat (VFX lights it), and an `Engine <i> exhaust` empty at the mouth
whose +X points along the exhaust. Sizes come from the slot widths (12 for the
four big drives, 8 for the three small ones each side); the small drives sit 7-9
units apart, so their pods touch and read as one block of three.
"""
import math
import pg_kit as K

ENGINES = [  # x, y, width, contrailSize, length, angle (from paragon.ship)
    (-179, 140, 12, 48, 48, 180),
    (-187, 105, 12, 48, 48, 180),
    (-189, 73, 8, 48, 32, 180),
    (-189, 64, 8, 48, 32, 180),
    (-187, 57, 8, 48, 32, 180),
    (-187, -56, 8, 48, 32, 180),
    (-189, -64, 8, 48, 32, 180),
    (-189, -72, 8, 48, 32, 180),
    (-187, -104, 12, 48, 48, 180),
    (-179, -139, 12, 48, 48, 180),
]
POD_FRONT_X = -146.0   # nacelles run forward into the aft pods to here
Z_AXIS = -2.0          # drive centreline height
RECORDS = []


def nacelle_section(hw, hh, n=20):
    """A superellipse YZ section, half-width hw and half-height hh: sleek but full."""
    pts = []
    for k in range(n):
        a = 2*math.pi*k/n
        c, s = math.cos(a), math.sin(a)
        pts.append((hw*math.copysign(abs(c)**.7, c), hh*math.copysign(abs(s)**.7, s)))
    return pts


def build(kit):
    RECORDS.clear()
    for i, (x, y, w, contrail, length, angle) in enumerate(ENGINES, start=1):
        r = w*.5                # mouth radius
        hw = r*1.0 if w < 10 else r*1.6
        hh = r*3.0 if w < 10 else r*2.6
        z = Z_AXIS
        mouth = x+1.5
        sec = nacelle_section(hw, hh)
        # Stations from the buried nose to the bell's forward plane: a long smooth taper.
        rear = mouth+4.6
        stations = [(xs, [(y+py*f, z+pz*f) for py, pz in sec])
                    for xs, f in ((rear, .9), (rear+2, .98), (rear+10, 1.0), (POD_FRONT_X-6, .9), (POD_FRONT_X, .55))]
        stations.sort(key=lambda s: s[0])
        K.loft_x(kit, f'Engine {i} drive pod', stations, kit.pale if w > 10 else kit.skin, bevel=0, smooth=40)
        # Bell, lip and throat are revolved about local Z and laid along -X from the bell's
        # forward plane (rear), so local z runs aft.
        def lay(ob):
            ob.rotation_euler = (0, -math.pi/2, 0)
            ob.location = (rear, y, z)
            return ob
        lay(K.revolve_loop(kit, f'Engine {i} aft-facing bell', [(r*.80, 0), (r*.88, 0), (r*1.04, 4.8), (r*.95, 4.8)],
                           kit.steel, segments=32))
        lay(K.revolve_loop(kit, f'Engine {i} mouth lip', [(r*.93, 4.5), (r*1.10, 4.5), (r*1.10, 5.2), (r*.93, 5.2)],
                           kit.slate, segments=32))
        lay(K.revolve(kit, f'Engine {i} dark throat', [(0, -.2), (r*.85, -.2), (r*.85, .4), (0, 1.4)], kit.tech,
                      segments=24))
        ex = kit.empty(f'Engine {i} exhaust', (x, y, z), angle, 'SINGLE_ARROW', w)
        ex['width'], ex['length'], ex['contrail_size'], ex['style'] = w, length, contrail, 'HIGH_TECH'
        RECORDS.append({'index': i, 'slot_location': [x, y], 'width': w, 'length': length, 'contrail_size': contrail,
                        'angle_deg': angle, 'style': 'HIGH_TECH', 'mouth_x': round(mouth, 2), 'axis_z': z,
                        'mouth_radius': r, 'exhaust_direction': [-1, 0, 0], 'anchor': ex.name,
                        'parts': [f'Engine {i} drive pod', f'Engine {i} aft-facing bell', f'Engine {i} mouth lip',
                                  f'Engine {i} dark throat']})


def records():
    return list(RECORDS)
