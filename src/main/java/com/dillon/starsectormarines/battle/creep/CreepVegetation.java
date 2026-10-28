package com.dillon.starsectormarines.battle.creep;

import com.dillon.starsectormarines.battle.world.gen.nature.NaturalPropShape;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.gen.forest.TreeCanopy;
import com.dillon.starsectormarines.battle.world.model.Doodad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Creep kills the trees it climbs (`colony-breach-nouns.md`, Creep).
 *
 * <p>A tree's crown cells are fixtures the creep climbs as it climbs a wall, so
 * their coverage is how long the creep has held them ({@link CreepField}). The
 * tree's <em>age</em> is the mean over its crown; drawn, it carries a tree through
 * veins, a crust rolling in from the rim and a withering crown. Once the crown's
 * living coverage reaches the tree's own seeded share of {@link #DEATH_LO} to
 * {@link #DEATH_HI} (about a minute and a half of climbing) the tree is dead,
 * and stays dead if the creep later withers.
 *
 * <p>A dead tree has dropped its leaves, so its crown no longer hides anything
 * and no longer shelters anyone (sight passes and it gives no edge cover) while
 * its trunk still does both, and its tangle of bare limbs still stops a body
 * walking through it. Felling still works as it does on a living tree. It is
 * undergrowth now: a heavy tank pushes it over, trunk and all, wherever no
 * living tree's crown holds its cells ({@code HeavyTracks}).
 *
 * <p>Reads the field and keeps its own per-tree state, so the field knows
 * nothing of trees. Looks every {@link #CHECK_TICKS}; deterministic in the seed.
 * {@code -Dbattle.swarm.creepTrees=false} is the control (trees never die).
 */
public final class CreepVegetation {

    /** Control-run switch; trees die unless it reads {@code false}. */
    public static final String PROPERTY = "battle.swarm.creepTrees";

    /** Ticks between looks: half a second. */
    public static final int CHECK_TICKS = 15;
    /** Mean living crown coverage, 0..255, at which a tree may die: its seeded threshold lies between these. */
    public static final int DEATH_LO = 215, DEATH_HI = 250;
    /** Cells of a crown within this distance of its centre are its trunk: they keep blocking once it is dead. */
    public static final float TRUNK_RADIUS = 0.75f;

    private final long seed;
    private final boolean enabled = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));
    private volatile Index index;

    public CreepVegetation(long seed) {
        this.seed = seed;
    }

    /** One tree: where it stands, the crown cells it blocks and which of them are its trunk. */
    record Tree(int x, int y, int[] crown, boolean[] trunk, int threshold) { }

    /**
     * Every tree of the map, found once, each one's death tick (-1 while it lives) and the coverage skitterers have
     * stripped off its remaining hold.
     */
    private record Index(List<Tree> trees, Map<Long, Integer> byOrigin, Map<Integer, int[]> owners, int[] diedAt,
                         int[] stripped) { }

    /** Mean living crown coverage a tree needs before a skitterer can work it: the creep's veins are in it. */
    public static final int STRIPPABLE_COVERAGE = 64;

    /**
     * The living, standing tree nearest {@code (x, y)} within {@code radius} whose crown the creep has reached
     * ({@link #STRIPPABLE_COVERAGE}), as {@code {tree, crownCellX, crownCellY}} (a crown cell a worker can stand
     * beside), or {@code null}; nothing before the first look. Serial.
     */
    public int[] nearestStrippable(BattleSimulation sim, float x, float y, float radius) {
        Index idx = index;
        CreepField field = sim.creepFieldIfPresent();
        if (!enabled || idx == null || field == null) return null;
        NavigationGrid grid = sim.getGrid();
        int width = grid.getWidth();
        int[] best = null;
        float bestDistance = radius * radius;
        for (int t = 0; t < idx.trees.size(); t++) {
            if (idx.diedAt[t] >= 0) continue;
            Tree tree = idx.trees.get(t);
            float dx = tree.x + 0.5f - x, dy = tree.y + 0.5f - y;
            float distance = dx * dx + dy * dy;
            if (distance >= bestDistance) continue;
            if (!standing(tree, grid) || livingCoverage(tree, field) < STRIPPABLE_COVERAGE) continue;
            int cell = tree.crown[0];
            best = new int[]{t, cell % width, cell / width};
            bestDistance = distance;
        }
        return best;
    }

    /** Whether tree {@code t} (from {@link #nearestStrippable}) has died. */
    public boolean dead(int t) {
        Index idx = index;
        return idx != null && idx.diedAt[t] >= 0;
    }

    /** A skitterer worked tree {@code t}: {@code points} of coverage off what the creep still has to climb. */
    public void strip(int t, int points) {
        Index idx = index;
        if (idx != null && idx.diedAt[t] < 0) idx.stripped[t] += Math.max(0, points);
    }

    /** Once per simulation tick, after the creep has spread. No-op while there is no creep. */
    public void tick(BattleSimulation sim) {
        int tick = sim.getSimTickIndex();
        if (tick % CHECK_TICKS != 0) return;
        look(sim, tick);
    }

    /** One look at {@code tick}, whatever the battle's clock: for a review that advances the creep field alone. */
    public void look(BattleSimulation sim, int tick) {
        if (!enabled) return;
        CreepField field = sim.creepFieldIfPresent();
        if (field == null || field.isEmpty()) return;
        MapEditor editor = sim.mapEditor();
        look(tick, field, sim.getGrid(), sim.getDoodads(), new Wither() {
            @Override public void open(int x, int y) { editor.witherCrownCell(x, y); }
            @Override public void died(int originX, int originY) { editor.witherTree(originX, originY); }
        });
    }

    /**
     * Opens one dead crown cell to sight ({@link MapEditor#witherCrownCell} in a battle), and hears of each tree as it
     * dies ({@link MapEditor#witherTree}).
     */
    public interface Wither {
        void open(int x, int y);
        default void died(int originX, int originY) { }
    }

    /** One look over the map's trees, its doodads read on the first look only. */
    void look(int tick, CreepField field, NavigationGrid grid, List<Doodad> doodads, Wither wither) {
        if (!enabled) return;
        Index idx = index;
        if (idx == null) index = idx = build(doodads, grid, seed);
        for (int t = 0; t < idx.trees.size(); t++) {
            if (idx.diedAt[t] >= 0) continue;
            Tree tree = idx.trees.get(t);
            if (!standing(tree, grid)) continue;
            if (livingCoverage(tree, field) + idx.stripped[t] < tree.threshold) continue;
            idx.diedAt[t] = tick;
            wither.died(tree.x, tree.y);
            for (int cell : tree.crown) {
                if (opens(idx, cell)) wither.open(cell % grid.getWidth(), cell / grid.getWidth());
            }
        }
    }

    /** A crown cell opens once every tree whose crown holds it is dead and it is none of their trunks. */
    private static boolean opens(Index idx, int cell) {
        for (int owner : idx.owners.get(cell)) {
            if (idx.diedAt[owner] < 0) return false;
            Tree tree = idx.trees.get(owner);
            for (int i = 0; i < tree.crown.length; i++) if (tree.crown[i] == cell && tree.trunk[i]) return false;
        }
        return true;
    }

    /** The tick the tree placed at this origin died, or -1 if it lives or is no tree. Safe for renderers. */
    public int diedAt(int originX, int originY) {
        Index idx = index;
        if (idx == null) return -1;
        Integer t = idx.byOrigin.get(key(originX, originY));
        return t == null ? -1 : idx.diedAt[t];
    }

    /** Trees found and trees dead, for evidence. */
    public String summary() {
        Index idx = index;
        if (idx == null) return "no trees looked at";
        int dead = 0;
        for (int d : idx.diedAt) if (d >= 0) dead++;
        return String.format(Locale.ROOT, "%d of %d trees dead", dead, idx.trees.size());
    }

    /** Mean coverage of living creep over the tree's crown, 0..255. */
    static int livingCoverage(Tree tree, CreepField field) {
        long sum = 0;
        for (int cell : tree.crown) {
            if (field.vitalityAt(cell) >= CreepField.CREEPED) sum += field.coverageAt(cell);
        }
        return (int) (sum / Math.max(1, tree.crown.length));
    }

    /** Whether the tree still stands: felling clears its natural obstruction. */
    private static boolean standing(Tree tree, NavigationGrid grid) {
        for (int cell : tree.crown) {
            if (grid.hasTag(cell % grid.getWidth(), cell / grid.getWidth(), NavigationGrid.CellTag.NATURAL_SOLID)) return true;
        }
        return false;
    }

    private static Index build(List<Doodad> doodads, NavigationGrid grid, long seed) {
        List<Tree> trees = new ArrayList<>();
        Map<Long, Integer> byOrigin = new HashMap<>();
        for (Doodad d : doodads) {
            TreeCanopy canopy = canopy(d.definitionId());
            if (canopy == null || d.definitionId().endsWith("-destroyed")) continue;
            int[] cells = crownCells(d, canopy, grid.getWidth(), grid.getHeight());
            if (cells.length == 0) continue;
            boolean[] trunk = trunkOf(d, cells, grid.getWidth());
            double u = unit(seed, d.cellX, d.cellY);
            int threshold = (int) Math.round(DEATH_LO + (DEATH_HI - DEATH_LO) * u);
            byOrigin.put(key(d.cellX, d.cellY), trees.size());
            trees.add(new Tree(d.cellX, d.cellY, cells, trunk, threshold));
        }
        Map<Integer, List<Integer>> holders = new HashMap<>();
        for (int t = 0; t < trees.size(); t++) {
            for (int cell : trees.get(t).crown) holders.computeIfAbsent(cell, unused -> new ArrayList<>()).add(t);
        }
        Map<Integer, int[]> owners = new HashMap<>();
        holders.forEach((cell, list) -> owners.put(cell, list.stream().mapToInt(Integer::intValue).toArray()));
        int[] diedAt = new int[trees.size()];
        Arrays.fill(diedAt, -1);
        return new Index(List.copyOf(trees), byOrigin, owners, diedAt, new int[trees.size()]);
    }

    /** The canopy a tree placement draws, intact, damaged or felled; null for anything else. */
    public static TreeCanopy canopy(String definitionId) {
        if (definitionId == null || !definitionId.startsWith("forest.")) return null;
        String base = definitionId;
        for (String suffix : new String[]{"-damaged", "-destroyed"}) {
            if (base.endsWith(suffix)) base = base.substring(0, base.length() - suffix.length());
        }
        if (base.endsWith(NaturalPropShape.DRY)) base = base.substring(0, base.length() - NaturalPropShape.DRY.length());
        for (TreeCanopy c : TreeCanopy.values()) if (c.id.equals(base)) return c;
        return null;
    }

    /** Every standing tree's crown cells on the map, for the creep to grow over. */
    public static int[] treeCells(List<Doodad> doodads, NavigationGrid grid) {
        List<Integer> cells = new ArrayList<>();
        for (Doodad d : doodads) {
            TreeCanopy canopy = canopy(d.definitionId());
            if (canopy == null || d.definitionId().endsWith("-destroyed")) continue;
            for (int cell : crownCells(d, canopy, grid.getWidth(), grid.getHeight())) cells.add(cell);
        }
        return cells.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Map cell indices the placement's crown blocks, as {@code NaturalPropDamage} registers them. */
    public static int[] crownCells(Doodad d, TreeCanopy canopy, int width, int height) {
        List<Integer> cells = new ArrayList<>();
        boolean oddTurn = (d.quarterTurns & 1) != 0;
        for (int y = 0; y < d.footprintCellsY; y++) {
            for (int x = 0; x < d.footprintCellsX; x++) {
                int maskX = d.mirrored && !oddTurn ? d.footprintCellsX - 1 - x : x;
                int maskY = d.mirrored && oddTurn ? d.footprintCellsY - 1 - y : y;
                if (!canopy.blocks(maskX, maskY, d.quarterTurns)) continue;
                int gx = d.cellX + x, gy = d.cellY + y;
                if (gx < 0 || gy < 0 || gx >= width || gy >= height) continue;
                cells.add(gy * width + gx);
            }
        }
        return cells.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Which crown cells are the trunk: those whose centres lie near the footprint's; at least the nearest one. */
    static boolean[] trunkOf(Doodad d, int[] cells, int width) {
        float cx = d.cellX + d.footprintCellsX * 0.5f, cy = d.cellY + d.footprintCellsY * 0.5f;
        boolean[] trunk = new boolean[cells.length];
        int nearest = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < cells.length; i++) {
            float dx = cells[i] % width + 0.5f - cx, dy = cells[i] / width + 0.5f - cy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            trunk[i] = dist <= TRUNK_RADIUS;
            if (dist < best) {
                best = dist;
                nearest = i;
            }
        }
        trunk[nearest] = true;
        return trunk;
    }

    private static long key(int x, int y) { return ((long) x << 32) ^ (y & 0xFFFFFFFFL); }

    private static double unit(long seed, int x, int y) {
        long z = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ 0x7EE5L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
