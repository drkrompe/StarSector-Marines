package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/**
 * One machine in the reserve hangar, as the Boat Deck's and the Mech Lab's Reserve strip list it:
 * its name, what it is, and RECALL, which is greyed while no vacant berth or gantry would take it.
 */
public record ReserveRow(String id, String name, String line, boolean recallable, Runnable recall)
        implements MarkupPropertySource {

    /** The strip's heading: how full the hangar is against what the home ship's refit holds. */
    static String title(int stored, int capacity) {
        return Strings.format("reserveHangarTitle", stored, capacity);
    }

    @Override public Object markupProperty(String p) {
        return switch (p) {
            case "id" -> id;
            case "nameId" -> id + "-name";
            case "lineId" -> id + "-line";
            case "recallId" -> id + "-recall";
            case "name" -> name;
            case "line" -> line;
            case "recallClasses" -> recallable ? "reserve-recall console-action" : "reserve-recall console-action blocked";
            case "recallDisabled" -> !recallable;
            case "recall" -> recall;
            default -> throw new IllegalArgumentException("Unknown reserve property: " + p);
        };
    }
}
