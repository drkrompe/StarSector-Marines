package com.dillon.starsectormarines.campaign.raid;

import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void theArmedModifierZeroesTheMarineLossRoll() {
        MutableStat losses = new MutableStat(1f);
        losses.modifyMult("prep", 1.5f);

        RaidOutcomeCommit.LOSSES.modifyMarineLossesStatPreRaid(null, null, losses);

        assertEquals(0f, losses.getModifiedValue());
    }
}
