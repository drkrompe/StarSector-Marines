"""The Shepherd (Morrow)'s inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../shepherd-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh for the
sequence. Livery: the vanilla Ko Combine drone tender read off drone_tender.png
(reference/sprite-livery.png): sand-tan hull plating, chalky cream on the
bridge's starboard crown and fittings, a light grey machinery drum, burnt
orange drive pods and outrigger chords, gunmetal machinery, near-black
recesses and the dark drone bay, tinted glazing. Clean state, service wear.
"""
NAME = 'Shepherd'
BLEND = 'build/geom/shepherd-geometry.blend'   # rebuild.sh step 1: ../shepherd-geometry/build_shepherd.py -- --out <this>/build/geom
GROUPS = ['hull', 'bridge', 'gantry', 'bay', 'engines']
MATERIAL_CLASS = {
    'Ko sand hull plating': 'sand',
    'Ko sand armour module': 'sand',
    'Ko sand armour module worn': 'sand',
    'Chalked cream fittings': 'cream',
    'Habitat shell cream': 'cream',
    'Gantry light grey': 'grey',
    'Burnt orange drive mount': 'orange',
    'Gunmetal machinery': 'gunmetal',
    'Drone bay pod armour': 'gunmetal',
    'Recess and machinery': 'dark',
    'Drone bay deck plate': 'dark',
    'Bridge glazing': 'glass',
    'Bridge tinted glass': 'glass',
}
# Kept on their own materials (no class, no swatch): lamps and glows, the
# drone bay's hazard yellow and turntable ring, drive bell liners and
# heat-blued nozzles, chrome rods, fender rubber, weld beads, hazard placards.
CLASS_ID = {'sand': (255, 0, 0), 'cream': (255, 255, 0), 'grey': (0, 255, 0), 'orange': (255, 128, 0),
            'gunmetal': (0, 0, 255), 'dark': (128, 0, 255), 'glass': (0, 255, 255), 'other': (255, 0, 255)}
CLASSES = ['sand', 'cream', 'grey', 'orange', 'gunmetal', 'dark', 'glass']
# Swap check: the warm paints are separate families (sand painted where orange
# belongs is a swap); grey, gunmetal, recess and glass are one neutral family.
FAMILY = {'sand': 'sand', 'cream': 'cream', 'grey': 'neutral', 'orange': 'orange',
          'gunmetal': 'neutral', 'dark': 'neutral', 'glass': 'neutral'}
# Sand, cream and light grey sit near the default grey background; a mid blue
# is far from every class (as the Hermes and the Mudskipper).
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 7.0                  # hull units one swatch image spans (5.5 m at 0.78 m/unit)
SWATCH_ORDER = ['sand', 'cream', 'grey', 'orange', 'gunmetal', 'dark']
SWATCH_DERIVED = [('glass', 'dark')]
MEASURED_CLASSES = ['sand', 'cream', 'grey', 'orange', 'gunmetal', 'dark']
REFERENCE_CAMERA = 'three-quarter'


def derive_palette(classes):
    # The reference's measured spreads (L std 8-14) carry its facet shading and,
    # on thin parts, their neighbours at the registration edge: the means stand,
    # the spreads are owned (as on the Hermes and the Hound (A)).
    owned = {'sand': [8.0, 1.0, 2.5], 'cream': [7.0, 0.6, 1.5], 'grey': [9.0, 0.6, 1.2],
             'orange': [8.0, 2.0, 3.5], 'gunmetal': [10.0, 0.6, 1.6], 'dark': [6.5, 0.5, 0.8]}
    out = {c: dict(classes[c], std=std, std_measured=classes[c]['std'],
                   note='spread owned: shading and edge contamination on the reference inflate it')
           for c, std in owned.items()}
    # The glazing is a few pixels on the reference: an owned tinted-glass value.
    return out | {'glass': {'derived': 'owned: tinted bridge glazing, dark with a faint warm cast',
                      'mean': [20.0, 0.5, 2.0], 'std': [5.0, 0.8, 1.2]}}


VARIATION_SPAN = {'sand': (3.0, 0.6, 1.2), 'cream': (3.0, 0.5, 1.0), 'grey': (3.0, 0.4, 0.8),
                  'orange': (3.0, 0.8, 1.2), 'gunmetal': (3.0, 0.4, 0.8), 'dark': (2.0, 0.2, 0.3),
                  'glass': (1.5, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'sand': (1.0, 1.0, 1.0), 'cream': (1.0, 1.0, 1.0), 'grey': (1.0, 0.8, 1.0),
                 'orange': (1.0, 1.0, 1.0), 'gunmetal': (0.0, 0.35, 0.7), 'dark': (0.0, 0.0, 0.25),
                 'glass': (0.0, 0.0, 0.0)}
ENGINES = [(-40.0, 20.0, -1.0), (-40.0, -19.0, -1.0)]   # main drive bell centres, hull units (sh_engines.ENGINES, POD_Z)
STERN_SOOT = (-28.0, -34.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull central body'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'starboard-quarter', 'port-side', 'top', 'front', 'stern',
                'starboard-side', 'port-aft-quarter', 'under-quarter']
REVIEW_CLOSE = ['bow-detail', 'bridge-detail', 'bridge-front', 'dronebay-detail', 'gantry-detail',
                'turret-detail', 'engine-detail', 'stern-detail']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-side': (180, 0, 100, (0, 0, 0)),
    'port-aft-quarter': (-45, 28, 115, (0, 0, 0)),
    'under-quarter': (40, -35, 115, (0, 0, -2)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-side', 'top']),
                ('views-whole-2', ['port-aft-quarter', 'starboard-side', 'front', 'under-quarter']),
                ('views-close', ['bow-detail', 'bridge-detail', 'bridge-front', 'dronebay-detail']),
                ('views-close-2', ['gantry-detail', 'turret-detail', 'engine-detail', 'stern-detail'])]
CAPTION = 'Shepherd (Morrow) surfacing, Ko Combine drone tender livery: graded Codex part skins + graded class swatches on every other base face.'
