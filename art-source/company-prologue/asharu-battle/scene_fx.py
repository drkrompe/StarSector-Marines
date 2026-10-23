"""Asharu key frames: the effects kit driven shot by shot (the final serial step of asharu-effects-plan.md).

battle_blockout's key frames describe their action as effect entries (`fx`) plus per-ship
`shields`. Without --swfx the blockout draws them as stand-ins (white streaks, bead missiles).
With --swfx each entry is drawn by the finished kit on the ships' own ShipFx copies, timed so the
key frame's still lands in the middle of the action:

- 'tracer' a -> b: the bearing gun nearest the muzzle the blockout chose is armed as its game
  weapon (`WEAPON_OF`) and fired at the game's cadence (swfx_guns.fire_weapon) for as long as a
  round takes to cross, ending on this frame. Rounds are in flight along the whole line, and the
  turret trains onto the target. Each round that has arrived strikes b where its line meets the
  hull (swfx_impacts.strike): on b's shield where that is up, through it late in the flux load,
  else on the armour.
- 'missiles' a -> b: a Harpoon salvo from a's tubes (swfx_missile_paths.salvo), timed to arrive
  on b's break_frame where b has one (the Phaeton's cook-off), else a moment after this frame.
- 'blast_on' / 'debris' / 'fire_on' / 'smoke': swfx_impacts' HE burst (no orbs), plate debris,
  a breach burning for a second and a half, and an old breach still smoking.
- 'flares': swfx_pd's flares from the ship's flare muzzles (the Colossus's four).
- `shields`: swfx_shield's film, raised toward the named attacker, carrying `flux`, with an
  overload `overload_s` before this frame where the shot says so.

An entry this module cannot draw (a stand-in shooter, a ship with no flare muzzles) returns False
and the blockout draws its stand-in. Runs inside Blender only.
"""
import importlib
import math
import random

import bpy
from mathutils import Matrix, Vector

# The game weapon on each kit turret, from the build's variant (bench_guns' table).
WEAPON_OF = {
    'manticore-p': {1: 'hellbore', 2: 'lightag', 3: 'lightneedler', 4: 'lightag'},
    'lasher': {1: 'lightmg', 2: 'lightmg', 3: 'lightmg'},
    'wolf-p': {1: 'ioncannon', 2: 'miningblaster'},
    'wolf': {1: 'ioncannon', 4: 'heavyblaster'},
    'colossus-mk3': {**{t: 'lightmg' for t in range(1, 7)}, 7: 'lightac', 8: 'lightac'},
    'hound-p': {1: 'chaingun', 2: 'lightdualmg'},
    'hound': {1: 'chaingun', 2: 'lightdualmg'},
}
# A build whose turrets the table does not list fires its variant's main gun from every mount.
# The Falcon (P)'s IR pulse lasers have no kit weapon yet: the heavy blaster's red-orange bolts
# stand in.
DEFAULT_WEAPON = {'falcon-p': 'heavyblaster', 'kite-p': 'lightdualmg', 'colossus': 'lightmg',
                  'tarsus': 'lightmg', 'tarsus-boarded': 'lightmg', 'shepherd': 'lightmg'}
HE = {'lightag', 'lightac', 'heavyac', 'mark9', 'hellbore', 'heavyblaster', 'miningblaster', 'ioncannon'}
FEEDER = {'lasher'}          # the Lasher's ship system, on whenever she fires (S6b, S9b)


def _mods():
    import swfx_impacts
    import swfx_missile_paths
    import swfx_pd
    import swfx_shield
    return {m.__name__: importlib.reload(m) for m in (swfx_impacts, swfx_missile_paths, swfx_pd, swfx_shield)}


