"""The Talon's inputs to the shared surfacing harness (../../second-wind-surface/hull.py).

Every harness script runs with `--hull <this folder>` (after `--` for Blender);
see README.md for the sequence. Livery read off the upres sprite master
(art-source/aircraft/fighter-upres/talon/master-v3.png): red enamel armour
plates over pale-to-mid worn steel structure, dark recesses and grilles,
bright steel rings and lips; the cyan canopy keeps its own glass material. Light service wear, sooted
drive drums.
"""
NAME = 'Talon'
BLEND = 'build/geom/talon-geometry.blend'   # rebuild.sh step 1: ../../fighters-geometry/build_fighter.py --fighter talon
GROUPS = ['hull']
MATERIAL_CLASS = {
    'Talon armour red': 'red',
    'Gunmetal structure': 'steel',
    'Worn bright steel': 'bright',
    'Recess and machinery': 'dark',
}
# 'Canopy glass' keeps its own glossy cyan material, like the Mudskipper's chrome: no class,
# no swatch (the swatch grader scales a coloured class's chroma about its source square,
# and no square on the sheet is cyan). The scale figure and pilot proxy are stripped from
# the build (../strip_review_props.py).
CLASS_ID = {'red': (0, 255, 0), 'steel': (0, 0, 255), 'bright': (0, 255, 255), 'dark': (255, 255, 0), 'other': (255, 0, 255)}
CLASSES = ['red', 'steel', 'bright', 'dark']
# Swap check: red painted where steel belongs (or the reverse) is a swap;
# lighter or darker steel is tone.
FAMILY = {'red': 'red', 'steel': 'neutral', 'bright': 'neutral', 'dark': 'neutral'}
SWATCH_UNITS = 4.0                  # hull units one swatch image spans (2.2 m at 0.55 m/unit)
SWATCH_ORDER = ['red', 'steel', 'bright', 'dark']
SWATCH_DERIVED = []
MEASURED_CLASSES = ['red', 'steel']
REFERENCE_CAMERA = 'three-quarter'


def derive_palette(classes):
    """Owned values for what the reference cannot measure.

    The steel's measured L spread (16) is the reference's shading across the
    drive drums' faces, not paint: its mean stands, the spread is owned. The
    bright rings (63 px) and the recesses (under 1,000 px of thin slots, which
    pick up red and steel at the registration edge) are owned outright,
    against the measured steel: bright is polished steel well above it, dark
    the near-black the reference paints in the vents.
    """
    out = {'steel': dict(classes['steel'], std=[8.0, 1.0, 1.4], std_measured=classes['steel']['std'],
                         note='spread owned: the measured one is shading across the drums')}
    out['bright'] = {'derived': 'owned: polished steel rings and lips, brighter than the structure',
                     'mean': [62.0, -0.3, 1.5], 'std': [6.0, 0.5, 0.8]}
    out['dark'] = {'derived': 'owned: near-black recesses, vents and grilles',
                   'mean': [13.0, 0.4, 1.0], 'std': [3.5, 0.5, 0.7]}
    return out


VARIATION_SPAN = {'red': (4.0, 2.0, 2.5), 'steel': (3.0, 0.4, 0.8), 'bright': (3.0, 0.3, 0.6),
                  'dark': (2.0, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'red': (1.0, 1.0, 1.0), 'steel': (0.0, 0.35, 0.7), 'bright': (0.0, 0.2, 0.5),
                 'dark': (0.0, 0.0, 0.25)}
ENGINES = [(-9.0, 6.0, -0.5), (-9.0, -4.0, -0.5)]   # drive mouths, hull units (geometry.json engines)
STERN_SOOT = (-5.0, -9.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull aft body'
SHEARED_FACES = True   # wings bent per vertex after build (pass-3 rework): out-of-plane faces

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'top', 'port-side', 'front',
                'starboard-quarter', 'port-aft-quarter', 'under-quarter', 'starboard-side']
REVIEW_CLOSE = ['gun-detail', 'cockpit-detail', 'aft-detail', 'engine-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-side': (180, 0, 30, (2, 1, 0)),
    'starboard-quarter': (135, 30, 35, (2, 1, 0)),
    'port-aft-quarter': (-45, 28, 35, (2, 1, 0)),
    'under-quarter': (40, -35, 35, (2, 1, 0)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-aft-quarter', 'top']),
                ('views-whole-2', ['port-side', 'starboard-side', 'front', 'under-quarter']),
                ('views-close', ['gun-detail', 'cockpit-detail', 'aft-detail', 'engine-detail'])]
CAPTION = 'Talon surfacing, service livery: graded Codex part skins + graded class swatches on every other base face.'
