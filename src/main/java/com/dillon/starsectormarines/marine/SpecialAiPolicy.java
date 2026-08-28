package com.dillon.starsectormarines.marine;

import org.json.JSONException;

/** Closed gameplay-AI policy selected by a special-equipment definition. */
public enum SpecialAiPolicy {
    HARDENED_DIRECT_FIRE("hardened-direct-fire"),
    SOFT_CLUSTER_INDIRECT("soft-cluster-indirect"),
    SQUAD_SMOKE_SCREEN("squad-smoke-screen"),
    CONTACT_DEMOLITION("contact-demolition"),
    /**
     * Placed ordnance denial: the carrier sets down a static emplacement that
     * engages hostile warheads crossing a bounded radius around it. It never
     * selects an actor, never spots, and never joins a squad — it refuses
     * incoming ordnance and nothing else.
     */
    AREA_DENIAL_EMPLACEMENT("area-denial-emplacement"),
    /**
     * Sustained anti-hard contact work: a visible, interruptible channel
     * against an adjacent hardened actor, or against an authored breach point
     * the carrier is already standing beside. It never seeks obstacles.
     */
    CONTACT_BREACH_CHANNEL("contact-breach-channel"),
    /**
     * Short anti-personnel reaction: one strike against an adjacent living
     * infantry contact. It cannot select a turret, hub, mech, vehicle, wall,
     * or a target the carrier cannot honestly reach.
     */
    CONTACT_REACTION_STRIKE("contact-reaction-strike");

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
