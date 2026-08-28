package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * The breacher assist's authored numbers: how much faster the suit moves while
 * the system is running, and how much damage the screen it raises will absorb
 * from the front before it breaks ({@code integral-armor-systems.md}).
 *
 * <p>{@code screenSoak} is <b>mitigation</b> in the sense
 * {@code combat-durability-nouns.md} owns — a bounded pool of incoming damage
 * absorbed, for an explicit duration, across a bounded arc. It is a quantity
 * rather than a share, which is what stops a screen being a soft invulnerability
 * window: concentrated fire spends it, and the screen ends. It is not a second
 * damage path and not armour capacity in a costume — when the pool empties or
 * the clock runs out, the suit is exactly the suit it was.
 *
 * <p>The arc matters as much as the number. A breacher in a doorway is covered
 * against what is in front of them and no better off from the flank than
 * anyone else, which is what keeps the entry a squad problem rather than a solo
 * trick.
 */
public record BreacherAssistSpec(
        float moveSpeedMult,
        float screenSoak,
        float shieldedArcDegrees) implements Serializable {

    static BreacherAssistSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        float moveSpeedMult = IntegralSystemDef.positive(json, "moveSpeedMult", armorId);
        if (moveSpeedMult <= 1f) {
            throw new JSONException("Breacher assist '" + systemId + "' on armor '" + armorId
                    + "' must speed the suit up: moveSpeedMult must exceed 1");
        }
        if (json.has("frontalResistance")) {
            throw new JSONException("Breacher assist '" + systemId + "' on armor '" + armorId
                    + "' declares 'frontalResistance'. A screen is a soak pool rather than a"
                    + " refused fraction — author 'screenSoak' as an amount of damage the"
                    + " screen absorbs before it breaks (combat-durability-nouns.md).");
        }
        float screenSoak = IntegralSystemDef.positive(json, "screenSoak", armorId);
        float arc = IntegralSystemDef.positive(json, "shieldedArcDegrees", armorId);
        if (arc >= 360f) {
            throw new JSONException("Breacher assist '" + systemId + "' on armor '" + armorId
                    + "' must leave a flank: shieldedArcDegrees must be below 360");
        }
        return new BreacherAssistSpec(moveSpeedMult, screenSoak, arc);
    }
}
