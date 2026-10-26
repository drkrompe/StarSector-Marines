package com.dillon.starsectormarines.battle.vehicle;

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
import java.util.Locale;
import java.util.Map;

/**
 * The dozer breach's acceptance: a {@link VehicleType#DOZER_BREACHER} pushes a route through a wall line that a
 * {@link VehicleType#HEAVY_APC} then drives through, against a control in which the APC is alone.
 *
 * <p>The field is cut in two by a two-cell wall running edge to edge. Its western part is ordinary wall (100
 * structure, the blade's threshold); its eastern part is fortified (150), which the blade will not push. Both loops
 * give their vehicles real player move orders through {@link VehicleMoveOrderService}, so the route proof, the
 * rolling planner and the physical sweep are all production.
 *
 * <p>{@code breach} orders the APC across first, which takes it no farther than the near side of the wall (a vehicle
 * order goes to the nearest cell the hull can reach); then the dozer, whose proof reads ground it can open as
 * reachable; then, once the dozer is through, the APC again over the same field. {@code control} has the APC alone
 * and ordered across for the whole run. Nothing here clears a cell: the gap is whatever the blade made. Orders are
 * planned from a queue, so each is read once it has been resolved.
 */
public final class DozerBreachScene implements BehaviorScene {

    public static final String BREACH = "breach";
    public static final String CONTROL = "control";

    static final int WIDTH = 64;
    static final int HEIGHT = 48;
    /** The wall line's two rows. */
    static final int WALL_Y0 = 22;
    static final int WALL_Y1 = 23;
    /** First fortified column; west of it is ordinary wall. */
    static final int FORTIFIED_X = 40;
    static final int ORDINARY_STRUCTURE = DozerBlade.MAX_PUSH_STRUCTURE;
    static final int FORTIFIED_STRUCTURE = 150;

    static final float DOZER_X = 20.5f, DOZER_Y = 9.5f;
    static final float APC_X = 30.5f, APC_Y = 8.5f;
    /** Where the dozer is sent: well past the wall and west, out of the APC's way. */
    static final int DOZER_GOAL_X = 14, DOZER_GOAL_Y = 40;
    /** Where the APC is sent, both loops. */
    static final int APC_GOAL_X = 26, APC_GOAL_Y = 36;
    /** The dozer counts as through once its centre is this far north. */
    private static final float THROUGH_Y = WALL_Y1 + 5f;
    private static final int TICKS = 1800;

    private final int frameEvery;

    public DozerBreachScene() { this(15); }

    public DozerBreachScene(int frameEvery) { this.frameEvery = frameEvery; }

    @Override public String id() { return "dozer-breach"; }

    @Override public String label() {
        return "Dozer breach: a dozer breacher pushes a route through an ordinary wall line and a troop APC drives "
                + "through it; the control is the APC alone, refused at the wall";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(run(BREACH, true, frames));
        reports.add(run(CONTROL, false, frames));
        return reports;
    }

