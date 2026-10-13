package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirTurrets;
import com.dillon.starsectormarines.battle.air.AirArmament;
import com.dillon.starsectormarines.battle.air.GunshipOrbit;
import com.dillon.starsectormarines.battle.air.MountedTurret;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.turret.TurretRole;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloseAirSupportTest {

    /** An armed craft mounts its turret on its hull's slot, which headless has to be told where to read. */
    @BeforeAll
    static void readWeaponSlotsOffTheInstall() { InstalledWeaponSlots.install(); }

    /** Process-wide, so put it back: nothing else here should find its aircraft armed. */
    @AfterAll
    static void forgetTheInstall() { TurretSlotResolver.useHullSpecs(null); }

    private static final int SIZE = 60;
    private static final int TARGET = 30;

    @Test
    void theGunshipCarriesTheHullsOwnTurretAtTheHullsOwnMount() {
        MountedTurret[] mounts = AirArmament.mounts(ShuttleType.MUDSKIPPER, TurretRole.A2G, 0f);
        float[][] slots = TurretSlotResolver.resolve(ShuttleType.MUDSKIPPER.renderHullId());
        assertEquals(1, mounts.length, "a Mudskipper has the one turret");
        assertTrue(slots.length >= 1, "the hull publishes a weapon slot to mount it on");
        assertEquals(slots[0][0], mounts[0].mount.localOffsetX, 1e-4f);
        assertEquals(slots[0][1], mounts[0].mount.localOffsetY, 1e-4f);
    }

    @Test
    void theCardNeedsGroundThePlayerCanSeeInsideTheMap() {
        CloseAirSupport power = new CloseAirSupport();
        BattleSimulation sim = battle();
        assertTrue(power.canTarget(TARGET, TARGET, sim));
        assertFalse(power.canTarget(-1, TARGET, sim));
        assertFalse(power.canTarget(TARGET, SIZE + 3, sim));
    }

    @Test
    void aCalledGunshipCirclesTheGroundFiresOutOfItsSideAndThenLeaves() {
        BattleSimulation sim = battle();
        long hostile = sim.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MILITIA, TARGET, TARGET)
                .health(100_000f).attackDamage(0f));
        CloseAirSupport power = new CloseAirSupport();
        sim.setCommandPowers(List.of(power));
        sim.setCommandPowerResources(new Stockpile(10));
        sim.getCommandPowerService().requestActivation(power.id, TARGET, TARGET);

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getAirEntityIds().length, "the call puts exactly one craft in the air");
        long craft = sim.getAirEntityIds()[0];
        ShuttleMission mission = sim.world().mission(craft);
        assertTrue(mission.gunship);
        assertEquals(0, mission.marinesRemaining, "a gunship carries nobody to set down");
        AirTurrets turrets = sim.world().airTurrets(craft);
        assertNotNull(turrets);
        assertEquals(1, turrets.mounts.length, "one turret, on the hull's own mount");

        float startHp = sim.world().hp(hostile);
        float seconds = 0f;
        float worstRingError = 0f;
        int orbitTicks = 0;
        int guard = 0;
        while (mission.state != ShuttleState.GONE && guard++ < 60 * 30) {
            sim.advance(BattleSimulation.TICK_DT);
            seconds += BattleSimulation.TICK_DT;
            if (mission.state == ShuttleState.ORBIT) {
                orbitTicks++;
                AirBody body = sim.world().kinematics(craft);
                float distance = (float) Math.hypot(body.x - (TARGET + 0.5f), body.y - (TARGET + 0.5f));
                if (orbitTicks > 90) {
                    worstRingError = Math.max(worstRingError, Math.abs(distance - GunshipOrbit.RADIUS_CELLS));
                }
            }
        }

        assertEquals(ShuttleState.GONE, mission.state, "the visit ends and the craft leaves");
        assertTrue(orbitTicks > 20 * 30, "it spends the better part of its visit on the ring: " + orbitTicks);
        assertTrue(worstRingError < 3.5f,
                "once settled it stays on the ring it was given, off by at most " + worstRingError);
        assertTrue(sim.world().hp(hostile) < startHp, "the turret puts rounds on the ground it circles");
        assertTrue(seconds < 60f, "and the whole visit is bounded: " + seconds);
    }

    @Test
    void theOrbitDirectionIsKeptForTheWholeVisit() {
        BattleSimulation sim = battle();
        sim.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MILITIA, TARGET, TARGET)
                .health(100_000f).attackDamage(0f));
        CloseAirSupport power = new CloseAirSupport();
        sim.setCommandPowers(List.of(power));
        sim.setCommandPowerResources(new Stockpile(10));
        sim.getCommandPowerService().requestActivation(power.id, TARGET, TARGET);
        sim.advance(BattleSimulation.TICK_DT);
        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        boolean chosen = mission.orbitClockwise;
        for (int i = 0; i < 25 * 30; i++) {
            sim.advance(BattleSimulation.TICK_DT);
            assertEquals(chosen, mission.orbitClockwise);
        }
        assertNotEquals(ShuttleState.PENDING, mission.state);
    }

    private static BattleSimulation battle() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(SIZE, SIZE));
        // Something for each side to keep the battle from ending around the scene.
        sim.spawn(new EntitySpec("keeper-d", Faction.DEFENDER, UnitType.MILITIA, SIZE - 3, SIZE - 3)
                .health(100_000f).attackDamage(0f));
        // The marine is what lets the player see the ground the gunship is called onto.
        sim.spawn(new EntitySpec("spotter", Faction.MARINE, UnitType.MARINE, TARGET - 5, TARGET + 5)
                .health(100_000f).attackDamage(0f));
        for (int i = 0; i < 10; i++) sim.advance(BattleSimulation.TICK_DT);
        return sim;
    }

    private static final class Stockpile implements CommandPowerResources {
        int supplies;

        Stockpile(int supplies) { this.supplies = supplies; }

        @Override public int availableSupplies() { return supplies; }

        @Override public boolean spendSupplies(int amount) {
            if (supplies < amount) return false;
            supplies -= amount;
            return true;
        }
    }
}
