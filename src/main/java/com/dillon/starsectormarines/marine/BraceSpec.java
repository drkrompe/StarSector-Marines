package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * The brace's authored numbers: how much steadier the suit shoots while it is
 * planted, and how much of its own mobility it gives up to be planted
 * ({@code integral-system-slate.md}).
 *
 * <p><b>The cost is the design.</b> A stance that only helps is a stat with
 * extra steps, so {@code moveSpeedMult} is validated to be a real reduction
 * rather than a rounding error — being braced in the wrong place has to be a
 * mistake worth making. Both numbers run on the system's own clock and the
 * stance releases itself; nothing external clears it.
 *
 * <p><b>Never durability, and never a second breaching assist.</b> What the
 * brace improves is what the wearer <em>hits</em>, which is behaviour. It may
 * not reduce what hits the wearer — that is mitigation, owned by
 * {@code combat-durability-nouns.md}, and a stance that quietly duplicated it
 * would be the forbidden thing wearing a third costume. It may not raise a
 * screen or speed the wearer up either: the breach family already owns timed
 * movement, and committing to a spot is the entire distinction between the two
 * ({@code assault crosses, line holds}).
 */
public record BraceSpec(float moveSpeedMult, float accuracyMult) implements Serializable {

    /**
     * Keys that would make a brace a breaching assist. Refused by name so the
     * split between the two families is a parse failure with a reason on it
     * rather than a slow blur nobody notices.
     */
    private static final String[] FORBIDDEN_BREACH_KEYS = {
            "screenSoak", "shieldedArcDegrees", "incomingAccuracyMult"
    };

    static BraceSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        for (String key : FORBIDDEN_BREACH_KEYS) {
            if (json.has(key)) {
                throw new JSONException("Brace '" + systemId + "' on armor '" + armorId
                        + "' declares '" + key + "'. A brace improves what the wearer hits and"
                        + " nothing else: raising a screen or refusing incoming fire is the"
                        + " breach family's half of the model, and a stance that did both would"
                        + " be one pattern carrying two capabilities"
                        + " (integral-system-slate.md).");
            }
        }
        float moveSpeedMult = IntegralSystemDef.positive(json, "moveSpeedMult", armorId);
        if (moveSpeedMult >= 1f) {
            throw new JSONException("Brace '" + systemId + "' on armor '" + armorId
                    + "' must cost the wearer their mobility: moveSpeedMult must be below 1."
                    + " A stance that is free to hold is a stat with extra steps, and one that"
                    + " sped the wearer up would be a breaching assist under another name.");
        }
        float accuracyMult = IntegralSystemDef.positive(json, "accuracyMult", armorId);
        if (accuracyMult <= 1f) {
            throw new JSONException("Brace '" + systemId + "' on armor '" + armorId
                    + "' must steady the wearer's fire: accuracyMult must exceed 1.");
        }
        return new BraceSpec(moveSpeedMult, accuracyMult);
    }
}