    /** The wall line, ordinary west of {@link #FORTIFIED_X} and fortified from it, stamped as generation stamps walls. */
    static void stampWall(NavigationGrid grid, CellTopology topology) {
        for (int y = WALL_Y0; y <= WALL_Y1; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, x < FORTIFIED_X ? ORDINARY_STRUCTURE : FORTIFIED_STRUCTURE);
                topology.setTag(x, y, CellTopology.Tag.WALL, true);
            }
        }
    }

    private SceneReport run(String loop, boolean dozer, FrameSink frames) {
        NavigationGrid field = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology ground = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) field.setWalkableFloor(x, y);
        stampWall(field, ground);
        SceneWorld world = SceneBuilder.over(field, ground)
                .missionCompletion(false)
                // A sealed box in the far corner holds the one defender, so the sim keeps ticking. Its walls carry
                // no structure, so nothing can push them.
                .wall(54, 38, 62, 38).wall(54, 46, 62, 46).wall(54, 38, 54, 46).wall(62, 38, 62, 46)
                .unit("holdout", Faction.DEFENDER, UnitType.MARINE, 58, 42)
                .build();
        BattleSimulation sim = world.sim();
        NavigationGrid grid = sim.getGrid();

        VehicleMoveOrderService orders = sim.getVehicleMoveOrderService();
        long apc = sim.convoy().spawn(VehicleType.HEAVY_APC, Faction.MARINE, VehicleMission.deployed(APC_X, APC_Y));
        sim.convoy().body(apc).teleport(APC_X, APC_Y, 0f);
        long bladed = 0L;
        if (dozer) {
            bladed = sim.convoy().spawn(VehicleType.DOZER_BREACHER, Faction.MARINE,
                    VehicleMission.deployed(DOZER_X, DOZER_Y));
            sim.convoy().body(bladed).teleport(DOZER_X, DOZER_Y, 0f);
        }

        orders.requestMove(apc, APC_GOAL_X, APC_GOAL_Y);
        settle(sim, orders, apc);
        VehicleMoveOrderService.RefusedOrder firstApc = orders.refusal(apc);
        VehicleMoveOrderService.ActiveOrder firstRoute = orders.activeOrder(apc);
        boolean apcHeldAtWall = firstRoute != null ? firstRoute.destinationY() < WALL_Y0
                : firstApc != null && firstApc.reason() == VehicleMoveOrderService.Refusal.NO_ROUTE;

        boolean dozerAccepted = false;
        if (dozer) {
            orders.requestMove(bladed, DOZER_GOAL_X, DOZER_GOAL_Y);
            settle(sim, orders, bladed);
            dozerAccepted = orders.activeOrder(bladed) != null;
        }

        GroundBody apcBody = sim.convoy().body(apc);
        GroundBody dozerBody = dozer ? sim.convoy().body(bladed) : null;
        int dozerThroughTick = -1, apcOrderTick = -1, apcThroughTick = -1;
        boolean apcSecondAccepted = false, apcSecondDecided = false, legal = true;
        float apcMaxY = apcBody.y;
        for (int tick = 0; tick < TICKS; tick++) {
            if (dozer && dozerThroughTick < 0 && dozerBody.y > THROUGH_Y) {
                dozerThroughTick = tick;
            }
            // Once the dozer is clear of its own gap, the APC is ordered across the same field again.
            if (dozer && apcOrderTick < 0 && dozerThroughTick >= 0 && dozerBody.y > THROUGH_Y + 4f) {
                orders.requestMove(apc, APC_GOAL_X, APC_GOAL_Y);
                apcOrderTick = tick;
            }
            if (!dozer && tick > 0 && tick % 300 == 0) orders.requestMove(apc, APC_GOAL_X, APC_GOAL_Y);
            sim.advance(BattleSimulation.TICK_DT);
            if (apcOrderTick >= 0 && !apcSecondDecided && !orders.pending(apc)) {
                apcSecondAccepted = orders.activeOrder(apc) != null;
                apcSecondDecided = true;
            }
            apcMaxY = Math.max(apcMaxY, apcBody.y);
            if (apcThroughTick < 0 && apcBody.y > THROUGH_Y) apcThroughTick = tick;
            legal &= VehicleFootprint.isPoseFeasible(apcBody.x, apcBody.y, apcBody.facingDegrees,
                    VehicleType.HEAVY_APC.visualLengthCells, VehicleType.HEAVY_APC.visualWidthCells, grid);
            if (dozer) legal &= VehicleFootprint.isPoseFeasible(dozerBody.x, dozerBody.y, dozerBody.facingDegrees,
                    VehicleType.DOZER_BREACHER.visualLengthCells, VehicleType.DOZER_BREACHER.visualWidthCells, grid);
            if (tick % frameEvery == 0) frames.frame(loop, sim, tick, caption(loop, tick, grid));
        }

        int openedOrdinary = 0, openedFortified = 0;
        for (int y = WALL_Y0; y <= WALL_Y1; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (!grid.isWalkable(x, y)) continue;
                if (x < FORTIFIED_X) openedOrdinary++;
                else openedFortified++;
            }
        }

        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("apc-held-at-wall", apcHeldAtWall,
                "the APC's first order across the intact wall takes it no farther than the near side"));
        verdicts.add(Verdict.of("whole-hull-clear", legal,
                "every applied pose of every hull clears the live grid: nothing drove through a standing wall"));
        verdicts.add(Verdict.of("fortified-stands", openedFortified == 0,
                openedFortified + " fortified wall cells opened"));
        if (dozer) {
            verdicts.add(Verdict.of("dozer-route-proved", dozerAccepted,
                    "the dozer's order is accepted over ground its blade can open"));
            verdicts.add(Verdict.of("dozer-through", dozerThroughTick >= 0,
                    "the dozer is through the wall line at tick " + dozerThroughTick));
            verdicts.add(Verdict.of("breach-opened", openedOrdinary > 0,
                    openedOrdinary + " ordinary wall cells pushed down"));
            verdicts.add(Verdict.of("apc-route-through-breach", apcSecondAccepted,
                    "the APC's second order, after the breach, is accepted (ordered at tick " + apcOrderTick + ")"));
            verdicts.add(Verdict.of("apc-through", apcThroughTick >= 0,
                    "the APC drives through the gap the dozer made, through at tick " + apcThroughTick));
        } else {
            verdicts.add(Verdict.of("apc-held", apcThroughTick < 0 && openedOrdinary == 0,
                    "alone, the APC never crosses: furthest north y=" + String.format(Locale.ROOT, "%.1f", apcMaxY)));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("openedOrdinaryCells", openedOrdinary);
        metrics.put("openedFortifiedCells", openedFortified);
        metrics.put("dozerThroughTick", dozerThroughTick);
        metrics.put("apcOrderTick", apcOrderTick);
        metrics.put("apcThroughTick", apcThroughTick);
        metrics.put("apcFinalX", apcBody.x);
        metrics.put("apcFinalY", apcBody.y);
        if (dozer) {
            metrics.put("dozerFinalX", dozerBody.x);
            metrics.put("dozerFinalY", dozerBody.y);
        }
        sim.close();
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /**
     * Ticks until {@code vehicle}'s queued order is resolved: orders are planned from a queue, a couple of route proofs
     * a tick, so one may wait a few ticks for its verdict.
     */
    private static void settle(BattleSimulation sim, VehicleMoveOrderService orders, long vehicle) {
        do sim.advance(BattleSimulation.TICK_DT);
        while (orders.pending(vehicle));
    }

    private static String caption(String loop, int tick, NavigationGrid grid) {
        int open = 0;
        for (int y = WALL_Y0; y <= WALL_Y1; y++) for (int x = 0; x < FORTIFIED_X; x++) if (grid.isWalkable(x, y)) open++;
        return loop + " / tick " + tick + " / wall cells pushed " + open;
    }
}
