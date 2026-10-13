package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.fs.starfarer.api.Global;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Text-first company-officer guidance. Stable IDs are also the recording-script beats. */
public final class CompanyManagementGuide {
    public static final String PATH = "data/marines/company_management_guide.json";
    private static List<LocalizedLine> lines = List.of();
    private static String speakerKey = "openingCompanyOfficerName";
    private static String portrait = "graphics/portraits/castle.png";
    private CompanyManagementGuide() { }
    public record Line(String id, String title, String text) { }
    public static String speaker() { return Strings.get(speakerKey); }
    public static String portrait() { return portrait; }
    private record LocalizedLine(String id, String titleKey, String textKey) { }
    public static List<Line> lines() {
        return lines.stream().map(line -> new Line(line.id(), Strings.get(line.titleKey()), Strings.get(line.textKey()))).toList();
    }
    public static void loadBuiltins() {
        try { install(Global.getSettings().loadJSON(PATH)); }
        catch (Exception failure) {
            Global.getLogger(CompanyManagementGuide.class).warn("Company management guide unavailable", failure);
        }
    }
    public static void install(JSONObject root) throws JSONException {
        var entries = root.getJSONArray("lines");
        var result = new ArrayList<LocalizedLine>();
        for (int i = 0; i < entries.length(); i++) {
            var entry = entries.getJSONObject(i);
            result.add(new LocalizedLine(entry.getString("id"), entry.getString("titleKey"), entry.getString("textKey")));
        }
        lines = List.copyOf(result);
        speakerKey = root.optString("speakerKey", "openingCompanyOfficerName");
        portrait = root.optString("portrait", "graphics/portraits/castle.png");
    }
}
