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
MAX_FIRES = 2           # persistent fire volumes per ship per shot (volumes are the render's cost)
MAX_VENTS = 1           # persistent vents per ship per shot


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


class Hit:
    """One hit. kind: 'rake' (a burst walking at -> to), 'he' (one heavy round), 'breach' (the hull
    opens), 'debris' (plating torn off), 'cut' (a mining laser's seam at -> to), 'kill'.
    frm: the side the hit came from (outward, ship frame); the hit is cast onto the hull along it.
    by: the shot fx kind that draws its moment ('tracer', 'blast_on', 'fire_on', 'smoke',
    'debris', 'mining'), or None for a story hit this module keys itself.
    burn_until: when its fire is out (seconds), None = still burning at the end.
    vent_s: seconds of venting (default the level's)."""

    def __init__(self, ship, t, kind, sev, at, frm=(0, 0, 1), to=None, by=None, src='story', note='',
                 burn_until=None, vent_s=None, size=None):
        self.ship, self.t, self.kind, self.sev = ship, float(t), kind, int(sev)
        self.at, self.frm, self.to = tuple(at), tuple(frm), tuple(to) if to else None
        self.by, self.src, self.note = by, src, note
        self.burn_until, self.size = burn_until, size
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
    H('lasher-lead', 53.8, 'rake', 1, (20, 8, 4), (.3, 1, .5), to=(2, 9, 5), by='tracer',
      src='S6 k3 tracer manticore-p', note="the Manticore's light guns walk along her port bow as she comes about"),
    H('lasher-lead', 55.4, 'he', 2, (-6, 9, 5), (-.2, 1, .6), src='story (S6; the Falcon at 3.8 km is past the kit 6 s round limit)',
      note="a Falcon (P) bolt holes her port quarter; she vents"),
    H('lasher-lead', 55.9, 'breach', 3, (8, 6, 6), (.2, .6, 1), by='blast_on', src='S6 k3 blast_on',
      note='the hit that turns her: her forward deck opens and burns; smoking from here to S9b'),
    H('lasher-lead', 103.5, 'breach', 3, (-20, 10, 6), (-.2, .5, 1), by='fire_on', src='S9b k4b fire_on',
      note="the Falcon's point guns as she rolls in: her aft deck catches"),
    H('lasher-lead', 104.6, 'debris', 2, (-10, 8, 0), (0, 1, 0), by='debris', src='S9b k4b debris',
      note='plating shed off her port side as she dives'),
    H('lasher-lead', 107.6, 'breach', 4, (-30, 0, 6), (0, 0, 1), by='tracer', src='S9b k4b tracer falcon-p',
      note="the Falcon's broadside opens her stern: she tumbles away burning"),
    H('lasher-lead', 108.0, 'rake', 1, (12, -4, 6), (0, 0, 1), to=(-18, -6, 6), by='tracer',
      src='S9b k4b tracer falcon-p', note='the rest of the broadside walks her deck'),

    # ---- Lasher A: burns in S6
    H('lasher-a', 52.8, 'he', 2, (-12, 10, 5), (-.3, 1, .5), src='story (S6)',
      note="a Falcon (P) bolt opens her port quarter before S6's still"),
    H('lasher-a', 54.5, 'breach', 3, (-20, 10, 6), (-.2, .5, 1), by='fire_on', src='S6 k3 fire_on',
      note='Lasher A burns'),

    # ---- WARDEN THREE, Lasher B: scored, gets away
    H('lasher-b', 55.0, 'rake', 1, (15, -9, 3), (0, -1, .3), to=(0, -10, 3), by='tracer',
      src='S6 k3 tracer falcon-p', note="the Falcon's fire scores her starboard bow"),
    H('lasher-b', 61.5, 'rake', 1, (-5, 0, 8), (0, 0, 1), to=(-18, 2, 8),
      src='story (S6b; the Buffalo Mk.II at 3.8 km is past the kit 6 s round limit)', note="the Buffalo Mk.II's fire scores her deck as she rolls over"),

    # ---- WARDEN FOUR, the merchant Wolf: a graze, gets away
    H('wolf-m', 73.0, 'he', 1, (5, -8, 3), (0, -1, .2), src='story (S6c)',
      note='a blaster graze as she chases the Wolves (P) through the gap'),

    # ---- WARDEN FIVE, the convoy's Hound: crippled in S6d
    H('hound-m', 75.2, 'rake', 1, (8, 6, -2), (0, .6, -1), to=(-6, 7, -2), by='tracer',
      src='S6d k3e tracer hound-p-b', note="the second Hound (P)'s chaingun from below as she rolls"),
    H('hound-m', 75.5, 'breach', 3, (-12, 4, 3), (-.6, .3, .7), by='fire_on', src='S6d k3e fire_on',
      note='drives hit: her drive deck burns'),
    H('hound-m', 77.4, 'he', 2, (-24, -3, 1), (-1, 0, .2), src='story (S6d)', vent_s=20.0,
      note='a second round in the drive cluster: she loses thrust, venting (the camera kick at 77.4)'),

    # ---- CARAVEL TWO, the Tarsus: drives shot out in S6c, boarded, taken
    H('tarsus', 68.0, 'rake', 1, (-30, -8, 4), (-.3, -1, .5), to=(-44, -6, 3), by='tracer',
      src='S6c k3d tracer wolf-p-a', note='the Wolves (P) rake aft toward her drives as they pass'),
    H('tarsus', 69.6, 'he', 2, (-44, 5, 2), (-1, .4, .4), by='tracer', src='S6c k3d tracer wolf-p-b',
      note='a drive bell holed'),
    H('tarsus', 70.5, 'breach', 3, (-40, 0, 6), (-.2, .5, 1), by='fire_on', src='S6c k3d fire_on',
      burn_until=102.0, note='drives shot out: fire burns up out of the breach (continuity), out before S10'),
    H('tarsus', 84.0, 'rake', 1, (36, -4, 9), (.2, -.3, 1), to=(24, 4, 10), by='tracer',
      src='S7 k3b tracer wolf-p-b', note="the covering Wolf (P)'s inverted gun run scores her forward deck"),

    # ---- CARAVEL, the Colossus: port pod shot out in S8, the other failing, she falls
    H('colossus', 89.0, 'rake', 1, (-40, 26, 8), (0, .5, 1), to=(-58, 30, 6),
      src='story (S8-S9; the Mk.III cannot depress onto her until her flight changes)', note="the Mk.III's guns walk onto the port pod from above"),
    H('colossus', 93.5, 'breach', 3, (-55, 36, 4), (-.3, 1, .5), by='fire_on', src='S8-S9 k4 fire_on',
      note='the port pod burns, and burns into the atmosphere'),
    H('colossus', 94.9, 'he', 4, (-62, 30, 0), (-.4, 1, .2), by=('blast_on', 'debris'), src='S8-S9 k4 blast_on + debris',
      vent_s=40.0, note='the port tug pod is shot out: torn open, its lines venting'),
    H('colossus', 108.0, 'he', 2, (-62, -30, 0), (-.4, -1, .2), src='story (between S9b and S10)', vent_s=40.0,
      note='the starboard pod failing: an overstrained plate gives, venting'),
    H('colossus', 113.3, 'debris', 3, (-30, 0, 10), (0, 0, 1), by='debris', src='S10 k5 debris',
      note='her back starts to go: plating shed off the dorsal spine as she noses down; it burns'),

    # ---- MORROW: under the Colossus's keel; scuffed, nothing more before the fall
    H('morrow', 95.5, 'rake', 0, (-6, 6, 9), (0, .3, 1), to=(-18, 4, 9), src='story (S8-S9)',
      note="the port pod's debris scuffs her back as she takes the weight"),

    # ---- BARON, the Falcon (P): shield overloaded, flank opened in S9b, still burning in S10
    H('falcon-p', 104.8, 'rake', 1, (40, -30, 6), (0, -.6, 1), to=(0, -34, 4), by='tracer',
      src='S9b k4b tracer lasher-lead', note="the Lasher's guns walk down the open flank after the overload"),
    H('falcon-p', 105.0, 'breach', 3, (-20, -14, 4), (-.3, -.6, .7), by='blast_on', src='S9b k4b blast_on',
      note='hit hard: the flank opens and burns; still burning in S10 (continuity)'),
    H('falcon-p', 106.0, 'he', 2, (-45, -20, 2), (0, -1, .4), src='story (S9b)', vent_s=20.0,
      note="the Lasher's last rounds hole her further aft, venting"),

    # ---- the Manticore (P): scored by the lead's guns
    H('manticore-p', 55.5, 'rake', 1, (25, 8, 4), (.4, 1, .3), to=(10, 10, 4),
      src='story (S6; the lead at 3.4 km is past the kit 6 s round limit)', note="the lead's light MGs as she turns back"),

    # ---- Wolf (P) A: grazed, then cut by a Borer
    H('wolf-p-a', 56.8, 'rake', 0, (10, 6, 3), (0, 1, .2), to=(0, 7, 3), by='tracer',
      src='S6 k3 tracer lasher-b', note="Lasher B's fire"),
    H('wolf-p-a', 71.6, 'he', 1, (-8, 8, 2), (-.3, 1, .3), by='tracer', src='S6c k3d tracer wolf-m',
      note="the merchant Wolf's bolt, past her front shield onto the flank"),
    H('wolf-p-a', 97.5, 'cut', 2, (20, -8, 4), (0, -1, .3), to=(-20, -9, 4), by='mining', vent_s=0.0,
      src='S8-S9 k4 mining borer-c', note="a Borer's mining laser cuts a long glowing seam down her side"),
    H('wolf-p-a', 95.8, 'he', 1, (-15, -9, 3), (0, -1, .3), by='tracer', src='S8-S9 k4 tracer colossus'),

    # ---- Wolf (P) B: a graze through her shield
    H('wolf-p-b', 71.2, 'rake', 0, (8, -8, 3), (0, -1, .2), to=(-4, -8, 3), by='tracer',
      src='S6c k3d tracer mule', note="the Mule's guns, mostly on her shield"),

    # ---- Kite (P) B: driven off by the Colossus in S9
    H('kite-p-b', 96.5, 'he', 2, (-10, -10, 3), (0, -1, .4), by='tracer', src='S8-S9 k4 tracer colossus',
      vent_s=20.0, note='leaks through her failing shield (flux .85): she breaks off, venting'),
    H('kite-p-b', 98.0, 'cut', 1, (10, -9, 2), (0, -1, .3), to=(-10, -9, 3), by='mining',
      src='S8-S9 k4 mining borer-d', note="a Borer's laser on her side"),

    # ---- HOOK, the Colossus Mk.III: the Colossus fires up into her belly
    H('colossus3', 96.0, 'rake', 1, (20, 10, -6), (0, .3, -1), to=(0, 12, -6), by='tracer',
      src='S8-S9 k4 tracer colossus', note="the Colossus's turrets score her belly"),
    H('colossus3', 99.5, 'rake', 1, (-10, -6, -7), (0, -.3, -1), to=(-25, -4, -7), src='story (S9)',
      note='a second burst as she swings her grapples away'),

    # ---- the Hounds (P) the convoy kills
    H('hound-p-c', 60.0, 'rake', 1, (5, 8, 4), (0, .3, 1), to=(-10, 8, 4), by='tracer',
      src='S6b k3c tracer lasher-b', note='bare armour soaks the first burst'),
    H('hound-p-c', 61.0, 'rake', 1, (-5, -8, 4), (0, -.3, 1), to=(10, -8, 4), by='tracer',
      src='S6b k3c tracer wolf-m'),
    H('hound-p-c', 61.9, 'kill', 4, (0, 0, 0), (0, 0, 1), by=('blast_on', 'debris'), src='S6b k3c blast_on + debris',
      note='the Ammo Feeder burst breaks her up'),
    H('hound-p-a', 75.0, 'rake', 1, (6, 5, 3), (0, .4, 1), to=(-8, 6, 3), by='tracer',
      src='S6d k3e tracer hound-m'),
    H('hound-p-a', 76.9, 'kill', 4, (0, 0, 0), (0, 0, 1), by=('blast_on', 'debris'), src='S6d k3e blast_on + debris',
      note='killed at close range'),
]
LEDGER.sort(key=lambda h: (h.ship, h.t))

