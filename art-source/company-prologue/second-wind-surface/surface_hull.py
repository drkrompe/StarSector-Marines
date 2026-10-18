"""The Second Wind's inputs to the surfacing harness (hull.py).

The harness's other hulls keep a file like this one in their own surfacing
folder; this one is the default when no --hull is given.
"""
NAME = 'Second Wind'
BLEND = 'build/geom/second-wind-geometry.blend'   # rebuild.sh step 1
GROUPS = ['port', 'starboard', 'bow', 'decks', 'turrets', 'arm', 'bay']
MATERIAL_CLASS = {
    'Chalked cream armor': 'cream',
    'Oxide orange armor': 'oxide',
    'Engine collars': 'gunmetal',
    'Recess and machinery': 'dark',
    'Grapple arm pale grey': 'pale',
    # Main-drive and RCS detail (sw_engines, sw_rcs): no part skins, class swatches only.
    'Drive bell liner': 'dark',
    'RCS housing gunmetal': 'gunmetal',
}
CLASS_ID = {'cream': (255, 0, 0), 'oxide': (0, 255, 0), 'gunmetal': (0, 0, 255), 'dark': (255, 255, 0),
            'pale': (0, 255, 255), 'other': (255, 0, 255)}
CLASSES = ['cream', 'oxide', 'gunmetal', 'dark', 'pale']
# A swap is a change of paint family (grey painted orange), not a lighter or
# darker grey: gunmetal and dark recess are one neutral family for the check.
FAMILY = {'cream': 'cream', 'oxide': 'oxide', 'gunmetal': 'neutral', 'dark': 'neutral', 'pale': 'neutral'}
SWATCH_UNITS = 8.0                  # hull units one swatch image spans (5.2 m at 0.65 m/unit)
SWATCH_ORDER = ['cream', 'oxide', 'gunmetal', 'dark']
SWATCH_DERIVED = [('pale', 'gunmetal')]
MEASURED_CLASSES = ['cream', 'oxide', 'gunmetal', 'dark']
REFERENCE_CAMERA = 'port-side'


def derive_palette(classes):
    # The grapple arm's pale grey is not on the reference (it is the arm's only
    # material and the reference's arm is a few pixels): an owned derivation.
    c, g = classes['cream'], classes['gunmetal']
    return {'pale': {'derived': 'not on the finish reference (the grapple arm is its only material): cream lightness, gunmetal chroma and spread',
                     'mean': [round(c['mean'][0] - 4, 2), g['mean'][1], g['mean'][2]],
                     'std': [round(c['std'][0] * 0.8, 2), g['std'][1], g['std'][2]]}}


# Painted classes may wander in hue a little; bare metal and recess only in
# lightness. Spans are +/- from the palette mean (variation.py).
VARIATION_SPAN = {'cream': (3.5, 1.0, 2.0), 'oxide': (4.0, 2.0, 2.5), 'gunmetal': (3.5, 0.4, 0.8),
                  'dark': (2.0, 0.2, 0.3), 'pale': (3.0, 0.4, 0.8)}
# Paint response per palette class (wear.py): (paint, fade, metal-reveal)
WEAR_RESPONSE = {'cream': (1.0, 1.0, 1.0), 'oxide': (1.0, 1.0, 1.0), 'pale': (1.0, 0.8, 1.0),
                 'gunmetal': (0.0, 0.35, 0.7), 'dark': (0.0, 0.0, 0.25)}
ENGINES = [(-39.5, 31.0, 0.0), (-38.5, 24.0, 0.0), (-38.5, -24.0, 0.0), (-39.5, -31.0, 0.0)]  # drive bell centres, hull units
STERN_SOOT = (-22.0, -40.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Main hull - level datum Z0'

# Review (review.py, compose_review.py). Whole-hull set: every side, above and below, then the close-ups.
REVIEW_WHOLE = ['port-side', 'port-quarter', 'high-quarter', 'starboard-side', 'starboard-quarter', 'high-quarter-starboard',
                'top', 'bow-quarter', 'stern', 'under-quarter']
REVIEW_CLOSE = ['port-flank-detail', 'port-aft-detail', 'bow-detail', 'bridge-detail', 'turret-detail',
                'starboard-aft-detail', 'stern-detail', 'bay-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'high-quarter': (45, 50, 170, (5, 0, 1)),
    'starboard-side': (180, 0, 132, (5, 0, 1)),
    'high-quarter-starboard': (135, 50, 170, (5, 0, 1)),
    'bow-quarter': (70, 20, 150, (5, 0, 1)),
    'under-quarter': (35, -35, 165, (5, 0, 1)),
    'bay-detail': (8, 4, 34, (14, 22, 0)),
    # The cinematics' framings at 1920x1080: C1's close starboard quarter (hull ~1,340 px
    # long, 30 deg above the deck) and C5's yard view (starboard stern quarter, ~780 px).
    'c1-starboard-quarter': (135, 30, 145, (5, 0, 1)),
    'c5-starboard-stern': (230, 18, 250, (0, 0, 1)),
}
# compose_review's thumbnail order
REVIEW_SHEET_WHOLE = ['port-side', 'port-quarter', 'high-quarter', 'top', 'starboard-side', 'starboard-quarter',
                      'high-quarter-starboard', 'bow-quarter', 'stern', 'under-quarter']
REVIEW_PAGES = [('views-port', ['port-side', 'port-quarter', 'high-quarter', 'top']),
                ('views-starboard', ['starboard-side', 'starboard-quarter', 'high-quarter-starboard', 'bow-quarter']),
                ('views-ends', ['stern', 'under-quarter', 'bow-detail', 'bridge-detail']),
                ('views-close', ['port-flank-detail', 'port-aft-detail', 'turret-detail', 'starboard-aft-detail']),
                ('views-close-2', ['stern-detail', 'bay-detail'])]
CAPTION = 'Second Wind whole-hull surfacing, clean build: graded Codex part skins + graded class swatches on every other base face.'
