"""Colossus heavy freighter: palette over the shared ship detail kit.

The primitives (materials, box/cylinder/mesh, chamfered prisms, lofts,
booleans, plane cuts, the downward ray-cast) live in the shared
`ship-detail-kit/ship_detail_kit.py`, which was factored out of this file and
the Legion's. The Colossus cuts through the kit's boolean_clean (planar() and
no live bevel on a cut mesh) and its plane cuts give the facet the part's own
material. Coordinates are hull-local, +X bow, +Y port, +Z dorsal, one sprite
pixel per unit about the .ship centre.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent/'ship-detail-kit'))
import ship_detail_kit as sdk
# The shared primitives, under the names the Colossus modules import.
from ship_detail_kit import (material, inset, section, prism, mirror_y, loft_x, octagon, planar, facet_normal,
                             ngon, pod, revolve_x, _left)
from ship_detail_kit import boolean_clean as boolean


def plane_cut(target, name, point, normal, size=200):
    """Remove everything on the normal side of a plane: one flat facet in the part's own material."""
    return sdk.plane_cut(target, name, point, normal, size, clean=True)


class Kit(sdk.BaseKit):
    def __init__(self):
        # Domain-era low-tech freighter scheme read off colossus.png: dusty
        # oxide-red hull plating, darker maroon module plating, pale
        # weathered-grey longerons and bridge shell, burnt-orange and
        # faded-blue panel bands on the side cargo modules, dark gunmetal
        # lattice, and black recesses.
        self.red = material('Oxide red hull plating', (.36, .15, .12), .25, .6)
        self.maroon = material('Maroon module plating', (.24, .10, .09), .3, .6)
        self.pale = material('Pale weathered grey structure', (.56, .53, .51), .2, .55)
        self.orange = material('Burnt orange panel band', (.45, .20, .06), .25)
        self.blue = material('Faded blue panel band', (.16, .30, .45), .2)
        self.steel = material('Gunmetal machinery', (.16, .16, .17), .65)
        self.dark = material('Recess and lattice', (.04, .04, .045), .5)
        self.tile = material('Ceramic heat-shield tile', (.075, .072, .07), .05, .85)
        self.glass = material('Bridge glazing', (.05, .08, .09), .2, .15, emission=(.9, .75, .4), strength=3.0)
        self.lamp = material('Running lamp', (.2, .2, .15), 0, .4, emission=(1.0, .9, .6), strength=6.0)
        self.hull = []  # objects the downward ray-cast seats parts on
