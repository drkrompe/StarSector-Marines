package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Arrays;

/**
 * The ground a fire lit at one cell could come to (`fire-nouns.md`, Starting
 * fires): the fuel round the lit cell, then every cell of fuel within
 * {@link #JUMP} of fuel already reached, since heat crosses a gap that narrow,
 * out to {@link #RADIUS} of the lit cell, and no further from where it was lit
 * than the cell's fuel carries a fire (`fire-nouns.md`, Spread), counted as the
 * field counts it. A cell is threatened when it is
 * reached or stands beside reached ground. An incendiary carrier throws only
 * where none of its own side is threatened: a meadow fire lit in the meadow you
 * stand in comes to you.
 *
 * <p>Fuel already burning counts as reached ground; burnt and bare ground does
 * not, which is what makes a road or a burnt patch a place to throw from.
 */
public final class FireReach {

    /** {@code -Dbattle.fire.throwSafety=false} is the control: incendiaries are thrown as frags are, by the blast alone. */
    public static final String PROPERTY = "battle.fire.throwSafety";
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));

    /** Chebyshev cells beyond the lit ground the reach is followed to. */
    public static final int RADIUS = 10;
    /** The widest gap in fuel, in cells, that heat is taken to cross: the field's reach in anything short of a strong wind. */
    public static final int JUMP = 3;
    /** Chebyshev cells round the lit cell that a thrown incendiary itself sets alight. */
    public static final int LIT = 2;

    private final boolean[] reached;
    private final int originX, originY, span;

    private FireReach(int originX, int originY, int span) {
        this.originX = originX;
        this.originY = originY;
        this.span = span;
        this.reached = new boolean[span * span];
    }

    /**
     * The ground a blast of this radius may set alight, scatter included, in Chebyshev cells round its aim point:
     * the cells whose centre the blast can cover, never less than one.
     */
    public static int litCells(float aoeRadius, float scatter) {
        return Math.max(1, (int) Math.floor(aoeRadius + scatter));
    }

    public static boolean enabled() { return ENABLED; }

    /** The reach of a fire a thrown incendiary lights at a cell on this field. */
    public static FireReach of(FireField field, int x, int y) {
        return of(field, x, y, LIT);
    }

    /** The reach of a fire lit over every cell within {@code lit} Chebyshev cells of one ({@link #litCells}). */
    public static FireReach of(FireField field, int x, int y, int lit) {
        int half = lit + RADIUS;
        int span = half * 2 + 1;
        FireReach reach = new FireReach(x - half, y - half, span);
        // Each cell's distance from the ignition as the field counts it (FireField.catchFire): a hop adds its
        // straight-line length in quarter cells, a cell catches only within its own seeded carry, and a cell of fuel
        // that carries a fire as far as it stands lights afresh at nought.
        int[] distance = new int[span * span];
        Arrays.fill(distance, Integer.MAX_VALUE);
        int[] queue = new int[span * span];
        boolean[] queued = new boolean[span * span];
        int head = 0, size = 0;
        for (int dy = -lit; dy <= lit; dy++) {
            for (int dx = -lit; dx <= lit; dx++) {
                int local = (half + dy) * span + half + dx;
                int from = startingDistance(field, x + dx, y + dy);
                if (from == NONE) continue;
                distance[local] = from;
                queued[local] = true;
                queue[(head + size++) % queue.length] = local;
            }
        }
        while (size > 0) {
            int local = queue[head];
            head = (head + 1) % queue.length;
            size--;
            queued[local] = false;
            int lx = local % span, ly = local / span;
            int base = carriesOnward(field, reach.originX + lx, reach.originY + ly) ? distance[local] : 0;
            for (int ny = Math.max(0, ly - JUMP); ny <= Math.min(span - 1, ly + JUMP); ny++) {
                for (int nx = Math.max(0, lx - JUMP); nx <= Math.min(span - 1, lx + JUMP); nx++) {
                    int next = ny * span + nx;
                    int gx = reach.originX + nx, gy = reach.originY + ny;
                    if (!fuel(field, gx, gy)) continue;
                    int d = base + (int) Math.round(FireField.QUARTERS * Math.hypot(nx - lx, ny - ly));
                    int mask = field.fuelMaskAt(gx, gy);
                    int limit = field.isBurning(gx, gy) || building(mask) ? Integer.MAX_VALUE
                            : field.spreadLimitAt(gy * field.width() + gx, mask);
                    if (d >= distance[next] || d > limit) continue;
                    distance[next] = d;
                    if (!queued[next]) {
                        queued[next] = true;
                        queue[(head + size++) % queue.length] = next;
                    }
                }
            }
        }
        for (int i = 0; i < distance.length; i++) reach.reached[i] = distance[i] != Integer.MAX_VALUE;
        return reach;
    }

    /** No fire starts here: neither burning nor able to burn. */
    private static final int NONE = Integer.MIN_VALUE;

    /** A lit cell's distance: nought for fuel the blast sets alight, the field's own for one already burning (below nought in a blaze); {@link #NONE} for none. */
    private static int startingDistance(FireField field, int x, int y) {
        if (field.isBurning(x, y)) return field.distanceAt(y * field.width() + x);
        return field.canBurn(x, y) ? 0 : NONE;
    }

    /** Whether a cell's fire keeps counting its distance onward, rather than lighting afresh as creep and buildings do. */
    private static boolean carriesOnward(FireField field, int x, int y) {
        int mask = field.fuelMaskAt(x, y);
        return FireFuel.spread(mask) < FireFuel.UNLIMITED && !building(mask);
    }

    /**
     * A building's walls and floors, taken as carrying a fire as far as they stand: a room flashes over whole and
     * its walls burn in the heat of all of it, which no count from where the charge landed foresees.
     */
    private static boolean building(int mask) {
        return FireFuel.FURNISHING.in(mask) || FireFuel.STRUCTURE.in(mask);
    }

    /** Whether a cell is reached, or beside reached ground. */
    public boolean threatens(int x, int y) {
        int lx = x - originX, ly = y - originY;
        for (int ny = Math.max(0, ly - 1); ny <= Math.min(span - 1, ly + 1); ny++)
            for (int nx = Math.max(0, lx - 1); nx <= Math.min(span - 1, lx + 1); nx++)
                if (reached[ny * span + nx]) return true;
        return false;
    }

    /** Whether a fire a thrown incendiary lights at the cell could come to anyone on {@code side}. */
    public static boolean reachesSide(FireField field, int x, int y, Faction side, BattleView sim) {
        return reachesSide(field, x, y, LIT, side, sim);
    }

    /**
     * Whether a fire lit within {@code lit} cells of the cell ({@link #litCells}) could come to anyone on
     * {@code side}: what an incendiary is never sent onto.
     */
    public static boolean reachesSide(FireField field, int x, int y, int lit, Faction side, BattleView sim) {
        FireReach reach = of(field, x, y, lit);
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long friendly = sim.liveUnitAt(i);
            if (!side.friendlyTo(sim.identity().faction(friendly))) continue;
            if (reach.threatens(sim.world().cellX(friendly), sim.world().cellY(friendly))) return true;
        }
        return false;
    }

    /** How many cells the reach covers. */
    public int size() {
        int n = 0;
        for (boolean r : reached) if (r) n++;
        return n;
    }

    private static boolean fuel(FireField field, int x, int y) {
        return field.canBurn(x, y) || field.isBurning(x, y);
    }

    @Override public String toString() { return "FireReach" + Arrays.asList(originX + span / 2, originY + span / 2, size()); }
}
