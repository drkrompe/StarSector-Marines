package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineAudioTest {

    @Test
    void throttleRunsFromAStandstillToTopSpeedEitherWay() {
        assertEquals(0f, MachineAudio.throttle(0f, 3f));
        assertEquals(.5f, MachineAudio.throttle(1.5f, 3f), 1e-6f);
        assertEquals(.5f, MachineAudio.throttle(-1.5f, 3f), 1e-6f, "reversing works the engine too");
        assertEquals(1f, MachineAudio.throttle(9f, 3f), "a shove past top speed does not over-rev");
        assertEquals(0f, MachineAudio.throttle(1f, 0f));
    }

    @Test
    void eachHullClassSpeaksWithItsOwnEngine() {
        assertEquals(MachineAudio.ENGINE_TRACKED, MachineAudio.engineVoice(VehicleType.BREAKTHROUGH_TANK).soundId());
        assertEquals(MachineAudio.ENGINE_TRACKED, MachineAudio.engineVoice(VehicleType.HEAVY_APC).soundId());
        assertEquals(MachineAudio.ENGINE_TURBINE, MachineAudio.engineVoice(VehicleType.LASER_TANK).soundId());
        assertEquals(MachineAudio.ENGINE_WHEELED, MachineAudio.engineVoice(VehicleType.MILITIA_TECHNICAL).soundId());
        assertEquals(MachineAudio.ENGINE_HOVER, MachineAudio.engineVoice(VehicleType.HOVER_SCOUT).soundId());
        MachineAudio.EngineVoice tank = MachineAudio.engineVoice(VehicleType.BREAKTHROUGH_TANK);
        assertTrue(tank.pitch(1f) > tank.pitch(0f) && tank.volume(1f) > tank.volume(0f), "full throttle is higher and louder");
    }

    @Test
    void aVentCuesOnceAsItOpensAndHoldsItsLoopWhileOpen() {
        MachineAudio audio = new MachineAudio();
        Object vent = new Object();
        List<MachineAudio.Loop> loops = new ArrayList<>();
        List<MachineAudio.Cue> cues = new ArrayList<>();

        audio.vent(vent, false, true, 0f, 0f, loops, cues);
        assertTrue(loops.isEmpty() && cues.isEmpty(), "a closed vent is silent");
        audio.vent(vent, true, true, 0f, 0f, loops, cues);
        assertEquals(1, cues.size(), "opening cues once");
        assertEquals(1, loops.size());
        audio.vent(vent, true, true, 0f, 0f, loops, cues);
        assertEquals(1, cues.size(), "staying open does not cue again");
        assertEquals(2, loops.size(), "the loop holds every frame it stays open");

        Object seenOpen = new Object();
        cues.clear();
        loops.clear();
        audio.vent(seenOpen, true, true, 0f, 0f, loops, cues);
        assertTrue(cues.isEmpty(), "a vent first seen open does not replay its opening");
        assertEquals(1, loops.size());

        Object unheard = new Object();
        loops.clear();
        audio.vent(unheard, false, false, 0f, 0f, loops, cues);
        audio.vent(unheard, true, false, 0f, 0f, loops, cues);
        assertTrue(loops.isEmpty() && cues.isEmpty(), "out of earshot or out of sight is silent");
    }
}
