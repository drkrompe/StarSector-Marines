package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * When {@link SpecialAiPolicy#WOUNDED_SQUADMATE_IN_REACH} has arrived, and what
 * the dressing is worth ({@code integral-system-slate.md}).
 *
 * <p>Three authored numbers, and each is a judgement about the section rather
 * than a fact about the battle:
 *
 * <ul>
 *   <li>{@code reachCells} — how far the carrier will go to somebody. It is
 *       deliberately short. A medic is not a turret with a healing beam; the
 *       fiction is a marine kneeling next to another marine, and a long reach
 *       would quietly turn the section's medic into an area effect.
 *   <li>{@code treatBelowHealthFraction} — how badly hurt is worth a dressing.
 *       This is the whole of the waste guard: the satchel is finite, and a
 *       marine who has lost a scratch is not a casualty.
 *   <li>{@code restoredHealth} — what one dressing is worth, as flat health
 *       rather than a fraction. A fraction would make the same satchel worth
 *       more on a battlesuit than on a rifleman, which is backwards: a bandage
 *       is a bandage, and it goes further on somebody with less to lose.
 * </ul>
 *
 * <p>Nothing here says how many dressings the satchel holds. That is
 * {@link IntegralSystemDef#startingAmmo()}, shared with every other
 * ammunition-gated system, for the same reason the missile pod does not restate
 * its own salvo count.
 */
public record FieldAidSpec(float reachCells, float treatBelowHealthFraction, float restoredHealth)
        implements IntegralSystemPolicySpec {

    @Override
    public SpecialAiPolicy aiPolicy() {
        return SpecialAiPolicy.WOUNDED_SQUADMATE_IN_REACH;
    }

    static FieldAidSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        float reach = IntegralSystemDef.positive(json, "reachCells", armorId);
        float threshold = IntegralSystemDef.positive(json, "treatBelowHealthFraction", armorId);
        if (threshold > 1f) {
            throw new JSONException("Integral system '" + systemId + "' on armor '" + armorId
                    + "' authors treatBelowHealthFraction above 1, which would treat marines"
                    + " who are not hurt. It is a fraction of maximum health.");
        }
        float restored = IntegralSystemDef.positive(json, "restoredHealth", armorId);
        return new FieldAidSpec(reach, threshold, restored);
    }
}
