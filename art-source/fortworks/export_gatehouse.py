"""Cut one look's gatehouse sheet (`generate.py <look> --gatehouse`) into its style's gate sprites, at the frames and
size the base gatehouse is cut at (art-source/fort-buildings/export.py), so a look registers with the base art.

  python art-source/fortworks/export_gatehouse.py <look> <style>      e.g. derelict-colony-steel derelict

Outputs mod/graphics/fort/<style>/gate-tower.png, gate-leaves.png, gate-tower-damaged.png: the folder
`FortGatehouseArt.inStyle` draws from for a style named in the gatehouse's `looks`.
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / 'art-source/fort-buildings'))
import export as fort  # noqa: E402
from PIL import Image  # noqa: E402


def main():
    look, style = sys.argv[1], sys.argv[2]
    im = Image.open(HERE / 'raw' / f'gatehouse-{look}.raw.png').convert('RGBA')
    if im.getextrema()[3][0] != 0:
        raise SystemExit(f'{look}: gatehouse master has no real transparency')
    out = ROOT / 'mod/graphics/fort' / style
    out.mkdir(parents=True, exist_ok=True)
    for q, name in ((0, 'gate-tower'), (1, 'gate-leaves'), (2, 'gate-tower-damaged')):
        fort.crop(im, fort.panel_box(q, 2, 3, 150), (2 * fort.PX, 3 * fort.PX)).save(out / f'{name}.png')
    print('wrote', out)


if __name__ == '__main__':
    main()
