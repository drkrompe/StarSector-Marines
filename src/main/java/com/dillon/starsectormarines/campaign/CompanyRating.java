package com.dillon.starsectormarines.campaign;

/**
 * Player-facing band over the authoritative MRB credibility score.
 *
 * <p>This is presentation vocabulary, not a second reputation track. Contract
 * outcomes continue to mutate {@link CampaignState#playerMrbRep}; the Company HQ
 * translates that number into a stable mercenary-industry profile that can be read
 * without knowing the score's balance scale.
 */
public enum CompanyRating {
    COMPROMISED,
    UNPROVEN,
    PROVISIONAL,
    RECOGNIZED,
    ESTABLISHED,
    PREMIER;

    public static CompanyRating fromMrbCredibility(int credibility) {
        if (credibility < 0) return COMPROMISED;
        if (credibility == 0) return UNPROVEN;
        if (credibility < 10) return PROVISIONAL;
        if (credibility < 30) return RECOGNIZED;
        if (credibility < 60) return ESTABLISHED;
        return PREMIER;
    }
}
