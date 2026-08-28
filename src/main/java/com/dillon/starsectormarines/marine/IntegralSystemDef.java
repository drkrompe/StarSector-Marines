package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * One capability an armour pattern carries in the suit itself
 * ({@code integral-armor-systems.md}).
 *
 * <p><b>Per pattern, not per role.</b> A role says what job a suit is for;
 * which concrete suit the player recovered is the part that should surprise
 * them. Most patterns declare nothing, which is what makes a system a reason to
 * want a particular suit rather than a tax on the tier.
 *
 * <p><b>It is part of the suit, not a carried item.</b> A billet still carries
 * at most one special item, and an integral system never spends it — a heavy
 * suit must not cost a marine their grenades, or the heavy role reads as a
 * downgrade. The two are separately authored and separately issued.
 *
 * <p>The resource and duration vocabulary is deliberately the one special
 * equipment already uses ({@link SpecialResourceMode}), because this is a
 * second carrier for that vocabulary rather than a second vocabulary.
 */
public record IntegralSystemDef(
        String id,
        String displayName,
        String description,
        IntegralSystemEffect effect,
        SpecialResourceMode resourceMode,
        float durationSeconds,
        float cooldownSeconds,
        int startingAmmo,
        BreacherAssistSpec breacherAssist,
        MissilePodSpec missilePod) implements Serializable {

    /**
     * Keys that would express an integral system as durability. Rejected by
     * name so a future author is told the rule rather than quietly widening the
     * thing this concept exists to avoid.
     */
    private static final String[] FORBIDDEN_DURABILITY_KEYS = {
            "armorCapacity", "armorCapacityBonus", "armorRating", "armorRatingBonus",
            "bonusHp", "maxHp", "hpBonus", "extraArmor"
    };

    /** Parses the optional {@code integralSystem} block on an armour catalog entry. */
    public static IntegralSystemDef parse(JSONObject json, String armorId) throws JSONException {
        rejectDurability(json, armorId);
        String id = requireText(json, "id", armorId);
        IntegralSystemEffect effect = IntegralSystemEffect.fromKey(
                requireText(json, "effect", armorId), armorId);
        SpecialResourceMode resourceMode = SpecialResourceMode.fromKey(
                requireText(json, "resource", armorId), armorId);
        float durationSeconds = positive(json, "durationSeconds", armorId);
        float cooldownSeconds = json.has("cooldownSeconds")
                ? positive(json, "cooldownSeconds", armorId) : 0f;
        int startingAmmo = json.optInt("startingAmmo", 0);

        switch (resourceMode) {
            case COOLDOWN -> {
                if (cooldownSeconds <= 0f) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' is cooldown-gated and must declare a positive cooldownSeconds");
                }
                if (startingAmmo != 0) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' is cooldown-gated and cannot declare startingAmmo");
                }
                if (cooldownSeconds <= durationSeconds) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' would be permanently available: cooldownSeconds must exceed"
                            + " durationSeconds, or the effect is not temporary");
                }
            }
            case AMMUNITION -> {
                if (startingAmmo <= 0) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' is ammunition-gated and must declare positive startingAmmo");
                }
            }
        }

        BreacherAssistSpec breacher = null;
        MissilePodSpec missilePod = null;
        switch (effect) {
            case BREACHER_ASSIST -> {
                if (resourceMode != SpecialResourceMode.COOLDOWN) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' is a breacher assist and must be cooldown-gated");
                }
                breacher = BreacherAssistSpec.parse(json, armorId, id);
            }
            case MISSILE_POD -> {
                if (resourceMode != SpecialResourceMode.AMMUNITION) {
                    throw new JSONException("Integral system '" + id + "' on armor '" + armorId
                            + "' is a missile pod and must be ammunition-gated");
                }
                missilePod = MissilePodSpec.parse(json, armorId, id);
            }
        }

        return new IntegralSystemDef(
                id,
                requireText(json, "displayName", armorId),
                requireText(json, "description", armorId),
                effect,
                resourceMode,
                durationSeconds,
                cooldownSeconds,
                startingAmmo,
                breacher,
                missilePod);
    }

    /** Ammunition-gated systems run out; cooldown-gated ones only make you wait. */
    public boolean usesAmmunition() {
        return resourceMode == SpecialResourceMode.AMMUNITION;
    }

    private static void rejectDurability(JSONObject json, String armorId) throws JSONException {
        for (String key : FORBIDDEN_DURABILITY_KEYS) {
            if (json.has(key)) {
                throw new JSONException("Armor '" + armorId + "' integral system declares '" + key
                        + "'. An integral system must express itself as behaviour, never as"
                        + " durability — authored protection belongs on the pattern's own"
                        + " armorCapacity/armorRating, and widening those is what this concept"
                        + " exists to avoid (integral-armor-systems.md).");
            }
        }
    }

    static float positive(JSONObject json, String key, String owner) throws JSONException {
        float value = (float) json.getDouble(key);
        if (!Float.isFinite(value) || value <= 0f) {
            throw new JSONException("Integral system on armor '" + owner + "' field '" + key
                    + "' must be finite and positive");
        }
        return value;
    }

    private static String requireText(JSONObject json, String key, String owner)
            throws JSONException {
        String value = json.optString(key, null);
        if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) {
            throw new JSONException("Armor '" + owner
                    + "' integral system is missing required text field '" + key + "'");
        }
        return value.trim();
    }
}
