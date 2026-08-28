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
 * the number here reads the opposite way to
 * {@link CrossingUnderFireSpec#threatRadiusCells()} — that one is an outer
 * bound past which the moment has not arrived, this one an inner bound inside
 * which it has already passed.
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
