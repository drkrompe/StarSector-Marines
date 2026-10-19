"""The Wolf (P)'s inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../wolf-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh for the
sequence. Livery: the pirate skin read off wolf_pirates.png (build/
sprite-livery.png, from the install, never committed): a worn crimson over
the high-tech hull's armour, warm grey machinery (fork, head, outriggers,
pods, engine block), near-black recesses, tinted glazing. "Heavily modified -
and poorly maintained - by its pirate crew" (the skin): battle wear, the
heaviest of the cinematic hulls short of the Second Wind's derelict state.

The turret kit's guns keep their own weathered paints (no class); so do the
lamps, the heat-vent glow and the running lights.
"""
NAME = 'Wolf (P)'
BLEND = 'build/geom/wolf-geometry.blend'   # rebuild.sh step 1: ../wolf-geometry/build_wolf.py -- --out <this>/build/geom
GROUPS = ['crimson', 'grey']
MATERIAL_CLASS = {
    'Wolf pirate crimson armour': 'crimson',
    'Wolf grey machinery': 'grey',
    'Wolf recess': 'dark',
    'Wolf bridge glazing': 'glass',
}
CLASS_ID = {'crimson': (255, 0, 0), 'grey': (0, 255, 0), 'dark': (255, 255, 0), 'glass': (0, 255, 255),
            'other': (255, 0, 255)}
CLASSES = ['crimson', 'grey', 'dark', 'glass']
# Swap check: crimson painted where grey belongs (or the reverse) is a swap;
# grey, recess and glass are one neutral family whose differences are tone.
FAMILY = {'crimson': 'crimson', 'grey': 'neutral', 'dark': 'neutral', 'glass': 'neutral'}
# Crimson and grey both sit far from a mid blue.
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 8.0                  # hull units one swatch image spans (6.0 m at 0.75 m/unit)
SWATCH_ORDER = ['crimson', 'grey', 'dark']
SWATCH_DERIVED = [('glass', 'dark')]
MEASURED_CLASSES = ['crimson', 'grey', 'dark']
REFERENCE_CAMERA = 'three-quarter'
# The crowned decks are sheared prisms and the rounded hull ellipsoids: read
# non-planar quads as drawn (parts.select_faces), as on the Onslaught.
SHEARED_FACES = True


def derive_palette(classes):
    # Thin grey fittings and recess slots pick up their crimson neighbours at the
    # registration edge: the means stand, the spreads are owned (as on the Hermes).
    out = {}
    for c, std in (('crimson', [8.0, 3.0, 2.5]), ('grey', [9.0, 0.8, 1.6]), ('dark', [6.5, 0.6, 0.8])):
        out[c] = dict(classes[c], std=std, std_measured=classes[c]['std'],
                      note='spread owned: shading and edge contamination on the reference inflate it')
    # The recesses are a few hundred reference pixels, washed toward their crimson and grey
    # neighbours (measured L 35, as light as the crimson): the near-black is owned.
    out['dark'] = dict(out['dark'], mean=[16.0, 0.8, 1.6], mean_measured=classes['dark']['mean'],
                       note='mean owned: the reference recesses are too few pixels and edge-contaminated')
    return out | {'glass': {'derived': 'owned: tinted armoured glazing, dark with a faint cool cast',
                            'mean': [22.0, -2.0, -5.0], 'std': [5.0, 0.8, 1.2]}}


VARIATION_SPAN = {'crimson': (4.5, 2.0, 2.0), 'grey': (3.5, 0.5, 1.0), 'dark': (2.0, 0.2, 0.3),
                  'glass': (1.5, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'crimson': (1.0, 1.0, 1.0), 'grey': (1.0, 0.7, 1.0), 'dark': (0.0, 0.0, 0.25),
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
CAPTION = 'Wolf (P) surfacing, pirate livery: graded Codex part skins + graded class swatches on every other base face; battle wear.'
