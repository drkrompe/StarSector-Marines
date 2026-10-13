package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

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
 * @param kind         what completing the site places; the order itself is the same for every kind
 * @param structureId  turret-catalog structure the finished site becomes
 * @param buildSeconds seconds a full crew of builders needs; fewer builders take longer
 * @param cost         build charges drawn from a metered tender's stock when the site is laid (unmetered tenders ignore it)
 */
public record BuildOption(String id, String label, Kind kind, String structureId, float buildSeconds, int cost) {

    /** What a finished site becomes. The construction order is identical; only completion differs. */
    public enum Kind {
        /** A bolted-down {@code MapTurret} from the turret catalog. */
        TURRET,
        /** A self-expiring point-defence pod placed through {@code PointDefenseService}. */
        POINT_DEFENCE
    }

    public BuildOption {
        if (!(buildSeconds > 0f)) throw new IllegalArgumentException("a build takes time");
        if (cost < 0) throw new IllegalArgumentException("a build cannot refund the tender");
    }

    /** The catalog structure of the carried Palisade pod. */
    static final String POD_STRUCTURE_ID = "structure.deployable-point-defence";

    /** A bolted-down vulcan emplacement: the simplest turret the catalog has. */
    public static final BuildOption VULCAN_TURRET = new BuildOption(
            "vulcan-turret", "Vulcan turret", Kind.TURRET, TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 12f, 1);

    /**
     * The next three are catalog turrets through the same structure path. Time and cost scale from
     * the vulcan by the structure's own weight (structure plus armour: 140, 180, 195, 210) and by
     * its weapon's reach and force score (8, 10, 12, 14).
     */
    public static final BuildOption ARBALEST_TURRET = new BuildOption(
            "arbalest-turret", "Arbalest turret", Kind.TURRET, TurretCatalogRegistry.ARBALEST_STRUCTURE_ID, 15f, 2);

    public static final BuildOption FLAK_TURRET = new BuildOption(
            "flak-turret", "Dual flak turret", Kind.TURRET, TurretCatalogRegistry.DUAL_FLAK_STRUCTURE_ID, 17f, 2);

    public static final BuildOption MORTAR_TURRET = new BuildOption(
            "mortar-turret", "Heavy mortar", Kind.TURRET, TurretCatalogRegistry.HEAVY_MORTAR_STRUCTURE_ID, 18f, 3);

    /**
     * The Palisade interceptor pod the marines carry, built on site instead. It is the structure the
     * carried item names (a test pins that), so the pod\'s durability, magazine and lifetime stay
     * authored in one place. Flimsy (structure plus armour 44 against the vulcan\'s 140) and short-lived,
     * hence quicker than any turret; the stance director never picks it ({@link #turretKind}).
     */
    public static final BuildOption POINT_DEFENCE_POD = new BuildOption(
            "point-defence-pod", "Interceptor pod", Kind.POINT_DEFENCE, POD_STRUCTURE_ID, 8f, 1);

    /** Every option a tender offers, in the order a player selects them (number keys 1-5). */
    public static final List<BuildOption> ALL = List.of(
            VULCAN_TURRET, ARBALEST_TURRET, FLAK_TURRET, MORTAR_TURRET, POINT_DEFENCE_POD);

    /** The carried-pod spec a point-defence option places; its lifetime and magazine come from the item. */
    public DeployableEmplacementSpec podSpec() {
        if (kind != Kind.POINT_DEFENCE) throw new IllegalStateException(id + " is not a pod");
        return SpecialEquipmentRegistry.require(SpecialEquipmentRegistry.POINT_DEFENCE_EMPLACEMENT_ID)
                .deployableEmplacementSpec();
    }

    /** Whether this option places a catalog turret, the only kind an unpiloted tender chooses for itself. */
    public boolean turretKind() {
        return kind == Kind.TURRET;
    }
}
