"""The Phaeton tanker's inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../phaeton-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh for the
sequence. Livery: an independent merchant tanker in the Asharu convoy, its red
read off tanker_medium.png (reference/sprite-livery.png) and its structure tied
to the convoy's independent escorts (lasher-geometry ls_kit.LIVERIES
'independent', ../hound-surface-independent): deep oxide-red tank hull and
gallery, pale warm-grey cargo domes, slate-blue frames, sponsons, drums and
pipework, buff-cream bridge cab, guard arcs and fittings, gunmetal machinery
strakes and drive block, near-black recesses. Clean state, service wear.
"""
NAME = 'Phaeton'
BLEND = 'build/geom/phaeton-geometry.blend'   # rebuild.sh step 1: ../phaeton-geometry/build_phaeton.py -- --out <this>/build/geom
GROUPS = ['hull', 'tanks', 'machinery', 'drive', 'bridge']
MATERIAL_CLASS = {
    'Hull red plating': 'red',
    'Tank dome red': 'dome',
    'Frame light grey': 'slate',
    'Bridge cab tan': 'cream',
    'Chalked cream fittings': 'cream',
    'Hull pale access plate': 'cream',
    'Gunmetal machinery': 'gunmetal',
    'Recess and machinery': 'dark',
}
# Kept on their own materials (no class, no swatch): the glazing and gallery
# windows, lamps, the drive housing glow, bell liners and heat-blued nozzles,
# hose rubber, chrome rods and the crane hook's hazard yellow.
CLASS_ID = {'red': (255, 0, 0), 'dome': (255, 255, 0), 'slate': (0, 255, 0), 'cream': (255, 128, 0),
            'gunmetal': (0, 0, 255), 'dark': (128, 0, 255), 'glass': (0, 255, 255), 'other': (255, 0, 255)}
CLASSES = ['red', 'dome', 'slate', 'cream', 'gunmetal', 'dark', 'glass']
# Swap check: red, the pale domes, slate blue and cream are each their own
# family (a dome painted red is a swap); gunmetal, recess and glass are neutral.
FAMILY = {'red': 'red', 'dome': 'pale', 'slate': 'blue', 'cream': 'cream',
          'gunmetal': 'neutral', 'dark': 'neutral', 'glass': 'neutral'}
# A mid blue background would sit near the slate-blue structure: a mid green
# is far from every class.
BACKGROUND = (80, 160, 90)
SWATCH_UNITS = 16.0                 # hull units one swatch image spans (5.3 m at 0.33 m/unit)
SWATCH_ORDER = ['red', 'dome', 'slate', 'cream', 'gunmetal', 'dark']
SWATCH_DERIVED = [('glass', 'dark')]
MEASURED_CLASSES = ['red', 'dome', 'slate', 'cream', 'gunmetal', 'dark']
REFERENCE_CAMERA = 'three-quarter'


def derive_palette(classes):
    # The reference's measured spreads carry its facet shading and, on thin
    # parts (pipes, rails, frames), their neighbours at the registration edge:
    # the means stand, the spreads are owned (as on the Shepherd and Hermes).
    owned = {'red': [8.0, 2.0, 2.5], 'dome': [8.0, .6, 1.2], 'slate': [8.0, .8, 1.5],
             'cream': [7.0, .6, 1.5], 'gunmetal': [10.0, .6, 1.6], 'dark': [6.5, .5, .8]}
    out = {c: dict(classes[c], std=std, std_measured=classes[c]['std'],
                   note='spread owned: shading and edge contamination on the reference inflate it')
           for c, std in owned.items()}
    # Two owned means on top:
    # - the recess class is a few thin slots on the reference and measured L 46,
    #   the slate pipework round them: owned near-black;
    # - the slate structure measured nearly grey (b -3.5, the drums and reactor
    #   dome came back gunmetal); owned toward the convoy escorts' slate accent
    #   (../hound-surface-independent), so the convoy reads as one fleet.
    out['dark'] = dict(out['dark'], mean=[16.0, 0.0, .5], mean_measured=classes['dark']['mean'],
                       note='mean owned: thin recesses measure their neighbours')
    s = classes['slate']
    out['slate'] = dict(out['slate'], mean=[s['mean'][0], -1.5, -8.0], mean_measured=s['mean'],
                        note='chroma owned toward the independent escorts\' slate accent')
    return out | {'glass': {'derived': 'owned: tinted bridge glazing, dark with a faint warm cast',
                            'mean': [20.0, .5, 2.0], 'std': [5.0, .8, 1.2]}}


VARIATION_SPAN = {'red': (3.0, .8, 1.0), 'dome': (2.5, .4, .8), 'slate': (3.0, .6, 1.0),
                  'cream': (3.0, .5, 1.0), 'gunmetal': (3.0, .4, .8), 'dark': (2.0, .2, .3),
                  'glass': (1.5, .2, .3)}
# wear.py: (paint, fade, metal-reveal) per class
WEAR_RESPONSE = {'red': (1.0, 1.0, 1.0), 'dome': (1.0, .8, 1.0), 'slate': (1.0, 1.0, 1.0),
                 'cream': (1.0, 1.0, 1.0), 'gunmetal': (0.0, .35, .7), 'dark': (0.0, 0.0, .25),
                 'glass': (0.0, 0.0, 0.0)}
ENGINES = [(-93.0, 36.0, -1.0), (-93.0, 24.0, -1.0), (-93.0, 9.0, -1.0),
           (-93.0, -9.0, -1.0), (-93.0, -24.0, -1.0), (-93.0, -36.0, -1.0)]   # ph_engines.ENGINES bell centres
STERN_SOOT = (-60.0, -85.0)   # hull X where stern soot starts and where it is full
HULL_OBJECT = 'Hull main body'

# Review: the build's own cameras plus the sides it does not have.
REVIEW_WHOLE = ['three-quarter', 'starboard-quarter', 'port-side', 'top', 'front', 'stern',
                'starboard-side', 'port-aft-quarter', 'under-quarter']
REVIEW_CLOSE = ['bow-detail', 'bridge-detail', 'crane-detail', 'tanks-detail',
                'turret-detail', 'engine-detail', 'stern-detail', 'underside']
# (azimuth from +Y toward +X deg, elevation deg, ortho scale, target)
REVIEW_EXTRA = {
    'starboard-side': (180, 0, 250, (22, 0, 0)),
    'port-aft-quarter': (-45, 28, 265, (22, 0, 0)),
    'under-quarter': (40, -35, 265, (22, 0, -4)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-quarter', 'port-side', 'top']),
                ('views-whole-2', ['port-aft-quarter', 'starboard-side', 'front', 'under-quarter']),
                ('views-close', ['bow-detail', 'bridge-detail', 'crane-detail', 'tanks-detail']),
                ('views-close-2', ['turret-detail', 'engine-detail', 'stern-detail', 'underside'])]
CAPTION = 'Phaeton surfacing, independent merchant tanker livery: graded Codex part skins + graded class swatches on every other base face.'
