package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleFootprint;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleState;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Production serial scheduling for one manual APC, including return to its retained move. */
public final class ControlledVehicleScene implements BehaviorScene {
    @Override public String id() { return "controlled-vehicle"; }
    @Override public String label() { return "Controlled APC terrain, point fire and move handback"; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("closed-edge-fire", "move-handback")) {
            Run first = run(loop, frames);
            Run repeated = run(loop, FrameSink.NONE);
            List<Verdict> verdicts = new ArrayList<>(first.verdicts);
            verdicts.add(Verdict.of("repeatable-input", first.trace.equals(repeated.trace),
                    "fixed inputs repeat chassis pose, resources, shots and ownership"));
            reports.add(new SceneReport(id(), loop, first.ticks, verdicts, first.metrics));
        }
        return reports;
    }

    private Run run(String loop, FrameSink frames) {
        boolean wall = loop.equals("closed-edge-fire");
        var scene = SceneBuilder.openGround(64, 64).missionCompletion(false).build();
        try (BattleSimulation sim = scene.sim()) {
            sim.random().setSeed(20260926L);
            var mission = VehicleMission.deployed(20.5f, 10.5f);
            sim.addConvoyVehicle(VehicleType.HEAVY_APC, Faction.MARINE, mission);
            long id = sim.getConvoyVehicleIds()[0];
            var body = sim.convoy().body(id);
            body.facingDegrees = 0f;
            var turret = sim.convoy().turret(id);
            var type = VehicleType.HEAVY_APC;
            if (wall) for (int x = 0; x < 64; x++) sim.getGrid().blockSharedEdge(x, 15, Direction.N);
            if (!wall) {
                sim.getVehicleMoveOrderService().requestMove(id, 20, 45);
                sim.advance(BattleSimulation.TICK_DT);
            }
            boolean hadOrder = wall || sim.getVehicleMoveOrderService().activeOrder(id) != null;
            boolean entered = sim.directControl().enter(id);
            int initialAmmo = turret.ammo;
            float blockedY = body.y, openedY = body.y, releaseY = body.y;
            boolean legal = true, heldAtEdge = true, singleOwner = true, paused = false;
            int shots = 0, suspendedAmmo = 0;
            int ticks = wall ? 420 : 720;
            StringBuilder trace = new StringBuilder();
            for (int tick = 0; tick < ticks; tick++) {
                if (wall) {
                    if (tick == 180) {
                        blockedY = body.y;
                        for (int x = 0; x < 64; x++) sim.getGrid().openSharedEdge(x, 15, Direction.N);
                    }
                    if (tick < 300) sim.directControl().submit(new ManualIntent(0f, 1f, 20.5f, 50f, true));
                    if (tick == 300) {
                        openedY = body.y;
                        sim.directControl().suspendInput();
                        suspendedAmmo = turret.ammo;
                    }
                } else {
                    if (tick < 90) sim.directControl().submit(new ManualIntent(0f, -1f, 20.5f, 50f, false));
                    if (tick == 90) sim.directControl().suspendInput();
                    if (tick == 120) {
                        releaseY = body.y;
                        sim.directControl().exit();
                    }
                }
                if (tick == 60) {
                    float x = body.x, y = body.y, facing = body.facingDegrees, clock = turret.cooldownTimer;
                    int ammo = turret.ammo;
                    sim.advance(0f);
                    paused = x == body.x && y == body.y && facing == body.facingDegrees
                            && clock == turret.cooldownTimer && ammo == turret.ammo;
                }
                sim.advance(BattleSimulation.TICK_DT);
                shots += (int) sim.getShotsThisFrame().stream().filter(shot -> shot.shooterId == id).count();
                legal &= VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees,
                        type.visualLengthCells, type.visualWidthCells, sim.getGrid());
                if (wall && tick < 180) heldAtEdge &= body.y <= 16f - type.visualLengthCells * .5f + .001f;
                if (sim.directControl().active()) singleOwner &= sim.getVehicleMoveOrderService().activeOrder(id) == null
                        && turret.targetId == 0L && turret.burstTargetId == 0L;
                trace.append(Float.floatToIntBits(body.x)).append(',').append(Float.floatToIntBits(body.y))
                        .append(',').append(Float.floatToIntBits(body.facingDegrees)).append(',')
                        .append(Float.floatToIntBits(turret.cooldownTimer)).append(',').append(turret.ammo)
                        .append(',').append(sim.directControl().activeUnitId()).append(',').append(shots).append(';');
                if (tick % 15 == 0) frames.frame(loop, sim, tick, loop + " / tick " + tick);
            }
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("entry", entered && hadOrder, "deployed APC accepts ownership and retains prior move intent"));
            verdicts.add(Verdict.of("whole-hull-clear", legal, "every applied pose clears full oriented hull and live terrain"));
            verdicts.add(Verdict.of("single-owner", singleOwner, "manual chassis and turret have no competing AI route or target"));
            verdicts.add(Verdict.of("pause", paused, "zero simulation time preserves pose and turret resources"));
            verdicts.add(Verdict.of("deployed-lifecycle", mission.state == VehicleState.DEPLOYED,
                    "manual intervention does not begin a delivery errand"));
            if (wall) {
                verdicts.add(Verdict.of("closed-edge", heldAtEdge && blockedY > 13f,
                        "chassis advances and stops nose at closed edge, center y=" + blockedY));
                verdicts.add(Verdict.of("opened-edge", openedY > 20f,
                        "same held input traverses newly opened edge, center y=" + openedY));
                verdicts.add(Verdict.of("point-fire", shots > 0 && initialAmmo - suspendedAmmo == shots,
                        shots + " physical shots debit exactly one round each without an entity target"));
                verdicts.add(Verdict.of("suspended-input", turret.ammo == suspendedAmmo && turret.burstRemaining == 0
                                && body.speed == 0f && body.y - openedY < 1.5f,
                        "neutral input brakes the chassis and cancels queued rounds"));
            } else {
                verdicts.add(Verdict.of("manual-reverse", releaseY < 9f,
                        "reverse detour moved to y=" + releaseY));
                verdicts.add(Verdict.of("handback", !sim.directControl().active() && body.y > releaseY + 8f,
                        "current move resumes from manual pose: final y=" + body.y));
            }
            return new Run(ticks, trace.toString(), verdicts, Map.of("shots", shots,
                    "ammo", turret.ammo, "finalX", body.x, "finalY", body.y));
        }
    }
    private record Run(int ticks, String trace, List<Verdict> verdicts, Map<String, Number> metrics) {}
}
