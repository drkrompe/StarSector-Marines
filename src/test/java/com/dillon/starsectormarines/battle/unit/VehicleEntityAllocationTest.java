package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-1 foundation of the convoy-{@code Vehicle}-into-world epic
 * ({@code roadmap/ecs-migration/stories/vehicle-into-world.md}), mirroring
 * {@link AirEntityAllocationTest}: {@link UnitRosterService#allocateVehicle} mints
 * a ground-vehicle entity from the SINGLE id authority (shared with ground
 * {@link UnitRosterService#allocate} and air {@link UnitRosterService#allocateAir},
 * so a vehicle id can never collide) and adopts it <em>world-only</em> — present in
 * the {@code EntityWorld} but absent from the dense ground roster, so every grid walk
 * (occupancy, spatial index, fog, win/objective counts) skips it for free. The
 * ground columns round-trip their OBJECT payloads by id.
 */
public class VehicleEntityAllocationTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static Entity ground(String label) {
        return new Entity(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    private static ComponentType[] vehicleArchetype(UnitRosterService r) {
        BattleComponents c = r.components();
        return new ComponentType[]{c.GROUND_IDENTITY, c.GROUND_KINEMATICS};
    }

    @Test
    public void allocateVehicleSharesTheSingleIdMintWithGroundAllocateAndNeverCollides() {
        UnitRosterService r = roster();
        long g1 = r.allocate(ground("g1"));
        long v1 = r.allocateVehicle(vehicleArchetype(r));
        long g2 = r.allocate(ground("g2"));
        long v2 = r.allocateVehicle(vehicleArchetype(r));
        // Strictly monotonic across the one nextId authority — vehicles interleave the
        // ground ids with no overlap (the dual-mint trap the epic closes; air shares
        // the same mint, proven by AirEntityAllocationTest).
        assertTrue(g1 < v1 && v1 < g2 && g2 < v2,
                "vehicle ids interleave the shared monotonic mint: " + g1 + "/" + v1 + "/" + g2 + "/" + v2);
    }

    @Test
    public void vehicleEntityIsWorldResidentButAbsentFromTheDenseGroundRoster() {
        UnitRosterService r = roster();
        long veh = r.allocateVehicle(vehicleArchetype(r));

        // World-resident: the ground-craft archetype's components are present.
        assertTrue(r.entityWorld().has(veh, r.components().GROUND_IDENTITY));
        assertTrue(r.entityWorld().has(veh, r.components().GROUND_KINEMATICS));

        // NOT in the dense ground roster — grid walks iterate [0, liveCount()) and
        // skip convoy vehicles for free (the archetype carries no POSITION/HEALTH).
        assertNull(r.getOrNull(veh), "a convoy vehicle is not a dense-roster entity");
        assertFalse(r.isLive(veh));
        assertEquals(UnitRosterService.INVALID_INDEX, r.indexOf(veh));
        assertEquals(0, r.liveCount(), "allocateVehicle must not bump the dense roster count");

        // No HEALTH → grid liveness reads false — harmless, because vehicle liveness is
        // mission.state, and nothing resolves a vehicle's own id for grid liveness.
        assertFalse(r.isAliveById(veh));
    }

    @Test
    public void groundColumnsSeedAndReadById() {
        UnitRosterService r = roster();
        EntityWorld world = r.entityWorld();
        BattleComponents c = r.components();
        long veh = r.allocateVehicle(vehicleArchetype(r));

        // Base archetype columns present but unseeded → OBJECT columns append null,
        // read (not throw) as null (has-gated reads are safe).
        assertNull(world.getObject(veh, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_TYPE));
        assertNull(world.getObject(veh, c.GROUND_KINEMATICS, BattleComponents.GROUND_KINEMATICS_BODY));

        world.setObject(veh, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_TYPE, VehicleType.HEAVY_APC);
        world.setObject(veh, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_FACTION, Faction.MARINE);
        assertSame(VehicleType.HEAVY_APC, world.getObject(veh, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_TYPE));
        assertSame(Faction.MARINE, world.getObject(veh, c.GROUND_IDENTITY, BattleComponents.GROUND_IDENTITY_FACTION));

        GroundBody body = VehicleType.HEAVY_APC.createBody();
        world.setObject(veh, c.GROUND_KINEMATICS, BattleComponents.GROUND_KINEMATICS_BODY, body);
        assertSame(body, world.getObject(veh, c.GROUND_KINEMATICS, BattleComponents.GROUND_KINEMATICS_BODY),
                "GROUND_KINEMATICS round-trips the same GroundBody instance");
    }
}
