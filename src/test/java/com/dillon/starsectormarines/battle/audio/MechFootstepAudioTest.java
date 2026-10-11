package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.mech.MechGaitState;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MechFootstepAudioTest {
    private final MechFootstepAudio audio = new MechFootstepAudio();
    private final MechGaitState gait = MechGaitState.create(5f, 5f, 0f, MechVariant.HOUND);
    private float bodyY = 5f;

    @Test
    void oneCuePerPlantWithNoImmediateVariantRepeat() {
        assertNull(sample(true, 5f));
        String previous = null;
        for (int step = 0; step < 8; step++) {
            plant();
            MechFootstepAudio.Cue cue = sample(true, bodyY);
            assertNotNull(cue);
            assertNotEquals(previous, cue.soundId());
            assertEquals(gait.lastTouchdown().x(), cue.cellX());
            assertEquals(gait.lastTouchdown().y(), cue.cellY());
            assertTrue(cue.pitch() >= 1.05f && cue.pitch() <= 1.11f);
            assertTrue(cue.volume() > 0 && cue.volume() <= 0.25f);
            assertNull(sample(true, bodyY), "paused or repeated render frame must be silent");
            previous = cue.soundId();
        }
    }

    @Test
    void hiddenAndDistantContactsNeverReplayOnReturn() {
        sample(true, 5f);
        plant();
        assertNull(sample(false, bodyY));
        assertNull(sample(true, bodyY));
        plant();
        assertNull(sample(true, bodyY + 100f));
        assertNull(sample(true, bodyY));
        plant();
        assertNotNull(sample(true, bodyY));
    }

    @Test
    void attachmentAndTeleportAreSilentAndMissedTicksDoNotCreateABurst() {
        plant();
        assertNull(sample(true, bodyY), "first attachment establishes the cursor");
        plant();
        plant();
        assertNotNull(sample(true, bodyY));
        assertNull(sample(true, bodyY));
        gait.advance(5f, bodyY + 10f, 0f, 0f, 1f / 30f);
        bodyY += 10f;
        assertNull(gait.lastTouchdown());
        assertNull(sample(true, bodyY));
        plant();
        assertNotNull(sample(true, bodyY));
        audio.reset();
        assertNull(sample(true, bodyY));
    }

    private MechFootstepAudio.Cue sample(boolean visible, float listenerY) {
        return audio.observe(gait, MechVariant.HOUND, visible, 5f, listenerY);
    }

    private void plant() {
        long previous = gait.lastTouchdown() == null ? 0 : gait.lastTouchdown().sequence();
        for (int i = 0; i < 100; i++) {
            bodyY += 0.05f;
            gait.advance(5f, bodyY, 0f, 0f, 1f / 30f);
            if (gait.lastTouchdown() != null && gait.lastTouchdown().sequence() != previous) return;
        }
        fail("walking gait never planted a foot");
    }
}
