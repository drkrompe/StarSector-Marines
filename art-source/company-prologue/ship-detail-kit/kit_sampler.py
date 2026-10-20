"""Kit sampler: every metric builder of ship_detail_kit on one deck block.

A worked example and a smoke test. It builds a 40 x 24 m deckhouse at the
chosen metres-per-unit and puts one of each Layer 2 fitting on it, so a new
hull can see the call shapes and a change to the kit can be checked by running
it (and check_detail.py over it) before any hull is rebuilt.

  sh ../../blender.sh --background --factory-startup --python kit_sampler.py -- [--mpu 0.5] [--save out.blend]
"""
import argparse
import sys
from pathlib import Path

import bpy

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ship_detail_kit as sdk
from ship_detail_kit import material


class Kit(sdk.BaseKit):
    def __init__(self):
        super().__init__()
        self.plate = material('Sampler plate', (.36, .15, .12), .25, .6)
        self.pale = material('Sampler pale structure', (.56, .53, .51), .2, .55)
        self.steel = material('Sampler gunmetal', (.16, .16, .17), .65)
        self.dark = material('Sampler recess', (.04, .04, .045), .5)
        self.glass = material('Sampler dark glass', (.05, .08, .09), .2, .15)
        self.lit = material('Sampler lit pane', (.05, .08, .09), .2, .15, emission=(.9, .75, .4), strength=3.0)
        self.lens = material('Sampler running lamp lens', (.2, .2, .15), 0, .4, emission=(1.0, .9, .6), strength=6.0)
        self.insulator = material('Sampler insulator', (.5, .45, .35), 0, .5)


def build(mpu):
    for ob in list(bpy.data.objects):
        bpy.data.objects.remove(ob)
    S = sdk.Scale(mpu)
    k = Kit()
    L, W, H = S.u(40), S.u(24), S.u(6)
    house = k.box('Sampler deckhouse', (0, 0, H/2), (L, W, H), k.pale, bevel=S.bevel('hull'))
    k.hull.append(house)
    top = H
    sdk.seam_cut(k, house, 'Sampler deckhouse seams',
                 [((-S.u(.15)+x, -W/2-1, -1), (S.u(.15)+x, W/2+1, top+1)) for x in (-L/4, 0, L/4)], k.dark)
    wall = (L/2, 0, H*.55)
    frame = {'glass': k.glass, 'lit': k.lit, 'frame': k.steel, 'surround': k.pale}
    sdk.window_row(k, S, 'Sampler windscreen', wall, (1, 0, 0), (0, 1, 0), 2.0, 1.6, 5, frame, lit=(1, 3),
                   target=house, groove=k.dark, visor_m=1.6, wipers=True)
    for i, y in enumerate((-W/3, W/3)):
        sdk.round_port(k, S, f'Sampler port {i+1}', (L/4, -W/2 if y < 0 else W/2, H*.5), (0, -1 if y < 0 else 1, 0),
                       .4, frame, target=house, groove=k.dark)
    sdk.wall_door(k, S, 'Sampler aft door', (-L/2, 0, 0), (-1, 0, 0), (0, 1, 0), {'leaf': k.plate, 'frame': k.steel})
    sdk.deck_hatch(k, S, 'Sampler roof hatch', (-L/4, -W/4, top), (1.9, 1.9), {'frame': k.steel, 'lid': k.plate})
    sdk.deck_hatch(k, S, 'Sampler skylight', (-L/4, W/4, top), (1.9, 1.2), {'frame': k.steel, 'glass': k.glass},
                   skylight=True)
    sdk.mushroom_vent(k, S, 'Sampler mushroom vents', [(0, -W/4, top), (S.u(2), -W/4, top)],
                      {'frame': k.steel, 'dark': k.dark})
    sdk.handrail(k, S, 'Sampler roof rail', [(-L/2+S.u(.5), -W/2+S.u(.5)), (L/2-S.u(.5), -W/2+S.u(.5)),
                                             (L/2-S.u(.5), W/2-S.u(.5))], top, k.steel)
    sdk.ladder(k, S, 'Sampler ladder', [(-L/2-S.u(.4), W/3, 0), (-L/2-S.u(.4), W/3, top)], k.steel,
               standoff=((S.u(.4), 0, 0), 1.5))
    sdk.rcs_cluster(k, S, 'Sampler RCS', (L/2-S.u(1.5), W/2-S.u(1.5), top), (0, 1, 0), 1,
                    {'housing': k.plate, 'steel': k.steel, 'lip': k.dark})
    sdk.lamp_fitting(k, S, 'Sampler work lamp', (L/2, -W/2, top), (1, -1, 0), {'housing': k.steel, 'lens': k.lens})
    sdk.mast(k, S, 'Sampler mast', (0, W/4, top), 3.5,
             {'steel': k.steel, 'lens': k.lens, 'insulator': k.insulator})
    sdk.dish(k, S, 'Sampler dish', (L/4, W/5, top), 1.7, {'steel': k.steel, 'dish': k.pale})
    return k


def main():
    argv = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
    ap = argparse.ArgumentParser()
    ap.add_argument('--mpu', type=float, default=.5)
    ap.add_argument('--save')
    a = ap.parse_args(argv)
    build(a.mpu)
    print('SAMPLER objects', len(bpy.data.objects))
    if a.save:
        bpy.ops.wm.save_as_mainfile(filepath=a.save)


main()
