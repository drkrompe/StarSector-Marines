package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RescueCommandSnapshot.Role;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RescueEscortRoleTest {
    @Test void promotesAnExistingScreenWhenEscortIsLostOrPlayerOwned() {
        Map<Integer,Role> roles=new HashMap<>(Map.of(1,Role.COHORT_ESCORT,2,Role.LEAD_SCREEN,3,Role.LEFT_SCREEN));
        RescueEscortCommand.restoreEscortRole(List.of(2,3),roles);
        assertEquals(Map.of(2,Role.COHORT_ESCORT,3,Role.LEFT_SCREEN),roles);
        RescueEscortCommand.restoreEscortRole(List.of(2,3),roles);
        assertEquals(Role.COHORT_ESCORT,roles.get(2));
        RescueEscortCommand.restoreEscortRole(List.of(),roles);
        assertTrue(roles.isEmpty());
    }
    @Test void aNewLowerIdDoesNotDisplaceALivingEscort() {
        Map<Integer,Role> roles=new HashMap<>(Map.of(2,Role.COHORT_ESCORT,3,Role.LEFT_SCREEN));
        RescueEscortCommand.restoreEscortRole(List.of(1,2,3),roles);
        assertEquals(Role.COHORT_ESCORT,roles.get(2)); assertFalse(roles.containsKey(1));
    }
}
