package com.dillon.starsectormarines.marine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class CampaignMechSquadPadsTest {

    private static CampaignMech mech(String id) {
        return new CampaignMech(id, id, MechVariant.HOUND, MechRole.ASSAULT, MissileReplenisherComponent.STANDARD.id());
    }

    private static CampaignVehicle vehicle(String id) {
        return new CampaignVehicle(id, id, VehicleType.HEAVY_APC);
    }

    @Test
    void aMachineLeavingLeavesTheOthersOnTheirPadsAndTheNextTakesTheGap() {
        CampaignMechSquad squad = new CampaignMechSquad("squad", "Squad");
        CampaignMech first = mech("first");
        CampaignVehicle second = vehicle("second");
        CampaignMech third = mech("third");
        squad.add(first);
        squad.add(second);
        squad.add(third);

        assertTrue(squad.remove(first));
        assertFalse(squad.padHeld(0));
        assertEquals(1, squad.padOf("second"));
        assertEquals(2, squad.padOf("third"));

        assertTrue(squad.remove(second));
        assertEquals(2, squad.padOf("third"));

        squad.add(mech("fourth"));
        assertEquals(0, squad.padOf("fourth"));
    }

    @Test
    void aMachineMovesOnlyOntoAVacantPad() {
        CampaignMechSquad squad = new CampaignMechSquad("squad", "Squad");
        squad.add(mech("first"));
        squad.add(vehicle("second"));

        assertFalse(squad.standOn("first", 1));
        assertTrue(squad.standOn("first", 3));
        assertEquals(3, squad.padOf("first"));
        assertNull(squad.mechOnPad(0));
        assertEquals("first", squad.mechOnPad(3).id());
    }

    @Test
    void machinesPastAFewerPadShipComeDownOntoVacantOnes() {
        CampaignMechSquad squad = new CampaignMechSquad("squad", "Squad");
        squad.add(mech("first"));
        squad.add(mech("second"));
        squad.standOn("second", 3);

        squad.fitPadsTo(2);
        assertEquals(0, squad.padOf("first"));
        assertEquals(1, squad.padOf("second"));
    }

    @Test
    void anOlderSaveSeatsItsMechsThenItsVehicles() throws Exception {
        CampaignMechSquad squad = new CampaignMechSquad("squad", "Squad");
        squad.add(vehicle("hull"));
        squad.add(mech("walker"));
        Field pads = CampaignMechSquad.class.getDeclaredField("pads");
        pads.setAccessible(true);
        pads.set(squad, null);

        Method readResolve = CampaignMechSquad.class.getDeclaredMethod("readResolve");
        readResolve.setAccessible(true);
        readResolve.invoke(squad);

        assertEquals(0, squad.padOf("walker"));
        assertEquals(1, squad.padOf("hull"));
    }
}
