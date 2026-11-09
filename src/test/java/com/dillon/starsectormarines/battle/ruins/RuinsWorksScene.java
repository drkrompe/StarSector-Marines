package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Consumer;
import com.dillon.starsectormarines.battle.satchel.SatchelChargeService;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A ruins site's works played on a small arena (`ruins-works.md`): a wall across the field with a seal door in it, a
 * power hall and a junction on the near side. The works are the production {@link RuinsWorks}, pressed through their
 * own prompt verbs as the player presses them; the squads stand where the player would have walked them.
 * <ul>
 *   <li>{@code powered}: a crew holds the hall's console. The hall restarts, and the door, fed by the junction, opens
 *       as passage.</li>
 *   <li>{@code forced}: a sapper beside the shut door is told to set a charge on it. He sets one on a door cell, and
 *       the blast that cuts it forces the door open for good.</li>
 *   <li>{@code collapse}: the wall is a collapse across the whole field, with no door. A sapper sets a charge on it and
 *       the blast clears it.</li>
 * </ul>
 * The verdicts assert the works' rules (the restart, the press reaching a charge, a cut cell read as forced or
 * cleared); ticks are readings.
 */
public final class RuinsWorksScene implements BehaviorScene {

    private static final int TICKS = 600;
    private static final int W = 48, H = 24;
    private static final int WALL_X = 30, DOOR_Y = 12;

    @Override public String id() { return "ruins-works"; }

