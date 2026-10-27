"""Progressive battle damage for the Asharu battle cinematic (asharu-damage.md).

The owner asked that ships carry their damage as the battle goes on: a ship hit in S6 still has
those scars, holes, fires and smoke in S6b, S7, S9b, S10 and on, getting worse as the story says.
battle_blockout rebuilds every ship's ShipFx copy for each shot, so whatever one shot keys is gone
in the next. This module is the memory between shots:

- LEDGER: for each ship, every hit it takes, at sequence time (seconds), with a severity 0-4,
  where on the hull (ship frame, metres: +X bow, +Y port, +Z dorsal) and from which side it came.
  Hits come from the shots' `fx` entries (`by` names the entry kind that draws them, `src` the
  shot) and from the story where the shots do not say enough (`by` None).
- apply(): called once per shot build inside Blender, per ship. It builds everything the ship
  took before the shot as persistent state (scorch, holes with smouldering rims, fires and their
  smoke, venting), and keys the hits that land inside the shot at their frames. Where the shot's
  own fx already draw a hit (a tracer's strikes, a blast_on, a fire_on), only the lasting damage
  is added, so nothing is drawn twice.

No orbs (owner, 2026-10-21): flashes are the kit's jagged bursts; persistent damage is scorch,
holes, embers, smoke and vapour. A scar is an irregular decal (ragged noise edge, stretched and
turned per hit), never a disc of light; its embers are specks along the torn edge, not a ring.

Plain Python reads the ledger (`python asharu_damage.py --table | --check | --at T`); the rest
runs in Blender only and imports bpy inside its functions.

    import asharu_damage
    asharu_damage.apply(key, placed[key]['fx'], t_shot0, t_shot1, frame_of,
                        placed[key]['matrix'], log=print, shot_fx=spec.get('fx', ()))
"""
import math
import os
import random
import sys
import zlib
from pathlib import Path

HERE = Path(__file__).resolve().parent
KIT = HERE.parent / 'second-wind-vfx'
for p in (HERE, KIT):
    if str(p) not in sys.path:
        sys.path.insert(0, str(p))

FPS = 24
MATCH_M = 15.0          # a shot's fx entry this close to a ledger hit is the shot drawing that hit
# A hit inside a shot's span exists only if a drawn round, missile or beam causes it in that shot
# (coordinator, 2026-10-24): it links to a drawn impact on her within LINK_S seconds and LINK_M
# metres (a kill, anywhere on her), or the build fails. Fires, blasts and debris are consequences.
LINK_S = .1
LINK_M = 5.0
MAX_FIRES = 2           # persistent fire volumes per ship per shot (volumes are the render's cost)
MAX_VENTS = 1           # persistent vents per ship per shot
MAX_SMOKE = 2           # persistent smoke threads per ship per shot
LOOK = 'v2'             # 'v1': the first look (small soot marks only), kept as the control


# ======================================================================================= levels

# What each severity leaves on the hull. Sizes in metres; *_s in seconds after the hit.
#   scorch   scorch decals around the hit, each cast onto the plating on its own
#   hole     a torn hole (black core, ragged glowing rim) of this size, or 0
#   glow_s   how long the rim's embers take to cool to a dull smoulder
#   smoulder ember level left after the first hot second (0..1)
#   fire     breach fire radius (the kit's flame, smoke and flickering light), or 0
#   vent_s   default seconds of venting vapour (a hit may set its own), or 0
LEVELS = {
    0: dict(name='graze', scorch=(1, .7, 1.2), hole=0.0, glow_s=4.0, smoulder=.0, fire=0.0, vent_s=0.0),
    1: dict(name='scored', scorch=(3, .9, 1.8), hole=0.0, glow_s=12.0, smoulder=.15, fire=0.0, vent_s=0.0),
    2: dict(name='holed', scorch=(4, 1.2, 2.4), hole=2.6, glow_s=40.0, smoulder=.3, fire=0.0, vent_s=14.0),
    3: dict(name='burning', scorch=(5, 1.4, 2.8), hole=3.4, glow_s=90.0, smoulder=.45, fire=2.6, vent_s=0.0),
    4: dict(name='broken', scorch=(7, 1.6, 3.2), hole=4.6, glow_s=1e6, smoulder=.6, fire=3.8, vent_s=30.0),
}

# v2 (coordinator, 2026-10-23: at 5-15% of frame width v1 "barely reads"): what reads at a distance is
# contrast and size, not detail. Scorch patches several times larger, with a lighter fringe of burnt
# paint and primer that catches the sun against the paint; holes larger and black, with a torn
# bare-metal rim; and a thin dark smoke thread from every hit that holed her (smoke_s), so a beaten
# ship trails smoke from where she was hurt. Fire and venting are as v1.
LEVELS_V2 = {
    0: dict(LEVELS[0], scorch=(1, 2.0, 3.0), rake_every=4.0, smoke_s=0.0),
    1: dict(LEVELS[1], scorch=(3, 2.6, 4.2), rake_every=3.5, smoke_s=0.0),
    2: dict(LEVELS[2], scorch=(5, 3.5, 6.0), hole=4.5, rake_every=3.5, smoke_s=45.0),
    3: dict(LEVELS[3], scorch=(6, 4.5, 8.0), hole=6.0, rake_every=3.5, smoke_s=1e6),
    4: dict(LEVELS[4], scorch=(8, 5.5, 10.0), hole=8.0, rake_every=3.5, smoke_s=1e6),
}
LOOKS = {'v1': LEVELS, 'v2': LEVELS_V2}


class Hit:
    """One hit. kind: 'rake' (a burst walking at -> to), 'he' (one heavy round), 'breach' (the hull
    opens), 'debris' (plating torn off), 'cut' (a mining laser's seam at -> to), 'kill'.
    frm: the side the hit came from (outward, ship frame); the hit is cast onto the hull along it.
    by: the shot fx kind that draws its moment ('tracer', 'blast_on', 'fire_on', 'smoke',
    'debris', 'mining'), 'missile' (a drawn missile's own burst is the moment; the hit is placed on
    where it met her), or None for a story hit this module keys itself.
    burn_until: when its fire is out (seconds), None = still burning at the end.
    vent_s: seconds of venting (default the level's).
    cause: the id of an earlier hit on her whose fire, still burning, brings this one on (a cook-off
    the fires reach): it links through that hit's drawn fire instead of a round of its own."""

    def __init__(self, ship, t, kind, sev, at, frm=(0, 0, 1), to=None, by=None, src='story', note='',
                 burn_until=None, vent_s=None, size=None, cause=None):
        self.ship, self.t, self.kind, self.sev = ship, float(t), kind, int(sev)
        self.at, self.frm, self.to = tuple(at), tuple(frm), tuple(to) if to else None
        self.by, self.src, self.note = by, src, note
        self.burn_until, self.size, self.cause = burn_until, size, cause
        self.vent_s = LEVELS[self.sev]['vent_s'] if vent_s is None else vent_s

    @property
    def bys(self):
        return () if self.by is None else (self.by,) if isinstance(self.by, str) else tuple(self.by)

    @property
    def id(self):
        return f'{self.ship}@{self.t:.1f}:{self.kind}'

    def burning(self, t):
        return LEVELS[self.sev]['fire'] > 0 and self.t <= t and (self.burn_until is None or t < self.burn_until)

    def venting(self, t):
        return self.vent_s > 0 and self.t <= t < self.t + self.vent_s

    def __repr__(self):
        return f'Hit({self.id} sev{self.sev})'


def H(*a, **kw):
    return Hit(*a, **kw)


# ======================================================================================= the ledger

