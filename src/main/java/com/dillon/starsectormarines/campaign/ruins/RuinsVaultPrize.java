package com.dillon.starsectormarines.campaign.ruins;

/**
 * The one prize a ruins vault holds beside vanilla's haul ({@code ruins-expedition.md}, The vault and its prize), drawn
 * by {@link RuinsVaultPrizes#draw} from the site alone and never stored. It comes out with the haul, so only a won
 * expedition takes it.
 *
 * @param family the kind of prize, which the site's theme always tells true
 * @param theme  what the site was built round
 * @param itemId vanilla's id for the prize: the special item for a colony item, the AI core commodity for a core;
 *               null for goods and tech, which roll their contents
 * @param stakes how much the prize raises the site, 0 (goods) to 3 (an alpha core, a pristine nanoforge), for the
 *               pressure and the guardians to read
 */
public record RuinsVaultPrize(Family family, RuinsVaultTheme theme, String itemId, int stakes) {

    /** Vanilla's AI core commodity ids, weakest first. */
    public static final String GAMMA_CORE = "gamma_core";
    public static final String BETA_CORE = "beta_core";
    public static final String ALPHA_CORE = "alpha_core";

    /** The kinds of prize, plainest first. */
    public enum Family {
        /** Commodities: a larger bundle of the ordinary finds. */
        GOODS,
        /** A tech cache: weapons, fighter wings, hullmods or blueprints from the site's vanilla drop groups. */
        TECH,
        /** A gamma, beta or alpha core. */
        AI_CORE,
        /** One of vanilla's colony items, the industry upgrades installed on a planet. */
        COLONY_ITEM
    }

    /** Whether a forced vault breaks this prize into tech caches (pick {@code ruins-prize-forced} a). */
    public boolean breaksWhenForced() {
        return family == Family.AI_CORE || family == Family.COLONY_ITEM;
    }
}
