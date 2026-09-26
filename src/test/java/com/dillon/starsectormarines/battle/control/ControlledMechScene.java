package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechLanceOrder;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fixed inputs through session ownership, chassis motion, mount firing and lance handback. */
public final class ControlledMechScene implements BehaviorScene {
    @Override public String id() { return "controlled-mech"; }
    @Override public String label() { return "Controlled Bulwark clearance, point fire and lance handback"; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("closed-edge-fire", "leader-handback")) {
            Run first = run(loop, frames);
            Run repeated = run(loop, FrameSink.NONE);
            List<Verdict> verdicts = new ArrayList<>(first.verdicts);
            verdicts.add(Verdict.of("repeatable-input", first.trace.equals(repeated.trace),
                    "fixed input repeats poses, hip turn, mount clock, rounds and control ownership"));
            reports.add(new SceneReport(id(), loop, first.ticks, verdicts, first.metrics));
        }
        return reports;
    }

    private Run run(String loop, FrameSink frames) {
        boolean wall = loop.equals("closed-edge-fire");
        SceneBuilder builder = SceneBuilder.openGround(64, 36).missionCompletion(false);
        builder.squad("lance").faction(Faction.MARINE).size(wall ? 1 : 2)
                .inFile(wall ? 3 : 5, wall ? 5 : 6, 0, 5)
                .mech(MechVariant.BULWARK, MechRole.BALANCED)
                .assigned(id -> ObjectiveAssignment.attackMove(id, 45, 6)).done();
        var scene = builder.build();
        try (BattleSimulation sim = scene.sim()) {
            sim.random().setSeed(20260926L);
            long mech = scene.members("lance")[0];
            long other = wall ? 0L : scene.members("lance")[1];
            var squad = sim.squadOf(mech);
            long leader = squad.leaderId;
            var assignment = squad.assignedObjective;
            if (!wall) sim.getMechDoctrineService().requestLanceOrder(mech, MechLanceOrder.FREE_REIGN);
            if (wall) for (int y = 0; y < 36; y++) sim.getGrid().blockSharedEdge(7, y, Direction.E);
            MechWeaponMount arms = sim.world().mechLoadout(mech).mount(MechMountSlot.ARMS);
            arms.cooldown = 1f;
            boolean entered = sim.directControl().enter(mech);
            float startX = sim.world().x(mech), startY = sim.world().y(mech);
            float otherStartX = wall ? 0f : sim.world().x(other);
            float blockedX = startX, openedX = startX, releaseX = startX, progress = 0f;
            boolean legal = true, heldAtEdge = true, noAiRoute = true, noEntityTarget = true;
            boolean pivotHeld = false, clockAdvanced = false, paused = false;
            int shots = 0, roundsAtSuspend = 0;
            int ticks = wall ? 360 : 660;
            StringBuilder trace = new StringBuilder();
            for (int tick = 0; tick < ticks; tick++) {
                if (wall) {
                    if (tick == 210) {
                        blockedX = sim.world().x(mech);
                        for (int y = 0; y < 36; y++) sim.getGrid().openSharedEdge(7, y, Direction.E);
                    }
                    if (tick < 330) sim.directControl().submit(new ManualIntent(1f, 0f, 28.5f, 5.5f, true));
                    if (tick == 330) {
                        openedX = sim.world().x(mech);
                        roundsAtSuspend = sim.telemetry().roundsFired(mech);
                        sim.directControl().suspendInput();
                    }
                } else {
                    if (tick < 150) sim.directControl().submit(new ManualIntent(0f, 1f, 45f, 18f, false));
                    if (tick == 150) sim.directControl().suspendInput();
                    if (tick == 240) {
                        progress = sim.world().x(other) - otherStartX;
                        releaseX = sim.world().x(mech);
                        sim.directControl().exit();
                    }
                }
                if (tick == 90) {
                    float x = sim.world().x(mech), y = sim.world().y(mech), cooldown = arms.cooldown;
                    int rounds = sim.telemetry().roundsFired(mech), ammo = arms.ammo;
                    sim.advance(0f);
                    paused = x == sim.world().x(mech) && y == sim.world().y(mech)
                            && cooldown == arms.cooldown && ammo == arms.ammo
                            && rounds == sim.telemetry().roundsFired(mech);
                }
                sim.advance(BattleSimulation.TICK_DT);
                shots += (int) sim.getShotsThisFrame().stream().filter(shot -> shot.shooterId == mech).count();
                if (tick == 0) {
                    pivotHeld = sim.world().x(mech) == startX && sim.world().y(mech) == startY;
                    clockAdvanced = arms.cooldown < 1f && arms.cooldown > 0f;
                }
                legal &= ManualTerrainMotion.canStand(sim.getGrid(), sim.world().x(mech),
                        sim.world().y(mech), sim.physicalRadius(mech));
                if (wall && tick < 210) heldAtEdge &= sim.world().x(mech) <= 8f - MechVariant.BULWARK.radius;
                if (sim.directControl().active()) {
                    noAiRoute &= Paths.isEmpty(sim.movement().path(mech));
                    noEntityTarget &= sim.combat().targetId(mech) == 0L;
                }
                trace.append(Float.floatToIntBits(sim.world().x(mech))).append(',')
                        .append(Float.floatToIntBits(sim.world().y(mech))).append(',')
                        .append(Float.floatToIntBits(sim.world().mechHipFacingDegrees(mech))).append(',')
                        .append(Float.floatToIntBits(arms.cooldown)).append(',')
                        .append(sim.telemetry().roundsFired(mech)).append(',')
                        .append(sim.directControl().activeUnitId()).append(';');
                if (!wall) trace.append(Float.floatToIntBits(sim.world().x(other))).append(',')
                        .append(Float.floatToIntBits(sim.world().y(other))).append(';');
                if (tick % 15 == 0) frames.frame(loop, sim, tick, loop + " / tick " + tick);
            }
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("entry", entered, "live lance Bulwark accepted"));
            verdicts.add(Verdict.of("whole-body-clear", legal, "every pose clears the actual 0.6-cell chassis radius"));
            verdicts.add(Verdict.of("manual-authority", noAiRoute && noEntityTarget,
                    "controlled chassis receives no autonomous route or entity fire target"));
            verdicts.add(Verdict.of("live-clock", clockAdvanced, "mount cooldown advances during manual control"));
            verdicts.add(Verdict.of("pause", paused, "zero simulation time preserves pose, rounds, ammunition and mount clock"));
            verdicts.add(Verdict.of("mission-preserved", leader == squad.leaderId
                            && assignment.equals(squad.assignedObjective), "original leader and mission remain unchanged"));
            if (wall) {
                verdicts.add(Verdict.of("pivot-before-drive", pivotHeld, "initial east input waits for hip alignment"));
                verdicts.add(Verdict.of("closed-edge", heldAtEdge && blockedX > startX + 2f,
                        "full body advances from " + startX + " and stops at " + blockedX));
                verdicts.add(Verdict.of("opened-edge", openedX > 9f,
                        "same input crosses newly opened terrain to x=" + openedX));
                verdicts.add(Verdict.of("point-fire", shots > 0 && sim.telemetry().roundsFired(mech) > 0,
                        "held LMB emits " + shots + " shots toward empty world space without an entity target"));
                verdicts.add(Verdict.of("suspended-input", Math.abs(sim.world().x(mech) - openedX) < 1e-5f
                                && sim.telemetry().roundsFired(mech) == roundsAtSuspend
                                && arms.burstRemaining == 0,
                        "input suspension leaves no drive or queued point burst"));
            } else {
                verdicts.add(Verdict.of("lance-progress", progress > 3f,
                        "autonomous lance member advanced " + progress + " cells during takeover"));
                verdicts.add(Verdict.of("handback", !sim.directControl().active()
                                && squad.controlledMemberId() == 0L && sim.world().x(mech) > releaseX + 3f,
                        "released leader resumed assignment from x=" + releaseX + " to " + sim.world().x(mech)));
            }
            return new Run(ticks, trace.toString(), verdicts, Map.of("shots", shots,
                    "rounds", sim.telemetry().roundsFired(mech), "finalX", sim.world().x(mech), "lanceProgress", progress));
        }
    }

    private record Run(int ticks, String trace, List<Verdict> verdicts, Map<String, Number> metrics) {}
}
