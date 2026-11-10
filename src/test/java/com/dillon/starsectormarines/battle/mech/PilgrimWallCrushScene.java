package com.dillon.starsectormarines.battle.mech;

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
import com.dillon.starsectormarines.battle.vehicle.WallCrush;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Pilgrim's wall crush ({@link MechWallCrush}): a Pilgrim given a real player move order across a wall line plans
 * through its ordinary part, walks into it, pushes it down by its weight and walks on, while the fortified part stands.
 * The control is the same order to a Warden, a medium frame, which does not cross.
 *
 * <p>The field is cut in two by a two-cell wall edge to edge: ordinary wall (100, the heavy ceiling) west of
 * {@link #FORTIFIED_X}, fortified (150) east of it. Nothing here clears a cell: any gap is what the walker pushed.
 */
public final class PilgrimWallCrushScene implements BehaviorScene {

    public static final String PILGRIM = "pilgrim";
    public static final String CONTROL = "control";

    static final int WIDTH = 48;
    static final int HEIGHT = 40;
    static final int WALL_Y0 = 18;
    static final int WALL_Y1 = 19;
    static final int FORTIFIED_X = 30;
    static final int ORDINARY_STRUCTURE = WallCrush.HEAVY_CEILING;
    static final int FORTIFIED_STRUCTURE = 150;
    static final int START_X = 16, START_Y = 8;
    static final int GOAL_X = 16, GOAL_Y = 30;
    private static final float THROUGH_Y = WALL_Y1 + 3f;
    private static final int TICKS = 1500;

    private final int frameEvery;

    public PilgrimWallCrushScene() { this(15); }

    public PilgrimWallCrushScene(int frameEvery) { this.frameEvery = frameEvery; }

    @Override public String id() { return "pilgrim-wall-crush"; }

    @Override public String label() {
        return "Pilgrim wall crush: the Pilgrim walks through an ordinary wall line by its weight; the control is a "
                + "Warden given the same order, which does not cross";
    }

    @Override public int frameDelayMillis() { return 90; }

    @Override public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(run(PILGRIM, MechVariant.PILGRIM, frames));
        reports.add(run(CONTROL, MechVariant.WARDEN, frames));
        return reports;
    }

    static void stampWall(NavigationGrid grid, CellTopology topology) {
        for (int y = WALL_Y0; y <= WALL_Y1; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, x < FORTIFIED_X ? ORDINARY_STRUCTURE : FORTIFIED_STRUCTURE);
                topology.setTag(x, y, CellTopology.Tag.WALL, true);
            }
        }
    }

    private SceneReport run(String loop, MechVariant variant, FrameSink frames) {
        NavigationGrid field = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology ground = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) field.setWalkableFloor(x, y);
        stampWall(field, ground);
        SceneBuilder builder = SceneBuilder.over(field, ground)
                .missionCompletion(false)
                // A sealed box in the far corner holds the one defender, so the sim keeps ticking.
                .wall(38, 30, 46, 30).wall(38, 38, 46, 38).wall(38, 30, 38, 38).wall(46, 30, 46, 38)
                .unit("holdout", Faction.DEFENDER, UnitType.MARINE, 42, 34);
        builder.squad("lance").faction(Faction.MARINE).size(1).at(START_X, START_Y)
                .mech(variant, MechRole.BALANCED).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        NavigationGrid grid = sim.getGrid();
        long walker = world.members("lance")[0];

        int throughTick = -1;
        float maxY = sim.world().y(walker);
        for (int tick = 0; tick < TICKS; tick++) {
            if (tick % 150 == 0) sim.getMechMoveOrderService().requestMove(walker, GOAL_X, GOAL_Y);
            sim.advance(BattleSimulation.TICK_DT);
            float y = sim.world().y(walker);
            maxY = Math.max(maxY, y);
            if (throughTick < 0 && y > THROUGH_Y) throughTick = tick;
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
        verdicts.add(Verdict.of("fortified-stands", openedFortified == 0,
                openedFortified + " fortified wall cells opened"));
        if (variant.crushesWalls()) {
            verdicts.add(Verdict.of("pilgrim-through", throughTick >= 0,
                    "the Pilgrim is through the wall line at tick " + throughTick));
            verdicts.add(Verdict.of("breach-opened", openedOrdinary > 0,
                    openedOrdinary + " ordinary wall cells pushed down"));
        } else {
            verdicts.add(Verdict.of("control-held", throughTick < 0 && openedOrdinary == 0,
                    "the Warden never crosses: furthest north y=" + String.format(Locale.ROOT, "%.1f", maxY)));
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("openedOrdinaryCells", openedOrdinary);
        metrics.put("openedFortifiedCells", openedFortified);
        metrics.put("throughTick", throughTick);
        metrics.put("maxY", maxY);
        sim.close();
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static String caption(String loop, int tick, NavigationGrid grid) {
        int open = 0;
        for (int y = WALL_Y0; y <= WALL_Y1; y++) for (int x = 0; x < WIDTH; x++) if (grid.isWalkable(x, y)) open++;
        return loop + "  t=" + tick + "  wall cells down: " + open;
    }
}
