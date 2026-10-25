package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Heavy tracks' acceptance (`convoy-nouns.md`, heavy tracks): a {@link VehicleType#BREAKTHROUGH_TANK} ordered across
 * a belt of trees that runs from edge to edge of the field, so there is no way round.
 *
 * <p>{@code young-wood}: two staggered rows of young pines; the tank's order is proved over the ground its tracks
 * open and it drives through, felling what it touches. {@code dead-wood}: a belt of mature oaks the creep has killed
 * (their deaths are declared to the map editor as {@code CreepVegetation} declares them); the tank pushes through it
 * the same way. {@code mature-wood}: the same oaks alive; the hull never crosses and nothing falls. {@code apc}: a
 * troop APC, a Light-lift hull, ordered across the young pines, held the same way. Every loop gives real player move orders through
 * {@link VehicleMoveOrderService}, so route proof, rolling planner and sweep are production.
 */
public final class TankUndergrowthScene implements BehaviorScene {

    public static final String YOUNG = "young-wood";
    public static final String DEAD = "dead-wood";
    public static final String MATURE = "mature-wood";
    public static final String APC = "apc";

    static final int WIDTH = 64;
    static final int HEIGHT = 48;
    /** The belt's first and last rows. */
    static final int BELT_Y0 = 20;
    static final int BELT_Y1 = 25;
    static final float START_X = 30.5f, START_Y = 9.5f;
    static final int GOAL_X = 30, GOAL_Y = 38;
    /** A hull counts as through once its centre is this far north. */
    private static final float THROUGH_Y = BELT_Y1 + 5f;
    private static final int TICKS = 1500;

    private final int frameEvery;

    public TankUndergrowthScene() { this(15); }

    public TankUndergrowthScene(int frameEvery) { this.frameEvery = frameEvery; }

    @Override public String id() { return "tank-undergrowth"; }

    @Override public String label() {
        return "Heavy tracks: a tank drives through young and creep-killed wood, felling it, and is refused by a living "
                + "mature wood; a troop APC is refused by the young wood";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(run(YOUNG, frames));
        reports.add(run(DEAD, frames));
        reports.add(run(MATURE, frames));
        reports.add(run(APC, frames));
        return reports;
    }

    /** The loop's belt of trees, edge to edge: two staggered rows of pines, or one overlapping row of oaks. */
    static List<ForestField.Tree> belt(String loop) {
        List<ForestField.Tree> trees = new ArrayList<>();
        if (loop.equals(DEAD) || loop.equals(MATURE)) {
            for (int x = 0; x + 5 <= WIDTH; x += 4) trees.add(new ForestField.Tree(x, BELT_Y0, TreeCanopy.OAK, 0));
            trees.add(new ForestField.Tree(WIDTH - 5, BELT_Y0, TreeCanopy.OAK, 0));
        } else {
            for (int x = 0; x + 3 <= WIDTH; x += 3) trees.add(new ForestField.Tree(x, BELT_Y0, TreeCanopy.PINE, 0));
            for (int x = 1; x + 3 <= WIDTH; x += 3) trees.add(new ForestField.Tree(x, BELT_Y0 + 2, TreeCanopy.PINE, 0));
        }
        return trees;
    }

    private SceneReport run(String loop, FrameSink frames) {
        NavigationGrid field = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology ground = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            field.setWalkableFloor(x, y);
            ground.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
        }
        List<Doodad> trees = new ArrayList<>();
        ForestBrush.stampTrees(belt(loop), field, ground, trees, null, false);
        boolean beltClosed = true;
        for (int x = 0; x < WIDTH; x++) {
            boolean closed = false;
            for (int y = BELT_Y0; y <= BELT_Y1; y++) closed |= !field.isWalkable(x, y);
            beltClosed &= closed;
        }
        SceneWorld world = SceneBuilder.over(field, ground)
                .missionCompletion(false)
                .doodads(trees)
                // A sealed box in the far corner holds the one defender, so the sim keeps ticking.
                .wall(54, 38, 62, 38).wall(54, 46, 62, 46).wall(54, 38, 54, 46).wall(62, 38, 62, 46)
                .unit("holdout", Faction.DEFENDER, UnitType.MARINE, 58, 42)
                .build();
        BattleSimulation sim = world.sim();
        NavigationGrid grid = sim.getGrid();
        if (loop.equals(DEAD)) {
            for (ForestField.Tree tree : belt(loop)) sim.mapEditor().witherTree(tree.x(), tree.y());
        }