    @Override public String label() {
        return "Ruins works: a restarted hall powers a seal door open; a charge forces a door or clears a collapse";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(playOne("powered", frames), playOne("forced", frames), playOne("collapse", frames));
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean collapseLoop = loop.equals("collapse");
        SceneBuilder builder = SceneBuilder.openGround(W, H).seed(20261109L).missionCompletion(false);
        if (collapseLoop) builder.seal(WALL_X);
        else builder.wallWithDoor(WALL_X, DOOR_Y);
        if (loop.equals("powered")) {
            builder.squad("crew").faction(Faction.MARINE).size(2).at(6, 6).primary("weapon.smg").stationary().done();
        } else {
            builder.squad("sappers").faction(Faction.MARINE).size(2).at(WALL_X - 2, DOOR_Y).primary("weapon.smg")
                    .special(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID, 2).stationary().done();
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            RuinsWorks works = works(collapseLoop);
            sim.installRuinsWorks(works);
            Trace trace = new Trace(works, loop);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 20, tick -> loop + " / " + trace.state() + " / tick " + tick)
                    .run(TICKS);

            List<Verdict> verdicts = new ArrayList<>();
            switch (loop) {
                case "powered" -> {
                    verdicts.add(Verdict.of("held-console-restarts-the-hall", trace.runningTick >= 0
                                    && trace.runningTick <= ticks(RuinsWorks.RESTART_SECONDS + 1f),
                            "running at tick " + trace.runningTick));
                    verdicts.add(Verdict.of("a-fed-door-opens-once-powered", trace.runningTick >= 0
                                    && trace.openTick >= trace.runningTick && trace.openTick <= trace.runningTick + 1
                                    && sim.getGrid().isWalkable(WALL_X, DOOR_Y),
                            "running at " + trace.runningTick + ", open at " + trace.openTick));
                }
                case "forced" -> {
                    verdicts.add(Verdict.of("the-press-sets-a-charge-on-the-door", trace.chargeTick >= 0
                                    && trace.chargeTick <= ticks(3f),
                            "charge set at tick " + trace.chargeTick));
                    verdicts.add(Verdict.of("a-cut-door-is-forced-for-good", trace.chargeTick < 0
                                    || !sim.getGrid().isWalkable(WALL_X, DOOR_Y) || works.isForced(0),
                            "door " + (sim.getGrid().isWalkable(WALL_X, DOOR_Y) ? "cut" : "standing")
                                    + ", forced " + works.isForced(0)));
                    verdicts.add(Verdict.of("unpowered-it-is-not-opened", works.isForced(0) || !works.isOpen(0),
                            "open " + works.isOpen(0)));
                }
                default -> {
                    verdicts.add(Verdict.of("the-press-sets-a-charge-on-the-collapse", trace.chargeTick >= 0
                                    && trace.chargeTick <= ticks(3f),
                            "charge set at tick " + trace.chargeTick));
                    verdicts.add(Verdict.of("a-cut-collapse-is-cleared", trace.chargeTick < 0
                                    || cut(sim, works.collapse()) == works.collapseCleared(),
                            "cut " + cut(sim, works.collapse()) + ", cleared " + works.collapseCleared()));
                }
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("runningTick", trace.runningTick);
            metrics.put("openTick", trace.openTick);
            metrics.put("chargeTick", trace.chargeTick);
            metrics.put("forcedTick", trace.doneTick);
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static int ticks(float seconds) { return Math.round(seconds / BattleSimulation.TICK_DT); }

    private static boolean cut(BattleSimulation sim, SealDoor target) {
        for (int[] c : target.cells()) if (sim.getGrid().isWalkable(c[0], c[1])) return true;
        return false;
    }

    /** The scene's works: the hall and one junction near the crew, the door fed on the way on; or the collapse. */
    private static RuinsWorks works(boolean collapseLoop) {
        RuinsWorks.Site hall = new RuinsWorks.Site(RuinsWorks.HALL, "hall", 6, 6);
        List<RuinsWorks.Site> junctions = List.of(new RuinsWorks.Site(RuinsWorks.JUNCTION + 0, "junction", 12, 6));
        if (collapseLoop) {
            int[][] cells = new int[5][];
            for (int i = 0; i < 5; i++) cells[i] = new int[]{WALL_X, DOOR_Y - 2 + i};
            SealDoor collapse = new SealDoor("collapse", "collapse", cells, WALL_X + 1, DOOR_Y,
                    new int[]{WALL_X, DOOR_Y - 2, WALL_X, DOOR_Y + 2}, false);
            return new RuinsWorks(new PowerGraph(1, List.of(Branch.ONWARD), List.of()), hall, junctions, List.of(),
                    List.of(), null, collapse);
        }
        SealDoor gate = new SealDoor("gate-1", "gate", new int[][]{{WALL_X, DOOR_Y}}, WALL_X + 1, DOOR_Y,
                new int[]{WALL_X, DOOR_Y, WALL_X, DOOR_Y}, false);
        return new RuinsWorks(new PowerGraph(2, List.of(Branch.ONWARD), List.of(new Consumer("gate-1", 0, Branch.ONWARD))),
                hall, junctions, List.of(gate), List.of(), null, null);
    }

    private static final class Trace implements TickObserver {
        private final RuinsWorks works;
        private final String loop;
        int runningTick = -1;
        int openTick = -1;
        int chargeTick = -1;
        int doneTick = -1;

        Trace(RuinsWorks works, String loop) {
            this.works = works;
            this.loop = loop;
        }

        String state() {
            if (loop.equals("collapse")) return works.collapseCleared() ? "cleared" : "choked";
            if (works.isForced(0)) return "forced";
            return (works.graph().running() ? "running" : "cold") + ", " + (works.isOpen(0) ? "open" : "sealed");
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (tick == 1) {
                if (loop.equals("powered")) works.choose(RuinsWorks.HALL, "restart");
                else if (loop.equals("forced")) works.choose(RuinsWorks.DOOR + "gate-1", RuinsWorks.FORCE);
                else works.choose(RuinsWorks.COLLAPSE, RuinsWorks.FORCE);
            }
            if (runningTick < 0 && works.graph().running()) runningTick = tick;
            if (!loop.equals("collapse") && openTick < 0 && works.isOpen(0)) openTick = tick;
            if (chargeTick < 0 && !sim.satchelCharges().activeCharges().isEmpty()) {
                for (SatchelChargeService.ChargeView charge : sim.satchelCharges().activeCharges()) {
                    if (charge.targetId() == 0L) chargeTick = tick;
                }
            }
            boolean done = loop.equals("collapse") ? works.collapseCleared() : works.isForced(0);
            if (doneTick < 0 && done) doneTick = tick;
        }
    }
}
