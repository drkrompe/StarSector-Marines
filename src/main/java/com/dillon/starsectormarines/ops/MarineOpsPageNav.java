package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.Map;
import java.util.function.Function;

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
    /**
     * What the shell knows about a room aboard the company ship.
     *
     * <p>Three answers rather than two. "She has no mech bay" and "she has not
     * been laid out yet" would both be a dead button, and they are not remotely
     * the same thing to tell a player: the first is a fact about the hull they
     * chose and the second is a wait of a second or two. A shell that showed
     * the second as the first would be telling somebody their capital had no
     * bay, and they would believe it.
     */
    enum Aboard { YES, NO, UNKNOWN }

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

        /** What a ship offering these rooms can say about this page. */
        Aboard availableAboard(Function<RoomPurpose, Aboard> aboard) {
            return room == null ? Aboard.YES : aboard.apply(room);
        }
    }

    /**
     * A ship with every room, for fixtures and tools that are not aboard a
     * particular hull and have no business pretending to be.
     */
    static final Function<RoomPurpose, Aboard> ANY_SHIP = purpose -> Aboard.YES;

    private MarineOpsPageNav() {
    }

    /**
     * @param aboard what the company ship actually has; a page whose room she
     *     does not have is shown unavailable and does nothing when clicked
     */
    static void put(Map<String, Object> props, Page current,
                    Function<RoomPurpose, Aboard> aboard,
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
                    Function<RoomPurpose, Aboard> aboard,
                    Runnable returnAction, Runnable hqAction,
                    Runnable armoryAction, Runnable mechLabAction) {
        put(props, current, aboard, returnAction, hqAction, () -> { },
                armoryAction, mechLabAction);
    }

    private static void put(Map<String, Object> props, Page page, Page current,
                            Function<RoomPurpose, Aboard> aboard, Runnable action) {
        String name = page.button();
        Runnable wired = required(action, name + "Action");
        Aboard available = page.availableAboard(aboard);
        // An unavailable page keeps its button rather than losing it, so the
        // player can see that the ship has no such place instead of wondering
        // where the Mech Lab went. Its action is dropped here rather than left
        // to the styling, because a route nothing can reach is the actual rule
        // and a greyed-out button that still worked would be a lie.
        //
        // A page that is only waiting keeps its action, because it stops
        // waiting. These properties are read once, when the page is built, and
        // a ship gets ready some seconds later; an action dropped here would
        // stay dropped on the page the player is looking at. Routing refuses
        // it until she is ready and stops refusing when she is, so the button
        // corrects itself even if nothing rebuilds it.
        props.put(name + "Action", available == Aboard.NO ? (Runnable) () -> { } : wired);
        props.put(name + "Classes", classes(page == current, available));
    }

    private static Runnable required(Runnable action, String name) {
        if (action == null) throw new IllegalArgumentException(name + " is required");
        return action;
    }

    private static String classes(boolean current, Aboard available) {
        if (available == Aboard.UNKNOWN) return "page-nav-waiting";
        if (available == Aboard.NO) return "page-nav-absent";
        return current ? "selected page-nav-current" : "";
    }
}
