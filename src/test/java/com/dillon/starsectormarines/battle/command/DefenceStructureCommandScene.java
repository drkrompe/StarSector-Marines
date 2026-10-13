package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.shield.ShieldGenerator;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A squad and a commander, and a shield generator: does the commander treat the structure as work?
 *
 * <p>The question is about command, so the world is as small as it can be: open ground, one generator, a squad a
 * hundred cells off, and the far lone body each scene keeps so neither side is ever absent. The geometry is measured
 * in the generator's own reach, not in any weapon's.
 *
 * <ul>
 *   <li>{@code take}: the company's commander and a generator held by the other side. It should send the squad to the
 *       generator's doorstep, and the squad should take it or wreck it.</li>
 *   <li>{@code control-no-command}: the same world with no commander installed. The squad has no one to tell it the
 *       generator exists, so it must not go there. This is the instrument's control: without it, "the squad went"
 *       could be the squad's own wandering.</li>
 *   <li>{@code control-own-structure}: the generator is the company's own and nobody presses it. A side does not send
 *       squads to stand beside a structure that is safe and its own.</li>
 *   <li>{@code retake}: the same rules written the other way round. A generator the garrison built is taken by a
 *       few of the company's tough marines. The garrison's commander should stand a squad to at it while the capture
 *       is under way (it is pressed), and order the squad back to retake it once it has fallen.</li>
 * </ul>
 */
public final class DefenceStructureCommandScene implements BehaviorScene {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 100;
    private static final int TICKS = 3600;
    private static final int GEN_X = 110;
    private static final int GEN_Y = 50;
    /** "Near the generator" for an order or a squad: well inside the dome's capture radius plus a margin. */
    private static final int NEAR = 8;
    private static final String RIFLE = "weapon.smg";
    /** One command pulse in ticks (2.5 s at 30 Hz): the soonest a commander can act on what it has just learned. */
    private static final int PULSE_TICKS = 75;
    /** The retaking squad must be at the generator's doorstep, and the generator back in its hands, by this tick. */
    private static final int RETAKE_BOUND_TICK = 2400;
    /** Tough enough to be standing when the capture completes, not so tough the squad can never finish them. */
    private static final float HOLDER_HEALTH = 150f;

    @Override public String id() { return "defence-structure-command"; }

    @Override public String label() {
        return "Defence structures as objectives: a commander sends a squad to take an enemy generator it knows of, "
                + "leaves its own alone, and orders a squad back to one it lost";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("take", "control-no-command", "control-own-structure", "retake")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
    }

