package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * The breacher assist's authored numbers: how much faster the suit moves while
 * the system is running, and how much of an attack it turns aside from the
 * front ({@code integral-armor-systems.md}).
 *
 * <p>{@code frontalResistance} is <b>mitigation</b> in the sense
 * {@code combat-durability-nouns.md} owns — a bounded fraction of incoming
 * damage refused, for an explicit duration, across a bounded arc. It is not a
 * second damage path and not armour capacity in a costume: when the clock runs
 * out the suit is exactly the suit it was.
 *
 * <p>The arc matters as much as the number. A breacher in a doorway is covered
 * against what is in front of them and no better off from the flank than
 * anyone else, which is what keeps the entry a squad problem rather than a solo
 * trick.
 */
public record BreacherAssistSpec(
        float moveSpeedMult,
        float frontalResistance,
        float shieldedArcDegrees) implements Serializable {

    static BreacherAssistSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        float moveSpeedMult = IntegralSystemDef.positive(json, "moveSpeedMult", armorId);
        if (moveSpeedMult <= 1f) {
            throw new JSONException("Breacher assist '" + systemId + "' on armor '" + armorId
                    + "' must speed the suit up: moveSpeedMult must exceed 1");
        }
        float frontalResistance = IntegralSystemDef.positive(json, "frontalResistance", armorId);
        if (frontalResistance >= 1f) {
            throw new JSONException("Breacher assist '" + systemId + "' on armor '" + armorId
                    + "' must stay penetrable: frontalResistance must be below 1");
        }
        float arc = IntegralSystemDef.positive(json, "shieldedArcDegrees", armorId);
        if (arc >= 360f) {
            throw new JSONException("Breacher assist '" + systemId + "' on armor '" + armorId
                    + "' must leave a flank: shieldedArcDegrees must be below 360");
        }
        return new BreacherAssistSpec(moveSpeedMult, frontalResistance, arc);
    }
}
