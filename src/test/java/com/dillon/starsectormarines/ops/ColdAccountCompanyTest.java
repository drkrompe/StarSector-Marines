package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineRoster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Cold Account company issues one of its cards to each line squad in turn, all on the one sheet. */
class ColdAccountCompanyTest {

    @Test
    void eachLineSquadTakesTheNextCardOnTheSheet() {
        MarineRoster roster = new MarineRoster();
        roster.ensureActiveSoldiers(ColdAccountCompany.WEAPON_CARDS.size() * 12);
        ColdAccountCompany.issue(roster);
        var squads = roster.squads().stream().filter(squad -> !squad.reserve()).toList();
        assertEquals(ColdAccountCompany.WEAPON_CARDS.size(), squads.size());
        for (int i = 0; i < squads.size(); i++) {
            assertEquals(ColdAccountCompany.WEAPON_CARDS.get(i), squads.get(i).weaponDoctrineId());
            assertEquals(ColdAccountCompany.ARMOR_SHEET, squads.get(i).armorDoctrineId());
        }
    }
}
