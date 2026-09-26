package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fixed-input acceptance through the actual session, squad pipeline and firing boundary. */
public final class ControlledMarineScene implements BehaviorScene {
    @Override public String id() { return "controlled-marine"; }
    @Override public String label() { return "Marine takeover, terrain, squad progress and handback"; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("wall-fire", "leader-handback")) {
            Run first = run(loop, frames);
            Run replay = run(loop, FrameSink.NONE);
            List<Verdict> verdicts = new ArrayList<>(first.verdicts);
            verdicts.add(Verdict.of("repeatable-input", first.trace.equals(replay.trace),
                    "identical fixed input must reproduce positions, shots and ownership"));
            reports.add(new SceneReport(id(), loop, first.ticks, verdicts, first.metrics));
        }
        return reports;
    }

    private Run run(String loop, FrameSink frames) {
        boolean wall = loop.equals("wall-fire");
        SceneBuilder builder = SceneBuilder.openGround(64, 36).missionCompletion(false);
        if (wall) {
            builder.wall(8, 0, 8, 35).unit("marine", Faction.MARINE, UnitType.MARINE, 3, 5,
                    spec -> spec.primaryWeapon(WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID)));
        } else {
            builder.squad("squad").faction(Faction.MARINE).type(UnitType.MARINE)
                    .size(6).at(5, 6).primary(WeaponRegistry.PULSE_RIFLE_ID)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, 45, 6)).done();
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            sim.random().setSeed(20260926L);
            long marine = wall ? world.unit("marine") : world.members("squad")[0];
            Squad squad = wall ? null : sim.squadOf(marine);
            long leader = squad == null ? 0L : squad.leaderId;
            ObjectiveAssignment assignment = squad == null ? null : squad.assignedObjective;
            boolean entered = sim.directControl().enter(marine);
            float startX = sim.world().renderX(marine);
            float blockedX = startX;
            float openedX = startX;
            float releaseX = startX;
            float progress = 0f;
            boolean paused = true;
            boolean terrainLegal = true;
            int shots = 0;
            int ticks = wall ? 240 : 660;
            StringBuilder trace = new StringBuilder();
            for (int tick = 0; tick < ticks; tick++) {
                if (wall) {
                    if (tick == 120) {
                        blockedX = sim.world().renderX(marine);
                        sim.getGrid().setWalkableFloor(8, 5);
                    }
                    if (tick < 200) sim.directControl().submit(new ManualIntent(1, 0, 28.5f, 5.5f, true));
                    if (tick == 200) {
                        openedX = sim.world().renderX(marine);
                        sim.directControl().suspendInput();
                    }
                } else {
                    if (tick < 150) sim.directControl().submit(new ManualIntent(0, 1, 45, 18, true));
                    if (tick == 150) sim.directControl().suspendInput();
                    if (tick == 240) {
                        progress = squad.centroidX - 7f;
                        releaseX = sim.world().renderX(marine);
                        sim.directControl().exit();
                    }
                }
                if (tick == 30) {
                    float px = sim.world().renderX(marine);
                    float py = sim.world().renderY(marine);
                    float cd = sim.combat().cooldownTimer(marine);
                    int rounds = sim.telemetry().roundsFired(marine);
                    sim.advance(0f);
                    paused = px == sim.world().renderX(marine) && py == sim.world().renderY(marine)
                            && cd == sim.combat().cooldownTimer(marine)
                            && rounds == sim.telemetry().roundsFired(marine);
                }
                sim.advance(BattleSimulation.TICK_DT);
                shots += (int) sim.getShotsThisFrame().stream().filter(s -> s.shooterId == marine).count();
                if (wall && tick < 120) terrainLegal &= sim.world().renderX(marine) <= 8f - UnitType.MARINE.radius;
                if (tick % 10 == 0) frames.frame(loop, sim, tick, loop + " / tick " + tick);
                trace.append(Float.floatToIntBits(sim.world().renderX(marine))).append(',')
                        .append(Float.floatToIntBits(sim.world().renderY(marine))).append(',')
                        .append(sim.telemetry().roundsFired(marine)).append(',')
                        .append(sim.directControl().activeUnitId()).append(';');
                if (squad != null) trace.append(Float.floatToIntBits(squad.centroidX)).append(';');
            }
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("entry", entered, "exact live Marine accepted"));
            verdicts.add(Verdict.of("pause", paused, "zero simulation time moves no body or weapon clock"));
            verdicts.add(Verdict.of("primary-fire", shots > 0, "emitted " + shots + " rounds through ordinary firing"));
            if (wall) {
                verdicts.add(Verdict.of("solid-wall", terrainLegal && blockedX > startX,
                        "body advanced from " + startX + " and stopped at " + blockedX));
                verdicts.add(Verdict.of("opened-wall", openedX > 8.5f,
                        "after opening, body reached " + openedX));
                verdicts.add(Verdict.of("suspended-input", Math.abs(sim.world().renderX(marine) - openedX) < 1e-5f
                                && sim.combat().burstRemaining(marine) == 0,
                        "suspended movement and burst remain neutral"));
            } else {
                verdicts.add(Verdict.of("squad-progress", progress > 4f,
                        "autonomous squad advanced " + progress + " cells while leader was controlled"));
                verdicts.add(Verdict.of("mission-preserved", squad.leaderId == leader
                                && assignment.equals(squad.assignedObjective),
                        "actual leader and mission assignment are unchanged"));
                verdicts.add(Verdict.of("handback", !sim.directControl().active()
                                && squad.controlledMemberId() == 0L
                                && sim.world().renderX(marine) > releaseX + 3f,
                        "released leader moved from " + releaseX + " to " + sim.world().renderX(marine)));
            }
            return new Run(ticks, trace.toString(), verdicts, Map.of("rounds", shots,
                    "finalX", sim.world().renderX(marine), "squadProgress", progress));
        }
    }

    private record Run(int ticks, String trace, List<Verdict> verdicts, Map<String, Number> metrics) { }
}
