"""Open port boat bay: its interior back wall, floor, the cream frame round the
opening with its gunmetal buttresses, and the davit head above it.

The starboard bay sits behind its sealed door (starboard group) and is never
seen, so its interior is left to swatches, as are the port bay ceiling (only
ever seen edge-on through the opening) and the frame's inner returns.
"""
from parts_common import PORT, UP_Z

PARTS = {
    'bay-back-wall-port': dict(members=[('Port bay interior', (14, 15.6, -0.5))], dir=PORT, up=UP_Z, cone=.6, ray=8,
                               what='port boat bay back wall, seen straight through the opening'),
    'bay-frame-port': dict(members=[('Bay coaming', (14, 24.5, -5.5)), ('Bay coaming.001', (14, 24.5, 4.6)),
                                    ('Bay jamb', (2, 24.5, -0.5)), ('Bay jamb.001', (26, 24.5, -0.5)),
                                    ('Bay structural buttress.002', (1, 24.8, 0)),
                                    ('Bay structural buttress.003', (27, 24.8, 0))],
                           dir=PORT, up=UP_Z, cone=.6,
                           what='cream coaming and jambs round the port bay opening, with the gunmetal buttresses either side'),
    'bay-floor-port': dict(members=[('Bay level floor', (14, 20, -4.8))], dir=UP_Z, up=PORT, cone=.6, ray=4,
                           what='port bay deck plate, top face'),
    'bay-davit-head-port': dict(members=[('Bay davit head', (17, 29.2, 9.3))], dir=PORT, up=UP_Z, cone=.6,
                                what='davit head over the port bay, port face'),
}

SHEETS = {
    'bay-port': ['bay-back-wall-port', 'bay-frame-port', 'bay-floor-port', 'bay-davit-head-port'],
}
