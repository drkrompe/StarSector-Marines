package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadAlertLevel;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BattleRadioChatterTest {

    @Test
    void contactWaitsForInitialRadioDelay() {
        BattleRadioChatter chatter = new BattleRadioChatter(new Random(1L));
        Squad marines = infantry(7, Faction.MARINE);
        marines.alertLevel = SquadAlertLevel.ENGAGED;
        marines.centroidX = 12.5f;
        marines.centroidY = 8.5f;

        assertNull(chatter.advance(0f, List.of(marines)));
        assertNull(chatter.advance(2.4f, List.of(marines)));
        BattleRadioChatter.Emission emission = chatter.advance(0.2f, List.of(marines));

        assertEquals(BattleRadioChatter.Cue.CONTACT, emission.cue());
        assertEquals(7, emission.squadId());
        assertEquals(12.5f, emission.cellX());
        assertEquals(8.5f, emission.cellY());
    }

    @Test
    void fallbackPreemptsAContactWaitingForTheVoiceBudget() {
        BattleRadioChatter chatter = new BattleRadioChatter(new Random(2L));
        Squad marines = infantry(1, Faction.MARINE);
        marines.alertLevel = SquadAlertLevel.ENGAGED;

        assertNull(chatter.advance(0f, List.of(marines)));
        marines.moraleBroken = true;
        BattleRadioChatter.Emission emission = chatter.advance(3f, List.of(marines));

        assertEquals(BattleRadioChatter.Cue.FALLBACK, emission.cue());
    }

    @Test
    void ignoresDefendersMechsDronesAndWipedSquads() {
        BattleRadioChatter chatter = new BattleRadioChatter(new Random(3L));
        Squad defender = infantry(1, Faction.DEFENDER);
        Squad mech = infantry(2, Faction.MARINE);
        mech.mechSquad = true;
        Squad drone = infantry(3, Faction.MARINE);
        drone.droneHubId = 44L;
        Squad wiped = infantry(4, Faction.MARINE);
        wiped.aliveMembers = 0;
        for (Squad squad : List.of(defender, mech, drone, wiped)) {
            squad.alertLevel = SquadAlertLevel.ENGAGED;
        }

        assertNull(chatter.advance(100f, List.of(defender, mech, drone, wiped)));
    }

    @Test
    void sustainedEngagementEventuallyProducesSparseAcknowledgement() {
        BattleRadioChatter chatter = new BattleRadioChatter(new Random(4L));
        Squad marines = infantry(1, Faction.MARINE);
        marines.alertLevel = SquadAlertLevel.ENGAGED;

        BattleRadioChatter.Emission contact = chatter.advance(3f, List.of(marines));
        assertEquals(BattleRadioChatter.Cue.CONTACT, contact.cue());

        BattleRadioChatter.Emission acknowledgement = chatter.advance(30f, List.of(marines));
        assertEquals(BattleRadioChatter.Cue.ACKNOWLEDGE, acknowledgement.cue());
    }

    @Test
    void resetRestoresTheInitialSilence() {
        BattleRadioChatter chatter = new BattleRadioChatter(new Random(5L));
        Squad marines = infantry(1, Faction.MARINE);
        marines.alertLevel = SquadAlertLevel.ENGAGED;
        assertEquals(BattleRadioChatter.Cue.CONTACT,
                chatter.advance(3f, List.of(marines)).cue());

        chatter.reset();

        assertNull(chatter.advance(0f, List.of(marines)));
        assertNull(chatter.advance(2f, List.of(marines)));
        assertEquals(BattleRadioChatter.Cue.CONTACT,
                chatter.advance(1f, List.of(marines)).cue());
    }

    private static Squad infantry(int id, Faction faction) {
        Squad squad = new Squad(id, faction);
        squad.aliveMembers = 4;
        squad.originalSize = 4;
        return squad;
    }
}
