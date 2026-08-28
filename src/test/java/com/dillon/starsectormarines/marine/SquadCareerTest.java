package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.sim.CombatTelemetryRow;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for squad-grain career evidence ({@code progression-nouns.md}).
 *
 * <p>The distinction under test throughout: attribution is the squad id
 * <em>frozen at spawn</em> and carried home on the telemetry row, never the
 * squad a marine happens to sit in when the debrief runs.
 */
class SquadCareerTest {

    private static CombatTelemetryRow row(String soldierId, String squadId, boolean survived,
                                          int fired, int hit, float dealt, float friendly,
                                          float taken, int kills) {
        return new CombatTelemetryRow(1L, "marine", Faction.MARINE, UnitType.MARINE,
                soldierId, squadId, survived, fired, hit, dealt, friendly, taken, kills, 0);
    }

    private static MarineRoster rosterWithSoldiers(int count) {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(count);
        return roster;
    }

    /** The first non-reserve squad the bootstrap complement filled. */
    private static MarineSquad lineSquad(MarineRoster roster) {
        for (MarineSquad squad : roster.squads()) {
            if (!squad.reserve() && !squad.memberIds().isEmpty()) return squad;
        }
        throw new IllegalStateException("bootstrap produced no manned line squad");
    }

    @Test
    void aFreshSquadStartsWithAnEmptyRecord() {
        MarineSquad squad = new MarineSquad("Squad 01");
        SquadCareer career = squad.career();
        assertNotNull(career, "career is never null");
        assertEquals(0, career.missionsDeployed());
        assertEquals(0, career.kills());
        assertEquals(0f, career.landedFraction(), 0.0001f);
        assertEquals(0f, career.casualtyRate(), 0.0001f);
    }

    @Test
    void aDeploymentFoldsEveryMemberIntoOneSquadRecord() {
        MarineRoster roster = rosterWithSoldiers(12);
        MarineSquad squad = lineSquad(roster);
        List<String> members = squad.memberIds();
        String a = members.get(0);
        String b = members.get(1);

        Map<String, MarineSoldierStatus> outcomes = new LinkedHashMap<>();
        outcomes.put(a, MarineSoldierStatus.ACTIVE);
        outcomes.put(b, MarineSoldierStatus.ACTIVE);
        Map<String, CombatTelemetryRow> telemetry = new LinkedHashMap<>();
        telemetry.put(a, row(a, squad.id(), true, 40, 14, 260f, 5f, 31f, 3));
        telemetry.put(b, row(b, squad.id(), true, 20, 6, 100f, 0f, 12f, 1));

        roster.applySoldierOutcome(outcomes, 50, 100f, 10f, telemetry, true);

        SquadCareer career = squad.career();
        assertEquals(1, career.missionsDeployed(), "one mission, however many billets filled");
        assertEquals(1, career.missionsWon());
        assertEquals(2, career.marinesDeployed());
        assertEquals(0, career.casualties());
        assertEquals(60, career.roundsFired());
        assertEquals(20, career.roundsHit());
        assertEquals(360f, career.damageDealt(), 0.001f);
        assertEquals(5f, career.friendlyFireDamage(), 0.001f);
        assertEquals(43f, career.damageTaken(), 0.001f);
        assertEquals(4, career.kills());
    }

    @Test
    void squadTotalsReconcileAgainstThePerMarineFolds() {
        MarineRoster roster = rosterWithSoldiers(12);
        MarineSquad squad = lineSquad(roster);
        String a = squad.memberIds().get(0);
        String b = squad.memberIds().get(1);

        Map<String, MarineSoldierStatus> outcomes = new LinkedHashMap<>();
        outcomes.put(a, MarineSoldierStatus.ACTIVE);
        outcomes.put(b, MarineSoldierStatus.ACTIVE);
        Map<String, CombatTelemetryRow> telemetry = new LinkedHashMap<>();
        telemetry.put(a, row(a, squad.id(), true, 33, 11, 210f, 7f, 19f, 2));
        telemetry.put(b, row(b, squad.id(), true, 17, 5, 60f, 3f, 40f, 1));

        roster.applySoldierOutcome(outcomes, 50, 100f, 10f, telemetry, true);

        int marineRounds = roster.soldierById(a).career().roundsFired()
                + roster.soldierById(b).career().roundsFired();
        float marineFriendly = roster.soldierById(a).career().friendlyFireDamage()
                + roster.soldierById(b).career().friendlyFireDamage();
        assertEquals(marineRounds, squad.career().roundsFired(),
                "the squad is the sum of the marines who served it");
        assertEquals(marineFriendly, squad.career().friendlyFireDamage(), 0.001f);
    }

