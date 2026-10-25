package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.SwarmCues;
import com.dillon.starsectormarines.battle.evacuation.SwarmWorkers;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwarmAudioTest {

    @Test void aBruteStepsEveryHalfCellAndARunnerEveryCell() {
        assertTrue(SwarmAudio.stepped(UnitType.SWARM_BRUTE, 0.4f, 0.6f));
        assertTrue(SwarmAudio.stepped(UnitType.SWARM_BRUTE, 0.9f, 0.1f));
        assertFalse(SwarmAudio.stepped(UnitType.SWARM_RUNNER, 0.4f, 0.6f));
        assertTrue(SwarmAudio.stepped(UnitType.SWARM_RUNNER, 0.9f, 0.1f));
    }

    @Test void takingUpABodyLatchesAndHaulingItDrags() {
        assertEquals(SwarmAudio.Sound.BODY_LATCH, SwarmAudio.sound(SwarmCues.Kind.DRAG, UnitType.SWARM_RUNNER));
        assertEquals(SwarmAudio.Sound.BODY_DRAG, SwarmAudio.footfall(UnitType.SWARM_RUNNER, 1, true));
        assertNull(SwarmAudio.footfall(UnitType.SWARM_RUNNER, 1, false));
        assertEquals(SwarmAudio.Sound.RUNNER_GALLOP, SwarmAudio.footfall(UnitType.SWARM_RUNNER, 2, false));
    }

    @Test void aBodysVoiceIsPitchedByItsSize() {
        assertEquals(1f, SwarmAudio.pitch(UnitType.SWARM_RUNNER, SwarmStage.ADULT));
        assertEquals(1f, SwarmAudio.pitch(UnitType.SWARM_RUNNER, null), "no stage is an adult");
        assertTrue(SwarmAudio.pitch(UnitType.SWARM_RUNNER, SwarmStage.WHELP) > 1f, "a whelp is higher");
        assertTrue(SwarmAudio.pitch(UnitType.SWARM_BRUTE, SwarmStage.ELDER) < 1f, "an elder is deeper");
        assertTrue(SwarmAudio.volume(SwarmStage.ELDER) > SwarmAudio.volume(SwarmStage.WHELP));
        assertEquals(SwarmAudio.sound(SwarmCues.Kind.ALERT, UnitType.SWARM_RUNNER),
                SwarmAudio.sound(SwarmCues.Kind.PACK_BROKEN, UnitType.SWARM_RUNNER), "the scatter shriek is the cry");
    }

    @Test void everyThrownBodyLandsWithTheSameThud() {
        for (UnitType type : new UnitType[]{UnitType.MARINE, UnitType.SWARM_RUNNER, UnitType.SWARM_BRUTE}) {
            assertEquals(SwarmAudio.Sound.BODY_THUD, SwarmAudio.sound(SwarmCues.Kind.THROWN, type), type.name());
        }
        assertEquals(1f, SwarmAudio.pitch(UnitType.MARINE, null), "a marine's thud is not pitched as a rav");
    }

    @Test void aStandingBodyMakesNoFootfall() {
        assertFalse(SwarmAudio.stepped(UnitType.SWARM_BRUTE, 0.3f, 0.3f));
        assertFalse(SwarmAudio.stepped(UnitType.SWARM_RUNNER, 0.7f, 0.7f));
    }

    @Test void aSkittererPattersOnEveryStrideAndDragsWhatItHauls() {
        assertEquals(SwarmAudio.Sound.WORKER_PATTER, SwarmAudio.footfall(UnitType.SWARM_WORKER, 1, false));
        assertEquals(SwarmAudio.Sound.WORKER_PATTER, SwarmAudio.footfall(UnitType.SWARM_WORKER, 2, false));
        assertEquals(SwarmAudio.Sound.WORKER_DRAG, SwarmAudio.footfall(UnitType.SWARM_WORKER, 1, true));
        assertTrue(SwarmAudio.pitch(UnitType.SWARM_WORKER, SwarmStage.ADULT)
                > SwarmAudio.pitch(UnitType.SWARM_RUNNER, SwarmStage.WHELP), "smaller than any whelp, higher");
    }

    @Test void aSkittererSqueaksWhenHurtAndWhenItScattersButNeverCries() {
        assertEquals(SwarmAudio.Sound.WORKER_SQUEAK, SwarmAudio.pain(UnitType.SWARM_WORKER));
        assertTrue(SwarmAudio.scattered(SwarmWorkers.Job.REPAIR.ordinal(), SwarmWorkers.Job.FLEE));
        assertFalse(SwarmAudio.scattered(SwarmWorkers.Job.FLEE.ordinal(), SwarmWorkers.Job.FLEE), "still running");
        assertFalse(SwarmAudio.scattered(-1, SwarmWorkers.Job.FLEE), "first heard already running");
        assertFalse(SwarmAudio.scattered(SwarmWorkers.Job.FLEE.ordinal(), SwarmWorkers.Job.MILL));
        for (SwarmCues.Kind kind : new SwarmCues.Kind[]{SwarmCues.Kind.ALERT, SwarmCues.Kind.STRIKE})
            assertNull(SwarmAudio.sound(kind, UnitType.SWARM_WORKER), kind.name());
    }

    @Test void aBusyNestIsHeardAtWorkByItsNearestFewOnly() {
        List<float[]> working = List.of(new float[]{9f, 0f}, new float[]{1f, 0f}, new float[]{4f, 0f},
                new float[]{6f, 0f}, new float[]{60f, 0f});
        List<SwarmAudio.Play> loops = SwarmAudio.workLoops(working, 0f, 0f);
        assertEquals(SwarmAudio.WORK_LOOPS, loops.size());
        assertEquals(1f, loops.get(0).x(), "nearest first");
        assertEquals(4f, loops.get(1).x());
        assertEquals(0, loops.get(0).loop());
        assertEquals(1, loops.get(1).loop());
        assertTrue(loops.get(1).volume() < loops.get(0).volume(), "a second worker thickens it, quieter");
        assertTrue(SwarmAudio.workLoops(List.of(new float[]{60f, 0f}), 0f, 0f).isEmpty(), "out of earshot");
    }
}
