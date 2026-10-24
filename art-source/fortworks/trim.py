"""The grade a style's art takes between its ImageGen master and its shipped cut (`fort-works.md`).

The League's modular works came back with near-white strip lamps and trim along slate panels, which the owner found
glaring beside the grey ("tone down the brightness of that white"). `soften_trim` pulls every bright, unsaturated pixel
down to a pale slate trim, eased in by brightness so a lamp's glow falls off as it did, and leaves colour and the
panels alone. It runs on the master before any cut, so a style's walls, parapets, barricades, gate leaves and turret
pieces all take the same grade. The masters stay as generated.
"""
import numpy as np
from PIL import Image

# Styles whose art is graded, and how.
STYLES = {'modular'}

# The trim a white becomes: pale slate, a little blue, about two thirds of its painted value.
_SCALE = 0.62
_TINT = np.array([6.0, 12.0, 20.0]) / 255.0


def _smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def soften_trim(image):
    """`image` with its bright near-white trim toned down to pale slate. RGBA in, RGBA out; alpha untouched."""
    im = np.asarray(image.convert('RGBA')).astype(np.float64) / 255.0
    rgb = im[..., :3]
    value = rgb.max(axis=2)
    saturation = np.where(value > 0, (value - rgb.min(axis=2)) / np.maximum(value, 1e-6), 0.0)
    weight = _smoothstep(0.60, 0.85, value) * (1.0 - _smoothstep(0.10, 0.22, saturation))
    toned = rgb * _SCALE + _TINT
    out = rgb * (1.0 - weight[..., None]) + toned * weight[..., None]
    im[..., :3] = np.clip(out, 0.0, 1.0)
    return Image.fromarray((im * 255.0 + 0.5).astype(np.uint8), 'RGBA')


def graded(image, style):
    """`image` as `style` ships it: graded where the style is, unchanged where not."""
    return soften_trim(image) if style in STYLES else image
