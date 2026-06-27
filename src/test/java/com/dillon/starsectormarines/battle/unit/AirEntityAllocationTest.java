package com.dillon.starsectormarines.battle.unit;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirTurrets;
import com.dillon.starsectormarines.battle.air.MountedTurret;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.engine.ThrusterFx;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase-1 foundation of the air-into-world epic
 * ({@code roadmap/air/air-entities-into-world.md}): {@link UnitRosterService#allocateAir}
 * mints an air entity from the SINGLE id authority (shared with ground
 * {@link UnitRosterService#allocate}, so a shuttle id can never collide with a
 * ground id) and adopts it <em>world-only</em> — present in the {@code EntityWorld}
 * but absent from the dense ground roster, so every grid walk skips it for free.
 * Plus the {@link World} air-accessor round-trips: has-gated null reads (never a
 * throw on an absent component), seed-on-present, and optional attach/remove.
 */
public class AirEntityAllocationTest {

    private static UnitRosterService roster() {
        return new UnitRosterService(new UnitSpatialIndex(256, 256), null);
    }

    private static Entity ground(String label) {
        return new Entity(label, Faction.MARINE, UnitType.MARINE_BLUE, 0, 0);
    }

    private static ComponentType[] airArchetype(UnitRosterService r) {
        BattleComponents c = r.components();
        return new ComponentType[]{c.AIR_IDENTITY, c.KINEMATICS, c.SHUTTLE_MISSION};
    }

    @Test
    public void allocateAirSharesTheSingleIdMintWithGroundAllocateAndNeverCollides() {
        UnitRosterService r = roster();
        long g1 = r.allocate(ground("g1"));
        long air = r.allocateAir(airArchetype(r));
        long g2 = r.allocate(ground("g2"));
        // Strictly monotonic across the one nextId authority — air interleaves the
        // ground ids with no overlap (the dual-mint trap the epic closes).
        assertTrue(g1 < air && air < g2,
                "air id interleaves the shared monotonic mint: " + g1 + "/" + air + "/" + g2);
        assertNotEquals(g1, air);
        assertNotEquals(air, g2);
    }

    @Test
    public void airEntityIsWorldResidentButAbsentFromTheDenseGroundRoster() {
        UnitRosterService r = roster();
        long air = r.allocateAir(airArchetype(r));

        // World-resident: the air archetype's components are present.
        assertTrue(r.entityWorld().has(air, r.components().AIR_IDENTITY));
        assertTrue(r.entityWorld().has(air, r.components().KINEMATICS));
        assertTrue(r.entityWorld().has(air, r.components().SHUTTLE_MISSION));

        // NOT in the dense ground roster — grid walks (occupancy, spatial index,
        // win/objective counts) iterate [0, liveCount()) and skip air for free.
        assertNull(r.getOrNull(air), "air is not a dense-roster entity");
        assertFalse(r.isLive(air));
        assertEquals(UnitRosterService.INVALID_INDEX, r.indexOf(air));
        assertEquals(0, r.liveCount(), "allocateAir must not bump the dense roster count");

        // Air carries no HEALTH, so grid liveness reads false — harmless, because
        // nothing resolves a shuttle's own id for grid liveness (turret targets
        // are ground ids).
        assertFalse(r.isAliveById(air));
    }

    @Test
    public void worldAirAccessorsSeedAndReadById() {
        UnitRosterService r = roster();
        World w = r.world();
        long air = r.allocateAir(airArchetype(r));

        // Base archetype columns present but unseeded → has-gated reads return null
        // (the OBJECT column appends null), never throw.
        assertTrue(w.hasKinematics(air));
        assertNull(w.kinematics(air));
        assertNull(w.mission(air));

        AirBody body = new AirBody();
        w.setKinematics(air, body);
        assertSame(body, w.kinematics(air), "KINEMATICS round-trips the same AirBody instance");

        w.setAirIdentity(air, ShuttleType.AEROSHUTTLE, Faction.MARINE);
        assertSame(ShuttleType.AEROSHUTTLE, w.airType(air));
        assertSame(Faction.MARINE, w.airFaction(air));
    }

    @Test
    public void optionalAirFxComponentsAttachAndRemoveWithHasGatedReads() {
        UnitRosterService r = roster();
        World w = r.world();
        long air = r.allocateAir(airArchetype(r));

        // Absent by default → has-gated null, never a throw (unarmed / pre-FX craft).
        assertFalse(w.hasThrusterFx(air));
        assertNull(w.thrusterFx(air));
        assertFalse(w.hasAirTurrets(air));
        assertNull(w.airTurrets(air));

        ThrusterFx fx = new ThrusterFx(2);
        w.attachThrusterFx(air, fx);
        assertTrue(w.hasThrusterFx(air));
        assertSame(fx, w.thrusterFx(air));
        w.removeThrusterFx(air);
        assertFalse(w.hasThrusterFx(air));

        AirTurrets turrets = new AirTurrets(new MountedTurret[0]);
        w.attachAirTurrets(air, turrets);
        assertTrue(w.hasAirTurrets(air));
        assertSame(turrets, w.airTurrets(air));
        w.removeAirTurrets(air);
        assertFalse(w.hasAirTurrets(air));
    }
}
