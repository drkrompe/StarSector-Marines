package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.Rank;
import org.junit.jupiter.api.Test;

import static com.dillon.starsectormarines.ops.BarracksCommandModel.xpFraction;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The officer's XP gauge fills toward the next rank and reads full at the top of the ladder. */
public class OfficerXpGaugeTest {

    @Test
    public void fillsTowardTheNextRank() {
        assertEquals(0f, xpFraction(0, Rank.CAPTAIN));
        assertEquals(0.5f, xpFraction(Rank.CAPTAIN.xpToNext() / 2, Rank.CAPTAIN));
        assertEquals(1f, xpFraction(Rank.CAPTAIN.xpToNext() * 3, Rank.CAPTAIN), "overflow clamps");
        assertEquals(0f, xpFraction(-50, Rank.CAPTAIN), "negative clamps");
    }

    @Test
    public void topRankReadsFull() {
        assertEquals(1f, xpFraction(0, Rank.COLONEL));
    }
}
