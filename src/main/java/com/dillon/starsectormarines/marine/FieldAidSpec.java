package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Marks a field-aid kit ({@code squad-medic.md}). The kit authors nothing of its
 * own here; its numbers are its entry in {@code squad-medicine.json}. Under an
 * officer without Field Medicine it makes its wearer a squad medic of the kit's
 * weaker form; under one who trains it, it complements the officer's medic,
 * feeding their pool and quickening their hands. Either way it is paid for out
 * of the medic's regenerating pool rather than a count of dressings.
 *
 * <p>The per-dressing keys of the satchel this replaced are rejected by name, so
 * an author meets the move rather than a kit that quietly ignores them.
 */
public record FieldAidSpec() implements IntegralSystemPolicySpec {

    private static final String[] RETIRED_KEYS = {
            "reachCells", "treatBelowHealthFraction", "restoredHealth", "startingAmmo"
    };

    @Override
    public SpecialAiPolicy aiPolicy() {
        return SpecialAiPolicy.WOUNDED_SQUADMATE_IN_REACH;
    }

    static FieldAidSpec parse(JSONObject json, String armorId, String systemId)
            throws JSONException {
        for (String key : RETIRED_KEYS) {
            if (json.has(key)) {
                throw new JSONException("Integral system '" + systemId + "' on armor '" + armorId
                        + "' declares '" + key + "', but a field-aid kit is a squad medic now: its"
                        + " numbers belong to the kit's entry in squad-medicine.json");
            }
        }
        return new FieldAidSpec();
    }
}
