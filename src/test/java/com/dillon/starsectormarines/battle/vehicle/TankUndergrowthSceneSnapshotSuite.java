package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Heavy tracks, drawn. The verdict loops of {@link TankUndergrowthScene} at 1920x1080 with the belt's crushable cells
 * marked; and the look shots the owner is shown: a breakthrough tank ordered straight across a wood on real verdant
 * ground, one of young trees (with a few mature ones it steers round) and one of mature trees the creep has killed,
 * and across a field of lone large boulders.
 */
public final class TankUndergrowthSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int SHOT_W = 64, SHOT_H = 36;
    /** The wood's columns, window-local. */
    private static final int WOOD_X0 = 20, WOOD_X1 = 44;
    private static final long CREEP_SOURCE = 0x7EE5_0001L;
    /** Recorded frames (one every 6 ticks) kept as stills: at the wood's edge, inside it, and through. */
    private static final int[] STILLS = {24, 40, 56, 90};

    public TankUndergrowthSceneSnapshotSuite() {
        super(new TankUndergrowthScene(15), 1920, 1080);
    }

    @Override protected float[] closeView(String loopId) {
        return new float[] {TankUndergrowthScene.START_X, (TankUndergrowthScene.BELT_Y0 + TankUndergrowthScene.BELT_Y1) / 2f, 40f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override protected ReviewAnnotations annotations(BattleSimulation simulation) {
        NavigationGrid grid = simulation.getGrid();
        List<ReviewAnnotation.Cell> crushable = new ArrayList<>();
        for (int y = TankUndergrowthScene.BELT_Y0; y <= TankUndergrowthScene.BELT_Y1; y++) {
            for (int x = 0; x < TankUndergrowthScene.WIDTH; x++) {
                if (HeavyTracks.crushes(grid, x, y)) crushable.add(new ReviewAnnotation.Cell(x, y));
            }
        }
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder()
                .cells(crushable, "CRUSHABLE", ReviewStyle.NOTE)
                .label(TankUndergrowthScene.GOAL_X + .5f, TankUndergrowthScene.GOAL_Y + 2.5f, "GOAL", ReviewStyle.LANDING);
        for (long id : simulation.getConvoyVehicleIds()) {
            GroundBody body = simulation.convoy().body(id);
            marks.label(body.x, body.y - 3.2f, simulation.convoy().vehicleType(id) == VehicleType.HEAVY_APC ? "APC" : "TANK",
                    ReviewStyle.NOTE);
        }
        return marks.build();
    }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        return new int[] {0, 12, 18, 24, 40};
    }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(shot("young-wood", false), shot("dead-wood", true), boulders());
    }

    /** A breakthrough tank driven straight across a field strewn with lone large boulders, as nature zones lay them. */
    private static LookReview.Shot boulders() {
        return LookReview.Shot.of("boulders", SHOT_W, SHOT_H, placement -> {
            MapResult map = placement.map();
            TileRegistry tiles = TileRegistry.installed();
            String[] rocks = {"nature.rock-large-1", "nature.rock-large-2", "nature.rock-large-3"};
            Random random = new Random(23L);
            for (int i = 0; i < 40; i++) {
                int x = placement.site().x0() + WOOD_X0 + random.nextInt(WOOD_X1 - WOOD_X0);
                int y = placement.site().y0() + SHOT_H / 2 - 6 + random.nextInt(12);
                if (!map.grid.isWalkable(x, y) || map.topology.isFixture(x, y)
                        || map.topology.getGroundKind(x, y) == CellTopology.GroundKind.WATER) continue;
                TileDef rock = tiles.tile(rocks[random.nextInt(rocks.length)]);
                map.topology.setNatureOverlayIndex(x, y, rock.index);
                map.grid.setWalkable(x, y, false);
                map.grid.setSeeThrough(x, y, true);
                map.topology.setFixture(x, y, true);
                map.grid.setTag(x, y, NavigationGrid.CellTag.CRUSHABLE, rock.crushable);
            }
            return driveAcross(placement, placement.holdouts(placement.builder()).build().sim());
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "tank"),
                LookReview.View.follow("close", placement, 24f, "tank")), 900).withStills(STILLS);
    }

    /** Spawns the tank at the window's west edge and orders it to the east edge. */
    private static BattleSimulation driveAcross(LookReview.Placement placement, BattleSimulation sim) {
        float[] start = placement.openNear(7f, SHOT_H / 2f);
        float[] goal = placement.openNear(SHOT_W - 6f, SHOT_H / 2f);
        long tank = sim.convoy().spawn(VehicleType.BREAKTHROUGH_TANK, Faction.MARINE,
                VehicleMission.deployed(start[0], start[1]));
        sim.convoy().body(tank).teleport(start[0], start[1], -90f);
        placement.subject("tank", tank);
        sim.getVehicleMoveOrderService().requestMove(tank, (int) goal[0], (int) goal[1]);
        return sim;
    }

    private static LookReview.Shot shot(String name, boolean dead) {
        return LookReview.Shot.of(name, SHOT_W, SHOT_H, placement -> {
            plantWood(placement, dead);
            SceneWorld world = placement.holdouts(placement.builder()).build();
            BattleSimulation sim = world.sim();
            if (dead) {
                float[] heart = placement.openNear((WOOD_X0 + WOOD_X1) / 2f, SHOT_H / 2f);
                // Reaching past the wood, so no living tree is left to steer round and the tank fells dead wood.
                sim.creepField().putSource(CREEP_SOURCE, (int) heart[0], (int) heart[1], new CreepGrowth(.45f, 40f));
                sim.creepField().mature();
                sim.creepVegetation().look(sim, 0);
            }
            return driveAcross(placement, sim);
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "tank"),
                LookReview.View.follow("close", placement, 24f, "tank")), 900).withStills(STILLS);
    }

    /**
     * A wood across the window, from edge to edge of its height between {@link #WOOD_X0} and {@link #WOOD_X1}, planted
     * through the production forest brush, packed: every second cell is tried in a seeded order and a tree kept
     * wherever the ground and the trees already there take it. Young trees with every fifth one mature, or every one
     * mature for the creep to kill.
     */
    private static void plantWood(LookReview.Placement placement, boolean dead) {
        MapResult map = placement.map();
        Random random = new Random(dead ? 41L : 17L);
        TreeCanopy[] young = {TreeCanopy.PINE, TreeCanopy.OAK_SMALL};
        TreeCanopy[] mature = {TreeCanopy.OAK, TreeCanopy.BIRCH, TreeCanopy.PINE_LARGE, TreeCanopy.CEDAR};
        List<int[]> spots = new ArrayList<>();
        for (int y = -2; y < SHOT_H + 2; y += 2) for (int x = WOOD_X0; x < WOOD_X1; x += 2) spots.add(new int[] {x, y});
        Collections.shuffle(spots, random);
        int n = 0;
        for (int[] spot : spots) {
            TreeCanopy canopy = dead || n % 5 == 4 ? mature[random.nextInt(mature.length)]
                    : young[random.nextInt(young.length)];
            ForestField.Tree tree = new ForestField.Tree(placement.site().x0() + spot[0],
                    placement.site().y0() + spot[1], canopy, random.nextInt(4));
            try {
                ForestBrush.stampTrees(List.of(tree), map.grid, map.topology, map.doodads, null, false);
                n++;
            } catch (IllegalArgumentException occupied) {
                // A road, a building or a tree already there: the next spot is tried.
            }
        }
    }

}
