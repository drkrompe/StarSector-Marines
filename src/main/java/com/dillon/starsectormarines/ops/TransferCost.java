package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ship.HullClass;

/**
 * What moving the company to another hull costs, and what makes it cost that.
 *
 * <p>A transfer is a refit, not a decision on paper. Bunks, lockers, an armory
 * that locks and a bay a walker can be worked on in are not aboard a freighter
 * until somebody builds them, and every marine's kit has to be struck down and
 * stowed to get there. Charging for that is what makes choosing a home a
 * choice: a free transfer would have the player shopping the fleet every time
 * a hull arrived a hundred berths larger, and the company would live wherever
 * the spreadsheet last pointed.
 *
 * <p><b>Leaving a home costs; being given one does not.</b> A company with
 * nowhere to live — newly founded, or displaced by the loss of their ship —
 * moves for nothing. There is nothing to move out of, and a price on the one
 * action the player has no alternative to is a tax rather than friction.
 *
 * @param marines how many marines are being moved
 * @param walkers how many machines are being moved
 * @param credits what the yard wants for all of it
 */
public record TransferCost(int marines, int walkers, int credits) {

    /** A move that costs nothing, because the company had nowhere to leave. */
    public static final TransferCost FREE = new TransferCost(0, 0, 0);

    /** Per marine: kit struck down, stowed, and re-issued into a new berth. */
    private static final int PER_MARINE = 150;

    /** Per machine: somewhere a walker can be worked on is not a bunk. */
    private static final int PER_WALKER = 2_500;

    /**
     * What the yard wants to move a company of this size onto this hull.
     *
     * @param hullClass the hull being moved into, whose size sets the fitting-out
     * @param marines the company's own strength, in heads
     * @param walkers the machines that have to come with them
     */
    public static TransferCost of(HullClass hullClass, int marines, int walkers) {
        int heads = Math.max(0, marines);
        int machines = Math.max(0, walkers);
        return new TransferCost(heads, machines,
                fittingOut(hullClass) + heads * PER_MARINE + machines * PER_WALKER);
    }

    /** Whether this move is on the house. */
    public boolean free() {
        return credits <= 0;
    }

    /**
     * Fitting out the hull herself. A capital has four decks of
     * nothing-in-particular to turn into a garrison; a frigate has one, and a
     * fighter has nowhere anybody could live at all.
     */
    private static int fittingOut(HullClass hullClass) {
        if (hullClass == null) return 0;
        return switch (hullClass) {
            case FIGHTER -> 0;
            case FRIGATE -> 4_000;
            case DESTROYER -> 8_000;
            case CRUISER -> 15_000;
            case CAPITAL -> 25_000;
        };
    }
}
