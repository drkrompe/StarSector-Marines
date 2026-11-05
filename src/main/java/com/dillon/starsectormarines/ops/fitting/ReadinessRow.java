package com.dillon.starsectormarines.ops.fitting;

import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/**
 * One line of a machine's readiness, as {@code fitting-readiness-row} draws it, in every fitting room: why the
 * selected machine can or cannot go, in the order it matters. Red is a part it cannot go without, yellow one it is
 * better for, green a machine with nothing missing or bare. A line about a socket opens that socket.
 */
public record ReadinessRow(String id, String classes, String barClasses, String title, String detail,
                           Runnable select) implements MarkupPropertySource {

    /** A line's colour: the socket cards' red and yellow, and green for a whole machine. */
    public enum Tone {
        DANGER("danger"), WARN("warn"), GOOD("good");

        private final String css;

        Tone(String css) { this.css = css; }
    }

    /**
     * @param idPrefix the room's prefix, so two rooms' rows never share an id
     * @param select   what a click does, or null for a line that opens nothing
     */
    public static ReadinessRow of(String idPrefix, String key, Tone tone, String title, String detail,
                                  Runnable select) {
        return new ReadinessRow(idPrefix + ":" + key,
                "readiness-row readiness-" + tone.css + (select != null ? " readiness-link" : ""),
                "readiness-bar readiness-bar-" + tone.css, title, detail, select != null ? select : () -> { });
    }

    @Override public Object markupProperty(String name) { return switch (name) {
        case "id" -> id; case "classes" -> classes; case "barClasses" -> barClasses;
        case "title" -> title; case "detail" -> detail; case "select" -> select;
        case "barId" -> id + ":bar"; case "textId" -> id + ":text";
        case "titleId" -> id + ":title"; case "detailId" -> id + ":detail";
        default -> throw new IllegalArgumentException("Unknown fitting-readiness-row property: " + name); }; }
}
