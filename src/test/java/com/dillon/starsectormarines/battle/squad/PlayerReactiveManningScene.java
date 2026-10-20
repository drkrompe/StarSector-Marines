package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.ReactiveWatch;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.ReactiveManningService.Status;
import com.dillon.starsectormarines.battle.squad.ReactiveManningService.View;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveDefendAreaOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveMoveOrder;
import com.dillon.starsectormarines.battle.squad.SquadMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Does a player's squad in the GARRISON stance man a nearby trench when its side believes the trench pressed, and
 * give it back when the pressure passes?</b> The acceptance of the player's opt-in reactive manning
 * (`garrisonables.md`, `ai-nouns.md` under fighting positions).
 *
 * <p>Open ground with one straight twelve-cell trench. A six-man rifle squad stands idle, with no order, ten cells
 * south-west of it. An enemy party of six sits sealed in a box in the far corner, and a scripted observer (a marine
 * sealed in a box of his own in the other) reports it to the marines' side as standing just east of the trench every second from {@link #REPORT_FROM} to
 * {@link #REPORT_UNTIL}: belief is what the stance reads, and the box keeps the squad from seeing (or shooting) anybody,
 * so the question is about the stance and not about a fight. The reports then stop and the belief fades.
 *
 * <p>Loops: {@code opted-in} (the squad is put in the stance at tick 0), {@code not-opted} (the same squad without it),
 * {@code explicit-order} (opted in; once it mans the trench the player orders it to a cell beyond its watch), and
 * {@code breach} (opted in; once it is on the trench a believed enemy is reported in the trench beside it).
 *
 * <p>Verdicts assert the stance's mechanics, timed from the side's own belief: the trench is manned within a few
 * pulses of being believed pressed and not before, the order is the occupy a click gives, it is held while the
 * pressure stands and given back only after it has stayed below the release level for the dwell, and the squad walks
 * back to its anchor; an unopted squad does nothing; an explicit order replaces the stance's at once and the stance
 * stays aside while it stands, then watches from where the squad stopped; a breach falls back and the trench is not
 * re-manned. Ticks and dug-in counts are metrics.
 */
public final class PlayerReactiveManningScene implements BehaviorScene {

    public static final String OPTED_IN = "opted-in";
    public static final String NOT_OPTED = "not-opted";
    public static final String EXPLICIT_ORDER = "explicit-order";
    public static final String BREACH = "breach";

    private static final int WIDTH = 96;
    private static final int HEIGHT = 64;
    private static final int TRENCH_Y = 24;
    private static final int TRENCH_X0 = 30, TRENCH_X1 = 41;
    private static final int SQUAD_X = 24, SQUAD_Y = 34;
    private static final int SQUAD_SIZE = 6;
    private static final int PARTY = 6;
    /** Where the party is reported: just east of the trench. */
    private static final int REPORT_X = 46, REPORT_Y = 22;
    private static final int REPORT_FROM = 150;
    private static final int REPORT_UNTIL = 1650;
    private static final int REPORT_EVERY = 30;
    /** The explicit order's cell: further from the trench than the watch reaches. */
    private static final int ORDER_X = 10, ORDER_Y = 50;
    private static final int TICKS = 4200;
    private static final int PULSE = ReactiveWatch.PULSE_TICKS;
    /** "A few pulses": the stance's first pulse after the belief, and the influence field's own refresh. */
    private static final int MAN_BOUND_TICKS = 3 * PULSE;
    private static final int FRAME_EVERY = 15;
    private static final int BOX_X = 86, BOX_Y = 54;

    private final int frameEvery;

    public PlayerReactiveManningScene() { this(FRAME_EVERY); }

    public PlayerReactiveManningScene(int frameEvery) { this.frameEvery = frameEvery; }

    @Override public String id() { return "player-reactive-manning"; }

    @Override public String label() {
        return "Player reactive manning: a squad in the GARRISON stance mans a nearby trench its side believes pressed "
                + "and gives it back when the pressure passes; unopted it does nothing; an explicit order overrides it";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(run(OPTED_IN, frames));
        reports.add(run(NOT_OPTED, frames));
        reports.add(run(EXPLICIT_ORDER, frames));
        reports.add(run(BREACH, frames));
        return reports;
    }

    private SceneReport run(String loop, FrameSink frames) {
        boolean opted = !loop.equals(NOT_OPTED);
        NavigationGrid field = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology ground = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) field.setWalkableFloor(x, y);
        SceneBuilder builder = SceneBuilder.over(field, ground).missionCompletion(false);
        builder.squad("rifles").faction(Faction.MARINE).type(UnitType.MARINE).size(SQUAD_SIZE)
                .primary("weapon.field-rifle").inFile(SQUAD_X - 2, SQUAD_Y, 1, 0).done();
        builder.wall(BOX_X - 4, BOX_Y - 4, BOX_X + 4, BOX_Y - 4).wall(BOX_X - 4, BOX_Y + 4, BOX_X + 4, BOX_Y + 4)
                .wall(BOX_X - 4, BOX_Y - 3, BOX_X - 4, BOX_Y + 3).wall(BOX_X + 4, BOX_Y - 3, BOX_X + 4, BOX_Y + 3);
        // The observer: one marine sealed in a box of his own, who sees nothing and reports what the scene tells him.
        builder.wall(2, 2, 6, 2).wall(2, 6, 6, 6).wall(2, 3, 2, 5).wall(6, 3, 6, 5);
        builder.squad("observer").faction(Faction.MARINE).type(UnitType.MARINE).size(1).at(4, 4).stationary().done();
        for (int i = 0; i < PARTY; i++) {
            builder.unit("party-" + i, Faction.DEFENDER, UnitType.MILITIA, BOX_X - 2 + i % 3, BOX_Y - 1 + i / 3,
                    spec -> spec.moveSpeed(0f));
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        digTrench(sim);
        int squadId = world.squadId("rifles");
        Squad squad = sim.getSquad(squadId);
        int observer = world.squadId("observer");
        long[] party = new long[PARTY];
        for (int i = 0; i < PARTY; i++) party[i] = world.unit("party-" + i);
        ReactiveManningService stance = sim.getReactiveManning();
        if (opted) stance.setEnabled(squadId, true);

        int pressedTick = -1, mannedTick = -1, coolTick = -1, releaseTick = -1, homeTick = -1;
        int heldWhilePressedBreaks = 0, mannedBeforePressure = 0, maxDugIn = 0, arrivedTick = -1;
        boolean occupyIsClick = false, everOrdered = false, everOnTrench = false;
        int orderTick = -1, overrideTick = -1, stanceWhileOrdered = 0, resumedTick = -1;
        int breachTick = -1, fallBackTick = -1, remannedAfterBreach = 0;
        int anchorX = -1, anchorY = -1, resumedAnchorX = -1, resumedAnchorY = -1;
        int consecutiveCool = 0, coolDwellAtRelease = -1;
        Status previous = Status.OFF;
        for (int tick = 0; tick < TICKS; tick++) {
            if (tick >= REPORT_FROM && tick < REPORT_UNTIL && tick % REPORT_EVERY == 0) {
                for (int i = 0; i < PARTY; i++) {
                    SquadBeliefs.sighted(sim.getSquad(observer), party[i], REPORT_X + i % 3, REPORT_Y + i / 3,
                            sim.getSimTickIndex());
                }
            }
            if (loop.equals(BREACH) && arrivedTick >= 0 && breachTick < 0 && tick >= arrivedTick + PULSE) {
                breachTick = tick;
            }
            if (breachTick >= 0 && tick < breachTick + 4 * PULSE && tick % REPORT_EVERY == 0) {
                int[] cell = trenchCellNear(sim, squad);
                SquadBeliefs.sighted(sim.getSquad(observer), party[0], cell[0], cell[1], sim.getSimTickIndex());
            }
            if (loop.equals(EXPLICIT_ORDER) && mannedTick >= 0 && orderTick < 0 && tick >= mannedTick + 2 * PULSE) {
                orderTick = tick;
                sim.getSquadMoveOrderService().requestMove(squadId, ORDER_X, ORDER_Y);
            }
            sim.advance(BattleSimulation.TICK_DT);

            float pressure = trenchPressure(sim);
            if (pressure >= ReactiveWatch.START_PRESSURE && pressedTick < 0) pressedTick = tick;
            consecutiveCool = pressure < ReactiveWatch.RELEASE_PRESSURE ? consecutiveCool + 1 : 0;
            if (pressedTick >= 0 && pressure < ReactiveWatch.RELEASE_PRESSURE && coolTick < 0 && mannedTick >= 0) {
                coolTick = tick;
            }
            View view = stance.view(squadId);
            ActiveOrder active = sim.getSquadMoveOrderService().activeOrder(squadId);
            if (active != null) everOrdered = true;
            if (view.status() == Status.MANNING_TRENCH) {
                if (pressedTick < 0) mannedBeforePressure++;
                if (mannedTick < 0) {
                    mannedTick = tick;
                    anchorX = view.anchorX();
                    anchorY = view.anchorY();
                    occupyIsClick = active instanceof ActiveDefendAreaOrder defend && defend.isOccupy()
                            && defend.occupyRunId() == runId(sim);
                }
                if (breachTick >= 0 && fallBackTick >= 0) remannedAfterBreach++;
            }
            if (previous == Status.MANNING_TRENCH && view.status() != Status.MANNING_TRENCH) {
                if (pressure >= ReactiveWatch.RELEASE_PRESSURE && view.status() != Status.UNDER_ORDERS
                        && breachTick < 0) {
                    heldWhilePressedBreaks++;
                }
                if (view.status() == Status.RETURNING || view.status() == Status.WATCHING) {
                    if (breachTick >= 0 && fallBackTick < 0) fallBackTick = tick;
                    else if (releaseTick < 0) {
                        releaseTick = tick;
                        coolDwellAtRelease = consecutiveCool;
                    }
                }
                if (view.status() == Status.UNDER_ORDERS && overrideTick < 0) overrideTick = tick;
            }
            if (orderTick >= 0 && active instanceof ActiveMoveOrder move && move.requestedX() == ORDER_X
                    && move.requestedY() == ORDER_Y && view.status() != Status.UNDER_ORDERS) {
                stanceWhileOrdered++;
            }
            if (overrideTick >= 0 && resumedTick < 0 && view.status() == Status.WATCHING) {
                resumedTick = tick;
                resumedAnchorX = view.anchorX();
                resumedAnchorY = view.anchorY();
            }
            int dug = 0, onTrench = 0;
            FightingPositionIndex index = sim.fightingPositions();
            for (long member : world.members("rifles")) {
                if (!sim.world().isAlive(member)) continue;
                if (sim.isDugIn(member)) dug++;
                if (index.runIdAt(sim.world().cellX(member), sim.world().cellY(member)) >= 0) onTrench++;
            }
            if (onTrench > 0) everOnTrench = true;
            if (mannedTick >= 0 && arrivedTick < 0 && onTrench >= SQUAD_SIZE / 2) arrivedTick = tick;
            maxDugIn = Math.max(maxDugIn, dug);
            if (releaseTick >= 0 && homeTick < 0 && anchorX >= 0
                    && Math.max(Math.abs(squad.centroidX - anchorX), Math.abs(squad.centroidY - anchorY))
                    <= ReactiveManningSystem.HOME_CELLS + 2) {
                homeTick = tick;
            }
            previous = view.status();
            if (tick % frameEvery == 0) {
                frames.frame(loop, sim, tick, loop + " / tick " + tick + " / " + view.status()
                        + String.format(" / pressure %.1f", pressure));
            }
        }

        List<Verdict> verdicts = new ArrayList<>();
        if (!opted) {
            verdicts.add(Verdict.of("does-nothing", !everOrdered && stance.view(squadId).status() == Status.OFF
                            && !everOnTrench,
                    "without the stance the squad was given no order and stood on the trench on no tick"
                            + " (ordered " + everOrdered + ", on trench " + everOnTrench + ")"));
            verdicts.add(Verdict.of("pressure-was-believed", pressedTick >= 0,
                    "the trench was believed pressed from tick " + pressedTick));
        } else {
            verdicts.add(Verdict.of("quiet-before", mannedBeforePressure == 0,
                    "manned on " + mannedBeforePressure + " ticks before its side believed the trench pressed"));
            verdicts.add(Verdict.of("mans-under-pressure", pressedTick >= 0 && mannedTick >= 0
                            && mannedTick <= pressedTick + MAN_BOUND_TICKS,
                    "believed pressed at tick " + pressedTick + ", manned at tick " + mannedTick));
            verdicts.add(Verdict.of("occupy-order", occupyIsClick,
                    "the stance's order was the occupy a click on the trench gives"));
            if (loop.equals(OPTED_IN)) {
                verdicts.add(Verdict.of("held-while-pressed", heldWhilePressedBreaks == 0,
                        heldWhilePressedBreaks + " releases while the pressure stood at or above the release level"));
                if (coolTick >= 0 && coolTick + 8 * PULSE < TICKS) {
                    verdicts.add(Verdict.of("released-after-dwell", releaseTick >= 0
                                    && coolDwellAtRelease >= (ReactiveWatch.DWELL_PULSES - 1) * PULSE,
                            "the pressure fell below the release level at tick " + coolTick + "; released at tick "
                                    + releaseTick + " after " + coolDwellAtRelease + " cool ticks"));
                }
                if (releaseTick >= 0 && releaseTick + 30 * 30 < TICKS) {
                    verdicts.add(Verdict.of("returns-to-anchor", homeTick >= 0,
                            "released at tick " + releaseTick + ", back at its anchor " + anchorX + "," + anchorY
                                    + " at tick " + homeTick));
                }
            }
            if (loop.equals(EXPLICIT_ORDER)) {
                verdicts.add(Verdict.of("explicit-wins", orderTick >= 0 && overrideTick >= 0
                                && overrideTick <= orderTick + 1,
                        "ordered at tick " + orderTick + ", the stance stood aside at tick " + overrideTick));
                verdicts.add(Verdict.of("stance-stands-aside", stanceWhileOrdered == 0,
                        "the stance acted on " + stanceWhileOrdered + " ticks while the player's order stood"));
                verdicts.add(Verdict.of("resumes-from-where-it-stopped", resumedTick >= 0
                                && Math.max(Math.abs(resumedAnchorX - ORDER_X), Math.abs(resumedAnchorY - ORDER_Y)) <= 4,
                        "the order ended and the stance watched again from " + resumedAnchorX + "," + resumedAnchorY
                                + " at tick " + resumedTick));
            }
            if (loop.equals(BREACH) && breachTick >= 0) {
                verdicts.add(Verdict.of("falls-back", fallBackTick >= 0 && fallBackTick <= breachTick + 2 * PULSE,
                        "the breach was reported at tick " + breachTick + "; the squad fell back at tick "
                                + fallBackTick));
                verdicts.add(Verdict.of("not-re-manned", remannedAfterBreach == 0,
                        "the trench was manned again on " + remannedAfterBreach + " ticks after the fall back"));
            }
        }

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("pressedTick", pressedTick);
        metrics.put("mannedTick", mannedTick);
        metrics.put("arrivedTick", arrivedTick);
        metrics.put("maxDugIn", maxDugIn);
        metrics.put("coolTick", coolTick);
        metrics.put("releaseTick", releaseTick);
        metrics.put("homeTick", homeTick);
        metrics.put("orderTick", orderTick);
        metrics.put("overrideTick", overrideTick);
        metrics.put("resumedTick", resumedTick);
        metrics.put("breachTick", breachTick);
        metrics.put("fallBackTick", fallBackTick);
        sim.close();
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** A straight trench, each cell joined to its east and west neighbours (mask N1 E2 S4 W8). */
    private static void digTrench(BattleSimulation sim) {
        TileRegistry registry = TileRegistry.installed();
        for (int x = TRENCH_X0; x <= TRENCH_X1; x++) {
            int mask = (x < TRENCH_X1 ? 2 : 0) | (x > TRENCH_X0 ? 8 : 0);
            DoodadDef def = registry.doodad("doodad.fort-trench-" + mask);
            if (def == null) throw new IllegalStateException("no trench piece for mask " + mask);
            sim.addDoodad(new Doodad(x, TRENCH_Y, def));
        }
        int run = sim.fightingPositions().runIdAt(TRENCH_X0, TRENCH_Y);
        if (run < 0 || sim.fightingPositions().runSize(run) != TRENCH_X1 - TRENCH_X0 + 1) {
            throw new IllegalStateException("the trench did not come out as one fighting position");
        }
    }

    private static int runId(BattleSimulation sim) {
        return sim.fightingPositions().runIdAt(TRENCH_X0, TRENCH_Y);
    }

    /** The trench's believed pressure as the stance reads it: its covered blocks' highest hostile reading. */
    private static float trenchPressure(BattleSimulation sim) {
        // A read, as the stance's own: it keeps the side's picture refreshing in the loop that has no stance too.
        CommanderInfluenceSnapshot influence = sim.getCommanderInfluence(Faction.MARINE);
        GarrisonableRegistry registry = sim.garrisonables();
        int run = runId(sim);
        if (influence == null || run < 0 || influence.width() != registry.blockWidth()) return 0f;
        float best = 0f;
        for (int block : registry.coveredBlocks(run)) {
            best = Math.max(best, influence.hostileAt(block % registry.blockWidth(), block / registry.blockWidth()));
        }
        return best;
    }

    /** A trench cell beside the squad, where the breach is reported. */
    private static int[] trenchCellNear(BattleSimulation sim, Squad squad) {
        int x = Math.max(TRENCH_X0, Math.min(TRENCH_X1, Math.round(squad.centroidX) + 2));
        return new int[]{x, TRENCH_Y};
    }
}
