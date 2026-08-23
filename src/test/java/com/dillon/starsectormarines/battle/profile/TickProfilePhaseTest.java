package com.dillon.starsectormarines.battle.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TickProfilePhaseTest {

    @Test
    void contactPictureImmediatelyFollowsSquadAlert() {
        TickProfile.Phase[] phases = TickProfile.Phase.VALUES;

        assertEquals(TickProfile.Phase.CONTACT_PICTURE,
                phases[TickProfile.Phase.SQUAD_ALERT.ordinal() + 1]);
    }
}
