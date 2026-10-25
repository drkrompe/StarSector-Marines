package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.Bsp;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.bsp.Compound;
import com.dillon.starsectormarines.battle.world.gen.bsp.DistrictMap;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * How planned the stock city and the colony read ({@code town-plan.md}), over 24 seeds of each: for shop, home
 * and main-street lots (every member of the district, its plaza included), how much of their street edge is built up (a building within three cells of the edge),
 * how many buildings stand per twenty cells of street edge and per lot, and for the zoning, how many blocks
 * are civic or mixed town or industry and how big the largest connected group of each is. Writes
 * {@code build/reports/town-plan/town-plan.md}. Asserts nothing: it measures.
 */
@Tag("generated-space-evidence")
class TownPlanEvidence {

    static final int SEEDS = 24;
    /** Cells into a lot from its street edge that count as fronting the street. */
    static final int FRONT_DEPTH = 3;

    private static final List<BlockKind> LOTS = List.of(BlockKind.BUILDING_COMMERCIAL,
            BlockKind.BUILDING_RESIDENTIAL, BlockKind.MAIN_STREET);

    @Test
    void townPlanMeasures() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        StringBuilder out = new StringBuilder("# Town plan measures\n\n");
        out.append("| family | lot kind | lots | street edge built | buildings / 20 edge cells | buildings / lot |\n");
        out.append("|---|---|---|---|---|---|\n");
        StringBuilder sides = new StringBuilder("\nShop and home lots by side length, cells (19 is 19 or more):\n\n| family | side |");
        for (int i = 3; i < 20; i++) sides.append(' ').append(i).append(" |");
        sides.append("\n|---|---|");
        for (int i = 3; i < 20; i++) sides.append("---|");
        sides.append('\n');
        StringBuilder zoning = new StringBuilder("\n| family | civic or mixed blocks / map | largest connected civic or mixed group | industrial blocks / map | largest connected industrial group | blocks / map |\n|---|---|---|---|---|---|\n");
        for (boolean colony : new boolean[]{false, true}) {
            Map<BlockKind, long[]> totals = new EnumMap<>(BlockKind.class);
            int[] shortSides = new int[20], longSides = new int[20];
            long townBlocks = 0, largest = 0, industryBlocks = 0, largestIndustry = 0, blocks = 0;
            for (long seed = 1; seed <= SEEDS; seed++) {
                MapScale scale = colony ? MapScale.COLONY : MapScale.MEDIUM;
                BspCityGenerator gen = new BspCityGenerator();
                MapResult map = colony
                        ? gen.generate(scale.width, scale.height, seed, null, TargetProfile.NEUTRAL,
                                BattleSetup.colonyPlanFor(scale, seed))
                        : gen.generate(scale.width, scale.height, seed);
                Bsp.Partition partition = gen.getLastPartition();
                boolean[][] inLeaf = new boolean[scale.width][scale.height];
                for (BlockLeaf leaf : partition.leaves) {
                    for (int x = leaf.left; x <= leaf.right; x++) {
                        for (int y = leaf.top; y <= leaf.bottom; y++) inLeaf[x][y] = true;
                    }
                }
                Set<BlockLeaf> spine = Collections.newSetFromMap(new IdentityHashMap<>());
                for (Compound compound : gen.getLastCompounds()) {
                    if (compound.kind == BlockKind.MAIN_STREET) spine.addAll(compound.members);
                }
                for (BlockLeaf leaf : partition.leaves) {
                    BlockKind kind = spine.contains(leaf) ? BlockKind.MAIN_STREET : leaf.kind;
                    if (!LOTS.contains(kind)) continue;
                    long[] t = totals.computeIfAbsent(kind, k -> new long[4]);
                    t[0]++;
                    int[] edge = streetEdge(map, leaf, inLeaf);
                    t[1] += edge[0];
                    t[2] += edge[1];
                    t[3] += buildingsIn(map, leaf);
                    if (kind != BlockKind.MAIN_STREET) {
                        shortSides[Math.min(19, Math.min(leaf.width(), leaf.height()))]++;
                        longSides[Math.min(19, Math.max(leaf.width(), leaf.height()))]++;
                    }
                }
                DistrictMap districts = gen.getLastDistrictMap();
                int[] z = blocksOf(districts, EnumSet.of(MapDistrictTheme.CIVIC, MapDistrictTheme.MIXED));
                townBlocks += z[0];
                largest += z[1];
                int[] i = blocksOf(districts, EnumSet.of(MapDistrictTheme.INDUSTRIAL));
                industryBlocks += i[0];
                largestIndustry += i[1];
                blocks += (long) districts.districtsX() * districts.districtsY();
            }
            String family = colony ? "colony" : "city";
            for (BlockKind kind : LOTS) {
                long[] t = totals.getOrDefault(kind, new long[4]);
                out.append(String.format(Locale.ROOT, "| %s | %s | %d | %.0f%% | %.2f | %.2f |%n", family, kind, t[0],
                        t[1] == 0 ? 0 : 100.0 * t[2] / t[1], t[1] == 0 ? 0 : 20.0 * t[3] / t[1],
                        t[0] == 0 ? 0 : (double) t[3] / t[0]));
            }
            sides.append(String.format(Locale.ROOT, "| %s | short |", family));
            for (int i = 3; i < 20; i++) sides.append(' ').append(shortSides[i]).append(" |");
            sides.append(String.format(Locale.ROOT, "%n| %s | long |", family));
            for (int i = 3; i < 20; i++) sides.append(' ').append(longSides[i]).append(" |");
            sides.append('\n');
            zoning.append(String.format(Locale.ROOT, "| %s | %.1f | %.1f | %.1f | %.1f | %.1f |%n", family,
                    (double) townBlocks / SEEDS, (double) largest / SEEDS, (double) industryBlocks / SEEDS,
                    (double) largestIndustry / SEEDS, (double) blocks / SEEDS));
        }
        out.append(sides).append(zoning);
        Path dir = root.resolve("build/reports/town-plan");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("town-plan.md"), out.toString());
        System.out.println(out);
    }

    /** {edge cells facing a street, of those the ones with a building within {@link #FRONT_DEPTH} cells}. */
    private static int[] streetEdge(MapResult map, BlockLeaf leaf, boolean[][] inLeaf) {
        int cells = 0, built = 0;
        int[][] sides = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] d : sides) {
            boolean horizontal = d[1] != 0;
            int lo = horizontal ? leaf.left : leaf.top, hi = horizontal ? leaf.right : leaf.bottom;
            for (int a = lo; a <= hi; a++) {
                int ex = horizontal ? a : d[0] < 0 ? leaf.left : leaf.right;
                int ey = horizontal ? (d[1] < 0 ? leaf.top : leaf.bottom) : a;
                int ox = ex + d[0], oy = ey + d[1];
                if (ox < 0 || oy < 0 || ox >= inLeaf.length || oy >= inLeaf[0].length || inLeaf[ox][oy]) continue;
                if (!map.grid.isWalkable(ox, oy) && !map.topology.isFixture(ox, oy)) continue;
                cells++;
                for (int k = 0; k <= FRONT_DEPTH; k++) {
                    int x = ex - d[0] * k, y = ey - d[1] * k;
                    if (!leaf.contains(x, y)) break;
                    if (map.topology.hasBuildingKindHint(x, y)) { built++; break; }
                }
            }
        }
        return new int[]{cells, built};
    }

    private static int buildingsIn(MapResult map, BlockLeaf leaf) {
        int n = 0;
        for (Building b : map.buildings.all()) {
            if (leaf.contains((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2)) n++;
        }
        return n;
    }

    /** {blocks of the given themes, the largest 4-connected group of them}. */
    private static int[] blocksOf(DistrictMap districts, Set<MapDistrictTheme> themes) {
        int nx = districts.districtsX(), ny = districts.districtsY();
        boolean[][] town = new boolean[nx][ny];
        int count = 0;
        for (int dx = 0; dx < nx; dx++) {
            for (int dy = 0; dy < ny; dy++) {
                MapDistrictTheme theme = districts.themeAt(dx * districts.districtCellWidth() + districts.districtCellWidth() / 2,
                        dy * districts.districtCellHeight() + districts.districtCellHeight() / 2);
                town[dx][dy] = themes.contains(theme);
                if (town[dx][dy]) count++;
            }
        }
        boolean[][] seen = new boolean[nx][ny];
        int best = 0;
        for (int sx = 0; sx < nx; sx++) {
            for (int sy = 0; sy < ny; sy++) {
                if (!town[sx][sy] || seen[sx][sy]) continue;
                int size = 0;
                Deque<int[]> stack = new ArrayDeque<>();
                stack.push(new int[]{sx, sy});
                seen[sx][sy] = true;
                while (!stack.isEmpty()) {
                    int[] at = stack.pop();
                    size++;
                    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] d : dirs) {
                        int x = at[0] + d[0], y = at[1] + d[1];
                        if (x < 0 || y < 0 || x >= nx || y >= ny || !town[x][y] || seen[x][y]) continue;
                        seen[x][y] = true;
                        stack.push(new int[]{x, y});
                    }
                }
                best = Math.max(best, size);
            }
        }
        return new int[]{count, best};
    }
}
