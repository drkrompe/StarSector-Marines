package com.dillon.starsectormarines.marine;

import org.json.JSONException;

/** Typed battle channel used by one item in a marine's special-equipment slot. */
public enum SpecialActivation {
    DIRECT_EXPLOSIVE("direct-explosive"),
    DIRECT_PRECISION("direct-precision"),
    ARC_EXPLOSIVE("arc-explosive"),
    UTILITY_SMOKE("utility-smoke"),
    UTILITY_SATCHEL("utility-satchel"),
    /**
     * Utility activation whose executor places a persistent, bounded object on
     * the field instead of resolving a shot. The carrier commits to a placement
     * channel; what the placed object then does is decided by the item's
     * {@link SpecialAiPolicy}, never by this activation alone.
     */
    UTILITY_DEPLOYABLE("utility-deployable"),
    /**
     * Weapon-like activation whose executor replaces the travelling shot with
     * an adjacent contact test. One typed channel serves every close-contact
     * family; which contacts are legal is decided by the item's
     * {@link SpecialAiPolicy}, never by this activation alone.
     */
    CLOSE_CONTACT("close-contact");

    public final String key;

    SpecialActivation(String key) {
        this.key = key;
    }

    public static SpecialActivation fromKey(String key, String equipmentId)
            throws JSONException {
        for (SpecialActivation activation : values()) {
            if (activation.key.equalsIgnoreCase(key)) return activation;
        }
        throw new JSONException("Special equipment '" + equipmentId
                + "' has unknown activation type '" + key + "'");
    }
}