# Key times: S6 56 (50-58), S6b 62 (58-66), S6c 72 (66-74), S6d 77 (74-80), S7 86 (80-88),
# S8-S9 95 (88-102), S9b 105 (102-110), S10 114 (110-118), S12 130 (126-134). A shot's fx land
# where scene_fx puts them: blast_on 2 frames before the key, debris 10 frames before, fire_on
# 1.5 s before, smoke 5 s before, a tracer's strikes through the shot.
LEDGER = [
    # ---- WARDEN LEAD, the lead Lasher: hit turning back in S6, smoking into S9b, finished there
    # (her 53.8 s rake is cut: no drawn round or Harpoon reaches her then)
    H('lasher-lead', 55.8, 'he', 2, (18, -2, -3), (.7, .7, 0), by='missile', src='S6 k3 missiles manticore-p',
      note="the first Harpoon of the second salvo holes her bow; she vents"),
    H('lasher-lead', 55.9, 'breach', 3, (15, -8, -9), (.2, .6, 1), by='missile', src='S6 k3 missiles manticore-p',
      note='the hit that turns her: the next Harpoon opens her forward hull and it burns; smoking from here to S9b'),
    H('lasher-lead', 103.71, 'breach', 3, (49, -5, 5), (-.2, .5, 1), by=('tracer', 'fire_on'),
      src='S9b k4b tracer falcon-p + fire_on', note="the Falcon's guns as she rolls in: her forward deck catches"),
    H('lasher-lead', 104.5, 'debris', 2, (53, -3, -4), (0, 1, 0), by=('tracer', 'debris'),
      src='S9b k4b tracer falcon-p + debris', note='the second burst tears plating off her bow'),
    # (her 106.9 s rake is cut: the Falcon's guns no longer bear by then)
    H('lasher-lead', 107.6, 'breach', 4, (8, 4, 6), (0, 0, 1), by=('blast_on', 'debris'),
      cause='lasher-lead@103.7:breach', src='S9b k4b fire from 103.7 s',
      note='the fires spread aft along her deck and reach her magazine: she breaks up and tumbles away burning'),

    # ---- Lasher A: burns in S6, the Manticore (P)'s first Harpoon salvo after the Phaeton's
    H('lasher-a', 53.2, 'breach', 3, (-4, 12, -5), (-.2, .5, 1), by=('missile', 'fire_on'), src='S6 k3 missiles manticore-p',
      note='a Harpoon opens her flank: Lasher A burns'),

    # ---- WARDEN THREE, Lasher B: scored, gets away
    # (her S6 rake from the Falcon is cut: the Falcon's guns draw nothing at that range)
    # (her S6b deck rake from the Buffalo Mk.II is cut with its fire: no hit without a visible shooter)

    # ---- WARDEN FOUR, the merchant Wolf: gets away unhit
    # (her S6c blaster graze is cut: nothing in S6c fires on her, and no hit lands without a visible shooter)

    # ---- WARDEN FIVE, the convoy's Hound: crippled in S6d
    # timed to the second Hound (P)'s drawn rounds: her first burst lands on her from 76.2 to 77.25 s
    H('hound-m', 76.2, 'rake', 1, (8, 6, -2), (0, .6, -1), to=(-6, 7, -2), by='tracer',
      src='S6d k3e tracer hound-p-b', note="the second Hound (P)'s guns from below as she rolls"),
    H('hound-m', 76.5, 'breach', 3, (16, 10, -4), (-.6, .3, .7), by='tracer', src='S6d k3e tracer hound-p-b',
      note="the burst breaks through her belly plating: it burns"),
    H('hound-m', 77.25, 'he', 2, (-24, -3, 1), (-1, 0, .2), by='tracer', src='S6d k3e tracer hound-p-b', vent_s=20.0,
      note='the last of that burst in her drive cluster: she loses thrust, venting (the camera kick at 77.25)'),

    # ---- CARAVEL TWO, the Tarsus: drives shot out in S6c, boarded, taken
    H('tarsus', 68.0, 'rake', 1, (-30, -8, 4), (-.3, -1, .5), to=(-44, -6, 3), by='tracer',
      src='S6c k3d tracer wolf-p-a', note='the Wolves (P) rake aft toward her drives as they pass'),
    H('tarsus', 70.5, 'breach', 3, (-33, -7, 6), (-.2, .5, 1), by='fire_on', src='S6c k3d tracer wolf-p-a + fire_on',
      burn_until=102.0, note='drives shot out: fire burns up out of the breach (continuity), out before S10'),
    H('tarsus', 81.42, 'rake', 1, (36, -4, 9), (.2, -.3, 1), to=(24, 4, 10), by='tracer',
      src='S7 k3b tracer wolf-p-b', note="the covering Wolf (P)'s inverted gun run scores her deck"),

    # ---- CARAVEL, the Colossus: port pod shot out in S8, the other failing, she falls
    H('colossus', 97.67, 'rake', 1, (-74, 39, -19), (0, .5, 1), to=(-58, 30, 6), by='tracer',
      src='S8-S9 k4 tracer colossus3', note="the Mk.III's guns walk onto the burning port pod from above"),
    H('colossus', 95.5, 'he', 4, (-64, 36, -7), (-.4, 1, .2), by=('missile', 'blast_on', 'debris', 'fire_on'),
      src='S8-S9 k4 missiles wolf-p-a + blast_on + debris + fire_on', vent_s=40.0,
      note="the Wolf (P)'s Harpoons the flares did not take shoot out her port tug pod: torn open, its lines "
           "venting, and it burns into the atmosphere"),
    H('colossus', 108.0, 'he', 2, (-62, -30, 0), (-.4, -1, .2), src='story (between S9b and S10)', vent_s=40.0,
      note='the starboard pod failing: an overstrained plate gives, venting'),
    H('colossus', 113.3, 'debris', 3, (-30, 0, 10), (0, 0, 1), by='debris', cause='colossus@95.5:he', src='S10 k5 debris (her port pod fire, burning since 95.5 s)',
      note='her back starts to go: plating shed off the dorsal spine as she noses down; it burns'),

    # ---- MORROW: under the Colossus's keel, untouched before the fall (her 95.5 s scuff is cut: no
    # drawn round reaches her)

    # ---- BARON, the Falcon (P): shield overloaded, flank opened in S9b, still burning in S10
    H('falcon-p', 104.62, 'rake', 1, (40, -30, 6), (0, -.6, 1), to=(0, -34, 4), by='tracer',
      src='S9b k4b tracer lasher-lead', note="the Lasher's guns walk down the open flank after the overload"),
    H('falcon-p', 104.79, 'breach', 3, (14, -5, -5), (-.3, -.6, .7), by=('tracer', 'blast_on'),
      src='S9b k4b tracer lasher-lead + blast_on',
      note='hit hard: the flank opens and burns; still burning in S10 (continuity)'),
    H('falcon-p', 108.04, 'he', 2, (-11, 31, 10), (0, -1, .4), by='tracer', src='S9b k4b tracer lasher-lead', vent_s=20.0,
      note="the Lasher's last rounds hole her further aft, venting"),

    # ---- the Manticore (P): scored by the lead's guns
    # (her S6 rake from the lead is cut: the lead's guns draw nothing at that range)

    # ---- Wolf (P) A: grazed, then cut by a Borer
    H('wolf-p-a', 57.5, 'rake', 0, (10, 6, 3), (0, 1, .2), to=(0, 7, 3), by='tracer',
      src='S6 k3 tracer lasher-b', note="Lasher B's fire"),
    H('wolf-p-a', 71.6, 'he', 1, (-8, 8, 2), (-.3, 1, .3), by='tracer', src='S6c k3d tracer wolf-m',
      note="the merchant Wolf's bolt, past her front shield onto the flank"),
    H('wolf-p-a', 96.9, 'cut', 2, (20, -8, 4), (0, -1, .3), to=(-20, -9, 4), by='mining', vent_s=0.0,
      src='S8-S9 k4 mining borer-c', note="a Borer's mining laser cuts a long glowing seam down her side"),
    H('wolf-p-a', 95.8, 'he', 1, (-15, -9, 3), (0, -1, .3), by='tracer', src='S8-S9 k4 tracer colossus'),

    # ---- Wolf (P) B: a graze through her shield
    H('wolf-p-b', 72.67, 'rake', 0, (8, -8, 3), (0, -1, .2), to=(-4, -8, 3), by='tracer',
      src='S6c k3d tracer colossus', note="the Colossus's guns, mostly on her shield"),

    # ---- Kite (P) B: driven off by the Colossus in S9
    H('kite-p-b', 96.5, 'he', 2, (-10, -10, 3), (0, -1, .4), by='tracer', src='S8-S9 k4 tracer colossus',
      vent_s=20.0, note='leaks through her failing shield (flux .85): she breaks off, venting'),
    H('kite-p-b', 95.85, 'cut', 1, (10, -9, 2), (0, -1, .3), to=(-10, -9, 3), by='mining',
      src='S8-S9 k4 mining borer-d', note="a Borer's laser on her side"),

    # ---- HOOK, the Colossus Mk.III: the Colossus fires up into her belly
    H('colossus3', 96.0, 'rake', 1, (20, 10, -6), (0, .3, -1), to=(0, 12, -6), by='tracer',
      src='S8-S9 k4 tracer colossus', note="the Colossus's turrets score her belly"),
    H('colossus3', 99.5, 'rake', 1, (-10, -6, -7), (0, -.3, -1), to=(-25, -4, -7), by=('tracer', 'mining'),
      src='S8-S9 k4 tracer colossus / mining borer-a, borer-b',
      note='a second burst as she swings her grapples away'),

    # ---- the Hounds (P) the convoy kills
    H('hound-p-c', 61.1, 'rake', 1, (5, 8, 4), (0, .3, 1), to=(-10, 8, 4), by='tracer',
      src='S6b k3c tracer wolf-m', note="bare armour soaks the merchant Wolf's first rounds to land"),
    H('hound-p-c', 61.6, 'rake', 1, (-5, -8, 4), (0, -.3, 1), to=(10, -8, 4), by='tracer',
      src='S6b k3c tracer wolf-m'),
    H('hound-p-c', 62.83, 'kill', 4, (0, 0, 0), (0, 0, 1), by=('blast_on', 'debris'), src='S6b k3c blast_on + debris',
      note="Lasher B's Ammo Feeder burst lands and breaks her up"),
    H('hound-p-a', 75.75, 'rake', 1, (6, 5, 3), (0, .4, 1), to=(-8, 6, 3), by='tracer',
      src='S6d k3e tracer hound-m', note="the convoy Hound's first rounds to reach her"),
    H('hound-p-a', 76.83, 'kill', 4, (0, 0, 0), (0, 0, 1), by=('blast_on', 'debris'), src='S6d k3e blast_on + debris',
      note="killed at close range by the convoy Hound's burst: she cooks off"),
]
# Where the shooter really is (asharu_gunnery, 2026-10-23): the unit vector from the target toward
# the shooter at the hit's time, target frame. A hit is cast onto the face that shooter sees, so a
# hit whose `at` lies on the far side lands on the near face in line with it.
SHOOTER_SIDE = {
    'hound-m@76.2:rake': (0.96, 0.04, -0.27),
    'wolf-p-a@95.8:he': (0.74, 0.61, -0.29),
    'kite-p-b@96.5:he': (-0.81, -0.43, 0.39), 'tarsus@68.0:rake': (-0.09, -0.99, -0.10), 'tarsus@70.5:breach': (0.85, -0.52, -0.07),
    'lasher-lead@103.7:breach': (0.98, -0.17, -0.06), 'falcon-p@104.8:breach': (0.69, 0.55, 0.46),
    'colossus3@96.0:rake': (0.40, -0.91, -0.05), 'hound-m@76.5:breach': (0.94, 0.01, -0.33),
    'falcon-p@104.6:rake': (0.75, 0.51, 0.43), 'wolf-p-b@72.7:rake': (0.39, -0.36, -0.85),
    'hound-p-a@76.8:kill': (0.87, -0.45, 0.19), 'hound-p-a@75.8:rake': (0.99, -0.08, 0.12), 'hound-m@77.2:he': (0.73, -0.22, -0.65),
    'tarsus@81.4:rake': (-0.27, 0.78, 0.57),
    'hound-p-c@61.1:rake': (0.89, 0.14, 0.44),
    'hound-p-c@62.8:kill': (0.71, 0.31, 0.64), 'hound-p-c@61.6:rake': (0.89, 0.13, 0.44),
    'lasher-lead@55.9:breach': (0.72, 0.70, -0.04), 'wolf-p-a@57.5:rake': (0.62, 0.55, -0.56),
    'wolf-p-a@71.6:he': (-0.94, 0.18, 0.30),
}
_orphans = sorted(set(SHOOTER_SIDE) - {_h.id for _h in LEDGER})
if _orphans:        # a retimed hit keeps its id's old time here and would silently lose its side
    raise RuntimeError(f'SHOOTER_SIDE names no ledger hit: {_orphans}')
