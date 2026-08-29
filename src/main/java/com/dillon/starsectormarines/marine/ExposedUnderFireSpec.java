package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * When {@link SpecialAiPolicy#EXPOSED_UNDER_FIRE} has arrived for one suit: how
 * much incoming justifies the cooldown, and how much terrain cover is enough to
 * make spending it pointless.
 *
 * <p>Both numbers are authored per system because both are judgements about the
 * suit rather than facts about the battle. A screen on an eleven-second cycle
 * can afford to answer the first burst; one on a thirty-second cycle that is
 * gone in a single exchange cannot, and its wearer is better served waiting for
 * a moment that deserves it. Neither author should have to change the other's
 * suit to say so.
 *
 * <p><b>These are the whole of the waste guard.</b> The occasions themselves —
 * crossing ground, being outranged — are facts and are not authorable: a suit
 * does not get to believe it is not being shot at. What a suit gets to believe
 * is that this much fire, at this much exposure, is not yet worth its one card.
 */
public record ExposedUnderFireSpec(float incomingPressureThreshold, float maxCoverLevel)
        implements IntegralSystemPolicySpec {

    @Override
    public SpecialAiPolicy aiPolicy() {
        return SpecialAiPolicy.EXPOSED_UNDER_FIRE;
    }

    static ExposedUnderFireSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        require(json, "incomingPressureThreshold", armorId, systemId,
                "how much fire has to be landing before this is worth its cooldown");
        require(json, "maxCoverLevel", armorId, systemId,
                "how much cover already on the threat bearing makes raising it pointless");
        float threshold = IntegralSystemDef.positive(json, "incomingPressureThreshold", armorId);
        float maxCover = (float) json.getDouble("maxCoverLevel");
        if (maxCover < 0f) {
            throw new JSONException("Integral system '" + systemId + "' on armor '" + armorId
                    + "' authors a negative maxCoverLevel; 0 already means"
                    + " 'only in the open'.");
        }
        return new ExposedUnderFireSpec(threshold, maxCover);
    }

    private static void require(JSONObject json, String field, String armorId, String systemId,
                                String meaning) throws JSONException {
        if (json.has(field)) return;
        throw new JSONException("Integral system '" + systemId + "' on armor '" + armorId
                + "' declares AI policy '" + SpecialAiPolicy.EXPOSED_UNDER_FIRE.key
                + "' and must author " + field + ": " + meaning + " is a judgement about"
                + " the suit, not a constant.");
    }
}
