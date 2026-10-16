"""The two drive pods: whole engine units hung on the spar either side.

Each pod is a drive unit in distinct sections along its thrust axis, each
its own faceted solid with a dark seam over a dark core: a domed intake cap,
a forward casing, the orange armour collar the spar enters (the sprite's
widest band, rows 37-52), a narrower aft casing with a reinforcing band, the
thrust housing (rows 63-69) and one engine can whose aft face is the
exhaust, the bell sunk into it. Sections are chamfered octagons, flat-shaded.

hound.ship has exactly two engine slots, LOW_TECH, width 16, length 80,
angle 180, at (-25, +22) and (-25, -22). Engine 1 is slot 0 (port, +Y),
Engine 2 is slot 1 (starboard). The nozzle centre is the slot XY at Z 0;
the bell mouth radius follows the Second Wind ratio (0.325 x slot width).
"""
import bpy
from hd_kit import boolean, loft_x, octagon

ENGINES = [(-25, 22, 16), (-25, -22, 16)]
BELL_R = .325 * 16
CAN_R = BELL_R + .5
CAN_X = (-26.0, -24.4)
SEAM = .4

# Port pod sections: (name, material key, [(x, (y0, y1, z0, z1, top chamfer,
# bottom chamfer)), ...]) with y0 inboard, y1 outboard. Mirrored for Engine 2.
PARTS = [
    ('intake cap', 'steel', [(11.0, (16, 27.4, -5.8, 6.3, 3, 2.5)), (14.6, (16, 27.4, -5.8, 6.3, 3, 2.5)),
                             (16.2, (17.4, 26, -4.3, 4.8, 2.4, 2)), (17.0, (18.6, 24.8, -2.6, 3.0, 1.4, 1.2))]),
    ('forward casing', 'rust', [(7.9, (14.4, 28.4, -6.5, 7, 3, 2.5)), (11.0, (14.6, 28.2, -6.3, 6.8, 3, 2.5))]),
    ('armour collar', 'orange', [(-8.6, (15.6, 29.2, -6.4, 6.8, 3.2, 2.8)), (-7.3, (14.2, 30, -7.1, 7.6, 3.6, 3)),
                                 (6.6, (14.2, 30, -7.1, 7.6, 3.6, 3)), (8.0, (14.4, 29.4, -6.6, 7.1, 3.4, 3))]),
    ('aft casing', 'rust', [(-18.0, (16.4, 27.4, -5.6, 6, 2.6, 2.2)), (-8.6, (16.4, 27.4, -5.6, 6, 2.6, 2.2))]),
    ('reinforcing band', 'steel', [(-14.0, (15.7, 28.2, -6.2, 6.6, 2.9, 2.5)), (-11.0, (15.7, 28.2, -6.2, 6.6, 2.9, 2.5))]),
    ('thrust housing', 'orange', [(-24.6, (16, 28.6, -6.2, 6.4, 3, 2.6)), (-23.8, (15, 29.6, -7, 7.1, 3.3, 3)),
                                  (-19.0, (15, 29.6, -7, 7.1, 3.3, 3)), (-18.0, (15.8, 28.6, -6.4, 6.5, 3, 2.6))]),
]
CORE = (17, 26.5, -4.6, 5.0, 2, 2)


def oct_yz(spec, side):
    y0, y1, z0, z1, ct, cb = spec
    pts = octagon(y0, y1, z0, z1, ct, cb)
    return pts if side > 0 else [(-y, z) for y, z in pts]


def build(kit):
    for i, (ex, ey, width) in enumerate(ENGINES, 1):
        side = 1 if ey > 0 else -1
        label = 'port' if side > 0 else 'starboard'
        for name, mat, stations in PARTS:
            # Seams: each section stops short of its neighbours over the core.
            xs = [x for x, _ in stations]
            lo, hi = min(xs), max(xs)
            st = [(min(max(x, lo+SEAM/2), hi-SEAM/2) if name not in ('reinforcing band', 'intake cap') else x,
                   oct_yz(s, side)) for x, s in stations]
            loft_x(kit, f'Engine {i} {label} {name}', st, getattr(kit, mat))
        # Dorsal vent housing on the collar: the ring the sprite shows there.
        kit.cylinder(f'Engine {i} {label} dorsal vent housing', (-.5, side*22, 7.6), 4.2, 1.6, kit.rust, 'Z', 8, .12)
        kit.cylinder(f'Engine {i} {label} dorsal vent grille', (-.5, side*22, 8.35), 3.0, .3, kit.dark, 'Z', 8, 0)
        loft_x(kit, f'Engine {i} {label} dark core', [(-24, oct_yz(CORE, side)), (11.5, oct_yz(CORE, side))], kit.dark, 0)
        # Engine can: a twelve-sided shroud whose aft face is the exhaust,
        # with the bell sunk into it rather than a nozzle poking out.
        cx = sum(CAN_X)/2
        can = kit.cylinder(f'Engine {i} {label} engine can', (cx, ey, 0), CAN_R, CAN_X[1]-CAN_X[0], kit.steel, 'X', 12, .1)
        bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=BELL_R, radius2=BELL_R*.45, depth=1.6,
                                        location=(CAN_X[0]+.6, ey, 0), rotation=(0, 1.5707963, 0))
        bore = bpy.context.object
        boolean(can, bore)
        bpy.ops.mesh.primitive_cone_add(vertices=12, radius1=BELL_R-.15, radius2=BELL_R*.4, depth=1.4,
                                        location=(CAN_X[0]+.75, ey, 0), rotation=(0, 1.5707963, 0))
        kit.finish(bpy.context.object, f'Engine {i} {label} exhaust bell', kit.dark)
        kit.empty(f'Engine {i} {label} exhaust anchor', (ex, ey, 0), 180)
