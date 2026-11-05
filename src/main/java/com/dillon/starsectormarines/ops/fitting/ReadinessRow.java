package com.dillon.starsectormarines.ops.fitting;

import com.dillon.starsectormarines.marine.FittingGap;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

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

        /** A gap's colour, on every machine: red for one it cannot go without, yellow for one it is better for. */
        public static Tone of(FittingGap gap) {
            return gap.required() ? DANGER : WARN;
        }
    }

    /**
     * A machine's readiness list, in every fitting room: one line per gap, in the order {@code gaps} gives (each
     * required part, then what it goes without), or the {@code whole} line alone when it lacks nothing. The room says
     * each gap in its own words; the order and the colours are this one rule.
     */
    public static List<ReadinessRow> list(List<FittingGap> gaps, BiFunction<FittingGap, Tone, ReadinessRow> line,
                                          Supplier<ReadinessRow> whole) {
        List<ReadinessRow> rows = new ArrayList<>();
        for (FittingGap gap : gaps) {
            ReadinessRow row = line.apply(gap, Tone.of(gap));
            if (row != null) rows.add(row);
        }
        if (rows.isEmpty()) rows.add(whole.get());
        return List.copyOf(rows);
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
