package com.dillon.starsectormarines.battle.turret;

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
import java.util.ArrayList;
import java.util.List;

/** Serial aimed flak and physical guided missiles against hostile airborne craft. */
public final class GroundAntiAirSystem {
    private static final float FLIGHT_SECONDS = .15f;
    private final List<Round> rounds = new ArrayList<>();
    private record Round(long shooter, long target, float damage, float penetration, int arrival) {}
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
        advanceMissiles(sim);
        for (var it = rounds.iterator(); it.hasNext();) {
            Round round = it.next();
            if (sim.getSimTickIndex() < round.arrival) continue;
            if (sim.world().isAlive(round.target)) {
                sim.applyAimedDamage(round.target, round.shooter, round.damage, round.penetration);
                sim.telemetry().recordRoundHit(round.shooter);
            }
            it.remove();
        }
        long[] aircraft = sim.getAirEntityIds();
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
        float x = sim.world().x(target), y = sim.world().y(target);
        if (!hit) { x += 2f; y += 1f; }
        sim.telemetry().recordRoundFired(id);
        if (hit) {
            rounds.add(new Round(id, target, weapon.damage, weapon.penetration,
                    sim.getSimTickIndex() + Math.round(FLIGHT_SECONDS / dt)));
        }
        sim.postShot(new ShotEvent(muzzle.x(), muzzle.y(), 0f, x, y,
                AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(target)), hit,
                sim.identity().faction(id), FLIGHT_SECONDS, structure, null, null, null,
                0f, hit, null, id));
    }

    /**
     * Whether the mount has a clear line up to the craft. A fort's yard mount fires over its own works; every other
     * mount, and anything standing outside a fort's works, blocks as ordinary structure.
     */
    private static boolean airLineClear(BattleSimulation sim, Fort fort, long mount, long craft) {
        int fromX = sim.world().cellX(mount), fromY = sim.world().cellY(mount);
        int toX = (int) sim.world().x(craft), toY = (int) sim.world().y(craft);
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
