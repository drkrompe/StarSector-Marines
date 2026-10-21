package com.dillon.starsectormarines.battle.world.tiles;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PropMotionTest {

    private static PropMotion parse(String json) throws Exception {
        return PropMotion.parse(new JSONObject(json));
    }

    @Test
    void aSlideDwellsAtEachStopThenEasesToTheNextAndComesBack() throws Exception {
        PropMotion.Part slide = parse("{doodad:'b',parts:[{part:'p',kind:'slide',stops:[[0,0],[0,4],[2,4]],"
                + "travelSeconds:2,dwellSeconds:1}]}").parts().get(0);
        assertEquals(0f, slide.slideAt(0.9f)[1], 1e-5f, "still dwelling at the first stop");
        assertEquals(2f, slide.slideAt(2f)[1], 1e-5f, "halfway along the first leg");
        assertEquals(4f, slide.slideAt(3.5f)[1], 1e-5f, "dwelling at the second stop");
        assertEquals(2f, slide.slideAt(6.5f)[0], 1e-5f, "dwelling at the far end");
        assertEquals(2f, slide.slideAt(11f)[1], 1e-5f, "halfway back along the last leg");
        assertEquals(0f, slide.slideAt(12f)[1], 1e-5f, "a whole cycle returns to the start");
    }

    @Test
    void aScrollWrapsWithinOneTile() throws Exception {
        PropMotion.Part belt = parse("{doodad:'b',parts:[{part:'p',kind:'scroll',window:[0,0,4,1],"
                + "period:0.5,speed:0.7}]}").parts().get(0);
        assertEquals(0.2f, belt.scrollOffset(1f), 1e-4f);
        assertEquals(0f, belt.scrollOffset(0f), 1e-6f);
    }

    @Test
    void aWorkedMachineStandsIdleUnworkedAndSlowsWhenPowerIsShort() throws Exception {
        PropMotion crane = parse("{doodad:'b',runs:'use+power',deficitRate:0.25,parts:[{part:'p',at:[0,0]}]}");
        assertEquals(0f, crane.targetRate(false, 0f));
        assertEquals(1f, crane.targetRate(true, 0f));
        assertEquals(0.25f, crane.targetRate(true, 1f), 1e-6f);
        PropMotion treadmill = parse("{doodad:'b',runs:'use',parts:[{part:'p',at:[0,0]}]}");
        assertEquals(1f, treadmill.targetRate(true, 1f), "a human-powered machine ignores the ship's power");
    }

    @Test
    void aDamagedMachineRunsAtItsDamagedRateAndByDefaultNotAtAll() throws Exception {
        PropMotion fan = parse("{doodad:'b',runs:'power',deficitRate:0.5,damagedRate:0.4,parts:[{part:'p',at:[0,0]}]}");
        assertEquals(0.4f, fan.targetRate(false, 0f, true), 1e-6f);
        assertEquals(0.2f, fan.targetRate(false, 1f, true), 1e-6f, "damage and a power deficit compound");
        PropMotion belt = parse("{doodad:'b',runs:'power',parts:[{part:'p',at:[0,0]}]}");
        assertEquals(0f, belt.targetRate(false, 0f, true));
    }
}
