"""The Borer's inputs to the shared surfacing harness (../../second-wind-surface/hull.py).

Every harness script runs with `--hull <this folder>` (after `--` for Blender); see
../README.md (Borer) and rebuild.sh for the sequence. Livery: the Shepherd's own Ko Combine
family, because the Borer is the tender's built-in drone and comes out of the same yard. Its
burnt orange armour is the Shepherd's drive-pod orange, its machinery the Shepherd's light grey
drum, its structure the Shepherd's gunmetal, with polished steel rings and lips and near-black
recesses. The sprite (drone_borer.png) is the colour key: orange body, grey extremities. The
emitter glass and the lamp and beacon lenses keep their own materials. A working drone of a
tender in service: light service wear, more grime and soot than a warship's fighter.
"""
NAME = 'Borer'
BLEND = 'build/geom/borer-geometry.blend'   # rebuild.sh step 1: ../../fighters-geometry/build_fighter.py --fighter borer
GROUPS = ['hull']
MATERIAL_CLASS = {
    'Borer orange armour': 'orange',
    'Borer grey machinery': 'grey',
    'Gunmetal structure': 'gunmetal',
    'Worn bright steel': 'bright',
    'Recess and machinery': 'dark',
}
CLASS_ID = {'orange': (255, 128, 0), 'grey': (0, 255, 0), 'gunmetal': (0, 0, 255), 'bright': (0, 255, 255),
            'dark': (128, 0, 255), 'other': (255, 0, 255)}
CLASSES = ['orange', 'grey', 'gunmetal', 'bright', 'dark']
# Swap check: orange painted where grey belongs is a swap; the greys are one neutral family.
FAMILY = {'orange': 'orange', 'grey': 'neutral', 'gunmetal': 'neutral', 'bright': 'neutral', 'dark': 'neutral'}
# Light grey machinery sits near the default grey background; a mid blue is far from every class
# (as the Shepherd's).
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 6.0                  # hull units one swatch image spans (1.5 m at 0.25 m/unit)
SWATCH_ORDER = ['orange', 'grey', 'gunmetal', 'bright', 'dark']
SWATCH_DERIVED = []
MEASURED_CLASSES = ['orange', 'grey', 'gunmetal']
REFERENCE_CAMERA = 'three-quarter'

# The Shepherd's graded paint (shepherd-surface/palette.json, CIELAB mean): the Borer is owned
# onto it, so a drone on its cradle reads as the tender's own.
SHEPHERD = {'orange': [49.21, 20.45, 33.61], 'grey': [48.16, 0.42, 1.76], 'gunmetal': [28.5, 0.69, 1.46],
            'dark': [17.82, 0.37, 0.92]}


def derive_palette(classes):
    """Owned values: the Shepherd's means for the paints the two share, owned spreads.

    The reference is measured (kept as mean_measured) but graded onto the tender's paint, so the
    family holds even where Codex drifts. Bright rings and lips are a few hundred pixels and pick
    up their neighbours at the registration edge: owned outright.
    """
    owned_std = {'orange': [8.0, 2.0, 3.5], 'grey': [9.0, 0.6, 1.2], 'gunmetal': [10.0, 0.6, 1.6]}
    out = {c: dict(classes[c], mean=SHEPHERD[c], mean_measured=classes[c]['mean'], std=std,
                   std_measured=classes[c]['std'],
                   note="mean owned: the Shepherd's graded paint; spread owned (shading on the reference)")
           for c, std in owned_std.items()}
    out['bright'] = {'derived': 'owned: polished steel rings, lips, tines and pins, brighter than the grey',
                     'mean': [64.0, -0.3, 1.5], 'std': [6.0, 0.5, 0.8]}
    out['dark'] = {'derived': "owned: the Shepherd's near-black recesses",
                   'mean': SHEPHERD['dark'], 'std': [5.0, 0.5, 0.7]}
    return out


VARIATION_SPAN = {'orange': (4.0, 2.0, 2.5), 'grey': (3.5, 0.4, 0.8), 'gunmetal': (3.0, 0.4, 0.8),
                  'bright': (3.0, 0.3, 0.6), 'dark': (2.0, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'orange': (1.0, 1.0, 1.0), 'grey': (0.4, 0.6, 0.8), 'gunmetal': (0.0, 0.35, 0.7),
                 'bright': (0.0, 0.2, 0.5), 'dark': (0.0, 0.0, 0.25)}
ENGINES = [(-6.45, 0.0, -0.1), (-6.92, 6.0, -0.7), (-6.92, -6.0, -0.7)]   # drive mouths (geometry.json anchors)
STERN_SOOT = (-4.0, -7.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull spine'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'top', 'port-side', 'front',
                'starboard-quarter', 'port-aft-quarter', 'under-quarter', 'starboard-side']
REVIEW_CLOSE = ['head-detail', 'pod-detail', 'drive-detail', 'belly-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-side': (180, 0, 20, (0.0, 0.0, -0.5)),
    'starboard-quarter': (135, 30, 24, (0.0, 0.0, -0.5)),
    'port-aft-quarter': (-45, 28, 24, (0.0, 0.0, -0.5)),
    'under-quarter': (40, -35, 24, (0.0, 0.0, -0.5)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-aft-quarter', 'top']),
                ('views-whole-2', ['port-side', 'starboard-side', 'front', 'under-quarter']),
                ('views-close', ['head-detail', 'pod-detail', 'drive-detail', 'belly-detail'])]
CAPTION = "Borer surfacing, the Shepherd's Ko Combine livery: graded Codex part skins + graded class swatches."
