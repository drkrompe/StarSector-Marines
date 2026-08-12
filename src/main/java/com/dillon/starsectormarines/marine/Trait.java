package com.dillon.starsectormarines.marine;

/**
 * Captain trait — granted on recruitment or as a level-up reward. Most mechanics are not
 * wired yet; this enum defines stable save identities as each trait comes online.
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
    LOGISTICS_CHIEF,
    /** Improves post-battle recovery and adds a high-value salvage roll. */
    SALVAGE_EXPERT
}