        VehicleType type = loop.equals(APC) ? VehicleType.HEAVY_APC : VehicleType.BREAKTHROUGH_TANK;
        VehicleMoveOrderService orders = sim.getVehicleMoveOrderService();
        long hull = sim.convoy().spawn(type, Faction.MARINE, VehicleMission.deployed(START_X, START_Y));
        sim.convoy().body(hull).teleport(START_X, START_Y, 0f);
        orders.requestMove(hull, GOAL_X, GOAL_Y);
        sim.advance(BattleSimulation.TICK_DT);
        boolean accepted = orders.activeOrder(hull) != null;
        VehicleMoveOrderService.RefusedOrder refusal = orders.refusal(hull);
        boolean refused = !accepted && refusal != null && refusal.reason() == VehicleMoveOrderService.Refusal.NO_ROUTE;

        GroundBody body = sim.convoy().body(hull);
        int throughTick = -1;
        boolean legal = true;
        float maxY = body.y;
        for (int tick = 0; tick < TICKS; tick++) {
            if (!accepted && tick > 0 && tick % 300 == 0) orders.requestMove(hull, GOAL_X, GOAL_Y);
            sim.advance(BattleSimulation.TICK_DT);
            maxY = Math.max(maxY, body.y);
            if (throughTick < 0 && body.y > THROUGH_Y) throughTick = tick;
            legal &= VehicleFootprint.isPoseFeasible(body.x, body.y, body.facingDegrees,
                    type.visualLengthCells, type.visualWidthCells, grid);
            if (tick % frameEvery == 0) frames.frame(loop, sim, tick, caption(loop, tick, sim));
        }

        int felled = felled(sim);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("belt-closed", beltClosed, "the belt closes every column of the field"));
        verdicts.add(Verdict.of("whole-hull-clear", legal,
                "every applied pose clears the live grid: the hull only ever stood where it had crushed its way"));
        if (loop.equals(YOUNG) || loop.equals(DEAD)) {
            verdicts.add(Verdict.of("route-proved", accepted, "the tank's order is accepted over ground its tracks open"));
            verdicts.add(Verdict.of("through", throughTick >= 0, "the tank is through the belt at tick " + throughTick));
            verdicts.add(Verdict.of("felled", felled > 0, felled + " trees felled"));
        } else {
            // An order the hull cannot carry out is refused, or resolved to the nearest ground it can reach, short of
            // the belt: either way it never crosses.
            verdicts.add(Verdict.of("held", throughTick < 0 && felled == 0, "never crosses, nothing falls: furthest "
                    + "north y=" + String.format(Locale.ROOT, "%.1f", maxY)));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("orderAccepted", accepted ? 1 : 0);
        metrics.put("orderRefusedNoRoute", refused ? 1 : 0);
        metrics.put("felledTrees", felled);
        metrics.put("throughTick", throughTick);
        metrics.put("finalX", body.x);
        metrics.put("finalY", body.y);
        sim.close();
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    static int felled(BattleSimulation sim) {
        int felled = 0;
        for (Doodad d : sim.getDoodads()) {
            if (d.definitionId() != null && d.definitionId().startsWith("forest.")
                    && d.definitionId().endsWith("-destroyed")) felled++;
        }
        return felled;
    }

    static int crushable(NavigationGrid grid) {
        int cells = 0;
        for (int y = BELT_Y0; y <= BELT_Y1; y++) {
            for (int x = 0; x < WIDTH; x++) if (grid.hasTag(x, y, CellTag.CRUSHABLE)) cells++;
        }
        return cells;
    }

    private static String caption(String loop, int tick, BattleSimulation sim) {
        return loop + " / tick " + tick + " / trees felled " + felled(sim) + " / crushable cells "
                + crushable(sim.getGrid());
    }
}
