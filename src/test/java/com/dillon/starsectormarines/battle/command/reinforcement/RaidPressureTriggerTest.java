package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.reinforcement.ReinforcementRequest.Strength;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A Raid's garrison columns: when the next is due, and how large ({@code raid-demolish-the-works.md}, slice 8). */
class RaidPressureTriggerTest {

    private static final RaidPressureTrigger.Clock CLOCK =
            new RaidPressureTrigger.Clock(180, 60, Strength.MEDIUM, Strength.SMALL, 2);

    private static int s(int seconds) {
        return Math.round(seconds / BattleSimulation.TICK_DT);
    }

    @Test
    void aQuietMarketSendsColumnsOnItsRoutineClock() {
        assertEquals(s(180), RaidPressureTrigger.due(0, -1, CLOCK));
        assertEquals(s(200) + s(180), RaidPressureTrigger.due(s(200), -1, CLOCK));
    }

    @Test
    void theAlarmBringsTheNextColumnForwardAndQuickensTheClock() {
        assertEquals(s(100) + s(60), RaidPressureTrigger.due(0, s(100), CLOCK),
                "raised at 100 s, the next column comes 60 s later, not at 180 s");
        assertEquals(s(180), RaidPressureTrigger.due(0, s(150), CLOCK),
                "a routine column already nearer than the alarm's still comes first");
        assertEquals(s(300) + s(60), RaidPressureTrigger.due(s(300), s(100), CLOCK));
    }

    @Test
    void eachSecondColumnIsALargerOneUpToTheLargest() {
        assertEquals(Strength.SMALL, RaidPressureTrigger.columnStrength(0, Strength.SMALL));
        assertEquals(Strength.SMALL, RaidPressureTrigger.columnStrength(1, Strength.SMALL));
        assertEquals(Strength.MEDIUM, RaidPressureTrigger.columnStrength(2, Strength.SMALL));
        assertEquals(Strength.LARGE, RaidPressureTrigger.columnStrength(4, Strength.SMALL));
        assertEquals(Strength.LARGE, RaidPressureTrigger.columnStrength(9, Strength.SMALL));
    }

    @Test
    void armourJoinsFromItsColumnTheGateHullFirstThenTheYardTank() {
        List<VehicleType> line = List.of(VehicleType.HEAVY_APC, VehicleType.MEDIUM_TANK);
        assertNull(RaidPressureTrigger.armour(1, 2, line), "the first two columns come without");
        assertEquals(VehicleType.HEAVY_APC, RaidPressureTrigger.armour(2, 2, line));
        assertEquals(VehicleType.MEDIUM_TANK, RaidPressureTrigger.armour(3, 2, line));
        assertEquals(VehicleType.MEDIUM_TANK, RaidPressureTrigger.armour(8, 2, line));
        assertNull(RaidPressureTrigger.armour(8, -1, line), "an open market sends none");
        assertNull(RaidPressureTrigger.armour(8, 2, List.of()));
    }

    @Test
    void anEscortParksShortOfTheTrucksDrop() {
        assertArrayEquals(new float[]{1, 2, 3}, ConvoyMeans.escortLeg(new float[]{1, 2, 3, 4, 5, 6, 7}));
        assertArrayEquals(new float[]{1, 2}, ConvoyMeans.escortLeg(new float[]{1, 2, 3}));
        assertArrayEquals(new float[]{1, 1}, ConvoyMeans.escortLeg(new float[]{1}));
    }
}
