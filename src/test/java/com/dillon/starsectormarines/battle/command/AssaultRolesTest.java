package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.Armament.Delivery;
import com.dillon.starsectormarines.battle.command.AssaultRoles.Need;
import com.dillon.starsectormarines.battle.command.AssaultRoles.Role;
import com.dillon.starsectormarines.battle.command.AssaultRoles.Staffing;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssaultRolesTest {

    private static final Armament CANNON = new Armament(Delivery.DIRECT, 26f, 1, 3f, 45f, 18f, 140, Armament.UNLIMITED);
    private static final Armament RIFLE = new Armament(Delivery.DIRECT, 18f, 1, 0.5f, 6f, 2f, 0, Armament.UNLIMITED);
    private static final Armament SATCHEL = new Armament(Delivery.CHARGE, 1.5f, 1, 12f, 200f, 20f, 450, Armament.UNLIMITED);

    private static CapabilityProfile mech() {
        return CapabilityProfile.of(List.of(CANNON), 0, 1);
    }

    private static CapabilityProfile riflemen() {
        return CapabilityProfile.of(List.of(RIFLE), 6, 6);
    }

    private static CapabilityProfile sappers() {
        return CapabilityProfile.of(List.of(RIFLE, SATCHEL), 6, 6);
    }

    private static Need breachAtRange() {
        return new Need(Role.BREACH, EnumSet.of(AssaultCapability.DISTANCE_BREACH), 10f, 100f);
    }

    private static Need breachClose() {
        return new Need(Role.BREACH, EnumSet.of(AssaultCapability.CLOSE_BREACH), 10f, 100f);
    }

    private static Need entry() {
        return new Need(Role.ENTRY, EnumSet.of(AssaultCapability.ASSAULT), 4f, 0f);
    }

    @Test
    void matchesSquadsToTheRolesTheirKitFits() {
        Staffing staffing = AssaultRoles.assign(List.of(breachAtRange(), entry()),
                Map.of(1, riflemen(), 2, mech()));
        assertTrue(staffing.staffed(), staffing.describeGaps().toString());
        assertEquals(List.of(2), staffing.squads(Role.BREACH));
        assertEquals(List.of(1), staffing.squads(Role.ENTRY));
    }

    @Test
    void scarceKitIsNotSpentOnARoleAnyoneElseCouldFill() {
        Staffing staffing = AssaultRoles.assign(List.of(entry(), breachClose()),
                Map.of(1, sappers(), 2, riflemen()));
        assertTrue(staffing.staffed(), staffing.describeGaps().toString());
        assertEquals(List.of(1), staffing.squads(Role.BREACH));
        assertEquals(List.of(2), staffing.squads(Role.ENTRY));
    }

    @Test
    void anUnstaffablePlanReportsWhatItNeeds() {
        Staffing staffing = AssaultRoles.assign(List.of(breachAtRange(), entry()), Map.of(1, riflemen()));
        assertFalse(staffing.staffed());
        assertEquals(1, staffing.gaps().size());
        assertEquals(Role.BREACH, staffing.gaps().get(0).need().role());
        assertTrue(staffing.describeGaps().get(0).startsWith("breach needs distance_breach"));
        assertEquals(List.of(1), staffing.squads(Role.ENTRY));
    }

    @Test
    void reserveCountsAsWellAsRate() {
        CapabilityProfile spent = CapabilityProfile.of(
                List.of(new Armament(Delivery.DIRECT, 22f, 1, 4f, 60f, 14f, 90, 1)), 0, 1);
        Staffing staffing = AssaultRoles.assign(List.of(new Need(Role.BREACH,
                EnumSet.of(AssaultCapability.DISTANCE_BREACH), 5f, 2000f)), Map.of(1, spent));
        assertFalse(staffing.staffed());
        assertTrue(staffing.gaps().get(0).reserveMissing() > 0f);
    }

    @Test
    void assignmentIsRepeatable() {
        Map<Integer, CapabilityProfile> squads = Map.of(3, mech(), 1, mech(), 2, riflemen());
        Staffing a = AssaultRoles.assign(List.of(breachAtRange(), entry()), squads);
        Staffing b = AssaultRoles.assign(List.of(entry(), breachAtRange()), squads);
        assertEquals(a.roles(), b.roles());
        assertEquals(List.of(1), a.squads(Role.BREACH));
    }
}
