package com.dillon.starsectormarines.marine;

import org.json.JSONException;

/**
 * What an armour pattern's integral system actually does
 * ({@code integral-armor-systems.md}).
 *
 * <p>Every value here must express itself as <b>behaviour</b> — movement,
 * protection with a clock on it, a delivered payload, perception. An effect
 * whose whole contribution is "more effective hit points" does not belong in
 * this enum: late equipment is meant to get more interesting, not harder to
 * kill, and the measured campaign arc is already bought mostly on the shooter's
 * side ({@code progression-nouns.md}).
 */
public enum IntegralSystemEffect {

    /**
     * A short movement boost paired with a brief directional resistance, on a
     * cooldown. Answers how a heavy suit crosses the doorway it exists to cross
     * without widening anyone's armour capacity.
     */
    BREACHER_ASSIST("breacher-assist"),

    /**
     * A small salvo fired from the suit's own shoulder mount against a
     * self-selected target, from finite onboard ammunition. Answers whether a
     * suit can put something downrange on its own, the opposite half of the
     * model from {@link #BREACHER_ASSIST} ({@code integral-armor-systems.md}).
     */
    MISSILE_POD("missile-pod");

    public final String key;

    IntegralSystemEffect(String key) {
        this.key = key;
    }

    public static IntegralSystemEffect fromKey(String key, String armorId) throws JSONException {
        for (IntegralSystemEffect effect : values()) {
            if (effect.key.equalsIgnoreCase(key)) return effect;
        }
        throw new JSONException("Armor '" + armorId + "' declares unknown integral system effect '"
                + key + "'. Known effects: " + knownKeys());
    }

    private static String knownKeys() {
        StringBuilder out = new StringBuilder();
        for (IntegralSystemEffect effect : values()) {
            if (out.length() > 0) out.append(", ");
            out.append(effect.key);
        }
        return out.toString();
    }
}
