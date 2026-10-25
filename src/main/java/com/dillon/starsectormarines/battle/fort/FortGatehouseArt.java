package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.turret.EmplacementStyle;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How a fort's gate is drawn: the leaves laid over the open passage. Its flanking towers are drawn by the works,
 * like the wall they stand in. Authored on the structure that is the fort's platform, so a fort kind names its own
 * gate. The leaves are authored in the layout's west-gate frame, two cells across and three along the wall, and turned
 * with the fort.
 *
 * <p>A gatehouse built in another emplacement style is drawn in that style's look, where the art names one
 * ({@code looks}: the style keys painted for it): the same file names in a folder named for the style beside the
 * base art, so a fort's gate is the same stuff as its walls (`fort-works.md`). A style it does not name draws the
 * base art.
 */
public final class FortGatehouseArt {

    private final String gate;
    private final Set<String> looks;

    private FortGatehouseArt(String gate, Set<String> looks) {
        this.gate = gate;
        this.looks = looks;
    }

    public static FortGatehouseArt parse(String structureId, JSONObject json) throws JSONException {
        Set<String> looks = new LinkedHashSet<>();
        JSONArray keys = json.optJSONArray("looks");
        for (int i = 0; keys != null && i < keys.length(); i++) looks.add(keys.getString(i));
        return new FortGatehouseArt(require(structureId, json, "gate"), looks);
    }

    /** This gatehouse as built in {@code style}: its look where it has one, else itself. */
    public FortGatehouseArt inStyle(EmplacementStyle style) {
        if (style == null || !looks.contains(style.key)) return this;
        return new FortGatehouseArt(lookPath(gate, style), Set.of());
    }

    /** {@code dir/name.png} drawn in {@code style}: {@code dir/<style key>/name.png}. */
    static String lookPath(String path, EmplacementStyle style) {
        int slash = path.lastIndexOf('/');
        return path.substring(0, slash + 1) + style.key + path.substring(slash);
    }

    private static String require(String structureId, JSONObject json, String key) throws JSONException {
        String value = json.optString(key, "").trim();
        if (value.isEmpty()) {
            throw new JSONException("Structure '" + structureId + "' gatehouse art is missing '" + key + "'");
        }
        return value;
    }

    /** The gate leaves, drawn over the open passage. */
    public String gate() {
        return gate;
    }

    /** Every sprite this gatehouse draws, its looks' included. */
    public List<String> spritePaths() {
        List<String> paths = new ArrayList<>(List.of(gate));
        for (EmplacementStyle style : EmplacementStyle.values()) {
            if (looks.contains(style.key)) paths.addAll(inStyle(style).spritePaths());
        }
        return paths;
    }
}
