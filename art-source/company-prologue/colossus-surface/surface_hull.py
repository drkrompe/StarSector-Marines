"""The Colossus's inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../colossus-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh for the
sequence. Livery: the vanilla Domain-era Colossus heavy freighter read off
colossus.png: dusty oxide-red hull plating on the hold and aft body, darker
maroon on the side cargo modules and drive pods, pale weathered-grey longerons,
bridge and bands, burnt-orange and faded-blue panel bands, gunmetal machinery,
near-black recesses. An old frontier freighter still in service: service wear,
dusty and hard-worked.

The cargo containers keep their own livery materials (seven colours, own
geometry for doors and lock bars); a class swatch would flatten them into one
paint.
"""
NAME = 'Colossus'
BLEND = 'build/geom/colossus-geometry.blend'   # rebuild.sh step 1: ../colossus-geometry/build_colossus.py -- --out <this>/build/geom
GROUPS = ['hold', 'modules', 'spine', 'bridge', 'pods']
MATERIAL_CLASS = {
    'Oxide red hull plating': 'red',
    'Drive pod worn aft plate': 'red_w',
    'Maroon module plating': 'maroon',
    'Pale weathered grey structure': 'pale',
    'Burnt orange panel band': 'orange',
    'Faded blue panel band': 'blue',
    'Gunmetal machinery': 'gunmetal',
    'Ceramic heat-shield tile': 'tile',
    'Recess and lattice': 'dark',
    'Seam groove shadow': 'dark',
    'Bridge seam groove shadow': 'dark',
}
# Kept on their own materials (no class, no swatch): the container liveries,
# the bridge glazing and dark port glass (a swatch is a painted plate; on the
# Shepherd's panes it read as steel), running lamps, the drive bell liners,
# nozzle lips, heat-shield bands and chambers, and the pods' fender rubber.
CLASS_ID = {'red': (255, 0, 0), 'red_w': (255, 128, 128), 'maroon': (128, 0, 0), 'pale': (255, 255, 0),
            'orange': (255, 128, 0), 'blue': (0, 128, 255), 'gunmetal': (0, 0, 255), 'tile': (0, 255, 0),
            'dark': (128, 0, 255), 'other': (255, 0, 255)}
CLASSES = ['red', 'red_w', 'maroon', 'pale', 'orange', 'blue', 'gunmetal', 'tile', 'dark']
# Swap check: the reds (oxide, worn, maroon) are one paint family (darker or
# lighter red is tone, reported); orange, blue and pale are their own; the
# steels, tiles and recesses are one neutral family.
FAMILY = {'red': 'red', 'red_w': 'red', 'maroon': 'red', 'pale': 'pale', 'orange': 'orange', 'blue': 'blue',
          'gunmetal': 'neutral', 'tile': 'neutral', 'dark': 'neutral'}
# Red, maroon and pale grey sit near the default grey background; a mid blue is
# far from them. The faded-blue bands are close to it but small, and their
# measurement is checked against the geometry's designed colour (derive_palette).
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 12.0                 # hull units one swatch image spans (6 m at 0.50 m/unit)
# Blue is painted on the sheet: swatches.py scales a chromatic class's chroma about
# neutral, so a blue regraded from a warm square stays warm (v1 tried blue from pale).
# Near-black and the tiles are neutral and regrade from gunmetal by a shift.
SWATCH_ORDER = ['red', 'maroon', 'pale', 'orange', 'blue', 'gunmetal']
SWATCH_DERIVED = [('red_w', 'red'), ('dark', 'gunmetal'), ('tile', 'gunmetal')]
MEASURED_CLASSES = ['red', 'maroon', 'pale', 'orange', 'blue', 'gunmetal', 'dark']
REFERENCE_CAMERA = 'three-quarter'

# Derived classes: CIELAB offset from the measured parent, from the geometry's
# own base colours (the pods' worn aft plate against the oxide red; the ceramic
# tiles against the recess black).
DERIVED = {'red_w': ('red', (+14.0, -6.0, -2.0)),
           'tile': ('dark', (+6.0, 0.0, 0.5))}


