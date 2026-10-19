"""The Broadsword's inputs to the shared surfacing harness (../../second-wind-surface/hull.py).

Every harness script runs with `--hull <this folder>` (after `--` for Blender);
see ../README.md for the sequence. Livery read off the upres sprite master
(art-source/aircraft/fighter-upres/broadsword/master-v1.png): yellowed khaki
armour plates over dark gunmetal structure (fuselage, nacelles, gun pods,
blades, strakes), polished steel barrels, bands and exhaust lips, near-black
recesses, grilles and bells; the canopy keeps its own glass material. The
geometry is its own package (../../broadsword-geometry/), built gear up so the
belly and its gear doors are clean. A front-line heavy fighter of the Legion's
air wing: light service wear, sooted nacelle sterns.
"""
NAME = 'Broadsword'
BLEND = 'build/geom/broadsword-geometry.blend'   # rebuild.sh step 1: ../../broadsword-geometry/build_broadsword.py --gear up
GROUPS = ['hull']
MATERIAL_CLASS = {
    'Yellowed khaki armour': 'khaki',
    'Gunmetal structure': 'steel',
    'Worn bright steel': 'bright',
    'Recess and machinery': 'dark',
}
# 'Canopy glass' keeps its own material: no class, no swatch; see ../README.md.
# The parked deck plane is stripped from the build (../strip_review_props.py).
CLASS_ID = {'khaki': (0, 255, 0), 'steel': (0, 0, 255), 'bright': (0, 255, 255), 'dark': (255, 255, 0), 'other': (255, 0, 255)}
CLASSES = ['khaki', 'steel', 'bright', 'dark']
FAMILY = {'khaki': 'khaki', 'steel': 'neutral', 'bright': 'neutral', 'dark': 'neutral'}
SWATCH_UNITS = 4.0                  # hull units one swatch image spans (2.0 m at 0.50 m/unit)
SWATCH_ORDER = ['khaki', 'steel', 'bright', 'dark']
SWATCH_DERIVED = []
MEASURED_CLASSES = ['khaki', 'steel']
REFERENCE_CAMERA = 'three-quarter'


def derive_palette(classes):
    """Owned values for what the reference cannot measure.

    The steel's measured spread is the reference's shading across the
    fuselage, nacelles and pods, not paint: its mean stands, the spread is
    owned. Bright barrels, bands and lips and the thin recesses, slats and
    vents are a few hundred pixels each and pick up their neighbours at the
    registration edge: owned outright.
    """
    out = {'steel': dict(classes['steel'], std=[8.0, 1.0, 1.4], std_measured=classes['steel']['std'],
                         note='spread owned: the measured one is shading')}
    out['bright'] = {'derived': 'owned: polished steel barrels, bands, lips and slats, brighter than the structure',
                     'mean': [62.0, -0.3, 1.5], 'std': [6.0, 0.5, 0.8]}
    out['dark'] = {'derived': 'owned: near-black recesses, vents, grilles and bells',
                   'mean': [13.0, 0.4, 1.0], 'std': [3.5, 0.5, 0.7]}
    return out


VARIATION_SPAN = {'khaki': (3.5, 1.2, 2.0), 'steel': (3.0, 0.4, 0.8), 'bright': (3.0, 0.3, 0.6), 'dark': (2.0, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'khaki': (1.0, 1.0, 1.0), 'steel': (0.0, 0.35, 0.7), 'bright': (0.0, 0.2, 0.5), 'dark': (0.0, 0.0, 0.25)}
ENGINES = [(-11.9, 5.5, -0.7), (-11.9, -5.5, -0.7)]   # drive mouths, hull units (geometry.json engines)
STERN_SOOT = (-7.0, -11.5)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull forward fuselage'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'top', 'port-side', 'front',
                'starboard-quarter', 'port-aft-quarter', 'under-quarter', 'starboard-side']
REVIEW_CLOSE = ['nose-detail', 'gun-detail', 'cockpit-detail', 'engine-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-side': (180, 0, 40, (3.5, 0.0, 0)),
    'starboard-quarter': (135, 30, 46, (3.5, 0.0, 0)),
    'port-aft-quarter': (-45, 28, 46, (3.5, 0.0, 0)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-aft-quarter', 'top']),
                ('views-whole-2', ['port-side', 'starboard-side', 'front', 'under-quarter']),
                ('views-close', ['nose-detail', 'gun-detail', 'cockpit-detail', 'engine-detail'])]
CAPTION = 'Broadsword surfacing, service livery: graded Codex part skins + graded class swatches on every other base face.'
