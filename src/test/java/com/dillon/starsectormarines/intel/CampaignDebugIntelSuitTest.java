package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampaignDebugIntelSuitTest {
    @Test
    void explicitLearningIsPermanentAndIdempotentWithoutRefittingNamedPeople() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(4);
        List<String> ids = roster.soldiers().stream().map(MarineSoldier::id).toList();
        List<String> armor = roster.soldiers().stream().map(MarineSoldier::armorId).toList();
        assertEquals(2, CampaignDebugIntel.learnSuitTestPatterns(roster.armory(), false));
        assertTrue(roster.armory().ownsArmorTemplate("armor.specter-recovered"));
        assertTrue(roster.armory().ownsArmorTemplate("armor.arbalest-recovered"));
        assertFalse(roster.armory().ownsArmorTemplate("armor.specter-heavy"));
        assertEquals(0, CampaignDebugIntel.learnSuitTestPatterns(roster.armory(), false));
        assertEquals(2, CampaignDebugIntel.learnSuitTestPatterns(roster.armory(), true));
        assertTrue(roster.armory().ownsArmorTemplate("armor.specter-heavy"));
        assertTrue(roster.armory().ownsArmorTemplate("armor.arbalest"));
        assertEquals(0, CampaignDebugIntel.learnSuitTestPatterns(roster.armory(), true));
        assertEquals(ids, roster.soldiers().stream().map(MarineSoldier::id).toList());
        assertEquals(armor, roster.soldiers().stream().map(MarineSoldier::armorId).toList());
    }
}
