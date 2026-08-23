package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.sim.CombatTelemetryRow;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link SoldierCareer} accumulation through
 * {@link MarineRoster#applySoldierOutcome} — progression S3 slice 3
 * ({@code s3-per-soldier-telemetry.md}).
 *
 * <p>The distinction under test throughout: {@code outcomes} is the
 * <em>deployment manifest</em> and telemetry is the <em>evidence</em>. Every
 * marine in the manifest gets a deployment counted; only the ones the battle
 * recorded something for get counters.
 */
class SoldierCareerTest {

    private static CombatTelemetryRow row(String soldierId, boolean survived,
                                          int fired, int hit, float dealt,
                                          float taken, int kills) {
        return new CombatTelemetryRow(1L, "marine", Faction.MARINE, UnitType.MARINE,
                soldierId, survived, fired, hit, dealt, 0f, taken, kills, 0);
    }

    private static MarineRoster rosterWithSoldiers(int count) {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(count);
        return roster;
    }

    @Test
    void aFreshSoldierStartsWithAnEmptyRecord() {
        MarineSoldier soldier = new MarineSoldier("id", "Marine 001", null);
        SoldierCareer career = soldier.career();
        assertNotNull(career, "career is never null");
        assertEquals(0, career.missionsDeployed());
        assertEquals(0, career.kills());
        assertEquals(0f, career.landedFraction(), 0.0001f, "no rounds fired yet");
    }

    @Test
    void aVictoriousDeploymentFoldsItsTelemetryIntoTheCareer() {
        MarineRoster roster = rosterWithSoldiers(2);
        MarineSoldier soldier = roster.soldiers().get(0);

        Map<String, MarineSoldierStatus> outcomes = new LinkedHashMap<>();
        outcomes.put(soldier.id(), MarineSoldierStatus.ACTIVE);
        Map<String, CombatTelemetryRow> telemetry = Collections.singletonMap(
                soldier.id(), row(soldier.id(), true, 40, 14, 260f, 31f, 3));

        roster.applySoldierOutcome(outcomes, 50, 100f, 10f, telemetry, true);

        SoldierCareer career = soldier.career();
        assertEquals(1, career.missionsDeployed());
        assertEquals(1, career.missionsWon());
        assertEquals(40, career.roundsFired());
        assertEquals(14, career.roundsHit());
        assertEquals(260f, career.damageDealt(), 0.001f);
        assertEquals(31f, career.damageTaken(), 0.001f);
        assertEquals(3, career.kills());
        assertEquals(0, career.timesWounded());
        assertEquals(0.35f, career.landedFraction(), 0.0001f);
        assertEquals(50, soldier.experienceXp(), "the existing XP payout is untouched");
    }

    @Test
    void aMarineWhoNeverFiredStillCountsAsDeployed() {
        MarineRoster roster = rosterWithSoldiers(2);
        MarineSoldier soldier = roster.soldiers().get(0);

        roster.applySoldierOutcome(
                Collections.singletonMap(soldier.id(), MarineSoldierStatus.ACTIVE),
                30, 100f, 10f, Collections.emptyMap(), false);

        SoldierCareer career = soldier.career();
        assertEquals(1, career.missionsDeployed(), "they were there");
        assertEquals(0, career.missionsWon());
        assertEquals(0, career.roundsFired());
    }

    @Test
    void woundsAreCountedAndTotalsAccumulateAcrossMissions() {
        MarineRoster roster = rosterWithSoldiers(2);
        MarineSoldier soldier = roster.soldiers().get(0);

        roster.applySoldierOutcome(
                Collections.singletonMap(soldier.id(), MarineSoldierStatus.ACTIVE),
                50, 100f, 10f,
                Collections.singletonMap(soldier.id(), row(soldier.id(), true, 20, 8, 100f, 5f, 1)),
                true);
        roster.applySoldierOutcome(
                Collections.singletonMap(soldier.id(), MarineSoldierStatus.WIA),
                50, 120f, 10f,
                Collections.singletonMap(soldier.id(), row(soldier.id(), false, 12, 3, 40f, 60f, 0)),
                false);

        SoldierCareer career = soldier.career();
        assertEquals(2, career.missionsDeployed());
        assertEquals(1, career.missionsWon(), "only the first was a win");
        assertEquals(1, career.timesWounded());
        assertEquals(32, career.roundsFired(), "lifetime totals, not last-mission");
        assertEquals(11, career.roundsHit());
        assertEquals(140f, career.damageDealt(), 0.001f);
        assertEquals(65f, career.damageTaken(), 0.001f);
        assertEquals(1, career.kills());
        assertEquals(MarineSoldierStatus.WIA, soldier.status());
    }

    @Test
    void aFallenMarineKeepsTheRecordOfTheMissionThatKilledThem() {
        MarineRoster roster = rosterWithSoldiers(2);
        MarineSoldier soldier = roster.soldiers().get(0);

        roster.applySoldierOutcome(
                Collections.singletonMap(soldier.id(), MarineSoldierStatus.KIA),
                50, 100f, 10f,
                Collections.singletonMap(soldier.id(), row(soldier.id(), false, 18, 9, 130f, 25f, 2)),
                true);

        SoldierCareer career = soldier.career();
        assertEquals(MarineSoldierStatus.KIA, soldier.status());
        assertEquals(1, career.missionsDeployed());
        assertEquals(2, career.kills(), "what they did before they fell is still on the record");
        assertEquals(130f, career.damageDealt(), 0.001f);
        assertTrue(soldier.experienceXp() == 0, "the dead earn no experience payout");
    }

    @Test
    void theTelemetryFreeOverloadStillCountsDeployments() {
        MarineRoster roster = rosterWithSoldiers(2);
        MarineSoldier soldier = roster.soldiers().get(0);

        roster.applySoldierOutcome(
                Collections.singletonMap(soldier.id(), MarineSoldierStatus.ACTIVE),
                20, 100f, 10f);

        assertEquals(1, soldier.career().missionsDeployed());
        assertEquals(0, soldier.career().missionsWon(),
                "the old overload cannot know the result, so it never claims a win");
    }
}
