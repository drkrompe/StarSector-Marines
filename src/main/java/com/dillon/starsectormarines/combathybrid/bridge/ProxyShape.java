package com.dillon.starsectormarines.combathybrid.bridge;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * How a proxied sim entity presents its <b>hittable footprint</b> to the vanilla combat engine. A
 * proxy is an invisible vanilla {@link ShipAPI} that exists only to be targeted + shot at by vanilla
 * ships ({@link SimProxyMirror}); its collision geometry is what fighter strafes and main-battery
 * fire register against, so it should match the ground structure's real size — not the frigate hull
 * the proxy happens to be spawned from. Without this, a turret on a small ground cell is hittable
 * across the whole frigate collision radius (and that radius doesn't shrink when the cell density
 * does — it's the vanilla hull's, independent of {@code WORLD_UNITS_PER_CELL}).
 *
 * <p>Vanilla collision is either a circle ({@code collisionRadius}, cheaply settable at runtime) or
 * a hull polygon ({@code BoundsAPI}, baked into the variant). Today every proxied target is a
 * defense structure and every shape is a circle ({@link FootprintCircleShape}); this interface is
 * the seam so the work ahead plugs in without touching the mirror. Two independent extension axes:
 * <ul>
 *   <li><b>More proxied targets</b> (fighter-bays, multi-cell compounds, …) → teach {@link #forUnit}
 *       which shape a new entity kind uses, and teach that shape the new kind's footprint.</li>
 *   <li><b>More shapes</b> (true polygon footprints, multi-proxy clusters, …) → add a sibling
 *       {@code ProxyShape} implementation.</li>
 * </ul>
 *
 * <p>Throwaway dev scaffolding; gated by {@code DevConfig.S0_COMBAT_PROBE}.
 */
@DebugOnly
public interface ProxyShape {

    /** Size/shape {@code proxy}'s collision to represent {@code unit} at this cell→world scale. */
    void applyTo(ShipAPI proxy, Entity unit, float worldUnitsPerCell);

    /**
     * The hittable shape for a proxied sim entity. Today every proxied target is a defense structure
     * (turret / drone hub) → {@link FootprintCircleShape#INSTANCE}; branch here as new proxied target
     * kinds arrive (keyed off the entity, the capability the bridge already has in hand).
     */
    static ProxyShape forUnit(Entity unit) {
        return FootprintCircleShape.INSTANCE;
    }
}
