package com.dillon.starsectormarines.battle.appearance;

import com.dillon.starsectormarines.battle.unit.UnitType;

/** Stable tier-neutral ids for independently swappable modular body/head art. */
public enum LayeredArmorFamily {
    CHARCOAL,
    BLUE_SCOUT,
    RED_ELITE,
    OUTLAW,
    ARMY_GREEN,
    MILITIA,
    /** Cloth fatigues and a bare head; legacy ordinal 6 must remain stable. */
    ARMORLESS,
    /** Grimy frontier workwear and an uncovered colonist head. */
    CIVILIAN_COLONIST,
    /** Reinforced industrial workwear and a sealed hazard helmet. */
    ENGINEER,
    /** Worn field-lab clothing and a compact research headset. */
    SCIENTIST,
    /** Dark chitinous body, skull carapace, and digitigrade feet for alien actors. */
    XENO,
    /** Additive faction patterns append after every persisted legacy ordinal. */
    AEGIS_COMPOSITE,
    PALATINE,
    FURNACE_LINE,
    REAVER,
    SPECTER_HEAVY,
    BULWARK_HEAVY,
    RELIQUARY_HEAVY,
    LIONS_MANTLE,
    FOUNDRY_BREAKER,
    SHIP_COMMAND,
    SHIP_ENGINEERING,
    SHIP_MEDICAL,
    SHIP_SERVICE,
    /** Articulated support shell; appended to preserve persisted appearance ordinals. */
    ARBALEST_HEAVY,
    /**
     * Pirate rebuilt boarding frame: welded mismatched plate on the shell registration Specter
     * established, so the same pose tracks articulate it. Appended for ordinal stability.
     */
    BOARDING_JACK,
    /**
     * The same rig in Greyhook's own paint: soot plate, scraped and scorched, with crude red crew
     * markings. Raiders wear it; the company's issue keeps {@link #BOARDING_JACK}. Appended for
     * ordinal stability.
     */
    BOARDING_JACK_PIRATE;

    /**
     * Whether this family is a powered shell cut into a torso and two hinged shoulder layers
     * with its own feet, rather than one body plate over the shared infantry boots.
     */
    /**
     * A painted family's own copy of a shared integral-system carrier, or {@code null} to draw the
     * shared one. Greyhook's paint carries its own Latch screen so a raider's shield matches its rig.
     */
    public String repaintedCarrier(String spritePath) {
        if (this != BOARDING_JACK_PIRATE || spritePath == null || !spritePath.endsWith(LATCH_SCREEN)) return null;
        return spritePath.substring(0, spritePath.length() - ".png".length()) + "-pirate.png";
    }

    private static final String LATCH_SCREEN = "/equipment/latch-screen.png";

    public boolean articulatedShoulders() {
        return this == SPECTER_HEAVY || this == ARBALEST_HEAVY || this == BOARDING_JACK
                || this == BOARDING_JACK_PIRATE;
    }

    public static LayeredArmorFamily spawnDefault(UnitType type) {
        switch (type) {
            case MARINE_BLUE: return BLUE_SCOUT;
            case MARINE_RED: return OUTLAW;
            case MILITIA: return MILITIA;
            case CIVILIAN: return CIVILIAN_COLONIST;
            case ENGINEER:
            // The same workwear, because it is the same person. What a sidearm
            // changes is what they do when shot at, not what they are wearing.
            case TECHNICIAN: return ENGINEER;
            case SCIENTIST: return SCIENTIST;
            case ALIEN:
            case SWARM_RUNNER: return XENO;
            case MARINE:
            default: return CHARCOAL;
        }
    }

    public static LayeredArmorFamily fromOrdinal(int ordinal) {
        LayeredArmorFamily[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CHARCOAL;
    }
}