    @Test
    void casualtiesAreCountedAndTheFallenStillCreditTheirSquad() {
        MarineRoster roster = rosterWithSoldiers(12);
        MarineSquad squad = lineSquad(roster);
        String killed = squad.memberIds().get(0);
        String wounded = squad.memberIds().get(1);

        Map<String, MarineSoldierStatus> outcomes = new LinkedHashMap<>();
        outcomes.put(killed, MarineSoldierStatus.KIA);
        outcomes.put(wounded, MarineSoldierStatus.WIA);
        Map<String, CombatTelemetryRow> telemetry = new LinkedHashMap<>();
        telemetry.put(killed, row(killed, squad.id(), false, 18, 9, 130f, 0f, 25f, 2));
        telemetry.put(wounded, row(wounded, squad.id(), true, 10, 2, 30f, 0f, 60f, 0));

        roster.applySoldierOutcome(outcomes, 50, 100f, 10f, telemetry, false);

        SquadCareer career = squad.career();
        assertEquals(2, career.marinesDeployed());
        assertEquals(2, career.casualties(), "WIA and KIA both cost the squad a marine");
        assertEquals(2, career.kills(), "what the fallen did before they fell is on the record");
        assertEquals(1, career.missionsDeployed());
        assertEquals(0, career.missionsWon(), "the mission was lost");
        assertEquals(1f, career.casualtyRate(), 0.0001f);
    }

    @Test
    void attributionIsFrozenAtSpawnAndIgnoresLaterReorganization() {
        MarineRoster roster = rosterWithSoldiers(12);
        MarineSquad deployedWith = lineSquad(roster);
        MarineSquad other = roster.createSquad();
        assertNotNull(other, "the fixture needs an empty squad to transfer into");

        String marine = deployedWith.memberIds().get(0);
        // The battle froze this marine into `deployedWith`; the debrief runs
        // against a roster where they have since been transferred away.
        Map<String, CombatTelemetryRow> telemetry = Map.of(
                marine, row(marine, deployedWith.id(), true, 25, 10, 150f, 0f, 8f, 2));
        assertTrue(roster.transferSoldier(marine, other.id()), "transfer should succeed");

        roster.applySoldierOutcome(
                Map.of(marine, MarineSoldierStatus.ACTIVE), 50, 100f, 10f, telemetry, true);

        assertEquals(2, deployedWith.career().kills(),
                "credit follows the squad frozen at spawn");
        assertEquals(0, other.career().kills(),
                "the squad they were moved into after the battle earns nothing");
        assertEquals(0, other.career().missionsDeployed());
    }

    @Test
    void theRecordSurvivesLosingEveryMember() {
        MarineRoster roster = rosterWithSoldiers(12);
        MarineSquad squad = lineSquad(roster);
        List<String> members = List.copyOf(squad.memberIds());

        Map<String, MarineSoldierStatus> first = new LinkedHashMap<>();
        Map<String, CombatTelemetryRow> telemetry = new LinkedHashMap<>();
        for (String id : members) {
            first.put(id, MarineSoldierStatus.KIA);
            telemetry.put(id, row(id, squad.id(), false, 10, 4, 50f, 0f, 30f, 1));
        }
        roster.applySoldierOutcome(first, 50, 100f, 10f, telemetry, true);

        int killsBefore = squad.career().kills();
        int missionsBefore = squad.career().missionsDeployed();
        assertEquals(members.size(), squad.career().casualties(), "the squad was wiped out");

        // Reconstitute the same squad: the fallen hold no living billet, so a
        // full complement of replacements refills it.
        List<MarineSoldier> replacements =
                roster.recruitToSquad(squad.id(), MarineSquad.CAPACITY);
        assertEquals(members.size(), replacements.size(), "every billet refilled");
        for (MarineSoldier replacement : replacements) {
            assertEquals(0, replacement.career().missionsDeployed(),
                    "a replacement brings no record of their own");
        }

        assertEquals(killsBefore, squad.career().kills(),
                "the formation keeps its history through a total loss");
        assertEquals(missionsBefore, squad.career().missionsDeployed());
    }
}
