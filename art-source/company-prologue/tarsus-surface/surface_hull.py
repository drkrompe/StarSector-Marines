"""The Tarsus's inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../tarsus-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh for the
sequence. Livery: an independent merchant's Tarsus, read off tarsus_base.png
(reference/sprite-livery.png): weathered ochre-tan module plating on the arms,
aft body and castle, pale cream deck plating on the central body, rust-brown
castings on the four turret drums and the drive housings, mid-grey structure,
white pressure tanks, signal-orange panels, copper drive cans, gunmetal
machinery and near-black recesses. A hazard-duty hauler in service: service
wear, dusty from planetside lifts, soot at the drives.
"""
NAME = 'Tarsus'
BLEND = 'build/geom/tarsus-geometry.blend'   # rebuild.sh step 1: ../tarsus-geometry/build_tarsus.py -- --out <this>/build/geom
GROUPS = ['arms', 'body']
MATERIAL_CLASS = {
    'Ochre module plating': 'ochre',
    'Pale cream deck plating': 'cream',
    'Light grey deck band': 'grey',
    'Rust brown casting': 'rust',
    'Drive housing red oxide': 'rust',
    'Mid grey structure': 'grey',
    'Drive pale radiator panel': 'grey',
    'White pressure tank': 'white',
    'Signal orange panel': 'orange',
    'Copper drive can': 'copper',
    'Gunmetal machinery': 'gunmetal',
    'Drive bright metal': 'gunmetal',
    'Drive radiator fin': 'gunmetal',
    'Drive chamber dark metal': 'dark',
    'Recess and lattice': 'dark',
}
# Kept on their own materials (no class, no swatch): bridge glazing (emissive),
# running, marker and stern lamps, the bay glow, the drive bell liners, the
# faded-blue transfer pipe, red valve handwheels and the red hazard marks.
CLASS_ID = {'ochre': (255, 0, 0), 'cream': (255, 255, 0), 'grey': (0, 255, 0), 'rust': (128, 0, 0),
            'white': (255, 255, 255), 'orange': (255, 128, 0), 'copper': (255, 0, 255), 'gunmetal': (0, 0, 255),
            'dark': (128, 0, 255), 'other': (0, 255, 255)}
CLASSES = ['ochre', 'cream', 'grey', 'rust', 'white', 'orange', 'copper', 'gunmetal', 'dark']
# Swap check: ochre, cream and white are separate paints (an ochre arm painted cream is a
# swap); rust, copper and orange are the warm metals and signal paint; the steels and
# recesses are one neutral family.
FAMILY = {'ochre': 'ochre', 'cream': 'cream', 'white': 'cream', 'grey': 'neutral', 'rust': 'rust',
          'copper': 'rust', 'orange': 'orange', 'gunmetal': 'neutral', 'dark': 'neutral'}
# A mid blue is far from every class.
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 12.0                 # hull units one swatch image spans (6 m at 0.50 m/unit)
SWATCH_ORDER = ['ochre', 'cream', 'rust', 'grey', 'orange', 'gunmetal']
SWATCH_DERIVED = [('white', 'cream'), ('copper', 'rust'), ('dark', 'gunmetal')]
MEASURED_CLASSES = ['ochre', 'cream', 'grey', 'rust', 'white', 'orange', 'gunmetal', 'dark']
REFERENCE_CAMERA = 'three-quarter'


def derive_palette(classes):
    # Thin fittings and recess slots pick up their neighbours at the registration
    # edge: the means stand, the spreads are owned (as on the Colossus and Kite).
    owned = {'ochre': [8.0, 1.5, 2.5], 'cream': [7.0, 0.8, 1.5], 'grey': [8.0, 0.6, 1.2],
             'rust': [7.0, 1.5, 2.5], 'white': [5.0, 0.4, 0.8], 'orange': [8.0, 2.0, 3.5],
             'gunmetal': [9.0, 0.6, 1.4], 'dark': [6.0, 0.5, 0.8]}
    out = {c: dict(classes[c], std=std, std_measured=classes[c]['std'],
                   note='spread owned: shading and edge contamination on the reference inflate it')
           for c, std in owned.items() if c in classes}
    if 'dark' in out:
        # Recesses and seam grooves cover 233 reference pixels, every one on a
        # registration edge, so the measured mean (L 40) is lighter than gunmetal.
        # The sheets' own seam grooves read L 21-29 before grading: own that.
        out['dark'] = dict(out['dark'], mean=[24.0, 0.8, 1.0], mean_measured=out['dark']['mean'],
                           note='mean owned: the reference measures only edge pixels for recesses')
    if 'rust' in out:
        m = out['rust']['mean']
        out['copper'] = {'derived': 'rust + [+6, +2, +4]: the drive cans are bare copper beside the painted castings',
                         'mean': [m[0] + 6.0, m[1] + 2.0, m[2] + 4.0], 'std': [7.0, 1.5, 2.5]}
    return out


VARIATION_SPAN = {'ochre': (3.5, 1.2, 1.5), 'cream': (3.0, 0.6, 1.0), 'grey': (3.0, 0.4, 0.8),
                  'rust': (3.5, 1.2, 1.5), 'white': (2.0, 0.2, 0.4), 'orange': (4.0, 1.5, 2.5),
                  'copper': (3.5, 1.2, 1.5), 'gunmetal': (3.0, 0.4, 0.8), 'dark': (2.0, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'ochre': (1.0, 1.0, 1.0), 'cream': (1.0, 0.8, 1.0), 'grey': (1.0, 0.8, 1.0),
                 'rust': (1.0, 1.0, 1.0), 'white': (1.0, 0.6, 0.8), 'orange': (1.0, 1.0, 1.0),
                 'copper': (0.3, 0.5, 0.6), 'gunmetal': (0.0, 0.35, 0.7), 'dark': (0.0, 0.0, 0.25)}
# Drive mouths (geometry.json engines 1-6), for soot.
ENGINES = [(-78.4, 19.0, 1.5), (-76.4, 26.0, -10.5), (-76.4, 11.0, -10.5),
           (-76.4, -11.0, -10.5), (-76.4, -25.0, -10.5), (-78.4, -18.0, 1.5)]
STERN_SOOT = (-62.0, -78.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull central body'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'starboard-quarter', 'port-side', 'top', 'stern', 's7-astern', 's10-above-behind']
REVIEW_CLOSE = ['bow-detail', 'drum-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    's7-astern': (-90, 11, 150, (-40, 1, 0)),
    's10-above-behind': (-121, 36, 200, (5, 1, 0)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-side', 'top']),
                ('views-shots', ['s7-astern', 's10-above-behind', 'stern', 'bow-detail'])]
CAPTION = 'Tarsus surfacing, independent merchant livery: graded Codex part skins + graded class swatches on every other base face.'
