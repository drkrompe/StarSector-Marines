package com.dillon.starsectormarines.battle.power;

import com.dillon.starsectormarines.battle.air.MechSupportPayload;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MechSupportTest {

    private static final int W = 30;
    private static final int H = 30;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    @Test
    public void invalidLandingTargetDoesNotSpendChargeOrCommandPoints() {
        BattleSimulation sim = openSim();
        for (int y = 10; y <= 20; y++) {
            for (int x = 10; x <= 20; x++) sim.getTopology().setBuildingId(x, y, 1);
        }
        MechSupport power = new MechSupport();
        sim.setCommandPowers(List.of(power));

        assertFalse(power.canTarget(15, 15, sim));
        sim.getCommandPowerService().requestActivation(power.id, 15, 15);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(4f + 0.5f * BattleSimulation.TICK_DT,
                sim.getCommandPowerService().getCommandPoints(), 0.001f);
        assertEquals(1, sim.getCommandPowerService().getChargesRemaining(power.id));
        assertEquals(0, sim.getAirEntityIds().length);
    }

    @Test
    public void validActivationFliesInBeforeDeployingOperationalMarineMech() {
        BattleSimulation sim = openSim();
        // Keep the elimination backstop battle alive while the support craft is inbound.
        sim.spawn(new EntitySpec("defender", Faction.DEFENDER, UnitType.MILITIA, 25, 25));
        MechSupport power = new MechSupport();
        sim.setCommandPowers(List.of(power));
        sim.getCommandPowerService().requestActivation(power.id, 15, 15);

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getAirEntityIds().length, "the physical carrier arrives first");
        long shuttleId = sim.getAirEntityIds()[0];
        ShuttleMission mission = sim.world().mission(shuttleId);
        assertSame(MechSupportPayload.INSTANCE, mission.payload);
        assertEquals(1, mission.marinesRemaining);
        assertEquals(0.5f * BattleSimulation.TICK_DT,
                sim.getCommandPowerService().getCommandPoints(), 0.001f);
        assertEquals(0, sim.getCommandPowerService().getChargesRemaining(power.id));

        long mech = 0L;
        for (int i = 0; i < 600 && mech == 0L; i++) {
            sim.advance(BattleSimulation.TICK_DT);
            for (int u = 0; u < sim.liveUnitCount(); u++) {
                long candidate = sim.liveUnitAt(u);
                if (sim.identity().faction(candidate) == Faction.MARINE
                        && sim.identity().type(candidate) == UnitType.HEAVY_MECH) {
                    mech = candidate;
                    break;
                }
            }
        }

        assertTrue(mech != 0L, "the Valkyrie eventually touches down and unloads its mech");
        assertSame(MechVariant.BULWARK, sim.identity().mechVariant(mech));
        assertTrue(sim.world().hasMechLoadout(mech), "the support mech carries its full weapon state");
        assertTrue(sim.squad().hasSquad(mech));
        Squad squad = sim.getSquad(sim.squad().squadId(mech));
        assertNotNull(squad);
        assertTrue(squad.isMechSquad());
        assertEquals(mech, squad.leaderId);
        assertEquals(1, squad.originalSize);
    }

    @Test
    public void configuredSupportPartitionsVariantsIntoFourMechLances() {
        MechSupport power = new MechSupport(List.of(
                MechVariant.HOUND, MechVariant.SIROCCO, MechVariant.BULWARK,
                MechVariant.HOUND, MechVariant.SIROCCO));

        assertEquals(2, power.maxCharges);
        assertEquals(List.of(MechVariant.HOUND, MechVariant.SIROCCO,
                MechVariant.BULWARK, MechVariant.HOUND),
                power.lanceForRemainingCharges(1));
        assertEquals(List.of(MechVariant.SIROCCO),
                power.lanceForRemainingCharges(0));
    }

    @Test
    public void configuredActivationUnloadsOneFourMechSquad() {
        BattleSimulation sim = openSim();
        sim.spawn(new EntitySpec("defender", Faction.DEFENDER, UnitType.MILITIA, 25, 25)
                .health(100_000f).attackDamage(0f));
        MechSupport power = new MechSupport(List.of(
                MechVariant.HOUND, MechVariant.SIROCCO,
                MechVariant.BULWARK, MechVariant.HOUND));
        sim.setCommandPowers(List.of(power));
        sim.getCommandPowerService().requestActivation(power.id, 15, 15);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(1, sim.getAirEntityIds().length);
        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertEquals(4, mission.marinesRemaining);
        assertEquals(List.of(MechVariant.HOUND, MechVariant.SIROCCO,
                        MechVariant.BULWARK, MechVariant.HOUND),
                List.of(mission.mechVariants));

        int mechCount = 0;
        int mechSquad = Squad.NO_SQUAD;
        for (int i = 0; i < 800 && mechCount < 4; i++) {
            sim.advance(BattleSimulation.TICK_DT);
            mechCount = 0;
            for (int u = 0; u < sim.liveUnitCount(); u++) {
                long candidate = sim.liveUnitAt(u);
                if (sim.identity().faction(candidate) != Faction.MARINE
                        || sim.identity().type(candidate) != UnitType.HEAVY_MECH) continue;
                int squadId = sim.squad().squadId(candidate);
                if (mechSquad == Squad.NO_SQUAD) mechSquad = squadId;
                assertEquals(mechSquad, squadId);
                mechCount++;
            }
        }

        assertEquals(4, mechCount);
        Squad squad = sim.getSquad(mechSquad);
        assertNotNull(squad);
        assertEquals(4, squad.originalSize);

        int hounds = 0;
        int siroccos = 0;
        int bulwarks = 0;
        for (int u = 0; u < sim.liveUnitCount(); u++) {
            long candidate = sim.liveUnitAt(u);
            if (sim.identity().faction(candidate) != Faction.MARINE
                    || sim.identity().type(candidate) != UnitType.HEAVY_MECH) continue;
            MechVariant variant = sim.identity().mechVariant(candidate);
            if (variant == MechVariant.HOUND) hounds++;
            else if (variant == MechVariant.SIROCCO) siroccos++;
            else if (variant == MechVariant.BULWARK) bulwarks++;
        }
        assertEquals(2, hounds);
        assertEquals(1, siroccos);
        assertEquals(1, bulwarks);
    }

    @Test
    public void campaignConfigurationInstallsFrozenDoctrineAndSubsystem() {
        BattleSimulation sim = openSim();
        sim.spawn(new EntitySpec("defender", Faction.DEFENDER, UnitType.MILITIA, 25, 25)
                .health(100_000f).attackDamage(0f));
        MechSupport power = MechSupport.configured(List.of(new MechDeploymentSpec(
                MechVariant.HOUND, MechRole.ASSAULT,
                MissileReplenisherComponent.ACCELERATED_FEED)));
        sim.setCommandPowers(List.of(power));
        sim.getCommandPowerService().requestActivation(power.id, 15, 15);
        sim.advance(BattleSimulation.TICK_DT);

        ShuttleMission mission = sim.world().mission(sim.getAirEntityIds()[0]);
        assertEquals(1, mission.mechDeployments.length);
        assertEquals(MissileReplenisherComponent.ACCELERATED_FEED,
                mission.mechDeployments[0].missileReplenisher());

        long mech = 0L;
        for (int i = 0; i < 600 && mech == 0L; i++) {
            sim.advance(BattleSimulation.TICK_DT);
            for (int u = 0; u < sim.liveUnitCount(); u++) {
                long candidate = sim.liveUnitAt(u);
                if (sim.identity().faction(candidate) == Faction.MARINE
                        && sim.identity().type(candidate) == UnitType.HEAVY_MECH) {
                    mech = candidate;
                    break;
                }
            }
        }

        assertTrue(mech != 0L);
        assertEquals(MechRole.ASSAULT, sim.world().mechLoadout(mech).role);
        assertEquals(MissileReplenisherComponent.ACCELERATED_FEED,
                sim.world().mechLoadout(mech).missileReplenisher());
    }
}
