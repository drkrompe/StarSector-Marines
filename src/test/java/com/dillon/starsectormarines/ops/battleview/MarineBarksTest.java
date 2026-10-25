package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.battleview.MarineBarks.Bark;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarineBarksTest {

    @Test void aSquadNeverTalksOverItself() {
        MarineBarks.Channels channels = new MarineBarks.Channels();
        channels.start(Bark.CONTACT, 1L, 7L, 10f);
        assertFalse(channels.admits(Bark.KILL, 2L, 7L, 10.5f), "a squadmate waits for the line to end");
        float free = 10f + Bark.CONTACT.lineSeconds + MarineBarks.SQUAD_PAUSE_SECONDS;
        assertTrue(channels.admits(Bark.KILL, 2L, 7L, free), "and speaks after the squad's breath");
        assertTrue(channels.admits(Bark.KILL, 3L, 8L, 10.5f), "another squad has its own channel");
        assertTrue(channels.admits(Bark.GRABBED, 1L, 7L, 10.5f), "but one seized cries out at once, even mid-line");
    }

    @Test void aMarineAndASquadKeepTheirGaps() {
        MarineBarks.Channels channels = new MarineBarks.Channels();
        channels.start(Bark.KILL, 1L, 7L, 0f);
        assertFalse(channels.admits(Bark.RELOAD, 1L, 7L, 2f), "one marine is not a stream of barks");
        assertTrue(channels.admits(Bark.RELOAD, 2L, 7L, 2f), "a squadmate may speak once the line is done");
        assertFalse(channels.admits(Bark.KILL, 2L, 7L, 2f), "but the squad does not repeat the call soon after");
        assertTrue(channels.admits(Bark.KILL, 2L, 7L, Bark.KILL.squadGapSeconds), "until its gap has passed");
    }

    @Test void theBattleHasAVoiceBudgetButEffortSoundsAreNotVoices() {
        MarineBarks.Channels channels = new MarineBarks.Channels();
        for (int i = 0; i < MarineBarks.VOICES; i++) channels.start(Bark.CONTACT, i, i, 0f);
        assertFalse(channels.admits(Bark.GRABBED, 99L, 99L, 0.1f), "every voice is sounding, cries included");
        assertTrue(channels.admits(Bark.SCUFFLE, 99L, 99L, 0.1f), "a scuffle is not a voice");
        assertTrue(channels.admits(Bark.GRABBED, 99L, 99L, Bark.CONTACT.lineSeconds), "a voice has finished");
    }

    @Test void linesOfAKindTakeTurns() {
        MarineBarks.Mix mix = new MarineBarks.Mix();
        int prior = mix.turn(Bark.CONTACT);
        for (int i = 0; i < 2 * MarineBarks.LINES; i++) {
            int line = mix.turn(Bark.CONTACT);
            assertNotEquals(prior, line, "the same line is never heard twice running");
            assertTrue(line >= 1 && line <= MarineBarks.LINES);
            prior = line;
        }
    }

    @Test void contactClosesNearAndClearsFarWithoutChattering() {
        assertTrue(MarineBarks.inContact(false, MarineBarks.CONTACT_CELLS));
        assertFalse(MarineBarks.inContact(false, MarineBarks.CONTACT_CELLS + 1f), "not yet close");
        assertTrue(MarineBarks.inContact(true, MarineBarks.CLEAR_CELLS - 1f), "a pack milling just out stays contact");
        assertFalse(MarineBarks.inContact(true, MarineBarks.CLEAR_CELLS + 1f), "clear again");
    }

    @Test void gruntsAreHandToHandAndReloadsAreCalledWithRavNear() {
        assertTrue(MarineBarks.hurt(100f, 90f, 1f));
        assertFalse(MarineBarks.hurt(100f, 90f, MarineBarks.MELEE_CELLS + 1f), "hurt by fire from afar");
        assertFalse(MarineBarks.hurt(100f, 99.5f, 1f), "a graze");
        assertFalse(MarineBarks.hurt(100f, 0f, 1f), "dead: the death cry is its own");
        assertTrue(MarineBarks.reloading(0f, 2f, 5f));
        assertFalse(MarineBarks.reloading(1.9f, 2f, 5f), "already reloading");
        assertFalse(MarineBarks.reloading(0f, 2f, MarineBarks.NEAR_CELLS + 1f), "no Rav near");
    }

    @Test void chargeTellsAndDeathsDrawTheirCalls() {
        assertEquals(Bark.BRUTE, MarineBarks.warning(UnitType.SWARM_BRUTE));
        assertEquals(Bark.MATRIARCH, MarineBarks.warning(UnitType.SWARM_MATRIARCH));
        assertNull(MarineBarks.warning(UnitType.SWARM_RUNNER));
        assertEquals(Bark.BIG_KILL, MarineBarks.kill(UnitType.SWARM_BRUTE));
        assertEquals(Bark.KILL, MarineBarks.kill(UnitType.SWARM_RUNNER));
    }

    @Test void aSpeakerKeepsOneVoiceAndSquadmatesDiffer() {
        MarineBarks.Mix mix = new MarineBarks.Mix();
        for (MarineBarks.Cast cast : MarineBarks.Cast.values()) {
            Set<Integer> squad = new HashSet<>();
            for (long id = 1; id <= cast.voices.length; id++) squad.add(mix.voice(cast.ordinal() * 100L + id, cast.ordinal(), cast));
            assertEquals(cast.voices.length, squad.size(), cast + ": a squad as large as the cast speaks in every voice");
        }
        assertEquals(mix.voice(3L, 0L, MarineBarks.Cast.MARINE), mix.voice(3L, 0L, MarineBarks.Cast.MARINE),
                "and each keeps its own");
        for (long id = 1; id <= 60; id++) {
            float pitch = MarineBarks.pitch(id);
            assertTrue(pitch >= 0.96f && pitch <= 1.04f);
        }
        assertEquals("marines_bark_contact_ox_2", MarineBarks.soundId(MarineBarks.Cast.MARINE, Bark.CONTACT, 4, 2));
        assertEquals("marines_bark_civilian_grabbed_b_1",
                MarineBarks.soundId(MarineBarks.Cast.CIVILIAN, Bark.GRABBED, 1, 1));
        assertEquals("marines_bark_scuffle", MarineBarks.soundId(MarineBarks.Cast.MILITIA, Bark.SCUFFLE, 0, 2));
    }

    @Test void whoSpeaksAsWhat() {
        assertEquals(MarineBarks.Cast.MARINE, MarineBarks.cast(Faction.MARINE, UnitType.MARINE));
        assertEquals(MarineBarks.Cast.MILITIA, MarineBarks.cast(Faction.ALLY, UnitType.MILITIA));
        assertEquals(MarineBarks.Cast.MARINE, MarineBarks.cast(Faction.ALLY, UnitType.MARINE), "allied company troops");
        assertEquals(MarineBarks.Cast.CIVILIAN, MarineBarks.cast(Faction.CIVILIAN, UnitType.CIVILIAN));
        assertNull(MarineBarks.cast(Faction.DEFENDER, UnitType.MARINE), "the enemy has no barks");
        assertNull(MarineBarks.cast(Faction.MARINE, UnitType.HEAVY_MECH), "machines have no barks");
        assertFalse(MarineBarks.Cast.CIVILIAN.barks.contains(Bark.RELOAD), "colonists make no soldier's calls");
        assertTrue(MarineBarks.Cast.CIVILIAN.barks.contains(Bark.GRABBED));
    }

    @Test void squadlessPeopleShareAChannelWithTheirCastNearby() {
        long here = MarineBarks.patch(MarineBarks.Cast.CIVILIAN, 10.2f, 10.7f);
        assertEquals(here, MarineBarks.patch(MarineBarks.Cast.CIVILIAN, 11.9f, 2.1f), "the same patch of ground");
        assertNotEquals(here, MarineBarks.patch(MarineBarks.Cast.CIVILIAN, 10.2f, 10.7f + MarineBarks.PATCH_CELLS),
                "the next patch");
        assertNotEquals(here, MarineBarks.patch(MarineBarks.Cast.MILITIA, 10.2f, 10.7f), "another cast");
    }
}
