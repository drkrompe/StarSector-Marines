"""Geometry only for the marines' held weapons (variants/weapons/*.png).

Code owns every silhouette; ImageGen reskins material and detail. Every weapon is seen from straight
overhead, lying along the marine's forward axis: MUZZLE UP, touching the top edge of its frame, because
the runtime places the muzzle flash at the sprite's top edge (`LayeredUnitComposer`, the muzzle distance
is the pivot's distance from the top). Each frame is the shipped sprite's exact pixel size times SCALE,
so the pose layouts in unit-layer-layouts.appearance.json and the special-equipment carrier layers keep
working unchanged. The red tick on each frame's edge in the review overlay marks its pivot: the firing
hand for a primary, the shoulder for the launcher.

  infantry-weapons-guide.png         one sheet of six frames side by side, 64 px gutters, transparent ground.
  infantry-weapons-guide.json        the frames: name, output size, pivot fraction, rectangle on the sheet;
                                     packedFrames for the packed sheet.
  infantry-weapons-grades-guide.png  the rifle and DMR frames only (surplus and masterwork reskins).
  infantry-weapons-laser-guide.png   the pulse rifle's second geometry, on the first sheet's layout.
  infantry-weapons-packed-guide.png  the rocket launcher stowed on the back (special.srm-1's
                                     packedLayer), its own one-frame sheet.

The surplus rifle and masterwork DMR reuse the rifle and DMR frames; they are reskins of the same
geometry, not separate silhouettes (see art-source/infantry-weapons/).
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art-source/templates'
SCALE = 8
GUTTER = 64

# name, shipped size, pivot (x, y fraction) -- sizes and pivots are the runtime contract, not choices.
FRAMES = [
    ('rifle', (25, 98), (0.5, 0.7449)),
    ('laser-gun', (21, 98), (0.5, 0.7449)),
    ('smg', (23, 78), (0.5, 0.7449)),
    ('dmr', (16, 123), (0.5, 0.7449)),
    ('anti-materiel-rifle', (79, 144), (0.5, 0.75)),
    ('srm-1', (64, 156), (0.5, 0.5385)),
]
# The launcher stowed on the back: its own sheet, at the packed layer's carrier size.
PACKED_FRAMES = [('srm-1-packed', (45, 144), (0.5, 0.5))]

# Guide greys: each distinct part a distinct tone, so the reskin can tell them apart.
DARK = '#2b3034'      # barrel steel, grips, rails
MID = '#5d666c'       # receiver / main body
LIGHT = '#8c969c'     # furniture: handguards, stocks, shrouds
PALE = '#b8c0c5'      # small fittings: sights, lugs, brackets
GLASS = '#3d6f8f'     # optics lenses
GLOW_BLUE = '#3fb4ff'
GLOW_GREEN = '#5dff7a'
HAZARD = '#c9962a'
COPPER = '#a8693a'
WEBBING = '#c8c2a8'


class Pen:
    """Draws in shipped-sprite pixels, centred on the frame's axis; scales to guide pixels."""

    def __init__(self, draw, origin, size):
        self.d = draw
        self.ox, self.oy = origin
        self.cx = size[0] / 2

    def _p(self, x, y):
        return self.ox + (self.cx + x) * SCALE, self.oy + y * SCALE

    def box(self, x0, y0, x1, y1, fill, r=0.0):
        a, b = self._p(x0, y0)
        c, d = self._p(x1, y1)
        if r:
            self.d.rounded_rectangle((a, b, c, d), r * SCALE, fill=fill)
        else:
            self.d.rectangle((a, b, c, d), fill=fill)

    def bar(self, w, y0, y1, fill, r=0.0, dx=0.0):
        """A part of width w centred on the axis (offset dx)."""
        self.box(dx - w / 2, y0, dx + w / 2, y1, fill, r)

    def poly(self, pts, fill):
        self.d.polygon([self._p(x, y) for x, y in pts], fill=fill)

    def disc(self, x, y, r, fill):
        a, b = self._p(x - r, y - r)
        c, d = self._p(x + r, y + r)
        self.d.ellipse((a, b, c, d), fill=fill)


