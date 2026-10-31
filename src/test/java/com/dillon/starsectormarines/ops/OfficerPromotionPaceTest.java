package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.OfficerCommandProfile;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The founding lieutenant's promotions across the opening, each mission played once (owner, 2026-10-27). */
class OfficerPromotionPaceTest {
    private static final int[] OPENING_FEES = {
            OpeningOperationKind.RELIEF.payout, OpeningOperationKind.COUNTERATTACK.payout, 12_000, 18_000};

    /** The rank after each mission, carrying XP the way {@code OfficerMissionResult.apply} does. */
    private static List<Rank> ranksAfterEach(int casualties) {
        Rank rank = Rank.LIEUTENANT;
        int xp = 0;
        List<Rank> ranks = new ArrayList<>();
        for (int fee : OPENING_FEES) {
            var officer = new OfficerCommandProfile("lt", "Lt", rank, xp, Status.ACTIVE, false, 0, 0);
            xp += OfficerMissionResult.compute(List.of(), officer, Set.of(), Set.of(), true, fee,
                    "m" + fee, 1f, 24, casualties).get(0).xpGained();
            while (!rank.isTerminal() && xp >= rank.xpToNext()) { xp -= rank.xpToNext(); rank = rank.promote(); }
            ranks.add(rank);
        }
        return ranks;
    }

    @Test void counterattackPromotesTheLieutenantAndThunderRunTheCaptain() {
        assertEquals(List.of(Rank.LIEUTENANT, Rank.CAPTAIN, Rank.CAPTAIN, Rank.MAJOR), ranksAfterEach(0));
    }

    @Test void heavyLossesStillPromoteOnTheSameMissions() {
        assertEquals(List.of(Rank.LIEUTENANT, Rank.CAPTAIN, Rank.CAPTAIN, Rank.MAJOR), ranksAfterEach(3));
    }
}