# Ships the ledger leaves clean on purpose: the Phaeton (her wreck state carries its own cook-off),
# the stand-ins (no kit copy to mark), the boats and drones, and the raiders nobody hurts.
CLEAN = ('phaeton', 'mule', 'buffalo-a', 'buffalo-b', 'dram', 'buffalo2-a', 'kite-p-a', 'hound-p-b',
         'hermes-p-a', 'hermes-p-b', 'hermes-p-c', 'borer-a', 'borer-b', 'borer-c', 'borer-d')


# Shot fx on a ship that are not damage: Morrow's Borers cutting the Colossus's burning pod free.
NOT_DAMAGE = {('mining', 'borer-a', 'colossus'), ('mining', 'borer-b', 'colossus')}


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

def _cast(scene, matrix, ship_fx, p_m, frm, standoff=30.0):
    """Where a hit at p_m coming from direction frm meets this ship's plating: (at_m, normal_m) in the
    ship's frame, or the authored point and frm when nothing is found. Casts from `standoff` metres out
    along frm, accepting only this ship's hull objects (a neighbour's hull, or any kit volume, is passed
    through)."""
    import bpy
    from mathutils import Vector
    import ship_fx as sfx_mod
    hull = getattr(ship_fx, 'hull', None)
    mine = {sfx_mod.base_name(o.name) for o in hull.all_objects} if hull is not None else None
    d_l = Vector(frm).normalized()
    dg = bpy.context.evaluated_depsgraph_get()
    o = matrix @ (Vector(p_m) + d_l * standoff)
    d = (matrix.to_3x3() @ -d_l).normalized()
    far = standoff * 2.5
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
        return tuple(inv @ loc), tuple(n)
    return tuple(p_m), tuple(d_l)


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


