package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.logistics.ResupplyCache;
import com.dillon.starsectormarines.battle.logistics.ResupplyService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;

/**
 * Puts each supply-carrying hull's stock into the battle's resupply authority and keeps it with the hull.
 *
 * <p>A {@link VehicleType#supplyStock() supply hull} owns one carried {@link ResupplyCache}, registered with the
 * battle's {@link ResupplyService} the first tick the hull is seen. From then on this system only moves it: the
 * cache follows the hull, is open while the hull is halted, and is lost with the hull. Who draws from it, how fast,
 * and what contests it belong to {@code ResupplySystem}, exactly as for a cache dropped from the air — a truck is a
 * cache that can be driven, not a second supply rule.
 *
 * <p>Runs before {@code ResupplySystem} in the tick so service is measured from where the truck stands now.
 */
public final class SupplyTruckSystem {

    /** A hull slower than this, in cells per second, counts as halted: its crates can come off. */
    public static final float PARKED_SPEED = 0.15f;

    private final ResupplyService resupply;

    public SupplyTruckSystem(ResupplyService resupply) {
        this.resupply = resupply;
    }

    /** Whether a supply hull in {@code state}, moving at {@code speed}, hands out its stock. */
    public static boolean issuing(VehicleState state, float speed) {
        return state != VehicleState.WRECKED && state != VehicleState.GONE && state != VehicleState.PENDING
                && Math.abs(speed) <= PARKED_SPEED;
    }

    public void tick(ConvoyService convoy) {
        for (int i = 0, n = convoy.vehicleCount(); i < n; i++) {
            long id = convoy.vehicleAt(i);
            VehicleType type = convoy.vehicleType(id);
            if (type == null || type.supplyStock() <= 0) continue;
            VehicleMission mission = convoy.mission(id);
            GroundBody body = convoy.body(id);
            if (mission == null || body == null) continue;
            ResupplyCache cache = resupply.carriedBy(id);
            if (cache == null) {
                if (mission.state == VehicleState.WRECKED || mission.state == VehicleState.GONE) continue;
                cache = ResupplyCache.carried(id, body.x, body.y, convoy.faction(id), type.supplyStock());
                resupply.add(cache);
            }
            if (mission.state == VehicleState.WRECKED || mission.state == VehicleState.GONE) {
                cache.lose();
            } else {
                cache.follow(body.x, body.y, issuing(mission.state, body.speed));
            }
        }
        // A hull taken off the map entirely (driven off an edge and despawned) takes its stock with it.
        for (ResupplyCache cache : resupply.caches()) {
            if (cache.carried() && !cache.depleted() && !convoy.isVehicle(cache.carrierId)) cache.lose();
        }
    }
}
