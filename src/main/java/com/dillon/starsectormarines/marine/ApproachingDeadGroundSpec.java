package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * When {@link SpecialAiPolicy#APPROACHING_DEAD_GROUND} has arrived for one
 * suit: the wearer is moving, and the ground this far along their heading is
 * ground they cannot presently see into.
 *
 * <p>The one authored number is how far ahead of themselves this suit calls
 * "ahead". A scout edging up on a doorway and a scout crossing open ground
 * toward a distant block are the same situation at two scales, and which of
 * them is worth spending a sweep on is a judgement about the suit's sensors
 * rather than a constant in the code that spends them.
 *
 * <p><b>The test is the wearer's own line of sight, never the player's fog.</b>
 * Fog of war is presentation authority and must not become a simulation input
 * ({@code fog-of-war-nouns.md}); a policy that triggered off the reveal bitmap
 * would make what the player has already been shown decide what a marine does,
 * which is that law inverted.
 */
public record ApproachingDeadGroundSpec(float lookaheadCells)
        implements IntegralSystemPolicySpec {

    @Override
    public SpecialAiPolicy aiPolicy() {
        return SpecialAiPolicy.APPROACHING_DEAD_GROUND;
    }

    static ApproachingDeadGroundSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        if (!json.has("lookaheadCells")) {
            throw new JSONException("Integral system '" + systemId + "' on armor '" + armorId
                    + "' declares AI policy '" + SpecialAiPolicy.APPROACHING_DEAD_GROUND.key
                    + "' and must author lookaheadCells: how far ahead of itself a suit calls"
                    + " 'ahead' is a judgement about that suit's sensors, not a constant.");
        }
        return new ApproachingDeadGroundSpec(
                IntegralSystemDef.positive(json, "lookaheadCells", armorId));
    }
}
