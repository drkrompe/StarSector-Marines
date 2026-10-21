package com.dillon.starsectormarines.campaign.nexerelin;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Nexerelin's custom-start list names the Second Wind start, and the start knows where to put the fleet. */
class SecondWindNexStartTest {

    @Test
    void theFleetIsPlacedAtTheOpeningsYardElseLeftToNexerelin() {
        assertEquals("corvus_iv", SecondWindNexStart.startEntityId("corvus_iv"));
        assertNull(SecondWindNexStart.startEntityId(null));
        assertNull(SecondWindNexStart.startEntityId(""));
    }
}
