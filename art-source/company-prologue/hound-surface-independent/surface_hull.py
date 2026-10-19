"""The Hound's independent livery: inputs to the shared surfacing harness.

A livery of the finished Hound (A) (../hound-surface/), not a new hull. The
geometry build, the part tables (groups/ re-exports ../hound-surface/groups)
and every face selection are the Hound's own; this folder holds only what a
livery changes: its finish reference, palette, Codex part sheets and swatches,
wear row, and outputs. Run every harness script with `--hull
../hound-surface-independent`; see README.md.

Livery: the Asharu convoy's independent escort scheme, matched to the Lasher's
(lasher-geometry ls_kit.LIVERIES 'independent'): pale warm-grey armour from the
independent faction colour where the Hegemony auxiliary is orange, slate-blue
structure where it is taupe, the same gunmetal machinery, dark recesses, cream
capsule nose and fittings, tinted glazing. Class names are the harness keys
the Hound's tables already use, so 'orange' is the armour class (grey here)
and 'taupe' the structure class (slate blue here).
"""
import importlib.util
from pathlib import Path

_parent = Path(__file__).resolve().parent.parent / 'hound-surface' / 'surface_hull.py'
_spec = importlib.util.spec_from_file_location('hound_surface_hull', _parent)
_base = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_base)
globals().update({k: v for k, v in vars(_base).items() if not k.startswith('__')})

NAME = 'Hound (independent)'
BLEND = '../hound-surface/build/geom/hound-geometry.blend'   # the Hound's clean build (../hound-surface/rebuild.sh step 1)
# Paint families for the swap check: grey armour, slate-blue structure and cream
# are each their own family; gunmetal, recess and glass stay one neutral family.
FAMILY = {'orange': 'armour', 'taupe': 'blue', 'cream': 'cream',
          'gunmetal': 'neutral', 'dark': 'neutral', 'glass': 'neutral'}
VARIATION_SPAN = dict(_base.VARIATION_SPAN, orange=(3.5, .6, 1.0), taupe=(3.0, 1.0, 1.5))
CAPTION = ('Hound, independent convoy livery: graded Codex part skins + graded class swatches '
           'on every other base face.')


def derive_palette(classes):
    # The Hound's owned gunmetal, recess and glazing values stand (see
    # ../hound-surface/surface_hull.py). Two livery-owned values on top:
    # - the grey armour's measured L spread (16) is the reference's shading on a
    #   pale paint, not paint variation; owned at the Hegemony orange's 11 so
    #   grading does not spread it down into the gunmetal;
    # - the slate-blue structure measured nearly grey (b -4); owned a little
    #   bluer, toward the Lasher's accent (lasher-geometry ls_kit.LIVERIES), so
    #   the two escorts read as one fleet.
    out = _base.derive_palette(classes)
    out['orange'] = dict(classes['orange'], std=[11.0, .7, 1.5], std_measured=classes['orange']['std'],
                         note='spread owned: reference shading on a pale paint inflates it')
    t = classes['taupe']
    out['taupe'] = dict(t, mean=[t['mean'][0], -1.5, -8.0], mean_measured=t['mean'],
                        note='chroma owned toward the Lasher independent accent')
    return out
