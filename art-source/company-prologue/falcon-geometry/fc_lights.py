"""Running lights and the bridge glazing.

falcon_p.png has no lights layer, so the lamps are placed where a ship this
shape needs them and the sprite shows lit pixels: red on the port prong's
bow and outrigger, green on the starboard's, a white masthead on the bridge
dome's crown and a white beacon on the stern of the central body. The orange
arc on the dome's forward face in the sprite is the bridge glazing: a band
of panes on the dome's forward quarter (`Bridge glazing pane <k>`).

Lamps are small emissive studs named `Light <side> <where> <k>`; their
colours and blink live in records(), which the VFX profile reads.
"""
import math
from fc_kit import parent

LAMPS = [  # name, x, y, colour role
    ('Light port prong bow 1', 104.5, 32.5, 'running'),
    ('Light starboard prong bow 1', 104.5, -32.5, 'running'),
    ('Light port outrigger tip 1', -22.0, 80.5, 'running'),
    ('Light starboard outrigger tip 1', -22.0, -80.5, 'running'),
    ('Light centre dome masthead 1', 5.0, 0.0, 'masthead'),
    ('Light centre stern 1', -45.0, 0.0, 'beacon'),
]
RECORDS = []


def build(kit):
    RECORDS.clear()
    for name, x, y, role in LAMPS:
        hit = kit.surface(x, y, top=80)
        z = hit[0].z if hit else 0.0
        if 'outrigger tip' in name:
            z = 0.0
        kit.cylinder(name, (x, y, z+.25), .55, .6, kit.lamp, 'Z', 10, 0)
        RECORDS.append({'name': name, 'location': [x, y, round(z+.5, 2)], 'role': role,
                        'colour': 'red' if ' port ' in name and role == 'running' else
                                  'green' if 'starboard' in name and role == 'running' else 'white',
                        'blink': role == 'beacon'})
    # Bridge glazing: nine panes round the dome's forward quarter, set into its
    # lower face between Z 9 and 11.4 (the sprite's orange arc).
    for k in range(9):
        a = math.radians(-48+12*k)
        r = 12.9
        x, y = 4+r*math.cos(a), r*math.sin(a)
        ob = kit.box(f'Bridge glazing pane {k+1}', (x, y, 10.2), (.5, 2.2, 1.6), kit.glass, .05)
        ob.rotation_euler = (0, math.radians(-28), a)


def records():
    return list(RECORDS)
