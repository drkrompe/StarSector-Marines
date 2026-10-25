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
    BOARDING_JACK_PIRATE,
    /** The swarm Brute: a heavier plated body, battering crest and crushing fore-claw. Appended. */
    XENO_BRUTE,
    /** The swarm Spitter: slim shoulders over an acid sac and a spout head; the Runner's feet and claws. Appended. */
    XENO_SPITTER;

    /**
     * Whether this family is a powered shell cut into a torso and two hinged shoulder layers
     * with its own feet, rather than one body plate over the shared infantry boots.
     */
    /**
     * How far the left shoulder layer turns about its hinge for each degree the torso turns away
     * from the neutral pose. The Boarding Jack's left slab is a hinged boarding shield that leads
     * with the torso, so it sweeps across the chest in aim, fire and brace; every other shell keeps
     * the authored shoulder track unchanged.
     */
    public float leftShoulderTurnGain() {
        return this == BOARDING_JACK || this == BOARDING_JACK_PIRATE ? 1f : 0f;
    }

    /**
     * Whether this family ships the four lying-body poses ({@code downed-0..3.png} beside its
     * body and head), so its fallen are drawn as themselves rather than through the unit type's
     * corpse sheet or its rig. Declared rather than probed: the game logs an error for every texture
     * it is asked to load that is not there.
     */
    public boolean hasDownedArt() {
        // Every infantry outfit falls as itself; the swarm bursts on its rig or lies on its type's sheet.
        return !isXeno();
    }

    /** Whether this is one of the swarm's chitin families, whose fallen are laid down by their rig or unit type. */
    public boolean isXeno() {
        return this == XENO || this == XENO_BRUTE || this == XENO_SPITTER;
    }

    /**
     * Whether this family ships forearm-and-hand layers ({@code station-arm-left.png} and
     * {@code station-arm-right.png}) drawn while it works a console or bench. Declared, not
     * probed, for the same reason as {@link #hasDownedArt}.
     */
    public boolean hasStationArms() {
        switch (this) {
            case ARMORLESS: case ARMY_GREEN: case CHARCOAL: case BLUE_SCOUT: case MILITIA:
            case OUTLAW: case RED_ELITE:
            case CIVILIAN_COLONIST: case ENGINEER: case SCIENTIST:
            case SHIP_COMMAND: case SHIP_ENGINEERING: case SHIP_MEDICAL: case SHIP_SERVICE:
                return true;
            default:
                return false;
        }
    }

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
            // The skitterer is drawn on the Runner's skeleton; the Runner's family stands in for its facing.
            case SWARM_WORKER:
            case SWARM_RUNNER: return XENO;
            case SWARM_BRUTE:
            // The Matriarch is drawn on its own skeleton; the Brute's family stands in for its facing.
            case SWARM_MATRIARCH: return XENO_BRUTE;
            case SWARM_SPITTER: return XENO_SPITTER;
            case MARINE:
            default: return CHARCOAL;
        }
    }

    public static LayeredArmorFamily fromOrdinal(int ordinal) {
        LayeredArmorFamily[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : CHARCOAL;
    }
}
