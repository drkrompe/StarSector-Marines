"""The Hermes's inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../hermes-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh for the
sequence. Livery: the vanilla Midline courier read off hermes_base.png
(reference/sprite-livery.png): an ivory-cream spine and fairing, light cool
grey hull panels, gunmetal sponsons, turrets and drives, near-black recesses,
tinted glazing. Clean state, light service wear.
"""
NAME = 'Hermes'
BLEND = 'build/geom/hermes-geometry.blend'   # rebuild.sh step 1: ../hermes-geometry/build_hermes.py -- --out <this>/build/geom
GROUPS = ['spine', 'sponsons', 'aft']
MATERIAL_CLASS = {
    'Cream hull plate': 'cream',
    'Grey hull panel': 'grey',
    'Gunmetal machinery': 'gunmetal',
    'Drive frame plate': 'gunmetal',
    'Recess and machinery': 'dark',
    'Cockpit glazing': 'glass',
}
# Kept on their own materials (no class, no swatch): the grey-blue vent
# blocks, the interiors (troop bay liner, canvas, seats, cockpit lining),
# lamps, chrome, rubber, the radome, the drive bell liners and hazard paint.
CLASS_ID = {'cream': (255, 0, 0), 'grey': (0, 255, 0), 'gunmetal': (0, 0, 255), 'dark': (255, 255, 0),
            'glass': (0, 255, 255), 'other': (255, 0, 255)}
CLASSES = ['cream', 'grey', 'gunmetal', 'dark', 'glass']
# Swap check: cream painted where grey belongs (or the reverse) is a swap;
# grey, gunmetal, recess and glass are one neutral family whose differences are tone.
FAMILY = {'cream': 'cream', 'grey': 'neutral', 'gunmetal': 'neutral', 'dark': 'neutral', 'glass': 'neutral'}
# Cream and pale grey sit inside imgops.segment's threshold of the default 214
# grey background; a mid blue is far from every class (as the Mudskipper).
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 40.0                 # hull units one swatch image spans (4.4 m at 0.11 m/unit)
SWATCH_ORDER = ['cream', 'grey', 'gunmetal', 'dark']
SWATCH_DERIVED = [('glass', 'dark')]
MEASURED_CLASSES = ['cream', 'grey', 'gunmetal', 'dark']
REFERENCE_CAMERA = 'port-quarter'


def derive_palette(classes):
    # Thin gunmetal fittings and recess slots pick up their cream and grey
    # neighbours at the registration edge (gunmetal L std 14.6, recess 10.5):
    # their means stand; the spreads are owned, as on the Hound (A).
    out = {}
    for c, std in (('gunmetal', [10.0, 0.6, 1.6]), ('dark', [6.5, 0.5, 0.8])):
        out[c] = dict(classes[c], std=std, std_measured=classes[c]['std'],
                      note='spread owned: edge contamination on the reference inflates it')
    # The glazing is a few pixels on the reference: an owned tinted-glass value.
    return out | {'glass': {'derived': 'owned: tinted armoured glazing, dark with a faint cool cast',
                      'mean': [22.0, -2.0, -5.0], 'std': [5.0, 0.8, 1.2]}}


VARIATION_SPAN = {'cream': (3.0, 0.8, 1.5), 'grey': (3.0, 0.5, 1.0), 'gunmetal': (3.0, 0.4, 0.8),
                  'dark': (2.0, 0.2, 0.3), 'glass': (1.5, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'cream': (1.0, 1.0, 1.0), 'grey': (1.0, 0.8, 1.0), 'gunmetal': (0.0, 0.35, 0.7),
                 'dark': (0.0, 0.0, 0.25), 'glass': (0.0, 0.0, 0.0)}
ENGINES = [(-39.0, 10.0, 0.0), (-39.0, 0.0, 0.0), (-39.0, -10.0, 0.0)]   # main drive mouths (geometry.json engines)
STERN_SOOT = (-30.0, -38.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Main hull - spine'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['port-side', 'port-quarter', 'top', 'bow', 'stern-quarter',
                'starboard-side', 'starboard-quarter', 'under-quarter']
REVIEW_CLOSE = ['bow-detail', 'cockpit-detail', 'turret-detail', 'aft-turret-detail', 'stern-detail', 'underside-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-side': (180, 0, 100, (1, 0, -2)),
    'starboard-quarter': (135, 30, 110, (1, 0, -3)),
    'under-quarter': (40, -35, 110, (1, 0, -3)),
}
REVIEW_PAGES = [('views-whole', ['port-quarter', 'starboard-quarter', 'port-side', 'top']),
                ('views-whole-2', ['stern-quarter', 'starboard-side', 'bow', 'under-quarter']),
                ('views-close', ['bow-detail', 'cockpit-detail', 'turret-detail', 'aft-turret-detail']),
                ('views-close-2', ['stern-detail', 'underside-detail'])]
CAPTION = 'Hermes surfacing, Midline courier livery: graded Codex part skins + graded class swatches on every other base face.'
