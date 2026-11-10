package com.dillon.starsectormarines.battle.world.gen.bsp.stage;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.LivingWorld;
import com.dillon.starsectormarines.battle.world.gen.PatchField;
import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.gen.forest.LandscapeAccess;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;

import java.util.ArrayList;
import java.util.List;

/**
 * The ground a living world's look lays over its wild grass ({@link LivingWorld}), in the closing landscape pass after
 * the hills and before the woods, so a wood grows on the ground it really has.
 *
 * <ul>
 *   <li><b>Tundra</b>: drifts of lying snow and bare frost-heaved earth over a short grass that stays the rest; all of
 *   it walked. (Frost-shattered stone was tried: the stone floor's pale tile read as stray paving.)</li>
 *   <li><b>Jungle</b>: swamp, wide patches of mud with standing pools at their hearts.</li>
 *   <li><b>Ocean</b>: a sea along the map's most open edge, marsh (mud flats with lagoons) inland, and a sand beach
 *   above every water.</li>
 * </ul>
 *
 * <p>Standing water is real water, drawn with the shore's rounded edge, rather than a wade over the ground: a wade
 * draws as a ford's stony shallows, which over a swamp's worth of ground reads as a grid of squares.
 *
 * <p>Only wild ground is dressed ({@link #wild}): never a building, a road, a mission's made ground or a hill's
 * visible face. Wet ground keeps off the landscape's reservations (places, spawns and the routes between them,
 * facilities, landing approaches). Water a squad cannot wade is laid only where it keeps the walkable country whole:
 * each pool by the woods' own local proof, infantry and tank alike ({@link LandscapeAccess}), and the sea by a
 * whole-map check that every cell of the main body before it is still reached after, laid all or not at all.
 */
final class LivingGround {

    /*
     * Every patch is where a two-octave smooth noise ({@link #blob}) falls under a level, so it comes out a rounded
     * blob with a ragged rim rather than a lattice of square patches: lying snow shows every step of a square one.
     * The levels are on that noise, not shares of the ground; the look suite prints the shares they give.
     */
    /** Level under which wild tundra lies under snow, and over which, under the next, it is bare permafrost. UNTUNED. */
    static final float SNOW = .36f, PERMAFROST = .34f;
    /** Level under which wild jungle is swamp mud, and under which, deeper in, it stands under water. UNTUNED. */
    static final float SWAMP = .4f, JUNGLE_POOL = .27f;
    /** Level under which wild ocean-world ground is marsh mud, and under which, deeper in, it is a lagoon. UNTUNED. */
    static final float MARSH = .34f, LAGOON = .22f;
    /** Width of a patch, in cells, for each look's noise. */
    static final float SNOW_CELLS = 11f, FROST_CELLS = 7f, SWAMP_CELLS = 13f, MARSH_CELLS = 11f;
    /** How deep the sea reaches in from its edge, in cells: the middle, the noise's swing either way, and the bounds. */
    static final int SEA_DEPTH = 13, SEA_SWING = 5, SEA_MIN = 6, SEA_MAX = 20;
    /** How far in from the edge the sea may begin, past a road or a margin round the map's rim. */
    static final int SEA_INSET = 3;
    /** How far above the sea a beach of sand runs, in cells. */
    static final int BEACH = 4;
    /** The woods' tank lane, which a pool may not close ({@link OutdoorLandscapeStage#TANK_CLEARANCE}). */
    private static final int TANK = OutdoorLandscapeStage.TANK_CLEARANCE;

    private LivingGround() {}

    /**
     * Dresses the wild ground for the field's look; nothing for {@link LivingWorld#TEMPERATE} or a world that is not
     * living.
     *
     * @param reserved the landscape's reservations, kept dry
     * @param seaReserved what a sea keeps off: what the landscape reserves but its routes, its roads' margins and the
     *                    map's edge band, which is the one place a sea may be (its own whole-map check keeps the routes)
     */
    static void dress(GenContext c, SurfaceField field, boolean[] reserved, boolean[] seaReserved) {
        switch (field.living()) {
            case TUNDRA -> tundra(c, field);
            case JUNGLE -> wet(c, field, reserved, c.seed ^ 0x5357414D50L, SWAMP_CELLS, SWAMP, JUNGLE_POOL);
            case OCEAN -> {
                boolean[] sea = sea(c, field, seaReserved);
                beach(c, field, sea);
                wet(c, field, reserved, c.seed ^ 0x4D41525348L, MARSH_CELLS, MARSH, LAGOON);
            }
            default -> { }
        }
    }

