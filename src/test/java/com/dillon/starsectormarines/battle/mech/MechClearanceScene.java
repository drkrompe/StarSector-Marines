package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.ManualTerrainMotion;
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

/** Exercises the production order, routing, locomotion and separation phases together. */
public final class MechClearanceScene implements BehaviorScene {
    @Override public String id() { return "mech-clearance"; }
    @Override public String label() { return "Mech body clearance and live terrain handback"; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("one-cell", "two-cell", "changed-door")) {
            Run first = run(loop, frames);
            Run repeated = run(loop, FrameSink.NONE);
            List<Verdict> verdicts = new ArrayList<>(first.verdicts);
            verdicts.add(Verdict.of("repeatable-route", first.trace.equals(repeated.trace),
                    "synchronous evidence routing repeats the physical trace"));
            reports.add(new SceneReport(id(), loop, 750, verdicts, Map.of("finalX", first.finalX)));
        }
        return reports;
    }

    private Run run(String loop, FrameSink frames) {
        boolean narrow = loop.equals("one-cell");
        boolean changed = loop.equals("changed-door");
        SceneBuilder builder = SceneBuilder.openGround(24, 12).seal(8).doorway(8, 5);
        if (!narrow) builder.doorway(8, 4);
        builder.squad("lance").faction(Faction.MARINE).size(1).inFile(2, 5, 1, 0)
                .mech(MechVariant.BULWARK, MechRole.BALANCED)
                .assigned(id -> ObjectiveAssignment.attackMove(id, 18, 5)).done();
        var scene = builder.build();
        try (BattleSimulation sim = scene.sim()) {
            sim.random().setSeed(20260926L);
            long mech = scene.members("lance")[0];
            var squad = sim.squadOf(mech);
            var mission = squad.assignedObjective;
            sim.getMechMoveOrderService().requestMove(mech, 18, 5);
            boolean legal = true;
            float xBeforeReopen = sim.world().x(mech);
            StringBuilder trace = new StringBuilder();
            for (int tick = 0; tick < 750; tick++) {
                if (changed && tick == 90) sim.getGrid().setWalkable(8, 4, false);
                if (changed && tick == 180) {
                    xBeforeReopen = sim.world().x(mech);
                    sim.getGrid().setWalkableFloor(8, 4);
                    sim.getMechMoveOrderService().requestMove(mech, 18, 5);
                }
                sim.advance(BattleSimulation.TICK_DT);
                legal &= ManualTerrainMotion.canStand(sim.getGrid(), sim.world().x(mech),
                        sim.world().y(mech), sim.physicalRadius(mech));
                trace.append(Float.floatToIntBits(sim.world().x(mech))).append(',')
                        .append(Float.floatToIntBits(sim.world().y(mech))).append(';');
                if (tick % 15 == 0) frames.frame(loop, sim, tick, loop + " / " + tick);
            }
            float x = sim.world().x(mech);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("whole-body-clear", legal, "every post-separation pose clears actual chassis radius"));
            verdicts.add(Verdict.of("passage-width", narrow ? x < 7.4f : x > 17f,
                    "Bulwark finished at x=" + x));
            verdicts.add(Verdict.of("mission-preserved", mission.equals(squad.assignedObjective),
                    "one-shot route does not rewrite lance assignment"));
            verdicts.add(Verdict.of("order-released", sim.getMechMoveOrderService().activeOrder(mech) == null,
                    "arrival or proven refusal releases the temporary move"));
            if (changed) verdicts.add(Verdict.of("closed-before-reopen", xBeforeReopen < 8f,
                    "live closure held chassis at x=" + xBeforeReopen));
            return new Run(trace.toString(), verdicts, x);
        }
    }

    private record Run(String trace, List<Verdict> verdicts, float finalX) {}
}
