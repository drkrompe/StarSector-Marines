package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.GenRecipe;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.fill.NatureZoneFiller;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.FinalizeStage;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Hive creep in green country (`colony-breach-nouns.md`, Creep): a maw opens on
 * open grass dressed with shrubs and tufts, with a grove in its path, fields of
 * crop strips beyond the grove, and a second grove out of its reach. The creep
 * takes the grass, chokes the crops and overgrows the near grove; the trees it
 * has held long enough die ({@link CreepVegetation}).
 *
 * <p>Verdicts are the rule's mechanics: a tree dies only once living creep holds
 * its crown to its threshold, a crown held past the highest threshold has died,
 * a dead crown lets sight
 * through and gives no cover outside its trunk while still stopping movement,
 * and a tree the creep never touched stands as it was. How many trees died and
 * when are metrics.
 */
public final class CreepVegetationScene implements BehaviorScene {

    static final int WIDTH = 120, HEIGHT = 68;
    static final int MAW_X = 10, MAW_Y = 34;
    static final int GROVE_X = 40, GROVE_Y = 34, GROVE_R = 11;
    static final int FAR_GROVE_X = 106, FAR_GROVE_Y = 56, FAR_GROVE_R = 7;
    /** The fields: cells {@code [FIELD_LEFT, WIDTH - 4)} by {@code [4, FIELD_TOP)}. */
    static final int FIELD_LEFT = 58, FIELD_TOP = 30;
    private static final long SEED = 20261022L;
    private static final int TICKS = 12600;
    private static final int FRAME_EVERY = 300;

    @Override public String id() { return "creep-vegetation"; }

    @Override public String label() {
        return "Creep in green country takes the grass, chokes the crops and kills the trees it holds";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(playOne("overgrown", frames));
    }

    /** The near grove and the fields beyond it, framed at play zoom: {@code {x, y, cellsAcross}}. */
    static float[] groveView() { return new float[]{58f, 27f, 60f}; }

    /** The ground every case stands up: grass and plants, the two groves and the fields. */
    public static GenContext country() {
        GenContext ctx = new GenContext(new NavigationGrid(WIDTH, HEIGHT), new CellTopology(WIDTH, HEIGHT),
                new Random(SEED), WIDTH, HEIGHT, SEED);
        ctx.put(BspKeys.MARKET_PROFILE, new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT,
                SettlementLink.ROAD));
        new GenRecipe("Creep vegetation", List.of(
                CreepVegetationScene::openCountry,
                CreepVegetationScene::fields,
                c -> grove(c, GROVE_X, GROVE_Y, GROVE_R),
                c -> grove(c, FAR_GROVE_X, FAR_GROVE_Y, FAR_GROVE_R),
                new FinalizeStage())).run(ctx);
        return ctx;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        GenContext ctx = country();
        SceneWorld world = SceneBuilder.over(ctx.grid, ctx.topology).seed(SEED).missionCompletion(false).build();
        try (BattleSimulation sim = world.sim()) {
            for (Doodad doodad : ctx.doodads) sim.addDoodad(doodad);
            SwarmBore maw = sim.swarmBores().open(sim, 0, true, MAW_X, MAW_Y);
            if (maw == null) throw new IllegalStateException("open grass refused the maw");
            Watch watch = new Watch(sim);
            SceneRun.of(world).observe(watch)
                    .frames(frames, loop, FRAME_EVERY, tick -> loop + " / " + watch.dead + " of " + watch.trees.size()
                            + " trees dead / " + tick / 30 + " s")
                    .run(TICKS);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("tree-dies-only-when-held", watch.early == 0,
                    watch.early + " of " + watch.dead + " trees died with their crown's living cover under "
                            + CreepVegetation.DEATH_LO + "/255"));
            verdicts.add(Verdict.of("held-tree-dies", watch.overdue == 0,
                    watch.overdue + " trees still standing alive with their crown held past "
                            + CreepVegetation.DEATH_HI + "/255 for two looks"));
            verdicts.add(Verdict.of("dead-crown-opens-at-the-trunk", watch.badCrowns == 0,
                    watch.badCrowns + " dead crown cells that still block sight or give cover, let a body"
                            + " through, or a trunk that stopped blocking"));
            verdicts.add(Verdict.of("untouched-tree-stands", watch.untouchedChanged == 0,
                    watch.untouchedChanged + " trees the creep never reached died or stopped blocking"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("trees", watch.trees.size());
            metrics.put("treesDead", watch.dead);
            metrics.put("firstDeathTick", watch.firstDeath);
            metrics.put("lastDeathTick", watch.lastDeath);
            metrics.put("creepedCells", sim.creepField().snapshot().coveredCells());
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static void openCountry(GenContext c) {
        boolean[] open = new boolean[c.width * c.height];
        for (int y = 0; y < c.height; y++) {
            for (int x = 0; x < c.width; x++) {
                c.grid.setWalkableFloor(x, y);
                c.topology.setGroundKind(x, y, CellTopology.GroundKind.GRASS);
                open[y * c.width + x] = !(x >= FIELD_LEFT && x < c.width - 4 && y >= 4 && y < FIELD_TOP);
            }
        }
        NatureZoneFiller.dressOpenGround(c, open);
    }

    /**
     * Crop rows of grass beside turned earth, two cells a pitch, inside a grass headland that carries a hedge of
     * shrubs, as the country's hedged plots are dressed ({@code RuralGround}).
     */
    private static void fields(GenContext c) {
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        int grass = mapping == null ? 0 : c.topology.surfaceIndex(mapping.groundBlockId(CellTopology.GroundKind.GRASS));
        int dirt = mapping == null ? 0 : c.topology.surfaceIndex(mapping.groundBlockId(CellTopology.GroundKind.DIRT));
        TileRegistry tiles = TileRegistry.installed();
        int shrub = tiles != null && tiles.has("nature.shrub-1") ? tiles.indexOf("nature.shrub-1") : -1;
        int x0 = FIELD_LEFT, x1 = c.width - 5, y0 = 4, y1 = FIELD_TOP - 1;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                boolean headland = x == x0 || x == x1 || y == y0 || y == y1;
                boolean crop = headland || ((y - y0) / 2) % 2 == 0;
                c.topology.setGroundKind(x, y, crop ? CellTopology.GroundKind.GRASS : CellTopology.GroundKind.DIRT);
                c.topology.setSurface(x, y, crop ? grass : dirt);
                c.topology.setNatureOverlayIndex(x, y, headland ? shrub : -1);
            }
        }
    }

