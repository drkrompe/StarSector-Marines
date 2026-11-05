"""Cut a painted plate's key colours: its screens were painted flat magenta and its windows flat green, so the
game's readouts and the space outside draw behind whatever the painter put in front of them, through holes. Its
button boards were painted flat cyan; those stay opaque, filled with the dark board the game's buttons sit on.

A pixel's coverage is how far it sits from the key towards the painting, so a painted edge keeps its
antialiasing. Spill on edge pixels is taken back out of their colour.
"""
import numpy as np

BOARD = np.array([24, 26, 29]) / 255


def keys(rgb):
    """Coverage of the screen, window and board keys, each 0 for painting and 1 for pure key."""
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    magenta = np.minimum(r, b) - g
    green = g - np.maximum(r, b)
    # cool steel leans slightly towards cyan, so its threshold sits above the others'
    cyan = np.minimum(g, b) - r
    return np.clip((magenta - 0.08) / 0.34, 0, 1), np.clip((green - 0.08) / 0.34, 0, 1), np.clip((cyan - 0.2) / 0.25, 0, 1)


def cut(rgb):
    """The plate as straight RGBA in 0..1: holes where the screens and windows were, boards filled."""
    m, g, c = keys(rgb)
    painted = rgb.copy()
    spill_m = np.minimum(rgb[..., 0], rgb[..., 2]) > rgb[..., 1]
    limit = rgb[..., 1] + 0.03
    painted[..., 0] = np.where(spill_m, np.minimum(rgb[..., 0], limit + (rgb[..., 0] - rgb[..., 2]).clip(0)), painted[..., 0])
    painted[..., 2] = np.where(spill_m, np.minimum(rgb[..., 2], limit + 0.04), painted[..., 2])
    spill_g = rgb[..., 1] > np.maximum(rgb[..., 0], rgb[..., 2])
    painted[..., 1] = np.where(spill_g, np.maximum(rgb[..., 0], rgb[..., 2]), painted[..., 1])
    painted = painted * (1 - c[..., None]) + BOARD * c[..., None]
    return np.dstack([painted, 1 - np.maximum(m, g)])
