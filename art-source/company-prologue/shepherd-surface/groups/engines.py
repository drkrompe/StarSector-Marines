"""Engines: the two burnt orange drive pod casings and the gunmetal machinery block.

`Engine 1 drive pod casing` is port (Y 12..28), `Engine 2` starboard; their
saddle bands, seams and yokes are separate parts on swatch. `Hull aft
machinery block` is the gunmetal block between them. One sheet: port pod top,
port pod outboard flank, machinery block top, machinery block stern face; the
starboard pod reuses the port tiles mirrored.
"""
from parts_common import PORT, STARBOARD, UP_Z, STERN, DOWN_X

POD1, POD2, MACH = 'Engine 1 drive pod casing', 'Engine 2 drive pod casing', 'Hull aft machinery block'

PARTS = {
    'pod-top-port': dict(members=[(POD1, (-27.4, 20.0, 8.7)), (POD1, (-19.2, 20.0, 8.7))], dir=UP_Z, up=DOWN_X, cone=.55,
                         what='port drive pod casing top'),
    'pod-top-stbd': dict(members=[(POD2, (-27.4, -19.0, 8.7)), (POD2, (-19.2, -19.0, 8.7))], dir=UP_Z, up=DOWN_X, cone=.55,
                         skin_from='pod-top-port', mirror_u=True, what='starboard drive pod casing top (mirror)'),
    'pod-side-port': dict(members=[(POD1, (-27.4, 27.7, -1.0)), (POD1, (-19.2, 27.7, -1.0))], dir=PORT, up=UP_Z, cone=.55,
                          what='port drive pod casing outboard flank'),
    'pod-side-stbd': dict(members=[(POD2, (-27.4, -26.7, -1.0)), (POD2, (-19.2, -26.7, -1.0))], dir=STARBOARD, up=UP_Z, cone=.55,
                          skin_from='pod-side-port', mirror_u=True, what='starboard pod outboard flank (mirror)'),
    'mach-top': dict(members=[(MACH, (-22.0, 0.0, 6.0))], dir=UP_Z, up=DOWN_X, cone=.6,
                     what='aft machinery block top around the machinery well'),
    'mach-stern': dict(members=[(MACH, (-31.6, 0.0, -4.0))], dir=STERN, up=UP_Z, cone=.6,
                       what='aft machinery block stern face'),
}

SHEETS = {
    'engines': ['pod-top-port', 'pod-side-port', 'mach-top', 'mach-stern'],
}
