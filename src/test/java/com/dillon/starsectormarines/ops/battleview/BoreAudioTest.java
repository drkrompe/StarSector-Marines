package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoreAudioTest {

    @Test void theHeaviestBodySetsAClutchsPitchAndTheMatriarchClawsUpDeepAndLoud() {
        BoreAudio.Claw runners = BoreAudio.clawFor(List.of(UnitType.SWARM_RUNNER, UnitType.SWARM_RUNNER));
        BoreAudio.Claw withBrute = BoreAudio.clawFor(List.of(UnitType.SWARM_RUNNER, UnitType.SWARM_BRUTE));
        BoreAudio.Claw matriarch = BoreAudio.clawFor(List.of(UnitType.SWARM_MATRIARCH));
        BoreAudio.Claw skitterer = BoreAudio.clawFor(List.of(UnitType.SWARM_WORKER));
        assertEquals(1f, runners.pitch());
        assertEquals(BoreAudio.pitch(UnitType.SWARM_BRUTE), withBrute.pitch());
        assertTrue(matriarch.pitch() < withBrute.pitch() && skitterer.pitch() > runners.pitch());
        assertTrue(matriarch.volume() > runners.volume());
    }

    @Test void moreBodiesBreakingOutTogetherAreLouderButSlowly() {
        float one = BoreAudio.clawFor(List.of(UnitType.SWARM_RUNNER)).volume();
        float three = BoreAudio.clawFor(List.of(UnitType.SWARM_RUNNER, UnitType.SWARM_RUNNER, UnitType.SWARM_RUNNER))
                .volume();
        assertEquals(1f, one);
        assertTrue(three > one && three < 1.5f * one);
    }

    @Test void aSurgesOverlappingClutchesChurnUnderTheVoiceBudgetEachQuieter() {
        BoreAudio.Mix mix = new BoreAudio.Mix();
        List<BoreAudio.Play> out = new ArrayList<>();
        BoreAudio.Claw claw = BoreAudio.clawFor(List.of(UnitType.SWARM_RUNNER));
        // A clutch every 3 ticks: far more than the voices, inside one take's length.
        for (int tick = 0; tick < 15; tick += 3) {
            mix.at(tick);
            mix.claw(out, 10f, 10f, claw, 0);
        }
        int budget = BoreAudio.Sound.CLAW_UP.layering.voices();
        assertEquals(budget, out.size(), "the fifth clutch inside a take's length is not voiced");
        for (int i = 1; i < out.size(); i++) assertTrue(out.get(i).volume() < out.get(i - 1).volume());
        assertTrue(out.get(budget - 1).volume() < BoreAudio.CLAW_UP_VOLUME * 0.8f);
        for (BoreAudio.Play p : out) {
            assertEquals(BoreAudio.CLAW_UP, p.soundId());
            assertTrue(Math.abs(p.pitch() - 1f) <= BoreAudio.PITCH_JITTER + 1e-6f);
        }
        // Once the first voices have played out, a clutch is heard again.
        mix.at(Math.round(BoreAudio.Sound.CLAW_UP.layering.voiceSeconds() / BattleSimulation.TICK_DT) + 1);
        mix.claw(out, 10f, 10f, claw, 0);
        assertEquals(budget + 1, out.size());
    }
}