for _h in LEDGER:
    _h.frm = SHOOTER_SIDE.get(_h.id, _h.frm)
LEDGER.sort(key=lambda h: (h.ship, h.t))

# Ships the ledger leaves clean on purpose: the Phaeton (her wreck state carries its own cook-off),
# the stand-ins (no kit copy to mark), the boats and drones, and the raiders nobody hurts.
CLEAN = ('phaeton', 'mule', 'buffalo-a', 'buffalo-b', 'dram', 'buffalo2-a', 'kite-p-a', 'hound-p-b',
         'hermes-p-a', 'hermes-p-b', 'hermes-p-c', 'borer-a', 'borer-b', 'borer-c', 'borer-d')


# Shot fx on a ship that are not damage (none now: Morrow's Borers fight the attackers, they do not
# cut at their own ship; owner, 2026-10-23).
# fx entries on a ship that land no hit in their shot: S6's Falcon on Lasher B and the lead on the
# Manticore fire at a range their rounds do not close in the shot, so nothing of theirs lands
NOT_DAMAGE = {('tracer', 'falcon-p', 'lasher-b'), ('tracer', 'lasher-lead', 'manticore-p')}


def hits(ship, until=None, since=None):
    """The ship's hits, in time order: those at or before `until`, after `since`."""
    return [h for h in LEDGER if h.ship == ship and (until is None or h.t <= until) and (since is None or h.t > since)]


def killed_at(ship):
    return next((h.t for h in LEDGER if h.ship == ship and h.kind == 'kill'), None)


def state(ship, t):
    """What the ship carries at sequence time t: its worst level, fires burning, vents venting."""
    hs = hits(ship, until=t)
    return dict(level=max((h.sev for h in hs), default=None), hits=len(hs),
                fires=[h.id for h in hs if h.burning(t)], vents=[h.id for h in hs if h.venting(t)],
                killed=killed_at(ship) is not None and killed_at(ship) <= t)


# ======================================================================================= shot fx matching

def _near(a, b, d=MATCH_M):
    return math.dist(tuple(a), tuple(b)) <= d


def _fx_on(shot_fx, ship):
    """The shot's fx entries that land on `ship`: (kind, at or None)."""
    out = []
    for fx in shot_fx or ():
        k = fx[0]
        if k in ('blast_on', 'debris') and fx[1] == ship:
            out.append((k, fx[3] if len(fx) > 3 else (0, 0, 0)))
        elif k in ('fire_on', 'smoke') and fx[1] == ship:
            out.append((k, fx[2]))
        elif k in ('tracer', 'mining') and len(fx) > 2 and fx[2] == ship:
            out.append((k, None))
    return out


def drawn(h, on):
    """Whether the shot's own fx draw this hit's moment (flash, strikes, blast)."""
    return any(k in h.bys and (at is None or _near(at, h.at)) for k, at in on)


def fire_drawn(h, on):
    """Whether the shot already draws a fire where this hit burns (a fire_on or smoke near it)."""
    return any(k in ('fire_on', 'smoke') and at is not None and _near(at, h.at) for k, at in on)


