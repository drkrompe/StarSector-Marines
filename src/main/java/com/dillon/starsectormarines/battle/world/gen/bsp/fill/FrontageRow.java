package com.dillon.starsectormarines.battle.world.gen.bsp.fill;

import com.dillon.starsectormarines.battle.world.gen.BlockLeaf;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.MapDistrictTheme;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A lot cut along its street frontage into a row of narrow buildings ({@code town-plan.md}): shopfronts or
 * terraced houses side by side, each pair sharing one party wall, their doors and windows on the street and on
 * the yard behind, never on the flanks. A lot deep enough, with a street behind it as well, takes two rows back
 * to back with an alley between them; otherwise the ground behind the row is its back yards. The row covers the
 * whole frontage, so the street reads as one continuous wall of narrow fronts rather than one large building
 * set in pavement.
 *
 * <p>{@link #plan} is the rule and draws only plot widths from the random stream; {@link #place} carves it.
 * A lot too short for two plots, or too shallow for one, is refused before anything changes.
 */
final class FrontageRow {

    /** Least width of the alley between two rows built back to back. */
    static final int ALLEY = 2;

    /**
     * How a district builds its frontage: plot widths along the street, the front garden between street and
     * front wall, the building depth, and the least yard worth leaving behind a row.
     */
    record Character(int minWidth, int maxWidth, int setback, int minDepth, int maxDepth, int yard) {
        Character {
            if (minWidth < 4 || maxWidth < minWidth || minDepth < 4 || maxDepth < minDepth || setback < 0 || yard < 1) {
                throw new IllegalArgumentException("a plot needs a hollow shell: " + this);
            }
            // Three of the narrowest must fit where two of the widest end, or some frontages hold no row.
            if (3 * (minWidth - 1) > 2 * (maxWidth - 1) + 1) {
                throw new IllegalArgumentException("plot widths leave frontages no row can cover: " + this);
            }
        }

        /** The front garden on a lot {@code lotDepth} deep: none where the lot holds a building and no more. */
        int setbackOn(int lotDepth) {
            return lotDepth - setback >= minDepth ? setback : 0;
        }

        /** The depth of a row's buildings on a lot {@code lotDepth} deep, or -1 when none fits. */
        int rowDepth(int lotDepth) {
            int room = lotDepth - setbackOn(lotDepth);
            if (room < minDepth) return -1;
            if (room <= maxDepth) return room;
            return Math.max(minDepth, Math.min(maxDepth, room - yard));
        }

        /** Whether a lot {@code lotDepth} deep holds two rows back to back with the alley between. */
        boolean backToBack(int lotDepth) {
            return lotDepth >= 2 * (setback + minDepth) + ALLEY;
        }
    }

    /** Shops on an ordinary commercial lot: narrow fronts straight onto the street, a service yard behind. */
    static final Character SHOPS = new Character(6, 9, 0, 5, 10, 2);
    /** Terraced houses: narrow fronts behind a one-cell front garden where the lot has room, gardens behind. */
    static final Character TERRACE = new Character(5, 7, 1, 5, 9, 3);
    /** Semi-detached pairs and wide villas in a mixed town: broader plots, a deeper front garden. */
    static final Character VILLAS = new Character(8, 11, 2, 6, 9, 3);
    /** Main street shopfronts behind the district's own pavement: the narrowest, most rhythmic fronts. */
    static final Character SHOPFRONTS = new Character(5, 7, 0, 5, 10, 3);

    /**
     * One building of a row in the lot's own frame: {@code along} runs with the street, {@code depth} from the
     * front edge inward. {@code back} is a building of the rear row, which faces the street behind the lot.
     */
    record Plot(int alongLo, int alongHi, int depthLo, int depthHi, boolean back) { }

    private FrontageRow() { }

    /** The character a district builds homes with, or null where homes stand alone on their lots. */
    static Character forHomes(MapDistrictTheme theme) {
        if (theme == null) return null;
        return switch (theme) {
            case RESIDENTIAL, CIVIC -> TERRACE;
            case MIXED, HARBOR_PORT -> VILLAS;
            default -> null;
        };
    }

    /** The character a district builds shops with, or null where a shop stands alone on its lot. */
    static Character forShops(MapDistrictTheme theme) {
        if (theme == null) return null;
        return switch (theme) {
            case RESIDENTIAL, CIVIC, MIXED, HARBOR_PORT, INDUSTRIAL, WATERFRONT -> SHOPS;
            default -> null;
        };
    }

    /**
     * The plot spans along a frontage {@code length} cells long, {@code {first, last}} each. Neighbours share
     * their party wall, so each span starts on the last cell of the one before, the first starts on 0 and the
     * last ends on {@code length - 1}. Every span is {@code min}..{@code max} cells. Null when fewer than two
     * plots fit, since one plot is an ordinary building.
     */
    static int[][] spans(int length, int min, int max, Random rng) {
        int fewest = Math.max(2, ceilDiv(length - 1, max - 1)), most = (length - 1) / (min - 1);
        if (fewest > most) return null;
        int target = (min + max + 1) / 2;
        int n = Math.max(fewest, Math.min(most, Math.round((length - 1) / (float) (target - 1))));
        int cells = length + n - 1;
        int[] widths = new int[n];
        int base = cells / n, extra = cells % n;
        for (int i = 0; i < n; i++) widths[i] = base;
        // The spare cells go to plots chosen at random, then neighbours trade a cell, so the fronts are
        // irregular as a real street is rather than ruled out evenly.
        for (int given = 0; given < extra; ) {
            int i = rng.nextInt(n);
            if (widths[i] == base) { widths[i]++; given++; }
        }
        for (int i = 0; i + 1 < n; i++) {
            int shift = rng.nextInt(3) - 1;
            if (widths[i] + shift < min || widths[i] + shift > max
                    || widths[i + 1] - shift < min || widths[i + 1] - shift > max) continue;
            widths[i] += shift;
            widths[i + 1] -= shift;
        }
        int[][] spans = new int[n][];
        int x = 0;
        for (int i = 0; i < n; i++) {
            spans[i] = new int[]{x, x + widths[i] - 1};
            x += widths[i] - 1;
        }
        return spans;
    }

    /**
     * The row, or rows, on a lot {@code length} along its street and {@code depth} deep. {@code streetBehind}
     * says the lot's far edge is a street too, which a rear row needs to front onto. Empty when no row fits.
     */
    static List<Plot> plan(int length, int depth, Character ch, boolean streetBehind, Random rng) {
        List<Plot> plots = new ArrayList<>();
        boolean twoRows = streetBehind && ch.backToBack(depth);
        int setback = twoRows ? ch.setback() : ch.setbackOn(depth);
        int rowDepth = twoRows
                ? Math.min(ch.maxDepth(), (depth - ALLEY) / 2 - setback)
                : ch.rowDepth(depth);
        if (rowDepth < 0) return plots;
        int[][] front = spans(length, ch.minWidth(), ch.maxWidth(), rng);
        if (front == null) return plots;
        for (int[] s : front) {
            plots.add(new Plot(s[0], s[1], setback, setback + rowDepth - 1, false));
        }
        if (!twoRows) return plots;
        int[][] rear = spans(length, ch.minWidth(), ch.maxWidth(), rng);
        for (int[] s : rear) {
            plots.add(new Plot(s[0], s[1], depth - setback - rowDepth, depth - setback - 1, true));
        }
        return plots;
    }

    /**
     * Carves the row on {@code lot}, its front onto {@code front}: the buildings, the front gardens with a
     * path to each front door, and the yards or alley behind. Returns false, having changed nothing, when no
     * row fits or {@code ch} is null (a district that builds no rows).
     */
    static boolean place(BlockLeaf lot, GenContext c, BuildingPlacement.Side front, Character ch,
                         BuildingShellCore.BuildingConfig config, boolean streetBehind) {
        if (ch == null) return false;
        Frame f = new Frame(lot, front);
        List<Plot> plots = plan(f.length, f.depth, ch, streetBehind, c.rng);
        if (plots.isEmpty()) return false;
        boolean home = config.layoutRecipe == BuildingLayouts.LayoutRecipe.HOME;
        // Open ground first: gardens and yards for homes, paved yards for shops, the alley paved for either.
        int frontRow = plots.get(0).depthHi(), rearRow = f.depth;
        for (Plot p : plots) if (p.back()) rearRow = Math.min(rearRow, p.depthLo());
        for (int k = 0; k < f.depth; k++) {
            boolean alley = rearRow < f.depth && k > frontRow && k < rearRow;
            GroundKind ground = alley ? GroundKind.COURTYARD : home ? GroundKind.GRASS : GroundKind.COURTYARD;
            for (int a = 0; a < f.length; a++) {
                int x = f.x(a, k), y = f.y(a, k);
                c.grid.setWalkableFloor(x, y);
                c.topology.setGroundKind(x, y, ground);
                c.markMadeGround(x, y);
            }
        }
        // Shopfronts alternate their two styles along the row, so each front reads as its own shop, roof and all.
        BuildingStyle[] styles = BuildingStyle.pair(config.buildingKind);
        int first = styles == null ? 0 : c.rng.nextInt(styles.length);
        for (int i = 0; i < plots.size(); i++) {
            Plot p = plots.get(i);
            int[] a = {f.x(p.alongLo(), p.depthLo()), f.y(p.alongLo(), p.depthLo())};
            int[] b = {f.x(p.alongHi(), p.depthHi()), f.y(p.alongHi(), p.depthHi())};
            BlockLeaf shell = new BlockLeaf(Math.min(a[0], b[0]), Math.min(a[1], b[1]),
                    Math.max(a[0], b[0]), Math.max(a[1], b[1]), false);
            BuildingPlacement.Side facing = p.back() ? front.opposite() : front;
            PointOfInterest poi = BuildingShellCore.carve(shell, c.grid, c.topology, c.doodads, c.rng, config,
                    BuildingPlacement.terraced(facing));
            if (poi != null) c.pois.add(poi);
            if (styles != null && !home) {
                styles[(first + i) % styles.length].paintShell(c.grid, c.topology,
                        shell.left, shell.top, shell.right, shell.bottom);
            }
            if (p.back() ? p.depthHi() < f.depth - 1 : p.depthLo() > 0) frontPaths(c, f, p);
        }
        return true;
    }

    /** A paved path across the front garden from each front door to the street. */
    private static void frontPaths(GenContext c, Frame f, Plot p) {
        int wall = p.back() ? p.depthHi() : p.depthLo(), step = p.back() ? 1 : -1;
        int edge = p.back() ? f.depth - 1 : 0;
        for (int a = p.alongLo() + 1; a < p.alongHi(); a++) {
            if (!c.grid.isDoorway(f.x(a, wall), f.y(a, wall))) continue;
            for (int k = wall + step; p.back() ? k <= edge : k >= edge; k += step) {
                int x = f.x(a, k), y = f.y(a, k);
                c.topology.setGroundKind(x, y, GroundKind.COURTYARD);
                c.topology.setSurface(x, y, c.topology.surfaceIndex("colony.terracotta"));
            }
        }
    }

    /** The lot in its street's frame: {@code along} with the frontage, {@code depth} in from the front edge. */
    private static final class Frame {
        final BlockLeaf lot;
        final BuildingPlacement.Side front;
        final int length, depth;

        Frame(BlockLeaf lot, BuildingPlacement.Side front) {
            this.lot = lot;
            this.front = front;
            boolean acrossX = front == BuildingPlacement.Side.TOP || front == BuildingPlacement.Side.BOTTOM;
            this.length = acrossX ? lot.width() : lot.height();
            this.depth = acrossX ? lot.height() : lot.width();
        }

        int x(int along, int k) {
            return switch (front) {
                case TOP, BOTTOM -> lot.left + along;
                case LEFT -> lot.left + k;
                case RIGHT -> lot.right - k;
            };
        }

        int y(int along, int k) {
            return switch (front) {
                case TOP -> lot.top + k;
                case BOTTOM -> lot.bottom - k;
                case LEFT, RIGHT -> lot.top + along;
            };
        }
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
