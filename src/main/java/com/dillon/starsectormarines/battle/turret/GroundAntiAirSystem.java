package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierRules;

import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.air.AirLocomotion;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.GuidedMissile;
import com.dillon.starsectormarines.battle.combat.UnguidedMissileAim;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.AirMissileSpec;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleAirFire;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipPointDefence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Serial aimed flak and physical guided missiles against hostile airborne craft. */
public final class GroundAntiAirSystem {
    private static final float FLIGHT_SECONDS = .15f;
    private final List<Round> rounds = new ArrayList<>();
    private record Round(long shooter, long target, float damage, float penetration, int arrival,
                         float burstRadius, float burstX, float burstY) {
        Round(long shooter, long target, float damage, float penetration, int arrival) {
            this(shooter, target, damage, penetration, arrival, 0f, 0f, 0f);
        }
    }
    private static final long[] NO_AIRCRAFT = new long[0];
    /** One round on its way to a low-flying hull, landed through the warships' own hit rules. */
    private record HullRound(long shooter, Faction side, Warship target, float fromX, float fromY,
                             float damage, float penetration, int arrival) {}
    private final List<HullRound> hullRounds = new ArrayList<>();
    /** The hull each mount's salvo was loosed at, by mount id: the rest of the salvo follows the first round to it. */
    private final Map<Long, Warship> hullSalvos = new HashMap<>();
    /** How high a low-flying hull's rounds are drawn bursting, in cells: about a strafing gunship's height. */
    private static final float LOW_HULL_HEIGHT_CELLS = 4f;
    private final List<Missile> missiles = new ArrayList<>();
    private static final class Missile {
        final long shooter;
        long target;
        final WeaponDef weapon;
        final ShotEvent shot;
        final GuidedMissile flight;
        boolean clearingLaunchHill;
        Missile(long shooter, long target, WeaponDef weapon, ShotEvent shot, GuidedMissile flight, boolean summit) {
            this.shooter = shooter; this.target = target; this.weapon = weapon;
            this.shot = shot; this.flight = flight; clearingLaunchHill = summit;
        }
    }

    public void tick(BattleSimulation sim) {
        run(sim, sim.getAirEntityIds());
    }

    /**
     * The same pass with no aircraft in it, for a battle whose air layer the host owns: the mounts still engage the
     * swarm's fliers, which are the battle's own bodies whoever owns the aircraft.
     */
    public void tickFliersOnly(BattleSimulation sim) {
        run(sim, NO_AIRCRAFT);
    }

