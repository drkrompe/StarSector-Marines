package com.dillon.starsectormarines.ops.spec;

import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/**
 * One labelled value with a proportional bar behind it, as a markup row.
 *
 * <p>Existed twice — once in the Fleet Armory's view model and once in the
 * equipment designer's — with two identical copies of the fill arithmetic and
 * two copies of the catalog-ceiling scans behind it. It is one record here, and
 * the ceilings are {@link CatalogCeilings}.
 *
 * <p>The ids are element ids the markup binds to, derived from one row id so a
 * caller names a meter once. {@code fillStyle} is an inline width so the
 * stylesheet owns the track and this owns only how much of it is filled.
 *
 * @param id        the row's element id
 * @param labelId   the label element's id
 * @param trackId   the bar track's id
 * @param fillId    the filled portion's id
 * @param valueId   the numeric readout's id
 * @param label     the stat's name as shown
 * @param value     the value as shown, already formatted
 * @param fillStyle inline {@code width: n%;} for the filled portion
 */
public record StatMeter(
        String id, String labelId, String trackId, String fillId, String valueId,
        String label, String value, String fillStyle)
        implements MarkupPropertySource {

    /**
     * One meter, filled by {@code amount} out of {@code maximum}.
     *
     * <p>The fill is clamped to the track and rounded to a whole percent: a
     * meter is read at a glance, and a non-positive ceiling reads empty rather
     * than throwing, because a catalog that scanned to nothing is a screen
     * showing no bars rather than a crash.
     */
    public static StatMeter of(String id, String label, String value,
                               float amount, float maximum) {
        int percentage = maximum > 0f
                ? Math.round(Math.max(0f, Math.min(1f, amount / maximum)) * 100f) : 0;
        return new StatMeter(id, id + ":label", id + ":track", id + ":fill",
                id + ":value", label, value, "width: " + percentage + "%;");
    }

    /** The fill as a fraction in 0..1, which is what a spec sheet's stat row carries. */
    public static float fraction(float amount, float maximum) {
        return maximum > 0f ? Math.max(0f, Math.min(1f, amount / maximum)) : 0f;
    }

    @Override
    public Object markupProperty(String property) {
        return switch (property) {
            case "id" -> id;
            case "labelId" -> labelId;
            case "trackId" -> trackId;
            case "fillId" -> fillId;
            case "valueId" -> valueId;
            case "label" -> label;
            case "value" -> value;
            case "fillStyle" -> fillStyle;
            default -> throw new IllegalArgumentException("Unknown stat-meter property");
        };
    }
}