    /** Two octaves of smooth noise in [0, 1]: broad blobs {@code cells} wide with a fringe a third that size. */
    static float blob(int x, int y, long seed, float cells) {
        float fine = cells / 3f;
        return .72f * SurfaceField.noise(x / cells, y / cells, seed)
                + .28f * SurfaceField.noise(x / fine, y / fine, seed ^ 0x46524159L);
    }

    /** What a tundra's wild cell lies under, from its snow and frost noise. */
    static GroundKind tundraGround(float snow, float frost) {
        if (snow < SNOW) return GroundKind.SNOW;
        if (frost < PERMAFROST) return GroundKind.DIRT;
        return GroundKind.GRASS;
    }

    private static void tundra(GenContext c, SurfaceField field) {
        long snow = c.seed ^ 0x534E4F57L, frost = c.seed ^ 0x46524F5354L;
        for (int y = 0; y < c.height; y++) for (int x = 0; x < c.width; x++) {
            if (!living(field, x, y) || !wild(c, x, y)) continue;
            GroundKind kind = tundraGround(blob(x, y, snow, SNOW_CELLS), blob(x, y, frost, FROST_CELLS));
            if (kind == GroundKind.GRASS) continue;
            c.topology.setGroundKind(x, y, kind);
            c.topology.setNatureOverlayIndex(x, y, -1);
        }
    }