class SceneFx:
    """One key frame's kit effects. placed: battle_blockout's placed ships (each with 'fx', its
    ShipFx or None, 'matrix' in metres, 'mpu'); build_of(key): the ship's finished build key;
    muzzle_world(key, muzzle): a muzzle's world point. span: (first, last) scene frame of the shot when
    it is rendered in motion (battle_blockout.add_span): the guns then fire through the whole shot, not
    only for the rounds in flight at the key instant."""

    def __init__(self, scene, placed, build_of, muzzle_world, frame, log=print, span=None):
        self.scene, self.placed, self.build_of, self.muzzle_world = scene, placed, build_of, muzzle_world
        self.now, self.fps = frame, scene.render.fps / scene.render.fps_base
        self.span = span
        self.log = log
        self.m = _mods()
        self.rows = []
        for pl in placed.values():
            if pl['fx'] is not None:
                pl['fx'].matrix = pl['matrix']      # swfx_missile_paths.space_of reads ship.matrix

    # ------------------------------------------------------------------ helpers
    def ship(self, key):
        s = self.placed[key]['fx'] if key in self.placed else None
        return s if s is not None and s.fx is not None else None

    def space(self, key):
        pl = self.placed[key]
        return pl['matrix'] @ Matrix.Scale(pl['mpu'], 4)

    def local(self, key, p):
        """A world point in the ship's own frame, metres."""
        return self.placed[key]['matrix'].inverted() @ Vector(p)

    def world(self, key, local=(0, 0, 0)):
        return self.placed[key]['matrix'] @ Vector(local)

    def guns(self, ctx, launcher=False):
        return [g for g in ctx.anchors.guns if (getattr(g, 'kind', 'gun') == 'launcher') == launcher]

    def nearest_gun(self, key, ctx, point):
        """(turret, tag) of the gun whose muzzle is nearest a world point (the blockout's bearing pick)."""
        import swfx_guns
        sp = self.space(key)
        best = min(self.guns(ctx), key=lambda g: ((sp @ swfx_guns.anchor_matrix(g.empty)).translation
                                                  - Vector(point)).length, default=None)
        return (best.turret, best.tag) if best else (None, None)

    def weapon(self, key, turret):
        b = self.build_of(key)
        return (WEAPON_OF.get(b) or {}).get(turret) or DEFAULT_WEAPON.get(b) or 'lightmg'

    def hit_point(self, target, from_world, aim_world):
        """Where a round from from_world toward aim_world meets the target's hull, in the target's
        frame (metres), with the surface normal; the aim point and an outward normal on a miss."""
        imp = self.m['swfx_impacts']
        a, b = self.local(target, from_world), self.local(target, aim_world)
        start = b + (a - b).normalized() * 60.0          # cast from just outside her, not from the shooter
        got = imp.ray_to_hull(self.scene, self.placed[target]['matrix'], tuple(start), tuple(b))
        if got:
            return Vector(got[0]), Vector(got[1])
        n = (a - b).normalized()
        return b, n

    # ------------------------------------------------------------------ shields
    def shield(self, key, s):
        """s: dict(toward=<attacker key>, flux=0..1, overload_s=None or seconds before now,
        up_s=seconds raised before now (default 4))."""
        ship = self.ship(key)
        if ship is None:
            return False
        sh = self.m['swfx_shield']
        with ship.kit() as (ctx, kit):
            sh = importlib.reload(sh)           # SHIELD is read at import, for this hull's profile
            rec = sh.build(ctx, film=True, hull_collection=ship.hull)
            if rec.get('bubble') is None:
                self.log(f'ASHARU kit shield {key}: none built ({rec.get("missing")})')
                return False
            if s.get('toward') in self.placed:
                d = self.local(key, self.world(s['toward']))
                ctx.set('shield_facing', math.degrees(math.atan2(d.y, d.x)))
            up = self.now - round(s.get('up_s', 4.0) * self.fps)
            sh.raise_shield(ctx, up, fps=self.fps)
            sh.flux(ctx, up, 0.0)
            sh.flux(ctx, self.now - 2, float(s.get('flux', .3)))
            if s.get('overload_s') is not None:
                d = self.local(key, self.world(s['toward'])) if s.get('toward') in self.placed else Vector((1, 0, 0))
                az = math.degrees(math.atan2(d.y, d.x))
                el = math.degrees(math.atan2(d.z, math.hypot(d.x, d.y)))
                sh.overload(ctx, self.now - round(s['overload_s'] * self.fps), az, el, fps=self.fps)
        self.rows.append(f'shield {key} {rec["type"]} {rec["arc"]:g} flux {s.get("flux", .3)}'
                         + (f' overload {s["overload_s"]} s ago' if s.get('overload_s') is not None else ''))
        return True

    # ------------------------------------------------------------------ guns
    def tracer(self, a, b, aim_local=None, muzzles=(None,), most=4):
        """a's guns on b: every bearing gun with a clear line (muzzles, the blockout's pick first), up
        to `most` turrets. True when the kit drew it (a has a kit copy)."""
        shooter, target = self.ship(a), self.ship(b)
        aim = self.world(b, aim_local or (0, 0, 0))
        mz = self.muzzle_world(a, muzzles[0]) if muzzles[0] is not None else self.world(a)
        hit_l, normal = self.hit_point(b, mz, aim) if b in self.placed else (None, None)
        hit_w = self.world(b, hit_l) if hit_l is not None else aim
        if shooter is None:
            # a stand-in shooter: the blockout draws its streaks; the hits still land on b's kit
            if target is not None and hit_l is not None:
                self.strikes(b, [self.now - k * 3 for k in range(4)], hit_l, normal, 'kinetic')
            return False
        landed, fired = [], []
        with shooter.kit() as (ctx, kit):
            turrets = []
            for m in muzzles:
                t, _tag = self.nearest_gun(a, ctx, self.muzzle_world(a, m) if m is not None else mz)
                if t is not None and t not in turrets:
                    turrets.append(t)
            for turret in turrets[:most]:
                weapon = self.weapon(a, turret)
                kit.guns.arm(ctx, turret, weapon)
                speed = (kit.guns.WEAPONS[weapon].get('round') or {}).get('speed', 400)
                dist = (Vector(hit_w) - Vector(mz)).length
                window = min(2.5, max(.6, dist / speed + .5)) + .1     # the last half second's rounds have landed
                f0 = self.now - round(window * self.fps) - 2 * len(fired)     # mounts not in step
                end = self.now
                if self.span:        # in motion: firing from before the shot opens until it cuts
                    f0 = min(f0, self.span[0] - round((dist / speed) * self.fps)) - 2 * len(fired)
                    end = self.span[1]
                    window = (end - f0) / self.fps
                feeder = self.build_of(a) in FEEDER
                if feeder:
                    kit.guns.feeder(ctx, turret, f0 - 6, end + 6)
                recs = kit.guns.fire_weapon(ctx, turret, f0, window + 1.0 / self.fps, feeder=feeder,
                                            target=self.space(a).inverted() @ Vector(hit_w),
                                            train=shooter.own_hull)
                landed += [r['hit_frame'] for r in recs if r.get('hit_frame') is not None and r['hit_frame'] <= end]
                fired.append(f't{turret} {weapon} x{len(recs)}' + (' (feeder)' if feeder else ''))
        if not fired:
            return False
        landed = sorted(set(landed))
        if target is not None and hit_l is not None and landed:
            he = any(self.weapon(a, int(f.split()[0][1:])) in HE for f in fired)
            if self.span:        # a strike every few frames through the shot, not every round
                landed = [f for f in landed if f >= self.span[0] - 12]
                landed = landed[::max(1, len(landed) // 24)]
            self.strikes(b, landed if self.span else landed[-8:], hit_l, normal, 'he' if he else 'kinetic')
        self.rows.append(f'tracer {a} -> {b}: {", ".join(fired)}, {len(landed)} landed')
        return True

    def strikes(self, key, frames, at_l, normal, kind):
        """Rounds arriving on key's hull (or shield) around at_l on these frames."""
        imp = self.m['swfx_impacts']
        ship = self.ship(key)
        if ship is None:
            return
        rnd = random.Random(f'asharu-strikes|{key}|{frames}')
        out = {}
        with ship.kit() as (ctx, kit):
            for f in frames:
                j = Vector((rnd.gauss(0, 1.2), rnd.gauss(0, 1.2), rnd.gauss(0, .6)))
                where = imp.strike(ctx, f, tuple(at_l + j), tuple(normal), kind=kind)
                out[where] = out.get(where, 0) + 1
        self.rows.append(f'strikes on {key}: {out}')

    # ------------------------------------------------------------------ missiles
    def missiles(self, a, b, arrive=None):
        shooter = self.ship(a)
        if shooter is None:
            return False
        mp = self.m['swfx_missile_paths']
        with shooter.kit() as (ctx, kit):
            tubes = self.guns(ctx, launcher=True)
            starts = [(*mp.tube(shooter, ctx, g.turret, g.tag), kit.guns.gun_label(g)) for g in tubes[:4]]
        if not starts:
            return False
        target = self.world(b)
        arrive = arrive if arrive is not None else self.now + 6
        frames = [3 * i for i in range(len(starts))]
        fl = mp.salvo('harpoon', starts, target, mp.space_of(shooter), frames=frames, target_name=b, width=.18)
        shift = arrive - max(f.end['frame'] for f in fl if f.end)
        fl = mp.salvo('harpoon', starts, target, mp.space_of(shooter), frames=[f + shift for f in frames],
                      target_name=b, width=.18)
        with shooter.kit() as (ctx, kit):
            kit.guns.launch_flights(ctx, fl)
        self.rows.append(f'missiles {a} -> {b}: {len(fl)} Harpoons, launched {frames[0] + shift}, '
                         f'arriving {[f.end["frame"] for f in fl if f.end]}')
        return True

    # ------------------------------------------------------------------ damage
    def outward(self, key, at):
        v = Vector(at)
        return (v.normalized() if v.length > 1e-3 else Vector((0, 0, 1)))

    def blast_on(self, key, radius, at):
        ship = self.ship(key)
        if ship is None:
            return False
        with ship.kit() as (ctx, kit):
            self.m['swfx_impacts'].he(ctx, self.now - 2, tuple(at), tuple(self.outward(key, at)), size_m=radius * .6)
        self.rows.append(f'blast on {key} r{radius}')
        return True

    def debris(self, key, count, at):
        ship = self.ship(key)
        if ship is None:
            return False
        with ship.kit() as (ctx, kit):
            self.m['swfx_impacts'].debris(ctx, self.now - 10, tuple(at), tuple(self.outward(key, at)), count=count)
        self.rows.append(f'debris {key} x{count}')
        return True

    def breach(self, key, at, toward, age_s, vent):
        ship = self.ship(key)
        if ship is None:
            return False
        with ship.kit() as (ctx, kit):
            self.m['swfx_impacts'].breach(ctx, self.now - round(age_s * self.fps), tuple(at), tuple(toward),
                                          vent=vent)
        self.rows.append(f'breach {key} {age_s:g} s old')
        return True

    def flares(self, key):
        ship = self.ship(key)
        if ship is None:
            return False
        pd = self.m['swfx_pd']
        anchors = pd.flare_anchors(ship)
        if not anchors:
            return False
        fl = pd.plan_flares(anchors, self.now - round(1.2 * self.fps))
        with ship.kit() as (ctx, kit):
            pd.key_flares(ctx, fl, self.space(key), ship.mpu)
        self.rows.append(f'flares {key}: {len(fl)}')
        return True
