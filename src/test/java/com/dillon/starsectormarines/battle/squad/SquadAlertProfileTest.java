package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.perception.NoiseEventBus;
import com.dillon.starsectormarines.battle.perception.NoiseKind;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Direct two-observer alert pass; no battle, scheduler, or world generation. */
class SquadAlertProfileTest {
    private final TickInnerProfile previousProfile = TickInnerProfile.currentIfBound();

    @AfterEach
    void restoreProfile() {
        if (previousProfile == null) TickInnerProfile.releaseCurrentThread();
        else TickInnerProfile.setCurrent(previousProfile);
    }

    @Test
    void recordsEveryStageOnceAndCountsActualCandidatePairsAndLosCalls() {
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        try (Fixture fixture = new Fixture()) {
            fixture.tick();
            fixture.assertState();
            for (TickInnerProfile.Bucket stage : new TickInnerProfile.Bucket[]{
                    TickInnerProfile.Bucket.ALERT_NOISE_DRAIN,
                    TickInnerProfile.Bucket.ALERT_BELIEF_RESET,
                    TickInnerProfile.Bucket.ALERT_AWARENESS,
                    TickInnerProfile.Bucket.ALERT_NOISE,
                    TickInnerProfile.Bucket.ALERT_INCOMING_FIRE,
                    TickInnerProfile.Bucket.ALERT_FINALIZE}) {
                assertEquals(1, profile.countOf(stage), stage.name());
                assertTrue(profile.nanosOf(stage) >= 0);
            }
            assertEquals(4, profile.countOf(TickInnerProfile.Bucket.ALERT_AWARENESS_CANDIDATE),
                    "both observers gather both bodies, including their own same-faction rejection");
            assertEquals(2, profile.countOf(TickInnerProfile.Bucket.ALERT_AWARENESS_LOS));
            assertEquals(2, profile.countOf(TickInnerProfile.Bucket.ALERT_NOISE_CONSIDERED));
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.ALERT_NOISE_DETECTED));
            assertEquals(2, profile.countOf(TickInnerProfile.Bucket.ALERT_INCOMING_CANDIDATE));
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.ALERT_INCOMING_LOS));
            assertEquals(0, profile.nanosOf(TickInnerProfile.Bucket.ALERT_AWARENESS_CANDIDATE));
            assertEquals(0, profile.nanosOf(TickInnerProfile.Bucket.ALERT_INCOMING_LOS));
        }
    }

    @Test
    void unboundProfileLeavesAlertAndFireDecisionsUnchanged() {
        TickInnerProfile.releaseCurrentThread();
        try (Fixture fixture = new Fixture()) {
            fixture.tick();
            fixture.assertState();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final NavigationService navigation;
        final UnitRosterService roster;
        final Squad marineSquad;
        final Squad defenderSquad;
        final NoiseEventBus noises = new NoiseEventBus(() -> 1);
        final ShotService shots = new ShotService();
        final SquadAlertSystem alert;

        Fixture() {
            NavigationGrid grid = new NavigationGrid(12, 6);
            for (int y = 0; y < 6; y++) {
                for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
            }
            navigation = new NavigationService(grid, new CellTopology(12, 6));
            roster = new UnitRosterService(navigation.getUnitIndex(), null);
            navigation.setRoster(roster);
            roster.setNavigationGrid(grid);
            int marineId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
            int defenderId = roster.mintSquad(Faction.DEFENDER, UnitType.MARINE);
            marineSquad = roster.getSquad(marineId);
            defenderSquad = roster.getSquad(defenderId);
            long marine = roster.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 2, 2).squad(marineId));
            long defender = roster.spawn(new EntitySpec("defender", Faction.DEFENDER, UnitType.MARINE, 4, 2).squad(defenderId));
            roster.vision().setVisionRange(marine, 10f);
            roster.vision().setVisionRange(defender, 10f);
            navigation.getUnitIndex().rebuild(roster);
            float x = roster.world().x(marine);
            float y = roster.world().y(marine);
            noises.post(x, y, 100f, 0L, Faction.DEFENDER, NoiseKind.SHOT);
            shots.postShot(new ShotEvent(roster.world().x(defender), roster.world().y(defender),
                    x, y, true, Faction.DEFENDER, 1f));
            alert = new SquadAlertSystem(navigation, roster, shots, noises);
        }

        void tick() { alert.tick(1f / 30f, 1); }

        void assertState() {
            assertEquals(SquadAlertLevel.ENGAGED, marineSquad.alertLevel);
            assertEquals(SquadAlertLevel.ENGAGED, defenderSquad.alertLevel);
            assertTrue(marineSquad._underFireAtLosThisTick);
            assertFalse(defenderSquad._underFireAtLosThisTick);
            assertTrue(marineSquad._suspiciousThisTick);
            assertEquals(0, noises.pendingCount());
        }

        @Override public void close() { navigation.close(); }
    }
}
