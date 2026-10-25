package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.evacuation.BroodSpires;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpireAudioTest {

    @Test void aMatriarchsBroodRisesWithTheSpiresLaunchQuieter() {
        BroodSpires.Launch spire = new BroodSpires.Launch(10, 42L, 0L, 1, 7);
        BroodSpires.Launch brood = new BroodSpires.Launch(10, 0L, 99L, 2, 6);
        List<SpireAudio.Play> loud = SpireAudio.plays(SpireAudio.Cue.LAUNCH, 5f, 5f, SpireAudio.gain(spire), 10, 0);
        List<SpireAudio.Play> quiet = SpireAudio.plays(SpireAudio.Cue.LAUNCH, 5f, 5f, SpireAudio.gain(brood), 10, 0);
        assertEquals(loud.size(), quiet.size());
        for (int i = 0; i < loud.size(); i++) {
            assertEquals(loud.get(i).soundId(), quiet.get(i).soundId());
            assertEquals(loud.get(i).volume() * SpireAudio.BROOD_GAIN, quiet.get(i).volume(), 1e-6f);
        }
    }

    @Test void aCuesLayersStartInOrderFromItsFirstTick() {
        for (SpireAudio.Cue cue : SpireAudio.Cue.values()) {
            assertEquals(0, cue.layers[0].offset, cue + " starts on its own tick");
            for (int i = 1; i < cue.layers.length; i++) {
                assertTrue(cue.layers[i].offset >= cue.layers[i - 1].offset, cue + " layers in order");
            }
        }
    }
}
