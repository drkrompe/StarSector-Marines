"""Both fleets of the Asharu battle, and where each hull's 3D stand-in comes from.

Plain Python (json, re, sys): imported by the Blender scene and by the plain-Python driver.

A hull is either FINISHED (a published cinematic build, appended at its own metres per unit) or
a STAND-IN: the game sprite's silhouette raised into a lens-shaped slab, painted with the sprite,
at the size ship_scale.py's option C solves for it from the sprite's plan area
(`ship_scale.estimate_units3`). An estimate is about +-8% in length; it is replaced by a
measurement when the hull is built. Nothing here writes a size by hand.

    python roster.py          # the roster table, every hull's size against the finished ships
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROLOGUE = HERE.parent
ART = PROLOGUE.parent
sys.path.insert(0, str(PROLOGUE))
sys.path.insert(0, str(ART))
import ship_scale  # noqa: E402
from starsector_path import GAME_CORE  # noqa: E402

# Finished cinematic builds this scene appends (build/asharu-battle/inputs/, ignored).
#   file and manifest (append collection, root, metres per unit, engine anchors), length m.
#   Drive sources: colossus-cinematic, shepherd builds/5860eae2b, kite-p/a48f8757b,
#   hound-p/33114de, lasher-independent/7805d3b, hound-independent/1e91821,
#   falcon-p/0044413fc, wolf-p/cec776a, wolf/cec776a.
FINISHED = {
    'colossus': dict(file='colossus.blend', manifest='colossus-manifest.json', length_m=108.5),
    'shepherd': dict(file='shepherd.blend', manifest='shepherd-manifest.json', length_m=67.1),
    'lasher': dict(file='lasher-independent.blend', manifest='lasher-independent-manifest.json', length_m=90.0),
    'hound': dict(file='hound-independent-clean.blend', manifest='hound-independent-manifest.json', length_m=60.8),
    'hound-p': dict(file='hound-p-pirate.blend', manifest='hound-p-manifest.json', length_m=60.8),
    'kite-p': dict(file='kite-p.blend', manifest='kite-p-manifest.json', length_m=52.2),
    'falcon-p': dict(file='falcon-p.blend', manifest='falcon-p-manifest.json', length_m=174.4),
    'wolf-p': dict(file='wolf-p.blend', manifest='wolf-p-manifest.json', length_m=74.6),
    'wolf': dict(file='wolf.blend', manifest='wolf-manifest.json', length_m=74.6),
}

# Each fleet member: key (unique in the scene), the game id (hull id, or skin file for a (P)
# skin), its role, and how it is drawn: 'finished' names a FINISHED entry, 'reuse' says which
# finished geometry a later build would start from.
MERCHANT = [
    dict(key='colossus', game='colossus', name='Colossus', role='principal: heavy freighter, the prize',
         finished='colossus'),
    dict(key='morrow', game='shepherd', name='Shepherd "Morrow"', role='drone tender sailing as the convoy repair ship',
         finished='shepherd'),
    dict(key='tarsus', game='tarsus', name='Tarsus', role='freighter: boarded and taken, the pirate prize'),
    dict(key='mule', game='mule', name='Mule', role='armed freighter'),
    dict(key='phaeton', game='phaeton', name='Phaeton', role='the convoy tanker: hit and destroyed in the opening'),
    dict(key='buffalo-a', game='buffalo', name='Buffalo', role='small freighter'),
    dict(key='buffalo-b', game='buffalo', name='Buffalo', role='small freighter'),
    dict(key='dram', game='dram', name='Dram', role='small tanker'),
    dict(key='lasher-lead', game='lasher', name='Lasher', role='escort lead (the Enforcer build was dropped)',
         finished='lasher'),
    dict(key='lasher-a', game='lasher', name='Lasher', role='escort frigate', finished='lasher'),
    dict(key='lasher-b', game='lasher', name='Lasher', role='escort frigate', finished='lasher'),
    dict(key='hound-m', game='hound', name='Hound', role='rear escort, combat freighter', finished='hound'),
    dict(key='wolf-m', game='wolf', name='Wolf', role='point escort frigate', finished='wolf'),
]
PIRATE = [
    dict(key='falcon-p', game='falcon_p.skin', name='Falcon (P)', role='flagship, light cruiser', finished='falcon-p'),
    dict(key='colossus3', game='colossus3', name='Colossus Mk.III', role='prize ship: an armed Colossus come to take the cargo',
         reuse='colossus'),
    dict(key='manticore-p', game='manticore_pirates.skin', name='Manticore (P)', role='long-range destroyer: breaks the screen'),
    dict(key='buffalo2-a', game='buffalo2', name='Buffalo Mk.II', role='destroyer'),
    dict(key='buffalo2-b', game='buffalo2', name='Buffalo Mk.II', role='destroyer'),
    dict(key='wolf-p-a', game='wolf_d_pirates.skin', name='Wolf (P)', role='fast frigate: cuts the column', finished='wolf-p'),
    dict(key='wolf-p-b', game='wolf_d_pirates.skin', name='Wolf (P)', role='fast frigate: cuts the column', finished='wolf-p'),
    dict(key='hound-p-a', game='hound_d_pirates.skin', name='Hound (P)', role='frigate', finished='hound-p'),
    dict(key='hound-p-b', game='hound_d_pirates.skin', name='Hound (P)', role='frigate', finished='hound-p'),
    dict(key='kite-p-a', game='kite_pirates.skin', name='Kite (P)', role='boarding shuttle', finished='kite-p'),
    dict(key='kite-p-b', game='kite_pirates.skin', name='Kite (P)', role='boarding shuttle', finished='kite-p'),
]
# Scale references on the roster sheet: finished ships the owner knows.
REFERENCES = [
    dict(key='ref-hound', game='hound', name='Hound 61 m', finished='hound'),
    dict(key='ref-onslaught', game='onslaught', name='Onslaught 301 m', published_m=301.0),
]


def _json(text):
    text = re.sub(r'#[^\n]*', '', text)
    text = re.sub(r',\s*([}\]])', r'\1', text)
    return json.loads(text)


_SHIPS = None


def _ships():
    global _SHIPS
    if _SHIPS is None:
        _SHIPS = {}
        for f in (GAME_CORE / 'data' / 'hulls').glob('*.ship'):
            try:
                d = _json(f.read_text(encoding='utf-8'))
            except ValueError:
                continue
            _SHIPS[d['hullId']] = d
    return _SHIPS


def _csv():
    import csv
    with open(GAME_CORE / 'data' / 'hulls' / 'ship_data.csv', encoding='utf-8') as fh:
        return {r['id']: r for r in csv.DictReader(fh) if r.get('id')}


def game_spec(game):
    """The game's own data for a hull id or a skin: sprite, centre, engines, size, contents."""
    skin = None
    if game.endswith('.skin'):
        t = (GAME_CORE / 'data' / 'hulls' / 'skins' / game).read_text(encoding='utf-8')
        skin = {k: (re.search(r'"%s"\s*:\s*"([^"]*)"' % k, t) or [None, None])[1]
                for k in ('baseHullId', 'spriteName', 'hullName', 'skinHullId')}
        base = skin['baseHullId']
    else:
        base = game
    ship = _ships()[base]
    row = _csv()[base]
    return dict(base=base, sprite=str(GAME_CORE / ((skin and skin['spriteName']) or ship['spriteName'])),
                centre=ship['center'], hull_size=ship['hullSize'], designation=row['designation'],
                crew=float(row['max crew'] or 0), cargo=float(row['cargo'] or 0), fuel=float(row['fuel'] or 0),
                bays=int(float(row['fighter bays'] or 0)),
                engines=[dict(location=e['location'], angle=e.get('angle', 180), width=e.get('width', 10),
                              style=e.get('style', 'LOW_TECH'))
                         for e in ship.get('engineSlots', ())])