class _Builder:
    """One ship's damage for one shot build, inside its kit() block."""

    def __init__(self, ctx, ship_key, ship_fx, matrix, frame_of, log):
        import swfx_core
        import swfx_impacts
        self.ctx, self.key, self.ship, self.matrix = ctx, ship_key, ship_fx, matrix
        self.frame_of, self.log = frame_of, log
        self.core, self.imp = swfx_core, swfx_impacts
        self.mats = {}
        self.made = dict(scars=0, holes=0, fires=0, vents=0, keyed=0)

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
        lv = LEVELS[h.sev]
        rnd = random.Random(self.seed(h, 'sites'))
        count, s0, s1 = lv['scorch']
        at = Vector(h.at)
        if h.kind in ('rake', 'cut') and h.to:
            to = Vector(h.to)
            n = max(2, int((to - at).length / (3.0 if h.kind == 'rake' else 1.6)) + 1)
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
        (a cut's or a rake's walk along their line). Returns the hit point and normal on the hull."""
        from mathutils import Matrix, Vector
        import bpy
        from swfx_smoke import _no_shadow_catch
        lv = LEVELS[h.sev]
        units = self.core.units
        coll = self.ctx.collection(self.imp.GROUP)
        rnd = random.Random(self.seed(h, 'decals'))
        hit_at, hit_n = _cast(self.ctx.scene, self.matrix, self.ship, h.at, h.frm)
        sites = self._sites(h)
        if lv['hole'] > 0 and h.kind not in ('rake', 'cut'):
            sites.insert(0, (Vector(hit_at), (h.size or lv['hole']), 0.0))
        line = Vector(h.to) - Vector(h.at) if h.to else None
        for i, (p, size, delay) in enumerate(sites):
            hole = i == 0 and lv['hole'] > 0 and h.kind not in ('rake', 'cut')
            at, n = (tuple(p), hit_n) if hole else _cast(self.ctx.scene, self.matrix, self.ship, tuple(p), h.frm)
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
                self.mats[key] = _scar_material(self.ctx, f'SWFX damage scar {self.seed(h, key)}', e,
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
        return hit_at, hit_n

    def fire(self, h, frame, at, n, ignite_s=0.0):
        """The hit's fire: the kit's breach flame, its smoke and flickering light, timed by its own empty."""
        import bpy
        import swfx_fire
        size = LEVELS[h.sev]['fire']
        e = self.empty(h, frame, at, n, 'damage fire', size_m=size, imp_ignite=ignite_s,
                       seed=self.seed(h, 'fire') % 997)
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

    def moment(self, h, frame, at, n):
        """The hit's moment, for a hit the shot does not draw: the kit's own impacts (through the shield
        where one is up), a breach's blast, debris for a ship coming apart."""
        imp = self.imp
        if h.kind in ('rake', 'cut'):
            if h.kind == 'cut':      # a laser seam has no rounds; its decals walk the line
                return
            for k, (p, _s, delay) in enumerate(self._sites(h)):
                at_k, n_k = _cast(self.ctx.scene, self.matrix, self.ship, tuple(p), h.frm)
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


