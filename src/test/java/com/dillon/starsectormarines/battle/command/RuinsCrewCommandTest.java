package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuinsCrewCommandTest {

    private static final int LIMIT = ExtractionDefenderCommand.ALARM_RESPONSE_LIMIT;

    @Test void aQuietSiteWalksOneSquadAtTheDigAndHoldsTheRest() {
        assertEquals(new RuinsCrewCommand.Answer(1, 0), RuinsCrewCommand.answer(false, false, false, 5));
    }

    @Test void anAlarmAtTheDigTakesTheWholeAlarm() {
        assertEquals(new RuinsCrewCommand.Answer(LIMIT, 0), RuinsCrewCommand.answer(false, true, false, 5));
    }

    @Test void troubleAtTheCampAloneSendsTheStashItsShareAndKeepsTheDigWalked() {
        assertEquals(new RuinsCrewCommand.Answer(1, RuinsCrewCommand.STASH_RESPONSE),
                RuinsCrewCommand.answer(false, false, true, 5));
    }

    @Test void theDigComesFirstWhenBothAreThreatened() {
        RuinsCrewCommand.Answer both = RuinsCrewCommand.answer(false, true, true, 5);
        assertEquals(LIMIT, both.dig() + both.stash());
        assertEquals(1, both.stash());
        assertEquals(new RuinsCrewCommand.Answer(2, 0), RuinsCrewCommand.answer(false, true, true, 2),
                "too few squads for both: the dig keeps them");
    }

    @Test void aSmallPoolAndADecidedVaultBoundTheAnswer() {
        assertEquals(new RuinsCrewCommand.Answer(1, 0), RuinsCrewCommand.answer(false, false, true, 1));
        assertEquals(new RuinsCrewCommand.Answer(0, 0), RuinsCrewCommand.answer(false, true, true, 0));
        assertEquals(new RuinsCrewCommand.Answer(0, 0), RuinsCrewCommand.answer(true, true, true, 5));
    }

    @Test void onlyContactsWithinTheThreatRadiusCountForAPlace() {
        int r = RuinsCrewCommand.THREAT_RADIUS;
        CommanderContact inside = contact(1, 10 + r, 10);
        CommanderContact outside = contact(2, 10 + r, 11);
        assertEquals(List.of(inside), RuinsCrewCommand.near(List.of(inside, outside), 10, 10));
    }

    private static CommanderContact contact(long id, int x, int y) {
        return new CommanderContact(id, x, y, 0, 1f, 1f, null, -1, false);
    }
}
