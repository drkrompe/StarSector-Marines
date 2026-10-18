"""The Hound (A)'s inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../hound-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md for the sequence.
Livery: the Hegemony auxiliary scheme read off hound_hegemony.png, clean and
maintained, light service wear: Hegemony orange armour over taupe
(brown-grey) structure, gunmetal machinery, dark recesses, cream capsule
nose and fittings, tinted glazing.
"""
NAME = 'Hound (A)'
BLEND = 'build/geom/hound-geometry.blend'   # rebuild.sh step 1: ../hound-geometry/build_hound.py
GROUPS = ['hull', 'engines', 'bow', 'decks']
MATERIAL_CLASS = {
    'Hegemony oxide armour': 'orange',
    'Hull faded oxide armour': 'orange',
    'Weathered brown armour': 'taupe',
    'Hull hold hatch brown': 'taupe',
    'Gunmetal machinery': 'gunmetal',
    'Worn light gunmetal': 'gunmetal',
    'Engine worn top plate': 'orange',     # the engines' lighter top plates (hd_engines); orange on the finish reference
    'Hull spine pale steel': 'gunmetal',
    'Engine bright steel': 'gunmetal',
    'Recess and machinery': 'dark',
    'Chalked cream fittings': 'cream',
    'Bridge glazing': 'glass',
}
CLASS_ID = {'orange': (0, 255, 0), 'taupe': (255, 0, 0), 'gunmetal': (0, 0, 255), 'dark': (255, 255, 0),
            'cream': (0, 255, 255), 'glass': (255, 255, 255), 'other': (255, 0, 255)}
CLASSES = ['orange', 'taupe', 'gunmetal', 'dark', 'cream', 'glass']
# Swap check: a change of paint family. Taupe structure, gunmetal, recess and
# glass are one neutral family (lighter or darker grey-brown is tone, reported);
# orange or cream painted where grey belongs is a swap.
FAMILY = {'orange': 'orange', 'cream': 'cream', 'taupe': 'neutral', 'gunmetal': 'neutral', 'dark': 'neutral', 'glass': 'neutral'}
SWATCH_UNITS = 6.0                  # hull units one swatch image spans (5.3 m at 0.88 m/unit)
SWATCH_ORDER = ['orange', 'taupe', 'gunmetal', 'dark', 'cream', 'glass']
SWATCH_DERIVED = []
MEASURED_CLASSES = ['orange', 'taupe', 'gunmetal', 'dark', 'cream']
REFERENCE_CAMERA = 'three-quarter'


def derive_palette(classes):
    # Thin gunmetal and recess shapes pick up their orange and taupe
    # neighbours at the registration's 1-2 px edge error, which inflates the
    # measured spread (gunmetal chroma std 7-10, recess L std 14) and would
    # let grading keep orange-tinted steel. Their means stand; the spreads
    # are owned values at the Second Wind's measured neutral spread.
    out = {}
    for c, std in (('gunmetal', [12.0, 1.8, 3.0]), ('dark', [6.5, 0.7, 1.0])):
        out[c] = dict(classes[c], std=std, std_measured=classes[c]['std'],
                      note='spread owned: edge contamination on the reference inflates it')
    # The glazing is a handful of pixels on the reference and lit from behind
    # there: an owned tinted-glass value, dark and faintly blue-green.
    out['glass'] = {'derived': 'owned: tinted armoured glazing, darker than recess paint is light, faint cool cast',
                      'mean': [24.0, -2.0, -4.0], 'std': [5.0, 0.8, 1.2]}
    return out


VARIATION_SPAN = {'orange': (4.0, 2.0, 2.5), 'taupe': (3.0, 0.8, 1.5), 'gunmetal': (3.0, 0.4, 0.8),
                  'dark': (2.0, 0.2, 0.3), 'cream': (3.0, 1.0, 2.0), 'glass': (1.5, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'orange': (1.0, 1.0, 1.0), 'taupe': (1.0, 0.8, 1.0), 'cream': (1.0, 1.0, 1.0),
                 'gunmetal': (0.0, 0.35, 0.7), 'dark': (0.0, 0.0, 0.25), 'glass': (0.0, 0.0, 0.0)}
ENGINES = [(-25.0, 22.0, 0.0), (-25.0, -22.0, 0.0)]   # drive bell centres, hull units (geometry.json engines)
STERN_SOOT = (-16.0, -27.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull cargo body'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['port-side', 'three-quarter', 'top', 'front', 'stern',
                'starboard-side', 'starboard-quarter', 'port-aft-quarter', 'under-quarter']
REVIEW_CLOSE = ['bow-detail', 'bridge-detail', 'fork-detail', 'turret-detail', 'engine-detail',
                'stern-detail', 'wheelhouse-detail', 'underside-aft']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-side': (180, 0, 92, (8, 0, 3)),
    'starboard-quarter': (135, 30, 112, (8, 0, 2)),
    'port-aft-quarter': (-45, 28, 112, (6, 0, 2)),
    'under-quarter': (40, -35, 112, (8, 0, 0)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-side', 'top']),
                ('views-whole-2', ['port-aft-quarter', 'starboard-side', 'front', 'under-quarter']),
                ('views-close', ['bow-detail', 'bridge-detail', 'wheelhouse-detail', 'engine-detail']),
                ('views-close-2', ['fork-detail', 'turret-detail', 'stern-detail', 'underside-aft'])]
CAPTION = 'Hound (A) surfacing, Hegemony auxiliary livery: graded Codex part skins + graded class swatches on every other base face.'