def derive_palette(classes):
    # The reference's measured spreads carry its facet shading and, on thin
    # parts, their neighbours at the registration edge: the means stand, the
    # spreads are owned (as on the Hermes, Hound (A), Shepherd and Onslaught).
    owned = {'red': [8.0, 1.5, 2.5], 'maroon': [7.0, 1.2, 2.0], 'pale': [8.0, 0.6, 1.2],
             'orange': [8.0, 2.0, 3.5], 'blue': [7.0, 1.0, 2.0], 'gunmetal': [9.0, 0.6, 1.4],
             'dark': [6.0, 0.5, 0.8]}
    out = {c: dict(classes[c], std=std, std_measured=classes[c]['std'],
                   note='spread owned: shading and edge contamination on the reference inflate it')
           for c, std in owned.items() if c in classes}
    # Pale's darkest tenth is near black (L p10 0.3): the longerons' bow end faces carry
    # the pale label but sit in the shadow between the module bow armour and the hold, and
    # render black on the reference. The median (66.8) is the paint; the mean is owned there.
    if 'pale' in out:
        m = out['pale']['mean']
        out['pale'] = dict(out['pale'], mean=[66.8, m[1], m[2]], mean_measured=m,
                           note='mean owned at the measured median L: shadowed longeron end faces drag the mean')
    for c, (parent, off) in DERIVED.items():
        m = out[parent]['mean']
        out[c] = {'derived': f'{parent} + {list(off)} (the geometry\'s designed tone offset)',
                  'mean': [m[0] + off[0], m[1] + off[1], m[2] + off[2]], 'std': list(out[parent]['std'])}
    return out


VARIATION_SPAN = {'red': (3.5, 1.2, 1.5), 'red_w': (3.5, 1.2, 1.5), 'maroon': (3.5, 1.0, 1.2),
                  'pale': (3.0, 0.5, 1.0), 'orange': (4.0, 1.5, 2.5), 'blue': (3.0, 0.8, 1.5),
                  'gunmetal': (3.0, 0.4, 0.8), 'tile': (2.0, 0.2, 0.3), 'dark': (2.0, 0.2, 0.3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'red': (1.0, 1.0, 1.0), 'red_w': (1.0, 1.0, 1.0), 'maroon': (1.0, 1.0, 1.0),
                 'pale': (1.0, 0.8, 1.0), 'orange': (1.0, 1.0, 1.0), 'blue': (1.0, 1.0, 1.0),
                 'gunmetal': (0.0, 0.35, 0.7), 'tile': (0.0, 0.2, 0.0), 'dark': (0.0, 0.0, 0.25)}
# Drive bell centres, hull units (geometry.json engines): three per pod.
ENGINES = [(-124.5, 64.0, 0.0), (-124.5, 56.0, 0.0), (-124.5, 49.0, 0.0),
           (-124.5, -61.0, 0.0), (-124.5, -54.0, 0.0), (-124.5, -46.0, 0.0)]
STERN_SOOT = (-108.0, -124.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull central cargo hold'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'starboard-quarter', 'port-side', 'top', 'front', 'stern',
                'starboard-side', 'port-aft-quarter', 'underside-quarter']
REVIEW_CLOSE = ['bow-detail', 'cargo-detail', 'bridge-detail', 'bridge-quarter', 'turret-detail',
                'pod-quarter', 'engine-detail', 'stern-detail', 'keel-docking']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-side': (180, 0, 240, (-17, 1.5, -10)),
    'port-aft-quarter': (-45, 28, 300, (-17, 1.5, -5)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-side', 'top']),
                ('views-whole-2', ['port-aft-quarter', 'starboard-side', 'front', 'underside-quarter']),
                ('views-close', ['bow-detail', 'cargo-detail', 'bridge-detail', 'bridge-quarter']),
                ('views-close-2', ['turret-detail', 'pod-quarter', 'engine-detail', 'keel-docking'])]
CAPTION = 'Colossus surfacing, Domain-era freighter livery: graded Codex part skins + graded class swatches on every other base face.'
