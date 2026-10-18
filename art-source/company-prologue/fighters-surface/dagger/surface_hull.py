"""The Dagger's inputs to the shared surfacing harness (../../second-wind-surface/hull.py).

Every harness script runs with `--hull <this folder>` (after `--` for Blender);
see ../README.md for the sequence. Livery read off the upres sprite master
(art-source/aircraft/fighter-upres/dagger/master-v2.png): pale blue-grey armour over dark gunmetal structure, a cream torpedo store, polished steel lips and rings, near-black recesses and grilles; the canopy,
lamps and sensor windows keep their own glass material. A front-line torpedo bomber: light service wear, sooted drive block.
"""
NAME = 'Dagger'
BLEND = 'build/geom/dagger-geometry.blend'   # rebuild.sh step 1: ../../fighters-geometry/build_fighter.py --fighter dagger
GROUPS = ['hull']
MATERIAL_CLASS = {
    'Dagger blue-gray armour': 'blue',
    'Gunmetal structure': 'steel',
    'Worn bright steel': 'bright',
    'Recess and machinery': 'dark',
    'Torpedo store cream': 'cream',
}
# 'Canopy glass' keeps its own material (canopy, lamps, sensor windows): no class,
# no swatch; see ../README.md. Review props are stripped from the build (../strip_review_props.py).
CLASS_ID = {'blue': (0, 255, 0), 'steel': (0, 0, 255), 'bright': (0, 255, 255), 'dark': (255, 255, 0), 'cream': (255, 0, 0), 'other': (255, 0, 255)}
CLASSES = ['blue', 'steel', 'bright', 'dark', 'cream']
FAMILY = {'blue': 'blue', 'steel': 'neutral', 'bright': 'neutral', 'dark': 'neutral', 'cream': 'cream'}
SWATCH_UNITS = 4.0                  # hull units one swatch image spans (2.0 m at 0.5 m/unit)
SWATCH_ORDER = ['blue', 'steel', 'bright', 'dark', 'cream']
SWATCH_DERIVED = []
MEASURED_CLASSES = ['blue', 'steel']
REFERENCE_CAMERA = 'three-quarter'


def derive_palette(classes):
    """Owned values for what the reference cannot measure.

    The steel's measured spread is the reference's shading across its parts,
    not paint: its mean stands, the spread is owned. Bright rings and lips and
    the thin recesses and vents are a few hundred pixels each and pick up their
    neighbours at the registration edge: owned outright.
    The torpedo store is a few thousand pixels at most: its cream is owned too.
    """
    out = {'steel': dict(classes['steel'], std=[8.0, 1.0, 1.4], std_measured=classes['steel']['std'],
                         note='spread owned: the measured one is shading')}
    out['bright'] = {'derived': 'owned: polished steel rings, lips and pins, brighter than the structure',
                     'mean': [62.0, -0.3, 1.5], 'std': [6.0, 0.5, 0.8]}
    out['dark'] = {'derived': 'owned: near-black recesses, vents and grilles',
                   'mean': [13.0, 0.4, 1.0], 'std': [3.5, 0.5, 0.7]}
    out['cream'] = {'derived': 'owned: the torpedo store off-white cream enamel',
                    'mean': [82.0, 1.5, 12.0], 'std': [3.0, 0.8, 1.5]}
    return out


VARIATION_SPAN = {'blue': (3.5, 1.2, 2.0), 'steel': (3.0, 0.4, 0.8), 'bright': (3.0, 0.3, 0.6), 'dark': (2.0, 0.2, 0.3), 'cream': (3.0, 1.0, 2.0)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'blue': (1.0, 1.0, 1.0), 'steel': (0.0, 0.35, 0.7), 'bright': (0.0, 0.2, 0.5), 'dark': (0.0, 0.0, 0.25), 'cream': (1.0, 1.0, 1.0)}
ENGINES = [(-13.0, 0.0, -0.5)]   # drive mouths, hull units (geometry.json engines)
STERN_SOOT = (-9.0, -13.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull cockpit body'
SHEARED_FACES = True   # wings bent per vertex after build (pass-3 rework): out-of-plane faces

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'top', 'port-side', 'front',
                'starboard-quarter', 'port-aft-quarter', 'under-quarter', 'starboard-side']
REVIEW_CLOSE = ['nose-detail', 'cockpit-detail', 'store-detail', 'engine-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-side': (180, 0, 44, (1.0, 2.0, 0)),
    'starboard-quarter': (135, 30, 49, (1.0, 2.0, 0)),
    'port-aft-quarter': (-45, 28, 49, (1.0, 2.0, 0)),
    'under-quarter': (40, -35, 49, (1.0, 2.0, 0)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-aft-quarter', 'top']), ('views-whole-2', ['port-side', 'starboard-side', 'front', 'under-quarter']), ('views-close', ['nose-detail', 'cockpit-detail', 'store-detail', 'engine-detail'])]
CAPTION = 'Dagger surfacing, service livery: graded Codex part skins + graded class swatches on every other base face.'
