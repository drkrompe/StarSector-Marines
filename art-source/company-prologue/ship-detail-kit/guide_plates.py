"""Compose the ship-detail-guide's figure plates from existing review renders.

Every plate is 1920x1080 at 100% (the preview rule): up to four crops of the
accepted Legion and Colossus review images on the shared Drive, each fitted
into a 960x540 cell with a caption. Nothing is rendered; the crops come from
the review runs named in PLATES. Output goes to the Drive review folder (or
--out), never into git.

  python guide_plates.py [--drive "G:/My Drive/starsector"] [--out <folder>]
"""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

LC = 'review/legion/2026-10-19_texture-pass2_windows/03-views-close.png'
LC2 = 'review/legion/2026-10-19_texture-pass2_windows/04-views-close-2.png'
LFD = 'review/legion/2026-10-19_pass7-export_windows/07-flight-deck-detail.png'
LUQ = 'review/legion/2026-10-19_pass7-export_windows/04-under-quarter.png'
CC = 'review/colossus/2026-10-19_pass6-round1-texturing_windows/03-views-close.png'
CC2 = 'review/colossus/2026-10-19_pass6-round1-texturing_windows/04-views-close-2.png'
CD = 'review/colossus/2026-10-19_pass7-round1-cinematic_windows/03-detail.png'
CW = 'review/colossus/2026-10-19_pass6-round1-texturing_windows/01-views-whole.png'
LW = 'review/legion/2026-10-19_pass7-export_windows/01-three-quarter.png'

# plate id, title, [(source, crop box in source pixels, caption), ...]
PLATES = [
    ('00-references', 'The reference hulls', [
        (LW, (180, 120, 1740, 960), 'Legion (XIV) battlecarrier: pass 7, textured, accepted'),
        (CW, (0, 30, 960, 540), 'Colossus heavy freighter: pass 6, textured, accepted'),
        (LC, (960, 30, 1920, 540), 'Legion: plated command dome inside its armour collar'),
        (CC, (0, 570, 960, 1040), 'Colossus: the deckhouse, every part people-sized'),
    ]),
    ('01-armour-plating', 'Armour plating and plate breaks', [
        (LC, (0, 30, 960, 540), 'Legion: belt modules over a dark underlay, chevron plates on the prow'),
        (LC2, (960, 30, 1920, 540), 'Legion: three stacked strakes, buttresses, seam gaps'),
        (CC2, (960, 30, 1920, 540), 'Colossus: segmented pod casing, seam rings, strata groove'),
        (CC, (960, 30, 1920, 540), 'Colossus: deck panels, bolted covers, seams cut skin-deep'),
    ]),
    ('02-windows-glazing', 'Windows and glazing', [
        (CC, (300, 660, 620, 900), 'Colossus windscreen: recess, mullions, surround, brow on gussets'),
        (CC, (590, 815, 790, 945), 'Colossus wing ports: recessed, framed, eyebrow over'),
        (CC, (1100, 50, 1720, 260), 'Colossus bolted round ports: dark glass, rim, bolt ring'),
        (LC, (1540, 200, 1760, 380), 'Legion command glazing: set back between plates, few panes lit'),
    ]),
    ('03-hatches-doors', 'Hatches and doors', [
        (CC, (1570, 340, 1780, 530), 'Colossus cargo door: two leaves, stiffeners, hazard frame'),
        (CC, (180, 760, 520, 1000), 'Colossus annex roof: hatch with coaming, skylights, mushroom vents'),
        (CC2, (960, 580, 1920, 1080), 'Colossus keel: round vent hatches with bolt rings, hazard surrounds'),
        (CD, (330, 70, 860, 340), 'Colossus deck: bolted service covers sized to a person'),
    ]),
    ('04-vents-rcs', 'Vents, grilles and RCS', [
        (CD, (240, 620, 520, 840), 'Colossus: fin bank in a recessed well, bolted cover beside it'),
        (CC2, (1280, 40, 1720, 260), 'Colossus pod: louvred grille, RCS quad on the corner, rails'),
        (LC2, (1000, 600, 1880, 860), 'Legion stern: grilles in framed recesses, pipe runs'),
        (LC, (220, 120, 380, 220), 'Legion bow: slatted grille set into the plate'),
    ]),
    ('05-masts-dishes-lamps', 'Masts, dishes and lamps', [
        (CC, (420, 570, 680, 780), 'Colossus: 48-segment ribbed dish on a yoke, sensor ring, dome'),
        (CC, (540, 570, 660, 720), 'Colossus mast: base, pole, yard with braces, whips, lamps'),
        (CC, (740, 900, 900, 1010), 'Colossus: running lamps in guards on the frame'),
        (LC, (1400, 860, 1560, 1010), 'Legion mast: 8 sides, bare lamp: below the bar now'),
    ]),
    ('06-rails-walkways', 'Railings, walkways and ladders', [
        (CC, (160, 580, 420, 780), 'Colossus: 1.1 m rails, stanchions every 1.4 m, knee rail'),
        (CC, (700, 680, 820, 960), 'Colossus: ladder over the shoulder, grab hoops onto the roof'),
        (CC, (170, 780, 580, 1000), 'Colossus: railed lookout deck round the annex roof'),
        (CC2, (1290, 40, 1460, 150), 'Colossus pod: rail round the dorsal housing'),
    ]),
    ('07-bays', 'Bays, jambs and coamings', [
        (LC2, (150, 60, 760, 470), 'Legion lift well: coaming, lining, red light strip, pallets'),
        (LFD, (400, 220, 1720, 920), 'Legion hangar mouth: jambs, header, sill, lit lane'),
        (CC, (0, 160, 140, 470), 'Colossus container bays: cell guides, boxes end-on'),
        (LUQ, (840, 280, 1500, 760), 'Legion underside: bays, wells, hatches in framed recesses'),
    ]),
    ('08-structure-machinery', 'Exposed structure and machinery', [
        (CW, (1400, 620, 1560, 960), 'Colossus: open lattice trusses between the bodies (top view)'),
        (LC2, (0, 580, 960, 1040), 'Legion engine block: machinery bed, trunks, conduits'),
        (CC2, (960, 260, 1500, 540), 'Colossus: tanks in straps, feed lines, skids'),
        (LC2, (960, 580, 1920, 1080), 'Legion stern: drive cans, spine, grilles'),
    ]),
    ('09-turrets', 'Turrets', [
        (CC2, (0, 30, 960, 540), 'Colossus turret: bolted barbette ring, house, trunnion, barrels'),
        (LC, (0, 570, 960, 1080), 'Legion turrets (turret-kit): stepped barbettes, ribbed houses'),
        (CD, (960, 30, 1920, 540), 'Colossus turret trained 40 deg: barrels clear the deck'),
        (LC, (960, 560, 1920, 1080), 'Legion: barbettes seated on the belt'),
    ]),
]


