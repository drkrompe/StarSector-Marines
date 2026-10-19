"""The Hound (P): pirate livery inputs to the shared surfacing harness.

A state of the finished Hound (A) (../hound-surface/), not a new hull: the
skin is applied to hound-geometry's pirate state (build_hound.py --state
pirate: welded scrap-armour slabs, chaingun, twin light MG, light battle
scars), whose base objects and part tables are the Hound's own (groups/
re-exports ../hound-surface/groups). Run every harness script with `--hull
../hound-surface-pirate`; see README.md.

Livery: the Hound (P), read off hound_pirates.png (hound_d_pirates.skin):
worn, crudely repainted crimson where the Hegemony auxiliary is orange; grimy
dark brown-grey structure where it is taupe, and the same on the welded
scrap-armour slabs; gunmetal machinery, near-black recesses, a grimy cream
capsule nose, tinted glazing. Poorly maintained, lightly battle-worn: the
`pirate` row of wear.json. Class names are the harness keys the Hound's tables
already use ('orange' is the red armour here, 'taupe' the dark structure).
"""
import importlib.util
from pathlib import Path

_parent = Path(__file__).resolve().parent.parent / 'hound-surface' / 'surface_hull.py'
_spec = importlib.util.spec_from_file_location('hound_surface_hull', _parent)
_base = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_base)
globals().update({k: v for k, v in vars(_base).items() if not k.startswith('__')})

NAME = 'Hound (P)'
BLEND = '../../../build/hound-p-geom/hound-geometry.blend'   # build_hound.py --state pirate --out build/hound-p-geom
MATERIAL_CLASS = dict(_base.MATERIAL_CLASS, **{
    'Pirate scrap armour': 'taupe',   # welded slabs: dark unpainted scrap, the structure's dark brown-grey
})
FAMILY = {'orange': 'red', 'taupe': 'neutral', 'cream': 'cream',
          'gunmetal': 'neutral', 'dark': 'neutral', 'glass': 'neutral'}
# A crude repaint varies more from plate to plate than a maintained one.
VARIATION_SPAN = dict(_base.VARIATION_SPAN, orange=(6.0, 3.0, 3.0), taupe=(4.0, 1.0, 1.5))
CAPTION = 'Hound (P), pirate livery: graded Codex part skins + graded class swatches on every other base face.'


def derive_palette(classes):
    # The Hound's owned gunmetal, recess and glazing values stand.
    return _base.derive_palette(classes)
