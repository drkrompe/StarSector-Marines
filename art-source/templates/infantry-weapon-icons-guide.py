"""Geometry only for the held weapons' armory icons (graphics/ui/armory/weapon-*.png).

The battle sprites (infantry-weapons-guide.py) are the guns seen from straight above; an armory icon is
the same gun in profile, as the other armory icons show their kit. Every part named in the top-down
guide appears here in side view -- the rifle's vented handguard and top optic, the pulse rifle's copper
coils, the SMG's side magazine and wire stock, the railgun's twin rails -- so the icon and the sprite on
the marine read as one weapon. Muzzle to the RIGHT, seen from the left side (the SMG's side magazine
faces the viewer), on a transparent ground. The exporter turns each one up to the right like the
anti-materiel rifle's icon.

  infantry-weapon-icons-guide.png   two columns of frames, 100 x 36 units at SCALE px each, 64 px gutters.
  infantry-weapon-icons-guide.json  the frames: name and rectangle on the sheet.

The anti-materiel rifle, drawn later, sits in the sixth slot on its own sheet
(infantry-weapon-icons-amr-guide.png). The surplus rifle and masterwork DMR share the rifle and DMR geometry; their icons are reskins of the
same frames (infantry-weapon-icons-grades-guide.png).
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'art-source/templates'
SCALE = 8
GUTTER = 64
UNITS = (100, 36)
FRAMES = ['rifle', 'laser-gun', 'smg', 'dmr', 'srm-1', 'anti-materiel-rifle']

DARK = '#2b3034'
MID = '#5d666c'
LIGHT = '#8c969c'
PALE = '#b8c0c5'
GLASS = '#3d6f8f'
GLOW_GREEN = '#5dff7a'
GLOW_BLUE = '#3fb4ff'
HAZARD = '#c9962a'
COPPER = '#a8693a'
CLEAR = (0, 0, 0, 0)


class Pen:
    """Draws in frame units (x right, y down), scaled to guide pixels."""

    def __init__(self, draw, origin):
        self.d = draw
        self.ox, self.oy = origin

    def _p(self, x, y):
        return self.ox + x * SCALE, self.oy + y * SCALE

    def box(self, x0, y0, x1, y1, fill, r=0.0):
        a, b = self._p(x0, y0)
        c, d = self._p(x1, y1)
        if r:
            self.d.rounded_rectangle((a, b, c, d), r * SCALE, fill=fill)
        else:
            self.d.rectangle((a, b, c, d), fill=fill)

    def poly(self, pts, fill):
        self.d.polygon([self._p(x, y) for x, y in pts], fill=fill)


def rifle(p):
    """FR-1 field rifle: solid stock, receiver with top optic and charging lug, pistol grip, curved box
    magazine, vented handguard, barrel with a front sight post and flash hider."""
    p.poly([(0, 9), (22, 11), (22, 20), (0, 27)], LIGHT)             # stock
    p.box(0, 8.5, 2, 27.5, DARK, 0.6)                                 # butt pad
    p.box(22, 8, 53, 19, MID, 1)                                      # receiver
    p.box(30, 2, 50, 7.5, DARK, 1.5)                                  # optic
    p.box(48.5, 2.5, 50.5, 7, GLASS, 0.6)                             # objective lens
    p.box(29.5, 3, 31, 6.5, GLASS, 0.5)                               # eyepiece
    p.box(33, 7, 36, 8, DARK)                                         # optic mounts
    p.box(44, 7, 47, 8, DARK)
    p.box(24, 12, 28, 14.5, PALE, 0.6)                                # charging lug
    p.poly([(35, 19), (41, 19), (39, 30), (33.5, 30)], DARK)          # pistol grip
    p.box(41, 19, 46, 22.5, DARK, 1)                                  # trigger guard
    p.box(42.5, 19, 44.5, 21, CLEAR)
    p.poly([(46.5, 19), (52.5, 19), (55, 31), (49, 32)], DARK)        # magazine
    p.box(53, 9, 79, 17.5, LIGHT, 1.5)                                # handguard
    for x in (57, 62, 67, 72):
        p.box(x, 11.5, x + 3, 15, DARK, 0.8)                          # handguard vents
    p.box(79, 11.5, 94, 14.5, DARK)                                   # barrel
    p.box(84, 8, 85.5, 11.5, PALE)                                    # front sight post
    p.box(94, 10.5, 100, 15.5, DARK, 0.8)                             # flash hider


def laser_gun(p):
    """Pulse rifle: stock block with a cut-out, ceramic body with top optic and charge light, power cell
    under the body, slim barrel wound with copper coils over a radiator plate, prism emitter."""
    p.box(0, 8, 24, 21, MID, 2)                                       # stock block
    p.box(5, 11.5, 19, 17.5, CLEAR, 1.5)                              # stock cut-out
    p.box(24, 6, 61, 19.5, LIGHT, 2.5)                                # ceramic body
    p.box(30, 1.5, 48, 6, DARK, 1.5)                                  # optic
    p.box(46.5, 2, 48.5, 5.5, GLASS, 0.6)
    p.box(54, 9, 57, 11, GLOW_GREEN, 0.6)                             # charge indicator
    p.box(45, 19.5, 58, 26, DARK, 1)                                  # power cell
    p.box(54, 21.5, 56, 23.5, GLOW_GREEN, 0.4)                        # cell indicator
    p.poly([(33, 19.5), (39, 19.5), (37.5, 30), (32, 30)], DARK)      # grip
    p.box(39, 19.5, 44, 22.5, DARK, 1)                                # trigger guard
    p.box(40.5, 19.5, 42.5, 21, CLEAR)
    p.box(61, 10, 89, 14.5, MID, 0.8)                                 # barrel
    p.box(63, 15.5, 87, 18.5, LIGHT, 1)                               # radiator plate
    p.box(73, 14, 76, 16, DARK)                                       # plate clamp
    for x in (64, 68, 72, 76, 80, 84):
        p.box(x, 8, x + 2.2, 16.5, COPPER, 0.8)                       # focusing coils
    p.box(88, 5.5, 100, 19, PALE, 1.5)                                # prism emitter
    p.box(98, 9.5, 100, 15, GLOW_GREEN, 0.6)                          # lens


def smg(p):
    """Shard SMG, from its left side: folding wire stock, rusty receiver with a reflex sight, the side
    magazine standing out towards the viewer, perforated shroud with a strip of hazard tape."""
    for y in (10, 17.5):
        p.box(14, y, 36, y + 1.6, DARK, 0.6)                          # wire stock struts
    p.box(12, 8, 15, 21, MID, 1)                                      # butt plate
    p.box(35, 8, 61, 18.5, MID, 1.2)                                  # receiver
    p.box(43, 3.5, 53, 8, DARK, 1)                                    # reflex sight
    p.box(44.5, 4.5, 46, 7, GLASS, 0.4)
    p.box(45, 9.5, 54, 18, DARK, 1)                                   # side magazine, towards the viewer
    p.box(46.5, 15, 52.5, 17, LIGHT, 0.5)                             # magazine floor plate
    p.box(56, 6.5, 59, 8, PALE, 0.5)                                  # cocking handle
    p.poly([(38, 18.5), (43.5, 18.5), (42, 28.5), (37, 28.5)], DARK)  # grip
    p.box(61, 9, 84, 16.5, LIGHT, 1.5)                                # perforated shroud
    for x in (64, 68, 72, 78):
        p.box(x, 11.5, x + 2.5, 14, DARK, 1)                          # perforations
    p.box(74.5, 9, 77, 16.5, HAZARD)                                  # hazard tape
    p.box(84, 10.5, 88, 15, DARK, 0.6)                                # muzzle


def dmr(p):
    """RG railgun marksman rifle: skeleton stock, navy capacitor block with dark bands, long scope, two
    long rails with the glowing field between them, pale braces."""
    p.box(0, 9, 22, 21, LIGHT, 2)                                     # skeleton stock
    p.box(4, 12, 18, 18, CLEAR, 1.5)                                  # stock opening
    p.box(0, 8.5, 2, 21.5, DARK, 0.6)                                 # butt pad
    p.box(22, 7, 51, 20, MID, 2)                                      # capacitor block
    for x in (31, 37, 43):
        p.box(x, 7, x + 2, 20, DARK)                                  # capacitor bands
    p.box(25, 1, 57, 5.5, DARK, 1.8)                                  # long scope
    p.box(55.5, 1.5, 57.5, 5, GLASS, 0.6)
    p.box(24.5, 1.8, 26, 4.7, GLASS, 0.5)
    p.box(30, 5.5, 32, 7, DARK)                                       # scope mounts
    p.box(50, 5.5, 52, 7, DARK)
    p.poly([(26, 20), (31, 20), (29.5, 30), (24.5, 30)], DARK)        # grip
    p.box(31, 20, 35.5, 23, DARK, 1)                                  # trigger guard
    p.box(32.5, 20, 34, 21.5, CLEAR)
    p.box(51, 8, 100, 10.5, DARK, 0.6)                                # upper rail
    p.box(51, 14.5, 100, 17, DARK, 0.6)                               # lower rail
    p.box(51, 10.5, 99, 14.5, GLOW_BLUE)                              # field between the rails
    for x in (58, 70, 82, 94):
        p.box(x, 7, x + 2.2, 18, PALE, 0.6)                           # rail braces


def srm_1(p):
    """Shoulder rocket launcher: olive tube with hazard bands, flared muzzle and rear venturi, sight
    housing on top, trigger grip and fore grip below, shoulder pad and folding front sight."""
    p.box(0, 4.5, 8, 27.5, DARK, 1.5)                                 # rear venturi
    p.box(6, 8, 94, 24, LIGHT, 2)                                     # tube
    p.box(92, 6, 100, 26, DARK, 1.5)                                  # muzzle flare
    for x in (10, 86):
        p.box(x, 8, x + 3, 24, HAZARD)                                # hazard bands
    for x in (30, 66):
        p.box(x, 8, x + 1.5, 24, MID)                                 # tube joints
    p.box(40, 1.5, 58, 8, MID, 1.5)                                   # sight housing
    p.box(52, 2.5, 56.5, 6.5, GLASS, 0.8)
    p.box(70, 2.5, 72, 8, PALE)                                       # folding front sight
    p.box(30, 24, 42, 28, DARK, 1)                                    # shoulder pad
    p.poly([(45, 24), (51, 24), (50, 34), (44.5, 34)], DARK)          # trigger grip
    p.box(61, 24, 65.5, 31, DARK, 1)                                  # fore grip


def anti_materiel_rifle(p):
    """Anti-materiel rifle: padded heavy stock with cheek pad and recoil pad, receiver with bolt handle and
    box magazine, a big scope, heavy barrel with the bipod folded back along it, ported muzzle brake."""
    p.box(0, 8, 2.5, 26.5, DARK, 0.8)                                 # recoil pad
    p.poly([(2.5, 8), (24, 10), (24, 19), (2.5, 26)], LIGHT)          # heavy stock
    p.box(6, 6.5, 20, 10, MID, 1.2)                                   # cheek pad
    p.box(24, 8, 53, 18.5, MID, 1.2)                                  # receiver
    p.box(26, 0.5, 47, 6.5, DARK, 2)                                  # scope tube
    p.box(46, 0, 54, 7.5, DARK, 1.5)                                  # objective bell
    p.box(52.5, 1, 54.5, 6.5, GLASS, 0.6)
    p.box(25.5, 1.5, 27, 5.5, GLASS, 0.5)
    p.box(31, 6.5, 33.5, 8, DARK)                                     # scope rings
    p.box(42, 6.5, 44.5, 8, DARK)
    p.box(26, 15, 29.5, 19.5, PALE, 0.8)                              # bolt handle
    p.poly([(30, 18.5), (35.5, 18.5), (34, 29.5), (28.5, 29.5)], DARK)   # grip
    p.box(35.5, 18.5, 40.5, 21.5, DARK, 1)                            # trigger guard
    p.box(37, 18.5, 39, 20, CLEAR)
    p.box(41.5, 18.5, 50, 27.5, LIGHT, 1)                             # box magazine
    p.box(53, 10.5, 89, 15, DARK, 0.6)                                # heavy barrel
    p.box(58, 15, 61, 17.5, PALE, 0.6)                                # bipod yoke
    p.box(61, 16, 86, 17.6, LIGHT, 0.6)                               # folded bipod legs
    p.box(88, 8.5, 100, 17, MID, 1)                                   # muzzle brake
    for x in (90.5, 95):
        p.box(x, 9.5, x + 2.2, 16, DARK, 0.5)                         # brake ports


DRAW = {'rifle': rifle, 'laser-gun': laser_gun, 'smg': smg, 'dmr': dmr,
        'srm-1': srm_1, 'anti-materiel-rifle': anti_materiel_rifle}


def layout():
    w, h = UNITS[0] * SCALE, UNITS[1] * SCALE
    frames = []
    for i, name in enumerate(FRAMES):
        col, row = i % 2, i // 2
        x, y = GUTTER + col * (w + GUTTER), GUTTER + row * (h + GUTTER)
        frames.append({'name': name, 'rect': [x, y, x + w, y + h]})
    rows = (len(FRAMES) + 1) // 2
    return frames, (GUTTER + 2 * (w + GUTTER), GUTTER + rows * (h + GUTTER))


def render(names=None):
    frames, size = layout()
    im = Image.new('RGBA', size)
    d = ImageDraw.Draw(im)
    for f in frames:
        if names is None or f['name'] in names:
            DRAW[f['name']](Pen(d, f['rect'][:2]))
    return im, frames


def main():
    # The first sheet keeps the five frames its retained raw was reskinned from; the anti-materiel
    # rifle, added after, has the sixth slot on its own sheet so every frame rectangle is shared.
    im, frames = render(set(FRAMES) - {'anti-materiel-rifle'})
    im.save(OUT / 'infantry-weapon-icons-guide.png')
    render({'anti-materiel-rifle'})[0].save(OUT / 'infantry-weapon-icons-amr-guide.png')
    (OUT / 'infantry-weapon-icons-guide.json').write_text(
        json.dumps({'scale': SCALE, 'frames': frames}, indent=2) + '\n')
    render({'rifle', 'dmr'})[0].save(OUT / 'infantry-weapon-icons-grades-guide.png')
    print(im.size, [f['name'] for f in frames])


if __name__ == '__main__':
    main()
