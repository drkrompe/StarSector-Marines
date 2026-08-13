package com.dillon.starsectormarines.marine;

/**
 * Captain trait — granted on recruitment or as a level-up reward. Most mechanics are not
 * wired yet; this enum defines stable save identities as each trait comes online.
 */
public enum Trait {
    /** Bonus vs fortified ground targets. */
    SIEGE_SPECIALIST("Siege Specialist"),
    /** Reduces collateral damage / civilian casualties on raids. */
    SAPPER("Sapper"),
    /** Returns more recon info before the raid resolves. */
    SCOUT("Scout"),
    /** Reduces marine casualty rate during the raid. */
    FIELD_MEDIC("Field Medic"),
    /** Bonuses operating ground vehicles / mechs. */
    COMBAT_ENGINEER("Combat Engineer"),
    /** Generic combat bonus — flat raid power multiplier. */
    VETERAN("Veteran"),
    /** Faster XP gain. */
    NATURAL_LEADER("Natural Leader"),
    /** Larger effective squad cap (works around rank ceiling). */
    LOGISTICS_CHIEF("Logistics Chief"),
    /** Improves post-battle recovery and adds a high-value salvage roll. */
    SALVAGE_EXPERT("Salvage Expert"),
    /** Retains faith in the company's purpose after sustained humane conduct. */
    IDEALIST("Idealist"),
    /** Develops a hardened view of the company's work after sustained ruthless conduct. */
    CYNICAL("Cynical");

    private final String displayName;

    Trait(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
