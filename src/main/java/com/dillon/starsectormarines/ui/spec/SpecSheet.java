package com.dillon.starsectormarines.ui.spec;

import java.util.List;

/**
 * What a hover overlay says about one catalog item ({@code ui-nouns.md}).
 *
 * <p>A value and nothing else: it carries no reference to the item it describes
 * and no behaviour. The copy factory writes one from the item's owning catalog;
 * the spec-sheet layer paints it. Neither side knows the other.
 *
 * @param title    the item's display name
 * @param subtitle one line beneath it — designation, role, tier, grade, access —
 *                 or empty
 * @param crestPath sprite path of the crest shown beside the heading, or null
 * @param accent   a short kind token ({@code weapon}, {@code armor},
 *                 {@code special}, {@code system}, {@code mech}) the stylesheet
 *                 keys a border colour on
 * @param stats    stat rows in display order; may be empty
 * @param notes    field-note paragraphs in display order; may be empty
 */
public record SpecSheet(String title,
                        String subtitle,
                        String crestPath,
                        String accent,
                        List<Stat> stats,
                        List<String> notes) {

    public SpecSheet {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A spec sheet needs a title");
        subtitle = subtitle == null ? "" : subtitle;
        accent = accent == null || accent.isBlank() ? "item" : accent;
        stats = stats == null ? List.of() : List.copyOf(stats);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /**
     * One stat row.
     *
     * @param label the stat's name as shown
     * @param value the value as shown, already formatted
     * @param fill  meter fill in 0..1, or negative for a row with no meter
     */
    public record Stat(String label, String value, float fill) {

        public static final float NO_METER = -1f;

        public Stat {
            if (label == null || label.isBlank()) throw new IllegalArgumentException("A stat needs a label");
            value = value == null ? "" : value;
            if (fill > 1f) fill = 1f;
            if (fill < 0f) fill = NO_METER;
        }

        public static Stat of(String label, String value) {
            return new Stat(label, value, NO_METER);
        }

        public boolean hasMeter() {
            return fill >= 0f;
        }
    }
}
