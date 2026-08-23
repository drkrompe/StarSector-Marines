package com.dillon.starsectormarines.ops;

import java.awt.Color;

/**
 * How far a job may deviate from what its {@link OperationTier} promises —
 * the <b>variance</b> axis.
 *
 * <p>Scale moved to {@link OperationTier}: map size, lift and the defender
 * curve are the tier's business now. What stays here is everything about
 * uncertainty — defender quality, the elite fraction, patrol strength, loot,
 * and a modest {@link #forceMult} nudge so a high-risk job at a given tier
 * really does field more than the brochure said. See
 * `mission-tier-nouns.md`.
 */
public enum RiskLevel {

    LOW   ("riskLow",    new Color(0x9C, 0xCC, 0x9C), 0.85f),
    MEDIUM("riskMedium", new Color(0xE0, 0xB0, 0x70), 1.00f),
    HIGH  ("riskHigh",   new Color(0xE0, 0x70, 0x70), 1.15f);

    public final String displayKey;
    public final Color  color;
    /**
     * Nudge on the tier's defender count. Deliberately small — risk colours
     * a tier, it does not replace it. A HIGH job is a fifteen-percent
     * surprise, not a different operation.
     */
    public final float forceMult;

    RiskLevel(String displayKey, Color color, float forceMult) {
        this.displayKey = displayKey;
        this.color = color;
        this.forceMult = forceMult;
    }
}
