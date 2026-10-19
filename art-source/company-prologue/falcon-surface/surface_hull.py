"""The Falcon (P)'s inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../falcon-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh for the
sequence. Livery: the pirates' repaint of a Midline light cruiser, read off
falcon_p.png. The prongs are dark khaki armour, with plates repainted pirate red
and light sand where the sprite paints its slashes; the geometry carries that
per plate (falcon-geometry/fc_detail.Livery), so the skins keep each plate its own
colour. There is grey plate on the belts, central body and dome, warm gunmetal
machinery, bare engine metal on the tanks, cores and pipes, hazard-yellow drive
bands and near-black recesses. A pirate flagship: hard-used and patched, so the
`pirate` row of wear.json is heavier than any navy's.
"""
NAME = 'Falcon (P)'
BLEND = 'build/geom/falcon-p-geometry.blend'   # rebuild.sh step 1: ../falcon-geometry/build_falcon.py -- --out <this>/build/geom
GROUPS = ['prongs', 'body']
MATERIAL_CLASS = {
    'Falcon khaki armour': 'khaki',
    'Falcon pirate red panel': 'red',
    'Falcon sand panel': 'sand',
    'Falcon grey plate': 'plate',
    'Falcon gunmetal machinery': 'gunmetal',
    'Falcon bare engine metal': 'bare',
    'Falcon hazard yellow': 'hazard',
    'Falcon recess': 'dark',
}
# Kept on their own materials (no class, no swatch): the bridge glazing and
# viewports, running lamps and the turret kit's own weathered paints.
CLASS_ID = {'khaki': (255, 128, 0), 'red': (255, 0, 0), 'sand': (255, 255, 0), 'plate': (0, 255, 0),
            'gunmetal': (0, 0, 255), 'bare': (0, 255, 255), 'hazard': (128, 128, 0), 'dark': (128, 0, 255),
            'other': (255, 0, 255)}
CLASSES = ['khaki', 'red', 'sand', 'plate', 'gunmetal', 'bare', 'hazard', 'dark']
# Swap check: khaki and sand are one tan family (lighter or darker is tone);
# the pirate red and the hazard yellow are their own; the greys, steels and
# recesses are one neutral family.
FAMILY = {'khaki': 'tan', 'sand': 'tan', 'red': 'red', 'hazard': 'hazard',
          'plate': 'neutral', 'gunmetal': 'neutral', 'bare': 'neutral', 'dark': 'neutral'}
# Khaki, sand and grey sit near the default grey background; a mid blue is far
# from every class.
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 10.0                 # hull units one swatch image spans (8.1 m at 0.81 m/unit)
SWATCH_ORDER = ['khaki', 'red', 'sand', 'plate', 'gunmetal', 'dark']
SWATCH_DERIVED = [('bare', 'gunmetal'), ('hazard', 'sand')]
MEASURED_CLASSES = ['khaki', 'red', 'sand', 'plate', 'gunmetal', 'dark']
REFERENCE_CAMERA = 'three-quarter'
# The prongs are lofts between stations whose quads twist slightly: read
# visibility against the triangle tree (parts.select_faces).
SHEARED_FACES = True

# Derived classes: CIELAB offset from the measured parent. Bare engine metal is
# the gunmetal steel unpainted and polished by heat (the geometry's lilac-grey
# cores, falcon_p.png's bare cylinders); the hazard bands are a strong yellow
# laid on the sand (the sprite's chevrons, about 200,165,40).
DERIVED = {'bare': ('gunmetal', (+30.0, +1.0, -3.0)),
           'hazard': ('sand', (+4.0, -8.0, +32.0))}


def derive_palette(classes):
    # The reference's measured spreads carry its facet shading and, on thin
    # parts, their neighbours at the registration edge: the means stand, the
    # spreads are owned (as on the Colossus and Onslaught).
    owned = {'khaki': [8.0, 1.2, 2.5], 'red': [8.0, 2.0, 2.5], 'sand': [8.0, 1.5, 3.0],
             'plate': [8.0, 0.6, 1.2], 'gunmetal': [9.0, 0.6, 1.4], 'dark': [6.0, 0.5, 0.8]}
    out = {c: dict(classes[c], std=std, std_measured=classes[c]['std'],
                   note='spread owned: shading and edge contamination on the reference inflate it')
           for c, std in owned.items() if c in classes}
    for c, (parent, off) in DERIVED.items():
        m = out[parent]['mean']
        out[c] = {'derived': f"{parent} + {list(off)} (the geometry's designed tone offset)",
                  'mean': [m[0] + off[0], m[1] + off[1], m[2] + off[2]], 'std': list(out[parent]['std'])}
    return out


VARIATION_SPAN = {'khaki': (4.0, 1.0, 2.0), 'red': (4.0, 2.0, 2.0), 'sand': (4.0, 1.2, 2.5),
                  'plate': (3.0, 0.5, 1.0), 'gunmetal': (3.0, 0.4, 0.8), 'bare': (3.0, 0.4, 0.8),
                  'hazard': (3.0, 1.0, 3.0), 'dark': (2.0, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'khaki': (1.0, 1.0, 1.0), 'red': (1.0, 1.0, 1.0), 'sand': (1.0, 1.0, 1.0),
                 'plate': (1.0, 0.8, 1.0), 'gunmetal': (0.0, 0.35, 0.7), 'bare': (0.0, 0.2, 0.5),
                 'hazard': (1.0, 1.0, 1.0), 'dark': (0.0, 0.0, 0.25)}
# Drive bell centres, hull units (geometry.json engines): the six main drives;
# the outrigger thrusters are small and burn briefly.
ENGINES = [(-81.0, 38.0, 0.0), (-83.0, 27.0, 0.0), (-84.0, 16.0, 0.0),
           (-83.0, -16.0, 0.0), (-83.0, -27.0, 0.0), (-82.0, -38.0, 0.0)]
STERN_SOOT = (-60.0, -84.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull central body'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'port-aft-quarter', 'port-side', 'top', 'front', 'stern',
                'starboard-side', 'starboard-quarter', 'under-quarter']
REVIEW_CLOSE = ['bow-detail', 'bridge-detail', 'turret-detail', 'engine-detail', 'stern-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'port-aft-quarter': (-45, 32, 250, (20, 0, 0)),
    'starboard-quarter': (135, 32, 250, (20, 0, 0)),
    'starboard-side': (180, 0, 236, (23, 0, 0)),
    'under-quarter': (40, -35, 250, (20, 0, 0)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'port-aft-quarter', 'port-side', 'top']),
                ('views-whole-2', ['front', 'stern', 'starboard-quarter', 'under-quarter']),
                ('views-close', ['bow-detail', 'bridge-detail', 'turret-detail', 'engine-detail']),
                ('views-close-2', ['stern-detail', 'starboard-side', 'port-aft-quarter', 'three-quarter'])]
CAPTION = 'Falcon (P) surfacing, pirate livery: graded Codex part skins + graded class swatches on every other base face.'
