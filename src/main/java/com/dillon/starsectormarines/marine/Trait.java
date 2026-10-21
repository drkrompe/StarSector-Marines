package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;

/**
 * Captain trait — assigned by a recruitment/discovery source or by a durable narrative
 * system such as moral-outlook drift. Promotion choices live in {@link OfficerSpecialty};
 * this enum retains stable recruitment/narrative save identities as each trait comes online.
 */
public enum Trait {
    /** Bonus vs fortified ground targets. */
    SIEGE_SPECIALIST("@traitSiegeSpecialist"),
    /** Reduces collateral damage / civilian casualties on raids. */
    SAPPER("@traitSapper"),
    /** Returns more recon info before the raid resolves. */
    SCOUT("@traitScout"),
    /** Reduces marine casualty rate during the raid. */
    FIELD_MEDIC("@traitFieldMedic"),
    /** Bonuses operating ground vehicles / mechs. */
    COMBAT_ENGINEER("@traitCombatEngineer"),
    /** Generic combat bonus — flat raid power multiplier. */
    VETERAN("@traitVeteran"),
    /** Faster XP gain. */
    NATURAL_LEADER("@traitNaturalLeader"),
    /** Larger effective squad cap (works around rank ceiling). */
    LOGISTICS_CHIEF("@traitLogisticsChief"),
    /** Improves post-battle recovery and adds a high-value salvage roll. */
    SALVAGE_EXPERT("@traitSalvageExpert"),
    /** Retains faith in the company's purpose after sustained humane conduct. */
    IDEALIST("@traitIdealist"),
    /** Develops a hardened view of the company's work after sustained ruthless conduct. */
    CYNICAL("@traitCynical");

    private final String displayName;

    Trait(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return Strings.resolve(displayName);
    }
}
