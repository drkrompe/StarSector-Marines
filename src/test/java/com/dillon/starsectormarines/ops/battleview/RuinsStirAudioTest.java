package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Aim;
import com.dillon.starsectormarines.battle.ruins.RuinsPressure.Push;
import com.dillon.starsectormarines.battle.ruins.RuinsPushes.Voice;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuinsStirAudioTest {

    private static Push hunt(int step) { return new Push(step, 1, Aim.HUNT, 2); }

    @Test
    void eachNewPushIsHeardOnceAndAFrameOfThemOnce() {
        RuinsStirAudio.Mix mix = new RuinsStirAudio.Mix();
        Object site = new Object();
        List<Push> pushes = new ArrayList<>();
        assertNull(mix.hear(site, Voice.CREW, pushes), "no push, no sting");
        pushes.add(hunt(1));
        assertNotNull(mix.hear(site, Voice.CREW, pushes));
        assertNull(mix.hear(site, Voice.CREW, pushes), "a push is heard once");
        pushes.add(hunt(2));
        pushes.add(hunt(3));
        assertNotNull(mix.hear(site, Voice.CREW, pushes));
        assertNull(mix.hear(site, Voice.CREW, pushes), "two pushes in one frame play one sting");
    }

    @Test
    void aNewSiteStartsUnheard() {
        RuinsStirAudio.Mix mix = new RuinsStirAudio.Mix();
        List<Push> pushes = List.of(hunt(1));
        assertNotNull(mix.hear(new Object(), Voice.SLEEPERS, pushes));
        assertNotNull(mix.hear(new Object(), Voice.SLEEPERS, pushes), "another battle's pressure is heard afresh");
    }

    @Test
    void aWayBackPushStingsLowerAndLouderInTheOccupantsSound() {
        RuinsStirAudio.Mix mix = new RuinsStirAudio.Mix();
        var sting = mix.hear(new Object(), Voice.SECURITY, List.of(hunt(1), new Push(2, 1, Aim.WAY_BACK, 0)));
        var hunt = RuinsStirAudio.sting(Voice.SECURITY, false);
        assertEquals(hunt.soundId(), sting.soundId());
        assertTrue(sting.pitch() < hunt.pitch() && sting.volume() > hunt.volume());
        assertNotEquals(RuinsStirAudio.sting(Voice.CREW, false).soundId(),
                RuinsStirAudio.sting(Voice.SLEEPERS, false).soundId(), "each occupant has its own sting");
    }
}
