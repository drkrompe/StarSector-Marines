"""The bone-white command dome (sprite X 4-66, Y +-22) over the spine.

The Legion's command-control, sufficient to field four fighter wings, sits in
the dome at the head of the spine, inside the inner belt's collar.
"""
from lg_kit import loft_x, octagon

# Command dome: (x, half-width, crown z) stations, nose to tail.
DOME = [(67, 1.5, 19.0), (64, 8, 24.5), (58, 14, 28.0), (48, 19, 30.0), (36, 21.5, 30.5),
        (24, 20, 29.5), (12, 15, 27.5), (5, 11, 25.0), (2, 6, 21.0)]
DOME_BASE = 12.0


def build(kit):
    stations = [(x, octagon(-w, w, DOME_BASE, z, ct=w*.5, cb=min(1.0, w*.2))) for x, w, z in DOME]
    dome = loft_x(kit, 'Bridge command dome', stations, kit.bone)
    kit.hull.append(dome)
