package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.warship.ShipClass.HullSize;
import com.dillon.starsectormarines.battle.warship.ShipClass.Role;
import com.dillon.starsectormarines.battle.warship.ShipClass.Shield;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The class is read from a hull's raw columns; these are the rows the vanilla table actually carries. */
class ShipClassTest {

    @Test
    void aGunShipIsCombatAtItsSize() {
        ShipClass onslaught = ShipClass.classify("CAPITAL_SHIP", "Battleship", "", "", "FRONT");
        assertEquals(new ShipClass(HullSize.CAPITAL, Role.COMBAT, 0, Shield.FRONT), onslaught);
        assertEquals(HullSize.FRIGATE, ShipClass.classify("FRIGATE", "Frigate", "", "", "FRONT").size());
        assertEquals(HullSize.DESTROYER, ShipClass.classify("DESTROYER", "Destroyer", "", "", "OMNI").size());
        assertEquals(HullSize.CRUISER, ShipClass.classify("CRUISER", "Cruiser", "", "", "OMNI").size());
    }

    @Test
    void theCarrierHintMakesACarrierAndACombatHintBesideItDoesNotUndoIt() {
        assertEquals(Role.CARRIER, ShipClass.classify("DESTROYER", "Light Carrier", "CARRIER", "2", "OMNI").role());
        assertEquals(Role.CARRIER, ShipClass.classify("CAPITAL_SHIP", "Battlecarrier", "CARRIER, COMBAT, NO_AUTO_ESCORT", "4", "FRONT").role());
    }

    @Test
    void manyBaysAreACarrierWithoutTheHintButOneOrTwoAreAGunShipWithAWing() {
        assertEquals(Role.CARRIER, ShipClass.classify("CAPITAL_SHIP", "Carrier", "", "3", "FRONT").role());
        assertEquals(Role.COMBAT, ShipClass.classify("CAPITAL_SHIP", "Battlecruiser", "", "2", "OMNI").role());
        assertEquals(Role.COMBAT, ShipClass.classify("CRUISER", "Cruiser", "", "1", "OMNI").role());
    }

    @Test
    void civilianHullsAndTendersAreSupportEvenWithACarrierHint() {
        assertEquals(Role.SUPPORT, ShipClass.classify("CAPITAL_SHIP", "Superfreighter", "CIVILIAN, FREIGHTER", "", "OMNI").role());
        assertEquals(Role.SUPPORT, ShipClass.classify("CRUISER", "Drone Tender", "CARRIER", "1", "OMNI").role());
        assertEquals(Role.SUPPORT, ShipClass.classify("CRUISER", "Phase Tender", "CIVILIAN, TANKER", "", "PHASE").role());
    }

    @Test
    void shieldTypeIsKeptIncludingPhaseAndBlankMeansNone() {
        assertEquals(Shield.PHASE, ShipClass.classify("CRUISER", "Phase Cruiser", "", "", "PHASE").shield());
        assertEquals(Shield.OMNI, ShipClass.classify("CRUISER", "Cruiser", "", "", " omni ").shield());
        assertEquals(Shield.NONE, ShipClass.classify("FRIGATE", "Frigate", "", "", "").shield());
    }

    @Test
    void missingOrUnreadableDataReadsAsTheDefaultCapital() {
        ShipClass unknown = ShipClass.classify(null, null, null, "many", null);
        assertEquals(HullSize.CAPITAL, unknown.size());
        assertEquals(Role.COMBAT, unknown.role());
        assertEquals(0, unknown.bays());
        assertEquals(ShipClass.DEFAULT, unknown);
        assertEquals(HullSize.FRIGATE, ShipClass.classify("FIGHTER", "", "", "", "").size());
    }
}