def option_c(spec, alpha_area_px):
    """metres per unit for a stand-in, by ship_scale.py's option C from the sprite's plan area."""
    cls = ship_scale.hull_class(spec['hull_size'], spec['designation'])
    h = dict(units3=ship_scale.estimate_units3(alpha_area_px, cls), crew_max=spec['crew'],
             cargo=spec['cargo'], fuel=spec['fuel'], cls=cls)
    if spec['bays']:
        h['wings'] = [('talon', 2)] * spec['bays']
    return ship_scale.solve(h)['metres_per_unit'], h['units3'], cls


def sprite_stats(path):
    """Plan area and length/beam of a sprite's opaque silhouette, in pixels (Pillow or Blender)."""
    try:
        import numpy as np
        from PIL import Image
        a = np.array(Image.open(path).convert('RGBA'))[..., 3] > 127
    except ImportError:
        import bpy
        import numpy as np
        img = bpy.data.images.load(path, check_existing=True)
        w, h = img.size
        a = (np.array(img.pixels[:], dtype=np.float32).reshape(h, w, 4)[::-1, :, 3] > .5)
    rows = a.any(1).nonzero()[0]
    cols = a.any(0).nonzero()[0]
    return int(a.sum()), int(rows.max() - rows.min() + 1), int(cols.max() - cols.min() + 1)


def size_row(m):
    if m.get('finished'):
        f = FINISHED[m['finished']]
        return dict(length_m=f['length_m'], basis='finished model' + (', pirate tint (pirate state not built)'
                                                                      if m.get('tint') else ''))
    if m.get('published_m'):
        return dict(length_m=m['published_m'], basis='finished model (shown as its sprite slab)')
    spec = game_spec(m['game'])
    area, length_px, beam_px = sprite_stats(spec['sprite'])
    mpu, units3, cls = option_c(spec, area)
    return dict(length_m=round(length_px * mpu, 1), beam_m=round(beam_px * mpu, 1), mpu=round(mpu, 3),
                cls=cls, basis='stand-in (option C from the sprite)')


def table():
    known = (('Hound', 61.0), ('Colossus', 108.5), ('Onslaught', 301.0))
    print(f"{'fleet':9} {'hull':20} {'length':>7}  {'vs Hound':>8} {'vs Colossus':>11} {'vs Onslaught':>12}  basis")
    for fleet, ms in (('merchant', MERCHANT), ('pirate', PIRATE)):
        seen = set()
        for m in ms:
            if m['name'] in seen:
                continue
            seen.add(m['name'])
            r = size_row(m)
            ratios = ' '.join(f"{r['length_m'] / v:{w}.2f}" for (_, v), w in zip(known, (8, 11, 12)))
            print(f"{fleet:9} {m['name']:20} {r['length_m']:6.1f}m  {ratios}  {r['basis']}"
                  + (f" ({m['reuse']} geometry)" if m.get('reuse') else ''))


if __name__ == '__main__':
    table()