# ======================================================================================= Blender

def _cast(scene, matrix, ship_fx, p_m, frm, standoff=70.0):
    """Where a hit at p_m coming from direction frm meets this ship's plating: (at_m, normal_m, True) in
    the ship's frame, or (the authored point, frm, False) when nothing is found. Casts from `standoff`
    metres out along frm, accepting only this ship's hull objects (a neighbour's hull, or any kit volume,
    is passed through). A miss means the point is off her hull: callers place nothing there (S11's
    floating discs were a rake's scars left at authored points past her side)."""
    import bpy
    from mathutils import Vector
    import ship_fx as sfx_mod
    hull = getattr(ship_fx, 'hull', None)
    mine = {sfx_mod.base_name(o.name) for o in hull.all_objects} if hull is not None else None
    d_l = Vector(frm).normalized()
    dg = bpy.context.evaluated_depsgraph_get()
    o = matrix @ (Vector(p_m) + d_l * standoff)
    d = (matrix.to_3x3() @ -d_l).normalized()
    far = standoff * 2.2
    gone = 0.0
    inv = matrix.inverted()
    for _ in range(48):
        ok, loc, nrm, _i, ob, _m = scene.ray_cast(dg, o, d, distance=far - gone)
        if not ok:
            break
        gone += (loc - o).length
        name = sfx_mod.base_name(ob.name) if ob is not None else ''
        if name.startswith('SWFX') or (mine is not None and name not in mine):
            o = loc + d * .05
            continue
        n = (inv.to_3x3() @ nrm).normalized()
        if n.dot(d_l) < 0:
            n = -n
        return tuple(inv @ loc), tuple(n), True
    return tuple(p_m), tuple(d_l), False


def _turned(normal, spin):
    """A rotation whose +Z is the normal, turned `spin` radians about it."""
    from mathutils import Matrix, Vector
    z = Vector(normal).normalized()
    return z.to_track_quat('Z', 'X').to_matrix().to_4x4() @ Matrix.Rotation(spin, 4, 'Z')


def _decal_mesh(name, seed, sides=22):
    """A flat polygon of radius 1 in XY with a slightly uneven rim (the material does the tearing)."""
    import bpy
    rnd = random.Random(seed)
    verts = []
    for i in range(sides):
        a = 2 * math.pi * i / sides
        r = 1.0 + rnd.uniform(-.06, .06)
        verts.append((r * math.cos(a), r * math.sin(a), 0.0))
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], [list(range(sides))])
    me.update()
    return me


def _scar_material(ctx, name, owner, seed, hole, glow_s, smoulder, gain):
    """A scar's look, timed by owner['imp_frame']: a torn black core (hole) inside a ragged soot mark,
    hot for its first second, then embers in specks along the torn edge cooling over glow_s to a
    smoulder that flickers. Object coordinates, so the shape stretches and turns with the decal."""
    from swfx_core import node_material, value_node
    from swfx_smoke import G
    m, nodes, links = node_material(name)
    g = G(m.node_tree)
    extra = {'t0': (owner, '["imp_frame"]')}
    age = f'((frame-t0)/{FPS})'
    on = value_node(ctx, nodes, 'on', f'({age}>=0)', extra=extra)
    fresh = value_node(ctx, nodes, 'fresh', f'({age}>=0)*exp(-max({age},0)/.7)', extra=extra)
    ember = value_node(ctx, nodes, 'ember', f'({age}>=0)*max(exp(-max({age},0)/1.5),'
                                            f'{smoulder:.3f}*exp(-max({age},0)/{glow_s:.1f}))', extra=extra)
    clock = value_node(ctx, nodes, 'clock', f'frame/{FPS}')
    co = g.node('ShaderNodeTexCoord').outputs['Object']
    r = g.v('LENGTH', co)
    n1 = g.noise(co, float(seed % 97), 2.2, detail=4.0, rough=.62, distortion=.3)
    rr = g.m('MULTIPLY_ADD', g.m('SUBTRACT', n1, .5), .9, r)              # ragged, lobed edge
    streak = g.noise(g.v('MULTIPLY', co, (.45, 2.2, 1.0)), float(seed % 89) + .3, 1.6, detail=3.0, rough=.6)
    mark = g.m('MULTIPLY', g.m('SUBTRACT', 1.0, g.mr(rr, .35, .95, smooth=True)), g.mr(streak, .22, .55))
    core = g.m('SUBTRACT', 1.0, g.mr(rr, .14, .3, smooth=True)) if hole else None
    # embers: specks in the torn band around the core (or through the scorch on a scar with no hole)
    speck = g.noise(co, g.m('MULTIPLY_ADD', clock, .9, float(seed % 83)), 6.5, detail=2.0, rough=.5)
    lo, hi = (.22, .48) if hole else (.0, .42)
    band = g.m('MULTIPLY', g.mr(rr, lo - .08, lo, smooth=True), g.m('SUBTRACT', 1.0, g.mr(rr, hi, hi + .14, smooth=True)))
    specks = g.m('MULTIPLY', band, g.mr(speck, .56, .7, smooth=True))
    hot = g.m('MULTIPLY', g.m('SUBTRACT', 1.0, g.mr(rr, .05, .5, smooth=True)), fresh)
    glow = g.m('ADD', g.m('MULTIPLY', specks, ember), g.m('MULTIPLY', hot, 1.6))
    temp = g.m('MULTIPLY_ADD', g.m('ADD', g.m('MULTIPLY', ember, .5), g.m('MULTIPLY', fresh, .8)), 1300.0, 780.0)
    bb = g.node('ShaderNodeBlackbody')
    g._in(bb.inputs['Temperature'], temp)
    em = g.node('ShaderNodeEmission')
    g.links.new(bb.outputs[0], em.inputs['Color'])
    g._in(em.inputs['Strength'], g.m('MULTIPLY', g.m('MULTIPLY', glow, on), gain))
    soot = g.node('ShaderNodeBsdfDiffuse')
    soot.inputs['Color'].default_value = (.014, .012, .011, 1)
    alpha = g.m('MULTIPLY', g.m('MINIMUM', g.m('ADD', mark, core) if hole else mark, 1.0), on)
    if hole:     # the core reads as a hole: black, no sheen
        black = g.node('ShaderNodeEmission')
        black.inputs['Color'].default_value = (0, 0, 0, 1)
        dark = g.node('ShaderNodeMixShader')
        g._in(dark.inputs[0], core)
        links.new(soot.outputs[0], dark.inputs[1])
        links.new(black.outputs[0], dark.inputs[2])
        body = dark.outputs[0]
    else:
        body = soot.outputs[0]
    tr = g.node('ShaderNodeBsdfTransparent')
    mix = g.node('ShaderNodeMixShader')
    g._in(mix.inputs[0], g.m('MULTIPLY', alpha, .94))
    links.new(tr.outputs[0], mix.inputs[1])
    links.new(body, mix.inputs[2])
    add = g.node('ShaderNodeAddShader')
    links.new(mix.outputs[0], add.inputs[0])
    links.new(em.outputs[0], add.inputs[1])
    out = g.node('ShaderNodeOutputMaterial')
    links.new(add.outputs[0], out.inputs['Surface'])
    return m


