package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.NightLighting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Where a ruin's light art lives ({@code battle-render-nouns.md}, night lighting): the painted lamp fixtures, lit and
 * dark, and the emissive overlays some of its doodad sheets carry. The night shade draws from it, and a battle's load
 * names it from the sheets the battle places ({@link #paths}), so none of it loads on first draw.
 */
final class RuinsLightArt {

    /** Where the lamp art and the sheets' emissive overlays live. */
    static final String LIGHTS = "graphics/battle/lights/";
    /** The sheets that carry an emissive overlay, by the sheet path a placement names, to the overlay's name. */
    private static final Map<String, String> EMISSIVE_SHEETS = new TreeMap<>(Map.of(
            "graphics/doodads/ruins-themes.png", "ruins-themes",
            "graphics/doodads/ruins-forge.png", "ruins-forge",
            "graphics/doodads/ruins-rooms.png", "ruins-rooms",
            "graphics/doodads/ruins-works-plant.png", "ruins-works-plant"));
    /** A ruin's sheets all start so; a battle placing one is a ruin, whose lamps are painted fixtures. */
    private static final String RUINS_SHEET = "graphics/doodads/ruins-";

    private RuinsLightArt() {}

    /** The painted art a fixture draws from ({@code lights/<id>-{lit,unlit}.png}); null for one drawn otherwise. */
    static String fixture(NightLighting.Fixture fixture) {
        return switch (fixture) {
            case CEILING_STRIP -> "ruins-ceiling-strip";
            case HIGH_BAY -> "ruins-ceiling-highbay";
            case EMERGENCY_STRIP -> "ruins-emergency-strip";
            case BEACON -> "ruins-emergency-beacon";
            default -> null;
        };
    }

    /** The image of a fixture's art, {@code lit} or dark. */
    static String fixturePath(String art, boolean lit) {
        return LIGHTS + art + (lit ? "-lit.png" : "-unlit.png");
    }

    /** The emissive overlay of a placement's sheet, or null for a sheet without one. */
    static String overlayPath(String sheetPath) {
        String name = sheetPath == null ? null : EMISSIVE_SHEETS.get(sheetPath);
        return name == null ? null : LIGHTS + "emissive/" + name + "-lit.png";
    }

    /**
     * The light art a battle placing {@code sheets} draws: every fixture's two images where any sheet is a ruin's, and
     * the overlay of each placed sheet that has one; nothing for a battle with no ruin in it.
     */
    static List<String> paths(Set<String> sheets) {
        List<String> out = new ArrayList<>();
        if (sheets == null || sheets.stream().noneMatch(s -> s.startsWith(RUINS_SHEET))) return out;
        for (NightLighting.Fixture f : NightLighting.Fixture.values()) {
            String art = fixture(f);
            if (art == null) continue;
            out.add(fixturePath(art, false));
            out.add(fixturePath(art, true));
        }
        for (String sheet : EMISSIVE_SHEETS.keySet()) {
            if (sheets.contains(sheet)) out.add(overlayPath(sheet));
        }
        return out;
    }
}
