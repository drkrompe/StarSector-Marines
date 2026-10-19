"""Arms: both gun arms of the Onslaught (XIV), port authored, starboard mirrored.

The arm is a long diagonal box (X -15..215, Y 50..133 on the port side): a
dorsal deck of mosaic plating (brown-grey, dark brown, bone-brown, doublers,
hatch lids, three turret housings and the devastator housing), stepping down
outboard to the orange belt rail with its weathered-orange plates and dark grey
straps, the dark grey side sponson with its radiator well below, and the
orange broadside fin and flak sponson at the aft root.

Sixteen sources, eight per view:
  deck   (from above, bow at the top of the tile): eight slabs along X of every
         face at deck level (Z >= -8). The outboard terraces below that level
         (rail and sponson tops) stay on the class swatches.
  flank  (from port, bow to the left): the fin root, the fin, and an upper /
         lower row along the rail and sponson, and the nose side.
Every starboard part is the port part mirrored (skin_from + mirror_u); the arm
is Y-symmetric to within a unit (flipped-mask IoU in part-list/arms.json).

Members are boxes over the plating meshes (a box member takes every face of the
object whose centre lies in it). Left out, so they keep their class swatch:
'Hull port arm' itself (long strips across the deck, 82% under the plates, which
would stretch every tile to the whole arm width), the keel bar, the flak sponson's
tall side faces, the turret housings' sides, and the small
fittings: radiator fins, louvres, the flank trunk and its brackets, bolts,
grilles, pumps, mains and feed lines. Density is capped by the arm's own width:
a deck slab is the full ~45-unit deck across, so about 25 units along X.
"""
from parts_common import PORT, STARBOARD, UP_Z, DOWN_Z, BOW

