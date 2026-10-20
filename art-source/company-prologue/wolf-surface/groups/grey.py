"""Grey: the machinery structure (head, bow fork, outriggers, missile pods,
spines and engine block).

Three sheets. grey-tops: the head with both outriggers from above, the round 6
prow (deck, jaws and gun hood), the engine block with the aft spine, the port
missile pod. grey-faces: the engine block's port flank, the port outrigger's
outboard wall, the engine block's stern face round the drives, the fore spine.
grey-bridge (round 7): the bridge roof, the bridge and gun-hood faces from ahead,
the prow's port flank and jaw, the prow's stem and jaw faces from ahead.
"""
from parts_common import BOW, PORT, STARBOARD, UP_Z, DOWN_Z, DOWN_X, STERN

PARTS = {
    'head-top': dict(members=[('Hull head', (38.0, 4.0, 4.4)), ('Hull port outrigger', (36.0, 13.0, 3.3)),
                              ('Hull starboard outrigger', (36.0, -13.0, 3.3))],
                     dir=UP_Z, up=DOWN_X, cone=.5, what='head and both outriggers from above'),
    # The round 6 prow from above (round 7 re-texture): the prow deck, both jaws and the
    # gun hood, the trough between them background.
    'fork-top': dict(members=[('Hull bow fork base', (49.0, 4.6, 2.4)), ('Hull bow port jaw', (48.0, 8.0, 3.6)),
                              ('Hull bow starboard jaw', (48.0, -8.0, 3.6)), ('Hull bow gun hood', (42.0, 0.0, 6.0))],
                     dir=UP_Z, up=DOWN_X, cone=.5, what='the prow deck, its two jaws and the gun hood from above'),
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

# Round 7: the bridge and the prow's faces.
PARTS.update({
    'bridge-top': dict(members=[('Hull bridge', (33.0, 0.0, 10.2))], dir=UP_Z, up=DOWN_X, cone=.5,
                       what='the bridge roof from above'),
    'bridge-face': dict(members=[('Hull bridge', (40.6, 0.0, 8.0)), ('Hull bow gun hood', (45.6, 0.0, 3.6))],
                        dir=BOW, up=UP_Z, cone=.4, what="the bridge's raked face and the gun hood's face from ahead"),
    'prow-side-port': dict(members=[('Hull bow port jaw', (49.0, 10.4, -2.0)), ('Hull bow fork base', (46.0, 9.9, -4.0))],
                           dir=PORT, up=UP_Z, cone=.6, what="the prow's port flank and port jaw"),
    'prow-front': dict(members=[('Hull bow fork base', (53.0, 4.6, -5.0)), ('Hull bow port jaw', (53.5, 6.0, 0.0)),
                                ('Hull bow starboard jaw', (53.5, -6.0, 0.0))],
                       dir=BOW, up=UP_Z, cone=.5, what="the prow's stem and the jaws' faces from ahead"),
    'prow-side-stbd': dict(members=[('Hull bow starboard jaw', (49.0, -10.4, -2.0)),
                                    ('Hull bow fork base', (46.0, -9.9, -4.0))], dir=STARBOARD, up=UP_Z, cone=.6,
                           skin_from='prow-side-port', mirror_u=True, what="the prow's starboard flank (mirror)"),
})

SHEETS = {
    'grey-tops': ['head-top', 'fork-top', 'engine-top', 'pod-top-port'],
    'grey-bridge': ['bridge-top', 'bridge-face', 'prow-side-port', 'prow-front'],
    'grey-faces': ['engine-side-port', 'outrigger-side-port', 'engine-stern', 'spine-fore'],
}

# Round 8 (the accepted underside): the keel with its two keel shoes from below,
# the head's underside with the prow's, the head's port flank, and the port
# missile pod's underside; starboard flank and pod mirror.
PARTS.update({
    'keel-under': dict(members=[('Hull ventral keel', (5.0, 0.0, -18.0)), ('Hull keel shoe fore', (12.0, 0.0, -19.4)),
                                ('Hull keel shoe aft', (-10.0, 0.0, -19.4))], dir=DOWN_Z, up=DOWN_X, cone=.6,
                       what='the ventral keel and its two keel shoes from below'),
    'head-under': dict(members=[('Hull head', (37.0, 0.0, -12.5)), ('Hull bow fork base', (47.0, 0.0, -9.0))],
                       dir=DOWN_Z, up=DOWN_X, cone=.6, what="the head's and the prow's underside"),
    'head-side-port': dict(members=[('Hull head', (37.0, 14.0, -3.0))], dir=PORT, up=UP_Z, cone=.6,
                           what="the head's port flank"),
    'pod-under-port': dict(members=[('Pod port missile body', (2.0, 18.0, -6.5))], dir=DOWN_Z, up=DOWN_X, cone=.5,
                           what='port missile pod from below'),
    'head-side-stbd': dict(members=[('Hull head', (37.0, -14.0, -3.0))], dir=STARBOARD, up=UP_Z, cone=.6,
                           skin_from='head-side-port', mirror_u=True, what="the head's starboard flank (mirror)"),
    'pod-under-stbd': dict(members=[('Pod starboard missile body', (2.0, -18.0, -6.5))], dir=DOWN_Z, up=DOWN_X, cone=.5,
                           skin_from='pod-under-port', mirror_u=True, what='starboard missile pod from below (mirror)'),
})
SHEETS['grey-belly'] = ['keel-under', 'head-under', 'head-side-port', 'pod-under-port']
