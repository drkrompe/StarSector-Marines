package com.dillon.starsectormarines.ops;

/**
 * A contract offered to the player. {@link #normalizedX}/{@link #normalizedY}
 * are 0..1 within the tactical map area — the tactical panel converts them to
 * absolute screen coords at render time. Long-term {@code MissionGenerator}
 * will place them based on texture analysis; for now they're a deterministic
 * seeded scatter.
 */
public final class Mission {

    public final String      id;
    public final String      name;
    public final MissionType type;
    public final int         payout;
    public final RiskLevel   risk;
    public final String      requirements;
    public final float       normalizedX;
    public final float       normalizedY;

    public Mission(String id,
                   String name,
                   MissionType type,
                   int payout,
                   RiskLevel risk,
                   String requirements,
                   float normalizedX,
                   float normalizedY) {
        this.id           = id;
        this.name         = name;
        this.type         = type;
        this.payout       = payout;
        this.risk         = risk;
        this.requirements = requirements;
        this.normalizedX  = normalizedX;
        this.normalizedY  = normalizedY;
    }
}
