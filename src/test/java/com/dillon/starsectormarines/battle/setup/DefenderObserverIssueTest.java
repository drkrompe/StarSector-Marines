package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.infantry.SquadSupportKit;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Which defender infantry squads carry the radio their side's pool is directed through. */
class DefenderObserverIssueTest {

    private static EntitySpec unit() {
        return new EntitySpec("d", Faction.DEFENDER, UnitType.MILITIA, 0, 0);
    }

    @Test
    void everyNthInfantrySquadCarriesTheRadioOnItsFirstMemberOnly() {
        DefenderObserverIssue issue = DefenderObserverIssue.of(new SquadSupportKit(0, 2));
        String radio = SpecialEquipmentRegistry.COMMS_RADIO_ID;
        EntitySpec[] first = new EntitySpec[4];
        for (int squad = 0; squad < 4; squad++) {
            first[squad] = unit();
            issue.issue(first[squad], true, true);
            EntitySpec second = unit();
            issue.issue(second, true, false);
            assertNull(second.specialEquipment, "only the first member of a squad can carry it");
        }
        assertNull(first[0].specialEquipment);
        assertEquals(radio, first[1].specialEquipment.id());
        assertNull(first[2].specialEquipment);
        assertEquals(radio, first[3].specialEquipment.id());
    }

    @Test
    void aMechIsNeverIssuedOneAndDoesNotCountAsASquad() {
        DefenderObserverIssue issue = DefenderObserverIssue.of(new SquadSupportKit(0, 1));
        EntitySpec mech = unit();
        issue.issue(mech, false, true);
        assertNull(mech.specialEquipment);
        EntitySpec infantry = unit();
        issue.issue(infantry, true, true);
        assertEquals(SpecialEquipmentRegistry.COMMS_RADIO_ID, infantry.specialEquipment.id());
    }

    @Test
    void aDeclarationThatStatesNoKitChangesNothing() {
        DefenderObserverIssue issue = DefenderObserverIssue.of(SquadSupportKit.NONE);
        EntitySpec unit = unit();
        issue.issue(unit, true, true);
        assertNull(unit.specialEquipment);
    }
}
