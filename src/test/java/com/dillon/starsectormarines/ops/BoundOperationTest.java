package com.dillon.starsectormarines.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundOperationTest {

    @Test
    void theBoardRouteLeadsToTheBoundOperationUntilItSettles() {
        assertEquals(ScreenId.BRIEFING, BoundOperation.route(ScreenId.MISSION_SELECT, true, true));
        assertEquals(ScreenId.COMPANY_HQ, BoundOperation.route(ScreenId.MISSION_SELECT, true, false));
    }

    @Test
    void anOrdinaryVisitKeepsTheBoard() {
        assertEquals(ScreenId.MISSION_SELECT, BoundOperation.route(ScreenId.MISSION_SELECT, false, true));
    }

    @Test
    void theShipsRoomsStayOpenDuringABoundOperation() {
        for (ScreenId room : new ScreenId[] {ScreenId.BARRACKS, ScreenId.MECH_LAB, ScreenId.BOAT_DECK, ScreenId.SHIP_VIEW}) {
            assertEquals(room, BoundOperation.route(room, true, true));
        }
    }

    @Test
    void briefingBackGoesToTheCompanyNotTheBoardWhenBound() {
        assertEquals(ScreenId.COMPANY_HQ, BoundOperation.briefingBack(true, false));
        assertEquals(ScreenId.MISSION_SELECT, BoundOperation.briefingBack(false, false));
        assertEquals(ScreenId.DEBUG_MISSIONS, BoundOperation.briefingBack(false, true));
    }
}