def _scar_material_v2(ctx, name, owner, seed, hole, glow_s, smoulder, gain):
    """v2's scar, made to read at a distance. A black soot core ringed by a lighter fringe of burnt
    paint and primer (rust-brown and grey, diffuse, so it catches the sun against the paint), patchy and
    streaked. A
    hole is a black core ringed by torn bare-metal petals. The embers along the torn edge are v1's.
    Timed by owner['imp_frame']."""
    from swfx_core import node_material, value_node
    from swfx_smoke import G
    m, nodes, links = node_material(name)
    g = G(m.node_tree)
    extra = {'t0': (owner, '["imp_frame"]')}
    age = f'((frame-t0)/{FPS})'
    on = value_node(ctx, nodes, 'on', f'({age}>=0)', extra=extra)
    fresh = value_node(ctx, nodes, 'fresh', f'({age}>=0)*exp(-max({age},0)/.7)', extra=extra)
    ember = value_node(ctx, nodes, 'ember', f'({age}>=0)*max(exp(-max({age},0)/1.5),'
                                            f'{smoulder:.3f}*exp(-max({age},0)/{glow_s:.1f}))', extra=extra)
    clock = value_node(ctx, nodes, 'clock', f'frame/{FPS}')
    co = g.node('ShaderNodeTexCoord').outputs['Object']
    r = g.v('LENGTH', co)
    n1 = g.noise(co, float(seed % 97), 2.0, detail=4.0, rough=.62, distortion=.35)
    rr = g.m('MULTIPLY_ADD', g.m('SUBTRACT', n1, .5), .8, r)              # ragged, lobed edge
    streak = g.noise(g.v('MULTIPLY', co, (.4, 2.4, 1.0)), float(seed % 89) + .3, 1.5, detail=3.0, rough=.6)
    blotch = g.noise(co, float(seed % 79) + .7, 4.0, detail=2.0, rough=.5)
    soot = g.m('MULTIPLY', g.m('SUBTRACT', 1.0, g.mr(rr, .38, .62, smooth=True)), g.mr(streak, .2, .45))
    tint = g.m('MULTIPLY', g.m('MULTIPLY', g.mr(rr, .3, .5, smooth=True),
                               g.m('SUBTRACT', 1.0, g.mr(rr, .78, .97, smooth=True))), g.mr(blotch, .3, .55))
    sootb = g.node('ShaderNodeBsdfDiffuse')
    sootb.inputs['Color'].default_value = (.01, .009, .008, 1)
    tintb = g.node('ShaderNodeBsdfPrincipled')
    # burnt paint and primer: diffuse and light, so it reads under the sun (a metallic fringe only
    # mirrors the black sky)
    g._in(tintb.inputs['Base Color'], g.mix(blotch, (.42, .27, .14, 1), (.3, .29, .27, 1)))
    tintb.inputs['Metallic'].default_value = .15
    tintb.inputs['Roughness'].default_value = .7
    surf = g.node('ShaderNodeMixShader')
    g._in(surf.inputs[0], g.m('DIVIDE', soot, g.m('MAXIMUM', g.m('ADD', soot, tint), .001)))
    links.new(tintb.outputs[0], surf.inputs[1])
    links.new(sootb.outputs[0], surf.inputs[2])
    body, alpha = surf.outputs[0], g.m('MINIMUM', g.m('ADD', soot, tint), 1.0)
    if hole:
        core = g.m('SUBTRACT', 1.0, g.mr(rr, .14, .2, smooth=True))
        petal = g.noise(co, float(seed % 71) + .2, 7.0, detail=2.0, rough=.5)
        rim = g.m('MULTIPLY', g.m('MULTIPLY', g.mr(rr, .14, .19), g.m('SUBTRACT', 1.0, g.mr(rr, .26, .34))),
                  g.mr(petal, .35, .5))
        metal = g.node('ShaderNodeBsdfPrincipled')
        metal.inputs['Base Color'].default_value = (.75, .73, .7, 1)
        metal.inputs['Metallic'].default_value = .35
        metal.inputs['Roughness'].default_value = .5
        torn = g.node('ShaderNodeMixShader')
        g._in(torn.inputs[0], rim)
        links.new(body, torn.inputs[1])
        links.new(metal.outputs[0], torn.inputs[2])
        black = g.node('ShaderNodeEmission')
        black.inputs['Color'].default_value = (0, 0, 0, 1)
        dark = g.node('ShaderNodeMixShader')
        g._in(dark.inputs[0], core)
        links.new(torn.outputs[0], dark.inputs[1])
        links.new(black.outputs[0], dark.inputs[2])
        body = dark.outputs[0]
        alpha = g.m('MINIMUM', g.m('ADD', g.m('ADD', alpha, core), rim), 1.0)
    # embers: specks in the torn band (round the core, or through the soot on a scar with no hole)
    speck = g.noise(co, g.m('MULTIPLY_ADD', clock, .9, float(seed % 83)), 6.5, detail=2.0, rough=.5)
    lo, hi = (.18, .36) if hole else (.0, .4)
    band = g.m('MULTIPLY', g.mr(rr, lo - .06, lo, smooth=True),
               g.m('SUBTRACT', 1.0, g.mr(rr, hi, hi + .12, smooth=True)))
    specks = g.m('MULTIPLY', band, g.mr(speck, .56, .7, smooth=True))
    hot = g.m('MULTIPLY', g.m('SUBTRACT', 1.0, g.mr(rr, .05, .4, smooth=True)), fresh)
    glow = g.m('ADD', g.m('MULTIPLY', specks, ember), g.m('MULTIPLY', hot, 1.6))
    temp = g.m('MULTIPLY_ADD', g.m('ADD', g.m('MULTIPLY', ember, .5), g.m('MULTIPLY', fresh, .8)), 1300.0, 780.0)
    bb = g.node('ShaderNodeBlackbody')
    g._in(bb.inputs['Temperature'], temp)
    em = g.node('ShaderNodeEmission')
    g.links.new(bb.outputs[0], em.inputs['Color'])
    g._in(em.inputs['Strength'], g.m('MULTIPLY', g.m('MULTIPLY', glow, on), gain))
    tr = g.node('ShaderNodeBsdfTransparent')
    mix = g.node('ShaderNodeMixShader')
    g._in(mix.inputs[0], g.m('MULTIPLY', alpha, on))
    links.new(tr.outputs[0], mix.inputs[1])
    links.new(body, mix.inputs[2])
    add = g.node('ShaderNodeAddShader')
    links.new(mix.outputs[0], add.inputs[0])
    links.new(em.outputs[0], add.inputs[1])
    out = g.node('ShaderNodeOutputMaterial')
    links.new(add.outputs[0], out.inputs['Surface'])
    return m


def _smoke_thread(ctx, coll, axis, owner, seed, length_m, mouth_m):
    """A thin, torn thread of dark smoke streaming along axis +X from a hole. It comes up over a second
    from owner['imp_frame'] and drifts outward. In vacuum it neither rises nor billows; it streams and
    thins. A volume in a box, like the kit's vent."""
    import swfx_core
    from swfx_core import node_material, units, value_node
    from swfx_smoke import G, box_mesh, volume_output
    L = units(length_m)
    m, nodes, links = node_material(f'SWFX damage smoke {seed}')
    g = G(m.node_tree)
    co = g.node('ShaderNodeTexCoord').outputs['Object']
    x, y, z = g.sep(co)
    t = value_node(ctx, nodes, 't', f'frame/{FPS}')
    amount = value_node(ctx, nodes, 'amount', f'min(max((frame-t0)/{FPS},0),1)',
                        extra={'t0': (owner, '["imp_frame"]')})
    u = g.m('DIVIDE', x, L)
    rad = g.m('MULTIPLY_ADD', u, L * .14, units(mouth_m))
    r = g.m('DIVIDE', g.v('LENGTH', g.comb(0.0, y, z)), rad)
    along = g.m('MULTIPLY', g.mr(u, -.02, .04), g.m('SUBTRACT', 1.0, g.mr(u, .3, 1.0)))
    flow = g.comb(g.m('SUBTRACT', x, g.m('MULTIPLY', t, units(7.0))), y, z)
    n = g.noise(flow, g.m('MULTIPLY_ADD', t, .25, float(seed % 97)), 4.0 / L, detail=5.0, rough=.62, distortion=.3)
    n2 = g.noise(flow, float(seed % 61), 1.6 / L, detail=2.0, rough=.5)
    shape = g.m('MULTIPLY', g.m('MULTIPLY', g.gauss(r, 1.0, 1.5), along),
                g.m('MULTIPLY', g.mr(n, .38, .7), g.mr(n2, .3, .6)))
    dens = g.m('MULTIPLY', g.m('MULTIPLY', shape, 1.1 / units(1.0)), amount)
    volume_output(g, dens, (0.06, 0.055, 0.05, 1), (0.55, 0.55, 0.57, 1), .3)
    half = L * .3
    ob = box_mesh(f'SWFX damage smoke | {owner.name}', coll, (-units(.5), -half, -half), (L, half, half), m)
    swfx_core.attach(ob, axis)
    return ob


