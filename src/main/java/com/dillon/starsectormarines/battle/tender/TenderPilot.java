package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipPilot;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * The player's hands on a tender: how one tick of pilot input becomes the orders the tender
 * already understands. The hull is flown by the warship helm and the shield is the warship
 * pilot's; this class adds the two things a tender has that a capital has not.
 *
 * <p>The <b>left button</b> lays a build site at the cursor for the selected option, the same
 * {@link Tender#lay} the debug panel calls: it needs ground the player has seen, open and
 * walkable, clear of other sites. The <b>right button</b>, on the press, recalls the defence
 * drones into the bay or deploys them if they are docked; the drone swarm has no "go to the
 * cursor" order and none is invented here. Nothing is a second order model, and a pilot
 * cannot make a build free, quicker or bigger than the option says.
 */
public final class TenderPilot {

    /** Slots on the number keys. Options fill them in order; the rest are shown as not yet available. */
    public static final int OPTION_SLOTS = 4;
    /** Live sites a pilot may have laid at once. A held button paints sites; this is what stops it flooding the line. */
    public static final int MAX_SITES = 4;
    /** Ticks a placement notice stays on the strip. */
    public static final int NOTICE_TICKS = 90;

    private TenderPilot() {}

    /** How the last attempt to lay a site went, for the build card. */
    public enum Notice {
        NONE, LAID, UNSEEN, BLOCKED, FULL, NO_CHARGES
    }

    /** Whether a site could be laid at a cell, before any is. */
    public enum Placement {
        OPEN, UNSEEN, BLOCKED, OFF_MAP
    }

    /**
     * One tick of the pilot's hands: the travel axes (world, north positive), the cursor's ground
     * point and the two buttons.
     */
    public record Hands(float moveX, float moveY, float aimX, float aimY, boolean build, boolean defence) {
        public boolean aimKnown() {
            return Float.isFinite(aimX) && Float.isFinite(aimY);
        }
    }

    /** What the pilot's hands remember between ticks: button edges and the last notice. */
    public static final class Pad {
        private boolean defenceHeld;
        private int lastCellX = Integer.MIN_VALUE;
        private int lastCellY = Integer.MIN_VALUE;
        private Notice notice = Notice.NONE;
        private int noticeAge = NOTICE_TICKS;

        /** The last attempt's outcome while it is fresh, else {@link Notice#NONE}. */
        public Notice notice() {
            return noticeAge < NOTICE_TICKS ? notice : Notice.NONE;
        }

        public void reset() {
            defenceHeld = false;
            lastCellX = Integer.MIN_VALUE;
            lastCellY = Integer.MIN_VALUE;
            notice = Notice.NONE;
            noticeAge = NOTICE_TICKS;
        }

        void note(Notice next) {
            notice = next;
            noticeAge = 0;
        }
    }

    /** Whether a tender may be piloted by {@code player}: its hull is a live friendly one that is not leaving. */
    public static boolean eligible(Tender tender, Faction player) {
        return tender != null && WarshipPilot.eligible(tender.ship(), player);
    }

    /** The option on number-key slot {@code slot} (0 for the key 1), or null for a slot no option fills. */
    public static BuildOption option(int slot) {
        List<BuildOption> all = BuildOption.ALL;
        return slot >= 0 && slot < OPTION_SLOTS && slot < all.size() ? all.get(slot) : null;
    }

    /** Whether the cell under {@code (x, y)} is ground a site may be laid on, ignoring crowding and the line's length. */
    public static Placement placement(Tender tender, float x, float y, NavigationGrid grid,
                                      BiPredicate<Integer, Integer> revealed) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        if (!Float.isFinite(x) || !Float.isFinite(y) || !grid.inBounds(cx, cy)) return Placement.OFF_MAP;
        if (!revealed.test(cx, cy)) return Placement.UNSEEN;
        if (!grid.isWalkable(cx, cy)) return Placement.BLOCKED;
        for (ConstructionOrder other : tender.orders()) {
            if (other.phase().terminal()) continue;
            if (Math.hypot(other.siteX() - (cx + 0.5f), other.siteY() - (cy + 0.5f)) < Tender.SITE_SPACING) {
                return Placement.BLOCKED;
            }
        }
        return Placement.OPEN;
    }

    /**
     * Applies one tick of {@code hands}: flies the hull; while the left button is held with the cursor
     * over ground the player has seen, lays a site for {@code option} (once per cell, not once per tick);
     * and on the press of the right button recalls or deploys the defence drones.
     *
     * @param option   the selected option, or null when the slot the pilot chose is empty
     * @param revealed whether a cell is one the player has observed
     */
    public static void apply(Tender tender, Hands hands, Pad pad, BuildOption option,
                             NavigationGrid grid, BiPredicate<Integer, Integer> revealed) {
        Warship ship = tender.ship();
        ship.pilotMove(hands.moveX(), hands.moveY());
        if (pad.noticeAge < NOTICE_TICKS) pad.noticeAge++;
        boolean pressed = hands.defence() && !pad.defenceHeld;
        pad.defenceHeld = hands.defence();
        if (pressed) tender.defenceOut(!tender.defenceOut());
        if (!hands.build() || !hands.aimKnown()) {
            if (!hands.build()) {
                pad.lastCellX = Integer.MIN_VALUE;
                pad.lastCellY = Integer.MIN_VALUE;
            }
            return;
        }
        int cx = (int) Math.floor(hands.aimX());
        int cy = (int) Math.floor(hands.aimY());
        if (cx == pad.lastCellX && cy == pad.lastCellY) return;
        pad.lastCellX = cx;
        pad.lastCellY = cy;
        if (option == null) {
            pad.note(Notice.BLOCKED);
            return;
        }
        Placement where = placement(tender, hands.aimX(), hands.aimY(), grid, revealed);
        if (where == Placement.OFF_MAP) return;
        if (where == Placement.UNSEEN) {
            pad.note(Notice.UNSEEN);
        } else if (where == Placement.BLOCKED) {
            pad.note(Notice.BLOCKED);
        } else if (liveSites(tender) >= MAX_SITES) {
            pad.note(Notice.FULL);
        } else if (tender.metered() && option.cost() > tender.stock()) {
            pad.note(Notice.NO_CHARGES);
        } else {
            pad.note(tender.lay(option, hands.aimX(), hands.aimY(), grid) != null ? Notice.LAID : Notice.BLOCKED);
        }
    }

    /** Withdraws every unfinished site; true always, since cancelling nothing is still the answer the pilot asked for. */
    public static boolean cancelBuilds(Tender tender) {
        tender.cancelAll();
        return true;
    }

    /** Sites laid and not yet finished or withdrawn. */
    public static int liveSites(Tender tender) {
        int n = 0;
        for (ConstructionOrder o : tender.orders()) if (!o.phase().terminal()) n++;
        return n;
    }
}
