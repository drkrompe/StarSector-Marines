package com.dillon.starsectormarines.marine;

/** Value-only launch snapshot: no live roster handles cross into an operation. */
public record OfficerCommandProfile(String id, String name, Rank rank, int xp,
        Status status, boolean naturalLeader, int cover, int medicine, boolean founding) {
    public OfficerCommandProfile(String id, String name, Rank rank, int xp, Status status,
                                 boolean naturalLeader, int cover, int medicine) {
        this(id, name, rank, xp, status, naturalLeader, cover, medicine, false);
    }
    public static OfficerCommandProfile capture(MarineCaptain captain) {
        if (captain == null) return null;
        return new OfficerCommandProfile(captain.id(), captain.name(), captain.rank(),
                captain.xp(), captain.status(), captain.traits().contains(Trait.NATURAL_LEADER),
                captain.career().level(OfficerSpecialty.TACTICAL_COVER),
                captain.career().level(OfficerSpecialty.FIELD_MEDICINE),
                captain.founding());
    }
    public float incomingAccuracyMultiplier() { return 1f - 0.05f * cover; }
}