class _Builder:
    """One ship's damage for one shot build, inside its kit() block."""

    def __init__(self, ctx, ship_key, ship_fx, matrix, frame_of, log, look=LOOK):
        import swfx_core
        import swfx_impacts
        self.ctx, self.key, self.ship, self.matrix = ctx, ship_key, ship_fx, matrix
        self.frame_of, self.log = frame_of, log
        self.core, self.imp = swfx_core, swfx_impacts
        self.mats = {}
        self.look, self.lv = look, LOOKS[look]
        self.made = dict(scars=0, holes=0, fires=0, vents=0, smoke=0, keyed=0, off_hull=0)

    def seed(self, h, salt=''):
        return zlib.crc32(f'asharu-damage|{h.id}|{salt}'.encode()) % 100003

    def frame(self, t):
        return float(self.frame_of(t))

    def empty(self, h, frame, at, normal, kind='damage', **props):
        e, _ = self.imp.anchor(self.ctx, kind, frame, at, normal, **props)
        return e

    def _sites(self, h):
        """(point, scale, delay_s) of every scar decal the hit leaves, before casting."""
        from mathutils import Vector
        lv = self.lv[h.sev]
        rnd = random.Random(self.seed(h, 'sites'))
        count, s0, s1 = lv['scorch']
        at = Vector(h.at)
        if h.kind in ('rake', 'cut') and h.to:
            to = Vector(h.to)
            n = max(2, int((to - at).length / (lv.get('rake_every', 3.0) if h.kind == 'rake' else 1.6)) + 1)
            over = .4 if h.kind == 'rake' else 1.5
            out = []
            for i in range(n):
                u = i / (n - 1)
                j = Vector((rnd.gauss(0, .7), rnd.gauss(0, .7), rnd.gauss(0, .3))) if h.kind == 'rake' else Vector()
                out.append((at.lerp(to, u) + j, rnd.uniform(s0, s1) * (.8 if h.kind == 'cut' else 1.0), u * over))
            return out
        spread = (h.size or lv['hole'] or s1) * 1.1
        out = [(at, rnd.uniform(s0, s1) * 1.2, 0.0)]
        for i in range(count - 1):
            a = rnd.uniform(0, 2 * math.pi)
            d = spread * rnd.uniform(.5, 1.25)
            out.append((at + Vector((math.cos(a) * d, math.sin(a) * d, rnd.uniform(-.3, .3))), rnd.uniform(s0, s1), 0.0))
        return out

    def scars(self, h, frame):
        """The hit's scorch decals (and its hole), each cast onto the plating, appearing at `frame`
        (a cut's or a rake's walk along their line). A site whose cast misses her plating is skipped.
        Returns the hit point and normal on the hull, and whether the hit itself met her plating."""
        from mathutils import Matrix, Vector
        import bpy
        from swfx_smoke import _no_shadow_catch
        lv = self.lv[h.sev]
        units = self.core.units
        coll = self.ctx.collection(self.imp.GROUP)
        rnd = random.Random(self.seed(h, 'decals'))
        hit_at, hit_n, on_hull = _cast(self.ctx.scene, self.matrix, self.ship, h.at, h.frm)
        sites = self._sites(h)
        if lv['hole'] > 0 and h.kind not in ('rake', 'cut') and on_hull:
            sites.insert(0, (Vector(hit_at), (h.size or lv['hole']), 0.0))
        line = Vector(h.to) - Vector(h.at) if h.to else None
        for i, (p, size, delay) in enumerate(sites):
            hole = i == 0 and lv['hole'] > 0 and h.kind not in ('rake', 'cut') and on_hull
            at, n, ok = (tuple(p), hit_n, True) if hole else _cast(self.ctx.scene, self.matrix, self.ship, tuple(p), h.frm)
            if not ok:
                self.made['off_hull'] += 1
                continue
            f = frame + round(delay * FPS)
            e = self.empty(h, f, at, n, 'scar')
            if h.kind == 'cut' and line is not None:      # a seam: long along the cut, narrow across it
                ln = Vector(n).normalized()
                along = (line - ln * line.dot(ln))
                spin = math.atan2(along.dot(_turned(n, 0).col[1].xyz), along.dot(_turned(n, 0).col[0].xyz))
                aspect = 2.8
            else:
                spin, aspect = rnd.uniform(0, 2 * math.pi), rnd.uniform(1.0, 1.9)
            key = (f, hole)
            if key not in self.mats:
                make = _scar_material_v2 if self.look == 'v2' else _scar_material
                self.mats[key] = make(self.ctx, f'SWFX damage scar {self.seed(h, key)}', e,
                                                self.seed(h, key), hole, lv['glow_s'] if h.kind != 'cut' else 25.0,
                                                lv['smoulder'] if h.kind != 'cut' else .25,
                                                9.0 if hole else (6.0 if h.kind == 'cut' else 5.0))
            me = _decal_mesh(f'SWFX damage scar | {e.name}', self.seed(h, i))
            me.materials.append(self.mats[key])
            ob = bpy.data.objects.new(me.name, me)
            coll.objects.link(ob)
            ob.parent = e
            ob.matrix_parent_inverse = Matrix.Identity(4)
            ob.matrix_basis = (Matrix.Rotation(spin, 4, 'Z') @
                               Matrix.Translation((0, 0, units(.05))) @
                               Matrix.Diagonal((units(size * .5 * aspect), units(size * .5), 1.0, 1.0)))
            ob.visible_shadow = False
            _no_shadow_catch(ob)
            ob.hide_render = False
            self.made['holes' if hole else 'scars'] += 1
        return hit_at, hit_n, on_hull

    def fire(self, h, frame, at, n, ignite_s=0.0):
        """The hit's fire: the kit's breach flame, its smoke and flickering light, timed by its own empty."""
        import bpy
        import swfx_fire
        size = LEVELS[h.sev]['fire']
        e = self.empty(h, frame, at, n, 'damage fire', size_m=size, imp_ignite=ignite_s,
                       seed=self.seed(h, 'fire') % 997)
        e['flame'] = 'stream'       # tongues off the breach edge, never a ball (owner, 2026-10-23: no orbs or blobs)
        swfx_fire._knobs(self.ctx)
        rec = swfx_fire.build_fire(self.ctx, self.ctx.collection(self.imp.GROUP), e)
        self.imp._retime([bpy.data.objects[o] for o in rec['objects']], e)
        self.made['fires'] += 1

    def vent(self, h, frame, at, n, ignite_s=0.0):
        """Pale vapour venting out of the hole along its normal (the kit's vent), timed by its own empty."""
        import bpy
        import swfx_fire
        e = self.empty(h, frame, at, n, 'damage vent', imp_ignite=ignite_s)
        coll = self.ctx.collection(self.imp.GROUP)
        v = self.core.empty(f'{self.imp.PREFIX} damage vent axis {self.seed(h, "vent")}', coll,
                            size=self.core.units(.5))
        self.core.attach(v, e, (0, 0, self.core.units(.2)), (0, -math.pi / 2, 0))     # +X along the normal
        v['length_m'] = 10.0 + 4.0 * h.sev
        v['seed'] = float(self.seed(h, 'vent') % 997)
        v.hide_render = True
        swfx_fire._knobs(self.ctx)
        rec = swfx_fire.build_vent(self.ctx, coll, v)
        self.imp._retime([bpy.data.objects[o] for o in rec['objects']], e)
        self.made['vents'] += 1

    def smoke(self, h, frame, at, n):
        """A thin dark smoke thread streaming out of the hole along its normal, from the hit on."""
        e = self.empty(h, frame, at, n, 'damage smoke')
        coll = self.ctx.collection(self.imp.GROUP)
        v = self.core.empty(f'{self.imp.PREFIX} damage smoke axis {self.seed(h, "smoke")}', coll,
                            size=self.core.units(.5))
        self.core.attach(v, e, (0, 0, self.core.units(.3)), (0, -math.pi / 2, 0))     # +X along the normal
        v.hide_render = True
        _smoke_thread(self.ctx, coll, v, e, self.seed(h, 'smoke'), 22.0 + 8.0 * h.sev, .8 + .25 * h.sev)
        self.made['smoke'] += 1

    def moment(self, h, frame, at, n):
        """The hit's moment, for a hit the shot does not draw: the kit's own impacts (through the shield
        where one is up), a breach's blast, debris for a ship coming apart."""
        imp = self.imp
        if h.kind in ('rake', 'cut'):
            if h.kind == 'cut':      # a laser seam has no rounds; its decals walk the line
                return
            for k, (p, _s, delay) in enumerate(self._sites(h)):
                at_k, n_k, ok = _cast(self.ctx.scene, self.matrix, self.ship, tuple(p), h.frm)
                if not ok:
                    self.made['off_hull'] += 1
                    continue
                imp.strike(self.ctx, frame + round(delay * FPS), at_k, n_k, kind='kinetic')
                self.made['keyed'] += 1
            return
        if h.kind == 'he':
            imp.strike(self.ctx, frame, at, n, kind='he', size_m=1.2 + .5 * h.sev)
        elif h.kind in ('breach', 'kill'):
            imp.he(self.ctx, frame, at, n, size_m=2.0 + .8 * h.sev)
        if h.kind in ('debris', 'kill') or h.sev >= 4:
            imp.debris(self.ctx, frame, at, n, count=20 + 12 * h.sev, sparks=60 + 20 * h.sev)
        self.made['keyed'] += 1


