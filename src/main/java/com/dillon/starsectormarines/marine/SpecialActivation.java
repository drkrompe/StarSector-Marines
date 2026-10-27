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
     * Utility activation whose carrier works a radio rather than a weapon: the item resolves no shot and places
     * nothing, it lets its carrier direct their side's air pool onto ground (see {@link SpecialAiPolicy#FORWARD_OBSERVER}).
     */
    UTILITY_COMMS("utility-comms"),
    /**
     * A salvo from a pod on the carrier's shoulder, which picks its own target and fires on its own clock. It never
     * runs through the hand's channel: the carrier keeps holding, and firing, the primary throughout. Ammunition and
     * cooldown are the slot's own; the moment is {@link SpecialAiPolicy#SIGHTED_STANDOFF_CONTACT}.
     */
    SHOULDER_POD("shoulder-pod");

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
