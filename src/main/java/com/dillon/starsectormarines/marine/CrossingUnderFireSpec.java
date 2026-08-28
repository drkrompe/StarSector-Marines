package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * When {@link SpecialAiPolicy#CROSSING_UNDER_FIRE} has arrived for one suit:
 * the wearer is moving, and a hostile is inside the authored threat radius.
 *
 * <p>The radius is deliberately wider than a marine's own reach. The moment is
 * "getting somewhere while being shot at", so the threat that justifies it is
 * one that can already shoot at the crossing rather than one already in the
 * carrier's face.
 *
 * <p>Authored per system because the judgement is per suit. A crude industrial
 * rig committing everything to one shove wants a different answer from a
 * corporate suit that can afford to wait for a better moment, and neither
 * author should have to change the other's suit to express that.
 */
public record CrossingUnderFireSpec(float threatRadiusCells)
        implements IntegralSystemPolicySpec {

    @Override
    public SpecialAiPolicy aiPolicy() {
        return SpecialAiPolicy.CROSSING_UNDER_FIRE;
    }

    static CrossingUnderFireSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        if (!json.has("threatRadiusCells")) {
            throw new JSONException("Integral system '" + systemId + "' on armor '" + armorId
                    + "' declares AI policy '" + SpecialAiPolicy.CROSSING_UNDER_FIRE.key
                    + "' and must author threatRadiusCells: how close a hostile has to be for"
                    + " crossing to be worth spending this on is a judgement about the suit,"
                    + " not a constant.");
        }
        return new CrossingUnderFireSpec(
                IntegralSystemDef.positive(json, "threatRadiusCells", armorId));
    }
}
