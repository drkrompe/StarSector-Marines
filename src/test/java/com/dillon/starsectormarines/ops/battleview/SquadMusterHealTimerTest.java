package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The barracks full-heal timer counts down to the last wounded billet, in whole hours. */
public class SquadMusterHealTimerTest {

    @Test
    public void timerReadsTheLatestReturnRoundedUpToTheHour() {
        MarineRoster roster = new MarineRoster();
        roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
        MarineSquad squad = roster.squads().stream().filter(q -> !q.reserve()).findFirst().orElseThrow();
        assertEquals(0, SquadMuster.hoursUntilFullHeal(List.of(SquadMuster.from(roster, squad.id(), 0f))));

        List<String> members = roster.manningMemberIds(squad);
        roster.applySoldierOutcome(Map.of(members.get(0), MarineSoldierStatus.WIA), 10f, MarineRoster.WIA_RECOVERY_DAYS);
        roster.applySoldierOutcome(Map.of(members.get(1), MarineSoldierStatus.WIA), 10.5f, MarineRoster.WIA_RECOVERY_DAYS);

        assertEquals(24, SquadMuster.hoursUntilFullHeal(List.of(SquadMuster.from(roster, squad.id(), 10.5f))));
        assertEquals(1, SquadMuster.hoursUntilFullHeal(List.of(SquadMuster.from(roster, squad.id(), 11.49f))));

        roster.recoverWounded(11.5f);
        assertEquals(0, SquadMuster.hoursUntilFullHeal(List.of(SquadMuster.from(roster, squad.id(), 11.5f))));
    }
}
