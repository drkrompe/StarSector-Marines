"""The Manticore (P)'s inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../manticore-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh.

Livery: the pirates' read off manticore_pirate.png: scarlet enamel on the
combat armour (the glacis bands, the wing armour, the beak and its strakes, the
gun nacelles' outboard plates, the core and the bridge tier), over a dark
brown-gunmetal structure (the wing framework, the engine block's machinery,
the plate seams, the drive cans); the grey service plate of fittings and lids;
the pale command dome. The sprite's own markings (the skull on the dome, the
yellow flame flashes either side of it) are laid on after grading by
livery_decal.py. Wear: `pirate`, heavy and neglected, matching the Kite (P),
the Falcon (P) and the Colossus Mk.III so the pirate fleet reads as one.
"""
NAME = 'Manticore (P)'
BLEND = 'build/geom/manticore.blend'   # rebuild.sh step 1: ../manticore-geometry/build_manticore.py -- --out <this>/build/geom
GROUPS = ['hull', 'wings', 'bow']
MATERIAL_CLASS = {
    'Manticore pirate red armour': 'red',
    'Manticore gunmetal machinery': 'gunmetal',
    'Manticore grey plate': 'plate',
    'Manticore pale command plating': 'pale',
    'Manticore recess': 'dark',
    'Manticore heat vent': 'dark',
}
# Kept on their own materials (no class, no swatch): the turret kit's own weathered
# paints, the bridge glazing and viewports (a swatch is a painted plate), and the lamps.
CLASS_ID = {'red': (255, 0, 0), 'gunmetal': (0, 0, 255), 'plate': (0, 255, 0), 'pale': (255, 255, 0),
            'dark': (128, 0, 255), 'other': (255, 0, 255)}
CLASSES = ['red', 'gunmetal', 'plate', 'pale', 'dark']
# Swap check: scarlet painted where structure belongs (or the reverse) is a swap, as is
# pale where red belongs; gunmetal, grey plate and recess differ in tone only.
FAMILY = {'red': 'red', 'pale': 'pale', 'gunmetal': 'neutral', 'plate': 'neutral', 'dark': 'neutral'}
BACKGROUND = (70, 110, 170)          # a mid blue, far from every class
SWATCH_UNITS = 12.0                  # hull units one swatch image spans (7.1 m at 0.59 m/unit)
SWATCH_ORDER = ['red', 'gunmetal', 'plate', 'pale']
SWATCH_DERIVED = [('dark', 'gunmetal')]
MEASURED_CLASSES = ['red', 'gunmetal', 'plate', 'pale', 'dark']
REFERENCE_CAMERA = 'three-quarter'


def derive_palette(classes):
    # Thin structure members, seams and recesses pick up their neighbours at the
    # registration edge: the means stand, the spreads are owned (as on the Kite (P),
    # the Hound and the Colossus Mk.III).
    owned = {'red': [8.0, 2.0, 3.0], 'gunmetal': [9.0, 0.8, 1.6], 'plate': [8.0, 0.6, 1.2],
             'pale': [7.0, 0.5, 1.0], 'dark': [6.0, 0.6, 1.0]}
    return {c: dict(classes[c], std=std, std_measured=classes[c]['std'],
                    note='spread owned: shading and edge contamination on the reference inflate it')
            for c, std in owned.items() if c in classes}


VARIATION_SPAN = {'red': (4.0, 1.5, 2.0), 'gunmetal': (3.0, 0.4, 0.8), 'plate': (3.0, 0.4, 0.8),
                  'pale': (3.0, 0.5, 1.0), 'dark': (2.0, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'red': (1.0, 1.0, 1.0), 'gunmetal': (0.0, 0.35, 0.7), 'plate': (0.6, 0.6, 1.0),
                 'pale': (1.0, 0.8, 1.0), 'dark': (0.0, 0.0, 0.25)}
# Drive mouths (geometry.json engines), for soot.
ENGINES = [(-66.0, 17.0, 0.0), (-62.0, 0.0, 0.0), (-66.0, -16.0, 0.0), (-62.0, 30.0, 0.0), (-62.0, -30.0, 0.0)]
STERN_SOOT = (-45.0, -64.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull core'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'starboard-quarter', 'port-side', 'top', 'front', 'stern']
REVIEW_CLOSE = ['bow-detail', 'turret-detail', 'wing-detail', 'engine-detail', 'stern-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-quarter': (130, 32, 215, (10, 0, 0)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-side', 'top']),
                ('views-close', ['bow-detail', 'turret-detail', 'wing-detail', 'engine-detail'])]
CAPTION = 'Manticore (P) surfacing, pirate livery: graded Codex part skins + graded class swatches on every other base face.'
