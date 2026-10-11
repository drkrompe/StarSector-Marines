package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.mech.MechGaitState;
import com.dillon.starsectormarines.battle.mech.MechGaitState.FootEvent;
import com.dillon.starsectormarines.battle.mech.MechGaitState.FootPhase;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MechFootstepAudioTest {
    private final MechFootstepAudio audio = new MechFootstepAudio();
    private final MechGaitState gait = MechGaitState.create(5f, 5f, 0f, MechVariant.HOUND);
    private float bodyY = 5f;

    @Test
    void pneumaticAtLiftAndHeavyImpactOnlyWhenThatFootLands() {
        assertTrue(sample(true, 5f).isEmpty());
        String previousLift = null;
        String previousImpact = null;
        for (int step = 0; step < 6; step++) {
            FootEvent lift = nextEvent(FootPhase.LIFT);
            MechFootstepAudio.Cue pneumatic = onlyCue();
            assertEquals(FootPhase.LIFT, pneumatic.phase());
            assertEquals(lift.foot(), pneumatic.foot());
            assertEquals(lift.x(), pneumatic.cellX());
            assertEquals(lift.y(), pneumatic.cellY());
            assertTrue(pneumatic.soundId().startsWith("marines_mech_lift_"));
            assertNotEquals(previousLift, pneumatic.soundId());
            assertTrue(sample(true, bodyY).isEmpty(), "paused/render-only frame is silent");
            FootEvent land = nextEvent(FootPhase.LAND);
            MechFootstepAudio.Cue impact = onlyCue();
            assertEquals(FootPhase.LAND, impact.phase());
            assertEquals(lift.foot(), impact.foot(), "impact belongs to the same lifted foot");
            assertEquals(land.x(), impact.cellX());
            assertEquals(land.y(), impact.cellY());
            assertTrue(impact.soundId().startsWith("marines_mech_land_"));
            assertNotEquals(previousImpact, impact.soundId());
            assertTrue(impact.volume() > pneumatic.volume());
            assertTrue(impact.pitch() < pneumatic.pitch());
            assertTrue(sample(true, bodyY).isEmpty());
            previousLift = pneumatic.soundId();
            previousImpact = impact.soundId();
        }
    }

    @Test
    void hiddenAndDistantEventsNeverReplayOnReturn() {
        sample(true, bodyY);
        for (FootPhase phase : new FootPhase[]{FootPhase.LIFT, FootPhase.LAND}) {
            nextEvent(phase);
            assertTrue(sample(false, bodyY).isEmpty());
            assertTrue(sample(true, bodyY).isEmpty());
        }
        for (FootPhase phase : new FootPhase[]{FootPhase.LIFT, FootPhase.LAND}) {
            nextEvent(phase);
            assertTrue(sample(true, bodyY + 100f).isEmpty());
            assertTrue(sample(true, bodyY).isEmpty());
        }
        nextEvent(FootPhase.LIFT);
        assertEquals(FootPhase.LIFT, onlyCue().phase());
    }

    @Test
    void attachmentAndTeleportAreSilentAndMissedStrokesDoNotReplay() {
        nextEvent(FootPhase.LAND);
        assertTrue(sample(true, bodyY).isEmpty(), "attachment establishes the cursor");
        nextEvent(FootPhase.LAND);
        nextEvent(FootPhase.LAND);
        assertEquals(FootPhase.LAND, onlyCue().phase(), "finished pneumatic strokes are stale");
        assertTrue(sample(true, bodyY).isEmpty());
        bodyY += 10f;
        gait.advance(5f, bodyY, 0f, 0f, 1f / 30f);
        assertNull(gait.lastTouchdown());
        assertNull(gait.lastLiftOff());
        assertTrue(sample(true, bodyY).isEmpty());
        nextEvent(FootPhase.LIFT);
        assertEquals(FootPhase.LIFT, onlyCue().phase());
        audio.reset();
        assertTrue(sample(true, bodyY).isEmpty());
    }

    @Test
    void landingAndNextFeetLiftBothSurviveSeveralTicksInOneFrame() {
        sample(true, bodyY);
        nextEvent(FootPhase.LAND);
        nextEvent(FootPhase.LIFT);
        List<MechFootstepAudio.Cue> cues = sample(true, bodyY);
        assertEquals(2, cues.size());
        assertEquals(FootPhase.LAND, cues.get(0).phase());
        assertEquals(FootPhase.LIFT, cues.get(1).phase());
        assertNotEquals(cues.get(0).foot(), cues.get(1).foot());
        assertTrue(sample(true, bodyY).isEmpty());
    }

    @Test
    void idleStanceDoesNotInventEvents() {
        sample(true, bodyY);
        for (int i = 0; i < 60; i++) {
            gait.advance(5f, bodyY, 0f, 0f, 1f / 30f);
            assertTrue(sample(true, bodyY).isEmpty());
        }
    }

    private MechFootstepAudio.Cue onlyCue() {
        List<MechFootstepAudio.Cue> cues = sample(true, bodyY);
        assertEquals(1, cues.size());
        return cues.get(0);
    }

    private List<MechFootstepAudio.Cue> sample(boolean visible, float listenerY) {
        return audio.observe(gait, MechVariant.HOUND, visible, 5f, listenerY);
    }

    private FootEvent nextEvent(FootPhase phase) {
        FootEvent old = phase == FootPhase.LIFT ? gait.lastLiftOff() : gait.lastTouchdown();
        long previous = old == null ? 0 : old.sequence();
        for (int i = 0; i < 100; i++) {
            bodyY += 0.05f;
            gait.advance(5f, bodyY, 0f, 0f, 1f / 30f);
            FootEvent event = phase == FootPhase.LIFT ? gait.lastLiftOff() : gait.lastTouchdown();
            if (event != null && event.sequence() != previous) return event;
        }
        throw new AssertionError("Walking gait never reached " + phase);
    }
}
