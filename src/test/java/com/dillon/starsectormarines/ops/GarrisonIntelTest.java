package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.setup.DefenderRoster;
import com.dillon.starsectormarines.battle.world.gen.MarketGarrison;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The holo-tank reads the garrison off the profile launch builds the battle from, so its headcount is the battle's. */
class GarrisonIntelTest {

    private static final TargetProfile MARKET = TargetProfile.NEUTRAL.withFactionId("hegemony")
            .withGarrison(new MarketGarrison(150f, 0.45f, 0.5f, 1f));

    private static Mission contract(MissionSource source) {
        return Mission.builder().id("contract-1").source(source).type(MissionType.ASSAULT)
                .tier(OperationTier.VETERAN).risk(RiskLevel.HIGH).build();
    }

    @Test
    void theHeadcountIsTheBattlesWhateverTheLandingDraws() {
        Mission m = contract(MissionSource.GENERATED);
        TargetProfile faced = MissionLaunch.facedProfile(m, MARKET);
        GarrisonIntel intel = GarrisonIntel.estimate(m, faced, true, null);
        assertEquals(intel.defenders(), intel.regulars() + intel.militia() + intel.mechs());
        assertTrue(intel.mechs() > 0, "a heavy-armour world at high risk fields mechs");
        for (float attackers : new float[]{0f, 40f, 400f, Float.POSITIVE_INFINITY}) {
            DefenderRoster battle = DefenderRoster.forMission(m.type, m.tier, m.risk, true, attackers, null,
                    faced.garrison());
            assertEquals(intel.defenders(), battle.totalCount, "attacker score " + attackers);
            assertTrue(battle.mechCount <= intel.mechs(), "mechs are up to the estimate");
            assertTrue(battle.eliteCount >= intel.regulars() && battle.militiaCount >= intel.militia());
        }
    }

    @Test
    void anInvasionFacesTheWholeGarrison() {
        Mission m = contract(MissionSource.NEX_INVASION);
        GarrisonIntel intel = GarrisonIntel.estimate(m, MissionLaunch.facedProfile(m, MARKET), false, null);
        assertEquals(DefenderRoster.wholeGarrisonCount(MARKET.garrison()), intel.defenders());
        assertEquals(0, intel.mechs());
    }

    @Test
    void aNamedDefenderChangesTheFactionAndNotTheGarrison() {
        Mission m = Mission.builder(contract(MissionSource.GENERATED)).defenderFactionOverride("tritachyon").build();
        TargetProfile faced = MissionLaunch.facedProfile(m, MARKET);
        assertEquals("tritachyon", faced.factionId());
        assertEquals(MARKET.garrison(), faced.garrison());
    }

    @Test
    void onlyMissionsWhoseDefendersAreDerivedAreRead() {
        assertTrue(MissionLaunch.derivesDefenders(contract(MissionSource.GENERATED)));
        assertFalse(MissionLaunch.derivesDefenders(contract(MissionSource.DEBUG)));
        assertFalse(MissionLaunch.derivesDefenders(Mission.builder(contract(MissionSource.STORY))
                .id(OpeningOperationKind.values()[0].missionId).build()));
    }
}
