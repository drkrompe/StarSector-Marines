package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * When {@link SpecialAiPolicy#HOLDING_A_FIRING_POSITION} has arrived for one
 * suit: the wearer has stopped where they mean to be, has something to shoot at
 * out where steadiness is worth having, and has nobody about to be on top of
 * them.
 *
 * <p>The two authored numbers are both judgements about the suit rather than
 * facts about the field. <b>How far out</b> a target has to be before planting
 * pays is a question about the weapon this pattern is built around — accuracy
 * falls off with distance, so a brace is worth most at the range where it is
 * needed and worth nothing across a room. <b>How close</b> a hostile may come
 * before planting is the wrong trade is a question about how badly this suit
 * suffers for being committed; a siege plate that was never going anywhere can
 * tolerate a contact that a militia vest cannot.
 *
 * <p>Everything else is a fact and is not authorable: whether the wearer is
 * under way, what they are engaging, how far off it is, and who else is near
 * them. A suit does not get an opinion about whether it has stopped walking.
 */
public record HoldingFiringPositionSpec(
        float minimumTargetRangeFraction,
        float breakOffRangeCells) implements IntegralSystemPolicySpec {

    @Override
    public SpecialAiPolicy aiPolicy() {
        return SpecialAiPolicy.HOLDING_A_FIRING_POSITION;
    }

    static HoldingFiringPositionSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        float fraction = IntegralSystemDef.positive(json, "minimumTargetRangeFraction", armorId);
        if (fraction >= 1f) {
            throw new JSONException("Integral system '" + systemId + "' on armor '" + armorId
                    + "' declares minimumTargetRangeFraction " + fraction + ", at or past the"
                    + " wearer's own reach: nothing they can engage would ever be far enough"
                    + " away and the stance would never be spent.");
        }
        return new HoldingFiringPositionSpec(fraction,
                IntegralSystemDef.positive(json, "breakOffRangeCells", armorId));
    }
}
