package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.air.GroundMarineArrival;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleFootprint;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code gradlew playBattle -Pbattle=proving-ground}: every machine in the game parked in two ranks on real generated
 * ground, with no enemy and no mission, for feeling how they drive. Each is the player's, so it answers move orders and
 * direct control; the battle never ends, and F9 lays it out again.
 *
 * <p>The ground is {@link LookGround}'s production colony map. The ranks stand on the clear window with the most
 * buildings and wild ground around it, and on green worlds {@link #GROVES} of the generator's own trees are planted
 * ahead of them, so there is something to drive past, round and through within a few seconds of any machine. Options:
 * {@code surface=ROCK|ARID|VERDANT|FROZEN} and {@code seed=N}.
 */
public final class ProvingGround {

    private static final int HULLS_PER_RANK = 8;
    /** Cells from one machine's centre to the next along a rank; the Wayline hauler needs most of it. */
    private static final int FILE_SPACING = 8;
    private static final int RANK_SPACING = 12;
    private static final int WIDTH = FILE_SPACING * HULLS_PER_RANK;
    private static final int HEIGHT = RANK_SPACING * 2;
    /** How far around the ranks a building or wild ground counts as nearby. */
    private static final int SURROUND = 20;
    /** Cells of either kind that count, so that a town with nothing wild does not outscore a little of both. */
    private static final int ENOUGH_OF_EITHER = 400;
    private static final int SEARCH_STEP = 2;
    private static final float FACING_NORTH = 0f;
    /** Woods and lone trees north of the ranks, as x, y and radius in cells from the window's corner. */
    private static final int[][] GROVES = {{8, 33, 6}, {30, 41, 8}, {54, 33, 6}, {20, 28, 1}, {42, 29, 1}};

    private ProvingGround() {
    }

    public static BattleSimulation build(SurfacePalette surface, long seed) {
        LookGround ground = LookGround.of(surface, seed);
        MapResult map = ground.generate();
        LookGround.Site site = site(map.grid, map.topology);
        SceneBuilder builder = SceneBuilder.onTerrain(ground, map, site).missionCompletion(false).seed(seed);
        if (surface == SurfacePalette.VERDANT) builder.doodads(plantGroves(map, site, seed));
        MechVariant[] mechs = MechVariant.values();
        for (int i = 0; i < mechs.length; i++) {
            int holdX = site.x(fileX(i));
            int holdY = site.y(rankY(0));
            // A one-mech lance forms up a cell and a half west of its spawn cell, so ask two east. Left with no
            // errand a lance walks off to its doctrine's ground, so each holds its slot until the player orders it.
            builder.squad(mechs[i].id).faction(Faction.MARINE).size(1).at(fileX(i) + 2, rankY(0))
                    .mech(MechDeploymentSpec.standard(mechs[i]))
                    .assigned(id -> ObjectiveAssignment.defendArea(id, holdX, holdY)).done();
        }
        BattleSimulation sim = builder.build().sim();
        ConvoyService convoy = sim.convoy();
        VehicleType[] hulls = VehicleType.values();
        for (int i = 0; i < hulls.length; i++) {
            int slot = mechs.length + i;
            float x = site.x(fileX(slot % HULLS_PER_RANK) + 0.5f);
            float y = site.y(rankY(slot / HULLS_PER_RANK) + 0.5f);
            VehicleType type = hulls[i];
            long id = convoy.spawn(type, Faction.MARINE, VehicleMission.deployed(x, y));
            convoy.body(id).teleport(x, y, FACING_NORTH);
            if (!VehicleFootprint.isPoseFeasible(x, y, FACING_NORTH, type.visualLengthCells, type.visualWidthCells,
                    sim.getGrid())) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "%s has no room at (%.1f, %.1f); try another seed", type, x, y));
            }
        }
        // Only frames the opening camera: nothing deploys this arrival, since no launch overlay is applied.
        sim.setGroundMarineArrival(new GroundMarineArrival(site.centreX(), site.centreY(),
                SquadCommandClaim.mission("proving-ground", "Parked for a test drive")));
        sim.playerCommandsCompany();
        sim.addObjective(new TestDrive());
        return sim;
    }

    private static int fileX(int file) {
        return FILE_SPACING / 2 + file * FILE_SPACING;
    }

    private static int rankY(int rank) {
        return RANK_SPACING / 2 + rank * RANK_SPACING;
    }

    /** Stamps the trees that fit on open ground into the map, for the simulation to be built over; returns their doodads. */
    private static List<Doodad> plantGroves(MapResult map, LookGround.Site site, long seed) {
        int w = map.grid.getWidth();
        int h = map.grid.getHeight();
        boolean[] paint = new boolean[w * h];
        for (int[] grove : GROVES) {
            int cx = site.x(grove[0]);
            int cy = site.y(grove[1]);
            int r = grove[2];
            for (int y = Math.max(0, cy - r); y <= Math.min(h - 1, cy + r); y++) {
                for (int x = Math.max(0, cx - r); x <= Math.min(w - 1, cx + r); x++) {
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) paint[y * w + x] = true;
                }
            }
        }
        List<ForestField.Tree> fitting = new ArrayList<>();
        for (ForestField.Tree tree : ForestField.build(w, h, seed, ForestPlan.Woodland.MIXED, paint).trees()) {
            if (fits(tree, map, site)) fitting.add(tree);
        }
        List<Doodad> doodads = new ArrayList<>();
        ForestBrush.stampTrees(fitting, map.grid, map.topology, doodads, null, false);
        return doodads;
    }

    /** Whether every cell the crown closes is the open natural ground {@link ForestBrush} accepts, clear of the ranks. */
    private static boolean fits(ForestField.Tree tree, MapResult map, LookGround.Site site) {
        CellTopology topology = map.topology;
        for (int y = tree.y(); y < tree.y() + tree.height(); y++) {
            for (int x = tree.x(); x < tree.x() + tree.width(); x++) {
                if (!tree.blocks(x, y)) continue;
                boolean natural = switch (topology.getGroundKind(x, y)) {
                    case GRASS, DIRT, STONE, SAND, SNOW -> true;
                    default -> false;
                };
                boolean inRanks = x >= site.x0() && x < site.x0() + site.width()
                        && y >= site.y0() && y < site.y0() + site.height();
                if (!natural || inRanks || LookGround.blocked(map.grid, topology, x, y) || topology.isWall(x, y)
                        || topology.isFixture(x, y) || topology.getSurface(x, y) != 0
                        || (topology.roadSurface() != null && topology.roadSurface().underlay(x, y) != null)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The clear window with the most buildings and wild ground around it, each capped at {@link #ENOUGH_OF_EITHER}. */
    static LookGround.Site site(NavigationGrid grid, CellTopology topology) {
        LookGround.Site best = null;
        int bestScore = -1;
        for (int y0 = SURROUND; y0 + HEIGHT + SURROUND <= grid.getHeight(); y0 += SEARCH_STEP) {
            for (int x0 = SURROUND; x0 + WIDTH + SURROUND <= grid.getWidth(); x0 += SEARCH_STEP) {
                if (!clear(grid, topology, x0, y0)) continue;
                int score = surroundings(topology, x0, y0);
                if (score > bestScore) {
                    bestScore = score;
                    best = new LookGround.Site(x0, y0, WIDTH, HEIGHT, 0);
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException("No clear " + WIDTH + "x" + HEIGHT + " window on this map; try another seed");
        }
        return best;
    }

    private static boolean clear(NavigationGrid grid, CellTopology topology, int x0, int y0) {
        for (int y = y0; y < y0 + HEIGHT; y++) {
            for (int x = x0; x < x0 + WIDTH; x++) {
                if (LookGround.blocked(grid, topology, x, y)) return false;
            }
        }
        return true;
    }

    private static int surroundings(CellTopology topology, int x0, int y0) {
        int buildings = 0;
        int nature = 0;
        for (int y = y0 - SURROUND; y < y0 + HEIGHT + SURROUND; y++) {
            for (int x = x0 - SURROUND; x < x0 + WIDTH + SURROUND; x++) {
                if (topology.getBuildingId(x, y) != 0) buildings++;
                if (topology.getNatureOverlayIndex(x, y) >= 0) nature++;
            }
        }
        return Math.min(buildings, ENOUGH_OF_EITHER) + Math.min(nature, ENOUGH_OF_EITHER);
    }

    /** Stands in for the elimination backstop, which would read as complete with no enemy on the field. */
    private static final class TestDrive implements Objective {
        @Override public Faction owningFaction() { return Faction.MARINE; }
        @Override public void tick(BattleView sim) { }
        @Override public boolean isComplete() { return false; }
        @Override public boolean isFailed() { return false; }
        @Override public String displayName() { return "Test drive"; }
    }
}
