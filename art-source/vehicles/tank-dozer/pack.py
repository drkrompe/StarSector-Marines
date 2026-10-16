"""Pack mod/graphics/battle/vehicles/army-tank-dozer.png (3072x768).

Slot 0/2: breakthrough hull (live/wreck) re-registered for the 4.6-cell frame,
drawn over the ImageGen blade cut out by the geometry silhouette.
Slot 1/3: APC roof-MG frames copied pixel-exact, padded 576 -> 768.
Run from anywhere: python art-source/vehicles/tank-dozer/pack.py
"""
import numpy as np
from PIL import Image, ImageFilter
from hulls import hull_frame, HERE, REPO, REG, F
import blade_geometry as bg


def blade_layer(index):
    fr = REG['frames'][index]
    raw = Image.open(HERE / fr['underlay']).convert('RGBA').resize((F, F), Image.LANCZOS)
    mask = bg.draw(1, wreck=index == 2).getchannel('A')
    d = REG['maskDilatePx']
    mask = mask.filter(ImageFilter.MaxFilter(2 * d + 1))
    m = np.array(mask) > 0
    m[:, REG['underlayMaxX']:] = False
    a = np.array(raw)
    a[~m] = 0
    return Image.fromarray(a, 'RGBA')


def turret_frame(index):
    fr = REG['frames'][index]
    sheet = Image.open(REPO / fr['sheet']).convert('RGBA')
    w, h = fr['sheetFrameSize']
    crop = sheet.crop((fr['sheetFrame'] * w, 0, fr['sheetFrame'] * w + w, h))
    out = Image.new('RGBA', (F, F), (0, 0, 0, 0))
    out.paste(crop, (0, fr['padTop']))
    return out


def build():
    sheet = Image.new('RGBA', tuple(REG['outputSize']), (0, 0, 0, 0))
    for i in range(4):
        if 'sheet' in REG['frames'][i]:
            frame = turret_frame(i)
        else:
            frame = blade_layer(i)
            frame.alpha_composite(hull_frame(i))
        sheet.paste(frame, (i * F, 0))
    return sheet


if __name__ == '__main__':
    out = REPO / 'mod/graphics/battle/vehicles/army-tank-dozer.png'
    build().save(out)
    print('wrote', out)
