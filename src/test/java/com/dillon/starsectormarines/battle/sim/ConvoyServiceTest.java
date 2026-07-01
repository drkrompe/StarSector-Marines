package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.vehicle.Vehicle;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-2 adoption of the convoy-{@code Vehicle}-into-world epic
 * ({@code roadmap/ecs-migration/stories/vehicle-into-world.md}): {@link ConvoyService}
 * adopts a {@link Vehicle} as a world entity ({@code {GROUND_IDENTITY,
 * GROUND_KINEMATICS}}) by <em>aliasing</em> the handle's own type / faction / body
 * instances, and reaps it (one {@code destroy}) at terminal GONE. The handle stays
 * authoritative for lifecycle state, and the entity is world-resident but off the dense
 * ground roster (grid systems skip it for free).
 */
public class ConvoyServiceTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static Vehicle vehicle() {
        return new Vehicle(VehicleType.HEAVY_APC, Faction.MARINE,
                new float[]{0.5f, 1.5f}, new float[]{0.5f, 0.5f},
                new float[]{1.5f, 0.5f}, new float[]{0.5f, 0.5f}, 0f);
    }

    @Test
    public void spawnAdoptsVehicleAsWorldEntityAliasingIdentityAndBody() {
        UnitRosterService r = roster();
        ConvoyService convoy = r.convoy();
        Vehicle v = vehicle();

        long id = convoy.spawn(v);

        assertNotEquals(0L, id, "spawn mints a real id");
        assertEquals(id, v.entityId, "the handle is stamped with its world id");

        // World-resident with the ground-craft archetype's two components.
        assertTrue(r.entityWorld().has(id, r.components().GROUND_IDENTITY));
        assertTrue(r.entityWorld().has(id, r.components().GROUND_KINEMATICS));

        // Aliased: the columns hold the SAME instances the handle holds (zero-churn).
        assertSame(v.body, convoy.body(id), "GROUND_KINEMATICS aliases the handle's GroundBody");
        assertSame(VehicleType.HEAVY_APC, convoy.vehicleType(id));
        assertSame(Faction.MARINE, convoy.faction(id));

        // Off the dense ground roster — grid systems iterate [0, liveCount()) and skip it.
        assertNull(r.getOrNull(id), "a convoy vehicle is not a dense-roster entity");
        assertFalse(r.isLive(id));
        assertEquals(0, r.liveCount(), "adoption must not bump the dense roster count");
    }

    @Test
    public void despawnDestroysTheWorldEntity() {
        UnitRosterService r = roster();
        ConvoyService convoy = r.convoy();
        Vehicle v = vehicle();
        long id = convoy.spawn(v);

        convoy.despawn(id);

        // One destroy drops all columns; has-gated reads return false/null, never throw.
        assertFalse(r.entityWorld().has(id, r.components().GROUND_IDENTITY));
        assertFalse(r.entityWorld().has(id, r.components().GROUND_KINEMATICS));
        assertNull(convoy.body(id), "a destroyed vehicle reads null (has-gated), not a throw");
        assertNull(convoy.vehicleType(id));
    }

    @Test
    public void despawnIsNoOpOnUnadoptedId() {
        UnitRosterService r = roster();
        // 0L (never adopted) is a silent no-op — the seeded default entityId.
        r.convoy().despawn(0L);
    }

    @Test
    public void eachSpawnMintsADistinctInterleavedId() {
        UnitRosterService r = roster();
        ConvoyService convoy = r.convoy();
        long a = convoy.spawn(vehicle());
        long b = convoy.spawn(vehicle());
        assertNotEquals(a, b, "each adoption mints a fresh id off the shared authority");
        assertTrue(a < b, "monotonic mint: " + a + " < " + b);
    }
}
