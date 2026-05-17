package com.dillon.starsectormarines.ops.intel;

import java.awt.Color;

/**
 * Derived defense rating for a planet/station. Computed from the presence of military
 * structures (Patrol HQ → High Command), orbital fortifications (Orbital Station →
 * Star Fortress), planetary shield, and stability — then bucketed into five tiers.
 *
 * <p>This is the bridge between the campaign's economy data and our gameplay-facing
 * "how hard is this fight" number. The mission generator uses it to derive a
 * {@code RiskLevel}; the battle layer can read it later to scale enemy unit counts.
 */
public enum DefenseLevel {
    UNDEFENDED("Undefended",    new Color(0xA0, 0xC0, 0xA0)),
    LIGHT     ("Light Defense", new Color(0xC0, 0xD0, 0x80)),
    MODERATE  ("Moderate",      new Color(0xE0, 0xC0, 0x70)),
    HEAVY     ("Heavy Defense", new Color(0xE0, 0x90, 0x60)),
    FORTRESS  ("Fortress",      new Color(0xE0, 0x60, 0x60));

    private final String displayName;
    private final Color  color;

    DefenseLevel(String displayName, Color color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() { return displayName; }
    public Color  color()       { return color; }
}
