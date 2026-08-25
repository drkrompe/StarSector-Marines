package com.dillon.starsectormarines.marine;

import org.json.JSONException;

/** Closed gameplay-AI policy selected by a special-equipment definition. */
public enum SpecialAiPolicy {
    HARDENED_DIRECT_FIRE("hardened-direct-fire"),
    SOFT_CLUSTER_INDIRECT("soft-cluster-indirect"),
    SQUAD_SMOKE_SCREEN("squad-smoke-screen"),
    CONTACT_DEMOLITION("contact-demolition");

    public final String key;

    SpecialAiPolicy(String key) {
        this.key = key;
    }

    public static SpecialAiPolicy fromKey(String key, String equipmentId)
            throws JSONException {
        for (SpecialAiPolicy policy : values()) {
            if (policy.key.equalsIgnoreCase(key)) return policy;
        }
        throw new JSONException("Special equipment '" + equipmentId
                + "' has unknown AI policy '" + key + "'");
    }
}