    private static void grove(GenContext c, int cx, int cy, int r) {
        boolean[] paint = new boolean[c.width * c.height];
        for (int y = cy - r; y <= cy + r; y++) {
            for (int x = cx - r; x <= cx + r; x++) {
                if (x < 0 || y < 0 || x >= c.width || y >= c.height) continue;
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) paint[y * c.width + x] = true;
            }
        }
        ForestField field = ForestField.build(c.width, c.height, c.seed ^ cx, ForestPlan.Woodland.MIXED, paint);
        ForestBrush.stampTrees(field.trees(), c.grid, c.topology, c.doodads, null, false);
    }

    /** One tree as the watch sees it. */
    private record Tree(Doodad placed, int[] crown, boolean[] trunk) { }

    private static final class Watch implements TickObserver {
        final List<Tree> trees = new ArrayList<>();
        /** For each crown cell, the trees holding it. */
        final Map<Integer, List<Integer>> owners = new HashMap<>();
        final boolean[] seenDead, touched, overdueCounted;
        final int[] heldPastHigh;
        int dead, early, overdue, badCrowns, untouchedChanged, firstDeath = -1, lastDeath = -1;

        Watch(BattleSimulation sim) {
            NavigationGrid grid = sim.getGrid();
            for (Doodad d : sim.getDoodads()) {
                TreeCanopy canopy = CreepVegetation.canopy(d.definitionId());
                if (canopy == null) continue;
                int[] crown = CreepVegetation.crownCells(d, canopy, grid.getWidth(), grid.getHeight());
                for (int cell : crown) owners.computeIfAbsent(cell, unused -> new ArrayList<>()).add(trees.size());
                trees.add(new Tree(d, crown, CreepVegetation.trunkOf(d, crown, grid.getWidth())));
            }
            seenDead = new boolean[trees.size()];
            touched = new boolean[trees.size()];
            overdueCounted = new boolean[trees.size()];
            heldPastHigh = new int[trees.size()];
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            CreepField field = sim.creepFieldIfPresent();
            CreepVegetation vegetation = sim.creepVegetationIfPresent();
            if (field == null || vegetation == null || tick % CreepVegetation.CHECK_TICKS != 0) return;
            NavigationGrid grid = sim.getGrid();
            int w = grid.getWidth();
            for (int t = 0; t < trees.size(); t++) {
                Tree tree = trees.get(t);
                int living = CreepVegetation.livingCoverage(
                        new CreepVegetation.Tree(0, 0, tree.crown, tree.trunk, 0), field);
                for (int cell : tree.crown) if (field.coverageAt(cell) > 0) touched[t] = true;
                int died = vegetation.diedAt(tree.placed.cellX, tree.placed.cellY);
                if (died >= 0 && !seenDead[t]) {
                    seenDead[t] = true;
                    dead++;
                    if (firstDeath < 0) firstDeath = died;
                    lastDeath = died;
                    if (living < CreepVegetation.DEATH_LO) early++;
                    for (int i = 0; i < tree.crown.length; i++) {
                        int cell = tree.crown[i], x = cell % w, y = cell / w;
                        boolean opaque = grid.blocksStructuralLineOfSightAt(cell);
                        boolean walkable = grid.isWalkable(x, y);
                        boolean noCover = grid.isEdgeCoverSuppressed(x, y);
                        boolean open = opensNow(vegetation, cell);
                        boolean right = open ? !opaque && noCover && !walkable : opaque && !noCover && !walkable;
                        if (!right) badCrowns++;
                    }
                }
                if (died < 0) {
                    heldPastHigh[t] = living >= CreepVegetation.DEATH_HI ? heldPastHigh[t] + 1 : 0;
                    if (heldPastHigh[t] > 2 && !overdueCounted[t]) {
                        overdueCounted[t] = true;
                        overdue++;
                    }
                }
            }
            if (tick < TICKS - CreepVegetation.CHECK_TICKS) return;
            for (int t = 0; t < trees.size(); t++) {
                if (touched[t]) continue;
                Tree tree = trees.get(t);
                boolean blocks = true;
                for (int cell : tree.crown) blocks &= grid.blocksStructuralLineOfSightAt(cell);
                if (seenDead[t] || !blocks) untouchedChanged++;
            }
        }

        /** Whether every tree holding the cell is dead and the cell is none of their trunks. */
        private boolean opensNow(CreepVegetation vegetation, int cell) {
            for (int owner : owners.get(cell)) {
                Tree tree = trees.get(owner);
                if (vegetation.diedAt(tree.placed.cellX, tree.placed.cellY) < 0) return false;
                for (int i = 0; i < tree.crown.length; i++) if (tree.crown[i] == cell && tree.trunk[i]) return false;
            }
            return true;
        }
    }
}
