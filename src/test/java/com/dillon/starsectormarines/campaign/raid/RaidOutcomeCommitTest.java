package com.dillon.starsectormarines.campaign.raid;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.lang.reflect.Proxy;

class RaidOutcomeCommitTest {

    @Test
    void aWonRaidIsCommittedAsItWasSetUp() {
        TempData temp = new TempData();
        temp.objectives.add(null);
        temp.raidMult = 0.6f;

        RaidOutcomeCommit.prepare(temp, true);

        assertEquals(1, temp.objectives.size());
        assertEquals(0.6f, temp.raidMult);
    }

    @Test
    void aLostRaidIsCommittedWithNoObjectives() {
        TempData temp = new TempData();
        temp.objectives.add(null);
        temp.objectives.add(null);

        RaidOutcomeCommit.prepare(temp, false);

        assertTrue(temp.objectives.isEmpty());
    }

    @Test
    void aWonDisruptingRaidCommitsAtTheShareOfTheWorksWrecked() {
        TempData temp = new TempData();
        temp.raidType = RaidType.DISRUPT;
        temp.raidMult = 0.8f;

        RaidOutcomeCommit.prepare(temp, true, 0.5f);

        assertEquals(0.4f, temp.raidMult, 1e-6);
    }

    @Test
    void aRaidForValuablesIgnoresTheWorksShare() {
        TempData temp = new TempData();
        temp.raidType = RaidType.VALUABLE;
        temp.raidMult = 0.8f;

        RaidOutcomeCommit.prepare(temp, true, 0.5f);

        assertEquals(0.8f, temp.raidMult);
    }

    @Test
    void aRaidForValuablesNamesNoIndustryEvenWhenItsGoodsComeFromOne() {
        TempData temp = new TempData();
        temp.raidType = RaidType.VALUABLE;
        temp.target = industry("heavyindustry");

        assertNull(RaidOutcomeCommit.targetIndustryId(temp));
    }

    @Test
    void aDisruptingRaidNamesItsTargetIndustry() {
        TempData temp = new TempData();
        temp.raidType = RaidType.DISRUPT;
        temp.target = industry("refining");

        assertEquals("refining", RaidOutcomeCommit.targetIndustryId(temp));
    }

    private static Industry industry(String id) {
        return (Industry) Proxy.newProxyInstance(Industry.class.getClassLoader(), new Class<?>[]{Industry.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getId")) return id;
                    throw new AssertionError(method.getName());
                });
    }

    @Test
    void theArmedModifierZeroesTheMarineLossRoll() {
        MutableStat losses = new MutableStat(1f);
        losses.modifyMult("prep", 1.5f);

        RaidOutcomeCommit.LOSSES.modifyMarineLossesStatPreRaid(null, null, losses);

        assertEquals(0f, losses.getModifiedValue());
    }
}
