"""The Mudskipper's inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Livery read off the vanilla sprite (reference/sprite-livery.png): off-white and
grey-green hull plate, deep red chevron stripes angling in toward the bridge,
tan modules and fenders, dark grey-brown drive steel with pinkish burnished
bands. The finish reference (reference/finish-reference.png) is one Codex
repaint of the port-quarter view with that livery and civilian working wear.
"""
NAME = 'Mudskipper'
BLEND = 'build/geom/mudskipper-geometry.blend'   # ../mudskipper-geometry/build_reference.py -- --out <this>/build/geom
GROUPS = ['port', 'tops', 'bow', 'drives']
MATERIAL_CLASS = {
    'Pale hull plate': 'white',
    'Deck cabin hood plate': 'white',
    'Grey-green sponson plate': 'grey',
    'Red stripe panel': 'red',
    'Drive pump housing red': 'red',
    'Tan hatch and fender work': 'tan',
    'Drive casing steel': 'steel',
    'Drive pod deck plate': 'steel',
    'Drive band burnished plate': 'burnished',
    'Recess and machinery': 'dark',
    'Drive bell heat-darkened steel': 'dark',
    'Leg sole rubber': 'dark',
    'Freight ramp toe rubber': 'dark',
}
# 'Leg ram chrome' (the landing legs' rams and guide rods) stays bright
# polished metal, like the glazing and lamps: no class, no swatch.
# Seven classes need more than the 0/255 corners: the class passes write ids
# exactly (parts.id_linear), so 128 components are safe.
CLASS_ID = {'white': (255, 0, 0), 'red': (0, 255, 0), 'grey': (0, 0, 255), 'dark': (255, 255, 0),
            'tan': (0, 255, 255), 'steel': (255, 128, 0), 'burnished': (128, 0, 255), 'other': (255, 0, 255)}
CLASSES = ['white', 'red', 'tan', 'grey', 'steel', 'dark', 'burnished']
FAMILY = {'white': 'white', 'red': 'red', 'tan': 'tan', 'grey': 'neutral', 'steel': 'neutral',
          'dark': 'neutral', 'burnished': 'neutral'}
# The off-white plate sits within imgops.segment's threshold of the default 214
# grey, which cut the plates out as background; mid blue is far from every class.
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 6.0                  # hull units one swatch image spans (5.1 m at 0.85 m/unit)
SWATCH_ORDER = ['white', 'red', 'tan', 'steel']
SWATCH_DERIVED = [('grey', 'white'), ('dark', 'steel'), ('burnished', 'steel')]
MEASURED_CLASSES = ['white', 'red', 'tan', 'grey', 'steel', 'dark', 'burnished']
REFERENCE_CAMERA = 'port-quarter'
VARIATION_SPAN = {'white': (3.5, 0.8, 1.5), 'red': (4.0, 2.0, 2.5), 'tan': (3.5, 1.0, 2.0), 'grey': (3.0, 0.6, 1.0),
                  'steel': (3.5, 0.4, 0.8), 'dark': (2.0, 0.2, 0.3), 'burnished': (3.0, 0.6, 1.0)}
WEAR_RESPONSE = {'white': (1.0, 1.0, 1.0), 'red': (1.0, 1.0, 1.0), 'tan': (1.0, 1.0, 1.0), 'grey': (1.0, 0.8, 1.0),
                 'steel': (0.0, 0.35, 0.7), 'dark': (0.0, 0.0, 0.25), 'burnished': (0.0, 0.3, 0.6)}
ENGINES = [(-36.0, 0.0, 0.0), (-36.0, 25.0, 0.0), (-36.0, -23.0, 0.0)]  # drive bell mouths, hull units
STERN_SOOT = (-20.0, -36.0)
HULL_OBJECT = 'Main hull - level datum Z0'

# Review: the geometry's own cameras plus these (azimuth from +Y toward +X, elevation, ortho scale, target).
REVIEW_WHOLE = ['port-side', 'port-quarter', 'high-quarter', 'top', 'starboard-side', 'starboard-quarter',
                'bow', 'bow-quarter', 'stern-quarter', 'under-quarter']
REVIEW_CLOSE = ['bow-detail', 'bridge-detail', 'turret-detail', 'waist-detail', 'stern-detail', 'engine-detail',
                'junction-detail', 'junction-starboard']
REVIEW_EXTRA = {
    'starboard-side': (180, 0, 100, (3, 0, 1)),
    'starboard-quarter': (140, 30, 118, (3, 0, 0)),
    'high-quarter': (41, 55, 112, (3, 0, 1)),
    'under-quarter': (41, -35, 118, (3, 0, 1)),
    'bow-quarter': (75, 20, 112, (3, 0, 1)),
}
REVIEW_SHEET_WHOLE = REVIEW_WHOLE
REVIEW_PAGES = [('views-port', ['port-side', 'port-quarter', 'high-quarter', 'top']),
                ('views-starboard', ['starboard-side', 'starboard-quarter', 'bow-quarter', 'under-quarter']),
                ('views-ends', ['bow', 'stern-quarter', 'bow-detail', 'bridge-detail']),
                ('views-close', ['turret-detail', 'waist-detail', 'stern-detail', 'engine-detail']),
                ('views-junction', ['junction-detail', 'junction-starboard'])]
CAPTION = 'Mudskipper surfacing, clean build: graded Codex part skins + graded class swatches on every other base face.'