UP = UP_Z
# (part, view, box ((x0, y0, z0), (x1, y1, z1)) on the port side, port objects)
PORT_PARTS = [
    ('arm-deck-1'    , UP   , ((-15, 40, -8), (10, 140, 30)),
     ['Armour port arm belt 5', 'Armour port arm flak sponson', 'Armour port broadside fin', 'Armour port broadside fin crest', 'Detail port arm hatch coamings', 'Detail port arm hatch lids', 'Hull port arm gallery']),
    ('arm-deck-2'    , UP   , ((10, 40, -8), (35, 140, 30)),
     ['Armour port arm belt rail', 'Armour port broadside fin', 'Armour port broadside fin crest', 'Detail port arm deck plate seams', 'Detail port arm deck plates', 'Detail port arm deck plates dark', 'Detail port arm deck plates light', 'Detail port arm flank plates dark', 'Detail port arm flank plates light', 'Detail port arm hatch coamings', 'Detail port arm rail plates weathered', 'Hull port arm gallery']),
    ('arm-deck-3'    , UP   , ((35, 40, -8), (60, 140, 30)),
     ['Armour port arm belt rail', 'Armour port arm devastator fairing', 'Armour port broadside fin', 'Armour port broadside fin crest', 'Detail port arm deck doublers', 'Detail port arm deck plate seams', 'Detail port arm deck plates', 'Detail port arm deck plates dark', 'Detail port arm deck plates light', 'Detail port arm flank plates dark', 'Detail port arm flank plates light', 'Detail port arm hatch coamings', 'Detail port arm hatch lids', 'Detail port arm rail plates weathered', 'Detail port arm rail straps', 'Hull port arm gallery']),
    ('arm-deck-4'    , UP   , ((60, 40, -8), (84, 140, 30)),
     ['Armour port arm belt rail', 'Armour port arm devastator fairing', 'Armour port arm devastator housing', 'Detail port arm deck doublers', 'Detail port arm deck plate seams', 'Detail port arm deck plates', 'Detail port arm deck plates dark', 'Detail port arm deck plates light', 'Detail port arm flank plates dark', 'Detail port arm flank plates light', 'Hull port arm gallery']),
    ('arm-deck-5'    , UP   , ((84, 40, -8), (108, 140, 30)),
     ['Armour port arm devastator housing', 'Armour port arm turret housing 3', 'Detail port arm deck plate seams', 'Detail port arm deck plates', 'Detail port arm deck plates dark', 'Detail port arm flank plates dark', 'Detail port arm machinery bay coamings', 'Detail port arm machinery bay floors', 'Detail port arm machinery bay frames', 'Hull port arm gallery']),
    ('arm-deck-6'    , UP   , ((108, 40, -8), (132, 140, 30)),
     ['Armour port arm coaming', 'Armour port arm turret housing 2', 'Armour port arm turret housing 3', 'Detail port arm deck doublers', 'Detail port arm deck plate seams', 'Detail port arm deck plates', 'Detail port arm deck plates dark', 'Detail port arm deck plates light', 'Detail port arm machinery bay coamings', 'Detail port arm machinery bay floors', 'Detail port arm machinery bay frames', 'Hull port arm gallery']),
    ('arm-deck-7'    , UP   , ((132, 40, -8), (156, 140, 30)),
     ['Armour port arm coaming', 'Armour port arm turret housing 1', 'Armour port arm turret housing 2', 'Detail port arm deck doublers', 'Detail port arm deck plate seams', 'Detail port arm deck plates dark', 'Detail port arm deck plates light', 'Detail port arm flank plates dark', 'Detail port arm hatch coamings', 'Detail port arm hatch lids', 'Hull port arm gallery']),
    ('arm-deck-8'    , UP   , ((156, 40, -8), (215, 140, 30)),
     ['Armour port arm turret housing 1', 'Detail port arm deck doublers', 'Detail port arm deck plate seams', 'Detail port arm deck plates', 'Detail port arm deck plates dark', 'Detail port arm deck plates light', 'Detail port arm flank plates dark', 'Detail port arm hatch coamings', 'Detail port arm hatch lids', 'Hull port arm gallery']),
    ('arm-flank-1'   , PORT , ((-15, 85, -12), (20, 140, 25)),
     ['Armour port arm belt 5', 'Armour port broadside fin', 'Armour port broadside fin crest', 'Detail port arm deck plates', 'Detail port arm flank plates dark', 'Detail port arm hatch lids', 'Hull port arm gallery']),
    ('arm-flank-2'   , PORT , ((20, 85, -8), (62, 140, 25)),
     ['Armour port arm devastator fairing', 'Armour port broadside fin', 'Armour port broadside fin crest', 'Detail port arm deck doublers', 'Detail port arm deck plate seams', 'Detail port arm deck plates', 'Detail port arm deck plates dark', 'Detail port arm deck plates light', 'Detail port arm flank plates dark', 'Detail port arm flank plates light', 'Detail port arm hatch lids', 'Detail port arm rail plates weathered', 'Detail port arm rail straps', 'Hull port arm gallery']),
    ('arm-flank-3'   , PORT , ((20, 85, -40), (62, 140, -8)),
     ['Armour port arm belt rail', 'Armour port arm step 3', 'Detail port arm flank plates dark', 'Detail port arm plate seams', 'Detail port arm rail plates weathered', 'Detail port arm rail straps', 'Hull port arm gallery']),
    ('arm-flank-4'   , PORT , ((62, 85, -20), (112, 140, 25)),
     ['Armour port arm belt rail', 'Armour port arm devastator housing', 'Detail port arm deck plate seams', 'Detail port arm deck plates', 'Detail port arm deck plates dark', 'Detail port arm deck plates light', 'Detail port arm flank plates dark', 'Detail port arm flank plates light', 'Detail port arm machinery bay floors', 'Detail port arm rail plates weathered', 'Detail port arm rail straps', 'Hull port arm gallery']),
    ('arm-flank-5'   , PORT , ((62, 85, -45), (112, 140, -20)),
     ['Armour port arm side sponson', 'Detail port arm flank plates dark', 'Detail port arm plate seams', 'Detail port arm rail plates weathered', 'Detail port arm sponson plates', 'Detail port arm sponson plates steel', 'Detail port arm sponson radiator floor', 'Detail port arm sponson radiator frame']),
    ('arm-flank-6'   , PORT , ((112, 72, -20), (165, 140, 25)),
     ['Armour port arm belt rail', 'Detail port arm deck doublers', 'Detail port arm deck plate seams', 'Detail port arm deck plates', 'Detail port arm deck plates dark', 'Detail port arm deck plates light', 'Detail port arm flank plates dark', 'Detail port arm flank plates light', 'Detail port arm hatch lids', 'Detail port arm machinery bay floors', 'Detail port arm machinery bay frames', 'Detail port arm rail plates weathered', 'Detail port arm rail straps', 'Hull port arm gallery']),
    ('arm-flank-7'   , PORT , ((112, 72, -45), (165, 140, -20)),
     ['Armour port arm belt rail', 'Armour port arm side sponson', 'Detail port arm flank plates light', 'Detail port arm plate seams', 'Detail port arm rail plates weathered', 'Detail port arm rail straps', 'Detail port arm sponson plates', 'Detail port arm sponson plates steel', 'Detail port arm sponson radiator floor', 'Detail port arm sponson radiator frame']),
    ('arm-flank-8'   , PORT , ((165, 55, -35), (215, 140, 25)),
     ['Armour port arm belt rail', 'Armour port nose cheek 1', 'Detail port arm casemate surround', 'Detail port arm deck doublers', 'Detail port arm deck plate seams', 'Detail port arm deck plates', 'Detail port arm deck plates dark', 'Detail port arm deck plates light', 'Detail port arm flank plates dark', 'Detail port arm flank plates light', 'Detail port arm hatch lids', 'Detail port arm rail plates weathered', 'Detail port arm rail straps', 'Hull port arm gallery']),
]
WHAT = {'deck': 'port arm dorsal deck plating, slab {i} of 8 from aft (X {a:.0f}..{b:.0f})',
        'flank': 'port arm outboard flank, tile {i} (X {a:.0f}..{b:.0f}, Z {c:.0f}..{d:.0f})'}


