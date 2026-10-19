"""Phaeton drives: the stern drive block with its six LOW_TECH bells, the two
reaction-mass drums on its corners, and the two drive housings either side
of the machinery spine with their guard arcs.

Engine slots from phaeton.ship, in file order (index i = 1..6), all angle
180 at X -93: Y +36 (w12), +24, +9, -9, -24 (w16), -36 (w12). Read off
tanker_medium.png:
- the drive block is the wide grey stern X -91..-62, Y -42..42, the six
  bells on its aft face;
- the drums at its corners (X -90..-66, Y 32..55 each side) are X-axis
  cylinders with a red band round them at X -78: reaction-mass drums;
- the dark rings at X -62..-38, Y 15..40 each side, with a pink glow at
  their centre, are vertical drive housings (fusion plant) whose crown
  port glows; the tan arcs outboard of them (Y 40..58) are guard frames,
  and the flare launchers (WS 005 / WS 006) sit at their outer tips.
Names follow the Second Wind: 'Engine <i> aft-facing bell', 'Engine <i>
mouth lip', 'Engine <i> dark throat', 'Engine <i> exhaust anchor' (an empty
on the mouth whose +X is the exhaust direction).
"""
import math
import bpy
from ph_kit import prism, section, pod, revolve_x, octagon, loft_x

ENGINES = [(-93, 36, 12, 180), (-93, 24, 16, 180), (-93, 9, 16, 180), (-93, -9, 16, 180),
           (-93, -24, 16, 180), (-93, -36, 12, 180)]
ENGINE_Z = -1.0
MOUTH_PER_WIDTH = .29     # bell mouth radius per unit of slot width (the Shepherd's ratio)
BELL_LENGTH = 6.0
BLOCK_AFT = -92.0         # drive block aft face; the bells stand proud of it to the slot line
ANCHORS = {}              # engine index -> (location, exhaust direction)
HOUSINGS = {'port': (-47, 29.0), 'starboard': (-47, -29.0)}
HOUSING_R = 16.0


def build(kit):
    block = prism(kit, 'Drive block', [(BLOCK_AFT, -33), (-84, -41), (-64, -41), (-60, -35), (-60, 35), (-64, 41),
                                      (-84, 41), (BLOCK_AFT, 33)], section(-13, 9, 4, 3), kit.steel)
    kit.hull.append(block)
    for side, s in (('port', 1), ('starboard', -1)):
        drum = pod(kit, f'Drive {side} reaction drum', [(-90, 6, 6), (-88.5, 8.8, 8.8), (-69, 8.8, 8.8), (-67, 7, 7)],
                   s*42.5, 1.0, kit.grey, n=16)
        kit.hull.append(drum)
        kit.cylinder(f'Drive {side} drum band', (-79, s*42.5, 1.0), 9.2, 3.0, kit.red, 'X', 16, .1)
        hx, hy = HOUSINGS[side]
        h = kit.cylinder(f'Drive {side} housing', (hx, hy, -1), HOUSING_R, 18, kit.steel, 'Z', 24, .3)
        kit.hull.append(h)
        kit.cylinder(f'Drive {side} housing crown port', (hx, hy, 8.1), 6.0, .4, kit.glow, 'Z', 24, 0)
        # Guard arc: an annular sector outboard of the housing, its tips carrying the flare launchers.
        verts, faces = [], []
        n = 12
        for i in range(n+1):
            a = math.radians(65+50*i/n)
            for rr in (15.0, 29.5):
                for z in (-5.0, 5.0):
                    verts.append((hx+rr*math.cos(a), hy+s*rr*math.sin(a), z))
        for i in range(n):
            a, b = 4*i, 4*(i+1)
            faces += [(a, b, b+1, a+1), (a+2, a+3, b+3, b+2), (a, a+2, b+2, b), (a+1, b+1, b+3, a+3)]
        faces += [(0, 1, 3, 2), (4*n, 4*n+2, 4*n+3, 4*n+1)]
        kit.mesh(f'Frame {side} guard arc', verts, faces, kit.tan, .3)
        # The arc's roots: it springs from the housing's forward and aft shoulders.
        for end, plan in (('forward', [(-42, 30), (-31, 33), (-33, 49), (-41, 50)]),
                          ('aft', [(-61, 37), (-53, 38), (-54, 50), (-61, 48)])):
            plan = [(px, s*py) for px, py in plan]
            if s < 0:
                plan.reverse()
            kit.hull.append(prism(kit, f'Frame {side} guard arc {end} root', plan, section(-5, 5, 1, 1), kit.tan))
        # Struts tying the arc and housing to the spine and block.
        kit.box(f'Frame {side} housing yoke', (hx, s*15.0, -1), (10, 6, 8), kit.grey, .3)
    for i, (x, y, w, a) in enumerate(ENGINES, 1):
        rm = MOUTH_PER_WIDTH*w
        rt = rm*.62
        z = ENGINE_Z
        revolve_x(kit, f'Engine {i} aft-facing bell',
                  [(BLOCK_AFT+.5, rt+.4), (x+.4, rm+.25), (x, rm+.25), (x, rm), (BLOCK_AFT+.5, rt)], y, z, kit.steel, 24)
        kit.cylinder(f'Engine {i} mouth lip', (x+.15, y, z), rm+.35, .3, kit.grey, 'X', 24, 0)
        kit.cylinder(f'Engine {i} dark throat', (BLOCK_AFT-.3, y, z), rt, .6, kit.dark, 'X', 24, 0)
        e = kit.empty(f'Engine {i} exhaust anchor', (x, y, z), a)
        ANCHORS[i] = ((x, y, z), (-1, 0, 0))
