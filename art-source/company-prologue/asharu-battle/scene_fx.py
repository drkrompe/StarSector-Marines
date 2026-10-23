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
import contextlib
import importlib
import math
import random
import re

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
    only for the rounds in flight at the key instant. cur: the scene frame this span frame renders at;
    only the rounds and strikes it can see are built (swfx_guns.fire_weapon's `only`)."""

    def __init__(self, scene, placed, build_of, muzzle_world, frame, log=print, span=None, cur=None,
                 matrix_at=None, cam_at=None):
        self.scene, self.placed, self.build_of, self.muzzle_world = scene, placed, build_of, muzzle_world
        self.now, self.fps = frame, scene.render.fps / scene.render.fps_base
        self.span, self.cur = span, cur
        self.matrix_at = matrix_at      # (key, scene frame) -> that ship's world matrix then, metres
        self.cam_at = cam_at            # scene frame -> (camera world location, horizontal fov rad)
        self.log = log
        self.m = _mods()
        self.rows = []
        self.before = set(bpy.data.objects)     # what the builds brought: projectile_check looks past it
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
        landed, fired, placed_hits = [], [], []
        with self.fired(a), shooter.kit() as (ctx, kit):
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
                aim_at = self.space(a).inverted() @ Vector(hit_w)
                if self.span and self.matrix_at is not None and hit_l is not None:
                    aim_at = self.tracking(a, b, self.local(a, mz), hit_l, speed)
                    if self.cur is None:      # the whole shot, built once: planned aim, bursts, real shots
                        recs = self.planned(ctx, kit, a, b, turret, weapon, self.local(a, mz), hit_l, speed,
                                            feeder, aim_at)
                        if recs is not None:
                            placed_hits += [(r['hit_frame'], r['hit_l'], r['hit_n']) for r in recs
                                            if r.get('hit_frame') is not None and r.get('hit_l') is not None]
                            if recs:
                                fired.append(f't{turret} {weapon} x{len(recs)}' + (' (feeder)' if feeder else ''))
                            continue
                only = None
                if self.span and self.cur is not None:     # this frame's rounds: in flight, or just landed
                    only = (self.cur - round((dist / speed + 1.5) * self.fps), self.cur + 1)
                recs = kit.guns.fire_weapon(ctx, turret, f0, window + 1.0 / self.fps, feeder=feeder,
                                            target=aim_at, train=shooter.own_hull, only=only)
                landed += [r['hit_frame'] for r in recs if r.get('hit_frame') is not None and r['hit_frame'] <= end]
                aims = [r['aim'] for r in recs if r.get('aim')]
                if aims:
                    self.rows.append(f'aim {a} t{turret} -> {b}: traverse/elevation {aims[0]} .. {aims[-1]} '
                                     f'over {len(aims)} rounds')
                if recs:             # a degenerate gun fires nothing: the stand-in draws the shot
                    fired.append(f't{turret} {weapon} x{len(recs)}' + (' (feeder)' if feeder else ''))
        if not fired:
            return False
        if placed_hits:      # planned rounds: each strike where its own straight line met her hull
            he = self.hit_kind(a, fired)
            placed_hits = [h for h in sorted(placed_hits, key=lambda h: h[0])
                           if h[0] >= self.span[0] - round(1.5 * self.fps)]
            self.strikes_at(b, placed_hits[::2], he)
        landed = sorted(set(landed))
        if target is not None and hit_l is not None and landed:
            he = self.hit_kind(a, fired)
            if self.span:        # in motion: the strikes this frame can see (each placed by its own frame)
                if self.cur is not None:
                    landed = [f for f in landed if f >= self.cur - round(1.5 * self.fps)]
                else:            # built once for the whole shot (--kit-once): every other strike in it
                    landed = [f for f in landed if f >= self.span[0] - round(1.5 * self.fps)][::2]
            self.strikes(b, landed if self.span else landed[-8:], hit_l, normal, he)
        self.rows.append(f'tracer {a} -> {b}: {", ".join(fired)}, {len(landed) + len(placed_hits)} landed')
        return True

    def tracking(self, a, b, muzzle_l, hit_l, speed):
        """A moving target for a: frame -> the point on b's hull (hit_l, b's frame) where a round fired
        then meets her, in a's copy space then. Both ships fly their own paths over the shot."""
        mpu = self.placed[a]['mpu']

        def at(f):
            ms = self.matrix_at(a, f)
            src = ms @ Vector(muzzle_l)
            hit = self.matrix_at(b, f) @ Vector(hit_l)
            fl = (hit - src).length / max(speed, 1.0) * self.fps
            hit = self.matrix_at(b, f + fl) @ Vector(hit_l)
            return (ms @ Matrix.Scale(mpu, 4)).inverted() @ hit
        return at

    # How a planned gun behaves (owner, 2026-10-23: turrets that do not elevate, pop instead of
    # slewing, fire at nothing in long bursts, and shells that home): it trains on its target
    # every frame at its turn rate, both axes, easing in; it fires in bursts with gaps, and only
    # while it is on target, in range and with no other hull in the way; its rounds fly straight.
    ON_TARGET_DEG = 5.0
    MAX_FLIGHT_S = 3.0      # long enough for the story rakes at 1.1-1.3 km (fire-control plan, 2026-10-23)
    MAX_RANGE_M = 3500.0
    BURST_S, GAP_S = (.8, 2.0), (1.0, 2.6)
    # Burst rhythm by weapon (owner, 2026-10-23: Vulcans fire long bursts of the streak; cannons
    # and the Hellbore fire heavy, deliberate shots): (burst seconds, gap seconds). Inside a burst
    # the weapon still fires at its own game cadence, so a Hellbore window holds one or two slugs.
    RHYTHM = {'mg': ((2.0, 4.5), (1.0, 2.2)), 'ac': ((1.0, 2.2), (1.4, 3.0)),
              'heavy': ((3.2, 6.5), (1.5, 3.0)), 'energy': ((1.0, 2.5), (1.2, 2.8))}
    FAMILY = {'lightmg': 'mg', 'lightdualmg': 'mg', 'vulcan': 'mg', 'chaingun': 'mg', 'lightneedler': 'mg',
              'lightac': 'ac', 'heavyac': 'ac', 'mark9': 'ac', 'lightag': 'ac', 'hellbore': 'heavy',
              'ioncannon': 'energy', 'miningblaster': 'energy', 'heavyblaster': 'energy'}
    PREROLL_S = 2.0
    ELEV_MAX_DEG = 65.0      # a mount's elevation travel either way from rest; past it, it cannot bear
    # Bursts walk (owner, 2026-10-23: "we don't need everything to hit; tasteful missing of burst
    # fire weapons around a ship is good"): each burst opens off to one side of the aim point and
    # walks across and past her; a round hits only where its straight line meets her hull, and
    # the rest fly on past her and fade. WALK_R is the walk's reach as a share of her length.
    WALK_R = .3

    def planned(self, ctx, kit, a, b, turret, weapon, muzzle_l, hit_l, speed, feeder, track):
        """Fire turret on a at b over the whole shot as a gun would: returns the round records, or
        None for a mount with no pivot (the caller fires it the old way)."""
        g = kit.guns
        fps = self.fps
        guns = sorted((x for x in ctx.anchors.guns if x.turret == turret), key=g.gun_index)
        if not guns:
            return None
        piv, elev = g.trainers(guns[0].empty)
        if piv is None:
            return None
        if elev is None:
            elev = self.add_elevation(a, piv, guns)
        w = g.WEAPONS[weapon]
        rate = math.radians(w.get('turn', 30.0)) / fps
        F0, F1 = self.span[0] - round(self.PREROLL_S * fps), self.span[1]
        y, p = piv.rotation_euler.z, (elev.rotation_euler.y if elev is not None else 0.0)
        plan, last = {}, None
        for f in range(F0, F1 + 1):
            got = g.aim_solve(ctx, turret, track(f))
            yd, pd = got[2], (got[3] if got[3] is not None else 0.0)
            ey = math.atan2(math.sin(yd - y), math.cos(yd - y))
            ep = pd - p
            # eased onto the target, plus the target's own swing since last frame (a gun layer
            # leads a crossing target rather than chasing it), never past the turn rate
            dy = math.atan2(math.sin(yd - last[0]), math.cos(yd - last[0])) if last else 0.0
            dp = pd - last[1] if last else 0.0
            last = (yd, pd)
            y += max(-rate, min(rate, ey * .3 + dy))
            p += max(-rate, min(rate, ep * .3 + dp))
            lim = math.radians(self.ELEV_MAX_DEG)
            if abs(p) > lim:             # past its mount's travel: it cannot bear, so it is off target
                p = math.copysign(lim, p)
                ep = max(abs(ep), abs(pd) - lim)
            plan[f] = (y, p, math.degrees(max(abs(ey), abs(ep))))
        frames = list(range(F0, F1 + 1))
        g._bake(piv, 'rotation_euler', 2, frames, [plan[f][0] for f in frames])
        if elev is not None:
            g._bake(elev, 'rotation_euler', 1, frames, [plan[f][1] for f in frames])

        def pose(f):
            piv.rotation_euler.z = plan[f][0]
            if elev is not None:
                elev.rotation_euler.y = plan[f][1]
        rnd = random.Random(f'asharu-bursts|{a}|{turret}|{b}')
        burst_s, gap_s = self.RHYTHM.get(self.FAMILY.get(weapon), (self.BURST_S, self.GAP_S))
        bursts, t = [], F0 + rnd.uniform(0, 1.5) * fps
        while t < F1:
            on = rnd.uniform(*burst_s) * fps
            bursts.append((t, t + on))
            t += on + rnd.uniform(*gap_s) * fps
        bm = self.placed[b]['matrix']
        reach = self.placed[b]['length'] / (bm.to_scale()[0] or 1.0) * self.WALK_R
        walks = []
        for t0, t1 in bursts:
            th = rnd.uniform(0, 2 * math.pi)
            r0 = reach * rnd.uniform(.9, 1.6)
            ph = th + math.pi + rnd.uniform(-.8, .8)
            r1 = reach * rnd.uniform(0, 1.3)
            walks.append((t0, t1, (math.cos(th) * r0, math.sin(th) * r0), (math.cos(ph) * r1, math.sin(ph) * r1)))
        imp = self.m['swfx_impacts']
        mpu = self.placed[a]['mpu']
        copy_at = lambda f: self.matrix_at(a, f) @ Matrix.Scale(mpu, 4)
        shots = {}

        def shot(f):
            """(aim point in a's copy space, hit on her hull (local, normal) or None) for a round on f."""
            if f in shots:
                return shots[f]
            ma = self.matrix_at(a, f)
            src = ma @ Vector(muzzle_l)
            fl = (self.matrix_at(b, f) @ Vector(hit_l) - src).length / max(speed, 1.0) * fps
            mb = self.matrix_at(b, f + fl)
            line = (Vector(hit_l) - mb.inverted() @ src).normalized()
            u = line.orthogonal().normalized()
            v = line.cross(u)
            ox = oy = 0.0
            for t0, t1, c, e in walks:
                if t0 <= f < t1:
                    k = (f - t0) / max(t1 - t0, 1.0)
                    ox, oy = c[0] + (e[0] - c[0]) * k, c[1] + (e[1] - c[1]) * k
                    break
            jr = random.Random(f'asharu-walk|{a}|{turret}|{b}|{f}')
            ox += jr.gauss(0, reach * .08)
            oy += jr.gauss(0, reach * .08)
            aim_l = Vector(hit_l) + u * ox + v * oy
            got = imp.ray_to_hull(self.scene, bm, tuple(aim_l - line * reach * 4), tuple(aim_l))
            hit = None
            if got is not None and (Vector(got[0]) - Vector(hit_l)).length < reach * 3:
                hit = (Vector(got[0]), Vector(got[1]))
                aim_l = hit[0]
            shots[f] = ((ma @ Matrix.Scale(mpu, 4)).inverted() @ (mb @ aim_l), hit)
            return shots[f]
        others = [k for k in self.placed if k not in (a, b)]
        clear = {}

        def line_clear(f):
            k = f - f % 6
            if k not in clear:
                src = self.matrix_at(a, k) @ Vector(muzzle_l)
                dst = self.matrix_at(b, k) @ Vector(hit_l)
                seg, ok = dst - src, True
                for o in others:
                    c = self.matrix_at(o, k).translation
                    r = self.placed[o]['length'] * .35
                    u = max(0.0, min(1.0, (c - src).dot(seg) / max(seg.length_squared, 1e-6)))
                    if (src + seg * u - c).length < r:
                        ok = False
                        break
                clear[k] = (ok, seg.length)
            return clear[k]
        stats = {'on': 0, 'off_target': 0, 'range': 0, 'blocked': 0, 'gap': 0}

        def allow(f):
            if not any(t0 <= f < t1 for t0, t1 in bursts):
                stats['gap'] += 1
                return False
            if plan.get(f, (0, 0, 99))[2] > self.ON_TARGET_DEG:
                stats['off_target'] += 1
                return False
            ok, dist = line_clear(f)
            if dist > min(speed * self.MAX_FLIGHT_S, self.MAX_RANGE_M):
                stats['range'] += 1
                return False
            if not ok:
                stats['blocked'] += 1
                return False
            stats['on'] += 1
            return True
        recs = g.fire_weapon(ctx, turret, F0, (F1 - F0) / fps, feeder=feeder, target=lambda f: shot(f)[0],
                             train=False, allow=allow, pose=pose, passes=lambda f: shot(f)[1] is None,
                             after=lambda f, objs: g.world_rounds(objs, lambda k, f=f: copy_at(k).inverted() @ copy_at(f)))
        hits = 0
        for r in recs:
            got = shots.get(r['frame'], (None, None))[1]
            if got is not None and r.get('hit_frame') is not None:
                r['hit_l'], r['hit_n'] = got
                hits += 1
        aims = [math.degrees(plan[f][1]) for f in frames[::24]]
        self.rows.append(f'planned {a} t{turret} -> {b}: {len(recs)} rounds in {len(bursts)} bursts, {hits} hit; '
                         f'skipped {stats}; elevation {"none (traverse only)" if elev is None else f"{min(aims):+.1f}..{max(aims):+.1f} deg"}; '
                         f'traverse {math.degrees(plan[F0][0]):+.1f} -> {math.degrees(plan[F1][0]):+.1f} deg')
        return recs

    def hit_kind(self, a, fired):
        """The impact a's fire draws: the Hellbore's own large jagged HE hit when it fired, an HE hit
        for any other HE weapon, else a kinetic one (swfx_impacts.strike kinds)."""
        ws = {self.weapon(a, int(f.split()[0][1:])) for f in fired}
        return 'hellbore' if 'hellbore' in ws else 'he' if ws & HE else 'kinetic'

    # ------------------------------------------------------------------ projectiles in the world
    # Owner, 2026-10-23 (S6: the Manticore's rounds turned with her): a projectile, once it leaves
    # the muzzle, owes the ship nothing. The kit builds rounds, missiles, flares and their trails in
    # the firing ship's copy space, which moves and turns with her; to_world re-keys each one so it
    # stays where the world put it: from its birth frame on, through the copy's own motion, it is
    # held in the space the ship had at that instant. A mover then flies its line from the muzzle
    # (the ship's motion at the fire instant is in that line: it was aimed at where the target
    # would be); a laid trail stays laid. Impacts belong to the target and ride her.
    INHERIT = re.compile(r'flare|puff|casing')
    PROJECTILE = re.compile(r' (round|bolt|tracer|missile|motor|glare|contrail|flare|canister|puff|casing)\b')

    @contextlib.contextmanager
    def fired(self, a):
        """Every unparented object made inside the block is a's projectile: to_world it after."""
        before = set(bpy.data.objects)
        try:
            yield
        finally:
            self.to_world(a, [o for o in bpy.data.objects if o not in before])

    def to_world(self, a, objs):
        if self.matrix_at is None or a not in self.placed:
            return 0
        import swfx_core
        import swfx_guns
        mpu = self.placed[a]['mpu']
        copy_at = lambda f: self.matrix_at(a, f) @ Matrix.Scale(mpu, 4)
        hi = self.span[1] if self.span else self.now
        n = 0
        for ob in objs:
            if ob.parent is not None or ob.get('world_space'):
                continue
            f0 = self._birth(ob, swfx_core)
            if f0 is None:
                continue
            base = copy_at(f0)
            if self.INHERIT.search(ob.name):
                # thrown, not aimed (a flare, a puff, a casing): it keeps the ship's velocity at the
                # instant it left her (owner, 2026-10-23: flares "stay in relation to the hound")
                v = (self.matrix_at(a, f0 + 1).translation - self.matrix_at(a, f0 - 1).translation) / 2
                delta = lambda k, base=base, v=v, f0=f0: copy_at(k).inverted() @ Matrix.Translation(v * (k - f0)) @ base
            else:
                delta = lambda k, base=base: copy_at(k).inverted() @ base
            fcs = [swfx_core.find_fcurve(ob, 'location', i) for i in range(3)]
            if all(fc is not None and len(fc.keyframe_points) for fc in fcs):
                swfx_guns.world_rounds([ob], delta)
            else:            # laid in place (a contrail, a bolt strike): held there every frame it may show
                m0 = ob.matrix_basis.copy()
                frames = list(range(f0, max(f0, hi) + 2))
                locs, rots, prev = [], [], ob.rotation_euler.copy()
                for k in frames:
                    m = delta(k) @ m0
                    locs.append(m.translation)
                    e = m.to_3x3().normalized().to_euler('XYZ', prev)
                    rots.append(e)
                    prev = e
                for i in range(3):
                    swfx_guns._bake(ob, 'location', i, frames, [v[i] for v in locs])
                    if ob.type != 'LIGHT':
                        swfx_guns._bake(ob, 'rotation_euler', i, frames, [e[i] for e in rots])
                ob['world_space'] = True
            n += 1
        if n:
            self.rows.append(f'world space: {n} of the projectiles {a} made held in the world from their fire frame')
        if self.cam_at is not None and hasattr(swfx_guns, 'screen_clamp'):
            # every round 1-3 px across on screen whatever the camera's distance (owner, 2026-10-23)
            k = swfx_guns.screen_clamp(objs, self.cam_at, to_world=copy_at)
            self.rows.append(f'screen clamp: {k} round frames held to the streak width band')
        return n

    @staticmethod
    def _birth(ob, core):
        """The frame an object left the ship: the f<frame> the kit names it by (its fire or launch
        frame), else its first shown frame, else its first key. The name comes first: a missile's
        contrail shows only from ignition, its body from the launch before it, and holding each
        from its own first shown frame put the trail tens of metres off the missile (owner,
        2026-10-23, S6: "disconnected from its gas trail")."""
        m = re.search(r' f(-?\d{3,})\b', ob.name)
        if m:
            return int(m.group(1))
        vis = core.find_fcurve(ob, 'hide_render')
        shown = [kp.co.x for kp in vis.keyframe_points if kp.co.y < .5] if vis is not None else []
        if shown:
            return int(round(min(shown)))
        fc = core.find_fcurve(ob, 'location', 0)
        if fc is not None and len(fc.keyframe_points):
            return int(round(min(kp.co.x for kp in fc.keyframe_points)))
        return None

    def projectile_check(self):
        """Fail the build if a projectile still rides its ship: every top-level kit object named as
        a round, bolt, missile, trail or flare must have been held in the world (to_world)."""
        bad = [o.name for o in bpy.data.objects if o not in self.before and o.parent is None
               and o.name.startswith('SWFX')
               and self.PROJECTILE.search(o.name) and not o.get('world_space')]
        self.rows.append(f'projectile check: {len(bad)} riding a ship')
        if bad and self.matrix_at is not None and self.span:
            raise RuntimeError(f'{len(bad)} projectiles still in the space of the ship that fired them '
                               f'(owner, 2026-10-23: projectiles fly in world space), e.g. {bad[:6]}')
        return bad

    def add_elevation(self, a, piv, guns):
        """A mount the build gave only a traverse pivot (the Hound's WS 002): an elevation joint made
        here so its gun can lift onto a target above or below her (owner, 2026-10-23, S6d: the
        Hound's gun must angle up to track). It sits on the pivot's train axis at the barrels'
        height and carries everything the pivot carried, so the whole head lifts. Only on a ship
        with her own hull copy (training a shared turret trains every copy). A stopgap until the
        build has a joint of its own: a guarded fallback that warns (every Hound build has a real
        joint since 87356990e, which trainers() finds first)."""
        ship = self.ship(a)
        if ship is None or not ship.own_hull:
            return None
        import swfx_guns
        inv_piv = swfx_guns.anchor_matrix(piv).inverted()
        zs = [(inv_piv @ swfx_guns.anchor_matrix(g.empty).translation).z for g in guns]
        z = sum(zs) / len(zs) if zs else 0.0
        name = piv.name.replace(' pivot', ' elevation') if ' pivot' in piv.name else piv.name + ' elevation'
        elev = bpy.data.objects.new(name, None)
        for c in piv.users_collection:
            c.objects.link(elev)
        elev.parent = piv
        elev.matrix_parent_inverse = Matrix()
        # its own X along the bore (flattened onto the train plane), so it lifts about the axis
        # across the barrels, as aim_solve turns an elevation about its local Y
        bore = Vector((0.0, 0.0, 0.0))
        for g in guns:
            bore += (inv_piv @ swfx_guns.anchor_matrix(g.empty)).to_3x3() @ Vector((1, 0, 0))
        bore.z = 0.0
        bx = bore.normalized() if bore.length > 1e-6 else Vector((1, 0, 0))
        R = Matrix((bx, Vector((0, 0, 1)).cross(bx), Vector((0, 0, 1)))).transposed().to_4x4()
        elev.matrix_basis = Matrix.Translation((0.0, 0.0, z)) @ R
        elev['elevation_synthesized'] = True
        inv = elev.matrix_basis.inverted()
        for ch in [c for c in piv.children if c is not elev]:
            ch.parent = elev
            ch.matrix_parent_inverse = inv @ ch.matrix_parent_inverse
        msg = (f'WARNING: elevation joint made for the {piv.name} of {a} at {z:.2f} u above its train '
               f'axis: the build has none (the Hound builds have had one since 87356990e; update this build)')
        self.rows.append(msg)
        self.log(f'ASHARU kit {msg}')
        return elev

    def strikes_at(self, key, hits, kind):
        """Rounds arriving on key's hull where each met it: hits are (frame, at_l, normal)."""
        imp = self.m['swfx_impacts']
        ship = self.ship(key)
        if ship is None:
            return
        out = {}
        with ship.kit() as (ctx, kit):
            for f, at_l, normal in hits:
                where = imp.strike(ctx, f, tuple(at_l), tuple(normal), kind=kind)
                out[where] = out.get(where, 0) + 1
        self.rows.append(f'strikes on {key}: {out} (where each round met her)')

    def strikes(self, key, frames, at_l, normal, kind):
        """Rounds arriving on key's hull (or shield) around at_l on these frames."""
        imp = self.m['swfx_impacts']
        ship = self.ship(key)
        if ship is None:
            return
        out = {}
        with ship.kit() as (ctx, kit):
            for f in frames:
                rnd = random.Random(f'asharu-strikes|{key}|{f}')      # a strike lands where it did on any frame
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
        if self.matrix_at is not None and self.span:
            # A seeker flies to where she will be when it arrives, in the world: the flights are
            # held in the shooter's space as it stood at launch (to_world), so the target goes
            # into that space, then back through the space salvo works in.
            f0 = min(f.frame for f in fl)
            launch = self.matrix_at(a, f0) @ Matrix.Scale(self.placed[a]['mpu'], 4)
            at_end = self.matrix_at(b, max(f.end['frame'] for f in fl if f.end)).translation
            target = mp.space_of(shooter) @ (launch.inverted() @ at_end)
            fl = mp.salvo('harpoon', starts, target, mp.space_of(shooter), frames=[f + shift for f in frames],
                          target_name=b, width=.18)
        with self.fired(a), shooter.kit() as (ctx, kit):
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
        # No flare muzzles on her build (a Hound): her flanks, so the kit's flares draw, not the stand-ins'
        anchors = pd.flare_anchors(ship) or pd.flank_anchors(ship)
        if not anchors:
            return False
        fl = pd.plan_flares(anchors, self.now - round(1.2 * self.fps))
        with self.fired(key), ship.kit() as (ctx, kit):
            # small bright points with thin smoke (coordinator's default, 2026-10-23, pending the owner):
            # a glare of 0.6 m, a faint grey trail, a dimmer light
            pd.key_flares(ctx, fl, self.space(key), ship.mpu, glare_m=.6, light_w=4.0e3,
                          trail=dict(trail_glow=0.0, trail_alpha=.3, trail_r_m=.45, trail_spread=1.2,
                                     trail=(.55, .55, .55)))
        self.rows.append(f'flares {key}: {len(fl)}')
        return True
