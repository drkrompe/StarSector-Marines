package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Text-first company-officer guidance. Stable IDs are also the recording-script beats. */
public final class CompanyManagementGuide {
    public static final String PATH = "data/marines/company_management_guide.json";
    private static List<Line> lines = List.of();
    private static String speaker = "Castle";
    private static String portrait = "graphics/portraits/castle.png";
    private CompanyManagementGuide() { }
    public record Line(String id, String title, String text) { }
    public static String speaker() { return speaker; }
    public static String portrait() { return portrait; }
    public static List<Line> lines() { return lines; }
    public static void loadBuiltins() {
        try { install(Global.getSettings().loadJSON(PATH)); }
        catch (Exception failure) {
            Global.getLogger(CompanyManagementGuide.class).warn("Company management guide unavailable", failure);
        }
    }
    public static void install(JSONObject root) throws JSONException {
        var entries = root.getJSONArray("lines");
        var result = new ArrayList<Line>();
        for (int i = 0; i < entries.length(); i++) {
            var entry = entries.getJSONObject(i);
            result.add(new Line(entry.getString("id"), entry.getString("title"), entry.getString("text")));
        }
        lines = List.copyOf(result);
        speaker = root.optString("speaker", "Castle");
        portrait = root.optString("portrait", "graphics/portraits/castle.png");
    }
}
