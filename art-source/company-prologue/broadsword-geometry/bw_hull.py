"""Broadsword centre body: the long armoured nose and forward fuselage,
the canopy, and the aft spine between the engine nacelles.

Read off broadsword.png (sprite) and fighter-upres/broadsword/master-v1.png
(detail): the nose is |Y| <= 2 at its blunt tip (X 18-19), |Y| <= 4 from X 9
to 17, and steps out to |Y| <= 6 at the canopy shoulders (X 5-9). The aft
spine runs between the nacelles and stops short of them; the sprite is
open on the centre line aft of X -7.
"""
import bw_kit as K
from bw_kit import section_loft, strip, prism, corner_s, at

# (x, half width, keel z, deck z, top chamfer, keel chamfer), nose to tail.
FUSELAGE = [
    (19.0, 1.6, -.7, .55, .55, .45),
    (18.0, 2.5, -1.15, 1.0, .85, .65),
    (16.5, 3.35, -1.6, 1.4, 1.05, .85),
    (12.0, 3.75, -2.05, 1.8, 1.15, 1.0),
    (9.4, 3.85, -2.2, 1.95, 1.15, 1.05),
    (8.7, 5.0, -2.25, 1.95, 1.25, 1.1),
    (8.1, 5.85, -2.3, 1.95, 1.35, 1.2),
    (5.2, 6.0, -2.4, 2.0, 1.35, 1.25),
    (4.4, 6.9, -2.4, 2.0, 1.4, 1.25),
    (.5, 6.9, -2.4, 1.9, 1.4, 1.2),
    (-1.8, 5.0, -2.2, 1.6, 1.2, 1.0),
]
SPINE = [
    (.5, 2.6, -2.0, 1.55, .7, .7),
    (-6.6, 2.6, -1.9, 1.3, .7, .7),
    (-7.0, 1.3, -1.4, .8, .45, .45),
]
# Canopy: (x, half width, height above the fuselage deck), nose to tail.
CANOPY = [(10.3, .9, .35), (9.7, 1.75, .95), (8.0, 1.95, 1.2), (5.2, 1.95, 1.15), (4.3, 1.2, .7)]
FLARE_SLOT = {'id': 'WS 005', 'location': (-6, 0), 'angle': 180, 'arc': 5, 'mount': 'HIDDEN',
              'size': 'SMALL', 'type': 'SYSTEM'}


def canopy_loft(kit, name, mat, grow=0.0, x_range=None):
    st = []
    rows = CANOPY if x_range is None else [r for r in CANOPY if x_range[0] <= r[0] <= x_range[1]]
    if x_range:
        lo, hi = x_range
        for x in (hi, lo):
            if all(abs(r[0]-x) > 1e-6 for r in rows):
                rows.append(_canopy_at(x))
        rows.sort(key=lambda r: -r[0])
    if grow and (x_range is None or x_range[1]-x_range[0] > 2):
        # A grown frame also stands proud of the glass fore and aft.
        rows = [(rows[0][0]+grow, *rows[0][1:])] + rows[1:-1] + [(rows[-1][0]-grow, *rows[-1][1:])]
    for x, w, h in rows:
        deck = at(FUSELAGE, x)[3]
        w += grow
        top = deck+h+grow
        ct = min(w*.55, h*.8)
        st.append((x, K.octagon(-w, w, deck-.25, top, ct, .05)))
    return K.loft_x(kit, name, st, mat, .03)


def _canopy_at(x):
    rows = sorted(CANOPY, key=lambda r: -r[0])
    for a, b in zip(rows, rows[1:]):
        if b[0] <= x <= a[0]:
            t = (a[0]-x)/(a[0]-b[0])
            return (x, a[1]+(b[1]-a[1])*t, a[2]+(b[2]-a[2])*t)
    edge = rows[0] if x > rows[0][0] else rows[-1]
    return (x, *edge[1:])


def build(kit):
    fus = section_loft(kit, 'Hull forward fuselage', FUSELAGE, kit.steel, bevel=.08)
    spine = section_loft(kit, 'Hull aft spine', SPINE, kit.steel, bevel=.08)
    kit.hull += [fus, spine]
    canopy_loft(kit, 'Canopy glass', kit.glass)
    if not kit.detail:
        # Blockout reads the armour as the dominant colour mass.
        fus.data.materials[0] = kit.khaki
        return
    detail(kit)


