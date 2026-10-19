"""The Paragon's inputs to the shared surfacing harness (../second-wind-surface/hull.py).

Every harness script runs with `--hull ../paragon-surface` (after `--` for
Blender) or SURF_HULL set to this folder; see README.md and rebuild.sh. The
Paragon is the harness's first OVERLAY hull: its armour is the owner-approved
procedural hex-ablative ceramic (paragon-geometry/pg_finish.py), continuous
across every face and scaled per part, and it stays. The class swatches (and
any part skins) only modulate each base material's colour; the hex plates and
their relief are never repainted (owner, 2026-10-19). Livery: Tri-Tachyon, read
off paragon.png: pale blue-white ceramic over blue-grey skin, a white core,
slate bands, emitter steel, near-black machinery recesses. A corporate flagship
kept immaculate: light service wear.
"""
NAME = 'Paragon'
BLEND = 'build/geom/paragon-geometry.blend'   # ../paragon-geometry/build_paragon.py, imported, never edited
GROUPS = []                                    # round 1: class swatches over the hex finish; no part tiles yet
MATERIAL_CLASS = {
    'Tri-Tachyon blue-grey skin': 'skin',
    'Tri-Tachyon pale skin': 'pale',
    'Tri-Tachyon white core': 'white',
    'Tri-Tachyon slate band': 'slate',
    'Tri-Tachyon hatch ceramic': 'hatch',
    'Tri-Tachyon substrate': 'substrate',
    'Emitter metal': 'steel',
    'Technical lining': 'dark',
    'Bore plating mid grey': 'bore',
    'Bore plating light grey': 'bore_light',
}
# Kept on their own materials (no class, no swatch): every glow, lamp and the bridge
# glazing, and the guns, which wear the turret kit's tinted copies of these materials
# ('... tint ...', 'Turret ...').
CLASS_ID = {'skin': (255, 0, 0), 'pale': (255, 128, 0), 'white': (255, 255, 0), 'slate': (0, 255, 0),
            'hatch': (0, 128, 255), 'substrate': (0, 255, 255), 'steel': (0, 0, 255), 'dark': (128, 0, 255),
            'bore': (255, 0, 128), 'bore_light': (128, 255, 0), 'other': (255, 0, 255)}
CLASSES = ['skin', 'pale', 'white', 'slate', 'hatch', 'substrate', 'steel', 'dark', 'bore', 'bore_light']
FAMILY = {'skin': 'blue', 'pale': 'pale', 'white': 'pale', 'slate': 'neutral', 'hatch': 'pale',
          'substrate': 'neutral', 'steel': 'neutral', 'dark': 'neutral', 'bore': 'neutral', 'bore_light': 'neutral'}
BACKGROUND = (70, 110, 170)
SWATCH_UNITS = 12.0                  # hull units one swatch image spans (10.4 m at 0.87 m/unit)
SWATCH_ORDER = ['skin', 'pale', 'white', 'slate', 'steel', 'dark']
SWATCH_DERIVED = [('hatch', 'pale'), ('substrate', 'slate'), ('bore', 'steel'), ('bore_light', 'pale')]
MEASURED_CLASSES = ['skin', 'pale', 'white', 'slate', 'steel', 'dark', 'bore', 'bore_light']
REFERENCE_CAMERA = 'three-quarter'
SHEARED_FACES = True

# Overlay (the harness's opt-in, hull.OVERLAY): how strongly each class's graded
# texture modulates the base finish. The hex ceramic carries the plate pattern;
# the swatch adds panel-to-panel tone, grime in seams and heat staining.
OVERLAY = {'skin': .6, 'pale': .6, 'white': .55, 'slate': .5, 'hatch': .5, 'substrate': .5,
           'steel': .55, 'dark': .4, 'bore': .55, 'bore_light': .55}