def apply(ship_key, ship_fx, t_shot0, t_shot1, frame_of, matrix=None, log=print, shot_fx=None,
          max_fires=MAX_FIRES, max_vents=MAX_VENTS, ctx_kit=None):
    """Every ledger hit on `ship_key` up to t_shot1, on that ship's ShipFx copy for one shot build.

    ship_fx: the placed ship's ShipFx (battle_blockout's placed[key]['fx']); a ship without a kit copy
      (a stand-in) gets nothing. t_shot0, t_shot1: the shot's span in sequence seconds (spec['shot_t'];
      a still passes its key time twice). frame_of(t): the scene frame of sequence time t
      (battle_blockout: anchor + round((t - spec['key_t']) * fps)). matrix: the ship's world matrix in
      metres (placed[key]['matrix']; default ship_fx.matrix). shot_fx: the shot's fx entries
      (spec['fx']), so what the shot draws itself is not drawn twice. max_fires / max_vents: how many
      persistent fire and vent volumes this ship may carry in this shot (0 for a shot that cannot
      afford volumes). ctx_kit: an open (ctx, kit) of this ship's kit() to build into, else one is
      opened here.

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
                         max_fires, max_vents, ctx_kit=ck)
    ctx, _kit = ctx_kit
    b = _Builder(ctx, ship_key, ship_fx, matrix, frame_of, log)
    past = [h for h in todo if h.t < t_shot0]
    now = [h for h in todo if h.t >= t_shot0]
    # persistent: fires and vents by priority, under the caps
    rank = sorted(past, key=lambda h: (-h.sev, -h.t))
    fires = [h for h in rank if h.burning(t_shot0) and not fire_drawn(h, on)][:max_fires]
    vents = [h for h in sorted(past, key=lambda h: -h.t) if h.venting(t_shot0)][:max_vents]
    rows = []
    for h in past:
        f = b.frame(h.t)
        at, n = b.scars(h, f)
        tag = []
        if h in fires:
            b.fire(h, f, at, n)
            tag.append('fire')
        elif h.burning(t_shot0):
            tag.append('fire drawn by the shot' if fire_drawn(h, on) else 'fire capped: embers')
        if h in vents:
            b.vent(h, f, at, n)
            tag.append('vent')
        rows.append(f'{h.t:6.1f} {h.kind:6} sev{h.sev} past' + (f' ({", ".join(tag)})' if tag else ''))
    for h in now:
        f = b.frame(h.t)
        at, n = b.scars(h, f)
        tag = []
        if drawn(h, on):
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
        rows.append(f'{h.t:6.1f} {h.kind:6} sev{h.sev} in shot ({", ".join(tag)})')
    for r in rows:
        log(f'ASHARU damage {ship_key} {r}')
    log(f'ASHARU damage {ship_key} {t_shot0:g}-{t_shot1:g} s: {len(past)} past, {len(now)} in shot, {b.made}')
    return dict(ship=ship_key, past=len(past), now=len(now), **b.made)


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