def font(size):
    for name in ('arialbd.ttf', 'Arial Bold.ttf', 'DejaVuSans-Bold.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def plate(drive, title, cells):
    W, H, CW, CH, BAR = 1920, 1080, 960, 540, 34
    out = Image.new('RGB', (W, H), (24, 25, 27))
    d = ImageDraw.Draw(out)
    f, ft = font(20), font(26)
    for k, (src, box, cap) in enumerate(cells):
        im = Image.open(drive/src).convert('RGB').crop(box)
        x0, y0 = (k % 2)*CW, (k//2)*CH
        aw, ah = CW-8, CH-BAR-8
        s = min(aw/im.width, ah/im.height)
        im = im.resize((max(1, round(im.width*s)), max(1, round(im.height*s))), Image.LANCZOS)
        out.paste(im, (x0+(CW-im.width)//2, y0+BAR+(CH-BAR-im.height)//2))
        d.rectangle((x0, y0, x0+CW, y0+BAR), fill=(14, 14, 16))
        d.text((x0+10, y0+6), f'{"ABCD"[k]}  {cap}', fill=(235, 225, 200), font=f)
        if s > 1.01:
            d.text((x0+CW-80, y0+6), f'x{s:.1f}', fill=(150, 150, 150), font=f)
    d.text((W-10-d.textlength(title, font=ft), H-40), title, fill=(255, 255, 255), font=ft)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--drive', default='G:/My Drive/starsector')
    ap.add_argument('--out')
    a = ap.parse_args()
    drive = Path(a.drive)
    out = Path(a.out) if a.out else drive/'review'/'ship-detail-guide'/'2026-10-20_guide-plates_windows'
    out.mkdir(parents=True, exist_ok=True)
    for pid, title, cells in PLATES:
        plate(drive, title, cells).save(out/f'{pid}.png')
        print(out/f'{pid}.png')


if __name__ == '__main__':
    main()
