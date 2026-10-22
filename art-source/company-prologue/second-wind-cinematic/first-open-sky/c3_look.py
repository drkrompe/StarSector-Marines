"""C3's land and sky graded onto the C4/C5 look, so the window and the overhead port read as one place.

C4 and C5 are Blender renders of the same Jangala (industrial-exterior/c4x_scene.py, c4_jungle.py):
AgX with the medium-high-contrast look, a high soft sun, and aerial perspective toward a cool
grey-blue haze (HAZE_RGB, 1/e at 9 km). Their jungle reads lighter, yellower and much flatter than
C3's painted plates: measured over C4's canopy at 13.5 s, sRGB mean (0.33, 0.40, 0.29), luminance
spread 0.24-0.46 (5th-95th percentile), against C3's (0.26, 0.29, 0.23) and 0.06-0.57. Deep crown
shadow is the biggest difference: C3's morning sun leaves near-black gaps that C4's light never
has.

The grade is applied per layer, at bake time (c3_layers.py), with the layer's distance:

1. tone: luminance compressed about a pivot in log space (C4's spread), shadows lifted, highlights
   rolled off the way AgX rolls them, so the crowns keep their shape and lose the hard sun;
2. colour: the canopy's hue pulled toward C4's yellower green, saturation trimmed;
3. haze: C4's own aerial perspective, toward C4's haze, by distance.

The sky plate gets the same haze and a lighter hand on saturation, so its blue matches the paler
air C4's haze implies. Nothing is relit: the sun stays where C3's paint put it (upper left).
"""
import numpy as np

# C4's canopy, measured (sRGB, 13.5 s, review cinematic-c4c5 2026-10-21_kit-plumes-still)
C4_CANOPY_MEAN = np.array([0.332, 0.396, 0.292], np.float32)
HAZE_SRGB = np.array([0.68, 0.73, 0.76], np.float32)    # C4's HAZE_RGB through its view, by eye on its far edges
HAZE_DIST = 9000.0                                       # c4x_scene.HAZE_DIST
HAZE_GAIN = 0.24                                         # C3's air is humid morning air: a little more than C4's 0.35

PIVOT = 0.16          # linear luminance held fixed by the tone curve (sRGB ~0.44)
GAMMA = 0.62          # log-space contrast: C4's canopy spread over C3's
SHADOW_LIFT = 0.012   # linear floor added before the curve: no crown gap goes black
SAT = 1.00
SHADOW_GREEN_BELOW = 0.05   # linear luminance under which shadow chroma moves to the canopy hue
HUE_PULL = 1.0        # how far the canopy's channel balance moves toward C4's


def to_lin(c):
    c = np.clip(c, 0, 1)
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def to_srgb(c):
    c = np.clip(c, 0, 1)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * c ** (1 / 2.4) - 0.055)


LUM = np.array([0.2126, 0.7152, 0.0722], np.float32)


def _balance():
    """Per-channel gains that move C3's canopy balance toward C4's (measured on the composed
    frame's canopy, sRGB (0.259, 0.289, 0.226), rescaled to unit luminance)."""
    c3 = to_lin(np.array([0.259, 0.289, 0.226], np.float32))
    c4 = to_lin(C4_CANOPY_MEAN)
    g = (c4 / (c4 @ LUM)) / (c3 / (c3 @ LUM))
    return 1 + HUE_PULL * (g - 1)


BALANCE = _balance()


def tone(lin):
    """The luminance curve, keeping each pixel's chroma ratios."""
    y = lin @ LUM
    y2 = PIVOT * ((y + SHADOW_LIFT) / PIVOT) ** GAMMA
    y2 = y2 / (1 + 0.35 * np.maximum(y2 - 0.35, 0))      # AgX-like roll-off of the brightest leaves
    out = lin * (y2 / np.maximum(y, 1e-4))[..., None]
    # Lifted crown gaps carry the paint's cold shadow tint, which lifting turns grey-blue; under
    # C4's light they are deep green. Pull the darkest pixels' chroma toward the canopy's own hue.
    w = np.clip(1 - y / SHADOW_GREEN_BELOW, 0, 1)[..., None] ** 1.5
    canopy = to_lin(C4_CANOPY_MEAN)
    return out * (1 - w) + (y2[..., None] * canopy / (canopy @ LUM)) * w


def saturate(lin, s):
    y = (lin @ LUM)[..., None]
    return y + (lin - y) * s


def haze(lin, distance_m, gain=HAZE_GAIN):
    f = gain * (1 - np.exp(-distance_m / HAZE_DIST))
    return lin * (1 - f) + to_lin(HAZE_SRGB) * f


def grade_land(rgb, distance_m):
    """sRGB float (..., 3) of a land layer at this slant distance -> graded sRGB."""
    lin = to_lin(rgb)
    lin = tone(lin) * BALANCE
    lin = saturate(lin, SAT)
    return to_srgb(haze(lin, distance_m))


def grade_sky(rgb):
    """The sky plate: the same air, a lighter hand (it has no crown shadow to lift)."""
    lin = saturate(to_lin(rgb), 0.85)
    return to_srgb(haze(lin, 12000.0, gain=0.25))
