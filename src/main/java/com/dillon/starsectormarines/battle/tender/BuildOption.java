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
 * @param cost         build charges drawn from the tender's stock when the site is laid
 */
public record BuildOption(String id, String label, String structureId, float buildSeconds, int cost) {

    public BuildOption {
        if (!(buildSeconds > 0f)) throw new IllegalArgumentException("a build takes time");
        if (cost < 0) throw new IllegalArgumentException("a build cannot refund the tender");
    }

    /** A bolted-down vulcan emplacement: the simplest turret the catalog has. */
    public static final BuildOption VULCAN_TURRET = new BuildOption(
            "vulcan-turret", "Vulcan turret", TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 12f, 1);

    /** Every option a tender offers, in the order a player selects them. */
    public static final List<BuildOption> ALL = List.of(VULCAN_TURRET);
}
