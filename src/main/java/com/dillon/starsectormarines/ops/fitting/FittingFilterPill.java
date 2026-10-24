package com.dillon.starsectormarines.ops.fitting;

import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;

/**
 * One button of the stores' filter strip, as {@code fitting-filter-pill} draws
 * it: a symbol on its category's colour, or the label alone where there is no
 * symbol.
 */
public record FittingFilterPill(String id, String label, String icon, String tone, String classes,
                                Runnable select) implements MarkupPropertySource {

    public static String classes(boolean selected) {
        return selected ? "catalog-filter-pill selected" : "catalog-filter-pill";
    }

    @Override public Object markupProperty(String p) { return switch (p) {
        case "id" -> id; case "label" -> label; case "classes" -> classes;
        case "icon" -> icon == null ? "" : icon;
        case "iconId" -> id + ":icon";
        case "iconClasses" -> icon == null ? "hidden" : "catalog-filter-icon";
        case "chipId" -> id + ":chip";
        case "chipClasses" -> icon == null ? "hidden" : "catalog-filter-chip";
        case "chipStyle" -> tone == null ? "" : "background-color: " + tone + ";";
        case "textId" -> id + ":text";
        case "text" -> icon == null ? label : "";
        case "textClasses" -> icon == null ? "catalog-filter-text" : "hidden";
        case "select" -> select;
        default -> throw new IllegalArgumentException("Unknown fitting-filter-pill property: " + p); }; }
}
