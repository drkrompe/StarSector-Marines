package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.AirLocomotion;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.GuidedMissile;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import java.util.ArrayList;
import java.util.List;

/** Serial aimed flak and physical guided missiles against hostile airborne craft. */
public final class GroundAntiAirSystem {
    private static final float FLIGHT_SECONDS = .15f;
    private final List<Round> rounds = new ArrayList<>();
    private record Round(long shooter, long target, float damage, float penetration, int arrival) {}
    private final List<Missile> missiles = new ArrayList<>();
    private record Missile(long shooter, long target, WeaponDef weapon, ShotEvent shot, GuidedMissile flight) {}

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
        float dt = BattleSimulation.TICK_DT;
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
            // The same structural air line used by aircraft guns; a roof directly below the craft
            // is not an intervening building. Fog never grants or denies a weapon's line of fire.
            if (!TurretAim.airFireLineClear(sim.getGrid(), sim.world().cellX(id), sim.world().cellY(id),
                    (int) sim.world().x(candidate), (int) sim.world().y(candidate), 0f, 4f)) continue;
            target = candidate;
            nearest = distance;
        }
        sim.world().setTargetId(id, target);
        if (target == 0L) { state.setBurstRemaining(id, 0); return; }
        float bearing = TurretAim.bearingTo(sim.world().x(id), sim.world().y(id),
                sim.world().x(target), sim.world().y(target));
        float facing = TurretAim.slewToward(state.facingDegrees(id), bearing, structure.mount.turnRateDegPerSec * dt);
        state.setFacingDegrees(id, facing);
        boolean burst = state.burstRemaining(id) > 0;
        state.setBurstTimer(id, Math.max(0f, state.burstTimer(id) - dt));
        if ((burst ? state.burstTimer(id) > 0f : cooldown > 0f)
                || Math.abs(TurretAim.shortestAngleDelta(facing, bearing)) > TurretAim.FIRE_ARC_DEG) return;
        int release = burst ? weapon.burstCount - state.burstRemaining(id) : 0;
        state.setBurstRemaining(id, burst ? state.burstRemaining(id) - 1 : weapon.burstCount - 1);
        state.setBurstTimer(id, weapon.burstSpacing);
        if (!burst) sim.world().setCooldownTimer(id, weapon.cooldown);
        state.setRecoilTimer(id, 0f);
        var muzzle = TurretMountGeometry.muzzle(sim.world().x(id), sim.world().y(id), facing, structure.mount, release);
        if (weapon.guidance != null) {
            var flight = new GuidedMissile(muzzle.x(), muzzle.y(), facing, weapon.roundVelocity, weapon.guidance);
            var shot = new ShotEvent(muzzle.x(), muzzle.y(), 0f, muzzle.x(), muzzle.y(), 0f,
                    false, sim.identity().faction(id), weapon.guidance.lifetime(), structure, null, null, null,
                    0f, false, null, id);
            shot.guidedFlight = flight;
            missiles.add(new Missile(id, target, weapon, shot, flight));
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

    private void advanceMissiles(BattleSimulation sim) {
        for (var it = missiles.iterator(); it.hasNext();) {
            Missile missile = it.next();
            var flight = missile.flight;
            var mission = sim.world().isAlive(missile.target) ? sim.world().mission(missile.target) : null;
            boolean tracking = sim.world().isAlive(missile.target) && mission != null && !mission.destroyed
                    && mission.isOnMap() && AirLocomotion.of(mission.state).airborne();
            float tx = tracking ? sim.world().x(missile.target) : flight.x;
            float ty = tracking ? sim.world().y(missile.target) : flight.y;
            float tz = tracking ? AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(missile.target)) : flight.z;
            // A short lead helps intercept transverse flight; guidance still steers toward live positions.
            float lead = tracking ? Math.min(.5f, (float) Math.hypot(tx - flight.x, ty - flight.y) / missile.weapon.roundVelocity) : 0f;
            var body = tracking ? sim.world().kinematics(missile.target) : null;
            flight.advance(BattleSimulation.TICK_DT, tracking, tx + (body == null ? 0f : body.vx * lead),
                    ty + (body == null ? 0f : body.vy * lead), tz);
            if (tracking && flight.contacts(tx, ty, tz, sim.world().airframe(missile.target).targetRadiusCells())) {
                sim.applyAimedDamage(missile.target, missile.shooter, missile.weapon.damage, missile.weapon.penetration);
                sim.telemetry().recordRoundHit(missile.shooter);
                flight.remaining = 0f;
            }
            // Once at flight height, the round clears low structures just as the aircraft does.
            // A low launch leg must not tunnel through a solid wall or mountain face.
            int cx = (int) Math.floor(flight.x), cy = (int) Math.floor(flight.y);
            if (flight.z < 1f && sim.getGrid().inBounds(cx, cy)
                    && sim.getGrid().blocksStructuralLineOfSight(cx, cy)) flight.remaining = 0f;
            missile.shot.toX = flight.x; missile.shot.toY = flight.y; missile.shot.toZ = flight.z;
            if (flight.remaining <= 0f) it.remove();
        }
    }
}