def rifle(p):
    """FR-1 field rifle: conventional layout, flash hider, vented handguard, top optic, side charging
    lug, pistol grip at the pivot and a solid fixed stock."""
    p.bar(5, 0, 6, DARK, 1)                       # flash hider
    p.bar(3, 6, 21, DARK)                         # barrel
    p.bar(3.4, 12, 14, PALE)                      # front sight post collar
    p.bar(9, 21, 45, LIGHT, 2)                    # handguard
    for y in (25, 30, 35, 40):
        p.bar(5, y, y + 2, DARK, 1)               # handguard vents
    p.bar(11, 44, 72, MID, 1.5)                   # receiver
    p.bar(7, 42, 60, DARK, 2)                     # optic body on the top rail
    p.bar(5, 42, 45, GLASS, 1.5)                  # objective lens, forward
    p.bar(4, 57, 60, GLASS, 1.5)                  # eyepiece, rearward
    p.box(5.5, 61, 10, 65, PALE, 1)               # charging lug, right side
    p.box(-8, 52, -5.5, 58, DARK, 0.8)            # ejection port cover, left side
    p.bar(6, 70, 79, DARK, 1.5)                   # pistol grip (the firing hand)
    p.poly([(-4.5, 78), (4.5, 78), (5, 96), (-5, 96)], LIGHT)   # stock
    p.bar(11, 95, 98, DARK, 1)                    # butt pad


def laser_gun(p):
    """Pulse rifle (LASER_GUN family): a squared prism emitter head, a slim barrel wound with six copper
    focusing coils, two radiator plates along its sides, a ceramic body and a side power cell. The glow
    is kept to the lens and two small indicators; the coils are metal, not light."""
    p.bar(11, 0, 9, PALE, 1.5)                    # prism emitter housing
    p.bar(5, 0.5, 2.5, GLOW_GREEN, 1)             # lens
    p.bar(6, 9, 38, MID, 1)                       # barrel
    for y in (12, 16, 20, 24, 28, 32):
        p.bar(9, y, y + 2.2, COPPER, 0.8)         # focusing coils, wound round the barrel
    for x in (-7, 7):
        p.bar(3, 13, 34, LIGHT, 1, dx=x)          # radiator plates either side
        p.bar(3.6, 22, 25, DARK, 0.6, dx=x)       # plate clamp to the barrel
    p.bar(13, 36, 72, LIGHT, 2.5)                 # body
    p.bar(6, 42, 60, DARK, 2)                     # top optic
    p.bar(4, 42, 45, GLASS, 1.5)
    p.bar(2.4, 64, 67, GLOW_GREEN, 1)             # charge indicator
    p.box(6.5, 50, 10.5, 66, DARK, 1)             # power cell, right side
    p.box(7.6, 62, 9.4, 64.5, GLOW_GREEN, 0.6)    # cell indicator
    p.bar(6, 70, 79, DARK, 1.5)                   # grip
    p.bar(11, 78, 98, MID, 3)                     # stock block
    p.bar(5, 82, 94, DARK, 2)                     # stock cut-out


def laser_gun_v1(p):
    """First pulse rifle geometry, kept because infantry-weapons.raw.png was reskinned from it: a lens emitter, a focusing barrel with three wide heat-sink fin
    pairs that give it its silhouette, a glowing coil along the body and a side power cell."""
    p.bar(9, 0, 5, PALE, 2)                       # emitter shroud
    p.bar(5, 0.5, 3, GLOW_GREEN, 1.5)             # lens
    p.bar(7, 5, 37, MID, 1)                       # focusing barrel
    for y in (10, 18, 26):
        p.bar(21, y, y + 4, DARK, 1)              # heat-sink fins, full width
    p.bar(2, 6, 36, GLOW_GREEN)                   # focusing coil glow, along the top
    p.bar(13, 36, 72, LIGHT, 2.5)                 # body
    p.bar(3, 40, 66, GLOW_GREEN, 1)               # coil window along the spine
    p.box(6.5, 50, 10.5, 66, DARK, 1)             # power cell, right side
    p.box(7.3, 53, 9.7, 63, GLOW_GREEN, 0.6)      # cell charge strip
    p.bar(6, 70, 79, DARK, 1.5)                   # grip
    p.bar(11, 78, 98, MID, 3)                     # stock block
    p.bar(5, 82, 94, DARK, 2)                     # stock cut-out


