package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.ruins.RuinsBatteries;
import com.dillon.starsectormarines.campaign.ruins.RuinsExpeditionMissionKey;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.campaign.ruins.RuinsSurvey;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrizes;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsExpeditionMissionFactoryTest {

    @Test
    void anExpeditionHasNoContractAndNoEmployer() {
        Mission mission = RuinsExpeditionMissionFactory.create(3, "ossuary_market", "Ossuary", RuinsGrade.EXTENSIVE,
                RuinsOccupant.SCAVENGERS);

        assertEquals(MissionSource.RUINS_EXPEDITION, mission.source);
        assertEquals(MissionType.RUINS, mission.type);
        assertEquals(RuinsGrade.EXTENSIVE, mission.ruinsGrade);
        assertEquals(new RuinsExpeditionMissionKey(3, "ossuary_market"), RuinsExpeditionMissionKey.parse(mission.id));
        assertEquals(-1L, mission.contractId);
        assertEquals(0, mission.employerShuttles);
        assertTrue(MissionForceEnvelope.allowsUnderstrength(mission), "the player's own expedition may go understrength");
    }

    @Test
    void largerRuinsNeverHoldAWeakerOccupant() {
        OperationTier previous = null;
        for (RuinsGrade grade : RuinsGrade.values()) {
            OperationTier tier = RuinsExpeditionMissionFactory.tierFor(grade);
            assertTrue(tier.compareTo(MissionType.RUINS.tierFloor) >= 0, grade + " below the family's floor");
            if (previous != null) assertTrue(tier.compareTo(previous) >= 0, grade + " weaker than the grade below");
            previous = tier;
        }
    }

    @Test
    void theBriefingIsTheStakesThenTheSurvey() {
        RuinsSurvey.Reading survey = new RuinsSurvey.Reading("ruinsSurveyScavengersAssessment",
                RuinsSurvey.Band.HEAVY, "ruinsSurveyScavengersBand",
                List.of("ruinsSurveyScavengersComms", "ruinsSurveyScavengersHeat"));

        List<MissionDialogue.Turn> turns = RuinsExpeditionMissionFactory.briefing("Ossuary", survey);

        assertEquals(4, turns.size());
        assertTrue(turns.get(0).speaker().isBlank(), "the stakes are unvoiced prose");
        assertTrue(turns.get(1).text().contains(Strings.get("ruinsSurveyBandHeavy")), "the assessment carries the band");
        assertEquals(Strings.get("ruinsSurveyScavengersHeat"), turns.get(3).text());
    }

    @Test
    void theBriefingEndsOnTheVaultsTell() {
        RuinsSurvey.Reading survey = new RuinsSurvey.Reading("ruinsSurveyScavengersAssessment",
                RuinsSurvey.Band.HEAVY, "ruinsSurveyScavengersBand", List.of("ruinsSurveyScavengersComms"));

        List<MissionDialogue.Turn> turns = RuinsExpeditionMissionFactory.briefing("Ossuary", survey,
                List.of("ruinsTellServerHall", "ruinsTellLikelyBetaCore"));

        assertEquals(5, turns.size());
        assertEquals(Strings.get("ruinsTellServerHall"), turns.get(3).text());
        assertEquals(Strings.get("ruinsTellLikelyBetaCore"), turns.get(4).text());
    }

    @Test
    void everyExpeditionToASiteMeetsTheSameGroundWithoutItsTakenFinds() {
        Mission first = RuinsExpeditionMissionFactory.create(3, "m", "Ossuary", RuinsGrade.VAST,
                RuinsOccupant.SCAVENGERS);
        Mission again = RuinsExpeditionMissionFactory.create(4, "m", "Ossuary", RuinsGrade.VAST,
                RuinsOccupant.SCAVENGERS, Set.of("field-cache-1"));

        assertEquals(first.battleSeed, again.battleSeed);
        assertEquals(Set.of("field-cache-1"), again.ruinsTakenFinds);
        assertTrue(first.ruinsTakenFinds.isEmpty());
    }

    @Test
    void aSiteKeepsTheBatteriesItsPrizeCallsFor() {
        for (int i = 0; i < 50; i++) {
            String market = "site_" + i;
            Mission mission = RuinsExpeditionMissionFactory.create(3, market, "Ossuary", RuinsGrade.VAST,
                    RuinsOccupant.DOMAIN_SECURITY, Set.of(), 9L);
            RuinsVaultPrize prize = RuinsVaultPrizes.draw(9L, market, RuinsGrade.VAST, RuinsOccupant.DOMAIN_SECURITY);
            assertEquals(RuinsBatteries.count(RuinsGrade.VAST, prize), mission.ruinsBatteries);
        }
        assertEquals(0, RuinsExpeditionMissionFactory.create(3, "m", "Ossuary", RuinsGrade.VAST,
                RuinsOccupant.SCAVENGERS).ruinsBatteries);
    }

    @Test
    void nothingToExploreBuildsNothing() {
        assertNull(RuinsExpeditionMissionFactory.create(0, "m", "Ossuary", RuinsGrade.VAST, RuinsOccupant.SCAVENGERS));
        assertNull(RuinsExpeditionMissionFactory.create(1, null, "Ossuary", RuinsGrade.VAST, RuinsOccupant.SCAVENGERS));
        assertNull(RuinsExpeditionMissionFactory.create(1, "m", "Ossuary", null, RuinsOccupant.SCAVENGERS));
        assertNull(RuinsExpeditionMissionFactory.create(1, "m", "Ossuary", RuinsGrade.VAST, null));
    }

    @Test
    void theOccupantRidesTheMissionAndChoosesOneDefendingFaction() {
        for (RuinsOccupant occupant : RuinsOccupant.values()) {
            Mission mission = RuinsExpeditionMissionFactory.create(3, "m", "Ossuary", RuinsGrade.VAST, occupant);
            assertEquals(occupant, mission.ruinsOccupant);
            assertEquals(mission.targetFactionId, mission.defenderFactionOverride, occupant + ": one defender");
        }
        Mission scavenged = RuinsExpeditionMissionFactory.create(3, "m", "Ossuary", RuinsGrade.VAST,
                RuinsOccupant.SCAVENGERS);
        assertEquals(RuinsOccupant.SCAVENGERS.factionId, scavenged.defenderFactionOverride);
    }
}
