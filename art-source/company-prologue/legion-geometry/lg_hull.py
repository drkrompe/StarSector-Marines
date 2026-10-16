"""Central hull: the fore and aft body, the dorsal and tail spines and the
orange armour belts and bow blades. The command dome is lg_bridge.

Planforms are measured off legion_xiv.png (300x308, .ship centre 150,149):
pixel column c, row r covers X 158-r..159-r, Y 149-c..150-c. Port halves are
written out and mirrored. Heights are proposed art: the sprite gives none.

Read off the sprite:
- the bow is a grey faceted prow between two broad orange armour blades
  (X 80-148), which taper into the outer belt;
- the outer belt sweeps aft and outboard between the forward and aft flight
  decks to the sponson's tail (X -88, Y 138);
- the inner belt is a collar round the dome's head (X 80) that runs aft and
  outboard past the machinery bays to the engine block (X -110, Y 73);
- aft of X -40 the body drops to a lower machinery bed either side of the
  tail spine, which ends in the spike at X -137.
The torso is deep (owner review of the first blockout: "too flat; a thicker
torso with wing-like arms"): a ventral keel hangs under the fore and aft body
with its own chamfered belly, raked up into the bow as a long forefoot and up
into the stern under the engine block, so the sponsons read as arms held out
from a heavy body rather than one slab.
"""
from lg_kit import facet_normal, mirror_y, plane_cut, prism, section, strip_plan, ccw

KEEL, DECK = -20.0, 10.0


def full(port):
    """Port half (bow to stern, Y >= 0) closed by its mirror into one CCW plan."""
    stern_to_bow = [(x, -y) for x, y in reversed(port) if y > 0]
    return ccw(port + stern_to_bow)


# Fore body: bow tip to X -40, out to the sponson roots (the sponsons overlap it).
FORE_PORT = [(157, 0), (156, 3), (152, 10), (148, 18), (148, 34), (141, 41), (132, 44), (120, 51),
             (112, 55), (104, 59), (96, 65), (88, 69), (84, 74), (40, 80), (-40, 80), (-40, 0)]
FORE = section(KEEL, DECK, 7.0, 2.0)
# Aft body: the lower machinery bed from X -40 to the engine block, out to the notch.
AFT_PORT = [(-38, 0), (-38, 80), (-60, 80), (-76, 86), (-96, 84), (-100, 82), (-100, 0)]
AFT = section(KEEL+3, 4.0, 6.0, 1.5)
# Dorsal spine over the prow, ahead of the dome.
SPINE_PORT = [(152, 0), (151, 6), (146, 15), (130, 21), (104, 23), (84, 20), (64, 12), (60, 0)]
SPINE = [(0, DECK-2), (0, 15.0), (2.5, 17.5)]
# Tail spine: from behind the dome to the spike.
TAIL_PORT = [(8, 0), (8, 12), (-40, 14), (-92, 12), (-100, 8), (-120, 8), (-126, 6), (-136, 3), (-138, 0)]
TAIL = section(-12.0, 14.0, 4.0, 2.5)

# Ventral keel: the deep belly under the fore and aft body, inside their keel chamfers.
VENTRAL_PORT = [(118, 0), (110, 28), (96, 48), (70, 60), (-60, 62), (-90, 60), (-108, 48), (-122, 30), (-126, 0)]
VENTRAL = [(12.0, -44.0), (0, -32.0), (0, KEEL+4.0)]   # top sunk into the aft body (keel -17)

# Bow blades: the broad orange armour either side of the prow.
BLADE_PORT = [(148, 35), (141, 41), (132, 44), (120, 51), (112, 55), (104, 59), (96, 65), (88, 69),
              (80, 72), (74, 66), (80, 57), (92, 50), (106, 45), (122, 39), (138, 34), (146, 32)]
BLADE = [(0, DECK-3), (0, 12.5), (4.5, 17.0)]
# The outer belt crosses from the torso onto the wing arm, whose deck is 6 lower,
# so it is two runs: one on the torso and one stepped down onto the arm.
OUTER_BELT = [(80, 64), (70, 70), (58, 78), (51, 84)]
ARM_BELT = [(54, 81), (48, 87), (40, 96), (30, 99), (20, 96), (10, 94),
            (0, 101), (-12, 108), (-26, 114), (-42, 119), (-56, 124), (-72, 131), (-88, 138)]
ARM_STEP = 6.0
INNER_BELT = [(80, -2), (70, 8), (60, 16), (48, 23), (36, 22), (24, 17), (12, 20), (0, 28), (-12, 34),
              (-30, 43), (-48, 51), (-66, 57), (-84, 65), (-100, 71), (-108, 73)]
BELT = [(0, DECK-3), (0, 13.5), (2.0, 15.0)]



def bow_facets(kit, ob, name):
    # Bounded to the bow by position: forward of X 128 the deck falls away at
    # 20 degrees, and forward of X 118 the keel rises at 26 degrees; neither
    # plane reaches the ship behind those stations.
    plane_cut(ob, name+' bow deck facet', (128, 0, DECK), facet_normal((1, 0), 20), size=400)
    plane_cut(ob, name+' bow keel facet', (118, 0, KEEL), (0.438, 0, -0.899), size=400)


def build(kit):
    fore = prism(kit, 'Hull fore body', full(FORE_PORT), FORE, kit.plate)
    bow_facets(kit, fore, 'Hull fore body')
    aft = prism(kit, 'Hull aft body', full(AFT_PORT), AFT, kit.plate)
    spine = prism(kit, 'Hull dorsal spine', full(SPINE_PORT), SPINE, kit.plate)
    bow_facets(kit, spine, 'Hull dorsal spine')
    tail = prism(kit, 'Hull tail spine', full(TAIL_PORT), TAIL, kit.bone)
    # The spine falls away aft of the engine block into the spike (X -137).
    plane_cut(tail, 'Hull tail spine spike facet', (-110, 0, 13), facet_normal((-1, 0), 17), size=400)
    keel = prism(kit, 'Hull ventral keel', full(VENTRAL_PORT), VENTRAL, kit.plate)
    # Raked forefoot and stern: bounded by position, as the bow facets are.
    plane_cut(keel, 'Hull ventral keel forefoot', (60, 0, -44), (0.53, 0, -0.848), size=400)
    plane_cut(keel, 'Hull ventral keel stern rake', (-78, 0, -44), (-0.574, 0, -0.819), size=400)
    kit.hull += [fore, aft, spine, tail, keel]

    for side, flip in (('port', False), ('starboard', True)):
        m = (lambda p: mirror_y(p)) if flip else (lambda p: ccw(p))
        flipy = (lambda pts: [(x, -y) for x, y in pts]) if flip else (lambda pts: pts)
        blade = prism(kit, f'Armour {side} bow blade', m(ccw(BLADE_PORT)), BLADE, kit.orange)
        bow_facets(kit, blade, f'Armour {side} bow blade')
        outer = prism(kit, f'Armour {side} outer belt', ccw(strip_plan(flipy(OUTER_BELT), 8.0)), BELT, kit.orange)
        arm = prism(kit, f'Armour {side} outer belt arm run', ccw(strip_plan(flipy(ARM_BELT), 8.0)),
                    [(d, z-ARM_STEP) for d, z in BELT], kit.orange)
        kit.hull.append(arm)
        inner = prism(kit, f'Armour {side} inner belt', ccw(strip_plan(flipy(INNER_BELT), 7.0)), BELT, kit.orange)
        kit.hull += [blade, outer, inner]