def smg(p):
    """Shard SMG: short perforated shroud, compact receiver, box magazine jutting out to the LEFT (a
    side-feed reads from above where an under-feed is hidden), folding wire stock."""
    p.bar(4, 0, 4, DARK, 1)                       # muzzle
    p.bar(7, 4, 31, LIGHT, 2)                     # barrel shroud
    for y in (8, 13, 18, 23):
        p.bar(3, y, y + 2.5, DARK, 1)             # shroud perforations
    p.bar(9, 30, 57, MID, 1.5)                    # receiver
    p.box(-11.5, 36, -4.5, 45, DARK, 1)           # side magazine, left
    p.box(-11.5, 38, -8, 43, LIGHT, 0.6)          # magazine floor plate
    p.box(4.5, 40, 7, 47, PALE, 0.8)              # cocking handle, right
    p.bar(5, 36, 52, DARK, 1.5)                   # top rail with a reflex sight
    p.bar(3, 37, 40, GLASS, 1)
    p.bar(6, 55, 62, DARK, 1.5)                   # grip
    for x in (-3.5, 3.5):
        p.bar(1.6, 61, 76, DARK, dx=x)            # wire stock struts
    p.bar(9, 74, 78, MID, 1.5)                    # butt plate


def dmr(p):
    """RG designated marksman railgun: two long rails with a dark gap between them, joined by braces, a
    capacitor block with a long scope, skeleton stock. Long and thin, the opposite of the SMG."""
    for x in (-3.5, 3.5):
        p.bar(3, 0, 62, DARK, 0.8, dx=x)          # rails
    p.bar(2, 1, 61, GLOW_BLUE)                    # the field between the rails
    for y in (6, 20, 34, 48):
        p.bar(11, y, y + 2.5, PALE, 1)            # rail braces
    p.bar(12, 60, 90, MID, 2)                     # capacitor block
    for y in (64, 70, 76):
        p.bar(9, y, y + 2, DARK, 0.8)             # capacitor bands
    p.bar(6, 46, 82, DARK, 2)                     # long scope
    p.bar(4.5, 46, 49, GLASS, 1.5)
    p.bar(4, 79, 82, GLASS, 1.5)
    p.bar(6, 89, 97, DARK, 1.5)                   # grip
    p.box(-5, 97, 5, 121, LIGHT, 2)               # skeleton stock frame
    p.box(-2.5, 100, 2.5, 116, (0, 0, 0, 0))      # stock opening, transparent
    p.bar(11, 119, 123, DARK, 1)                  # butt pad


def anti_materiel_rifle(p):
    """Anti-materiel rifle: heavy barrel with a big ported muzzle brake, bipod legs folded back along the
    barrel, a box magazine on the right, a large scope, a padded heavy stock. Mostly narrow; the frame's
    width is the carrier layer's aspect, the silhouette does not have to fill it."""
    p.bar(15, 0, 11, MID, 2)                      # muzzle brake
    for y in (2.5, 6.5):
        p.bar(19, y, y + 2, DARK, 0.8)            # brake ports, out to the sides
    p.bar(6, 11, 66, DARK, 1)                     # heavy barrel
    p.bar(8, 30, 34, PALE, 1)                     # bipod yoke
    for x in (-5.5, 5.5):
        p.bar(1.8, 33, 64, LIGHT, 0.8, dx=x)      # folded bipod legs
    p.bar(16, 64, 106, MID, 2)                    # receiver
    p.bar(10, 58, 96, DARK, 3)                    # scope
    p.bar(8, 58, 62, GLASS, 2)
    p.bar(6, 92, 96, GLASS, 2)
    p.box(8, 72, 17, 90, LIGHT, 1.5)              # box magazine, right side
    p.box(-12, 80, -8, 86, PALE, 1)               # bolt handle, left side
    p.bar(8, 104, 113, DARK, 2)                   # grip (firing hand at the pivot, y = 108)
    p.bar(16, 112, 140, LIGHT, 3)                 # heavy stock
    p.bar(8, 118, 132, MID, 2)                    # cheek pad
    p.bar(18, 139, 144, DARK, 1.5)                # recoil pad


