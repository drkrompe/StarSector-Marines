"""Derives the bore shaft's collar lip from its ImageGen master (``ruins-theme-halls``, the shaft hall's ``SHAFT``).

As the foundry's kerb (``art-source/voids/foundry/derive.py``): the master is cut to its guide's drawn rows (the guide
leaves a transparent margin under the nosing), shrunk to 128x64 (one cell along the edge by half a cell across, the
bottom row the drop's edge) and its left and right edges healed by blending a band with the strip rolled by half its
width, so it repeats left to right. It ships opaque. Run from the repository root:

    python art-source/voids/shaft/derive.py
"""
import importlib.util

# The foundry's derivation, loaded by path: it is also named ``derive``.
_spec = importlib.util.spec_from_file_location("foundry_derive", "art-source/voids/foundry/derive.py")
foundry = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(foundry)
derive = foundry.derive

if __name__ == "__main__":
    foundry.SRC = "art-source/voids/shaft/"
    # Cut at the master's own last opaque row (777 of 887, measured): ImageGen left a deeper transparent margin under
    # the nosing than the guide's 11 rows, and the guide's cut kept a black band along the drop edge.
    derive("shaft-collar-lip", (128, 64), False, "RGB", rows=777 / 887)
