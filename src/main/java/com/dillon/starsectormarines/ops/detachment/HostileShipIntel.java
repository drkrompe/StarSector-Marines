package com.dillon.starsectormarines.ops.detachment;

import java.util.function.UnaryOperator;

/**
 * The briefing's one line about the defender's ship. Text comes through a lookup so it lives in
 * {@code strings.json} and the composition is testable without the game.
 */
public final class HostileShipIntel {

    public static final String KEY_COMING = "hostileShipComing";
    public static final String KEY_COMING_ALONE = "hostileShipComingAlone";
    public static final String KEY_HELD = "hostileShipHeld";
    public static final String KEY_ABSENT = "hostileShipAbsent";

    private HostileShipIntel() {}

    /** The line to show, or null when the report has nothing to say (an undefended world). */
    public static String line(HostileShipReport report, UnaryOperator<String> strings) {
        if (report == null) return null;
        switch (report.outcome()) {
            case COMING:
                return fill(strings.apply(report.fleetName() == null ? KEY_COMING_ALONE : KEY_COMING), report);
            case HELD:
                return fill(strings.apply(KEY_HELD), report)
                        .replace("{3}", strings.apply(report.held().stringsKey));
            case ABSENT:
                return strings.apply(KEY_ABSENT);
            default:
                return null;
        }
    }

    private static String fill(String template, HostileShipReport r) {
        String fleet = r.fleetName() != null ? r.fleetName() : "";
        return template.replace("{0}", r.shipName()).replace("{1}", r.hullName()).replace("{2}", fleet);
    }
}
