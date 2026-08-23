package com.dillon.starsectormarines.ops;

import java.awt.Color;

/**
 * Mission operation categories shown on the tactical map. Each maps to a
 * color + single-letter glyph for the node marker, plus an i18n key for the
 * full display name in the hover popup.
 *
 * <p>Each type also declares its place on the scale axis: a
 * {@link #tierFloor} below which it is not offered, and a
 * {@link #defenderWeight} scaling {@code OperationTier.defenderBase} into an
 * actual defender count. Together these replace the fifteen hand-written
 * numbers in {@code DefenderRoster.totalFor}, which encoded tier inside type
 * and so could not express a late-game raid or a beginner's conquest — the
 * latter correctly, the former by accident. See `mission-tier-nouns.md`.
 */
public enum MissionType {

    ASSAULT   ("missionTypeAssault",    'A', new Color(0xE0, 0x70, 0x70),
               OperationTier.FIRST_CONTRACT, 0.75f),
    SABOTAGE  ("missionTypeSabotage",   'S', new Color(0xE0, 0xB0, 0x70),
               OperationTier.FIRST_CONTRACT, 0.45f),
    RAID      ("missionTypeRaid",       'R', new Color(0xC0, 0x90, 0xE0),
               OperationTier.FIRST_CONTRACT, 0.60f),
    EXTRACTION("missionTypeExtraction", 'E', new Color(0x70, 0xC0, 0xE0),
               OperationTier.FIRST_CONTRACT, 0.65f),
    /**
     * Taking and holding a world. Late-game by nature, which is why it floors
     * at {@link OperationTier#REINFORCED} — a "low risk conquest" was never a
     * coherent thing to offer.
     */
    CONQUEST  ("missionTypeConquest",   'C', new Color(0xB0, 0x50, 0x50),
               OperationTier.REINFORCED, 1.00f);

    public final String displayKey;
    public final char   glyph;
    public final Color  color;
    /** Lowest tier this type is offered at. */
    public final OperationTier tierFloor;
    /** Scales the tier's defender curve — a sabotage is smaller than a conquest at any tier. */
    public final float defenderWeight;

    MissionType(String displayKey, char glyph, Color color,
                OperationTier tierFloor, float defenderWeight) {
        this.displayKey = displayKey;
        this.glyph = glyph;
        this.color = color;
        this.tierFloor = tierFloor;
        this.defenderWeight = defenderWeight;
    }
}
