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
 * The wall crush's acceptance: the Onager ({@link VehicleType#BREAKTHROUGH_TANK}, ultra-heavy) crushes a route through a
 * wall line by its own weight that a {@link VehicleType#HEAVY_APC} then drives through, against a control in which the
 * APC is alone.
 *
 * <p>The field is cut in two by a two-cell wall running edge to edge. Its western part is ordinary wall (100
 * structure, the ultra-heavy ceiling); its eastern part is fortified (150), which weight alone will not push. Both loops
 * give their vehicles real player move orders through {@link VehicleMoveOrderService}, so the route proof, the
 * rolling planner and the physical sweep are all production.
 *
 * <p>{@code breach} orders the APC across first, which takes it no farther than the near side of the wall (a vehicle
 * order goes to the nearest cell the hull can reach); then the Onager, whose proof reads ground it can open as
 * reachable; then, once the Onager is through, the APC again over the same field. {@code control} has the APC alone
 * and ordered across for the whole run. Nothing here clears a cell: the gap is whatever the hull crushed. Orders are
 * planned from a queue, so each is read once it has been resolved.
 */
public final class WallCrushScene implements BehaviorScene {

    public static final String BREACH = "breach";
    public static final String CONTROL = "control";

    static final int WIDTH = 64;
    static final int HEIGHT = 48;
    /** The wall line's two rows. */
    static final int WALL_Y0 = 22;
    static final int WALL_Y1 = 23;
    /** First fortified column; west of it is ordinary wall. */
    static final int FORTIFIED_X = 40;
    static final int ORDINARY_STRUCTURE = WallCrush.ULTRA_HEAVY_CEILING;
    static final int FORTIFIED_STRUCTURE = 150;

    static final float ONAGER_X = 20.5f, ONAGER_Y = 9.5f;
    static final float APC_X = 30.5f, APC_Y = 8.5f;
    /** Where the Onager is sent: well past the wall and west, out of the APC's way. */
    static final int ONAGER_GOAL_X = 14, ONAGER_GOAL_Y = 40;
    /** Where the APC is sent, both loops. */
    static final int APC_GOAL_X = 26, APC_GOAL_Y = 36;
    /** The Onager counts as through once its centre is this far north. */
    private static final float THROUGH_Y = WALL_Y1 + 5f;
    private static final int TICKS = 1800;

    private final int frameEvery;

    public WallCrushScene() { this(15); }

    public WallCrushScene(int frameEvery) { this.frameEvery = frameEvery; }

    @Override public String id() { return "wall-crush"; }

    @Override public String label() {
        return "Wall crush: the Onager crushes a route through an ordinary wall line by its weight and a troop APC "
                + "drives through it; the control is the APC alone, refused at the wall";
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

    private SceneReport run(String loop, boolean onager, FrameSink frames) {
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
        long heavy = 0L;
        if (onager) {
            heavy = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.MARINE,
                    VehicleMission.deployed(ONAGER_X, ONAGER_Y));
            sim.convoy().body(heavy).teleport(ONAGER_X, ONAGER_Y, 0f);
        }

        orders.requestMove(apc, APC_GOAL_X, APC_GOAL_Y);
        settle(sim, orders, apc);
        VehicleMoveOrderService.RefusedOrder firstApc = orders.refusal(apc);
        VehicleMoveOrderService.ActiveOrder firstRoute = orders.activeOrder(apc);
        boolean apcHeldAtWall = firstRoute != null ? firstRoute.destinationY() < WALL_Y0
                : firstApc != null && firstApc.reason() == VehicleMoveOrderService.Refusal.NO_ROUTE;

        boolean onagerAccepted = false;
        if (onager) {
            orders.requestMove(heavy, ONAGER_GOAL_X, ONAGER_GOAL_Y);
            settle(sim, orders, heavy);
            onagerAccepted = orders.activeOrder(heavy) != null;
        }

        GroundBody apcBody = sim.convoy().body(apc);
        GroundBody onagerBody = onager ? sim.convoy().body(heavy) : null;
        int onagerThroughTick = -1, apcOrderTick = -1, apcThroughTick = -1;
        boolean apcSecondAccepted = false, apcSecondDecided = false, legal = true;
        float apcMaxY = apcBody.y;
        for (int tick = 0; tick < TICKS; tick++) {
            if (onager && onagerThroughTick < 0 && onagerBody.y > THROUGH_Y) {
                onagerThroughTick = tick;
            }
            // Once the Onager is clear of its own gap, the APC is ordered across the same field again.
            if (onager && apcOrderTick < 0 && onagerThroughTick >= 0 && onagerBody.y > THROUGH_Y + 4f) {
                orders.requestMove(apc, APC_GOAL_X, APC_GOAL_Y);
                apcOrderTick = tick;
            }
            if (!onager && tick > 0 && tick % 300 == 0) orders.requestMove(apc, APC_GOAL_X, APC_GOAL_Y);
            sim.advance(BattleSimulation.TICK_DT);
            if (apcOrderTick >= 0 && !apcSecondDecided && !orders.pending(apc)) {
                apcSecondAccepted = orders.activeOrder(apc) != null;
                apcSecondDecided = true;
            }
            apcMaxY = Math.max(apcMaxY, apcBody.y);
            if (apcThroughTick < 0 && apcBody.y > THROUGH_Y) apcThroughTick = tick;
            legal &= VehicleFootprint.isPoseFeasible(apcBody.x, apcBody.y, apcBody.facingDegrees,
                    VehicleType.HEAVY_APC.visualLengthCells, VehicleType.HEAVY_APC.visualWidthCells, grid);
            if (onager) legal &= VehicleFootprint.isPoseFeasible(onagerBody.x, onagerBody.y, onagerBody.facingDegrees,
                    VehicleType.BREAKTHROUGH_TANK.visualLengthCells, VehicleType.BREAKTHROUGH_TANK.visualWidthCells, grid);
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
        if (onager) {
            verdicts.add(Verdict.of("onager-route-proved", onagerAccepted,
                    "the Onager's order is accepted over ground its weight can open"));
            verdicts.add(Verdict.of("onager-through", onagerThroughTick >= 0,
                    "the Onager is through the wall line at tick " + onagerThroughTick));
            verdicts.add(Verdict.of("breach-opened", openedOrdinary > 0,
                    openedOrdinary + " ordinary wall cells crushed"));
            verdicts.add(Verdict.of("apc-route-through-breach", apcSecondAccepted,
                    "the APC's second order, after the breach, is accepted (ordered at tick " + apcOrderTick + ")"));
            verdicts.add(Verdict.of("apc-through", apcThroughTick >= 0,
                    "the APC drives through the gap the Onager made, through at tick " + apcThroughTick));
        } else {
            verdicts.add(Verdict.of("apc-held", apcThroughTick < 0 && openedOrdinary == 0,
                    "alone, the APC never crosses: furthest north y=" + String.format(Locale.ROOT, "%.1f", apcMaxY)));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("openedOrdinaryCells", openedOrdinary);
        metrics.put("openedFortifiedCells", openedFortified);
        metrics.put("onagerThroughTick", onagerThroughTick);
        metrics.put("apcOrderTick", apcOrderTick);
        metrics.put("apcThroughTick", apcThroughTick);
        metrics.put("apcFinalX", apcBody.x);
        metrics.put("apcFinalY", apcBody.y);
        if (onager) {
            metrics.put("onagerFinalX", onagerBody.x);
            metrics.put("onagerFinalY", onagerBody.y);
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
