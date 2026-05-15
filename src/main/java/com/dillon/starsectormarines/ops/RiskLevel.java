package com.dillon.starsectormarines.ops;

import java.awt.Color;

/**
 * Risk tier of a mission. Used for color coding in the popup and (later) for
 * tying difficulty modifiers to mission resolution. Requirements text is
 * displayed separately and currently free-form.
 */
public enum RiskLevel {

    LOW   ("riskLow",    new Color(0x9C, 0xCC, 0x9C)),
    MEDIUM("riskMedium", new Color(0xE0, 0xB0, 0x70)),
    HIGH  ("riskHigh",   new Color(0xE0, 0x70, 0x70));

    public final String displayKey;
    public final Color  color;

    RiskLevel(String displayKey, Color color) {
        this.displayKey = displayKey;
        this.color = color;
    }
}