def detail(kit):
    edge = lambda off: (lambda w, ct: corner_s(w, ct, 2)+off)
    for side, s in (('port', 1), ('starboard', -1)):
        # Long nose plates either side of a dark centre channel, wrapping the
        # shoulder chamfer, as the master's two khaki nose slabs.
        strip(kit, f'Hull nose armour plate {side}', FUSELAGE, 10.7, 18.5, .85, edge(.35), kit.khaki,
              side=s, stations=6)
        # Canopy shoulder plates over the widened forward body.
        strip(kit, f'Hull shoulder armour plate {side}', FUSELAGE, -1.0, 9.35, 2.15, edge(.55), kit.khaki,
              side=s, stations=6)
        # Dark side strake along the nose wall (the master's dark flank band).
        st = []
        for x in (11.0, 13.5, 16.0):
            _, w, z0, z1, ct, cb = at(FUSELAGE, x)
            st.append((x, [(s*(w-.05), z0+cb+.2), (s*(w+.3), z0+cb+.35), (s*(w+.3), z1-ct-.15), (s*(w-.05), z1-ct)]))
        K.loft_x(kit, f'Hull nose side strake {side}', st, kit.dark, .03)
        # Three cooling slots on each shoulder plate.
        for i, x in enumerate((6.75, 6.15, 5.55), 1):
            z = kit.seat_z(x, s*4.0)
            kit.box(f'Hull shoulder vent slot {side} {i}', (x, s*4.0, z+.02), (.28, 1.0, .16), kit.dark, .02)
        for i, (x, y) in enumerate(((11.0, 3.0), (8.6, 4.6), (1.0, 4.6), (16.0, 2.1)), 1):
            kit.bolt(f'Hull bolt {side} {i}', x, s*y)
    # Nose intake grille in the centre channel.
    for i in range(6):
        x = 15.7-i*.45
        z = at(FUSELAGE, x)[3]
        kit.box(f'Hull nose grille slat {i+1}', (x, 0, z+.05), (.16, 1.35, .14), kit.bright, .02)
    kit.box('Hull nose grille well', (14.6, 0, at(FUSELAGE, 14.6)[3]-.02), (2.9, 1.5, .1), kit.dark, 0)
    kit.box('Hull nose tip cap', (18.85, 0, .35), (.4, 1.5, .35), kit.bright, .05)
    # Canopy frame: a sill around the base and one cross bow.
    canopy_loft(kit, 'Canopy frame sill', kit.steel, grow=.12, x_range=(4.2, 10.35))
    canopy_loft(kit, 'Canopy frame bow', kit.steel, grow=.07, x_range=(7.75, 8.05))
    canopy_loft(kit, 'Canopy frame aft hoop', kit.steel, grow=.07, x_range=(5.0, 5.25))
    _cut_sill(kit)
    # Dorsal plate behind the canopy, with the master's small slot.
    z = kit.seat_z(2.2, 0)
    K.prism(kit, 'Hull dorsal plate', [(3.9, 1.25), (3.9, -1.25), (.6, -1.25), (.6, 1.25)],
            K.section(z-.1, z+.16, .0, .1), kit.khaki, .03)
    kit.box('Hull dorsal plate slot', (1.0, 0, z+.17), (.5, .16, .06), kit.dark, 0)
    # Spine: khaki centre plate, slatted grille, flare launcher at the tail.
    for side, s in (('port', 1), ('starboard', -1)):
        strip(kit, f'Hull spine armour plate {side}', SPINE, -5.6, -.3, 0, 1.3, kit.khaki, stations=3, side=s)
    zg = kit.seat_z(-3.4, 0)
    kit.box('Hull spine grille well', (-3.4, 0, zg), (3.3, 1.7, .12), kit.dark, 0)
    for i in range(6):
        kit.box(f'Hull spine grille slat {i+1}', (-2.0-i*.55, 0, zg+.07), (.18, 1.5, .1), kit.bright, .02)
    zf = kit.seat_z(-6.2, 0)
    kit.box('Flare launcher WS 005 housing', (-6.2, 0, zf+.18), (1.6, 1.6, .5), kit.steel, .08)
    for i in range(3):
        kit.box(f'Flare launcher WS 005 tube {i+1}', (-6.75, (i-1)*.5, zf+.3), (.6, .34, .3), kit.dark, .02)


def _cut_sill(kit):
    import bpy
    sill = bpy.data.objects['Canopy frame sill']
    # Keep only a low band of the grown canopy: the frame the glass seats in.
    cutter = kit.box('Temporary sill cutter', (7.3, 0, at(FUSELAGE, 7.3)[3]+5.35), (8, 6, 10), kit.dark, 0)
    K.boolean(sill, cutter)
