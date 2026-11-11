package com.dillon.starsectormarines.battle.world.gen.hull;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.raid.TankFarm;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.BitSet;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * A free port's quarters in its town ({@code long-survey-maps.md}): on a story map whose patch names them
 * ({@link HullSitePlan#port}), the open lots inside each box are given over to the quarter's trade, by turns from the
 * largest clear piece down. A spaceport quarter's lots become container yards, each column of stacks straddled by its
 * gantry crane, or aprons of landing pads where two fit; a market's lots become rows of stalls, each an awning over its
 * goods with the shoppers' aisle in front.
 *
 * <p>Laid when the hull is finished, with the port's yards, so the claim lattice and the shower see the quarters as they
 * stand. Nothing is laid on a street or a cell off one, a building, a wall, a doorway, the hull's ground, or within
 * {@link MineCountry#STAKE_CLEAR} of a claim lattice point.
 */
public final class PortQuarter {

    /** A container yard's column: a crane leg, the span's clear cell, a stack four wide, two clear cells, a leg. */
    static final int COLUMN = 9;
    /** Stacks in a run down a column before a cross aisle, and the aisle's cells. */
    static final int RUN = 3, CROSS = 2;
    /** One stack in this many is an empty bay, so a yard is not a solid block. UNTUNED. */
    static final int EMPTY_BAY = 7;
    /** A stall's pitch along its row (an awning three wide and a gap), and from row to row (the stall, two of aisle). */
    static final int STALL_PITCH = 4, ROW_PITCH = 3;
    /** The narrowest clear piece a quarter takes, and the most pieces one box takes. */
    static final int SMALLEST = 8, MOST_PIECES = 10;
    /** The most clear pieces a box is searched for, thin ones passed over included. */
    static final int TRIES = 40;

    static final String[] STACKS = {"origin-freight.container-rust", "origin-freight.container-blue",
            "origin-freight.container-rust", "origin-freight.split-container", "origin-freight.container-blue"};
    static final String CRANE_SPAN = "origin-freight.gantry-span-clean", CRANE_LEG = "origin-freight.gantry-leg-clean";
    static final String[] AWNINGS = {"doodad.street-awning-striped", "doodad.street-awning-canvas",
            "doodad.street-awning-steel"};
    /** A stall's goods, under its awning: at its near end and its far end, cycled. */
    static final String[] GOODS = {"doodad.industrial-crate-stack", "doodad.industrial-pallet-stack",
            "doodad.industrial-drum-cluster"};
    /** A yard's ground, and a market's: concrete for the haulers, brick for the shoppers. */
    static final GroundKind YARD = GroundKind.COURTYARD, MARKET = GroundKind.BRICK;

    private final GenContext ctx;
    private final TileRegistry registry;
    private final int w, h;
    private final BitSet taken;
    private final IntPredicate open;

    private PortQuarter(GenContext ctx, TileRegistry registry, BitSet hull, BitSet trench, BitSet aprons,
                        int latticeX, int latticeY) {
        this.ctx = ctx;
        this.registry = registry;
        this.w = ctx.width;
        this.h = ctx.height;
        this.taken = new BitSet(w * h);
        boolean[][] roads = ctx.get(BspKeys.ROAD_RESERVATION);
        int s = ClaimLattice.SPACING, clear = MineCountry.STAKE_CLEAR;
        this.open = i -> {
            int x = i % w, y = i / w;
            if (x < 3 || y < 3 || x >= w - 3 || y >= h - 3) return false;
            if (taken.get(i) || hull.get(i) || trench.get(i) || aprons.get(i)) return false;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                if (roads != null && roads[x + dx][y + dy]) return false;
            }
            int lx = Math.floorMod(x - latticeX, s), ly = Math.floorMod(y - latticeY, s);
            if (Math.min(lx, s - lx) <= clear && Math.min(ly, s - ly) <= clear) return false;
            if (!ctx.grid.isWalkable(x, y) || ctx.grid.isDoorway(x, y) || ctx.topology.isWall(x, y)
                    || ctx.topology.isFixture(x, y) || ctx.topology.getBuildingId(x, y) != 0) return false;
            GroundKind g = ctx.topology.getGroundKind(x, y);
            return g != GroundKind.INDOOR && g != GroundKind.WATER && g != GroundKind.VOID && g != GroundKind.STREET
                    && g != GroundKind.SIDEWALK;
        };
    }

    /** Dresses {@code port}'s quarters on {@code ctx}; the claim lattice's grid is about {@code (latticeX, latticeY)}. */
    static void lay(GenContext ctx, List<HullSitePlan.Port> port, BitSet hull, BitSet trench, BitSet aprons,
                    int latticeX, int latticeY) {
        TileRegistry registry = TileRegistry.installed();
        if (port == null || port.isEmpty() || registry == null) return;
        PortQuarter quarter = new PortQuarter(ctx, registry, hull, trench, aprons, latticeX, latticeY);
        for (HullSitePlan.Port q : port) quarter.dress(q);
    }

    private void dress(HullSitePlan.Port q) {
        HullSitePlan.Box b = q.box();
        int x0 = Math.max(0, b.x0()), y0 = Math.max(0, b.y0()), x1 = Math.min(w - 1, b.x1()), y1 = Math.min(h - 1, b.y1());
        // By turns from the largest clear piece down; a piece too thin for any trade is passed over, not the end.
        for (int piece = 0, tries = 0; piece < MOST_PIECES && tries < TRIES; tries++) {
            int[] r = TankFarm.largestClear(w, x0, y0, x1, y1, open);
            if (r == null) break;
            boolean thin = r[2] - r[0] + 1 < SMALLEST || r[3] - r[1] + 1 < SMALLEST;
            if (thin) {
                if ((long) (r[2] - r[0] + 1) * (r[3] - r[1] + 1) < (long) SMALLEST * SMALLEST) break;
            } else {
                boolean laid = false;
                switch (q.kind()) {
                    case PADS -> laid = PortYards.pads(ctx, registry, r[0], r[1], r[2], r[3], open, false, 1)
                            || containers(r);
                    case CONTAINERS -> laid = containers(r);
                    case STALLS -> laid = stalls(r);
                }
                if (laid) piece++;
            }
            // A laid piece keeps a cell clear round it; a thin one is only struck off.
            int pad = thin ? 0 : 1;
            for (int y = r[1] - pad; y <= r[3] + pad; y++) {
                for (int x = r[0] - pad; x <= r[2] + pad; x++) if (x >= 0 && y >= 0 && x < w && y < h) taken.set(y * w + x);
            }
        }
    }

    /**
     * A container yard on the piece: as many columns as fit across it, each a run of stacks down its middle with a
     * cross aisle every {@link #RUN} stacks, and a gantry crane straddling it halfway down, its legs in the aisles. The
     * columns run down the piece's longer way where both fit, else whichever way a column fits; false when none does.
     */
    private boolean containers(int[] r) {
        int wide = r[2] - r[0] + 1, tall = r[3] - r[1] + 1;
        // Columns run along y (stacks laid along x) unless only the other way fits, or it is the piece's long way.
        boolean alongX = wide < COLUMN || (tall >= COLUMN && wide > tall);
        int span = alongX ? tall : wide, length = alongX ? wide : tall;
        int across = span / COLUMN;
        if (across == 0 || length < 2) return false;
        pave(r, YARD);
        int turns = alongX ? 1 : 0;
        int o = (alongX ? r[1] : r[0]) + (span - across * COLUMN) / 2, start = alongX ? r[0] : r[1];
        int n = 0;
        for (int i = 0; i < across; i++) {
            int c = o + i * COLUMN, run = 0;
            for (int a = start; a + 1 < start + length; ) {
                int[] at = cell(alongX, a, c + 2);
                if (Math.floorMod(at[0] * 31 + at[1] * 17, EMPTY_BAY) != 0) solid(STACKS[n++ % STACKS.length], at[0], at[1], turns);
                a += 2;
                if (++run == RUN) {
                    run = 0;
                    a += CROSS;
                }
            }
            int depth = 3;
            DoodadDef span3 = registry.doodad(CRANE_SPAN), leg = registry.doodad(CRANE_LEG);
            if (span3 == null || leg == null || length < depth) continue;
            int a0 = start + (length - depth) / 2;
            for (int lc : new int[]{c, c + COLUMN - 1}) {
                for (int a = a0; a < a0 + depth; a++) {
                    int[] at = cell(alongX, a, lc);
                    fixture(at[0], at[1]);
                }
                int[] at = cell(alongX, a0, lc);
                ctx.doodads.add(new Doodad(at[0], at[1], leg, turns));
            }
            int[] at = cell(alongX, a0, c + 1);
            ctx.doodads.add(new Doodad(at[0], at[1], span3, turns));
        }
        return true;
    }

    /** The cell {@code a} along a yard's columns and {@code c} across them. */
    private static int[] cell(boolean alongX, int a, int c) {
        return alongX ? new int[]{a, c} : new int[]{c, a};
    }

    /**
     * A market on the piece: rows of stalls across it, each an awning over its goods at either end with the middle
     * left as its counter, and two cells of aisle between one row and the next.
     */
    private boolean stalls(int[] r) {
        int across = (r[2] - r[0] + 2) / STALL_PITCH;
        if (across == 0) return false;
        pave(r, MARKET);
        int ox = r[0] + (r[2] - r[0] + 2 - across * STALL_PITCH) / 2;
        int n = 0;
        for (int y = r[1] + 1; y <= r[3] - 1; y += ROW_PITCH) {
            for (int i = 0; i < across; i++) {
                int x = ox + i * STALL_PITCH;
                solid(GOODS[n % GOODS.length], x, y, 0);
                solid(GOODS[(n + 1) % GOODS.length], x + 2, y, 0);
                DoodadDef awning = registry.doodad(AWNINGS[n % AWNINGS.length]);
                if (awning != null) ctx.doodads.add(new Doodad(x, y, awning));
                n++;
            }
        }
        return true;
    }

    /** The piece paved as the quarter's made ground, the loose things on it cleared. */
    private void pave(int[] r, GroundKind kind) {
        ctx.doodads.removeIf(d -> !ctx.topology.isFixture(d.cellX, d.cellY) && d.cellX <= r[2]
                && d.cellX + Math.max(1, d.footprintCellsX) - 1 >= r[0] && d.cellY <= r[3]
                && d.cellY + Math.max(1, d.footprintCellsY) - 1 >= r[1]);
        for (int y = r[1]; y <= r[3]; y++) {
            for (int x = r[0]; x <= r[2]; x++) {
                ctx.topology.setGroundKind(x, y, kind);
                ctx.topology.setSurface(x, y, 0);
                ctx.topology.setNatureOverlayIndex(x, y, -1);
                ctx.markMadeGround(x, y);
            }
        }
    }

    /** One standing piece from {@code (x, y)}, solid where it is cover. */
    private void solid(String id, int x, int y, int turns) {
        DoodadDef def = registry.doodad(id);
        if (def == null) return;
        Doodad d = new Doodad(x, y, def, turns);
        if (def.cover == DoodadCover.MED || def.cover == DoodadCover.HEAVY) {
            for (int yy = y; yy < y + d.footprintCellsY; yy++) for (int xx = x; xx < x + d.footprintCellsX; xx++) fixture(xx, yy);
        }
        ctx.doodads.add(d);
    }

    private void fixture(int x, int y) {
        ctx.grid.setWalkable(x, y, false);
        ctx.grid.setSeeThrough(x, y, true);
        ctx.topology.setFixture(x, y, true);
    }
}
