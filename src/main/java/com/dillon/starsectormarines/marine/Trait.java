package com.dillon.starsectormarines.marine;

/**
 * Captain trait — granted on recruitment or as a level-up reward. Mechanics aren't wired
 * yet; this enum just defines the slots so the {@link MarineCaptain} POJO has somewhere to
 * store them across saves.
 */
public enum Trait {
    /** Bonus vs fortified ground targets. */
    SIEGE_SPECIALIST,
    /** Reduces collateral damage / civilian casualties on raids. */
    SAPPER,
    /** Returns more recon info before the raid resolves. */
    SCOUT,
    /** Reduces marine casualty rate during the raid. */
    FIELD_MEDIC,
    /** Bonuses operating ground vehicles / mechs. */
    COMBAT_ENGINEER,
    /** Generic combat bonus — flat raid power multiplier. */
    VETERAN,
    /** Faster XP gain. */
    NATURAL_LEADER,
    /** Larger effective squad cap (works around rank ceiling). */
    LOGISTICS_CHIEF
}
