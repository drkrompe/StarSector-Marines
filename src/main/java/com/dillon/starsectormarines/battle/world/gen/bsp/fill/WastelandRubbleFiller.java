package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.world.gen.PlacementGuards;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralGround;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.CellTopology.WallRemnant;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Fills a {@link BlockKind#WASTELAND_RUBBLE} leaf: a lot with no standing
 * building on it. What stands there instead depends on the place.
 *
 * <p>In a <b>farming settlement</b> (a map with a rural layout round its town,
 * as the Colony Breach colony has) nothing was ever knocked down: the lot is
 * land in use. It is an empty lot of bare earth and weeds, a fenced dirt yard
 * with a few stacks of stores, or a kitchen farm plot in crop rows.
 *
 * <p>Anywhere else it is a <b>ruin</b>: the footprint of a building that burned
 * or was shelled out. Short runs of its wall still stand, which are real walls
 * with real cover; the rest of the outline is rubble stubs drawn from the same
 * material's rubble art. A collapsed corner lies heaped inside and the wrecked
 * furniture is still where the rooms were. Every standing run is short and
 * broken by open ground, so the lot is always crossable.
 */
public final class WastelandRubbleFiller implements BlockFiller {

    /** The least footprint a ruin is laid out on; a smaller lot is a bare rubble heap. */
    static final int MIN_RUIN_SIDE = 6;
    /** The longest run of a ruin's wall that still stands. */
    static final int MAX_STANDING_RUN = 3;
    private static final String[] RUIN_WALLS = {"colony.masonry-wall", "colony.ceramic-wall", "colony.olive-wall"};
    private static final String[] RUIN_FLOORS = {"colony.concrete", "colony.timber", "colony.terrazzo", "colony.terracotta"};
    private static final String[] WRECKAGE = {"doodad.shelf-dam-1", "doodad.shelf-dam-2", "doodad.desk-dam",
            "doodad.box-dam", "doodad.chair-s-yellow-dam", "doodad.chair-s-green-dam"};
    private static final String SCRAP = "doodad.industrial-scrap-pile";
    private static final String[] YARD_STORES = {"doodad.industrial-pallet-stack", "doodad.industrial-crate-stack",
            "doodad.industrial-drum-cluster", "doodad.industrial-cable-reel"};
    private static final String[] WEEDS = {"nature.tuft-1", "nature.tuft-2", "nature.tuft-3"};

    /** What a settlement lot is used for. */
    enum LandUse { EMPTY_LOT, YARD, FARM_PLOT }

    @Override
    public BlockKind kind() { return BlockKind.WASTELAND_RUBBLE; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        for (int y = leaf.top; y <= leaf.bottom; y++)
            for (int x = leaf.left; x <= leaf.right; x++) ctx.grid.setWalkableFloor(x, y);
        if (ctx.get(BspKeys.RURAL_LAYOUT) != null && settlementLots()) {
            settlementLot(leaf, ctx, landUse(ctx.rng, leaf.width(), leaf.height()));
        } else if (leaf.width() - 2 >= MIN_RUIN_SIDE && leaf.height() - 2 >= MIN_RUIN_SIDE) {
            ruin(leaf, ctx);
        } else {
            rubbleHeap(leaf, ctx);
        }
    }

    /**
     * Whether a farming settlement's lots are land in use.
     * {@code -Dbattle.mapgen.settlementLots=false} is the control: they are ruins too.
     */
    static boolean settlementLots() {
        return Boolean.parseBoolean(System.getProperty("battle.mapgen.settlementLots", "true"));
    }

    /** A settlement lot's use: a yard or a plot needs room for its fence and rows. */
    static LandUse landUse(Random rng, int width, int height) {
        if (Math.min(width, height) < 6) return LandUse.EMPTY_LOT;
        int roll = rng.nextInt(10);
        return roll < 3 ? LandUse.EMPTY_LOT : roll < 6 ? LandUse.YARD : LandUse.FARM_PLOT;
    }

    // ---- settlement lots --------------------------------------------------------------------

    private static void settlementLot(BlockLeaf leaf, GenContext ctx, LandUse use) {
        switch (use) {
            case EMPTY_LOT -> emptyLot(leaf, ctx);
            case YARD -> yard(leaf, ctx);
            case FARM_PLOT -> farmPlot(leaf, ctx);
        }
    }

    /** Bare earth, grown over at its edges, with weeds coming up through it. */
    private static void emptyLot(BlockLeaf leaf, GenContext ctx) {
        int[] weeds = natureIndices(WEEDS);
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                boolean margin = x == leaf.left || x == leaf.right || y == leaf.top || y == leaf.bottom;
                ground(ctx, x, y, margin ? GroundKind.GRASS : GroundKind.DIRT);
                if (weeds.length > 0 && ctx.rng.nextInt(margin ? 4 : 14) == 0)
                    ctx.topology.setNatureOverlayIndex(x, y, weeds[ctx.rng.nextInt(weeds.length)]);
                ctx.markMadeGround(x, y);
            }
        }
    }

    /** Trodden earth inside a fence, gated on every side, with stores stacked along it. */
    private static void yard(BlockLeaf leaf, GenContext ctx) {
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                ground(ctx, x, y, GroundKind.DIRT);
                ctx.markMadeGround(x, y);
            }
        }
        fence(leaf, ctx);
        // Stores stand a cell in from the fence, clear of the gate lanes through the middle.
        List<int[]> spots = new ArrayList<>();
        for (int y = leaf.top + 2; y <= leaf.bottom - 2; y++) {
            for (int x = leaf.left + 2; x <= leaf.right - 2; x++) {
                boolean edge = x == leaf.left + 2 || x == leaf.right - 2 || y == leaf.top + 2 || y == leaf.bottom - 2;
                if (!edge || Math.abs(x - leaf.centerX()) <= 1 || Math.abs(y - leaf.centerY()) <= 1) continue;
                spots.add(new int[]{x, y});
            }
        }
        int count = Math.min(spots.size(), 2 + ctx.rng.nextInt(3));
        for (int i = 0; i < count; i++) {
            int[] spot = spots.remove(ctx.rng.nextInt(spots.size()));
            if (PlacementGuards.touchesDoorway(ctx.grid, spot[0], spot[1])
                    || PlacementGuards.wouldStrandGround(ctx.grid, spot[0], spot[1])) continue;
            IndustrialYardFiller.placeProp(ctx, YARD_STORES[ctx.rng.nextInt(YARD_STORES.length)], spot[0], spot[1], true);
        }
    }

    /** A kitchen plot in crop rows, headland round it and a fence to keep stock out. */
    private static void farmPlot(BlockLeaf leaf, GenContext ctx) {
        RuralLayout.Use crop = ctx.rng.nextInt(5) == 0 ? RuralLayout.Use.GRAIN : RuralLayout.Use.ROW_CROP;
        RuralLayout.Plot plot = new RuralLayout.Plot(leaf.left + 1, leaf.top + 1, leaf.right - 1, leaf.bottom - 1,
                crop, leaf.width() >= leaf.height(), false);
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                GroundKind kind = plot.contains(x, y) ? RuralGround.plotGround(plot, x, y) : GroundKind.DIRT;
                ground(ctx, x, y, kind);
                ctx.markMadeGround(x, y);
            }
        }
        fence(leaf, ctx);
    }

    /**
     * Industrial fence a cell in from the lot's edge, with a three-cell gate
     * through the middle of every side, so the lot never closes a way through
     * the block.
     */
    private static void fence(BlockLeaf leaf, GenContext ctx) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return;
        int l = leaf.left + 1, t = leaf.top + 1, r = leaf.right - 1, b = leaf.bottom - 1;
        for (int y = t; y <= b; y++) {
            for (int x = l; x <= r; x++) {
                if (x != l && x != r && y != t && y != b) continue;
                boolean side = x == l || x == r, end = y == t || y == b;
                if ((side && !end && Math.abs(y - leaf.centerY()) <= 1)
                        || (end && !side && Math.abs(x - leaf.centerX()) <= 1)) continue;
                if (PlacementGuards.touchesEdgeBarrier(ctx.grid, x, y) || PlacementGuards.touchesDoorway(ctx.grid, x, y)
                        || PlacementGuards.wouldStrandGround(ctx.grid, x, y)) continue;
                // Corner names follow the art's north-up frame, in which the map's top row is south.
                String piece = x == l && y == t ? "corner-sw" : x == r && y == t ? "corner-se"
                        : x == l && y == b ? "corner-nw" : x == r && y == b ? "corner-ne"
                        : end ? "straight-h" : "straight-v";
                DoodadDef def = registry.doodad("doodad.industrial-fence-" + piece);
                if (def == null) continue;
                ctx.grid.setWalkable(x, y, false);
                ctx.grid.setSeeThrough(x, y, true);
                ctx.topology.setFixture(x, y, true);
                ctx.doodads.add(new Doodad(x, y, def));
            }
        }
    }

    // ---- ruins ------------------------------------------------------------------------------

    /**
     * The footprint of a burned-out building a cell in from the lot's edge: its
     * outline part standing wall, part rubble stub, part gone; its floor still
     * down, a collapsed corner heaped inside, wrecked furniture where it fell.
     */
    private static void ruin(BlockLeaf leaf, GenContext ctx) {
        CellTopology topology = ctx.topology;
        NavigationGrid grid = ctx.grid;
        Random rng = ctx.rng;
        String wall = RUIN_WALLS[rng.nextInt(RUIN_WALLS.length)];
        String floor = RUIN_FLOORS[rng.nextInt(RUIN_FLOORS.length)];
        int floorSurface = topology.surfaceIndex(floor);
        int l = leaf.left + 1, t = leaf.top + 1, r = leaf.right - 1, b = leaf.bottom - 1;
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                boolean inside = x >= l && x <= r && y >= t && y <= b;
                if (inside) {
                    topology.setGroundKind(x, y, GroundKind.COURTYARD);
                    topology.setSurface(x, y, floorSurface);
                } else {
                    ground(ctx, x, y, GroundKind.DIRT);
                }
                ctx.markMadeGround(x, y);
            }
        }

        // Walk the outline once round, deciding each cell: standing, stub or gone.
        List<int[]> ring = outline(l, t, r, b);
        char[] fate = outlineFates(ring.size(), rng);
        int wallSurface = topology.surfaceIndex(wall);
        List<int[]> stubs = new ArrayList<>();
        for (int i = 0; i < ring.size(); i++) {
            int x = ring.get(i)[0], y = ring.get(i)[1];
            if (fate[i] == 'W' && !PlacementGuards.touchesDoorway(grid, x, y)
                    && !PlacementGuards.touchesEdgeBarrier(grid, x, y) && !PlacementGuards.wouldStrandGround(grid, x, y)) {
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, false);
                topology.setSurface(x, y, wallSurface);
                topology.setWallGroundSurface(x, y, floor);
            } else if (fate[i] != '.') {
                ground(ctx, x, y, GroundKind.RUBBLE);
                stubs.add(ring.get(i));
            }
        }
        // A stub shows a face wherever the old wall met open air, as a felled wall does.
        for (int[] s : stubs) {
            int x = s[0], y = s[1], faces = 0;
            if (exposed(grid, topology, x, y + 1)) faces |= CellTopology.WALL_DIR_N;
            if (exposed(grid, topology, x + 1, y)) faces |= CellTopology.WALL_DIR_E;
            if (exposed(grid, topology, x, y - 1)) faces |= CellTopology.WALL_DIR_S;
            if (exposed(grid, topology, x - 1, y)) faces |= CellTopology.WALL_DIR_W;
            topology.setWallRemnant(x, y, new WallRemnant(wall, GroundKind.COURTYARD, floor, faces));
        }

        // One corner fell in: a heap of rubble against it, with scrap through it.
        boolean east = rng.nextBoolean(), south = rng.nextBoolean();
        int size = 2 + rng.nextInt(2);
        for (int dy = 0; dy < size; dy++) {
            for (int dx = 0; dx < size - dy; dx++) {
                int x = east ? r - 1 - dx : l + 1 + dx, y = south ? b - 1 - dy : t + 1 + dy;
                ground(ctx, x, y, GroundKind.RUBBLE);
            }
        }
        placeLoose(ctx, SCRAP, east ? r - 1 : l + 1, south ? b - 1 : t + 1);
        if (size == 3) placeLoose(ctx, SCRAP, east ? r - 2 : l + 2, south ? b - 1 : t + 1);

        // The rooms' furniture, wrecked where it stood, away from the heap.
        int pieces = 2 + rng.nextInt(3);
        for (int i = 0; i < pieces; i++) {
            int x = l + 2 + rng.nextInt(Math.max(1, r - l - 3)), y = t + 2 + rng.nextInt(Math.max(1, b - t - 3));
            if (topology.getGroundKind(x, y) == GroundKind.RUBBLE) continue;
            placeLoose(ctx, WRECKAGE[rng.nextInt(WRECKAGE.length)], x, y);
        }
    }

    /**
     * What becomes of each cell round a ruin's outline: {@code 'W'} still
     * standing, {@code 's'} a rubble stub, {@code '.'} gone. Standing runs are
     * one to {@link #MAX_STANDING_RUN} long and never meet another, so no
     * enclosure survives; about a third of the outline stands.
     */
    static char[] outlineFates(int length, Random rng) {
        char[] fate = new char[length];
        int i = 0;
        while (i < length) {
            int standing = 1 + rng.nextInt(MAX_STANDING_RUN);
            for (int k = 0; k < standing && i < length; k++) fate[i++] = 'W';
            int down = 2 + rng.nextInt(4);
            for (int k = 0; k < down && i < length; k++) fate[i++] = rng.nextInt(4) == 0 ? '.' : 's';
        }
        // The outline is a loop: the last run must not meet the first.
        if (length > 1 && fate[length - 1] == 'W') fate[length - 1] = 's';
        return fate;
    }

    /** The outline of a rectangle, once round in order. */
    static List<int[]> outline(int l, int t, int r, int b) {
        List<int[]> out = new ArrayList<>();
        for (int x = l; x <= r; x++) out.add(new int[]{x, t});
        for (int y = t + 1; y <= b; y++) out.add(new int[]{r, y});
        for (int x = r - 1; x >= l; x--) out.add(new int[]{x, b});
        for (int y = b - 1; y > t; y--) out.add(new int[]{l, y});
        return out;
    }

    /** A lot too small for a footprint: a heap of rubble with scrap and wreckage on it. */
    private static void rubbleHeap(BlockLeaf leaf, GenContext ctx) {
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                boolean margin = x == leaf.left || x == leaf.right || y == leaf.top || y == leaf.bottom;
                ground(ctx, x, y, margin ? GroundKind.DIRT : GroundKind.RUBBLE);
                ctx.markMadeGround(x, y);
            }
        }
        int pieces = 2 + ctx.rng.nextInt(3);
        for (int i = 0; i < pieces; i++) {
            int x = leaf.left + ctx.rng.nextInt(leaf.width()), y = leaf.top + ctx.rng.nextInt(leaf.height());
            placeLoose(ctx, i == 0 ? SCRAP : WRECKAGE[ctx.rng.nextInt(WRECKAGE.length)], x, y);
        }
    }

    /** Outdoor ground of a kind, drawn as that kind: a surface an earlier stage left would otherwise win. */
    private static void ground(GenContext ctx, int x, int y, GroundKind kind) {
        ctx.topology.setGroundKind(x, y, kind);
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        String block = mapping == null ? null : mapping.groundBlockId(kind);
        if (block != null) ctx.topology.setSurface(x, y, ctx.topology.surfaceIndex(block));
    }

    private static boolean exposed(NavigationGrid grid, CellTopology topology, int x, int y) {
        return topology.inBounds(x, y) && grid.isWalkable(x, y) && topology.getWallRemnant(x, y) == null;
    }

    /** Loose debris: drawn on the ground, nothing a body cannot step over. */
    private static void placeLoose(GenContext ctx, String id, int x, int y) {
        TileRegistry registry = TileRegistry.installed();
        DoodadDef def = registry == null ? null : registry.doodad(id);
        if (def == null || !ctx.grid.isWalkable(x, y)) return;
        ctx.doodads.add(new Doodad(x, y, def));
    }

    private static int[] natureIndices(String[] ids) {
        TileRegistry registry = TileRegistry.installed();
        if (registry == null) return new int[0];
        List<Integer> out = new ArrayList<>();
        for (String id : ids) if (registry.has(id)) out.add(registry.indexOf(id));
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
}