def _stbd(name):
    return name.replace(' port ', ' starboard ', 1)


PARTS = {}
for part, view, box, objs in PORT_PARTS:
    (x0, y0, z0), (x1, y1, z1) = box
    kind, i = part.split('-')[1], part.split('-')[2]
    if view == UP:
        spec = dict(dir=UP_Z, up=BOW, what=WHAT['deck'].format(i=i, a=x0, b=x1))
        sdir = UP_Z
    else:
        spec = dict(dir=PORT, up=UP_Z, what=WHAT['flank'].format(i=i, a=x0, b=x1, c=z0, d=z1))
        sdir = STARBOARD
    PARTS[part + '-port'] = dict(spec, members=[(o, {'box': box}) for o in objs], cone=.6)
    sbox = ((x0, -y1, z0), (x1, -y0, z1))
    PARTS[part + '-stbd'] = dict(spec, dir=sdir, members=[(_stbd(o), {'box': sbox}) for o in objs], cone=.6,
                                 skin_from=part + '-port', mirror_u=True,
                                 what=spec['what'].replace('port arm', 'starboard arm') + ' (mirror of port)')

# v2 of every sheet: the first generation painted the bone-brown plates nearly bone white
# (L ~71 against the class's 53), so they graded as a colour-layout swap; the v2 prompts
# ask for the medium tan explicitly. The v1 masters stay in generated/ as the record.
# v2 still came back about 10 L bright on every class (relative tones right), which left
# the bone-brown of four tiles at the bone boundary; those four were regenerated together
# (arms-pale-v3, asking for INPUT 2's values) and the v2 sheets keep their other tiles
# (a v2 sheet's trailing tiles are simply not cut).
SHEETS = {
    'arms-deck-fwd-v2': ['arm-deck-5-port', 'arm-deck-6-port', 'arm-deck-7-port'],
    'arms-deck-aft-v2': ['arm-deck-1-port', 'arm-deck-2-port', 'arm-deck-3-port', 'arm-deck-4-port'],
    'arms-flank-aft-v2': ['arm-flank-1-port', 'arm-flank-2-port', 'arm-flank-3-port', 'arm-flank-4-port'],
    'arms-flank-fwd-v2': ['arm-flank-5-port'],
    'arms-pale-v3': ['arm-deck-8-port', 'arm-flank-6-port', 'arm-flank-7-port', 'arm-flank-8-port'],
}