def _seg_dist(p, a, b):
    """Metres from point p to the segment a-b (a point when b is None)."""
    from mathutils import Vector
    p, a = Vector(p), Vector(a)
    if b is None:
        return (p - a).length
    b = Vector(b)
    ab = b - a
    u = max(0.0, min(1.0, (p - a).dot(ab) / max(ab.length_squared, 1e-9)))
    return (p - (a + ab * u)).length


def drawn_impacts(h, frame, impacts, fps=FPS):
    """The drawn impacts that may cause hit h landing on `frame`: [(metres off her line, impact)],
    nearest first. impacts: [(ship, first frame, last frame, (x, y, z) her frame metres, what)]."""
    tol = LINK_S * fps + 1e-6
    out = [(0.0 if h.kind == 'kill' else _seg_dist(im[3], h.at, h.to), im) for im in impacts
           if im[0] == h.ship and im[1] - tol <= frame <= im[2] + tol]
    return sorted(out, key=lambda x: x[0])


def link_check(events, impacts, fps=FPS):
    """Every in-shot hit (apply's events) against the drawn impacts: (rows, fails)."""
    rows, fails = [], []
    for ev in events:
        h, f = ev['hit'], ev['frame']
        if h.cause is not None:      # brought on by her own fire: that hit must be drawn and still burning
            c = next((x for x in LEDGER if x.id == h.cause), None)
            if c is not None and c.ship == h.ship and c.t < h.t and c.burning(h.t):
                rows.append(f'link {h.id}: the fire of {c.id}, burning since {c.t:g} s')
            else:
                fails.append(f'{h.id} ({h.src}): its cause {h.cause} is no fire burning on her then')
            continue
        got = [x for x in drawn_impacts(h, f, impacts, fps) if x[0] <= LINK_M]
        if got:
            rows.append(f'link {h.id}: {got[0][1][4]} at {got[0][0]:.1f} m')
        else:
            near = drawn_impacts(h, f, impacts, fps)
            mine = sorted((min(abs(im[1] - f), abs(im[2] - f)), im) for im in impacts if im[0] == h.ship)
            why = (f'nearest drawn impact in time is {near[0][0]:.1f} m off ({near[0][1][4]} at ' + '({:.0f}, {:.0f}, {:.0f}))'.format(*near[0][1][3]) if near
                   else f'no drawn impact on her within {LINK_S:g} s; nearest on her '
                        + (f'{mine[0][0] / fps:.2f} s off at {mine[0][1][1] / fps:+.2f} s from the key ({mine[0][1][4]})'
                           if mine else 'none in the shot'))
            if os.environ.get('ASHARU_LINK_LIST'):     # every drawn impact on her, to retime a hit onto
                why += '; on her: ' + ', '.join(f'{im[1] / fps:+.2f} s ' + '({:.0f}, {:.0f}, {:.0f})'.format(*im[3])
                                                for im in sorted(impacts, key=lambda im: im[1]) if im[0] == h.ship)
            fails.append(f'{h.id} ({h.src}): {why}')
    return rows, fails


