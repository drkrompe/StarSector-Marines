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
    BREACHER_ASSIST("breacher-assist", "Breach assist",
            "graphics/ui/armory/system-breach-assist.png"),

    /**
     * A small salvo fired from the suit's own shoulder mount against a
     * self-selected target, from finite onboard ammunition. Answers whether a
     * suit can put something downrange on its own, the opposite half of the
     * model from {@link #BREACHER_ASSIST} ({@code integral-armor-systems.md}).
     */
    MISSILE_POD("missile-pod", "Missile pod",
            "graphics/ui/armory/system-missile-pod.png"),

    /**
     * A brief, wide, partly wall-tolerant read of the ground around the wearer,
     * contributed to the player's picture as a temporary observer and gone when
     * the window closes ({@code fog-of-war-nouns.md}). Answers what a suit built
     * around sensors spends those sensors on, and it is deliberately the one
     * effect here that changes nothing about a fight except what the player can
     * see of it — a sweep that were worth damage would have stopped being
     * perception.
     */
    PERCEPTION_SWEEP("perception-sweep", "Sensor sweep",
            "graphics/ui/armory/system-sensor-sweep.png");

    public final String key;

    /**
     * The family name every pattern's take on this effect shares. Six suits
     * carry a breach assist and each calls its own version something else; the
     * family is what lets a player see they are the same capability rather than
     * six unrelated tricks ({@code integral-system-slate.md}).
     */
    public final String displayName;

    /** One icon per family. A pattern's own version is told apart by name and grade, not by art. */
    public final String iconPath;

    IntegralSystemEffect(String key, String displayName, String iconPath) {
        this.key = key;
        this.displayName = displayName;
        this.iconPath = iconPath;
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
