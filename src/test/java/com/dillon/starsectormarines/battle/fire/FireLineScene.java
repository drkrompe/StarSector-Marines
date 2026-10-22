package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.creep.CreepGrowth;
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
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A fire line lit along a road, burning east through a hedged meadow into a
 * forest the creep has grown over, and stopping at a four-cell road with more
 * grass and creep beyond it (`fire-nouns.md`). The road is bare: creep over a
 * road is fuel, so the forest's creep stops short of it. One loop per wind:
 * <ul>
 *   <li>{@code calm}: no wind; the front runs on fuel alone.</li>
 *   <li>{@code tailwind}: a strong wind toward the break, the worst case a
 *   four-cell break is meant to hold.</li>
 * </ul>
 * Verdicts are the field's mechanics: nothing past the break ever catches, burning
 * creep is gone within seconds of catching, burnt ground holds no living creep
 * while it refuses it, and a tree whose crown has burnt through has fallen. How
 * far the fire got, how long it took and how much creep it took are metrics.
 */
public final class FireLineScene implements BehaviorScene {

    static final int WIDTH = 130, HEIGHT = 56;
    static final int START_ROAD = 3, MEADOW_END = 33, FOREST_END = 85, BREAK_END = 89;
    /** Creep in the forest, stopping short of the road, and a second patch beyond the break. */
    static final int CREEP_X = 58, CREEP_Y = 28, FAR_CREEP_X = 112;
    private static final long SEED = 20261022L;
    private static final int LIGHT_TICK = 900;
    private static final int TICKS = LIGHT_TICK + 30 * 200 + FireField.CREEP_REFUSAL_TICKS;
    private static final int FRAME_EVERY = 150;
    /** Ticks after catching by which burning creep must be gone. */
    private static final int RECEDE_TICKS = 90;

    @Override public String id() { return "fire-line"; }

    @Override public String label() {
        return "A fire line burns through a meadow into creep-grown forest and stops at a road";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(playOne("calm", 0f, frames), playOne("tailwind", 1f, frames));
    }

