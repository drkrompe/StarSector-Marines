package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;

import java.util.List;

/**
 * One thing a tender can build: a deployable that already exists, what it costs
 * the tender's stock, and how long a full crew of builder drones takes.
 *
 * <p>The structure's durability, weapon and art all come from the turret catalog;
 * an option names it and adds only what is the tender's to decide. Adding a
 * candidate (a revetment, a sensor, a supply cache) is one entry here, not a new
 * kind of order.
 *
 * @param id           stable key, used by readouts and tests
 * @param label        what a player is told it is
 * @param structureId  turret-catalog structure the finished site becomes
 * @param buildSeconds seconds a full crew of builders needs; fewer builders take longer
 * @param cost         build charges drawn from a metered tender's stock when the site is laid (unmetered tenders ignore it)
 */
public record BuildOption(String id, String label, String structureId, float buildSeconds, int cost) {

    public BuildOption {
        if (!(buildSeconds > 0f)) throw new IllegalArgumentException("a build takes time");
        if (cost < 0) throw new IllegalArgumentException("a build cannot refund the tender");
    }

    /** A bolted-down vulcan emplacement: the simplest turret the catalog has. */
    public static final BuildOption VULCAN_TURRET = new BuildOption(
            "vulcan-turret", "Vulcan turret", TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 12f, 1);

    /**
     * The next three are catalog turrets through the same structure path. Time and cost scale from
     * the vulcan by the structure's own weight (structure plus armour: 140, 180, 195, 210) and by
     * its weapon's reach and force score (8, 10, 12, 14).
     */
    public static final BuildOption ARBALEST_TURRET = new BuildOption(
            "arbalest-turret", "Arbalest turret", TurretCatalogRegistry.ARBALEST_STRUCTURE_ID, 15f, 2);

    public static final BuildOption FLAK_TURRET = new BuildOption(
            "flak-turret", "Dual flak turret", TurretCatalogRegistry.DUAL_FLAK_STRUCTURE_ID, 17f, 2);

    public static final BuildOption MORTAR_TURRET = new BuildOption(
            "mortar-turret", "Heavy mortar", TurretCatalogRegistry.HEAVY_MORTAR_STRUCTURE_ID, 18f, 3);

    /** Every option a tender offers, in the order a player selects them (number keys 1-4). */
    public static final List<BuildOption> ALL = List.of(VULCAN_TURRET, ARBALEST_TURRET, FLAK_TURRET, MORTAR_TURRET);
}
