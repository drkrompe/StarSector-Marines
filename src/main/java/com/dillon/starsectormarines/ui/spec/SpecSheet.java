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
 * @param seats    one picture per seat for a sheet that describes a whole
 *                 squad's issue, painted as fire teams of {@link #TEAM_SIZE},
 *                 each team a two-by-two block; empty for a single item
 * @param teams    a heading per fire team, in seat order; may be empty
 * @param stamp    who issued the item and on what terms, printed under the
 *                 heading; null for an item that carries none
 * @param scene    a moving picture of the item at work, painted under the
 *                 heading by whatever source the layer has for its kind; null
 *                 for none
 */
public record SpecSheet(String title,
                        String subtitle,
                        String crestPath,
                        String accent,
                        List<Stat> stats,
                        List<String> notes,
                        List<Seat> seats,
                        List<String> teams,
                        Stamp stamp,
                        Scene scene) {

    /** Seats per painted block: a fire team. */
    public static final int TEAM_SIZE = 4;

    public SpecSheet(String title, String subtitle, String crestPath, String accent,
                     List<Stat> stats, List<String> notes) {
        this(title, subtitle, crestPath, accent, stats, notes, List.of(), List.of(), null);
    }

    public SpecSheet(String title, String subtitle, String crestPath, String accent,
                     List<Stat> stats, List<String> notes, List<Seat> seats,
                     List<String> teams, Stamp stamp) {
        this(title, subtitle, crestPath, accent, stats, notes, seats, teams, stamp, null);
    }

    public SpecSheet {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A spec sheet needs a title");
        subtitle = subtitle == null ? "" : subtitle;
        accent = accent == null || accent.isBlank() ? "item" : accent;
        stats = stats == null ? List.of() : List.copyOf(stats);
        notes = notes == null ? List.of() : List.copyOf(notes);
        seats = seats == null ? List.of() : List.copyOf(seats);
        teams = teams == null ? List.of() : List.copyOf(teams);
    }

    /** This sheet with one picture per seat, under a heading per fire team. */
    public SpecSheet withSeats(List<Seat> seats, List<String> teams) {
        return new SpecSheet(title, subtitle, crestPath, accent, stats, notes, seats, teams, stamp, scene);
    }

    /** This sheet with an issuer's stamp under its heading. */
    public SpecSheet withStamp(Stamp stamp) {
        return new SpecSheet(title, subtitle, crestPath, accent, stats, notes, seats, teams, stamp, scene);
    }

    /** This sheet with a moving picture of its item under the heading. */
    public SpecSheet withScene(Scene scene) {
        return new SpecSheet(title, subtitle, crestPath, accent, stats, notes, seats, teams, stamp, scene);
    }

    /**
     * Which moving picture a sheet shows, by name only: the layer looks its
     * {@code kind} up among the scene sources installed for it, so this value
     * stays free of the code that paints it.
     *
     * @param kind    the source to ask, such as {@code weapon}
     * @param subject the item, by its catalog id
     * @param variant what distinguishes this showing of it, such as a grade; may be empty
     */
    public record Scene(String kind, String subject, String variant) {
        public Scene {
            if (kind == null || kind.isBlank()) throw new IllegalArgumentException("A scene needs a kind");
            if (subject == null || subject.isBlank()) throw new IllegalArgumentException("A scene needs a subject");
            variant = variant == null ? "" : variant;
        }
    }

    /**
     * The issuer's stamp a loadout card carries: who printed it and, for a
     * Restricted card, whose warrant it answers to.
     *
     * @param issued      the issuing line, office and serial
     * @param restriction the Restricted badge's text, or null for an open card
     */
    public record Stamp(String issued, String restriction) {
        public Stamp {
            issued = issued == null ? "" : issued;
        }

        public boolean restricted() {
            return restriction != null && !restriction.isBlank();
        }
    }

    /**
     * What one seat carries, as pictures.
     *
     * @param image the seat's main item: its weapon, or its suit
     * @param badge a second, smaller item drawn on its corner, or null
     */
    public record Seat(String image, String badge) { }

    /**
     * One stat row.
     *
     * @param id stable identity for comparisons, independent of the translated label
     * @param label the stat's name as shown
     * @param value the value as shown, already formatted
     * @param fill  meter fill in 0..1, or negative for a row with no meter
     * @param symbol the {@link RatingSymbols} symbol the meter is drawn as, a row of
     *               them lit in quarters; null for a plain bar
     */
    public record Stat(String id, String label, String value, float fill, String symbol) {

        /** Literal rows remain supported; comparable translated rows supply an explicit id. */
        public Stat(String label, String value, float fill) {
            this(label, label, value, fill);
        }

        public Stat(String id, String label, String value, float fill) {
            this(id, label, value, fill, null);
        }

        public static final float NO_METER = -1f;

        public Stat {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("A stat needs an id");
            if (label == null || label.isBlank()) throw new IllegalArgumentException("A stat needs a label");
            value = value == null ? "" : value;
            if (fill > 1f) fill = 1f;
            if (fill < 0f) fill = NO_METER;
            if (symbol != null && symbol.isBlank()) symbol = null;
        }

        public static Stat of(String label, String value) {
            return new Stat(label, value, NO_METER);
        }

        public boolean hasMeter() {
            return fill >= 0f;
        }
    }
}
