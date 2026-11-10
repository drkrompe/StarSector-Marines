package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.nav.ClearanceRoutePlanner;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.WallCrush;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.List;

/**
 * The heavy walkers' wall crush ({@link MechWallCrush}): {@link PilgrimWallCrushScene}'s loops as verdict evidence,
 * and for the owner a look recording on real generated ground, {@code look/walker-breach-*}: a derelict MG-40 in the
 * colony's town given a move order whose cheapest route, at the breach price, goes through a house wall, so it walks
 * up, stands at the wall while it pushes it down and walks on into the room. It is fielded on the company's side
 * because a defender's machine takes its commander's orders, not a scripted move; the chassis draws the same.
 * Followed at play zoom and close.
 */
public final class PilgrimWallCrushSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    static final int FOOT_W = 72;
    static final int FOOT_H = 40;
    static final int LOOK_TICKS = 1200;
    private static final int ROUTE_BUDGET = 60_000;
    /** How far off the room the walker starts, cells. */
    private static final int LEG = 10;
    private static final int MAX_LEGS = 160;

    public PilgrimWallCrushSceneSnapshotSuite() {
        super(new PilgrimWallCrushScene(15), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected float[] closeView(String loopId) {
        return new float[] {16f, PilgrimWallCrushScene.WALL_Y1, 30f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(LookReview.Shot.of("walker-breach", FOOT_W, FOOT_H, PilgrimWallCrushSceneSnapshotSuite::stage,
                placement -> List.of(LookReview.View.followAtPlay(placement, "walker"),
                        LookReview.View.follow("close", placement, 24f, "walker")),
                LOOK_TICKS).withStills(20, 50, 80, 110, 140, 170));
    }

    private static BattleSimulation stage(LookReview.Placement placement) {
        float[][] leg = breachLeg(placement);
        float[] start = leg[0], goal = leg[1];
        SceneBuilder builder = placement.builder();
        builder.squad("walker").faction(Faction.MARINE).size(1)
                .at((int) start[0] - placement.site().x0(), (int) start[1] - placement.site().y0())
                .mech(MechVariant.SECURITY_WALKER, MechRole.ARMORED_SUPPORT).done();
        placement.holdouts(builder);
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        long walker = placement.subject("walker", world.members("walker")[0]);
        int gx = (int) goal[0], gy = (int) goal[1];
        System.out.printf("    [look/walker-breach] MG-40 from %.1f,%.1f to %d,%d%n", start[0], start[1], gx, gy);
        placement.beforeTick(s -> {
            if (s.getSimTickIndex() % 150 == 0) s.getMechMoveOrderService().requestMove(walker, gx, gy);
        });
        return sim;
    }

    /**
     * Where to stand the walker and where to send it so its route goes through a wall: a room inside one of the
     * town's buildings, with room to stand, and an open cell outside {@link #LEG} cells off to one side, whose
     * cheapest route, planned over the walkers' breach view at the breach price, crosses the most breach cells, so
     * the walker pushes its own way in rather than walking round to the door. Read from the map as generated, before
     * any shot is fired.
     */
    static float[][] breachLeg(LookReview.Placement placement) {
        NavigationGrid grid = placement.map().grid;
        CellTopology topology = placement.map().topology;
        NavigationGrid view = WallCrush.breachView(grid, topology, null);
        ClearanceRoutePlanner planner = new ClearanceRoutePlanner();
        float radius = MechVariant.SECURITY_WALKER.radius;
        float[][] best = {placement.openNear(8f, FOOT_H / 2f), placement.openNear(FOOT_W - 8f, FOOT_H / 2f)};
        int bestCells = 0, tried = 0;
        int[][] sides = {{LEG, 0}, {-LEG, 0}, {0, LEG}, {0, -LEG}};
        for (int y = 40; y < grid.getHeight() - 40 && tried < MAX_LEGS; y += 2) {
            for (int x = 40; x < grid.getWidth() - 40 && tried < MAX_LEGS; x += 2) {
                if (topology.getBuildingId(x, y) == 0 || !roomy(grid, x, y)) continue;
                for (int[] side : sides) {
                    int sx = x + side[0], sy = y + side[1];
                    if (!outdoors(grid, topology, sx, sy)) continue;
                    tried++;
                    var result = planner.findRoute(view, sx + .5f, sy + .5f, x + .5f, y + .5f, radius, 0f,
                            ROUTE_BUDGET);
                    if (result.status() != ClearanceRoutePlanner.Status.FOUND) continue;
                    int cells = breachCells(view, result.waypoints());
                    if (cells > bestCells) {
                        bestCells = cells;
                        best = new float[][] {{sx + .5f, sy + .5f}, {x + .5f, y + .5f}};
                    }
                }
            }
        }
        System.out.printf("    [look/walker-breach] %d legs tried, best crosses %d breach samples%n", tried,
                bestCells);
        return best;
    }

    /** Whether cell ({@code x}, {@code y}) and the eight round it are walkable: room for the walker to stand. */
    private static boolean roomy(NavigationGrid grid, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) if (!grid.isWalkable(x + dx, y + dy)) return false;
        }
        return true;
    }

    /** Whether a walker could stand at cell ({@code x}, {@code y}) out of doors with room round it. */
    private static boolean outdoors(NavigationGrid grid, CellTopology topology, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!grid.isWalkable(x + dx, y + dy) || topology.getBuildingId(x + dx, y + dy) != 0) return false;
            }
        }
        return true;
    }

    private static int breachCells(NavigationGrid view, List<ClearanceRoutePlanner.Point> points) {
        int count = 0;
        for (int i = 1; i < points.size(); i++) {
            var a = points.get(i - 1);
            var b = points.get(i);
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(b.x() - a.x(), b.y() - a.y()) * 4f));
            for (int s = 0; s < steps; s++) {
                float t = (float) s / steps;
                int cx = (int) Math.floor(a.x() + (b.x() - a.x()) * t);
                int cy = (int) Math.floor(a.y() + (b.y() - a.y()) * t);
                if (view.hasTag(cx, cy, NavigationGrid.CellTag.BREACH)) count++;
            }
        }
        return count;
    }
}
