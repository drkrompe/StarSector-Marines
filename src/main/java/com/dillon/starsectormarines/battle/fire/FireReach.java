package com.dillon.starsectormarines.battle.fire;

import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.Arrays;

/**
 * The ground a fire lit at one cell could come to (`fire-nouns.md`, Starting
 * fires): the fuel round the lit cell, then every cell of fuel within
 * {@link #JUMP} of fuel already reached, since heat crosses a gap that narrow,
 * out to {@link #RADIUS} of the lit cell. A cell is threatened when it is
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

    /** Chebyshev cells from the lit cell the reach is followed to. */
    public static final int RADIUS = 10;
    /** The widest gap in fuel, in cells, that heat is taken to cross: the field's reach in anything short of a strong wind. */
    public static final int JUMP = 3;
    /** Chebyshev cells round the lit cell that the blast itself sets alight. */
    private static final int LIT = 2;
    private static final int SIDE = RADIUS * 2 + 1;

    private final boolean[] reached = new boolean[SIDE * SIDE];
    private final int originX, originY;

    private FireReach(int originX, int originY) {
        this.originX = originX;
        this.originY = originY;
    }

    public static boolean enabled() { return ENABLED; }

    /** The reach of a fire lit at a cell on this field. */
    public static FireReach of(FireField field, int x, int y) {
        FireReach reach = new FireReach(x - RADIUS, y - RADIUS);
        int[] queue = new int[SIDE * SIDE];
        int head = 0, tail = 0;
        for (int dy = -LIT; dy <= LIT; dy++) {
            for (int dx = -LIT; dx <= LIT; dx++) {
                int local = (RADIUS + dy) * SIDE + RADIUS + dx;
                if (!reach.reached[local] && fuel(field, x + dx, y + dy)) {
                    reach.reached[local] = true;
                    queue[tail++] = local;
                }
            }
        }
        while (head < tail) {
            int local = queue[head++];
            int lx = local % SIDE, ly = local / SIDE;
            for (int ny = Math.max(0, ly - JUMP); ny <= Math.min(SIDE - 1, ly + JUMP); ny++) {
                for (int nx = Math.max(0, lx - JUMP); nx <= Math.min(SIDE - 1, lx + JUMP); nx++) {
                    int next = ny * SIDE + nx;
                    if (reach.reached[next] || !fuel(field, reach.originX + nx, reach.originY + ny)) continue;
                    reach.reached[next] = true;
                    queue[tail++] = next;
                }
            }
        }
        return reach;
    }

    /** Whether a cell is reached, or beside reached ground. */
    public boolean threatens(int x, int y) {
        int lx = x - originX, ly = y - originY;
        for (int ny = Math.max(0, ly - 1); ny <= Math.min(SIDE - 1, ly + 1); ny++)
            for (int nx = Math.max(0, lx - 1); nx <= Math.min(SIDE - 1, lx + 1); nx++)
                if (reached[ny * SIDE + nx]) return true;
        return false;
    }

    /** Whether a fire lit at the cell could come to anyone on {@code side}: what an incendiary is never sent onto. */
    public static boolean reachesSide(FireField field, int x, int y, Faction side, BattleView sim) {
        FireReach reach = of(field, x, y);
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

    @Override public String toString() { return "FireReach" + Arrays.asList(originX + RADIUS, originY + RADIUS, size()); }
}
