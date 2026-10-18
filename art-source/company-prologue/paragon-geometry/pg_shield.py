"""The Paragon's shield: the emitter anchor the VFX kit keys off, and the emitter array round the bore.

paragon.ship gives an OMNI shield (360 deg) of radius 270 about shieldCenter
(1, 0), and the ship system is Fortress Shield. That centre falls in the open
bore, so the model reads the bore lining as the emitter: a ring of glowing
emitter nodes stand on it, each with an `Shield emitter node <k>` empty facing
the bore's axis, and the `Shield emitter` empty sits at the shield centre on the
hull's mid-plane with the shield's numbers as custom properties. The nodes
behind the core's nose (where the core covers the lining) are left out.
"""
import math
import pg_kit as K
import pg_hull

SHIELD_CENTER = (1, 0)
SHIELD_RADIUS = 270
SHIELD_ARC = 360
NODE_R = 58.8
NODE_ANGLES = [15+30*k for k in range(12)]
RECORDS = {}


def build(kit, metres_per_unit):
    cx, cy = pg_hull.BORE_C
    em = kit.empty('Shield emitter', (*SHIELD_CENTER, 0.0), 0, 'SPHERE', 20)
    em['shield_type'] = 'OMNI'
    em['arc_deg'] = SHIELD_ARC
    em['radius_units'] = SHIELD_RADIUS
    em['radius_m'] = round(SHIELD_RADIUS*metres_per_unit, 1)
    em['ship_system'] = 'fortressshield'
    em['colour'] = K.GLOW_BLUE
    nodes = []
    top = 26.0  # lining top (pg_hull.lining)
    for k, a in enumerate(NODE_ANGLES, start=1):
        x = cx+NODE_R*math.cos(math.radians(a))
        y = cy+NODE_R*math.sin(math.radians(a))
        if x < -18 and abs(y) < 30:  # under the core's nose
            continue
        n = len(nodes)+1
        K.revolve(kit, f'Shield emitter node {n} base', [(0, -1), (2.2, -1), (2.2, .5), (1.8, 1.1), (0, 1.1)],
                  kit.steel, center=(x, y, top), segments=32)
        K.revolve(kit, f'Shield emitter node {n} lens', [(0, 0), (1.5, 0), (1.3, .9), (.7, 1.4), (0, 1.6)],
                  kit.glow, center=(x, y, top+1.0), segments=32)
        face = math.degrees(math.atan2(cy-y, cx-x))
        e = kit.empty(f'Shield emitter node {n}', (x, y, top+2.5), face, 'SINGLE_ARROW', 4)
        nodes.append({'name': e.name, 'location': [round(x, 2), round(y, 2), round(top+2.5, 2)],
                      'faces_deg': round(face, 1)})
    RECORDS.update({'anchor': em.name, 'center': [*SHIELD_CENTER, 0.0], 'type': 'OMNI', 'arc_deg': SHIELD_ARC,
                    'radius_units': SHIELD_RADIUS, 'radius_m': em['radius_m'], 'ship_system': 'fortressshield',
                    'source': 'paragon.ship shieldCenter/shieldRadius, ship_data.csv shield type OMNI arc 360',
                    'emitter_nodes': nodes})
    return em
