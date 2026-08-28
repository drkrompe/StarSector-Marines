package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.Map;
import java.util.function.Predicate;

/** Shared property contract for the persistent shipboard-room navigation shell. */
final class MarineOpsPageNav {

    /**
     * A page of the shell, and the compartment it is a view of.
     *
     * <p>Most of these screens are somewhere aboard, and a screen that is
     * somewhere can be somewhere the ship <em>does not have</em>. A hull that
     * structurally cannot hold a vehicle bay has no Mech Lab, and no amount of
     * refitting gives it one — that is the tension the company ship is built
     * around, so the shell has to be able to say it. Declaring the purpose here
     * rather than testing for it at each call site is what makes that automatic
     * for the next room screen somebody adds.
     *
     * <p>{@link #HQ} deliberately names no room. It is where the shell puts you
     * and where every other page returns to, so gating it on a compartment would
     * let a hull exist that the player cannot navigate at all. Company
     * headquarters is the company, not a wardroom.
     */
    enum Page {
        HQ(null, "hq"),
        BARRACKS(RoomPurpose.BARRACKS, "barracks"),
        ARMORY(RoomPurpose.ARMORY, "armory"),
        MECH_LAB(RoomPurpose.VEHICLE_BAY, "mechLab"),
        /**
         * Choosing the ship, which is not a place aboard one and so has no
         * button of its own: the shell is for moving around the vessel, and
         * this page is about which vessel it is. It names no room for the same
         * reason {@link #HQ} does not — a hull cannot fail to have it.
         */
        SHIP_TRANSFER(null, null),
        /**
         * The whole vessel rather than a place in her. It names no room for the
         * opposite reason {@link #HQ} does not: every hull with an interior has
         * a deck, so there is nothing here a ship can fail to have.
         */
        SHIP_VIEW(null, null);

        private final RoomPurpose room;
        private final String button;

        Page(RoomPurpose room, String button) {
            this.room = room;
            this.button = button;
        }

        /**
         * The property prefix of this page's button in the shell, or null for a
         * page the shell does not offer a way to. Declared here so the shell,
         * and anything checking it, agree on which pages have buttons.
         */
        String button() {
            return button;
        }

        /** The compartment this page frames, or null when it is not a room. */
        RoomPurpose room() {
            return room;
        }

        /** Whether a ship offering these rooms can be shown this page. */
        boolean availableAboard(Predicate<RoomPurpose> aboard) {
            return room == null || aboard.test(room);
        }
    }

    /**
     * A ship with every room, for fixtures and tools that are not aboard a
     * particular hull and have no business pretending to be.
     */
    static final Predicate<RoomPurpose> ANY_SHIP = purpose -> true;

    private MarineOpsPageNav() {
    }

    /**
     * @param aboard what the company ship actually has; a page whose room she
     *     does not have is shown unavailable and does nothing when clicked
     */
    static void put(Map<String, Object> props, Page current,
                    Predicate<RoomPurpose> aboard,
                    Runnable returnAction, Runnable hqAction,
                    Runnable barracksAction,
                    Runnable armoryAction, Runnable mechLabAction) {
        if (props == null) throw new IllegalArgumentException("props are required");
        if (current == null) throw new IllegalArgumentException("current page is required");
        if (aboard == null) throw new IllegalArgumentException("the ship's rooms are required");
        props.put("returnAction", required(returnAction, "returnAction"));
        put(props, Page.HQ, current, aboard, hqAction);
        put(props, Page.BARRACKS, current, aboard, barracksAction);
        put(props, Page.ARMORY, current, aboard, armoryAction);
        put(props, Page.MECH_LAB, current, aboard, mechLabAction);
    }

    /** Compatibility helper for focused fixtures that do not exercise room routing. */
    static void put(Map<String, Object> props, Page current,
                    Predicate<RoomPurpose> aboard,
                    Runnable returnAction, Runnable hqAction,
                    Runnable armoryAction, Runnable mechLabAction) {
        put(props, current, aboard, returnAction, hqAction, () -> { },
                armoryAction, mechLabAction);
    }

    private static void put(Map<String, Object> props, Page page, Page current,
                            Predicate<RoomPurpose> aboard, Runnable action) {
        String name = page.button();
        Runnable wired = required(action, name + "Action");
        boolean available = page.availableAboard(aboard);
        // An unavailable page keeps its button rather than losing it, so the
        // player can see that the ship has no such place instead of wondering
        // where the Mech Lab went. Its action is dropped here rather than left
        // to the styling, because a route nothing can reach is the actual rule
        // and a greyed-out button that still worked would be a lie.
        props.put(name + "Action", available ? wired : (Runnable) () -> { });
        props.put(name + "Classes", classes(page == current, available));
    }

    private static Runnable required(Runnable action, String name) {
        if (action == null) throw new IllegalArgumentException(name + " is required");
        return action;
    }

    private static String classes(boolean current, boolean available) {
        if (!available) return "page-nav-absent";
        return current ? "selected page-nav-current" : "";
    }
}
