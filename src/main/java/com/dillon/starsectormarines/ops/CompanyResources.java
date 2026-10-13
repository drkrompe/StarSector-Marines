package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import java.text.NumberFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/** Shared presentation only: each caller supplies its authoritative quote and accessible stock. */
public final class CompanyResources {
    private CompanyResources() { }

    public static String number(long amount) {
        return NumberFormat.getIntegerInstance(Locale.getDefault()).format(Math.max(0, amount));
    }

    public static Row stock(String id, String name, String icon, long available) {
        return new Row(id, name, icon, Math.max(0, available), 0);
    }

    public static Row cost(String id, String name, String icon, long available, long required) {
        return new Row(id, name, icon, Math.max(0, available), Math.max(0, required));
    }

    public static List<Row> credits(String id, long available, long required) {
        return List.of(cost(id, Strings.get("companyResourceCredits"), "graphics/ui/company/credits.svg", available, required));
    }

    /** Text-only host fallback uses the same words and never repeats the whole stock balance. */
    public static String creditBill(long required, long available) {
        if (required <= 0) return "";
        return namedBill(Strings.get("companyResourceCredits"), required, available);
    }

    public static String namedBill(String name, long required, long available) {
        return Strings.format("companyResourceNamedCost", name, number(required))
                + (available < required ? " · " + Strings.format("companyResourceShortBy", number(required - available)) : "");
    }

    public static int order(String commodity) {
        int rank = List.of("credits", "marines", "supplies", "food", "heavy_machinery", "metals", "rare_metals", "hand_weapons").indexOf(commodity);
        return rank < 0 ? Integer.MAX_VALUE : rank;
    }

    public static List<Row> withCredits(List<Row> materials, long available) {
        List<Row> rows = new ArrayList<>();
        rows.add(stock("boat-credit-stock", Strings.get("companyResourceCredits"), "graphics/ui/company/credits.svg", available));
        rows.addAll(materials);
        return List.copyOf(rows);
    }

    public static String billHeight(List<Row> rows) { return "height: " + rows.size() * 36 + "px;"; }

    public record Row(String id, String name, String icon, long available, long required)
            implements MarkupPropertySource {
        public String iconId() { return id + ":icon"; }
        public String labelId() { return id + ":label"; }
        public String label() { return number(required > 0 ? required : available); }
        public long shortfall() { return Math.max(0, required - available); }
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> id; case "iconId" -> iconId(); case "labelId" -> labelId();
                case "copyId" -> id + ":copy";
                case "nameId" -> id + ":name"; case "shortId" -> id + ":short";
                case "lineId" -> id + ":line";
                case "name" -> name; case "icon" -> icon; case "label" -> label();
                case "cost" -> Strings.format("companyResourceCost", number(required));
                case "stock" -> number(available);
                case "shortage" -> shortfall() > 0 ? Strings.format("companyResourceShortBy", number(shortfall())) : "";
                case "classes" -> shortfall() > 0 ? "company-resource-cost short" : "company-resource-cost";
                default -> throw new IllegalArgumentException("Unknown resource row property: " + key);
            };
        }
    }
}
