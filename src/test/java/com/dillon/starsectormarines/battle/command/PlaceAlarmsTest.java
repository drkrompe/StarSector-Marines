package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceAlarmsTest {

    private static final int BLOCK = 8;

    /** A fort aspect at (40, 40) with one belt cell at (60, 40), held by {@code holder}, built for the defender. */
    private static DefenceStructureFact aspect(Faction holder, boolean threatened) {
        return new DefenceStructureFact(DefenceStructureFact.Kind.FIRE_CONTROL, 40, 40, holder, Faction.DEFENDER,
                threatened, 6, 40, 40, 0, DefenceStructureFact.Family.BATTERY_FORT, new int[0], new int[]{60, 40});
    }

    private static PlaceAlarms.Reading quiet() {
        return reading(0f, 0f);
    }

    private static PlaceAlarms.Reading reading(float hostile, float losses) {
        return new PlaceAlarms.Reading() {
            @Override public float hostileAt(int bx, int by) { return hostile; }
            @Override public float lossesAt(int bx, int by) { return losses; }
        };
    }

    private static void update(PlaceAlarms alarms, int tick, DefenceStructureFact fact, PlaceAlarms.Reading reading) {
        alarms.update(tick, Faction.DEFENDER, List.of(fact), reading, BLOCK, 20, 20);
    }

    @Test
    void aQuietFortRaisesNoAlarm() {
        PlaceAlarms alarms = new PlaceAlarms();
        update(alarms, 0, aspect(Faction.DEFENDER, false), quiet());
        assertEquals(0f, alarms.floorAt(5, 5));
        assertTrue(alarms.alarmedBlocks().isEmpty());
    }

    @Test
    void anAspectBeingTakenHoldsAFloorOnTheFortsGroundThatFadesSlowly() {
        PlaceAlarms alarms = new PlaceAlarms();
        update(alarms, 0, aspect(Faction.DEFENDER, true), quiet());
        assertEquals(PlaceAlarms.ALARM_PRESSURE, alarms.floorAt(5, 5), 1e-4f, "the aspect's block");
        assertEquals(PlaceAlarms.ALARM_PRESSURE, alarms.floorAt(7, 5), 1e-4f, "the belt's block");
        assertEquals(0f, alarms.floorAt(12, 12), "ground off the fort");

        update(alarms, (int) PlaceAlarms.HALF_LIFE_TICKS, aspect(Faction.DEFENDER, false), quiet());
        assertEquals(PlaceAlarms.ALARM_PRESSURE / 2f, alarms.floorAt(5, 5), 1e-3f, "one half-life with no new trigger");
    }

    @Test
    void ownLossesOnTheFortsGroundAreATrigger() {
        PlaceAlarms alarms = new PlaceAlarms();
        update(alarms, 0, aspect(Faction.DEFENDER, false), reading(0f, 0f));
        update(alarms, 75, aspect(Faction.DEFENDER, false), reading(0f, PlaceAlarms.LOSS_TRIGGER));
        assertTrue(alarms.floorAt(5, 5) > 0f, "the picket went dark");
    }

    @Test
    void aFortWhoseEveryAspectIsLostIsALostCause() {
        PlaceAlarms alarms = new PlaceAlarms();
        update(alarms, 0, aspect(Faction.DEFENDER, true), quiet());
        update(alarms, 75, aspect(Faction.MARINE, false), quiet());
        assertEquals(0f, alarms.floorAt(5, 5), "no more squads are fed into it piecemeal");
        assertTrue(alarms.describe().contains("lost"));
    }

    @Test
    void anEnemyFortIsNotOurAlarm() {
        PlaceAlarms alarms = new PlaceAlarms();
        alarms.update(0, Faction.MARINE, List.of(aspect(Faction.DEFENDER, true)), quiet(), BLOCK, 20, 20);
        assertEquals(0f, alarms.floorAt(5, 5));
    }
}
