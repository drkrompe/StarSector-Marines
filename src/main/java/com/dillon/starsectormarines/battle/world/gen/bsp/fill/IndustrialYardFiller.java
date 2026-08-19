package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.BlockFiller;
import com.dillon.starsectormarines.battle.world.gen.BlockKind;
import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.HashSet;
import java.util.Set;

/**
 * Fills an {@link BlockKind#INDUSTRIAL_YARD} as an authored work yard rather
 * than a random prop scatter. A seed selects one of three semantic grammars:
 * freight staging, maintenance/service, or salvage. Each grammar keeps its
 * props in perimeter storage bands and leaves the long central aisle clear.
 * Mirroring and long-axis rotation provide variation without breaking the
 * relationships that make each lot readable.
 */
public final class IndustrialYardFiller implements BlockFiller {

    private static final String CRATES    = "doodad.industrial-crate-stack";
    private static final String DRUMS     = "doodad.industrial-drum-cluster";
    private static final String CABLE     = "doodad.industrial-cable-reel";
    private static final String GENERATOR = "doodad.industrial-generator";
    private static final String DUMPSTER  = "doodad.industrial-dumpster";
    private static final String PIPES     = "doodad.industrial-pipe-bundle";
    private static final String PALLETS   = "doodad.industrial-pallet-stack";
    private static final String SCRAP     = "doodad.industrial-scrap-pile";
    private static final String TANK      = "doodad.industrial-fluid-tank";

    @Override
    public BlockKind kind() { return BlockKind.INDUSTRIAL_YARD; }

    @Override
    public void fill(BlockLeaf leaf, GenContext ctx) {
        paintOpenDirt(leaf, ctx.grid, ctx.topology);
        furnishArea(leaf, ctx, false, ctx.rng.nextInt(4));
    }

    /** Reusable authored yard furnishing for compound-owned inset yards. */
    static void furnishArea(BlockLeaf area, GenContext ctx, boolean tactical, int grammar) {
        YardLayout yard = new YardLayout(area, ctx, tactical);
        boolean mirror = ctx.rng.nextBoolean();
        switch (grammar) {
            case 0: freightStaging(yard, mirror); break;
            case 1: maintenanceBay(yard, mirror); break;
            case 2: salvageLot(yard, mirror); break;
            default: tankFarm(yard, mirror); break;
        }
    }

    private static void freightStaging(YardLayout y, boolean mirror) {
        y.paintPad(1, 6, false, mirror);
        y.paintPad(4, 6, true, mirror);
        y.place(CRATES,   1, 6, false, mirror);
        y.place(DRUMS,    2, 6, false, mirror);
        y.place(DUMPSTER, 5, 6, false, mirror);
        y.place(PALLETS,  3, 6, true,  mirror);
        y.place(PIPES,    5, 6, true,  mirror);
    }

    private static void maintenanceBay(YardLayout y, boolean mirror) {
        y.paintPad(2, 6, false, mirror);
        y.place(GENERATOR, 2, 6, false, mirror);
        y.place(CABLE,     3, 6, false, mirror);
        y.place(DRUMS,     4, 6, false, mirror);
        y.place(PIPES,     2, 6, true,  mirror);
        y.place(CRATES,    4, 6, true,  mirror);
        y.place(DUMPSTER,  5, 6, true,  mirror);
    }

    private static void salvageLot(YardLayout y, boolean mirror) {
        int[] scrapA = y.place(SCRAP,     1, 6, false, mirror);
        int[] scrapB = y.place(SCRAP,     2, 6, false, mirror);
        y.paintRubble(scrapA);
        y.paintRubble(scrapB);
        y.place(DUMPSTER,  5, 6, false, mirror);
        y.place(PALLETS,   1, 6, true,  mirror);
        y.place(CABLE,     3, 6, true,  mirror);
        y.place(DRUMS,     4, 6, true,  mirror);
        y.place(CRATES,    5, 6, true,  mirror);
    }

