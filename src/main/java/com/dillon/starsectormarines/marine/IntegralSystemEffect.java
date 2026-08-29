package com.dillon.starsectormarines.marine;

import org.json.JSONException;

/**
 * What an armour pattern's integral system actually does
 * ({@code integral-armor-systems.md}).
 *
 * <p>Every value here must express itself as <b>behaviour</b> — movement,
 * protection with a clock on it, a delivered payload, perception, care. An
 * effect whose whole contribution is "more effective hit points" does not
 * belong in this enum: late equipment is meant to get more interesting, not
 * harder to kill, and the measured campaign arc is already bought mostly on the
 * shooter's side ({@code progression-nouns.md}).
 *
 * <p>{@link #FIELD_AID} sits closest to that line and stays the right side of
 * it. What it buys is not the wearer surviving longer — the wearer gains
 * nothing at all — but a <em>squadmate</em> getting back up, out of a finite
 * satchel, by a marine who had to be next to them to do it. It undoes damage
 * already taken rather than pre-empting damage to come, and it is spent on
 * somebody else. A system that healed its own wearer passively would be the
 * thing this rule forbids.
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
            "graphics/ui/armory/system-sensor-sweep.png"),

    /**
     * Treatment for a wounded squadmate within arm's reach, out of a finite
     * satchel. Answers what the marine carrying the section's heavy load is
     * carrying besides ammunition, and it is the only effect here whose whole
     * value lands on somebody other than the wearer.
     *
     * <p>Deliberately treats the walking wounded rather than the dead. A marine
     * in this simulation is alive or dead with nothing in between, so a system
     * built around recovering casualties would need a concept that does not
     * exist yet; a system built around the trooper still on their feet at a
     * third of their health needs nothing new at all, and that trooper is
     * already there in every battle ({@code integral-system-slate.md}).
     */
    FIELD_AID("field-aid", "Field aid",
            "graphics/ui/armory/system-field-aid.png");

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
