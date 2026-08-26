package com.dillon.starsectormarines.ops;

import java.util.Map;

/** Shared property contract for the persistent shipboard-room navigation shell. */
final class MarineOpsPageNav {

    enum Page { HQ, BARRACKS, ARMORY, MECH_LAB }

    private MarineOpsPageNav() {
    }

    static void put(Map<String, Object> props, Page current,
                    Runnable returnAction, Runnable hqAction,
                    Runnable barracksAction,
                    Runnable armoryAction, Runnable mechLabAction) {
        if (props == null) throw new IllegalArgumentException("props are required");
        if (current == null) throw new IllegalArgumentException("current page is required");
        props.put("returnAction", required(returnAction, "returnAction"));
        props.put("hqAction", required(hqAction, "hqAction"));
        props.put("barracksAction", required(barracksAction, "barracksAction"));
        props.put("armoryAction", required(armoryAction, "armoryAction"));
        props.put("mechLabAction", required(mechLabAction, "mechLabAction"));
        props.put("hqClasses", classes(current == Page.HQ));
        props.put("barracksClasses", classes(current == Page.BARRACKS));
        props.put("armoryClasses", classes(current == Page.ARMORY));
        props.put("mechLabClasses", classes(current == Page.MECH_LAB));
    }

    /** Compatibility helper for focused fixtures that do not exercise room routing. */
    static void put(Map<String, Object> props, Page current,
                    Runnable returnAction, Runnable hqAction,
                    Runnable armoryAction, Runnable mechLabAction) {
        put(props, current, returnAction, hqAction, () -> { },
                armoryAction, mechLabAction);
    }

    private static Runnable required(Runnable action, String name) {
        if (action == null) throw new IllegalArgumentException(name + " is required");
        return action;
    }

    private static String classes(boolean current) {
        return current ? "selected page-nav-current" : "";
    }
}