    private static void tankFarm(YardLayout y, boolean mirror) {
        y.paintPad(1, 6, false, mirror);
        y.paintPad(3, 6, false, mirror);
        y.paintPad(5, 6, false, mirror);
        y.place(TANK,      1, 6, false, mirror);
        y.place(TANK,      3, 6, false, mirror);
        y.place(TANK,      5, 6, false, mirror);
        y.place(PIPES,     2, 6, true,  mirror);
        y.place(DRUMS,     4, 6, true,  mirror);
        y.place(GENERATOR, 5, 6, true,  mirror);
    }

    static void paintOpenDirt(BlockLeaf leaf, NavigationGrid grid,
                              CellTopology topology) {
        for (int y = leaf.top; y <= leaf.bottom; y++) {
            for (int x = leaf.left; x <= leaf.right; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
    }

    /** Canonical long-axis coordinates make the same grammar fit wide/tall BSP leaves. */
    private static final class YardLayout {
        private final BlockLeaf leaf;
        private final GenContext ctx;
        private final boolean horizontal;
        private final int length;
        private final int breadth;
        private final boolean tactical;
        private final Set<Long> occupied = new HashSet<>();

        YardLayout(BlockLeaf leaf, GenContext ctx, boolean tactical) {
            this.leaf = leaf;
            this.ctx = ctx;
            this.tactical = tactical;
            this.horizontal = leaf.width() >= leaf.height();
            this.length = horizontal ? leaf.width() : leaf.height();
            this.breadth = horizontal ? leaf.height() : leaf.width();
        }

        int[] place(String id, int numerator, int denominator,
                    boolean farSide, boolean mirror) {
            if (length <= 0 || breadth <= 0) return null;
            int u = fraction(numerator, denominator, length);
            if (mirror) u = length - 1 - u;
            int v = farSide ? breadth - 1 : 0;
            int[] cell = nearestFree(u, v);
            if (cell == null) return null;
            DoodadDef def = TileRegistry.installed().doodad(id);
            if (def == null) throw new IllegalStateException("Missing industrial doodad " + id);
            ctx.doodads.add(new Doodad(cell[0], cell[1], def));
            if (tactical) {
                ctx.grid.setWalkable(cell[0], cell[1], false);
                ctx.grid.setSeeThrough(cell[0], cell[1], !TANK.equals(id));
                ctx.topology.setWall(cell[0], cell[1], false);
                ctx.topology.setFixture(cell[0], cell[1], true);
            }
            occupied.add(key(cell[0], cell[1]));
            return cell;
        }

        void paintPad(int numerator, int denominator, boolean farSide, boolean mirror) {
            int center = fraction(numerator, denominator, length);
            if (mirror) center = length - 1 - center;
            int inward = farSide ? Math.max(0, breadth - 2) : Math.min(breadth - 1, 1);
            for (int du = 0; du <= 1; du++) {
                int u = Math.max(0, Math.min(length - 1, center + du));
                setGround(u, farSide ? breadth - 1 : 0, GroundKind.STRIPED);
                setGround(u, inward, GroundKind.STRIPED);
            }
        }

        void paintRubble(int[] cell) {
            if (cell != null) ctx.topology.setGroundKind(cell[0], cell[1], GroundKind.RUBBLE);
        }

        private int[] nearestFree(int preferredU, int v) {
            for (int distance = 0; distance < length; distance++) {
                int[] candidates = distance == 0
                        ? new int[]{preferredU}
                        : new int[]{preferredU - distance, preferredU + distance};
                for (int u : candidates) {
                    if (u < 0 || u >= length) continue;
                    int[] cell = world(u, v);
                    if (!occupied.contains(key(cell[0], cell[1]))) return cell;
                }
            }
            return null;
        }

        private void setGround(int u, int v, GroundKind kind) {
            int[] cell = world(u, v);
            ctx.topology.setGroundKind(cell[0], cell[1], kind);
        }

        private int[] world(int u, int v) {
            return horizontal
                    ? new int[]{leaf.left + u, leaf.top + v}
                    : new int[]{leaf.left + v, leaf.top + u};
        }

        private static int fraction(int numerator, int denominator, int size) {
            if (size <= 1) return 0;
            return Math.max(0, Math.min(size - 1,
                    Math.round((size - 1) * (numerator / (float) denominator))));
        }

        private static long key(int x, int y) {
            return ((long) x << 32) ^ (y & 0xFFFFFFFFL);
        }
    }
}
