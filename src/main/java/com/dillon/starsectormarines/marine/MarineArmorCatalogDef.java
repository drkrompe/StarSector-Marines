package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Data-authored identity, presentation, appearance family, and combat values for
 * infantry armor.
 *
 * <p>{@link #role} is a closed vocabulary rather than the free display text it
 * replaced. What a pattern is <em>for</em> has to be countable before the
 * catalog's shape can be checked at all ({@code role-and-access.md}); a word
 * only the Armory read could say anything, and did.
 */
public record MarineArmorCatalogDef(
        String id,
        String displayName,
        ArmorRole role,
        ArmorClass armorClass,
        ArmorTradition tradition,
        String description,
        int tier,
        String iconPath,
        LayeredArmorFamily appearanceFamily,
        float armorCapacity,
        float armorRating,
        float moveSpeedMult,
        float incomingAccuracyMult,
        IntegralSystemDef integralSystem,
        List<String> sheets,
        String presentationPending) {
    public MarineArmorCatalogDef {
        sheets = sheets == null ? List.of() : List.copyOf(sheets);
        if (presentationPending != null && presentationPending.isBlank()) presentationPending = null;
    }

    public MarineArmorCatalogDef(String id, String displayName, ArmorRole role, ArmorClass armorClass,
            ArmorTradition tradition, String description, int tier, String iconPath,
            LayeredArmorFamily appearanceFamily, float armorCapacity, float armorRating,
            float moveSpeedMult, float incomingAccuracyMult, IntegralSystemDef integralSystem,
            List<String> sheets) {
        this(id, displayName, role, armorClass, tradition, description, tier, iconPath,
                appearanceFamily, armorCapacity, armorRating, moveSpeedMult, incomingAccuracyMult,
                integralSystem, sheets, null);
    }

    /**
     * Whether the pattern's battle and armory presentation is complete. A pattern may be
     * authored (combat values, system, sheet) before its art exists; it then declares why in
     * {@code presentationPending}, and no card carrying it can become cargo
     * ({@link LoadoutCard#presentationDeclared}). Its appearance family meanwhile has no
     * layered assets, so a debug or test deployment draws a plain marker rather than
     * borrowing another suit's look.
     */
    public boolean presentationDeclared() { return presentationPending == null; }

    /**
     * Tactic sheets this pattern is added to on top of the ones that already carry it, by
     * sheet id. This is how a contributed pattern reaches a squad: a sheet issues only what
     * its card carries, so a pattern no sheet lists can be collected but never worn.
     */
    public List<String> sheets() { return sheets; }

    public String displayName() { return Strings.resolve(displayName); }

    public String description() { return Strings.resolve(description); }


    /** Compatibility for callers predating explicit protection classes. */
    public MarineArmorCatalogDef(String id, String displayName, ArmorRole role,
            ArmorTradition tradition, String description, int tier, String iconPath,
            LayeredArmorFamily appearanceFamily, float armorCapacity, float armorRating,
            float moveSpeedMult, float incomingAccuracyMult, IntegralSystemDef integralSystem) {
        this(id, displayName, role, ArmorClass.STANDARD, tradition, description, tier,
                iconPath, appearanceFamily, armorCapacity, armorRating, moveSpeedMult,
                incomingAccuracyMult, integralSystem, List.of(), null);
    }

    public static MarineArmorCatalogDef parse(JSONObject json) throws JSONException {
        return parse(json, Map.of());
    }

    /**
     * Parses one pattern, resolving an {@code integralSystem} block that names a
     * shared system by {@code ref} against {@code sharedSystems}: the catalog's
     * top-level {@code integralSystems}, where a system several suits carry is
     * defined once ({@code integral-system-slate.md}, the shield tiers).
     */
    public static MarineArmorCatalogDef parse(JSONObject json, Map<String, JSONObject> sharedSystems)
            throws JSONException {
        String id = requireText(json, "id", "armor catalog entry");
        JSONObject catalog = json.getJSONObject("catalog");
        JSONObject battle = json.getJSONObject("battle");
        int tier = catalog.getInt("tier");
        if (tier < 1) throw new JSONException("Armor '" + id + "' tier must be positive");
        LayeredArmorFamily appearanceFamily;
        try {
            appearanceFamily = LayeredArmorFamily.valueOf(
                    requireText(battle, "appearanceFamily", id));
        } catch (IllegalArgumentException failure) {
            throw new JSONException("Armor '" + id + "' has unknown appearanceFamily: "
                    + failure.getMessage());
        }
        return new MarineArmorCatalogDef(
                id,
                requireText(catalog, "displayName", id),
                ArmorRole.parse(catalog.optString("role", null), id),
                catalog.has("armorClass")
                        ? ArmorClass.parse(catalog.optString("armorClass", null), id)
                        : ArmorClass.STANDARD,
                ArmorTradition.parse(catalog.optString("tradition", null), id),
                requireText(catalog, "description", id),
                tier,
                requireText(catalog, "iconPath", id),
                appearanceFamily,
                nonNegative(battle, "armorCapacity", id),
                nonNegative(battle, "armorRating", id),
                positive(battle, "moveSpeedMult", id),
                nonNegative(battle, "incomingAccuracyMult", id),
                battle.has("integralSystem")
                        ? IntegralSystemDef.parse(resolveIntegralSystem(
                                battle.getJSONObject("integralSystem"), sharedSystems, id), id)
                        : null,
                sheets(catalog.optJSONArray("sheets")),
                catalog.has("presentationPending")
                        ? requireText(catalog, "presentationPending", id) : null);
    }

    /**
     * The capability this suit carries, or null. Most patterns carry none —
     * that is what makes one worth hunting for
     * ({@code integral-armor-systems.md}).
     */
    public boolean hasIntegralSystem() {
        return integralSystem != null;
    }

    /**
     * A suit's own system block, or the shared system it references with the suit's
     * presentation laid over it. Only presentation may differ between suits sharing
     * one system: the numbers and the name are the tier's, so a reference that tries
     * to retune them is refused rather than quietly forked.
     */
    static JSONObject resolveIntegralSystem(JSONObject block, Map<String, JSONObject> sharedSystems,
                                            String armorId) throws JSONException {
        if (!block.has("ref")) return block;
        String ref = requireText(block, "ref", armorId);
        JSONObject shared = sharedSystems.get(ref);
        if (shared == null) {
            throw new JSONException("Armor '" + armorId + "' references unknown integral system '"
                    + ref + "'. Known shared systems: " + sharedSystems.keySet());
        }
        JSONObject resolved = new JSONObject();
        for (Iterator<?> keys = shared.keys(); keys.hasNext(); ) {
            String key = (String) keys.next();
            resolved.put(key, shared.get(key));
        }
        for (Iterator<?> keys = block.keys(); keys.hasNext(); ) {
            String key = (String) keys.next();
            if (key.equals("ref")) continue;
            if (!key.equals("presentation")) {
                throw new JSONException("Armor '" + armorId + "' references integral system '" + ref
                        + "' and may override only its presentation, not '" + key + "'");
            }
            resolved.put(key, block.get(key));
        }
        return resolved;
    }

    private static List<String> sheets(JSONArray array) throws JSONException {
        List<String> sheets = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) sheets.add(array.getString(i));
        }
        return sheets;
    }

    private static float nonNegative(JSONObject json, String key, String owner)
            throws JSONException {
        float value = (float) json.getDouble(key);
        if (!Float.isFinite(value) || value < 0f) {
            throw new JSONException(owner + " field '" + key + "' must be finite and non-negative");
        }
        return value;
    }

    private static float positive(JSONObject json, String key, String owner)
            throws JSONException {
        float value = nonNegative(json, key, owner);
        if (value == 0f) throw new JSONException(owner + " field '" + key + "' must be positive");
        return value;
    }

    private static String requireText(JSONObject json, String key, String owner)
            throws JSONException {
        String value = json.optString(key, null);
        if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) {
            throw new JSONException(owner + " is missing required text field '" + key + "'");
        }
        return value.trim();
    }
}
