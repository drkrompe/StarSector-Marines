package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionForceEnvelopeTest {

    @Test
    void ordinaryGeneratedWorkSeparatesMinimumFromRecommendation() {
        Mission mission = mission(MissionSource.GENERATED, MissionType.RAID,
                OperationTier.ESTABLISHED);

        assertTrue(MissionForceEnvelope.allowsUnderstrength(mission));
        assertEquals(4, MissionForceEnvelope.minimumPersonnel(mission, 96));
        assertEquals(36, MissionForceEnvelope.recommendedPersonnel(mission));
    }

    /** A raid already on the ground will not wait for a full task force. */
    @Test
    void aColonyDefenceMayLaunchWithWhateverIsReady() {
        Mission defence = mission(MissionSource.POLITY_DEFENCE, MissionType.ASSAULT,
                OperationTier.VETERAN);

        assertTrue(MissionForceEnvelope.allowsUnderstrength(defence));
        assertEquals(4, MissionForceEnvelope.minimumPersonnel(defence, 96));
    }

    @Test
    void authoredAndConquestWorkKeepTheirExistingGate() {
        Mission story = mission(MissionSource.STORY, MissionType.RAID,
                OperationTier.FIRST_CONTRACT);
        Mission conquest = mission(MissionSource.GENERATED, MissionType.CONQUEST,
                OperationTier.REINFORCED);

        assertFalse(MissionForceEnvelope.allowsUnderstrength(story));
        assertFalse(MissionForceEnvelope.allowsUnderstrength(conquest));
        assertEquals(24, MissionForceEnvelope.minimumPersonnel(story, 24));
    }

    @Test
    void fullStrengthConquestRecommendsAThousandMarinesInOneBattle() {
        Mission conquest = mission(MissionSource.DEBUG, MissionType.CONQUEST,
                OperationTier.FULL_STRENGTH);

        assertEquals(84, MissionForceEnvelope.recommendedSquads(conquest));
        assertEquals(1_008, MissionForceEnvelope.recommendedPersonnel(conquest));
        assertFalse(MissionForceEnvelope.allowsUnderstrength(conquest));
    }

    @Test
    void selectedQualityReportsVisibleIssuedBandsWithoutAnAggregateScore() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(12);
        String squadId = roster.squads().get(0).id();

        MissionForceEnvelope.ExperienceMix mix =
                MissionForceEnvelope.selectedExperience(roster, Set.of(squadId));

        assertEquals(12, mix.total());
        assertTrue(mix.display().contains("Green") || mix.display().contains("Regular"));
    }

    private static Mission mission(MissionSource source, MissionType type,
                                   OperationTier tier) {
        return Mission.builder().id("test").name("Test").type(type)
                .source(source).tier(tier).risk(RiskLevel.LOW).build();
    }
}
