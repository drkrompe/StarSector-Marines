package com.dillon.starsectormarines.battle.world.gen.nature;

/**
 * Which country's art a tree or an undergrowth patch is drawn with. Every flora is a reskin of the green woods: the
 * same {@link NaturalPropShape} silhouettes, footprints and states, placed under the green id with the flora's
 * {@link #suffix} after the species ({@code forest.oak-jungle}, and its states {@code forest.oak-jungle-damaged},
 * {@code -destroyed} and {@code -blight-k}), so combat, fire, creep and placement read every flora as its green twin.
 *
 * @see NaturalPropShape#find
 */
public enum Flora {

    /** The green temperate woods ({@code forest-*} sheets): the shapes' own ids. */
    TEMPERATE(""),

    /** The grey scrub a desert basin's rim grows ({@code dry-scrub-*} sheets). */
    DRY("-dry"),

    /** Rainforest: broadleaf giants, palms, tree ferns, vines ({@code jungle-*} sheets). */
    JUNGLE("-jungle"),

    /** Boreal conifers, dwarf birch and willow, heath and lichen ({@code taiga-*} sheets). */
    TAIGA("-taiga");

    /** What follows the species in a placed id; empty for the green woods. */
    public final String suffix;

    Flora(String suffix) {
        this.suffix = suffix;
    }

    /** The id a shape's art is placed under in this flora. */
    public String placedId(NaturalPropShape shape) {
        return shape.id() + suffix;
    }

    /** {@code id} with any flora's suffix taken off its end: the green twin's id. Null stays null. */
    public static String baseId(String id) {
        if (id == null) return null;
        for (Flora f : values()) {
            if (!f.suffix.isEmpty() && id.endsWith(f.suffix)) return id.substring(0, id.length() - f.suffix.length());
        }
        return id;
    }
}