    private void run(BattleSimulation sim, long[] aircraft) {
        advanceMissiles(sim);
        for (var it = rounds.iterator(); it.hasNext();) {
            Round round = it.next();
            if (sim.getSimTickIndex() < round.arrival) continue;
            if (sim.world().isAlive(round.target)) {
                sim.applyAimedDamage(round.target, round.shooter, round.damage, round.penetration);
                sim.telemetry().recordRoundHit(round.shooter);
            }
            if (round.burstRadius > 0f) airburst(sim, round);
            it.remove();
        }
        for (var it = hullRounds.iterator(); it.hasNext();) {
            HullRound round = it.next();
            if (sim.getSimTickIndex() < round.arrival) continue;
            sim.warships().groundAaHit(round.side, round.fromX, round.fromY, round.target, round.damage,
                    round.penetration);
            sim.telemetry().recordRoundHit(round.shooter);
            it.remove();
        }
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            if (!sim.world().isAlive(id) || !sim.turretState().isTurret(id)) continue;
            StructureDef structure = sim.turretState().structure(id);
            if (structure.targetRole != TurretRole.AA) continue;
            tickMount(sim, id, structure, aircraft);
        }
    }

    private void tickMount(BattleSimulation sim, long id, StructureDef structure, long[] aircraft) {
        var state = sim.turretState();
        var weapon = structure.mount.weapon;
        // A mount in a fort's anti-air yard obeys the yard: silent while it is wrecked, contested or another side's, slow
        // without the fort's power.
        Fort fort = sim.forts().airMountFort(id);
        float rate = fort == null ? 1f : fort.airDefenceRate(sim.identity().faction(id));
        if (rate <= 0f) {
            sim.world().setTargetId(id, 0L);
            state.setBurstRemaining(id, 0);
            return;
        }
        float dt = BattleSimulation.TICK_DT * rate;
        state.setRecoilTimer(id, state.recoilTimer(id) + dt);
        float cooldown = Math.max(0f, sim.world().cooldownTimer(id) - dt);
        sim.world().setCooldownTimer(id, cooldown);
        Warship salvo = hullSalvos.get(id);
        if (salvo != null) {
            if (state.burstRemaining(id) > 0 && salvo.inGroundAaReach()) {
                fireAtHull(sim, id, structure, salvo, dt, cooldown);
                return;
            }
            hullSalvos.remove(id);
        }
        long target = 0L;
        float nearest = weapon.range * weapon.range;
        for (long candidate : aircraft) {
            if (!sim.world().isAlive(candidate)) continue;
            var mission = sim.world().mission(candidate);
            if (mission == null || mission.destroyed || !mission.isOnMap() || !AirLocomotion.of(mission.state).airborne()
                    || !sim.identity().faction(id).hostileTo(sim.world().airFaction(candidate))) continue;
            float dx = sim.world().x(candidate) - sim.world().x(id);
            float dy = sim.world().y(candidate) - sim.world().y(id);
            float distance = dx * dx + dy * dy;
            if (distance > nearest || distance < weapon.minRange * weapon.minRange) continue;
            // A summit mount clears its supporting hill. Beyond it, structural air LOS applies;
            // a roof directly below the aircraft is not an intervening building.
            if (!airLineClear(sim, fort, id, candidate)) continue;
            target = candidate;
            nearest = distance;
        }
        if (target == 0L && (weapon.airMissile == null || state.burstRemaining(id) == 0)) {
            long flier = nearestFlier(sim, id, weapon, fort);
            if (flier != 0L) {
                fireAtFlier(sim, id, structure, flier, dt, cooldown);
                return;
            }
            Warship hull = nearestLowHull(sim, id, weapon, fort);
            if (hull != null) {
                hullSalvos.put(id, hull);
                fireAtHull(sim, id, structure, hull, dt, cooldown);
                return;
            }
        }
        sim.world().setTargetId(id, target);
        if (target == 0L && (weapon.airMissile == null || state.burstRemaining(id) == 0)) {
            state.setBurstRemaining(id, 0); return;
        }
        // A committed native salvo is noninterruptible: without a new target its remaining rounds leave on the held bearing.
        float held = (float) Math.toRadians(state.facingDegrees(id) + 90f);
        float aimX = target == 0 ? sim.world().x(id) + (float) Math.cos(held) * weapon.range : sim.world().x(target);
        float aimY = target == 0 ? sim.world().y(id) + (float) Math.sin(held) * weapon.range : sim.world().y(target);
        float aimZ = target == 0 ? 4f : AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(target));
        if (target != 0 && weapon.airMissile != null && !weapon.airMissile.guided()) {
            var body = sim.world().kinematics(target);
            if (body != null) {
                float lead = UnguidedMissileAim.interceptSeconds(aimX - sim.world().x(id),
                        aimY - sim.world().y(id), aimZ, body.vx, body.vy, weapon.airMissile);
                aimX += body.vx * lead;
                aimY += body.vy * lead;
            }
        }
        float bearing = target == 0 ? state.facingDegrees(id)
                : TurretAim.bearingTo(sim.world().x(id), sim.world().y(id), aimX, aimY);
        float facing = TurretAim.slewToward(state.facingDegrees(id), bearing, structure.mount.turnRateDegPerSec * dt);
        state.setFacingDegrees(id, facing);
        boolean burst = state.burstRemaining(id) > 0;
        state.setBurstTimer(id, Math.max(0f, state.burstTimer(id) - dt));
        float clockTolerance = weapon.airMissile == null ? 0f : .0001f;
        if ((burst ? state.burstTimer(id) > clockTolerance : cooldown > clockTolerance)
                || (!burst || weapon.airMissile == null)
                && Math.abs(TurretAim.shortestAngleDelta(facing, bearing)) > TurretAim.FIRE_ARC_DEG) return;
        int release = burst ? weapon.burstCount - state.burstRemaining(id) : 0;
        state.setBurstRemaining(id, burst ? state.burstRemaining(id) - 1 : weapon.burstCount - 1);
        state.setBurstTimer(id, weapon.burstSpacing);
        if (weapon.airMissile != null) {
            if (state.burstRemaining(id) == 0) sim.world().setCooldownTimer(id, weapon.cooldown);
        } else if (!burst) sim.world().setCooldownTimer(id, weapon.cooldown);
        state.setRecoilTimer(id, 0f);
        var muzzle = TurretMountGeometry.muzzle(sim.world().x(id), sim.world().y(id), facing, structure.mount, release);
        if (weapon.guidance != null || weapon.airMissile != null) {
            if (target != 0) sim.world().mission(target).lastMissileLaunchTick = sim.getSimTickIndex();
            float launchFacing = facing + (weapon.airMissile != null && weapon.airMissile.launchSpreadDeg() > 0f
                    ? weapon.airMissile.launchAngleOffset(sim.random().nextFloat()) : 0f);
            var flight = weapon.airMissile == null
                    ? new GuidedMissile(muzzle.x(), muzzle.y(), facing, weapon.roundVelocity, weapon.guidance)
                    : new GuidedMissile(muzzle.x(), muzzle.y(), launchFacing, aimX, aimY, aimZ, weapon.airMissile);
            var shot = new ShotEvent(muzzle.x(), muzzle.y(), 0f, muzzle.x(), muzzle.y(), 0f,
                    false, sim.identity().faction(id), flight.remaining, structure, null, null, null,
                    0f, false, null, id);
            shot.guidedFlight = flight;
            shot.flightTargetId = target;
            var terrain = sim.getTopology().terrainSurface();
            boolean summit = terrain != null && terrain.isFullSummit(sim.world().cellX(id), sim.world().cellY(id));
            missiles.add(new Missile(id, target, weapon, shot, flight, summit));
            sim.telemetry().recordRoundFired(id);
            sim.postShot(shot);
            return;
        }
        boolean hit = sim.random().nextFloat() < weapon.accuracy;
        // A weapon may set its own flight time: a lobbed spore clump hangs in the air, a flak shell does not.
        float flight = weapon.flightSec > 0f ? weapon.flightSec : FLIGHT_SECONDS;
        float x = sim.world().x(target), y = sim.world().y(target);
        if (!hit) { x += 2f; y += 1f; }
        sim.telemetry().recordRoundFired(id);
        if (hit) {
            rounds.add(new Round(id, target, weapon.damage, weapon.penetration,
                    sim.getSimTickIndex() + Math.round(flight / dt)));
        }
        sim.postShot(new ShotEvent(muzzle.x(), muzzle.y(), 0f, x, y,
                AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(target)), hit,
                sim.identity().faction(id), flight, structure, null, null, null,
                0f, hit, null, id));
    }

    /**
     * The nearest live Rav flier the mount can reach, or 0. Aircraft come first: this is asked only of a mount with
     * no aircraft to shoot at.
     */
    private static long nearestFlier(BattleSimulation sim, long id, WeaponDef weapon, Fort fort) {
        if (!sim.identity().faction(id).hostileTo(Faction.DEFENDER)) return 0L;
        float x = sim.world().x(id), y = sim.world().y(id);
        long best = 0L;
        float nearest = weapon.range * weapon.range;
        float min = weapon.minRange * weapon.minRange;
        for (HarrierPack pack : sim.swarmFliers().packs()) {
            for (HarrierFlight m : pack.members) {
                if (!sim.world().isAlive(m.id)) continue;
                float dx = m.body.x - x, dy = m.body.y - y;
                float d = dx * dx + dy * dy;
                if (d > nearest || d < min || d == nearest && best != 0L && m.id > best) continue;
                if (!airLineClear(sim, fort, id, m.id)) continue;
                best = m.id;
                nearest = d;
            }
        }
        return best;
    }

    /**
     * Engages a flier as the mount engages a craft, on the same cooldown, burst and slew, with the round's flight
     * solved against the flier's own velocity. A guided round is resolved as a timed round rather than flown: a
     * flier carries no airframe for the missile path to home on. The hit bursts among the flier's packmates
     * ({@link #airburst}), which is what a tight, committed pack pays for.
     */
    private void fireAtFlier(BattleSimulation sim, long id, StructureDef structure, long flier, float dt,
                             float cooldown) {
        var state = sim.turretState();
        WeaponDef weapon = structure.mount.weapon;
        sim.world().setTargetId(id, flier);
        float ox = sim.world().x(id), oy = sim.world().y(id);
        AirBody body = sim.world().kinematics(flier);
        float fx = sim.world().x(flier), fy = sim.world().y(flier);
        float distance = (float) Math.hypot(fx - ox, fy - oy);
        float speed = weapon.airMissile != null ? weapon.airMissile.cruiseSpeed() : weapon.roundVelocity;
        float flight = weapon.flightSec > 0f ? weapon.flightSec : speed > 0f ? distance / speed : FLIGHT_SECONDS;
        flight = Math.max(BattleSimulation.TICK_DT, flight);
        float aimX = fx + (body == null ? 0f : body.vx * flight);
        float aimY = fy + (body == null ? 0f : body.vy * flight);
        float bearing = TurretAim.bearingTo(ox, oy, aimX, aimY);
        float facing = TurretAim.slewToward(state.facingDegrees(id), bearing, structure.mount.turnRateDegPerSec * dt);
        state.setFacingDegrees(id, facing);
        boolean burst = state.burstRemaining(id) > 0;
        state.setBurstTimer(id, Math.max(0f, state.burstTimer(id) - dt));
        if (burst ? state.burstTimer(id) > 0f : cooldown > 0f) return;
        if (!burst && Math.abs(TurretAim.shortestAngleDelta(facing, bearing)) > TurretAim.FIRE_ARC_DEG) return;
        int release = burst ? weapon.burstCount - state.burstRemaining(id) : 0;
        state.setBurstRemaining(id, burst ? state.burstRemaining(id) - 1 : weapon.burstCount - 1);
        state.setBurstTimer(id, weapon.burstSpacing);
        if (!burst) sim.world().setCooldownTimer(id, weapon.cooldown);
        state.setRecoilTimer(id, 0f);
        var muzzle = TurretMountGeometry.muzzle(ox, oy, facing, structure.mount, release);
        HarrierFlight own = sim.swarmFliers().flight(flier);
        float z = own == null ? HarrierRules.LOITER_HEIGHT_CELLS : own.altitude * HarrierRules.LOITER_HEIGHT_CELLS;
        boolean hit = sim.random().nextFloat() < weapon.accuracy;
        float x = aimX, y = aimY;
        if (!hit) { x += 2f; y += 1f; }
        sim.telemetry().recordRoundFired(id);
        float burstRadius = Math.max(HarrierRules.FLAK_MIN_BURST, Math.max(weapon.aoeRadius,
                weapon.airMissile != null ? weapon.airMissile.blastRadius() : 0f));
        if (hit) {
            rounds.add(new Round(id, flier, weapon.damage, weapon.penetration,
                    sim.getSimTickIndex() + Math.max(1, Math.round(flight / BattleSimulation.TICK_DT)),
                    burstRadius, aimX, aimY));
        }
        sim.postShot(new ShotEvent(muzzle.x(), muzzle.y(), 0f, x, y, z, hit,
                sim.identity().faction(id), flight, structure, null, null, null,
                0f, hit, null, id));
    }

    /**
     * The nearest hostile hull flying low enough for the mount to reach ({@link Warship#inGroundAaReach}), or
     * {@code null}. Aircraft and fliers come first: this is asked only of a mount with neither to shoot at.
     */
    private static Warship nearestLowHull(BattleSimulation sim, long id, WeaponDef weapon, Fort fort) {
        Faction side = sim.identity().faction(id);
        float x = sim.world().x(id), y = sim.world().y(id);
        Warship best = null;
        float nearest = weapon.range * weapon.range;
        float min = weapon.minRange * weapon.minRange;
        for (Warship ship : sim.warships().ships()) {
            if (!ship.inGroundAaReach() || !side.hostileTo(ship.faction)) continue;
            float dx = ship.centerX() - x, dy = ship.centerY() - y;
            float d = dx * dx + dy * dy;
            if (d > nearest || d < min) continue;
            if (!airLineClear(sim, fort, id, (int) ship.centerX(), (int) ship.centerY())) continue;
            best = ship;
            nearest = d;
        }
        return best;
    }

    /**
     * Engages a low-flying hull as {@link #fireAtFlier} engages a flier: the same cooldown, burst and slew, the round's
     * flight solved against the hull's own velocity, and a guided round resolved as a timed one, which the hull's point
     * defence may stop ({@code WarshipPointDefence.groundMissileStopChance}). The hit lands through
     * the warships' rules ({@code WarshipSystem.groundAaHit}), so the hull's shield and plate answer it.
     */
    private void fireAtHull(BattleSimulation sim, long id, StructureDef structure, Warship hull, float dt,
                            float cooldown) {
        var state = sim.turretState();
        WeaponDef weapon = structure.mount.weapon;
        sim.world().setTargetId(id, 0L);
        float ox = sim.world().x(id), oy = sim.world().y(id);
        float hx = hull.centerX(), hy = hull.centerY();
        float distance = (float) Math.hypot(hx - ox, hy - oy);
        float speed = weapon.airMissile != null ? weapon.airMissile.cruiseSpeed() : weapon.roundVelocity;
        float flight = weapon.flightSec > 0f ? weapon.flightSec : speed > 0f ? distance / speed : FLIGHT_SECONDS;
        flight = Math.max(BattleSimulation.TICK_DT, flight);
        float aimX = hx + hull.velocityX() * flight;
        float aimY = hy + hull.velocityY() * flight;
        float bearing = TurretAim.bearingTo(ox, oy, aimX, aimY);
        float facing = TurretAim.slewToward(state.facingDegrees(id), bearing, structure.mount.turnRateDegPerSec * dt);
        state.setFacingDegrees(id, facing);
        boolean burst = state.burstRemaining(id) > 0;
        state.setBurstTimer(id, Math.max(0f, state.burstTimer(id) - dt));
        if (burst ? state.burstTimer(id) > 0f : cooldown > 0f) return;
        if (!burst && Math.abs(TurretAim.shortestAngleDelta(facing, bearing)) > TurretAim.FIRE_ARC_DEG) return;
        int release = burst ? weapon.burstCount - state.burstRemaining(id) : 0;
        state.setBurstRemaining(id, burst ? state.burstRemaining(id) - 1 : weapon.burstCount - 1);
        state.setBurstTimer(id, weapon.burstSpacing);
        if (!burst) sim.world().setCooldownTimer(id, weapon.cooldown);
        state.setRecoilTimer(id, 0f);
        var muzzle = TurretMountGeometry.muzzle(ox, oy, facing, structure.mount, release);
        boolean hit = sim.random().nextFloat() < weapon.accuracy;
        // A guided round is the hull's point defence's to stop; it goes wide when it does.
        if (hit && weapon.airMissile != null && weapon.airMissile.guided()
                && sim.random().nextFloat() < WarshipPointDefence.groundMissileStopChance(hull)) hit = false;
        float x = aimX, y = aimY;
        if (!hit) { x += 2f; y += 1f; }
        Faction side = sim.identity().faction(id);
        sim.telemetry().recordRoundFired(id);
        if (hit) {
            hullRounds.add(new HullRound(id, side, hull, muzzle.x(), muzzle.y(), weapon.damage, weapon.penetration,
                    sim.getSimTickIndex() + Math.max(1, Math.round(flight / BattleSimulation.TICK_DT))));
        }
        sim.postShot(new ShotEvent(muzzle.x(), muzzle.y(), 0f, x, y, LOW_HULL_HEIGHT_CELLS, hit,
                side, flight, structure, null, null, null, 0f, hit, null, id));
    }

    /** A hit on a flier bursts among the fliers round it, each taking {@link HarrierRules#BURST_SHARE} of it. */
    private static void airburst(BattleSimulation sim, Round round) {
        float r2 = round.burstRadius * round.burstRadius;
        for (HarrierPack pack : sim.swarmFliers().packs()) {
            for (HarrierFlight m : pack.members) {
                if (m.id == round.target || !sim.world().isAlive(m.id)) continue;
                float dx = m.body.x - round.burstX, dy = m.body.y - round.burstY;
                if (dx * dx + dy * dy > r2) continue;
                sim.applyAimedDamage(m.id, round.shooter, round.damage * HarrierRules.BURST_SHARE, round.penetration);
                sim.telemetry().recordRoundHit(round.shooter);
            }
        }
    }

    /**
     * The air layer as a vehicle gun that reaches the air sees it ({@link VehicleAirFire}): the same eligibility an
     * emplacement's pick applies (airborne, on the map, hostile, in reach, a clear line up), and a round rolled and
     * landed the way this pass lands an emplacement's flak.
     */
    public VehicleAirFire vehicleAirFire(BattleSimulation sim) {
        return new VehicleAirFire() {
            @Override
            public long pick(Faction side, float x, float y, float range, long held) {
                if (held != 0L && valid(side, x, y, range, held)) return held;
                long best = 0L;
                float nearest = range * range;
                for (long craft : sim.getAirEntityIds()) {
                    if (!valid(side, x, y, range, craft)) continue;
                    float dx = sim.world().x(craft) - x, dy = sim.world().y(craft) - y;
                    float distance = dx * dx + dy * dy;
                    if (distance < nearest || distance == nearest && craft < best) {
                        best = craft;
                        nearest = distance;
                    }
                }
                return best;
            }

            @Override
            public boolean valid(Faction side, float x, float y, float range, long craft) {
                if (!sim.world().isAlive(craft)) return false;
                var mission = sim.world().mission(craft);
                if (mission == null || mission.destroyed || !mission.isOnMap()
                        || !AirLocomotion.of(mission.state).airborne()
                        || !side.hostileTo(sim.world().airFaction(craft))) return false;
                float dx = sim.world().x(craft) - x, dy = sim.world().y(craft) - y;
                if (dx * dx + dy * dy > range * range) return false;
                return TurretAim.summitAirFireLineClear(sim.getGrid(), sim.getTopology(), (int) Math.floor(x),
                        (int) Math.floor(y), (int) sim.world().x(craft), (int) sim.world().y(craft), 4f);
            }

            @Override
            public void fire(long shooter, Faction side, StructureDef structure, long craft,
                             float muzzleX, float muzzleY) {
                WeaponDef weapon = structure.mount.weapon;
                boolean hit = sim.random().nextFloat() < weapon.accuracy;
                float flight = weapon.flightSec > 0f ? weapon.flightSec : FLIGHT_SECONDS;
                float x = sim.world().x(craft), y = sim.world().y(craft);
                if (!hit) { x += 2f; y += 1f; }
                sim.telemetry().recordRoundFired(shooter);
                if (hit) {
                    rounds.add(new Round(shooter, craft, weapon.damage, weapon.penetration,
                            sim.getSimTickIndex() + Math.max(1, Math.round(flight / BattleSimulation.TICK_DT))));
                }
                sim.postShot(new ShotEvent(muzzleX, muzzleY, 0f, x, y,
                        AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(craft)), hit,
                        side, flight, structure, null, null, null, 0f, hit, null, shooter));
            }
        };
    }

    /**
     * Whether the mount has a clear line up to the craft. A fort's yard mount fires over its own works; every other
     * mount, and anything standing outside a fort's works, blocks as ordinary structure.
     */
    private static boolean airLineClear(BattleSimulation sim, Fort fort, long mount, long craft) {
        return airLineClear(sim, fort, mount, (int) sim.world().x(craft), (int) sim.world().y(craft));
    }

    private static boolean airLineClear(BattleSimulation sim, Fort fort, long mount, int toX, int toY) {
        int fromX = sim.world().cellX(mount), fromY = sim.world().cellY(mount);
        FortFrame frame = fort == null ? null : fort.frame();
        if (frame == null) {
            return TurretAim.summitAirFireLineClear(sim.getGrid(), sim.getTopology(), fromX, fromY, toX, toY, 4f);
        }
        return TurretAim.fortAirFireLineClear(sim.getGrid(), sim.getTopology(), fromX, fromY, toX, toY, 4f,
                frame::isWorks);
    }

    private void advanceMissiles(BattleSimulation sim) {
        for (var it = missiles.iterator(); it.hasNext();) {
            Missile missile = it.next();
            var flight = missile.flight;
            var spec = missile.weapon.airMissile;
            if (spec != null && spec.guided() && spec.retargetCloser()) {
                float nearest = Float.POSITIVE_INFINITY;
                long selected = 0;
                for (long candidate : sim.getAirEntityIds()) {
                    if (!eligible(sim, missile, candidate, true)) continue;
                    float dx = sim.world().x(candidate) - flight.x, dy = sim.world().y(candidate) - flight.y;
                    float dz = AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(candidate)) - flight.z;
                    float distance = dx * dx + dy * dy + dz * dz;
                    if (distance < nearest || distance == nearest && candidate == missile.target) {
                        selected = candidate; nearest = distance;
                    }
                }
                missile.target = selected;
            }
            missile.shot.flightTargetId = missile.target;
            var mission = sim.world().isAlive(missile.target) ? sim.world().mission(missile.target) : null;
            boolean tracking = sim.world().isAlive(missile.target) && mission != null && !mission.destroyed
                    && mission.isOnMap() && AirLocomotion.of(mission.state).airborne()
                    && (spec == null || eligible(sim, missile, missile.target, true));
            float tx = tracking ? sim.world().x(missile.target) : flight.x;
            float ty = tracking ? sim.world().y(missile.target) : flight.y;
            float tz = tracking ? AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(missile.target)) : flight.z;
            // Legacy guidance keeps its short lead; physical families solve a constant-velocity intercept.
            var body = tracking ? sim.world().kinematics(missile.target) : null;
            float lead = !tracking ? 0f : spec == null
                    ? Math.min(.5f, (float) Math.hypot(tx - flight.x, ty - flight.y) / missile.weapon.roundVelocity)
                    : GuidedMissile.leadSeconds(tx - flight.x, ty - flight.y, tz - flight.z,
                            body == null ? 0f : body.vx, body == null ? 0f : body.vy,
                            spec.cruiseSpeed(), flight.remaining);
            flight.advance(BattleSimulation.TICK_DT, tracking, tx + (body == null ? 0f : body.vx * lead),
                    ty + (body == null ? 0f : body.vy * lead), tz);
            if (spec == null && tracking && flight.contacts(tx, ty, tz, sim.world().airframe(missile.target).targetRadiusCells())) {
                sim.applyAimedDamage(missile.target, missile.shooter, missile.weapon.damage, missile.weapon.penetration);
                sim.telemetry().recordRoundHit(missile.shooter);
                flight.remaining = 0f;
                missile.shot.flightImpact = true;
            }
            if (spec != null) {
                // Leaving the simulated battlefield ends a miss quietly, without an invented edge detonation.
                int flightCx = (int) Math.floor(flight.x), flightCy = (int) Math.floor(flight.y);
                resolveFamilyContact(sim, missile);
                if (!missile.shot.flightImpact && !sim.getGrid().inBounds(flightCx, flightCy)) flight.remaining = 0f;
            }
            // Once at flight height, the round clears low structures just as the aircraft does.
            // A summit launch clears its own supporting mass only until it leaves that mass.
            int cx = (int) Math.floor(flight.x), cy = (int) Math.floor(flight.y);
            if (missile.clearingLaunchHill && !TurretAim.terrainMassAt(sim.getGrid(), sim.getTopology(), cx, cy))
                missile.clearingLaunchHill = false;
            if (!missile.shot.flightImpact && flight.z < 1f && !missile.clearingLaunchHill && sim.getGrid().inBounds(cx, cy)
                    && sim.getGrid().blocksStructuralLineOfSight(cx, cy)) {
                flight.remaining = 0f;
                missile.shot.flightImpact = true;
                if (spec != null && spec.blastRadius() > 0f) applyFamilyBlast(sim, missile, 1f);
            }
            missile.shot.toX = flight.x; missile.shot.toY = flight.y; missile.shot.toZ = flight.z;
            if (flight.remaining <= 0f) {
                missile.shot.flightResolved = true;
                missile.shot.lifetime = 0f;
                it.remove();
            }
        }
    }
    private static boolean eligible(BattleSimulation sim, Missile missile, long candidate, boolean hostileOnly) {
        if (!sim.world().isAlive(candidate)) return false;
        var mission = sim.world().mission(candidate);
        return mission != null && !mission.destroyed && mission.isOnMap()
                && (missile.weapon.airMissile == null || sim.getGrid().inBounds(
                        (int) Math.floor(sim.world().x(candidate)), (int) Math.floor(sim.world().y(candidate))))
                && AirLocomotion.of(mission.state).airborne()
                && (!hostileOnly || missile.shot.shooterFaction.hostileTo(sim.world().airFaction(candidate)));
    }

    /** Contact includes the last in-bounds portion of a boundary-crossing craft, unlike acquisition. */
    private static boolean eligibleContact(BattleSimulation sim, Missile missile, long candidate, boolean hostileOnly) {
        if (!sim.world().isAlive(candidate)) return false;
        var mission = sim.world().mission(candidate);
        if (mission == null || mission.destroyed || !mission.isOnMap()
                || !AirLocomotion.of(mission.state).airborne()
                || hostileOnly && !missile.shot.shooterFaction.hostileTo(sim.world().airFaction(candidate))) return false;
        var body = sim.world().kinematics(candidate);
        return sim.getGrid().inBounds((int) Math.floor(sim.world().x(candidate)), (int) Math.floor(sim.world().y(candidate)))
                || body != null && sim.getGrid().inBounds((int) Math.floor(body.previousX), (int) Math.floor(body.previousY));
    }

    private static void resolveFamilyContact(BattleSimulation sim, Missile missile) {
        var spec = missile.weapon.airMissile;
        var flight = missile.flight;
        long contacted = 0;
        float first = Float.POSITIVE_INFINITY;
        for (long candidate : sim.getAirEntityIds()) {
            if (!eligibleContact(sim, missile, candidate, !spec.friendlyFire())) continue;
            float x = sim.world().x(candidate), y = sim.world().y(candidate);
            float z = AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(candidate));
            boolean hostile = missile.shot.shooterFaction.hostileTo(sim.world().airFaction(candidate));
            float radius = sim.world().airframe(candidate).targetRadiusCells() + (hostile ? spec.fuseRadius() : 0f);
            var body = sim.world().kinematics(candidate);
            float stepFraction = Math.min(1f, flight.stepSeconds / BattleSimulation.TICK_DT);
            float fraction = body == null ? flight.contactFraction(x, y, z, radius)
                    : flight.contactFractionMoving(body.previousX, body.previousY, body.previousAltitude,
                            body.previousX + (x - body.previousX) * stepFraction,
                            body.previousY + (y - body.previousY) * stepFraction,
                            body.previousAltitude + (z - body.previousAltitude) * stepFraction, radius);
            float contactX = flight.previousX + (flight.x - flight.previousX) * fraction;
            float contactY = flight.previousY + (flight.y - flight.previousY) * fraction;
            if (Float.isFinite(fraction) && sim.getGrid().inBounds((int) Math.floor(contactX), (int) Math.floor(contactY))
                    && fraction < first) { first = fraction; contacted = candidate; }
        }
        if (contacted == 0) return;
        missile.shot.flightContactId = contacted;
        // Stop at the first physical contact, even when a tick would otherwise cross the entire hull.
        flight.x = flight.previousX + (flight.x - flight.previousX) * first;
        flight.y = flight.previousY + (flight.y - flight.previousY) * first;
        flight.z = flight.previousZ + (flight.z - flight.previousZ) * first;
        flight.remaining = 0;
        missile.shot.flightImpact = true;
        if (spec.blastRadius() == 0f) {
            sim.applyAimedDamage(contacted, missile.shooter, missile.weapon.damage, missile.weapon.penetration);
            sim.telemetry().recordRoundHit(missile.shooter);
            return;
        }
        applyFamilyBlast(sim, missile, first * Math.min(1f, flight.stepSeconds / BattleSimulation.TICK_DT));
    }

    /** The same payload is spent once whether the charge meets an aircraft or terrain. */
    private static void applyFamilyBlast(BattleSimulation sim, Missile missile, float contactFraction) {
        var spec = missile.weapon.airMissile;
        var flight = missile.flight;
        for (long candidate : sim.getAirEntityIds()) {
            if (!eligibleContact(sim, missile, candidate, !spec.friendlyFire())) continue;
            var body = sim.world().kinematics(candidate);
            float x = sim.world().x(candidate), y = sim.world().y(candidate);
            float z = AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(candidate));
            if (body != null) {
                x = body.previousX + (x - body.previousX) * contactFraction;
                y = body.previousY + (y - body.previousY) * contactFraction;
                z = body.previousAltitude + (z - body.previousAltitude) * contactFraction;
            }
            float dx = x - flight.x, dy = y - flight.y;
            float dz = z - flight.z;
            float surface = Math.max(0f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz)
                    - sim.world().airframe(candidate).targetRadiusCells());
            float damage = missile.weapon.damage * spec.damageFraction(surface);
            if (damage <= 0) continue;
            sim.applyAimedDamage(candidate, missile.shooter, damage, missile.weapon.penetration);
            sim.telemetry().recordRoundHit(missile.shooter);
        }
        var victims = new ArrayList<BlastVictim>();
        for (int i = 0; i < sim.liveUnitCount(); i++) gatherGroundBlast(sim, missile, sim.liveUnitAt(i), victims);
        for (int i = 0; i < sim.bodies().bodyCount(); i++)
            gatherGroundBlast(sim, missile, sim.bodies().bodyAt(i), victims);
        for (var victim : victims) {
            sim.applyAimedDamage(victim.id(), missile.shooter, victim.damage(), missile.weapon.penetration);
            sim.telemetry().recordRoundHit(missile.shooter);
        }
    }

    private record BlastVictim(long id, float damage) {}

    private static void gatherGroundBlast(BattleSimulation sim, Missile missile, long id, List<BlastVictim> victims) {
        if (!sim.bodies().isTargetable(id)) return;
        // A passenger is behind the hull, which is caught by the body pass.
        if (sim.isRiding(id)) return;
        var carrier = sim.bodies().carrierOf(id);
        if (carrier != null && carrier.isAirborne(id)) return;
        var spec = missile.weapon.airMissile;
        var faction = carrier == null ? sim.identity().faction(id) : carrier.faction(id);
        if (!spec.friendlyFire() && !missile.shot.shooterFaction.hostileTo(faction)) return;
        float x = sim.world().x(id), y = sim.world().y(id);
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        if (!sim.getGrid().inBounds(cx, cy)) return;
        var topology = sim.getTopology();
        if (topology.getBuildingId(cx, cy) != 0 && !topology.isRoofDestroyed(cx, cy)) return;
        var flight = missile.flight;
        if (!sim.getGrid().hasLineOfSight((int) Math.floor(flight.x), (int) Math.floor(flight.y), cx, cy)) return;
        float surface = AirMissileSpec.groundSurfaceDistance((float) Math.hypot(x - flight.x, y - flight.y),
                flight.z, sim.physicalRadius(id), sim.getRoster().hitHalfHeight(id));
        float damage = missile.weapon.damage * spec.damageFraction(surface);
        if (damage > 0f) victims.add(new BlastVictim(id, damage));
    }

}
