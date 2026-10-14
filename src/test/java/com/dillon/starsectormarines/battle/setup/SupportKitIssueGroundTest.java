package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.fixture.MarineSeatCommitment;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.SoldierAptitude;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.infantry.SquadSupportKit;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A company that starts on the ground is issued its kit by squad, in the order the squads were committed. */
class SupportKitIssueGroundTest {

    @Test
    void tubeGoesToTheFirstUnissuedSeatOfTheNamedSquad() {
        List<MarineSeatCommitment> seats = new ArrayList<>();
        for (int i = 0; i < 3; i++) seats.add(seat("a" + i, "alpha", null));
        seats.add(seat("b0", "bravo", SpecialEquipmentRegistry.COMMS_RADIO_ID));
        seats.add(seat("b1", "bravo", null));
        seats.add(seat("b2", "bravo", null));

        List<MarineSeatCommitment> issued = SupportKitIssue.issueGround(new SquadSupportKit(2, 0), seats);

        for (int i = 0; i < 3; i++) assertNull(issued.get(i).specialEquipmentId(), "first squad is not a mortar team");
        assertEquals(SpecialEquipmentRegistry.COMMS_RADIO_ID, issued.get(3).specialEquipmentId(), "issued kit is kept");
        assertEquals(SpecialEquipmentRegistry.FIELD_MORTAR_ID, issued.get(4).specialEquipmentId());
        assertNull(issued.get(5).specialEquipmentId());
        assertEquals("b1", issued.get(4).campaignSoldierId());
        assertEquals("bravo", issued.get(4).campaignSquadId());
    }

    @Test
    void noKitLeavesTheSeatsAlone() {
        List<MarineSeatCommitment> seats = List.of(seat("a0", "alpha", null), seat("a1", "alpha", null));
        assertSame(seats, SupportKitIssue.issueGround(new SquadSupportKit(0, 0), seats));
    }

    private static MarineSeatCommitment seat(String soldier, String squad, String special) {
        return new MarineSeatCommitment(soldier, WeaponRegistry.require(WeaponRegistry.PULSE_RIFLE_ID).id,
                EquipmentGrade.MILSPEC, new SoldierProfile(SoldierAptitude.GIFTED, 321), special,
                LayeredArmorFamily.CHARCOAL, 60f, 5f, 0.95f, 0.9f, squad, squad, soldier.endsWith("0"), 3, 0);
    }
}
