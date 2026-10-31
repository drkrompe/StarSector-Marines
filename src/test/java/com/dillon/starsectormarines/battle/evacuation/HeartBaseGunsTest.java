package com.dillon.starsectormarines.battle.evacuation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeartBaseGunsTest {

    @Test
    void aBasesPitsStockAsOnePlace() {
        assertEquals("locusts-logistics", HeartBaseGuns.place("locust-pit-logistics-1"));
        assertEquals("locusts-logistics", HeartBaseGuns.place("locust-pit-logistics-2"));
        assertEquals("locusts-airbase-1", HeartBaseGuns.place("locust-pit-airbase-1-2"));
    }
}