    /**
     * Turns wild ground to mud where the noise falls under {@code muddy}, and stands water where it falls under
     * {@code deep} in the mud's heart, each pool kept only where it closes no way round.
     */
    private static void wet(GenContext c, SurfaceField field, boolean[] reserved, long seed, float cells, float muddy,
                            float deep) {
        int w = c.width, h = c.height;
        boolean[] mud = new boolean[w * h], pool = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x;
            if (reserved[i] || !living(field, x, y) || !wild(c, x, y)) continue;
            float level = blob(x, y, seed, cells);
            if (level >= muddy) continue;
            mud[i] = true;
            pool[i] = level < deep;
        }
        // A pool needs mud all round it: standing water is never a swamp's edge.
        boolean[] core = new boolean[w * h];
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            if (!pool[y * w + x]) continue;
            boolean ringed = true;
            for (int dy = -1; dy <= 1 && ringed; dy++) for (int dx = -1; dx <= 1; dx++)
                if (!mud[(y + dy) * w + x + dx]) { ringed = false; break; }
            core[y * w + x] = ringed;
        }
        for (int i = 0; i < w * h; i++) {
            if (!mud[i]) continue;
            c.topology.setGroundKind(i % w, i / w, GroundKind.DIRT);
            c.topology.setNatureOverlayIndex(i % w, i / w, -1);
        }
        boolean[] closed = new boolean[w * h];
        for (List<Integer> body : components(core, w, h)) {
            int l = w, b = h, r = -1, t = -1;
            for (int i : body) { l = Math.min(l, i % w); r = Math.max(r, i % w); b = Math.min(b, i / w); t = Math.max(t, i / w); }
            if (!LandscapeAccess.canClose(c.grid, closed, l, b, r, t, (x, y) -> core[y * w + x])) continue;
            if (!LandscapeAccess.keepsClearance(c.grid, closed, l, b, r, t, (x, y) -> core[y * w + x], TANK)) continue;
            for (int i : body) {
                closed[i] = true;
                water(c, i % w, i / w);
            }
        }
    }

    /**
     * The sea along the edge with the most open wild ground in its band, reaching in a wandering
     * {@link #SEA_MIN}..{@link #SEA_MAX} cells and stopping at anything reserved, laid only when the walkable country
     * stays whole without the cells it takes.
     */
    private static boolean[] sea(GenContext c, SurfaceField field, boolean[] seaReserved) {
        int w = c.width, h = c.height;
        int edge = -1, most = -1;
        for (int e = 0; e < 4; e++) {
            int open = 0;
            int span = e < 2 ? h : w;
            for (int i = 0; i < span; i++) for (int d = 0; d < SEA_MAX; d++) {
                int[] at = cell(e, i, d, w, h);
                if (at != null && seaGround(c, field, seaReserved, at[0], at[1])) open++;
            }
            if (open > most) { most = open; edge = e; }
        }
        int span = edge < 2 ? h : w;
        int[] depths = seaDepths(span, c.seed);
        boolean[] carve = new boolean[w * h];
        for (int i = 0; i < span; i++) {
            boolean started = false;
            for (int d = 0; d < depths[i] + SEA_INSET; d++) {
                int[] at = cell(edge, i, d, w, h);
                boolean takes = at != null && seaGround(c, field, seaReserved, at[0], at[1]);
                // A road round the map's rim stays a coast road, the sea just inside it.
                if (!takes && !started && d < SEA_INSET) continue;
                // The sea stops at the first thing it may not take: a road reaching the shore stays a causeway.
                if (!takes) break;
                started = true;
                carve[at[1] * w + at[0]] = true;
            }
        }
        if (!keepsWhole(c.grid, carve)) return new boolean[w * h];
        for (int i = 0; i < w * h; i++) if (carve[i]) water(c, i % w, i / w);
        return carve;
    }

    /** The sea's reach along an edge {@code span} cells long: a smooth wander about {@link #SEA_DEPTH}. */
    static int[] seaDepths(int span, long seed) {
        PatchField wander = new PatchField(seed ^ 0x534541L, 9);
        int[] out = new int[span];
        for (int i = 0; i < span; i++) {
            // Two lattice rows averaged and smoothed along the edge so the shore curves rather than steps.
            float sum = 0f;
            int n = 0;
            for (int j = Math.max(0, i - 3); j <= Math.min(span - 1, i + 3); j++) { sum += wander.sample(j, 0); n++; }
            int depth = Math.round(SEA_DEPTH + SEA_SWING * (2f * sum / n - 1f) * 1.6f);
            out[i] = Math.max(SEA_MIN, Math.min(SEA_MAX, depth));
        }
        return out;
    }

    /**
     * The cell {@code depth} in from edge {@code edge} (0 west, 1 east, 2 south, 3 north) at position {@code along}
     * on it, or null off the map.
     */
    static int[] cell(int edge, int along, int depth, int w, int h) {
        int x = switch (edge) { case 0 -> depth; case 1 -> w - 1 - depth; default -> along; };
        int y = switch (edge) { case 2 -> depth; case 3 -> h - 1 - depth; default -> along; };
        return x < 0 || y < 0 || x >= w || y >= h ? null : new int[]{x, y};
    }

    private static boolean seaGround(GenContext c, SurfaceField field, boolean[] seaReserved, int x, int y) {
        if (seaReserved[y * c.width + x] || !living(field, x, y)) return false;
        return switch (c.topology.getGroundKind(x, y)) {
            case WATER -> true;
            case GRASS, DIRT, STONE, SAND, SNOW -> open(c, x, y);
            default -> false;
        };
    }

    /**
     * Whether taking {@code carve} leaves every walkable cell that was in the main walkable body still joined to the
     * rest of it: the largest body after the carve holds every main-body cell the carve did not take.
     */
    static boolean keepsWhole(NavigationGrid grid, boolean[] carve) {
        int[] before = bodies(grid, null), after = bodies(grid, carve);
        int main = largest(before), kept = largest(after);
        if (main == 0) return true;
        for (int i = 0; i < before.length; i++) {
            if (before[i] == main && !carve[i] && after[i] != kept) return false;
        }
        return true;
    }

    /** Sand above the sea a beach's width, over open natural living ground; the marsh's lagoons keep mud banks. */
    private static void beach(GenContext c, SurfaceField field, boolean[] wet) {
        int w = c.width, h = c.height;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!living(field, x, y) || !open(c, x, y) || !natural(c.topology.getGroundKind(x, y))) continue;
            // The beach's reach wanders, a half to one and a half its width, so its inland edge is a line, not a band.
            float reach = BEACH * (.5f + blob(x, y, c.seed ^ 0x4245414348L, 9f));
            int r = (int) Math.ceil(reach);
            boolean near = false;
            for (int dy = -r; dy <= r && !near; dy++) for (int dx = -r; dx <= r; dx++) {
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && wet[ny * w + nx] && dx * dx + dy * dy <= reach * reach) {
                    near = true;
                    break;
                }
            }
            if (!near) continue;
            c.topology.setGroundKind(x, y, GroundKind.SAND);
            c.topology.setNatureOverlayIndex(x, y, -1);
        }
    }

    private static boolean natural(GroundKind kind) {
        return switch (kind) {
            case GRASS, DIRT, STONE, SAND, SNOW -> true;
            default -> false;
        };
    }

    private static void water(GenContext c, int x, int y) {
        c.topology.setSurface(x, y, 0);
        c.topology.setGroundKind(x, y, GroundKind.WATER);
        c.topology.setNatureOverlayIndex(x, y, -1);
        c.topology.setTag(x, y, CellTopology.Tag.SHALLOW_WATER, false);
        c.grid.setTag(x, y, NavigationGrid.CellTag.SHALLOW_WATER, false);
        c.grid.setWalkable(x, y, false);
        c.grid.setSeeThrough(x, y, true);
        c.grid.setEdgeCoverSuppressed(x, y, true);
    }

    /** A basin's floor keeps its own look: only the world's own living ground is dressed. */
    private static boolean living(SurfaceField field, int x, int y) {
        return field.at(x, y) == field.world();
    }

    /** The wild grass: open walkable lawn nobody made, built on, paved or raised a hill face over. */
    static boolean wild(GenContext c, int x, int y) {
        return c.topology.getGroundKind(x, y) == GroundKind.GRASS && open(c, x, y);
    }

    private static boolean open(GenContext c, int x, int y) {
        if (!c.grid.isWalkable(x, y) || c.grid.isDoorway(x, y) || c.topology.isWall(x, y) || c.topology.isFixture(x, y)
                || c.topology.getBuildingId(x, y) != 0 || c.topology.getSurface(x, y) != 0 || c.isMadeGround(x, y))
            return false;
        if (c.topology.roadSurface() != null && c.topology.roadSurface().underlay(x, y) != null) return false;
        var terrain = c.topology.terrainSurface();
        if (terrain != null) for (int layer = 0; layer < TerrainSurface.LAYERS; layer++)
            if (terrain.patch(x, y, layer) != null) return false;
        return true;
    }

    /** 8-connected bodies of the marked cells. */
    private static List<List<Integer>> components(boolean[] marked, int w, int h) {
        List<List<Integer>> out = new ArrayList<>();
        boolean[] seen = new boolean[marked.length];
        int[] queue = new int[marked.length];
        for (int s = 0; s < marked.length; s++) {
            if (!marked[s] || seen[s]) continue;
            List<Integer> body = new ArrayList<>();
            int head = 0, tail = 0;
            queue[tail++] = s;
            seen[s] = true;
            while (head < tail) {
                int i = queue[head++];
                body.add(i);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = i % w + dx, ny = i / w + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int n = ny * w + nx;
                    if (marked[n] && !seen[n]) { seen[n] = true; queue[tail++] = n; }
                }
            }
            out.add(body);
        }
        return out;
    }

    /** Body labels (1..) of the walkable cells not in {@code without}, joined through passable cardinal edges. */
    private static int[] bodies(NavigationGrid grid, boolean[] without) {
        int w = grid.getWidth(), h = grid.getHeight();
        int[] label = new int[w * h], queue = new int[w * h];
        int next = 0;
        for (int s = 0; s < w * h; s++) {
            if (label[s] != 0 || !open(grid, without, s % w, s / w)) continue;
            int id = ++next, head = 0, tail = 0;
            queue[tail++] = s;
            label[s] = id;
            while (head < tail) {
                int i = queue[head++], x = i % w, y = i / w;
                for (Direction d : Direction.CARDINALS) {
                    int nx = x + d.dx, ny = y + d.dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int n = ny * w + nx;
                    if (label[n] != 0 || !open(grid, without, nx, ny)) continue;
                    if (!grid.isEdgePassable(x, y, d) || !grid.isEdgePassable(nx, ny, d.opposite())) continue;
                    label[n] = id;
                    queue[tail++] = n;
                }
            }
        }
        return label;
    }

    private static boolean open(NavigationGrid grid, boolean[] without, int x, int y) {
        return grid.isWalkable(x, y) && (without == null || !without[y * grid.getWidth() + x]);
    }

    /** The label held by the most cells; 0 when there is none. */
    private static int largest(int[] label) {
        int max = 0;
        for (int l : label) max = Math.max(max, l);
        int[] count = new int[max + 1];
        for (int l : label) if (l > 0) count[l]++;
        int best = 0;
        for (int l = 1; l <= max; l++) if (count[l] > count[best]) best = l;
        return best;
    }
}
