package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ConvoyService} as the convoy-vehicle factory + data owner (the dissolution
 * end-state of {@code roadmap/ecs-migration/stories/vehicle-into-world.md}, mirroring
 * the air {@code ShuttleMission} shape): {@link ConvoyService#spawn} builds a vehicle's
 * {@code GroundBody} + {@code GroundTurret} from the variant, mints a world entity, and
 * seeds the ground-craft columns ({@code {GROUND_IDENTITY, GROUND_KINEMATICS,
 * VEHICLE_MISSION}} + {@code GROUND_TURRET} iff armed). Identity / kinematics / turret
 * live in their own columns and are read by id; the {@link VehicleMission} bag holds only
 * lifecycle / path state. The entity is world-resident but off the dense ground roster
 * (grid systems skip it for free).
 */
public class ConvoyServiceTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static VehicleMission mission() {
        return new VehicleMission(
                new float[]{0.5f, 1.5f}, new float[]{0.5f, 0.5f},   // inbound (head is the spawn cell)
                new float[]{1.5f, 0.5f}, new float[]{0.5f, 0.5f},   // outbound
                0f, VehicleType.HEAVY_APC.capacity);
    }

    @Test
    public void spawnBuildsWorldEntityWithComponentColumns() {
        UnitRosterService r = roster();
        ConvoyService convoy = r.convoy();
        VehicleMission m = mission();

        long id = convoy.spawn(VehicleType.HEAVY_APC, Faction.MARINE, m);

        assertNotEquals(0L, id, "spawn mints a real id");
        assertTrue(r.entityWorld().has(id, r.components().GROUND_IDENTITY));
        assertTrue(r.entityWorld().has(id, r.components().GROUND_KINEMATICS));
        assertTrue(r.entityWorld().has(id, r.components().VEHICLE_MISSION));

        // Identity + variant seeded from the spawn args.
        assertSame(VehicleType.HEAVY_APC, convoy.vehicleType(id));
        assertSame(Faction.MARINE, convoy.faction(id));

        // The factory built the GroundBody (teleported to the inbound head) — component-owned,
        // NOT a field on the mission bag (the air ShuttleMission shape).
        GroundBody body = convoy.body(id);
        assertNotNull(body, "spawn builds the GroundBody into GROUND_KINEMATICS");
        assertEquals(0.5f, body.x, 1e-4, "body teleported to the inbound head cell");
        assertEquals(0.5f, body.y, 1e-4);

        // HEAVY_APC is armed → GROUND_TURRET present, ammo seeded from the TurretKind.
        assertTrue(r.entityWorld().has(id, r.components().GROUND_TURRET), "armed vehicle carries GROUND_TURRET");
        assertNotNull(convoy.turret(id), "GROUND_TURRET seeded for an armed variant");
        assertEquals(VehicleType.HEAVY_APC.turretKind.startingAmmo, convoy.turret(id).ammo,
                "turret ammo seeded from the TurretKind");

        // The handed-in mission bag is the VEHICLE_MISSION payload (the id→mission resolution).
        assertSame(m, convoy.mission(id), "convoy.mission(id) resolves the seeded mission bag");

        // Off the dense ground roster — grid systems iterate [0, liveCount()) and skip it.
        assertNull(r.getOrNull(id), "a convoy vehicle is not a dense-roster entity");
        assertFalse(r.isLive(id));
        assertEquals(0, r.liveCount(), "spawn must not bump the dense roster count");
    }

    @Test
    public void despawnDestroysTheWorldEntity() {
        UnitRosterService r = roster();
        ConvoyService convoy = r.convoy();
        long id = convoy.spawn(VehicleType.HEAVY_APC, Faction.MARINE, mission());

        convoy.despawn(id);

        // One destroy drops all columns; has-gated reads return false/null, never throw.
        assertFalse(r.entityWorld().has(id, r.components().GROUND_IDENTITY));
        assertFalse(r.entityWorld().has(id, r.components().GROUND_KINEMATICS));
        assertFalse(r.entityWorld().has(id, r.components().GROUND_TURRET));
        assertFalse(r.entityWorld().has(id, r.components().VEHICLE_MISSION));
        assertNull(convoy.body(id), "a destroyed vehicle reads null (has-gated), not a throw");
        assertNull(convoy.vehicleType(id));
        assertNull(convoy.turret(id));
        assertNull(convoy.mission(id));
    }

    @Test
    public void despawnIsNoOpOnUnadoptedId() {
        UnitRosterService r = roster();
        // 0L (never spawned) is a silent no-op.
        r.convoy().despawn(0L);
    }

    @Test
    public void eachSpawnMintsADistinctInterleavedId() {
        UnitRosterService r = roster();
        ConvoyService convoy = r.convoy();
        long a = convoy.spawn(VehicleType.HEAVY_APC, Faction.MARINE, mission());
        long b = convoy.spawn(VehicleType.HEAVY_APC, Faction.MARINE, mission());
        assertNotEquals(a, b, "each spawn mints a fresh id off the shared authority");
        assertTrue(a < b, "monotonic mint: " + a + " < " + b);
    }
}
