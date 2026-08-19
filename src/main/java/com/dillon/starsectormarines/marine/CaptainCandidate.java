package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Frozen, campaign-source-owned offer for one future captain.
 *
 * <p>The candidate deliberately contains only XStream-friendly personnel data. Campaign
 * entities and interaction objects remain with the discovery source.
 */
public class CaptainCandidate implements Serializable {

    private static final String ID_NAMESPACE = "starsector_marines:captain_candidate:";

    private final String id;
    private final String sourceKey;
    private final String name;
    private final String portraitSprite;
    private final Rank startingRank;
    private final Trait startingTrait;
    private final float discoveredAtDay;
    private CaptainCandidateState state;

    CaptainCandidate(String sourceKey, String name, String portraitSprite,
                     Rank startingRank, Trait startingTrait, float discoveredAtDay) {
        this.id = idForSource(sourceKey);
        this.sourceKey = sourceKey;
        this.name = name;
        this.portraitSprite = portraitSprite;
        this.startingRank = startingRank;
        this.startingTrait = startingTrait;
        this.discoveredAtDay = Math.max(0f, discoveredAtDay);
        this.state = CaptainCandidateState.AVAILABLE;
    }

    public String id() { return id; }
    public String sourceKey() { return sourceKey; }
    public String name() { return name; }
    public String portraitSprite() { return portraitSprite; }
    public Rank startingRank() { return startingRank; }
    public Trait startingTrait() { return startingTrait; }
    public float discoveredAtDay() { return discoveredAtDay; }
    public CaptainCandidateState state() { return state; }

    boolean valid() {
        return hasNamespace(sourceKey)
                && id != null && id.equals(idForSource(sourceKey))
                && name != null && !name.trim().isEmpty()
                && startingRank != null
                && startingTrait != Trait.IDEALIST
                && startingTrait != Trait.CYNICAL
                && Float.isFinite(discoveredAtDay);
    }

    MarineCaptain createCaptain() {
        MarineCaptain captain = new MarineCaptain(
                id, name, portraitSprite, startingRank, discoveredAtDay);
        if (startingTrait != null) captain.traits().add(startingTrait);
        return captain;
    }

    void markAccepted() { state = CaptainCandidateState.ACCEPTED; }
    void markDeclined() { state = CaptainCandidateState.DECLINED; }

    private Object readResolve() {
        if (state == null) state = CaptainCandidateState.AVAILABLE;
        return this;
    }

    private static String idForSource(String sourceKey) {
        if (sourceKey == null) return null;
        return UUID.nameUUIDFromBytes(
                (ID_NAMESPACE + sourceKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static boolean hasNamespace(String sourceKey) {
        if (sourceKey == null) return false;
        int separator = sourceKey.indexOf(':');
        return separator > 0 && separator < sourceKey.length() - 1;
    }
}
