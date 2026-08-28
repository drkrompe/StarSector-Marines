package com.dillon.starsectormarines.marine;

import org.json.JSONException;

/**
 * Closed gameplay-AI policy selected by an equipment definition — a carried
 * special item or an armour pattern's integral system.
 *
 * <p><b>A policy names a moment, not an item.</b> Every member answers the same
 * question: what has to be true for spending this to be a good idea. The
 * earliest members read like descriptions of the thing being spent because the
 * thing and the moment coincided — a demolition charge is for a contact you are
 * standing beside. An integral system broke that coincidence, because a
 * self-directed capability has nothing to aim at and only a situation it suits,
 * so the later members are named for the situation outright. A policy named
 * after its effect would be a second vocabulary wearing this one's clothes.
 *
 * <p><b>One vocabulary, two carriers.</b> "Special" names where this vocabulary
 * started rather than the only place it is spoken; an integral system is a
 * second carrier for it, not a second enum
 * ({@code progression-nouns.md}). Membership alone grants nothing: each carrier
 * validates policy against its own declared effect at parse time, so a policy
 * that makes no sense for what it accompanies is refused before load rather
 * than ignored at runtime.
 */
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
    CONTACT_REACTION_STRIKE("contact-reaction-strike"),
    /**
     * Crossing ground that is expensive to cross: the carrier is actually
     * moving and something hostile is near enough to make the crossing costly.
     * Names the moment rather than the capability spent on it, because what is
     * spent may have no target at all — the situation is the whole trigger.
     */
    CROSSING_UNDER_FIRE("crossing-under-fire"),
    /**
     * A hostile sighted at standoff: something the carrier holds a clear,
     * reachable line on, far enough off that a delivered payload is a better
     * answer than the weapon already in their hands. Holding fire on a contact
     * that is already on top of them is the point, not an omission.
     */
    SIGHTED_STANDOFF_CONTACT("sighted-standoff-contact");

    public final String key;

    SpecialAiPolicy(String key) {
        this.key = key;
    }

    /**
     * @param equipmentId the carried item or integral system that declared the
     *                    key, named in the failure so an author knows which
     *                    catalog entry to fix.
     */
    public static SpecialAiPolicy fromKey(String key, String equipmentId)
            throws JSONException {
        for (SpecialAiPolicy policy : values()) {
            if (policy.key.equalsIgnoreCase(key)) return policy;
        }
        throw new JSONException("Equipment '" + equipmentId
                + "' has unknown AI policy '" + key + "'. Known policies: " + keysOf(values()));
    }

    /** The declarable keys of a given set of policies, for a targeted parse failure. */
    public static String keysOf(SpecialAiPolicy... policies) {
        StringBuilder out = new StringBuilder();
        for (SpecialAiPolicy policy : policies) {
            if (out.length() > 0) out.append(", ");
            out.append(policy.key);
        }
        return out.toString();
    }
}
