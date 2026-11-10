package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;

/**
 * The shape a doll draws in a mount for the weapon it holds (`damage-doll.md`): a doll that carries these draws each
 * mount's weapon as {@code <g id="part-LOCATION.GLYPH">}, seen down its bore from in front, and shows only the one the
 * mount holds, in the mount's state colour. A chassis whose frames fit different guns on one body (the derelict
 * walkers' line, siege and vault frames) so shows what each one carries.
 */
enum MountGlyph {
    /** A multi-barrel rotary gun: a ring of bores. */
    ROTARY,
    /** One cannon bore. */
    CANNON,
    /** A laser's lens. */
    LASER,
    /** A small missile pack: two by two tubes. */
    SMALL_RACK,
    /** A large missile pack: three by three tubes. */
    LARGE_RACK;

    /** Separates a location from its glyph in a part's name. */
    static final char SEPARATOR = '.';

    /** The glyph for {@code weapon}; null for none. */
    static MountGlyph of(MechWeaponComponent weapon) {
        if (weapon == null) return null;
        return switch (weapon.hardpointType) {
            case MISSILE -> weapon.footprintRows >= 3 ? LARGE_RACK : SMALL_RACK;
            case ENERGY -> LASER;
            case BALLISTIC -> weapon.rotary() != null ? ROTARY : CANNON;
        };
    }

    /** The glyph a part's name ends in ({@code LEFT_ARM.CANNON}); null for a plain location. */
    static MountGlyph ofPart(String part) {
        int at = part.indexOf(SEPARATOR);
        if (at < 0) return null;
        String name = part.substring(at + 1);
        for (MountGlyph glyph : values()) if (glyph.name().equals(name)) return glyph;
        return null;
    }

    /** The location a glyph part stands in ({@code LEFT_ARM} of {@code LEFT_ARM.CANNON}); the name itself otherwise. */
    static String location(String part) {
        int at = part.indexOf(SEPARATOR);
        return at < 0 ? part : part.substring(0, at);
    }
}
