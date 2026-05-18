package com.dillon.starsectormarines.ops;

import java.awt.Color;

/**
 * Mission operation categories shown on the tactical map. Each maps to a
 * color + single-letter glyph for the node marker, plus an i18n key for the
 * full display name in the hover popup.
 */
public enum MissionType {

    ASSAULT   ("missionTypeAssault",    'A', new Color(0xE0, 0x70, 0x70)),
    SABOTAGE  ("missionTypeSabotage",   'S', new Color(0xE0, 0xB0, 0x70)),
    RAID      ("missionTypeRaid",       'R', new Color(0xC0, 0x90, 0xE0)),
    EXTRACTION("missionTypeExtraction", 'E', new Color(0x70, 0xC0, 0xE0)),
    CONQUEST  ("missionTypeConquest",   'C', new Color(0xB0, 0x50, 0x50));

    public final String displayKey;
    public final char   glyph;
    public final Color  color;

    MissionType(String displayKey, char glyph, Color color) {
        this.displayKey = displayKey;
        this.glyph = glyph;
        this.color = color;
    }
}
