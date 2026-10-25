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

    @Test
    void aBumpCuesOnceWhenTheCountRisesLouderTheHarderItHit() {
        MachineAudio audio = new MachineAudio();
        Object hull = new Object();
        List<MachineAudio.Cue> cues = new ArrayList<>();
        VehicleType tank = VehicleType.BREAKTHROUGH_TANK;

        audio.bump(hull, 3, true, tank, 1f, 0f, 0f, cues);
        assertTrue(cues.isEmpty(), "a hull first seen does not replay its old bumps");
        audio.bump(hull, 3, true, tank, 1f, 0f, 0f, cues);
        assertTrue(cues.isEmpty(), "no new bump, no sound");
        audio.bump(hull, 4, true, tank, tank.maxSpeed * .25f, 0f, 0f, cues);
        audio.bump(hull, 5, true, tank, tank.maxSpeed, 0f, 0f, cues);
        assertEquals(2, cues.size());
        assertEquals(MachineAudio.HULL_BUMP, cues.get(0).soundId());
        assertTrue(cues.get(1).volume() > cues.get(0).volume(), "a hit at top speed is louder");

        audio.bump(hull, 6, false, tank, tank.maxSpeed, 0f, 0f, cues);
        assertEquals(2, cues.size(), "an unheard bump is silent");

        List<MachineAudio.Cue> light = new ArrayList<>();
        Object pickup = new Object();
        audio.bump(pickup, 0, true, VehicleType.MILITIA_TECHNICAL, 1f, 0f, 0f, light);
        audio.bump(pickup, 1, true, VehicleType.MILITIA_TECHNICAL, 1f, 0f, 0f, light);
        assertTrue(light.get(0).pitch() > cues.get(0).pitch(), "a light hull bumps higher than a tank");
    }
}
