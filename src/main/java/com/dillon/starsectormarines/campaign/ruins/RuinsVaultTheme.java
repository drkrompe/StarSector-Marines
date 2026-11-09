package com.dillon.starsectormarines.campaign.ruins;

/**
 * What a ruins site was built round, read from its vault's prize ({@code ruins-expedition.md}, The vault and its
 * prize): the theme sets the site's look, its works and its survey tell, so a player who reads the site can guess
 * what the vault holds. Goods, tech and AI cores each have one theme; a colony item's site is that item's own, named
 * by vanilla's special item id.
 *
 * <p>{@link #rareTechWeight} is the item's weight in vanilla's {@code rare_tech} drop group, so themed sites keep
 * vanilla's own rarity among items.
 */
public enum RuinsVaultTheme {
    /** Goods: a depot of crates and dead habitat stores. */
    DEPOT(RuinsVaultPrize.Family.GOODS, null, 0, false),
    /** Tech: an armory of racked hardware and shielded storage. */
    ARMORY(RuinsVaultPrize.Family.TECH, null, 0, false),
    /** AI cores: a server hall, its racks humming and its repair drones at work. */
    SERVER_HALL(RuinsVaultPrize.Family.AI_CORE, null, 0, false),

    PRISTINE_NANOFORGE(RuinsVaultPrize.Family.COLONY_ITEM, "pristine_nanoforge", 3, false),
    CORRUPTED_NANOFORGE(RuinsVaultPrize.Family.COLONY_ITEM, "corrupted_nanoforge", 7, false),
    SYNCHROTRON(RuinsVaultPrize.Family.COLONY_ITEM, "synchrotron", 10, false),
    FUSION_LAMP(RuinsVaultPrize.Family.COLONY_ITEM, "orbital_fusion_lamp", 10, false),
    MANTLE_BORE(RuinsVaultPrize.Family.COLONY_ITEM, "mantle_bore", 10, true),
    CATALYTIC_CORE(RuinsVaultPrize.Family.COLONY_ITEM, "catalytic_core", 10, false),
    SOIL_NANITES(RuinsVaultPrize.Family.COLONY_ITEM, "soil_nanites", 10, true),
    BIOFACTORY_EMBRYO(RuinsVaultPrize.Family.COLONY_ITEM, "biofactory_embryo", 10, true),
    FULLERENE_SPOOL(RuinsVaultPrize.Family.COLONY_ITEM, "fullerene_spool", 10, false),
    PLASMA_DYNAMO(RuinsVaultPrize.Family.COLONY_ITEM, "plasma_dynamo", 10, false),
    CRYOARITHMETIC_ENGINE(RuinsVaultPrize.Family.COLONY_ITEM, "cryoarithmetic_engine", 10, false),
    /** Banned by the Domain long before the Collapse; only derelict security still keeps one. */
    DRONE_REPLICATOR(RuinsVaultPrize.Family.COLONY_ITEM, "drone_replicator", 10, false),
    DEALMAKER_HOLOSUITE(RuinsVaultPrize.Family.COLONY_ITEM, "dealmaker_holosuite", 10, false),
    HYPERSHUNT_TAP(RuinsVaultPrize.Family.COLONY_ITEM, "coronal_portal", 10, false);

    /** The prize family this theme tells of. */
    public final RuinsVaultPrize.Family family;
    /** Vanilla's special item id for a colony item's theme; null for the other families. */
    public final String itemId;
    /** The item's weight in vanilla's {@code rare_tech} drop group; 0 for the other families. */
    public final int rareTechWeight;
    /** Living technology or deep ground, the only items under a Rav-held site. */
    public final boolean ravGround;

    RuinsVaultTheme(RuinsVaultPrize.Family family, String itemId, int rareTechWeight, boolean ravGround) {
        this.family = family;
        this.itemId = itemId;
        this.rareTechWeight = rareTechWeight;
        this.ravGround = ravGround;
    }

    /** The string key of the survey's tell for this theme; the briefing resolves it. */
    public String tellKey() {
        StringBuilder key = new StringBuilder("ruinsTell");
        for (String part : name().split("_")) {
            key.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return key.toString();
    }

    /** The string key of the close reading the company takes of the vault's layer, inside the ruin. */
    public String insideTellKey() {
        return "ruinsInside" + tellKey().substring("ruinsTell".length());
    }

    /** Whether a site held by {@code occupant} may be built round this theme. */
    boolean allowedFor(RuinsOccupant occupant) {
        if (this == DRONE_REPLICATOR) return occupant.isDerelict();
        if (family == RuinsVaultPrize.Family.COLONY_ITEM && occupant == RuinsOccupant.RAV) return ravGround;
        return true;
    }
}
