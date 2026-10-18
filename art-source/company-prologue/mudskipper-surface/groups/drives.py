"""Aft drive block: the two engine pods and the main drive casing.

Sources (one sheet): the port pod's outer flank, the port pod's top, the port
pod's aft face, and the main drive casing's top with its burnished barrel
bands. The starboard pod mirrors every port pod part; the main casing's port
and starboard flanks reuse its top (the casing is a faceted barrel with the
same cover frames and bands on all three faces).

Left to swatches on purpose: bells, throats, mouth rims, can bands, nozzle
housings, gimbal ring/waist/actuators, feed trunks, coolant lines, valve
blocks and pump housings, the pods' dark inboard cores (recess) and the main
hull's stern face, which the pods' cores and the main casing hide.
Engine names are identifiers and are never renamed.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_X, STERN


def _mirror(src):
    return dict(skin_from=src, mirror_u=True)


PARTS = {
    'pod-outer-port': dict(members=[('Engine 2 port pod drive casing', (-21, 28.5, 5)),
                                    ('Engine 2 pod thrust collar', (-27.5, 28.7, 0)),
                                    ('Engine 2 pod thrust housing', (-32, 28.5, -5)),
                                    ('Engine 2 pod flank panel', (-18.2, 28.8, 0.3)),
                                    ('Engine 2 pod flank panel.001', (-22.6, 28.8, 0.3)),
                                    ('Engine 2 pod flank panel frame', (-16.5, 28.7, 0.3)),
                                    ('Engine 2 pod flank panel frame.001', (-20.9, 28.7, 0.3))],
                           dir=PORT, up=UP_Z, cone=.6,
                           what='port engine pod outer flank: steel drive casing with two flush panels, tan thrust collar, steel thrust housing'),
    'pod-top-port': dict(members=[('Engine 2 port pod drive casing', (-23, 23, 8.5)),
                                  ('Engine 2 pod thrust collar', (-27.5, 20, 8.2)),
                                  ('Engine 2 pod machinery deck', (-21, 16, 8.8)),
                                  ('Engine 2 pod deck strake', (-21, 20.2, 8.8)),
                                  ('Engine 2 pod access hatch', (-18.8, 23, 8.8)),
                                  ('Engine 2 pod access hatch frame', (-20.4, 23, 8.7)),
                                  ('Engine 2 radiator bay floor', (-22, 26.5, 7.6))],
                         dir=UP_Z, up=DOWN_X, cone=.6,
                         what='port engine pod top: casing top, machinery deck, strake, tan access hatch, tan collar top'),
    'pod-aft-port': dict(members=[('Engine 2 pod thrust housing', (-35, 15, -4)),
                                  ('Engine 2 pod thrust collar', (-29, 20, 6))],
                         dir=STERN, up=UP_Z, cone=.6,
                         what='port engine pod aft face: steel thrust housing below, tan thrust collar above'),
    'drive-casing-top': dict(members=[('Engine 1 main drive casing', (-23, 0, 7.7)),
                                      ('Engine 1 barrel band 1', (-19.5, 0, 8.3)),
                                      ('Engine 1 barrel band 2', (-24.5, 0, 8.3)),
                                      ('Engine 1 barrel band 3', (-29.5, 0, 8.3)),
                                      ('Engine 1 thrust frame flange', (-16.3, 0, 8.3)),
                                      ('Engine 1 casing cover frame', (-22, 0, 7.9)),
                                      ('Engine 1 casing cover frame.003', (-27, 0, 7.9))],
                             dir=UP_Z, up=DOWN_X, cone=.6,
                             what='main drive casing top: faceted steel barrel with three pinkish burnished bands, flange and two dark cover frames'),
    # free reuses
    'pod-outer-stbd': dict(members=[('Engine 3 starboard pod drive casing', (-21, -28.5, 5)),
                                    ('Engine 3 pod thrust collar', (-27.5, -28.7, 0)),
                                    ('Engine 3 pod thrust housing', (-32, -28.5, -5)),
                                    ('Engine 3 pod flank panel', (-18.2, -28.8, 0.3)),
                                    ('Engine 3 pod flank panel.001', (-22.6, -28.8, 0.3)),
                                    ('Engine 3 pod flank panel frame', (-16.5, -28.7, 0.3)),
                                    ('Engine 3 pod flank panel frame.001', (-20.9, -28.7, 0.3))],
                           dir=STARBOARD, up=UP_Z, cone=.6, **_mirror('pod-outer-port'),
                           what='starboard pod outer flank (mirror of pod-outer-port)'),
    'pod-top-stbd': dict(members=[('Engine 3 starboard pod drive casing', (-23, -23, 8.5)),
                                  ('Engine 3 pod thrust collar', (-27.5, -20, 8.2)),
                                  ('Engine 3 pod machinery deck', (-21, -16, 8.8)),
                                  ('Engine 3 pod deck strake', (-21, -20.2, 8.8)),
                                  ('Engine 3 pod access hatch', (-18.8, -23, 8.8)),
                                  ('Engine 3 pod access hatch frame', (-20.4, -23, 8.7)),
                                  ('Engine 3 radiator bay floor', (-22, -26.5, 7.6))],
                         dir=UP_Z, up=DOWN_X, cone=.6, **_mirror('pod-top-port'),
                         what='starboard pod top (mirror of pod-top-port)'),
    'pod-aft-stbd': dict(members=[('Engine 3 pod thrust housing', (-35, -15, -4)),
                                  ('Engine 3 pod thrust collar', (-29, -20, 6))],
                         dir=STERN, up=UP_Z, cone=.6, **_mirror('pod-aft-port'),
                         what='starboard pod aft face (mirror of pod-aft-port)'),
    'drive-casing-port': dict(members=[('Engine 1 main drive casing', (-23, 7.7, 0)),
                                       ('Engine 1 barrel band 1', (-19.5, 8.3, 0)),
                                       ('Engine 1 barrel band 2', (-24.5, 8.3, 0)),
                                       ('Engine 1 barrel band 3', (-29.5, 8.3, 0)),
                                       ('Engine 1 thrust frame flange', (-16.3, 8.3, 0)),
                                       ('Engine 1 casing cover frame.001', (-22, 7.9, 0)),
                                       ('Engine 1 casing cover frame.004', (-27, 7.9, 0))],
                              dir=PORT, up=DOWN_X, cone=.6, skin_from='drive-casing-top',
                              what='main drive casing port flank (reuses drive-casing-top)'),
    'drive-casing-stbd': dict(members=[('Engine 1 main drive casing', (-23, -7.7, 0)),
                                       ('Engine 1 barrel band 1', (-19.5, -8.3, 0)),
                                       ('Engine 1 barrel band 2', (-24.5, -8.3, 0)),
                                       ('Engine 1 barrel band 3', (-29.5, -8.3, 0)),
                                       ('Engine 1 thrust frame flange', (-16.3, -8.3, 0)),
                                       ('Engine 1 casing cover frame.002', (-22, -7.9, 0)),
                                       ('Engine 1 casing cover frame.005', (-27, -7.9, 0))],
                              dir=STARBOARD, up=DOWN_X, cone=.6, skin_from='drive-casing-top',
                              what='main drive casing starboard flank (reuses drive-casing-top: a rotation of the barrel, not a reflection)'),
}

SHEETS = {
    'drives-a': ['pod-outer-port', 'pod-top-port', 'pod-aft-port', 'drive-casing-top'],
}
