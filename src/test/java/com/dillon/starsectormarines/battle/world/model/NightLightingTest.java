package com.dillon.starsectormarines.battle.world.model;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NightLightingTest {
    @Test void restoringPowerRetainsEmergencyLightsAndDoesNotMutateThePreviousState() {
        var emergency=new NightLighting.Lamp(10,10,8,1,.6f,.2f,"emergency");
        var work=new NightLighting.Lamp(20,10,16,1,.9f,.8f,"yard");
        var before=new NightLighting(.76f,List.of(emergency,work),Set.of("emergency"));
        var after=before.withPower("yard");
        assertTrue(before.powered(emergency));
        assertFalse(before.powered(work));
        assertTrue(after.powered(emergency));
        assertTrue(after.powered(work));
        assertEquals(before.lamps(),after.lamps());
        assertEquals(after,after.withPower("yard"));
    }

    @Test void poolsAreBoundedAndFadeToZeroAtTheirAuthoredReach() {
        var lamp=new NightLighting.Lamp(10,10,8,1,.6f,.2f,"yard");
        assertEquals(1,lamp.pool(10,10));
        assertEquals(0,lamp.pool(18,10));
        assertEquals(0,lamp.pool(30,10));
        assertTrue(lamp.pool(12,10)>lamp.pool(16,10));
        assertEquals(lamp.pool(12,10),lamp.pool(10,12));
    }
}
