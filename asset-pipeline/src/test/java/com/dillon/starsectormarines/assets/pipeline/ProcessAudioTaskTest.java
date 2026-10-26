package com.dillon.starsectormarines.assets.pipeline;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessAudioTaskTest {

    @Test
    void levelingGainFollowsTheTreatment() {
        List<String> comms = ProcessAudioTask.outputArguments(AudioManifest.Kind.UI, AudioManifest.Processing.COMMS);
        String chain = ProcessAudioTask.levelled(comms, 7.5).get(comms.indexOf("-af") + 1);
        assertTrue(chain.startsWith("highpass=") && chain.contains(",volume=7.50dB,alimiter="),
                "the gain levels the treated voice, not the clean take");
        List<String> clean = ProcessAudioTask.levelled(ProcessAudioTask.outputArguments(AudioManifest.Kind.MUSIC), -8);
        assertTrue(clean.get(clean.indexOf("-af") + 1).startsWith("volume=-8.00dB,"));
    }

    @Test
    void loudnessIsReadFromTheSummaryNotTheRunningFrames() throws IOException {
        String log = "[Parsed_ebur128_0] t: 0.4 M: -30.1 S: -120.7 I: -31.0 LUFS LRA: 0.0 LU\n"
                + "[Parsed_ebur128_0] Summary:\n  Integrated loudness:\n    I:         -26.7 LUFS\n";
        assertEquals(-26.7, ProcessAudioTask.integratedLoudness(log), 1e-9);
        assertThrows(IOException.class, () -> ProcessAudioTask.integratedLoudness("    I:         -70.0 LUFS\n"));
    }
}