    private record Run(Trace trace, long generator, BattleSimulation sim) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean retake = loop.equals("retake");
        boolean ownStructure = loop.equals("control-own-structure");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 195, 95, spec -> spec.moveSpeed(0f));
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 3, 3, spec -> spec.moveSpeed(0f));
        if (retake) {
            builder.squad("garrison").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6)
                    .at(170, 50).primary(RIFLE).done();
            // The company's holders: tough enough to still be standing at the generator when the order is read.
            for (int i = 0; i < 3; i++) {
                builder.unit("holder-" + i, Faction.MARINE, UnitType.MARINE, GEN_X + 3, GEN_Y - 1 + i,
                        spec -> spec.moveSpeed(0f).health(HOLDER_HEALTH));
            }
        } else {
            builder.squad("company").faction(Faction.MARINE).size(6).at(20, 50).primary(RIFLE).done();
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261015L);
        Faction holder = ownStructure ? Faction.MARINE : Faction.DEFENDER;
        long generator = sim.spawn(ShieldGenerator.create("generator", holder, GEN_X, GEN_Y));

        ConquestTrackLayout tracks = new ConquestTrackLayout(TraversalAxis.WEST_TO_EAST, WIDTH, HEIGHT);
        if (retake) {
            ConquestDefenderCommand defenders = new ConquestDefenderCommand(tracks);
            sim.claimSquadCommand(world.squadId("garrison"), CommandAuthority.MISSION_COMMAND,
                    defenders.strategyId(), "scene patrol reserve");
            sim.setAutonomousCommander(Faction.DEFENDER, defenders, ConquestCommandDisclosure.INSTANCE);
        } else if (!loop.equals("control-no-command")) {
            sim.setAutonomousCommander(Faction.MARINE, new ConquestCommand(tracks),
                    ConquestCommandDisclosure.INSTANCE);
        }
        Squad subject = world.squad(retake ? "garrison" : "company");
        Trace trace = new Trace(subject, world.members(retake ? "garrison" : "company"),
                retake ? Faction.DEFENDER : Faction.MARINE);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 15, tick -> String.format(Locale.ROOT, "%s / tick %d / %s",
                        loop, tick, trace.caption(sim)))
                .run(TICKS);
        return new Run(trace, generator, sim);
    }

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        BattleSimulation sim = run.sim();
        List<Verdict> verdicts = new ArrayList<>();
        Faction holderAtEnd = sim.defenceStructures().isEmpty() ? null : sim.defenceStructures().get(0).holder();
        boolean generatorStands = sim.getRoster().isAliveById(run.generator());
        switch (loop) {
            case "take" -> {
                verdicts.add(Verdict.of("ordered-to-the-generator", t.firstOrderTick >= 0,
                        "commander reason " + t.firstOrderReason + " to within " + NEAR + " cells of it, first at tick " + t.firstOrderTick));
                // The order cannot come before the company has seen the generator (the commander is never told of
                // ground nobody has looked at), and then it is owed within one command pulse. How late the sighting
                // is belongs to the walk across the map, which is not what this loop is about.
                verdicts.add(Verdict.of("ordered-within-a-pulse-of-sighting",
                        t.firstSeenTick >= 0 && t.firstOrderTick >= t.firstSeenTick
                                && t.firstOrderTick - t.firstSeenTick <= PULSE_TICKS,
                        "generator first in view at tick " + t.firstSeenTick + ", ordered at tick " + t.firstOrderTick
                                + " (pulse is " + PULSE_TICKS + " ticks)"));
                verdicts.add(Verdict.of("squad-gets-there", t.nearestSquad <= NEAR,
                        String.format(Locale.ROOT, "nearest approach %.1f cells", t.nearestSquad)));
                verdicts.add(Verdict.of("taken-or-wrecked", !generatorStands || holderAtEnd == Faction.MARINE,
                        generatorStands ? "held by " + holderAtEnd : "destroyed"));
            }
            case "control-no-command" -> {
                verdicts.add(Verdict.of("never-ordered-there", t.firstOrderTick < 0, "first order tick " + t.firstOrderTick));
                verdicts.add(Verdict.of("squad-stays-away", t.nearestSquad > 40f,
                        String.format(Locale.ROOT, "nearest approach %.1f cells", t.nearestSquad)));
                verdicts.add(Verdict.of("generator-untouched", generatorStands && holderAtEnd == Faction.DEFENDER,
                        "held by " + holderAtEnd));
            }
            case "control-own-structure" -> {
                verdicts.add(Verdict.of("never-ordered-to-its-own-calm-structure", t.firstOrderTick < 0,
                        "first order tick " + t.firstOrderTick));
                verdicts.add(Verdict.of("still-the-companys", generatorStands && holderAtEnd == Faction.MARINE,
                        "held by " + holderAtEnd));
            }
            default -> {
                verdicts.add(Verdict.of("generator-falls-first", t.fellTick >= 0,
                        "held by the company from tick " + t.fellTick));
                verdicts.add(Verdict.of("stands-to-while-it-is-pressed", t.firstDefendTick >= 0 && t.firstDefendTick < t.fellTick,
                        "area defence ordered at tick " + t.firstDefendTick + ", before it fell at tick " + t.fellTick));
                verdicts.add(Verdict.of("no-retake-before-it-falls", t.firstRetakeTick < 0 || t.firstRetakeTick >= t.fellTick,
                        "first retake order tick " + t.firstRetakeTick));
                verdicts.add(Verdict.of("garrison-ordered-to-retake", t.fellTick >= 0 && t.firstRetakeTick >= t.fellTick,
                        "commander reason DEFENCE_STRUCTURE_RETAKE first at tick " + t.firstRetakeTick));
                verdicts.add(Verdict.of("retaking-squad-reaches-the-generator", t.nearestSquad <= NEAR,
                        String.format(Locale.ROOT, "nearest approach %.1f cells", t.nearestSquad)));
                verdicts.add(Verdict.of("generator-retaken-in-time",
                        t.retakenTick >= 0 && t.retakenTick <= RETAKE_BOUND_TICK && holderAtEnd == Faction.DEFENDER,
                        "back in the garrison's hands at tick " + t.retakenTick + " (bound " + RETAKE_BOUND_TICK
                                + "), held by " + holderAtEnd + " at the end"));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("firstOrderTick", t.firstOrderTick);
        metrics.put("firstSeenTick", t.firstSeenTick);
        metrics.put("retakenTick", t.retakenTick);
        metrics.put("nearestSquadCells", Math.round(t.nearestSquad * 10f) / 10f);
        metrics.put("fellTick", t.fellTick);
        metrics.put("firstDefendTick", t.firstDefendTick);
        metrics.put("firstRetakeTick", t.firstRetakeTick);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final Squad subject;
        private final long[] members;
        private final Faction side;
        int firstOrderTick = -1;
        int fellTick = -1;
        int firstSeenTick = -1;
        int retakenTick = -1;
        String firstOrderReason = "none";
        int firstDefendTick = -1;
        int firstRetakeTick = -1;
        float nearestSquad = Float.MAX_VALUE;

        private Trace(Squad subject, long[] members, Faction side) {
            this.subject = subject;
            this.members = members;
            this.side = side;
        }

        String caption(BattleSimulation sim) {
            return sim.defenceStructures().isEmpty() ? "generator gone"
                    : "generator held by " + sim.defenceStructures().get(0).holder();
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (fellTick < 0 && !sim.defenceStructures().isEmpty()
                    && sim.defenceStructures().get(0).holder() != sim.defenceStructures().get(0).originalHolder()) {
                fellTick = tick;
            }
            if (fellTick >= 0 && retakenTick < 0 && !sim.defenceStructures().isEmpty()
                    && sim.defenceStructures().get(0).holder() == sim.defenceStructures().get(0).originalHolder()) {
                retakenTick = tick;
            }
            if (firstSeenTick < 0 && sim.playerSeesCell(GEN_X, GEN_Y)) firstSeenTick = tick;
            // The order counts only if the commander says why it gave it: a lane order that happens to end near the
            // generator is not the commander treating the generator as work.
            CommanderSnapshot<?> snapshot = sim.getCommanderSnapshot(side);
            CommandDirective directive = snapshot == null ? null : snapshot.directiveFor(subject.id);
            ObjectiveAssignment order = subject.assignedObjective;
            if (firstOrderTick < 0 && directive != null && directive.reason().startsWith("DEFENCE_STRUCTURE_")
                    && order != null && order.targetCellX() >= 0
                    && Math.max(Math.abs(order.targetCellX() - GEN_X), Math.abs(order.targetCellY() - GEN_Y)) <= NEAR) {
                firstOrderTick = tick;
                firstOrderReason = directive.reason();
            }
            if (directive != null && order != null && order.targetCellX() >= 0
                    && Math.max(Math.abs(order.targetCellX() - GEN_X), Math.abs(order.targetCellY() - GEN_Y)) <= NEAR) {
                if (firstDefendTick < 0 && directive.reason().equals("DEFENCE_STRUCTURE_DEFEND")) firstDefendTick = tick;
                if (firstRetakeTick < 0 && directive.reason().equals("DEFENCE_STRUCTURE_RETAKE")) firstRetakeTick = tick;
            }
            float cx = 0f;
            float cy = 0f;
            int alive = 0;
            for (long member : members) {
                if (!sim.getRoster().isAliveById(member)) continue;
                cx += sim.world().x(member);
                cy += sim.world().y(member);
                alive++;
            }
            if (alive == 0) return;
            nearestSquad = Math.min(nearestSquad,
                    (float) Math.max(Math.abs(cx / alive - GEN_X), Math.abs(cy / alive - GEN_Y)));
        }
    }
}
