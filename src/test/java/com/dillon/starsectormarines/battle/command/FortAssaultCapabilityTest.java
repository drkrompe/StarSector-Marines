package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.Armament.Delivery;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.world.gen.fortress.AssaultApproach.Variant;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plan chooses its way in by what the company can staff, and reports what it lacks. */
class FortAssaultCapabilityTest {

    private static final Armament CANNON = new Armament(Delivery.DIRECT, 26f, 1, 3f, 45f, 18f, 140, Armament.UNLIMITED);
    private static final Armament RIFLE = new Armament(Delivery.DIRECT, 18f, 1, 0.5f, 6f, 2f, 0, Armament.UNLIMITED);
    private static final Armament ROCKET = new Armament(Delivery.DIRECT, 22f, 1, 4f, 60f, 14f, 10, 12);
    private static final Armament SATCHEL = new Armament(Delivery.CHARGE, 1.5f, 1, 12f, 200f, 20f, 450, Armament.UNLIMITED);

    private static FortAssault.Works works() {
        return FortAssault.Works.of(new FortFrame(BatteryFort.PLAN, 100, 100, 0));
    }

    private static CapabilityProfile riflemen() {
        return CapabilityProfile.of(List.of(RIFLE), 8, 8);
    }

    private static CapabilityProfile rocketeers() {
        return CapabilityProfile.of(List.of(RIFLE, ROCKET, ROCKET, ROCKET, ROCKET), 8, 8);
    }

    private static CapabilityProfile sappers() {
        return CapabilityProfile.of(List.of(RIFLE, SATCHEL), 8, 8);
    }

    private static CapabilityProfile cannonMech() {
        return CapabilityProfile.of(List.of(CANNON, CANNON, CANNON, CANNON), 0, 1);
    }

    private static FortAssault.Defence assess(Map<Integer, CapabilityProfile> squads) {
        FortAssault.Works works = works();
        FortAssault.NestBelief belief = new FortAssault.NestBelief(works.nests.size());
        return FortAssault.assess(works, belief, 100, 40, squads);
    }

    @Test
    void aCompanyWithNothingToHurtANestCannotStaffAnyWayAndSaysWhatItLacks() {
        FortAssault.Defence defence = assess(Map.of(1, riflemen(), 2, riflemen()));
        assertFalse(defence.staffed());
        assertTrue(defence.staffing().describeGaps().get(0).startsWith("base_of_fire"));
        assertNotNull(defence.way(), "an unstaffable plan is still a plan; the shortfall is what is reported");
    }

    @Test
    void rocketsStaffTheGateBecauseTheyAnswerTheNests() {
        FortAssault.Defence defence = assess(Map.of(1, riflemen(), 2, rocketeers()));
        assertTrue(defence.staffed(), String.valueOf(defence.staffing().describeGaps()));
        assertNotEquals(Variant.BREACH, defence.way().variant(), "nothing here can open a wall: a gate");
    }

    @Test
    void aCompanyThatBroughtChargesCutsTheWallAtItsFoot() {
        FortAssault.Defence defence = assess(Map.of(1, riflemen(), 2, rocketeers(), 3, sappers()));
        assertTrue(defence.staffed(), String.valueOf(defence.staffing().describeGaps()));
        assertEquals(Variant.BREACH, defence.way().variant());
        assertEquals(FortAssault.BreachMeans.CLOSE, defence.means());
    }

    @Test
    void aCannonMechStaffsADistanceBreachAndIsBothBreacherAndBaseOfFire() {
        FortAssault.Defence defence = assess(Map.of(1, riflemen(), 2, cannonMech()));
        assertTrue(defence.staffed(), String.valueOf(defence.staffing().describeGaps()));
        assertEquals(Variant.BREACH, defence.way().variant());
        assertEquals(FortAssault.BreachMeans.DISTANCE, defence.means());
        assertEquals(List.of(2), defence.staffing().squads(AssaultRoles.Role.BREACH));
        assertEquals(List.of(1), defence.staffing().squads(AssaultRoles.Role.ENTRY));
    }

    @Test
    void anEmptyCompanyReportsEveryNeed() {
        FortAssault.Defence defence = assess(Map.of());
        assertFalse(defence.staffed());
        assertFalse(defence.staffing().gaps().isEmpty());
    }
}
