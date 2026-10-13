package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.AirLocomotion;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import java.util.ArrayList;
import java.util.List;

/** Serial aimed flak. Ordinary mount slew, weapon cadence and shared aircraft damage; no area drain. */
public final class GroundAntiAirSystem {
    private static final float FLIGHT_SECONDS = .15f;
    private final List<Round> rounds = new ArrayList<>();
    private record Round(long shooter, long target, float damage, float penetration, int arrival) {}

    public void tick(BattleSimulation sim) {
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
            var mission = sim.world().mission(candidate);
            if (mission == null || mission.destroyed || !mission.isOnMap() || !AirLocomotion.of(mission.state).airborne()
                    || !sim.world().isAlive(candidate)
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
        if (target == 0L) return;
        float bearing = TurretAim.bearingTo(sim.world().x(id), sim.world().y(id),
                sim.world().x(target), sim.world().y(target));
        float facing = TurretAim.slewToward(state.facingDegrees(id), bearing, structure.mount.turnRateDegPerSec * dt);
        state.setFacingDegrees(id, facing);
        if (cooldown > 0f || Math.abs(TurretAim.shortestAngleDelta(facing, bearing)) > TurretAim.FIRE_ARC_DEG) return;
        sim.world().setCooldownTimer(id, weapon.cooldown);
        state.setRecoilTimer(id, 0f);
        var muzzle = TurretMountGeometry.muzzle(sim.world().x(id), sim.world().y(id), facing, structure.mount, 0);
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
}
