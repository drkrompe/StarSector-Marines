package com.dillon.starsectormarines.marine;

/** Value-only launch snapshot: no live roster handles cross into an operation. */
public record OfficerCommandProfile(String id, String name, Rank rank, int xp,
        Status status, boolean naturalLeader, int cover, int recovery, int salvage) {
    public static OfficerCommandProfile capture(MarineCaptain captain) {
        if (captain == null) return null;
        return new OfficerCommandProfile(captain.id(), captain.name(), captain.rank(),
                captain.xp(), captain.status(), captain.traits().contains(Trait.NATURAL_LEADER),
                captain.career().level(OfficerSpecialty.TACTICAL_COVER),
                captain.career().level(OfficerSpecialty.RECOVERY_PLANNING),
                captain.career().level(OfficerSpecialty.SALVAGE_STEWARDSHIP));
    }
    public float incomingAccuracyMultiplier() { return 1f - 0.05f * cover; }
    public float recoveryMultiplier() { return 1f - 0.15f * recovery; }
}
