"""Colossus Mk.III refit: what the pirates welded onto a Colossus to make a prize ship of her.

The sprite (colossus3_pirate.png) shows the guns and the launch bay (c3_mounts, c3_hull);
this module carries the rest of the refit the Mk.III's armour (600 against the Colossus's
300 in ship_data.csv) implies, all laid over the hull rather than cut into it, so the
silhouette and the scale solids are untouched:

- side skirts: rows of heavy plates hung on stand-off brackets a little off the cut-down
  modules' outboard walls, between the door rows, with the doors left clear;
- a bow glacis: two thick raked slabs over the hold's bow face either side of the
  launch bay's mouth, where a prize ship takes fire as she closes;
- scavenged plates: patches cut from other hulls and welded over the decks in their old
  grey primer and faded blue, every one framed in a weld bead.

Names: 'Refit ...'.
"""
import math
import random
from c3_hull import (Parts, SIDES, SIDE_SPLIT, FRONT, HOLD_AFT, HOLD_DECK, SIDE_DECK, WALL, DOORS, YC, y)

SKIRT_TOP, SKIRT_FOOT = -3.5, -33.0
SKIRT_OFF = 1.3                     # stand-off from the wall to the plate's inner face
SKIRT_T = .7                        # plate thickness: 35 cm of welded steel


def skirts(kit):
    rng = random.Random('Colossus Mk.III skirts')
    plates, scrap, welds, brackets, bolts = Parts(), Parts(), Parts(), Parts(), Parts()
    for side, s in SIDES:
        for part, (x0, x1) in (('fore', (SIDE_SPLIT+1.0, FRONT-3.0)), ('aft', (-16.0, SIDE_SPLIT-1.0))):
            (dx0, dx1), hatch = DOORS[part]
            gaps = [(dx0-2.2, dx1+2.2)]+([(hatch[0]-1.2, hatch[1]+1.2)] if hatch else [])
            runs, a = [], x0
            for g0, g1 in sorted(gaps):
                if g0 > a:
                    runs.append((a, g0))
                a = max(a, g1)
            if a < x1:
                runs.append((a, x1))
            for r0, r1 in runs:
                n = max(1, round((r1-r0)/7.5))
                pitch = (r1-r0)/n
                for k in range(n):
                    p0, p1 = r0+k*pitch+.15, r0+(k+1)*pitch-.15
                    top = SKIRT_TOP-rng.uniform(0, 1.6)
                    foot = SKIRT_FOOT+rng.uniform(-1.2, 1.5)
                    tgt = scrap if rng.random() < .22 else plates
                    w0, w1 = WALL+SKIRT_OFF, WALL+SKIRT_OFF+SKIRT_T
                    # The plate's foot is raked inboard, the way a skirt deflects a round down.
                    tgt.slab([(p0, y(r, s), z) for r, z in ((w0, top), (w1, top), (w1, foot+2.0), (w1-.6, foot),
                                                             (w0-.6, foot), (w0, foot+2.0))], (p1-p0, 0, 0))
                    welds.rbox(p0+.3, p1-.3, w1-.05, w1+.12, top-.5, top-.25, s)
                    for bz in (top-1.6, (top+foot)/2, foot+2.6):
                        brackets.rbox((p0+p1)/2-.35, (p0+p1)/2+.35, WALL-.1, w0+.05, bz-.5, bz+.5, s)
                        for bx in (p0+.8, p1-.8):
                            bolts.cyl((bx, y(w1, s), bz), .24, s*.18, 'Y', 6)
    plates.build(kit, 'Refit side skirt plates', kit.armour, .1)
    scrap.build(kit, 'Refit side skirt scavenged plates', kit.scrap_grey, .1)
    welds.build(kit, 'Refit side skirt welds', kit.weld)
    brackets.build(kit, 'Refit side skirt stand-off brackets', kit.steel, .05)
    bolts.build(kit, 'Refit side skirt bolts', kit.steel)


def glacis(kit):
    """Two raked slabs over the bow face beside the bay mouth (rel y 19..35, z -15..17)."""
    slabs, welds = Parts(), Parts()
    f = FRONT+.5
    for side, s in SIDES:
        a, b = 19.4, 35.4
        z0, z1 = -15.0, 17.0
        out = 2.2                                   # proud of the face at the foot, raking back to 0.6
        sec = [(f, z0), (f+out, z0+1.2), (f+.6, z1), (f, z1)]
        for r0, r1 in ((a, (a+b)/2-.2), ((a+b)/2+.2, b)):
            slabs.slab([(x, y(r0, s), z) for x, z in sec], (0, y(r1, s)-y(r0, s), 0))
        for r in (a-.1, (a+b)/2, b+.1):
            welds.slab([(f+.02, y(r-.12, s), z0), (f+out+.1, y(r-.12, s), z0+1.2), (f+.7, y(r-.12, s), z1),
                        (f+.02, y(r-.12, s), z1)], (0, y(r+.12, s)-y(r-.12, s), 0))
    slabs.build(kit, 'Refit bow glacis plates', kit.armour, .15)
    welds.build(kit, 'Refit bow glacis welds', kit.weld)


# Scavenged deck patches (x0, x1, rel0, rel1, side sign, livery): asymmetric on purpose.
PATCHES = [(-17.5, -13.0, 4.0, 15.0, 1, 'grey'), (-11.0, -5.0, 20.0, 31.0, -1, 'blue'),
           (2.0, 9.0, 29.0, 34.5, 1, 'grey'), (-17.0, -12.5, 18.0, 27.0, -1, 'grey'),
           (36.0, 41.5, 4.0, 12.0, -1, 'blue'), (30.5, 36.0, 49.0, 57.0, -1, 'grey')]


def patches(kit):
    parts = {'grey': Parts(), 'blue': Parts()}
    welds = Parts()
    for x0, x1, r0, r1, s, livery in PATCHES:
        hit = kit.surface((x0+x1)/2, y((r0+r1)/2, s))
        z = hit[0].z
        parts[livery].rbox(x0, x1, r0, r1, z, z+.28, s)
        for a, b, c, d in ((x0-.12, x1+.12, r0-.12, r0+.05), (x0-.12, x1+.12, r1-.05, r1+.12),
                           (x0-.12, x0+.05, r0, r1), (x1-.05, x1+.12, r0, r1)):
            welds.rbox(a, b, c, d, z, z+.36, s)
    parts['grey'].build(kit, 'Refit scavenged deck plates grey', kit.scrap_grey, .05)
    parts['blue'].build(kit, 'Refit scavenged deck plates blue', kit.scrap_blue, .05)
    welds.build(kit, 'Refit scavenged deck plate welds', kit.weld)


def build(kit):
    patches(kit)
    skirts(kit)
    glacis(kit)