def srm_1(p):
    """Shoulder rocket launcher: a straight tube with a flared muzzle and rear venturi, the sight and
    trigger housing on the right side, a shoulder pad at the pivot. Hazard bands at both ends."""
    p.bar(40, 0, 14, DARK, 4)                     # front flare
    p.bar(32, 3, 11, '#1a1d1f', 3)                # bore mouth
    p.bar(30, 12, 146, LIGHT, 3)                  # tube
    for y in (16, 140):
        p.bar(31, y, y + 4, HAZARD)               # hazard bands
    p.bar(30, 44, 48, MID)                        # tube joints
    p.bar(30, 110, 114, MID)
    p.box(15, 58, 30, 102, MID, 3)                # sight / trigger housing, right
    p.box(18, 62, 27, 72, GLASS, 2)               # sight glass
    p.box(19, 84, 26, 98, DARK, 2)                # trigger grip
    p.box(-22, 30, -15, 40, PALE, 1.5)            # folding front sight, left
    p.bar(22, 76, 92, DARK, 4)                    # shoulder pad (pivot y = 84)
    p.bar(42, 144, 156, DARK, 4)                  # rear venturi
    p.bar(34, 147, 154, '#1a1d1f', 3)


def srm_1_packed(p):
    """The launcher stowed across the wearer's back: the same tube with both ends capped, two webbing
    straps crossing it with buckles, the sight housing folded flat along the right side. Drawn at its
    own carrier size; the packed layer slings it diagonally over the body."""
    p.bar(26, 6, 138, LIGHT, 3)                   # tube
    p.bar(30, 0, 10, DARK, 4)                     # front end cap
    p.bar(30, 134, 144, DARK, 4)                  # rear end cap
    for y in (14, 126):
        p.bar(27, y, y + 3, HAZARD)               # hazard bands
    for y in (34, 100):
        p.bar(45, y, y + 9, WEBBING, 1.5)         # sling straps, running off under the body
        p.bar(10, y - 1, y + 10, DARK, 1.5, dx=-17)   # buckles
    p.box(13, 56, 20, 88, MID, 2)                 # folded sight housing, right
    p.box(14.5, 60, 18.5, 67, GLASS, 1)


DRAW = {
    'rifle': rifle, 'laser-gun': laser_gun, 'smg': smg, 'dmr': dmr,
    'anti-materiel-rifle': anti_materiel_rifle, 'srm-1': srm_1,
    'srm-1-packed': srm_1_packed,
}


def layout(frame_list=FRAMES):
    x, frames = GUTTER, []
    height = max(s[1] for _, s, _ in frame_list) * SCALE
    for name, size, pivot in frame_list:
        w, h = size[0] * SCALE, size[1] * SCALE
        frames.append({'name': name, 'size': list(size), 'pivot': list(pivot),
                       'rect': [x, GUTTER, x + w, GUTTER + h]})
        x += w + GUTTER
    return frames, (x, height + 2 * GUTTER)


def render(names=None, draw=None, frame_list=FRAMES):
    """A sheet, optionally restricted to some frames (blank elsewhere), with per-frame drawing overrides."""
    frames, size = layout(frame_list)
    im = Image.new('RGBA', size)
    d = ImageDraw.Draw(im)
    for f in frames:
        if names is None or f['name'] in names:
            (draw or {}).get(f['name'], DRAW[f['name']])(Pen(d, f['rect'][:2], f['size']))
    return im, frames


def main():
    # The first sheet and its grades keep the geometry their retained raws were reskinned from.
    first = {'laser-gun': laser_gun_v1}
    im, frames = render(draw=first)
    im.save(OUT / 'infantry-weapons-guide.png')
    packed_frames = layout(PACKED_FRAMES)[0]
    (OUT / 'infantry-weapons-guide.json').write_text(json.dumps(
        {'scale': SCALE, 'frames': frames, 'packedFrames': packed_frames}, indent=2) + '\n')
    render({'rifle', 'dmr'}, first)[0].save(OUT / 'infantry-weapons-grades-guide.png')
    # The pulse rifle's second geometry, on the first sheet's layout so its frame rect is shared.
    render({'laser-gun'})[0].save(OUT / 'infantry-weapons-laser-guide.png')
    render(frame_list=PACKED_FRAMES)[0].save(OUT / 'infantry-weapons-packed-guide.png')
    print(im.size, [f['name'] for f in frames])


if __name__ == '__main__':
    main()
