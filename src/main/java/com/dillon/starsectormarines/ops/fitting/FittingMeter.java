package com.dillon.starsectormarines.ops.fitting;

import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

import java.util.Locale;

/**
 * One bar of a specifications card, drawn by {@code fitting-meter}: positioned
 * segments on a track, the stock share in a dim shade of the part's colour,
 * any gain past stock in the full colour, any shortfall below stock as a
 * deficit, a tick where stock stands, and a hovered candidate's change as a
 * ghost over the top.
 */
public record FittingMeter(String id, String label, String value,
                           String fillStyle, String ghostStyle, String ghostClasses,
                           String gainStyle, String gainClasses,
                           String deficitStyle, String deficitClasses,
                           String stockStyle, String stockClasses)
        implements MarkupPropertySource {

    /*
     * Each stat bar wears the colour of the part that moves it, the tones the dolls' bricks use:
     * plate for armor, the engine's blue for mobility, flux purple for capacitors and
     * dissipation, a weapon's amber for reach and guns, missile green for racks.
     */
    public static final String PLATE_TONE = "#b9b6a2";
    public static final String ENGINE_TONE = "#5e96d2";
    public static final String FLUX_TONE = "#a07edc";
    public static final String WEAPON_TONE = "#e2c248";
    public static final String MISSILE_TONE = "#6cc45e";
    /** The colour of a figure no part moves: drawn plain, with no stock tick. */
    public static final String NEUTRAL_TONE = "#acae96";

    /** A bar with no stock reference and no candidate. */
    public static FittingMeter of(String id, String label, String value, float amount, float maximum) {
        return of(id, label, value, amount, maximum, amount, amount, NEUTRAL_TONE);
    }

    public static FittingMeter of(String id, String label, String value, float currentAmount, float maximum,
                                  float ghostAmount, float stockAmount, String tone) {
        int current = percent(currentAmount, maximum);
        int ghost = percent(ghostAmount, maximum);
        int stock = percent(stockAmount, maximum);

        String ghostStyle;
        String ghostClasses;
        if (ghost > current) {
            ghostStyle = segment(current, ghost - current);
            ghostClasses = "meter-ghost gain";
        } else if (ghost < current) {
            ghostStyle = segment(ghost, current - ghost);
            ghostClasses = "meter-ghost loss";
        } else {
            ghostStyle = "";
            ghostClasses = "meter-ghost hidden";
        }
        String fillStyle = segment(0, Math.min(current, stock)) + "background-color: " + dim(tone) + ";";
        boolean gained = current > stock;
        boolean fallsShort = current < stock;
        String gainStyle = gained ? segment(stock, current - stock) + "background-color: " + tone + ";" : "";
        String deficitStyle = fallsShort ? segment(current, stock - current) : "";
        boolean stockShown = !NEUTRAL_TONE.equals(tone);
        return new FittingMeter(id, label, value, fillStyle, ghostStyle, ghostClasses,
                gainStyle, gained ? "meter-gain" : "meter-gain hidden",
                deficitStyle, fallsShort ? "meter-deficit" : "meter-deficit hidden",
                "left: " + stock + "%;", stockShown ? "meter-stock" : "meter-stock hidden");
    }

    private static int percent(float amount, float maximum) {
        int value = maximum > 0f ? Math.round(amount / maximum * 100f) : 0;
        return Math.max(0, Math.min(100, value));
    }

    private static String segment(int left, int width) {
        return "left: " + left + "%; width: " + width + "%;";
    }

    /** The part's colour sunk most of the way into the track, for the share stock already gives. */
    public static String dim(String tone) {
        int rgb = Integer.parseInt(tone.substring(1), 16);
        int track = 0x121719;
        int r = Math.round(((rgb >> 16) & 0xff) * .5f + ((track >> 16) & 0xff) * .5f);
        int g = Math.round(((rgb >> 8) & 0xff) * .5f + ((track >> 8) & 0xff) * .5f);
        int b = Math.round((rgb & 0xff) * .5f + (track & 0xff) * .5f);
        return String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b);
    }

    public String labelId() { return id + ":label"; }
    public String valueId() { return id + ":value"; }
    public String fillId() { return id + ":fill"; }

    @Override public Object markupProperty(String p) { return switch (p) {
        case "id" -> id; case "labelId" -> labelId(); case "valueId" -> valueId();
        case "trackId" -> id + ":track"; case "fillId" -> fillId(); case "ghostId" -> id + ":ghost";
        case "gainId" -> id + ":gain"; case "deficitId" -> id + ":deficit"; case "stockId" -> id + ":stock";
        case "label" -> label; case "value" -> value; case "fillStyle" -> fillStyle;
        case "ghostStyle" -> ghostStyle; case "ghostClasses" -> ghostClasses;
        case "gainStyle" -> gainStyle; case "gainClasses" -> gainClasses;
        case "deficitStyle" -> deficitStyle; case "deficitClasses" -> deficitClasses;
        case "stockStyle" -> stockStyle; case "stockClasses" -> stockClasses;
        default -> throw new IllegalArgumentException("Unknown fitting-meter property: " + p); }; }
}
