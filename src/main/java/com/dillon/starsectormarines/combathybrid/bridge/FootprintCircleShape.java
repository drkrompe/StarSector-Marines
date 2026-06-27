package com.dillon.starsectormarines.combathybrid.bridge;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.drone.DroneHubUnit;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * The general structure-proxy shape: a collision <b>circle</b> sized to the entity's ground
 * footprint, so a proxy is hit when fire lands within the structure's real cell extent rather than
 * across the (much larger) frigate hull it's spawned from. The radius is derived in cells, so it
 * tracks {@code WORLD_UNITS_PER_CELL} for free — denser cells → smaller hittable footprint, in
 * lockstep with the shrinking visual. This is the shape for the turret + drone-hub proxies the
 * bridge spawns today.
 *
 * <p>Stateless + shared via {@link #INSTANCE}. The per-target footprint lookup
 * ({@link #footprintCells}) is the <em>more-proxied-targets</em> extension point; a future
 * non-circular shape (polygon compound, multi-proxy cluster) is a sibling {@link ProxyShape}.
 *
 * <p>Throwaway dev scaffolding; gated by {@code DevConfig.S0_COMBAT_PROBE}.
 */
@DebugOnly
public final class FootprintCircleShape implements ProxyShape {

    public static final FootprintCircleShape INSTANCE = new FootprintCircleShape();

    /** Footprint for a proxied target that declares no cell size — a sane structure-ish default. */
    private static final float DEFAULT_FOOTPRINT_CELLS = 1.5f;

    private FootprintCircleShape() {}

    @Override
    public void applyTo(ShipAPI proxy, Entity unit, float worldUnitsPerCell) {
        proxy.setCollisionRadius(footprintCells(unit) * worldUnitsPerCell * 0.5f);
    }

    /**
     * Ground footprint (long-axis cells) of a proxied structure. New proxied target kinds register
     * their size here; both current kinds already publish it ({@code TurretKind.visualCells},
     * {@link DroneHubUnit#VISUAL_CELLS}). Falls back to {@link #DEFAULT_FOOTPRINT_CELLS} for anything
     * unrecognized so a new target type is hittable (just not perfectly sized) before it's wired in.
     */
    private static float footprintCells(Entity unit) {
        if (unit instanceof MapTurret) return ((MapTurret) unit).kind.visualCells;
        if (unit instanceof DroneHubUnit) return DroneHubUnit.VISUAL_CELLS;
        return DEFAULT_FOOTPRINT_CELLS;
    }
}
