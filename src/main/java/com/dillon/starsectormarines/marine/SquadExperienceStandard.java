package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;

/**
 * Resolves the experience band a marine deploys at from the armour they wear
 * ({@code progression-nouns.md}).
 *
 * <p><b>Experience is issued, not earned.</b> A rank-and-file marine does not
 * accumulate a personal ladder; the company's growth is what the Armory has
 * collected and can issue. A squad put into heavy battlesuits is, by
 * construction, the squad the company put its best people in.
 *
 * <p><b>Why the armour pattern.</b> Armour tier is the one quality axis authored
 * across all four steps, carrying no grade axis of its own, and it is visible:
 * the pattern drives the layered appearance family, and even its icons are named
 * by tier, so a squad's band is readable from what the player can literally see
 * standing on the deck. The primary deliberately does not feed this — a
 * primary's access tier is authored as a strict function of
 * {@code EquipmentGrade} (surplus and service common, milspec advanced,
 * masterwork prestige), so sourcing the band there would fuse two axes the
 * standing composition law keeps separate: grade supplies quality, profile
 * supplies person.
 *
 * <p><b>No hidden promotions.</b> There is deliberately no "+1 because they
 * lead" rule. The built-in squad definitions already issue the leader's billet
 * scarcer armour, so an NCO comes out steadier through the visible mechanism;
 * an invisible bonus on top would both double-count and break the standing law
 * that fighting quality is fully determined by what the player can see. If a
 * leader should be better, issue them a better suit.
 *
 * <p>Deterministic by construction — no RNG. Two marines in the same armour
 * resolve to the same band, on every run and in replay.
 */
public final class SquadExperienceStandard {

    /** Armour that cannot be resolved fields recruits rather than veterans. */
    public static final ExperienceTier FALLBACK = ExperienceTier.GREEN;

    private SquadExperienceStandard() {}

    /** The band this marine deploys at, from the armour they are wearing. */
    public static ExperienceTier bandFor(MarineSoldier soldier) {
        return soldier == null ? FALLBACK : bandForArmorTier(armorTier(soldier));
    }

    /** The battle-ready profile for this marine: persisted aptitude, issued band. */
    public static SoldierProfile profileFor(MarineSoldier soldier) {
        if (soldier == null) return new SoldierProfile(SoldierAptitude.STEADY, FALLBACK.minimumXp);
        return new SoldierProfile(soldier.aptitude(), bandFor(soldier).minimumXp);
    }

    /**
     * The band an armour tier fields. The four authored tiers line up one-to-one
     * with the four bands, which is why no promotion or distribution rule is
     * needed to reach {@link ExperienceTier#ELITE}.
     */
    static ExperienceTier bandForArmorTier(int tier) {
        return switch (tier) {
            case 2 -> ExperienceTier.REGULAR;
            case 3 -> ExperienceTier.VETERAN;
            case 4 -> ExperienceTier.ELITE;
            default -> ExperienceTier.GREEN;
        };
    }

    /**
     * Zero rather than a throw when the pattern is unknown. The armour catalog is
     * not installed in every headless context that builds a roster, and an
     * unresolved suit should cost a squad its veterans, not abort a deployment.
     */
    private static int armorTier(MarineSoldier soldier) {
        String armorId = soldier.armorId();
        if (armorId == null || MarineArmorCatalogRegistry.installed() == null) return 0;
        MarineArmorCatalogDef def = MarineArmorCatalogRegistry.installed().get(armorId);
        return def != null ? def.tier() : 0;
    }
}
