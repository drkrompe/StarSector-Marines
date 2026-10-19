"""The merchant Wolf's inputs to the shared surfacing harness (../second-wind-surface/hull.py).

The convoy's own Wolf in the Asharu battle: the base hull (no pirate armour,
every slot armed; ../wolf-geometry/build_wolf.py --variant merchant) in an
independent livery read off wolf_base.png (build/sprite-livery.png, from the
install, never committed): pale blue-grey armour over warm grey machinery,
near-black recesses, tinted glazing. A working escort: light service wear.
Same harness and part layout as the Wolf (P)'s ../wolf-surface, own art.
"""
NAME = 'Wolf (merchant)'
BLEND = 'build/geom/wolf-geometry.blend'   # rebuild.sh step 1: ../wolf-geometry/build_wolf.py -- --out <this>/build/geom
GROUPS = ['armour', 'grey']
MATERIAL_CLASS = {
    'Wolf hull armour': 'armour',
    'Wolf grey machinery': 'grey',
    'Wolf recess': 'dark',
    'Wolf bridge glazing': 'glass',
}
CLASS_ID = {'armour': (255, 0, 0), 'grey': (0, 255, 0), 'dark': (255, 255, 0), 'glass': (0, 255, 255),
            'other': (255, 0, 255)}
CLASSES = ['armour', 'grey', 'dark', 'glass']
# Swap check: armour painted where grey belongs (or the reverse) is a swap;
# grey, recess and glass are one neutral family whose differences are tone.
FAMILY = {'armour': 'armour', 'grey': 'neutral', 'dark': 'neutral', 'glass': 'neutral'}
# Crimson and grey both sit far from a mid blue.
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 8.0                  # hull units one swatch image spans (7.0 m at 0.88 m/unit)
SWATCH_ORDER = ['armour', 'grey', 'dark']
SWATCH_DERIVED = [('glass', 'dark')]
MEASURED_CLASSES = ['armour', 'grey', 'dark']
REFERENCE_CAMERA = 'three-quarter'
# The crowned decks are sheared prisms and the rounded hull ellipsoids: read
# non-planar quads as drawn (parts.select_faces), as on the Onslaught.
SHEARED_FACES = True


def derive_palette(classes):
    # Thin grey fittings and recess slots pick up their armour neighbours at the
    # registration edge: the means stand, the spreads are owned (as on the Hermes).
    out = {}
    for c, std in (('armour', [8.0, 1.2, 1.6]), ('grey', [9.0, 0.8, 1.6]), ('dark', [6.5, 0.6, 0.8])):
        out[c] = dict(classes[c], std=std, std_measured=classes[c]['std'],
                      note='spread owned: shading and edge contamination on the reference inflate it')
    # The recesses are a few hundred reference pixels, washed toward their armour and grey
    # neighbours (measured L 35, as light as the armour): the near-black is owned.
    out['dark'] = dict(out['dark'], mean=[16.0, 0.8, 1.6], mean_measured=classes['dark']['mean'],
                       note='mean owned: the reference recesses are too few pixels and edge-contaminated')
    return out | {'glass': {'derived': 'owned: tinted armoured glazing, dark with a faint cool cast',
                            'mean': [22.0, -2.0, -5.0], 'std': [5.0, 0.8, 1.2]}}


VARIATION_SPAN = {'armour': (3.5, 0.6, 1.0), 'grey': (3.5, 0.5, 1.0), 'dark': (2.0, 0.2, 0.3),
                  'glass': (1.5, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'armour': (1.0, 1.0, 1.0), 'grey': (1.0, 0.7, 1.0), 'dark': (0.0, 0.0, 0.25),
                 'glass': (0.0, 0.0, 0.0)}
ENGINES = [(-36.0, 12.0, 0.0), (-40.0, 0.0, 0.0), (-36.0, -11.0, 0.0)]   # drive mouths (geometry.json engines)
STERN_SOOT = (-28.0, -39.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull main body'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'port-side', 'top', 'front', 'stern', 'starboard-quarter', 'stern-quarter',
                'under-quarter']
REVIEW_CLOSE = ['bow-detail', 'turret-detail', 'engine-detail', 'stern-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-quarter': (135, 30, 124, (7, 0, 0)),
    'stern-quarter': (-45, 28, 124, (7, 0, 0)),
    'under-quarter': (40, -35, 124, (7, 0, -2)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-side', 'top']),
                ('views-whole-2', ['stern-quarter', 'front', 'stern', 'under-quarter']),
                ('views-close', ['bow-detail', 'turret-detail', 'engine-detail', 'stern-detail'])]
CAPTION = 'Wolf (merchant) surfacing, independent livery: graded Codex part skins + graded class swatches; service wear.'