def apply(ship_key, ship_fx, t_shot0, t_shot1, frame_of, matrix=None, log=print, shot_fx=None,
          max_fires=MAX_FIRES, max_vents=MAX_VENTS, ctx_kit=None, look=None, max_smoke=MAX_SMOKE, impacts=None):
    """Every ledger hit on `ship_key` up to t_shot1, on that ship's ShipFx copy for one shot build.

    ship_fx: the placed ship's ShipFx (battle_blockout's placed[key]['fx']); a ship without a kit copy
      (a stand-in) gets nothing. t_shot0, t_shot1: the shot's span in sequence seconds (spec['shot_t'];
      a still passes its key time twice). frame_of(t): the scene frame of sequence time t
      (battle_blockout: anchor + round((t - spec['key_t']) * fps)). matrix: the ship's world matrix in
      metres (placed[key]['matrix']; default ship_fx.matrix). shot_fx: the shot's fx entries
      (spec['fx']), so what the shot draws itself is not drawn twice. max_fires / max_vents: how many
      persistent fire and vent volumes this ship may carry in this shot (0 for a shot that cannot
      afford volumes). ctx_kit: an open (ctx, kit) of this ship's kit() to build into, else one is
      opened here. look: 'v2' (the default, LOOK) or 'v1' (the control). max_smoke: how many smoke
      threads (a volume each) this ship may trail in this shot. impacts: the shot's drawn impacts
      (drawn_impacts): a hit a tracer draws is placed where a drawn round met her then, not where
      the ledger authored it.

    Hits before t_shot0 are persistent: their scars (embers cooling by their age), their fires if
    still burning and their vents if still venting, up to the caps, the most severe and newest first.
    Hits in [t_shot0, t_shot1] are keyed at their frames: scars appearing as they land, the moment
    itself unless the shot draws it, and the fire or vent unless the shot draws a fire there.
    Returns a summary dict (also logged as 'ASHARU damage ...')."""
    if ship_fx is None or getattr(ship_fx, 'fx', None) is None:
        if hits(ship_key, until=t_shot1):
            log(f'ASHARU damage {ship_key}: no kit copy, {len(hits(ship_key, until=t_shot1))} hits not drawn')
        return None
    todo = hits(ship_key, until=t_shot1)
    if not todo:
        return None
    matrix = matrix if matrix is not None else ship_fx.matrix
    on = _fx_on(shot_fx, ship_key)
    if ctx_kit is None:
        with ship_fx.kit() as ck:
            return apply(ship_key, ship_fx, t_shot0, t_shot1, frame_of, matrix, log, shot_fx,
                         max_fires, max_vents, ctx_kit=ck, look=look, max_smoke=max_smoke, impacts=impacts)
    ctx, _kit = ctx_kit
    b = _Builder(ctx, ship_key, ship_fx, matrix, frame_of, log, look or LOOK)

    def smoke_s(h):
        return b.lv[h.sev].get('smoke_s', 0.0)
    past = [h for h in todo if h.t < t_shot0]
    now = [h for h in todo if h.t >= t_shot0]
    # persistent: fires and vents by priority, under the caps
    rank = sorted(past, key=lambda h: (-h.sev, -h.t))
    fires = [h for h in rank if h.burning(t_shot0) and not fire_drawn(h, on)][:max_fires]
    vents = [h for h in sorted(past, key=lambda h: -h.t) if h.venting(t_shot0)][:max_vents]
    smokes = [h for h in rank if h.kind != 'kill' and h.t + smoke_s(h) > t_shot0][:max_smoke]
    rows = []
    for h in past:
        f = b.frame(h.t)
        at, n, on_hull = b.scars(h, f)
        tag = []
        if not on_hull and h.kind not in ('rake', 'cut'):
            rows.append(f'{h.t:6.1f} {h.kind:6} sev{h.sev} past (off her plating: nothing placed)')
            continue
        if h in fires:
            b.fire(h, f, at, n)
            tag.append('fire')
        elif h.burning(t_shot0):
            tag.append('fire drawn by the shot' if fire_drawn(h, on) else 'fire capped: embers')
        if h in vents:
            b.vent(h, f, at, n)
            tag.append('vent')
        if h in smokes:
            b.smoke(h, f, at, n)
            tag.append('smoke')
        rows.append(f'{h.t:6.1f} {h.kind:6} sev{h.sev} past' + (f' ({", ".join(tag)})' if tag else ''))
    events = []
    for h in now:
        f = b.frame(h.t)
        tag = []
        if impacts is not None and ({'tracer', 'missile', 'mining'} & set(h.bys)):
            got = drawn_impacts(h, f, impacts)
            if got and got[0][0] > LINK_M:      # where the drawn round met her, the burst's walk kept
                import copy
                from mathutils import Vector
                d_ = Vector(got[0][1][3]) - Vector(h.at)
                h0, h = h, copy.copy(h)
                h.at = tuple(got[0][1][3])
                h.to = tuple(Vector(h0.to) + d_) if h0.to else None
                tag.append(f'placed on a drawn round {d_.length:.0f} m from its authored point, at ' + '({:.0f}, {:.0f}, {:.0f})'.format(*h.at))
        at, n, on_hull = b.scars(h, f)
        events.append(dict(hit=h, frame=f))
        if not on_hull and h.kind not in ('rake', 'cut'):
            rows.append(f'{h.t:6.1f} {h.kind:6} sev{h.sev} in shot (off her plating: nothing placed)')
            continue
        if 'missile' in h.bys:
            tag.append('moment drawn by its missile')
        elif drawn(h, on):
            tag.append(f'moment drawn by the shot ({h.by})')
        else:
            b.moment(h, f, at, n)
            tag.append('moment keyed')
        if LEVELS[h.sev]['fire'] > 0 and h.kind != 'kill':
            if fire_drawn(h, on):
                tag.append('fire drawn by the shot')
            else:
                b.fire(h, f, at, n, ignite_s=.2)
                tag.append('fire')
        if h.vent_s > 0 and h.kind != 'kill':
            b.vent(h, f, at, n, ignite_s=.1)
            tag.append('vent')
        if h.kind != 'kill' and h.sev >= 3 and smoke_s(h) > 0:
            b.smoke(h, f, at, n)
            tag.append('smoke')
        rows.append(f'{h.t:6.1f} {h.kind:6} sev{h.sev} in shot ({", ".join(tag)})')
    for r in rows:
        log(f'ASHARU damage {ship_key} {r}')
    log(f'ASHARU damage {ship_key} {t_shot0:g}-{t_shot1:g} s: {len(past)} past, {len(now)} in shot, {b.made}')
    return dict(ship=ship_key, past=len(past), now=len(now), events=events, **b.made)


def apply_all(placed, t_shot0, t_shot1, frame_of, log=print, shot_fx=None, **kw):
    """apply() for every placed ship (battle_blockout's `placed`). Returns {ship: summary}."""
    out = {}
    for key, pl in placed.items():
        r = apply(key, pl.get('fx'), t_shot0, t_shot1, frame_of, pl.get('matrix'), log, shot_fx, **kw)
        if r:
            out[key] = r
    return out


# ======================================================================================= plain Python

def table(ship=None):
    """The ledger as markdown, one table per ship."""
    out = []
    for s in sorted({h.ship for h in LEDGER} if ship is None else {ship}):
        out += [f'### {s}', '', '| t (s) | Hit | Severity | Source | What |', '|---|---|---|---|---|']
        for h in hits(s):
            out.append(f'| {h.t:.1f} | {h.kind} | {h.sev} {LEVELS[h.sev]["name"]} | {h.src} | {h.note} |')
        out.append('')
    return '\n'.join(out)


def check():
    """The ledger against battle_blockout's shots: every fx entry that lands on a ship (blast_on,
    fire_on, smoke, debris, a tracer's or a mining beam's target) has a ledger hit drawn by that kind
    within its shot's span (and, for a placed entry, within MATCH_M), and every ledger hit with a
    `by` has its entry. Prints mismatches; returns how many."""
    import battle_blockout as bb
    bad = 0
    for kf, spec in bb.KEYFRAMES.items():
        cam = spec['camera'].get('at')
        if cam is None or not spec.get('fx'):
            continue
        t0, t1 = cam.keys[0]['t'], cam.keys[-1]['t']
        for fx in spec['fx']:
            k = fx[0]
            if k in ('blast_on', 'debris'):
                ship, at = fx[1], (fx[3] if len(fx) > 3 else (0, 0, 0))
            elif k in ('fire_on', 'smoke'):
                ship, at = fx[1], fx[2]
            elif k in ('tracer', 'mining') and len(fx) > 2:
                ship, at = fx[2], None
            else:
                continue
            if ship in CLEAN or (k, fx[1], ship) in NOT_DAMAGE:
                continue
            ok = any(h.ship == ship and t0 - 6 <= h.t <= t1 and (k in h.bys or (k == 'tracer' and h.by is None))
                     and (at is None or _near(at, h.at)) for h in LEDGER)
            if k in ('fire_on', 'smoke'):        # a fire still burning from an earlier shot, drawn again
                ok = ok or any(h.ship == ship and h.t <= t1 and (h.burning(t0) or h.burning(t1)) and _near(at, h.at)
                               for h in LEDGER)
            if not ok:
                bad += 1
                print(f'DAMAGE check {kf} ({spec["shot"]} {t0:g}-{t1:g}): {fx[:3]} has no ledger hit')
    for h in LEDGER:
        if h.by is None:
            continue
        shots = [kf for kf, spec in bb.KEYFRAMES.items() if spec['camera'].get('at') is not None
                 and spec['camera']['at'].keys[0]['t'] - 6 <= h.t <= spec['camera']['at'].keys[-1]['t']
                 and any(drawn(h, [e]) for e in _fx_on(spec.get('fx'), h.ship))]
        if not shots:
            bad += 1
            print(f'DAMAGE check {h.id}: by {h.by!r} but no shot draws it')
    for kf, spec in bb.KEYFRAMES.items():
        for ship in spec.get('ships', {}):
            k = killed_at(ship)
            cam = spec['camera'].get('at')
            if k is not None and cam is not None and cam.keys[0]['t'] > k + 1:
                bad += 1
                print(f'DAMAGE check {kf}: {ship} is placed after her kill at {k:g} s')
    print(f'DAMAGE check: {len(LEDGER)} hits on {len({h.ship for h in LEDGER})} ships, {bad} mismatches')
    return bad


def main():
    argv = sys.argv[1:]
    if '--table' in argv:
        print(table())
    elif '--at' in argv:
        t = float(argv[argv.index('--at') + 1])
        for s in sorted({h.ship for h in LEDGER}):
            print(f'{s:12} {state(s, t)}')
    else:
        raise SystemExit(1 if check() else 0)


if __name__ == '__main__':
    main()
