package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The kit a mission issues is a pure period over a force's squad ordinals. */
class SquadSupportKitTest {

    @Test
    void everyNthSquadIsIssuedTheItemTheLastOfEachRun() {
        SquadSupportKit kit = new SquadSupportKit(3, 2);
        int mortar = 0;
        int radio = 0;
        for (int i = 0; i < 12; i++) {
            if (kit.mortarTeam(i)) mortar++;
            if (kit.observer(i)) radio++;
        }
        assertEquals(4, mortar);
        assertEquals(6, radio);
        assertFalse(kit.mortarTeam(0));
        assertTrue(kit.mortarTeam(2));
        assertTrue(kit.observer(1));
    }

    @Test
    void aNonPositivePeriodIssuesNothingAndNothingDeclaredIsNone() {
        SquadSupportKit none = new SquadSupportKit(0, -4);
        assertTrue(none.isNone());
        for (int i = 0; i < 20; i++) assertFalse(none.mortarTeam(i) || none.observer(i));
        assertTrue(SquadSupportDeclaration.NONE.with(Faction.MARINE, none).isNone(), "an empty kit is not kept");
        assertEquals(SquadSupportKit.NONE, SquadSupportDeclaration.NONE.forSide(Faction.MARINE));
    }
}
