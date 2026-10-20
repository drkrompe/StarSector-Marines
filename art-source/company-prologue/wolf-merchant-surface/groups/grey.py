"""Grey: the machinery structure (head, bow fork, outriggers, missile pods,
spines and engine block).

Two sheets. grey-tops: the head with both outriggers from above, the bow
fork's base and outer tines, the engine block with the aft spine, the port
missile pod. grey-faces: the engine block's port flank, the port outrigger's
outboard wall, the engine block's stern face round the drives, the fore spine.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_X, STERN

PARTS = {
    'head-top': dict(members=[('Hull head', (38.0, 4.0, 4.4)), ('Hull port outrigger', (36.0, 13.0, 3.3)),
                              ('Hull starboard outrigger', (36.0, -13.0, 3.3))],
                     dir=UP_Z, up=DOWN_X, cone=.5, what='head and both outriggers from above'),
    # Round 6 reshaped the prow (deeper, broader, jaws and a gun hood over it): this tile was
    # generated for the round 5 wedge, so it frames its guide but is not applied; the prow,
    # jaws and hood take the graded grey swatch until the sheet is regenerated.
    'fork-top': dict(members=[('Hull bow fork base', (46.0, 6.0, 0.5))],
                     dir=UP_Z, up=DOWN_X, cone=.5, what='bow fork base and outer tines from above',
                     apply_skip=('Hull bow fork base',)),
    'engine-top': dict(members=[('Hull engine block', (-23.0, 0.0, 6.4)), ('Hull dorsal spine aft', (-15.0, 0.0, 5.3))],
                       dir=UP_Z, up=DOWN_X, cone=.5, what='engine block deck and the aft spine'),
    'pod-top-port': dict(members=[('Pod port missile body', (2.0, 18.0, 1.5))], dir=UP_Z, up=DOWN_X, cone=.5,
                         what='port missile pod from above'),
    'engine-side-port': dict(members=[('Hull engine block', (-23.0, 13.0, -2.0))], dir=PORT, up=UP_Z, cone=.6,
                             what='engine block port flank'),
    'outrigger-side-port': dict(members=[('Hull port outrigger', (36.0, 16.2, 0.0))], dir=PORT, up=UP_Z, cone=.6,
                                what='port outrigger outboard wall'),
    'engine-stern': dict(members=[('Hull engine block', (-27.0, 3.0, 2.0))], dir=STERN, up=UP_Z, cone=.6,
                         what='engine block stern face round the drives'),
    'spine-fore': dict(members=[('Hull dorsal spine fore', (24.0, 0.0, 6.0))], dir=UP_Z, up=DOWN_X, cone=.5,
                       what='the neck spine from above'),
    'pod-top-stbd': dict(members=[('Pod starboard missile body', (2.0, -18.0, 1.5))], dir=UP_Z, up=DOWN_X, cone=.5,
                         skin_from='pod-top-port', mirror_u=True, what='starboard missile pod (mirror)'),
    'engine-side-stbd': dict(members=[('Hull engine block', (-23.0, -13.0, -2.0))], dir=STARBOARD, up=UP_Z, cone=.6,
                             skin_from='engine-side-port', mirror_u=True, what='engine block starboard flank (mirror)'),
    'outrigger-side-stbd': dict(members=[('Hull starboard outrigger', (36.0, -16.2, 0.0))], dir=STARBOARD, up=UP_Z,
                                cone=.6, skin_from='outrigger-side-port', mirror_u=True,
                                what='starboard outrigger outboard wall (mirror)'),
}

SHEETS = {
    'grey-tops': ['head-top', 'fork-top', 'engine-top', 'pod-top-port'],
    'grey-faces': ['engine-side-port', 'outrigger-side-port', 'engine-stern', 'spine-fore'],
}
