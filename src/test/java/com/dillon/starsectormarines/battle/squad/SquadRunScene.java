package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.control.MarineSprint;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Does a squad told to run get there sooner, on its own stamina, and walk again when the order is over?</b>
 * The acceptance of the run toggle ({@code mission-command-nouns.md}, Run).
 *
 * <p>Open ground. A six-man rifle squad is ordered ninety cells east. Loops: {@code walk} (the order alone) and
 * {@code run} (the same order, and R once the squad is under way). Verdicts: the runners arrive sooner than the
 * walkers did; they spend stamina, run themselves out at least once and keep going at a walk; the walkers spend
 * none; the run ends with the order; and the squad's stamina comes back once it stands. Arrival ticks and the
 * lowest mean stamina are metrics.
 */
public final class SquadRunScene implements BehaviorScene {

    public static final String WALK = "walk";
    public static final String RUN = "run";

    private static final int WIDTH = 120;
    private static final int HEIGHT = 32;
    private static final int START_X = 12, Y = 16;
    private static final int GOAL_X = 102;
    private static final int SQUAD_SIZE = 6;
    private static final int ORDER_TICK = 15;
    /** R is pressed once the squad is moving. */
    private static final int RUN_TICK = 60;
    private static final int TICKS = 2700;
    private static final int FRAME_EVERY = 15;

    private final int frameEvery;

    public SquadRunScene() { this(FRAME_EVERY); }

    public SquadRunScene(int frameEvery) { this.frameEvery = frameEvery; }

    @Override public String id() { return "squad-run"; }

    @Override public String label() {
        return "Squad run: R on a squad under way runs it on its Marines' stamina; it arrives sooner than walking"
                + " and walks again when the order is over";
    }

    @Override public int frameDelayMillis() { return 60; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        int[] walkArrival = {-1};
        reports.add(run(WALK, frames, walkArrival));
        reports.add(run(RUN, frames, walkArrival));
        return reports;
    }

    private SceneReport run(String loop, FrameSink frames, int[] walkArrival) {
        boolean running = loop.equals(RUN);
        NavigationGrid field = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology ground = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) field.setWalkableFloor(x, y);
        SceneBuilder builder = SceneBuilder.over(field, ground).missionCompletion(false);
        builder.squad("rifles").faction(Faction.MARINE).type(UnitType.MARINE).size(SQUAD_SIZE)
                .primary("weapon.field-rifle").inFile(START_X, Y - 3, 0, 1).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        int squadId = world.squadId("rifles");
        long[] members = world.members("rifles");
        SquadMoveOrderService orders = sim.getSquadMoveOrderService();
        MarineSprint stamina = sim.directControl().marineStamina();

        int arrivedTick = -1, exhaustedTick = -1, recoveredTick = -1;
        float lowest = 1f;
        boolean runningAfterArrival = false, toggled = false;
        int boostedTicks = 0;
        for (int tick = 0; tick < TICKS; tick++) {
            if (tick == ORDER_TICK) orders.requestMove(squadId, GOAL_X, Y);
            if (running && tick == RUN_TICK) toggled = orders.toggleRun(squadId);
            sim.advance(BattleSimulation.TICK_DT);
            float mean = 0f;
            for (long member : members) {
                MarineSprint.Status status = stamina.status(member);
                mean += status.staminaFraction();
                if (status.exhausted() && exhaustedTick < 0) exhaustedTick = tick;
                if (sim.squadRun().boosted(member)) boostedTicks++;
            }
            mean /= members.length;
            lowest = Math.min(lowest, mean);
            if (tick > ORDER_TICK + 1 && arrivedTick < 0 && orders.activeOrder(squadId) == null) arrivedTick = tick;
            if (arrivedTick >= 0) {
                runningAfterArrival |= orders.isRunning(squadId);
                if (recoveredTick < 0 && mean >= .999f) recoveredTick = tick;
            }
            if (frames != FrameSink.NONE && frames.records(loop, sim, tick, frameEvery)) {
                frames.frame(loop, sim, tick, loop + " / tick " + tick + " / stamina "
                        + Math.round(mean * 100f) + "%" + (orders.isRunning(squadId) ? " / RUN" : ""));
            }
            frames.advanced(loop, sim, tick);
        }

        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("arrives", arrivedTick >= 0, "the order was over at tick " + arrivedTick));
        if (running) {
            verdicts.add(Verdict.of("toggled-under-way", toggled, "R at tick " + RUN_TICK + " ran the squad"));
            verdicts.add(Verdict.of("arrives-sooner", arrivedTick >= 0 && walkArrival[0] >= 0
                            && arrivedTick < walkArrival[0],
                    "running arrived at tick " + arrivedTick + ", walking at tick " + walkArrival[0]));
            verdicts.add(Verdict.of("spends-stamina", lowest < .5f,
                    "the squad's mean stamina fell to " + lowest));
            verdicts.add(Verdict.of("runs-out-and-walks-on", exhaustedTick >= 0 && arrivedTick >= 0,
                    "first Marine winded at tick " + exhaustedTick + "; the squad still arrived"));
            verdicts.add(Verdict.of("run-ends-with-order", !runningAfterArrival,
                    "after arriving the squad was " + (runningAfterArrival ? "still" : "no longer") + " running"));
            verdicts.add(Verdict.of("recovers", recoveredTick >= 0,
                    "stamina full again at tick " + recoveredTick));
        } else {
            walkArrival[0] = arrivedTick;
            verdicts.add(Verdict.of("walks-free", lowest >= .999f && boostedTicks == 0,
                    "walking, the squad's mean stamina stayed at " + lowest + " with " + boostedTicks
                            + " boosted Marine-ticks"));
        }

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("arrivedTick", arrivedTick);
        metrics.put("exhaustedTick", exhaustedTick);
        metrics.put("recoveredTick", recoveredTick);
        metrics.put("lowestMeanStamina", lowest);
        metrics.put("boostedMarineTicks", boostedTicks);
        sim.close();
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }
}
