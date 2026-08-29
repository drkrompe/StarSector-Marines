package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * When {@link SpecialAiPolicy#SIGHTED_STANDOFF_CONTACT} has arrived for one
 * suit: the wearer holds a clear, reachable line on a hostile, and that hostile
 * is no closer than the authored standoff.
 *
 * <p>The inner bound is the whole judgement. A payload delivered into something
 * already at arm's length wastes a use the rifle would have covered anyway, and
 * an explosive one lands its splash on the wearer's own side of the fight. So
 * the number here is an inner bound inside which the moment has already
 * passed, rather than a threshold something has to rise to meet.
 *
 * <p>The outer bound is not authored here: it is the referenced weapon's own
 * range, which {@code moddable-weapons-nouns.md} already owns. Restating it
 * would create a second place the same number could disagree with itself.
 */
public record SightedStandoffSpec(float minimumStandoffCells)
        implements IntegralSystemPolicySpec {

    @Override
    public SpecialAiPolicy aiPolicy() {
        return SpecialAiPolicy.SIGHTED_STANDOFF_CONTACT;
    }

    static SightedStandoffSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        if (!json.has("minimumStandoffCells")) {
            throw new JSONException("Integral system '" + systemId + "' on armor '" + armorId
                    + "' declares AI policy '" + SpecialAiPolicy.SIGHTED_STANDOFF_CONTACT.key
                    + "' and must author minimumStandoffCells: how close is too close to spend"
                    + " a delivered payload is a judgement about the suit, not a constant.");
        }
        return new SightedStandoffSpec(
                IntegralSystemDef.positive(json, "minimumStandoffCells", armorId));
    }
}