def derive_palette(classes):
    # The reference's measured spreads carry facet shading and the hex seams; under
    # overlay the palette only normalises each texture, so the means stand and the
    # spreads are owned.
    owned = {'skin': [7.0, 0.6, 1.4], 'pale': [6.0, 0.5, 1.2], 'white': [5.0, 0.4, 0.8], 'slate': [6.0, 0.5, 1.0],
             'steel': [8.0, 0.5, 1.0], 'dark': [5.0, 0.4, 0.8]}
    out = {c: dict(classes[c], std=std, std_measured=classes[c]['std'],
                   note='spread owned: shading and hex seams on the reference inflate it')
           for c, std in owned.items() if c in classes}
    # Hatches and the service-field substrate are not seen from the reference camera:
    # owned derivations, a hatch a shade under the pale ceramic, the substrate the slate.
    def shifted(src, dl, note):
        m = list(classes[src]['mean'])
        m[0] += dl
        return {'derived': note, 'mean': m, 'std': list(out[src]['std'])}
    out['hatch'] = shifted('pale', -4.0, 'owned: hatch ceramic, 4 L under the pale ceramic')
    out['substrate'] = shifted('slate', 2.0, 'owned: service-field substrate, the slate band lifted 2 L')
    return out


VARIATION_SPAN = {'skin': (2.5, 0.3, 0.8), 'pale': (2.5, 0.3, 0.6), 'white': (2.0, 0.2, 0.4),
                  'slate': (2.0, 0.2, 0.5), 'hatch': (2.0, 0.2, 0.4), 'substrate': (2.0, 0.2, 0.4),
                  'steel': (2.5, 0.3, 0.6), 'dark': (1.5, 0.2, 0.3), 'bore': (2.5, 0.3, 0.6),
                  'bore_light': (2.5, 0.3, 0.6)}
# wear.py: (paint, fade, metal-reveal) per class. Ceramic does not chip to primer:
# its 'paint' response is low (glaze scuffs to a paler body), fade light.
WEAR_RESPONSE = {'skin': (.35, .6, .3), 'pale': (.35, .5, .25), 'white': (.3, .4, .2), 'slate': (.3, .5, .4),
                 'hatch': (.35, .5, .25), 'substrate': (0.0, .3, .5), 'steel': (0.0, .35, .7),
                 'dark': (0.0, 0.0, .25), 'bore': (0.0, .35, .6), 'bore_light': (0.0, .35, .6)}
# The ten drive mouths (geometry.json engines: mouth_x, slot y, axis z).
ENGINES = [(-177.5, 140, -2.0), (-185.5, 105, -2.0), (-187.5, 73, -2.0), (-187.5, 64, -2.0), (-185.5, 57, -2.0),
           (-185.5, -56, -2.0), (-187.5, -64, -2.0), (-187.5, -72, -2.0), (-185.5, -104, -2.0), (-177.5, -139, -2.0)]
STERN_SOOT = (-160.0, -185.0)
HULL_OBJECT = 'Hull belt'

REVIEW_WHOLE = ['three-quarter', 'starboard-side', 'top', 'front', 'stern', 'belly', 'port-aft-quarter', 'under-quarter']
REVIEW_CLOSE = ['bow-detail', 'bore-detail', 'core-detail', 'turret-detail', 'engine-detail', 'hangar-detail',
                'stern-detail', 'hp-close']
REVIEW_EXTRA = {
    'port-aft-quarter': (-45, 28, 460, (0, 0, 0)),
    'under-quarter': (40, -35, 460, (0, 0, -10)),
}
REVIEW_PAGES = [('views-whole', ['three-quarter', 'starboard-side', 'top', 'port-aft-quarter']),
                ('views-whole-2', ['front', 'stern', 'belly', 'under-quarter']),
                ('views-close', ['bow-detail', 'bore-detail', 'core-detail', 'turret-detail']),
                ('views-close-2', ['engine-detail', 'hangar-detail', 'stern-detail', 'hp-close'])]
CAPTION = 'Paragon surfacing round 1 (overlay): graded Codex class swatches modulating the procedural hex-ablative ceramic, light service wear.'
