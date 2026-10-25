package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.ReachableRegion;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;

/**
 * How an advancing mech chooses where to stand: doctrine names the point it is going for, and a positioning strategy
 * names the cell it actually walks to near it.
 *
 * <p>Strategies are the place a mech's way of fighting can differ without each doctrine growing a branch:
 * {@link #forMember} is the one choice, so a pilot's experience, an ability that wants a vantage or a chassis that
 * fights best from a particular kind of ground becomes a strategy and a line there. Cover is not weighed while
 * advancing; that is a concern for a mech under fire.
 */
public enum AdvancePositioning {

    /** The point as doctrine named it: the walk a green pilot makes, straight at the contact or the zone. */
    DIRECT {
        @Override int[] choose(Advance advance, BattleView sim) { return advance.aim(); }
    },

    /**
     * The cell near the point that sees the most of the ground the advance is about, within weapon range: a
     * contact and where it was last seen, a zone's doorways and middle. The point itself wins a tie, so a pick
     * that already sees everything is kept, and one that sees nothing from anywhere near leaves the point as it
     * was. Each key cell seen is worth {@link #SIGHT_WEIGHT} cells of distance from the point.
     */
    OVERLOOK {
        @Override int[] choose(Advance advance, BattleView sim) {
            int[] aim = advance.aim();
            if (aim == null || advance.keyCells().length == 0) return aim;
            MechLoadoutComponent loadout = sim.world().mechLoadout(advance.member());
            int tick = sim.getSimTickIndex();
            if (loadout.advancePickedTick != Integer.MIN_VALUE && tick - loadout.advancePickedTick < HOLD_TICKS
                    && Math.abs(loadout.advanceAimX - aim[0]) <= 1 && Math.abs(loadout.advanceAimY - aim[1]) <= 1
                    && MechRouteIntent.candidate(advance.member(), loadout.advancePickX, loadout.advancePickY, sim)) {
                return new int[]{loadout.advancePickX, loadout.advancePickY};
            }
            int[] pick = vantage(advance, sim);
            loadout.advanceAimX = aim[0];
            loadout.advanceAimY = aim[1];
            loadout.advancePickX = pick[0];
            loadout.advancePickY = pick[1];
            loadout.advancePickedTick = tick;
            return pick;
        }
    };

    /** Cells around doctrine's point a vantage is looked for in. */
    static final int RING = 4;
    /** Cells of distance from the point one more key cell in sight is worth. */
    static final float SIGHT_WEIGHT = 4f;
    /** Ticks a vantage is held while doctrine's point stays within a cell of where it was. */
    static final int HOLD_TICKS = 60;

    /**
     * What an advance is about. {@code aim} is doctrine's point; {@code keyCells} the grid indices of the ground it
     * must see; {@code support} the mech or squad whose lead bounds it ({@code 0} for none); {@code target} a cell
     * never to stand on ({@code 0} for none).
     */
    record Advance(long member, Squad squad, int[] aim, int[] keyCells, long support, long target) {}

    abstract int[] choose(Advance advance, BattleView sim);

    /**
     * The strategy {@code member} positions by. Every assault mech overlooks today; the choice is made here, once, so
     * a strategy for a pilot's experience or an ability is a line in this method.
     */
    static AdvancePositioning forMember(long member, BattleView sim) {
        return OVERLOOK;
    }

    /** Whether an advancing mech may stand on a cell. */
    interface Standable {
        boolean at(int x, int y);
    }

    private static int[] vantage(Advance advance, BattleView sim) {
        long member = advance.member();
        ReachableRegion region = sim.mechReachableRegion(member);
        boolean hasTarget = advance.target() != 0L && sim.resolveUnit(advance.target()) != 0L;
        int targetX = hasTarget ? sim.world().cellX(advance.target()) : Integer.MIN_VALUE;
        int targetY = hasTarget ? sim.world().cellY(advance.target()) : Integer.MIN_VALUE;
        boolean led = advance.support() != 0L;
        float supportX = led ? sim.world().x(advance.support()) : 0f;
        float supportY = led ? sim.world().y(advance.support()) : 0f;
        float leadSq = BreachAndAssault.MAX_SUPPORT_LEAD * BreachAndAssault.MAX_SUPPORT_LEAD + 0.01f;
        Standable standable = (x, y) -> {
            if (x == targetX && y == targetY) return false;
            if (led) {
                float dx = x + 0.5f - supportX, dy = y + 0.5f - supportY;
                if (dx * dx + dy * dy > leadSq) return false;
            }
            return (region == null || region.reaches(x, y))
                    && MechRouteIntent.candidate(member, x, y, sim)
                    && MechAssignmentBoundary.permitsCell(member, advance.squad(), x, y, sim);
        };
        return overlook(sim.getGrid(), advance.aim(), advance.keyCells(), sim.world().attackRange(member), standable);
    }

    /**
     * The {@link #OVERLOOK} vantage around {@code aim}: the standable cell within {@link #RING} that has a line of
     * fire to the most {@code keyCells} within {@code range}, less its distance from {@code aim}; nearest ring, then
     * lowest row and column, on a tie. {@code aim} when no standable cell sees any.
     */
    static int[] overlook(NavigationGrid grid, int[] aim, int[] keyCells, float range, Standable standable) {
        int width = grid.getWidth();
        float rangeSq = range * range;
        int[] best = aim;
        float bestScore = 0f;
        boolean bestSees = false;
        for (int ring = 0; ring <= RING; ring++) {
            for (int y = aim[1] - ring; y <= aim[1] + ring; y++) {
                for (int x = aim[0] - ring; x <= aim[0] + ring; x++) {
                    if (Math.max(Math.abs(x - aim[0]), Math.abs(y - aim[1])) != ring) continue;
                    if (!grid.inBounds(x, y) || !standable.at(x, y)) continue;
                    int seen = 0;
                    for (int key : keyCells) {
                        int kx = key % width, ky = key / width;
                        float dx = kx - x, dy = ky - y;
                        if (dx * dx + dy * dy <= rangeSq
                                && grid.hasLineOfFire(x + 0.5f, y + 0.5f, kx + 0.5f, ky + 0.5f)) seen++;
                    }
                    if (seen == 0) continue;
                    float score = SIGHT_WEIGHT * seen
                            - (float) Math.sqrt((x - aim[0]) * (x - aim[0]) + (y - aim[1]) * (y - aim[1]));
                    if (!bestSees || score > bestScore) {
                        best = new int[]{x, y};
                        bestScore = score;
                        bestSees = true;
                    }
                }
            }
        }
        return best;
    }
}
