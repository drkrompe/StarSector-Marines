package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuinsDefenceTest {

    @Test
    void anEmptySiteFieldsNoOne() {
        RuinsDefence defence = RuinsDefence.of(RuinsOccupant.NOBODY);
        assertEquals(RuinsDefence.NONE, defence);
        for (RuinsGrade grade : RuinsGrade.values()) {
            assertNull(BattleSetup.ruinsCrew(RuinsOccupant.NOBODY, grade), grade + " empty site has a crew");
        }
        DefenderRoster roster = BattleSetup.ruinsRoster(defence, null, RiskLevel.HIGH);
        assertEquals(0, roster.totalCount);
        assertEquals(0, roster.mechCount);
    }

    @Test
    void onlyTheScavengersFieldACrew() {
        for (RuinsOccupant occupant : RuinsOccupant.values()) {
            if (occupant == RuinsOccupant.SCAVENGERS) assertEquals(RuinsDefence.CREW, RuinsDefence.of(occupant));
            else {
                assertNotEquals(RuinsDefence.CREW, RuinsDefence.of(occupant), occupant + " fields a crew");
                assertNull(BattleSetup.ruinsCrew(occupant, RuinsGrade.VAST), occupant + " has a crew");
                assertEquals(0, BattleSetup.ruinsRoster(RuinsDefence.of(occupant), null, RiskLevel.HIGH).totalCount,
                        occupant + " fields generic squads");
            }
        }
    }

    @Test
    void eachOccupiedSiteFieldsItsOwnSide() {
        assertEquals(RuinsDefence.SECURITY, RuinsDefence.of(RuinsOccupant.DOMAIN_SECURITY));
        assertEquals(RuinsDefence.SECURITY, RuinsDefence.of(RuinsOccupant.CONTINGENCY_SECURITY));
        assertEquals(RuinsDefence.SLEEPERS, RuinsDefence.of(RuinsOccupant.RAV));
    }
}
