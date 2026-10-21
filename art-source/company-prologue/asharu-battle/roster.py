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

# Finished cinematic builds this scene appends. Each build is one Drive folder, copied whole to
# build/asharu-battle/builds/<dir>/ (ignored) by hand or as a render-queue drive input:
#   dir: the local folder; drive: its source under Drive starsector/; file: the .blend in it (the
#   manifest is the folder's manifest.json unless 'manifest' names another); length_m: the settled hull length.
FINISHED = {
    'colossus': dict(dir='colossus', drive='builds/colossus/139e8b8b6', file='colossus.blend', length_m=108.5),
    'shepherd': dict(dir='shepherd', drive='builds/shepherd/c85fb19b6', file='shepherd.blend', length_m=67.1),
    # Morrow's mining drones: always through ShipFx (own_fx), so each Borer flies, blinks and cuts on its own
    'borer': dict(dir='borer', drive='builds/borer/8f950d18e', file='borer.blend', length_m=3.6, own_fx=True),
    'lasher': dict(dir='lasher-independent', drive='builds/lasher-independent/0cb9d83', file='lasher.blend',
                   length_m=90.0),
    'hound': dict(dir='hound-independent', drive='builds/hound-independent/5cb098e10',
                  file='hound-independent-clean.blend', length_m=60.8),
    'hound-p': dict(dir='hound-p', drive='builds/hound-p/5cb098e10', file='hound-p-pirate.blend', length_m=60.8),
    'kite-p': dict(dir='kite-p', drive='builds/kite-p/0cb9d83', file='kite-p.blend', length_m=52.2),
    'falcon-p': dict(dir='falcon-p', drive='builds/falcon-p/06784b46f', file='falcon-p.blend', length_m=174.4),
    'manticore-p': dict(dir='manticore-p', drive='builds/manticore-p/056e05c26', file='manticore-p.blend', length_m=91.3),
    'wolf-p': dict(dir='wolf-p', drive='builds/wolf-p/968fc1a01', file='wolf-p.blend', length_m=70.0),
    'wolf': dict(dir='wolf', drive='builds/wolf/968fc1a01', file='wolf.blend', manifest='manifest-merchant.json',
                 length_m=70.0),
    'colossus-mk3': dict(dir='colossus-mk3', drive='builds/colossus-mk3/005c3b11b', file='colossus-mk3.blend',
                         length_m=109.6),
    'phaeton': dict(dir='phaeton', drive='builds/phaeton/e9ab52297/intact', file='phaeton.blend', length_m=76.6),
    # the wreck carries its own flash, fireball, break-up and fires in its SWFX collection
    'phaeton-wreck': dict(dir='phaeton-destroyed', drive='builds/phaeton/e9ab52297/destroyed',
                          file='phaeton-destroyed.blend', length_m=76.6, swfx=True),
    'tarsus': dict(dir='tarsus', drive='builds/tarsus/2e7d396fc/clean', file='tarsus.blend', length_m=94.0),
    'tarsus-boarded': dict(dir='tarsus-boarded', drive='builds/tarsus/2e7d396fc/boarded', file='tarsus.blend',
                           length_m=94.0),
}

# Each fleet member: key (unique in the scene), the game id (hull id, or skin file for a (P)
# skin), its role, and how it is drawn: 'finished' names a FINISHED entry, 'reuse' says which
# finished geometry a later build would start from.
MERCHANT = [
    dict(key='colossus', game='colossus', name='Colossus', role='principal: heavy freighter, the prize',
         finished='colossus'),
    dict(key='morrow', game='shepherd', name='Shepherd "Morrow"', role='drone tender sailing as the convoy repair ship',
         finished='shepherd'),
    dict(key='tarsus', game='tarsus', name='Tarsus', role='freighter: boarded and taken, the pirate prize', finished='tarsus'),
    dict(key='mule', game='mule', name='Mule', role='armed freighter'),
    dict(key='phaeton', game='phaeton', name='Phaeton', role='the convoy tanker: hit and destroyed in the opening', finished='phaeton'),
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
         finished='colossus-mk3'),
    dict(key='manticore-p', game='manticore_pirates.skin', name='Manticore (P)', role='long-range destroyer: breaks the screen',
         finished='manticore-p'),
    dict(key='buffalo2-a', game='buffalo2', name='Buffalo Mk.II', role='destroyer'),
    dict(key='wolf-p-a', game='wolf_d_pirates.skin', name='Wolf (P)', role='fast frigate: cuts the column', finished='wolf-p'),
    dict(key='wolf-p-b', game='wolf_d_pirates.skin', name='Wolf (P)', role='fast frigate: cuts the column', finished='wolf-p'),
    dict(key='hound-p-a', game='hound_d_pirates.skin', name='Hound (P)', role='frigate', finished='hound-p'),
    dict(key='hound-p-b', game='hound_d_pirates.skin', name='Hound (P)', role='frigate', finished='hound-p'),
    dict(key='hound-p-c', game='hound_d_pirates.skin', name='Hound (P)', role='frigate: broken up by the escorts in S6b',
         finished='hound-p'),     # was a second Buffalo Mk.II: the S6b victim is seen close, and stand-ins stay distant
    dict(key='kite-p-a', game='kite_pirates.skin', name='Kite (P)', role='boarding shuttle', finished='kite-p'),
    dict(key='kite-p-b', game='kite_pirates.skin', name='Kite (P)', role='boarding shuttle', finished='kite-p'),
]
# Scale references on the roster sheet: finished ships the owner knows.
# Morrow's Borers (the Shepherd's built-in borer_wing): small craft placed around their tender, kept off
# the fleet rosters.
DRONES = [
    dict(key=f'borer-{c}', game='drone_borer', name=f'Borer {c.upper()} (Morrow)', role="Morrow's mining drone",
         finished='borer') for c in 'abcd'
]

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
                         for e in ship.get('engineSlots', ())],
                weapons=[dict(location=w['locations'][:2], angle=w.get('angle', 0), arc=w.get('arc', 0),
                              mount=w.get('mount', 'TURRET'), type=w.get('type', ''))
                         for w in ship.get('weaponSlots', ())
                         if w.get('mount') in ('TURRET', 'HARDPOINT') and w.get('type') not in ('DECORATIVE', 'LAUNCH_BAY')])


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