    private SceneReport playOne(String loop, float wind, FrameSink frames) throws Exception {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        List<Doodad> doodads = new ArrayList<>();
        List<ForestField.Tree> trees = lay(grid, topology, doodads);
        SceneWorld world = SceneBuilder.over(grid, topology).seed(SEED).missionCompletion(false).build();
        try (BattleSimulation sim = world.sim()) {
            for (Doodad doodad : doodads) sim.addDoodad(doodad);
            sim.creepField().putSource(1L, CREEP_X, CREEP_Y, new CreepGrowth(4f, 22f));
            sim.creepField().putSource(2L, FAR_CREEP_X, CREEP_Y, new CreepGrowth(4f, 14f));
            Watch watch = new Watch(sim, trees, wind);
            SceneRun.of(world).observe(watch)
                    .frames(frames, loop, FRAME_EVERY, tick -> loop + " / " + watch.burningNow + " burning / "
                            + watch.burnt + " burnt / front x " + watch.frontX + " / tick " + tick)
                    .run(TICKS);
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("the-break-holds", watch.pastBreak == 0,
                    watch.pastBreak + " cells past the " + (BREAK_END - FOREST_END) + "-cell road caught"
                            + (watch.frontX >= FOREST_END - 2 ? "" : " (the fire never reached the road: front x "
                            + watch.frontX + ")")));
            verdicts.add(Verdict.of("burning-creep-recedes", watch.lingering == 0,
                    watch.lingering + " of " + watch.creepCaught + " creep cells still held creep "
                            + RECEDE_TICKS + " ticks after catching"));
            verdicts.add(Verdict.of("burnt-ground-refuses-creep", watch.creepOnRefused == 0,
                    watch.creepOnRefused + " looks found living creep on burnt ground inside its refusal"));
            verdicts.add(Verdict.of("burnt-through-trees-fall", watch.standingBurnt() == 0,
                    watch.standingBurnt() + " trees with at least three burnt-out crown cells still stand"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("cellsBurnt", watch.burnt);
            metrics.put("creepCellsBurnt", watch.creepCaught);
            metrics.put("peakBurning", watch.peakBurning);
            metrics.put("frontX", watch.frontX);
            metrics.put("ticksToForest", watch.forestTick < 0 ? -1 : watch.forestTick - LIGHT_TICK);
            metrics.put("ticksToBreak", watch.breakTick < 0 ? -1 : watch.breakTick - LIGHT_TICK);
            metrics.put("lastBurningTick", watch.lastBurningTick);
            metrics.put("treesFallen", watch.treesFallen());
            metrics.put("creepBeforeLight", watch.creepBefore);
            metrics.put("creepAtFireOut", watch.creepAtOut);
            metrics.put("creepAtEnd", watch.creepNow);
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    /** Lays the ground: road, hedged meadow, forest, the break, and grass beyond; returns the trees. */
    static List<ForestField.Tree> lay(NavigationGrid grid, CellTopology topology, List<Doodad> doodads) {
        int shrub = -1;
        TileRegistry registry = TileRegistry.installed();
        if (registry != null && registry.has("nature.shrub-1")) shrub = registry.indexOf("nature.shrub-1");
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                boolean road = x <= START_ROAD || (x > FOREST_END && x <= BREAK_END);
                topology.setGroundKind(x, y, road ? CellTopology.GroundKind.STREET : CellTopology.GroundKind.GRASS);
                if (!road && x > START_ROAD + 4 && x < MEADOW_END - 2 && y % 9 == 4 && shrub >= 0) {
                    topology.setNatureOverlayIndex(x, y, shrub);
                }
            }
        }
        List<ForestField.Tree> trees = new ArrayList<>();
        TreeCanopy[] kinds = {TreeCanopy.OAK, TreeCanopy.PINE_LARGE, TreeCanopy.OAK_SMALL, TreeCanopy.CEDAR};
        Random rng = new Random(SEED);
        for (int ty = 1; ty + 7 < HEIGHT; ty += 7) {
            for (int tx = MEADOW_END + 1; tx + 6 <= FOREST_END; tx += 7) {
                TreeCanopy kind = kinds[rng.nextInt(kinds.length)];
                int x = tx + rng.nextInt(2), y = ty + rng.nextInt(2);
                if (x + kind.width() > FOREST_END || y + kind.height() >= HEIGHT) continue;
                trees.add(new ForestField.Tree(x, y, kind, 0));
            }
        }
        // A few trees beyond the break, so it is fuel the fire would take if it crossed.
        trees.add(new ForestField.Tree(BREAK_END + 6, 10, TreeCanopy.OAK, 0));
        trees.add(new ForestField.Tree(BREAK_END + 6, 36, TreeCanopy.PINE_LARGE, 0));
        ForestBrush.stampTrees(trees, grid, topology, doodads, null, false);
        return trees;
    }

    private static final class Watch implements TickObserver {
        private final List<ForestField.Tree> trees;
        private final float wind;
        private final int width, cells;
        private final int[] caughtAt;
        private final boolean[] caughtCreep;
        int burningNow, peakBurning, burnt, frontX = -1, forestTick = -1, breakTick = -1, lastBurningTick = -1;
        int pastBreak, creepCaught, lingering, creepOnRefused;
        int creepBefore, creepAtOut = -1, creepNow;
        private BattleSimulation sim;

        Watch(BattleSimulation sim, List<ForestField.Tree> trees, float wind) {
            this.sim = sim;
            this.trees = trees;
            this.wind = wind;
            this.width = sim.getGrid().getWidth();
            this.cells = width * sim.getGrid().getHeight();
            this.caughtAt = new int[cells];
            this.caughtCreep = new boolean[cells];
            Arrays.fill(caughtAt, -1);
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            this.sim = sim;
            CreepField creep = sim.creepFieldIfPresent();
            if (tick == LIGHT_TICK) {
                creepBefore = creepCells(creep);
                FireField fire = sim.fireField();
                fire.setWind(1f, 0f, wind);
                for (int y = 0; y < HEIGHT; y++) fire.addHeat(START_ROAD + 1, y, 3000);
            }
            FireField fire = sim.fireFieldIfPresent();
            if (fire == null) return;
            if (tick % 15 != 0) return;
            burningNow = fire.burningCount();
            peakBurning = Math.max(peakBurning, burningNow);
            if (burningNow > 0) lastBurningTick = tick;
            else if (creepAtOut < 0 && tick > LIGHT_TICK + 30) creepAtOut = creepCells(creep);
            burnt = 0;
            for (int idx = 0; idx < cells; idx++) {
                byte s = fire.stateAt(idx);
                if (s == FireField.UNBURNT) continue;
                int x = idx % width;
                if (s == FireField.BURNT) burnt++;
                if (caughtAt[idx] < 0) {
                    caughtAt[idx] = tick;
                    int mask = fire.burnedMaskAt(idx);
                    caughtCreep[idx] = FireFuel.CREEP.in(mask) || FireFuel.DEAD_CREEP.in(mask);
                    if (caughtCreep[idx]) creepCaught++;
                    if (x > BREAK_END) pastBreak++;
                    if (x > frontX) frontX = x;
                    if (forestTick < 0 && x > MEADOW_END) forestTick = tick;
                    if (breakTick < 0 && x >= FOREST_END - 2) breakTick = tick;
                }
                if (creep == null) continue;
                if (caughtCreep[idx] && tick - caughtAt[idx] == RECEDE_TICKS
                        && creep.coverageAt(idx) > 0) lingering++;
                if (fire.refusesCreep(idx) && fire.burntAge(idx) > RECEDE_TICKS && creep.isCreeped(x, idx / width)) {
                    creepOnRefused++;
                }
            }
            creepNow = creepCells(creep);
        }

        private static int creepCells(CreepField creep) {
            return creep == null ? 0 : creep.snapshot().coveredCells();
        }

        /** Trees with at least three burnt-out crown cells whose crown still blocks. */
        int standingBurnt() {
            FireField fire = sim.fireFieldIfPresent();
            if (fire == null) return 0;
            int standing = 0;
            for (ForestField.Tree tree : trees) {
                int burntCrown = 0;
                boolean blocks = false;
                for (int y = tree.y(); y < tree.y() + tree.height(); y++) {
                    for (int x = tree.x(); x < tree.x() + tree.width(); x++) {
                        if (!tree.blocks(x, y)) continue;
                        int idx = y * width + x;
                        if (fire.stateAt(idx) == FireField.BURNT && FireFuel.CROWN.in(fire.burnedMaskAt(idx))) burntCrown++;
                        if (sim.getGrid().hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)) blocks = true;
                    }
                }
                if (burntCrown >= 3 && blocks) standing++;
            }
            return standing;
        }

        int treesFallen() {
            int fallen = 0;
            for (ForestField.Tree tree : trees) {
                boolean blocks = false;
                for (int y = tree.y(); y < tree.y() + tree.height() && !blocks; y++)
                    for (int x = tree.x(); x < tree.x() + tree.width() && !blocks; x++)
                        if (tree.blocks(x, y) && sim.getGrid().hasTag(x, y, NavigationGrid.CellTag.NATURAL_SOLID)) blocks = true;
                if (!blocks) fallen++;
            }
            return fallen;
        }
    }
}
